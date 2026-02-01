package io.quarkiverse.qubit.it.testdata;

import io.quarkiverse.qubit.it.Department;
import io.quarkiverse.qubit.it.Person;
import io.quarkiverse.qubit.it.Phone;
import io.quarkiverse.qubit.it.Product;
import io.quarkiverse.qubit.it.Tag;

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

    // Cache for reusable departments
    private static final Map<String, Department> departmentCache = new HashMap<>();

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
        return tagCache.computeIfAbsent(name, _ -> {
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

    // ========== Department Helpers (Private) ==========

    private static Department getOrCreateDepartment(String name, String code, int budget) {
        return departmentCache.computeIfAbsent(name, _ -> {
            Department dept = new Department(name, code, budget);
            dept.persist();
            return dept;
        });
    }

    private static void createStandardDepartments() {
        getOrCreateDepartment("Engineering", "ENG", 500000);
        getOrCreateDepartment("Sales", "SLS", 300000);
        getOrCreateDepartment("Human Resources", "HR", 150000);
        getOrCreateDepartment("Marketing", "MKT", 200000);
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
     * Creates and persists persons with phone relationships (one-to-many)
     * and department relationships (many-to-one) for three-level navigation testing.
     * John has 2 phones, Jane has 1 phone, Bob has 3 phones, etc.
     * Persons are assigned to departments:
     * - John, Alice -> Engineering (ENG, budget 500000)
     * - Jane, Charlie -> Sales (SLS, budget 300000)
     * - Bob -> Human Resources (HR, budget 150000)
     */
    public static void createPersonsWithPhones() {
        // Create departments first (must be persisted before assigning to persons)
        departmentCache.clear();
        createStandardDepartments();

        Department engineering = departmentCache.get("Engineering");
        Department sales = departmentCache.get("Sales");
        Department hr = departmentCache.get("Human Resources");

        Person john = createJohnDoe();
        john.department = engineering;
        john.persist();
        addPhonesTo(john, new String[][]{
            {"555-0101", "mobile", "true"},
            {"555-0102", "work", "false"}
        });

        Person jane = createJaneSmith();
        jane.department = sales;
        jane.persist();
        addPhonesTo(jane, new String[][]{
            {"555-0201", "mobile", "true"}
        });

        Person bob = createBobJohnson();
        bob.department = hr;
        bob.persist();
        addPhonesTo(bob, new String[][]{
            {"555-0301", "mobile", "true"},
            {"555-0302", "home", "false"},
            {"555-0303", "work", "false"}
        });

        Person alice = createAliceWilliams();
        alice.department = engineering;
        alice.persist();
        addPhonesTo(alice, new String[][]{
            {"555-0401", "mobile", "true"},
            {"555-0402", "work", "false"}
        });

        Person charlie = createCharlieBrown();
        charlie.department = sales;
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
        // Delete in order respecting foreign key constraints:
        // 1. Phone references Person, so delete phones first
        // 2. Person references Department, so delete persons before departments
        // 3. Product_Tag is a join table, handled by JPA
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
        try {
            Department.deleteAll();
        } catch (Exception e) {
            // Table might not exist in some test configurations
        }
        Product.deleteAll();
        tagCache.clear();
        departmentCache.clear();
    }
}
