package io.quarkus.qusaq.it.basic;

import io.quarkus.qusaq.it.Person;
import io.quarkus.qusaq.it.testdata.TestDataFactory;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for null safety checks on various field types.
 */
@QuarkusTest
class NullCheckTest {

    @BeforeEach
    @Transactional
    void setupTestData() {
        TestDataFactory.clearAllData();
        TestDataFactory.createPersonsForNullChecks();
    }

    @Test
    @Transactional
    void stringNullCheck() {
        new Person("Test", "User", null, 20,
                LocalDate.of(2003, 1, 1), true, 40000.0, null, null, null, null).persist();

        var results = Person.findWhere((Person p) -> p.email == null);

        assertThat(results)
                .hasSizeGreaterThan(0)
                .allMatch(p -> p.getEmail() == null);
    }

    @Test
    void stringNotNullCheck() {
        var results = Person.findWhere((Person p) -> p.email != null);

        assertThat(results)
                .hasSizeGreaterThan(0)
                .allMatch(p -> p.getEmail() != null);
    }

    @Test
    void doubleNullCheck() {
        var results = Person.findWhere((Person p) -> p.salary == null);

        assertThat(results)
                .hasSize(1)
                .allMatch(p -> p.getSalary() == null);
    }

    @Test
    void longNullCheck() {
        var results = Person.findWhere((Person p) -> p.employeeId == null);

        assertThat(results)
                .hasSize(1)
                .allMatch(p -> p.getEmployeeId() == null);
    }

    @Test
    void floatNullCheck() {
        var results = Person.findWhere((Person p) -> p.height == null);

        assertThat(results)
                .hasSize(1)
                .allMatch(p -> p.getHeight() == null);
    }

    @Test
    void localDateNullCheck() {
        var results = Person.findWhere((Person p) -> p.birthDate == null);

        assertThat(results)
                .hasSize(1)
                .allMatch(p -> p.getBirthDate() == null);
    }

    @Test
    void localDateTimeNullCheck() {
        var results = Person.findWhere((Person p) -> p.createdAt == null);

        assertThat(results)
                .hasSize(1)
                .allMatch(p -> p.getCreatedAt() == null);
    }

    @Test
    void localTimeNullCheck() {
        var results = Person.findWhere((Person p) -> p.startTime == null);

        assertThat(results)
                .hasSize(1)
                .allMatch(p -> p.getStartTime() == null);
    }
}
