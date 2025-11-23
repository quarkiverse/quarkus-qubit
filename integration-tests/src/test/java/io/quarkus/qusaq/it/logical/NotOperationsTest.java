package io.quarkus.qusaq.it.logical;

import io.quarkus.qusaq.it.Person;
import io.quarkus.qusaq.it.testdata.TestDataFactory;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;


import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for NOT logical operations.
 */
@QuarkusTest
class NotOperationsTest {

    @BeforeEach
    @Transactional
    void setupTestData() {
        TestDataFactory.clearAllData();
        TestDataFactory.createStandardPersons();
    }

    @Test
    void simpleNot() {
        var results = Person.where((Person p) -> !p.active).toList();

        assertThat(results)
                .hasSizeGreaterThan(0)
                .noneMatch(Person::isActive);
    }

    @Test
    void notWithAnd() {
        var results = Person.where((Person p) -> !p.active && p.age > 40).toList();

        assertThat(results)
                .hasSizeGreaterThan(0)
                .allMatch(p -> !p.isActive() && p.getAge() > 40);
    }

    @Test
    void notWithAndSalary() {
        var results = Person.where((Person p) -> !p.active && p.salary > 80000.0).toList();

        assertThat(results)
                .hasSizeGreaterThan(0)
                .allMatch(p -> !p.isActive() && p.getSalary() > 80000.0);
    }

    @Test
    void notWithComplexOrAnd() {
        var results = Person.where((Person p) ->
                !(p.age < 28 || p.age > 42) && p.active
        ).toList();

        assertThat(results)
                .hasSizeGreaterThan(0)
                .allMatch(p -> !(p.getAge() < 28 || p.getAge() > 42) && p.isActive());
    }

    @Test
    void notWithComplexAnd() {
        var results = Person.where((Person p) -> !(p.age > 10 && p.salary < 5000)).toList();

        assertThat(results)
                .hasSizeGreaterThan(0)
                .allMatch(p -> !(p.getAge() > 10 && p.getSalary() < 5000));
    }

    @Test
    void doubleNegation() {
        var results = Person.where((Person p) -> !!p.active).toList();

        assertThat(results)
                .hasSizeGreaterThan(0)
                .allMatch(Person::isActive);
    }

    @Test
    void notWithOr() {
        var results = Person.where((Person p) -> !(p.active || p.salary > 90000)).toList();

        assertThat(results)
                .hasSizeGreaterThan(0)
                .allMatch(p -> !(p.isActive() || p.getSalary() > 90000));
    }
}
