package io.quarkiverse.qubit.it.fluent;

import io.quarkiverse.qubit.it.Person;
import io.quarkiverse.qubit.it.Product;
import io.quarkiverse.qubit.it.testdata.TestDataFactory;
import io.quarkiverse.qubit.it.testutil.PersonQueryOperations;
import io.quarkiverse.qubit.it.testutil.ProductQueryOperations;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Abstract base class for sorting functionality tests.
 *
 * <p>
 * Contains all test methods that can be run with either static entity methods
 * or repository instance methods.
 *
 * <p>
 * Tests cover:
 * <ul>
 * <li>Single-level ascending/descending sort</li>
 * <li>Multi-level sorting</li>
 * <li>Sorting with filtering (where)</li>
 * <li>Sorting with projection (select)</li>
 * <li>Combined where + select + sort</li>
 * </ul>
 */
public abstract class AbstractSortingTest {

    protected abstract PersonQueryOperations personOps();

    protected abstract ProductQueryOperations productOps();

    @BeforeEach
    @Transactional
    protected void setupTestData() {
        TestDataFactory.clearAllData();
        TestDataFactory.createStandardPersonsAndProducts();
    }

    // =============================================================================================
    // SINGLE-LEVEL ASCENDING SORT
    // =============================================================================================

    @Test
    void sortedBy_age_ascendingOrder() {
        List<Person> sorted = personOps().sortedBy((Person p) -> p.age).toList();

        assertThat(sorted)
                .hasSize(5)
                .isSortedAccordingTo(Comparator.comparing(Person::getAge));
        // Verify specific ordering: 25, 28, 30, 35, 45
        assertThat(sorted.get(0).firstName).isEqualTo("Jane"); // age 25
        assertThat(sorted.get(1).firstName).isEqualTo("Charlie"); // age 28
        assertThat(sorted.get(2).firstName).isEqualTo("John"); // age 30
        assertThat(sorted.get(3).firstName).isEqualTo("Alice"); // age 35
        assertThat(sorted.get(4).firstName).isEqualTo("Bob"); // age 45
    }

    @Test
    void sortedBy_salary_ascendingOrder() {
        List<Person> sorted = personOps().sortedBy((Person p) -> p.salary).toList();

        assertThat(sorted)
                .hasSize(5)
                .isSortedAccordingTo(Comparator.comparing(Person::getSalary));
    }

    @Test
    void sortedBy_firstName_alphabeticalOrder() {
        List<Person> sorted = personOps().sortedBy((Person p) -> p.firstName).toList();

        assertThat(sorted)
                .hasSize(5)
                .isSortedAccordingTo(Comparator.comparing(Person::getFirstName));
        assertThat(sorted.getFirst().firstName).isEqualTo("Alice");
        assertThat(sorted.getLast().firstName).isEqualTo("John");
    }

    // =============================================================================================
    // SINGLE-LEVEL DESCENDING SORT
    // =============================================================================================

    @Test
    void sortedDescendingBy_age_descendingOrder() {
        List<Person> sorted = personOps().sortedDescendingBy((Person p) -> p.age).toList();

        assertThat(sorted)
                .hasSize(5)
                .isSortedAccordingTo(Comparator.comparing(Person::getAge).reversed());
        // Verify specific ordering: 45, 35, 30, 28, 25
        assertThat(sorted.getFirst().firstName).isEqualTo("Bob"); // age 45
        assertThat(sorted.getLast().firstName).isEqualTo("Jane"); // age 25
    }

    @Test
    void sortedDescendingBy_salary_descendingOrder() {
        List<Person> sorted = personOps().sortedDescendingBy((Person p) -> p.salary).toList();

        assertThat(sorted)
                .hasSize(5)
                .isSortedAccordingTo(Comparator.comparing(Person::getSalary).reversed());
    }

    @Test
    void sortedDescendingBy_firstName_reverseAlphabetical() {
        List<Person> sorted = personOps().sortedDescendingBy((Person p) -> p.firstName).toList();

        assertThat(sorted)
                .hasSize(5)
                .isSortedAccordingTo(Comparator.comparing(Person::getFirstName).reversed());
    }

    // =============================================================================================
    // MULTI-LEVEL SORTING
    // =============================================================================================

    @Test
    @Transactional
    protected void multiLevelSort_lastNameThenFirstName() {
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
        List<Person> sorted = personOps().sortedBy((Person p) -> p.firstName)
                .sortedBy((Person p) -> p.lastName)
                .toList();

        assertThat(sorted).hasSize(4);
        // Primary sort: lastName
        assertThat(sorted.get(0).lastName).isEqualTo("Jones");
        assertThat(sorted.get(1).lastName).isEqualTo("Jones");
        assertThat(sorted.get(2).lastName).isEqualTo("Smith");
        assertThat(sorted.get(3).lastName).isEqualTo("Smith");

        // Secondary sort: firstName (within same lastName)
        assertThat(sorted.get(0).firstName).isEqualTo("Alice"); // Jones, Alice
        assertThat(sorted.get(1).firstName).isEqualTo("Charlie"); // Jones, Charlie
        assertThat(sorted.get(2).firstName).isEqualTo("Alice"); // Smith, Alice
        assertThat(sorted.get(3).firstName).isEqualTo("Bob"); // Smith, Bob
    }

    @Test
    void multiLevelSort_ageDescendingThenNameAscending() {
        // Last call wins approach
        List<Person> sorted = personOps().sortedBy((Person p) -> p.firstName)
                .sortedDescendingBy((Person p) -> p.age)
                .toList();

        assertThat(sorted)
                .hasSize(5)
                .isSortedAccordingTo(Comparator.comparing(Person::getAge).reversed()); // Primary sort: age descending
    }

    @Test
    void multiLevelSort_threeLevels() {
        // Last call wins: age is primary
        List<Person> sorted = personOps().sortedBy((Person p) -> p.firstName)
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
        List<Person> sorted = personOps().sortedDescendingBy((Person p) -> p.salary)
                .sortedBy((Person p) -> p.age)
                .toList();

        assertThat(sorted)
                .hasSize(5)
                .isSortedAccordingTo(Comparator.comparing(Person::getAge)); // Primary sort: age ascending
    }

    // =============================================================================================
    // SORTING WITH FILTERING
    // =============================================================================================

    @Test
    void whereAndSortedBy_filterAndSort() {
        List<Person> results = personOps().where((Person p) -> p.age > 30)
                .sortedBy((Person p) -> p.age)
                .toList();

        assertThat(results)
                .hasSize(2) // Alice, Bob
                .isSortedAccordingTo(Comparator.comparing(Person::getAge));
        assertThat(results.getFirst().firstName).isEqualTo("Alice"); // age 35
        assertThat(results.getLast().firstName).isEqualTo("Bob"); // age 45
    }

    @Test
    void whereAndSortedDescendingBy_filterAndReverseSort() {
        List<Person> results = personOps().where((Person p) -> p.active)
                .sortedDescendingBy((Person p) -> p.salary)
                .toList();

        assertThat(results)
                .allMatch(p -> p.active)
                .isSortedAccordingTo(Comparator.comparing(Person::getSalary).reversed());
    }

    @Test
    void complexWhereAndMultiSort() {
        List<Person> results = personOps().where((Person p) -> p.age >= 30)
                .where((Person p) -> p.salary > 60000.0)
                .sortedBy((Person p) -> p.lastName)
                .sortedDescendingBy((Person p) -> p.age)
                .toList();

        assertThat(results)
                .allMatch(p -> p.age >= 30 && p.salary > 60000.0)
                .isSortedAccordingTo(Comparator.comparing(Person::getAge).reversed()); // Primary sort: age descending
    }

    // =============================================================================================
    // SORTING WITH PROJECTION
    // =============================================================================================

    @Test
    void selectAndSortedBy_projectAndSort() {
        List<String> names = personOps().select((Person p) -> p.firstName)
                .sortedBy((String s) -> s)
                .toList();

        assertThat(names)
                .hasSize(5)
                .isSorted() // String natural ordering
                .containsExactly("Alice", "Bob", "Charlie", "Jane", "John");
    }

    @Test
    void selectAndSortedDescendingBy_projectAndReverseSort() {
        List<Integer> ages = personOps().select((Person p) -> p.age)
                .sortedDescendingBy((Integer i) -> i)
                .toList();

        assertThat(ages)
                .hasSize(5)
                .isSortedAccordingTo(Comparator.reverseOrder())
                .containsExactly(45, 35, 30, 28, 25);
    }

    @Test
    void selectAndMultiSort_projectWithMultiLevelSort() {
        List<Double> salaries = personOps().select((Person p) -> p.salary)
                .sortedDescendingBy((Double d) -> d)
                .toList();

        assertThat(salaries)
                .hasSize(5)
                .isSortedAccordingTo(Comparator.reverseOrder()); // Sorted in descending order
    }

    // =============================================================================================
    // COMBINED WHERE + SELECT + SORT
    // =============================================================================================

    @Test
    void whereSelectSorted_fullPipeline() {
        List<String> names = personOps().where((Person p) -> p.age > 30)
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
        List<Integer> ages = personOps().where((Person p) -> p.active)
                .select((Person p) -> p.age)
                .sortedDescendingBy((Integer i) -> i)
                .toList();

        assertThat(ages)
                .isSortedAccordingTo(Comparator.reverseOrder());
    }

    // =============================================================================================
    // PRODUCT ENTITY TESTS
    // =============================================================================================

    @Test
    void sortedBy_productPrice_ascendingBigDecimal() {
        List<Product> sorted = productOps().sortedBy((Product p) -> p.price).toList();

        assertThat(sorted)
                .hasSize(5)
                .isSortedAccordingTo(Comparator.comparing(p -> p.price));
        // Verify first and last
        assertThat(sorted.getFirst().name).isEqualTo("Coffee Maker");
        assertThat(sorted.getLast().name).isEqualTo("Laptop");
    }

    @Test
    void sortedDescendingBy_productStockQuantity_descendingInteger() {
        List<Product> sorted = productOps().sortedDescendingBy((Product p) -> p.stockQuantity).toList();

        assertThat(sorted)
                .hasSize(5)
                .isSortedAccordingTo(Comparator.comparing((Product p) -> p.stockQuantity).reversed());
    }

    @Test
    void productWhereAndSort_availabilityAndPriceSort() {
        List<Product> results = productOps().where((Product p) -> p.available)
                .sortedBy((Product p) -> p.price)
                .toList();

        assertThat(results)
                .allMatch(p -> p.available)
                .isSortedAccordingTo(Comparator.comparing(p -> p.price));
    }
}
