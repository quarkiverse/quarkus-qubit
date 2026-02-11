package io.quarkiverse.qubit.deployment.generation.expression;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import io.quarkiverse.qubit.deployment.ast.LambdaExpression;

/**
 * Tests for {@link TemporalExpressionBuilder}.
 *
 * <p>
 * Tests the unit-testable methods: {@code isTemporalComparison()} and
 * {@code isSupportedTemporalType()}. The Gizmo-dependent methods require
 * integration tests.
 */
@DisplayName("TemporalExpressionBuilder")
class TemporalExpressionBuilderTest {

    private TemporalExpressionBuilder builder;

    @BeforeEach
    void setUp() {
        builder = TemporalExpressionBuilder.INSTANCE;
    }

    @Nested
    @DisplayName("isTemporalComparison()")
    class IsTemporalComparisonTests {

        @ParameterizedTest(name = "\"{0}\" should be recognized as temporal comparison")
        @ValueSource(strings = { "isAfter", "isBefore", "isEqual" })
        void shouldReturnTrueForTemporalComparisonMethods(String methodName) {
            LambdaExpression.MethodCall methodCall = createMethodCall(methodName);
            assertThat(builder.isTemporalComparison(methodCall))
                    .as("Method '%s' should be recognized as temporal comparison", methodName)
                    .isTrue();
        }

        @ParameterizedTest(name = "\"{0}\" should NOT be recognized as temporal comparison")
        @ValueSource(strings = { "equals", "compareTo", "getYear", "getMonth", "getDayOfMonth",
                "getHour", "getMinute", "getSecond", "toString", "hashCode" })
        void shouldReturnFalseForNonTemporalComparisonMethods(String methodName) {
            LambdaExpression.MethodCall methodCall = createMethodCall(methodName);
            assertThat(builder.isTemporalComparison(methodCall))
                    .as("Method '%s' should NOT be recognized as temporal comparison", methodName)
                    .isFalse();
        }

        @Test
        @DisplayName("should return false for empty method name")
        void shouldReturnFalseForEmptyMethodName() {
            LambdaExpression.MethodCall methodCall = createMethodCall("");
            assertThat(builder.isTemporalComparison(methodCall))
                    .as("Empty method name should not be recognized as temporal comparison")
                    .isFalse();
        }
    }

    @Nested
    @DisplayName("isSupportedTemporalType()")
    class IsSupportedTemporalTypeTests {

        @Test
        @DisplayName("LocalDate should be supported")
        void shouldSupportLocalDate() {
            assertThat(TemporalExpressionBuilder.isSupportedTemporalType(LocalDate.class))
                    .as("LocalDate should be a supported temporal type")
                    .isTrue();
        }

        @Test
        @DisplayName("LocalDateTime should be supported")
        void shouldSupportLocalDateTime() {
            assertThat(TemporalExpressionBuilder.isSupportedTemporalType(LocalDateTime.class))
                    .as("LocalDateTime should be a supported temporal type")
                    .isTrue();
        }

        @Test
        @DisplayName("LocalTime should be supported")
        void shouldSupportLocalTime() {
            assertThat(TemporalExpressionBuilder.isSupportedTemporalType(LocalTime.class))
                    .as("LocalTime should be a supported temporal type")
                    .isTrue();
        }

        @Test
        @DisplayName("ZonedDateTime should NOT be supported")
        void shouldNotSupportZonedDateTime() {
            assertThat(TemporalExpressionBuilder.isSupportedTemporalType(ZonedDateTime.class))
                    .as("ZonedDateTime should NOT be a supported temporal type")
                    .isFalse();
        }

        @Test
        @DisplayName("java.util.Date should NOT be supported")
        void shouldNotSupportJavaUtilDate() {
            assertThat(TemporalExpressionBuilder.isSupportedTemporalType(Date.class))
                    .as("java.util.Date should NOT be a supported temporal type")
                    .isFalse();
        }

        @Test
        @DisplayName("String should NOT be supported")
        void shouldNotSupportString() {
            assertThat(TemporalExpressionBuilder.isSupportedTemporalType(String.class))
                    .as("String should NOT be a supported temporal type")
                    .isFalse();
        }

        @Test
        @DisplayName("Integer should NOT be supported")
        void shouldNotSupportInteger() {
            assertThat(TemporalExpressionBuilder.isSupportedTemporalType(Integer.class))
                    .as("Integer should NOT be a supported temporal type")
                    .isFalse();
        }

        @Test
        @DisplayName("Object should NOT be supported")
        void shouldNotSupportObject() {
            assertThat(TemporalExpressionBuilder.isSupportedTemporalType(Object.class))
                    .as("Object should NOT be a supported temporal type")
                    .isFalse();
        }

        @Test
        @DisplayName("null should NOT be supported")
        void shouldNotSupportNull() {
            assertThat(TemporalExpressionBuilder.isSupportedTemporalType(null))
                    .as("null should NOT be a supported temporal type")
                    .isFalse();
        }
    }

    /**
     * Helper method to create a MethodCall AST node for testing.
     */
    private LambdaExpression.MethodCall createMethodCall(String methodName) {
        return new LambdaExpression.MethodCall(
                null, // target
                methodName,
                List.of(), // arguments
                Object.class // returnType
        );
    }
}
