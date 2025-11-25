package io.quarkus.qusaq.it.repository.logical;

import io.quarkus.qusaq.it.Person;
import io.quarkus.qusaq.it.PersonRepository;
import io.quarkus.qusaq.it.testdata.TestDataFactory;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Repository pattern tests for complex nested logical expressions.
 * Mirrors io.quarkus.qusaq.it.logical.ComplexExpressionsTest using repository injection.
 */
@QuarkusTest
class RepositoryComplexExpressionsTest {

    @Inject
    PersonRepository repository;

    @BeforeEach
    @Transactional
    void setupTestData() {
        TestDataFactory.clearAllData();
        TestDataFactory.createStandardPersons();
    }

    @Test
    void nestedAndOrExpression() {
        var results = repository.where((Person p) ->
                (p.age > 25 && p.age < 35) || p.salary > 80000
        ).toList();

        assertThat(results)
                .hasSizeGreaterThan(0)
                .allMatch(p -> (p.getAge() > 25 && p.getAge() < 35) ||
                              p.getSalary() > 80000);
    }

    @Test
    void andWithNestedOr() {
        var results = repository.where((Person p) ->
                p.active && (p.age < 30 || p.salary > 80000)
        ).toList();

        assertThat(results)
                .hasSizeGreaterThan(0)
                .allMatch(p -> p.isActive() &&
                              (p.getAge() < 30 || p.getSalary() > 80000));
    }

    @Test
    void complexNestedOrAnd() {
        var results = repository.where((Person p) ->
                (p.age < 30 || p.age > 40) && (p.active || p.salary > 70000)
        ).toList();

        assertThat(results)
                .hasSizeGreaterThan(0)
                .allMatch(p -> (p.getAge() < 30 || p.getAge() > 40) &&
                              (p.isActive() || p.getSalary() > 70000));
    }

    @Test
    void tripleAndWithOr() {
        var results = repository.where((Person p) ->
                (p.age >= 25 && p.age <= 30 && p.active) || p.salary > 88000
        ).toList();

        assertThat(results)
                .hasSizeGreaterThan(0)
                .allMatch(p -> (p.getAge() >= 25 && p.getAge() <= 30 && p.isActive()) ||
                              p.getSalary() > 88000);
    }

    @Test
    void deeplyNestedMultipleOrGroups() {
        var results = repository.where((Person p) ->
                ((p.age > 25 && p.age < 40) || p.salary > 85000) &&
                (p.active || p.firstName.startsWith("B"))
        ).toList();

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
        var results = repository.where((Person p) ->
                (p.age + 10 > 40) || (p.age * 2 < 60)
        ).toList();

        assertThat(results)
                .hasSizeGreaterThan(0)
                .allMatch(p -> (p.getAge() + 10 > 40) || (p.getAge() * 2 < 60));
    }

    @Test
    void complexArithmeticInOr() {
        var results = repository.where((Person p) ->
                (p.age * 2 - 10 > 50) || (p.age + 15 < 50)
        ).toList();

        assertThat(results)
                .hasSizeGreaterThan(0)
                .allMatch(p -> (p.getAge() * 2 - 10 > 50) || (p.getAge() + 15 < 50));
    }

    @Test
    void complexNestedConditions() {
        var results = repository.where((Person p) ->
                (p.firstName.equals("John") || p.firstName.equals("Jane")) &&
                p.age >= 25 && p.active
        ).toList();

        assertThat(results).hasSizeGreaterThan(0);
    }

    @Test
    void complexLogicalExpression() {
        var results = repository.where((Person p) ->
                (p.age > 25 && p.age < 35) || p.salary > 80000
        ).toList();

        assertThat(results).hasSizeGreaterThan(0);
    }
}
