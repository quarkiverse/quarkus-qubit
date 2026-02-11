package io.quarkiverse.qubit.deployment.generation.expression;

import java.util.Objects;

/**
 * Registry holding all expression builder instances for dependency injection.
 * Enables testability by allowing mock builder injection; use {@link #createDefault()} in production.
 */
public record ExpressionBuilderRegistry(
        ArithmeticExpressionBuilder arithmeticBuilder,
        ComparisonExpressionBuilder comparisonBuilder,
        StringExpressionBuilder stringBuilder,
        TemporalExpressionBuilder temporalBuilder,
        BigDecimalExpressionBuilder bigDecimalBuilder,
        SubqueryExpressionBuilder subqueryBuilder,
        BiEntityExpressionBuilder biEntityBuilder,
        GroupExpressionBuilder groupBuilder) {

    /** Creates a registry with validation that no builders are null. */
    public ExpressionBuilderRegistry {
        Objects.requireNonNull(arithmeticBuilder, "arithmeticBuilder cannot be null");
        Objects.requireNonNull(comparisonBuilder, "comparisonBuilder cannot be null");
        Objects.requireNonNull(stringBuilder, "stringBuilder cannot be null");
        Objects.requireNonNull(temporalBuilder, "temporalBuilder cannot be null");
        Objects.requireNonNull(bigDecimalBuilder, "bigDecimalBuilder cannot be null");
        Objects.requireNonNull(subqueryBuilder, "subqueryBuilder cannot be null");
        Objects.requireNonNull(biEntityBuilder, "biEntityBuilder cannot be null");
        Objects.requireNonNull(groupBuilder, "groupBuilder cannot be null");
    }

    /** Creates a default registry with all standard expression builders. */
    public static ExpressionBuilderRegistry createDefault() {
        return new ExpressionBuilderRegistry(
                ArithmeticExpressionBuilder.INSTANCE,
                ComparisonExpressionBuilder.INSTANCE,
                StringExpressionBuilder.INSTANCE,
                TemporalExpressionBuilder.INSTANCE,
                BigDecimalExpressionBuilder.INSTANCE,
                SubqueryExpressionBuilder.INSTANCE,
                BiEntityExpressionBuilder.INSTANCE,
                GroupExpressionBuilder.INSTANCE);
    }
}
