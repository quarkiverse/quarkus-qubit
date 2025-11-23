package io.quarkus.qusaq.deployment.generation.builders;

import io.quarkus.gizmo.MethodCreator;
import io.quarkus.gizmo.ResultHandle;
import io.quarkus.qusaq.deployment.LambdaExpression;

import java.util.Set;

import static io.quarkus.qusaq.deployment.LambdaExpression.BinaryOp.Operator.ADD;
import static io.quarkus.qusaq.deployment.LambdaExpression.BinaryOp.Operator.DIV;
import static io.quarkus.qusaq.deployment.LambdaExpression.BinaryOp.Operator.MUL;
import static io.quarkus.qusaq.deployment.LambdaExpression.BinaryOp.Operator.SUB;
import static io.quarkus.qusaq.runtime.QusaqConstants.*;

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
 */
public class BigDecimalExpressionBuilder {

    /**
     * BigDecimal arithmetic method names.
     */
    private static final Set<String> BIG_DECIMAL_METHODS = Set.of(
        METHOD_ADD,
        METHOD_SUBTRACT,
        METHOD_MULTIPLY,
        METHOD_DIVIDE
    );

    /**
     * Maps BigDecimal method names to binary operators.
     *
     * @param methodName the BigDecimal method name (add, subtract, multiply, divide)
     * @return the corresponding binary operator, or null
     */
    public static LambdaExpression.BinaryOp.Operator mapMethodToOperator(String methodName) {
        return switch (methodName) {
            case METHOD_ADD -> ADD;
            case METHOD_SUBTRACT -> SUB;
            case METHOD_MULTIPLY -> MUL;
            case METHOD_DIVIDE -> DIV;
            default -> null;
        };
    }

    /**
     * Checks if a method call is a BigDecimal arithmetic operation.
     *
     * @param methodCall the method call expression
     * @return true if BigDecimal arithmetic method (add, subtract, multiply, divide)
     */
    public boolean isBigDecimalArithmetic(LambdaExpression.MethodCall methodCall) {
        return BIG_DECIMAL_METHODS.contains(methodCall.methodName());
    }

    /**
     * Generates bytecode for BigDecimal arithmetic: add, subtract, multiply, divide.
     *
     * <p>Delegates to {@link ArithmeticExpressionBuilder} after mapping method name to operator.
     *
     * @param method the Gizmo method creator
     * @param methodCall the method call expression
     * @param cb the CriteriaBuilder handle
     * @param fieldExpression the target field expression
     * @param argument the argument expression
     * @param arithmeticBuilder the arithmetic builder to delegate to
     * @return the arithmetic Expression
     */
    public ResultHandle buildBigDecimalArithmetic(
            MethodCreator method,
            LambdaExpression.MethodCall methodCall,
            ResultHandle cb,
            ResultHandle fieldExpression,
            ResultHandle argument,
            ArithmeticExpressionBuilder arithmeticBuilder) {

        if (!BIG_DECIMAL_METHODS.contains(methodCall.methodName())) {
            return null;
        }

        LambdaExpression.BinaryOp.Operator operator = mapMethodToOperator(methodCall.methodName());
        if (operator == null) {
            throw new IllegalStateException("Unexpected BigDecimal method: " + methodCall.methodName());
        }

        // Delegate to arithmetic builder for the actual code generation
        return arithmeticBuilder.buildArithmeticOperation(method, operator, cb, fieldExpression, argument);
    }
}
