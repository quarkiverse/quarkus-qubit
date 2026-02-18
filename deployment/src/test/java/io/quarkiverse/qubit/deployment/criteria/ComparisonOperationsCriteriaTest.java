package io.quarkiverse.qubit.deployment.criteria;

import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import io.quarkiverse.qubit.deployment.ast.LambdaExpression;

/**
 * Criteria query generation tests for comparison operations (>, <, >=, <=, !=).
 *
 * <p>
 * These tests verify that lambda expressions with comparison operations
 * are correctly transformed into JPA Criteria API predicate building code.
 *
 * <p>
 * Test Pattern:
 *
 * <pre>
 * Lambda: p -> p.age > 30
 *   ↓
 * AST: BinaryOp[GT, FieldAccess[age], Constant[30]]
 *   ↓
 * Criteria: cb.greaterThan(root.get("age"), 30)
 *   ↓
 * Verify: Generation succeeds without errors
 * </pre>
 *
 * <p>
 * This class uses JUnit 5 parameterized tests to consolidate repetitive
 * test patterns, reducing code duplication while maintaining full coverage.
 */
class ComparisonOperationsCriteriaTest extends CriteriaQueryTestBase {

    /**
     * Test data for all comparison operations (numeric and temporal).
     * Each entry: lambdaMethodName, expectedCriteriaMethod
     */
    static Stream<Arguments> allComparisons() {
        return Stream.of(
                // Integer comparisons
                Arguments.of("integerGreaterThan", "greaterThan"),
                Arguments.of("integerGreaterThanOrEqual", "greaterThanOrEqualTo"),
                Arguments.of("integerLessThan", "lessThan"),
                Arguments.of("integerLessThanOrEqual", "lessThanOrEqualTo"),
                Arguments.of("integerNotEquals", "notEqual"),

                // Long comparisons
                Arguments.of("longGreaterThan", "greaterThan"),
                Arguments.of("longGreaterThanOrEqual", "greaterThanOrEqualTo"),
                Arguments.of("longLessThan", "lessThan"),
                Arguments.of("longLessThanOrEqual", "lessThanOrEqualTo"),
                Arguments.of("longNotEquals", "notEqual"),

                // Float comparisons
                Arguments.of("floatGreaterThan", "greaterThan"),
                Arguments.of("floatGreaterThanOrEqual", "greaterThanOrEqualTo"),
                Arguments.of("floatLessThan", "lessThan"),
                Arguments.of("floatLessThanOrEqual", "lessThanOrEqualTo"),
                Arguments.of("floatNotEquals", "notEqual"),

                // Double comparisons
                Arguments.of("doubleGreaterThan", "greaterThan"),
                Arguments.of("doubleGreaterThanOrEqual", "greaterThanOrEqualTo"),
                Arguments.of("doubleLessThan", "lessThan"),
                Arguments.of("doubleLessThanOrEqual", "lessThanOrEqualTo"),
                Arguments.of("doubleNotEquals", "notEqual"),

                // BigDecimal comparisons
                Arguments.of("bigDecimalGreaterThan", "greaterThan"),
                Arguments.of("bigDecimalGreaterThanOrEqual", "greaterThanOrEqualTo"),
                Arguments.of("bigDecimalLessThan", "lessThan"),
                Arguments.of("bigDecimalLessThanOrEqual", "lessThanOrEqualTo"),
                Arguments.of("bigDecimalNotEquals", "notEqual"),

                // Temporal comparisons (isAfter/isBefore → greaterThan/lessThan)
                Arguments.of("localDateAfter", "greaterThan"),
                Arguments.of("localDateBefore", "lessThan"),
                Arguments.of("localDateTimeAfter", "greaterThan"),
                Arguments.of("localDateTimeBefore", "lessThan"),
                Arguments.of("localTimeAfter", "greaterThan"),
                Arguments.of("localTimeBefore", "lessThan"));
    }

    /**
     * Test data for range queries (compound AND expressions).
     * Each entry: lambdaMethodName
     */
    static Stream<Arguments> rangeQueries() {
        return Stream.of(
                Arguments.of("integerRangeQuery"),
                Arguments.of("longRangeQuery"),
                Arguments.of("floatRangeQuery"),
                Arguments.of("bigDecimalRangeQuery"));
    }

    @ParameterizedTest(name = "{0} → cb.{1}()")
    @MethodSource("allComparisons")
    void comparison(String lambdaMethodName, String expectedCriteriaMethod) {
        LambdaExpression expr = analyzeLambda(lambdaMethodName);
        CriteriaQueryStructure structure = generateCriteriaQuery(expr);

        assertCriteriaGenerationSucceeds(expr);
        assertCriteriaMethodCalled(structure, expectedCriteriaMethod);
    }

    @ParameterizedTest(name = "{0} → cb.between()")
    @MethodSource("rangeQueries")
    void rangeQuery(String lambdaMethodName) {
        LambdaExpression expr = analyzeLambda(lambdaMethodName);
        CriteriaQueryStructure structure = generateCriteriaQuery(expr);

        assertCriteriaGenerationSucceeds(expr);
        assertCriteriaMethodCalled(structure, "between");
    }
}
