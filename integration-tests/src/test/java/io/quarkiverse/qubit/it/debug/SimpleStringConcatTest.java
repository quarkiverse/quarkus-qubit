package io.quarkiverse.qubit.it.debug;

import io.quarkiverse.qubit.it.Person;
import io.quarkiverse.qubit.it.testdata.TestDataFactory;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Minimal test to debug string concatenation.
 */
@QuarkusTest
class SimpleStringConcatTest {

    @BeforeEach
    @Transactional
    void setupTestData() {
        TestDataFactory.clearAllData();
        TestDataFactory.createStandardPersonsAndProducts();
    }

    @Test
    void simpleFieldOnly() {
        var names = Person.select((Person p) -> p.firstName).toList();

        assertThat(names)
                .hasSize(5)
                .contains("John", "Jane");
    }

    @Test
    void simpleConcatTwoFields() {
        var names = Person.select((Person p) -> p.firstName + p.lastName).toList();

        // If concat works: "JohnDoe", "JaneSmith", etc.
        // If not working: probably just "John", "Jane" or error
        assertThat(names).hasSize(5);
    }

    @Test
    void simpleConcatConstantAndField() {
        var names = Person.select((Person p) -> "Mr. " + p.firstName).toList();

        // Expected: "Mr. John", "Mr. Jane", etc.
        assertThat(names).hasSize(5);
    }

    @Test
    void simpleArithmeticInteger() {
        var ages = Person.select((Person p) -> p.age + 10).toList();

        // Expected: 40, 35, 55, 45, 38
        assertThat(ages)
                .hasSize(5)
                .contains(40, 35);
    }
}
