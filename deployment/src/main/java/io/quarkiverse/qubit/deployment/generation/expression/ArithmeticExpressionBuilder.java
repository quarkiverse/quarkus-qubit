package io.quarkiverse.qubit.deployment.generation.expression;

import static io.quarkiverse.qubit.deployment.generation.MethodDescriptors.CB_DIFF;
import static io.quarkiverse.qubit.deployment.generation.MethodDescriptors.CB_MOD;
import static io.quarkiverse.qubit.deployment.generation.MethodDescriptors.CB_PROD;
import static io.quarkiverse.qubit.deployment.generation.MethodDescriptors.CB_QUOT;
import static io.quarkiverse.qubit.deployment.generation.MethodDescriptors.CB_SUM_BINARY;

import io.quarkus.gizmo.MethodCreator;
import io.quarkus.gizmo.MethodDescriptor;
import io.quarkus.gizmo.ResultHandle;
import io.quarkiverse.qubit.deployment.ast.LambdaExpression;

/**
 * Builds JPA Criteria API expressions for arithmetic operations.
 *
 * <p>Operator mappings:
 * <ul>
 *   <li>ADD (+) → {@code CriteriaBuilder.sum()}</li>
 *   <li>SUB (-) → {@code CriteriaBuilder.diff()}</li>
 *   <li>MUL (*) → {@code CriteriaBuilder.prod()}</li>
 *   <li>DIV (/) → {@code CriteriaBuilder.quot()}</li>
 *   <li>MOD (%) → {@code CriteriaBuilder.mod()}</li>
 * </ul>
 */
public class ArithmeticExpressionBuilder implements ExpressionBuilder {

    /**
     * Generates bytecode for arithmetic operations.
     *
     * @param method the Gizmo method creator
     * @param operator the arithmetic operator (ADD, SUB, MUL, DIV, MOD)
     * @param cb the CriteriaBuilder handle
     * @param left the left operand Expression
     * @param right the right operand Expression
     * @return the arithmetic Expression
     */
    public ResultHandle buildArithmeticOperation(
            MethodCreator method,
            LambdaExpression.BinaryOp.Operator operator,
            ResultHandle cb,
            ResultHandle left,
            ResultHandle right) {

        MethodDescriptor arithmeticMethod = switch (operator) {
            case ADD -> CB_SUM_BINARY;
            case SUB -> CB_DIFF;
            case MUL -> CB_PROD;
            case DIV -> CB_QUOT;
            case MOD -> CB_MOD;
            default -> throw new IllegalArgumentException("Not an arithmetic operator: " + operator);
        };

        return method.invokeInterfaceMethod(arithmeticMethod, cb, left, right);
    }
}
