package io.quarkiverse.qubit.deployment.bytecode;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import io.quarkiverse.qubit.deployment.ast.LambdaExpression;

/**
 * Bytecode analysis tests for null check operations (== null, != null).
 * Tests lambda bytecode parsing without executing queries.
 *
 * <p>
 * This class uses JUnit 5 parameterized tests to consolidate repetitive
 * test patterns, reducing code duplication while maintaining full coverage.
 */
class NullCheckOperationsBytecodeTest extends PrecompiledLambdaAnalyzer {

    // ==================== PARAMETERIZED TESTS ====================

    /**
     * Tests for field == null patterns across String, wrapper types, and temporal types.
     */
    @ParameterizedTest(name = "{0}: {1} == null")
    @CsvSource({
            "stringNullCheck, email",
            "doubleNullCheck, salary",
            "longNullCheck, employeeId",
            "floatNullCheck, height",
            "localDateNullCheck, birthDate",
            "localDateTimeNullCheck, createdAt",
            "localTimeNullCheck, startTime"
    })
    void nullCheck(String lambdaMethodName, String expectedFieldName) {
        LambdaExpression expr = analyzeLambda(lambdaMethodName);

        assertBinaryOp(expr, LambdaExpression.BinaryOp.Operator.EQ);
        LambdaExpression.BinaryOp binOp = (LambdaExpression.BinaryOp) expr;
        assertFieldAccess(binOp.left(), expectedFieldName);
        assertNullLiteral(binOp.right());
    }

    // ==================== SPECIAL CASE TESTS ====================

    @Test
    void stringNotNullCheck() {
        LambdaExpression expr = analyzeLambda("stringNotNullCheck");

        // p.email != null
        assertBinaryOp(expr, LambdaExpression.BinaryOp.Operator.NE);
        LambdaExpression.BinaryOp binOp = (LambdaExpression.BinaryOp) expr;
        assertFieldAccess(binOp.left(), "email");
        assertNullLiteral(binOp.right());
    }

    @Test
    void nullCheckWithAnd() {
        LambdaExpression expr = analyzeLambda("nullCheckWithAnd");

        // p.email != null && p.firstName != null
        assertBinaryOp(expr, LambdaExpression.BinaryOp.Operator.AND);
        LambdaExpression.BinaryOp andOp = (LambdaExpression.BinaryOp) expr;

        // Left: p.email != null
        assertBinaryOp(andOp.left(), LambdaExpression.BinaryOp.Operator.NE);
        LambdaExpression.BinaryOp leftNullCheck = (LambdaExpression.BinaryOp) andOp.left();
        assertFieldAccess(leftNullCheck.left(), "email");
        assertNullLiteral(leftNullCheck.right());

        // Right: p.firstName != null
        assertBinaryOp(andOp.right(), LambdaExpression.BinaryOp.Operator.NE);
        LambdaExpression.BinaryOp rightNullCheck = (LambdaExpression.BinaryOp) andOp.right();
        assertFieldAccess(rightNullCheck.left(), "firstName");
        assertNullLiteral(rightNullCheck.right());
    }

    @Test
    void nullCheckWithCondition() {
        LambdaExpression expr = analyzeLambda("nullCheckWithCondition");

        // p.email != null && p.age > 30
        assertBinaryOp(expr, LambdaExpression.BinaryOp.Operator.AND);
        LambdaExpression.BinaryOp andOp = (LambdaExpression.BinaryOp) expr;

        // Left: p.email != null
        assertBinaryOp(andOp.left(), LambdaExpression.BinaryOp.Operator.NE);
        LambdaExpression.BinaryOp nullCheck = (LambdaExpression.BinaryOp) andOp.left();
        assertFieldAccess(nullCheck.left(), "email");
        assertNullLiteral(nullCheck.right());

        // Right: p.age > 30
        assertBinaryOp(andOp.right(), LambdaExpression.BinaryOp.Operator.GT);
    }

    @Test
    void nullCheckWithOr() {
        LambdaExpression expr = analyzeLambda("nullCheckWithOr");

        // p.email == null || p.firstName == null
        // Compiler may use NE with inverted control flow instead of EQ
        assertBinaryOp(expr, LambdaExpression.BinaryOp.Operator.OR);
        LambdaExpression.BinaryOp orOp = (LambdaExpression.BinaryOp) expr;

        // Verify both sides are binary ops with null checks
        assertThat(orOp.left()).isInstanceOf(LambdaExpression.BinaryOp.class);
        assertThat(orOp.right()).isInstanceOf(LambdaExpression.BinaryOp.class);
    }
}
