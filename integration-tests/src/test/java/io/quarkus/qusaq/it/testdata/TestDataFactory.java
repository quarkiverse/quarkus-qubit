package io.quarkus.qusaq.it.testdata;

import io.quarkus.qusaq.it.Person;
import io.quarkus.qusaq.it.Product;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * Centralized factory for creating test data across integration tests.
 * Provides consistent test datasets with methods for common scenarios.
 */
public class TestDataFactory {

    /**
     * Creates and persists the standard set of 5 Person entities.
     * This is the most common dataset used across tests.
     */
    public static void createStandardPersons() {
        new Person("John", "Doe", "john.doe@example.com", 30,
                LocalDate.of(1993, 5, 15), true, 75000.0, 1000001L, 1.75f,
                LocalDateTime.of(2024, 1, 15, 9, 30), LocalTime.of(9, 0)).persist();

        new Person("Jane", "Smith", "jane.smith@example.com", 25,
                LocalDate.of(1998, 8, 22), true, 65000.0, 1000002L, 1.68f,
                LocalDateTime.of(2024, 2, 20, 14, 45), LocalTime.of(8, 30)).persist();

        new Person("Bob", "Johnson", "bob.johnson@example.com", 45,
                LocalDate.of(1978, 3, 10), false, 85000.0, 1000003L, 1.82f,
                LocalDateTime.of(2024, 3, 10, 8, 0), LocalTime.of(10, 0)).persist();

        new Person("Alice", "Williams", "alice.williams@example.com", 35,
                LocalDate.of(1988, 11, 5), true, 90000.0, 1000004L, 1.65f,
                LocalDateTime.of(2024, 4, 5, 16, 20), LocalTime.of(8, 0)).persist();

        new Person("Charlie", "Brown", "charlie.brown@example.com", 28,
                LocalDate.of(1995, 7, 18), true, 55000.0, 1000005L, 1.78f,
                LocalDateTime.of(2024, 5, 12, 10, 15), LocalTime.of(9, 30)).persist();
    }

    /**
     * Creates and persists the standard 5 persons plus David.
     * Used by tests requiring additional person data.
     */
    public static void createAdditionalPersons() {
        createStandardPersons();

        new Person("David", "Miller", "david.miller@example.com", 40,
                LocalDate.of(1984, 6, 20), true, 95000.0, 1000006L, 1.80f,
                LocalDateTime.of(2024, 6, 1, 11, 0), LocalTime.of(9, 15)).persist();
    }

    /**
     * Creates and persists persons for null checking tests.
     * Includes standard persons plus Eve with null values.
     */
    public static void createPersonsForNullChecks() {
        new Person("John", "Doe", "john.doe@example.com", 30,
                LocalDate.of(1993, 5, 15), true, 75000.0, 1000001L, 1.75f,
                LocalDateTime.of(2024, 1, 15, 9, 30), LocalTime.of(9, 0)).persist();

        new Person("Jane", "Smith", "jane.smith@example.com", 25,
                LocalDate.of(1998, 8, 22), true, 65000.0, 1000002L, 1.68f,
                LocalDateTime.of(2024, 2, 20, 14, 45), LocalTime.of(8, 30)).persist();

        // Person with null values for testing null safety
        new Person("Eve", "Wilson", "", 50,
                null, false, null, null, null, null, null).persist();
    }

    /**
     * Creates and persists persons for string operation tests.
     * Includes variations in email formatting (spaces, different domains).
     */
    public static void createPersonsForStringTests() {
        createStandardPersons();

        new Person("David", "Miller", " david.miller@example.com ", 40,
                LocalDate.of(1984, 6, 20), true, 95000.0, 1000006L, 1.80f,
                LocalDateTime.of(2024, 6, 1, 11, 0), LocalTime.of(9, 15)).persist();

        new Person("Eve", "Wilson", "", 50,
                null, false, null, null, null, null, null).persist();
    }

    /**
     * Creates and persists the standard set of 5 Product entities.
     */
    public static void createStandardProducts() {
        new Product("Laptop", "Electronics", new BigDecimal("1299.99"),
                50, true, "High-performance laptop", 4.5).persist();

        new Product("Smartphone", "Electronics", new BigDecimal("899.99"),
                100, true, "Latest smartphone model", 4.7).persist();

        new Product("Desk Chair", "Furniture", new BigDecimal("299.99"),
                25, true, "Ergonomic office chair", 4.2).persist();

        new Product("Coffee Maker", "Appliances", new BigDecimal("89.99"),
                0, false, "Programmable coffee maker", 4.0).persist();

        new Product("Monitor", "Electronics", new BigDecimal("399.99"),
                30, true, "27-inch 4K monitor", 4.6).persist();
    }

    /**
     * Creates and persists a subset of persons (3) for simpler tests.
     */
    public static void createMinimalPersons() {
        new Person("John", "Doe", "john.doe@example.com", 30,
                LocalDate.of(1993, 5, 15), true, 75000.0, 1000001L, 1.75f,
                LocalDateTime.of(2024, 1, 15, 9, 30), LocalTime.of(9, 0)).persist();

        new Person("Jane", "Smith", "jane.smith@example.com", 25,
                LocalDate.of(1998, 8, 22), true, 65000.0, 1000002L, 1.68f,
                LocalDateTime.of(2024, 2, 20, 14, 45), LocalTime.of(8, 30)).persist();

        new Person("Bob", "Johnson", "bob.johnson@example.com", 45,
                LocalDate.of(1978, 3, 10), false, 85000.0, 1000003L, 1.82f,
                LocalDateTime.of(2024, 3, 10, 8, 0), LocalTime.of(10, 0)).persist();
    }

    /**
     * Creates and persists persons plus products for tests that need both.
     */
    public static void createStandardPersonsAndProducts() {
        createStandardPersons();
        createStandardProducts();
    }

    /**
     * Clears all test data from the database.
     * Should be called in @BeforeEach to ensure test isolation.
     */
    public static void clearAllData() {
        Person.deleteAll();
        Product.deleteAll();
    }
}
