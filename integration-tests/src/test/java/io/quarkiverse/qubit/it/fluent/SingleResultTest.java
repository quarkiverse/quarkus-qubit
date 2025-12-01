package io.quarkiverse.qubit.it.fluent;

import io.quarkiverse.qubit.it.Person;
import io.quarkiverse.qubit.it.testdata.TestDataFactory;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.persistence.NoResultException;
import jakarta.persistence.NonUniqueResultException;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for getSingleResult() and findFirst() terminal operations.
 * Phase 2.2+: Tests single-result query patterns.
 */
@QuarkusTest
class SingleResultTest {

    @BeforeEach
    @Transactional
    void setupTestData() {
        TestDataFactory.clearAllData();
        TestDataFactory.createStandardPersons();
    }

    // =============================================================================================
    // getSingleResult() TESTS
    // =============================================================================================

    @Test
    @Transactional
    void getSingleResult_uniqueMatch_returnsEntity() {
        // Standard test data has unique email addresses
        Person result = Person.where((Person p) -> p.email.equals("alice.williams@example.com"))
                .getSingleResult();

        assertThat(result)
                .isNotNull()
                .extracting(Person::getFirstName)
                .isEqualTo("Alice");
    }

    @Test
    @Transactional
    void getSingleResult_noMatch_throwsNoResultException() {
        assertThatThrownBy(() ->
                Person.where((Person p) -> p.email.equals("nonexistent@example.com"))
                        .getSingleResult()
        )
                .isInstanceOf(NoResultException.class)
                .hasMessageContaining("expected exactly one result but found none");
    }

    @Test
    @Transactional
    void getSingleResult_multipleMatches_throwsNonUniqueResultException() {
        // Query for all active people (assuming multiple exist in standard data)
        assertThatThrownBy(() ->
                Person.where((Person p) -> p.active).getSingleResult()
        )
                .isInstanceOf(NonUniqueResultException.class)
                .hasMessageContaining("expected exactly one result but found");
    }

    @Test
    @Transactional
    void getSingleResult_withComplexPredicate_returnsEntity() {
        // Find person with specific email and age (John Doe is 30 years old)
        Person result = Person.where((Person p) -> p.age == 30 && p.email.equals("john.doe@example.com"))
                .getSingleResult();

        assertThat(result)
                .isNotNull()
                .satisfies(p -> {
                    assertThat(p.age).isEqualTo(30);
                    assertThat(p.firstName).isEqualTo("John");
                });
    }

    @Test
    @Transactional
    void getSingleResult_withMultipleWhere_returnsEntity() {
        // Multiple where() calls combined with AND (Alice Williams is active)
        Person result = Person.where((Person p) -> p.firstName.equals("Alice"))
                .where((Person p) -> p.lastName.equals("Williams"))
                .getSingleResult();

        assertThat(result)
                .isNotNull()
                .extracting(Person::getFirstName)
                .isEqualTo("Alice");
    }

    // =============================================================================================
    // findFirst() TESTS
    // =============================================================================================

    @Test
    @Transactional
    void findFirst_hasResults_returnsOptionalWithFirstResult() {
        Optional<Person> result = Person.where((Person p) -> p.active).findFirst();

        assertThat(result)
                .isPresent()
                .get()
                .extracting(Person::isActive)
                .isEqualTo(true);
    }

    @Test
    @Transactional
    void findFirst_noResults_returnsEmptyOptional() {
        Optional<Person> result = Person.where((Person p) -> p.email.equals("nonexistent@example.com"))
                .findFirst();

        assertThat(result).isEmpty();
    }

    @Test
    @Transactional
    void findFirst_multipleResults_returnsFirstOnly() {
        // Query for all active people (multiple results expected)
        // First verify that multiple results exist
        List<Person> allResults = Person.where((Person p) -> p.active).toList();
        assertThat(allResults).hasSizeGreaterThan(1);

        // Now verify findFirst() returns only one result (the first one)
        Optional<Person> firstResult = Person.where((Person p) -> p.active).findFirst();

        assertThat(firstResult)
                .isPresent()
                .get()
                .satisfies(person -> {
                    assertThat(person.isActive()).isTrue();
                    // Verify it's the same as the first element from toList()
                    assertThat(person.id).isEqualTo(allResults.get(0).id);
                });
    }

    @Test
    @Transactional
    void findFirst_withComplexPredicate_returnsOptionalWithEntity() {
        Optional<Person> result = Person.where((Person p) -> p.age > 25 && p.active)
                .findFirst();

        assertThat(result)
                .isPresent()
                .get()
                .satisfies(p -> {
                    assertThat(p.age).isGreaterThan(25);
                    assertThat(p.active).isTrue();
                });
    }

    @Test
    @Transactional
    void findFirst_withMultipleWhere_returnsOptionalWithEntity() {
        // Multiple where() calls combined with AND
        Optional<Person> result = Person.where((Person p) -> p.age > 20)
                .where((Person p) -> p.active)
                .findFirst();

        assertThat(result)
                .isPresent()
                .get()
                .satisfies(p -> {
                    assertThat(p.age).isGreaterThan(20);
                    assertThat(p.active).isTrue();
                });
    }

    @Test
    @Transactional
    void findFirst_uniqueEmail_returnsExactMatch() {
        Optional<Person> result = Person.where((Person p) -> p.email.equals("alice.williams@example.com"))
                .findFirst();

        assertThat(result)
                .isPresent()
                .get()
                .extracting(Person::getFirstName)
                .isEqualTo("Alice");
    }

    // =============================================================================================
    // EDGE CASES
    // =============================================================================================

    @Test
    @Transactional
    void getSingleResult_afterMultiplePredicates_worksCorrectly() {
        // Chain multiple where() calls - should still get single result (Alice Williams is active)
        Person result = Person.where((Person p) -> p.firstName.equals("Alice"))
                .where((Person p) -> p.lastName.equals("Williams"))
                .where((Person p) -> p.active)
                .getSingleResult();

        assertThat(result)
                .isNotNull()
                .satisfies(p -> {
                    assertThat(p.firstName).isEqualTo("Alice");
                    assertThat(p.lastName).isEqualTo("Williams");
                    assertThat(p.active).isTrue();
                });
    }

    @Test
    @Transactional
    void findFirst_afterMultiplePredicates_worksCorrectly() {
        // Chain multiple where() calls
        Optional<Person> result = Person.where((Person p) -> p.age > 18)
                .where((Person p) -> p.active)
                .where((Person p) -> p.salary > 50000.0)
                .findFirst();

        assertThat(result)
                .isPresent()
                .get()
                .satisfies(p -> {
                    assertThat(p.age).isGreaterThan(18);
                    assertThat(p.active).isTrue();
                    assertThat(p.salary).isGreaterThan(50000.0);
                });
    }
}
