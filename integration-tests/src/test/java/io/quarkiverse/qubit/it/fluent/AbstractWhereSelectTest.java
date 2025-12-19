package io.quarkiverse.qubit.it.fluent;

import io.quarkiverse.qubit.it.Person;
import io.quarkiverse.qubit.it.Product;
import io.quarkiverse.qubit.it.testdata.TestDataFactory;
import io.quarkiverse.qubit.it.testutil.PersonQueryOperations;
import io.quarkiverse.qubit.it.testutil.ProductQueryOperations;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Abstract base class for combined where() + select() query tests.
 *
 * <p>Contains all test methods that can be run with either static entity methods
 * or repository instance methods.
 *
 * <p>Example: {@code Person.where(p -> p.age > 25).select(p -> p.firstName).toList()}
 */
public abstract class AbstractWhereSelectTest {

    protected abstract PersonQueryOperations personOps();
    protected abstract ProductQueryOperations productOps();

    @BeforeEach
    @Transactional
    protected void setupTestData() {
        TestDataFactory.clearAllData();
        TestDataFactory.createStandardPersonsAndProducts();
    }

    // ========== String Field Projection with WHERE ==========

    @Test
    void whereAge_selectFirstName() {
        List<String> names = personOps().where((Person p) -> p.age > 25)
                                   .select((Person p) -> p.firstName)
                                   .toList();

        // age > 25 → John(30), Bob(45), Alice(35), Charlie(28)
        assertThat(names)
                .hasSize(4)
                .containsExactlyInAnyOrder("John", "Bob", "Alice", "Charlie");
    }

    @Test
    void whereActive_selectEmail() {
        List<String> emails = personOps().where((Person p) -> p.active)
                                    .select((Person p) -> p.email)
                                    .toList();

        // active=true → John, Jane, Alice, Charlie (Bob is inactive)
        assertThat(emails)
                .hasSize(4)
                .containsExactlyInAnyOrder(
                        "john.doe@example.com",
                        "jane.smith@example.com",
                        "alice.williams@example.com",
                        "charlie.brown@example.com");
    }

    @Test
    void whereSalary_selectLastName() {
        List<String> lastNames = personOps().where((Person p) -> p.salary > 70000.0)
                                       .select((Person p) -> p.lastName)
                                       .toList();

        // salary > $70K → John($75K), Bob($85K), Alice($90K)
        assertThat(lastNames)
                .hasSize(3)
                .containsExactlyInAnyOrder("Doe", "Johnson", "Williams");
    }

    // ========== Integer Field Projection with WHERE ==========

    @Test
    void whereActive_selectAge() {
        List<Integer> ages = personOps().where((Person p) -> p.active)
                                   .select((Person p) -> p.age)
                                   .toList();

        // active=true → John(30), Jane(25), Alice(35), Charlie(28)
        assertThat(ages)
                .hasSize(4)
                .containsExactlyInAnyOrder(30, 25, 35, 28);
    }

    // ========== Long Field Projection with WHERE ==========

    @Test
    void whereAge_selectEmployeeId() {
        List<Long> employeeIds = personOps().where((Person p) -> p.age >= 30)
                                       .select((Person p) -> p.employeeId)
                                       .toList();

        // age >= 30 → John(1000001), Bob(1000003), Alice(1000004)
        assertThat(employeeIds)
                .hasSize(3)
                .containsExactlyInAnyOrder(1000001L, 1000003L, 1000004L);
    }

    // ========== Double Field Projection with WHERE ==========

    @Test
    void whereActive_selectSalary() {
        List<Double> salaries = personOps().where((Person p) -> p.active)
                                      .select((Person p) -> p.salary)
                                      .toList();

        // active=true → John($75K), Jane($65K), Alice($90K), Charlie($55K)
        assertThat(salaries)
                .hasSize(4)
                .containsExactlyInAnyOrder(75000.0, 65000.0, 90000.0, 55000.0);
    }

    // ========== Float Field Projection with WHERE ==========

    @Test
    void whereAge_selectHeight() {
        List<Float> heights = personOps().where((Person p) -> p.age < 30)
                                    .select((Person p) -> p.height)
                                    .toList();

        // age < 30 → Jane(1.68f), Charlie(1.78f)
        assertThat(heights)
                .hasSize(2)
                .containsExactlyInAnyOrder(1.68f, 1.78f);
    }

    // ========== Boolean Field Projection with WHERE ==========

    @Test
    void whereAge_selectActive() {
        List<Boolean> activeStatuses = personOps().where((Person p) -> p.age > 40)
                                             .select((Person p) -> p.active)
                                             .toList();

        // age > 40 → Bob(false)
        assertThat(activeStatuses)
                .hasSize(1)
                .containsExactly(false);
    }

    // ========== Complex WHERE Predicates with Projection ==========

    @Test
    void complexWhere_selectFirstName() {
        List<String> names = personOps().where((Person p) -> p.age > 25 && p.active && p.salary > 60000.0)
                                   .select((Person p) -> p.firstName)
                                   .toList();

        // age > 25 && active && salary > $60K → John($75K), Alice($90K)
        assertThat(names)
                .hasSize(2)
                .containsExactlyInAnyOrder("John", "Alice");
    }

    @Test
    void whereOr_selectEmail() {
        List<String> emails = personOps().where((Person p) -> p.age < 26 || p.age > 40)
                                    .select((Person p) -> p.email)
                                    .toList();

        // age < 26 OR age > 40 → Jane(25), Bob(45)
        assertThat(emails)
                .hasSize(2)
                .containsExactlyInAnyOrder("jane.smith@example.com", "bob.johnson@example.com");
    }

    @Test
    void whereStringContains_selectAge() {
        List<Integer> ages = personOps().where((Person p) -> p.email.contains("@example.com"))
                                   .select((Person p) -> p.age)
                                   .toList();

        // All test persons have @example.com emails
        assertThat(ages)
                .hasSize(5)
                .containsExactlyInAnyOrder(30, 25, 45, 35, 28);
    }

    // ========== Product Entity Tests ==========

    @Test
    void whereProductAvailable_selectName() {
        List<String> productNames = productOps().where((Product p) -> p.available)
                                           .select((Product p) -> p.name)
                                           .toList();

        // available=true → Laptop, Smartphone, Desk Chair, Monitor (Coffee Maker is unavailable)
        assertThat(productNames)
                .hasSize(4)
                .containsExactlyInAnyOrder("Laptop", "Smartphone", "Desk Chair", "Monitor");
    }

    @Test
    void whereProductPrice_selectStockQuantity() {
        List<Integer> stockQuantities = productOps().where((Product p) -> p.price.compareTo(new BigDecimal("500.00")) > 0)
                                               .select((Product p) -> p.stockQuantity)
                                               .toList();

        // price > $500 → Laptop(50), Smartphone(100)
        assertThat(stockQuantities)
                .hasSize(2)
                .containsExactlyInAnyOrder(50, 100);
    }

    @Test
    void whereProductCategory_selectPrice() {
        List<BigDecimal> prices = productOps().where((Product p) -> p.category.equals("Electronics"))
                                         .select((Product p) -> p.price)
                                         .toList();

        // category=Electronics → Laptop($1299.99), Smartphone($899.99), Monitor($399.99)
        assertThat(prices)
                .hasSize(3)
                .containsExactlyInAnyOrder(
                        new BigDecimal("1299.99"),
                        new BigDecimal("899.99"),
                        new BigDecimal("399.99"));
    }

    // ========== Edge Cases ==========

    @Test
    void whereNoMatches_selectField_returnsEmptyList() {
        List<String> names = personOps().where((Person p) -> p.age > 100)
                                   .select((Person p) -> p.firstName)
                                   .toList();

        assertThat(names).isEmpty();
    }

    @Test
    void whereAllMatch_selectField_returnsAllProjections() {
        List<Integer> ages = personOps().where((Person p) -> p.age > 0)
                                   .select((Person p) -> p.age)
                                   .toList();

        // All persons have age > 0
        assertThat(ages)
                .hasSize(5)
                .containsExactlyInAnyOrder(30, 25, 45, 35, 28);
    }
}
