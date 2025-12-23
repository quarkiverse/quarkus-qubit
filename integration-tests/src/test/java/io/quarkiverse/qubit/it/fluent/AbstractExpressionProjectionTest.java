package io.quarkiverse.qubit.it.fluent;

import io.quarkiverse.qubit.it.Person;
import io.quarkiverse.qubit.it.Product;
import io.quarkiverse.qubit.it.testdata.TestDataFactory;
import io.quarkiverse.qubit.it.testutil.PersonQueryOperations;
import io.quarkiverse.qubit.it.testutil.ProductQueryOperations;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Abstract base class for expression projection tests.
 *
 * <p>Validates projection of computed expressions including arithmetic operations
 * and string concatenation.
 *
 * <p>Examples:
 * <ul>
 * <li>Arithmetic: {@code Person.select(p -> p.salary * 1.1).toList()}</li>
 * <li>String concat: {@code Person.select(p -> p.firstName + " " + p.lastName).toList()}</li>
 * <li>Combined: {@code Person.where(p -> p.active).select(p -> p.salary * 1.1).toList()}</li>
 * </ul>
 */
public abstract class AbstractExpressionProjectionTest {

    protected abstract PersonQueryOperations personOps();
    protected abstract ProductQueryOperations productOps();

    @BeforeEach
    @Transactional
    protected void setupTestData() {
        TestDataFactory.clearAllData();
        TestDataFactory.createStandardPersonsAndProducts();
    }

    // ========== Arithmetic Operations - Addition ==========

    @Test
    void selectArithmetic_integerAddition() {
        var agesPlus5 = personOps().select((Person p) -> p.age + 5).toList();

        // Original ages: 30, 25, 45, 35, 28
        // Expected: 35, 30, 50, 40, 33
        assertThat(agesPlus5)
                .hasSize(5)
                .containsExactlyInAnyOrder(35, 30, 50, 40, 33);
    }

    @Test
    void selectArithmetic_doubleAddition() {
        var salaryIncrease = personOps().select((Person p) -> p.salary + 5000.0).toList();

        // Original salaries: $75K, $65K, $85K, $90K, $55K
        // Expected: $80K, $70K, $90K, $95K, $60K
        assertThat(salaryIncrease)
                .hasSize(5)
                .containsExactlyInAnyOrder(80000.0, 70000.0, 90000.0, 95000.0, 60000.0);
    }

    // ========== Arithmetic Operations - Subtraction ==========

    @Test
    void selectArithmetic_integerSubtraction() {
        var agesMinus2 = personOps().select((Person p) -> p.age - 2).toList();

        // Original ages: 30, 25, 45, 35, 28
        // Expected: 28, 23, 43, 33, 26
        assertThat(agesMinus2)
                .hasSize(5)
                .containsExactlyInAnyOrder(28, 23, 43, 33, 26);
    }

    @Test
    void selectArithmetic_doubleSubtraction() {
        var salaryDecrease = personOps().select((Person p) -> p.salary - 10000.0).toList();

        // Original salaries: $75K, $65K, $85K, $90K, $55K
        // Expected: $65K, $55K, $75K, $80K, $45K
        assertThat(salaryDecrease)
                .hasSize(5)
                .containsExactlyInAnyOrder(65000.0, 55000.0, 75000.0, 80000.0, 45000.0);
    }

    // ========== Arithmetic Operations - Multiplication ==========

    @Test
    void selectArithmetic_integerMultiplication() {
        var agesDoubled = personOps().select((Person p) -> p.age * 2).toList();

        // Original ages: 30, 25, 45, 35, 28
        // Expected: 60, 50, 90, 70, 56
        assertThat(agesDoubled)
                .hasSize(5)
                .containsExactlyInAnyOrder(60, 50, 90, 70, 56);
    }

    @Test
    void selectArithmetic_doubleMultiplication_tenPercentRaise() {
        var salariesRaised = personOps().select((Person p) -> p.salary * 1.1).toList();

        // Original salaries: $75K, $65K, $85K, $90K, $55K
        // Expected (10% raise): $82.5K, $71.5K, $93.5K, $99K, $60.5K
        // Use tolerance for floating-point precision (1.1 cannot be exactly represented in binary)
        assertThat(salariesRaised)
                .hasSize(5)
                .satisfiesExactlyInAnyOrder(
                        s -> assertThat(s).isCloseTo(82500.0, within(0.01)),
                        s -> assertThat(s).isCloseTo(71500.0, within(0.01)),
                        s -> assertThat(s).isCloseTo(93500.0, within(0.01)),
                        s -> assertThat(s).isCloseTo(99000.0, within(0.01)),
                        s -> assertThat(s).isCloseTo(60500.0, within(0.01))
                );
    }

    @Test
    void selectArithmetic_floatMultiplication() {
        var heightsInCm = personOps().select((Person p) -> p.height * 100.0f).toList();

        // Original heights: 1.75m, 1.68m, 1.82m, 1.65m, 1.78m
        // Expected: 175cm, 168cm, 182cm, 165cm, 178cm
        assertThat(heightsInCm)
                .hasSize(5)
                .containsExactlyInAnyOrder(175.0f, 168.0f, 182.0f, 165.0f, 178.0f);
    }

    // ========== Arithmetic Operations - Division ==========

    @Test
    void selectArithmetic_integerDivision() {
        var agesHalved = personOps().select((Person p) -> p.age / 2).toList();

        // Original ages: 30, 25, 45, 35, 28
        // Expected (integer division): 15, 12, 22, 17, 14
        assertThat(agesHalved)
                .hasSize(5)
                .containsExactlyInAnyOrder(15, 12, 22, 17, 14);
    }

    @Test
    void selectArithmetic_doubleDivision() {
        var monthlySalaries = personOps().select((Person p) -> p.salary / 12.0).toList();

        // Original salaries: $75K, $65K, $85K, $90K, $55K
        // Expected monthly: ~$6250, ~$5417, ~$7083, ~$7500, ~$4583
        assertThat(monthlySalaries)
                .hasSize(5)
                .contains(
                        75000.0 / 12.0,
                        65000.0 / 12.0,
                        85000.0 / 12.0,
                        90000.0 / 12.0,
                        55000.0 / 12.0);
    }

    // ========== Arithmetic Operations - Modulo ==========

    @Test
    void selectArithmetic_integerModulo() {
        var agesMod10 = personOps().select((Person p) -> p.age % 10).toList();

        // Original ages: 30, 25, 45, 35, 28
        // Expected (mod 10): 0, 5, 5, 5, 8
        assertThat(agesMod10)
                .hasSize(5)
                .containsExactlyInAnyOrder(0, 5, 5, 5, 8);
    }

    // ========== String Concatenation ==========

    @Test
    void selectStringConcat_fullName() {
        var fullNames = personOps().select((Person p) -> p.firstName + " " + p.lastName).toList();

        assertThat(fullNames)
                .hasSize(5)
                .containsExactlyInAnyOrder(
                        "John Doe",
                        "Jane Smith",
                        "Bob Johnson",
                        "Alice Williams",
                        "Charlie Brown");
    }

    @Test
    void selectStringConcat_emailPrefix() {
        var greetings = personOps().select((Person p) -> "Hello, " + p.firstName).toList();

        assertThat(greetings)
                .hasSize(5)
                .containsExactlyInAnyOrder(
                        "Hello, John",
                        "Hello, Jane",
                        "Hello, Bob",
                        "Hello, Alice",
                        "Hello, Charlie");
    }

    @Test
    void selectStringConcat_multipleStrings() {
        var formattedNames = personOps().select((Person p) ->
                p.lastName + ", " + p.firstName + " (" + p.email + ")"
        ).toList();

        assertThat(formattedNames)
                .hasSize(5)
                .containsExactlyInAnyOrder(
                        "Doe, John (john.doe@example.com)",
                        "Smith, Jane (jane.smith@example.com)",
                        "Johnson, Bob (bob.johnson@example.com)",
                        "Williams, Alice (alice.williams@example.com)",
                        "Brown, Charlie (charlie.brown@example.com)");
    }

    // ========== Combined WHERE + Expression Projection ==========

    @Test
    void whereActive_selectSalaryRaise() {
        var raisedSalaries = personOps().where((Person p) -> p.active)
                                   .select((Person p) -> p.salary * 1.15)
                                   .toList();

        // Active persons: John($75K), Jane($65K), Alice($90K), Charlie($55K)
        // Expected (15% raise): $86250, $74750, $103500, $63250
        // Use tolerance for floating-point precision (1.15 cannot be exactly represented in binary)
        assertThat(raisedSalaries)
                .hasSize(4)
                .satisfiesExactlyInAnyOrder(
                        s -> assertThat(s).isCloseTo(86250.0, within(0.01)),
                        s -> assertThat(s).isCloseTo(74750.0, within(0.01)),
                        s -> assertThat(s).isCloseTo(103500.0, within(0.01)),
                        s -> assertThat(s).isCloseTo(63250.0, within(0.01))
                );
    }

    @Test
    void whereAgeLessThan30_selectFullName() {
        var youngPeopleNames = personOps().where((Person p) -> p.age < 30)
                                     .select((Person p) -> p.firstName + " " + p.lastName)
                                     .toList();

        // age < 30 → Jane(25), Charlie(28)
        assertThat(youngPeopleNames)
                .hasSize(2)
                .containsExactlyInAnyOrder("Jane Smith", "Charlie Brown");
    }

    @Test
    void whereSalaryGreaterThan70K_selectAgeDoubled() {
        var highEarnersAges = personOps().where((Person p) -> p.salary > 70000.0)
                                    .select((Person p) -> p.age * 2)
                                    .toList();

        // salary > $70K → John(30), Bob(45), Alice(35)
        // Expected ages doubled: 60, 90, 70
        assertThat(highEarnersAges)
                .hasSize(3)
                .containsExactlyInAnyOrder(60, 90, 70);
    }

    @Test
    void whereInactive_selectFullNameWithStatus() {
        var inactivePeople = personOps().where((Person p) -> !p.active)
                                   .select((Person p) -> p.firstName + " " + p.lastName + " (INACTIVE)")
                                   .toList();

        // inactive → Bob
        assertThat(inactivePeople)
                .hasSize(1)
                .containsExactly("Bob Johnson (INACTIVE)");
    }

    // ========== Complex Arithmetic Expressions ==========

    @Test
    void selectComplexArithmetic_multipleOperations() {
        // Calculate: (age * 2) + 10
        var complexAges = personOps().select((Person p) -> (p.age * 2) + 10).toList();

        // Original ages: 30, 25, 45, 35, 28
        // Expected: (30*2)+10=70, (25*2)+10=60, (45*2)+10=100, (35*2)+10=80, (28*2)+10=66
        assertThat(complexAges)
                .hasSize(5)
                .containsExactlyInAnyOrder(70, 60, 100, 80, 66);
    }

    @Test
    void selectComplexArithmetic_salaryCalculation() {
        // Calculate annual bonus: (salary * 0.1) + 5000
        var bonuses = personOps().select((Person p) -> (p.salary * 0.1) + 5000.0).toList();

        // Original salaries: $75K, $65K, $85K, $90K, $55K
        // Expected bonuses: $12500, $11500, $13500, $14000, $10500
        assertThat(bonuses)
                .hasSize(5)
                .containsExactlyInAnyOrder(12500.0, 11500.0, 13500.0, 14000.0, 10500.0);
    }

    // ========== Product Entity Tests ==========

    @Test
    void productSelect_priceWithTax() {
        var pricesWithTax = productOps().select((Product p) -> p.stockQuantity * 2).toList();

        // Stock quantities: 50, 100, 25, 0 (Coffee Maker), 30
        // Expected: 100, 200, 50, 0, 60
        assertThat(pricesWithTax)
                .hasSize(5)
                .containsExactlyInAnyOrder(100, 200, 50, 0, 60);
    }

    @Test
    void productWhereAvailable_selectDiscountedStock() {
        var discountedStock = productOps().where((Product p) -> p.available)
                                     .select((Product p) -> p.stockQuantity - 5)
                                     .toList();

        // Available products: Laptop(50), Smartphone(100), Desk Chair(25), Monitor(30)
        // Expected stock after 5-unit reduction: 45, 95, 20, 25
        assertThat(discountedStock)
                .hasSize(4)
                .containsExactlyInAnyOrder(45, 95, 20, 25);
    }

    @Test
    void productSelect_nameWithCategory() {
        var productDescriptions = productOps().select((Product p) ->
                p.name + " [" + p.category + "]"
        ).toList();

        assertThat(productDescriptions)
                .hasSize(5)
                .containsExactlyInAnyOrder(
                        "Laptop [Electronics]",
                        "Smartphone [Electronics]",
                        "Desk Chair [Furniture]",
                        "Coffee Maker [Appliances]",
                        "Monitor [Electronics]");
    }

    // ========== Edge Cases ==========

    @Test
    void whereNoMatches_selectExpression_returnsEmptyList() {
        var results = personOps().where((Person p) -> p.age > 100)
                            .select((Person p) -> p.age * 2)
                            .toList();

        assertThat(results).isEmpty();
    }

    @Test
    void selectExpression_resultCountMatchesEntityCount() {
        long entityCount = personOps().where((Person p) -> p.age > 0).count();
        var expressions = personOps().select((Person p) -> p.age + 10).toList();

        assertThat(expressions).hasSize((int) entityCount);
    }

    @Test
    void selectArithmetic_withZero() {
        var results = personOps().select((Person p) -> p.age * 0).toList();

        // All should be 0
        assertThat(results)
                .hasSize(5)
                .allMatch(obj -> obj.equals(0));
    }

    @Test
    void selectArithmetic_divisionResultsInDecimal() {
        var results = personOps().select((Person p) -> p.age / 3).toList();

        // Integer division: 30/3=10, 25/3=8, 45/3=15, 35/3=11, 28/3=9
        assertThat(results)
                .hasSize(5)
                .containsExactlyInAnyOrder(10, 8, 15, 11, 9);
    }
}
