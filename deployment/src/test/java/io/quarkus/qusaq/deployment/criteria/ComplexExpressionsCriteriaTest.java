package io.quarkus.qusaq.deployment.criteria;

import io.quarkus.qusaq.deployment.LambdaExpression;
import org.junit.jupiter.api.Test;

/**
 * Criteria query generation tests for complex nested expressions.
 */
class ComplexExpressionsCriteriaTest extends CriteriaQueryTestBase {

    @Test
    void nestedAndOrExpression() {
        LambdaExpression expr = analyzeLambda("nestedAndOrExpression");
        CriteriaQueryStructure structure = generateCriteriaQuery(expr);
        assertCriteriaGenerationSucceeds(expr);
        assertCriteriaMethodCalled(structure, "and");
        assertCriteriaMethodCalled(structure, "or");
    }

    @Test
    void andWithNestedOr() {
        LambdaExpression expr = analyzeLambda("andWithNestedOr");
        CriteriaQueryStructure structure = generateCriteriaQuery(expr);
        assertCriteriaGenerationSucceeds(expr);
        assertCriteriaMethodCalled(structure, "and");
        assertCriteriaMethodCalled(structure, "or");
    }

    @Test
    void complexNestedOrAnd() {
        LambdaExpression expr = analyzeLambda("complexNestedOrAnd");
        CriteriaQueryStructure structure = generateCriteriaQuery(expr);
        assertCriteriaGenerationSucceeds(expr);
        assertCriteriaMethodCalled(structure, "and");
        assertCriteriaMethodCalled(structure, "or");
    }

    @Test
    void tripleAndWithOr() {
        LambdaExpression expr = analyzeLambda("tripleAndWithOr");
        CriteriaQueryStructure structure = generateCriteriaQuery(expr);
        assertCriteriaGenerationSucceeds(expr);
        assertCriteriaMethodCalled(structure, "and");
        assertCriteriaMethodCalled(structure, "or");
    }

    @Test
    void deeplyNestedMultipleOrGroups() {
        LambdaExpression expr = analyzeLambda("deeplyNestedMultipleOrGroups");
        CriteriaQueryStructure structure = generateCriteriaQuery(expr);
        assertCriteriaGenerationSucceeds(expr);
        assertCriteriaMethodCalled(structure, "or");
    }

    @Test
    void arithmeticInOrGroups() {
        LambdaExpression expr = analyzeLambda("arithmeticInOrGroups");
        CriteriaQueryStructure structure = generateCriteriaQuery(expr);
        assertCriteriaGenerationSucceeds(expr);
        assertCriteriaMethodCalled(structure, "or");
    }

    @Test
    void complexArithmeticInOr() {
        LambdaExpression expr = analyzeLambda("complexArithmeticInOr");
        CriteriaQueryStructure structure = generateCriteriaQuery(expr);
        assertCriteriaGenerationSucceeds(expr);
        assertCriteriaMethodCalled(structure, "or");
    }

    @Test
    void complexNestedConditions() {
        LambdaExpression expr = analyzeLambda("complexNestedConditions");
        CriteriaQueryStructure structure = generateCriteriaQuery(expr);
        assertCriteriaGenerationSucceeds(expr);
        assertCriteriaMethodCalled(structure, "and");
        // String equals operations may not generate or() calls
    }
}
