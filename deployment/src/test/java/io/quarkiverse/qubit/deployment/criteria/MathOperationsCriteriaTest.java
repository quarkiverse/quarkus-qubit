package io.quarkiverse.qubit.deployment.criteria;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import io.quarkiverse.qubit.deployment.ast.LambdaExpression;

/**
 * Criteria query generation tests for math operations.
 *
 * <p>
 * Verifies that all math lambda methods from LambdaTestSources
 * generate criteria queries successfully without throwing exceptions.
 */
@DisplayName("Math operations criteria generation")
class MathOperationsCriteriaTest extends CriteriaQueryTestBase {

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {
            "mathAbs", "mathAbsArithmetic", "mathSqrt", "mathCeil",
            "mathFloor", "mathExp", "mathLog", "mathPow",
            "mathPowCapturedExponent", "mathRound", "integerSignum",
            "unaryNegation", "doubleNegation_arithmetic", "qubitRound"
    })
    @DisplayName("generates criteria for math operations")
    void mathOperation_generatesCriteria(String lambdaMethodName) {
        LambdaExpression expression = analyzeLambda(lambdaMethodName);
        assertCriteriaGenerationSucceeds(expression);
    }
}
