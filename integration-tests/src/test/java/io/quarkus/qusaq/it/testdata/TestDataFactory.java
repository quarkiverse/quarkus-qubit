package io.quarkus.qusaq.it.testdata;

import io.quarkus.qusaq.it.Person;
import io.quarkus.qusaq.it.Phone;
import io.quarkus.qusaq.it.Product;
import io.quarkus.qusaq.it.Tag;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Centralized factory for creating test data across integration tests.
 * Provides consistent test datasets with methods for common scenarios.
 * <p>
 * Uses builder pattern internally to reduce duplication and improve maintainability.
 * Each named person (John, Jane, etc.) is defined once and reused across factory methods.
 */
public class TestDataFactory {

    // Cache for reusable tags
    private static final Map<String, Tag> tagCache = new HashMap<>();

    // ========== Person Builders (Private) ==========

    private static Person createJohnDoe() {
        return new Person("John", "Doe", "john.doe@example.com", 30,
                LocalDate.of(1993, 5, 15), true, 75000.0, 1000001L, 1.75f,
                LocalDateTime.of(2024, 1, 15, 9, 30), LocalTime.of(9, 0));
    }

    private static Person createJaneSmith() {
        return new Person("Jane", "Smith", "jane.smith@example.com", 25,
                LocalDate.of(1998, 8, 22), true, 65000.0, 1000002L, 1.68f,
                LocalDateTime.of(2024, 2, 20, 14, 45), LocalTime.of(8, 30));
    }

    private static Person createBobJohnson() {
        return new Person("Bob", "Johnson", "bob.johnson@example.com", 45,
                LocalDate.of(1978, 3, 10), false, 85000.0, 1000003L, 1.82f,
                LocalDateTime.of(2024, 3, 10, 8, 0), LocalTime.of(10, 0));
    }

    private static Person createAliceWilliams() {
        return new Person("Alice", "Williams", "alice.williams@example.com", 35,
                LocalDate.of(1988, 11, 5), true, 90000.0, 1000004L, 1.65f,
                LocalDateTime.of(2024, 4, 5, 16, 20), LocalTime.of(8, 0));
    }

    private static Person createCharlieBrown() {
        return new Person("Charlie", "Brown", "charlie.brown@example.com", 28,
                LocalDate.of(1995, 7, 18), true, 55000.0, 1000005L, 1.78f,
                LocalDateTime.of(2024, 5, 12, 10, 15), LocalTime.of(9, 30));
    }

    private static Person createDavidMiller() {
        return new Person("David", "Miller", "david.miller@example.com", 40,
                LocalDate.of(1984, 6, 20), true, 95000.0, 1000006L, 1.80f,
                LocalDateTime.of(2024, 6, 1, 11, 0), LocalTime.of(9, 15));
    }

    private static Person createDavidMillerWithSpaces() {
        return new Person("David", "Miller", " david.miller@example.com ", 40,
                LocalDate.of(1984, 6, 20), true, 95000.0, 1000006L, 1.80f,
                LocalDateTime.of(2024, 6, 1, 11, 0), LocalTime.of(9, 15));
    }

    private static Person createEveWilsonWithNulls() {
        return new Person("Eve", "Wilson", "", 50,
                null, false, null, null, null, null, null);
    }

    // ========== Tag Helpers (Private) ==========

    private static Tag getOrCreateTag(String name, String color) {
        return tagCache.computeIfAbsent(name, k -> {
            Tag tag = new Tag(name, color);
            tag.persist();
            return tag;
        });
    }

    private static void createStandardTags() {
        getOrCreateTag("electronics", "blue");
        getOrCreateTag("premium", "gold");
        getOrCreateTag("bestseller", "green");
        getOrCreateTag("sale", "red");
        getOrCreateTag("new-arrival", "purple");
        getOrCreateTag("eco-friendly", "lime");
    }

    // ========== Phone Helpers (Private) ==========

    private static void addPhonesTo(Person person, String[][] phoneData) {
        for (String[] data : phoneData) {
            Phone phone = new Phone(data[0], data[1], Boolean.parseBoolean(data[2]), person);
            phone.persist();
            person.phones.add(phone);
        }
    }

    // ========== Public Factory Methods ==========

    /**
     * Creates and persists the standard set of 5 Person entities.
     * This is the most common dataset used across tests.
     */
    public static void createStandardPersons() {
        createJohnDoe().persist();
        createJaneSmith().persist();
        createBobJohnson().persist();
        createAliceWilliams().persist();
        createCharlieBrown().persist();
    }

    /**
     * Creates and persists persons with phone relationships (one-to-many).
     * John has 2 phones, Jane has 1 phone, Bob has 3 phones, etc.
     */
    public static void createPersonsWithPhones() {
        Person john = createJohnDoe();
        john.persist();
        addPhonesTo(john, new String[][]{
            {"555-0101", "mobile", "true"},
            {"555-0102", "work", "false"}
        });

        Person jane = createJaneSmith();
        jane.persist();
        addPhonesTo(jane, new String[][]{
            {"555-0201", "mobile", "true"}
        });

        Person bob = createBobJohnson();
        bob.persist();
        addPhonesTo(bob, new String[][]{
            {"555-0301", "mobile", "true"},
            {"555-0302", "home", "false"},
            {"555-0303", "work", "false"}
        });

        Person alice = createAliceWilliams();
        alice.persist();
        addPhonesTo(alice, new String[][]{
            {"555-0401", "mobile", "true"},
            {"555-0402", "work", "false"}
        });

        Person charlie = createCharlieBrown();
        charlie.persist();
        addPhonesTo(charlie, new String[][]{
            {"555-0501", "mobile", "true"}
        });
    }

    /**
     * Creates and persists the standard 5 persons plus David.
     * Used by tests requiring additional person data.
     */
    public static void createAdditionalPersons() {
        createStandardPersons();
        createDavidMiller().persist();
    }

    /**
     * Creates and persists persons for null checking tests.
     * Includes standard persons plus Eve with null values.
     */
    public static void createPersonsForNullChecks() {
        createJohnDoe().persist();
        createJaneSmith().persist();
        createEveWilsonWithNulls().persist();
    }

    /**
     * Creates and persists persons for string operation tests.
     * Includes variations in email formatting (spaces, different domains).
     */
    public static void createPersonsForStringTests() {
        createStandardPersons();
        createDavidMillerWithSpaces().persist();
        createEveWilsonWithNulls().persist();
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
     * Creates and persists products with tag relationships (many-to-many).
     */
    public static void createProductsWithTags() {
        tagCache.clear();
        createStandardTags();

        Product laptop = new Product("Laptop", "Electronics", new BigDecimal("1299.99"),
                50, true, "High-performance laptop", 4.5);
        laptop.persist();
        laptop.tags.add(getOrCreateTag("electronics", "blue"));
        laptop.tags.add(getOrCreateTag("premium", "gold"));
        laptop.tags.add(getOrCreateTag("bestseller", "green"));

        Product smartphone = new Product("Smartphone", "Electronics", new BigDecimal("899.99"),
                100, true, "Latest smartphone model", 4.7);
        smartphone.persist();
        smartphone.tags.add(getOrCreateTag("electronics", "blue"));
        smartphone.tags.add(getOrCreateTag("new-arrival", "purple"));
        smartphone.tags.add(getOrCreateTag("bestseller", "green"));

        Product chair = new Product("Desk Chair", "Furniture", new BigDecimal("299.99"),
                25, true, "Ergonomic office chair", 4.2);
        chair.persist();
        chair.tags.add(getOrCreateTag("eco-friendly", "lime"));
        chair.tags.add(getOrCreateTag("sale", "red"));

        Product coffeeMaker = new Product("Coffee Maker", "Appliances", new BigDecimal("89.99"),
                0, false, "Programmable coffee maker", 4.0);
        coffeeMaker.persist();
        coffeeMaker.tags.add(getOrCreateTag("sale", "red"));

        Product monitor = new Product("Monitor", "Electronics", new BigDecimal("399.99"),
                30, true, "27-inch 4K monitor", 4.6);
        monitor.persist();
        monitor.tags.add(getOrCreateTag("electronics", "blue"));
        monitor.tags.add(getOrCreateTag("premium", "gold"));
    }

    /**
     * Creates and persists a subset of persons (3) for simpler tests.
     */
    public static void createMinimalPersons() {
        createJohnDoe().persist();
        createJaneSmith().persist();
        createBobJohnson().persist();
    }

    /**
     * Creates and persists persons plus products for tests that need both.
     */
    public static void createStandardPersonsAndProducts() {
        createStandardPersons();
        createStandardProducts();
    }

    /**
     * Creates and persists all entities with their relationships.
     * Persons have phones (one-to-many), Products have tags (many-to-many).
     */
    public static void createAllDataWithRelationships() {
        createPersonsWithPhones();
        createProductsWithTags();
    }

    /**
     * Clears all test data from the database.
     * Should be called in @BeforeEach to ensure test isolation.
     */
    public static void clearAllData() {
        // Delete in order respecting foreign key constraints
        // Phone references Person, so delete phones first
        // Product_Tag is a join table, handled by JPA
        try {
            Phone.deleteAll();
        } catch (Exception e) {
            // Table might not exist in some test configurations
        }
        try {
            Tag.deleteAll();
        } catch (Exception e) {
            // Table might not exist in some test configurations
        }
        Person.deleteAll();
        Product.deleteAll();
        tagCache.clear();
    }
}
