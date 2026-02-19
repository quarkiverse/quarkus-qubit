package io.quarkiverse.qubit.deployment.bytecode;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.quarkiverse.qubit.deployment.ast.LambdaExpression;

/**
 * Bytecode analysis tests for Qubit.left() and Qubit.right() marker methods.
 * Verifies that the bytecode analyzer produces the correct AST nodes.
 */
@DisplayName("LEFT/RIGHT bytecode analysis")
class LeftRightBytecodeTest extends PrecompiledLambdaAnalyzer {

    @Test
    @DisplayName("Qubit.left(field, constant) produces MethodCall with 'left' name")
    void qubitLeft_producesMethodCallNode() {
        LambdaExpression expr = analyzeLambda("qubitLeft");

        // Top-level is equals (left(...).equals("Joh"))
        assertBinaryOp(expr, LambdaExpression.BinaryOp.Operator.EQ);
        LambdaExpression.BinaryOp eq = (LambdaExpression.BinaryOp) expr;

        // Left side should be the left MethodCall
        assertMethodCall(eq.left(), "left");
        LambdaExpression.MethodCall methodCall = (LambdaExpression.MethodCall) eq.left();
        assertFieldAccess(methodCall.target(), "firstName");
        assertThat(methodCall.arguments()).hasSize(1);
        assertConstant(methodCall.arguments().getFirst(), 3);

        // Right side should be the "Joh" constant
        assertConstant(eq.right(), "Joh");
    }

    @Test
    @DisplayName("Qubit.right(field, constant) produces MethodCall with 'right' name")
    void qubitRight_producesMethodCallNode() {
        LambdaExpression expr = analyzeLambda("qubitRight");

        // Top-level is equals (right(...).equals(".com"))
        assertBinaryOp(expr, LambdaExpression.BinaryOp.Operator.EQ);
        LambdaExpression.BinaryOp eq = (LambdaExpression.BinaryOp) expr;

        // Left side should be the right MethodCall
        assertMethodCall(eq.left(), "right");
        LambdaExpression.MethodCall methodCall = (LambdaExpression.MethodCall) eq.left();
        assertFieldAccess(methodCall.target(), "email");
        assertThat(methodCall.arguments()).hasSize(1);
        assertConstant(methodCall.arguments().getFirst(), 4);

        // Right side should be the ".com" constant
        assertConstant(eq.right(), ".com");
    }

    @Test
    @DisplayName("Qubit.left(field, captured) produces MethodCall with CapturedVariable length")
    void qubitLeftCaptured_producesCapturedVariableArg() {
        LambdaExpression expr = analyzeLambda("qubitLeftCaptured");

        // Top-level is equals (left(...).equals("Joh"))
        assertBinaryOp(expr, LambdaExpression.BinaryOp.Operator.EQ);
        LambdaExpression.BinaryOp eq = (LambdaExpression.BinaryOp) expr;

        // Left side should be the left MethodCall
        assertMethodCall(eq.left(), "left");
        LambdaExpression.MethodCall methodCall = (LambdaExpression.MethodCall) eq.left();
        assertFieldAccess(methodCall.target(), "firstName");
        assertThat(methodCall.arguments()).hasSize(1);
        assertThat(methodCall.arguments().getFirst()).isInstanceOf(LambdaExpression.CapturedVariable.class);
    }

    @Test
    @DisplayName("Qubit.right(field, constant) as projection produces MethodCall")
    void qubitRightProjection_producesMethodCallNode() {
        LambdaExpression expr = analyzeLambda("qubitRightProjection");

        assertMethodCall(expr, "right");
        LambdaExpression.MethodCall methodCall = (LambdaExpression.MethodCall) expr;
        assertFieldAccess(methodCall.target(), "email");
        assertThat(methodCall.arguments()).hasSize(1);
        assertConstant(methodCall.arguments().getFirst(), 4);
    }
}
