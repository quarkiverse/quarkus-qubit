package io.quarkiverse.qubit.it.datatypes;

import io.quarkiverse.qubit.it.Product;
import io.quarkiverse.qubit.it.testdata.TestDataFactory;
import io.quarkiverse.qubit.it.testutil.ProductQueryOperations;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Abstract base class for BigDecimal operation tests.
 *
 * <p>
 * Contains all test methods that can be run with either static entity methods
 * or repository instance methods.
 */
public abstract class AbstractBigDecimalTest {

    protected abstract ProductQueryOperations productOps();

    @BeforeEach
    @Transactional
    protected void setupTestData() {
        TestDataFactory.clearAllData();
        TestDataFactory.createStandardProducts();
    }

    // BigDecimal arithmetic operations
    @Test
    void bigDecimalAdd() {
        var results = productOps()
                .where((Product p) -> p.price.add(new BigDecimal("100")).compareTo(new BigDecimal("1000")) > 0).toList();

        assertThat(results)
                .hasSizeGreaterThan(0)
                .allMatch(p -> p.price.add(new BigDecimal("100")).compareTo(new BigDecimal("1000")) > 0);
    }

    @Test
    void bigDecimalSubtract() {
        var results = productOps()
                .where((Product p) -> p.price.subtract(new BigDecimal("50")).compareTo(new BigDecimal("100")) < 0).toList();

        assertThat(results)
                .hasSizeGreaterThan(0)
                .allMatch(p -> p.price.subtract(new BigDecimal("50")).compareTo(new BigDecimal("100")) < 0);
    }

    @Test
    void bigDecimalMultiply() {
        var results = productOps()
                .where((Product p) -> p.price.multiply(new BigDecimal("2")).compareTo(new BigDecimal("1000")) > 0).toList();

        assertThat(results)
                .hasSizeGreaterThan(0)
                .allMatch(p -> p.price.multiply(new BigDecimal("2")).compareTo(new BigDecimal("1000")) > 0);
    }

    @Test
    void bigDecimalDivide() {
        var results = productOps()
                .where((Product p) -> p.price.divide(new BigDecimal("2")).compareTo(new BigDecimal("400")) > 0).toList();

        assertThat(results)
                .hasSizeGreaterThan(0)
                .allMatch(p -> p.price.divide(new BigDecimal("2")).compareTo(new BigDecimal("400")) > 0);
    }

    // BigDecimal in complex expressions
    @Test
    void bigDecimalWithAvailability() {
        var results = productOps().where((Product p) -> p.price.compareTo(new BigDecimal("1000.00")) > 0 && p.available)
                .toList();

        assertThat(results)
                .hasSizeGreaterThan(0)
                .allMatch(p -> p.getPrice().compareTo(new BigDecimal("1000.00")) > 0 &&
                        p.isAvailable());
    }

    @Test
    void bigDecimalMixedTypes() {
        var results = productOps().where((Product p) -> p.category.equals("Electronics") &&
                p.price.compareTo(new BigDecimal("800.00")) >= 0 &&
                p.stockQuantity > 0 &&
                p.rating > 4.0).toList();

        assertThat(results)
                .hasSizeGreaterThan(0)
                .allMatch(p -> p.getCategory().equals("Electronics") &&
                        p.getPrice().compareTo(new BigDecimal("800.00")) >= 0 &&
                        p.getStockQuantity() > 0 &&
                        p.getRating() > 4.0);
    }
}
