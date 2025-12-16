package io.quarkiverse.qubit.deployment.generation.expression;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import io.quarkiverse.qubit.deployment.ast.LambdaExpression.BinaryOp.Operator;

/**
 * Tests for error paths in expression builders.
 *
 * <p>These tests verify that expression builders correctly reject invalid operators
 * and throw appropriate exceptions with descriptive messages.
 *
 * <p>Key insight: The switch statements in these builders validate the operator BEFORE
 * attempting to use the Gizmo MethodCreator, so we can test with null parameters for
 * method, cb, left, and right.
 */
class ExpressionBuilderErrorTest {

    @Nested
    @DisplayName("ComparisonExpressionBuilder Error Paths")
    class ComparisonBuilderTests {

        private ComparisonExpressionBuilder builder;

        @BeforeEach
        void setUp() {
            builder = new ComparisonExpressionBuilder();
        }

        @ParameterizedTest
        @DisplayName("buildComparisonOperation rejects arithmetic operators")
        @EnumSource(value = Operator.class, names = {"ADD", "SUB", "MUL", "DIV", "MOD"})
        void buildComparison_arithmeticOperator_throws(Operator operator) {
            assertThatThrownBy(() -> builder.buildComparisonOperation(null, operator, null, null, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Not a comparison operator")
                    .hasMessageContaining(operator.name());
        }

        @ParameterizedTest
        @DisplayName("buildComparisonOperation rejects logical operators")
        @EnumSource(value = Operator.class, names = {"AND", "OR"})
        void buildComparison_logicalOperator_throws(Operator operator) {
            assertThatThrownBy(() -> builder.buildComparisonOperation(null, operator, null, null, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Not a comparison operator")
                    .hasMessageContaining(operator.name());
        }

        @Test
        @DisplayName("buildComparisonOperation accepts all comparison operators (no exception)")
        void buildComparison_validOperators_doesNotThrowOnOperatorCheck() {
            // These operators are valid for comparison, so the switch won't throw.
            // The method will fail later when trying to use the null MethodCreator,
            // but that's a different error (NullPointerException from Gizmo).
            // We verify here that the operator validation passes.

            Operator[] comparisonOps = {Operator.EQ, Operator.NE, Operator.GT, Operator.GE, Operator.LT, Operator.LE};
            for (Operator op : comparisonOps) {
                // This should throw NPE from Gizmo, not IllegalArgumentException
                assertThatThrownBy(() -> builder.buildComparisonOperation(null, op, null, null, null))
                        .isNotInstanceOf(IllegalArgumentException.class);
            }
        }
    }

    @Nested
    @DisplayName("ArithmeticExpressionBuilder Error Paths")
    class ArithmeticBuilderTests {

        private ArithmeticExpressionBuilder builder;

        @BeforeEach
        void setUp() {
            builder = new ArithmeticExpressionBuilder();
        }

        @ParameterizedTest
        @DisplayName("buildArithmeticOperation rejects comparison operators")
        @EnumSource(value = Operator.class, names = {"EQ", "NE", "LT", "LE", "GT", "GE"})
        void buildArithmetic_comparisonOperator_throws(Operator operator) {
            assertThatThrownBy(() -> builder.buildArithmeticOperation(null, operator, null, null, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Not an arithmetic operator")
                    .hasMessageContaining(operator.name());
        }

        @ParameterizedTest
        @DisplayName("buildArithmeticOperation rejects logical operators")
        @EnumSource(value = Operator.class, names = {"AND", "OR"})
        void buildArithmetic_logicalOperator_throws(Operator operator) {
            assertThatThrownBy(() -> builder.buildArithmeticOperation(null, operator, null, null, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Not an arithmetic operator")
                    .hasMessageContaining(operator.name());
        }

        @Test
        @DisplayName("buildArithmeticOperation accepts all arithmetic operators (no exception)")
        void buildArithmetic_validOperators_doesNotThrowOnOperatorCheck() {
            // These operators are valid for arithmetic, so the switch won't throw.
            // The method will fail later when trying to use the null MethodCreator,
            // but that's a different error (NullPointerException from Gizmo).
            // We verify here that the operator validation passes.

            Operator[] arithmeticOps = {Operator.ADD, Operator.SUB, Operator.MUL, Operator.DIV, Operator.MOD};
            for (Operator op : arithmeticOps) {
                // This should throw NPE from Gizmo, not IllegalArgumentException
                assertThatThrownBy(() -> builder.buildArithmeticOperation(null, op, null, null, null))
                        .isNotInstanceOf(IllegalArgumentException.class);
            }
        }
    }
}
