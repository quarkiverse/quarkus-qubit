package io.quarkiverse.qubit.deployment.generation.expression;

import static io.quarkiverse.qubit.deployment.common.ExceptionMessages.COUNT_SHOULD_BE_HANDLED_ABOVE;
import static io.quarkiverse.qubit.deployment.common.PatternDetector.isLogicalOperation;
import static io.quarkiverse.qubit.deployment.generation.MethodDescriptors.CB_BETWEEN_EXPR;
import static io.quarkiverse.qubit.deployment.generation.MethodDescriptors.CB_NULLIF_EXPR;
import static io.quarkiverse.qubit.deployment.generation.MethodDescriptors.CB_NULL_LITERAL;
import static io.quarkiverse.qubit.deployment.generation.GizmoHelper.buildConstructorExpression;
import static io.quarkiverse.qubit.deployment.generation.MethodDescriptors.CASE_OTHERWISE_EXPR;
import static io.quarkiverse.qubit.deployment.generation.MethodDescriptors.CASE_WHEN_EXPR;
import static io.quarkiverse.qubit.deployment.generation.MethodDescriptors.CB_AVG;
import static io.quarkiverse.qubit.deployment.generation.MethodDescriptors.CB_COUNT;
import static io.quarkiverse.qubit.deployment.generation.MethodDescriptors.CB_COUNT_DISTINCT;
import static io.quarkiverse.qubit.deployment.generation.MethodDescriptors.CB_MAX;
import static io.quarkiverse.qubit.deployment.generation.MethodDescriptors.CB_MIN;
import static io.quarkiverse.qubit.deployment.generation.MethodDescriptors.CB_SELECT_CASE;
import static io.quarkiverse.qubit.deployment.generation.MethodDescriptors.CB_SUM;
import static io.quarkiverse.qubit.deployment.generation.MethodDescriptors.CB_SUM_AS_DOUBLE;
import static io.quarkiverse.qubit.deployment.generation.MethodDescriptors.CB_SUM_AS_LONG;
import static io.quarkiverse.qubit.deployment.generation.MethodDescriptors.CB_TUPLE;

import jakarta.persistence.criteria.Selection;

import org.jspecify.annotations.Nullable;

import io.quarkiverse.qubit.deployment.ast.LambdaExpression;
import io.quarkiverse.qubit.deployment.ast.LambdaExpression.GroupAggregation;
import io.quarkiverse.qubit.deployment.ast.LambdaExpression.GroupAggregationType;
import io.quarkiverse.qubit.deployment.ast.LambdaExpression.GroupKeyReference;
import io.quarkiverse.qubit.deployment.ast.LambdaExpression.PathExpression;
import io.quarkiverse.qubit.deployment.common.PatternDetector;
import io.quarkiverse.qubit.deployment.generation.UnsupportedExpressionException;
import io.quarkus.gizmo2.Expr;
import io.quarkus.gizmo2.LocalVar;
import io.quarkus.gizmo2.creator.BlockCreator;

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

            case LambdaExpression.NullLiteral(var expectedType) -> {
                Expr typeClass = io.quarkus.gizmo2.Const.of(expectedType);
                yield bc.invokeInterface(CB_NULL_LITERAL, cb, typeClass);
            }

            case LambdaExpression.BinaryOp binOp ->
                generateGroupBinaryOperation(bc, binOp, cb, root, groupKeyExpr, capturedValues, helper);

            case LambdaExpression.Conditional conditional -> {
                // NULLIF optimization: detect field == sentinel ? null : field
                PatternDetector.NullifComponents nullif = PatternDetector.detectNullif(conditional);
                if (nullif != null) {
                    Expr fieldExpr = generateGroupSelectExpression(bc, nullif.expression(), cb, root, groupKeyExpr,
                            capturedValues, helper);
                    Expr sentinelExpr = generateGroupSelectExpression(bc, nullif.sentinel(), cb, root, groupKeyExpr,
                            capturedValues, helper);
                    yield bc.invokeInterface(CB_NULLIF_EXPR, cb, fieldExpr, sentinelExpr);
                }
                // Standard CASE WHEN handling
                yield generateGroupConditionalExpression(bc, conditional, cb, root, groupKeyExpr, capturedValues, helper);
            }

            case LambdaExpression.MathFunction mathFunc ->
                generateGroupMathFunction(bc, mathFunc, cb, root, groupKeyExpr, capturedValues, helper);

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
            default -> throw new IllegalStateException("Unexpected group aggregation type: " + aggType);
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
            // BETWEEN optimization: detect field >= low && field <= high
            if (binOp.operator() == LambdaExpression.BinaryOp.Operator.AND) {
                PatternDetector.BetweenComponents between = PatternDetector.detectBetween(binOp);
                if (between != null) {
                    Expr fieldExpr = generateGroupSelectExpression(bc, between.field(), cb, root, groupKeyExpr,
                            capturedValues, helper);
                    Expr lowerExpr = generateGroupSelectExpression(bc, between.lowerBound(), cb, root, groupKeyExpr,
                            capturedValues, helper);
                    Expr upperExpr = generateGroupSelectExpression(bc, between.upperBound(), cb, root, groupKeyExpr,
                            capturedValues, helper);
                    return bc.invokeInterface(CB_BETWEEN_EXPR, cb, fieldExpr, lowerExpr, upperExpr);
                }
            }

            Expr left = generateGroupPredicate(bc, binOp.left(), cb, root, groupKeyExpr, capturedValues, helper);
            Expr right = generateGroupPredicate(bc, binOp.right(), cb, root, groupKeyExpr, capturedValues, helper);

            // Handle unsupported expressions that return null
            if (left == null || right == null) {
                throw new UnsupportedExpressionException(binOp,
                        "Logical operation (" + binOp.operator() + ") contains unsupported operand types in GROUP BY context");
            }

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

    /** Generates JPA math function expression in GROUP BY context. */
    private Expr generateGroupMathFunction(
            BlockCreator bc,
            LambdaExpression.MathFunction mathFunc,
            Expr cb,
            Expr root,
            Expr groupKeyExpr,
            Expr capturedValues,
            ExpressionGeneratorHelper helper) {

        Expr operandExpr = generateGroupSelectExpression(bc, mathFunc.operand(), cb, root, groupKeyExpr, capturedValues,
                helper);

        Expr secondExpr = null;
        if (mathFunc.op().isBinary()) {
            if (mathFunc.op() == LambdaExpression.MathFunction.MathOp.ROUND) {
                secondExpr = helper.generateExpression(bc, mathFunc.secondOperand(), cb, root, capturedValues);
            } else {
                secondExpr = generateGroupSelectExpression(bc, mathFunc.secondOperand(), cb, root, groupKeyExpr,
                        capturedValues, helper);
            }
        }

        return MathExpressionBuilder.build(bc, cb, operandExpr, secondExpr, mathFunc.op());
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

    /**
     * Generates JPA CASE WHEN expression from a ternary conditional in GROUP BY context.
     * <p>
     * Maps: {@code condition ? trueValue : falseValue}
     * To JPA: {@code cb.selectCase().when(conditionPredicate, trueExpr).otherwise(falseExpr)}
     */
    private Expr generateGroupConditionalExpression(
            BlockCreator bc,
            LambdaExpression.Conditional conditional,
            Expr cb,
            Expr root,
            Expr groupKeyExpr,
            Expr capturedValues,
            ExpressionGeneratorHelper helper) {

        // Generate and store sub-expressions in LocalVars (Gizmo2 stack discipline requirement)
        LocalVar conditionLocal = bc.localVar("groupTernaryCondition",
                generateGroupPredicate(bc, conditional.condition(), cb, root, groupKeyExpr, capturedValues, helper));
        LocalVar trueLocal = bc.localVar("groupTernaryTrue",
                generateGroupSelectExpression(bc, conditional.trueValue(), cb, root, groupKeyExpr, capturedValues, helper));
        LocalVar falseLocal = bc.localVar("groupTernaryFalse",
                generateGroupSelectExpression(bc, conditional.falseValue(), cb, root, groupKeyExpr, capturedValues, helper));

        // Build: cb.selectCase().when(condition, trueExpr).otherwise(falseExpr)
        LocalVar caseBuilder = bc.localVar("caseBuilder", bc.invokeInterface(CB_SELECT_CASE, cb));
        LocalVar caseWhen = bc.localVar("caseWhen",
                bc.invokeInterface(CASE_WHEN_EXPR, caseBuilder, conditionLocal, trueLocal));
        return bc.invokeInterface(CASE_OTHERWISE_EXPR, caseWhen, falseLocal);
    }
}
