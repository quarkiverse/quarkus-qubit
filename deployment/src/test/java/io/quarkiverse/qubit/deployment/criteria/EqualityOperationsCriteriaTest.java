package io.quarkiverse.qubit.deployment.criteria;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import io.quarkiverse.qubit.deployment.ast.LambdaExpression;

/**
 * Criteria query generation tests for equality operations (==, equals(), isEqual()).
 *
 * <p>
 * This class uses JUnit 5 parameterized tests to consolidate repetitive
 * test patterns, reducing code duplication while maintaining full coverage.
 */
class EqualityOperationsCriteriaTest extends CriteriaQueryTestBase {

    /**
     * Tests for simple equality operations that use cb.equal().
     */
    @ParameterizedTest(name = "{0} → cb.equal()")
    @ValueSource(strings = {
            "integerEquality",
            "floatEquality",
            "doubleEquality",
            "localDateEquality",
            "localDateTimeEquality",
            "localTimeEquality"
    })
    void simpleEquality(String lambdaMethodName) {
        LambdaExpression expr = analyzeLambda(lambdaMethodName);
        CriteriaQueryStructure structure = generateCriteriaQuery(expr);
        assertCriteriaGenerationSucceeds(expr);
        assertCriteriaMethodCalled(structure, "equal");
    }

    @Test
    void stringEquality() {
        LambdaExpression expr = analyzeLambda("stringEquality");
        CriteriaQueryStructure structure = generateCriteriaQuery(expr);
        assertCriteriaGenerationSucceeds(expr);
        assertCriteriaMethodCalled(structure, "equal");
        assertFieldAccessed(structure, "firstName");
        assertConstantUsed(structure, "John");
    }

    @Test
    void longEquality() {
        LambdaExpression expr = analyzeLambda("longEquality");
        CriteriaQueryStructure structure = generateCriteriaQuery(expr);
        assertCriteriaGenerationSucceeds(expr);
        assertCriteriaMethodCalled(structure, "equal");
        assertFieldAccessed(structure, "employeeId");
        assertConstantUsed(structure, 1000001L);
    }

    @Test
    void bigDecimalEquality() {
        LambdaExpression expr = analyzeLambda("bigDecimalEquality");
        CriteriaQueryStructure structure = generateCriteriaQuery(expr);
        assertCriteriaGenerationSucceeds(expr);
        assertCriteriaMethodCalled(structure, "equal");
        assertFieldAccessed(structure, "price");
        assertConstantUsed(structure, "899.99");
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
        // p.active != false → analyzer produces BinaryOp(EQ, active, true) → cb.equal()
        assertCriteriaMethodCalled(structure, "equal");
        assertFieldAccessed(structure, "active");
    }
}
