package io.quarkus.qusaq.it.entity;

import io.quarkus.qusaq.it.Product;
import io.quarkus.qusaq.it.testdata.TestDataFactory;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Product entity-specific query tests.
 */
@QuarkusTest
class ProductQueryTest {

    @BeforeEach
    @Transactional
    void setupTestData() {
        TestDataFactory.clearAllData();
        TestDataFactory.createStandardProducts();
    }

    @Test
    void productByCategory() {
        var results = Product.findWhere((Product p) -> p.category.equals("Electronics"));

        assertThat(results)
                .hasSizeGreaterThan(0)
                .allMatch(p -> p.getCategory().equals("Electronics"));
    }

    @Test
    void productAvailability() {
        var results = Product.findWhere((Product p) -> p.available && p.stockQuantity > 0);

        assertThat(results)
                .hasSizeGreaterThan(0)
                .allMatch(p -> p.isAvailable() && p.getStockQuantity() > 0);
    }

    @Test
    void productPriceRange() {
        var results = Product.findWhere((Product p) -> p.price.compareTo(new BigDecimal("300")) > 0);

        assertThat(results)
                .hasSizeGreaterThan(0)
                .allMatch(p -> p.getPrice().compareTo(new BigDecimal("300")) > 0);
    }

    @Test
    void productRating() {
        var results = Product.findWhere((Product p) -> p.rating >= 4.5);

        assertThat(results)
                .hasSizeGreaterThan(0)
                .allMatch(p -> p.getRating() >= 4.5);
    }

    @Test
    void productComplexMixedTypes() {
        var results = Product.findWhere((Product p) ->
                p.category.equals("Electronics") &&
                p.price.compareTo(new BigDecimal("800.00")) >= 0 &&
                p.stockQuantity > 0 &&
                p.rating > 4.0
        );

        assertThat(results)
                .hasSizeGreaterThan(0)
                .allMatch(p -> p.getCategory().equals("Electronics") &&
                              p.getPrice().compareTo(new BigDecimal("800.00")) >= 0 &&
                              p.getStockQuantity() > 0 &&
                              p.getRating() > 4.0);
    }
}
