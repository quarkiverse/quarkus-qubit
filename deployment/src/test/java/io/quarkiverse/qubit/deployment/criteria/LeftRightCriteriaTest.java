package io.quarkiverse.qubit.deployment.criteria;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import io.quarkiverse.qubit.deployment.ast.LambdaExpression;

/**
 * Criteria query generation tests for Qubit.left() and Qubit.right() operations.
 *
 * <p>
 * Verifies that all LEFT/RIGHT lambda methods from LambdaTestSources
 * generate criteria queries successfully without throwing exceptions.
 */
@DisplayName("LEFT/RIGHT criteria generation")
class LeftRightCriteriaTest extends CriteriaQueryTestBase {

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {
            "qubitLeft", "qubitRight", "qubitLeftCaptured", "qubitRightProjection"
    })
    @DisplayName("generates criteria for LEFT/RIGHT operations")
    void leftRightOperation_generatesCriteria(String lambdaMethodName) {
        LambdaExpression expression = analyzeLambda(lambdaMethodName);
        assertCriteriaGenerationSucceeds(expression);
    }

    @Test
    @DisplayName("Qubit.left() generates cb.left() call")
    void qubitLeft_generatesCbLeft() {
        LambdaExpression expr = analyzeLambda("qubitLeft");
        CriteriaQueryStructure structure = generateCriteriaQuery(expr);
        assertCriteriaGenerationSucceeds(expr);
        assertCriteriaMethodCalled(structure, "left");
        assertFieldAccessed(structure, "firstName");
    }

    @Test
    @DisplayName("Qubit.right() generates cb.right() call")
    void qubitRight_generatesCbRight() {
        LambdaExpression expr = analyzeLambda("qubitRight");
        CriteriaQueryStructure structure = generateCriteriaQuery(expr);
        assertCriteriaGenerationSucceeds(expr);
        assertCriteriaMethodCalled(structure, "right");
        assertFieldAccessed(structure, "email");
    }

    @Test
    @DisplayName("Qubit.left() with captured variable generates cb.left()")
    void qubitLeftCaptured_generatesCbLeft() {
        LambdaExpression expr = analyzeLambda("qubitLeftCaptured");
        CriteriaQueryStructure structure = generateCriteriaQuery(expr);
        assertCriteriaGenerationSucceeds(expr);
        assertCriteriaMethodCalled(structure, "left");
        assertFieldAccessed(structure, "firstName");
    }
}
