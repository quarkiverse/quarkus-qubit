package io.quarkiverse.qubit.deployment.criteria;

import io.quarkiverse.qubit.deployment.ast.LambdaExpression;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

/**
 * Criteria query generation tests for arithmetic operations (+, -, *, /, %).
 *
 * <p>This class uses JUnit 5 parameterized tests to consolidate repetitive
 * test patterns, reducing code duplication while maintaining full coverage.
 */
class ArithmeticOperationsCriteriaTest extends CriteriaQueryTestBase {

    // ==================== PARAMETERIZED TEST DATA ====================

    /**
     * Test data for all arithmetic operations.
     * Each entry: lambdaMethodName, expectedCriteriaMethod
     */
    static Stream<Arguments> arithmeticOperations() {
        return Stream.of(
                // Integer arithmetic
                Arguments.of("integerAddition", "sum"),
                Arguments.of("integerSubtraction", "diff"),
                Arguments.of("integerMultiplication", "prod"),
                Arguments.of("integerDivision", "quot"),
                Arguments.of("integerModulo", "mod"),

                // Long arithmetic
                Arguments.of("longAddition", "sum"),
                Arguments.of("longSubtraction", "diff"),
                Arguments.of("longMultiplication", "prod"),
                Arguments.of("longDivision", "quot"),
                Arguments.of("longModulo", "mod"),

                // Float arithmetic
                Arguments.of("floatAddition", "sum"),
                Arguments.of("floatSubtraction", "diff"),
                Arguments.of("floatMultiplication", "prod"),
                Arguments.of("floatDivision", "quot"),

                // Double arithmetic
                Arguments.of("doubleAddition", "sum"),
                Arguments.of("doubleSubtraction", "diff"),
                Arguments.of("doubleMultiplication", "prod"),
                Arguments.of("doubleDivision", "quot"),

                // Field-field arithmetic
                Arguments.of("longFieldFieldAddition", "sum"),
                Arguments.of("longFieldFieldSubtraction", "diff")
        );
    }

    // ==================== PARAMETERIZED TESTS ====================

    @ParameterizedTest(name = "{0} → cb.{1}()")
    @MethodSource("arithmeticOperations")
    void arithmeticOperation(String lambdaMethodName, String expectedCriteriaMethod) {
        LambdaExpression expr = analyzeLambda(lambdaMethodName);
        CriteriaQueryStructure structure = generateCriteriaQuery(expr);

        assertCriteriaGenerationSucceeds(expr);
        assertCriteriaMethodCalled(structure, expectedCriteriaMethod);
    }
}
