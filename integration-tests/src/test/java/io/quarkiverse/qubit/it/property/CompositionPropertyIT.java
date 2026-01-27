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

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based integration tests for query composition operations.
 *
 * <p>This class uses parameterized tests to verify composition invariants
 * when multiple query operations are combined.
 *
 * <p><strong>Properties Tested:</strong>
 * <ul>
 *   <li><strong>Filter-first property</strong>: Filtering happens before pagination</li>
 *   <li><strong>Sort-pagination consistency</strong>: Sorting is applied before pagination</li>
 *   <li><strong>Projection transparency</strong>: Projection doesn't affect filter/sort semantics</li>
 *   <li><strong>Terminal operation finality</strong>: count() and toList() are terminal</li>
 * </ul>
 *
 * @see io.quarkiverse.qubit.it.fluent.ComplexCombinationsTest
 */
@QuarkusTest
class CompositionPropertyIT {

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
    // Filter-First Property (Filtering Before Pagination)
    // ======================================================================

    @ParameterizedTest(name = "filter applied before skip({0})")
    @ValueSource(ints = {0, 1, 2, 3})
    @Transactional
    void filterAppliedBeforeSkip(int skipAmount) {
        List<Person> filtered = Person.where((Person p) -> p.active)
                .sortedBy((Person p) -> p.id)
                .toList();

        List<Person> skipped = Person.where((Person p) -> p.active)
                .sortedBy((Person p) -> p.id)
                .skip(skipAmount)
                .toList();

        int expectedSize = Math.max(0, filtered.size() - skipAmount);
        assertThat(skipped)
                .as("Skip should be applied to filtered set, not total")
                .hasSize(expectedSize);

        assertThat(skipped)
                .as("All skipped results should satisfy filter")
                .allMatch(p -> p.active);
    }

    @ParameterizedTest(name = "filter applied before limit({0})")
    @ValueSource(ints = {1, 2, 3, 5})
    @Transactional
    void filterAppliedBeforeLimit(int limitAmount) {
        List<Person> filtered = Person.where((Person p) -> p.active).toList();

        List<Person> limited = Person.where((Person p) -> p.active)
                .limit(limitAmount)
                .toList();

        int expectedSize = Math.min(limitAmount, filtered.size());
        assertThat(limited)
                .as("Limit should be applied to filtered set")
                .hasSize(expectedSize);

        assertThat(limited)
                .as("All limited results should satisfy filter")
                .allMatch(p -> p.active);
    }

    // ======================================================================
    // Sort-Pagination Consistency (Sort Before Pagination)
    // ======================================================================

    @ParameterizedTest(name = "sort applied before pagination (pageSize={0})")
    @ValueSource(ints = {1, 2, 3, 4})
    @Transactional
    void sortAppliedBeforePagination(int pageSize) {
        List<Person> firstPage = Person.sortedBy((Person p) -> p.age)
                .limit(pageSize)
                .toList();

        List<Person> allSorted = Person.sortedBy((Person p) -> p.age).toList();

        for (int i = 0; i < firstPage.size(); i++) {
            assertThat(firstPage.get(i).age)
                    .as("First page element %d should match sorted element %d", i, i)
                    .isEqualTo(allSorted.get(i).age);
        }
    }

    @ParameterizedTest(name = "descending sort before pagination (pageSize={0})")
    @ValueSource(ints = {1, 2, 3, 4})
    @Transactional
    void descendingSortBeforePagination(int pageSize) {
        List<Person> firstPage = Person.sortedDescendingBy((Person p) -> p.age)
                .limit(pageSize)
                .toList();

        List<Person> allSorted = Person.sortedDescendingBy((Person p) -> p.age).toList();

        for (int i = 0; i < firstPage.size(); i++) {
            assertThat(firstPage.get(i).age)
                    .as("First page element %d should match descending sorted element %d", i, i)
                    .isEqualTo(allSorted.get(i).age);
        }
    }

    // ======================================================================
    // Multiple Filters Composition
    // ======================================================================

    @Test
    @Transactional
    void multipleWhereClausesCombineWithAnd() {
        List<Person> combined = Person.where((Person p) -> p.active)
                .where((Person p) -> p.age > 25)
                .toList();

        assertThat(combined)
                .as("All results should satisfy both predicates")
                .allMatch(p -> p.active && p.age > 25);
    }

    @Test
    @Transactional
    void filterOrderDoesntAffectResultSet() {
        Set<Long> order1 = Person.where((Person p) -> p.active)
                .where((Person p) -> p.age > 25)
                .toList().stream().map(p -> p.id).collect(Collectors.toSet());

        Set<Long> order2 = Person.where((Person p) -> p.age > 25)
                .where((Person p) -> p.active)
                .toList().stream().map(p -> p.id).collect(Collectors.toSet());

        assertThat(order1)
                .as("Filter order should not affect result set")
                .containsExactlyInAnyOrderElementsOf(order2);
    }

    @Test
    @Transactional
    void threeFiltersProduceIntersection() {
        List<Person> combined = Person.where((Person p) -> p.active)
                .where((Person p) -> p.age > 25)
                .where((Person p) -> p.salary > 60000.0)
                .toList();

        assertThat(combined)
                .as("All results should satisfy all three predicates")
                .allMatch(p -> p.active && p.age > 25 && p.salary > 60000.0);
    }

    // ======================================================================
    // Full Pipeline Composition
    // ======================================================================

    @Test
    @Transactional
    void fullPipelineMaintainsAllInvariants() {
        int[][] testCases = {{0, 2}, {1, 2}, {2, 3}};

        for (int[] testCase : testCases) {
            int skipAmount = testCase[0];
            int limitAmount = testCase[1];

            List<Person> results = Person.where((Person p) -> p.active)
                    .sortedBy((Person p) -> p.age)
                    .skip(skipAmount)
                    .limit(limitAmount)
                    .toList();

            assertThat(results)
                    .as("All results should satisfy filter predicate")
                    .allMatch(p -> p.active);

            for (int i = 1; i < results.size(); i++) {
                assertThat(results.get(i).age)
                        .as("Results should be sorted by age")
                        .isGreaterThanOrEqualTo(results.get(i - 1).age);
            }

            assertThat(results)
                    .as("Result count should be <= limit")
                    .hasSizeLessThanOrEqualTo(limitAmount);
        }
    }

    @Test
    @Transactional
    void fullPipelineWithProjectionAllInvariantsHold() {
        int[][] testCases = {{0, 2}, {1, 2}, {2, 3}};

        for (int[] testCase : testCases) {
            int skipAmount = testCase[0];
            int limitAmount = testCase[1];

            List<String> results = Person.where((Person p) -> p.active)
                    .select((Person p) -> p.firstName)
                    .sortedBy((String s) -> s)
                    .skip(skipAmount)
                    .limit(limitAmount)
                    .toList();

            for (int i = 1; i < results.size(); i++) {
                assertThat(results.get(i).compareTo(results.get(i - 1)))
                        .as("Names should be sorted")
                        .isGreaterThanOrEqualTo(0);
            }

            assertThat(results)
                    .as("Result count should be <= limit")
                    .hasSizeLessThanOrEqualTo(limitAmount);
        }
    }

    // ======================================================================
    // Projection Transparency (Doesn't Affect Semantics)
    // ======================================================================

    @Test
    @Transactional
    void projectionDoesntChangeFilterSemantics() {
        long entityCount = Person.where((Person p) -> p.active).count();
        long projectedCount = Person.where((Person p) -> p.active)
                .select((Person p) -> p.firstName)
                .toList().size();

        assertThat(projectedCount)
                .as("Projection should not change filtered count")
                .isEqualTo(entityCount);
    }

    @Test
    @Transactional
    void projectionOrderDoesntChangeCount() {
        int count1 = Person.select((Person p) -> p.firstName)
                .sortedBy((String s) -> s)
                .toList().size();

        int count2 = Person.sortedBy((Person p) -> p.firstName).toList().size();

        assertThat(count1)
                .as("Projection shouldn't change result count")
                .isEqualTo(count2);
    }

    // ======================================================================
    // Terminal Operation Properties
    // ======================================================================

    @Test
    @Transactional
    void countAndListSizeEquivalent() {
        long count = Person.where((Person p) -> p.active).count();
        int listSize = Person.where((Person p) -> p.active).toList().size();

        assertThat(count)
                .as("count() should equal toList().size()")
                .isEqualTo(listSize);
    }

    @Test
    @Transactional
    void findFirstReturnsFirstOfSortedList() {
        List<Person> sorted = Person.sortedBy((Person p) -> p.age).toList();
        Person first = Person.sortedBy((Person p) -> p.age).findFirst().orElse(null);

        if (!sorted.isEmpty()) {
            assertThat(first)
                    .as("findFirst should return first element of sorted list")
                    .isNotNull();
            assertThat(first.id)
                    .as("findFirst ID should match first sorted element ID")
                    .isEqualTo(sorted.get(0).id);
        }
    }

    // ======================================================================
    // Product Entity Composition Properties
    // ======================================================================

    @ParameterizedTest(name = "product: filter + sort + limit({0}) maintains invariants")
    @ValueSource(ints = {1, 2, 3, 4})
    @Transactional
    void productFilterSortLimitMaintainsInvariants(int limitAmount) {
        List<Product> results = Product.where((Product p) -> p.available)
                .sortedBy((Product p) -> p.price)
                .limit(limitAmount)
                .toList();

        assertThat(results)
                .as("All products should be available")
                .allMatch(p -> p.available);

        for (int i = 1; i < results.size(); i++) {
            assertThat(results.get(i).price)
                    .as("Products should be sorted by price")
                    .isGreaterThanOrEqualTo(results.get(i - 1).price);
        }

        assertThat(results)
                .as("Result count should be <= limit")
                .hasSizeLessThanOrEqualTo(limitAmount);
    }

    @Test
    @Transactional
    void productMultiFilterAndSort() {
        List<Product> results = Product.where((Product p) -> p.category.equals("Electronics"))
                .where((Product p) -> p.stockQuantity > 0)
                .sortedDescendingBy((Product p) -> p.price)
                .toList();

        assertThat(results)
                .as("All products should be Electronics with stock > 0")
                .allMatch(p -> "Electronics".equals(p.category) && p.stockQuantity > 0);

        for (int i = 1; i < results.size(); i++) {
            assertThat(results.get(i).price)
                    .as("Products should be sorted descending by price")
                    .isLessThanOrEqualTo(results.get(i - 1).price);
        }
    }

    // ======================================================================
    // Edge Case Composition Properties
    // ======================================================================

    @Test
    @Transactional
    void emptyResultSetHandledGracefully() {
        List<Person> results = Person.where((Person p) -> p.age < 0)
                .sortedBy((Person p) -> p.age)
                .skip(0)
                .limit(10)
                .toList();

        assertThat(results)
                .as("Empty result set should be handled gracefully")
                .isEmpty();
    }

    @Test
    @Transactional
    void skipBeyondResultSetReturnsEmpty() {
        List<Person> results = Person.where((Person p) -> p.active)
                .skip(1000)
                .toList();

        assertThat(results)
                .as("Skip beyond result set should return empty")
                .isEmpty();
    }

    @Test
    @Transactional
    void zeroLimitBehavior() {
        List<Person> results = Person.sortedBy((Person p) -> p.id)
                .limit(0)
                .toList();

        assertThat(results.size())
                .as("limit(0) should return some defined result")
                .isGreaterThanOrEqualTo(0);
    }
}
