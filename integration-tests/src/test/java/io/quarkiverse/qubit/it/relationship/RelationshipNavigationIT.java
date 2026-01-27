package io.quarkiverse.qubit.it.relationship;

import io.quarkiverse.qubit.it.Department;
import io.quarkiverse.qubit.it.Person;
import io.quarkiverse.qubit.it.Phone;
import io.quarkiverse.qubit.it.Product;
import io.quarkiverse.qubit.it.Tag;
import io.quarkiverse.qubit.it.testutil.PhoneQueryOperations;
import io.quarkiverse.qubit.it.testutil.StaticPhoneQueryOperations;
import io.quarkiverse.qubit.it.testutil.StaticTagQueryOperations;
import io.quarkiverse.qubit.it.testutil.TagQueryOperations;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for Phone and Tag entities using static entity methods.
 */
@QuarkusTest
class RelationshipNavigationIT extends AbstractRelationshipNavigationTest {

    @Override
    protected PhoneQueryOperations phoneOps() {
        return StaticPhoneQueryOperations.INSTANCE;
    }

    @Override
    protected TagQueryOperations tagOps() {
        return StaticTagQueryOperations.INSTANCE;
    }

    // ========== COMBINED ENTITY QUERIES ==========

    @Test
    void queryAllEntityTypes() {
        var activePersons = Person.where((Person p) -> p.active).toList();
        assertThat(activePersons).hasSizeGreaterThan(0);

        var electronics = Product.where((Product p) -> p.category.equals("Electronics")).toList();
        assertThat(electronics).hasSize(3);

        var mobilePhones = Phone.where((Phone ph) -> ph.type.equals("mobile")).toList();
        assertThat(mobilePhones).hasSize(5);

        var electronicsTags = Tag.where((Tag t) -> t.name.equals("electronics")).toList();
        assertThat(electronicsTags).hasSize(1);
    }

    @Test
    void mixedEntityQueries() {
        var adults = Person.where((Person p) -> p.age >= 30).toList();
        assertThat(adults).hasSizeGreaterThan(0);

        var premiumProducts = Product.where((Product p) ->
                p.price.compareTo(new BigDecimal("500")) > 0 && p.available
        ).toList();
        assertThat(premiumProducts).hasSizeGreaterThan(0);

        var primaryPhones = Phone.where((Phone ph) -> ph.isPrimaryPhone).toList();
        assertThat(primaryPhones).hasSize(5);

        var coloredTags = Tag.where((Tag t) -> !t.color.equals("")).toList();
        assertThat(coloredTags).hasSizeGreaterThan(0);
    }

    // ========== COLLECTION GETTER TESTS (for mutation coverage) ==========

    @Test
    @Transactional
    void departmentGetEmployeesReturnsAssignedPersons() {
        var engineering = Department.where((Department d) -> d.name.equals("Engineering")).findFirst();

        assertThat(engineering).isPresent();
        var employees = engineering.get().getEmployees();
        assertThat(employees)
                .isNotEmpty()
                .extracting(Person::getFirstName)
                .contains("John", "Alice");
    }

    @Test
    @Transactional
    void tagGetProductsReturnsAssociatedProducts() {
        var electronicsTag = Tag.where((Tag t) -> t.name.equals("electronics")).findFirst();

        assertThat(electronicsTag).isPresent();
        var products = electronicsTag.get().getProducts();
        assertThat(products)
                .isNotEmpty()
                .extracting(Product::getName)
                .contains("Laptop", "Smartphone", "Monitor");
    }
}
