package io.quarkiverse.qubit.deployment.bytecode;

import io.quarkiverse.qubit.deployment.ast.LambdaExpression;
import org.junit.jupiter.api.Test;

/**
 * Bytecode analysis tests for arithmetic operations (+, -, *, /, %).
 * Tests lambda bytecode parsing without executing queries.
 */
class ArithmeticOperationsBytecodeTest extends PrecompiledLambdaAnalyzer {

    // ==================== INTEGER ARITHMETIC ====================

    @Test
    void integerAddition() {
        LambdaExpression expr = analyzeLambda("integerAddition");

        // p.age + 5 > 35
        assertBinaryOp(expr, LambdaExpression.BinaryOp.Operator.GT);
        LambdaExpression.BinaryOp gtOp = (LambdaExpression.BinaryOp) expr;

        // Left: p.age + 5
        assertBinaryOp(gtOp.left(), LambdaExpression.BinaryOp.Operator.ADD);
        LambdaExpression.BinaryOp addOp = (LambdaExpression.BinaryOp) gtOp.left();
        assertFieldAccess(addOp.left(), "age");
        assertConstant(addOp.right(), 5);

        // Right: 35
        assertConstant(gtOp.right(), 35);
    }

    @Test
    void integerSubtraction() {
        LambdaExpression expr = analyzeLambda("integerSubtraction");

        // p.age - 5 > 20
        assertBinaryOp(expr, LambdaExpression.BinaryOp.Operator.GT);
        LambdaExpression.BinaryOp gtOp = (LambdaExpression.BinaryOp) expr;

        // Left: p.age - 5
        assertBinaryOp(gtOp.left(), LambdaExpression.BinaryOp.Operator.SUB);
        LambdaExpression.BinaryOp subOp = (LambdaExpression.BinaryOp) gtOp.left();
        assertFieldAccess(subOp.left(), "age");
        assertConstant(subOp.right(), 5);

        // Right: 20
        assertConstant(gtOp.right(), 20);
    }

    @Test
    void integerMultiplication() {
        LambdaExpression expr = analyzeLambda("integerMultiplication");

        // p.age * 2 > 60
        assertBinaryOp(expr, LambdaExpression.BinaryOp.Operator.GT);
        LambdaExpression.BinaryOp gtOp = (LambdaExpression.BinaryOp) expr;

        // Left: p.age * 2
        assertBinaryOp(gtOp.left(), LambdaExpression.BinaryOp.Operator.MUL);
        LambdaExpression.BinaryOp mulOp = (LambdaExpression.BinaryOp) gtOp.left();
        assertFieldAccess(mulOp.left(), "age");
        assertConstant(mulOp.right(), 2);

        // Right: 60
        assertConstant(gtOp.right(), 60);
    }

    @Test
    void integerDivision() {
        LambdaExpression expr = analyzeLambda("integerDivision");

        // p.age / 2 > 15
        assertBinaryOp(expr, LambdaExpression.BinaryOp.Operator.GT);
        LambdaExpression.BinaryOp gtOp = (LambdaExpression.BinaryOp) expr;

        // Left: p.age / 2
        assertBinaryOp(gtOp.left(), LambdaExpression.BinaryOp.Operator.DIV);
        LambdaExpression.BinaryOp divOp = (LambdaExpression.BinaryOp) gtOp.left();
        assertFieldAccess(divOp.left(), "age");
        assertConstant(divOp.right(), 2);

        // Right: 15
        assertConstant(gtOp.right(), 15);
    }

    @Test
    void integerModulo() {
        LambdaExpression expr = analyzeLambda("integerModulo");

        // p.age % 10 == 0
        assertBinaryOp(expr, LambdaExpression.BinaryOp.Operator.EQ);
        LambdaExpression.BinaryOp eqOp = (LambdaExpression.BinaryOp) expr;

        // Left: p.age % 10
        assertBinaryOp(eqOp.left(), LambdaExpression.BinaryOp.Operator.MOD);
        LambdaExpression.BinaryOp modOp = (LambdaExpression.BinaryOp) eqOp.left();
        assertFieldAccess(modOp.left(), "age");
        assertConstant(modOp.right(), 10);

        // Right: 0
        assertConstant(eqOp.right(), 0);
    }

    // ==================== LONG ARITHMETIC ====================

    @Test
    void longAddition() {
        LambdaExpression expr = analyzeLambda("longAddition");

        // p.employeeId + 10L > 1000010L
        assertBinaryOp(expr, LambdaExpression.BinaryOp.Operator.GT);
        LambdaExpression.BinaryOp gtOp = (LambdaExpression.BinaryOp) expr;

        // Left: p.employeeId + 10L
        assertBinaryOp(gtOp.left(), LambdaExpression.BinaryOp.Operator.ADD);
        LambdaExpression.BinaryOp addOp = (LambdaExpression.BinaryOp) gtOp.left();
        assertFieldAccess(addOp.left(), "employeeId");
        assertConstant(addOp.right(), 10L);

        // Right: 1000010L
        assertConstant(gtOp.right(), 1000010L);
    }

    @Test
    void longSubtraction() {
        LambdaExpression expr = analyzeLambda("longSubtraction");

        // p.employeeId - 10L < 1000000L
        assertBinaryOp(expr, LambdaExpression.BinaryOp.Operator.LT);
        LambdaExpression.BinaryOp ltOp = (LambdaExpression.BinaryOp) expr;

        // Left: p.employeeId - 10L
        assertBinaryOp(ltOp.left(), LambdaExpression.BinaryOp.Operator.SUB);
        LambdaExpression.BinaryOp subOp = (LambdaExpression.BinaryOp) ltOp.left();
        assertFieldAccess(subOp.left(), "employeeId");
        assertConstant(subOp.right(), 10L);

        // Right: 1000000L
        assertConstant(ltOp.right(), 1000000L);
    }

    @Test
    void longMultiplication() {
        LambdaExpression expr = analyzeLambda("longMultiplication");

        // p.employeeId * 2L > 2000000L
        assertBinaryOp(expr, LambdaExpression.BinaryOp.Operator.GT);
        LambdaExpression.BinaryOp gtOp = (LambdaExpression.BinaryOp) expr;

        // Left: p.employeeId * 2L
        assertBinaryOp(gtOp.left(), LambdaExpression.BinaryOp.Operator.MUL);
        LambdaExpression.BinaryOp mulOp = (LambdaExpression.BinaryOp) gtOp.left();
        assertFieldAccess(mulOp.left(), "employeeId");
        assertConstant(mulOp.right(), 2L);

        // Right: 2000000L
        assertConstant(gtOp.right(), 2000000L);
    }

    @Test
    void longDivision() {
        LambdaExpression expr = analyzeLambda("longDivision");

        // p.employeeId / 2L < 500002L
        assertBinaryOp(expr, LambdaExpression.BinaryOp.Operator.LT);
        LambdaExpression.BinaryOp ltOp = (LambdaExpression.BinaryOp) expr;

        // Left: p.employeeId / 2L
        assertBinaryOp(ltOp.left(), LambdaExpression.BinaryOp.Operator.DIV);
        LambdaExpression.BinaryOp divOp = (LambdaExpression.BinaryOp) ltOp.left();
        assertFieldAccess(divOp.left(), "employeeId");
        assertConstant(divOp.right(), 2L);

        // Right: 500002L
        assertConstant(ltOp.right(), 500002L);
    }

    @Test
    void longModulo() {
        LambdaExpression expr = analyzeLambda("longModulo");

        // p.employeeId % 2L == 1L
        assertBinaryOp(expr, LambdaExpression.BinaryOp.Operator.EQ);
        LambdaExpression.BinaryOp eqOp = (LambdaExpression.BinaryOp) expr;

        // Left: p.employeeId % 2L
        assertBinaryOp(eqOp.left(), LambdaExpression.BinaryOp.Operator.MOD);
        LambdaExpression.BinaryOp modOp = (LambdaExpression.BinaryOp) eqOp.left();
        assertFieldAccess(modOp.left(), "employeeId");
        assertConstant(modOp.right(), 2L);

        // Right: 1L
        assertConstant(eqOp.right(), 1L);
    }

    // ==================== FLOAT ARITHMETIC ====================

    @Test
    void floatAddition() {
        LambdaExpression expr = analyzeLambda("floatAddition");

        // p.height + 0.10f > 1.85f
        assertBinaryOp(expr, LambdaExpression.BinaryOp.Operator.GT);
        LambdaExpression.BinaryOp gtOp = (LambdaExpression.BinaryOp) expr;

        // Left: p.height + 0.10f
        assertBinaryOp(gtOp.left(), LambdaExpression.BinaryOp.Operator.ADD);
        LambdaExpression.BinaryOp addOp = (LambdaExpression.BinaryOp) gtOp.left();
        assertFieldAccess(addOp.left(), "height");
        assertConstant(addOp.right(), 0.10f);

        // Right: 1.85f
        assertConstant(gtOp.right(), 1.85f);
    }

    @Test
    void floatSubtraction() {
        LambdaExpression expr = analyzeLambda("floatSubtraction");

        // p.height - 0.05f < 1.70f
        assertBinaryOp(expr, LambdaExpression.BinaryOp.Operator.LT);
        LambdaExpression.BinaryOp ltOp = (LambdaExpression.BinaryOp) expr;

        // Left: p.height - 0.05f
        assertBinaryOp(ltOp.left(), LambdaExpression.BinaryOp.Operator.SUB);
        LambdaExpression.BinaryOp subOp = (LambdaExpression.BinaryOp) ltOp.left();
        assertFieldAccess(subOp.left(), "height");
        assertConstant(subOp.right(), 0.05f);

        // Right: 1.70f
        assertConstant(ltOp.right(), 1.70f);
    }

    @Test
    void floatMultiplication() {
        LambdaExpression expr = analyzeLambda("floatMultiplication");

        // p.height * 2.0f > 3.5f
        assertBinaryOp(expr, LambdaExpression.BinaryOp.Operator.GT);
        LambdaExpression.BinaryOp gtOp = (LambdaExpression.BinaryOp) expr;

        // Left: p.height * 2.0f
        assertBinaryOp(gtOp.left(), LambdaExpression.BinaryOp.Operator.MUL);
        LambdaExpression.BinaryOp mulOp = (LambdaExpression.BinaryOp) gtOp.left();
        assertFieldAccess(mulOp.left(), "height");
        assertConstant(mulOp.right(), 2.0f);

        // Right: 3.5f
        assertConstant(gtOp.right(), 3.5f);
    }

    @Test
    void floatDivision() {
        LambdaExpression expr = analyzeLambda("floatDivision");

        // p.height / 2.0f < 0.85f
        assertBinaryOp(expr, LambdaExpression.BinaryOp.Operator.LT);
        LambdaExpression.BinaryOp ltOp = (LambdaExpression.BinaryOp) expr;

        // Left: p.height / 2.0f
        assertBinaryOp(ltOp.left(), LambdaExpression.BinaryOp.Operator.DIV);
        LambdaExpression.BinaryOp divOp = (LambdaExpression.BinaryOp) ltOp.left();
        assertFieldAccess(divOp.left(), "height");
        assertConstant(divOp.right(), 2.0f);

        // Right: 0.85f
        assertConstant(ltOp.right(), 0.85f);
    }

    // ==================== DOUBLE ARITHMETIC ====================

    @Test
    void doubleAddition() {
        LambdaExpression expr = analyzeLambda("doubleAddition");

        // p.salary + 5000.0 > 80000.0
        assertBinaryOp(expr, LambdaExpression.BinaryOp.Operator.GT);
        LambdaExpression.BinaryOp gtOp = (LambdaExpression.BinaryOp) expr;

        // Left: p.salary + 5000.0
        assertBinaryOp(gtOp.left(), LambdaExpression.BinaryOp.Operator.ADD);
        LambdaExpression.BinaryOp addOp = (LambdaExpression.BinaryOp) gtOp.left();
        assertFieldAccess(addOp.left(), "salary");
        assertConstant(addOp.right(), 5000.0);

        // Right: 80000.0
        assertConstant(gtOp.right(), 80000.0);
    }

    @Test
    void doubleSubtraction() {
        LambdaExpression expr = analyzeLambda("doubleSubtraction");

        // p.salary - 10000.0 < 70000.0
        assertBinaryOp(expr, LambdaExpression.BinaryOp.Operator.LT);
        LambdaExpression.BinaryOp ltOp = (LambdaExpression.BinaryOp) expr;

        // Left: p.salary - 10000.0
        assertBinaryOp(ltOp.left(), LambdaExpression.BinaryOp.Operator.SUB);
        LambdaExpression.BinaryOp subOp = (LambdaExpression.BinaryOp) ltOp.left();
        assertFieldAccess(subOp.left(), "salary");
        assertConstant(subOp.right(), 10000.0);

        // Right: 70000.0
        assertConstant(ltOp.right(), 70000.0);
    }

    @Test
    void doubleMultiplication() {
        LambdaExpression expr = analyzeLambda("doubleMultiplication");

        // p.salary * 1.1 > 80000.0
        assertBinaryOp(expr, LambdaExpression.BinaryOp.Operator.GT);
        LambdaExpression.BinaryOp gtOp = (LambdaExpression.BinaryOp) expr;

        // Left: p.salary * 1.1
        assertBinaryOp(gtOp.left(), LambdaExpression.BinaryOp.Operator.MUL);
        LambdaExpression.BinaryOp mulOp = (LambdaExpression.BinaryOp) gtOp.left();
        assertFieldAccess(mulOp.left(), "salary");
        assertConstant(mulOp.right(), 1.1);

        // Right: 80000.0
        assertConstant(gtOp.right(), 80000.0);
    }

    @Test
    void doubleDivision() {
        LambdaExpression expr = analyzeLambda("doubleDivision");

        // p.salary / 1000.0 > 75.0
        assertBinaryOp(expr, LambdaExpression.BinaryOp.Operator.GT);
        LambdaExpression.BinaryOp gtOp = (LambdaExpression.BinaryOp) expr;

        // Left: p.salary / 1000.0
        assertBinaryOp(gtOp.left(), LambdaExpression.BinaryOp.Operator.DIV);
        LambdaExpression.BinaryOp divOp = (LambdaExpression.BinaryOp) gtOp.left();
        assertFieldAccess(divOp.left(), "salary");
        assertConstant(divOp.right(), 1000.0);

        // Right: 75.0
        assertConstant(gtOp.right(), 75.0);
    }

    // ==================== FIELD-FIELD ARITHMETIC ====================

    @Test
    void longFieldFieldAddition() {
        LambdaExpression expr = analyzeLambda("longFieldFieldAddition");

        // p.employeeId + p.employeeId > 2000000L
        assertBinaryOp(expr, LambdaExpression.BinaryOp.Operator.GT);
        LambdaExpression.BinaryOp gtOp = (LambdaExpression.BinaryOp) expr;

        // Left: p.employeeId + p.employeeId
        assertBinaryOp(gtOp.left(), LambdaExpression.BinaryOp.Operator.ADD);
        LambdaExpression.BinaryOp addOp = (LambdaExpression.BinaryOp) gtOp.left();
        assertFieldAccess(addOp.left(), "employeeId");
        assertFieldAccess(addOp.right(), "employeeId");

        // Right: 2000000L
        assertConstant(gtOp.right(), 2000000L);
    }

    @Test
    void longFieldFieldSubtraction() {
        LambdaExpression expr = analyzeLambda("longFieldFieldSubtraction");

        // p.employeeId - p.employeeId == 0L
        assertBinaryOp(expr, LambdaExpression.BinaryOp.Operator.EQ);
        LambdaExpression.BinaryOp eqOp = (LambdaExpression.BinaryOp) expr;

        // Left: p.employeeId - p.employeeId
        assertBinaryOp(eqOp.left(), LambdaExpression.BinaryOp.Operator.SUB);
        LambdaExpression.BinaryOp subOp = (LambdaExpression.BinaryOp) eqOp.left();
        assertFieldAccess(subOp.left(), "employeeId");
        assertFieldAccess(subOp.right(), "employeeId");

        // Right: 0L
        assertConstant(eqOp.right(), 0L);
    }
}
