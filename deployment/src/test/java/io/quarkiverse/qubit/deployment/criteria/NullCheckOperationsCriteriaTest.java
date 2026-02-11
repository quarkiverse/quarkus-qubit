package io.quarkiverse.qubit.deployment.criteria;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import io.quarkiverse.qubit.deployment.ast.LambdaExpression;

/**
 * Criteria query generation tests for null check operations (== null, != null).
 */
class NullCheckOperationsCriteriaTest extends CriteriaQueryTestBase {

    @ParameterizedTest(name = "{0} → cb.isNull()")
    @ValueSource(strings = {
            "stringNullCheck",
            "doubleNullCheck",
            "longNullCheck",
            "floatNullCheck",
            "localDateNullCheck",
            "localDateTimeNullCheck",
            "localTimeNullCheck"
    })
    void nullCheck(String lambdaMethodName) {
        LambdaExpression expr = analyzeLambda(lambdaMethodName);
        CriteriaQueryStructure structure = generateCriteriaQuery(expr);
        assertCriteriaGenerationSucceeds(expr);
        assertCriteriaMethodCalled(structure, "isNull");
    }

    @Test
    void stringNotNullCheck() {
        LambdaExpression expr = analyzeLambda("stringNotNullCheck");
        CriteriaQueryStructure structure = generateCriteriaQuery(expr);
        assertCriteriaGenerationSucceeds(expr);
        assertCriteriaMethodCalled(structure, "isNotNull");
        assertFieldAccessed(structure, "email");
    }

    @Test
    void nullCheckWithAnd() {
        LambdaExpression expr = analyzeLambda("nullCheckWithAnd");
        CriteriaQueryStructure structure = generateCriteriaQuery(expr);
        assertCriteriaGenerationSucceeds(expr);
        assertCriteriaMethodCalled(structure, "and");
        assertCriteriaMethodCalled(structure, "isNotNull");
    }

    @Test
    void nullCheckWithCondition() {
        LambdaExpression expr = analyzeLambda("nullCheckWithCondition");
        CriteriaQueryStructure structure = generateCriteriaQuery(expr);
        assertCriteriaGenerationSucceeds(expr);
        assertCriteriaMethodCalled(structure, "and");
        assertCriteriaMethodCalled(structure, "isNotNull");
    }

    @Test
    void nullCheckWithOr() {
        LambdaExpression expr = analyzeLambda("nullCheckWithOr");
        CriteriaQueryStructure structure = generateCriteriaQuery(expr);
        assertCriteriaGenerationSucceeds(expr);
        assertCriteriaMethodCalled(structure, "or");
        assertCriteriaMethodCalled(structure, "isNull");
    }
}
