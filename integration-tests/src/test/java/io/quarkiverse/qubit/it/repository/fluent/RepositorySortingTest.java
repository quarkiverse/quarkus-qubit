package io.quarkiverse.qubit.it.repository.fluent;

import io.quarkiverse.qubit.it.Person;
import io.quarkiverse.qubit.it.PersonRepository;
import io.quarkiverse.qubit.it.Product;
import io.quarkiverse.qubit.it.ProductRepository;
import io.quarkiverse.qubit.it.testdata.TestDataFactory;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Repository pattern tests for Phase 3: Sorting functionality.
 * Mirrors io.quarkiverse.qubit.it.fluent.SortingTest using repository injection.
 *
 * <p>Tests cover:
 * <ul>
 * <li>Single-level ascending/descending sort</li>
 * <li>Multi-level sorting</li>
 * <li>Sorting with filtering (where)</li>
 * <li>Sorting with projection (select)</li>
 * <li>Combined where + select + sort</li>
 * </ul>
 */
@QuarkusTest
class RepositorySortingTest {

    @Inject
    PersonRepository personRepository;

    @Inject
    ProductRepository productRepository;

    @Inject
    EntityManager em;

    @BeforeEach
    @Transactional
    void setUp() {
        TestDataFactory.clearAllData();
        TestDataFactory.createStandardPersonsAndProducts();
    }

    // =============================================================================================
    // SINGLE-LEVEL ASCENDING SORT (Step 3.5.1)
    // =============================================================================================

    @Test
    void sortedBy_age_ascendingOrder() {
        List<Person> sorted = personRepository.sortedBy((Person p) -> p.age).toList();

        assertThat(sorted)
                .hasSize(5)
                .isSortedAccordingTo(Comparator.comparing(Person::getAge));
        // Verify specific ordering: 25, 28, 30, 35, 45
        assertThat(sorted.get(0).firstName).isEqualTo("Jane");    // age 25
        assertThat(sorted.get(1).firstName).isEqualTo("Charlie"); // age 28
        assertThat(sorted.get(2).firstName).isEqualTo("John");    // age 30
        assertThat(sorted.get(3).firstName).isEqualTo("Alice");   // age 35
        assertThat(sorted.get(4).firstName).isEqualTo("Bob");     // age 45
    }

    @Test
    void sortedBy_salary_ascendingOrder() {
        List<Person> sorted = personRepository.sortedBy((Person p) -> p.salary).toList();

        assertThat(sorted)
                .hasSize(5)
                .isSortedAccordingTo(Comparator.comparing(Person::getSalary));
    }

    @Test
    void sortedBy_firstName_alphabeticalOrder() {
        List<Person> sorted = personRepository.sortedBy((Person p) -> p.firstName).toList();

        assertThat(sorted)
                .hasSize(5)
                .isSortedAccordingTo(Comparator.comparing(Person::getFirstName));
        assertThat(sorted.get(0).firstName).isEqualTo("Alice");
        assertThat(sorted.get(4).firstName).isEqualTo("John");
    }

    // =============================================================================================
    // SINGLE-LEVEL DESCENDING SORT (Step 3.5.2)
    // =============================================================================================

    @Test
    void sortedDescendingBy_age_descendingOrder() {
        List<Person> sorted = personRepository.sortedDescendingBy((Person p) -> p.age).toList();

        assertThat(sorted)
                .hasSize(5)
                .isSortedAccordingTo(Comparator.comparing(Person::getAge).reversed());
        // Verify specific ordering: 45, 35, 30, 28, 25
        assertThat(sorted.get(0).firstName).isEqualTo("Bob");     // age 45
        assertThat(sorted.get(4).firstName).isEqualTo("Jane");    // age 25
    }

    @Test
    void sortedDescendingBy_salary_descendingOrder() {
        List<Person> sorted = personRepository.sortedDescendingBy((Person p) -> p.salary).toList();

        assertThat(sorted)
                .hasSize(5)
                .isSortedAccordingTo(Comparator.comparing(Person::getSalary).reversed());
    }

    @Test
    void sortedDescendingBy_firstName_reverseAlphabetical() {
        List<Person> sorted = personRepository.sortedDescendingBy((Person p) -> p.firstName).toList();

        assertThat(sorted)
                .hasSize(5)
                .isSortedAccordingTo(Comparator.comparing(Person::getFirstName).reversed());
    }

    // =============================================================================================
    // MULTI-LEVEL SORTING (Step 3.5.3)
    // =============================================================================================

    @Test
    @Transactional
    void multiLevelSort_lastNameThenFirstName() {
        // Create persons with same last name for multi-level sorting
        Person.deleteAll();

        Person p1 = new Person();
        p1.firstName = "Bob";
        p1.lastName = "Smith";
        p1.age = 30;
        p1.persist();

        Person p2 = new Person();
        p2.firstName = "Alice";
        p2.lastName = "Smith";
        p2.age = 25;
        p2.persist();

        Person p3 = new Person();
        p3.firstName = "Charlie";
        p3.lastName = "Jones";
        p3.age = 35;
        p3.persist();

        Person p4 = new Person();
        p4.firstName = "Alice";
        p4.lastName = "Jones";
        p4.age = 28;
        p4.persist();

        // Last call wins: lastName is primary sort, firstName is secondary
        List<Person> sorted = personRepository.sortedBy((Person p) -> p.firstName)
                                    .sortedBy((Person p) -> p.lastName)
                                    .toList();

        assertThat(sorted).hasSize(4);
        // Primary sort: lastName
        assertThat(sorted.get(0).lastName).isEqualTo("Jones");
        assertThat(sorted.get(1).lastName).isEqualTo("Jones");
        assertThat(sorted.get(2).lastName).isEqualTo("Smith");
        assertThat(sorted.get(3).lastName).isEqualTo("Smith");

        // Secondary sort: firstName (within same lastName)
        assertThat(sorted.get(0).firstName).isEqualTo("Alice");   // Jones, Alice
        assertThat(sorted.get(1).firstName).isEqualTo("Charlie"); // Jones, Charlie
        assertThat(sorted.get(2).firstName).isEqualTo("Alice");   // Smith, Alice
        assertThat(sorted.get(3).firstName).isEqualTo("Bob");     // Smith, Bob
    }

    @Test
    void multiLevelSort_ageDescendingThenNameAscending() {
        // Last call wins approach
        List<Person> sorted = personRepository.sortedBy((Person p) -> p.firstName)
                                    .sortedDescendingBy((Person p) -> p.age)
                                    .toList();

        assertThat(sorted)
                .hasSize(5)
                .isSortedAccordingTo(Comparator.comparing(Person::getAge).reversed()); // Primary sort: age descending
    }

    @Test
    void multiLevelSort_threeLevels() {
        // Last call wins: age is primary
        List<Person> sorted = personRepository.sortedBy((Person p) -> p.firstName)
                                    .sortedBy((Person p) -> p.lastName)
                                    .sortedBy((Person p) -> p.age)
                                    .toList();

        assertThat(sorted)
                .hasSize(5)
                .isSortedAccordingTo(Comparator.comparing(Person::getAge)); // Primary sort must be by age
    }

    @Test
    void multiLevelSort_mixedAscDesc() {
        // Mixed ascending/descending sorts
        List<Person> sorted = personRepository.sortedDescendingBy((Person p) -> p.salary)
                                    .sortedBy((Person p) -> p.age)
                                    .toList();

        assertThat(sorted)
                .hasSize(5)
                .isSortedAccordingTo(Comparator.comparing(Person::getAge)); // Primary sort: age ascending
    }

    // =============================================================================================
    // SORTING WITH FILTERING (Step 3.5.4)
    // =============================================================================================

    @Test
    void whereAndSortedBy_filterAndSort() {
        List<Person> results = personRepository.where((Person p) -> p.age > 30)
                                     .sortedBy((Person p) -> p.age)
                                     .toList();

        assertThat(results)
                .hasSize(2) // Alice, Bob
                .isSortedAccordingTo(Comparator.comparing(Person::getAge));
        assertThat(results.get(0).firstName).isEqualTo("Alice");   // age 35
        assertThat(results.get(1).firstName).isEqualTo("Bob");     // age 45
    }

    @Test
    void whereAndSortedDescendingBy_filterAndReverseSort() {
        List<Person> results = personRepository.where((Person p) -> p.active)
                                     .sortedDescendingBy((Person p) -> p.salary)
                                     .toList();

        assertThat(results)
                .allMatch(p -> p.active)
                .isSortedAccordingTo(Comparator.comparing(Person::getSalary).reversed());
    }

    @Test
    void complexWhereAndMultiSort() {
        List<Person> results = personRepository.where((Person p) -> p.age >= 30)
                                     .where((Person p) -> p.salary > 60000.0)
                                     .sortedBy((Person p) -> p.lastName)
                                     .sortedDescendingBy((Person p) -> p.age)
                                     .toList();

        assertThat(results)
                .allMatch(p -> p.age >= 30 && p.salary > 60000.0)
                .isSortedAccordingTo(Comparator.comparing(Person::getAge).reversed()); // Primary sort: age descending
    }

    // =============================================================================================
    // SORTING WITH PROJECTION (Step 3.5.5)
    // =============================================================================================

    @Test
    void selectAndSortedBy_projectAndSort() {
        List<String> names = personRepository.select((Person p) -> p.firstName)
                                   .sortedBy((String s) -> s)
                                   .toList();

        assertThat(names)
                .hasSize(5)
                .isSorted() // String natural ordering
                .containsExactly("Alice", "Bob", "Charlie", "Jane", "John");
    }

    @Test
    void selectAndSortedDescendingBy_projectAndReverseSort() {
        List<Integer> ages = personRepository.select((Person p) -> p.age)
                                   .sortedDescendingBy((Integer i) -> i)
                                   .toList();

        assertThat(ages)
                .hasSize(5)
                .isSortedAccordingTo(Comparator.reverseOrder())
                .containsExactly(45, 35, 30, 28, 25);
    }

    @Test
    void selectAndMultiSort_projectWithMultiLevelSort() {
        List<Double> salaries = personRepository.select((Person p) -> p.salary)
                                      .sortedDescendingBy((Double d) -> d)
                                      .toList();

        assertThat(salaries)
                .hasSize(5)
                .isSortedAccordingTo(Comparator.reverseOrder()); // Sorted in descending order
    }

    // =============================================================================================
    // COMBINED WHERE + SELECT + SORT (Additional Coverage)
    // =============================================================================================

    @Test
    void whereSelectSorted_fullPipeline() {
        List<String> names = personRepository.where((Person p) -> p.age > 30)
                                   .select((Person p) -> p.firstName)
                                   .sortedBy((String s) -> s)
                                   .toList();

        assertThat(names)
                .hasSize(2) // Alice, Bob
                .isSorted()
                .containsExactly("Alice", "Bob");
    }

    @Test
    void whereSelectSortedDescending_fullPipelineReversed() {
        List<Integer> ages = personRepository.where((Person p) -> p.active)
                                   .select((Person p) -> p.age)
                                   .sortedDescendingBy((Integer i) -> i)
                                   .toList();

        assertThat(ages)
                .isSortedAccordingTo(Comparator.reverseOrder());
    }

    // =============================================================================================
    // PRODUCT ENTITY TESTS (Different data types)
    // =============================================================================================

    @Test
    void sortedBy_productPrice_ascendingBigDecimal() {
        List<Product> sorted = productRepository.sortedBy((Product p) -> p.price).toList();

        assertThat(sorted)
                .hasSize(5)
                .isSortedAccordingTo(Comparator.comparing(p -> p.price));
        // Verify first and last
        assertThat(sorted.get(0).name).isEqualTo("Coffee Maker");
        assertThat(sorted.get(4).name).isEqualTo("Laptop");
    }

    @Test
    void sortedDescendingBy_productStockQuantity_descendingInteger() {
        List<Product> sorted = productRepository.sortedDescendingBy((Product p) -> p.stockQuantity).toList();

        assertThat(sorted)
                .hasSize(5)
                .isSortedAccordingTo(Comparator.comparing((Product p) -> p.stockQuantity).reversed());
    }

    @Test
    void productWhereAndSort_availabilityAndPriceSort() {
        List<Product> results = productRepository.where((Product p) -> p.available)
                                       .sortedBy((Product p) -> p.price)
                                       .toList();

        assertThat(results)
                .allMatch(p -> p.available)
                .isSortedAccordingTo(Comparator.comparing(p -> p.price));
    }
}
