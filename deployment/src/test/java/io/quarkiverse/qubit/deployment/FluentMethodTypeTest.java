package io.quarkiverse.qubit.deployment;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link FluentMethodType}.
 * <p>
 * Tests the enum functionality including method name lookup, category checks,
 * and static enum sets.
 */
class FluentMethodTypeTest {

    // ==================== Parameterized Test Data ====================

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
                Arguments.of("sumDouble", FluentMethodType.SUM_DOUBLE)
        );
    }

    /** FluentMethodType to MethodCategory mappings for category tests. */
    static Stream<Arguments> categoryMappings() {
        return Stream.of(
                Arguments.of(FluentMethodType.WHERE, FluentMethodType.MethodCategory.PREDICATE),
                Arguments.of(FluentMethodType.SELECT, FluentMethodType.MethodCategory.PROJECTION),
                Arguments.of(FluentMethodType.SORTED_BY, FluentMethodType.MethodCategory.SORTING),
                Arguments.of(FluentMethodType.SORTED_DESCENDING_BY, FluentMethodType.MethodCategory.SORTING),
                Arguments.of(FluentMethodType.MIN, FluentMethodType.MethodCategory.AGGREGATION),
                Arguments.of(FluentMethodType.MAX, FluentMethodType.MethodCategory.AGGREGATION),
                Arguments.of(FluentMethodType.AVG, FluentMethodType.MethodCategory.AGGREGATION),
                Arguments.of(FluentMethodType.SUM_INTEGER, FluentMethodType.MethodCategory.AGGREGATION),
                Arguments.of(FluentMethodType.SUM_LONG, FluentMethodType.MethodCategory.AGGREGATION),
                Arguments.of(FluentMethodType.SUM_DOUBLE, FluentMethodType.MethodCategory.AGGREGATION)
        );
    }

    // ========== fromMethodName Tests ==========

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
        @ValueSource(strings = {"unknown", "UNKNOWN", "Where", "WHERE", "find", "query", " where"})
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

    // ========== Category Tests ==========

    @Nested
    @DisplayName("Category and isAggregation()")
    class CategoryTests {

        @ParameterizedTest(name = "{0} → {1}")
        @MethodSource("io.quarkiverse.qubit.deployment.FluentMethodTypeTest#categoryMappings")
        @DisplayName("FluentMethodType has correct category")
        void methodType_hasCorrectCategory(FluentMethodType type, FluentMethodType.MethodCategory expectedCategory) {
            assertThat(type.getCategory()).isEqualTo(expectedCategory);
        }

        @ParameterizedTest
        @EnumSource(value = FluentMethodType.class, names = {"MIN", "MAX", "AVG", "SUM_INTEGER", "SUM_LONG", "SUM_DOUBLE"})
        @DisplayName("isAggregation returns true for aggregation methods")
        void isAggregation_forAggregationMethods_returnsTrue(FluentMethodType type) {
            assertThat(type.isAggregation()).isTrue();
        }

        @ParameterizedTest
        @EnumSource(value = FluentMethodType.class, names = {"WHERE", "SELECT", "SORTED_BY", "SORTED_DESCENDING_BY"})
        @DisplayName("isAggregation returns false for non-aggregation methods")
        void isAggregation_forNonAggregationMethods_returnsFalse(FluentMethodType type) {
            assertThat(type.isAggregation()).isFalse();
        }

        @ParameterizedTest
        @EnumSource(FluentMethodType.class)
        @DisplayName("Every enum value has a non-null category")
        void allValues_haveNonNullCategory(FluentMethodType type) {
            assertThat(type.getCategory())
                    .as("%s should have a non-null category", type)
                    .isNotNull();
        }
    }

    // ========== EnumSet Constants Tests ==========

    @Nested
    @DisplayName("EnumSet Constants")
    class EnumSetConstantsTests {

        @Test
        @DisplayName("AGGREGATIONS contains exactly the aggregation methods")
        void aggregations_containsCorrectMethods() {
            assertThat(FluentMethodType.AGGREGATIONS)
                    .containsExactlyInAnyOrder(
                            FluentMethodType.MIN,
                            FluentMethodType.MAX,
                            FluentMethodType.AVG,
                            FluentMethodType.SUM_INTEGER,
                            FluentMethodType.SUM_LONG,
                            FluentMethodType.SUM_DOUBLE
                    );
        }

        @Test
        @DisplayName("SORTING contains exactly the sorting methods")
        void sorting_containsCorrectMethods() {
            assertThat(FluentMethodType.SORTING)
                    .containsExactlyInAnyOrder(
                            FluentMethodType.SORTED_BY,
                            FluentMethodType.SORTED_DESCENDING_BY
                    );
        }

        @Test
        @DisplayName("AGGREGATIONS matches isAggregation() for all values")
        void aggregations_matchesIsAggregationMethod() {
            for (FluentMethodType type : FluentMethodType.values()) {
                boolean inSet = FluentMethodType.AGGREGATIONS.contains(type);
                boolean isAggregation = type.isAggregation();
                assertThat(inSet)
                        .as("%s: AGGREGATIONS.contains() should equal isAggregation()", type)
                        .isEqualTo(isAggregation);
            }
        }

        @Test
        @DisplayName("AGGREGATIONS contains only AGGREGATION category members")
        void aggregations_containsOnlyAggregationCategory() {
            for (FluentMethodType type : FluentMethodType.AGGREGATIONS) {
                assertThat(type.getCategory())
                        .as("%s in AGGREGATIONS should have AGGREGATION category", type)
                        .isEqualTo(FluentMethodType.MethodCategory.AGGREGATION);
            }
        }

        @Test
        @DisplayName("SORTING contains only SORTING category members")
        void sorting_containsOnlySortingCategory() {
            for (FluentMethodType type : FluentMethodType.SORTING) {
                assertThat(type.getCategory())
                        .as("%s in SORTING should have SORTING category", type)
                        .isEqualTo(FluentMethodType.MethodCategory.SORTING);
            }
        }
    }

    // ========== MethodCategory Tests ==========

    @Nested
    @DisplayName("MethodCategory enum")
    class MethodCategoryTests {

        @Test
        @DisplayName("All four categories exist")
        void allCategoriesExist() {
            assertThat(FluentMethodType.MethodCategory.values())
                    .hasSize(4)
                    .containsExactlyInAnyOrder(
                            FluentMethodType.MethodCategory.PREDICATE,
                            FluentMethodType.MethodCategory.PROJECTION,
                            FluentMethodType.MethodCategory.SORTING,
                            FluentMethodType.MethodCategory.AGGREGATION
                    );
        }

        @Test
        @DisplayName("Each category has at least one FluentMethodType")
        void eachCategoryHasAtLeastOneMethod() {
            for (FluentMethodType.MethodCategory category : FluentMethodType.MethodCategory.values()) {
                boolean hasMethod = false;
                for (FluentMethodType type : FluentMethodType.values()) {
                    if (type.getCategory() == category) {
                        hasMethod = true;
                        break;
                    }
                }
                assertThat(hasMethod)
                        .as("Category %s should have at least one FluentMethodType", category)
                        .isTrue();
            }
        }
    }

    // ========== Method Name Tests ==========

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
