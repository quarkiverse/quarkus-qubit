package io.quarkiverse.qubit.it.repository.fluent;

import io.quarkiverse.qubit.it.Person;
import io.quarkiverse.qubit.it.PersonRepository;
import io.quarkiverse.qubit.it.testdata.TestDataFactory;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Repository pattern tests for pagination (skip/limit) operations.
 * Mirrors io.quarkiverse.qubit.it.fluent.PaginationTest using repository injection.
 *
 * <p>Tests for Phase 4: skip() and limit() methods.
 */
@QuarkusTest
class RepositoryPaginationTest {

    @Inject
    PersonRepository personRepository;

    @Inject
    EntityManager em;

    @BeforeEach
    @Transactional
    void setup() {
        TestDataFactory.clearAllData();
        TestDataFactory.createStandardPersonsAndProducts();
    }

    // =============================================================================================
    // SKIP TESTS
    // =============================================================================================

    @Test
    @Transactional
    void skip_offsetsResults() {
        // Get all persons sorted by ID
        List<Person> all = personRepository.sortedBy((Person p) -> p.id).toList();

        // Skip first 2 results
        List<Person> skipped = personRepository.sortedBy((Person p) -> p.id).skip(2).toList();

        assertThat(skipped)
                .hasSize(all.size() - 2)
                .satisfies(list -> assertThat(((Person) list.get(0)).id).isEqualTo(all.get(2).id));
    }

    @Test
    @Transactional
    void skip_withPredicate_offsetsFilteredResults() {
        // Get all active persons sorted by ID
        List<Person> allActive = personRepository.where((Person p) -> p.active)
                .sortedBy((Person p) -> p.id)
                .toList();

        // Skip first 1 active person
        List<Person> skippedActive = personRepository.where((Person p) -> p.active)
                .sortedBy((Person p) -> p.id)
                .skip(1)
                .toList();

        assertThat(skippedActive)
                .hasSize(allActive.size() - 1)
                .satisfies(list -> assertThat(((Person) list.get(0)).id).isEqualTo(allActive.get(1).id));
    }

    @Test
    @Transactional
    void skip_zero_returnsAllResults() {
        List<Person> all = personRepository.sortedBy((Person p) -> p.id).toList();
        List<Person> skipped = personRepository.sortedBy((Person p) -> p.id).skip(0).toList();

        assertThat(skipped).hasSize(all.size());
    }

    // =============================================================================================
    // LIMIT TESTS
    // =============================================================================================

    @Test
    @Transactional
    void limit_restrictsResults() {
        List<Person> limited = personRepository.sortedBy((Person p) -> p.id).limit(3).toList();

        assertThat(limited).hasSize(3);
    }

    @Test
    @Transactional
    void limit_withPredicate_restrictsFilteredResults() {
        List<Person> limited = personRepository.where((Person p) -> p.active)
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
        long totalCount = personRepository.where((Person p) -> p.age > 0).count();
        List<Person> limited = personRepository.where((Person p) -> p.age > 0)
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
        List<Person> page1 = personRepository.sortedBy((Person p) -> p.id)
                .skip(0)
                .limit(2)
                .toList();

        assertThat(page1).hasSize(2);
    }

    @Test
    @Transactional
    void skipAndLimit_pagination_page2() {
        List<Person> all = personRepository.sortedBy((Person p) -> p.id).toList();

        // Page 2: skip 2, limit 2
        List<Person> page2 = personRepository.sortedBy((Person p) -> p.id)
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
        List<Person> results = personRepository.where((Person p) -> p.active)
                .sortedDescendingBy((Person p) -> p.age)
                .skip(1)
                .limit(2)
                .toList();

        assertThat(results)
                .hasSize(2)
                .allMatch(obj -> ((Person) obj).active);

        // Verify descending order
        if (results.size() == 2) {
            assertThat(((Person) results.get(0)).age).isGreaterThanOrEqualTo(((Person) results.get(1)).age);
        }
    }

    @Test
    @Transactional
    void skipAndLimit_withProjection() {
        // Project to names, skip first 2, limit to 3
        List<String> names = personRepository.select((Person p) -> p.firstName)
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
        List<Person> results = personRepository.where((Person p) -> p.salary > 50000.0)
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
        List<Person> results = personRepository.where((Person p) -> p.age > 20)
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
        long count = personRepository.where((Person p) -> p.active).limit(2).count();
        long expectedCount = personRepository.where((Person p) -> p.active).count();

        // Note: limit() on count query should be ignored
        assertThat(count).isEqualTo(expectedCount);
    }
}
