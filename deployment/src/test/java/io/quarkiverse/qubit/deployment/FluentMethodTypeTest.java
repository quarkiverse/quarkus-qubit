package io.quarkiverse.qubit.deployment;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link FluentMethodType}.
 * <p>
 * Tests the enum functionality including method name lookup, category checks,
 * and static enum sets.
 */
class FluentMethodTypeTest {

    // ========== fromMethodName Tests ==========

    @Nested
    @DisplayName("fromMethodName() lookup")
    class FromMethodNameTests {

        @Test
        @DisplayName("Returns WHERE for 'where'")
        void fromMethodName_where_returnsWhere() {
            Optional<FluentMethodType> result = FluentMethodType.fromMethodName("where");
            assertThat(result)
                    .isPresent()
                    .contains(FluentMethodType.WHERE);
        }

        @Test
        @DisplayName("Returns SELECT for 'select'")
        void fromMethodName_select_returnsSelect() {
            Optional<FluentMethodType> result = FluentMethodType.fromMethodName("select");
            assertThat(result)
                    .isPresent()
                    .contains(FluentMethodType.SELECT);
        }

        @Test
        @DisplayName("Returns SORTED_BY for 'sortedBy'")
        void fromMethodName_sortedBy_returnsSortedBy() {
            Optional<FluentMethodType> result = FluentMethodType.fromMethodName("sortedBy");
            assertThat(result)
                    .isPresent()
                    .contains(FluentMethodType.SORTED_BY);
        }

        @Test
        @DisplayName("Returns SORTED_DESCENDING_BY for 'sortedDescendingBy'")
        void fromMethodName_sortedDescendingBy_returnsSortedDescendingBy() {
            Optional<FluentMethodType> result = FluentMethodType.fromMethodName("sortedDescendingBy");
            assertThat(result)
                    .isPresent()
                    .contains(FluentMethodType.SORTED_DESCENDING_BY);
        }

        @Test
        @DisplayName("Returns MIN for 'min'")
        void fromMethodName_min_returnsMin() {
            Optional<FluentMethodType> result = FluentMethodType.fromMethodName("min");
            assertThat(result)
                    .isPresent()
                    .contains(FluentMethodType.MIN);
        }

        @Test
        @DisplayName("Returns MAX for 'max'")
        void fromMethodName_max_returnsMax() {
            Optional<FluentMethodType> result = FluentMethodType.fromMethodName("max");
            assertThat(result)
                    .isPresent()
                    .contains(FluentMethodType.MAX);
        }

        @Test
        @DisplayName("Returns AVG for 'avg'")
        void fromMethodName_avg_returnsAvg() {
            Optional<FluentMethodType> result = FluentMethodType.fromMethodName("avg");
            assertThat(result)
                    .isPresent()
                    .contains(FluentMethodType.AVG);
        }

        @Test
        @DisplayName("Returns SUM_INTEGER for 'sumInteger'")
        void fromMethodName_sumInteger_returnsSumInteger() {
            Optional<FluentMethodType> result = FluentMethodType.fromMethodName("sumInteger");
            assertThat(result)
                    .isPresent()
                    .contains(FluentMethodType.SUM_INTEGER);
        }

        @Test
        @DisplayName("Returns SUM_LONG for 'sumLong'")
        void fromMethodName_sumLong_returnsSumLong() {
            Optional<FluentMethodType> result = FluentMethodType.fromMethodName("sumLong");
            assertThat(result)
                    .isPresent()
                    .contains(FluentMethodType.SUM_LONG);
        }

        @Test
        @DisplayName("Returns SUM_DOUBLE for 'sumDouble'")
        void fromMethodName_sumDouble_returnsSumDouble() {
            Optional<FluentMethodType> result = FluentMethodType.fromMethodName("sumDouble");
            assertThat(result)
                    .isPresent()
                    .contains(FluentMethodType.SUM_DOUBLE);
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

        @Test
        @DisplayName("WHERE has PREDICATE category")
        void where_hasCategoryPredicate() {
            assertThat(FluentMethodType.WHERE.getCategory())
                    .isEqualTo(FluentMethodType.MethodCategory.PREDICATE);
        }

        @Test
        @DisplayName("SELECT has PROJECTION category")
        void select_hasCategoryProjection() {
            assertThat(FluentMethodType.SELECT.getCategory())
                    .isEqualTo(FluentMethodType.MethodCategory.PROJECTION);
        }

        @Test
        @DisplayName("SORTED_BY has SORTING category")
        void sortedBy_hasCategorySorting() {
            assertThat(FluentMethodType.SORTED_BY.getCategory())
                    .isEqualTo(FluentMethodType.MethodCategory.SORTING);
        }

        @Test
        @DisplayName("SORTED_DESCENDING_BY has SORTING category")
        void sortedDescendingBy_hasCategorySorting() {
            assertThat(FluentMethodType.SORTED_DESCENDING_BY.getCategory())
                    .isEqualTo(FluentMethodType.MethodCategory.SORTING);
        }

        @Test
        @DisplayName("MIN has AGGREGATION category")
        void min_hasCategoryAggregation() {
            assertThat(FluentMethodType.MIN.getCategory())
                    .isEqualTo(FluentMethodType.MethodCategory.AGGREGATION);
        }

        @Test
        @DisplayName("MAX has AGGREGATION category")
        void max_hasCategoryAggregation() {
            assertThat(FluentMethodType.MAX.getCategory())
                    .isEqualTo(FluentMethodType.MethodCategory.AGGREGATION);
        }

        @Test
        @DisplayName("AVG has AGGREGATION category")
        void avg_hasCategoryAggregation() {
            assertThat(FluentMethodType.AVG.getCategory())
                    .isEqualTo(FluentMethodType.MethodCategory.AGGREGATION);
        }

        @Test
        @DisplayName("SUM_INTEGER has AGGREGATION category")
        void sumInteger_hasCategoryAggregation() {
            assertThat(FluentMethodType.SUM_INTEGER.getCategory())
                    .isEqualTo(FluentMethodType.MethodCategory.AGGREGATION);
        }

        @Test
        @DisplayName("SUM_LONG has AGGREGATION category")
        void sumLong_hasCategoryAggregation() {
            assertThat(FluentMethodType.SUM_LONG.getCategory())
                    .isEqualTo(FluentMethodType.MethodCategory.AGGREGATION);
        }

        @Test
        @DisplayName("SUM_DOUBLE has AGGREGATION category")
        void sumDouble_hasCategoryAggregation() {
            assertThat(FluentMethodType.SUM_DOUBLE.getCategory())
                    .isEqualTo(FluentMethodType.MethodCategory.AGGREGATION);
        }

        @Test
        @DisplayName("isAggregation returns true for aggregation methods")
        void isAggregation_forAggregationMethods_returnsTrue() {
            assertThat(FluentMethodType.MIN.isAggregation()).isTrue();
            assertThat(FluentMethodType.MAX.isAggregation()).isTrue();
            assertThat(FluentMethodType.AVG.isAggregation()).isTrue();
            assertThat(FluentMethodType.SUM_INTEGER.isAggregation()).isTrue();
            assertThat(FluentMethodType.SUM_LONG.isAggregation()).isTrue();
            assertThat(FluentMethodType.SUM_DOUBLE.isAggregation()).isTrue();
        }

        @Test
        @DisplayName("isAggregation returns false for non-aggregation methods")
        void isAggregation_forNonAggregationMethods_returnsFalse() {
            assertThat(FluentMethodType.WHERE.isAggregation()).isFalse();
            assertThat(FluentMethodType.SELECT.isAggregation()).isFalse();
            assertThat(FluentMethodType.SORTED_BY.isAggregation()).isFalse();
            assertThat(FluentMethodType.SORTED_DESCENDING_BY.isAggregation()).isFalse();
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
        @DisplayName("ENTRY_POINTS contains all enum values")
        void entryPoints_containsAllValues() {
            assertThat(FluentMethodType.ENTRY_POINTS)
                    .containsExactlyInAnyOrder(FluentMethodType.values());
        }

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
