package io.quarkiverse.qubit.deployment.generation.expression;

import io.quarkiverse.qubit.deployment.ast.LambdaExpression;
import io.quarkiverse.qubit.deployment.common.OperatorMethodMapper;
import io.quarkus.gizmo.MethodCreator;
import io.quarkus.gizmo.MethodDescriptor;
import io.quarkus.gizmo.ResultHandle;

/**
 * Builds JPA Criteria API predicates for comparison operations.
 *
 * <p>Operator mappings:
 * <ul>
 *   <li>EQ (==) → {@code CriteriaBuilder.equal()}</li>
 *   <li>NE (!=) → {@code CriteriaBuilder.notEqual()}</li>
 *   <li>GT (>) → {@code CriteriaBuilder.greaterThan()}</li>
 *   <li>GE (>=) → {@code CriteriaBuilder.greaterThanOrEqualTo()}</li>
 *   <li>LT (<) → {@code CriteriaBuilder.lessThan()}</li>
 *   <li>LE (<=) → {@code CriteriaBuilder.lessThanOrEqualTo()}</li>
 * </ul>
 */
public enum ComparisonExpressionBuilder implements ExpressionBuilder {
    INSTANCE;

    /** Generates bytecode for comparison operations. */
    public ResultHandle buildComparisonOperation(
            MethodCreator method,
            LambdaExpression.BinaryOp.Operator operator,
            ResultHandle cb,
            ResultHandle left,
            ResultHandle right) {

        MethodDescriptor comparisonMethod = OperatorMethodMapper.mapComparisonOperator(operator, true);
        return method.invokeInterfaceMethod(comparisonMethod, cb, left, right);
    }
}
