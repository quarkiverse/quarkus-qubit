package io.quarkiverse.qubit.it.logical;

import io.quarkiverse.qubit.it.Person;
import io.quarkiverse.qubit.it.Product;
import io.quarkiverse.qubit.it.testdata.TestDataFactory;
import io.quarkiverse.qubit.it.testutil.PersonQueryOperations;
import io.quarkiverse.qubit.it.testutil.ProductQueryOperations;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Abstract base class for AND logical operation tests.
 *
 * <p>
 * Contains all test methods that can be run with either static entity methods
 * or repository instance methods.
 */
public abstract class AbstractAndOperationsTest {

    protected abstract PersonQueryOperations personOps();

    protected abstract ProductQueryOperations productOps();

    @BeforeEach
    @Transactional
    protected void setupTestData() {
        TestDataFactory.clearAllData();
        TestDataFactory.createStandardPersonsAndProducts();
    }

    @Test
    void twoConditionAnd() {
        var results = personOps().where((Person p) -> p.age > 25 && p.active).toList();

        assertThat(results)
                .hasSize(3)
                .extracting(Person::getFirstName)
                .containsExactlyInAnyOrder("John", "Alice", "Charlie");
    }

    @Test
    void threeConditionAnd() {
        var results = personOps().where((Person p) -> p.age >= 35 && p.active && p.salary != null).toList();

        assertThat(results)
                .hasSize(1)
                .extracting(Person::getFirstName)
                .containsExactly("Alice");
    }

    @Test
    void fourConditionAnd() {
        var results = personOps().where((Person p) -> p.age >= 35 && p.active && p.salary != null && p.salary > 85000.0)
                .toList();

        assertThat(results)
                .hasSize(1)
                .extracting(Person::getFirstName)
                .containsExactly("Alice");
    }

    @Test
    void fiveConditionAnd() {
        var results = personOps().where((Person p) -> p.age >= 30 && p.active && p.salary != null &&
                p.salary > 70000.0 && p.email.contains("@")).toList();

        assertThat(results)
                .hasSize(2)
                .extracting(Person::getFirstName)
                .containsExactlyInAnyOrder("John", "Alice");
    }

    @Test
    void longAndChain() {
        var results = personOps().where((Person p) -> p.age >= 25 && p.age <= 45 && p.active && p.salary != null &&
                p.salary > 60000.0 && p.email.contains("@") &&
                p.height != null && p.height > 1.6f).toList();

        assertThat(results)
                .hasSize(3)
                .extracting(Person::getFirstName)
                .containsExactlyInAnyOrder("John", "Jane", "Alice");
    }

    @Test
    void multipleFieldsWithAnd() {
        var results = personOps().where((Person p) -> p.firstName.startsWith("J") && p.age > 25 && p.active).toList();

        assertThat(results)
                .hasSize(1)
                .extracting(Person::getFirstName)
                .containsExactly("John");
    }

    @Test
    void andWithStringOperations() {
        var results = personOps().where((Person p) -> p.email.contains("@example.com") && p.salary > 60000.0 && p.active)
                .toList();

        assertThat(results)
                .hasSize(3)
                .extracting(Person::getFirstName)
                .containsExactlyInAnyOrder("John", "Jane", "Alice");
    }

    @Test
    void andWithMultipleTypes() {
        var results = personOps().where((Person p) -> p.age > 30 && p.email.contains("@")).toList();

        assertThat(results)
                .hasSize(2)
                .extracting(Person::getFirstName)
                .containsExactlyInAnyOrder("Bob", "Alice");
    }

    @Test
    void andWithAllNumericTypes() {
        var results = personOps().where((Person p) -> p.age > 26 &&
                p.salary != null && p.salary > 70000.0 &&
                p.height != null && p.height > 1.70f &&
                p.employeeId != null && p.employeeId > 1000002L).toList();

        assertThat(results)
                .hasSize(1)
                .extracting(Person::getFirstName)
                .containsExactly("Bob");
    }

    @Test
    void productAndOperation() {
        var results = productOps().where((Product p) -> p.available && p.stockQuantity > 0).toList();

        assertThat(results)
                .hasSize(4)
                .extracting(Product::getName)
                .containsExactlyInAnyOrder("Laptop", "Smartphone", "Desk Chair", "Monitor");
    }
}
