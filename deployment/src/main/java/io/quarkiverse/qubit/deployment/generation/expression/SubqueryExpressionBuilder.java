package io.quarkiverse.qubit.deployment.generation.expression;

import static io.quarkiverse.qubit.deployment.ast.LambdaExpression.BinaryOp.Operator.AND;
import static io.quarkiverse.qubit.deployment.common.ExceptionMessages.COUNT_SHOULD_BE_HANDLED_ABOVE;
import static io.quarkiverse.qubit.deployment.common.ExceptionMessages.EXISTS_SUBQUERY_NULL;
import static io.quarkiverse.qubit.deployment.common.ExceptionMessages.FIELD_PATH_EXPRESSION_NULL;
import static io.quarkiverse.qubit.deployment.common.ExceptionMessages.IN_SUBQUERY_NULL;
import static io.quarkiverse.qubit.deployment.common.ExceptionMessages.MISSING_CASE_HANDLER_HINT;
import static io.quarkiverse.qubit.deployment.common.ExceptionMessages.SCALAR_SUBQUERY_NULL;
import static io.quarkiverse.qubit.deployment.common.ExceptionMessages.unexpectedAggregationType;
import static io.quarkiverse.qubit.deployment.common.ExceptionMessages.unsupportedFieldPathExpressionType;
import static io.quarkiverse.qubit.deployment.common.PatternDetector.isLogicalOperation;
import static io.quarkiverse.qubit.deployment.common.ExpressionTypeInferrer.isGetterMethodName;
import static io.quarkiverse.qubit.deployment.generation.MethodDescriptors.*;
import static io.quarkiverse.qubit.runtime.internal.QubitConstants.METHOD_EQUALS;

import io.quarkiverse.qubit.deployment.ast.LambdaExpression;
import io.quarkiverse.qubit.deployment.common.OperatorMethodMapper;
import io.quarkiverse.qubit.deployment.ast.LambdaExpression.ExistsSubquery;
import io.quarkiverse.qubit.deployment.ast.LambdaExpression.FieldAccess;
import io.quarkiverse.qubit.deployment.ast.LambdaExpression.InSubquery;
import io.quarkiverse.qubit.deployment.ast.LambdaExpression.PathExpression;
import io.quarkiverse.qubit.deployment.ast.LambdaExpression.PathSegment;
import io.quarkiverse.qubit.deployment.ast.LambdaExpression.ScalarSubquery;
import io.quarkiverse.qubit.deployment.ast.LambdaExpression.SubqueryAggregationType;
import io.quarkiverse.qubit.deployment.common.ExpressionTypeInferrer;
import io.quarkiverse.qubit.deployment.generation.GizmoHelper;
import io.quarkus.gizmo2.Const;
import io.quarkus.gizmo2.Expr;
import io.quarkus.gizmo2.LocalVar;
import io.quarkus.gizmo2.creator.BlockCreator;
import io.quarkus.gizmo2.desc.MethodDesc;
import io.quarkus.logging.Log;
import jakarta.persistence.criteria.Predicate;

/** Generates JPA bytecode for subquery expressions (ScalarSubquery, ExistsSubquery, InSubquery). */
public enum SubqueryExpressionBuilder implements ExpressionBuilder {
    INSTANCE;

    /** Generates JPA scalar aggregation subquery. */
    public Expr buildScalarSubquery(
            BlockCreator bc,
            ScalarSubquery scalar,
            Expr cb,
            Expr query,
            Expr outerRoot,
            Expr capturedValues) {

        if (scalar == null) {
            throw new IllegalArgumentException(SCALAR_SUBQUERY_NULL);
        }

        // Determine the subquery result type based on aggregation
        Class<?> resultType = getAggregationResultType(scalar.aggregationType());

        // Create subquery: query.subquery(resultType)
        // Use LocalVar for values used across multiple operations (Gizmo2 requirement)
        Expr resultTypeHandle = Const.of(resultType);
        LocalVar subquery = bc.localVar("subquery", bc.invokeInterface(
                CRITERIA_QUERY_SUBQUERY, query, resultTypeHandle));

        // Create from clause: subquery.from(entityClass)
        Expr entityClassHandle = GizmoHelper.loadEntityClass(bc, scalar.entityClass(), scalar.entityClassName());
        LocalVar subRoot = bc.localVar("subRoot", bc.invokeInterface(
                SUBQUERY_FROM, subquery, entityClassHandle));

        // Generate the select expression with aggregation
        Expr aggregation = generateAggregation(bc, scalar, cb, subRoot);

        // Set the select: subquery.select(aggregation)
        bc.invokeInterface(SUBQUERY_SELECT, subquery, aggregation);

        // Generate the where clause if there's a predicate
        if (scalar.hasPredicate()) {
            Expr wherePredicate = generateSubqueryPredicate(
                    bc, scalar.predicate(), cb, subRoot, outerRoot, capturedValues);
            if (wherePredicate != null) {
                applySubqueryWhere(bc, subquery, wherePredicate);
            }
        }

        return subquery;
    }

    /** Generates JPA EXISTS subquery predicate. */
    public Expr buildExistsSubquery(
            BlockCreator bc,
            ExistsSubquery exists,
            Expr cb,
            Expr query,
            Expr outerRoot,
            Expr capturedValues) {

        if (exists == null) {
            throw new IllegalArgumentException(EXISTS_SUBQUERY_NULL);
        }

        // Create subquery: query.subquery(entityClass)
        // Use LocalVar for values used across multiple operations (Gizmo2 requirement)
        LocalVar entityClassHandle = bc.localVar("entityClass",
                GizmoHelper.loadEntityClass(bc, exists.entityClass(), exists.entityClassName()));
        LocalVar subquery = bc.localVar("subquery", bc.invokeInterface(
                CRITERIA_QUERY_SUBQUERY, query, entityClassHandle));

        // Create from clause: subquery.from(entityClass)
        LocalVar subRoot = bc.localVar("subRoot", bc.invokeInterface(
                SUBQUERY_FROM, subquery, entityClassHandle));

        // Select the subquery root (EXISTS just needs any selection)
        bc.invokeInterface(SUBQUERY_SELECT, subquery, subRoot);

        // Generate the where clause from the predicate
        Expr wherePredicate = generateSubqueryPredicate(
                bc, exists.predicate(), cb, subRoot, outerRoot, capturedValues);
        if (wherePredicate != null) {
            applySubqueryWhere(bc, subquery, wherePredicate);
        }

        // Generate cb.exists(subquery)
        Expr existsPredicate = bc.invokeInterface(CB_EXISTS, cb, subquery);

        // If negated, wrap with cb.not()
        if (exists.negated()) {
            return bc.invokeInterface(CB_NOT, cb, existsPredicate);
        }

        return existsPredicate;
    }

    /** Generates JPA IN subquery predicate. */
    public Expr buildInSubquery(
            BlockCreator bc,
            InSubquery inSubquery,
            Expr cb,
            Expr query,
            Expr outerRoot,
            Expr capturedValues) {

        if (inSubquery == null) {
            throw new IllegalArgumentException(IN_SUBQUERY_NULL);
        }

        // Generate the left-hand field expression from the outer query
        // Use LocalVar for values used across multiple operations (Gizmo2 requirement)
        LocalVar fieldPath = bc.localVar("fieldPath", generateFieldPath(bc, inSubquery.field(), outerRoot));

        // Determine the result type from the select expression
        Class<?> selectType = ExpressionTypeInferrer.inferFieldType(inSubquery.selectExpression());

        // Create subquery: query.subquery(selectType)
        Expr selectTypeHandle = Const.of(selectType);
        LocalVar subquery = bc.localVar("subquery", bc.invokeInterface(
                CRITERIA_QUERY_SUBQUERY, query, selectTypeHandle));

        // Create from clause: subquery.from(entityClass)
        Expr entityClassHandle = GizmoHelper.loadEntityClass(bc, inSubquery.entityClass(), inSubquery.entityClassName());
        LocalVar subRoot = bc.localVar("subRoot", bc.invokeInterface(
                SUBQUERY_FROM, subquery, entityClassHandle));

        // Generate the select expression
        Expr selectExpr = generateFieldPath(bc, inSubquery.selectExpression(), subRoot);

        // Set the select: subquery.select(selectExpr)
        bc.invokeInterface(SUBQUERY_SELECT, subquery, selectExpr);

        // Generate the where clause if there's a predicate
        if (inSubquery.hasPredicate()) {
            Expr wherePredicate = generateSubqueryPredicate(
                    bc, inSubquery.predicate(), cb, subRoot, outerRoot, capturedValues);
            if (wherePredicate != null) {
                applySubqueryWhere(bc, subquery, wherePredicate);
            }
        }

        // Generate fieldPath.in(subquery)
        Expr inPredicate = bc.invokeInterface(
                EXPRESSION_IN, fieldPath, subquery);

        // If negated, wrap with cb.not()
        if (inSubquery.negated()) {
            return bc.invokeInterface(CB_NOT, cb, inPredicate);
        }

        return inPredicate;
    }

    private Class<?> getAggregationResultType(SubqueryAggregationType aggType) {
        return switch (aggType) {
            case AVG -> Double.class;
            case SUM -> Number.class;
            case MIN, MAX -> Comparable.class;
            case COUNT -> Long.class;
            default -> throw new IllegalStateException(unexpectedAggregationType(aggType));
        };
    }

    private Expr generateAggregation(
            BlockCreator bc,
            ScalarSubquery scalar,
            Expr cb,
            Expr subRoot) {

        // For COUNT, we don't need a field expression
        if (scalar.isCount()) {
            return bc.invokeInterface(CB_COUNT, cb, subRoot);
        }

        // Generate the field path from the field expression
        Expr fieldPath = generateFieldPath(bc, scalar.fieldExpression(), subRoot);

        // Generate the appropriate aggregation
        return switch (scalar.aggregationType()) {
            case AVG -> bc.invokeInterface(CB_AVG, cb, fieldPath);
            case SUM -> bc.invokeInterface(CB_SUM, cb, fieldPath);
            case MIN -> bc.invokeInterface(CB_MIN, cb, fieldPath);
            case MAX -> bc.invokeInterface(CB_MAX, cb, fieldPath);
            case COUNT -> throw new IllegalStateException(COUNT_SHOULD_BE_HANDLED_ABOVE);
            default -> throw new IllegalStateException(unexpectedAggregationType(scalar.aggregationType()));
        };
    }

    private Expr generateFieldPath(BlockCreator bc, LambdaExpression expr, Expr root) {
        if (expr == null) {
            throw new IllegalArgumentException(FIELD_PATH_EXPRESSION_NULL);
        }
        return switch (expr) {
            case FieldAccess field -> {
                Expr fieldName = Const.of(field.fieldName());
                yield bc.invokeInterface(PATH_GET, root, fieldName);
            }

            case PathExpression pathExpr -> {
                // Use LocalVar for intermediate path values used across loop iterations (Gizmo2 requirement)
                LocalVar currentPath = bc.localVar("currentPath", root);
                for (PathSegment segment : pathExpr.segments()) {
                    Expr fieldName = Const.of(segment.fieldName());
                    bc.set(currentPath, bc.invokeInterface(PATH_GET, currentPath, fieldName));
                }
                yield currentPath;
            }

            default -> throw new IllegalArgumentException(
                    unsupportedFieldPathExpressionType(expr.getClass().getSimpleName()));
        };
    }

    private Expr generateSubqueryPredicate(
            BlockCreator bc,
            LambdaExpression predicate,
            Expr cb,
            Expr subRoot,
            Expr outerRoot,
            Expr capturedValues) {

        if (predicate == null) {
            return null;
        }

        return switch (predicate) {
            // Handle BinaryOp predicates (most common case)
            case LambdaExpression.BinaryOp binOp ->
                generateBinaryOpPredicate(bc, binOp, cb, subRoot, outerRoot, capturedValues);

            case LambdaExpression.UnaryOp unaryOp -> {
                Expr operand = generateSubqueryPredicate(
                        bc, unaryOp.operand(), cb, subRoot, outerRoot, capturedValues);
                yield switch (unaryOp.operator()) {
                    case NOT -> bc.invokeInterface(CB_NOT, cb, operand);
                };
            }

            case LambdaExpression.MethodCall methodCall ->
                generateMethodCallPredicate(bc, methodCall, cb, subRoot, outerRoot, capturedValues);

            // Handle FieldAccess as boolean
            case FieldAccess field -> {
                Expr path = generateFieldPath(bc, field, subRoot);
                yield bc.invokeInterface(CB_IS_TRUE, cb, path);
            }

            // Handle PathExpression as boolean
            case PathExpression pathExpr -> {
                Expr path = generateFieldPath(bc, pathExpr, subRoot);
                yield bc.invokeInterface(CB_IS_TRUE, cb, path);
            }

            default -> {
                Log.warnf("Unhandled predicate type in generateSubqueryPredicate: %s. %s",
                        predicate.getClass().getSimpleName(), MISSING_CASE_HANDLER_HINT);
                yield null;
            }
        };
    }

    private Expr generateMethodCallPredicate(
            BlockCreator bc,
            LambdaExpression.MethodCall methodCall,
            Expr cb,
            Expr subRoot,
            Expr outerRoot,
            Expr capturedValues) {

        // Handle .equals() method calls -> cb.equal()
        if (METHOD_EQUALS.equals(methodCall.methodName()) && !methodCall.arguments().isEmpty()) {
            // Generate expression for the target (e.g., ph.owner.id)
            Expr targetExpr = generateSubqueryExpression(
                    bc, methodCall.target(), cb, subRoot, outerRoot, capturedValues);

            // Generate expression for the argument (e.g., p.id which may be a CorrelatedVariable)
            Expr argumentExpr = generateSubqueryExpression(
                    bc, methodCall.arguments().getFirst(), cb, subRoot, outerRoot, capturedValues);

            // Generate cb.equal(target, argument)
            return bc.invokeInterface(CB_EQUAL, cb, targetExpr, argumentExpr);
        }

        Log.warnf("Unhandled method call in subquery predicate: %s. %s",
                methodCall.methodName(), MISSING_CASE_HANDLER_HINT);
        return null;
    }

    private Expr generateBinaryOpPredicate(
            BlockCreator bc,
            LambdaExpression.BinaryOp binOp,
            Expr cb,
            Expr subRoot,
            Expr outerRoot,
            Expr capturedValues) {

        // Handle logical operators (AND/OR) differently - they need predicates
        if (isLogicalOperation(binOp)) {
            // Recursively generate predicates for both sides
            Expr leftPredicate = generateSubqueryPredicate(bc, binOp.left(), cb, subRoot, outerRoot, capturedValues);
            Expr rightPredicate = generateSubqueryPredicate(bc, binOp.right(), cb, subRoot, outerRoot, capturedValues);

            Expr predicateArray = GizmoHelper.createElementArray(bc, Predicate.class, leftPredicate, rightPredicate);

            MethodDesc cbOperator = binOp.operator() == AND ? CB_AND : CB_OR;
            return bc.invokeInterface(cbOperator, cb, predicateArray);
        }

        // For comparison operators, generate expressions
        Expr left = generateSubqueryExpression(bc, binOp.left(), cb, subRoot, outerRoot, capturedValues);
        Expr right = generateSubqueryExpression(bc, binOp.right(), cb, subRoot, outerRoot, capturedValues);

        // Generate the appropriate comparison
        if (OperatorMethodMapper.isComparisonOperator(binOp.operator())) {
            var comparisonMethod = OperatorMethodMapper.mapComparisonOperator(binOp.operator(), false);
            return bc.invokeInterface(comparisonMethod, cb, left, right);
        }
        return null;
    }

    private Expr generateSubqueryExpression(
            BlockCreator bc,
            LambdaExpression expr,
            Expr cb,
            Expr subRoot,
            Expr outerRoot,
            Expr capturedValues) {

        if (expr == null) {
            return null;
        }

        return switch (expr) {
            case FieldAccess field ->
                generateFieldPath(bc, field, subRoot);
            case PathExpression pathExpr ->
                generateFieldPath(bc, pathExpr, subRoot);
            case LambdaExpression.CorrelatedVariable correlated ->
                generateFieldPath(bc, correlated.fieldExpression(), outerRoot);
            case LambdaExpression.Constant constant ->
                generateConstant(bc, constant);
            case LambdaExpression.CapturedVariable capturedVar ->
                // Array element already returns Object type, no cast needed
                capturedValues.elem(capturedVar.index());

            // The subquery parameter refers to the subquery root
            case LambdaExpression.Parameter _ -> subRoot;

            case LambdaExpression.MethodCall methodCall ->
                generateMethodCallExpression(bc, methodCall, cb, subRoot, outerRoot, capturedValues);

            // Handle BinaryOp expressions
            // For comparison operators (EQ, NE, GT, GE, LT, LE), use generateBinaryOpPredicate
            // since Predicate extends Expression<Boolean>. This handles nested predicates like:
            // BinaryOp[BinaryOp[path, EQ, field], EQ, Constant[true]]
            // For arithmetic operators (ADD, SUB, MUL, DIV), use generateBinaryOpExpression.
            case LambdaExpression.BinaryOp binOp -> {
                if (isComparisonOrLogicalOperator(binOp.operator())) {
                    yield generateBinaryOpPredicate(bc, binOp, cb, subRoot, outerRoot, capturedValues);
                } else {
                    yield generateBinaryOpExpression(bc, binOp, cb, subRoot, outerRoot, capturedValues);
                }
            }

            default -> {
                Log.warnf("Unhandled expression type in generateSubqueryExpression: %s. %s",
                        expr.getClass().getSimpleName(), MISSING_CASE_HANDLER_HINT);
                yield null;
            }
        };
    }

    private Expr generateMethodCallExpression(
            BlockCreator bc,
            LambdaExpression.MethodCall methodCall,
            Expr cb,
            Expr subRoot,
            Expr outerRoot,
            Expr capturedValues) {

        String methodName = methodCall.methodName();

        // Handle getter methods (getXxx, isXxx) -> path navigation
        if (isGetterMethodName(methodName)) {
            // First, resolve the target expression
            Expr targetPath = generateSubqueryExpression(
                    bc, methodCall.target(), cb, subRoot, outerRoot, capturedValues);

            // Extract field name from getter (getOwner -> owner, isActive -> active)
            String fieldName = ExpressionTypeInferrer.extractFieldName(methodName);
            Expr fieldNameHandle = Const.of(fieldName);

            // Generate path.get(fieldName)
            return bc.invokeInterface(PATH_GET, targetPath, fieldNameHandle);
        }

        Log.warnf("Unhandled method call expression in subquery: %s. %s",
                methodName, MISSING_CASE_HANDLER_HINT);
        return null;
    }

    private Expr generateBinaryOpExpression(
            BlockCreator bc,
            LambdaExpression.BinaryOp binOp,
            Expr cb,
            Expr subRoot,
            Expr outerRoot,
            Expr capturedValues) {

        Expr left = generateSubqueryExpression(bc, binOp.left(), cb, subRoot, outerRoot, capturedValues);
        Expr right = generateSubqueryExpression(bc, binOp.right(), cb, subRoot, outerRoot, capturedValues);

        // Generate the appropriate arithmetic operation
        if (OperatorMethodMapper.isArithmeticOperator(binOp.operator())) {
            var arithmeticMethod = OperatorMethodMapper.mapArithmeticOperator(binOp.operator());
            return bc.invokeInterface(arithmeticMethod, cb, left, right);
        }
        Log.warnf("Unhandled binary operator in subquery expression: %s", binOp.operator());
        return null;
    }

    private boolean isComparisonOrLogicalOperator(LambdaExpression.BinaryOp.Operator operator) {
        return switch (operator) {
            case EQ, NE, GT, GE, LT, LE, AND, OR -> true;
            case ADD, SUB, MUL, DIV, MOD -> false;
        };
    }

    private Expr generateConstant(BlockCreator bc, LambdaExpression.Constant constant) {
        return GizmoHelper.loadConstant(bc, constant.value());
    }

    /** JPA's Subquery.where() takes Predicate... varargs. */
    private void applySubqueryWhere(BlockCreator bc, Expr subquery, Expr predicate) {
        Expr predicateArray = GizmoHelper.createElementArray(bc, Predicate.class, predicate);
        bc.invokeInterface(SUBQUERY_WHERE, subquery, predicateArray);
    }
}
