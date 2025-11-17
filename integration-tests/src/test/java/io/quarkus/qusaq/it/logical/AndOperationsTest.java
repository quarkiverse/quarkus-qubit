package io.quarkus.qusaq.it.logical;

import io.quarkus.qusaq.it.Person;
import io.quarkus.qusaq.it.testdata.TestDataFactory;
import io.quarkus.qusaq.it.Product;
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
        var results = Person.findWhere((Person p) -> p.age > 25 && p.active);

        assertThat(results)
                .hasSizeGreaterThan(0)
                .allMatch(p -> p.getAge() > 25 && p.isActive());
    }

    @Test
    void threeConditionAnd() {
        var results = Person.findWhere((Person p) ->
                p.age >= 35 && p.active && p.salary != null
        );

        assertThat(results)
                .hasSizeGreaterThan(0)
                .allMatch(p -> p.getAge() >= 35 && p.isActive() && p.getSalary() != null);
    }

    @Test
    void fourConditionAnd() {
        var results = Person.findWhere((Person p) ->
                p.age >= 35 && p.active && p.salary != null && p.salary > 85000.0
        );

        assertThat(results)
                .hasSizeGreaterThan(0)
                .allMatch(p -> p.getAge() >= 35 && p.isActive() &&
                              p.getSalary() != null && p.getSalary() > 85000.0);
    }

    @Test
    void fiveConditionAnd() {
        var results = Person.findWhere((Person p) ->
                p.age >= 30 && p.active && p.salary != null &&
                p.salary > 70000.0 && p.email.contains("@")
        );

        assertThat(results)
                .hasSizeGreaterThan(0)
                .allMatch(p -> p.getAge() >= 30 && p.isActive() &&
                              p.getSalary() != null && p.getSalary() > 70000.0 &&
                              p.getEmail().contains("@"));
    }

    @Test
    void longAndChain() {
        var results = Person.findWhere((Person p) ->
                p.age >= 25 && p.age <= 45 && p.active && p.salary != null &&
                p.salary > 60000.0 && p.email.contains("@") &&
                p.height != null && p.height > 1.6f
        );

        assertThat(results)
                .hasSizeGreaterThan(0)
                .allMatch(p -> p.getAge() >= 25 && p.getAge() <= 45 &&
                              p.isActive() && p.getSalary() != null &&
                              p.getSalary() > 60000.0 && p.getEmail().contains("@") &&
                              p.getHeight() != null && p.getHeight() > 1.6f);
    }

    @Test
    void multipleFieldsWithAnd() {
        var results = Person.findWhere((Person p) ->
                p.firstName.startsWith("J") && p.age > 25 && p.active
        );

        assertThat(results).hasSizeGreaterThan(0);
    }

    @Test
    void andWithStringOperations() {
        var results = Person.findWhere((Person p) ->
                p.email.contains("@example.com") && p.salary > 60000.0 && p.active
        );

        assertThat(results)
                .hasSizeGreaterThan(0)
                .allMatch(p -> p.getEmail().contains("@example.com") &&
                              p.getSalary() > 60000.0 && p.isActive());
    }

    @Test
    void andWithMultipleTypes() {
        var results = Person.findWhere((Person p) ->
                p.age > 30 && p.email.contains("@")
        );

        assertThat(results)
                .hasSizeGreaterThan(0)
                .allMatch(p -> p.getAge() > 30 && p.getEmail().contains("@"));
    }

    @Test
    void andWithAllNumericTypes() {
        var results = Person.findWhere((Person p) ->
                p.age > 26 &&
                p.salary != null && p.salary > 70000.0 &&
                p.height != null && p.height > 1.70f &&
                p.employeeId != null && p.employeeId > 1000002L
        );

        assertThat(results)
                .hasSizeGreaterThan(0)
                .allMatch(p -> p.getAge() > 26 &&
                              p.getSalary() != null && p.getSalary() > 70000.0 &&
                              p.getHeight() != null && p.getHeight() > 1.70f &&
                              p.getEmployeeId() != null && p.getEmployeeId() > 1000002L);
    }

    @Test
    void productAndOperation() {
        var results = Product.findWhere((Product p) ->
                p.available && p.stockQuantity > 0
        );

        assertThat(results)
                .hasSizeGreaterThan(0)
                .allMatch(p -> p.isAvailable() && p.getStockQuantity() > 0);
    }
}
