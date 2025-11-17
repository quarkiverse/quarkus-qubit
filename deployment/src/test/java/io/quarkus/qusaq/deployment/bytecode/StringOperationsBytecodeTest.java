package io.quarkus.qusaq.deployment.bytecode;

import io.quarkus.qusaq.deployment.LambdaExpression;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Bytecode analysis tests for String operations.
 * Tests lambda bytecode parsing without executing queries.
 */
class StringOperationsBytecodeTest extends PrecompiledLambdaAnalyzer {

    @Test
    void stringStartsWith() {
        LambdaExpression expr = analyzeLambda("stringStartsWith");

        // p.firstName.startsWith("J")
        // When method call is the only expression, it's NOT wrapped in == true
        assertMethodCall(expr, "startsWith");
        LambdaExpression.MethodCall methodCall = (LambdaExpression.MethodCall) expr;
        assertFieldAccess(methodCall.target(), "firstName");
        assertThat(methodCall.arguments()).hasSize(1);
        assertConstant(methodCall.arguments().get(0), "J");
    }

    @Test
    void stringEndsWith() {
        LambdaExpression expr = analyzeLambda("stringEndsWith");

        // p.email.endsWith("@example.com")
        // When method call is the only expression, it's NOT wrapped in == true
        assertMethodCall(expr, "endsWith");
        LambdaExpression.MethodCall methodCall = (LambdaExpression.MethodCall) expr;
        assertFieldAccess(methodCall.target(), "email");
        assertThat(methodCall.arguments()).hasSize(1);
        assertConstant(methodCall.arguments().get(0), "@example.com");
    }

    @Test
    void stringContains() {
        LambdaExpression expr = analyzeLambda("stringContains");

        // p.email.contains("john")
        // When method call is the only expression, it's NOT wrapped in == true
        assertMethodCall(expr, "contains");
        LambdaExpression.MethodCall methodCall = (LambdaExpression.MethodCall) expr;
        assertFieldAccess(methodCall.target(), "email");
        assertThat(methodCall.arguments()).hasSize(1);
        assertConstant(methodCall.arguments().get(0), "john");
    }

    @Test
    void stringLength() {
        LambdaExpression expr = analyzeLambda("stringLength");

        // p.firstName.length() > 4
        assertBinaryOp(expr, LambdaExpression.BinaryOp.Operator.GT);
        LambdaExpression.BinaryOp gtOp = (LambdaExpression.BinaryOp) expr;

        // Left: length() method call
        assertMethodCall(gtOp.left(), "length");
        LambdaExpression.MethodCall methodCall = (LambdaExpression.MethodCall) gtOp.left();
        assertFieldAccess(methodCall.target(), "firstName");
        assertThat(methodCall.arguments()).isEmpty();

        // Right: 4
        assertConstant(gtOp.right(), 4);
    }

    @Test
    void stringToLowerCase() {
        LambdaExpression expr = analyzeLambda("stringToLowerCase");

        // p.firstName.toLowerCase().equals("john")
        // Analyzer transforms .equals() to ==
        assertBinaryOp(expr, LambdaExpression.BinaryOp.Operator.EQ);
        LambdaExpression.BinaryOp eqOp = (LambdaExpression.BinaryOp) expr;

        // Left: toLowerCase() method call
        assertMethodCall(eqOp.left(), "toLowerCase");
        LambdaExpression.MethodCall methodCall = (LambdaExpression.MethodCall) eqOp.left();
        assertFieldAccess(methodCall.target(), "firstName");
        assertThat(methodCall.arguments()).isEmpty();

        // Right: "john"
        assertConstant(eqOp.right(), "john");
    }

    @Test
    void stringToUpperCase() {
        LambdaExpression expr = analyzeLambda("stringToUpperCase");

        // p.firstName.toUpperCase().equals("JANE")
        // Analyzer transforms .equals() to ==
        assertBinaryOp(expr, LambdaExpression.BinaryOp.Operator.EQ);
        LambdaExpression.BinaryOp eqOp = (LambdaExpression.BinaryOp) expr;

        // Left: toUpperCase() method call
        assertMethodCall(eqOp.left(), "toUpperCase");
        LambdaExpression.MethodCall methodCall = (LambdaExpression.MethodCall) eqOp.left();
        assertFieldAccess(methodCall.target(), "firstName");
        assertThat(methodCall.arguments()).isEmpty();

        // Right: "JANE"
        assertConstant(eqOp.right(), "JANE");
    }

    @Test
    void stringTrim() {
        LambdaExpression expr = analyzeLambda("stringTrim");

        // p.email.trim().equals("david.miller@example.com")
        // Analyzer transforms .equals() to ==
        assertBinaryOp(expr, LambdaExpression.BinaryOp.Operator.EQ);
        LambdaExpression.BinaryOp eqOp = (LambdaExpression.BinaryOp) expr;

        // Left: trim() method call
        assertMethodCall(eqOp.left(), "trim");
        LambdaExpression.MethodCall methodCall = (LambdaExpression.MethodCall) eqOp.left();
        assertFieldAccess(methodCall.target(), "email");
        assertThat(methodCall.arguments()).isEmpty();

        // Right: "david.miller@example.com"
        assertConstant(eqOp.right(), "david.miller@example.com");
    }

    @Test
    void stringIsEmpty() {
        LambdaExpression expr = analyzeLambda("stringIsEmpty");

        // p.email.isEmpty()
        // When method call is the only expression, it's NOT wrapped in == true
        assertMethodCall(expr, "isEmpty");
        LambdaExpression.MethodCall methodCall = (LambdaExpression.MethodCall) expr;
        assertFieldAccess(methodCall.target(), "email");
        assertThat(methodCall.arguments()).isEmpty();
    }

    @Test
    void stringSubstring() {
        LambdaExpression expr = analyzeLambda("stringSubstring");

        // p.firstName.substring(0, 4).equals("John")
        // Analyzer transforms .equals() to ==
        assertBinaryOp(expr, LambdaExpression.BinaryOp.Operator.EQ);
        LambdaExpression.BinaryOp eqOp = (LambdaExpression.BinaryOp) expr;

        // Left: substring(0, 4) method call
        assertMethodCall(eqOp.left(), "substring");
        LambdaExpression.MethodCall methodCall = (LambdaExpression.MethodCall) eqOp.left();
        assertFieldAccess(methodCall.target(), "firstName");
        assertThat(methodCall.arguments()).hasSize(2);
        assertConstant(methodCall.arguments().get(0), 0);
        assertConstant(methodCall.arguments().get(1), 4);

        // Right: "John"
        assertConstant(eqOp.right(), "John");
    }

    @Test
    void stringMethodChaining() {
        LambdaExpression expr = analyzeLambda("stringMethodChaining");

        // p.email.toLowerCase().contains("example")
        // When method call is the only expression, it's NOT wrapped in == true
        assertMethodCall(expr, "contains");
        LambdaExpression.MethodCall containsCall = (LambdaExpression.MethodCall) expr;
        assertThat(containsCall.arguments()).hasSize(1);
        assertConstant(containsCall.arguments().get(0), "example");

        // Target of contains is toLowerCase
        assertMethodCall(containsCall.target(), "toLowerCase");
        LambdaExpression.MethodCall toLowerCall = (LambdaExpression.MethodCall) containsCall.target();
        assertFieldAccess(toLowerCall.target(), "email");
    }

    @Test
    void stringComplexConditions() {
        LambdaExpression expr = analyzeLambda("stringComplexConditions");

        // p.email != null && p.email.contains("@") && p.email.endsWith(".com")
        assertBinaryOp(expr, LambdaExpression.BinaryOp.Operator.AND);
        LambdaExpression.BinaryOp outerAnd = (LambdaExpression.BinaryOp) expr;

        // Should be structured as: (p.email != null && p.email.contains("@")) && p.email.endsWith(".com")
        // Or: p.email != null && (p.email.contains("@") && p.email.endsWith(".com"))
        // Either way, verify it's an AND chain
        assertThat(outerAnd.left()).isNotNull();
        assertThat(outerAnd.right()).isNotNull();
    }
}
