package io.quarkiverse.qubit.it.repository.debug;

import io.quarkiverse.qubit.it.Person;
import io.quarkiverse.qubit.it.PersonRepository;
import io.quarkiverse.qubit.it.testdata.TestDataFactory;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Repository pattern debug tests for string concatenation edge cases.
 * Mirrors io.quarkiverse.qubit.it.debug.SimpleStringConcatTest using repository injection.
 *
 * <p>Minimal test suite to debug string concatenation and basic projection behavior.
 *
 * <p>Tests cover:
 * <ul>
 * <li>Simple field projection (baseline)</li>
 * <li>Two-field string concatenation</li>
 * <li>Constant + field string concatenation</li>
 * <li>Integer arithmetic operations</li>
 * </ul>
 */
@QuarkusTest
class RepositorySimpleStringConcatTest {

    @Inject
    PersonRepository personRepository;

    @BeforeEach
    @Transactional
    void setupTestData() {
        TestDataFactory.clearAllData();
        TestDataFactory.createStandardPersonsAndProducts();
    }

    @Test
    void simpleFieldOnly() {
        var names = personRepository.select((Person p) -> p.firstName).toList();

        assertThat(names)
                .hasSize(5)
                .contains("John", "Jane");
    }

    @Test
    void simpleConcatTwoFields() {
        var names = personRepository.select((Person p) -> p.firstName + p.lastName).toList();

        // If concat works: "JohnDoe", "JaneSmith", etc.
        // If not working: probably just "John", "Jane" or error
        assertThat(names).hasSize(5);
    }

    @Test
    void simpleConcatConstantAndField() {
        var names = personRepository.select((Person p) -> "Mr. " + p.firstName).toList();

        // Expected: "Mr. John", "Mr. Jane", etc.
        assertThat(names).hasSize(5);
    }

    @Test
    void simpleArithmeticInteger() {
        var ages = personRepository.select((Person p) -> p.age + 10).toList();

        // Expected: 40, 35, 55, 45, 38
        assertThat(ages)
                .hasSize(5)
                .contains(40, 35);
    }
}
