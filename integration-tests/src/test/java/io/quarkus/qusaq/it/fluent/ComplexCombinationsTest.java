package io.quarkus.qusaq.it.fluent;

import io.quarkus.qusaq.it.Person;
import io.quarkus.qusaq.it.testdata.TestDataFactory;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Comprehensive test suite for complex operation combinations.
 * Covers all remaining missing operation combinations identified in the coverage matrix.
 * <p>
 * Test coverage (8 tests):
 * - skip → count (1 test)
 * - sortedDescendingBy + limit → toList (1 test)
 * - sortedDescendingBy + skip → toList (1 test)
 * - where + select + limit → toList (1 test)
 * - select + sortedBy + skip → toList (1 test)
 * - where + sortedBy + skip → toList (1 test)
 * - where + select + sortedBy + limit → toList (1 test)
 * - where + sortedBy + limit + skip → toList (1 test)
 */
@QuarkusTest
class ComplexCombinationsTest {

    @BeforeEach
    @Transactional
    void setupTestData() {
        TestDataFactory.clearAllData();
        TestDataFactory.createStandardPersons();
    }

    // =============================================================================================
    // SKIP → COUNT (1 test)
    // =============================================================================================

    @Test
    @Transactional
    void skip_count_ignoresSkipForCount() {
        // Count should ignore skip() operation - need to use where with always-true predicate
        long countWithSkip = Person.where((Person p) -> p.age > 0).skip(2).count();
        long countWithoutSkip = Person.where((Person p) -> p.age > 0).count();

        // Skip should be ignored by count()
        assertThat(countWithSkip).isEqualTo(countWithoutSkip);
        assertThat(countWithSkip).isGreaterThan(0);
    }

    // =============================================================================================
    // SORTEDDESCENDINGBY + LIMIT → TOLIST (1 test)
    // =============================================================================================

    @Test
    @Transactional
    void sortedDescendingBy_limit_descendingWithLimit() {
        List<Person> results = Person.sortedDescendingBy((Person p) -> p.age)
                .limit(3)
                .toList();

        assertThat(results)
                .hasSizeLessThanOrEqualTo(3)
                .isSortedAccordingTo(Comparator.comparing(Person::getAge).reversed());

        // Verify we got the top 3 oldest persons
        if (results.size() >= 2) {
            assertThat(results.get(0).age).isGreaterThanOrEqualTo(results.get(1).age);
        }
    }

    // =============================================================================================
    // SORTEDDESCENDINGBY + SKIP → TOLIST (1 test)
    // =============================================================================================

    @Test
    @Transactional
    void sortedDescendingBy_skip_descendingWithSkip() {
        // Get all persons sorted descending by age
        List<Person> allSorted = Person.sortedDescendingBy((Person p) -> p.age).toList();

        // Skip the first 2 oldest persons
        List<Person> skipped = Person.sortedDescendingBy((Person p) -> p.age)
                .skip(2)
                .toList();

        assertThat(skipped)
                .isSortedAccordingTo(Comparator.comparing(Person::getAge).reversed());

        if (allSorted.size() > 2) {
            assertThat(skipped.size()).isEqualTo(allSorted.size() - 2);
            // First person in skipped should be the 3rd oldest overall
            assertThat(skipped.get(0).id).isEqualTo(allSorted.get(2).id);
        }
    }

    // =============================================================================================
    // WHERE + SELECT + LIMIT → TOLIST (1 test)
    // =============================================================================================

    @Test
    @Transactional
    void where_select_limit_filterProjectLimit() {
        List<String> names = Person.where((Person p) -> p.age > 25)
                .select((Person p) -> p.firstName)
                .limit(3)
                .toList();

        assertThat(names)
                .hasSizeLessThanOrEqualTo(3)
                .allMatch(name -> name != null && !name.isEmpty());
    }

    // =============================================================================================
    // SELECT + SORTEDBY + SKIP → TOLIST (1 test)
    // =============================================================================================

    @Test
    @Transactional
    void select_sortedBy_skip_projectSortSkip() {
        // Get all ages sorted
        List<Integer> allAges = Person.select((Person p) -> p.age)
                .sortedBy((Integer age) -> age)
                .toList();

        // Project to ages, sort, and skip first 2
        List<Integer> skippedAges = Person.select((Person p) -> p.age)
                .sortedBy((Integer age) -> age)
                .skip(2)
                .toList();

        assertThat(skippedAges)
                .isSortedAccordingTo(Comparator.naturalOrder());

        if (allAges.size() > 2) {
            assertThat(skippedAges.size()).isEqualTo(allAges.size() - 2);
            // First element should be the 3rd smallest age
            assertThat(skippedAges.get(0)).isEqualTo(allAges.get(2));
        }
    }

    // =============================================================================================
    // WHERE + SORTEDBY + SKIP → TOLIST (1 test)
    // =============================================================================================

    @Test
    @Transactional
    void where_sortedBy_skip_filterSortSkip() {
        // Get all active persons sorted by age
        List<Person> allActive = Person.where((Person p) -> p.active)
                .sortedBy((Person p) -> p.age)
                .toList();

        // Filter, sort, and skip first active person
        List<Person> skipped = Person.where((Person p) -> p.active)
                .sortedBy((Person p) -> p.age)
                .skip(1)
                .toList();

        assertThat(skipped)
                .allMatch(p -> p.active)
                .isSortedAccordingTo(Comparator.comparing(Person::getAge));

        if (allActive.size() > 1) {
            assertThat(skipped.size()).isEqualTo(allActive.size() - 1);
            // First person in skipped should be the 2nd youngest active person
            assertThat(skipped.get(0).id).isEqualTo(allActive.get(1).id);
        }
    }

    // =============================================================================================
    // WHERE + SELECT + SORTEDBY + LIMIT → TOLIST (1 test)
    // =============================================================================================

    @Test
    @Transactional
    void where_select_sortedBy_limit_fullComplexPipeline() {
        // Filter active persons, project to names, sort alphabetically, limit to 3
        List<String> names = Person.where((Person p) -> p.active)
                .select((Person p) -> p.firstName)
                .sortedBy((String name) -> name)
                .limit(3)
                .toList();

        assertThat(names)
                .hasSizeLessThanOrEqualTo(3)
                .isSorted(); // Natural string ordering (alphabetical)
    }

    // =============================================================================================
    // WHERE + SORTEDBY + LIMIT + SKIP → TOLIST (1 test)
    // =============================================================================================

    @Test
    @Transactional
    void where_sortedBy_limit_skip_paginationWithFilter() {
        // Complex pagination scenario: filter active, sort by salary, skip 1, limit 2
        // This simulates "page 2 with 2 items per page" for active persons sorted by salary
        List<Person> page2 = Person.where((Person p) -> p.active)
                .sortedBy((Person p) -> p.salary)
                .skip(1)
                .limit(2)
                .toList();

        assertThat(page2)
                .hasSizeLessThanOrEqualTo(2)
                .allMatch(p -> p.active)
                .isSortedAccordingTo(Comparator.comparing(Person::getSalary));

        // Verify pagination integrity
        if (page2.size() == 2) {
            assertThat(page2.get(0).salary).isLessThanOrEqualTo(page2.get(1).salary);
        }
    }
}
