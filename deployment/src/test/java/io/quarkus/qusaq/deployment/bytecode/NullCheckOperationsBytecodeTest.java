package io.quarkus.qusaq.deployment.bytecode;

import io.quarkus.qusaq.deployment.LambdaExpression;
import org.junit.jupiter.api.Test;

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
}
