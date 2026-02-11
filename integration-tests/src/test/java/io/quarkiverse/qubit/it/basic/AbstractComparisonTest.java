package io.quarkiverse.qubit.it.basic;

import io.quarkiverse.qubit.it.Person;
import io.quarkiverse.qubit.it.Product;
import io.quarkiverse.qubit.it.testdata.TestDataFactory;
import io.quarkiverse.qubit.it.testutil.PersonQueryOperations;
import io.quarkiverse.qubit.it.testutil.ProductQueryOperations;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Abstract base class for comparison operation tests (>, <, >=, <=, !=) on various field types.
 */
public abstract class AbstractComparisonTest {

    protected abstract PersonQueryOperations personOps();

    protected abstract ProductQueryOperations productOps();

    @BeforeEach
    @Transactional
    protected void setupTestData() {
        TestDataFactory.clearAllData();
        TestDataFactory.createStandardPersonsAndProducts();
    }

    // Integer comparisons
    @Test
    void integerGreaterThan() {
        var results = personOps().where((Person p) -> p.age > 30).toList();

        assertThat(results)
                .hasSizeGreaterThan(0)
                .allMatch(p -> p.getAge() > 30);
    }

    @Test
    void integerGreaterThanOrEqual() {
        var results = personOps().where((Person p) -> p.age >= 30).toList();

        assertThat(results)
                .hasSizeGreaterThan(0)
                .allMatch(p -> p.getAge() >= 30);
    }

    @Test
    void integerLessThan() {
        var results = personOps().where((Person p) -> p.age < 30).toList();

        assertThat(results)
                .hasSizeGreaterThan(0)
                .allMatch(p -> p.getAge() < 30);
    }

    @Test
    void integerLessThanOrEqual() {
        var results = personOps().where((Person p) -> p.age <= 30).toList();

        assertThat(results)
                .hasSizeGreaterThan(0)
                .allMatch(p -> p.getAge() <= 30);
    }

    @Test
    void integerNotEquals() {
        var results = personOps().where((Person p) -> p.age != 30).toList();

        assertThat(results)
                .hasSizeGreaterThan(0)
                .noneMatch(p -> p.getAge() == 30);
    }

    // Long comparisons
    @Test
    void longGreaterThan() {
        var results = personOps().where((Person p) -> p.employeeId > 1000003L).toList();

        assertThat(results)
                .hasSizeGreaterThan(0)
                .allMatch(p -> p.getEmployeeId() > 1000003L);
    }

    @Test
    void longGreaterThanOrEqual() {
        var results = personOps().where((Person p) -> p.employeeId >= 1000002L).toList();

        assertThat(results)
                .hasSizeGreaterThan(0)
                .allMatch(p -> p.getEmployeeId() >= 1000002L);
    }

    @Test
    void longLessThan() {
        var results = personOps().where((Person p) -> p.employeeId < 1000003L).toList();

        assertThat(results)
                .hasSizeGreaterThan(0)
                .allMatch(p -> p.getEmployeeId() < 1000003L);
    }

    @Test
    void longLessThanOrEqual() {
        var results = personOps().where((Person p) -> p.employeeId <= 1000003L).toList();

        assertThat(results)
                .hasSizeGreaterThan(0)
                .allMatch(p -> p.getEmployeeId() <= 1000003L);
    }

    @Test
    void longNotEquals() {
        var results = personOps().where((Person p) -> p.employeeId != 1000001L).toList();

        assertThat(results)
                .hasSizeGreaterThan(0)
                .allMatch(p -> p.getEmployeeId() != 1000001L);
    }

    // Float comparisons
    @Test
    void floatGreaterThan() {
        var results = personOps().where((Person p) -> p.height > 1.70f).toList();

        assertThat(results)
                .hasSizeGreaterThan(0)
                .allMatch(p -> p.getHeight() > 1.70f);
    }

    @Test
    void floatGreaterThanOrEqual() {
        var results = personOps().where((Person p) -> p.height >= 1.70f).toList();

        assertThat(results)
                .hasSizeGreaterThan(0)
                .allMatch(p -> p.getHeight() >= 1.70f);
    }

    @Test
    void floatLessThan() {
        var results = personOps().where((Person p) -> p.height < 1.70f).toList();

        assertThat(results)
                .hasSizeGreaterThan(0)
                .allMatch(p -> p.getHeight() < 1.70f);
    }

    @Test
    void floatLessThanOrEqual() {
        var results = personOps().where((Person p) -> p.height <= 1.75f).toList();

        assertThat(results)
                .hasSizeGreaterThan(0)
                .allMatch(p -> p.getHeight() <= 1.75f);
    }

    @Test
    void floatNotEquals() {
        var results = personOps().where((Person p) -> p.height != 1.75f).toList();

        assertThat(results)
                .hasSizeGreaterThan(0)
                .allMatch(p -> p.getHeight() != 1.75f);
    }

    // Double comparisons
    @Test
    void doubleGreaterThan() {
        var results = personOps().where((Person p) -> p.salary > 70000.0).toList();

        assertThat(results)
                .hasSizeGreaterThan(0)
                .allMatch(p -> p.getSalary() > 70000.0);
    }

    @Test
    void doubleGreaterThanOrEqual() {
        var results = personOps().where((Person p) -> p.salary >= 75000.0).toList();

        assertThat(results)
                .hasSizeGreaterThan(0)
                .allMatch(p -> p.getSalary() >= 75000.0);
    }

    @Test
    void doubleLessThan() {
        var results = personOps().where((Person p) -> p.salary < 80000.0).toList();

        assertThat(results)
                .hasSizeGreaterThan(0)
                .allMatch(p -> p.getSalary() < 80000.0);
    }

    @Test
    void doubleLessThanOrEqual() {
        var results = personOps().where((Person p) -> p.salary <= 75000.0).toList();

        assertThat(results)
                .hasSizeGreaterThan(0)
                .allMatch(p -> p.getSalary() <= 75000.0);
    }

    @Test
    void doubleNotEquals() {
        var results = personOps().where((Person p) -> p.salary != 75000.0).toList();

        assertThat(results)
                .hasSizeGreaterThan(0)
                .noneMatch(p -> p.getSalary() == 75000.0);
    }

    // BigDecimal comparisons
    @Test
    void bigDecimalGreaterThan() {
        var results = productOps().where((Product p) -> p.price.compareTo(new BigDecimal("500")) > 0).toList();

        assertThat(results)
                .hasSizeGreaterThan(0)
                .allMatch(p -> p.getPrice().compareTo(new BigDecimal("500")) > 0);
    }

    @Test
    void bigDecimalGreaterThanOrEqual() {
        var results = productOps().where((Product p) -> p.price.compareTo(new BigDecimal("500")) >= 0).toList();

        assertThat(results)
                .hasSizeGreaterThan(0)
                .allMatch(p -> p.getPrice().compareTo(new BigDecimal("500")) >= 0);
    }

    @Test
    void bigDecimalLessThan() {
        var results = productOps().where((Product p) -> p.price.compareTo(new BigDecimal("1000")) < 0).toList();

        assertThat(results)
                .hasSizeGreaterThan(0)
                .allMatch(p -> p.getPrice().compareTo(new BigDecimal("1000")) < 0);
    }

    @Test
    void bigDecimalLessThanOrEqual() {
        var results = productOps().where((Product p) -> p.price.compareTo(new BigDecimal("300")) <= 0).toList();

        assertThat(results)
                .hasSizeGreaterThan(0)
                .allMatch(p -> p.getPrice().compareTo(new BigDecimal("300")) <= 0);
    }

    @Test
    void bigDecimalNotEquals() {
        var results = productOps().where((Product p) -> p.price.compareTo(new BigDecimal("899.99")) != 0).toList();

        assertThat(results)
                .hasSizeGreaterThan(0)
                .noneMatch(p -> p.getPrice().compareTo(new BigDecimal("899.99")) == 0);
    }

    // Temporal comparisons
    @Test
    void localDateAfter() {
        var results = personOps().where((Person p) -> p.birthDate.isAfter(LocalDate.of(1990, 1, 1))).toList();

        assertThat(results)
                .hasSizeGreaterThan(0)
                .allMatch(p -> p.getBirthDate().isAfter(LocalDate.of(1990, 1, 1)));
    }

    @Test
    void localDateBefore() {
        var results = personOps().where((Person p) -> p.birthDate.isBefore(LocalDate.of(1990, 1, 1))).toList();

        assertThat(results)
                .hasSizeGreaterThan(0)
                .allMatch(p -> p.getBirthDate().isBefore(LocalDate.of(1990, 1, 1)));
    }

    @Test
    void localDateTimeAfter() {
        var results = personOps().where((Person p) -> p.createdAt.isAfter(LocalDateTime.of(2024, 3, 1, 0, 0))).toList();

        assertThat(results)
                .hasSizeGreaterThan(0)
                .allMatch(p -> p.getCreatedAt().isAfter(LocalDateTime.of(2024, 3, 1, 0, 0)));
    }

    @Test
    void localDateTimeBefore() {
        var results = personOps().where((Person p) -> p.createdAt.isBefore(LocalDateTime.of(2024, 3, 1, 0, 0))).toList();

        assertThat(results)
                .hasSizeGreaterThan(0)
                .allMatch(p -> p.getCreatedAt().isBefore(LocalDateTime.of(2024, 3, 1, 0, 0)));
    }

    @Test
    void localTimeAfter() {
        var results = personOps().where((Person p) -> p.startTime.isAfter(LocalTime.of(9, 0))).toList();

        assertThat(results)
                .hasSizeGreaterThan(0)
                .allMatch(p -> p.getStartTime().isAfter(LocalTime.of(9, 0)));
    }

    @Test
    void localTimeBefore() {
        var results = personOps().where((Person p) -> p.startTime.isBefore(LocalTime.of(9, 0))).toList();

        assertThat(results)
                .hasSizeGreaterThan(0)
                .allMatch(p -> p.getStartTime().isBefore(LocalTime.of(9, 0)));
    }

    // Range queries
    @Test
    void integerRangeQuery() {
        var results = personOps().where((Person p) -> p.age >= 25 && p.age <= 35).toList();

        assertThat(results)
                .hasSizeGreaterThan(0)
                .allMatch(p -> p.getAge() >= 25 && p.getAge() <= 35);
    }

    @Test
    void longRangeQuery() {
        var results = personOps().where((Person p) -> p.employeeId >= 1000002L && p.employeeId <= 1000004L).toList();

        assertThat(results)
                .hasSizeGreaterThan(0)
                .allMatch(p -> p.getEmployeeId() >= 1000002L && p.getEmployeeId() <= 1000004L);
    }

    @Test
    void floatRangeQuery() {
        var results = personOps().where((Person p) -> p.height >= 1.65f && p.height <= 1.80f).toList();

        assertThat(results)
                .hasSizeGreaterThan(0)
                .allMatch(p -> p.getHeight() >= 1.65f && p.getHeight() <= 1.80f);
    }

    @Test
    void bigDecimalRangeQuery() {
        var results = productOps().where((Product p) -> p.price.compareTo(new BigDecimal("800.00")) >= 0 &&
                p.price.compareTo(new BigDecimal("1500.00")) <= 0).toList();

        assertThat(results)
                .hasSizeGreaterThan(0)
                .allMatch(p -> p.getPrice().compareTo(new BigDecimal("800.00")) >= 0 &&
                        p.getPrice().compareTo(new BigDecimal("1500.00")) <= 0);
    }
}
