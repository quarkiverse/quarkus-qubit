package io.quarkiverse.qubit.deployment.generation.expression;

import static org.assertj.core.api.Assertions.assertThat;

import io.quarkiverse.qubit.deployment.ast.LambdaExpression;
import io.quarkiverse.qubit.deployment.generation.expression.StringExpressionBuilder.StringOperationType;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

/**
 * Unit tests for {@link StringExpressionBuilder}.
 *
 * <p>Note: The {@code getOperationType()} method is currently dead code (never called
 * in production) as identified in ENUM-006 analysis. These tests ensure the method
 * works correctly if it becomes needed in the future, and they kill mutations
 * that would otherwise survive.
 *
 * <p>The bytecode generation methods ({@code buildStringTransformation},
 * {@code buildStringPattern}, {@code buildStringSubstring}, {@code buildStringUtility})
 * require integration tests with Quarkus Gizmo framework and are tested via
 * {@code StringOperationsCriteriaTest} and {@code StringOperationsBytecodeTest}.
 */
@DisplayName("StringExpressionBuilder")
class StringExpressionBuilderTest {

    private StringExpressionBuilder builder;

    @BeforeEach
    void setUp() {
        builder = new StringExpressionBuilder();
    }

    @Nested
    @DisplayName("getOperationType()")
    class GetOperationTypeTests {

        @ParameterizedTest(name = "{0} should return TRANSFORMATION")
        @ValueSource(strings = { "toLowerCase", "toUpperCase", "trim" })
        void shouldReturnTransformationForTransformationMethods(String methodName) {
            LambdaExpression.MethodCall methodCall = createMethodCall(methodName);

            StringOperationType result = builder.getOperationType(methodCall);

            assertThat(result)
                    .as("getOperationType(\"%s\") should return TRANSFORMATION", methodName)
                    .isEqualTo(StringOperationType.TRANSFORMATION);
        }

        @ParameterizedTest(name = "{0} should return PATTERN")
        @ValueSource(strings = { "startsWith", "endsWith", "contains" })
        void shouldReturnPatternForPatternMethods(String methodName) {
            LambdaExpression.MethodCall methodCall = createMethodCall(methodName);

            StringOperationType result = builder.getOperationType(methodCall);

            assertThat(result)
                    .as("getOperationType(\"%s\") should return PATTERN", methodName)
                    .isEqualTo(StringOperationType.PATTERN);
        }

        @Test
        @DisplayName("substring should return SUBSTRING")
        void shouldReturnSubstringForSubstringMethod() {
            LambdaExpression.MethodCall methodCall = createMethodCall("substring");

            StringOperationType result = builder.getOperationType(methodCall);

            assertThat(result)
                    .as("getOperationType(\"substring\") should return SUBSTRING")
                    .isEqualTo(StringOperationType.SUBSTRING);
        }

        @ParameterizedTest(name = "{0} should return UTILITY")
        @ValueSource(strings = { "equals", "length", "isEmpty" })
        void shouldReturnUtilityForUtilityMethods(String methodName) {
            LambdaExpression.MethodCall methodCall = createMethodCall(methodName);

            StringOperationType result = builder.getOperationType(methodCall);

            assertThat(result)
                    .as("getOperationType(\"%s\") should return UTILITY", methodName)
                    .isEqualTo(StringOperationType.UTILITY);
        }

        @ParameterizedTest(name = "{0} should return null")
        @ValueSource(strings = { "hashCode", "toString", "charAt", "split", "replace", "matches", "" })
        void shouldReturnNullForUnknownMethods(String methodName) {
            LambdaExpression.MethodCall methodCall = createMethodCall(methodName);

            StringOperationType result = builder.getOperationType(methodCall);

            assertThat(result)
                    .as("getOperationType(\"%s\") should return null", methodName)
                    .isNull();
        }

        @Test
        @DisplayName("should differentiate all operation types correctly")
        void shouldDifferentiateAllOperationTypes() {
            // Test each type once to verify no cross-contamination
            assertThat(builder.getOperationType(createMethodCall("toLowerCase")))
                    .isEqualTo(StringOperationType.TRANSFORMATION);
            assertThat(builder.getOperationType(createMethodCall("startsWith")))
                    .isEqualTo(StringOperationType.PATTERN);
            assertThat(builder.getOperationType(createMethodCall("substring")))
                    .isEqualTo(StringOperationType.SUBSTRING);
            assertThat(builder.getOperationType(createMethodCall("equals")))
                    .isEqualTo(StringOperationType.UTILITY);
            assertThat(builder.getOperationType(createMethodCall("unknownMethod")))
                    .isNull();
        }
    }

    @Nested
    @DisplayName("StringOperationType enum")
    class StringOperationTypeTests {

        @Test
        @DisplayName("should have exactly 4 values")
        void shouldHaveExactlyFourValues() {
            assertThat(StringOperationType.values())
                    .as("StringOperationType should have 4 values")
                    .hasSize(4)
                    .containsExactly(
                            StringOperationType.TRANSFORMATION,
                            StringOperationType.PATTERN,
                            StringOperationType.SUBSTRING,
                            StringOperationType.UTILITY
                    );
        }

        @ParameterizedTest(name = "valueOf(\"{0}\") should return {0}")
        @CsvSource({
            "TRANSFORMATION",
            "PATTERN",
            "SUBSTRING",
            "UTILITY"
        })
        void shouldResolveValueOfCorrectly(String name) {
            assertThat(StringOperationType.valueOf(name))
                    .as("StringOperationType.valueOf(\"%s\")", name)
                    .isNotNull();
        }
    }

    /**
     * Helper method to create a mock MethodCall with the given method name.
     */
    private LambdaExpression.MethodCall createMethodCall(String methodName) {
        // Use a minimal MethodCall with null target (not used by getOperationType)
        return new LambdaExpression.MethodCall(
                null,                    // target (not used)
                methodName,              // methodName
                List.of(),               // arguments (not used)
                String.class             // returnType (not used)
        );
    }
}
