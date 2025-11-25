package io.quarkus.qusaq.it.query;

import io.quarkus.qusaq.it.Person;
import io.quarkus.qusaq.it.Product;
import io.quarkus.qusaq.it.testdata.TestDataFactory;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for exists query operations.
 */
@QuarkusTest
class ExistsQueryTest {

    @BeforeEach
    @Transactional
    void setupTestData() {
        TestDataFactory.clearAllData();
        TestDataFactory.createStandardPersonsAndProducts();
    }

    @Test
    void existsTrue() {
        boolean exists = Person.where((Person p) -> p.firstName.equals("John")).exists();

        assertThat(exists).isTrue();
    }

    @Test
    void existsFalse() {
        boolean exists = Person.where((Person p) -> p.firstName.equals("NonExistent")).exists();

        assertThat(exists).isFalse();
    }

    @Test
    void existsWithAnd() {
        boolean exists = Person.where((Person p) ->
                p.firstName.equals("Bob") && !p.active
        ).exists();

        assertThat(exists).isTrue();
    }

    @Test
    void existsWithComplexExpression() {
        boolean exists = Person.where((Person p) ->
                p.active && p.salary > 85000.0 && p.height != null &&
                p.height > 1.60f && p.email.contains("@example.com")
        ).exists();

        assertThat(exists).isTrue();
    }

    @Test
    void productExistsTrue() {
        boolean exists = Product.where((Product p) -> p.name.equals("Laptop")).exists();

        assertThat(exists).isTrue();
    }

    @Test
    void productExistsFalse() {
        boolean exists = Product.where((Product p) -> p.name.equals("NonExistent")).exists();

        assertThat(exists).isFalse();
    }

    @Test
    void productExistsWithComplexExpression() {
        boolean exists = Product.where((Product p) ->
                p.category.equals("Electronics") &&
                p.price.compareTo(new BigDecimal("1000")) > 0 &&
                p.available
        ).exists();

        assertThat(exists).isTrue(); // Laptop is > $1000
    }
}
