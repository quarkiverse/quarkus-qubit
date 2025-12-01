package io.quarkiverse.qubit.deployment.criteria;

import io.quarkiverse.qubit.deployment.ast.LambdaExpression;
import org.junit.jupiter.api.Test;

/**
 * Criteria query generation tests for NOT logical operations (!).
 */
class NotOperationsCriteriaTest extends CriteriaQueryTestBase {

    @Test
    void simpleNot() {
        LambdaExpression expr = analyzeLambda("simpleNot");
        CriteriaQueryStructure structure = generateCriteriaQuery(expr);
        assertCriteriaGenerationSucceeds(expr);
        assertCriteriaMethodCalled(structure, "not");
    }

    @Test
    void notWithAnd() {
        LambdaExpression expr = analyzeLambda("notWithAnd");
        CriteriaQueryStructure structure = generateCriteriaQuery(expr);
        assertCriteriaGenerationSucceeds(expr);
        assertCriteriaMethodCalled(structure, "not");
        assertCriteriaMethodCalled(structure, "and");
    }

    @Test
    void notWithComplexOrAnd() {
        LambdaExpression expr = analyzeLambda("notWithComplexOrAnd");
        assertCriteriaGenerationSucceeds(expr);
        // Complex NOT expressions may compile to inverted conditions, not not()
    }

    @Test
    void stringNotEquals() {
        LambdaExpression expr = analyzeLambda("stringNotEquals");
        assertCriteriaGenerationSucceeds(expr);
        // Negated method calls may compile to inverted conditions, not notEqual()
    }

    @Test
    void notWithComplexAnd() {
        LambdaExpression expr = analyzeLambda("notWithComplexAnd");
        CriteriaQueryStructure structure = generateCriteriaQuery(expr);
        assertCriteriaGenerationSucceeds(expr);
        assertCriteriaMethodCalled(structure, "or");
    }

    @Test
    void doubleNegation() {
        LambdaExpression expr = analyzeLambda("doubleNegation");
        assertCriteriaGenerationSucceeds(expr);
        // Double negation compiles to simple equality check
    }

    @Test
    void notWithOr() {
        LambdaExpression expr = analyzeLambda("notWithOr");
        CriteriaQueryStructure structure = generateCriteriaQuery(expr);
        assertCriteriaGenerationSucceeds(expr);
        assertCriteriaMethodCalled(structure, "and");
    }
}
