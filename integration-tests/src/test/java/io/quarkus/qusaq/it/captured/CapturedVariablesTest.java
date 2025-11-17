package io.quarkus.qusaq.it.captured;

import io.quarkus.qusaq.it.Person;
import io.quarkus.qusaq.it.Product;
import io.quarkus.qusaq.it.testdata.TestDataFactory;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for captured variables in lambda expressions.
 *
 * <p>Captured variables are variables from the enclosing scope that are used
 * inside the lambda expression. For example:
 * <pre>{@code
 * int minAge = 30;
 * List<Person> results = Person.findWhere(p -> p.age > minAge);
 * }</pre>
 *
 * <p>The {@code minAge} variable is "captured" by the lambda and must be
 * extracted at runtime and passed to the generated query executor.
 */
@QuarkusTest
class CapturedVariablesTest {

    @BeforeEach
    @Transactional
    void setupTestData() {
        TestDataFactory.clearAllData();
        TestDataFactory.createStandardPersonsAndProducts();
    }

    // ========== Single Captured Variable Tests ==========

    @Test
    void singleCapturedVariable_int() {
        int minAge = 30;
        var results = Person.findWhere((Person p) -> p.age > minAge);

        assertThat(results)
                .hasSizeGreaterThan(0)
                .allMatch(p -> p.getAge() > minAge);
    }

    @Test
    void singleCapturedVariable_long() {
        long minEmployeeId = 1000003L;
        var results = Person.findWhere((Person p) -> p.employeeId >= minEmployeeId);

        assertThat(results)
                .hasSizeGreaterThan(0)
                .allMatch(p -> p.getEmployeeId() >= minEmployeeId);
    }

    @Test
    void singleCapturedVariable_double() {
        double minSalary = 70000.0;
        var results = Person.findWhere((Person p) -> p.salary > minSalary);

        assertThat(results)
                .hasSizeGreaterThan(0)
                .allMatch(p -> p.getSalary() > minSalary);
    }

    @Test
    void singleCapturedVariable_float() {
        float minHeight = 1.70f;
        var results = Person.findWhere((Person p) -> p.height > minHeight);

        assertThat(results)
                .hasSizeGreaterThan(0)
                .allMatch(p -> p.getHeight() > minHeight);
    }

    @Test
    void singleCapturedVariable_String() {
        String targetName = "Alice";
        var results = Person.findWhere((Person p) -> p.firstName.equals(targetName));

        assertThat(results)
                .hasSizeGreaterThan(0)
                .allMatch(p -> p.getFirstName().equals(targetName));
    }

    @Test
    void singleCapturedVariable_BigDecimal() {
        BigDecimal maxPrice = new BigDecimal("1000");
        var results = Product.findWhere((Product p) -> p.price.compareTo(maxPrice) < 0);

        assertThat(results)
                .hasSizeGreaterThan(0)
                .allMatch(p -> p.getPrice().compareTo(maxPrice) < 0);
    }

    @Test
    void singleCapturedVariable_LocalDate() {
        LocalDate cutoffDate = LocalDate.of(1990, 1, 1);
        var results = Person.findWhere((Person p) -> p.birthDate.isAfter(cutoffDate));

        assertThat(results)
                .hasSizeGreaterThan(0)
                .allMatch(p -> p.getBirthDate().isAfter(cutoffDate));
    }

    @Test
    void singleCapturedVariable_boolean() {
        boolean activeStatus = true;
        var results = Person.findWhere((Person p) -> p.active == activeStatus);

        assertThat(results)
                .hasSizeGreaterThan(0)
                .allMatch(p -> p.isActive() == activeStatus);
    }

    // ========== Multiple Captured Variables Tests ==========

    @Test
    void twoCapturedVariables_range() {
        int minAge = 25;
        int maxAge = 35;
        var results = Person.findWhere((Person p) -> p.age >= minAge && p.age <= maxAge);

        assertThat(results)
                .hasSizeGreaterThan(0)
                .allMatch(p -> p.getAge() >= minAge && p.getAge() <= maxAge);
    }

    @Test
    void twoCapturedVariables_differentTypes() {
        int minAge = 30;
        double minSalary = 70000.0;
        var results = Person.findWhere((Person p) -> p.age > minAge && p.salary > minSalary);

        assertThat(results)
                .hasSizeGreaterThan(0)
                .allMatch(p -> p.getAge() > minAge && p.getSalary() > minSalary);
    }

    @Test
    void threeCapturedVariables() {
        int minAge = 25;
        double minSalary = 60000.0;
        String lastName = "Smith";
        var results = Person.findWhere((Person p) ->
            p.age >= minAge && p.salary >= minSalary && p.lastName.equals(lastName)
        );

        assertThat(results)
                .hasSizeGreaterThan(0)
                .allMatch(p -> p.getAge() >= minAge &&
                              p.getSalary() >= minSalary &&
                              p.getLastName().equals(lastName));
    }

    // ========== Arithmetic Operations with Captured Variables ==========

    @Test
    void capturedVariable_inArithmeticOperation_addition() {
        int baseAge = 20;
        var results = Person.findWhere((Person p) -> p.age > baseAge + 10);

        assertThat(results)
                .hasSizeGreaterThan(0)
                .allMatch(p -> p.getAge() > baseAge + 10);
    }

    @Test
    void capturedVariable_inArithmeticOperation_multiplication() {
        double multiplier = 2.0;
        var results = Person.findWhere((Person p) -> p.salary > 35000.0 * multiplier);

        assertThat(results)
                .hasSizeGreaterThan(0)
                .allMatch(p -> p.getSalary() > 35000.0 * multiplier);
    }

    @Test
    void capturedVariable_BigDecimal_arithmetic() {
        BigDecimal adjustment = new BigDecimal("100");
        var results = Product.findWhere((Product p) ->
            p.price.subtract(adjustment).compareTo(new BigDecimal("700")) > 0
        );

        assertThat(results)
                .hasSizeGreaterThan(0)
                .allMatch(p -> p.getPrice().subtract(adjustment).compareTo(new BigDecimal("700")) > 0);
    }

    // ========== String Operations with Captured Variables ==========

    @Test
    void capturedVariable_stringContains() {
        String searchTerm = "ice";
        var results = Person.findWhere((Person p) -> p.firstName.contains(searchTerm));

        assertThat(results)
                .hasSizeGreaterThan(0)
                .allMatch(p -> p.getFirstName().contains(searchTerm));
    }

    @Test
    void capturedVariable_stringStartsWith() {
        String prefix = "A";
        var results = Person.findWhere((Person p) -> p.firstName.startsWith(prefix));

        assertThat(results)
                .hasSizeGreaterThan(0)
                .allMatch(p -> p.getFirstName().startsWith(prefix));
    }

    @Test
    void capturedVariable_stringEndsWith() {
        String suffix = "e";
        var results = Person.findWhere((Person p) -> p.firstName.endsWith(suffix));

        assertThat(results)
                .hasSizeGreaterThan(0)
                .allMatch(p -> p.getFirstName().endsWith(suffix));
    }

    // ========== Complex Expressions with Captured Variables ==========

    @Test
    void complexExpression_orWithCapturedVariables() {
        int youngAge = 26;
        int oldAge = 40;
        var results = Person.findWhere((Person p) -> p.age < youngAge || p.age > oldAge);

        assertThat(results)
                .hasSizeGreaterThan(0)
                .allMatch(p -> p.getAge() < youngAge || p.getAge() > oldAge);
    }

    @Test
    void complexExpression_notWithCapturedVariable() {
        String excludeEmail = "john@example.com";
        var results = Person.findWhere((Person p) -> !p.email.equals(excludeEmail));

        assertThat(results)
                .hasSizeGreaterThan(0)
                .allMatch(p -> !p.getEmail().equals(excludeEmail));
    }

    @Test
    void complexExpression_nestedLogic() {
        int minAge = 30;
        double minSalary = 70000.0;
        String firstName1 = "Alice";
        String firstName2 = "Bob";

        var results = Person.findWhere((Person p) ->
            (p.age >= minAge && p.salary >= minSalary) ||
            (p.firstName.equals(firstName1) || p.firstName.equals(firstName2))
        );

        assertThat(results).hasSizeGreaterThan(0);

        for (var person : results) {
            boolean condition1 = person.getAge() >= minAge && person.getSalary() >= minSalary;
            boolean condition2 = person.getFirstName().equals(firstName1) || person.getFirstName().equals(firstName2);
            assertThat(condition1 || condition2).isTrue();
        }
    }

    // ========== Count Queries with Captured Variables ==========

    @Test
    void countQuery_withCapturedVariable() {
        int minAge = 30;
        long count = Person.countWhere((Person p) -> p.age > minAge);

        assertThat(count).isGreaterThan(0);

        // Verify with findWhere
        var results = Person.findWhere((Person p) -> p.age > minAge);
        assertThat(count).isEqualTo(results.size());
    }

    @Test
    void countQuery_withMultipleCapturedVariables() {
        int minAge = 25;
        int maxAge = 35;
        long count = Person.countWhere((Person p) -> p.age >= minAge && p.age <= maxAge);

        assertThat(count).isGreaterThan(0);

        var results = Person.findWhere((Person p) -> p.age >= minAge && p.age <= maxAge);
        assertThat(count).isEqualTo(results.size());
    }

    // ========== Exists Queries with Captured Variables ==========

    @Test
    void existsQuery_withCapturedVariable_true() {
        int minAge = 30;
        boolean exists = Person.exists((Person p) -> p.age > minAge);

        assertThat(exists).isTrue();
    }

    @Test
    void existsQuery_withCapturedVariable_false() {
        int impossibleAge = 200;
        boolean exists = Person.exists((Person p) -> p.age > impossibleAge);

        assertThat(exists).isFalse();
    }

    // ========== Edge Cases ==========

    @Test
    void capturedVariable_usedMultipleTimes() {
        int threshold = 30;
        var results = Person.findWhere((Person p) ->
            p.age > threshold && p.age < threshold + 20
        );

        assertThat(results)
                .hasSizeGreaterThan(0)
                .allMatch(p -> p.getAge() > threshold && p.getAge() < threshold + 20);
    }

    @Test
    void capturedVariable_nullComparison() {
        Double nullValue = null;
        var results = Person.findWhere((Person p) -> p.salary == nullValue);

        // This test expects to find persons with null salary
        // If no such persons exist in test data, the result may be empty
        // So we don't assert hasSizeGreaterThan(0), just verify the query works
        assertThat(results).isNotNull();
        assertThat(results).allMatch(p -> p.getSalary() == null);
    }
}
