package io.quarkiverse.qubit.deployment.generation.expression;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link ExpressionBuilderRegistry}.
 */
@DisplayName("ExpressionBuilderRegistry")
class ExpressionBuilderRegistryTest {

    @Nested
    @DisplayName("createDefault()")
    class CreateDefaultTests {

        @Test
        @DisplayName("returns non-null registry")
        void returnsNonNull() {
            ExpressionBuilderRegistry registry = ExpressionBuilderRegistry.createDefault();

            assertThat(registry).isNotNull();
        }
    }

    @Nested
    @DisplayName("Constructor validation")
    class ConstructorValidationTests {

        @Test
        @DisplayName("throws NullPointerException for null arithmeticBuilder")
        void throwsForNullArithmetic() {
            assertThatThrownBy(() -> new ExpressionBuilderRegistry(
                    null,
                    ComparisonExpressionBuilder.INSTANCE,
                    StringExpressionBuilder.INSTANCE,
                    TemporalExpressionBuilder.INSTANCE,
                    BigDecimalExpressionBuilder.INSTANCE,
                    SubqueryExpressionBuilder.INSTANCE,
                    BiEntityExpressionBuilder.INSTANCE,
                    GroupExpressionBuilder.INSTANCE))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("arithmeticBuilder");
        }

        @Test
        @DisplayName("throws NullPointerException for null comparisonBuilder")
        void throwsForNullComparison() {
            assertThatThrownBy(() -> new ExpressionBuilderRegistry(
                    ArithmeticExpressionBuilder.INSTANCE,
                    null,
                    StringExpressionBuilder.INSTANCE,
                    TemporalExpressionBuilder.INSTANCE,
                    BigDecimalExpressionBuilder.INSTANCE,
                    SubqueryExpressionBuilder.INSTANCE,
                    BiEntityExpressionBuilder.INSTANCE,
                    GroupExpressionBuilder.INSTANCE))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("comparisonBuilder");
        }

        @Test
        @DisplayName("throws NullPointerException for null stringBuilder")
        void throwsForNullString() {
            assertThatThrownBy(() -> new ExpressionBuilderRegistry(
                    ArithmeticExpressionBuilder.INSTANCE,
                    ComparisonExpressionBuilder.INSTANCE,
                    null,
                    TemporalExpressionBuilder.INSTANCE,
                    BigDecimalExpressionBuilder.INSTANCE,
                    SubqueryExpressionBuilder.INSTANCE,
                    BiEntityExpressionBuilder.INSTANCE,
                    GroupExpressionBuilder.INSTANCE))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("stringBuilder");
        }

        @Test
        @DisplayName("throws NullPointerException for null temporalBuilder")
        void throwsForNullTemporal() {
            assertThatThrownBy(() -> new ExpressionBuilderRegistry(
                    ArithmeticExpressionBuilder.INSTANCE,
                    ComparisonExpressionBuilder.INSTANCE,
                    StringExpressionBuilder.INSTANCE,
                    null,
                    BigDecimalExpressionBuilder.INSTANCE,
                    SubqueryExpressionBuilder.INSTANCE,
                    BiEntityExpressionBuilder.INSTANCE,
                    GroupExpressionBuilder.INSTANCE))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("temporalBuilder");
        }

        @Test
        @DisplayName("throws NullPointerException for null bigDecimalBuilder")
        void throwsForNullBigDecimal() {
            assertThatThrownBy(() -> new ExpressionBuilderRegistry(
                    ArithmeticExpressionBuilder.INSTANCE,
                    ComparisonExpressionBuilder.INSTANCE,
                    StringExpressionBuilder.INSTANCE,
                    TemporalExpressionBuilder.INSTANCE,
                    null,
                    SubqueryExpressionBuilder.INSTANCE,
                    BiEntityExpressionBuilder.INSTANCE,
                    GroupExpressionBuilder.INSTANCE))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("bigDecimalBuilder");
        }

        @Test
        @DisplayName("throws NullPointerException for null subqueryBuilder")
        void throwsForNullSubquery() {
            assertThatThrownBy(() -> new ExpressionBuilderRegistry(
                    ArithmeticExpressionBuilder.INSTANCE,
                    ComparisonExpressionBuilder.INSTANCE,
                    StringExpressionBuilder.INSTANCE,
                    TemporalExpressionBuilder.INSTANCE,
                    BigDecimalExpressionBuilder.INSTANCE,
                    null,
                    BiEntityExpressionBuilder.INSTANCE,
                    GroupExpressionBuilder.INSTANCE))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("subqueryBuilder");
        }

        @Test
        @DisplayName("throws NullPointerException for null biEntityBuilder")
        void throwsForNullBiEntity() {
            assertThatThrownBy(() -> new ExpressionBuilderRegistry(
                    ArithmeticExpressionBuilder.INSTANCE,
                    ComparisonExpressionBuilder.INSTANCE,
                    StringExpressionBuilder.INSTANCE,
                    TemporalExpressionBuilder.INSTANCE,
                    BigDecimalExpressionBuilder.INSTANCE,
                    SubqueryExpressionBuilder.INSTANCE,
                    null,
                    GroupExpressionBuilder.INSTANCE))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("biEntityBuilder");
        }

        @Test
        @DisplayName("throws NullPointerException for null groupBuilder")
        void throwsForNullGroup() {
            assertThatThrownBy(() -> new ExpressionBuilderRegistry(
                    ArithmeticExpressionBuilder.INSTANCE,
                    ComparisonExpressionBuilder.INSTANCE,
                    StringExpressionBuilder.INSTANCE,
                    TemporalExpressionBuilder.INSTANCE,
                    BigDecimalExpressionBuilder.INSTANCE,
                    SubqueryExpressionBuilder.INSTANCE,
                    BiEntityExpressionBuilder.INSTANCE,
                    null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("groupBuilder");
        }
    }
}
