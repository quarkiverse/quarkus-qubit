package io.quarkus.qusaq.it.logical;

import io.quarkus.qusaq.it.Person;
import io.quarkus.qusaq.it.testdata.TestDataFactory;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;


import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for complex nested logical expressions.
 */
@QuarkusTest
class ComplexExpressionsTest {

    @BeforeEach
    @Transactional
    void setupTestData() {
        TestDataFactory.clearAllData();
        TestDataFactory.createStandardPersons();
    }

    @Test
    void nestedAndOrExpression() {
        var results = Person.findWhere((Person p) ->
                (p.age > 25 && p.age < 35) || p.salary > 80000
        );

        assertThat(results)
                .hasSizeGreaterThan(0)
                .allMatch(p -> (p.getAge() > 25 && p.getAge() < 35) ||
                              p.getSalary() > 80000);
    }

    @Test
    void andWithNestedOr() {
        var results = Person.findWhere((Person p) ->
                p.active && (p.age < 30 || p.salary > 80000)
        );

        assertThat(results)
                .hasSizeGreaterThan(0)
                .allMatch(p -> p.isActive() &&
                              (p.getAge() < 30 || p.getSalary() > 80000));
    }

    @Test
    void complexNestedOrAnd() {
        var results = Person.findWhere((Person p) ->
                (p.age < 30 || p.age > 40) && (p.active || p.salary > 70000)
        );

        assertThat(results)
                .hasSizeGreaterThan(0)
                .allMatch(p -> (p.getAge() < 30 || p.getAge() > 40) &&
                              (p.isActive() || p.getSalary() > 70000));
    }

    @Test
    void tripleAndWithOr() {
        var results = Person.findWhere((Person p) ->
                (p.age >= 25 && p.age <= 30 && p.active) || p.salary > 88000
        );

        assertThat(results)
                .hasSizeGreaterThan(0)
                .allMatch(p -> (p.getAge() >= 25 && p.getAge() <= 30 && p.isActive()) ||
                              p.getSalary() > 88000);
    }

    @Test
    void deeplyNestedMultipleOrGroups() {
        var results = Person.findWhere((Person p) ->
                ((p.age > 25 && p.age < 40) || p.salary > 85000) &&
                (p.active || p.firstName.startsWith("B"))
        );

        assertThat(results)
                .hasSizeGreaterThan(0)
                .allMatch(p -> {
                    boolean leftSide = (p.getAge() > 25 && p.getAge() < 40) || p.getSalary() > 85000;
                    boolean rightSide = p.isActive() || p.getFirstName().startsWith("B");
                    return leftSide && rightSide;
                });
    }

    @Test
    void arithmeticInOrGroups() {
        var results = Person.findWhere((Person p) ->
                (p.age + 10 > 40) || (p.age * 2 < 60)
        );

        assertThat(results)
                .hasSizeGreaterThan(0)
                .allMatch(p -> (p.getAge() + 10 > 40) || (p.getAge() * 2 < 60));
    }

    @Test
    void complexArithmeticInOr() {
        var results = Person.findWhere((Person p) ->
                (p.age * 2 - 10 > 50) || (p.age + 15 < 50)
        );

        assertThat(results)
                .hasSizeGreaterThan(0)
                .allMatch(p -> (p.getAge() * 2 - 10 > 50) || (p.getAge() + 15 < 50));
    }

    @Test
    void complexNestedConditions() {
        var results = Person.findWhere((Person p) ->
                (p.firstName.equals("John") || p.firstName.equals("Jane")) &&
                p.age >= 25 && p.active
        );

        assertThat(results).hasSizeGreaterThan(0);
    }

    @Test
    void complexLogicalExpression() {
        var results = Person.findWhere((Person p) ->
                (p.age > 25 && p.age < 35) || p.salary > 80000
        );

        assertThat(results).hasSizeGreaterThan(0);
    }
}
