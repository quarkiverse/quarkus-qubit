package io.quarkiverse.qubit.deployment.generation.expression;

import static io.quarkiverse.qubit.deployment.common.ExceptionMessages.COUNT_SHOULD_BE_HANDLED_ABOVE;
import static io.quarkiverse.qubit.deployment.common.ExceptionMessages.unexpectedGroupAggregationType;
import static io.quarkiverse.qubit.deployment.common.PatternDetector.isLogicalOperation;
import static io.quarkiverse.qubit.deployment.generation.GizmoHelper.buildConstructorExpression;
import static io.quarkiverse.qubit.deployment.generation.MethodDescriptors.CB_AVG;
import static io.quarkiverse.qubit.deployment.generation.MethodDescriptors.CB_COUNT;
import static io.quarkiverse.qubit.deployment.generation.MethodDescriptors.CB_COUNT_DISTINCT;
import static io.quarkiverse.qubit.deployment.generation.MethodDescriptors.CB_MAX;
import static io.quarkiverse.qubit.deployment.generation.MethodDescriptors.CB_MIN;
import static io.quarkiverse.qubit.deployment.generation.MethodDescriptors.CB_NOT;
import static io.quarkiverse.qubit.deployment.generation.MethodDescriptors.CB_SUM;
import static io.quarkiverse.qubit.deployment.generation.MethodDescriptors.CB_SUM_AS_DOUBLE;
import static io.quarkiverse.qubit.deployment.generation.MethodDescriptors.CB_SUM_AS_LONG;
import static io.quarkiverse.qubit.deployment.generation.MethodDescriptors.CB_TUPLE;

import io.quarkus.gizmo.MethodCreator;
import io.quarkus.gizmo.ResultHandle;
import io.quarkiverse.qubit.deployment.ast.LambdaExpression;
import io.quarkiverse.qubit.deployment.ast.LambdaExpression.GroupAggregation;
import io.quarkiverse.qubit.deployment.ast.LambdaExpression.GroupAggregationType;
import io.quarkiverse.qubit.deployment.ast.LambdaExpression.GroupKeyReference;
import io.quarkiverse.qubit.deployment.ast.LambdaExpression.PathExpression;
import io.quarkiverse.qubit.deployment.common.PatternDetector;
import jakarta.persistence.criteria.Selection;

/** Builds JPA Criteria expressions for GROUP BY queries. */
public enum GroupExpressionBuilder implements ExpressionBuilder {
    INSTANCE;

    /** Generates JPA Predicate for HAVING clause. */
    public ResultHandle generateGroupPredicate(
            MethodCreator method,
            LambdaExpression expression,
            ResultHandle cb,
            ResultHandle root,
            ResultHandle groupKeyExpr,
            ResultHandle capturedValues,
            ExpressionGeneratorHelper helper) {

        if (expression == null) {
            return null;
        }

        return switch (expression) {
            case LambdaExpression.BinaryOp binOp ->
                generateGroupBinaryOperation(method, binOp, cb, root, groupKeyExpr, capturedValues, helper);

            case LambdaExpression.UnaryOp unOp ->
                generateGroupUnaryOperation(method, unOp, cb, root, groupKeyExpr, capturedValues, helper);

            case GroupAggregation groupAgg ->
                // Aggregation used as a boolean predicate (rare, but possible)
                generateGroupAggregationExpression(method, groupAgg, cb, root, capturedValues, helper);

            case GroupKeyReference ignored ->
                // Key reference as boolean (if key is boolean type)
                groupKeyExpr;

            default -> null;
        };
    }

    /** Generates JPA Expression for GROUP BY SELECT. */
    public ResultHandle generateGroupSelectExpression(
            MethodCreator method,
            LambdaExpression expression,
            ResultHandle cb,
            ResultHandle root,
            ResultHandle groupKeyExpr,
            ResultHandle capturedValues,
            ExpressionGeneratorHelper helper) {

        if (expression == null) {
            return null;
        }

        return switch (expression) {
            case GroupKeyReference ignored ->
                // g.key() -> use the pre-computed grouping key expression
                groupKeyExpr;

            case GroupAggregation groupAgg ->
                // g.count(), g.avg(), etc. -> generate aggregation expression
                generateGroupAggregationExpression(method, groupAgg, cb, root, capturedValues, helper);

            case LambdaExpression.ArrayCreation arrayCreation ->
                // Object[] projection using cb.tuple()
                generateGroupArrayCreation(method, arrayCreation, cb, root, groupKeyExpr, capturedValues, helper);

            case LambdaExpression.ConstructorCall constructorCall ->
                // DTO constructor with group elements
                generateGroupConstructorCall(method, constructorCall, cb, root, groupKeyExpr, capturedValues, helper);

            case LambdaExpression.FieldAccess field ->
                // Field access in group context (from nested lambda in aggregation)
                helper.generateFieldAccess(method, field, root);

            case PathExpression pathExpr ->
                helper.generatePathExpression(method, pathExpr, root);

            case LambdaExpression.Constant constant -> {
                ResultHandle constantValue = helper.generateConstant(method, constant);
                yield helper.wrapAsLiteral(method, cb, constantValue);
            }

            case LambdaExpression.CapturedVariable capturedVar ->
                helper.loadAndWrapCapturedValue(method, cb, capturedVar, capturedValues);

            case LambdaExpression.BinaryOp binOp ->
                generateGroupBinaryOperation(method, binOp, cb, root, groupKeyExpr, capturedValues, helper);

            default -> null;
        };
    }

    /** Generates JPA Expression for group ORDER BY clause. */
    public ResultHandle generateGroupSortExpression(
            MethodCreator method,
            LambdaExpression expression,
            ResultHandle cb,
            ResultHandle root,
            ResultHandle groupKeyExpr,
            ResultHandle capturedValues,
            ExpressionGeneratorHelper helper) {

        // Delegate to generateGroupSelectExpression since they handle the same types
        return generateGroupSelectExpression(method, expression, cb, root, groupKeyExpr, capturedValues, helper);
    }

    /** Generates JPA multiselect array for Object[] projections. */
    public ResultHandle generateGroupArraySelections(
            MethodCreator method,
            LambdaExpression.ArrayCreation arrayCreation,
            ResultHandle cb,
            ResultHandle root,
            ResultHandle groupKeyExpr,
            ResultHandle capturedValues,
            ExpressionGeneratorHelper helper) {

        int elementCount = arrayCreation.elements().size();
        ResultHandle selectionsArray = method.newArray(Selection.class, elementCount);

        for (int i = 0; i < elementCount; i++) {
            LambdaExpression element = arrayCreation.elements().get(i);
            ResultHandle elementSelection = generateGroupSelectExpression(
                    method, element, cb, root, groupKeyExpr, capturedValues, helper);
            method.writeArrayValue(selectionsArray, i, elementSelection);
        }

        return selectionsArray;
    }

    // ========== Private Helper Methods ==========

    private ResultHandle generateGroupAggregationExpression(
            MethodCreator method,
            GroupAggregation groupAgg,
            ResultHandle cb,
            ResultHandle root,
            ResultHandle capturedValues,
            ExpressionGeneratorHelper helper) {

        GroupAggregationType aggType = groupAgg.aggregationType();

        // Handle COUNT specially - it operates on the root, not a field
        if (aggType == GroupAggregationType.COUNT) {
            return method.invokeInterfaceMethod(CB_COUNT, cb, root);
        }

        // For all other aggregations, we need to extract the field expression
        LambdaExpression fieldExpr = groupAgg.fieldExpression();
        ResultHandle fieldPath = helper.generateExpressionAsJpaExpression(method, fieldExpr, cb, root, capturedValues);

        if (fieldPath == null) {
            // Fallback: if field expression is null, use root
            fieldPath = root;
        }

        return switch (aggType) {
            case COUNT_DISTINCT -> method.invokeInterfaceMethod(CB_COUNT_DISTINCT, cb, fieldPath);
            case AVG -> method.invokeInterfaceMethod(CB_AVG, cb, fieldPath);
            case SUM_INTEGER -> method.invokeInterfaceMethod(CB_SUM, cb, fieldPath);
            case SUM_LONG -> method.invokeInterfaceMethod(CB_SUM_AS_LONG, cb, fieldPath);
            case SUM_DOUBLE -> method.invokeInterfaceMethod(CB_SUM_AS_DOUBLE, cb, fieldPath);
            case MIN -> method.invokeInterfaceMethod(CB_MIN, cb, fieldPath);
            case MAX -> method.invokeInterfaceMethod(CB_MAX, cb, fieldPath);
            case COUNT -> throw new IllegalStateException(COUNT_SHOULD_BE_HANDLED_ABOVE);
            default -> throw new IllegalStateException(unexpectedGroupAggregationType(aggType));
        };
    }

    private ResultHandle generateGroupBinaryOperation(
            MethodCreator method,
            LambdaExpression.BinaryOp binOp,
            ResultHandle cb,
            ResultHandle root,
            ResultHandle groupKeyExpr,
            ResultHandle capturedValues,
            ExpressionGeneratorHelper helper) {

        // Logical operations (AND, OR)
        if (isLogicalOperation(binOp)) {
            ResultHandle left = generateGroupPredicate(method, binOp.left(), cb, root, groupKeyExpr, capturedValues, helper);
            ResultHandle right = generateGroupPredicate(method, binOp.right(), cb, root, groupKeyExpr, capturedValues, helper);
            return helper.combinePredicates(method, cb, left, right, binOp.operator());
        }

        // Arithmetic operations
        if (PatternDetector.isArithmeticExpression(binOp)) {
            ResultHandle left = generateGroupSelectExpression(method, binOp.left(), cb, root, groupKeyExpr, capturedValues, helper);
            ResultHandle right = generateGroupSelectExpression(method, binOp.right(), cb, root, groupKeyExpr, capturedValues, helper);
            return ArithmeticExpressionBuilder.INSTANCE.buildArithmeticOperation(method, binOp.operator(), cb, left, right);
        }

        // Comparison operations (most common in HAVING)
        ResultHandle left = generateGroupSelectExpression(method, binOp.left(), cb, root, groupKeyExpr, capturedValues, helper);
        ResultHandle right = generateGroupSelectExpression(method, binOp.right(), cb, root, groupKeyExpr, capturedValues, helper);
        return ComparisonExpressionBuilder.INSTANCE.buildComparisonOperation(method, binOp.operator(), cb, left, right);
    }

    private ResultHandle generateGroupUnaryOperation(
            MethodCreator method,
            LambdaExpression.UnaryOp unOp,
            ResultHandle cb,
            ResultHandle root,
            ResultHandle groupKeyExpr,
            ResultHandle capturedValues,
            ExpressionGeneratorHelper helper) {

        ResultHandle operand = generateGroupPredicate(method, unOp.operand(), cb, root, groupKeyExpr, capturedValues, helper);

        return switch (unOp.operator()) {
            case NOT -> method.invokeInterfaceMethod(CB_NOT, cb, operand);
        };
    }

    private ResultHandle generateGroupArrayCreation(
            MethodCreator method,
            LambdaExpression.ArrayCreation arrayCreation,
            ResultHandle cb,
            ResultHandle root,
            ResultHandle groupKeyExpr,
            ResultHandle capturedValues,
            ExpressionGeneratorHelper helper) {

        // Generate Selection array for all elements
        ResultHandle selectionsArray = generateGroupArraySelections(
                method, arrayCreation, cb, root, groupKeyExpr, capturedValues, helper);

        return method.invokeInterfaceMethod(CB_TUPLE, cb, selectionsArray);
    }

    private ResultHandle generateGroupConstructorCall(
            MethodCreator method,
            LambdaExpression.ConstructorCall constructorCall,
            ResultHandle cb,
            ResultHandle root,
            ResultHandle groupKeyExpr,
            ResultHandle capturedValues,
            ExpressionGeneratorHelper helper) {

        ResultHandle resultClassHandle = helper.loadDtoClass(method, constructorCall.className());

        return buildConstructorExpression(method, cb, resultClassHandle, constructorCall.arguments(),
                arg -> generateGroupSelectExpression(method, arg, cb, root, groupKeyExpr, capturedValues, helper));
    }
}
