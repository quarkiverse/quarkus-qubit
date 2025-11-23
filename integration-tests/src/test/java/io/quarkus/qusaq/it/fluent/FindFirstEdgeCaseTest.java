package io.quarkus.qusaq.it.fluent;

import io.quarkus.qusaq.it.Person;
import io.quarkus.qusaq.it.testdata.TestDataFactory;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Edge case tests verifying findFirst() return logic correctness.
 * Tests that isEmpty() check is necessary even with limit(1) optimization.
 */
@QuarkusTest
class FindFirstEdgeCaseTest {

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
    void findFirst_noMatches_returnsEmptyOptional() {
        // Query with no matches → isEmpty() must return true
        Optional<Person> result = Person.where((Person p) -> p.age > 1000)
                .findFirst();

        // Verify isEmpty() logic works: Optional.empty() returned
        assertThat(result)
                .isEmpty()
                .isEqualTo(Optional.empty());
    }

    @Test
    @Transactional
    void findFirst_oneMatch_returnsOptionalOfFirstElement() {
        // Query with exactly one match → isEmpty() must return false
        Optional<Person> result = Person.where((Person p) -> p.email.equals("alice.williams@example.com"))
                .findFirst();

        // Verify !isEmpty() logic works: Optional.of(results.get(0)) returned
        assertThat(result)
                .isPresent()
                .hasValueSatisfying(p -> assertThat(p.firstName).isEqualTo("Alice"));
    }

    @Test
    @Transactional
    void findFirst_manyMatches_returnsOptionalOfFirstElementOnly() {
        // Query with multiple matches → limit(1) optimization → size is 1
        Optional<Person> result = Person.where((Person p) -> p.active)
                .findFirst();

        // Verify results.get(0) is safe (only one element due to LIMIT 1)
        assertThat(result).isPresent();

        // Verify it's the actual FIRST result (not second, third, etc.)
        List<Person> allActive = Person.where((Person p) -> p.active).toList();
        assertThat(result.get().id).isEqualTo(allActive.get(0).id);
    }

    @Test
    @Transactional
    void findFirst_withLimit0_alwaysReturnsEmpty() {
        // User explicitly sets limit(0) → results will be empty
        // isEmpty() check must handle this edge case
        Optional<Person> result = Person.where((Person p) -> p.active)
                .limit(0)
                .findFirst();

        // Verify isEmpty() correctly returns Optional.empty()
        assertThat(result).isEmpty();
    }

    @Test
    @Transactional
    void findFirst_elementIsNeverNull() {
        // Verify Optional.of() is safe (never receives null)
        Optional<Person> result = Person.where((Person p) -> p.active)
                .findFirst();

        // JPA never returns null entities in result lists
        // So results.get(0) is always non-null when present
        assertThat(result)
                .isPresent()
                .hasValueSatisfying(p -> assertThat(p).isNotNull());
    }
}
