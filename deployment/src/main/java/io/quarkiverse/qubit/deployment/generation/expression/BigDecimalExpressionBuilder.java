package io.quarkiverse.qubit.deployment.generation.expression;

import io.quarkus.gizmo2.Expr;
import io.quarkus.gizmo2.creator.BlockCreator;
import io.quarkiverse.qubit.deployment.ast.LambdaExpression;

import static io.quarkiverse.qubit.deployment.common.ExceptionMessages.unexpectedBigDecimalMethod;
import static io.quarkiverse.qubit.deployment.generation.expression.BuilderResult.notApplicable;
import static io.quarkiverse.qubit.deployment.generation.expression.BuilderResult.success;

import static io.quarkiverse.qubit.deployment.ast.LambdaExpression.BinaryOp.Operator.ADD;
import static io.quarkiverse.qubit.deployment.ast.LambdaExpression.BinaryOp.Operator.DIV;
import static io.quarkiverse.qubit.deployment.ast.LambdaExpression.BinaryOp.Operator.MUL;
import static io.quarkiverse.qubit.deployment.ast.LambdaExpression.BinaryOp.Operator.SUB;
import static io.quarkiverse.qubit.runtime.internal.QubitConstants.*;

/**
 * Builds JPA Criteria API expressions for BigDecimal arithmetic operations.
 *
 * <p>Method mappings:
 * <ul>
 *   <li>add() → {@code CriteriaBuilder.sum()}</li>
 *   <li>subtract() → {@code CriteriaBuilder.diff()}</li>
 *   <li>multiply() → {@code CriteriaBuilder.prod()}</li>
 *   <li>divide() → {@code CriteriaBuilder.quot()}</li>
 * </ul>
 *
 * <p><b>Note:</b> BigDecimal methods are mapped to binary operators and delegated
 * to {@link ArithmeticExpressionBuilder} for actual bytecode generation.
 *
 * <p>Uses Gizmo 2 API with BlockCreator and Expr types.
 */
public enum BigDecimalExpressionBuilder implements ExpressionBuilder {
    INSTANCE;

    /** Maps BigDecimal method names to binary operators. */
    public static LambdaExpression.BinaryOp.Operator mapMethodToOperator(String methodName) {
        return switch (methodName) {
            case METHOD_ADD -> ADD;
            case METHOD_SUBTRACT -> SUB;
            case METHOD_MULTIPLY -> MUL;
            case METHOD_DIVIDE -> DIV;
            default -> null;
        };
    }

    /** Checks if a method call is a BigDecimal arithmetic operation. */
    public boolean isBigDecimalArithmetic(LambdaExpression.MethodCall methodCall) {
        return BIG_DECIMAL_ARITHMETIC_METHODS.contains(methodCall.methodName());
    }

    /** Generates bytecode for BigDecimal arithmetic, delegating to {@link ArithmeticExpressionBuilder}. */
    public BuilderResult buildBigDecimalArithmetic(
            BlockCreator bc,
            LambdaExpression.MethodCall methodCall,
            Expr cb,
            Expr fieldExpression,
            Expr argument,
            ArithmeticExpressionBuilder arithmeticBuilder) {

        if (!BIG_DECIMAL_ARITHMETIC_METHODS.contains(methodCall.methodName())) {
            return notApplicable();
        }

        LambdaExpression.BinaryOp.Operator operator = mapMethodToOperator(methodCall.methodName());
        if (operator == null) {
            throw new IllegalStateException(unexpectedBigDecimalMethod(methodCall.methodName()));
        }

        // Delegate to arithmetic builder for the actual code generation
        Expr result = arithmeticBuilder.buildArithmeticOperation(bc, operator, cb, fieldExpression, argument);
        return success(result);
    }
}
