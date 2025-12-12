package io.quarkiverse.qubit.deployment.criteria;

import io.quarkiverse.qubit.deployment.ast.LambdaExpression;
import org.junit.jupiter.api.Test;

/**
 * Criteria query generation tests for equality operations (==, equals(), isEqual()).
 */
class EqualityOperationsCriteriaTest extends CriteriaQueryTestBase {

    @Test
    void stringEquality() {
        LambdaExpression expr = analyzeLambda("stringEquality");
        CriteriaQueryStructure structure = generateCriteriaQuery(expr);

        // Verify generation succeeded
        assertCriteriaGenerationSucceeds(expr);

        // Verify the correct Criteria API method was called
        assertCriteriaMethodCalled(structure, "equal");

        // Verify the field was accessed
        assertFieldAccessed(structure, "firstName");

        // Verify the constant value was used (equals("John"))
        assertConstantUsed(structure, "John");
    }

    @Test
    void integerEquality() {
        LambdaExpression expr = analyzeLambda("integerEquality");
        CriteriaQueryStructure structure = generateCriteriaQuery(expr);
        assertCriteriaGenerationSucceeds(expr);
        assertCriteriaMethodCalled(structure, "equal");
    }

    @Test
    void booleanEqualityTrue() {
        LambdaExpression expr = analyzeLambda("booleanEqualityTrue");
        CriteriaQueryStructure structure = generateCriteriaQuery(expr);
        assertCriteriaGenerationSucceeds(expr);
        // p.active == true should call isTrue
        assertCriteriaMethodCalled(structure, "isTrue");
        assertFieldAccessed(structure, "active");
    }

    @Test
    void booleanEqualityFalse() {
        LambdaExpression expr = analyzeLambda("booleanEqualityFalse");
        CriteriaQueryStructure structure = generateCriteriaQuery(expr);
        assertCriteriaGenerationSucceeds(expr);
        // Compiler optimizes "p.active == false" to "!p.active"
        // which generates: not(isTrue(field))
        assertCriteriaMethodCalled(structure, "not");
        assertCriteriaMethodCalled(structure, "isTrue");
        assertFieldAccessed(structure, "active");
    }

    @Test
    void booleanImplicit() {
        LambdaExpression expr = analyzeLambda("booleanImplicit");
        CriteriaQueryStructure structure = generateCriteriaQuery(expr);
        assertCriteriaGenerationSucceeds(expr);
        // p.active (implicit true check) should call isTrue
        assertCriteriaMethodCalled(structure, "isTrue");
        assertFieldAccessed(structure, "active");
    }

    @Test
    void booleanNotEqualityTrue() {
        LambdaExpression expr = analyzeLambda("booleanNotEqualityTrue");
        CriteriaQueryStructure structure = generateCriteriaQuery(expr);
        assertCriteriaGenerationSucceeds(expr);
        // p.active != true should call isFalse
        assertCriteriaMethodCalled(structure, "isFalse");
        assertFieldAccessed(structure, "active");
    }

    @Test
    void booleanNotEqualityFalse() {
        LambdaExpression expr = analyzeLambda("booleanNotEqualityFalse");
        CriteriaQueryStructure structure = generateCriteriaQuery(expr);
        assertCriteriaGenerationSucceeds(expr);
        // Compiler transforms "p.active != false" to "p.active == true" with boolean constant
        // which falls through to general equality and generates cb.equal()
        assertCriteriaMethodCalled(structure, "equal");
        assertFieldAccessed(structure, "active");
    }

    @Test
    void longEquality() {
        LambdaExpression expr = analyzeLambda("longEquality");
        CriteriaQueryStructure structure = generateCriteriaQuery(expr);

        // Verify generation succeeded
        assertCriteriaGenerationSucceeds(expr);

        // Verify the correct Criteria API method was called
        assertCriteriaMethodCalled(structure, "equal");

        // Verify the field was accessed
        assertFieldAccessed(structure, "employeeId");

        // Verify the constant value was used (== 1000001L)
        assertConstantUsed(structure, 1000001L);
    }

    @Test
    void floatEquality() {
        LambdaExpression expr = analyzeLambda("floatEquality");
        CriteriaQueryStructure structure = generateCriteriaQuery(expr);
        assertCriteriaGenerationSucceeds(expr);
        assertCriteriaMethodCalled(structure, "equal");
    }

    @Test
    void doubleEquality() {
        LambdaExpression expr = analyzeLambda("doubleEquality");
        CriteriaQueryStructure structure = generateCriteriaQuery(expr);
        assertCriteriaGenerationSucceeds(expr);
        assertCriteriaMethodCalled(structure, "equal");
    }

    @Test
    void localDateEquality() {
        LambdaExpression expr = analyzeLambda("localDateEquality");
        CriteriaQueryStructure structure = generateCriteriaQuery(expr);
        assertCriteriaGenerationSucceeds(expr);
        assertCriteriaMethodCalled(structure, "equal");
    }

    @Test
    void localDateTimeEquality() {
        LambdaExpression expr = analyzeLambda("localDateTimeEquality");
        CriteriaQueryStructure structure = generateCriteriaQuery(expr);
        assertCriteriaGenerationSucceeds(expr);
        assertCriteriaMethodCalled(structure, "equal");
    }

    @Test
    void localTimeEquality() {
        LambdaExpression expr = analyzeLambda("localTimeEquality");
        CriteriaQueryStructure structure = generateCriteriaQuery(expr);
        assertCriteriaGenerationSucceeds(expr);
        assertCriteriaMethodCalled(structure, "equal");
    }

    @Test
    void bigDecimalEquality() {
        LambdaExpression expr = analyzeLambda("bigDecimalEquality");
        CriteriaQueryStructure structure = generateCriteriaQuery(expr);

        // Verify generation succeeded
        assertCriteriaGenerationSucceeds(expr);

        // Verify the correct Criteria API method was called
        assertCriteriaMethodCalled(structure, "equal");

        // Verify the field was accessed
        assertFieldAccessed(structure, "price");

        // Verify the constant value was used (new BigDecimal("899.99"))
        assertConstantUsed(structure, "899.99");
    }
}
