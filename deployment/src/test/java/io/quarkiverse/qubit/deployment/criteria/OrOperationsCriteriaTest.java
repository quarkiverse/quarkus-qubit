package io.quarkiverse.qubit.deployment.criteria;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import io.quarkiverse.qubit.deployment.ast.LambdaExpression;

/**
 * Criteria query generation tests for OR logical operations (||).
 */
class OrOperationsCriteriaTest extends CriteriaQueryTestBase {

    @ParameterizedTest(name = "{0} → cb.or()")
    @ValueSource(strings = {
            "simpleOr",
            "orWithStringOperations",
            "threeWayOr",
            "fourWayOr"
    })
    void orOperation(String lambdaMethodName) {
        LambdaExpression expr = analyzeLambda(lambdaMethodName);
        CriteriaQueryStructure structure = generateCriteriaQuery(expr);
        assertCriteriaGenerationSucceeds(expr);
        assertCriteriaMethodCalled(structure, "or");
    }
}
