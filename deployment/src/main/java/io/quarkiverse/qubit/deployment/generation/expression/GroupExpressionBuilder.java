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
import static io.quarkiverse.qubit.deployment.generation.MethodDescriptors.CB_SUM;
import static io.quarkiverse.qubit.deployment.generation.MethodDescriptors.CB_SUM_AS_DOUBLE;
import static io.quarkiverse.qubit.deployment.generation.MethodDescriptors.CB_SUM_AS_LONG;
import static io.quarkiverse.qubit.deployment.generation.MethodDescriptors.CB_TUPLE;

import org.jspecify.annotations.Nullable;

import io.quarkus.gizmo2.Expr;
import io.quarkus.gizmo2.creator.BlockCreator;
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
    public @Nullable Expr generateGroupPredicate(
            BlockCreator bc,
            @Nullable LambdaExpression expression,
            Expr cb,
            Expr root,
            Expr groupKeyExpr,
            Expr capturedValues,
            ExpressionGeneratorHelper helper) {

        if (expression == null) {
            return null;
        }

        return switch (expression) {
            case LambdaExpression.BinaryOp binOp ->
                generateGroupBinaryOperation(bc, binOp, cb, root, groupKeyExpr, capturedValues, helper);

            case LambdaExpression.UnaryOp unOp ->
                generateGroupUnaryOperation(bc, unOp, cb, root, groupKeyExpr, capturedValues, helper);

            case GroupAggregation groupAgg ->
                // Aggregation used as a boolean predicate (rare, but possible)
                generateGroupAggregationExpression(bc, groupAgg, cb, root, capturedValues, helper);

            case GroupKeyReference _ ->
                // Key reference as boolean (if key is boolean type)
                groupKeyExpr;

            default -> null;
        };
    }

    /** Generates JPA Expression for GROUP BY SELECT. */
    public @Nullable Expr generateGroupSelectExpression(
            BlockCreator bc,
            @Nullable LambdaExpression expression,
            Expr cb,
            Expr root,
            Expr groupKeyExpr,
            Expr capturedValues,
            ExpressionGeneratorHelper helper) {

        if (expression == null) {
            return null;
        }

        return switch (expression) {
            case GroupKeyReference _ ->
                // g.key() -> use the pre-computed grouping key expression
                groupKeyExpr;

            case GroupAggregation groupAgg ->
                // g.count(), g.avg(), etc. -> generate aggregation expression
                generateGroupAggregationExpression(bc, groupAgg, cb, root, capturedValues, helper);

            case LambdaExpression.ArrayCreation arrayCreation ->
                // Object[] projection using cb.tuple()
                generateGroupArrayCreation(bc, arrayCreation, cb, root, groupKeyExpr, capturedValues, helper);

            case LambdaExpression.ConstructorCall constructorCall ->
                // DTO constructor with group elements
                generateGroupConstructorCall(bc, constructorCall, cb, root, groupKeyExpr, capturedValues, helper);

            case LambdaExpression.FieldAccess field ->
                // Field access in group context (from nested lambda in aggregation)
                helper.generateFieldAccess(bc, field, root);

            case PathExpression pathExpr ->
                helper.generatePathExpression(bc, pathExpr, root);

            case LambdaExpression.Constant constant -> {
                Expr constantValue = helper.generateConstant(bc, constant);
                yield helper.wrapAsLiteral(bc, cb, constantValue);
            }

            case LambdaExpression.CapturedVariable capturedVar ->
                helper.loadAndWrapCapturedValue(bc, cb, capturedVar, capturedValues);

            case LambdaExpression.BinaryOp binOp ->
                generateGroupBinaryOperation(bc, binOp, cb, root, groupKeyExpr, capturedValues, helper);

            default -> null;
        };
    }

    /** Generates JPA Expression for group ORDER BY clause. */
    public @Nullable Expr generateGroupSortExpression(
            BlockCreator bc,
            @Nullable LambdaExpression expression,
            Expr cb,
            Expr root,
            Expr groupKeyExpr,
            Expr capturedValues,
            ExpressionGeneratorHelper helper) {

        // Delegate to generateGroupSelectExpression since they handle the same types
        return generateGroupSelectExpression(bc, expression, cb, root, groupKeyExpr, capturedValues, helper);
    }

    /** Generates JPA multiselect array for Object[] projections. */
    public Expr generateGroupArraySelections(
            BlockCreator bc,
            LambdaExpression.ArrayCreation arrayCreation,
            Expr cb,
            Expr root,
            Expr groupKeyExpr,
            Expr capturedValues,
            ExpressionGeneratorHelper helper) {

        // Generate array from list using Gizmo2 API
        return bc.newArray(Selection.class, arrayCreation.elements(),
                element -> generateGroupSelectExpression(bc, element, cb, root, groupKeyExpr, capturedValues, helper));
    }

    // ========== Private Helper Methods ==========

    private Expr generateGroupAggregationExpression(
            BlockCreator bc,
            GroupAggregation groupAgg,
            Expr cb,
            Expr root,
            Expr capturedValues,
            ExpressionGeneratorHelper helper) {

        GroupAggregationType aggType = groupAgg.aggregationType();

        // Handle COUNT specially - it operates on the root, not a field
        if (aggType == GroupAggregationType.COUNT) {
            return bc.invokeInterface(CB_COUNT, cb, root);
        }

        // For all other aggregations, we need to extract the field expression
        LambdaExpression fieldExpr = groupAgg.fieldExpression();
        Expr fieldPath = helper.generateExpressionAsJpaExpression(bc, fieldExpr, cb, root, capturedValues);

        if (fieldPath == null) {
            // Fallback: if field expression is null, use root
            fieldPath = root;
        }

        return switch (aggType) {
            case COUNT_DISTINCT -> bc.invokeInterface(CB_COUNT_DISTINCT, cb, fieldPath);
            case AVG -> bc.invokeInterface(CB_AVG, cb, fieldPath);
            case SUM_INTEGER -> bc.invokeInterface(CB_SUM, cb, fieldPath);
            case SUM_LONG -> bc.invokeInterface(CB_SUM_AS_LONG, cb, fieldPath);
            case SUM_DOUBLE -> bc.invokeInterface(CB_SUM_AS_DOUBLE, cb, fieldPath);
            case MIN -> bc.invokeInterface(CB_MIN, cb, fieldPath);
            case MAX -> bc.invokeInterface(CB_MAX, cb, fieldPath);
            case COUNT -> throw new IllegalStateException(COUNT_SHOULD_BE_HANDLED_ABOVE);
            default -> throw new IllegalStateException(unexpectedGroupAggregationType(aggType));
        };
    }

    private Expr generateGroupBinaryOperation(
            BlockCreator bc,
            LambdaExpression.BinaryOp binOp,
            Expr cb,
            Expr root,
            Expr groupKeyExpr,
            Expr capturedValues,
            ExpressionGeneratorHelper helper) {

        // Logical operations (AND, OR)
        if (isLogicalOperation(binOp)) {
            Expr left = generateGroupPredicate(bc, binOp.left(), cb, root, groupKeyExpr, capturedValues, helper);
            Expr right = generateGroupPredicate(bc, binOp.right(), cb, root, groupKeyExpr, capturedValues, helper);
            return helper.combinePredicates(bc, cb, left, right, binOp.operator());
        }

        // Arithmetic operations
        if (PatternDetector.isArithmeticExpression(binOp)) {
            Expr left = generateGroupSelectExpression(bc, binOp.left(), cb, root, groupKeyExpr, capturedValues, helper);
            Expr right = generateGroupSelectExpression(bc, binOp.right(), cb, root, groupKeyExpr, capturedValues, helper);
            return ArithmeticExpressionBuilder.INSTANCE.buildArithmeticOperation(bc, binOp.operator(), cb, left, right);
        }

        // Comparison operations (most common in HAVING)
        Expr left = generateGroupSelectExpression(bc, binOp.left(), cb, root, groupKeyExpr, capturedValues, helper);
        Expr right = generateGroupSelectExpression(bc, binOp.right(), cb, root, groupKeyExpr, capturedValues, helper);
        return ComparisonExpressionBuilder.INSTANCE.buildComparisonOperation(bc, binOp.operator(), cb, left, right);
    }

    private Expr generateGroupUnaryOperation(
            BlockCreator bc,
            LambdaExpression.UnaryOp unOp,
            Expr cb,
            Expr root,
            Expr groupKeyExpr,
            Expr capturedValues,
            ExpressionGeneratorHelper helper) {

        Expr operand = generateGroupPredicate(bc, unOp.operand(), cb, root, groupKeyExpr, capturedValues, helper);
        return helper.applyUnaryOperator(bc, cb, operand, unOp.operator());
    }

    private Expr generateGroupArrayCreation(
            BlockCreator bc,
            LambdaExpression.ArrayCreation arrayCreation,
            Expr cb,
            Expr root,
            Expr groupKeyExpr,
            Expr capturedValues,
            ExpressionGeneratorHelper helper) {

        // Generate Selection array for all elements
        Expr selectionsArray = generateGroupArraySelections(
                bc, arrayCreation, cb, root, groupKeyExpr, capturedValues, helper);

        return bc.invokeInterface(CB_TUPLE, cb, selectionsArray);
    }

    private Expr generateGroupConstructorCall(
            BlockCreator bc,
            LambdaExpression.ConstructorCall constructorCall,
            Expr cb,
            Expr root,
            Expr groupKeyExpr,
            Expr capturedValues,
            ExpressionGeneratorHelper helper) {

        Expr resultClassHandle = helper.loadDtoClass(bc, constructorCall.className());

        return buildConstructorExpression(bc, cb, resultClassHandle, constructorCall.arguments(),
                arg -> generateGroupSelectExpression(bc, arg, cb, root, groupKeyExpr, capturedValues, helper));
    }
}
