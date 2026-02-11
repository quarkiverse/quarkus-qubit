package io.quarkiverse.qubit.deployment.bytecode;

import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import io.quarkiverse.qubit.deployment.ast.LambdaExpression;

/**
 * Bytecode analysis tests for arithmetic operations (+, -, *, /, %).
 * Tests lambda bytecode parsing without executing queries.
 *
 * <p>
 * This class uses JUnit 5 parameterized tests to consolidate repetitive
 * test patterns, reducing code duplication while maintaining full coverage.
 */
class ArithmeticOperationsBytecodeTest extends PrecompiledLambdaAnalyzer {

    // ==================== PARAMETERIZED TEST DATA ====================

    /**
     * Test data for arithmetic operations with comparison.
     * Each entry: lambdaMethodName, comparisonOp, arithmeticOp, fieldName, arithmeticOperand, comparisonConstant
     */
    static Stream<Arguments> arithmeticWithComparison() {
        return Stream.of(
                // Integer arithmetic
                Arguments.of("integerAddition", LambdaExpression.BinaryOp.Operator.GT,
                        LambdaExpression.BinaryOp.Operator.ADD, "age", 5, 35),
                Arguments.of("integerSubtraction", LambdaExpression.BinaryOp.Operator.GT,
                        LambdaExpression.BinaryOp.Operator.SUB, "age", 5, 20),
                Arguments.of("integerMultiplication", LambdaExpression.BinaryOp.Operator.GT,
                        LambdaExpression.BinaryOp.Operator.MUL, "age", 2, 60),
                Arguments.of("integerDivision", LambdaExpression.BinaryOp.Operator.GT,
                        LambdaExpression.BinaryOp.Operator.DIV, "age", 2, 15),
                Arguments.of("integerModulo", LambdaExpression.BinaryOp.Operator.EQ,
                        LambdaExpression.BinaryOp.Operator.MOD, "age", 10, 0),

                // Long arithmetic
                Arguments.of("longAddition", LambdaExpression.BinaryOp.Operator.GT,
                        LambdaExpression.BinaryOp.Operator.ADD, "employeeId", 10L, 1000010L),
                Arguments.of("longSubtraction", LambdaExpression.BinaryOp.Operator.LT,
                        LambdaExpression.BinaryOp.Operator.SUB, "employeeId", 10L, 1000000L),
                Arguments.of("longMultiplication", LambdaExpression.BinaryOp.Operator.GT,
                        LambdaExpression.BinaryOp.Operator.MUL, "employeeId", 2L, 2000000L),
                Arguments.of("longDivision", LambdaExpression.BinaryOp.Operator.LT,
                        LambdaExpression.BinaryOp.Operator.DIV, "employeeId", 2L, 500002L),
                Arguments.of("longModulo", LambdaExpression.BinaryOp.Operator.EQ,
                        LambdaExpression.BinaryOp.Operator.MOD, "employeeId", 2L, 1L),

                // Float arithmetic
                Arguments.of("floatAddition", LambdaExpression.BinaryOp.Operator.GT,
                        LambdaExpression.BinaryOp.Operator.ADD, "height", 0.10f, 1.85f),
                Arguments.of("floatSubtraction", LambdaExpression.BinaryOp.Operator.LT,
                        LambdaExpression.BinaryOp.Operator.SUB, "height", 0.05f, 1.70f),
                Arguments.of("floatMultiplication", LambdaExpression.BinaryOp.Operator.GT,
                        LambdaExpression.BinaryOp.Operator.MUL, "height", 2.0f, 3.5f),
                Arguments.of("floatDivision", LambdaExpression.BinaryOp.Operator.LT,
                        LambdaExpression.BinaryOp.Operator.DIV, "height", 2.0f, 0.85f),

                // Double arithmetic
                Arguments.of("doubleAddition", LambdaExpression.BinaryOp.Operator.GT,
                        LambdaExpression.BinaryOp.Operator.ADD, "salary", 5000.0, 80000.0),
                Arguments.of("doubleSubtraction", LambdaExpression.BinaryOp.Operator.LT,
                        LambdaExpression.BinaryOp.Operator.SUB, "salary", 10000.0, 70000.0),
                Arguments.of("doubleMultiplication", LambdaExpression.BinaryOp.Operator.GT,
                        LambdaExpression.BinaryOp.Operator.MUL, "salary", 1.1, 80000.0),
                Arguments.of("doubleDivision", LambdaExpression.BinaryOp.Operator.GT,
                        LambdaExpression.BinaryOp.Operator.DIV, "salary", 1000.0, 75.0));
    }

    // ==================== PARAMETERIZED TESTS ====================

    @ParameterizedTest(name = "{0}: ({3} {2} {4}) {1} {5}")
    @MethodSource("arithmeticWithComparison")
    void arithmeticOperation(
            String lambdaMethodName,
            LambdaExpression.BinaryOp.Operator comparisonOp,
            LambdaExpression.BinaryOp.Operator arithmeticOp,
            String fieldName,
            Object arithmeticOperand,
            Object comparisonConstant) {

        LambdaExpression expr = analyzeLambda(lambdaMethodName);

        // Outer: comparison operation (>, <, ==)
        assertBinaryOp(expr, comparisonOp);
        LambdaExpression.BinaryOp compOp = (LambdaExpression.BinaryOp) expr;

        // Left: arithmetic operation (field op constant)
        assertBinaryOp(compOp.left(), arithmeticOp);
        LambdaExpression.BinaryOp arithOp = (LambdaExpression.BinaryOp) compOp.left();
        assertFieldAccess(arithOp.left(), fieldName);
        assertConstant(arithOp.right(), arithmeticOperand);

        // Right: comparison constant
        assertConstant(compOp.right(), comparisonConstant);
    }

    // ==================== FIELD-FIELD ARITHMETIC ====================

    @Test
    void longFieldFieldAddition() {
        LambdaExpression expr = analyzeLambda("longFieldFieldAddition");

        // p.employeeId + p.employeeId > 2000000L
        assertBinaryOp(expr, LambdaExpression.BinaryOp.Operator.GT);
        LambdaExpression.BinaryOp gtOp = (LambdaExpression.BinaryOp) expr;

        // Left: p.employeeId + p.employeeId
        assertBinaryOp(gtOp.left(), LambdaExpression.BinaryOp.Operator.ADD);
        LambdaExpression.BinaryOp addOp = (LambdaExpression.BinaryOp) gtOp.left();
        assertFieldAccess(addOp.left(), "employeeId");
        assertFieldAccess(addOp.right(), "employeeId");

        assertConstant(gtOp.right(), 2000000L);
    }

    @Test
    void longFieldFieldSubtraction() {
        LambdaExpression expr = analyzeLambda("longFieldFieldSubtraction");

        // p.employeeId - p.employeeId == 0L
        assertBinaryOp(expr, LambdaExpression.BinaryOp.Operator.EQ);
        LambdaExpression.BinaryOp eqOp = (LambdaExpression.BinaryOp) expr;

        // Left: p.employeeId - p.employeeId
        assertBinaryOp(eqOp.left(), LambdaExpression.BinaryOp.Operator.SUB);
        LambdaExpression.BinaryOp subOp = (LambdaExpression.BinaryOp) eqOp.left();
        assertFieldAccess(subOp.left(), "employeeId");
        assertFieldAccess(subOp.right(), "employeeId");

        assertConstant(eqOp.right(), 0L);
    }
}
