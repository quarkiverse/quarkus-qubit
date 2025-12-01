package io.quarkiverse.qubit.it.query;

import io.quarkiverse.qubit.it.Person;
import io.quarkiverse.qubit.it.Product;
import io.quarkiverse.qubit.it.testdata.TestDataFactory;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for count query operations.
 */
@QuarkusTest
class CountQueryTest {

    @BeforeEach
    @Transactional
    void setupTestData() {
        TestDataFactory.clearAllData();
        TestDataFactory.createStandardPersonsAndProducts();
    }

    @Test
    void countSimplePredicate() {
        long count = Person.where((Person p) -> p.age > 25).count();

        assertThat(count).isGreaterThan(0);
    }

    @Test
    void countComplexPredicate() {
        long count = Person.where((Person p) -> p.age > 25 && p.active).count();

        assertThat(count).isGreaterThan(0);
    }

    @Test
    void countWithNestedExpression() {
        long count = Person.where((Person p) ->
                (p.age >= 28 && p.age <= 35) || p.salary > 85000
        ).count();

        assertThat(count).isGreaterThan(0);
    }

    @Test
    void productCountByCategory() {
        long count = Product.where((Product p) -> p.category.equals("Electronics")).count();

        // Electronics: Laptop, Smartphone, Monitor
        assertThat(count).isEqualTo(3);
    }

    @Test
    void productCountAvailable() {
        long count = Product.where((Product p) -> p.available && p.stockQuantity > 0).count();

        // Available with stock: Laptop, Smartphone, Chair, Monitor
        assertThat(count).isEqualTo(4);
    }

    @Test
    void productCountByPriceRange() {
        long count = Product.where((Product p) ->
                p.price.compareTo(new BigDecimal("300")) > 0
        ).count();

        assertThat(count).isGreaterThan(0);
    }
}
