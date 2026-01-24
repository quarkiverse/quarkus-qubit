package io.quarkiverse.qubit.deployment.common;

import static io.quarkiverse.qubit.deployment.generation.MethodDescriptors.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.quarkiverse.qubit.deployment.ast.LambdaExpression.BinaryOp.Operator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Unit tests for {@link OperatorMethodMapper}.
 */
class OperatorMethodMapperTest {

    // ========================================================================
    // mapArithmeticOperator Tests
    // ========================================================================

    @Nested
    @DisplayName("mapArithmeticOperator")
    class MapArithmeticOperatorTests {

        @Test
        void add_returnsSumBinary() {
            assertThat(OperatorMethodMapper.mapArithmeticOperator(Operator.ADD)).isEqualTo(CB_SUM_BINARY);
        }

        @Test
        void sub_returnsDiff() {
            assertThat(OperatorMethodMapper.mapArithmeticOperator(Operator.SUB)).isEqualTo(CB_DIFF);
        }

        @Test
        void mul_returnsProd() {
            assertThat(OperatorMethodMapper.mapArithmeticOperator(Operator.MUL)).isEqualTo(CB_PROD);
        }

        @Test
        void div_returnsQuot() {
            assertThat(OperatorMethodMapper.mapArithmeticOperator(Operator.DIV)).isEqualTo(CB_QUOT);
        }

        @Test
        void mod_returnsMod() {
            assertThat(OperatorMethodMapper.mapArithmeticOperator(Operator.MOD)).isEqualTo(CB_MOD);
        }

        @ParameterizedTest
        @EnumSource(value = Operator.class, names = {"EQ", "NE", "GT", "GE", "LT", "LE"})
        void comparisonOperator_throws(Operator operator) {
            assertThatThrownBy(() -> OperatorMethodMapper.mapArithmeticOperator(operator))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("arithmetic");
        }

        @ParameterizedTest
        @EnumSource(value = Operator.class, names = {"AND", "OR"})
        void logicalOperator_throws(Operator operator) {
            assertThatThrownBy(() -> OperatorMethodMapper.mapArithmeticOperator(operator))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("arithmetic");
        }
    }

    // ========================================================================
    // mapComparisonOperator Tests - Expression Variant (useExpressionVariant=true)
    // ========================================================================

    @Nested
    @DisplayName("mapComparisonOperator with Expression variant")
    class MapComparisonOperatorExpressionVariantTests {

        @Test
        void eq_returnsEqual() {
            assertThat(OperatorMethodMapper.mapComparisonOperator(Operator.EQ, true)).isEqualTo(CB_EQUAL);
        }

        @Test
        void ne_returnsNotEqualExpr() {
            assertThat(OperatorMethodMapper.mapComparisonOperator(Operator.NE, true)).isEqualTo(CB_NOT_EQUAL_EXPR);
        }

        @Test
        void gt_returnsGreaterThanExpr() {
            assertThat(OperatorMethodMapper.mapComparisonOperator(Operator.GT, true)).isEqualTo(CB_GREATER_THAN_EXPR);
        }

        @Test
        void ge_returnsGreaterThanOrEqualExpr() {
            assertThat(OperatorMethodMapper.mapComparisonOperator(Operator.GE, true)).isEqualTo(CB_GREATER_THAN_OR_EQUAL_EXPR);
        }

        @Test
        void lt_returnsLessThanExpr() {
            assertThat(OperatorMethodMapper.mapComparisonOperator(Operator.LT, true)).isEqualTo(CB_LESS_THAN_EXPR);
        }

        @Test
        void le_returnsLessThanOrEqualExpr() {
            assertThat(OperatorMethodMapper.mapComparisonOperator(Operator.LE, true)).isEqualTo(CB_LESS_THAN_OR_EQUAL_EXPR);
        }

        @ParameterizedTest
        @EnumSource(value = Operator.class, names = {"ADD", "SUB", "MUL", "DIV", "MOD"})
        void arithmeticOperator_throws(Operator operator) {
            assertThatThrownBy(() -> OperatorMethodMapper.mapComparisonOperator(operator, true))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("comparison");
        }

        @ParameterizedTest
        @EnumSource(value = Operator.class, names = {"AND", "OR"})
        void logicalOperator_throws(Operator operator) {
            assertThatThrownBy(() -> OperatorMethodMapper.mapComparisonOperator(operator, true))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("comparison");
        }
    }

    // ========================================================================
    // mapComparisonOperator Tests - Comparable Variant (useExpressionVariant=false)
    // ========================================================================

    @Nested
    @DisplayName("mapComparisonOperator with Comparable variant")
    class MapComparisonOperatorComparableVariantTests {

        @Test
        void eq_returnsEqual() {
            // EQ returns same descriptor in both variants
            assertThat(OperatorMethodMapper.mapComparisonOperator(Operator.EQ, false)).isEqualTo(CB_EQUAL);
        }

        @Test
        void ne_returnsNotEqual() {
            // NE returns CB_NOT_EQUAL (not CB_NOT_EQUAL_EXPR)
            assertThat(OperatorMethodMapper.mapComparisonOperator(Operator.NE, false)).isEqualTo(CB_NOT_EQUAL);
        }

        @Test
        void gt_returnsGreaterThan() {
            // GT returns CB_GREATER_THAN (not CB_GREATER_THAN_EXPR)
            assertThat(OperatorMethodMapper.mapComparisonOperator(Operator.GT, false)).isEqualTo(CB_GREATER_THAN);
        }

        @Test
        void ge_returnsGreaterThanOrEqual() {
            assertThat(OperatorMethodMapper.mapComparisonOperator(Operator.GE, false)).isEqualTo(CB_GREATER_THAN_OR_EQUAL);
        }

        @Test
        void lt_returnsLessThan() {
            assertThat(OperatorMethodMapper.mapComparisonOperator(Operator.LT, false)).isEqualTo(CB_LESS_THAN);
        }

        @Test
        void le_returnsLessThanOrEqual() {
            assertThat(OperatorMethodMapper.mapComparisonOperator(Operator.LE, false)).isEqualTo(CB_LESS_THAN_OR_EQUAL);
        }

        @ParameterizedTest
        @EnumSource(value = Operator.class, names = {"ADD", "SUB", "MUL", "DIV", "MOD"})
        void arithmeticOperator_throws(Operator operator) {
            assertThatThrownBy(() -> OperatorMethodMapper.mapComparisonOperator(operator, false))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("comparison");
        }

        @ParameterizedTest
        @EnumSource(value = Operator.class, names = {"AND", "OR"})
        void logicalOperator_throws(Operator operator) {
            assertThatThrownBy(() -> OperatorMethodMapper.mapComparisonOperator(operator, false))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("comparison");
        }
    }

    // ========================================================================
    // Variant Distinction Tests - Verify expression vs comparable return different descriptors
    // ========================================================================

    @Nested
    @DisplayName("Expression vs Comparable variant distinction")
    class VariantDistinctionTests {

        @Test
        void ne_variantsReturnDifferentDescriptors() {
            var expressionVariant = OperatorMethodMapper.mapComparisonOperator(Operator.NE, true);
            var comparableVariant = OperatorMethodMapper.mapComparisonOperator(Operator.NE, false);

            assertThat(expressionVariant).isNotEqualTo(comparableVariant);
            assertThat(expressionVariant).isEqualTo(CB_NOT_EQUAL_EXPR);
            assertThat(comparableVariant).isEqualTo(CB_NOT_EQUAL);
        }

        @Test
        void gt_variantsReturnDifferentDescriptors() {
            var expressionVariant = OperatorMethodMapper.mapComparisonOperator(Operator.GT, true);
            var comparableVariant = OperatorMethodMapper.mapComparisonOperator(Operator.GT, false);

            assertThat(expressionVariant).isNotEqualTo(comparableVariant);
            assertThat(expressionVariant).isEqualTo(CB_GREATER_THAN_EXPR);
            assertThat(comparableVariant).isEqualTo(CB_GREATER_THAN);
        }

        @Test
        void ge_variantsReturnDifferentDescriptors() {
            var expressionVariant = OperatorMethodMapper.mapComparisonOperator(Operator.GE, true);
            var comparableVariant = OperatorMethodMapper.mapComparisonOperator(Operator.GE, false);

            assertThat(expressionVariant).isNotEqualTo(comparableVariant);
        }

        @Test
        void lt_variantsReturnDifferentDescriptors() {
            var expressionVariant = OperatorMethodMapper.mapComparisonOperator(Operator.LT, true);
            var comparableVariant = OperatorMethodMapper.mapComparisonOperator(Operator.LT, false);

            assertThat(expressionVariant).isNotEqualTo(comparableVariant);
        }

        @Test
        void le_variantsReturnDifferentDescriptors() {
            var expressionVariant = OperatorMethodMapper.mapComparisonOperator(Operator.LE, true);
            var comparableVariant = OperatorMethodMapper.mapComparisonOperator(Operator.LE, false);

            assertThat(expressionVariant).isNotEqualTo(comparableVariant);
        }

        @Test
        void eq_variantsReturnSameDescriptor() {
            // EQ is special - both variants return the same CB_EQUAL
            var expressionVariant = OperatorMethodMapper.mapComparisonOperator(Operator.EQ, true);
            var comparableVariant = OperatorMethodMapper.mapComparisonOperator(Operator.EQ, false);

            assertThat(expressionVariant).isEqualTo(comparableVariant);
            assertThat(expressionVariant).isEqualTo(CB_EQUAL);
        }
    }

    // ========================================================================
    // isComparisonOperator Tests
    // ========================================================================

    @Nested
    @DisplayName("isComparisonOperator")
    class IsComparisonOperatorTests {

        @ParameterizedTest
        @EnumSource(value = Operator.class, names = {"EQ", "NE", "GT", "GE", "LT", "LE"})
        void comparisonOperators_returnTrue(Operator operator) {
            assertThat(OperatorMethodMapper.isComparisonOperator(operator)).isTrue();
        }

        @ParameterizedTest
        @EnumSource(value = Operator.class, names = {"ADD", "SUB", "MUL", "DIV", "MOD"})
        void arithmeticOperators_returnFalse(Operator operator) {
            assertThat(OperatorMethodMapper.isComparisonOperator(operator)).isFalse();
        }

        @ParameterizedTest
        @EnumSource(value = Operator.class, names = {"AND", "OR"})
        void logicalOperators_returnFalse(Operator operator) {
            assertThat(OperatorMethodMapper.isComparisonOperator(operator)).isFalse();
        }
    }

    // ========================================================================
    // isArithmeticOperator Tests
    // ========================================================================

    @Nested
    @DisplayName("isArithmeticOperator")
    class IsArithmeticOperatorTests {

        @ParameterizedTest
        @EnumSource(value = Operator.class, names = {"ADD", "SUB", "MUL", "DIV", "MOD"})
        void arithmeticOperators_returnTrue(Operator operator) {
            assertThat(OperatorMethodMapper.isArithmeticOperator(operator)).isTrue();
        }

        @ParameterizedTest
        @EnumSource(value = Operator.class, names = {"EQ", "NE", "GT", "GE", "LT", "LE"})
        void comparisonOperators_returnFalse(Operator operator) {
            assertThat(OperatorMethodMapper.isArithmeticOperator(operator)).isFalse();
        }

        @ParameterizedTest
        @EnumSource(value = Operator.class, names = {"AND", "OR"})
        void logicalOperators_returnFalse(Operator operator) {
            assertThat(OperatorMethodMapper.isArithmeticOperator(operator)).isFalse();
        }
    }

    // ========================================================================
    // isLogicalOperator Tests
    // ========================================================================

    @Nested
    @DisplayName("isLogicalOperator")
    class IsLogicalOperatorTests {

        @ParameterizedTest
        @EnumSource(value = Operator.class, names = {"AND", "OR"})
        void logicalOperators_returnTrue(Operator operator) {
            assertThat(OperatorMethodMapper.isLogicalOperator(operator)).isTrue();
        }

        @ParameterizedTest
        @EnumSource(value = Operator.class, names = {"EQ", "NE", "GT", "GE", "LT", "LE"})
        void comparisonOperators_returnFalse(Operator operator) {
            assertThat(OperatorMethodMapper.isLogicalOperator(operator)).isFalse();
        }

        @ParameterizedTest
        @EnumSource(value = Operator.class, names = {"ADD", "SUB", "MUL", "DIV", "MOD"})
        void arithmeticOperators_returnFalse(Operator operator) {
            assertThat(OperatorMethodMapper.isLogicalOperator(operator)).isFalse();
        }
    }
}
