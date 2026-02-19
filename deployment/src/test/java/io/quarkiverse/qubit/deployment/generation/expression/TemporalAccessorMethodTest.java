package io.quarkiverse.qubit.deployment.generation.expression;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Optional;

import jakarta.persistence.criteria.LocalDateField;
import jakarta.persistence.criteria.LocalDateTimeField;
import jakarta.persistence.criteria.LocalTimeField;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Unit tests for {@link TemporalAccessorMethod} enum.
 *
 * <p>
 * These tests ensure complete mutation coverage by testing:
 * <ul>
 * <li>{@link TemporalAccessorMethod#getJavaMethod()} for all enum values</li>
 * <li>{@link TemporalAccessorMethod#getExtractFieldName()} for all enum values</li>
 * <li>{@link TemporalAccessorMethod#fromJavaMethod(String)} with null, valid, and invalid inputs</li>
 * <li>{@link TemporalAccessorMethod#isTemporalAccessor(String)} for both true and false paths</li>
 * </ul>
 */
@DisplayName("TemporalAccessorMethod")
class TemporalAccessorMethodTest {

    // INSTANCE METHOD TESTS - getJavaMethod() and getExtractFieldName()

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
                "GET_SECOND, getSecond",
                "QUARTER, quarter",
                "WEEK, week"
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
    @DisplayName("getExtractFieldName()")
    class GetExtractFieldNameTests {

        @ParameterizedTest(name = "{0} should map to JPA 3.2 field \"{1}\"")
        @CsvSource({
                "GET_YEAR, YEAR",
                "GET_MONTH_VALUE, MONTH",
                "GET_DAY_OF_MONTH, DAY",
                "GET_HOUR, HOUR",
                "GET_MINUTE, MINUTE",
                "GET_SECOND, SECOND",
                "QUARTER, QUARTER",
                "WEEK, WEEK"
        })
        void shouldReturnCorrectExtractFieldName(TemporalAccessorMethod method, String expectedFieldName) {
            assertThat(method.getExtractFieldName())
                    .as("getExtractFieldName() for %s", method.name())
                    .isEqualTo(expectedFieldName);
        }

        @Test
        @DisplayName("all enum values should have non-null extract field names")
        void allEnumValuesShouldHaveNonNullExtractFieldNames() {
            for (TemporalAccessorMethod method : TemporalAccessorMethod.values()) {
                assertThat(method.getExtractFieldName())
                        .as("getExtractFieldName() for %s should not be null", method.name())
                        .isNotNull()
                        .isNotEmpty();
            }
        }

        @Test
        @DisplayName("date accessor fields should exist on LocalDateField")
        void dateAccessorFieldsShouldExistOnLocalDateField() throws NoSuchFieldException {
            assertThat(LocalDateField.class.getField("YEAR")).isNotNull();
            assertThat(LocalDateField.class.getField("MONTH")).isNotNull();
            assertThat(LocalDateField.class.getField("DAY")).isNotNull();
            assertThat(LocalDateField.class.getField("QUARTER")).isNotNull();
            assertThat(LocalDateField.class.getField("WEEK")).isNotNull();
        }

        @Test
        @DisplayName("time accessor fields should exist on LocalTimeField")
        void timeAccessorFieldsShouldExistOnLocalTimeField() throws NoSuchFieldException {
            assertThat(LocalTimeField.class.getField("HOUR")).isNotNull();
            assertThat(LocalTimeField.class.getField("MINUTE")).isNotNull();
            assertThat(LocalTimeField.class.getField("SECOND")).isNotNull();
        }

        @Test
        @DisplayName("all accessor fields should exist on LocalDateTimeField")
        void allAccessorFieldsShouldExistOnLocalDateTimeField() throws NoSuchFieldException {
            assertThat(LocalDateTimeField.class.getField("YEAR")).isNotNull();
            assertThat(LocalDateTimeField.class.getField("MONTH")).isNotNull();
            assertThat(LocalDateTimeField.class.getField("DAY")).isNotNull();
            assertThat(LocalDateTimeField.class.getField("HOUR")).isNotNull();
            assertThat(LocalDateTimeField.class.getField("MINUTE")).isNotNull();
            assertThat(LocalDateTimeField.class.getField("SECOND")).isNotNull();
            assertThat(LocalDateTimeField.class.getField("QUARTER")).isNotNull();
            assertThat(LocalDateTimeField.class.getField("WEEK")).isNotNull();
        }
    }

    // STATIC METHOD TESTS - fromJavaMethod()

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
                "getSecond, GET_SECOND",
                "quarter, QUARTER",
                "week, WEEK"
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
                "getYEAR", // case-sensitive
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

    // STATIC METHOD TESTS - isTemporalAccessor()

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
                "getSecond",
                "quarter",
                "week"
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
                "getTime", // not a supported method
                "getDate", // not a supported method
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

    // TemporalExpressionBuilder.getTemporalFieldClass() tests (package-private access)

    @Nested
    @DisplayName("getTemporalFieldClass()")
    class GetTemporalFieldClassTests {

        @Test
        @DisplayName("LocalDate should map to LocalDateField")
        void localDateShouldMapToLocalDateField() {
            assertThat(TemporalExpressionBuilder.getTemporalFieldClass(LocalDate.class))
                    .isEqualTo(LocalDateField.class);
        }

        @Test
        @DisplayName("LocalDateTime should map to LocalDateTimeField")
        void localDateTimeShouldMapToLocalDateTimeField() {
            assertThat(TemporalExpressionBuilder.getTemporalFieldClass(LocalDateTime.class))
                    .isEqualTo(LocalDateTimeField.class);
        }

        @Test
        @DisplayName("LocalTime should map to LocalTimeField")
        void localTimeShouldMapToLocalTimeField() {
            assertThat(TemporalExpressionBuilder.getTemporalFieldClass(LocalTime.class))
                    .isEqualTo(LocalTimeField.class);
        }

        @Test
        @DisplayName("unsupported types should return null")
        void unsupportedTypesShouldReturnNull() {
            assertThat(TemporalExpressionBuilder.getTemporalFieldClass(String.class)).isNull();
            assertThat(TemporalExpressionBuilder.getTemporalFieldClass(Object.class)).isNull();
        }
    }

    @Nested
    @DisplayName("enum completeness")
    class EnumCompletenessTests {

        @Test
        @DisplayName("should have exactly 8 temporal accessor methods")
        void shouldHaveExactlyEightValues() {
            assertThat(TemporalAccessorMethod.values())
                    .as("TemporalAccessorMethod should have 8 values (6 standard + QUARTER + WEEK)")
                    .hasSize(8);
        }
    }
}
