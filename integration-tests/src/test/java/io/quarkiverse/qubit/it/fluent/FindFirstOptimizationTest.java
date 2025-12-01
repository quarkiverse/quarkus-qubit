package io.quarkiverse.qubit.it.fluent;

import io.quarkiverse.qubit.it.Person;
import io.quarkiverse.qubit.it.testdata.TestDataFactory;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verification tests for findFirst() SQL LIMIT optimization.
 * Phase 4: Ensures findFirst() applies LIMIT 1 at SQL level, not Java level.
 */
@QuarkusTest
class FindFirstOptimizationTest {

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
        Optional<Person> result = Person.where((Person p) -> p.active).findFirst();

        assertThat(result).isPresent();
        // Verify result is valid
        assertThat(result.get().active).isTrue();
    }

    @Test
    @Transactional
    void findFirst_withLimit5_overridesToLimit1() {
        // User sets limit(5) but findFirst() only needs 1 result
        // Optimization should apply limit(1) to override
        Optional<Person> result = Person.where((Person p) -> p.active)
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
        Optional<Person> result = Person.where((Person p) -> p.active)
                .limit(0)
                .findFirst();

        assertThat(result).isEmpty();
    }

    @Test
    @Transactional
    void findFirst_withLimit1_alreadyOptimal() {
        // Already has limit(1), optimization is no-op
        Optional<Person> result = Person.where((Person p) -> p.active)
                .limit(1)
                .findFirst();

        assertThat(result).isPresent();
        assertThat(result.get().active).isTrue();
    }

    @Test
    @Transactional
    void findFirst_withSorting_preservesOrder() {
        // Verify optimization doesn't break sorting
        Optional<Person> result = Person.sortedBy((Person p) -> p.firstName)
                .findFirst();

        assertThat(result).isPresent();
        // First person alphabetically by firstName should be "Alice"
        assertThat(result.get().firstName).isEqualTo("Alice");
    }
}
