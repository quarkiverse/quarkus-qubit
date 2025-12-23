package io.quarkiverse.qubit.deployment.generation.expression;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import io.quarkiverse.qubit.deployment.generation.MethodDescriptors;
import io.quarkus.gizmo.MethodDescriptor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Unit tests for {@link TemporalAccessorMethod} enum.
 *
 * <p>These tests ensure complete mutation coverage by testing:
 * <ul>
 *   <li>{@link TemporalAccessorMethod#getJavaMethod()} for all enum values</li>
 *   <li>{@link TemporalAccessorMethod#getMethodDescriptor()} for all enum values</li>
 *   <li>{@link TemporalAccessorMethod#fromJavaMethod(String)} with null, valid, and invalid inputs</li>
 *   <li>{@link TemporalAccessorMethod#isTemporalAccessor(String)} for both true and false paths</li>
 *   <li>EnumSet constants (DATE_METHODS, TIME_METHODS, ALL)</li>
 * </ul>
 */
@DisplayName("TemporalAccessorMethod")
class TemporalAccessorMethodTest {

    // =============================================================================================
    // INSTANCE METHOD TESTS - getJavaMethod() and getMethodDescriptor()
    // =============================================================================================

    @Nested
    @DisplayName("getJavaMethod()")
    class GetJavaMethodTests {

        @ParameterizedTest(name = "{0} should return \"{1}\"")
        @CsvSource({
            "GET_YEAR, getYear",
            "GET_MONTH_VALUE, getMonthValue",
            "GET_DAY_OF_MONTH, getDayOfMonth",
            "GET_HOUR, getHour",
            "GET_MINUTE, getMinute",
            "GET_SECOND, getSecond"
        })
        void shouldReturnCorrectJavaMethodName(TemporalAccessorMethod method, String expectedJavaMethod) {
            assertThat(method.getJavaMethod())
                    .as("getJavaMethod() for %s", method.name())
                    .isEqualTo(expectedJavaMethod);
        }

        @Test
        @DisplayName("all enum values should have non-null Java method names")
        void allEnumValuesShouldHaveNonNullJavaMethodNames() {
            for (TemporalAccessorMethod method : TemporalAccessorMethod.values()) {
                assertThat(method.getJavaMethod())
                        .as("getJavaMethod() for %s should not be null", method.name())
                        .isNotNull()
                        .isNotEmpty();
            }
        }
    }

    @Nested
    @DisplayName("getMethodDescriptor()")
    class GetMethodDescriptorTests {

        @Test
        @DisplayName("GET_YEAR should return HCB_YEAR descriptor")
        void getYear_shouldReturnHcbYearDescriptor() {
            assertThat(TemporalAccessorMethod.GET_YEAR.getMethodDescriptor())
                    .as("getMethodDescriptor() for GET_YEAR")
                    .isSameAs(MethodDescriptors.HCB_YEAR);
        }

        @Test
        @DisplayName("GET_MONTH_VALUE should return HCB_MONTH descriptor")
        void getMonthValue_shouldReturnHcbMonthDescriptor() {
            assertThat(TemporalAccessorMethod.GET_MONTH_VALUE.getMethodDescriptor())
                    .as("getMethodDescriptor() for GET_MONTH_VALUE")
                    .isSameAs(MethodDescriptors.HCB_MONTH);
        }

        @Test
        @DisplayName("GET_DAY_OF_MONTH should return HCB_DAY descriptor")
        void getDayOfMonth_shouldReturnHcbDayDescriptor() {
            assertThat(TemporalAccessorMethod.GET_DAY_OF_MONTH.getMethodDescriptor())
                    .as("getMethodDescriptor() for GET_DAY_OF_MONTH")
                    .isSameAs(MethodDescriptors.HCB_DAY);
        }

        @Test
        @DisplayName("GET_HOUR should return HCB_HOUR descriptor")
        void getHour_shouldReturnHcbHourDescriptor() {
            assertThat(TemporalAccessorMethod.GET_HOUR.getMethodDescriptor())
                    .as("getMethodDescriptor() for GET_HOUR")
                    .isSameAs(MethodDescriptors.HCB_HOUR);
        }

        @Test
        @DisplayName("GET_MINUTE should return HCB_MINUTE descriptor")
        void getMinute_shouldReturnHcbMinuteDescriptor() {
            assertThat(TemporalAccessorMethod.GET_MINUTE.getMethodDescriptor())
                    .as("getMethodDescriptor() for GET_MINUTE")
                    .isSameAs(MethodDescriptors.HCB_MINUTE);
        }

        @Test
        @DisplayName("GET_SECOND should return HCB_SECOND descriptor")
        void getSecond_shouldReturnHcbSecondDescriptor() {
            assertThat(TemporalAccessorMethod.GET_SECOND.getMethodDescriptor())
                    .as("getMethodDescriptor() for GET_SECOND")
                    .isSameAs(MethodDescriptors.HCB_SECOND);
        }

        @Test
        @DisplayName("all enum values should have non-null MethodDescriptors")
        void allEnumValuesShouldHaveNonNullMethodDescriptors() {
            for (TemporalAccessorMethod method : TemporalAccessorMethod.values()) {
                assertThat(method.getMethodDescriptor())
                        .as("getMethodDescriptor() for %s should not be null", method.name())
                        .isNotNull();
            }
        }

        @Test
        @DisplayName("all enum values should have MethodDescriptors for HibernateCriteriaBuilder")
        void allEnumValuesShouldHaveHibernateCriteriaBuilderDescriptors() {
            for (TemporalAccessorMethod method : TemporalAccessorMethod.values()) {
                MethodDescriptor descriptor = method.getMethodDescriptor();
                assertThat(descriptor.getDeclaringClass())
                        .as("getMethodDescriptor() for %s should be for HibernateCriteriaBuilder", method.name())
                        .isEqualTo("org/hibernate/query/criteria/HibernateCriteriaBuilder");
            }
        }
    }

    // =============================================================================================
    // STATIC METHOD TESTS - fromJavaMethod()
    // =============================================================================================

    @Nested
    @DisplayName("fromJavaMethod()")
    class FromJavaMethodTests {

        @Test
        @DisplayName("should return empty Optional for null input")
        void shouldReturnEmptyForNullInput() {
            Optional<TemporalAccessorMethod> result = TemporalAccessorMethod.fromJavaMethod(null);

            assertThat(result)
                    .as("fromJavaMethod(null) should return empty Optional")
                    .isEmpty();
        }

        @ParameterizedTest(name = "fromJavaMethod(\"{0}\") should return {1}")
        @CsvSource({
            "getYear, GET_YEAR",
            "getMonthValue, GET_MONTH_VALUE",
            "getDayOfMonth, GET_DAY_OF_MONTH",
            "getHour, GET_HOUR",
            "getMinute, GET_MINUTE",
            "getSecond, GET_SECOND"
        })
        void shouldReturnCorrectEnumValueForValidMethodName(String methodName, TemporalAccessorMethod expected) {
            Optional<TemporalAccessorMethod> result = TemporalAccessorMethod.fromJavaMethod(methodName);

            assertThat(result)
                    .as("fromJavaMethod(\"%s\") should return %s", methodName, expected.name())
                    .isPresent()
                    .contains(expected);
        }

        @ParameterizedTest(name = "fromJavaMethod(\"{0}\") should return empty")
        @ValueSource(strings = {
            "invalidMethod",
            "getYEAR",  // case-sensitive
            "GETYEAR",
            "get_year",
            "year",
            "",
            "   "
        })
        void shouldReturnEmptyForInvalidMethodName(String methodName) {
            Optional<TemporalAccessorMethod> result = TemporalAccessorMethod.fromJavaMethod(methodName);

            assertThat(result)
                    .as("fromJavaMethod(\"%s\") should return empty Optional", methodName)
                    .isEmpty();
        }
    }

    // =============================================================================================
    // STATIC METHOD TESTS - isTemporalAccessor()
    // =============================================================================================

    @Nested
    @DisplayName("isTemporalAccessor()")
    class IsTemporalAccessorTests {

        @ParameterizedTest(name = "isTemporalAccessor(\"{0}\") should return true")
        @ValueSource(strings = {
            "getYear",
            "getMonthValue",
            "getDayOfMonth",
            "getHour",
            "getMinute",
            "getSecond"
        })
        void shouldReturnTrueForValidTemporalAccessorMethods(String methodName) {
            boolean result = TemporalAccessorMethod.isTemporalAccessor(methodName);

            assertThat(result)
                    .as("isTemporalAccessor(\"%s\") should return true", methodName)
                    .isTrue();
        }

        @ParameterizedTest(name = "isTemporalAccessor(\"{0}\") should return false")
        @ValueSource(strings = {
            "invalidMethod",
            "toString",
            "hashCode",
            "equals",
            "getTime",  // not a supported method
            "getDate",  // not a supported method
            ""
        })
        void shouldReturnFalseForInvalidMethods(String methodName) {
            boolean result = TemporalAccessorMethod.isTemporalAccessor(methodName);

            assertThat(result)
                    .as("isTemporalAccessor(\"%s\") should return false", methodName)
                    .isFalse();
        }

        @Test
        @DisplayName("should return false for null input")
        void shouldReturnFalseForNullInput() {
            boolean result = TemporalAccessorMethod.isTemporalAccessor(null);

            assertThat(result)
                    .as("isTemporalAccessor(null) should return false")
                    .isFalse();
        }
    }

    // =============================================================================================
    // ENUMSET CONSTANT TESTS
    // =============================================================================================

    @Nested
    @DisplayName("EnumSet Constants")
    class EnumSetConstantTests {

        @Test
        @DisplayName("DATE_METHODS should contain exactly year, month, day methods")
        void dateMethods_shouldContainCorrectMethods() {
            assertThat(TemporalAccessorMethod.DATE_METHODS)
                    .as("DATE_METHODS should contain date component methods")
                    .containsExactlyInAnyOrder(
                            TemporalAccessorMethod.GET_YEAR,
                            TemporalAccessorMethod.GET_MONTH_VALUE,
                            TemporalAccessorMethod.GET_DAY_OF_MONTH
                    );
        }

        @Test
        @DisplayName("TIME_METHODS should contain exactly hour, minute, second methods")
        void timeMethods_shouldContainCorrectMethods() {
            assertThat(TemporalAccessorMethod.TIME_METHODS)
                    .as("TIME_METHODS should contain time component methods")
                    .containsExactlyInAnyOrder(
                            TemporalAccessorMethod.GET_HOUR,
                            TemporalAccessorMethod.GET_MINUTE,
                            TemporalAccessorMethod.GET_SECOND
                    );
        }

        @Test
        @DisplayName("ALL should contain all 6 temporal accessor methods")
        void all_shouldContainAllMethods() {
            assertThat(TemporalAccessorMethod.ALL)
                    .as("ALL should contain all temporal accessor methods")
                    .hasSize(6)
                    .containsExactlyInAnyOrder(TemporalAccessorMethod.values());
        }

        @Test
        @DisplayName("DATE_METHODS and TIME_METHODS should be disjoint")
        void dateMethods_and_timeMethods_shouldBeDisjoint() {
            assertThat(TemporalAccessorMethod.DATE_METHODS)
                    .as("DATE_METHODS should have no overlap with TIME_METHODS")
                    .doesNotContainAnyElementsOf(TemporalAccessorMethod.TIME_METHODS);
        }

        @Test
        @DisplayName("DATE_METHODS combined with TIME_METHODS should equal ALL")
        void dateAndTimeMethods_shouldEqualAll() {
            var combined = java.util.EnumSet.copyOf(TemporalAccessorMethod.DATE_METHODS);
            combined.addAll(TemporalAccessorMethod.TIME_METHODS);

            assertThat(combined)
                    .as("DATE_METHODS + TIME_METHODS should equal ALL")
                    .isEqualTo(TemporalAccessorMethod.ALL);
        }
    }
}
