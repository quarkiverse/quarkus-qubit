package io.quarkus.qusaq.deployment.criteria;

import io.quarkus.qusaq.deployment.LambdaExpression;
import org.junit.jupiter.api.Test;

/**
 * Criteria query generation tests for null check operations (== null, != null).
 */
class NullCheckOperationsCriteriaTest extends CriteriaQueryTestBase {

    @Test
    void stringNullCheck() {
        LambdaExpression expr = analyzeLambda("stringNullCheck");
        CriteriaQueryStructure structure = generateCriteriaQuery(expr);

        // Verify generation succeeded
        assertCriteriaGenerationSucceeds(expr);

        // Verify the correct Criteria API method was called
        assertCriteriaMethodCalled(structure, "isNull");

        // Verify the field was accessed (p.email == null)
        assertFieldAccessed(structure, "email");
    }

    @Test
    void stringNotNullCheck() {
        LambdaExpression expr = analyzeLambda("stringNotNullCheck");
        CriteriaQueryStructure structure = generateCriteriaQuery(expr);

        // Verify generation succeeded
        assertCriteriaGenerationSucceeds(expr);

        // Verify the correct Criteria API method was called
        assertCriteriaMethodCalled(structure, "isNotNull");

        // Verify the field was accessed (p.email != null)
        assertFieldAccessed(structure, "email");
    }

    @Test
    void doubleNullCheck() {
        LambdaExpression expr = analyzeLambda("doubleNullCheck");
        CriteriaQueryStructure structure = generateCriteriaQuery(expr);
        assertCriteriaGenerationSucceeds(expr);
        assertCriteriaMethodCalled(structure, "isNull");
    }

    @Test
    void longNullCheck() {
        LambdaExpression expr = analyzeLambda("longNullCheck");
        CriteriaQueryStructure structure = generateCriteriaQuery(expr);
        assertCriteriaGenerationSucceeds(expr);
        assertCriteriaMethodCalled(structure, "isNull");
    }

    @Test
    void floatNullCheck() {
        LambdaExpression expr = analyzeLambda("floatNullCheck");
        CriteriaQueryStructure structure = generateCriteriaQuery(expr);
        assertCriteriaGenerationSucceeds(expr);
        assertCriteriaMethodCalled(structure, "isNull");
    }

    @Test
    void localDateNullCheck() {
        LambdaExpression expr = analyzeLambda("localDateNullCheck");
        CriteriaQueryStructure structure = generateCriteriaQuery(expr);
        assertCriteriaGenerationSucceeds(expr);
        assertCriteriaMethodCalled(structure, "isNull");
    }

    @Test
    void localDateTimeNullCheck() {
        LambdaExpression expr = analyzeLambda("localDateTimeNullCheck");
        CriteriaQueryStructure structure = generateCriteriaQuery(expr);
        assertCriteriaGenerationSucceeds(expr);
        assertCriteriaMethodCalled(structure, "isNull");
    }

    @Test
    void localTimeNullCheck() {
        LambdaExpression expr = analyzeLambda("localTimeNullCheck");
        CriteriaQueryStructure structure = generateCriteriaQuery(expr);
        assertCriteriaGenerationSucceeds(expr);
        assertCriteriaMethodCalled(structure, "isNull");
    }
}
