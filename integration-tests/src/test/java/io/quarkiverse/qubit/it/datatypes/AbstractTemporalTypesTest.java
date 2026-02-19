package io.quarkiverse.qubit.it.datatypes;

import io.quarkiverse.qubit.Qubit;
import io.quarkiverse.qubit.it.Person;
import io.quarkiverse.qubit.it.testdata.TestDataFactory;
import io.quarkiverse.qubit.it.testutil.PersonQueryOperations;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.IsoFields;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Abstract base class for temporal types operation tests.
 *
 * <p>
 * Contains all test methods that can be run with either static entity methods
 * or repository instance methods.
 */
public abstract class AbstractTemporalTypesTest {

    protected abstract PersonQueryOperations personOps();

    @BeforeEach
    @Transactional
    protected void setupTestData() {
        TestDataFactory.clearAllData();
        TestDataFactory.createStandardPersons();
    }

    // LocalDate tests
    @Test
    void localDateGetYear() {
        var results = personOps().where((Person p) -> p.birthDate.getYear() == 1993).toList();

        assertThat(results)
                .hasSizeGreaterThan(0)
                .allMatch(p -> p.getBirthDate().getYear() == 1993);
    }

    @Test
    void localDateGetMonth() {
        var results = personOps().where((Person p) -> p.birthDate.getMonthValue() == 5).toList();

        assertThat(results)
                .hasSizeGreaterThan(0)
                .allMatch(p -> p.getBirthDate().getMonthValue() == 5);
    }

    @Test
    void localDateGetDayOfMonth() {
        var results = personOps().where((Person p) -> p.birthDate.getDayOfMonth() == 15).toList();

        assertThat(results)
                .hasSizeGreaterThan(0)
                .allMatch(p -> p.getBirthDate().getDayOfMonth() == 15);
    }

    // LocalDateTime tests
    @Test
    void localDateTimeGetYear() {
        var results = personOps().where((Person p) -> p.createdAt.getYear() == 2024).toList();

        assertThat(results)
                .hasSize(5)
                .allMatch(p -> p.getCreatedAt().getYear() == 2024);
    }

    @Test
    void localDateTimeGetMonth() {
        var results = personOps().where((Person p) -> p.createdAt.getMonthValue() == 1).toList();

        assertThat(results)
                .hasSize(1)
                .allMatch(p -> p.getCreatedAt().getMonthValue() == 1);
    }

    @Test
    void localDateTimeGetDayOfMonth() {
        var results = personOps().where((Person p) -> p.createdAt.getDayOfMonth() == 15).toList();

        assertThat(results)
                .hasSize(1)
                .allMatch(p -> p.getCreatedAt().getDayOfMonth() == 15);
    }

    @Test
    void localDateTimeGetHour() {
        var results = personOps().where((Person p) -> p.createdAt.getHour() == 8).toList();

        assertThat(results)
                .hasSize(1)
                .allMatch(p -> p.getCreatedAt().getHour() == 8);
    }

    @Test
    void localDateTimeGetMinute() {
        var results = personOps().where((Person p) -> p.createdAt.getMinute() == 30).toList();

        assertThat(results)
                .hasSize(1)
                .allMatch(p -> p.getCreatedAt().getMinute() == 30);
    }

    @Test
    void localDateTimeGetSecond() {
        var results = personOps().where((Person p) -> p.createdAt.getSecond() == 0).toList();

        assertThat(results)
                .hasSize(5)
                .allMatch(p -> p.getCreatedAt().getSecond() == 0);
    }

    // LocalTime tests
    @Test
    void localTimeGetHour() {
        var results = personOps().where((Person p) -> p.startTime.getHour() == 8).toList();

        assertThat(results)
                .hasSize(2)
                .allMatch(p -> p.getStartTime().getHour() == 8);
    }

    @Test
    void localTimeGetMinute() {
        var results = personOps().where((Person p) -> p.startTime.getMinute() == 0).toList();

        assertThat(results)
                .hasSize(3)
                .allMatch(p -> p.getStartTime().getMinute() == 0);
    }

    @Test
    void localTimeGetSecond() {
        var results = personOps().where((Person p) -> p.startTime.getSecond() == 0).toList();

        assertThat(results)
                .hasSize(5)
                .allMatch(p -> p.getStartTime().getSecond() == 0);
    }

    // Qubit.quarter() tests — JPA 3.2 EXTRACT(QUARTER FROM ...)

    @Test
    void localDateQuarter() {
        // John: 1993-05-15 → Q2, Jane: 1998-08-22 → Q3, Bob: 1978-03-10 → Q1,
        // Alice: 1988-11-05 → Q4, Charlie: 1995-07-18 → Q3
        var results = personOps().where((Person p) -> Qubit.quarter(p.birthDate) == 2).toList();

        assertThat(results)
                .hasSizeGreaterThan(0)
                .allMatch(p -> {
                    int quarter = (p.getBirthDate().getMonthValue() - 1) / 3 + 1;
                    return quarter == 2;
                });
    }

    @Test
    void localDateTimeQuarter() {
        // createdAt: Jan=Q1, Feb=Q1, Mar=Q1, Apr=Q2, May=Q2
        var results = personOps().where((Person p) -> Qubit.quarter(p.createdAt) == 1).toList();

        assertThat(results)
                .hasSizeGreaterThan(0)
                .allMatch(p -> {
                    int quarter = (p.getCreatedAt().getMonthValue() - 1) / 3 + 1;
                    return quarter == 1;
                });
    }

    // Qubit.week() tests — JPA 3.2 EXTRACT(WEEK FROM ...)

    @Test
    void localDateWeek() {
        // John: 1993-05-15 → ISO week 19
        int expectedWeek = LocalDate.of(1993, 5, 15).get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
        var results = personOps().where((Person p) -> Qubit.week(p.birthDate) == expectedWeek).toList();

        assertThat(results)
                .hasSizeGreaterThan(0)
                .allMatch(p -> p.getBirthDate().get(IsoFields.WEEK_OF_WEEK_BASED_YEAR) == expectedWeek);
    }

    @Test
    void localDateTimeWeek() {
        // createdAt 2024-01-15 → ISO week 3
        int expectedWeek = LocalDate.of(2024, 1, 15).get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
        var results = personOps().where((Person p) -> Qubit.week(p.createdAt) == expectedWeek).toList();

        assertThat(results)
                .hasSizeGreaterThan(0)
                .allMatch(p -> p.getCreatedAt().toLocalDate().get(IsoFields.WEEK_OF_WEEK_BASED_YEAR) == expectedWeek);
    }

    // Quarter combined with other conditions
    @Test
    void quarterWithAdditionalConditions() {
        var results = personOps().where((Person p) -> Qubit.quarter(p.birthDate) >= 3 && p.age < 30).toList();

        assertThat(results)
                .hasSizeGreaterThan(0)
                .allMatch(p -> {
                    int quarter = (p.getBirthDate().getMonthValue() - 1) / 3 + 1;
                    return quarter >= 3 && p.getAge() < 30;
                });
    }

    // Mixed temporal types
    @Test
    void mixedTemporalTypes() {
        var results = personOps().where((Person p) -> p.birthDate.isAfter(LocalDate.of(1990, 1, 1)) &&
                p.createdAt.isBefore(LocalDateTime.of(2024, 4, 1, 0, 0)) &&
                p.startTime.isAfter(LocalTime.of(8, 0))).toList();

        assertThat(results)
                .hasSizeGreaterThan(0)
                .allMatch(p -> p.getBirthDate().isAfter(LocalDate.of(1990, 1, 1)) &&
                        p.getCreatedAt().isBefore(LocalDateTime.of(2024, 4, 1, 0, 0)) &&
                        p.getStartTime().isAfter(LocalTime.of(8, 0)));
    }

    @Test
    void localDateTimeWithComplexConditions() {
        var results = personOps().where((Person p) -> p.createdAt != null &&
                p.createdAt.isAfter(LocalDateTime.of(2024, 2, 1, 0, 0)) &&
                p.age < 40).toList();

        assertThat(results)
                .hasSizeGreaterThan(0)
                .allMatch(p -> p.getCreatedAt() != null &&
                        p.getCreatedAt().isAfter(LocalDateTime.of(2024, 2, 1, 0, 0)) &&
                        p.getAge() < 40);
    }

    @Test
    void localTimeWithOrConditions() {
        var results = personOps().where((Person p) -> p.startTime.isBefore(LocalTime.of(9, 0)) ||
                p.startTime.isAfter(LocalTime.of(9, 0))).toList();

        assertThat(results)
                .hasSizeGreaterThan(0)
                .allMatch(p -> p.getStartTime().isBefore(LocalTime.of(9, 0)) ||
                        p.getStartTime().isAfter(LocalTime.of(9, 0)));
    }
}
