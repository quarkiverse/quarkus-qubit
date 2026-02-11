package io.quarkiverse.qubit.deployment.bytecode;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import io.quarkiverse.qubit.deployment.ast.LambdaExpression;

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

        // Left: p.firstName.startsWith("A") - MethodCall is a predicate
        assertMethodCall(orOp.left(), "startsWith");

        // Right: p.age > 40
        assertBinaryOp(orOp.right(), LambdaExpression.BinaryOp.Operator.GT);
    }

    @ParameterizedTest(name = "{0} → OR chain")
    @ValueSource(strings = { "threeWayOr", "fourWayOr" })
    void orChain(String lambdaMethodName) {
        LambdaExpression expr = analyzeLambda(lambdaMethodName);

        // Verify it's a binary operation (OR chain structure varies by compiler)
        assertThat(expr).isInstanceOf(LambdaExpression.BinaryOp.class);
    }
}
