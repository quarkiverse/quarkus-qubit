package io.quarkiverse.qubit.deployment.common;

import static io.quarkiverse.qubit.deployment.common.ExceptionMessages.notArithmeticOperator;
import static io.quarkiverse.qubit.deployment.common.ExceptionMessages.notComparisonOperator;
import static io.quarkiverse.qubit.deployment.generation.MethodDescriptors.*;

import io.quarkiverse.qubit.deployment.ast.LambdaExpression.BinaryOp.Operator;
import io.quarkus.gizmo.MethodDescriptor;

import java.util.EnumMap;
import java.util.Map;

/** Maps binary operators to JPA CriteriaBuilder method descriptors. */
public final class OperatorMethodMapper {

    private OperatorMethodMapper() {}

    // ========== Comparison Operator Registry ==========

    /** Holds both Expression-based and Comparable-based method descriptors for a comparison operator. */
    private record ComparisonSpec(MethodDescriptor exprVariant, MethodDescriptor comparableVariant) {}

    /** Data-driven registry mapping comparison operators to their CriteriaBuilder methods. */
    private static final Map<Operator, ComparisonSpec> COMPARISON_SPECS;

    static {
        Map<Operator, ComparisonSpec> specs = new EnumMap<>(Operator.class);
        specs.put(Operator.EQ, new ComparisonSpec(CB_EQUAL, CB_EQUAL));
        specs.put(Operator.NE, new ComparisonSpec(CB_NOT_EQUAL_EXPR, CB_NOT_EQUAL));
        specs.put(Operator.GT, new ComparisonSpec(CB_GREATER_THAN_EXPR, CB_GREATER_THAN));
        specs.put(Operator.GE, new ComparisonSpec(CB_GREATER_THAN_OR_EQUAL_EXPR, CB_GREATER_THAN_OR_EQUAL));
        specs.put(Operator.LT, new ComparisonSpec(CB_LESS_THAN_EXPR, CB_LESS_THAN));
        specs.put(Operator.LE, new ComparisonSpec(CB_LESS_THAN_OR_EQUAL_EXPR, CB_LESS_THAN_OR_EQUAL));
        COMPARISON_SPECS = Map.copyOf(specs);
    }

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
        ComparisonSpec spec = COMPARISON_SPECS.get(operator);
        if (spec == null) {
            throw new IllegalArgumentException(notComparisonOperator(operator));
        }
        return useExpressionVariant ? spec.exprVariant() : spec.comparableVariant();
    }

    /** Returns true if operator is EQ, NE, GT, GE, LT, or LE. */
    public static boolean isComparisonOperator(Operator operator) {
        return COMPARISON_SPECS.containsKey(operator);
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
