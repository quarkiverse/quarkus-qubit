package io.quarkus.qusaq.it.fluent;

import io.quarkus.qusaq.it.Person;
import io.quarkus.qusaq.it.Product;
import io.quarkus.qusaq.it.testdata.TestDataFactory;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for simple field projection using the fluent API.
 * <p>
 * Phase 2.1: Simple field projection only (no where, no DTO construction).
 * Example: {@code Person.select(p -> p.firstName).toList()}
 */
@QuarkusTest
class ProjectionTest {

    @BeforeEach
    @Transactional
    void setupTestData() {
        TestDataFactory.clearAllData();
        TestDataFactory.createStandardPersonsAndProducts();
    }

    // ========== String Field Projection Tests ==========

    @Test
    void selectStringField_firstName() {
        List<String> firstNames = Person.select((Person p) -> p.firstName).toList();

        assertThat(firstNames)
                .hasSize(5)
                .containsExactlyInAnyOrder("John", "Jane", "Bob", "Alice", "Charlie");
    }

    @Test
    void selectStringField_lastName() {
        List<String> lastNames = Person.select((Person p) -> p.lastName).toList();

        assertThat(lastNames)
                .hasSize(5)
                .containsExactlyInAnyOrder("Doe", "Smith", "Johnson", "Williams", "Brown");
    }

    @Test
    void selectStringField_email() {
        List<String> emails = Person.select((Person p) -> p.email).toList();

        assertThat(emails)
                .hasSize(5)
                .containsExactlyInAnyOrder(
                        "john.doe@example.com",
                        "jane.smith@example.com",
                        "bob.johnson@example.com",
                        "alice.williams@example.com",
                        "charlie.brown@example.com");
    }

    // ========== Integer Field Projection Tests ==========

    @Test
    void selectIntegerField_age() {
        List<Integer> ages = Person.select((Person p) -> p.age).toList();

        assertThat(ages)
                .hasSize(5)
                .containsExactlyInAnyOrder(30, 25, 45, 35, 28);
    }

    // ========== Long Field Projection Tests ==========

    @Test
    void selectLongField_employeeId() {
        List<Long> employeeIds = Person.select((Person p) -> p.employeeId).toList();

        assertThat(employeeIds)
                .hasSize(5)
                .containsExactlyInAnyOrder(1000001L, 1000002L, 1000003L, 1000004L, 1000005L);
    }

    // ========== Double Field Projection Tests ==========

    @Test
    void selectDoubleField_salary() {
        List<Double> salaries = Person.select((Person p) -> p.salary).toList();

        assertThat(salaries)
                .hasSize(5)
                .containsExactlyInAnyOrder(75000.0, 65000.0, 85000.0, 90000.0, 55000.0);
    }

    // ========== Float Field Projection Tests ==========

    @Test
    void selectFloatField_height() {
        List<Float> heights = Person.select((Person p) -> p.height).toList();

        assertThat(heights)
                .hasSize(5)
                .containsExactlyInAnyOrder(1.75f, 1.68f, 1.82f, 1.65f, 1.78f);
    }

    // ========== Boolean Field Projection Tests ==========

    @Test
    void selectBooleanField_active() {
        List<Boolean> activeStatuses = Person.select((Person p) -> p.active).toList();

        assertThat(activeStatuses)
                .hasSizeGreaterThan(0)
                .contains(true, false); // Should have both active and inactive persons
    }

    // ========== LocalDate Field Projection Tests ==========

    @Test
    void selectLocalDateField_birthDate() {
        List<LocalDate> birthDates = Person.select((Person p) -> p.birthDate).toList();

        assertThat(birthDates)
                .hasSize(5)
                .containsExactlyInAnyOrder(
                        LocalDate.of(1993, 5, 15),  // John
                        LocalDate.of(1998, 8, 22),  // Jane
                        LocalDate.of(1978, 3, 10),  // Bob
                        LocalDate.of(1988, 11, 5),  // Alice
                        LocalDate.of(1995, 7, 18)); // Charlie
    }

    // ========== BigDecimal Field Projection Tests ==========

    @Test
    void selectBigDecimalField_price() {
        List<BigDecimal> prices = Product.select((Product p) -> p.price).toList();

        assertThat(prices)
                .hasSize(5)
                .containsExactlyInAnyOrder(
                        new BigDecimal("1299.99"),  // Laptop
                        new BigDecimal("899.99"),   // Smartphone
                        new BigDecimal("299.99"),   // Desk Chair
                        new BigDecimal("89.99"),    // Coffee Maker
                        new BigDecimal("399.99"));  // Monitor
    }

    // ========== Verify Result Count Matches Entity Count ==========

    @Test
    void selectField_resultCountMatchesEntityCount() {
        long entityCount = Person.where((Person p) -> p.age > 0).count();
        List<Integer> ages = Person.select((Person p) -> p.age).toList();

        // All persons should have an age, so counts should match
        assertThat(ages).hasSize((int) entityCount);
    }

    // ========== Comprehensive Integration Test ==========

    @Test
    void selectMultipleFieldTypes_allWorkCorrectly() {
        // Test that projection works correctly across different data types in sequence
        List<String> firstNames = Person.select((Person p) -> p.firstName).toList();
        List<Integer> ages = Person.select((Person p) -> p.age).toList();
        List<Boolean> activeStatuses = Person.select((Person p) -> p.active).toList();
        List<BigDecimal> prices = Product.select((Product p) -> p.price).toList();

        // Verify each projection returns correct count
        assertThat(firstNames).hasSize(5);
        assertThat(ages).hasSize(5);
        assertThat(activeStatuses).hasSize(5);
        assertThat(prices).hasSize(5);

        // Spot-check specific values to ensure correct data
        assertThat(firstNames).contains("John", "Alice");
        assertThat(ages).contains(30, 35);
        assertThat(activeStatuses).contains(true, false);
        assertThat(prices).contains(new BigDecimal("1299.99"), new BigDecimal("89.99"));
    }
}
