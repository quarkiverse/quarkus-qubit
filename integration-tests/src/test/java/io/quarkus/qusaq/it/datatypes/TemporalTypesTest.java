package io.quarkus.qusaq.it.datatypes;

import io.quarkus.qusaq.it.Person;
import io.quarkus.qusaq.it.testdata.TestDataFactory;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for temporal types (LocalDate, LocalDateTime, LocalTime) operations.
 */
@QuarkusTest
class TemporalTypesTest {

    @BeforeEach
    @Transactional
    void setupTestData() {
        TestDataFactory.clearAllData();
        TestDataFactory.createStandardPersons();
    }

    // LocalDate tests
    @Test
    void localDateGetYear() {
        var results = Person.findWhere((Person p) -> p.birthDate.getYear() == 1993);

        assertThat(results)
                .hasSizeGreaterThan(0)
                .allMatch(p -> p.getBirthDate().getYear() == 1993);
    }

    @Test
    void localDateGetMonth() {
        var results = Person.findWhere((Person p) -> p.birthDate.getMonthValue() == 5);

        assertThat(results)
                .hasSizeGreaterThan(0)
                .allMatch(p -> p.getBirthDate().getMonthValue() == 5);
    }

    @Test
    void localDateGetDayOfMonth() {
        var results = Person.findWhere((Person p) -> p.birthDate.getDayOfMonth() == 15);

        assertThat(results)
                .hasSizeGreaterThan(0)
                .allMatch(p -> p.getBirthDate().getDayOfMonth() == 15);
    }

    // LocalDateTime tests
    @Test
    void localDateTimeGetYear() {
        var results = Person.findWhere((Person p) -> p.createdAt.getYear() == 2024);

        assertThat(results)
                .hasSize(5)
                .allMatch(p -> p.getCreatedAt().getYear() == 2024);
    }

    @Test
    void localDateTimeGetMonth() {
        var results = Person.findWhere((Person p) -> p.createdAt.getMonthValue() == 1);

        assertThat(results)
                .hasSize(1)
                .allMatch(p -> p.getCreatedAt().getMonthValue() == 1);
    }

    @Test
    void localDateTimeGetDayOfMonth() {
        var results = Person.findWhere((Person p) -> p.createdAt.getDayOfMonth() == 15);

        assertThat(results)
                .hasSize(1)
                .allMatch(p -> p.getCreatedAt().getDayOfMonth() == 15);
    }

    @Test
    void localDateTimeGetHour() {
        var results = Person.findWhere((Person p) -> p.createdAt.getHour() == 8);

        assertThat(results)
                .hasSize(1)
                .allMatch(p -> p.getCreatedAt().getHour() == 8);
    }

    @Test
    void localDateTimeGetMinute() {
        var results = Person.findWhere((Person p) -> p.createdAt.getMinute() == 30);

        assertThat(results)
                .hasSize(1)
                .allMatch(p -> p.getCreatedAt().getMinute() == 30);
    }

    @Test
    void localDateTimeGetSecond() {
        var results = Person.findWhere((Person p) -> p.createdAt.getSecond() == 0);

        assertThat(results)
                .hasSize(5)
                .allMatch(p -> p.getCreatedAt().getSecond() == 0);
    }

    // LocalTime tests
    @Test
    void localTimeGetHour() {
        var results = Person.findWhere((Person p) -> p.startTime.getHour() == 8);

        assertThat(results)
                .hasSize(2)
                .allMatch(p -> p.getStartTime().getHour() == 8);
    }

    @Test
    void localTimeGetMinute() {
        var results = Person.findWhere((Person p) -> p.startTime.getMinute() == 0);

        assertThat(results)
                .hasSize(3)
                .allMatch(p -> p.getStartTime().getMinute() == 0);
    }

    @Test
    void localTimeGetSecond() {
        var results = Person.findWhere((Person p) -> p.startTime.getSecond() == 0);

        assertThat(results)
                .hasSize(5)
                .allMatch(p -> p.getStartTime().getSecond() == 0);
    }

    // Mixed temporal types
    @Test
    void mixedTemporalTypes() {
        var results = Person.findWhere((Person p) ->
                p.birthDate.isAfter(LocalDate.of(1990, 1, 1)) &&
                p.createdAt.isBefore(LocalDateTime.of(2024, 4, 1, 0, 0)) &&
                p.startTime.isAfter(LocalTime.of(8, 0))
        );

        assertThat(results)
                .hasSizeGreaterThan(0)
                .allMatch(p -> p.getBirthDate().isAfter(LocalDate.of(1990, 1, 1)) &&
                              p.getCreatedAt().isBefore(LocalDateTime.of(2024, 4, 1, 0, 0)) &&
                              p.getStartTime().isAfter(LocalTime.of(8, 0)));
    }

    @Test
    void localDateTimeWithComplexConditions() {
        var results = Person.findWhere((Person p) ->
                p.createdAt != null &&
                p.createdAt.isAfter(LocalDateTime.of(2024, 2, 1, 0, 0)) &&
                p.age < 40
        );

        assertThat(results)
                .hasSizeGreaterThan(0)
                .allMatch(p -> p.getCreatedAt() != null &&
                              p.getCreatedAt().isAfter(LocalDateTime.of(2024, 2, 1, 0, 0)) &&
                              p.getAge() < 40);
    }

    @Test
    void localTimeWithOrConditions() {
        var results = Person.findWhere((Person p) ->
                p.startTime.isBefore(LocalTime.of(9, 0)) ||
                p.startTime.isAfter(LocalTime.of(9, 0))
        );

        assertThat(results)
                .hasSizeGreaterThan(0)
                .allMatch(p -> p.getStartTime().isBefore(LocalTime.of(9, 0)) ||
                              p.getStartTime().isAfter(LocalTime.of(9, 0)));
    }
}
