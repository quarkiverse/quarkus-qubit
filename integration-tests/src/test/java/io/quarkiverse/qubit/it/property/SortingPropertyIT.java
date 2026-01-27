package io.quarkiverse.qubit.it.property;

import io.quarkiverse.qubit.it.Person;
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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based integration tests for sorting operations.
 *
 * <p>This class uses parameterized tests to verify ordering invariants
 * that must hold for various sort configurations.
 *
 * <p><strong>Properties Tested:</strong>
 * <ul>
 *   <li><strong>Total ordering</strong>: Every adjacent pair is ordered correctly</li>
 *   <li><strong>Completeness</strong>: Sort contains all original elements</li>
 *   <li><strong>Idempotence</strong>: Sorting twice produces same result as sorting once</li>
 *   <li><strong>Reversal</strong>: Descending order is exact reverse of ascending</li>
 * </ul>
 *
 * @see io.quarkiverse.qubit.it.fluent.SortingTest
 */
@QuarkusTest
class SortingPropertyIT {

    @Inject
    EntityManager em;

    @BeforeEach
    @Transactional
    void setup() {
        TestDataFactory.clearAllData();
        TestDataFactory.createAdditionalPersons();
    }

    // ======================================================================
    // Total Ordering Properties
    // ======================================================================

    @Test
    @Transactional
    void ascendingSortAdjacentPairsOrdered() {
        List<Person> sorted = Person.sortedBy((Person p) -> p.age).toList();

        for (int i = 1; i < sorted.size(); i++) {
            assertThat(sorted.get(i).age)
                    .as("Element at index %d should have age >= element at index %d", i, i - 1)
                    .isGreaterThanOrEqualTo(sorted.get(i - 1).age);
        }
    }

    @Test
    @Transactional
    void descendingSortAdjacentPairsOrdered() {
        List<Person> sorted = Person.sortedDescendingBy((Person p) -> p.age).toList();

        for (int i = 1; i < sorted.size(); i++) {
            assertThat(sorted.get(i).age)
                    .as("Element at index %d should have age <= element at index %d", i, i - 1)
                    .isLessThanOrEqualTo(sorted.get(i - 1).age);
        }
    }

    @Test
    @Transactional
    void ascendingSortBySalaryOrdered() {
        List<Person> sorted = Person.sortedBy((Person p) -> p.salary).toList();

        for (int i = 1; i < sorted.size(); i++) {
            assertThat(sorted.get(i).salary)
                    .as("Salary at index %d should be >= salary at index %d", i, i - 1)
                    .isGreaterThanOrEqualTo(sorted.get(i - 1).salary);
        }
    }

    @Test
    @Transactional
    void ascendingSortByFirstNameOrdered() {
        List<Person> sorted = Person.sortedBy((Person p) -> p.firstName).toList();

        for (int i = 1; i < sorted.size(); i++) {
            assertThat(sorted.get(i).firstName.compareTo(sorted.get(i - 1).firstName))
                    .as("Name at index %d should be >= name at index %d alphabetically", i, i - 1)
                    .isGreaterThanOrEqualTo(0);
        }
    }

    // ======================================================================
    // Completeness Property (No Lost Elements)
    // ======================================================================

    @Test
    @Transactional
    void sortingPreservesElementCount() {
        List<Person> unsorted = Person.listAll();
        List<Person> sorted = Person.sortedBy((Person p) -> p.age).toList();

        assertThat(sorted)
                .as("Sorted list should have same size as unsorted list")
                .hasSize(unsorted.size());
    }

    @Test
    @Transactional
    void sortingPreservesAllElements() {
        List<Long> unsortedIds = Person.listAll().stream().map(p -> ((Person) p).id).sorted().toList();
        List<Long> sortedIds = Person.sortedBy((Person p) -> p.age).toList()
                .stream().map(p -> p.id).sorted().toList();

        assertThat(sortedIds)
                .as("Sorted list should contain all original element IDs")
                .containsExactlyElementsOf(unsortedIds);
    }

    // ======================================================================
    // Idempotence Property (Sort Twice = Sort Once)
    // ======================================================================

    @Test
    @Transactional
    void sortingIsIdempotent() {
        List<Person> sortedOnce = Person.sortedBy((Person p) -> p.age).toList();
        List<Long> idsOnce = sortedOnce.stream().map(p -> p.id).toList();

        List<Person> sortedTwice = Person.sortedBy((Person p) -> p.age).toList();
        List<Long> idsTwice = sortedTwice.stream().map(p -> p.id).toList();

        assertThat(idsTwice)
                .as("Sorting twice should produce same result as sorting once")
                .containsExactlyElementsOf(idsOnce);
    }

    // ======================================================================
    // Reversal Property (Descending = Reverse of Ascending)
    // ======================================================================

    @Test
    @Transactional
    void descendingIsReverseOfAscending() {
        List<Person> ascending = Person.sortedBy((Person p) -> p.age).toList();
        List<Person> descending = Person.sortedDescendingBy((Person p) -> p.age).toList();

        List<Long> ascendingIds = ascending.stream().map(p -> p.id).toList();
        List<Long> descendingIds = descending.stream().map(p -> p.id).toList();

        List<Long> reversedAscending = ascendingIds.reversed();

        assertThat(descendingIds)
                .as("Descending order should be exact reverse of ascending order")
                .containsExactlyElementsOf(reversedAscending);
    }

    @Test
    @Transactional
    void descendingSalaryIsReverseOfAscending() {
        List<Person> ascending = Person.sortedBy((Person p) -> p.salary).toList();
        List<Person> descending = Person.sortedDescendingBy((Person p) -> p.salary).toList();

        List<Long> ascendingIds = ascending.stream().map(p -> p.id).toList();
        List<Long> descendingIds = descending.stream().map(p -> p.id).toList();

        List<Long> reversedAscending = ascendingIds.reversed();

        assertThat(descendingIds)
                .as("Descending salary order should be exact reverse of ascending")
                .containsExactlyElementsOf(reversedAscending);
    }

    // ======================================================================
    // Sort with Filter Properties
    // ======================================================================

    @Test
    @Transactional
    void sortWithFilterMaintainsOrdering() {
        List<Person> sorted = Person.where((Person p) -> p.active)
                .sortedBy((Person p) -> p.age)
                .toList();

        for (int i = 1; i < sorted.size(); i++) {
            assertThat(sorted.get(i).age)
                    .as("Age at index %d should be >= age at index %d within filtered set", i, i - 1)
                    .isGreaterThanOrEqualTo(sorted.get(i - 1).age);
        }
    }

    @Test
    @Transactional
    void sortWithFilterAllElementsSatisfyPredicate() {
        List<Person> sorted = Person.where((Person p) -> p.age > 25)
                .sortedBy((Person p) -> p.salary)
                .toList();

        assertThat(sorted)
                .as("All sorted elements should satisfy the filter predicate")
                .allMatch(p -> p.age > 25);
    }

    // ======================================================================
    // Sort with Pagination Properties
    // ======================================================================

    @ParameterizedTest(name = "skip({0}) maintains sort ordering")
    @ValueSource(ints = {0, 1, 2, 3, 4})
    @Transactional
    void sortWithSkipMaintainsOrdering(int skipAmount) {
        List<Person> sorted = Person.sortedBy((Person p) -> p.age)
                .skip(skipAmount)
                .toList();

        for (int i = 1; i < sorted.size(); i++) {
            assertThat(sorted.get(i).age)
                    .as("Age ordering should be maintained after skip")
                    .isGreaterThanOrEqualTo(sorted.get(i - 1).age);
        }
    }

    @ParameterizedTest(name = "limit({0}) maintains sort ordering")
    @ValueSource(ints = {1, 2, 3, 4, 5, 6})
    @Transactional
    void sortWithLimitMaintainsOrdering(int limitAmount) {
        List<Person> sorted = Person.sortedBy((Person p) -> p.age)
                .limit(limitAmount)
                .toList();

        for (int i = 1; i < sorted.size(); i++) {
            assertThat(sorted.get(i).age)
                    .as("Age ordering should be maintained after limit")
                    .isGreaterThanOrEqualTo(sorted.get(i - 1).age);
        }
    }

    @Test
    @Transactional
    void sortWithSkipLimitMaintainsOrdering() {
        int[][] testCases = {{0, 3}, {1, 2}, {2, 3}, {0, 4}};

        for (int[] testCase : testCases) {
            int skipAmount = testCase[0];
            int limitAmount = testCase[1];

            List<Person> sorted = Person.sortedBy((Person p) -> p.age)
                    .skip(skipAmount)
                    .limit(limitAmount)
                    .toList();

            for (int i = 1; i < sorted.size(); i++) {
                assertThat(sorted.get(i).age)
                        .as("Age ordering should be maintained after skip.limit")
                        .isGreaterThanOrEqualTo(sorted.get(i - 1).age);
            }
        }
    }

    // ======================================================================
    // Sort with Projection Properties
    // ======================================================================

    @Test
    @Transactional
    void projectedSortMaintainsStringOrdering() {
        List<String> names = Person.select((Person p) -> p.firstName)
                .sortedBy((String s) -> s)
                .toList();

        for (int i = 1; i < names.size(); i++) {
            assertThat(names.get(i).compareTo(names.get(i - 1)))
                    .as("Names should be in alphabetical order")
                    .isGreaterThanOrEqualTo(0);
        }
    }

    @Test
    @Transactional
    void projectedSortMaintainsIntegerOrdering() {
        List<Integer> ages = Person.select((Person p) -> p.age)
                .sortedBy((Integer a) -> a)
                .toList();

        for (int i = 1; i < ages.size(); i++) {
            assertThat(ages.get(i))
                    .as("Ages should be in ascending order")
                    .isGreaterThanOrEqualTo(ages.get(i - 1));
        }
    }

    @Test
    @Transactional
    void projectedDescendingSortMaintainsReverseOrdering() {
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
    // Boundary Properties
    // ======================================================================

    @Test
    @Transactional
    void firstElementHasMinimumValue() {
        List<Person> sorted = Person.sortedBy((Person p) -> p.age).toList();

        if (!sorted.isEmpty()) {
            int firstAge = sorted.get(0).age;
            assertThat(sorted)
                    .as("First element should have minimum age value")
                    .allMatch(p -> p.age >= firstAge);
        }
    }

    @Test
    @Transactional
    void lastElementHasMaximumValue() {
        List<Person> sorted = Person.sortedBy((Person p) -> p.age).toList();

        if (!sorted.isEmpty()) {
            int lastAge = sorted.get(sorted.size() - 1).age;
            assertThat(sorted)
                    .as("Last element should have maximum age value")
                    .allMatch(p -> p.age <= lastAge);
        }
    }

    @Test
    @Transactional
    void firstElementInDescendingHasMaximumValue() {
        List<Person> sorted = Person.sortedDescendingBy((Person p) -> p.age).toList();

        if (!sorted.isEmpty()) {
            int firstAge = sorted.get(0).age;
            assertThat(sorted)
                    .as("First element in descending should have maximum age value")
                    .allMatch(p -> p.age <= firstAge);
        }
    }
}
