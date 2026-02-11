package io.quarkiverse.qubit.deployment.criteria;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import io.quarkiverse.qubit.deployment.ast.LambdaExpression;

/**
 * Criteria query generation tests for NOT logical operations (!).
 *
 * <p>
 * This class uses JUnit 5 parameterized tests to consolidate repetitive
 * test patterns, reducing code duplication while maintaining full coverage.
 */
class NotOperationsCriteriaTest extends CriteriaQueryTestBase {

    /**
     * Tests for NOT operations that just verify generation succeeds.
     * Complex NOT expressions may compile to inverted conditions, not not().
     */
    @ParameterizedTest(name = "{0} → success")
    @ValueSource(strings = { "notWithComplexOrAnd", "stringNotEquals", "doubleNegation" })
    void notOperationSucceeds(String lambdaMethodName) {
        LambdaExpression expr = analyzeLambda(lambdaMethodName);
        assertCriteriaGenerationSucceeds(expr);
    }

    /**
     * Tests for NOT operations with specific method assertions.
     * De Morgan's law transforms: !(a && b) => !a || !b, !(a || b) => !a && !b
     */
    @ParameterizedTest(name = "{0} → cb.{1}()")
    @CsvSource({
            "simpleNot, not",
            "notWithAnd, and",
            "notWithComplexAnd, or",
            "notWithOr, and"
    })
    void notOperationWithMethod(String lambdaMethodName, String expectedMethod) {
        LambdaExpression expr = analyzeLambda(lambdaMethodName);
        CriteriaQueryStructure structure = generateCriteriaQuery(expr);
        assertCriteriaGenerationSucceeds(expr);
        assertCriteriaMethodCalled(structure, expectedMethod);
    }
}
