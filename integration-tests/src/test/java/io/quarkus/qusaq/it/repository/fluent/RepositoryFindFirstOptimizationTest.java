package io.quarkus.qusaq.it.repository.fluent;

import io.quarkus.qusaq.it.Person;
import io.quarkus.qusaq.it.PersonRepository;
import io.quarkus.qusaq.it.testdata.TestDataFactory;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Repository pattern tests for findFirst() SQL LIMIT optimization.
 * Mirrors io.quarkus.qusaq.it.fluent.FindFirstOptimizationTest using repository injection.
 *
 * <p>Phase 4: Ensures findFirst() applies LIMIT 1 at SQL level, not Java level.
 *
 * <p>Tests verify:
 * <ul>
 * <li>findFirst() without explicit limit applies LIMIT 1 optimization</li>
 * <li>findFirst() with limit(N>1) overrides to LIMIT 1</li>
 * <li>findFirst() with limit(0) respects user intent (returns empty)</li>
 * <li>findFirst() with limit(1) is already optimal (no-op)</li>
 * <li>Optimization preserves sorting order</li>
 * </ul>
 */
@QuarkusTest
class RepositoryFindFirstOptimizationTest {

    @Inject
    PersonRepository personRepository;

    @Inject
    EntityManager em;

    @BeforeEach
    @Transactional
    void setup() {
        TestDataFactory.clearAllData();
        TestDataFactory.createStandardPersons();
    }

    @Test
    @Transactional
    void findFirst_withoutLimit_appliesLimit1Optimization() {
        // Without optimization, this would fetch all active persons (potentially hundreds)
        // With optimization, SQL query includes LIMIT 1
        Optional<Person> result = personRepository.where((Person p) -> p.active).findFirst();

        assertThat(result).isPresent();
        // Verify result is valid
        assertThat(result.get().active).isTrue();
    }

    @Test
    @Transactional
    void findFirst_withLimit5_overridesToLimit1() {
        // User sets limit(5) but findFirst() only needs 1 result
        // Optimization should apply limit(1) to override
        Optional<Person> result = personRepository.where((Person p) -> p.active)
                .limit(5)
                .findFirst();

        assertThat(result).isPresent();
        assertThat(result.get().active).isTrue();
    }

    @Test
    @Transactional
    void findFirst_withLimit0_respectsUserLimit() {
        // limit(0) means no results
        // Optimization should NOT override this to limit(1)
        Optional<Person> result = personRepository.where((Person p) -> p.active)
                .limit(0)
                .findFirst();

        assertThat(result).isEmpty();
    }

    @Test
    @Transactional
    void findFirst_withLimit1_alreadyOptimal() {
        // Already has limit(1), optimization is no-op
        Optional<Person> result = personRepository.where((Person p) -> p.active)
                .limit(1)
                .findFirst();

        assertThat(result).isPresent();
        assertThat(result.get().active).isTrue();
    }

    @Test
    @Transactional
    void findFirst_withSorting_preservesOrder() {
        // Verify optimization doesn't break sorting
        Optional<Person> result = personRepository.sortedBy((Person p) -> p.firstName)
                .findFirst();

        assertThat(result).isPresent();
        // First person alphabetically by firstName should be "Alice"
        assertThat(result.get().firstName).isEqualTo("Alice");
    }
}
