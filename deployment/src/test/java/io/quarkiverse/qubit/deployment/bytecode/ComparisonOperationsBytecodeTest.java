package io.quarkiverse.qubit.deployment.bytecode;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import io.quarkiverse.qubit.deployment.ast.LambdaExpression;

/**
 * Bytecode analysis tests for comparison operations (>, <, >=, <=, !=).
 * Tests lambda bytecode parsing without executing queries.
 *
 * <p>
 * Mirrors the test patterns from ComparisonTest integration tests,
 * but focuses on verifying correct bytecode analysis and AST generation.
 *
 * <p>
 * Uses pre-compiled lambda sources from {@link LambdaTestSources} for
 * reliable bytecode generation and analysis.
 *
 * <p>
 * This class uses JUnit 5 parameterized tests to consolidate repetitive
 * test patterns, reducing code duplication while maintaining full coverage.
 */
class ComparisonOperationsBytecodeTest extends PrecompiledLambdaAnalyzer {

    // ==================== PARAMETERIZED TEST DATA ====================

    /**
     * Test data for simple binary comparison operations.
     * Each entry: methodName, expectedOperator, expectedFieldName, expectedConstant
     */
    static Stream<Arguments> simpleBinaryComparisons() {
        return Stream.of(
                // Integer comparisons
                Arguments.of("integerGreaterThan", LambdaExpression.BinaryOp.Operator.GT, "age", 30),
                Arguments.of("integerGreaterThanOrEqual", LambdaExpression.BinaryOp.Operator.GE, "age", 30),
                Arguments.of("integerLessThan", LambdaExpression.BinaryOp.Operator.LT, "age", 30),
                Arguments.of("integerLessThanOrEqual", LambdaExpression.BinaryOp.Operator.LE, "age", 30),
                Arguments.of("integerNotEquals", LambdaExpression.BinaryOp.Operator.NE, "age", 30),

                // Long comparisons
                Arguments.of("longGreaterThan", LambdaExpression.BinaryOp.Operator.GT, "employeeId", 1000003L),
                Arguments.of("longGreaterThanOrEqual", LambdaExpression.BinaryOp.Operator.GE, "employeeId", 1000002L),
                Arguments.of("longLessThan", LambdaExpression.BinaryOp.Operator.LT, "employeeId", 1000003L),
                Arguments.of("longLessThanOrEqual", LambdaExpression.BinaryOp.Operator.LE, "employeeId", 1000003L),
                Arguments.of("longNotEquals", LambdaExpression.BinaryOp.Operator.NE, "employeeId", 1000001L),

                // Float comparisons
                Arguments.of("floatGreaterThan", LambdaExpression.BinaryOp.Operator.GT, "height", 1.70f),
                Arguments.of("floatGreaterThanOrEqual", LambdaExpression.BinaryOp.Operator.GE, "height", 1.70f),
                Arguments.of("floatLessThan", LambdaExpression.BinaryOp.Operator.LT, "height", 1.70f),
                Arguments.of("floatLessThanOrEqual", LambdaExpression.BinaryOp.Operator.LE, "height", 1.75f),
                Arguments.of("floatNotEquals", LambdaExpression.BinaryOp.Operator.NE, "height", 1.75f),

                // Double comparisons
                Arguments.of("doubleGreaterThan", LambdaExpression.BinaryOp.Operator.GT, "salary", 70000.0),
                Arguments.of("doubleGreaterThanOrEqual", LambdaExpression.BinaryOp.Operator.GE, "salary", 75000.0),
                Arguments.of("doubleLessThan", LambdaExpression.BinaryOp.Operator.LT, "salary", 80000.0),
                Arguments.of("doubleLessThanOrEqual", LambdaExpression.BinaryOp.Operator.LE, "salary", 75000.0),
                Arguments.of("doubleNotEquals", LambdaExpression.BinaryOp.Operator.NE, "salary", 75000.0));
    }

    /**
     * Test data for BigDecimal comparisons (excluding notEquals which has special handling).
     * Each entry: methodName, expectedOperator, expectedFieldName, expectedConstant
     */
    static Stream<Arguments> bigDecimalComparisons() {
        return Stream.of(
                Arguments.of("bigDecimalGreaterThan", LambdaExpression.BinaryOp.Operator.GT, "price", new BigDecimal("500")),
                Arguments.of("bigDecimalGreaterThanOrEqual", LambdaExpression.BinaryOp.Operator.GE, "price",
                        new BigDecimal("500")),
                Arguments.of("bigDecimalLessThan", LambdaExpression.BinaryOp.Operator.LT, "price", new BigDecimal("1000")),
                Arguments.of("bigDecimalLessThanOrEqual", LambdaExpression.BinaryOp.Operator.LE, "price",
                        new BigDecimal("300")));
    }

    /**
     * Test data for temporal comparisons (isAfter/isBefore).
     * Each entry: methodName, expectedMethodName, expectedFieldName
     */
    static Stream<Arguments> temporalComparisons() {
        return Stream.of(
                Arguments.of("localDateAfter", "isAfter", "birthDate"),
                Arguments.of("localDateBefore", "isBefore", "birthDate"),
                Arguments.of("localDateTimeAfter", "isAfter", "createdAt"),
                Arguments.of("localDateTimeBefore", "isBefore", "createdAt"),
                Arguments.of("localTimeAfter", "isAfter", "startTime"),
                Arguments.of("localTimeBefore", "isBefore", "startTime"));
    }

    /**
     * Test data for range queries (compound AND expressions).
     * Each entry: methodName, leftOperator, rightOperator
     */
    static Stream<Arguments> rangeQueries() {
        return Stream.of(
                Arguments.of("integerRangeQuery", LambdaExpression.BinaryOp.Operator.GE, LambdaExpression.BinaryOp.Operator.LE),
                Arguments.of("longRangeQuery", LambdaExpression.BinaryOp.Operator.GE, LambdaExpression.BinaryOp.Operator.LE),
                Arguments.of("floatRangeQuery", LambdaExpression.BinaryOp.Operator.GE, LambdaExpression.BinaryOp.Operator.LE),
                Arguments.of("bigDecimalRangeQuery", LambdaExpression.BinaryOp.Operator.GE,
                        LambdaExpression.BinaryOp.Operator.LE));
    }

    // ==================== PARAMETERIZED TESTS ====================

    @ParameterizedTest(name = "{0}: field {2} {1} {3}")
    @MethodSource("simpleBinaryComparisons")
    void simpleBinaryComparison(
            String methodName,
            LambdaExpression.BinaryOp.Operator expectedOperator,
            String expectedFieldName,
            Object expectedConstant) {

        LambdaExpression expr = analyzeLambda(methodName);

        assertBinaryOp(expr, expectedOperator);
        LambdaExpression.BinaryOp binOp = (LambdaExpression.BinaryOp) expr;
        assertFieldAccess(binOp.left(), expectedFieldName);
        assertConstant(binOp.right(), expectedConstant);
    }

    @ParameterizedTest(name = "{0}: field {2} {1} {3}")
    @MethodSource("bigDecimalComparisons")
    void bigDecimalComparison(
            String methodName,
            LambdaExpression.BinaryOp.Operator expectedOperator,
            String expectedFieldName,
            BigDecimal expectedConstant) {

        LambdaExpression expr = analyzeLambda(methodName);

        // The analyzer transforms: p.price.compareTo(new BigDecimal("500")) > 0
        // Into the optimized form: p.price > new BigDecimal("500")
        assertBinaryOp(expr, expectedOperator);
        LambdaExpression.BinaryOp binOp = (LambdaExpression.BinaryOp) expr;
        assertFieldAccess(binOp.left(), expectedFieldName);
        assertConstant(binOp.right(), expectedConstant);
    }

    @Test
    void bigDecimalNotEquals() {
        // Special case: compareTo() != 0 is represented as compareTo(...) == true
        // This is how the bytecode analyzer represents "non-zero result"
        LambdaExpression expr = analyzeLambda("bigDecimalNotEquals");

        assertBinaryOp(expr, LambdaExpression.BinaryOp.Operator.EQ);
        LambdaExpression.BinaryOp binOp = (LambdaExpression.BinaryOp) expr;

        // Left side: compareTo method call
        assertMethodCall(binOp.left(), "compareTo");
        LambdaExpression.MethodCall methodCall = (LambdaExpression.MethodCall) binOp.left();
        assertFieldAccess(methodCall.target(), "price");

        // Right side: true (meaning "non-zero", i.e., not equal)
        assertConstant(binOp.right(), true);
    }

    @ParameterizedTest(name = "{0}: {2}.{1}()")
    @MethodSource("temporalComparisons")
    void temporalComparison(
            String methodName,
            String expectedMethodName,
            String expectedFieldName) {

        LambdaExpression expr = analyzeLambda(methodName);

        assertMethodCall(expr, expectedMethodName);
        LambdaExpression.MethodCall methodCall = (LambdaExpression.MethodCall) expr;
        assertFieldAccess(methodCall.target(), expectedFieldName);
        assertThat(methodCall.arguments()).hasSize(1);
    }

    @ParameterizedTest(name = "{0}: left {1}, right {2}")
    @MethodSource("rangeQueries")
    void rangeQuery(
            String methodName,
            LambdaExpression.BinaryOp.Operator expectedLeftOperator,
            LambdaExpression.BinaryOp.Operator expectedRightOperator) {

        LambdaExpression expr = analyzeLambda(methodName);

        // Top level should be AND
        assertBinaryOp(expr, LambdaExpression.BinaryOp.Operator.AND);
        LambdaExpression.BinaryOp andOp = (LambdaExpression.BinaryOp) expr;

        // Left: first comparison (e.g., age >= 25)
        assertBinaryOp(andOp.left(), expectedLeftOperator);

        // Right: second comparison (e.g., age <= 35)
        assertBinaryOp(andOp.right(), expectedRightOperator);
    }

    // ==================== BOXED INTEGER FIELD-TO-FIELD COMPARISONS ====================
    // These tests reproduce the JFR-documented stack underflow bug where
    // comparing two boxed Integer fields triggers Integer.intValue() unboxing.
    // Bytecode pattern:
    //   aload_0, getfield age:Integer, invokevirtual intValue,
    //   aload_0, getfield minAge:Integer, invokevirtual intValue, if_icmpgt

    /**
     * Test data for boxed Integer field-to-field comparisons.
     * Each entry: methodName, expectedOperator
     */
    static Stream<Arguments> boxedIntegerFieldComparisons() {
        return Stream.of(
                Arguments.of("boxedIntegerFieldLessThanOrEqual", LambdaExpression.BinaryOp.Operator.LE),
                Arguments.of("boxedIntegerFieldGreaterThan", LambdaExpression.BinaryOp.Operator.GT),
                Arguments.of("boxedIntegerFieldGreaterThanOrEqual", LambdaExpression.BinaryOp.Operator.GE),
                Arguments.of("boxedIntegerFieldLessThan", LambdaExpression.BinaryOp.Operator.LT),
                Arguments.of("boxedIntegerFieldEquals", LambdaExpression.BinaryOp.Operator.EQ),
                Arguments.of("boxedIntegerFieldNotEquals", LambdaExpression.BinaryOp.Operator.NE));
    }

    @ParameterizedTest(name = "{0}: age {1} minAge")
    @MethodSource("boxedIntegerFieldComparisons")
    void boxedIntegerFieldComparison(
            String methodName,
            LambdaExpression.BinaryOp.Operator expectedOperator) {

        // This test reproduces the stack underflow bug documented in jfr-analysis-2026-02-04.md:
        // "Stack underflow processing IF_ICMP*/IF_ACMP*: expected 2 elements, found 1"
        LambdaExpression expr = analyzeLambda(methodName);

        assertBinaryOp(expr, expectedOperator);
        LambdaExpression.BinaryOp binOp = (LambdaExpression.BinaryOp) expr;
        assertFieldAccess(binOp.left(), "age");
        assertFieldAccess(binOp.right(), "minAge");
    }
}
