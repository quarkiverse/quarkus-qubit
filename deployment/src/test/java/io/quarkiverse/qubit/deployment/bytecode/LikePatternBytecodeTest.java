package io.quarkiverse.qubit.deployment.bytecode;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.quarkiverse.qubit.deployment.ast.LambdaExpression;

/**
 * Bytecode analysis tests for Qubit.like() and Qubit.notLike() marker methods.
 * Verifies that the bytecode analyzer produces the correct AST nodes.
 */
@DisplayName("LIKE pattern bytecode analysis")
class LikePatternBytecodeTest extends PrecompiledLambdaAnalyzer {

    @Test
    @DisplayName("Qubit.like(field, constant) produces MethodCall with 'like' name")
    void likePattern_producesMethodCallNode() {
        LambdaExpression expr = analyzeLambda("likePattern");

        assertMethodCall(expr, "like");
        LambdaExpression.MethodCall methodCall = (LambdaExpression.MethodCall) expr;
        assertFieldAccess(methodCall.target(), "email");
        assertThat(methodCall.arguments()).hasSize(1);
        assertConstant(methodCall.arguments().getFirst(), "%@%.com");
    }

    @Test
    @DisplayName("Qubit.like(field, captured) produces MethodCall with CapturedVariable pattern")
    void likeCapturedPattern_producesCapturedVariableArg() {
        LambdaExpression expr = analyzeLambda("likeCapturedPattern");

        assertMethodCall(expr, "like");
        LambdaExpression.MethodCall methodCall = (LambdaExpression.MethodCall) expr;
        assertFieldAccess(methodCall.target(), "email");
        assertThat(methodCall.arguments()).hasSize(1);
        assertThat(methodCall.arguments().getFirst()).isInstanceOf(LambdaExpression.CapturedVariable.class);
    }

    @Test
    @DisplayName("Qubit.notLike(field, constant) produces MethodCall with 'notLike' name")
    void notLikePattern_producesNotLikeMethodCall() {
        LambdaExpression expr = analyzeLambda("notLikePattern");

        assertMethodCall(expr, "notLike");
        LambdaExpression.MethodCall methodCall = (LambdaExpression.MethodCall) expr;
        assertFieldAccess(methodCall.target(), "email");
        assertThat(methodCall.arguments()).hasSize(1);
        assertConstant(methodCall.arguments().getFirst(), "%spam%");
    }

    @Test
    @DisplayName("Qubit.like(field, single-char wildcard) produces correct AST")
    void likeSingleCharWildcard_producesCorrectAst() {
        LambdaExpression expr = analyzeLambda("likeSingleCharWildcard");

        assertMethodCall(expr, "like");
        LambdaExpression.MethodCall methodCall = (LambdaExpression.MethodCall) expr;
        assertFieldAccess(methodCall.target(), "firstName");
        assertThat(methodCall.arguments()).hasSize(1);
        assertConstant(methodCall.arguments().getFirst(), "J_hn");
    }

    @Test
    @DisplayName("Qubit.like() combined with && produces AND with MethodCall")
    void likeCombinedWithAnd_producesAndWithMethodCall() {
        LambdaExpression expr = analyzeLambda("likeCombinedWithAnd");

        // Top-level should be AND
        assertBinaryOp(expr, LambdaExpression.BinaryOp.Operator.AND);
        LambdaExpression.BinaryOp andOp = (LambdaExpression.BinaryOp) expr;

        // Left side should be the like MethodCall
        assertMethodCall(andOp.left(), "like");
        LambdaExpression.MethodCall methodCall = (LambdaExpression.MethodCall) andOp.left();
        assertFieldAccess(methodCall.target(), "email");
        assertConstant(methodCall.arguments().getFirst(), "%@%.com");

        // Right side should be the boolean active field (compiled as active == true)
        assertBinaryOp(andOp.right(), LambdaExpression.BinaryOp.Operator.EQ);
        LambdaExpression.BinaryOp activeEq = (LambdaExpression.BinaryOp) andOp.right();
        assertFieldAccess(activeEq.left(), "active");
    }
}
