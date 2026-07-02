package io.quarkiverse.qubit.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Unit tests for {@link FluentMethodType}.
 */
class FluentMethodTypeTest {

    /** Method name to FluentMethodType mappings for lookup tests. */
    static Stream<Arguments> methodNameMappings() {
        return Stream.of(
                Arguments.of("where", FluentMethodType.WHERE),
                Arguments.of("select", FluentMethodType.SELECT),
                Arguments.of("sortedBy", FluentMethodType.SORTED_BY),
                Arguments.of("sortedDescendingBy", FluentMethodType.SORTED_DESCENDING_BY),
                Arguments.of("min", FluentMethodType.MIN),
                Arguments.of("max", FluentMethodType.MAX),
                Arguments.of("avg", FluentMethodType.AVG),
                Arguments.of("sumInteger", FluentMethodType.SUM_INTEGER),
                Arguments.of("sumLong", FluentMethodType.SUM_LONG),
                Arguments.of("sumDouble", FluentMethodType.SUM_DOUBLE));
    }

    @Nested
    @DisplayName("fromMethodName() lookup")
    class FromMethodNameTests {

        @ParameterizedTest(name = "fromMethodName(\"{0}\") → {1}")
        @MethodSource("io.quarkiverse.qubit.deployment.FluentMethodTypeTest#methodNameMappings")
        @DisplayName("Returns correct FluentMethodType for known method names")
        void fromMethodName_knownMethodName_returnsCorrectType(String methodName, FluentMethodType expectedType) {
            Optional<FluentMethodType> result = FluentMethodType.fromMethodName(methodName);
            assertThat(result)
                    .isPresent()
                    .contains(expectedType);
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = { "unknown", "UNKNOWN", "Where", "WHERE", "find", "query", " where" })
        @DisplayName("Returns empty for unknown or invalid method names")
        void fromMethodName_unknownOrInvalid_returnsEmpty(String methodName) {
            Optional<FluentMethodType> result = FluentMethodType.fromMethodName(methodName);
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("All enum values have unique method names")
        void allEnumValues_haveUniqueMethodNames() {
            FluentMethodType[] values = FluentMethodType.values();
            long distinctCount = java.util.Arrays.stream(values)
                    .map(FluentMethodType::getMethodName)
                    .distinct()
                    .count();
            assertThat(distinctCount).isEqualTo(values.length);
        }

        @ParameterizedTest
        @EnumSource(FluentMethodType.class)
        @DisplayName("Lookup by getMethodName returns same enum value")
        void fromMethodName_forEachEnumValue_returnsCorrectValue(FluentMethodType type) {
            String methodName = type.getMethodName();
            Optional<FluentMethodType> result = FluentMethodType.fromMethodName(methodName);
            assertThat(result)
                    .as("Lookup for method name '%s' should return %s", methodName, type)
                    .isPresent()
                    .contains(type);
        }
    }

    @Nested
    @DisplayName("getMethodName()")
    class GetMethodNameTests {

        @ParameterizedTest
        @EnumSource(FluentMethodType.class)
        @DisplayName("Every enum value has a non-null, non-empty method name")
        void allValues_haveNonEmptyMethodName(FluentMethodType type) {
            assertThat(type.getMethodName())
                    .as("%s should have a non-null method name", type)
                    .isNotNull()
                    .isNotEmpty();
        }

        @Test
        @DisplayName("Method names are lowercase or camelCase")
        void methodNames_areCamelCase() {
            for (FluentMethodType type : FluentMethodType.values()) {
                String methodName = type.getMethodName();
                // First character should be lowercase
                assertThat(Character.isLowerCase(methodName.charAt(0)))
                        .as("%s method name '%s' should start with lowercase", type, methodName)
                        .isTrue();
            }
        }
    }
}
