package io.quarkiverse.qubit.deployment.generation.expression;

import java.util.Objects;

/**
 * Registry holding all expression builder instances for dependency injection.
 *
 * <p>This registry enables testability by allowing tests to inject mock or custom
 * builder implementations. In production, use {@link #createDefault()} to obtain
 * a registry with standard builder instances.
 *
 * <p><b>Design Rationale:</b>
 * <ul>
 *   <li>All builders are stateless, making them safe to share</li>
 *   <li>Record provides immutability and automatic equals/hashCode</li>
 *   <li>Static factory method encapsulates default configuration</li>
 *   <li>Constructor injection enables testing with mock builders</li>
 * </ul>
 *
 * <p><b>Usage in Production:</b>
 * <pre>
 * CriteriaExpressionGenerator generator = new CriteriaExpressionGenerator();
 * // Uses default registry internally
 * </pre>
 *
 * <p><b>Usage in Tests:</b>
 * <pre>
 * ExpressionBuilderRegistry customRegistry = new ExpressionBuilderRegistry(
 *     mockArithmeticBuilder,
 *     mockComparisonBuilder,
 *     // ... other builders
 * );
 * CriteriaExpressionGenerator generator = new CriteriaExpressionGenerator(customRegistry);
 * </pre>
 *
 * @see ExpressionBuilder
 * @see io.quarkiverse.qubit.deployment.generation.CriteriaExpressionGenerator
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

    /**
     * Creates a registry with validation that no builders are null.
     *
     * @throws NullPointerException if any builder is null
     */
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

    /**
     * Creates a default registry with all standard expression builders.
     *
     * <p>This is the recommended way to obtain a registry for production use.
     * All builders are stateless and thread-safe.
     *
     * @return a new registry with default builder instances
     */
    public static ExpressionBuilderRegistry createDefault() {
        return new ExpressionBuilderRegistry(
                new ArithmeticExpressionBuilder(),
                new ComparisonExpressionBuilder(),
                new StringExpressionBuilder(),
                new TemporalExpressionBuilder(),
                new BigDecimalExpressionBuilder(),
                new SubqueryExpressionBuilder(),
                new BiEntityExpressionBuilder(),
                new GroupExpressionBuilder()
        );
    }
}
