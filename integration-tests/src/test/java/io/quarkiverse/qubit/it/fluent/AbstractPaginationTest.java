package io.quarkiverse.qubit.it.fluent;

import io.quarkiverse.qubit.it.Person;
import io.quarkiverse.qubit.it.testdata.TestDataFactory;
import io.quarkiverse.qubit.it.testutil.PersonQueryOperations;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Abstract base class for pagination (skip/limit) operation tests.
 *
 * <p>Contains all test methods that can be run with either static entity methods
 * or repository instance methods.
 */
public abstract class AbstractPaginationTest {

    protected abstract PersonQueryOperations personOps();

    @BeforeEach
    @Transactional
    protected void setupTestData() {
        TestDataFactory.clearAllData();
        TestDataFactory.createStandardPersons();
    }

    // =============================================================================================
    // SKIP TESTS
    // =============================================================================================

    @Test
    @Transactional
    void skip_offsetsResults() {
        // Get all persons sorted by ID
        List<Person> all = personOps().sortedBy((Person p) -> p.id).toList();

        // Skip first 2 results
        List<Person> skipped = personOps().sortedBy((Person p) -> p.id).skip(2).toList();

        assertThat(skipped)
                .hasSize(all.size() - 2)
                .satisfies(list -> assertThat(((Person) list.getFirst()).id).isEqualTo(all.get(2).id));
    }

    @Test
    @Transactional
    void skip_withPredicate_offsetsFilteredResults() {
        // Get all active persons sorted by ID
        List<Person> allActive = personOps().where((Person p) -> p.active)
                .sortedBy((Person p) -> p.id)
                .toList();

        // Skip first 1 active person
        List<Person> skippedActive = personOps().where((Person p) -> p.active)
                .sortedBy((Person p) -> p.id)
                .skip(1)
                .toList();

        assertThat(skippedActive)
                .hasSize(allActive.size() - 1)
                .satisfies(list -> assertThat(((Person) list.getFirst()).id).isEqualTo(allActive.get(1).id));
    }

    @Test
    @Transactional
    void skip_zero_returnsAllResults() {
        List<Person> all = personOps().sortedBy((Person p) -> p.id).toList();
        List<Person> skipped = personOps().sortedBy((Person p) -> p.id).skip(0).toList();

        assertThat(skipped).hasSize(all.size());
    }

    // =============================================================================================
    // LIMIT TESTS
    // =============================================================================================

    @Test
    @Transactional
    void limit_restrictsResults() {
        List<Person> limited = personOps().sortedBy((Person p) -> p.id).limit(3).toList();

        assertThat(limited).hasSize(3);
    }

    @Test
    @Transactional
    void limit_withPredicate_restrictsFilteredResults() {
        List<Person> limited = personOps().where((Person p) -> p.active)
                .sortedBy((Person p) -> p.id)
                .limit(2)
                .toList();

        assertThat(limited)
                .hasSize(2)
                .allMatch(obj -> ((Person) obj).active);
    }

    @Test
    @Transactional
    void limit_exceedsResults_returnsAllAvailable() {
        long totalCount = personOps().where((Person p) -> p.age > 0).count();
        List<Person> limited = personOps().where((Person p) -> p.age > 0)
                .limit((int) totalCount + 100)
                .toList();

        assertThat(limited).hasSize((int) totalCount);
    }

    // =============================================================================================
    // SKIP + LIMIT TESTS (Pagination)
    // =============================================================================================

    @Test
    @Transactional
    void skipAndLimit_pagination_page1() {
        // Page 1: skip 0, limit 2
        List<Person> page1 = personOps().sortedBy((Person p) -> p.id)
                .skip(0)
                .limit(2)
                .toList();

        assertThat(page1).hasSize(2);
    }

    @Test
    @Transactional
    void skipAndLimit_pagination_page2() {
        List<Person> all = personOps().sortedBy((Person p) -> p.id).toList();

        // Page 2: skip 2, limit 2
        List<Person> page2 = personOps().sortedBy((Person p) -> p.id)
                .skip(2)
                .limit(2)
                .toList();

        assertThat(page2)
                .hasSize(2)
                .satisfies(list -> {
                    assertThat(((Person) list.get(0)).id).isEqualTo(all.get(2).id);
                    assertThat(((Person) list.get(1)).id).isEqualTo(all.get(3).id);
                });
    }

    @Test
    @Transactional
    void skipAndLimit_withPredicateAndSort() {
        // Get active persons, sorted by age descending, page 2 (skip 1, limit 2)
        List<Person> results = personOps().where((Person p) -> p.active)
                .sortedDescendingBy((Person p) -> p.age)
                .skip(1)
                .limit(2)
                .toList();

        assertThat(results)
                .hasSize(2)
                .allMatch(obj -> ((Person) obj).active);

        // Verify descending order
        if (results.size() == 2) {
            assertThat(((Person) results.getFirst()).age).isGreaterThanOrEqualTo(((Person) results.getLast()).age);
        }
    }

    @Test
    @Transactional
    void skipAndLimit_withProjection() {
        // Project to names, skip first 2, limit to 3
        List<String> names = personOps().select((Person p) -> p.firstName)
                .sortedBy((String name) -> name)
                .skip(2)
                .limit(3)
                .toList();

        assertThat(names).hasSizeLessThanOrEqualTo(3);
    }

    // =============================================================================================
    // COMPLEX COMPOSITION TESTS
    // =============================================================================================

    @Test
    @Transactional
    void complexQuery_whereSortPage() {
        // Complex composition: WHERE + SORT + PAGINATION
        List<Person> results = personOps().where((Person p) -> p.salary > 50000.0)
                .sortedBy((Person p) -> p.firstName)
                .skip(0)
                .limit(3)
                .toList();

        assertThat(results)
                .hasSizeLessThanOrEqualTo(3)
                .allMatch(obj -> ((Person) obj).salary > 50000.0);
    }

    @Test
    @Transactional
    void complexQuery_multipleWhereWithPagination() {
        List<Person> results = personOps().where((Person p) -> p.age > 20)
                .where((Person p) -> p.active)
                .sortedBy((Person p) -> p.firstName)
                .skip(0)
                .limit(5)
                .toList();

        assertThat(results)
                .hasSizeLessThanOrEqualTo(5)
                .allMatch(obj -> {
                    Person person = (Person) obj;
                    return person.age > 20 && person.active;
                });
    }

    @Test
    @Transactional
    void limit_withCount_returnsCorrectCount() {
        // Limit doesn't affect count()
        long count = personOps().where((Person p) -> p.active).limit(2).count();
        long expectedCount = personOps().where((Person p) -> p.active).count();

        // Note: limit() on count query should be ignored
        assertThat(count).isEqualTo(expectedCount);
    }
}
