package io.quarkiverse.qubit.deployment.bytecode;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import io.quarkiverse.qubit.deployment.ast.LambdaExpression;

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

    @ParameterizedTest(name = "{0} → AND chain")
    @ValueSource(strings = {
            "threeConditionAnd",
            "fourConditionAnd",
            "fiveConditionAnd",
            "longAndChain"
    })
    void andChain(String lambdaMethodName) {
        LambdaExpression expr = analyzeLambda(lambdaMethodName);

        assertBinaryOp(expr, LambdaExpression.BinaryOp.Operator.AND);
        LambdaExpression.BinaryOp andOp = (LambdaExpression.BinaryOp) expr;

        // Verify it's an AND chain with non-null operands
        assertThat(andOp.left()).isNotNull();
        assertThat(andOp.right()).isNotNull();
    }
}
