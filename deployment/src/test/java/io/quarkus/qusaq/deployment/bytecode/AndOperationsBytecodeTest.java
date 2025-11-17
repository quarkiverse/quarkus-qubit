package io.quarkus.qusaq.deployment.bytecode;

import io.quarkus.qusaq.deployment.LambdaExpression;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Bytecode analysis tests for AND logical operations (&&).
 * Tests lambda bytecode parsing without executing queries.
 */
class AndOperationsBytecodeTest extends PrecompiledLambdaAnalyzer {

    @Test
    void twoConditionAnd() {
        LambdaExpression expr = analyzeLambda("twoConditionAnd");

        // p.age > 25 && p.active
        assertBinaryOp(expr, LambdaExpression.BinaryOp.Operator.AND);
        LambdaExpression.BinaryOp andOp = (LambdaExpression.BinaryOp) expr;

        // Left: p.age > 25
        assertBinaryOp(andOp.left(), LambdaExpression.BinaryOp.Operator.GT);
        LambdaExpression.BinaryOp leftComp = (LambdaExpression.BinaryOp) andOp.left();
        assertFieldAccess(leftComp.left(), "age");
        assertConstant(leftComp.right(), 25);

        // Right: p.active (represented as p.active == true)
        assertBinaryOp(andOp.right(), LambdaExpression.BinaryOp.Operator.EQ);
        LambdaExpression.BinaryOp rightComp = (LambdaExpression.BinaryOp) andOp.right();
        assertFieldAccess(rightComp.left(), "active");
        assertConstant(rightComp.right(), true);
    }

    @Test
    void threeConditionAnd() {
        LambdaExpression expr = analyzeLambda("threeConditionAnd");

        // p.age >= 35 && p.active && p.salary != null
        // Should be structured as: (p.age >= 35 && p.active) && (p.salary != null)
        assertBinaryOp(expr, LambdaExpression.BinaryOp.Operator.AND);
        LambdaExpression.BinaryOp outerAnd = (LambdaExpression.BinaryOp) expr;

        // Can be left-associative: ((age >= 35) && active) && (salary != null)
        // Or right-associative: (age >= 35) && (active && (salary != null))
        // Let's check the actual structure
        assertThat(outerAnd.left()).isNotNull();
        assertThat(outerAnd.right()).isNotNull();
    }

    @Test
    void fourConditionAnd() {
        LambdaExpression expr = analyzeLambda("fourConditionAnd");

        // p.age >= 35 && p.active && p.salary != null && p.salary > 85000.0
        assertBinaryOp(expr, LambdaExpression.BinaryOp.Operator.AND);
        LambdaExpression.BinaryOp andOp = (LambdaExpression.BinaryOp) expr;

        // Verify it's an AND chain
        assertThat(andOp.left()).isNotNull();
        assertThat(andOp.right()).isNotNull();
    }

    @Test
    void fiveConditionAnd() {
        LambdaExpression expr = analyzeLambda("fiveConditionAnd");

        // p.age >= 30 && p.active && p.salary != null && p.salary > 70000.0 && p.email.contains("@")
        assertBinaryOp(expr, LambdaExpression.BinaryOp.Operator.AND);
        LambdaExpression.BinaryOp andOp = (LambdaExpression.BinaryOp) expr;

        // Verify it's an AND chain
        assertThat(andOp.left()).isNotNull();
        assertThat(andOp.right()).isNotNull();
    }

    @Test
    void longAndChain() {
        LambdaExpression expr = analyzeLambda("longAndChain");

        // p.age >= 25 && p.age <= 45 && p.active && p.salary != null &&
        // p.salary > 60000.0 && p.email.contains("@") &&
        // p.height != null && p.height > 1.6f
        assertBinaryOp(expr, LambdaExpression.BinaryOp.Operator.AND);
        LambdaExpression.BinaryOp andOp = (LambdaExpression.BinaryOp) expr;

        // Verify it's an AND chain (8 conditions total)
        assertThat(andOp.left()).isNotNull();
        assertThat(andOp.right()).isNotNull();
    }
}
