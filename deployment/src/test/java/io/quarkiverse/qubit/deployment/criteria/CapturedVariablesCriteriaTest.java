package io.quarkiverse.qubit.deployment.criteria;

import io.quarkiverse.qubit.deployment.ast.LambdaExpression;
import org.junit.jupiter.api.Test;

/**
 * Criteria query generation tests for lambda expressions with captured variables.
 *
 * <p>Captured variables are variables from the enclosing scope that are used
 * inside a lambda expression. These tests verify that captured variables are
 * correctly translated to JPA Criteria API parameters.
 *
 * <p>Example:
 * <pre>{@code
 * String searchName = "John";
 * List<Person> results = Person.where(p -> p.firstName.equals(searchName)).toList();
 * }</pre>
 *
 * <p>In the generated Criteria query, {@code searchName} should become a parameter.
 */
class CapturedVariablesCriteriaTest extends CriteriaQueryTestBase {

    @Test
    void capturedStringVariable() {
        LambdaExpression expr = analyzeLambda("capturedStringVariable");
        CriteriaQueryStructure structure = generateCriteriaQuery(expr);

        // Verify generation succeeded
        assertCriteriaGenerationSucceeds(expr);

        // Verify field access
        assertFieldAccessed(structure, "firstName");

        // Verify equal comparison (String.equals() -> cb.equal())
        assertCriteriaMethodCalled(structure, "equal");
    }

    @Test
    void capturedIntVariable() {
        LambdaExpression expr = analyzeLambda("capturedIntVariable");
        CriteriaQueryStructure structure = generateCriteriaQuery(expr);

        // Verify generation succeeded
        assertCriteriaGenerationSucceeds(expr);

        // Verify field access
        assertFieldAccessed(structure, "age");

        // Verify greater than comparison
        assertCriteriaMethodCalled(structure, "greaterThan");
    }

    @Test
    void capturedDoubleVariable() {
        LambdaExpression expr = analyzeLambda("capturedDoubleVariable");
        CriteriaQueryStructure structure = generateCriteriaQuery(expr);

        // Verify generation succeeded
        assertCriteriaGenerationSucceeds(expr);

        // Verify field access
        assertFieldAccessed(structure, "salary");

        // Verify greater than or equal comparison
        assertCriteriaMethodCalled(structure, "greaterThanOrEqualTo");
    }

    @Test
    void capturedStringStartsWith() {
        LambdaExpression expr = analyzeLambda("capturedStringStartsWith");
        CriteriaQueryStructure structure = generateCriteriaQuery(expr);

        // Verify generation succeeded
        assertCriteriaGenerationSucceeds(expr);

        // Verify field access
        assertFieldAccessed(structure, "firstName");

        // Verify LIKE pattern for startsWith
        assertCriteriaMethodCalled(structure, "like");
    }

    @Test
    void multipleCapturedVariables() {
        LambdaExpression expr = analyzeLambda("multipleCapturedVariables");
        CriteriaQueryStructure structure = generateCriteriaQuery(expr);

        // Verify generation succeeded
        assertCriteriaGenerationSucceeds(expr);

        // Verify AND operation
        assertCriteriaMethodCalled(structure, "and");

        // Verify both fields accessed
        assertFieldAccessed(structure, "firstName");
        assertFieldAccessed(structure, "age");

        // Verify both comparison types
        assertCriteriaMethodCalled(structure, "equal");
        assertCriteriaMethodCalled(structure, "greaterThan");
    }

    @Test
    void capturedVariableInComplexExpression() {
        LambdaExpression expr = analyzeLambda("capturedVariableInComplexExpression");
        CriteriaQueryStructure structure = generateCriteriaQuery(expr);

        // Verify generation succeeded
        assertCriteriaGenerationSucceeds(expr);

        // Verify OR operation at top level
        assertCriteriaMethodCalled(structure, "or");

        // Verify AND operation in nested expression
        assertCriteriaMethodCalled(structure, "and");

        // Verify all fields accessed
        assertFieldAccessed(structure, "age");
        assertFieldAccessed(structure, "active");
        assertFieldAccessed(structure, "salary");

        // Verify comparison operations
        assertCriteriaMethodCalled(structure, "greaterThan");
        assertCriteriaMethodCalled(structure, "equal");
    }
}
