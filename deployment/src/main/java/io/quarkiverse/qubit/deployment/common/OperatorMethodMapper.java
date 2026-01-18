package io.quarkiverse.qubit.deployment.common;

import static io.quarkiverse.qubit.deployment.common.ExceptionMessages.notArithmeticOperator;
import static io.quarkiverse.qubit.deployment.common.ExceptionMessages.notComparisonOperator;
import static io.quarkiverse.qubit.deployment.generation.MethodDescriptors.*;

import io.quarkiverse.qubit.deployment.ast.LambdaExpression.BinaryOp.Operator;
import io.quarkus.gizmo.MethodDescriptor;

/** Maps binary operators to JPA CriteriaBuilder method descriptors. */
public final class OperatorMethodMapper {

    private OperatorMethodMapper() {}

    /** Maps arithmetic operator to CriteriaBuilder method (sum/diff/prod/quot/mod). */
    public static MethodDescriptor mapArithmeticOperator(Operator operator) {
        return switch (operator) {
            case ADD -> CB_SUM_BINARY;
            case SUB -> CB_DIFF;
            case MUL -> CB_PROD;
            case DIV -> CB_QUOT;
            case MOD -> CB_MOD;
            default -> throw new IllegalArgumentException(notArithmeticOperator(operator));
        };
    }

    /**
     * Maps comparison operator to CriteriaBuilder method.
     * @param useExpressionVariant true for Expression-based, false for Comparable-based
     */
    public static MethodDescriptor mapComparisonOperator(Operator operator, boolean useExpressionVariant) {
        if (useExpressionVariant) {
            return switch (operator) {
                case EQ -> CB_EQUAL;
                case NE -> CB_NOT_EQUAL_EXPR;
                case GT -> CB_GREATER_THAN_EXPR;
                case GE -> CB_GREATER_THAN_OR_EQUAL_EXPR;
                case LT -> CB_LESS_THAN_EXPR;
                case LE -> CB_LESS_THAN_OR_EQUAL_EXPR;
                default -> throw new IllegalArgumentException(notComparisonOperator(operator));
            };
        } else {
            return switch (operator) {
                case EQ -> CB_EQUAL;
                case NE -> CB_NOT_EQUAL;
                case GT -> CB_GREATER_THAN;
                case GE -> CB_GREATER_THAN_OR_EQUAL;
                case LT -> CB_LESS_THAN;
                case LE -> CB_LESS_THAN_OR_EQUAL;
                default -> throw new IllegalArgumentException(notComparisonOperator(operator));
            };
        }
    }

    /** Returns true if operator is EQ, NE, GT, GE, LT, or LE. */
    public static boolean isComparisonOperator(Operator operator) {
        return switch (operator) {
            case EQ, NE, GT, GE, LT, LE -> true;
            default -> false;
        };
    }

    /** Returns true if operator is ADD, SUB, MUL, DIV, or MOD. */
    public static boolean isArithmeticOperator(Operator operator) {
        return switch (operator) {
            case ADD, SUB, MUL, DIV, MOD -> true;
            default -> false;
        };
    }

    /** Returns true if operator is AND or OR. */
    public static boolean isLogicalOperator(Operator operator) {
        return switch (operator) {
            case AND, OR -> true;
            default -> false;
        };
    }
}
