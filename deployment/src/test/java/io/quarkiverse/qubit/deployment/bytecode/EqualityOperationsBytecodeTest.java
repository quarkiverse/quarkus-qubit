package io.quarkiverse.qubit.deployment.bytecode;

import io.quarkiverse.qubit.deployment.ast.LambdaExpression;
import org.junit.jupiter.api.Test;

/**
 * Bytecode analysis tests for equality operations (==, equals(), isEqual()).
 * Tests lambda bytecode parsing without executing queries.
 */
class EqualityOperationsBytecodeTest extends PrecompiledLambdaAnalyzer {

    // ==================== STRING EQUALITY ====================

    @Test
    void stringEquality() {
        LambdaExpression expr = analyzeLambda("stringEquality");

        // Analyzer transforms: p.firstName.equals("John") -> p.firstName == "John"
        assertBinaryOp(expr, LambdaExpression.BinaryOp.Operator.EQ);
        LambdaExpression.BinaryOp binOp = (LambdaExpression.BinaryOp) expr;
        assertFieldAccess(binOp.left(), "firstName");
        assertConstant(binOp.right(), "John");
    }

    // ==================== PRIMITIVE EQUALITY ====================

    @Test
    void integerEquality() {
        LambdaExpression expr = analyzeLambda("integerEquality");

        // p.age == 30
        assertBinaryOp(expr, LambdaExpression.BinaryOp.Operator.EQ);
        LambdaExpression.BinaryOp binOp = (LambdaExpression.BinaryOp) expr;
        assertFieldAccess(binOp.left(), "age");
        assertConstant(binOp.right(), 30);
    }

    @Test
    void longEquality() {
        LambdaExpression expr = analyzeLambda("longEquality");

        // p.employeeId == 1000001L
        assertBinaryOp(expr, LambdaExpression.BinaryOp.Operator.EQ);
        LambdaExpression.BinaryOp binOp = (LambdaExpression.BinaryOp) expr;
        assertFieldAccess(binOp.left(), "employeeId");
        assertConstant(binOp.right(), 1000001L);
    }

    @Test
    void floatEquality() {
        LambdaExpression expr = analyzeLambda("floatEquality");

        // p.height == 1.75f
        assertBinaryOp(expr, LambdaExpression.BinaryOp.Operator.EQ);
        LambdaExpression.BinaryOp binOp = (LambdaExpression.BinaryOp) expr;
        assertFieldAccess(binOp.left(), "height");
        assertConstant(binOp.right(), 1.75f);
    }

    @Test
    void doubleEquality() {
        LambdaExpression expr = analyzeLambda("doubleEquality");

        // p.salary == 75000.0
        assertBinaryOp(expr, LambdaExpression.BinaryOp.Operator.EQ);
        LambdaExpression.BinaryOp binOp = (LambdaExpression.BinaryOp) expr;
        assertFieldAccess(binOp.left(), "salary");
        assertConstant(binOp.right(), 75000.0);
    }

    // ==================== BOOLEAN EQUALITY ====================

    @Test
    void booleanEqualityTrue() {
        LambdaExpression expr = analyzeLambda("booleanEqualityTrue");

        // Compiler may either:
        // 1. Optimize p.active == true to just p.active (FieldAccess)
        // 2. Generate comparison bytecode: active IF_ICMPNE 1 (BinaryOp[active EQ true])
        // Both are semantically equivalent
        if (expr instanceof LambdaExpression.FieldAccess) {
            assertFieldAccess(expr, "active");
        } else {
            assertBinaryOp(expr, LambdaExpression.BinaryOp.Operator.EQ);
            LambdaExpression.BinaryOp binOp = (LambdaExpression.BinaryOp) expr;
            assertFieldAccess(binOp.left(), "active");
            // Right side is a constant (value 1 or true depending on bytecode pattern)
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

    // ==================== TEMPORAL EQUALITY ====================

    @Test
    void localDateEquality() {
        LambdaExpression expr = analyzeLambda("localDateEquality");

        // p.birthDate.isEqual(LocalDate.of(1993, 5, 15))
        assertMethodCall(expr, "isEqual");
        LambdaExpression.MethodCall methodCall = (LambdaExpression.MethodCall) expr;
        assertFieldAccess(methodCall.target(), "birthDate");
        // The argument is a LocalDate constant from LocalDate.of()
    }

    @Test
    void localDateTimeEquality() {
        LambdaExpression expr = analyzeLambda("localDateTimeEquality");

        // p.createdAt.isEqual(LocalDateTime.of(2024, 1, 15, 9, 30))
        assertMethodCall(expr, "isEqual");
        LambdaExpression.MethodCall methodCall = (LambdaExpression.MethodCall) expr;
        assertFieldAccess(methodCall.target(), "createdAt");
    }

    @Test
    void localTimeEquality() {
        LambdaExpression expr = analyzeLambda("localTimeEquality");

        // Analyzer transforms: p.startTime.equals(LocalTime.of(9, 0)) -> p.startTime == LocalTime.of(9, 0)
        assertBinaryOp(expr, LambdaExpression.BinaryOp.Operator.EQ);
        LambdaExpression.BinaryOp binOp = (LambdaExpression.BinaryOp) expr;
        assertFieldAccess(binOp.left(), "startTime");
        // Right side is a LocalTime constant
    }

    // ==================== BIGDECIMAL EQUALITY ====================

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
