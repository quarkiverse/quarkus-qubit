package io.quarkiverse.qubit.deployment.criteria;

import io.quarkiverse.qubit.deployment.ast.LambdaExpression;
import org.junit.jupiter.api.Test;

/**
 * Criteria query generation tests for String operations.
 */
class StringOperationsCriteriaTest extends CriteriaQueryTestBase {

    @Test
    void stringStartsWith() {
        LambdaExpression expr = analyzeLambda("stringStartsWith");
        CriteriaQueryStructure structure = generateCriteriaQuery(expr);

        // Verify generation succeeded
        assertCriteriaGenerationSucceeds(expr);

        // Verify the correct Criteria API method was called
        assertCriteriaMethodCalled(structure, "like");

        // Verify the field was accessed
        assertFieldAccessed(structure, "firstName");

        // Verify the pattern constants (startsWith("J") generates "J" + "%")
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

        // Verify generation succeeded
        assertCriteriaGenerationSucceeds(expr);

        // Verify the correct Criteria API method was called
        assertCriteriaMethodCalled(structure, "length");

        // Verify the field was accessed
        assertFieldAccessed(structure, "firstName");
    }

    @Test
    void stringToLowerCase() {
        LambdaExpression expr = analyzeLambda("stringToLowerCase");
        CriteriaQueryStructure structure = generateCriteriaQuery(expr);

        // Verify generation succeeded
        assertCriteriaGenerationSucceeds(expr);

        // Verify the correct Criteria API method was called
        assertCriteriaMethodCalled(structure, "lower");

        // Verify the field was accessed
        assertFieldAccessed(structure, "firstName");

        // Verify the comparison constant was used (equals("john"))
        assertConstantUsed(structure, "john");
    }

    @Test
    void stringToUpperCase() {
        LambdaExpression expr = analyzeLambda("stringToUpperCase");
        CriteriaQueryStructure structure = generateCriteriaQuery(expr);
        assertCriteriaGenerationSucceeds(expr);
        assertCriteriaMethodCalled(structure, "upper");
    }

    @Test
    void stringTrim() {
        LambdaExpression expr = analyzeLambda("stringTrim");
        CriteriaQueryStructure structure = generateCriteriaQuery(expr);
        assertCriteriaGenerationSucceeds(expr);
        assertCriteriaMethodCalled(structure, "trim");
    }

    @Test
    void stringIsEmpty() {
        LambdaExpression expr = analyzeLambda("stringIsEmpty");
        CriteriaQueryStructure structure = generateCriteriaQuery(expr);
        assertCriteriaGenerationSucceeds(expr);
        assertCriteriaMethodCalled(structure, "equal");
    }

    @Test
    void stringSubstring() {
        LambdaExpression expr = analyzeLambda("stringSubstring");
        CriteriaQueryStructure structure = generateCriteriaQuery(expr);
        assertCriteriaGenerationSucceeds(expr);
        assertCriteriaMethodCalled(structure, "substring");
    }

    @Test
    void stringMethodChaining() {
        LambdaExpression expr = analyzeLambda("stringMethodChaining");
        assertCriteriaGenerationSucceeds(expr);
    }

    @Test
    void stringComplexConditions() {
        LambdaExpression expr = analyzeLambda("stringComplexConditions");
        assertCriteriaGenerationSucceeds(expr);
    }
}
