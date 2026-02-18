package io.quarkiverse.qubit.deployment.criteria;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import io.quarkiverse.qubit.deployment.ast.LambdaExpression;

@DisplayName("BETWEEN optimization criteria generation")
class BetweenOptimizationCriteriaTest extends CriteriaQueryTestBase {

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {
            "betweenCanonical", "betweenCapturedVariables", "betweenReversedAndOrder",
            "betweenReversedLeftOperand", "betweenReversedRightOperand", "betweenDoubleSalary",
            "exclusiveRangeNotBetween", "differentFieldsNotBetween", "mixedOperatorsNotBetween"
    })
    @DisplayName("generates criteria for range operations")
    void rangeOperation_generatesCriteria(String lambdaMethodName) {
        LambdaExpression expression = analyzeLambda(lambdaMethodName);
        assertCriteriaGenerationSucceeds(expression);
    }
}
