package io.quarkiverse.qubit.deployment.criteria;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import io.quarkiverse.qubit.deployment.ast.LambdaExpression;

/**
 * Criteria query generation tests for Qubit.like() and Qubit.notLike() operations.
 *
 * <p>
 * Verifies that all LIKE pattern lambda methods from LambdaTestSources
 * generate criteria queries successfully without throwing exceptions.
 */
@DisplayName("LIKE pattern criteria generation")
class LikePatternCriteriaTest extends CriteriaQueryTestBase {

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {
            "likePattern", "likeCapturedPattern", "notLikePattern",
            "likeSingleCharWildcard", "likeCombinedWithAnd"
    })
    @DisplayName("generates criteria for LIKE pattern operations")
    void likeOperation_generatesCriteria(String lambdaMethodName) {
        LambdaExpression expression = analyzeLambda(lambdaMethodName);
        assertCriteriaGenerationSucceeds(expression);
    }

    @Test
    @DisplayName("Qubit.like() generates cb.like() call")
    void likePattern_generatesCbLike() {
        LambdaExpression expr = analyzeLambda("likePattern");
        CriteriaQueryStructure structure = generateCriteriaQuery(expr);
        assertCriteriaGenerationSucceeds(expr);
        assertCriteriaMethodCalled(structure, "like");
        assertFieldAccessed(structure, "email");
        assertConstantUsed(structure, "%@%.com");
    }

    @Test
    @DisplayName("Qubit.notLike() generates cb.not(cb.like()) calls")
    void notLikePattern_generatesCbNotCbLike() {
        LambdaExpression expr = analyzeLambda("notLikePattern");
        CriteriaQueryStructure structure = generateCriteriaQuery(expr);
        assertCriteriaGenerationSucceeds(expr);
        assertCriteriaMethodCalled(structure, "like");
        assertCriteriaMethodCalled(structure, "not");
        assertFieldAccessed(structure, "email");
        assertConstantUsed(structure, "%spam%");
    }

    @Test
    @DisplayName("Qubit.like() with captured variable generates cb.like()")
    void likeCapturedPattern_generatesCbLike() {
        LambdaExpression expr = analyzeLambda("likeCapturedPattern");
        CriteriaQueryStructure structure = generateCriteriaQuery(expr);
        assertCriteriaGenerationSucceeds(expr);
        assertCriteriaMethodCalled(structure, "like");
        assertFieldAccessed(structure, "email");
    }
}
