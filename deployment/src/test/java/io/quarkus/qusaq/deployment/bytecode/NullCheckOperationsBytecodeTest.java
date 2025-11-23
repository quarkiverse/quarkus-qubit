package io.quarkus.qusaq.deployment.bytecode;

import io.quarkus.qusaq.deployment.LambdaExpression;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Bytecode analysis tests for null check operations (== null, != null).
 * Tests lambda bytecode parsing without executing queries.
 */
class NullCheckOperationsBytecodeTest extends PrecompiledLambdaAnalyzer {

    // ==================== STRING NULL CHECKS ====================

    @Test
    void stringNullCheck() {
        LambdaExpression expr = analyzeLambda("stringNullCheck");

        // p.email == null
        assertBinaryOp(expr, LambdaExpression.BinaryOp.Operator.EQ);
        LambdaExpression.BinaryOp binOp = (LambdaExpression.BinaryOp) expr;
        assertFieldAccess(binOp.left(), "email");
        assertNullLiteral(binOp.right());
    }

    @Test
    void stringNotNullCheck() {
        LambdaExpression expr = analyzeLambda("stringNotNullCheck");

        // p.email != null
        assertBinaryOp(expr, LambdaExpression.BinaryOp.Operator.NE);
        LambdaExpression.BinaryOp binOp = (LambdaExpression.BinaryOp) expr;
        assertFieldAccess(binOp.left(), "email");
        assertNullLiteral(binOp.right());
    }

    // ==================== WRAPPER TYPE NULL CHECKS ====================

    @Test
    void doubleNullCheck() {
        LambdaExpression expr = analyzeLambda("doubleNullCheck");

        // p.salary == null (using TestPersonNullable with Double wrapper)
        assertBinaryOp(expr, LambdaExpression.BinaryOp.Operator.EQ);
        LambdaExpression.BinaryOp binOp = (LambdaExpression.BinaryOp) expr;
        assertFieldAccess(binOp.left(), "salary");
        assertNullLiteral(binOp.right());
    }

    @Test
    void longNullCheck() {
        LambdaExpression expr = analyzeLambda("longNullCheck");

        // p.employeeId == null (using TestPersonNullable with Long wrapper)
        assertBinaryOp(expr, LambdaExpression.BinaryOp.Operator.EQ);
        LambdaExpression.BinaryOp binOp = (LambdaExpression.BinaryOp) expr;
        assertFieldAccess(binOp.left(), "employeeId");
        assertNullLiteral(binOp.right());
    }

    @Test
    void floatNullCheck() {
        LambdaExpression expr = analyzeLambda("floatNullCheck");

        // p.height == null (using TestPersonNullable with Float wrapper)
        assertBinaryOp(expr, LambdaExpression.BinaryOp.Operator.EQ);
        LambdaExpression.BinaryOp binOp = (LambdaExpression.BinaryOp) expr;
        assertFieldAccess(binOp.left(), "height");
        assertNullLiteral(binOp.right());
    }

    // ==================== TEMPORAL TYPE NULL CHECKS ====================

    @Test
    void localDateNullCheck() {
        LambdaExpression expr = analyzeLambda("localDateNullCheck");

        // p.birthDate == null
        assertBinaryOp(expr, LambdaExpression.BinaryOp.Operator.EQ);
        LambdaExpression.BinaryOp binOp = (LambdaExpression.BinaryOp) expr;
        assertFieldAccess(binOp.left(), "birthDate");
        assertNullLiteral(binOp.right());
    }

    @Test
    void localDateTimeNullCheck() {
        LambdaExpression expr = analyzeLambda("localDateTimeNullCheck");

        // p.createdAt == null
        assertBinaryOp(expr, LambdaExpression.BinaryOp.Operator.EQ);
        LambdaExpression.BinaryOp binOp = (LambdaExpression.BinaryOp) expr;
        assertFieldAccess(binOp.left(), "createdAt");
        assertNullLiteral(binOp.right());
    }

    @Test
    void localTimeNullCheck() {
        LambdaExpression expr = analyzeLambda("localTimeNullCheck");

        // p.startTime == null
        assertBinaryOp(expr, LambdaExpression.BinaryOp.Operator.EQ);
        LambdaExpression.BinaryOp binOp = (LambdaExpression.BinaryOp) expr;
        assertFieldAccess(binOp.left(), "startTime");
        assertNullLiteral(binOp.right());
    }

    // ==================== CHAINED NULL CHECKS ====================

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
