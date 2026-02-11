package io.quarkiverse.qubit.deployment.generation.expression;

import io.quarkiverse.qubit.deployment.ast.LambdaExpression;
import io.quarkiverse.qubit.deployment.common.OperatorMethodMapper;
import io.quarkus.gizmo2.Expr;
import io.quarkus.gizmo2.creator.BlockCreator;
import io.quarkus.gizmo2.desc.MethodDesc;

/**
 * Builds JPA Criteria API predicates for comparison operations.
 *
 * <p>
 * Operator mappings:
 * <ul>
 * <li>EQ (==) → {@code CriteriaBuilder.equal()}</li>
 * <li>NE (!=) → {@code CriteriaBuilder.notEqual()}</li>
 * <li>GT (>) → {@code CriteriaBuilder.greaterThan()}</li>
 * <li>GE (>=) → {@code CriteriaBuilder.greaterThanOrEqualTo()}</li>
 * <li>LT (<) → {@code CriteriaBuilder.lessThan()}</li>
 * <li>LE (<=) → {@code CriteriaBuilder.lessThanOrEqualTo()}</li>
 * </ul>
 */
public enum ComparisonExpressionBuilder implements ExpressionBuilder {
    INSTANCE;

    /** Generates bytecode for comparison operations. */
    public Expr buildComparisonOperation(
            BlockCreator bc,
            LambdaExpression.BinaryOp.Operator operator,
            Expr cb,
            Expr left,
            Expr right) {

        MethodDesc comparisonMethod = OperatorMethodMapper.mapComparisonOperator(operator, true);
        return bc.invokeInterface(comparisonMethod, cb, left, right);
    }
}
