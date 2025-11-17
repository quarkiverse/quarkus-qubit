package io.quarkus.qusaq.deployment.generation.builders;

import io.quarkus.gizmo.MethodCreator;
import io.quarkus.gizmo.MethodDescriptor;
import io.quarkus.gizmo.ResultHandle;
import io.quarkus.qusaq.deployment.LambdaExpression;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;

import static io.quarkus.qusaq.runtime.QusaqConstants.*;

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
public class ComparisonExpressionBuilder {

    /**
     * Generates bytecode for comparison operations.
     *
     * @param method the Gizmo method creator
     * @param operator the comparison operator (EQ, NE, GT, GE, LT, LE)
     * @param cb the CriteriaBuilder handle
     * @param left the left operand Expression
     * @param right the right operand Expression or Object
     * @return the comparison Predicate
     */
    public ResultHandle buildComparisonOperation(
            MethodCreator method,
            LambdaExpression.BinaryOp.Operator operator,
            ResultHandle cb,
            ResultHandle left,
            ResultHandle right) {

        MethodDescriptor comparisonMethod = switch (operator) {
            case EQ -> md(CB_EQUAL, Expression.class, Object.class);
            case NE -> md(CB_NOT_EQUAL, Expression.class, Object.class);
            case GT -> md(CB_GREATER_THAN, Expression.class, Expression.class);
            case GE -> md(CB_GREATER_THAN_OR_EQUAL_TO, Expression.class, Expression.class);
            case LT -> md(CB_LESS_THAN, Expression.class, Expression.class);
            case LE -> md(CB_LESS_THAN_OR_EQUAL_TO, Expression.class, Expression.class);
            default -> throw new IllegalArgumentException("Not a comparison operator: " + operator);
        };

        return method.invokeInterfaceMethod(comparisonMethod, cb, left, right);
    }

    /**
     * Creates MethodDescriptor for a method.
     */
    private static MethodDescriptor md(String methodName, Class<?>... params) {
        return MethodDescriptor.ofMethod(CriteriaBuilder.class, methodName, Predicate.class, params);
    }
}
