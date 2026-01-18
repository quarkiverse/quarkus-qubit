package io.quarkiverse.qubit.deployment.generation.expression;

import io.quarkiverse.qubit.deployment.ast.LambdaExpression;
import io.quarkiverse.qubit.deployment.common.OperatorMethodMapper;
import io.quarkus.gizmo.MethodCreator;
import io.quarkus.gizmo.MethodDescriptor;
import io.quarkus.gizmo.ResultHandle;

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
public enum ArithmeticExpressionBuilder implements ExpressionBuilder {
    INSTANCE;

    /** Generates bytecode for arithmetic operations. */
    public ResultHandle buildArithmeticOperation(
            MethodCreator method,
            LambdaExpression.BinaryOp.Operator operator,
            ResultHandle cb,
            ResultHandle left,
            ResultHandle right) {

        MethodDescriptor arithmeticMethod = OperatorMethodMapper.mapArithmeticOperator(operator);
        return method.invokeInterfaceMethod(arithmeticMethod, cb, left, right);
    }
}
