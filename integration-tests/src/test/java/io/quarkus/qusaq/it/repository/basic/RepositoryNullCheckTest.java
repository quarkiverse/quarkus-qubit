package io.quarkus.qusaq.it.repository.basic;

import io.quarkus.qusaq.it.Person;
import io.quarkus.qusaq.it.PersonRepository;
import io.quarkus.qusaq.it.testdata.TestDataFactory;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Repository pattern tests for null safety checks on various field types.
 * Mirrors io.quarkus.qusaq.it.basic.NullCheckTest using repository injection.
 */
@QuarkusTest
class RepositoryNullCheckTest {

    @Inject
    PersonRepository repository;

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

        var results = repository.where((Person p) -> p.email == null).toList();

        assertThat(results)
                .hasSizeGreaterThan(0)
                .allMatch(p -> p.getEmail() == null);
    }

    @Test
    void stringNotNullCheck() {
        var results = repository.where((Person p) -> p.email != null).toList();

        assertThat(results)
                .hasSizeGreaterThan(0)
                .allMatch(p -> p.getEmail() != null);
    }

    @Test
    void doubleNullCheck() {
        var results = repository.where((Person p) -> p.salary == null).toList();

        assertThat(results)
                .hasSize(1)
                .allMatch(p -> p.getSalary() == null);
    }

    @Test
    void longNullCheck() {
        var results = repository.where((Person p) -> p.employeeId == null).toList();

        assertThat(results)
                .hasSize(1)
                .allMatch(p -> p.getEmployeeId() == null);
    }

    @Test
    void floatNullCheck() {
        var results = repository.where((Person p) -> p.height == null).toList();

        assertThat(results)
                .hasSize(1)
                .allMatch(p -> p.getHeight() == null);
    }

    @Test
    void localDateNullCheck() {
        var results = repository.where((Person p) -> p.birthDate == null).toList();

        assertThat(results)
                .hasSize(1)
                .allMatch(p -> p.getBirthDate() == null);
    }

    @Test
    void localDateTimeNullCheck() {
        var results = repository.where((Person p) -> p.createdAt == null).toList();

        assertThat(results)
                .hasSize(1)
                .allMatch(p -> p.getCreatedAt() == null);
    }

    @Test
    void localTimeNullCheck() {
        var results = repository.where((Person p) -> p.startTime == null).toList();

        assertThat(results)
                .hasSize(1)
                .allMatch(p -> p.getStartTime() == null);
    }

    @Test
    void nullCheckWithAnd() {
        var results = repository.where((Person p) -> p.email != null && p.firstName != null).toList();

        assertThat(results)
                .hasSizeGreaterThan(0)
                .allMatch(p -> p.getEmail() != null && p.getFirstName() != null);
    }

    @Test
    void nullCheckWithCondition() {
        var results = repository.where((Person p) -> p.email != null && p.age > 30).toList();

        assertThat(results)
                .hasSizeGreaterThan(0)
                .allMatch(p -> p.getEmail() != null && p.getAge() > 30);
    }

    @Test
    @Transactional
    void nullCheckWithOr() {
        // Test the EXACT same lambda as in LambdaTestSources.nullCheckWithOr:
        // p -> p.email == null || p.firstName == null

        // Create one person with null email (firstName NOT null)
        Person withNullEmail = new Person();
        withNullEmail.firstName = "TestFirst";
        withNullEmail.lastName = "TestLast";
        withNullEmail.email = null;
        withNullEmail.age = 99;
        withNullEmail.persist();

        var results = repository.where((Person p) -> p.email == null || p.firstName == null).toList();

        // Expected: Only the person we just created (plus any from test data with empty string email)
        // Eve has email="" (empty string, not null) so should NOT match
        // Our test person has email=null so SHOULD match
        assertThat(results)
                .hasSizeGreaterThan(0)
                .anyMatch(p -> "TestFirst".equals(p.getFirstName()));
    }
}
