package io.quarkiverse.qubit.deployment.generation.expression;

import static io.quarkiverse.qubit.deployment.ast.LambdaExpression.BinaryOp.Operator.AND;
import static io.quarkiverse.qubit.deployment.ast.LambdaExpression.BinaryOp.Operator.OR;
import static io.quarkiverse.qubit.deployment.generation.MethodDescriptors.*;
import static io.quarkiverse.qubit.runtime.QubitConstants.METHOD_EQUALS;

import io.quarkiverse.qubit.deployment.ast.LambdaExpression;
import io.quarkiverse.qubit.deployment.ast.LambdaExpression.ExistsSubquery;
import io.quarkiverse.qubit.deployment.ast.LambdaExpression.FieldAccess;
import io.quarkiverse.qubit.deployment.ast.LambdaExpression.InSubquery;
import io.quarkiverse.qubit.deployment.ast.LambdaExpression.PathExpression;
import io.quarkiverse.qubit.deployment.ast.LambdaExpression.PathSegment;
import io.quarkiverse.qubit.deployment.ast.LambdaExpression.ScalarSubquery;
import io.quarkiverse.qubit.deployment.ast.LambdaExpression.SubqueryAggregationType;
import io.quarkiverse.qubit.deployment.common.ExpressionTypeInferrer;
import io.quarkiverse.qubit.deployment.generation.GizmoHelper;
import io.quarkus.gizmo.MethodCreator;
import io.quarkus.gizmo.MethodDescriptor;
import io.quarkus.gizmo.ResultHandle;
import io.quarkus.logging.Log;
import jakarta.persistence.criteria.Predicate;

/**
 * Generates JPA Criteria API bytecode for subquery expressions.
 * <p>
 * Handles ScalarSubquery, ExistsSubquery, and InSubquery.
 *
 * <p>Architecture:
 * <ul>
 *   <li>ScalarSubquery → JPA Subquery with aggregation function</li>
 *   <li>ExistsSubquery → cb.exists(subquery) or cb.not(cb.exists(subquery))</li>
 *   <li>InSubquery → path.in(subquery) or cb.not(path.in(subquery))</li>
 * </ul>
 *
 * <p>Example generated code for ScalarSubquery:
 * <pre>
 * // p.salary > subquery(Person.class).avg(q -> q.salary)
 * Subquery&lt;Double&gt; avgSub = query.subquery(Double.class);
 * Root&lt;Person&gt; subRoot = avgSub.from(Person.class);
 * avgSub.select(cb.avg(subRoot.get("salary")));
 * // Used in: cb.greaterThan(root.get("salary"), avgSub)
 * </pre>
 *
 * internal generate* methods return ResultHandle, but callers
 * typically know the result is non-null in their specific context.
 */
public class SubqueryExpressionBuilder implements ExpressionBuilder {

    /**
     * Generates JPA scalar aggregation subquery.
     *
     * <p>Creates a subquery that returns a single aggregated value (AVG, SUM, MIN, MAX, COUNT).
     *
     * @param method the method creator for bytecode generation
     * @param scalar the scalar subquery expression
     * @param cb the CriteriaBuilder handle
     * @param query the CriteriaQuery handle (needed to create subqueries)
     * @param outerRoot the outer query's root handle (for correlated subqueries)
     * @param capturedValues the captured variables array handle
     * @return the JPA Subquery handle
     */
    public ResultHandle buildScalarSubquery(
            MethodCreator method,
            ScalarSubquery scalar,
            ResultHandle cb,
            ResultHandle query,
            ResultHandle outerRoot,
            ResultHandle capturedValues) {

        // BR-003: Null check at method entry to prevent NPE on scalar.aggregationType()
        if (scalar == null) {
            throw new IllegalArgumentException("ScalarSubquery cannot be null");
        }

        // Determine the subquery result type based on aggregation
        Class<?> resultType = getAggregationResultType(scalar.aggregationType());

        // Create subquery: query.subquery(resultType)
        ResultHandle resultTypeHandle = method.loadClass(resultType);
        ResultHandle subquery = method.invokeInterfaceMethod(
                CRITERIA_QUERY_SUBQUERY, query, resultTypeHandle);

        // Create from clause: subquery.from(entityClass)
        ResultHandle entityClassHandle = GizmoHelper.loadEntityClass(method, scalar.entityClass(), scalar.entityClassName());
        ResultHandle subRoot = method.invokeInterfaceMethod(
                SUBQUERY_FROM, subquery, entityClassHandle);

        // Generate the select expression with aggregation
        ResultHandle aggregation = generateAggregation(method, scalar, cb, subRoot);

        // Set the select: subquery.select(aggregation)
        method.invokeInterfaceMethod(SUBQUERY_SELECT, subquery, aggregation);

        // Generate the where clause if there's a predicate
        if (scalar.hasPredicate()) {
            ResultHandle wherePredicate = generateSubqueryPredicate(
                    method, scalar.predicate(), cb, subRoot, outerRoot, capturedValues);
            if (wherePredicate != null) {
                applySubqueryWhere(method, subquery, wherePredicate);
            }
        }

        return subquery;
    }

    /**
     * Generates JPA EXISTS subquery predicate.
     *
     * <p>Creates cb.exists(subquery) or cb.not(cb.exists(subquery)).
     *
     * @param method the method creator for bytecode generation
     * @param exists the EXISTS subquery expression
     * @param cb the CriteriaBuilder handle
     * @param query the CriteriaQuery handle
     * @param outerRoot the outer query's root handle (for correlation)
     * @param capturedValues the captured variables array handle
     * @return the JPA Predicate handle
     */
    public ResultHandle buildExistsSubquery(
            MethodCreator method,
            ExistsSubquery exists,
            ResultHandle cb,
            ResultHandle query,
            ResultHandle outerRoot,
            ResultHandle capturedValues) {

        // BR-003: Null check at method entry to prevent NPE on exists.entityClass()
        if (exists == null) {
            throw new IllegalArgumentException("ExistsSubquery cannot be null");
        }

        // Create subquery: query.subquery(entityClass)
        ResultHandle entityClassHandle = GizmoHelper.loadEntityClass(method, exists.entityClass(), exists.entityClassName());
        ResultHandle subquery = method.invokeInterfaceMethod(
                CRITERIA_QUERY_SUBQUERY, query, entityClassHandle);

        // Create from clause: subquery.from(entityClass)
        ResultHandle subRoot = method.invokeInterfaceMethod(
                SUBQUERY_FROM, subquery, entityClassHandle);

        // Select the subquery root (EXISTS just needs any selection)
        method.invokeInterfaceMethod(SUBQUERY_SELECT, subquery, subRoot);

        // Generate the where clause from the predicate
        ResultHandle wherePredicate = generateSubqueryPredicate(
                method, exists.predicate(), cb, subRoot, outerRoot, capturedValues);
        if (wherePredicate != null) {
            applySubqueryWhere(method, subquery, wherePredicate);
        }

        // Generate cb.exists(subquery)
        ResultHandle existsPredicate = method.invokeInterfaceMethod(CB_EXISTS, cb, subquery);

        // If negated, wrap with cb.not()
        if (exists.negated()) {
            return method.invokeInterfaceMethod(CB_NOT, cb, existsPredicate);
        }

        return existsPredicate;
    }

    /**
     * Generates JPA IN subquery predicate.
     *
     * <p>Creates path.in(subquery) or cb.not(path.in(subquery)).
     *
     * @param method the method creator for bytecode generation
     * @param inSubquery the IN subquery expression
     * @param cb the CriteriaBuilder handle
     * @param query the CriteriaQuery handle
     * @param outerRoot the outer query's root handle
     * @param capturedValues the captured variables array handle
     * @return the JPA Predicate handle
     */
    public ResultHandle buildInSubquery(
            MethodCreator method,
            InSubquery inSubquery,
            ResultHandle cb,
            ResultHandle query,
            ResultHandle outerRoot,
            ResultHandle capturedValues) {

        // BR-003: Null check at method entry to prevent NPE on inSubquery.field()
        if (inSubquery == null) {
            throw new IllegalArgumentException("InSubquery cannot be null");
        }

        // Generate the left-hand field expression from the outer query
        ResultHandle fieldPath = generateFieldPath(method, inSubquery.field(), outerRoot);

        // Determine the result type from the select expression
        Class<?> selectType = ExpressionTypeInferrer.inferFieldType(inSubquery.selectExpression());

        // Create subquery: query.subquery(selectType)
        ResultHandle selectTypeHandle = method.loadClass(selectType);
        ResultHandle subquery = method.invokeInterfaceMethod(
                CRITERIA_QUERY_SUBQUERY, query, selectTypeHandle);

        // Create from clause: subquery.from(entityClass)
        ResultHandle entityClassHandle = GizmoHelper.loadEntityClass(method, inSubquery.entityClass(), inSubquery.entityClassName());
        ResultHandle subRoot = method.invokeInterfaceMethod(
                SUBQUERY_FROM, subquery, entityClassHandle);

        // Generate the select expression
        ResultHandle selectExpr = generateFieldPath(method, inSubquery.selectExpression(), subRoot);

        // Set the select: subquery.select(selectExpr)
        method.invokeInterfaceMethod(SUBQUERY_SELECT, subquery, selectExpr);

        // Generate the where clause if there's a predicate
        if (inSubquery.hasPredicate()) {
            ResultHandle wherePredicate = generateSubqueryPredicate(
                    method, inSubquery.predicate(), cb, subRoot, outerRoot, capturedValues);
            if (wherePredicate != null) {
                applySubqueryWhere(method, subquery, wherePredicate);
            }
        }

        // Generate fieldPath.in(subquery)
        ResultHandle inPredicate = method.invokeInterfaceMethod(
                EXPRESSION_IN, fieldPath, subquery);

        // If negated, wrap with cb.not()
        if (inSubquery.negated()) {
            return method.invokeInterfaceMethod(CB_NOT, cb, inPredicate);
        }

        return inPredicate;
    }

    /**
     * Returns the JPA result type for an aggregation.
     */
    private Class<?> getAggregationResultType(SubqueryAggregationType aggType) {
        // CS-008: Added default case for future-proofing
        return switch (aggType) {
            case AVG -> Double.class;
            case SUM -> Number.class;  // Could be Integer, Long, Double, etc.
            case MIN, MAX -> Comparable.class;
            case COUNT -> Long.class;
            default -> throw new IllegalStateException("Unexpected aggregation type: " + aggType);
        };
    }

    /**
     * Generates the aggregation function call for a scalar subquery.
     */
    private ResultHandle generateAggregation(
            MethodCreator method,
            ScalarSubquery scalar,
            ResultHandle cb,
            ResultHandle subRoot) {

        // For COUNT, we don't need a field expression
        if (scalar.isCount()) {
            return method.invokeInterfaceMethod(CB_COUNT, cb, subRoot);
        }

        // Generate the field path from the field expression
        ResultHandle fieldPath = generateFieldPath(method, scalar.fieldExpression(), subRoot);

        // Generate the appropriate aggregation
        // CS-008: Added default case for future-proofing
        return switch (scalar.aggregationType()) {
            case AVG -> method.invokeInterfaceMethod(CB_AVG, cb, fieldPath);
            case SUM -> method.invokeInterfaceMethod(CB_SUM, cb, fieldPath);
            case MIN -> method.invokeInterfaceMethod(CB_MIN, cb, fieldPath);
            case MAX -> method.invokeInterfaceMethod(CB_MAX, cb, fieldPath);
            case COUNT -> throw new IllegalStateException("COUNT should be handled above");
            default -> throw new IllegalStateException("Unexpected aggregation type: " + scalar.aggregationType());
        };
    }

    /**
     * Generates a field path expression.
     *
     * @param method the method creator for bytecode generation
     * @param expr the expression to generate a path for (must be FieldAccess or PathExpression)
     * @param root the root handle to build the path from
     * @return ResultHandle for the generated path expression
     * @throws IllegalArgumentException if expr is null or an unsupported expression type
     */
    private ResultHandle generateFieldPath(MethodCreator method, LambdaExpression expr, ResultHandle root) {
        if (expr == null) {
            throw new IllegalArgumentException("Field path expression cannot be null");
        }
        // Java 21 pattern matching switch for type dispatch
        return switch (expr) {
            case FieldAccess field -> {
                ResultHandle fieldName = method.load(field.fieldName());
                yield method.invokeInterfaceMethod(PATH_GET, root, fieldName);
            }

            case PathExpression pathExpr -> {
                ResultHandle currentPath = root;
                for (PathSegment segment : pathExpr.segments()) {
                    ResultHandle fieldName = method.load(segment.fieldName());
                    currentPath = method.invokeInterfaceMethod(PATH_GET, currentPath, fieldName);
                }
                yield currentPath;
            }

            default -> throw new IllegalArgumentException(
                    "Unsupported expression type for field path generation: " + expr.getClass().getSimpleName()
                            + ". Expected FieldAccess or PathExpression.");
        };
    }

    /**
     * Generates a predicate for the subquery's WHERE clause.
     *
     * <p>This handles both simple predicates (using only subquery root) and
     * correlated predicates (referencing the outer query's root).
     *
     * <p>BR-010: Added MethodCall handling for .equals() comparisons in EXISTS/NOT EXISTS predicates.
     *
     * @param method the method creator for bytecode generation
     * @param predicate the predicate expression, or null
     * @param cb the CriteriaBuilder handle
     * @param subRoot the subquery root handle
     * @param outerRoot the outer query root handle
     * @param capturedValues the captured values array handle
     * @return the generated predicate handle, or null if predicate is null or unhandled
     */
    private ResultHandle generateSubqueryPredicate(
            MethodCreator method,
            LambdaExpression predicate,
            ResultHandle cb,
            ResultHandle subRoot,
            ResultHandle outerRoot,
            ResultHandle capturedValues) {

        if (predicate == null) {
            return null;
        }

        // Java 21 pattern matching switch for type dispatch
        return switch (predicate) {
            // Handle BinaryOp predicates (most common case)
            case LambdaExpression.BinaryOp binOp ->
                generateBinaryOpPredicate(method, binOp, cb, subRoot, outerRoot, capturedValues);

            // BR-010: Handle UnaryOp predicates (e.g., NOT operations)
            case LambdaExpression.UnaryOp unaryOp -> {
                ResultHandle operand = generateSubqueryPredicate(
                        method, unaryOp.operand(), cb, subRoot, outerRoot, capturedValues);
                yield switch (unaryOp.operator()) {
                    case NOT -> method.invokeInterfaceMethod(CB_NOT, cb, operand);
                };
            }

            // BR-010: Handle MethodCall predicates (e.g., .equals() calls in EXISTS predicates)
            case LambdaExpression.MethodCall methodCall ->
                generateMethodCallPredicate(method, methodCall, cb, subRoot, outerRoot, capturedValues);

            // Handle FieldAccess as boolean
            case FieldAccess field -> {
                ResultHandle path = generateFieldPath(method, field, subRoot);
                yield method.invokeInterfaceMethod(CB_IS_TRUE, cb, path);
            }

            // Handle PathExpression as boolean
            case PathExpression pathExpr -> {
                ResultHandle path = generateFieldPath(method, pathExpr, subRoot);
                yield method.invokeInterfaceMethod(CB_IS_TRUE, cb, path);
            }

            default -> {
                Log.warnf("Unhandled predicate type in generateSubqueryPredicate: %s. "
                        + "This may indicate a missing case handler.",
                        predicate.getClass().getSimpleName());
                yield null;
            }
        };
    }

    /**
     * Generates a predicate for method call expressions in subquery WHERE clauses.
     *
     * <p>BR-010: Handles .equals() method calls which translate to cb.equal() predicates.
     * This is essential for EXISTS/NOT EXISTS subqueries where predicates like
     * {@code ph.owner.id.equals(p.id)} need to be translated to JPA predicates.
     *
     * @param method the method creator for bytecode generation
     * @param methodCall the method call expression
     * @param cb the CriteriaBuilder handle
     * @param subRoot the subquery root handle
     * @param outerRoot the outer query root handle
     * @param capturedValues the captured values array handle
     * @return the generated predicate handle, or null if method is not recognized
     */
    private ResultHandle generateMethodCallPredicate(
            MethodCreator method,
            LambdaExpression.MethodCall methodCall,
            ResultHandle cb,
            ResultHandle subRoot,
            ResultHandle outerRoot,
            ResultHandle capturedValues) {

        // Handle .equals() method calls → cb.equal()
        if (METHOD_EQUALS.equals(methodCall.methodName()) && !methodCall.arguments().isEmpty()) {
            // Generate expression for the target (e.g., ph.owner.id)
            ResultHandle targetExpr = generateSubqueryExpression(
                    method, methodCall.target(), cb, subRoot, outerRoot, capturedValues);

            // Generate expression for the argument (e.g., p.id which may be a CorrelatedVariable)
            ResultHandle argumentExpr = generateSubqueryExpression(
                    method, methodCall.arguments().get(0), cb, subRoot, outerRoot, capturedValues);

            // Generate cb.equal(target, argument)
            return method.invokeInterfaceMethod(CB_EQUAL, cb, targetExpr, argumentExpr);
        }

        Log.warnf("Unhandled method call in subquery predicate: %s. "
                + "This may indicate a missing case handler.",
                methodCall.methodName());
        return null;
    }

    /**
     * Generates a binary operation predicate for subquery WHERE clause.
     *
     * @param method the method creator for bytecode generation
     * @param binOp the binary operation expression
     * @param cb the CriteriaBuilder handle
     * @param subRoot the subquery root handle
     * @param outerRoot the outer query root handle
     * @param capturedValues the captured values array handle
     * @return the generated predicate, or null for unsupported operators
     */
    private ResultHandle generateBinaryOpPredicate(
            MethodCreator method,
            LambdaExpression.BinaryOp binOp,
            ResultHandle cb,
            ResultHandle subRoot,
            ResultHandle outerRoot,
            ResultHandle capturedValues) {

        // Handle logical operators (AND/OR) differently - they need predicates
        if (binOp.operator() == AND ||binOp.operator() == OR) {
            // Recursively generate predicates for both sides
            ResultHandle leftPredicate = generateSubqueryPredicate(method, binOp.left(), cb, subRoot, outerRoot, capturedValues);
            ResultHandle rightPredicate = generateSubqueryPredicate(method, binOp.right(), cb, subRoot, outerRoot, capturedValues);

            ResultHandle predicateArray = method.newArray(Predicate.class, 2);
            method.writeArrayValue(predicateArray, 0, leftPredicate);
            method.writeArrayValue(predicateArray, 1, rightPredicate);

            MethodDescriptor cbOperator = binOp.operator() == AND ? CB_AND : CB_OR;
            return method.invokeInterfaceMethod(cbOperator, cb, predicateArray);
        }

        // For comparison operators, generate expressions
        ResultHandle left = generateSubqueryExpression(method, binOp.left(), cb, subRoot, outerRoot, capturedValues);
        ResultHandle right = generateSubqueryExpression(method, binOp.right(), cb, subRoot, outerRoot, capturedValues);

        // Generate the appropriate comparison
        return switch (binOp.operator()) {
            case EQ -> method.invokeInterfaceMethod(CB_EQUAL, cb, left, right);
            case NE -> method.invokeInterfaceMethod(CB_NOT_EQUAL, cb, left, right);
            case GT -> method.invokeInterfaceMethod(CB_GREATER_THAN, cb, left, right);
            case GE -> method.invokeInterfaceMethod(CB_GREATER_THAN_OR_EQUAL, cb, left, right);
            case LT -> method.invokeInterfaceMethod(CB_LESS_THAN, cb, left, right);
            case LE -> method.invokeInterfaceMethod(CB_LESS_THAN_OR_EQUAL, cb, left, right);
            default -> null;
        };
    }

    /**
     * Generates an expression for use in subquery predicates.
     *
     * <p>This handles both subquery-local expressions and correlated references
     * to the outer query.
     *
     * <p>BR-010: Added MethodCall handling for getter chains and BinaryOp for nested expressions.
     *
     * @param method the method creator for bytecode generation
     * @param expr the expression to generate, or null
     * @param cb the CriteriaBuilder handle
     * @param subRoot the subquery root handle
     * @param outerRoot the outer query root handle (for correlated subqueries)
     * @param capturedValues the captured values array handle
     * @return ResultHandle for the generated expression, or null if expr is null or unhandled
     */
    private ResultHandle generateSubqueryExpression(
            MethodCreator method,
            LambdaExpression expr,
            ResultHandle cb,
            ResultHandle subRoot,
            ResultHandle outerRoot,
            ResultHandle capturedValues) {

        if (expr == null) {
            return null;
        }

        return switch (expr) {
            case FieldAccess field ->
                generateFieldPath(method, field, subRoot);
            case PathExpression pathExpr ->
                generateFieldPath(method, pathExpr, subRoot);
            case LambdaExpression.CorrelatedVariable correlated ->
                generateFieldPath(method, correlated.fieldExpression(), outerRoot);
            case LambdaExpression.Constant constant ->
                generateConstant(method, constant);
            case LambdaExpression.CapturedVariable capturedVar -> {
                ResultHandle index = method.load(capturedVar.index());
                ResultHandle value = method.readArrayValue(capturedValues, index);
                yield method.checkCast(value, Object.class);
            }

            // BR-010: Handle Parameter expressions (the lambda parameter itself, e.g., `ph` in `ph.owner`)
            // The subquery parameter refers to the subquery root
            case LambdaExpression.Parameter ignored -> subRoot;

            // BR-010: Handle MethodCall expressions (getter chains like ph.getOwner().getId())
            case LambdaExpression.MethodCall methodCall ->
                generateMethodCallExpression(method, methodCall, cb, subRoot, outerRoot, capturedValues);

            // BR-010: Handle BinaryOp expressions
            // For comparison operators (EQ, NE, GT, GE, LT, LE), use generateBinaryOpPredicate
            // since Predicate extends Expression<Boolean>. This handles nested predicates like:
            // BinaryOp[BinaryOp[path, EQ, field], EQ, Constant[true]]
            // For arithmetic operators (ADD, SUB, MUL, DIV), use generateBinaryOpExpression.
            case LambdaExpression.BinaryOp binOp -> {
                if (isComparisonOrLogicalOperator(binOp.operator())) {
                    yield generateBinaryOpPredicate(method, binOp, cb, subRoot, outerRoot, capturedValues);
                } else {
                    yield generateBinaryOpExpression(method, binOp, cb, subRoot, outerRoot, capturedValues);
                }
            }

            default -> {
                Log.warnf("Unhandled expression type in generateSubqueryExpression: %s. "
                        + "This may indicate a missing case handler or an unexpected AST structure.",
                        expr.getClass().getSimpleName());
                yield null;
            }
        };
    }

    /**
     * Generates a JPA expression for method call expressions in subqueries.
     *
     * <p>BR-010: Handles getter method chains that navigate through entity relationships.
     * For example, {@code ph.getOwner().getId()} generates a path expression.
     *
     * @param method the method creator for bytecode generation
     * @param methodCall the method call expression
     * @param cb the CriteriaBuilder handle
     * @param subRoot the subquery root handle
     * @param outerRoot the outer query root handle
     * @param capturedValues the captured values array handle
     * @return the generated expression handle
     */
    private ResultHandle generateMethodCallExpression(
            MethodCreator method,
            LambdaExpression.MethodCall methodCall,
            ResultHandle cb,
            ResultHandle subRoot,
            ResultHandle outerRoot,
            ResultHandle capturedValues) {

        String methodName = methodCall.methodName();

        // Handle getter methods (getXxx, isXxx) → path navigation
        if (methodName.startsWith("get") || methodName.startsWith("is")) {
            // First, resolve the target expression
            ResultHandle targetPath = generateSubqueryExpression(
                    method, methodCall.target(), cb, subRoot, outerRoot, capturedValues);

            // Extract field name from getter (getOwner → owner, isActive → active)
            // CS-014: Use shared utility method instead of local duplicate
            String fieldName = ExpressionTypeInferrer.extractFieldName(methodName);
            ResultHandle fieldNameHandle = method.load(fieldName);

            // Generate path.get(fieldName)
            return method.invokeInterfaceMethod(PATH_GET, targetPath, fieldNameHandle);
        }

        Log.warnf("Unhandled method call expression in subquery: %s. "
                + "This may indicate a missing case handler.",
                methodName);
        return null;
    }

    /**
     * Generates a JPA expression for binary operations in subquery expressions.
     *
     * <p>BR-010: Handles arithmetic and comparison operations within subquery predicates.
     *
     * @param method the method creator for bytecode generation
     * @param binOp the binary operation expression
     * @param cb the CriteriaBuilder handle
     * @param subRoot the subquery root handle
     * @param outerRoot the outer query root handle
     * @param capturedValues the captured values array handle
     * @return the generated expression handle
     */
    private ResultHandle generateBinaryOpExpression(
            MethodCreator method,
            LambdaExpression.BinaryOp binOp,
            ResultHandle cb,
            ResultHandle subRoot,
            ResultHandle outerRoot,
            ResultHandle capturedValues) {

        ResultHandle left = generateSubqueryExpression(method, binOp.left(), cb, subRoot, outerRoot, capturedValues);
        ResultHandle right = generateSubqueryExpression(method, binOp.right(), cb, subRoot, outerRoot, capturedValues);

        // Generate the appropriate arithmetic operation
        return switch (binOp.operator()) {
            case ADD -> method.invokeInterfaceMethod(CB_SUM_BINARY, cb, left, right);
            case SUB -> method.invokeInterfaceMethod(CB_DIFF, cb, left, right);
            case MUL -> method.invokeInterfaceMethod(CB_PROD, cb, left, right);
            case DIV -> method.invokeInterfaceMethod(CB_QUOT, cb, left, right);
            default -> {
                Log.warnf("Unhandled binary operator in subquery expression: %s", binOp.operator());
                yield null;
            }
        };
    }

    /**
     * Checks if an operator is a comparison or logical operator.
     *
     * <p>BR-010: Comparison and logical operators produce predicates (Expression&lt;Boolean&gt;),
     * not numeric expressions. This is used to route BinaryOp expressions to the correct
     * generator method in subquery handling.
     *
     * @param operator the binary operator to check
     * @return true if the operator is EQ, NE, GT, GE, LT, LE, AND, or OR
     */
    private boolean isComparisonOrLogicalOperator(LambdaExpression.BinaryOp.Operator operator) {
        return switch (operator) {
            case EQ, NE, GT, GE, LT, LE, AND, OR -> true;
            case ADD, SUB, MUL, DIV -> false;
            default -> false;  // Future operators default to arithmetic handling
        };
    }

    /**
     * Generates a constant value.
     *
     * <p>BR-006: Added warning logging for unhandled types to prevent silent failures.
     */
    private ResultHandle generateConstant(MethodCreator method, LambdaExpression.Constant constant) {
        Object value = constant.value();
        // Java 21 pattern matching switch for type dispatch
        return switch (value) {
            case null -> method.loadNull();
            case String s -> method.load(s);
            case Integer i -> method.load(i);
            case Long l -> method.load(l);
            case Boolean b -> method.load(b);
            case Double d -> method.load(d);
            case Float f -> method.load(f);
            // BR-006: Log warning for unhandled constant types instead of silently returning null
            default -> {
                Log.warnf("Unhandled constant type in subquery generateConstant: %s. "
                        + "This may indicate a missing case handler. Returning null literal.",
                        value.getClass().getSimpleName());
                yield method.loadNull();
            }
        };
    }

    /**
     * Applies a WHERE predicate to a subquery using the correct JPA API.
     * <p>
     * JPA's Subquery.where() method takes Predicate... (varargs), so we need
     * to create an array for the invocation.
     */
    private void applySubqueryWhere(MethodCreator method, ResultHandle subquery, ResultHandle predicate) {
        // Create a Predicate[] array with a single element
        ResultHandle predicateArray = method.newArray(Predicate.class, 1);
        method.writeArrayValue(predicateArray, 0, predicate);

        // Call subquery.where(predicateArray)
        method.invokeInterfaceMethod(SUBQUERY_WHERE, subquery, predicateArray);
    }
}
