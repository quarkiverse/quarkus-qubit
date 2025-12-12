package io.quarkiverse.qubit.it.relationship;

import io.quarkiverse.qubit.it.Department;
import io.quarkiverse.qubit.it.Person;
import io.quarkiverse.qubit.it.Phone;
import io.quarkiverse.qubit.it.Product;
import io.quarkiverse.qubit.it.Tag;
import io.quarkiverse.qubit.it.testdata.TestDataFactory;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for Phone and Tag entities.
 * <p>
 * These tests verify that the new entities work correctly with Qubit queries.
 * Note: Relationship navigation (e.g., ph.owner.firstName) is not yet implemented.
 */
@QuarkusTest
class RelationshipNavigationTest {

    @BeforeEach
    @Transactional
    void setupTestData() {
        TestDataFactory.clearAllData();
        TestDataFactory.createAllDataWithRelationships();
    }

    // ========== PHONE ENTITY TESTS ==========

    @Test
    void phonesByType() {
        var results = Phone.where((Phone ph) -> ph.type.equals("mobile")).toList();

        assertThat(results)
                .hasSize(5) // One mobile phone per person
                .allMatch(ph -> ph.getType().equals("mobile"));
    }

    @Test
    void phonesByNumber() {
        var results = Phone.where((Phone ph) -> ph.number.equals("555-0101")).toList();

        assertThat(results)
                .hasSize(1)
                .extracting(Phone::getNumber)
                .containsExactly("555-0101");
    }

    @Test
    void phonesByPrimaryStatus() {
        var results = Phone.where((Phone ph) -> ph.isPrimaryPhone).toList();

        assertThat(results)
                .hasSize(5) // Each person has one primary phone
                .allMatch(Phone::isPrimaryPhone);
    }

    @Test
    void workPhones() {
        var results = Phone.where((Phone ph) -> ph.type.equals("work")).toList();

        assertThat(results)
                .hasSizeGreaterThan(0)
                .allMatch(ph -> ph.getType().equals("work"));
    }

    @Test
    void homePhones() {
        var results = Phone.where((Phone ph) -> ph.type.equals("home")).toList();

        assertThat(results)
                .hasSizeGreaterThan(0)
                .allMatch(ph -> ph.getType().equals("home"));
    }

    @Test
    void phoneCountByType() {
        long mobileCount = Phone.where((Phone ph) -> ph.type.equals("mobile")).count();
        long workCount = Phone.where((Phone ph) -> ph.type.equals("work")).count();

        assertThat(mobileCount).isEqualTo(5);
        assertThat(workCount).isGreaterThan(0);
    }

    @Test
    void phoneExists() {
        boolean exists = Phone.where((Phone ph) -> ph.number.startsWith("555-")).exists();

        assertThat(exists).isTrue();
    }

    @Test
    void phoneNumberStartsWith() {
        var results = Phone.where((Phone ph) -> ph.number.startsWith("555-01")).toList();

        assertThat(results)
                .hasSizeGreaterThan(0)
                .allMatch(ph -> ph.getNumber().startsWith("555-01"));
    }

    // ========== TAG ENTITY TESTS ==========

    @Test
    void tagsByName() {
        var results = Tag.where((Tag t) -> t.name.equals("electronics")).toList();

        assertThat(results)
                .hasSize(1)
                .extracting(Tag::getName)
                .containsExactly("electronics");
    }

    @Test
    void tagsByColor() {
        var results = Tag.where((Tag t) -> t.color.equals("blue")).toList();

        assertThat(results)
                .hasSize(1)
                .extracting(Tag::getColor)
                .containsExactly("blue");
    }

    @Test
    void tagsStartingWith() {
        var results = Tag.where((Tag t) -> t.name.startsWith("b")).toList();

        // "bestseller" starts with 'b'
        assertThat(results)
                .hasSizeGreaterThan(0)
                .allMatch(t -> t.getName().startsWith("b"));
    }

    @Test
    void tagCount() {
        long count = Tag.where((Tag t) -> t.name.contains("-")).count();

        // Tags with '-': new-arrival, eco-friendly
        assertThat(count).isEqualTo(2);
    }

    @Test
    void tagExists() {
        boolean exists = Tag.where((Tag t) -> t.name.equals("premium")).exists();

        assertThat(exists).isTrue();
    }

    @Test
    void tagNotExists() {
        boolean exists = Tag.where((Tag t) -> t.name.equals("nonexistent")).exists();

        assertThat(exists).isFalse();
    }

    // ========== COMBINED ENTITY QUERIES ==========

    @Test
    void queryAllEntityTypes() {
        // Verify all entity types work together

        // Person queries
        var activePersons = Person.where((Person p) -> p.active).toList();
        assertThat(activePersons).hasSizeGreaterThan(0);

        // Product queries
        var electronics = Product.where((Product p) -> p.category.equals("Electronics")).toList();
        assertThat(electronics).hasSize(3);

        // Phone queries
        var mobilePhones = Phone.where((Phone ph) -> ph.type.equals("mobile")).toList();
        assertThat(mobilePhones).hasSize(5);

        // Tag queries
        var electronicsTags = Tag.where((Tag t) -> t.name.equals("electronics")).toList();
        assertThat(electronicsTags).hasSize(1);
    }

    @Test
    void mixedEntityQueries() {
        // Query different entities with various predicates

        // Person by age
        var adults = Person.where((Person p) -> p.age >= 30).toList();
        assertThat(adults).hasSizeGreaterThan(0);

        // Product by price
        var premiumProducts = Product.where((Product p) ->
                p.price.compareTo(new BigDecimal("500")) > 0 && p.available
        ).toList();
        assertThat(premiumProducts).hasSizeGreaterThan(0);

        // Phone by type
        var primaryPhones = Phone.where((Phone ph) -> ph.isPrimaryPhone).toList();
        assertThat(primaryPhones).hasSize(5);

        // Tag by color
        var coloredTags = Tag.where((Tag t) -> !t.color.equals("")).toList();
        assertThat(coloredTags).hasSizeGreaterThan(0);
    }

    // ========== COLLECTION GETTER TESTS (for mutation coverage) ==========

    @Test
    @Transactional
    void departmentGetEmployeesReturnsAssignedPersons() {
        // Find Engineering department and verify getEmployees() returns assigned persons
        var engineering = Department.where((Department d) -> d.name.equals("Engineering")).findFirst();

        assertThat(engineering).isPresent();
        // Call getEmployees() to cover the mutation - must verify content not just size
        var employees = engineering.get().getEmployees();
        assertThat(employees)
                .isNotEmpty()
                .extracting(Person::getFirstName)
                .contains("John", "Alice");
    }

    @Test
    @Transactional
    void tagGetProductsReturnsAssociatedProducts() {
        // Find electronics tag and verify getProducts() returns associated products
        var electronicsTag = Tag.where((Tag t) -> t.name.equals("electronics")).findFirst();

        assertThat(electronicsTag).isPresent();
        // Call getProducts() to cover the mutation - must verify content not just size
        var products = electronicsTag.get().getProducts();
        assertThat(products)
                .isNotEmpty()
                .extracting(Product::getName)
                .contains("Laptop", "Smartphone", "Monitor");
    }
}
