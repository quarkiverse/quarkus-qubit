package io.quarkiverse.qubit.deployment.criteria;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import io.quarkiverse.qubit.deployment.ast.LambdaExpression;

/**
 * Criteria query generation tests for String operations.
 *
 * <p>
 * This class uses JUnit 5 parameterized tests to consolidate repetitive
 * test patterns, reducing code duplication while maintaining full coverage.
 */
class StringOperationsCriteriaTest extends CriteriaQueryTestBase {

    // ==================== PARAMETERIZED TESTS ====================

    /**
     * Tests for simple string transformation methods.
     */
    @ParameterizedTest(name = "{0} → cb.{1}()")
    @CsvSource({
            "stringToUpperCase, upper",
            "stringTrim, trim",
            "stringIsEmpty, equal",
            "stringIsBlank, equal",
            "stringSubstring, substring"
    })
    void stringTransformation(String lambdaMethodName, String expectedMethod) {
        LambdaExpression expr = analyzeLambda(lambdaMethodName);
        CriteriaQueryStructure structure = generateCriteriaQuery(expr);
        assertCriteriaGenerationSucceeds(expr);
        assertCriteriaMethodCalled(structure, expectedMethod);
    }

    /**
     * Tests that verify criteria generation succeeds without additional assertions.
     */
    @ParameterizedTest(name = "{0} → success")
    @ValueSource(strings = { "stringMethodChaining", "stringComplexConditions" })
    void complexStringOperation(String lambdaMethodName) {
        LambdaExpression expr = analyzeLambda(lambdaMethodName);
        assertCriteriaGenerationSucceeds(expr);
    }

    // ==================== PATTERN-BASED STRING OPERATIONS ====================

    @Test
    void stringStartsWith() {
        LambdaExpression expr = analyzeLambda("stringStartsWith");
        CriteriaQueryStructure structure = generateCriteriaQuery(expr);
        assertCriteriaGenerationSucceeds(expr);
        assertCriteriaMethodCalled(structure, "like");
        assertFieldAccessed(structure, "firstName");
        // startsWith("J") generates "J" + "%"
        assertConstantUsed(structure, "J");
        assertConstantUsed(structure, "%");
    }

    @Test
    void stringEndsWith() {
        LambdaExpression expr = analyzeLambda("stringEndsWith");
        CriteriaQueryStructure structure = generateCriteriaQuery(expr);
        assertCriteriaGenerationSucceeds(expr);
        assertCriteriaMethodCalled(structure, "like");
        assertFieldAccessed(structure, "email");
        // endsWith pattern should have % prefix
        assertConstantUsed(structure, "%");
        assertCriteriaMethodCalled(structure, "concat");
    }

    @Test
    void stringContains() {
        LambdaExpression expr = analyzeLambda("stringContains");
        CriteriaQueryStructure structure = generateCriteriaQuery(expr);
        assertCriteriaGenerationSucceeds(expr);
        assertCriteriaMethodCalled(structure, "like");
        assertFieldAccessed(structure, "email");
        // contains pattern should have % prefix and suffix (2 concats)
        assertCriteriaMethodCallCount(structure, "concat", 2);
    }

    @Test
    void stringLength() {
        LambdaExpression expr = analyzeLambda("stringLength");
        CriteriaQueryStructure structure = generateCriteriaQuery(expr);
        assertCriteriaGenerationSucceeds(expr);
        assertCriteriaMethodCalled(structure, "length");
        assertFieldAccessed(structure, "firstName");
    }

    @Test
    void stringToLowerCase() {
        LambdaExpression expr = analyzeLambda("stringToLowerCase");
        CriteriaQueryStructure structure = generateCriteriaQuery(expr);
        assertCriteriaGenerationSucceeds(expr);
        assertCriteriaMethodCalled(structure, "lower");
        assertFieldAccessed(structure, "firstName");
        assertConstantUsed(structure, "john");
    }

    @Test
    void stringIsBlank() {
        LambdaExpression expr = analyzeLambda("stringIsBlank");
        CriteriaQueryStructure structure = generateCriteriaQuery(expr);
        assertCriteriaGenerationSucceeds(expr);
        // isBlank() generates cb.equal(cb.trim(field), cb.literal(""))
        assertCriteriaMethodCalled(structure, "trim");
        assertCriteriaMethodCalled(structure, "equal");
        assertFieldAccessed(structure, "email");
        assertConstantUsed(structure, ""); // empty string literal
    }
}
