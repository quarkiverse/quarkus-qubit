package io.quarkiverse.qubit.it.logical;

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
 * Abstract base class for complex nested logical expression tests.
 *
 * <p>
 * Contains all test methods that can be run with either static entity methods
 * or repository instance methods.
 */
public abstract class AbstractComplexExpressionsTest {

    protected abstract PersonQueryOperations personOps();

    protected abstract ProductQueryOperations productOps();

    @BeforeEach
    @Transactional
    protected void setupTestData() {
        TestDataFactory.clearAllData();
        TestDataFactory.createStandardPersonsAndProducts();
    }

    @Test
    void nestedAndOrExpression() {
        var results = personOps().where((Person p) -> (p.age > 25 && p.age < 35) || p.salary > 80000).toList();

        assertThat(results)
                .hasSizeGreaterThan(0)
                .allMatch(p -> (p.getAge() > 25 && p.getAge() < 35) ||
                        p.getSalary() > 80000);
    }

    @Test
    void andWithNestedOr() {
        var results = personOps().where((Person p) -> p.active && (p.age < 30 || p.salary > 80000)).toList();

        assertThat(results)
                .hasSizeGreaterThan(0)
                .allMatch(p -> p.isActive() &&
                        (p.getAge() < 30 || p.getSalary() > 80000));
    }

    @Test
    void complexNestedOrAnd() {
        var results = personOps().where((Person p) -> (p.age < 30 || p.age > 40) && (p.active || p.salary > 70000)).toList();

        assertThat(results)
                .hasSizeGreaterThan(0)
                .allMatch(p -> (p.getAge() < 30 || p.getAge() > 40) &&
                        (p.isActive() || p.getSalary() > 70000));
    }

    @Test
    void tripleAndWithOr() {
        var results = personOps().where((Person p) -> (p.age >= 25 && p.age <= 30 && p.active) || p.salary > 88000).toList();

        assertThat(results)
                .hasSizeGreaterThan(0)
                .allMatch(p -> (p.getAge() >= 25 && p.getAge() <= 30 && p.isActive()) ||
                        p.getSalary() > 88000);
    }

    @Test
    void deeplyNestedMultipleOrGroups() {
        var results = personOps().where((Person p) -> ((p.age > 25 && p.age < 40) || p.salary > 85000) &&
                (p.active || p.firstName.startsWith("B"))).toList();

        assertThat(results)
                .hasSizeGreaterThan(0)
                .allMatch(p -> {
                    boolean leftSide = (p.getAge() > 25 && p.getAge() < 40) || p.getSalary() > 85000;
                    boolean rightSide = p.isActive() || p.getFirstName().startsWith("B");
                    return leftSide && rightSide;
                });
    }

    @Test
    void arithmeticInOrGroups() {
        var results = personOps().where((Person p) -> (p.age + 10 > 40) || (p.age * 2 < 60)).toList();

        assertThat(results)
                .hasSizeGreaterThan(0)
                .allMatch(p -> (p.getAge() + 10 > 40) || (p.getAge() * 2 < 60));
    }

    @Test
    void complexArithmeticInOr() {
        var results = personOps().where((Person p) -> (p.age * 2 - 10 > 50) || (p.age + 15 < 50)).toList();

        assertThat(results)
                .hasSizeGreaterThan(0)
                .allMatch(p -> (p.getAge() * 2 - 10 > 50) || (p.getAge() + 15 < 50));
    }

    @Test
    void complexNestedConditions() {
        var results = personOps().where((Person p) -> (p.firstName.equals("John") || p.firstName.equals("Jane")) &&
                p.age >= 25 && p.active).toList();

        assertThat(results).hasSizeGreaterThan(0);
    }

    @Test
    void complexLogicalExpression() {
        var results = personOps().where((Person p) -> (p.age > 25 && p.age < 35) || p.salary > 80000).toList();

        assertThat(results).hasSizeGreaterThan(0);
    }

    @Test
    void productNestedAndOrExpression() {
        var results = productOps()
                .where((Product p) -> (p.category.equals("Electronics") && p.rating > 4.5) || p.stockQuantity > 75).toList();

        assertThat(results)
                .hasSizeGreaterThan(0)
                .allMatch(prod -> (prod.getCategory().equals("Electronics") && prod.getRating() > 4.5) ||
                        prod.getStockQuantity() > 75);
    }

    @Test
    void productComplexMixedTypes() {
        var results = productOps().where((Product p) -> p.category.equals("Electronics") &&
                p.price.compareTo(new BigDecimal("800.00")) >= 0 &&
                p.stockQuantity > 0 &&
                p.rating > 4.0).toList();

        assertThat(results)
                .hasSizeGreaterThan(0)
                .allMatch(prod -> prod.getCategory().equals("Electronics") &&
                        prod.getPrice().compareTo(new BigDecimal("800.00")) >= 0 &&
                        prod.getStockQuantity() > 0 &&
                        prod.getRating() > 4.0);
    }
}
