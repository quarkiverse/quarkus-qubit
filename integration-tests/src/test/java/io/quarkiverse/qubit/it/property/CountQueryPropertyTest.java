package io.quarkiverse.qubit.it.property;

import io.quarkiverse.qubit.it.Person;
import io.quarkiverse.qubit.it.Product;
import io.quarkiverse.qubit.it.testdata.TestDataFactory;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based integration tests for count query operations.
 *
 * <p>This class uses parameterized tests to verify count invariants
 * that must hold for various query configurations.
 *
 * <p><strong>Properties Tested:</strong>
 * <ul>
 *   <li><strong>Count-list consistency</strong>: count() == toList().size()</li>
 *   <li><strong>Non-negativity</strong>: count() >= 0</li>
 *   <li><strong>Filter monotonicity</strong>: count(filter1 AND filter2) <= count(filter1)</li>
 *   <li><strong>Pagination independence</strong>: count ignores skip/limit</li>
 *   <li><strong>Sort independence</strong>: count ignores sortedBy</li>
 * </ul>
 *
 * @see io.quarkiverse.qubit.it.fluent.BasicQueryTest
 */
@QuarkusTest
class CountQueryPropertyTest {

    @Inject
    EntityManager em;

    @BeforeEach
    @Transactional
    void setup() {
        TestDataFactory.clearAllData();
        TestDataFactory.createAdditionalPersons();
        TestDataFactory.createStandardProducts();
    }

    // ======================================================================
    // Count-List Consistency Properties
    // ======================================================================

    @Test
    @Transactional
    void countEqualsListSizeUnfiltered() {
        long count = Person.count();
        int listSize = Person.listAll().size();

        assertThat(count)
                .as("count() should equal toList().size()")
                .isEqualTo(listSize);
    }

    @Test
    @Transactional
    void countEqualsListSizeFiltered() {
        long count = Person.where((Person p) -> p.active).count();
        int listSize = Person.where((Person p) -> p.active).toList().size();

        assertThat(count)
                .as("Filtered count() should equal filtered toList().size()")
                .isEqualTo(listSize);
    }

    @Test
    @Transactional
    void countEqualsListSizeMultiFiltered() {
        long count = Person.where((Person p) -> p.active)
                .where((Person p) -> p.age > 25)
                .count();
        int listSize = Person.where((Person p) -> p.active)
                .where((Person p) -> p.age > 25)
                .toList().size();

        assertThat(count)
                .as("Multi-filtered count() should equal toList().size()")
                .isEqualTo(listSize);
    }

    @Test
    @Transactional
    void productCountEqualsListSize() {
        long count = Product.where((Product p) -> p.available).count();
        int listSize = Product.where((Product p) -> p.available).toList().size();

        assertThat(count)
                .as("Product count() should equal toList().size()")
                .isEqualTo(listSize);
    }

    // ======================================================================
    // Non-Negativity Property
    // ======================================================================

    @Test
    @Transactional
    void countIsNonNegative() {
        long unfilteredCount = Person.count();
        long filteredCount = Person.where((Person p) -> p.active).count();
        long impossibleCount = Person.where((Person p) -> p.age < 0).count();

        assertThat(unfilteredCount)
                .as("Unfiltered count should be non-negative")
                .isGreaterThanOrEqualTo(0);
        assertThat(filteredCount)
                .as("Filtered count should be non-negative")
                .isGreaterThanOrEqualTo(0);
        assertThat(impossibleCount)
                .as("Impossible filter count should be non-negative (zero)")
                .isEqualTo(0);
    }

    // ======================================================================
    // Filter Monotonicity Property
    // ======================================================================

    @Test
    @Transactional
    void addingFiltersNeverIncreasesCount() {
        long noFilterCount = Person.count();
        long oneFilterCount = Person.where((Person p) -> p.active).count();
        long twoFilterCount = Person.where((Person p) -> p.active)
                .where((Person p) -> p.age > 25)
                .count();

        assertThat(oneFilterCount)
                .as("One filter count <= no filter count")
                .isLessThanOrEqualTo(noFilterCount);
        assertThat(twoFilterCount)
                .as("Two filter count <= one filter count")
                .isLessThanOrEqualTo(oneFilterCount);
    }

    // ======================================================================
    // Pagination Independence Properties
    // ======================================================================

    @ParameterizedTest(name = "count ignores skip({0})")
    @ValueSource(ints = {0, 1, 2, 5, 10})
    @Transactional
    void countIgnoresSkip(int skipAmount) {
        long countWithoutSkip = Person.where((Person p) -> p.active).count();
        long countWithSkip = Person.where((Person p) -> p.active).skip(skipAmount).count();

        assertThat(countWithSkip)
                .as("count() should ignore skip(%d)", skipAmount)
                .isEqualTo(countWithoutSkip);
    }

    @ParameterizedTest(name = "count ignores limit({0})")
    @ValueSource(ints = {1, 2, 5, 10})
    @Transactional
    void countIgnoresLimit(int limitAmount) {
        long countWithoutLimit = Person.where((Person p) -> p.active).count();
        long countWithLimit = Person.where((Person p) -> p.active).limit(limitAmount).count();

        assertThat(countWithLimit)
                .as("count() should ignore limit(%d)", limitAmount)
                .isEqualTo(countWithoutLimit);
    }

    @Test
    @Transactional
    void countIgnoresSkipAndLimit() {
        int[][] testCases = {{0, 2}, {1, 2}, {2, 3}, {5, 5}};

        for (int[] testCase : testCases) {
            int skipAmount = testCase[0];
            int limitAmount = testCase[1];

            long countWithoutPaging = Person.where((Person p) -> p.active).count();
            long countWithPaging = Person.where((Person p) -> p.active)
                    .skip(skipAmount)
                    .limit(limitAmount)
                    .count();

            assertThat(countWithPaging)
                    .as("count() should ignore skip(%d).limit(%d)", skipAmount, limitAmount)
                    .isEqualTo(countWithoutPaging);
        }
    }

    // ======================================================================
    // Sort Independence Property
    // ======================================================================

    @Test
    @Transactional
    void countIgnoresSortOrder() {
        long countWithoutSort = Person.where((Person p) -> p.active).count();
        long countWithAscending = Person.where((Person p) -> p.active)
                .sortedBy((Person p) -> p.age)
                .count();
        long countWithDescending = Person.where((Person p) -> p.active)
                .sortedDescendingBy((Person p) -> p.age)
                .count();

        assertThat(countWithAscending)
                .as("count() should ignore sortedBy()")
                .isEqualTo(countWithoutSort);
        assertThat(countWithDescending)
                .as("count() should ignore sortedDescendingBy()")
                .isEqualTo(countWithoutSort);
    }

    // ======================================================================
    // Count vs List Size with Various Filters
    // ======================================================================

    @ParameterizedTest(name = "count with age > {0} matches list size")
    @ValueSource(ints = {20, 25, 30, 35, 40, 45, 50})
    @Transactional
    void countWithAgeThresholdMatchesListSize(int ageThreshold) {
        long count = Person.where((Person p) -> p.age > ageThreshold).count();
        int listSize = Person.where((Person p) -> p.age > ageThreshold).toList().size();

        assertThat(count)
                .as("count() with age > %d should equal toList().size()", ageThreshold)
                .isEqualTo(listSize);
    }

    @ParameterizedTest(name = "count with salary > {0} matches list size")
    @ValueSource(ints = {50000, 60000, 70000, 80000, 90000})
    @Transactional
    void countWithSalaryThresholdMatchesListSize(int salaryThreshold) {
        long count = Person.where((Person p) -> p.salary > (double) salaryThreshold).count();
        int listSize = Person.where((Person p) -> p.salary > (double) salaryThreshold).toList().size();

        assertThat(count)
                .as("count() with salary > %d should equal toList().size()", salaryThreshold)
                .isEqualTo(listSize);
    }

    // ======================================================================
    // Empty Result Set Properties
    // ======================================================================

    @Test
    @Transactional
    void impossibleFilterReturnsZeroCount() {
        long count = Person.where((Person p) -> p.age < 0).count();

        assertThat(count)
                .as("Impossible filter should return count of 0")
                .isZero();
    }

    @Test
    @Transactional
    void contradictoryFiltersReturnZeroCount() {
        long count = Person.where((Person p) -> p.active)
                .where((Person p) -> !p.active)
                .count();

        assertThat(count)
                .as("Contradictory filters should return count of 0")
                .isZero();
    }

    // ======================================================================
    // Count Consistency Across Operations
    // ======================================================================

    @Test
    @Transactional
    void countIsConsistentAcrossMultipleCalls() {
        long count1 = Person.where((Person p) -> p.active).count();
        long count2 = Person.where((Person p) -> p.active).count();
        long count3 = Person.where((Person p) -> p.active).count();

        assertThat(count1)
                .as("count() should be consistent across calls")
                .isEqualTo(count2)
                .isEqualTo(count3);
    }

    // ======================================================================
    // Product Entity Count Properties
    // ======================================================================

    @Test
    @Transactional
    void productCountWithCategoryFilter() {
        long count = Product.where((Product p) -> p.category.equals("Electronics")).count();
        int listSize = Product.where((Product p) -> p.category.equals("Electronics")).toList().size();

        assertThat(count)
                .as("Product count with category filter should equal list size")
                .isEqualTo(listSize);
    }

    @ParameterizedTest(name = "product count with stock >= {0}")
    @ValueSource(ints = {0, 10, 25, 50, 100})
    @Transactional
    void productCountWithStockThreshold(int minStock) {
        long count = Product.where((Product p) -> p.stockQuantity >= minStock).count();
        int listSize = Product.where((Product p) -> p.stockQuantity >= minStock).toList().size();

        assertThat(count)
                .as("Product count with stock >= %d should equal list size", minStock)
                .isEqualTo(listSize);
    }
}
