package io.quarkiverse.qubit.deployment.generation.expression;

import io.quarkiverse.qubit.deployment.ast.LambdaExpression;
import io.quarkiverse.qubit.deployment.common.OperatorMethodMapper;
import io.quarkus.gizmo2.Expr;
import io.quarkus.gizmo2.creator.BlockCreator;
import io.quarkus.gizmo2.desc.MethodDesc;

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
 *
 * <p>Uses Gizmo 2 API with BlockCreator and Expr types.
 */
public enum ArithmeticExpressionBuilder implements ExpressionBuilder {
    INSTANCE;

    /** Generates bytecode for arithmetic operations. */
    public Expr buildArithmeticOperation(
            BlockCreator bc,
            LambdaExpression.BinaryOp.Operator operator,
            Expr cb,
            Expr left,
            Expr right) {

        MethodDesc arithmeticMethod = OperatorMethodMapper.mapArithmeticOperator(operator);
        return bc.invokeInterface(arithmeticMethod, cb, left, right);
    }
}
