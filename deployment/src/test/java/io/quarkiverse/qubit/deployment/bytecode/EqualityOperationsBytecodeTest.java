package io.quarkiverse.qubit.deployment.bytecode;

import io.quarkiverse.qubit.deployment.ast.LambdaExpression;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

/**
 * Bytecode analysis tests for equality operations (==, equals(), isEqual()).
 * Tests lambda bytecode parsing without executing queries.
 *
 * <p>This class uses JUnit 5 parameterized tests to consolidate repetitive
 * test patterns, reducing code duplication while maintaining full coverage.
 */
class EqualityOperationsBytecodeTest extends PrecompiledLambdaAnalyzer {

    // ==================== PARAMETERIZED TEST DATA ====================

    /**
     * Test data for simple binary equality operations.
     * Each entry: lambdaMethodName, expectedFieldName, expectedConstant
     */
    static Stream<Arguments> simpleEqualities() {
        return Stream.of(
                Arguments.of("stringEquality", "firstName", "John"),
                Arguments.of("integerEquality", "age", 30),
                Arguments.of("longEquality", "employeeId", 1000001L),
                Arguments.of("floatEquality", "height", 1.75f),
                Arguments.of("doubleEquality", "salary", 75000.0)
        );
    }

    /**
     * Test data for temporal equality operations using isEqual() method.
     * Each entry: lambdaMethodName, expectedFieldName
     */
    static Stream<Arguments> temporalEqualities() {
        return Stream.of(
                Arguments.of("localDateEquality", "birthDate"),
                Arguments.of("localDateTimeEquality", "createdAt")
        );
    }

    // ==================== PARAMETERIZED TESTS ====================

    @ParameterizedTest(name = "{0}: {1} == {2}")
    @MethodSource("simpleEqualities")
    void simpleEquality(String lambdaMethodName, String expectedFieldName, Object expectedConstant) {
        LambdaExpression expr = analyzeLambda(lambdaMethodName);

        assertBinaryOp(expr, LambdaExpression.BinaryOp.Operator.EQ);
        LambdaExpression.BinaryOp binOp = (LambdaExpression.BinaryOp) expr;
        assertFieldAccess(binOp.left(), expectedFieldName);
        assertConstant(binOp.right(), expectedConstant);
    }

    @ParameterizedTest(name = "{0}: {1}.isEqual()")
    @MethodSource("temporalEqualities")
    void temporalEquality(String lambdaMethodName, String expectedFieldName) {
        LambdaExpression expr = analyzeLambda(lambdaMethodName);

        assertMethodCall(expr, "isEqual");
        LambdaExpression.MethodCall methodCall = (LambdaExpression.MethodCall) expr;
        assertFieldAccess(methodCall.target(), expectedFieldName);
    }

    // ==================== SPECIAL CASE TESTS ====================

    @Test
    void booleanEqualityTrue() {
        LambdaExpression expr = analyzeLambda("booleanEqualityTrue");

        // Compiler may either:
        // 1. Optimize p.active == true to just p.active (FieldAccess)
        // 2. Generate comparison bytecode: active IF_ICMPNE 1 (BinaryOp[active EQ true])
        if (expr instanceof LambdaExpression.FieldAccess) {
            assertFieldAccess(expr, "active");
        } else {
            assertBinaryOp(expr, LambdaExpression.BinaryOp.Operator.EQ);
            LambdaExpression.BinaryOp binOp = (LambdaExpression.BinaryOp) expr;
            assertFieldAccess(binOp.left(), "active");
        }
    }

    @Test
    void booleanEqualityFalse() {
        LambdaExpression expr = analyzeLambda("booleanEqualityFalse");

        // Analyzer transforms: p.active == false -> !p.active
        assertUnaryOp(expr, LambdaExpression.UnaryOp.Operator.NOT);
        LambdaExpression.UnaryOp unaryOp = (LambdaExpression.UnaryOp) expr;
        assertFieldAccess(unaryOp.operand(), "active");
    }

    @Test
    void booleanImplicit() {
        LambdaExpression expr = analyzeLambda("booleanImplicit");

        // p.active (implicit boolean check)
        assertFieldAccess(expr, "active");
    }

    @Test
    void localTimeEquality() {
        LambdaExpression expr = analyzeLambda("localTimeEquality");

        // Analyzer transforms: p.startTime.equals(LocalTime.of(9, 0)) -> p.startTime == LocalTime.of(9, 0)
        assertBinaryOp(expr, LambdaExpression.BinaryOp.Operator.EQ);
        LambdaExpression.BinaryOp binOp = (LambdaExpression.BinaryOp) expr;
        assertFieldAccess(binOp.left(), "startTime");
    }

    @Test
    void bigDecimalEquality() {
        LambdaExpression expr = analyzeLambda("bigDecimalEquality");

        // For equality (== 0), the analyzer does NOT optimize like it does for > 0
        // It keeps: compareTo(...) == 0
        assertBinaryOp(expr, LambdaExpression.BinaryOp.Operator.EQ);
        LambdaExpression.BinaryOp binOp = (LambdaExpression.BinaryOp) expr;

        // Left side: compareTo method call
        assertMethodCall(binOp.left(), "compareTo");
        LambdaExpression.MethodCall methodCall = (LambdaExpression.MethodCall) binOp.left();
        assertFieldAccess(methodCall.target(), "price");

        // Right side: 0
        assertConstant(binOp.right(), 0);
    }
}
