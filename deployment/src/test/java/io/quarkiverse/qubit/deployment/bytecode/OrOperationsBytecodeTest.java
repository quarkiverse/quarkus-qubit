package io.quarkiverse.qubit.deployment.bytecode;

import io.quarkiverse.qubit.deployment.ast.LambdaExpression;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Bytecode analysis tests for OR logical operations (||).
 * Tests lambda bytecode parsing without executing queries.
 */
class OrOperationsBytecodeTest extends PrecompiledLambdaAnalyzer {

    @Test
    void simpleOr() {
        LambdaExpression expr = analyzeLambda("simpleOr");

        // p.age < 26 || p.age > 40
        assertBinaryOp(expr, LambdaExpression.BinaryOp.Operator.OR);
        LambdaExpression.BinaryOp orOp = (LambdaExpression.BinaryOp) expr;

        // Left: p.age < 26
        assertBinaryOp(orOp.left(), LambdaExpression.BinaryOp.Operator.LT);
        LambdaExpression.BinaryOp leftComp = (LambdaExpression.BinaryOp) orOp.left();
        assertFieldAccess(leftComp.left(), "age");
        assertConstant(leftComp.right(), 26);

        // Right: p.age > 40
        assertBinaryOp(orOp.right(), LambdaExpression.BinaryOp.Operator.GT);
        LambdaExpression.BinaryOp rightComp = (LambdaExpression.BinaryOp) orOp.right();
        assertFieldAccess(rightComp.left(), "age");
        assertConstant(rightComp.right(), 40);
    }

    @Test
    void orWithStringOperations() {
        LambdaExpression expr = analyzeLambda("orWithStringOperations");

        // p.firstName.startsWith("A") || p.age > 40
        assertBinaryOp(expr, LambdaExpression.BinaryOp.Operator.OR);
        LambdaExpression.BinaryOp orOp = (LambdaExpression.BinaryOp) expr;

        // Left: p.firstName.startsWith("A") - returned as-is (MethodCall is a predicate)
        // Predicates (MethodCall returning boolean) are NOT wrapped with == true
        assertMethodCall(orOp.left(), "startsWith");

        // Right: p.age > 40
        assertBinaryOp(orOp.right(), LambdaExpression.BinaryOp.Operator.GT);
    }

    @Test
    void threeWayOr() {
        LambdaExpression expr = analyzeLambda("threeWayOr");

        // p.age < 26 || p.age > 44 || p.firstName.equals("John")
        assertBinaryOp(expr, LambdaExpression.BinaryOp.Operator.OR);
        LambdaExpression.BinaryOp orOp = (LambdaExpression.BinaryOp) expr;

        // Verify it's an OR chain
        assertThat(orOp.left()).isNotNull();
        assertThat(orOp.right()).isNotNull();
    }

    @Test
    void fourWayOr() {
        LambdaExpression expr = analyzeLambda("fourWayOr");

        // p.age < 27 || p.age > 43 || p.firstName.equals("Alice") || p.email.contains("@example.com")
        // Compiler optimizes this into a complex AND/OR structure
        // Just verify it's a binary op
        assertThat(expr).isInstanceOf(LambdaExpression.BinaryOp.class);
    }
}
