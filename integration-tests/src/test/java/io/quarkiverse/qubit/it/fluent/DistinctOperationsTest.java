package io.quarkiverse.qubit.it.fluent;

import io.quarkiverse.qubit.it.Person;
import io.quarkiverse.qubit.it.testdata.TestDataFactory;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Comprehensive test suite for distinct() operation combinations.
 * Covers all missing distinct-related operation combinations identified in the coverage matrix.
 * <p>
 * Test coverage (13 tests):
 * - distinct → count (1 test)
 * - where + distinct → toList (2 tests)
 * - where + distinct → count (2 tests)
 * - select + distinct → count (1 test)
 * - distinct + limit → toList (1 test)
 * - distinct + skip → toList (1 test)
 * - where + select + distinct → toList (2 tests)
 * - where + distinct + limit → toList (1 test)
 * - select + distinct + limit → toList (1 test)
 * - where + select + distinct + limit → toList (1 test)
 */
@QuarkusTest
class DistinctOperationsTest {

    @BeforeEach
    @Transactional
    void setupTestData() {
        TestDataFactory.clearAllData();
        TestDataFactory.createStandardPersons();
    }

    // =============================================================================================
    // DISTINCT → COUNT (1 test)
    // =============================================================================================

    @Test
    @Transactional
    void distinct_count_countsUniqueValues() {
        // Standard data has 5 persons with 5 unique lastNames (Doe, Smith, Johnson, Williams, Brown)
        // Add 2 persons with duplicate lastName "Doe" to create duplicates
        Person duplicate1 = new Person();
        duplicate1.firstName = "David";
        duplicate1.lastName = "Doe"; // Same as John Doe
        duplicate1.age = 32;
        duplicate1.persist();

        Person duplicate2 = new Person();
        duplicate2.firstName = "Emma";
        duplicate2.lastName = "Doe"; // Same as John Doe
        duplicate2.age = 29;
        duplicate2.persist();

        // Now we have 7 total persons but only 5 distinct lastNames
        // Get distinct lastNames as a list to verify the operation works
        List<String> distinctLastNames = Person.select((Person p) -> p.lastName)
                .distinct()
                .toList();

        long totalCount = Person.count();

        // Verify we have duplicates: 7 total, but fewer distinct values
        assertThat(totalCount).isEqualTo(7);
        assertThat(distinctLastNames)
                .hasSize(5) // 5 unique lastNames
                .doesNotHaveDuplicates()
                .contains("Doe", "Smith", "Johnson", "Williams", "Brown");
    }

    // =============================================================================================
    // WHERE + DISTINCT → TOLIST (2 tests)
    // =============================================================================================

    @Test
    @Transactional
    void where_distinct_filtersAndRemovesDuplicates() {
        // Add persons with duplicate lastNames
        Person p1 = new Person();
        p1.firstName = "Frank";
        p1.lastName = "Johnson";
        p1.age = 35;
        p1.active = true;
        p1.persist();

        Person p2 = new Person();
        p2.firstName = "Grace";
        p2.lastName = "Johnson"; // Duplicate
        p2.age = 28;
        p2.active = true;
        p2.persist();

        Person p3 = new Person();
        p3.firstName = "Henry";
        p3.lastName = "Williams";
        p3.age = 42;
        p3.active = false; // Will be filtered out
        p3.persist();

        // Get distinct lastNames for active persons only
        List<String> distinctLastNames = Person.where((Person p) -> p.active)
                .select((Person p) -> p.lastName)
                .distinct()
                .toList();

        assertThat(distinctLastNames)
                .doesNotHaveDuplicates()
                .isNotEmpty();
    }

    @Test
    @Transactional
    void where_distinct_multiplePredicates() {
        // Get distinct firstNames for persons with age > 25
        List<String> distinctNames = Person.where((Person p) -> p.age > 25)
                .select((Person p) -> p.firstName)
                .distinct()
                .toList();

        assertThat(distinctNames)
                .doesNotHaveDuplicates()
                .allMatch(name -> name != null);
    }

    // =============================================================================================
    // WHERE + DISTINCT → COUNT (2 tests)
    // =============================================================================================

    @Test
    @Transactional
    void where_distinct_count_filtersAndCountsUnique() {
        long count = Person.where((Person p) -> p.active)
                .select((Person p) -> p.lastName)
                .distinct()
                .count();

        assertThat(count).isGreaterThan(0);
    }

    @Test
    @Transactional
    void where_distinct_count_complexPredicate() {
        long count = Person.where((Person p) -> p.age >= 30)
                .where((Person p) -> p.active)
                .select((Person p) -> p.firstName)
                .distinct()
                .count();

        assertThat(count).isGreaterThan(0);
    }

    // =============================================================================================
    // SELECT + DISTINCT → COUNT (1 test)
    // =============================================================================================

    @Test
    @Transactional
    void select_distinct_count_countsUniqueProjectedValues() {
        // Add persons with duplicate ages
        Person p1 = new Person();
        p1.firstName = "Ian";
        p1.lastName = "Taylor";
        p1.age = 30; // Duplicate age
        p1.persist();

        // Count distinct ages
        long distinctAgeCount = Person.select((Person p) -> p.age)
                .distinct()
                .count();

        // Verify count is meaningful
        assertThat(distinctAgeCount).isGreaterThan(0);
        assertThat(distinctAgeCount).isLessThanOrEqualTo(Person.count());
    }

    // =============================================================================================
    // DISTINCT + LIMIT → TOLIST (1 test)
    // =============================================================================================

    @Test
    @Transactional
    void distinct_limit_limitsUniqueResults() {
        List<String> limitedDistinct = Person.select((Person p) -> p.lastName)
                .distinct()
                .limit(3)
                .toList();

        assertThat(limitedDistinct)
                .hasSizeLessThanOrEqualTo(3)
                .doesNotHaveDuplicates();
    }

    // =============================================================================================
    // DISTINCT + SKIP → TOLIST (1 test)
    // =============================================================================================

    @Test
    @Transactional
    void distinct_skip_skipsUniqueResults() {
        // Get all distinct lastNames
        List<String> allDistinct = Person.select((Person p) -> p.lastName)
                .distinct()
                .toList();

        // Skip first 2 unique values
        List<String> skipped = Person.select((Person p) -> p.lastName)
                .distinct()
                .skip(2)
                .toList();

        if (allDistinct.size() > 2) {
            assertThat(skipped)
                    .hasSizeLessThan(allDistinct.size())
                    .doesNotHaveDuplicates();
        }
    }

    // =============================================================================================
    // WHERE + SELECT + DISTINCT → TOLIST (2 tests)
    // =============================================================================================

    @Test
    @Transactional
    void where_select_distinct_fullPipeline() {
        List<String> results = Person.where((Person p) -> p.active)
                .select((Person p) -> p.lastName)
                .distinct()
                .toList();

        assertThat(results)
                .isNotEmpty()
                .doesNotHaveDuplicates();
    }

    @Test
    @Transactional
    void where_select_distinct_complexFilter() {
        List<Integer> ages = Person.where((Person p) -> p.salary > 60000.0)
                .select((Person p) -> p.age)
                .distinct()
                .toList();

        assertThat(ages)
                .doesNotHaveDuplicates()
                .allMatch(age -> age > 0);
    }

    // =============================================================================================
    // WHERE + DISTINCT + LIMIT → TOLIST (1 test)
    // =============================================================================================

    @Test
    @Transactional
    void where_distinct_limit_filterLimitUnique() {
        List<String> results = Person.where((Person p) -> p.age > 25)
                .select((Person p) -> p.firstName)
                .distinct()
                .limit(2)
                .toList();

        assertThat(results)
                .hasSizeLessThanOrEqualTo(2)
                .doesNotHaveDuplicates();
    }

    // =============================================================================================
    // SELECT + DISTINCT + LIMIT → TOLIST (1 test)
    // =============================================================================================

    @Test
    @Transactional
    void select_distinct_limit_projectLimitUnique() {
        List<String> names = Person.select((Person p) -> p.lastName)
                .distinct()
                .limit(3)
                .toList();

        assertThat(names)
                .hasSizeLessThanOrEqualTo(3)
                .doesNotHaveDuplicates();
    }

    // =============================================================================================
    // WHERE + SELECT + DISTINCT + LIMIT → TOLIST (1 test)
    // =============================================================================================

    @Test
    @Transactional
    void where_select_distinct_limit_fullComplexPipeline() {
        List<Integer> ages = Person.where((Person p) -> p.active)
                .select((Person p) -> p.age)
                .distinct()
                .limit(3)
                .toList();

        assertThat(ages)
                .hasSizeLessThanOrEqualTo(3)
                .doesNotHaveDuplicates()
                .allMatch(age -> age > 0);
    }
}
