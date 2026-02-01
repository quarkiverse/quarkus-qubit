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
import io.quarkus.gizmo.MethodCreator;
import io.quarkus.gizmo.MethodDescriptor;
import io.quarkus.gizmo.ResultHandle;
import io.quarkus.logging.Log;
import jakarta.persistence.criteria.Predicate;

/** Generates JPA bytecode for subquery expressions (ScalarSubquery, ExistsSubquery, InSubquery). */
public enum SubqueryExpressionBuilder implements ExpressionBuilder {
    INSTANCE;

    /** Generates JPA scalar aggregation subquery. */
    public ResultHandle buildScalarSubquery(
            MethodCreator method,
            ScalarSubquery scalar,
            ResultHandle cb,
            ResultHandle query,
            ResultHandle outerRoot,
            ResultHandle capturedValues) {

        if (scalar == null) {
            throw new IllegalArgumentException(SCALAR_SUBQUERY_NULL);
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

    /** Generates JPA EXISTS subquery predicate. */
    public ResultHandle buildExistsSubquery(
            MethodCreator method,
            ExistsSubquery exists,
            ResultHandle cb,
            ResultHandle query,
            ResultHandle outerRoot,
            ResultHandle capturedValues) {

        if (exists == null) {
            throw new IllegalArgumentException(EXISTS_SUBQUERY_NULL);
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

    /** Generates JPA IN subquery predicate. */
    public ResultHandle buildInSubquery(
            MethodCreator method,
            InSubquery inSubquery,
            ResultHandle cb,
            ResultHandle query,
            ResultHandle outerRoot,
            ResultHandle capturedValues) {

        if (inSubquery == null) {
            throw new IllegalArgumentException(IN_SUBQUERY_NULL);
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

    private Class<?> getAggregationResultType(SubqueryAggregationType aggType) {
        return switch (aggType) {
            case AVG -> Double.class;
            case SUM -> Number.class;
            case MIN, MAX -> Comparable.class;
            case COUNT -> Long.class;
            default -> throw new IllegalStateException(unexpectedAggregationType(aggType));
        };
    }

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
        return switch (scalar.aggregationType()) {
            case AVG -> method.invokeInterfaceMethod(CB_AVG, cb, fieldPath);
            case SUM -> method.invokeInterfaceMethod(CB_SUM, cb, fieldPath);
            case MIN -> method.invokeInterfaceMethod(CB_MIN, cb, fieldPath);
            case MAX -> method.invokeInterfaceMethod(CB_MAX, cb, fieldPath);
            case COUNT -> throw new IllegalStateException(COUNT_SHOULD_BE_HANDLED_ABOVE);
            default -> throw new IllegalStateException(unexpectedAggregationType(scalar.aggregationType()));
        };
    }

    private ResultHandle generateFieldPath(MethodCreator method, LambdaExpression expr, ResultHandle root) {
        if (expr == null) {
            throw new IllegalArgumentException(FIELD_PATH_EXPRESSION_NULL);
        }
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
                    unsupportedFieldPathExpressionType(expr.getClass().getSimpleName()));
        };
    }

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

        return switch (predicate) {
            // Handle BinaryOp predicates (most common case)
            case LambdaExpression.BinaryOp binOp ->
                generateBinaryOpPredicate(method, binOp, cb, subRoot, outerRoot, capturedValues);

            case LambdaExpression.UnaryOp unaryOp -> {
                ResultHandle operand = generateSubqueryPredicate(
                        method, unaryOp.operand(), cb, subRoot, outerRoot, capturedValues);
                yield switch (unaryOp.operator()) {
                    case NOT -> method.invokeInterfaceMethod(CB_NOT, cb, operand);
                };
            }

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
                Log.warnf("Unhandled predicate type in generateSubqueryPredicate: %s. %s",
                        predicate.getClass().getSimpleName(), MISSING_CASE_HANDLER_HINT);
                yield null;
            }
        };
    }

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
                    method, methodCall.arguments().getFirst(), cb, subRoot, outerRoot, capturedValues);

            // Generate cb.equal(target, argument)
            return method.invokeInterfaceMethod(CB_EQUAL, cb, targetExpr, argumentExpr);
        }

        Log.warnf("Unhandled method call in subquery predicate: %s. %s",
                methodCall.methodName(), MISSING_CASE_HANDLER_HINT);
        return null;
    }

    private ResultHandle generateBinaryOpPredicate(
            MethodCreator method,
            LambdaExpression.BinaryOp binOp,
            ResultHandle cb,
            ResultHandle subRoot,
            ResultHandle outerRoot,
            ResultHandle capturedValues) {

        // Handle logical operators (AND/OR) differently - they need predicates
        if (isLogicalOperation(binOp)) {
            // Recursively generate predicates for both sides
            ResultHandle leftPredicate = generateSubqueryPredicate(method, binOp.left(), cb, subRoot, outerRoot, capturedValues);
            ResultHandle rightPredicate = generateSubqueryPredicate(method, binOp.right(), cb, subRoot, outerRoot, capturedValues);

            ResultHandle predicateArray = GizmoHelper.createElementArray(method, Predicate.class, leftPredicate, rightPredicate);

            MethodDescriptor cbOperator = binOp.operator() == AND ? CB_AND : CB_OR;
            return method.invokeInterfaceMethod(cbOperator, cb, predicateArray);
        }

        // For comparison operators, generate expressions
        ResultHandle left = generateSubqueryExpression(method, binOp.left(), cb, subRoot, outerRoot, capturedValues);
        ResultHandle right = generateSubqueryExpression(method, binOp.right(), cb, subRoot, outerRoot, capturedValues);

        // Generate the appropriate comparison
        if (OperatorMethodMapper.isComparisonOperator(binOp.operator())) {
            var comparisonMethod = OperatorMethodMapper.mapComparisonOperator(binOp.operator(), false);
            return method.invokeInterfaceMethod(comparisonMethod, cb, left, right);
        }
        return null;
    }

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

            // The subquery parameter refers to the subquery root
            case LambdaExpression.Parameter _ -> subRoot;

            case LambdaExpression.MethodCall methodCall ->
                generateMethodCallExpression(method, methodCall, cb, subRoot, outerRoot, capturedValues);

            // Handle BinaryOp expressions
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
                Log.warnf("Unhandled expression type in generateSubqueryExpression: %s. %s",
                        expr.getClass().getSimpleName(), MISSING_CASE_HANDLER_HINT);
                yield null;
            }
        };
    }

    private ResultHandle generateMethodCallExpression(
            MethodCreator method,
            LambdaExpression.MethodCall methodCall,
            ResultHandle cb,
            ResultHandle subRoot,
            ResultHandle outerRoot,
            ResultHandle capturedValues) {

        String methodName = methodCall.methodName();

        // Handle getter methods (getXxx, isXxx) → path navigation
        if (isGetterMethodName(methodName)) {
            // First, resolve the target expression
            ResultHandle targetPath = generateSubqueryExpression(
                    method, methodCall.target(), cb, subRoot, outerRoot, capturedValues);

            // Extract field name from getter (getOwner → owner, isActive → active)
            String fieldName = ExpressionTypeInferrer.extractFieldName(methodName);
            ResultHandle fieldNameHandle = method.load(fieldName);

            // Generate path.get(fieldName)
            return method.invokeInterfaceMethod(PATH_GET, targetPath, fieldNameHandle);
        }

        Log.warnf("Unhandled method call expression in subquery: %s. %s",
                methodName, MISSING_CASE_HANDLER_HINT);
        return null;
    }

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
        if (OperatorMethodMapper.isArithmeticOperator(binOp.operator())) {
            var arithmeticMethod = OperatorMethodMapper.mapArithmeticOperator(binOp.operator());
            return method.invokeInterfaceMethod(arithmeticMethod, cb, left, right);
        }
        Log.warnf("Unhandled binary operator in subquery expression: %s", binOp.operator());
        return null;
    }

    private boolean isComparisonOrLogicalOperator(LambdaExpression.BinaryOp.Operator operator) {
        return switch (operator) {
            case EQ, NE, GT, GE, LT, LE, AND, OR -> true;
            case ADD, SUB, MUL, DIV -> false;
            default -> false;  // Future operators default to arithmetic handling
        };
    }

    private ResultHandle generateConstant(MethodCreator method, LambdaExpression.Constant constant) {
        return GizmoHelper.loadConstant(method, constant.value());
    }

    /** JPA's Subquery.where() takes Predicate... varargs. */
    private void applySubqueryWhere(MethodCreator method, ResultHandle subquery, ResultHandle predicate) {
        ResultHandle predicateArray = GizmoHelper.createElementArray(method, Predicate.class, predicate);
        method.invokeInterfaceMethod(SUBQUERY_WHERE, subquery, predicateArray);
    }
}
