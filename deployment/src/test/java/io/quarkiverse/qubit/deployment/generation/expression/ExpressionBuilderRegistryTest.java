package io.quarkiverse.qubit.deployment.generation.expression;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Unit tests for {@link ExpressionBuilderRegistry}.
 *
 * <p>
 * This class uses JUnit 5 parameterized tests to consolidate repetitive
 * test patterns, reducing code duplication while maintaining full coverage.
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

        /**
         * Tests that constructor throws NullPointerException for each null parameter.
         * Each parameter is tested by setting it to null while others use default instances.
         */
        @ParameterizedTest(name = "throws NullPointerException for null {0}")
        @ValueSource(strings = {
                "arithmeticBuilder",
                "comparisonBuilder",
                "stringBuilder",
                "temporalBuilder",
                "bigDecimalBuilder",
                "subqueryBuilder",
                "biEntityBuilder",
                "groupBuilder"
        })
        void throwsForNullParameter(String parameterName) {
            assertThatThrownBy(() -> createRegistryWithNull(parameterName))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining(parameterName);
        }

        /**
         * Creates an ExpressionBuilderRegistry with the specified parameter set to null.
         */
        private ExpressionBuilderRegistry createRegistryWithNull(String nullParameter) {
            return new ExpressionBuilderRegistry(
                    "arithmeticBuilder".equals(nullParameter) ? null : ArithmeticExpressionBuilder.INSTANCE,
                    "comparisonBuilder".equals(nullParameter) ? null : ComparisonExpressionBuilder.INSTANCE,
                    "stringBuilder".equals(nullParameter) ? null : StringExpressionBuilder.INSTANCE,
                    "temporalBuilder".equals(nullParameter) ? null : TemporalExpressionBuilder.INSTANCE,
                    "bigDecimalBuilder".equals(nullParameter) ? null : BigDecimalExpressionBuilder.INSTANCE,
                    "subqueryBuilder".equals(nullParameter) ? null : SubqueryExpressionBuilder.INSTANCE,
                    "biEntityBuilder".equals(nullParameter) ? null : BiEntityExpressionBuilder.INSTANCE,
                    "groupBuilder".equals(nullParameter) ? null : GroupExpressionBuilder.INSTANCE);
        }
    }
}
