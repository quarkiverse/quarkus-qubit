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

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based integration tests for filter (where) operations.
 *
 * <p>
 * This class uses parameterized tests to verify predicate satisfaction
 * invariants that must hold for various filter combinations.
 *
 * <p>
 * <strong>Properties Tested:</strong>
 * <ul>
 * <li><strong>Predicate satisfaction</strong>: All results satisfy the filter</li>
 * <li><strong>Subset property</strong>: Filtered set is subset of unfiltered</li>
 * <li><strong>Conjunction</strong>: AND-combined filters produce intersection</li>
 * <li><strong>Monotonicity</strong>: Adding filters never increases result count</li>
 * <li><strong>Impossible filter</strong>: Contradictory filters return empty</li>
 * </ul>
 *
 * @see io.quarkiverse.qubit.it.fluent.BasicQueryTest
 */
@QuarkusTest
class FilterPropertyIT {

    @Inject
    EntityManager em;

    @BeforeEach
    @Transactional
    void setup() {
        TestDataFactory.clearAllData();
        TestDataFactory.createStandardPersonsAndProducts();
    }

    // Predicate Satisfaction Properties

    @Test
    @Transactional
    void allResultsSatisfyActiveFilter() {
        List<Person> filtered = Person.where((Person p) -> p.active).toList();

        assertThat(filtered)
                .as("All filtered results should be active")
                .allMatch(p -> p.active);
    }

    @ParameterizedTest(name = "all results satisfy age > {0} filter")
    @ValueSource(ints = { 20, 25, 30, 35, 40, 45, 50 })
    @Transactional
    void allResultsSatisfyAgeComparisonFilter(int ageThreshold) {
        List<Person> filtered = Person.where((Person p) -> p.age > ageThreshold).toList();

        assertThat(filtered)
                .as("All filtered results should have age > %d", ageThreshold)
                .allMatch(p -> p.age > ageThreshold);
    }

    @ParameterizedTest(name = "all results satisfy salary > {0} filter")
    @ValueSource(ints = { 50000, 60000, 70000, 80000, 90000 })
    @Transactional
    void allResultsSatisfySalaryFilter(int salaryThreshold) {
        List<Person> filtered = Person.where((Person p) -> p.salary > (double) salaryThreshold).toList();

        assertThat(filtered)
                .as("All filtered results should have salary > %d", salaryThreshold)
                .allMatch(p -> p.salary > salaryThreshold);
    }

    @Test
    @Transactional
    void allResultsSatisfyProductAvailabilityFilter() {
        List<Product> filtered = Product.where((Product p) -> p.available).toList();

        assertThat(filtered)
                .as("All filtered products should be available")
                .allMatch(p -> p.available);
    }

    // Subset Property (Filtered is Subset of Unfiltered)

    @Test
    @Transactional
    void filteredIsSubsetOfUnfiltered() {
        List<Long> allIds = Person.listAll().stream().map(p -> ((Person) p).id).toList();
        List<Long> filteredIds = Person.where((Person p) -> p.active).toList()
                .stream().map(p -> p.id).toList();

        assertThat(allIds)
                .as("Filtered IDs should be a subset of all IDs")
                .containsAll(filteredIds);
    }

    @Test
    @Transactional
    void filteredCountLessThanOrEqualTotal() {
        long totalCount = Person.count();
        long filteredCount = Person.where((Person p) -> p.active).count();

        assertThat(filteredCount)
                .as("Filtered count should be <= total count")
                .isLessThanOrEqualTo(totalCount);
    }

    // Conjunction (AND) Properties

    @Test
    @Transactional
    void andCombinedFiltersSatisfyBothPredicates() {
        List<Person> filtered = Person.where((Person p) -> p.active)
                .where((Person p) -> p.age > 25)
                .toList();

        assertThat(filtered)
                .as("All results should satisfy both predicates")
                .allMatch(p -> p.active && p.age > 25);
    }

    @Test
    @Transactional
    void andCombinedFiltersProduceIntersection() {
        List<Long> activeIds = Person.where((Person p) -> p.active).toList()
                .stream().map(p -> p.id).toList();
        List<Long> olderThan25Ids = Person.where((Person p) -> p.age > 25).toList()
                .stream().map(p -> p.id).toList();

        List<Long> combinedIds = Person.where((Person p) -> p.active)
                .where((Person p) -> p.age > 25)
                .toList().stream().map(p -> p.id).toList();

        assertThat(activeIds)
                .as("Combined result should be subset of first filter result")
                .containsAll(combinedIds);
        assertThat(olderThan25Ids)
                .as("Combined result should be subset of second filter result")
                .containsAll(combinedIds);
    }

    @Test
    @Transactional
    void threeAndCombinedFiltersSatisfyAllPredicates() {
        List<Person> filtered = Person.where((Person p) -> p.active)
                .where((Person p) -> p.age > 25)
                .where((Person p) -> p.salary > 60000.0)
                .toList();

        assertThat(filtered)
                .as("All results should satisfy all three predicates")
                .allMatch(p -> p.active && p.age > 25 && p.salary > 60000.0);
    }

    // Monotonicity Property (More Filters = Fewer Results)

    @Test
    @Transactional
    void addingFiltersNeverIncreasesCount() {
        long noFilterCount = Person.count();
        long oneFilterCount = Person.where((Person p) -> p.active).count();
        long twoFilterCount = Person.where((Person p) -> p.active)
                .where((Person p) -> p.age > 25)
                .count();
        long threeFilterCount = Person.where((Person p) -> p.active)
                .where((Person p) -> p.age > 25)
                .where((Person p) -> p.salary > 60000.0)
                .count();

        assertThat(oneFilterCount)
                .as("One filter count <= no filter count")
                .isLessThanOrEqualTo(noFilterCount);
        assertThat(twoFilterCount)
                .as("Two filter count <= one filter count")
                .isLessThanOrEqualTo(oneFilterCount);
        assertThat(threeFilterCount)
                .as("Three filter count <= two filter count")
                .isLessThanOrEqualTo(twoFilterCount);
    }

    // Impossible Filter Properties

    @Test
    @Transactional
    void contradictoryFiltersReturnEmpty() {
        List<Person> filtered = Person.where((Person p) -> p.active)
                .where((Person p) -> !p.active)
                .toList();

        assertThat(filtered)
                .as("Contradictory filters should return empty result")
                .isEmpty();
    }

    @Test
    @Transactional
    void impossibleRangeFiltersReturnEmpty() {
        List<Person> filtered = Person.where((Person p) -> p.age > 100)
                .where((Person p) -> p.age < 0)
                .toList();

        assertThat(filtered)
                .as("Impossible range filters should return empty result")
                .isEmpty();
    }

    // Filter with Sorting Properties

    @Test
    @Transactional
    void filterWithSortSatisfiesPredicateAndOrdered() {
        List<Person> filtered = Person.where((Person p) -> p.active)
                .sortedBy((Person p) -> p.age)
                .toList();

        assertThat(filtered)
                .as("All results should be active")
                .allMatch(p -> p.active);

        for (int i = 1; i < filtered.size(); i++) {
            assertThat(filtered.get(i).age)
                    .as("Results should be ordered by age")
                    .isGreaterThanOrEqualTo(filtered.get(i - 1).age);
        }
    }

    // Filter with Pagination Properties

    @Test
    @Transactional
    void filterWithPaginationSatisfiesPredicate() {
        int[][] testCases = { { 0, 2 }, { 1, 2 }, { 2, 3 }, { 0, 4 } };

        for (int[] testCase : testCases) {
            int skip = testCase[0];
            int limit = testCase[1];

            List<Person> paged = Person.where((Person p) -> p.active)
                    .sortedBy((Person p) -> p.id)
                    .skip(skip)
                    .limit(limit)
                    .toList();

            assertThat(paged)
                    .as("All paginated results should satisfy filter predicate")
                    .allMatch(p -> p.active);
        }
    }

    // Filter with Projection Properties

    @Test
    @Transactional
    void filterWithProjectionCountMatches() {
        long entityCount = Person.where((Person p) -> p.active).count();
        long projectedCount = Person.where((Person p) -> p.active)
                .select((Person p) -> p.firstName)
                .toList().size();

        assertThat(projectedCount)
                .as("Projected count should match entity count")
                .isEqualTo(entityCount);
    }

    // Product Entity Filter Properties

    @Test
    @Transactional
    void productCategoryFilterAllResultsCorrect() {
        List<Product> filtered = Product.where((Product p) -> p.category.equals("Electronics")).toList();

        assertThat(filtered)
                .as("All filtered products should be in Electronics category")
                .allMatch(p -> "Electronics".equals(p.category));
    }

    @ParameterizedTest(name = "product price filter with threshold {0}")
    @ValueSource(ints = { 50, 100, 200, 300, 500 })
    @Transactional
    void productPriceFilterWithThreshold(int priceThreshold) {
        BigDecimal threshold = new BigDecimal(priceThreshold);
        List<Product> filtered = Product.where((Product p) -> p.price.compareTo(threshold) > 0).toList();

        assertThat(filtered)
                .as("All filtered products should have price > %s", threshold)
                .allMatch(p -> p.price.compareTo(threshold) > 0);
    }

    @ParameterizedTest(name = "product stock filter with min stock {0}")
    @ValueSource(ints = { 0, 10, 25, 50 })
    @Transactional
    void productStockFilterAllResultsSufficient(int minStock) {
        List<Product> filtered = Product.where((Product p) -> p.stockQuantity >= minStock).toList();

        assertThat(filtered)
                .as("All filtered products should have stock >= %d", minStock)
                .allMatch(p -> p.stockQuantity >= minStock);
    }
}
