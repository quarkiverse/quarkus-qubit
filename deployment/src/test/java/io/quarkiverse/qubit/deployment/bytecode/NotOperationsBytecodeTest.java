package io.quarkiverse.qubit.deployment.bytecode;

import org.junit.jupiter.api.Test;

import io.quarkiverse.qubit.deployment.ast.LambdaExpression;

/**
 * Bytecode analysis tests for NOT logical operations (!).
 * Tests lambda bytecode parsing without executing queries.
 */
class NotOperationsBytecodeTest extends PrecompiledLambdaAnalyzer {

    @Test
    void simpleNot() {
        LambdaExpression expr = analyzeLambda("simpleNot");

        // !p.active
        assertUnaryOp(expr, LambdaExpression.UnaryOp.Operator.NOT);
        LambdaExpression.UnaryOp notOp = (LambdaExpression.UnaryOp) expr;
        assertFieldAccess(notOp.operand(), "active");
    }

    @Test
    void notWithAnd() {
        LambdaExpression expr = analyzeLambda("notWithAnd");

        // !p.active && p.age > 40
        assertBinaryOp(expr, LambdaExpression.BinaryOp.Operator.AND);
        LambdaExpression.BinaryOp andOp = (LambdaExpression.BinaryOp) expr;

        // Left: !p.active
        assertUnaryOp(andOp.left(), LambdaExpression.UnaryOp.Operator.NOT);
        LambdaExpression.UnaryOp notOp = (LambdaExpression.UnaryOp) andOp.left();
        assertFieldAccess(notOp.operand(), "active");

        // Right: p.age > 40
        assertBinaryOp(andOp.right(), LambdaExpression.BinaryOp.Operator.GT);
    }

    @Test
    void notWithComplexOrAnd() {
        LambdaExpression expr = analyzeLambda("notWithComplexOrAnd");

        // !(p.age < 28 || p.age > 42) && p.active
        // Compiler optimizes using De Morgan's law: !(a || b) => !a && !b
        // Results in: (p.age >= 28 && p.age <= 42) && p.active
        assertBinaryOp(expr, LambdaExpression.BinaryOp.Operator.AND);
        LambdaExpression.BinaryOp andOp = (LambdaExpression.BinaryOp) expr;

        // Left: (p.age >= 28 && p.age <= 42)
        assertBinaryOp(andOp.left(), LambdaExpression.BinaryOp.Operator.AND);
        LambdaExpression.BinaryOp leftAnd = (LambdaExpression.BinaryOp) andOp.left();

        // Left side of inner AND: p.age >= 28
        assertBinaryOp(leftAnd.left(), LambdaExpression.BinaryOp.Operator.GE);

        // Right side of inner AND: p.age <= 42
        assertBinaryOp(leftAnd.right(), LambdaExpression.BinaryOp.Operator.LE);

        // Right: p.active (represented as p.active == true)
        assertBinaryOp(andOp.right(), LambdaExpression.BinaryOp.Operator.EQ);
    }

    @Test
    void stringNotEquals() {
        LambdaExpression expr = analyzeLambda("stringNotEquals");

        // !p.firstName.equals("John")
        // Analyzer may transform this to: p.firstName != "John"
        assertUnaryOp(expr, LambdaExpression.UnaryOp.Operator.NOT);
        LambdaExpression.UnaryOp notOp = (LambdaExpression.UnaryOp) expr;

        // Inside NOT: p.firstName == "John" (optimized from equals)
        assertBinaryOp(notOp.operand(), LambdaExpression.BinaryOp.Operator.EQ);
    }

    @Test
    void notWithComplexAnd() {
        LambdaExpression expr = analyzeLambda("notWithComplexAnd");

        // !(p.age > 10 && p.salary < 5000)
        // Compiler optimizes using De Morgan's law: !(a && b) => !a || !b
        // Results in: (p.age <= 10 || p.salary >= 5000)
        assertBinaryOp(expr, LambdaExpression.BinaryOp.Operator.OR);
        LambdaExpression.BinaryOp orOp = (LambdaExpression.BinaryOp) expr;

        // Left: p.age <= 10
        assertBinaryOp(orOp.left(), LambdaExpression.BinaryOp.Operator.LE);

        // Right: p.salary >= 5000
        assertBinaryOp(orOp.right(), LambdaExpression.BinaryOp.Operator.GE);
    }

    @Test
    void doubleNegation() {
        LambdaExpression expr = analyzeLambda("doubleNegation");

        // !!p.active
        // Compiler optimizes double negation to: p.active == true
        assertBinaryOp(expr, LambdaExpression.BinaryOp.Operator.EQ);
    }

    @Test
    void notWithOr() {
        LambdaExpression expr = analyzeLambda("notWithOr");

        // !(p.active || p.salary > 90000)
        // Compiler optimizes using De Morgan's law: !(a || b) => !a && !b
        // Results in: (!p.active && p.salary <= 90000)
        assertBinaryOp(expr, LambdaExpression.BinaryOp.Operator.AND);
        LambdaExpression.BinaryOp andOp = (LambdaExpression.BinaryOp) expr;

        // Left: !p.active
        assertUnaryOp(andOp.left(), LambdaExpression.UnaryOp.Operator.NOT);
        LambdaExpression.UnaryOp notOp = (LambdaExpression.UnaryOp) andOp.left();
        assertFieldAccess(notOp.operand(), "active");

        // Right: p.salary <= 90000
        assertBinaryOp(andOp.right(), LambdaExpression.BinaryOp.Operator.LE);
    }
}
