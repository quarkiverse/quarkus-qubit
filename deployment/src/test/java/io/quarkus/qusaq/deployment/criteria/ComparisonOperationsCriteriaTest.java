package io.quarkus.qusaq.deployment.criteria;

import io.quarkus.qusaq.deployment.LambdaExpression;
import org.junit.jupiter.api.Test;

/**
 * Criteria query generation tests for comparison operations (>, <, >=, <=, !=).
 *
 * <p>These tests verify that lambda expressions with comparison operations
 * are correctly transformed into JPA Criteria API predicate building code.
 *
 * <p>Test Pattern:
 * <pre>
 * Lambda: p -> p.age > 30
 *   ↓
 * AST: BinaryOp[GT, FieldAccess[age], Constant[30]]
 *   ↓
 * Criteria: cb.greaterThan(root.get("age"), 30)
 *   ↓
 * Verify: Generation succeeds without errors
 * </pre>
 */
class ComparisonOperationsCriteriaTest extends CriteriaQueryTestBase {

    // ==================== INTEGER COMPARISONS ====================

    @Test
    void integerGreaterThan() {
        LambdaExpression expr = analyzeLambda("integerGreaterThan");
        CriteriaQueryStructure structure = generateCriteriaQuery(expr);

        // Verify generation succeeded
        assertCriteriaGenerationSucceeds(expr);

        // Verify the correct Criteria API method was called
        assertCriteriaMethodCalled(structure, "greaterThan");

        // Verify the field was accessed
        assertFieldAccessed(structure, "age");

        // Verify the constant value was used
        assertConstantUsed(structure, 30);
    }

    @Test
    void integerGreaterThanOrEqual() {
        LambdaExpression expr = analyzeLambda("integerGreaterThanOrEqual");
        CriteriaQueryStructure structure = generateCriteriaQuery(expr);
        assertCriteriaGenerationSucceeds(expr);
        assertCriteriaMethodCalled(structure, "greaterThanOrEqualTo");
        assertFieldAccessed(structure, "age");
    }

    @Test
    void integerLessThan() {
        LambdaExpression expr = analyzeLambda("integerLessThan");
        CriteriaQueryStructure structure = generateCriteriaQuery(expr);
        assertCriteriaGenerationSucceeds(expr);
        assertCriteriaMethodCalled(structure, "lessThan");
        assertFieldAccessed(structure, "age");
    }

    @Test
    void integerLessThanOrEqual() {
        LambdaExpression expr = analyzeLambda("integerLessThanOrEqual");
        CriteriaQueryStructure structure = generateCriteriaQuery(expr);
        assertCriteriaGenerationSucceeds(expr);
        assertCriteriaMethodCalled(structure, "lessThanOrEqualTo");
        assertFieldAccessed(structure, "age");
    }

    @Test
    void integerNotEquals() {
        LambdaExpression expr = analyzeLambda("integerNotEquals");
        CriteriaQueryStructure structure = generateCriteriaQuery(expr);
        assertCriteriaGenerationSucceeds(expr);
        assertCriteriaMethodCalled(structure, "notEqual");
        assertFieldAccessed(structure, "age");
    }

    // ==================== LONG COMPARISONS ====================

    @Test
    void longGreaterThan() {
        LambdaExpression expr = analyzeLambda("longGreaterThan");
        CriteriaQueryStructure structure = generateCriteriaQuery(expr);
        assertCriteriaGenerationSucceeds(expr);
        assertCriteriaMethodCalled(structure, "greaterThan");
    }

    @Test
    void longGreaterThanOrEqual() {
        LambdaExpression expr = analyzeLambda("longGreaterThanOrEqual");
        CriteriaQueryStructure structure = generateCriteriaQuery(expr);
        assertCriteriaGenerationSucceeds(expr);
        assertCriteriaMethodCalled(structure, "greaterThanOrEqualTo");
    }

    @Test
    void longLessThan() {
        LambdaExpression expr = analyzeLambda("longLessThan");
        CriteriaQueryStructure structure = generateCriteriaQuery(expr);
        assertCriteriaGenerationSucceeds(expr);
        assertCriteriaMethodCalled(structure, "lessThan");
    }

    @Test
    void longLessThanOrEqual() {
        LambdaExpression expr = analyzeLambda("longLessThanOrEqual");
        CriteriaQueryStructure structure = generateCriteriaQuery(expr);
        assertCriteriaGenerationSucceeds(expr);
        assertCriteriaMethodCalled(structure, "lessThanOrEqualTo");
    }

    @Test
    void longNotEquals() {
        LambdaExpression expr = analyzeLambda("longNotEquals");
        CriteriaQueryStructure structure = generateCriteriaQuery(expr);
        assertCriteriaGenerationSucceeds(expr);
        assertCriteriaMethodCalled(structure, "notEqual");
    }

    // ==================== FLOAT COMPARISONS ====================

    @Test
    void floatGreaterThan() {
        LambdaExpression expr = analyzeLambda("floatGreaterThan");
        CriteriaQueryStructure structure = generateCriteriaQuery(expr);
        assertCriteriaGenerationSucceeds(expr);
        assertCriteriaMethodCalled(structure, "greaterThan");
    }

    @Test
    void floatGreaterThanOrEqual() {
        LambdaExpression expr = analyzeLambda("floatGreaterThanOrEqual");
        CriteriaQueryStructure structure = generateCriteriaQuery(expr);
        assertCriteriaGenerationSucceeds(expr);
        assertCriteriaMethodCalled(structure, "greaterThanOrEqualTo");
    }

    @Test
    void floatLessThan() {
        LambdaExpression expr = analyzeLambda("floatLessThan");
        CriteriaQueryStructure structure = generateCriteriaQuery(expr);
        assertCriteriaGenerationSucceeds(expr);
        assertCriteriaMethodCalled(structure, "lessThan");
    }

    @Test
    void floatLessThanOrEqual() {
        LambdaExpression expr = analyzeLambda("floatLessThanOrEqual");
        CriteriaQueryStructure structure = generateCriteriaQuery(expr);
        assertCriteriaGenerationSucceeds(expr);
        assertCriteriaMethodCalled(structure, "lessThanOrEqualTo");
    }

    @Test
    void floatNotEquals() {
        LambdaExpression expr = analyzeLambda("floatNotEquals");
        CriteriaQueryStructure structure = generateCriteriaQuery(expr);
        assertCriteriaGenerationSucceeds(expr);
        assertCriteriaMethodCalled(structure, "notEqual");
    }

    // ==================== DOUBLE COMPARISONS ====================

    @Test
    void doubleGreaterThan() {
        LambdaExpression expr = analyzeLambda("doubleGreaterThan");
        CriteriaQueryStructure structure = generateCriteriaQuery(expr);
        assertCriteriaGenerationSucceeds(expr);
        assertCriteriaMethodCalled(structure, "greaterThan");
    }

    @Test
    void doubleGreaterThanOrEqual() {
        LambdaExpression expr = analyzeLambda("doubleGreaterThanOrEqual");
        CriteriaQueryStructure structure = generateCriteriaQuery(expr);
        assertCriteriaGenerationSucceeds(expr);
        assertCriteriaMethodCalled(structure, "greaterThanOrEqualTo");
    }

    @Test
    void doubleLessThan() {
        LambdaExpression expr = analyzeLambda("doubleLessThan");
        CriteriaQueryStructure structure = generateCriteriaQuery(expr);
        assertCriteriaGenerationSucceeds(expr);
        assertCriteriaMethodCalled(structure, "lessThan");
    }

    @Test
    void doubleLessThanOrEqual() {
        LambdaExpression expr = analyzeLambda("doubleLessThanOrEqual");
        CriteriaQueryStructure structure = generateCriteriaQuery(expr);
        assertCriteriaGenerationSucceeds(expr);
        assertCriteriaMethodCalled(structure, "lessThanOrEqualTo");
    }

    @Test
    void doubleNotEquals() {
        LambdaExpression expr = analyzeLambda("doubleNotEquals");
        CriteriaQueryStructure structure = generateCriteriaQuery(expr);
        assertCriteriaGenerationSucceeds(expr);
        assertCriteriaMethodCalled(structure, "notEqual");
    }

    // ==================== BIGDECIMAL COMPARISONS ====================

    @Test
    void bigDecimalGreaterThan() {
        LambdaExpression expr = analyzeLambda("bigDecimalGreaterThan");
        CriteriaQueryStructure structure = generateCriteriaQuery(expr);
        assertCriteriaGenerationSucceeds(expr);
        assertCriteriaMethodCalled(structure, "greaterThan");
    }

    @Test
    void bigDecimalGreaterThanOrEqual() {
        LambdaExpression expr = analyzeLambda("bigDecimalGreaterThanOrEqual");
        CriteriaQueryStructure structure = generateCriteriaQuery(expr);
        assertCriteriaGenerationSucceeds(expr);
        assertCriteriaMethodCalled(structure, "greaterThanOrEqualTo");
    }

    @Test
    void bigDecimalLessThan() {
        LambdaExpression expr = analyzeLambda("bigDecimalLessThan");
        CriteriaQueryStructure structure = generateCriteriaQuery(expr);
        assertCriteriaGenerationSucceeds(expr);
        assertCriteriaMethodCalled(structure, "lessThan");
    }

    @Test
    void bigDecimalLessThanOrEqual() {
        LambdaExpression expr = analyzeLambda("bigDecimalLessThanOrEqual");
        CriteriaQueryStructure structure = generateCriteriaQuery(expr);
        assertCriteriaGenerationSucceeds(expr);
        assertCriteriaMethodCalled(structure, "lessThanOrEqualTo");
    }

    @Test
    void bigDecimalNotEquals() {
        LambdaExpression expr = analyzeLambda("bigDecimalNotEquals");
        CriteriaQueryStructure structure = generateCriteriaQuery(expr);
        assertCriteriaGenerationSucceeds(expr);
        assertCriteriaMethodCalled(structure, "notEqual");
    }

    // ==================== TEMPORAL COMPARISONS ====================

    @Test
    void localDateAfter() {
        LambdaExpression expr = analyzeLambda("localDateAfter");
        CriteriaQueryStructure structure = generateCriteriaQuery(expr);
        assertCriteriaGenerationSucceeds(expr);
        assertCriteriaMethodCalled(structure, "greaterThan");
    }

    @Test
    void localDateBefore() {
        LambdaExpression expr = analyzeLambda("localDateBefore");
        CriteriaQueryStructure structure = generateCriteriaQuery(expr);
        assertCriteriaGenerationSucceeds(expr);
        assertCriteriaMethodCalled(structure, "lessThan");
    }

    @Test
    void localDateTimeAfter() {
        LambdaExpression expr = analyzeLambda("localDateTimeAfter");
        CriteriaQueryStructure structure = generateCriteriaQuery(expr);
        assertCriteriaGenerationSucceeds(expr);
        assertCriteriaMethodCalled(structure, "greaterThan");
    }

    @Test
    void localDateTimeBefore() {
        LambdaExpression expr = analyzeLambda("localDateTimeBefore");
        CriteriaQueryStructure structure = generateCriteriaQuery(expr);
        assertCriteriaGenerationSucceeds(expr);
        assertCriteriaMethodCalled(structure, "lessThan");
    }

    @Test
    void localTimeAfter() {
        LambdaExpression expr = analyzeLambda("localTimeAfter");
        CriteriaQueryStructure structure = generateCriteriaQuery(expr);
        assertCriteriaGenerationSucceeds(expr);
        assertCriteriaMethodCalled(structure, "greaterThan");
    }

    @Test
    void localTimeBefore() {
        LambdaExpression expr = analyzeLambda("localTimeBefore");
        CriteriaQueryStructure structure = generateCriteriaQuery(expr);
        assertCriteriaGenerationSucceeds(expr);
        assertCriteriaMethodCalled(structure, "lessThan");
    }

    // ==================== RANGE QUERIES ====================

    @Test
    void integerRangeQuery() {
        LambdaExpression expr = analyzeLambda("integerRangeQuery");
        CriteriaQueryStructure structure = generateCriteriaQuery(expr);
        assertCriteriaGenerationSucceeds(expr);
        assertCriteriaMethodCalled(structure, "and");
        assertCriteriaMethodCalled(structure, "greaterThanOrEqualTo");
        assertCriteriaMethodCalled(structure, "lessThanOrEqualTo");
    }

    @Test
    void longRangeQuery() {
        LambdaExpression expr = analyzeLambda("longRangeQuery");
        CriteriaQueryStructure structure = generateCriteriaQuery(expr);
        assertCriteriaGenerationSucceeds(expr);
        assertCriteriaMethodCalled(structure, "and");
        assertCriteriaMethodCalled(structure, "greaterThanOrEqualTo");
        assertCriteriaMethodCalled(structure, "lessThanOrEqualTo");
    }

    @Test
    void floatRangeQuery() {
        LambdaExpression expr = analyzeLambda("floatRangeQuery");
        CriteriaQueryStructure structure = generateCriteriaQuery(expr);
        assertCriteriaGenerationSucceeds(expr);
        assertCriteriaMethodCalled(structure, "and");
        assertCriteriaMethodCalled(structure, "greaterThanOrEqualTo");
        assertCriteriaMethodCalled(structure, "lessThanOrEqualTo");
    }

    @Test
    void bigDecimalRangeQuery() {
        LambdaExpression expr = analyzeLambda("bigDecimalRangeQuery");
        CriteriaQueryStructure structure = generateCriteriaQuery(expr);
        assertCriteriaGenerationSucceeds(expr);
        assertCriteriaMethodCalled(structure, "and");
        assertCriteriaMethodCalled(structure, "greaterThanOrEqualTo");
        assertCriteriaMethodCalled(structure, "lessThanOrEqualTo");
    }
}
