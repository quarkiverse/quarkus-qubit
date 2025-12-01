package io.quarkiverse.qubit.deployment.generation.expression;

import io.quarkus.gizmo.MethodCreator;
import io.quarkus.gizmo.MethodDescriptor;
import io.quarkus.gizmo.ResultHandle;
import io.quarkiverse.qubit.deployment.ast.LambdaExpression;
import io.quarkiverse.qubit.deployment.common.ExpressionTypeInferrer;
import io.quarkiverse.qubit.deployment.generation.GizmoHelper;
import io.quarkiverse.qubit.deployment.ast.LambdaExpression.ExistsSubquery;
import io.quarkiverse.qubit.deployment.ast.LambdaExpression.FieldAccess;
import io.quarkiverse.qubit.deployment.ast.LambdaExpression.InSubquery;
import io.quarkiverse.qubit.deployment.ast.LambdaExpression.PathExpression;
import io.quarkiverse.qubit.deployment.ast.LambdaExpression.PathSegment;
import io.quarkiverse.qubit.deployment.ast.LambdaExpression.ScalarSubquery;
import io.quarkiverse.qubit.deployment.ast.LambdaExpression.SubqueryAggregationType;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.jboss.logging.Logger;

import static io.quarkiverse.qubit.deployment.ast.LambdaExpression.BinaryOp.Operator.AND;
import static io.quarkiverse.qubit.deployment.ast.LambdaExpression.BinaryOp.Operator.OR;
import static io.quarkiverse.qubit.runtime.QubitConstants.CB_NOT;
import static io.quarkiverse.qubit.runtime.QubitConstants.PATH_GET;

/**
 * Generates JPA Criteria API bytecode for subquery expressions.
 *
 * <p>Iteration 8: Subqueries - handles ScalarSubquery, ExistsSubquery, and InSubquery.
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
 */
public class SubqueryExpressionBuilder implements ExpressionBuilder {

    private static final Logger log = Logger.getLogger(SubqueryExpressionBuilder.class);

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

        // Determine the subquery result type based on aggregation
        Class<?> resultType = getAggregationResultType(scalar.aggregationType());

        // Create subquery: query.subquery(resultType)
        ResultHandle resultTypeHandle = method.loadClass(resultType);
        ResultHandle subquery = method.invokeInterfaceMethod(
                MethodDescriptor.ofMethod(CriteriaQuery.class, "subquery", Subquery.class, Class.class),
                query, resultTypeHandle);

        // Create from clause: subquery.from(entityClass)
        ResultHandle entityClassHandle = GizmoHelper.loadEntityClass(method, scalar.entityClass(), scalar.entityClassName());
        ResultHandle subRoot = method.invokeInterfaceMethod(
                MethodDescriptor.ofMethod(Subquery.class, "from", Root.class, Class.class),
                subquery, entityClassHandle);

        // Generate the select expression with aggregation
        ResultHandle aggregation = generateAggregation(method, scalar, cb, subRoot);

        // Set the select: subquery.select(aggregation)
        method.invokeInterfaceMethod(
                MethodDescriptor.ofMethod(Subquery.class, "select", Subquery.class, Expression.class),
                subquery, aggregation);

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

        // Create subquery: query.subquery(entityClass)
        ResultHandle entityClassHandle = GizmoHelper.loadEntityClass(method, exists.entityClass(), exists.entityClassName());
        ResultHandle subquery = method.invokeInterfaceMethod(
                MethodDescriptor.ofMethod(CriteriaQuery.class, "subquery", Subquery.class, Class.class),
                query, entityClassHandle);

        // Create from clause: subquery.from(entityClass)
        ResultHandle subRoot = method.invokeInterfaceMethod(
                MethodDescriptor.ofMethod(Subquery.class, "from", Root.class, Class.class),
                subquery, entityClassHandle);

        // Select the subquery root (EXISTS just needs any selection)
        method.invokeInterfaceMethod(
                MethodDescriptor.ofMethod(Subquery.class, "select", Subquery.class, Expression.class),
                subquery, subRoot);

        // Generate the where clause from the predicate
        ResultHandle wherePredicate = generateSubqueryPredicate(
                method, exists.predicate(), cb, subRoot, outerRoot, capturedValues);
        if (wherePredicate != null) {
            applySubqueryWhere(method, subquery, wherePredicate);
        }

        // Generate cb.exists(subquery)
        ResultHandle existsPredicate = method.invokeInterfaceMethod(
                MethodDescriptor.ofMethod(CriteriaBuilder.class, "exists", Predicate.class, Subquery.class),
                cb, subquery);

        // If negated, wrap with cb.not()
        if (exists.negated()) {
            return method.invokeInterfaceMethod(
                    MethodDescriptor.ofMethod(CriteriaBuilder.class, CB_NOT, Predicate.class, Expression.class),
                    cb, existsPredicate);
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

        // Generate the left-hand field expression from the outer query
        ResultHandle fieldPath = generateFieldPath(method, inSubquery.field(), outerRoot);

        // Determine the result type from the select expression
        Class<?> selectType = ExpressionTypeInferrer.inferFieldType(inSubquery.selectExpression());

        // Create subquery: query.subquery(selectType)
        ResultHandle selectTypeHandle = method.loadClass(selectType);
        ResultHandle subquery = method.invokeInterfaceMethod(
                MethodDescriptor.ofMethod(CriteriaQuery.class, "subquery", Subquery.class, Class.class),
                query, selectTypeHandle);

        // Create from clause: subquery.from(entityClass)
        ResultHandle entityClassHandle = GizmoHelper.loadEntityClass(method, inSubquery.entityClass(), inSubquery.entityClassName());
        ResultHandle subRoot = method.invokeInterfaceMethod(
                MethodDescriptor.ofMethod(Subquery.class, "from", Root.class, Class.class),
                subquery, entityClassHandle);

        // Generate the select expression
        ResultHandle selectExpr = generateFieldPath(method, inSubquery.selectExpression(), subRoot);

        // Set the select: subquery.select(selectExpr)
        method.invokeInterfaceMethod(
                MethodDescriptor.ofMethod(Subquery.class, "select", Subquery.class, Expression.class),
                subquery, selectExpr);

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
                MethodDescriptor.ofMethod(Expression.class, "in", Predicate.class, Expression.class),
                fieldPath, subquery);

        // If negated, wrap with cb.not()
        if (inSubquery.negated()) {
            return method.invokeInterfaceMethod(
                    MethodDescriptor.ofMethod(CriteriaBuilder.class, CB_NOT, Predicate.class, Expression.class),
                    cb, inPredicate);
        }

        return inPredicate;
    }

    /**
     * Returns the JPA result type for an aggregation.
     */
    private Class<?> getAggregationResultType(SubqueryAggregationType aggType) {
        return switch (aggType) {
            case AVG -> Double.class;
            case SUM -> Number.class;  // Could be Integer, Long, Double, etc.
            case MIN, MAX -> Comparable.class;
            case COUNT -> Long.class;
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
            return method.invokeInterfaceMethod(
                    MethodDescriptor.ofMethod(CriteriaBuilder.class, "count", Expression.class, Expression.class),
                    cb, subRoot);
        }

        // Generate the field path from the field expression
        ResultHandle fieldPath = generateFieldPath(method, scalar.fieldExpression(), subRoot);

        // Generate the appropriate aggregation
        return switch (scalar.aggregationType()) {
            case AVG -> method.invokeInterfaceMethod(
                    MethodDescriptor.ofMethod(CriteriaBuilder.class, "avg", Expression.class, Expression.class),
                    cb, fieldPath);
            case SUM -> method.invokeInterfaceMethod(
                    MethodDescriptor.ofMethod(CriteriaBuilder.class, "sum", Expression.class, Expression.class),
                    cb, fieldPath);
            case MIN -> method.invokeInterfaceMethod(
                    MethodDescriptor.ofMethod(CriteriaBuilder.class, "min", Expression.class, Expression.class),
                    cb, fieldPath);
            case MAX -> method.invokeInterfaceMethod(
                    MethodDescriptor.ofMethod(CriteriaBuilder.class, "max", Expression.class, Expression.class),
                    cb, fieldPath);
            case COUNT -> throw new IllegalStateException("COUNT should be handled above");
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
                yield method.invokeInterfaceMethod(
                        MethodDescriptor.ofMethod(Path.class, PATH_GET, Path.class, String.class),
                        root, fieldName);
            }

            case PathExpression pathExpr -> {
                ResultHandle currentPath = root;
                for (PathSegment segment : pathExpr.segments()) {
                    ResultHandle fieldName = method.load(segment.fieldName());
                    currentPath = method.invokeInterfaceMethod(
                            MethodDescriptor.ofMethod(Path.class, PATH_GET, Path.class, String.class),
                            currentPath, fieldName);
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

            // Handle FieldAccess as boolean
            case FieldAccess field -> {
                ResultHandle path = generateFieldPath(method, field, subRoot);
                yield method.invokeInterfaceMethod(
                        MethodDescriptor.ofMethod(CriteriaBuilder.class, "isTrue", Predicate.class, Expression.class),
                        cb, path);
            }

            default -> null;
        };
    }

    /**
     * Generates a binary operation predicate for subquery WHERE clause.
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

            String methodName = binOp.operator() == AND ? "and" : "or";
            return method.invokeInterfaceMethod(
                    MethodDescriptor.ofMethod(CriteriaBuilder.class, methodName, Predicate.class, Predicate[].class),
                    cb, predicateArray);
        }

        // For comparison operators, generate expressions
        ResultHandle left = generateSubqueryExpression(method, binOp.left(), cb, subRoot, outerRoot, capturedValues);
        ResultHandle right = generateSubqueryExpression(method, binOp.right(), cb, subRoot, outerRoot, capturedValues);

        // Generate the appropriate comparison
        return switch (binOp.operator()) {
            case EQ -> method.invokeInterfaceMethod(
                    MethodDescriptor.ofMethod(CriteriaBuilder.class, "equal", Predicate.class, Expression.class, Object.class),
                    cb, left, right);
            case NE -> method.invokeInterfaceMethod(
                    MethodDescriptor.ofMethod(CriteriaBuilder.class, "notEqual", Predicate.class, Expression.class, Object.class),
                    cb, left, right);
            case GT -> method.invokeInterfaceMethod(
                    MethodDescriptor.ofMethod(CriteriaBuilder.class, "greaterThan", Predicate.class, Expression.class, Comparable.class),
                    cb, left, right);
            case GE -> method.invokeInterfaceMethod(
                    MethodDescriptor.ofMethod(CriteriaBuilder.class, "greaterThanOrEqualTo", Predicate.class, Expression.class, Comparable.class),
                    cb, left, right);
            case LT -> method.invokeInterfaceMethod(
                    MethodDescriptor.ofMethod(CriteriaBuilder.class, "lessThan", Predicate.class, Expression.class, Comparable.class),
                    cb, left, right);
            case LE -> method.invokeInterfaceMethod(
                    MethodDescriptor.ofMethod(CriteriaBuilder.class, "lessThanOrEqualTo", Predicate.class, Expression.class, Comparable.class),
                    cb, left, right);
            default -> null;
        };
    }

    /**
     * Generates an expression for use in subquery predicates.
     *
     * <p>This handles both subquery-local expressions and correlated references
     * to the outer query.
     *
     * @param method the method creator for bytecode generation
     * @param expr the expression to generate
     * @param cb the CriteriaBuilder handle
     * @param subRoot the subquery root handle
     * @param outerRoot the outer query root handle (for correlated subqueries)
     * @param capturedValues the captured values array handle
     * @return ResultHandle for the generated expression, or null if expr is null
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

        // Java 21 pattern matching switch for type dispatch
        return switch (expr) {
            case FieldAccess field ->
                // Default to subquery root for simple field access
                generateFieldPath(method, field, subRoot);

            case PathExpression pathExpr ->
                generateFieldPath(method, pathExpr, subRoot);

            case LambdaExpression.CorrelatedVariable correlated ->
                // Correlated reference - use the outer root
                generateFieldPath(method, correlated.fieldExpression(), outerRoot);

            case LambdaExpression.Constant constant ->
                generateConstant(method, constant);

            case LambdaExpression.CapturedVariable capturedVar -> {
                ResultHandle index = method.load(capturedVar.index());
                ResultHandle value = method.readArrayValue(capturedValues, index);
                yield method.checkCast(value, Object.class);
            }

            default -> {
                log.warnf("Unhandled expression type in generateSubqueryExpression: %s. "
                        + "This may indicate a missing case handler or an unexpected AST structure.",
                        expr.getClass().getSimpleName());
                yield null;
            }
        };
    }

    /**
     * Generates a constant value.
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
            default -> method.loadNull();
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
        method.invokeInterfaceMethod(
                MethodDescriptor.ofMethod(Subquery.class, "where", Subquery.class, Predicate[].class),
                subquery, predicateArray);
    }
}
