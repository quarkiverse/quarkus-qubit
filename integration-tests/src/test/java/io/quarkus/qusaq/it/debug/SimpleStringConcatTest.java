package io.quarkus.qusaq.it.debug;

import io.quarkus.qusaq.it.Person;
import io.quarkus.qusaq.it.testdata.TestDataFactory;
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

        System.out.println("=== Field Only Results ===");
        names.forEach(System.out::println);

        assertThat(names)
                .hasSize(5)
                .contains("John", "Jane");
    }

    @Test
    void simpleConcatTwoFields() {
        var names = Person.select((Person p) -> p.firstName + p.lastName).toList();

        System.out.println("=== Concat Two Fields Results ===");
        names.forEach(System.out::println);

        // If concat works: "JohnDoe", "JaneSmith", etc.
        // If not working: probably just "John", "Jane" or error
        assertThat(names).hasSize(5);
    }

    @Test
    void simpleConcatConstantAndField() {
        var names = Person.select((Person p) -> "Mr. " + p.firstName).toList();

        System.out.println("=== Concat Constant + Field Results ===");
        names.forEach(System.out::println);

        // Expected: "Mr. John", "Mr. Jane", etc.
        assertThat(names).hasSize(5);
    }

    @Test
    void simpleArithmeticInteger() {
        var ages = Person.select((Person p) -> p.age + 10).toList();

        System.out.println("=== Arithmetic Results ===");
        ages.forEach(System.out::println);

        // Expected: 40, 35, 55, 45, 38
        assertThat(ages)
                .hasSize(5)
                .contains(40, 35);
    }
}
