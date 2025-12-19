package io.quarkiverse.qubit.it.query;

import io.quarkiverse.qubit.it.Person;
import io.quarkiverse.qubit.it.Product;
import io.quarkiverse.qubit.it.testdata.TestDataFactory;
import io.quarkiverse.qubit.it.testutil.PersonQueryOperations;
import io.quarkiverse.qubit.it.testutil.ProductQueryOperations;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Abstract base class for exists query operation tests.
 */
public abstract class AbstractExistsQueryTest {

    protected abstract PersonQueryOperations personOps();
    protected abstract ProductQueryOperations productOps();

    @BeforeEach
    @Transactional
    protected void setupTestData() {
        TestDataFactory.clearAllData();
        TestDataFactory.createStandardPersonsAndProducts();
    }

    @Test
    void existsTrue() {
        boolean exists = personOps().where((Person p) -> p.firstName.equals("John")).exists();

        assertThat(exists).isTrue();
    }

    @Test
    void existsFalse() {
        boolean exists = personOps().where((Person p) -> p.firstName.equals("NonExistent")).exists();

        assertThat(exists).isFalse();
    }

    @Test
    void existsWithAnd() {
        boolean exists = personOps().where((Person p) ->
                p.firstName.equals("Bob") && !p.active
        ).exists();

        assertThat(exists).isTrue();
    }

    @Test
    void existsWithComplexExpression() {
        boolean exists = personOps().where((Person p) ->
                p.active && p.salary > 85000.0 && p.height != null &&
                p.height > 1.60f && p.email.contains("@example.com")
        ).exists();

        assertThat(exists).isTrue();
    }

    @Test
    void productExistsTrue() {
        boolean exists = productOps().where((Product p) -> p.name.equals("Laptop")).exists();

        assertThat(exists).isTrue();
    }

    @Test
    void productExistsFalse() {
        boolean exists = productOps().where((Product p) -> p.name.equals("NonExistent")).exists();

        assertThat(exists).isFalse();
    }

    @Test
    void productExistsWithComplexExpression() {
        boolean exists = productOps().where((Product p) ->
                p.category.equals("Electronics") &&
                p.price.compareTo(new BigDecimal("1000")) > 0 &&
                p.available
        ).exists();

        assertThat(exists).isTrue(); // Laptop is > $1000
    }
}
