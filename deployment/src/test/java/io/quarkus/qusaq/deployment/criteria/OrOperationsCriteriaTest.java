package io.quarkus.qusaq.deployment.criteria;

import io.quarkus.qusaq.deployment.LambdaExpression;
import org.junit.jupiter.api.Test;

/**
 * Criteria query generation tests for OR logical operations (||).
 */
class OrOperationsCriteriaTest extends CriteriaQueryTestBase {

    @Test
    void simpleOr() {
        LambdaExpression expr = analyzeLambda("simpleOr");
        CriteriaQueryStructure structure = generateCriteriaQuery(expr);
        assertCriteriaGenerationSucceeds(expr);
        assertCriteriaMethodCalled(structure, "or");
    }

    @Test
    void orWithStringOperations() {
        LambdaExpression expr = analyzeLambda("orWithStringOperations");
        CriteriaQueryStructure structure = generateCriteriaQuery(expr);
        assertCriteriaGenerationSucceeds(expr);
        assertCriteriaMethodCalled(structure, "or");
    }

    @Test
    void threeWayOr() {
        LambdaExpression expr = analyzeLambda("threeWayOr");
        CriteriaQueryStructure structure = generateCriteriaQuery(expr);
        assertCriteriaGenerationSucceeds(expr);
        assertCriteriaMethodCalled(structure, "or");
    }

    @Test
    void fourWayOr() {
        LambdaExpression expr = analyzeLambda("fourWayOr");
        CriteriaQueryStructure structure = generateCriteriaQuery(expr);
        assertCriteriaGenerationSucceeds(expr);
        assertCriteriaMethodCalled(structure, "or");
    }
}
