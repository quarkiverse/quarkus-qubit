package io.quarkiverse.qubit.deployment.bytecode;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import io.quarkiverse.qubit.deployment.ast.LambdaExpression;

/**
 * Bytecode analysis tests for String operations.
 * Tests lambda bytecode parsing without executing queries.
 *
 * <p>
 * This class uses JUnit 5 parameterized tests to consolidate repetitive
 * test patterns, reducing code duplication while maintaining full coverage.
 */
class StringOperationsBytecodeTest extends PrecompiledLambdaAnalyzer {

    /**
     * Test data for string predicate methods (startsWith, endsWith, contains).
     * Each entry: lambdaMethodName, expectedMethodName, expectedFieldName, expectedArgument
     */
    static Stream<Arguments> stringPredicateMethods() {
        return Stream.of(
                Arguments.of("stringStartsWith", "startsWith", "firstName", "J"),
                Arguments.of("stringEndsWith", "endsWith", "email", "@example.com"),
                Arguments.of("stringContains", "contains", "email", "john"));
    }

    /**
     * Test data for string transformation methods followed by equals().
     * Each entry: lambdaMethodName, expectedMethodName, expectedFieldName, expectedConstant
     */
    static Stream<Arguments> stringTransformMethods() {
        return Stream.of(
                Arguments.of("stringToLowerCase", "toLowerCase", "firstName", "john"),
                Arguments.of("stringToUpperCase", "toUpperCase", "firstName", "JANE"),
                Arguments.of("stringTrim", "trim", "email", "david.miller@example.com"));
    }

    @ParameterizedTest(name = "{0}: {2}.{1}(\"{3}\")")
    @MethodSource("stringPredicateMethods")
    void stringPredicateMethod(String lambdaMethodName, String expectedMethodName,
            String expectedFieldName, String expectedArgument) {
        LambdaExpression expr = analyzeLambda(lambdaMethodName);

        assertMethodCall(expr, expectedMethodName);
        LambdaExpression.MethodCall methodCall = (LambdaExpression.MethodCall) expr;
        assertFieldAccess(methodCall.target(), expectedFieldName);
        assertThat(methodCall.arguments()).hasSize(1);
        assertConstant(methodCall.arguments().getFirst(), expectedArgument);
    }

    @ParameterizedTest(name = "{0}: {2}.{1}().equals(\"{3}\")")
    @MethodSource("stringTransformMethods")
    void stringTransformMethod(String lambdaMethodName, String expectedMethodName,
            String expectedFieldName, String expectedConstant) {
        LambdaExpression expr = analyzeLambda(lambdaMethodName);

        assertBinaryOp(expr, LambdaExpression.BinaryOp.Operator.EQ);
        LambdaExpression.BinaryOp eqOp = (LambdaExpression.BinaryOp) expr;

        assertMethodCall(eqOp.left(), expectedMethodName);
        LambdaExpression.MethodCall methodCall = (LambdaExpression.MethodCall) eqOp.left();
        assertFieldAccess(methodCall.target(), expectedFieldName);
        assertThat(methodCall.arguments()).isEmpty();

        assertConstant(eqOp.right(), expectedConstant);
    }

    @Test
    void stringLength() {
        LambdaExpression expr = analyzeLambda("stringLength");

        // p.firstName.length() > 4
        assertBinaryOp(expr, LambdaExpression.BinaryOp.Operator.GT);
        LambdaExpression.BinaryOp gtOp = (LambdaExpression.BinaryOp) expr;

        assertMethodCall(gtOp.left(), "length");
        LambdaExpression.MethodCall methodCall = (LambdaExpression.MethodCall) gtOp.left();
        assertFieldAccess(methodCall.target(), "firstName");
        assertThat(methodCall.arguments()).isEmpty();

        assertConstant(gtOp.right(), 4);
    }

    @Test
    void stringIsEmpty() {
        LambdaExpression expr = analyzeLambda("stringIsEmpty");

        // p.email.isEmpty()
        assertMethodCall(expr, "isEmpty");
        LambdaExpression.MethodCall methodCall = (LambdaExpression.MethodCall) expr;
        assertFieldAccess(methodCall.target(), "email");
        assertThat(methodCall.arguments()).isEmpty();
    }

    @Test
    void stringIsBlank() {
        LambdaExpression expr = analyzeLambda("stringIsBlank");

        // p.email.isBlank()
        assertMethodCall(expr, "isBlank");
        LambdaExpression.MethodCall methodCall = (LambdaExpression.MethodCall) expr;
        assertFieldAccess(methodCall.target(), "email");
        assertThat(methodCall.arguments()).isEmpty();
    }

    @Test
    void stringSubstring() {
        LambdaExpression expr = analyzeLambda("stringSubstring");

        // p.firstName.substring(0, 4).equals("John")
        assertBinaryOp(expr, LambdaExpression.BinaryOp.Operator.EQ);
        LambdaExpression.BinaryOp eqOp = (LambdaExpression.BinaryOp) expr;

        assertMethodCall(eqOp.left(), "substring");
        LambdaExpression.MethodCall methodCall = (LambdaExpression.MethodCall) eqOp.left();
        assertFieldAccess(methodCall.target(), "firstName");
        assertThat(methodCall.arguments()).hasSize(2);
        assertConstant(methodCall.arguments().getFirst(), 0);
        assertConstant(methodCall.arguments().get(1), 4);

        assertConstant(eqOp.right(), "John");
    }

    @Test
    void stringMethodChaining() {
        LambdaExpression expr = analyzeLambda("stringMethodChaining");

        // p.email.toLowerCase().contains("example")
        assertMethodCall(expr, "contains");
        LambdaExpression.MethodCall containsCall = (LambdaExpression.MethodCall) expr;
        assertThat(containsCall.arguments()).hasSize(1);
        assertConstant(containsCall.arguments().getFirst(), "example");

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

        assertThat(outerAnd.left()).isNotNull();
        assertThat(outerAnd.right()).isNotNull();
    }

    @Nested
    @DisplayName("replace operations")
    class ReplaceOperations {

        @Test
        @DisplayName("replace(String, String) produces MethodCall with replace name and two arguments")
        void stringReplaceConstant_producesMethodCallNode() {
            LambdaExpression expr = analyzeLambda("stringReplaceConstant");
            // Top level is BinaryOp (equality check)
            assertBinaryOp(expr, LambdaExpression.BinaryOp.Operator.EQ);
            LambdaExpression.BinaryOp eqOp = (LambdaExpression.BinaryOp) expr;
            // Left side should be MethodCall with replace
            assertMethodCall(eqOp.left(), "replace");
            LambdaExpression.MethodCall methodCall = (LambdaExpression.MethodCall) eqOp.left();
            assertThat(methodCall.arguments()).hasSize(2);
            assertConstant(methodCall.arguments().get(0), "old");
            assertConstant(methodCall.arguments().get(1), "new");
            // Target should be FieldAccess for firstName
            assertFieldAccess(methodCall.target(), "firstName");
        }

        @Test
        @DisplayName("replace with captured variables has CapturedVariable arguments")
        void stringReplaceCaptured_hasCapturedArguments() {
            LambdaExpression expr = analyzeLambda("stringReplaceCaptured");
            assertBinaryOp(expr, LambdaExpression.BinaryOp.Operator.EQ);
            LambdaExpression.BinaryOp eqOp = (LambdaExpression.BinaryOp) expr;
            assertMethodCall(eqOp.left(), "replace");
            LambdaExpression.MethodCall methodCall = (LambdaExpression.MethodCall) eqOp.left();
            assertThat(methodCall.arguments()).hasSize(2);
            assertThat(methodCall.arguments().get(0)).isInstanceOf(LambdaExpression.CapturedVariable.class);
            assertThat(methodCall.arguments().get(1)).isInstanceOf(LambdaExpression.CapturedVariable.class);
        }

        @Test
        @DisplayName("replace in projection produces MethodCall")
        void stringReplaceProjection_producesMethodCall() {
            LambdaExpression expr = analyzeLambda("stringReplaceProjection");
            assertMethodCall(expr, "replace");
            LambdaExpression.MethodCall methodCall = (LambdaExpression.MethodCall) expr;
            assertThat(methodCall.arguments()).hasSize(2);
            assertFieldAccess(methodCall.target(), "firstName");
        }
    }

    @Nested
    @DisplayName("indexOf operations")
    class IndexOfOperations {

        @Test
        @DisplayName("indexOf(String) produces MethodCall with indexOf name")
        void stringIndexOf_producesMethodCallNode() {
            LambdaExpression expr = analyzeLambda("stringIndexOf");
            // Top level is BinaryOp (comparison > 0)
            assertBinaryOp(expr, LambdaExpression.BinaryOp.Operator.GT);
            LambdaExpression.BinaryOp comparison = (LambdaExpression.BinaryOp) expr;
            // Left side should be MethodCall with indexOf
            assertMethodCall(comparison.left(), "indexOf");
            LambdaExpression.MethodCall methodCall = (LambdaExpression.MethodCall) comparison.left();
            assertThat(methodCall.arguments()).hasSize(1);
            // Target should be FieldAccess for email
            assertFieldAccess(methodCall.target(), "email");
        }

        @Test
        @DisplayName("indexOf(String) with captured pattern")
        void stringIndexOfWithCapturedPattern_hasCapturedArgument() {
            LambdaExpression expr = analyzeLambda("stringIndexOfWithCapturedPattern");
            assertBinaryOp(expr, LambdaExpression.BinaryOp.Operator.GE);
            LambdaExpression.BinaryOp comparison = (LambdaExpression.BinaryOp) expr;
            assertMethodCall(comparison.left(), "indexOf");
            LambdaExpression.MethodCall methodCall = (LambdaExpression.MethodCall) comparison.left();
            assertThat(methodCall.arguments()).hasSize(1);
            // First argument should be a CapturedVariable (the pattern)
            assertThat(methodCall.arguments().getFirst()).isInstanceOf(LambdaExpression.CapturedVariable.class);
        }
    }
}
