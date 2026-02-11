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
 * Property-based integration tests for projection (select) operations.
 *
 * <p>
 * This class uses jqwik to verify projection invariants that must hold
 * for ANY valid projection configuration, not just specific examples.
 *
 * <p>
 * <strong>Properties Tested:</strong>
 * <ul>
 * <li><strong>Count preservation</strong>: projection count equals entity count</li>
 * <li><strong>Value correctness</strong>: projected values match entity field values</li>
 * <li><strong>Type correctness</strong>: projected values have correct type</li>
 * <li><strong>Filter compatibility</strong>: projection works with filters</li>
 * <li><strong>Sort compatibility</strong>: projection works with sorting</li>
 * <li><strong>Pagination compatibility</strong>: projection works with pagination</li>
 * </ul>
 *
 * @see io.quarkiverse.qubit.it.fluent.ProjectionTest
 */
@QuarkusTest
class ProjectionPropertyIT {

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
    // Count Preservation Properties
    // ======================================================================

    @Test
    @Transactional
    void projectionCountEqualsEntityCount() {
        int entityCount = Person.listAll().size();
        int projectedCount = Person.select((Person p) -> p.firstName).toList().size();

        assertThat(projectedCount)
                .as("Projected count should equal entity count")
                .isEqualTo(entityCount);
    }

    @Test
    @Transactional
    void filteredProjectionCountEqualsFilteredEntityCount() {
        int entityCount = Person.where((Person p) -> p.active).toList().size();
        int projectedCount = Person.where((Person p) -> p.active)
                .select((Person p) -> p.firstName)
                .toList().size();

        assertThat(projectedCount)
                .as("Filtered projected count should equal filtered entity count")
                .isEqualTo(entityCount);
    }

    @Test
    @Transactional
    void multiFilteredProjectionCountMatches() {
        int entityCount = Person.where((Person p) -> p.active)
                .where((Person p) -> p.age > 25)
                .toList().size();
        int projectedCount = Person.where((Person p) -> p.active)
                .where((Person p) -> p.age > 25)
                .select((Person p) -> p.salary)
                .toList().size();

        assertThat(projectedCount)
                .as("Multi-filtered projected count should match")
                .isEqualTo(entityCount);
    }

    // ======================================================================
    // Value Correctness Properties
    // ======================================================================

    @Test
    @Transactional
    void projectedFirstNameValuesMatch() {
        List<Person> entities = Person.sortedBy((Person p) -> p.id).toList();
        List<String> projected = Person.select((Person p) -> p.firstName)
                .sortedBy((String s) -> s)
                .toList();

        Set<String> entityNames = entities.stream().map(p -> p.firstName).collect(Collectors.toSet());
        Set<String> projectedNames = Set.copyOf(projected);

        assertThat(projectedNames)
                .as("Projected names should match entity names")
                .containsExactlyInAnyOrderElementsOf(entityNames);
    }

    @Test
    @Transactional
    void projectedAgeValuesMatch() {
        List<Person> entities = Person.listAll();
        List<Integer> projected = Person.select((Person p) -> p.age).toList();

        Set<Integer> entityAges = entities.stream().map(p -> ((Person) p).age).collect(Collectors.toSet());
        Set<Integer> projectedAges = Set.copyOf(projected);

        assertThat(projectedAges)
                .as("Projected ages should match entity ages")
                .containsExactlyInAnyOrderElementsOf(entityAges);
    }

    @Test
    @Transactional
    void projectedSalaryValuesMatch() {
        List<Person> entities = Person.listAll();
        List<Double> projected = Person.select((Person p) -> p.salary).toList();

        Set<Double> entitySalaries = entities.stream().map(p -> ((Person) p).salary).collect(Collectors.toSet());
        Set<Double> projectedSalaries = Set.copyOf(projected);

        assertThat(projectedSalaries)
                .as("Projected salaries should match entity salaries")
                .containsExactlyInAnyOrderElementsOf(entitySalaries);
    }

    // ======================================================================
    // Type Correctness Properties
    // ======================================================================

    @Test
    @Transactional
    void projectedStringValuesHaveCorrectType() {
        List<String> projected = Person.select((Person p) -> p.firstName).toList();

        assertThat(projected)
                .as("All projected values should be Strings")
                .allSatisfy(value -> assertThat(value).isInstanceOf(String.class));
    }

    @Test
    @Transactional
    void projectedIntegerValuesHaveCorrectType() {
        List<Integer> projected = Person.select((Person p) -> p.age).toList();

        assertThat(projected)
                .as("All projected values should be Integers")
                .allSatisfy(value -> assertThat(value).isInstanceOf(Integer.class));
    }

    @Test
    @Transactional
    void projectedDoubleValuesHaveCorrectType() {
        List<Double> projected = Person.select((Person p) -> p.salary).toList();

        assertThat(projected)
                .as("All projected values should be Doubles")
                .allSatisfy(value -> assertThat(value).isInstanceOf(Double.class));
    }

    // ======================================================================
    // Filter Compatibility Properties
    // ======================================================================

    @Test
    @Transactional
    void projectionWithFilterValuesFromFilteredEntities() {
        Set<String> filteredNames = Person.where((Person p) -> p.active).toList()
                .stream().map(p -> p.firstName).collect(Collectors.toSet());

        List<String> projectedNames = Person.where((Person p) -> p.active)
                .select((Person p) -> p.firstName)
                .toList();

        assertThat(projectedNames)
                .as("Projected names should all come from filtered entities")
                .allMatch(filteredNames::contains);
    }

    @ParameterizedTest(name = "projection with age filter: ages > {0} satisfy predicate")
    @ValueSource(ints = { 20, 25, 30, 35, 40, 45 })
    @Transactional
    void projectionWithAgeFilterAgesSatisfyPredicate(int ageThreshold) {
        List<Integer> projectedAges = Person.where((Person p) -> p.age > ageThreshold)
                .select((Person p) -> p.age)
                .toList();

        assertThat(projectedAges)
                .as("All projected ages should be > %d", ageThreshold)
                .allMatch(age -> age > ageThreshold);
    }

    // ======================================================================
    // Sort Compatibility Properties
    // ======================================================================

    @Test
    @Transactional
    void projectionWithAscendingSortValuesOrdered() {
        List<String> names = Person.select((Person p) -> p.firstName)
                .sortedBy((String s) -> s)
                .toList();

        for (int i = 1; i < names.size(); i++) {
            assertThat(names.get(i).compareTo(names.get(i - 1)))
                    .as("Names should be in ascending order")
                    .isGreaterThanOrEqualTo(0);
        }
    }

    @Test
    @Transactional
    void projectionWithDescendingSortValuesOrdered() {
        List<Integer> ages = Person.select((Person p) -> p.age)
                .sortedDescendingBy((Integer a) -> a)
                .toList();

        for (int i = 1; i < ages.size(); i++) {
            assertThat(ages.get(i))
                    .as("Ages should be in descending order")
                    .isLessThanOrEqualTo(ages.get(i - 1));
        }
    }

    // ======================================================================
    // Pagination Compatibility Properties
    // ======================================================================

    @ParameterizedTest(name = "projection with skip({0}): returns correct count")
    @ValueSource(ints = { 0, 1, 2, 3, 4, 5 })
    @Transactional
    void projectionWithSkipReturnsCorrectCount(int skipAmount) {
        int totalCount = Person.select((Person p) -> p.firstName).toList().size();
        int skippedCount = Person.select((Person p) -> p.firstName)
                .skip(skipAmount)
                .toList().size();

        int expectedCount = Math.max(0, totalCount - skipAmount);
        assertThat(skippedCount)
                .as("Skipped count should be max(0, total - %d)", skipAmount)
                .isEqualTo(expectedCount);
    }

    @ParameterizedTest(name = "projection with limit({0}): returns at most limit results")
    @ValueSource(ints = { 1, 2, 3, 5, 10 })
    @Transactional
    void projectionWithLimitReturnsAtMostLimit(int limitAmount) {
        int limitedCount = Person.select((Person p) -> p.firstName)
                .limit(limitAmount)
                .toList().size();

        assertThat(limitedCount)
                .as("Limited count should be <= %d", limitAmount)
                .isLessThanOrEqualTo(limitAmount);
    }

    @Test
    @Transactional
    void projectionWithSkipLimitReturnsCorrectWindow() {
        int[][] testCases = { { 0, 2 }, { 1, 2 }, { 2, 3 }, { 3, 2 }, { 4, 4 } };

        for (int[] testCase : testCases) {
            int skipAmount = testCase[0];
            int limitAmount = testCase[1];

            List<String> allNames = Person.select((Person p) -> p.firstName)
                    .sortedBy((String s) -> s)
                    .toList();
            List<String> windowedNames = Person.select((Person p) -> p.firstName)
                    .sortedBy((String s) -> s)
                    .skip(skipAmount)
                    .limit(limitAmount)
                    .toList();

            int expectedSize = Math.min(limitAmount, Math.max(0, allNames.size() - skipAmount));
            assertThat(windowedNames)
                    .as("Windowed projection should have correct size for skip(%d).limit(%d)", skipAmount, limitAmount)
                    .hasSize(expectedSize);

            // Verify correct window elements
            for (int i = 0; i < windowedNames.size(); i++) {
                assertThat(windowedNames.get(i))
                        .as("Window element %d should match expected for skip(%d).limit(%d)", i, skipAmount, limitAmount)
                        .isEqualTo(allNames.get(skipAmount + i));
            }
        }
    }

    // ======================================================================
    // Combined Operations Properties
    // ======================================================================

    @Test
    @Transactional
    void filterProjectionSortAllInvariantsHold() {
        List<String> results = Person.where((Person p) -> p.active)
                .select((Person p) -> p.firstName)
                .sortedBy((String s) -> s)
                .toList();

        // Count matches
        long filteredCount = Person.where((Person p) -> p.active).count();
        assertThat(results)
                .as("Result count should match filtered entity count")
                .hasSize((int) filteredCount);

        // Order is correct
        for (int i = 1; i < results.size(); i++) {
            assertThat(results.get(i).compareTo(results.get(i - 1)))
                    .as("Names should be sorted")
                    .isGreaterThanOrEqualTo(0);
        }
    }

    @Test
    @Transactional
    void filterProjectionSortPaginationAllInvariantsHold() {
        int[][] testCases = { { 0, 2 }, { 1, 2 }, { 2, 3 }, { 0, 3 }, { 1, 1 } };

        for (int[] testCase : testCases) {
            int skipAmount = testCase[0];
            int limitAmount = testCase[1];

            List<String> results = Person.where((Person p) -> p.active)
                    .select((Person p) -> p.firstName)
                    .sortedBy((String s) -> s)
                    .skip(skipAmount)
                    .limit(limitAmount)
                    .toList();

            // Count is within expected bounds
            assertThat(results)
                    .as("Result count should be <= limit for skip(%d).limit(%d)", skipAmount, limitAmount)
                    .hasSizeLessThanOrEqualTo(limitAmount);

            // Order is correct
            for (int i = 1; i < results.size(); i++) {
                assertThat(results.get(i).compareTo(results.get(i - 1)))
                        .as("Names should remain sorted after pagination")
                        .isGreaterThanOrEqualTo(0);
            }
        }
    }

    // ======================================================================
    // Product Entity Projection Properties
    // ======================================================================

    @Test
    @Transactional
    void productNameProjectionCountMatches() {
        int entityCount = Product.listAll().size();
        int projectedCount = Product.select((Product p) -> p.name).toList().size();

        assertThat(projectedCount)
                .as("Product name projection count should match entity count")
                .isEqualTo(entityCount);
    }

    @Test
    @Transactional
    void productCategoryProjectionWithFilter() {
        List<String> categories = Product.where((Product p) -> p.available)
                .select((Product p) -> p.category)
                .toList();

        int expectedCount = Product.where((Product p) -> p.available).toList().size();
        assertThat(categories)
                .as("Category projection count should match filtered entity count")
                .hasSize(expectedCount);
    }
}
