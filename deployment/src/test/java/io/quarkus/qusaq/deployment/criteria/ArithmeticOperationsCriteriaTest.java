package io.quarkus.qusaq.deployment.criteria;

import io.quarkus.qusaq.deployment.LambdaExpression;
import org.junit.jupiter.api.Test;

/**
 * Criteria query generation tests for arithmetic operations (+, -, *, /, %).
 */
class ArithmeticOperationsCriteriaTest extends CriteriaQueryTestBase {

    // ==================== INTEGER ARITHMETIC ====================

    @Test
    void integerAddition() {
        LambdaExpression expr = analyzeLambda("integerAddition");
        CriteriaQueryStructure structure = generateCriteriaQuery(expr);

        // Verify generation succeeded
        assertCriteriaGenerationSucceeds(expr);

        // Verify the correct Criteria API method was called
        assertCriteriaMethodCalled(structure, "sum");

        // Verify the field was accessed
        assertFieldAccessed(structure, "age");

        // Verify comparison constant (small integers like 5 compile to BIPUSH, not LDC)
        assertConstantUsed(structure, 35);
    }

    @Test
    void integerSubtraction() {
        LambdaExpression expr = analyzeLambda("integerSubtraction");
        CriteriaQueryStructure structure = generateCriteriaQuery(expr);

        // Verify generation succeeded
        assertCriteriaGenerationSucceeds(expr);

        // Verify the correct Criteria API method was called
        assertCriteriaMethodCalled(structure, "diff");

        // Verify the field was accessed
        assertFieldAccessed(structure, "age");
    }

    @Test
    void integerMultiplication() {
        LambdaExpression expr = analyzeLambda("integerMultiplication");
        CriteriaQueryStructure structure = generateCriteriaQuery(expr);
        assertCriteriaGenerationSucceeds(expr);
        assertCriteriaMethodCalled(structure, "prod");
    }

    @Test
    void integerDivision() {
        LambdaExpression expr = analyzeLambda("integerDivision");
        CriteriaQueryStructure structure = generateCriteriaQuery(expr);
        assertCriteriaGenerationSucceeds(expr);
        assertCriteriaMethodCalled(structure, "quot");
    }

    @Test
    void integerModulo() {
        LambdaExpression expr = analyzeLambda("integerModulo");
        CriteriaQueryStructure structure = generateCriteriaQuery(expr);
        assertCriteriaGenerationSucceeds(expr);
        assertCriteriaMethodCalled(structure, "mod");
    }

    // ==================== LONG ARITHMETIC ====================

    @Test
    void longAddition() {
        LambdaExpression expr = analyzeLambda("longAddition");
        CriteriaQueryStructure structure = generateCriteriaQuery(expr);
        assertCriteriaGenerationSucceeds(expr);
        assertCriteriaMethodCalled(structure, "sum");
    }

    @Test
    void longSubtraction() {
        LambdaExpression expr = analyzeLambda("longSubtraction");
        CriteriaQueryStructure structure = generateCriteriaQuery(expr);
        assertCriteriaGenerationSucceeds(expr);
        assertCriteriaMethodCalled(structure, "diff");
    }

    @Test
    void longMultiplication() {
        LambdaExpression expr = analyzeLambda("longMultiplication");
        CriteriaQueryStructure structure = generateCriteriaQuery(expr);
        assertCriteriaGenerationSucceeds(expr);
        assertCriteriaMethodCalled(structure, "prod");
    }

    @Test
    void longDivision() {
        LambdaExpression expr = analyzeLambda("longDivision");
        CriteriaQueryStructure structure = generateCriteriaQuery(expr);
        assertCriteriaGenerationSucceeds(expr);
        assertCriteriaMethodCalled(structure, "quot");
    }

    @Test
    void longModulo() {
        LambdaExpression expr = analyzeLambda("longModulo");
        CriteriaQueryStructure structure = generateCriteriaQuery(expr);
        assertCriteriaGenerationSucceeds(expr);
        assertCriteriaMethodCalled(structure, "mod");
    }

    // ==================== FLOAT ARITHMETIC ====================

    @Test
    void floatAddition() {
        LambdaExpression expr = analyzeLambda("floatAddition");
        CriteriaQueryStructure structure = generateCriteriaQuery(expr);
        assertCriteriaGenerationSucceeds(expr);
        assertCriteriaMethodCalled(structure, "sum");
    }

    @Test
    void floatSubtraction() {
        LambdaExpression expr = analyzeLambda("floatSubtraction");
        CriteriaQueryStructure structure = generateCriteriaQuery(expr);
        assertCriteriaGenerationSucceeds(expr);
        assertCriteriaMethodCalled(structure, "diff");
    }

    @Test
    void floatMultiplication() {
        LambdaExpression expr = analyzeLambda("floatMultiplication");
        CriteriaQueryStructure structure = generateCriteriaQuery(expr);
        assertCriteriaGenerationSucceeds(expr);
        assertCriteriaMethodCalled(structure, "prod");
    }

    @Test
    void floatDivision() {
        LambdaExpression expr = analyzeLambda("floatDivision");
        CriteriaQueryStructure structure = generateCriteriaQuery(expr);
        assertCriteriaGenerationSucceeds(expr);
        assertCriteriaMethodCalled(structure, "quot");
    }

    // ==================== DOUBLE ARITHMETIC ====================

    @Test
    void doubleAddition() {
        LambdaExpression expr = analyzeLambda("doubleAddition");
        CriteriaQueryStructure structure = generateCriteriaQuery(expr);
        assertCriteriaGenerationSucceeds(expr);
        assertCriteriaMethodCalled(structure, "sum");
    }

    @Test
    void doubleSubtraction() {
        LambdaExpression expr = analyzeLambda("doubleSubtraction");
        CriteriaQueryStructure structure = generateCriteriaQuery(expr);
        assertCriteriaGenerationSucceeds(expr);
        assertCriteriaMethodCalled(structure, "diff");
    }

    @Test
    void doubleMultiplication() {
        LambdaExpression expr = analyzeLambda("doubleMultiplication");
        CriteriaQueryStructure structure = generateCriteriaQuery(expr);
        assertCriteriaGenerationSucceeds(expr);
        assertCriteriaMethodCalled(structure, "prod");
    }

    @Test
    void doubleDivision() {
        LambdaExpression expr = analyzeLambda("doubleDivision");
        CriteriaQueryStructure structure = generateCriteriaQuery(expr);
        assertCriteriaGenerationSucceeds(expr);
        assertCriteriaMethodCalled(structure, "quot");
    }

    // ==================== FIELD-FIELD ARITHMETIC ====================

    @Test
    void longFieldFieldAddition() {
        LambdaExpression expr = analyzeLambda("longFieldFieldAddition");
        CriteriaQueryStructure structure = generateCriteriaQuery(expr);

        // Verify generation succeeded
        assertCriteriaGenerationSucceeds(expr);

        // Verify the correct Criteria API method was called
        assertCriteriaMethodCalled(structure, "sum");

        // Verify the field was accessed (p.employeeId + p.employeeId)
        assertFieldAccessed(structure, "employeeId");

        // Verify the constant value was used (> 2000000L)
        assertConstantUsed(structure, 2000000L);
    }

    @Test
    void longFieldFieldSubtraction() {
        LambdaExpression expr = analyzeLambda("longFieldFieldSubtraction");
        CriteriaQueryStructure structure = generateCriteriaQuery(expr);
        assertCriteriaGenerationSucceeds(expr);
        assertCriteriaMethodCalled(structure, "diff");
    }
}
