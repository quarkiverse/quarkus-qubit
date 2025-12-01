package io.quarkiverse.qubit.deployment.criteria;

import io.quarkiverse.qubit.deployment.ast.LambdaExpression;
import org.junit.jupiter.api.Test;

/**
 * Criteria query generation tests for AND logical operations (&&).
 */
class AndOperationsCriteriaTest extends CriteriaQueryTestBase {

    @Test
    void twoConditionAnd() {
        LambdaExpression expr = analyzeLambda("twoConditionAnd");
        CriteriaQueryStructure structure = generateCriteriaQuery(expr);
        assertCriteriaGenerationSucceeds(expr);
        assertCriteriaMethodCalled(structure, "and");
    }

    @Test
    void threeConditionAnd() {
        LambdaExpression expr = analyzeLambda("threeConditionAnd");
        CriteriaQueryStructure structure = generateCriteriaQuery(expr);
        assertCriteriaGenerationSucceeds(expr);
        assertCriteriaMethodCalled(structure, "and");
    }

    @Test
    void fourConditionAnd() {
        LambdaExpression expr = analyzeLambda("fourConditionAnd");
        CriteriaQueryStructure structure = generateCriteriaQuery(expr);
        assertCriteriaGenerationSucceeds(expr);
        assertCriteriaMethodCalled(structure, "and");
    }

    @Test
    void fiveConditionAnd() {
        LambdaExpression expr = analyzeLambda("fiveConditionAnd");
        CriteriaQueryStructure structure = generateCriteriaQuery(expr);
        assertCriteriaGenerationSucceeds(expr);
        assertCriteriaMethodCalled(structure, "and");
    }

    @Test
    void longAndChain() {
        LambdaExpression expr = analyzeLambda("longAndChain");
        CriteriaQueryStructure structure = generateCriteriaQuery(expr);
        assertCriteriaGenerationSucceeds(expr);
        assertCriteriaMethodCalled(structure, "and");
    }
}
