package io.quarkiverse.qubit.deployment.criteria;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import io.quarkiverse.qubit.deployment.ast.LambdaExpression;

/**
 * Criteria query generation tests for lambda expressions with captured variables.
 *
 * <p>
 * Captured variables are variables from the enclosing scope that are used
 * inside a lambda expression. These tests verify that captured variables are
 * correctly translated to JPA Criteria API parameters.
 *
 * <p>
 * Example:
 *
 * <pre>{@code
 * String searchName = "John";
 * List<Person> results = Person.where(p -> p.firstName.equals(searchName)).toList();
 * }</pre>
 *
 * <p>
 * In the generated Criteria query, {@code searchName} should become a parameter.
 *
 * <p>
 * This class uses JUnit 5 parameterized tests to consolidate repetitive
 * test patterns, reducing code duplication while maintaining full coverage.
 */
class CapturedVariablesCriteriaTest extends CriteriaQueryTestBase {

    // ==================== PARAMETERIZED TESTS ====================

    /**
     * Tests for simple captured variable patterns (field access + comparison method).
     */
    @ParameterizedTest(name = "{0}: {1} → cb.{2}()")
    @CsvSource({
            "capturedStringVariable, firstName, equal",
            "capturedIntVariable, age, greaterThan",
            "capturedDoubleVariable, salary, greaterThanOrEqualTo",
            "capturedStringStartsWith, firstName, like",
            "capturedBooleanVariable, active, equal",
            "capturedLongVariable, employeeId, equal"
    })
    void simpleCapturedVariable(String lambdaMethodName, String fieldName, String expectedMethod) {
        LambdaExpression expr = analyzeLambda(lambdaMethodName);
        CriteriaQueryStructure structure = generateCriteriaQuery(expr);
        assertCriteriaGenerationSucceeds(expr);
        assertFieldAccessed(structure, fieldName);
        assertCriteriaMethodCalled(structure, expectedMethod);
    }

    // ==================== COMPLEX CAPTURED VARIABLE TESTS ====================

    @Test
    void multipleCapturedVariables() {
        LambdaExpression expr = analyzeLambda("multipleCapturedVariables");
        CriteriaQueryStructure structure = generateCriteriaQuery(expr);
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
