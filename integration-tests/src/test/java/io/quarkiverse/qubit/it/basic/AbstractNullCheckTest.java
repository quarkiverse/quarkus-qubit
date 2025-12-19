package io.quarkiverse.qubit.it.basic;

import io.quarkiverse.qubit.it.Person;
import io.quarkiverse.qubit.it.testdata.TestDataFactory;
import io.quarkiverse.qubit.it.testutil.PersonQueryOperations;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Abstract base class for null safety check tests on various field types.
 */
public abstract class AbstractNullCheckTest {

    protected abstract PersonQueryOperations personOps();

    @BeforeEach
    @Transactional
    protected void setupTestData() {
        TestDataFactory.clearAllData();
        TestDataFactory.createPersonsForNullChecks();
    }

    @Test
    @Transactional
    protected void stringNullCheck() {
        new Person("Test", "User", null, 20,
                LocalDate.of(2003, 1, 1), true, 40000.0, null, null, null, null).persist();

        var results = personOps().where((Person p) -> p.email == null).toList();

        assertThat(results)
                .hasSizeGreaterThan(0)
                .allMatch(p -> p.getEmail() == null);
    }

    @Test
    void stringNotNullCheck() {
        var results = personOps().where((Person p) -> p.email != null).toList();

        assertThat(results)
                .hasSizeGreaterThan(0)
                .allMatch(p -> p.getEmail() != null);
    }

    @Test
    void doubleNullCheck() {
        var results = personOps().where((Person p) -> p.salary == null).toList();

        assertThat(results)
                .hasSize(1)
                .allMatch(p -> p.getSalary() == null);
    }

    @Test
    void longNullCheck() {
        var results = personOps().where((Person p) -> p.employeeId == null).toList();

        assertThat(results)
                .hasSize(1)
                .allMatch(p -> p.getEmployeeId() == null);
    }

    @Test
    void floatNullCheck() {
        var results = personOps().where((Person p) -> p.height == null).toList();

        assertThat(results)
                .hasSize(1)
                .allMatch(p -> p.getHeight() == null);
    }

    @Test
    void localDateNullCheck() {
        var results = personOps().where((Person p) -> p.birthDate == null).toList();

        assertThat(results)
                .hasSize(1)
                .allMatch(p -> p.getBirthDate() == null);
    }

    @Test
    void localDateTimeNullCheck() {
        var results = personOps().where((Person p) -> p.createdAt == null).toList();

        assertThat(results)
                .hasSize(1)
                .allMatch(p -> p.getCreatedAt() == null);
    }

    @Test
    void localTimeNullCheck() {
        var results = personOps().where((Person p) -> p.startTime == null).toList();

        assertThat(results)
                .hasSize(1)
                .allMatch(p -> p.getStartTime() == null);
    }

    @Test
    void nullCheckWithAnd() {
        var results = personOps().where((Person p) -> p.email != null && p.firstName != null).toList();

        assertThat(results)
                .hasSizeGreaterThan(0)
                .allMatch(p -> p.getEmail() != null && p.getFirstName() != null);
    }

    @Test
    void nullCheckWithCondition() {
        var results = personOps().where((Person p) -> p.email != null && p.age > 30).toList();

        assertThat(results)
                .hasSizeGreaterThan(0)
                .allMatch(p -> p.getEmail() != null && p.getAge() > 30);
    }

    @Test
    @Transactional
    protected void nullCheckWithOr() {
        // Create one person with null email (firstName NOT null)
        Person withNullEmail = new Person();
        withNullEmail.firstName = "TestFirst";
        withNullEmail.lastName = "TestLast";
        withNullEmail.email = null;
        withNullEmail.age = 99;
        withNullEmail.persist();

        var results = personOps().where((Person p) -> p.email == null || p.firstName == null).toList();

        // Expected: Only the person we just created (plus any from test data with empty string email)
        // Eve has email="" (empty string, not null) so should NOT match
        // Our test person has email=null so SHOULD match
        assertThat(results)
                .hasSizeGreaterThan(0)
                .anyMatch(p -> "TestFirst".equals(p.getFirstName()));
    }
}
