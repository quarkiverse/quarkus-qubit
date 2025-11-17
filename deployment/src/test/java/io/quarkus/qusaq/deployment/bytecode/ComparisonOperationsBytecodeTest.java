package io.quarkus.qusaq.deployment.bytecode;

import io.quarkus.qusaq.deployment.LambdaExpression;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Bytecode analysis tests for comparison operations (>, <, >=, <=, !=).
 * Tests lambda bytecode parsing without executing queries.
 *
 * <p>Mirrors the test patterns from ComparisonTest integration tests,
 * but focuses on verifying correct bytecode analysis and AST generation.
 *
 * <p>Uses pre-compiled lambda sources from {@link LambdaTestSources} for
 * reliable bytecode generation and analysis.
 */
class ComparisonOperationsBytecodeTest extends PrecompiledLambdaAnalyzer {

    // ==================== INTEGER COMPARISONS ====================

    @Test
    void integerGreaterThan() {
        LambdaExpression expr = analyzeLambda("integerGreaterThan");

        assertBinaryOp(expr, LambdaExpression.BinaryOp.Operator.GT);
        LambdaExpression.BinaryOp binOp = (LambdaExpression.BinaryOp) expr;
        assertFieldAccess(binOp.left(), "age");
        assertConstant(binOp.right(), 30);
    }

    @Test
    void integerGreaterThanOrEqual() {
        LambdaExpression expr = analyzeLambda("integerGreaterThanOrEqual");

        assertBinaryOp(expr, LambdaExpression.BinaryOp.Operator.GE);
        LambdaExpression.BinaryOp binOp = (LambdaExpression.BinaryOp) expr;
        assertFieldAccess(binOp.left(), "age");
        assertConstant(binOp.right(), 30);
    }

    @Test
    void integerLessThan() {
        LambdaExpression expr = analyzeLambda("integerLessThan");

        assertBinaryOp(expr, LambdaExpression.BinaryOp.Operator.LT);
        LambdaExpression.BinaryOp binOp = (LambdaExpression.BinaryOp) expr;
        assertFieldAccess(binOp.left(), "age");
        assertConstant(binOp.right(), 30);
    }

    @Test
    void integerLessThanOrEqual() {
        LambdaExpression expr = analyzeLambda("integerLessThanOrEqual");

        assertBinaryOp(expr, LambdaExpression.BinaryOp.Operator.LE);
        LambdaExpression.BinaryOp binOp = (LambdaExpression.BinaryOp) expr;
        assertFieldAccess(binOp.left(), "age");
        assertConstant(binOp.right(), 30);
    }

    @Test
    void integerNotEquals() {
        LambdaExpression expr = analyzeLambda("integerNotEquals");

        assertBinaryOp(expr, LambdaExpression.BinaryOp.Operator.NE);
        LambdaExpression.BinaryOp binOp = (LambdaExpression.BinaryOp) expr;
        assertFieldAccess(binOp.left(), "age");
        assertConstant(binOp.right(), 30);
    }

    // ==================== LONG COMPARISONS ====================

    @Test
    void longGreaterThan() {
        LambdaExpression expr = analyzeLambda("longGreaterThan");

        assertBinaryOp(expr, LambdaExpression.BinaryOp.Operator.GT);
        LambdaExpression.BinaryOp binOp = (LambdaExpression.BinaryOp) expr;
        assertFieldAccess(binOp.left(), "employeeId");
        assertConstant(binOp.right(), 1000003L);
    }

    @Test
    void longGreaterThanOrEqual() {
        LambdaExpression expr = analyzeLambda("longGreaterThanOrEqual");

        assertBinaryOp(expr, LambdaExpression.BinaryOp.Operator.GE);
        LambdaExpression.BinaryOp binOp = (LambdaExpression.BinaryOp) expr;
        assertFieldAccess(binOp.left(), "employeeId");
        assertConstant(binOp.right(), 1000002L);
    }

    @Test
    void longLessThan() {
        LambdaExpression expr = analyzeLambda("longLessThan");

        assertBinaryOp(expr, LambdaExpression.BinaryOp.Operator.LT);
        LambdaExpression.BinaryOp binOp = (LambdaExpression.BinaryOp) expr;
        assertFieldAccess(binOp.left(), "employeeId");
        assertConstant(binOp.right(), 1000003L);
    }

    @Test
    void longLessThanOrEqual() {
        LambdaExpression expr = analyzeLambda("longLessThanOrEqual");

        assertBinaryOp(expr, LambdaExpression.BinaryOp.Operator.LE);
        LambdaExpression.BinaryOp binOp = (LambdaExpression.BinaryOp) expr;
        assertFieldAccess(binOp.left(), "employeeId");
        assertConstant(binOp.right(), 1000003L);
    }

    @Test
    void longNotEquals() {
        LambdaExpression expr = analyzeLambda("longNotEquals");

        assertBinaryOp(expr, LambdaExpression.BinaryOp.Operator.NE);
        LambdaExpression.BinaryOp binOp = (LambdaExpression.BinaryOp) expr;
        assertFieldAccess(binOp.left(), "employeeId");
        assertConstant(binOp.right(), 1000001L);
    }

    // ==================== FLOAT COMPARISONS ====================

    @Test
    void floatGreaterThan() {
        LambdaExpression expr = analyzeLambda("floatGreaterThan");

        assertBinaryOp(expr, LambdaExpression.BinaryOp.Operator.GT);
        LambdaExpression.BinaryOp binOp = (LambdaExpression.BinaryOp) expr;
        assertFieldAccess(binOp.left(), "height");
        assertConstant(binOp.right(), 1.70f);
    }

    @Test
    void floatGreaterThanOrEqual() {
        LambdaExpression expr = analyzeLambda("floatGreaterThanOrEqual");

        assertBinaryOp(expr, LambdaExpression.BinaryOp.Operator.GE);
        LambdaExpression.BinaryOp binOp = (LambdaExpression.BinaryOp) expr;
        assertFieldAccess(binOp.left(), "height");
        assertConstant(binOp.right(), 1.70f);
    }

    @Test
    void floatLessThan() {
        LambdaExpression expr = analyzeLambda("floatLessThan");

        assertBinaryOp(expr, LambdaExpression.BinaryOp.Operator.LT);
        LambdaExpression.BinaryOp binOp = (LambdaExpression.BinaryOp) expr;
        assertFieldAccess(binOp.left(), "height");
        assertConstant(binOp.right(), 1.70f);
    }

    @Test
    void floatLessThanOrEqual() {
        LambdaExpression expr = analyzeLambda("floatLessThanOrEqual");

        assertBinaryOp(expr, LambdaExpression.BinaryOp.Operator.LE);
        LambdaExpression.BinaryOp binOp = (LambdaExpression.BinaryOp) expr;
        assertFieldAccess(binOp.left(), "height");
        assertConstant(binOp.right(), 1.75f);
    }

    @Test
    void floatNotEquals() {
        LambdaExpression expr = analyzeLambda("floatNotEquals");

        assertBinaryOp(expr, LambdaExpression.BinaryOp.Operator.NE);
        LambdaExpression.BinaryOp binOp = (LambdaExpression.BinaryOp) expr;
        assertFieldAccess(binOp.left(), "height");
        assertConstant(binOp.right(), 1.75f);
    }

    // ==================== DOUBLE COMPARISONS ====================

    @Test
    void doubleGreaterThan() {
        LambdaExpression expr = analyzeLambda("doubleGreaterThan");

        assertBinaryOp(expr, LambdaExpression.BinaryOp.Operator.GT);
        LambdaExpression.BinaryOp binOp = (LambdaExpression.BinaryOp) expr;
        assertFieldAccess(binOp.left(), "salary");
        assertConstant(binOp.right(), 70000.0);
    }

    @Test
    void doubleGreaterThanOrEqual() {
        LambdaExpression expr = analyzeLambda("doubleGreaterThanOrEqual");

        assertBinaryOp(expr, LambdaExpression.BinaryOp.Operator.GE);
        LambdaExpression.BinaryOp binOp = (LambdaExpression.BinaryOp) expr;
        assertFieldAccess(binOp.left(), "salary");
        assertConstant(binOp.right(), 75000.0);
    }

    @Test
    void doubleLessThan() {
        LambdaExpression expr = analyzeLambda("doubleLessThan");

        assertBinaryOp(expr, LambdaExpression.BinaryOp.Operator.LT);
        LambdaExpression.BinaryOp binOp = (LambdaExpression.BinaryOp) expr;
        assertFieldAccess(binOp.left(), "salary");
        assertConstant(binOp.right(), 80000.0);
    }

    @Test
    void doubleLessThanOrEqual() {
        LambdaExpression expr = analyzeLambda("doubleLessThanOrEqual");

        assertBinaryOp(expr, LambdaExpression.BinaryOp.Operator.LE);
        LambdaExpression.BinaryOp binOp = (LambdaExpression.BinaryOp) expr;
        assertFieldAccess(binOp.left(), "salary");
        assertConstant(binOp.right(), 75000.0);
    }

    @Test
    void doubleNotEquals() {
        LambdaExpression expr = analyzeLambda("doubleNotEquals");

        assertBinaryOp(expr, LambdaExpression.BinaryOp.Operator.NE);
        LambdaExpression.BinaryOp binOp = (LambdaExpression.BinaryOp) expr;
        assertFieldAccess(binOp.left(), "salary");
        assertConstant(binOp.right(), 75000.0);
    }

    // ==================== BIGDECIMAL COMPARISONS ====================

    @Test
    void bigDecimalGreaterThan() {
        LambdaExpression expr = analyzeLambda("bigDecimalGreaterThan");

        // The analyzer transforms: p.price.compareTo(new BigDecimal("500")) > 0
        // Into the optimized form: p.price > new BigDecimal("500")
        assertBinaryOp(expr, LambdaExpression.BinaryOp.Operator.GT);
        LambdaExpression.BinaryOp binOp = (LambdaExpression.BinaryOp) expr;

        assertFieldAccess(binOp.left(), "price");
        assertConstant(binOp.right(), new BigDecimal("500"));
    }

    @Test
    void bigDecimalGreaterThanOrEqual() {
        LambdaExpression expr = analyzeLambda("bigDecimalGreaterThanOrEqual");

        // Transformed: p.price >= new BigDecimal("500")
        assertBinaryOp(expr, LambdaExpression.BinaryOp.Operator.GE);
        LambdaExpression.BinaryOp binOp = (LambdaExpression.BinaryOp) expr;
        assertFieldAccess(binOp.left(), "price");
        assertConstant(binOp.right(), new BigDecimal("500"));
    }

    @Test
    void bigDecimalLessThan() {
        LambdaExpression expr = analyzeLambda("bigDecimalLessThan");

        // Transformed: p.price < new BigDecimal("1000")
        assertBinaryOp(expr, LambdaExpression.BinaryOp.Operator.LT);
        LambdaExpression.BinaryOp binOp = (LambdaExpression.BinaryOp) expr;
        assertFieldAccess(binOp.left(), "price");
        assertConstant(binOp.right(), new BigDecimal("1000"));
    }

    @Test
    void bigDecimalLessThanOrEqual() {
        LambdaExpression expr = analyzeLambda("bigDecimalLessThanOrEqual");

        // Transformed: p.price <= new BigDecimal("300")
        assertBinaryOp(expr, LambdaExpression.BinaryOp.Operator.LE);
        LambdaExpression.BinaryOp binOp = (LambdaExpression.BinaryOp) expr;
        assertFieldAccess(binOp.left(), "price");
        assertConstant(binOp.right(), new BigDecimal("300"));
    }

    @Test
    void bigDecimalNotEquals() {
        LambdaExpression expr = analyzeLambda("bigDecimalNotEquals");

        // Note: compareTo() != 0 is represented as compareTo(...) == true
        // This is how the bytecode analyzer represents "non-zero result"
        assertBinaryOp(expr, LambdaExpression.BinaryOp.Operator.EQ);
        LambdaExpression.BinaryOp binOp = (LambdaExpression.BinaryOp) expr;

        // Left side: compareTo method call
        assertMethodCall(binOp.left(), "compareTo");
        LambdaExpression.MethodCall methodCall = (LambdaExpression.MethodCall) binOp.left();
        assertFieldAccess(methodCall.target(), "price");

        // Right side: true (meaning "non-zero", i.e., not equal)
        assertConstant(binOp.right(), true);
    }

    // ==================== TEMPORAL COMPARISONS ====================

    @Test
    void localDateAfter() {
        LambdaExpression expr = analyzeLambda("localDateAfter");

        assertMethodCall(expr, "isAfter");
        LambdaExpression.MethodCall methodCall = (LambdaExpression.MethodCall) expr;
        assertFieldAccess(methodCall.target(), "birthDate");
        assertThat(methodCall.arguments()).hasSize(1);
    }

    @Test
    void localDateBefore() {
        LambdaExpression expr = analyzeLambda("localDateBefore");

        assertMethodCall(expr, "isBefore");
        LambdaExpression.MethodCall methodCall = (LambdaExpression.MethodCall) expr;
        assertFieldAccess(methodCall.target(), "birthDate");
        assertThat(methodCall.arguments()).hasSize(1);
    }

    @Test
    void localDateTimeAfter() {
        LambdaExpression expr = analyzeLambda("localDateTimeAfter");

        assertMethodCall(expr, "isAfter");
        LambdaExpression.MethodCall methodCall = (LambdaExpression.MethodCall) expr;
        assertFieldAccess(methodCall.target(), "createdAt");
        assertThat(methodCall.arguments()).hasSize(1);
    }

    @Test
    void localDateTimeBefore() {
        LambdaExpression expr = analyzeLambda("localDateTimeBefore");

        assertMethodCall(expr, "isBefore");
        LambdaExpression.MethodCall methodCall = (LambdaExpression.MethodCall) expr;
        assertFieldAccess(methodCall.target(), "createdAt");
        assertThat(methodCall.arguments()).hasSize(1);
    }

    @Test
    void localTimeAfter() {
        LambdaExpression expr = analyzeLambda("localTimeAfter");

        assertMethodCall(expr, "isAfter");
        LambdaExpression.MethodCall methodCall = (LambdaExpression.MethodCall) expr;
        assertFieldAccess(methodCall.target(), "startTime");
        assertThat(methodCall.arguments()).hasSize(1);
    }

    @Test
    void localTimeBefore() {
        LambdaExpression expr = analyzeLambda("localTimeBefore");

        assertMethodCall(expr, "isBefore");
        LambdaExpression.MethodCall methodCall = (LambdaExpression.MethodCall) expr;
        assertFieldAccess(methodCall.target(), "startTime");
        assertThat(methodCall.arguments()).hasSize(1);
    }

    // ==================== RANGE QUERIES ====================

    @Test
    void integerRangeQuery() {
        LambdaExpression expr = analyzeLambda("integerRangeQuery");

        // Top level should be AND
        assertBinaryOp(expr, LambdaExpression.BinaryOp.Operator.AND);
        LambdaExpression.BinaryOp andOp = (LambdaExpression.BinaryOp) expr;

        // Left: age >= 25
        assertBinaryOp(andOp.left(), LambdaExpression.BinaryOp.Operator.GE);

        // Right: age <= 35
        assertBinaryOp(andOp.right(), LambdaExpression.BinaryOp.Operator.LE);
    }

    @Test
    void longRangeQuery() {
        LambdaExpression expr = analyzeLambda("longRangeQuery");

        assertBinaryOp(expr, LambdaExpression.BinaryOp.Operator.AND);
        LambdaExpression.BinaryOp andOp = (LambdaExpression.BinaryOp) expr;
        assertBinaryOp(andOp.left(), LambdaExpression.BinaryOp.Operator.GE);
        assertBinaryOp(andOp.right(), LambdaExpression.BinaryOp.Operator.LE);
    }

    @Test
    void floatRangeQuery() {
        LambdaExpression expr = analyzeLambda("floatRangeQuery");

        assertBinaryOp(expr, LambdaExpression.BinaryOp.Operator.AND);
        LambdaExpression.BinaryOp andOp = (LambdaExpression.BinaryOp) expr;
        assertBinaryOp(andOp.left(), LambdaExpression.BinaryOp.Operator.GE);
        assertBinaryOp(andOp.right(), LambdaExpression.BinaryOp.Operator.LE);
    }

    @Test
    void bigDecimalRangeQuery() {
        LambdaExpression expr = analyzeLambda("bigDecimalRangeQuery");

        assertBinaryOp(expr, LambdaExpression.BinaryOp.Operator.AND);
        LambdaExpression.BinaryOp andOp = (LambdaExpression.BinaryOp) expr;
        assertBinaryOp(andOp.left(), LambdaExpression.BinaryOp.Operator.GE);
        assertBinaryOp(andOp.right(), LambdaExpression.BinaryOp.Operator.LE);
    }
}
