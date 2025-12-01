package io.quarkiverse.qubit.it.logical;

import io.quarkiverse.qubit.it.Person;
import io.quarkiverse.qubit.it.testdata.TestDataFactory;
import io.quarkiverse.qubit.it.Product;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;


import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for AND logical operations.
 */
@QuarkusTest
class AndOperationsTest {

    @BeforeEach
    @Transactional
    void setupTestData() {
        TestDataFactory.clearAllData();
        TestDataFactory.createStandardPersonsAndProducts();
    }

    @Test
    void twoConditionAnd() {
        var results = Person.where((Person p) -> p.age > 25 && p.active).toList();

        // age > 25 && active → John(30), Alice(35), Charlie(28)
        assertThat(results)
                .hasSize(3)
                .extracting(Person::getFirstName)
                .containsExactlyInAnyOrder("John", "Alice", "Charlie");
    }

    @Test
    void threeConditionAnd() {
        var results = Person.where((Person p) ->
                p.age >= 35 && p.active && p.salary != null
        ).toList();

        // age >= 35 && active && salary != null → Alice(35, active, $90K)
        assertThat(results)
                .hasSize(1)
                .extracting(Person::getFirstName)
                .containsExactly("Alice");
    }

    @Test
    void fourConditionAnd() {
        var results = Person.where((Person p) ->
                p.age >= 35 && p.active && p.salary != null && p.salary > 85000.0
        ).toList();

        // age >= 35 && active && salary > $85K → Alice($90K)
        assertThat(results)
                .hasSize(1)
                .extracting(Person::getFirstName)
                .containsExactly("Alice");
    }

    @Test
    void fiveConditionAnd() {
        var results = Person.where((Person p) ->
                p.age >= 30 && p.active && p.salary != null &&
                p.salary > 70000.0 && p.email.contains("@")
        ).toList();

        // age >= 30 && active && salary > $70K && email.contains("@") → John($75K), Alice($90K)
        assertThat(results)
                .hasSize(2)
                .extracting(Person::getFirstName)
                .containsExactlyInAnyOrder("John", "Alice");
    }

    @Test
    void longAndChain() {
        var results = Person.where((Person p) ->
                p.age >= 25 && p.age <= 45 && p.active && p.salary != null &&
                p.salary > 60000.0 && p.email.contains("@") &&
                p.height != null && p.height > 1.6f
        ).toList();

        // age 25-45 && active && salary > $60K && email has @ && height > 1.6m
        // → John(30, $75K, 1.75m), Jane(25, $65K, 1.68m), Alice(35, $90K, 1.65m)
        assertThat(results)
                .hasSize(3)
                .extracting(Person::getFirstName)
                .containsExactlyInAnyOrder("John", "Jane", "Alice");
    }

    @Test
    void multipleFieldsWithAnd() {
        var results = Person.where((Person p) ->
                p.firstName.startsWith("J") && p.age > 25 && p.active
        ).toList();

        // firstName starts with "J" && age > 25 && active → John(30, active)
        // (Jane is 25, not > 25)
        assertThat(results)
                .hasSize(1)
                .extracting(Person::getFirstName)
                .containsExactly("John");
    }

    @Test
    void andWithStringOperations() {
        var results = Person.where((Person p) ->
                p.email.contains("@example.com") && p.salary > 60000.0 && p.active
        ).toList();

        // email contains "@example.com" && salary > $60K && active
        // → John($75K), Jane($65K), Alice($90K)
        assertThat(results)
                .hasSize(3)
                .extracting(Person::getFirstName)
                .containsExactlyInAnyOrder("John", "Jane", "Alice");
    }

    @Test
    void andWithMultipleTypes() {
        var results = Person.where((Person p) ->
                p.age > 30 && p.email.contains("@")
        ).toList();

        // age > 30 && email contains "@" → Bob(45), Alice(35)
        assertThat(results)
                .hasSize(2)
                .extracting(Person::getFirstName)
                .containsExactlyInAnyOrder("Bob", "Alice");
    }

    @Test
    void andWithAllNumericTypes() {
        var results = Person.where((Person p) ->
                p.age > 26 &&
                p.salary != null && p.salary > 70000.0 &&
                p.height != null && p.height > 1.70f &&
                p.employeeId != null && p.employeeId > 1000002L
        ).toList();

        // age > 26 && salary > $70K && height > 1.70m && employeeId > 1000002
        // Only Bob(45, $85K, 1.82m, ID:1000003) matches all conditions
        assertThat(results)
                .hasSize(1)
                .extracting(Person::getFirstName)
                .containsExactly("Bob");
    }

    @Test
    void productAndOperation() {
        var results = Product.where((Product p) ->
                p.available && p.stockQuantity > 0
        ).toList();

        // available && stockQuantity > 0
        // → Laptop(50), Smartphone(100), Desk Chair(25), Monitor(30)
        // Coffee Maker is unavailable
        assertThat(results)
                .hasSize(4)
                .extracting(Product::getName)
                .containsExactlyInAnyOrder("Laptop", "Smartphone", "Desk Chair", "Monitor");
    }
}
