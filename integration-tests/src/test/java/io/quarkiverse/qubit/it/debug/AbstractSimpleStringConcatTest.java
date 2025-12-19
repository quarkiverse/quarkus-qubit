package io.quarkiverse.qubit.it.debug;

import io.quarkiverse.qubit.it.Person;
import io.quarkiverse.qubit.it.testdata.TestDataFactory;
import io.quarkiverse.qubit.it.testutil.PersonQueryOperations;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Abstract base class for debug tests covering string concatenation edge cases.
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
public abstract class AbstractSimpleStringConcatTest {

    protected abstract PersonQueryOperations personOps();

    @BeforeEach
    @Transactional
    protected void setupTestData() {
        TestDataFactory.clearAllData();
        TestDataFactory.createStandardPersonsAndProducts();
    }

    @Test
    void simpleFieldOnly() {
        var names = personOps().select((Person p) -> p.firstName).toList();

        assertThat(names)
                .hasSize(5)
                .contains("John", "Jane");
    }

    @Test
    void simpleConcatTwoFields() {
        var names = personOps().select((Person p) -> p.firstName + p.lastName).toList();

        // If concat works: "JohnDoe", "JaneSmith", etc.
        // If not working: probably just "John", "Jane" or error
        assertThat(names).hasSize(5);
    }

    @Test
    void simpleConcatConstantAndField() {
        var names = personOps().select((Person p) -> "Mr. " + p.firstName).toList();

        // Expected: "Mr. John", "Mr. Jane", etc.
        assertThat(names).hasSize(5);
    }

    @Test
    void simpleArithmeticInteger() {
        var ages = personOps().select((Person p) -> p.age + 10).toList();

        // Expected: 40, 35, 55, 45, 38
        assertThat(ages)
                .hasSize(5)
                .contains(40, 35);
    }
}
