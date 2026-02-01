package io.quarkiverse.qubit.deployment.analysis;

import io.quarkiverse.qubit.deployment.ast.LambdaExpression;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static io.quarkiverse.qubit.deployment.ast.LambdaExpression.Constant;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Error path tests for CapturedVariableHelper.
 * Tests exception handling for edge cases in captured variable operations.
 */
@DisplayName("CapturedVariableHelper Error Path Tests")
class CapturedVariableHelperErrorTest {

    @Nested
    @DisplayName("combinePredicatesWithAnd(List) error cases")
    class CombinePredicatesListTests {

        @Test
        @DisplayName("K1: Empty list throws IllegalArgumentException")
        void combinePredicates_emptyList_throws() {
            // Given
            List<LambdaExpression> emptyList = List.of();

            // When/Then
            assertThatThrownBy(() -> CapturedVariableHelper.combinePredicatesWithAnd(emptyList))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Cannot combine empty predicate list");
        }

        @Test
        @DisplayName("Single element list returns that element unchanged")
        void combinePredicates_singleElement_returnsSameElement() {
            // Given
            LambdaExpression single = Constant.TRUE;
            List<LambdaExpression> singletonList = List.of(single);

            // When
            LambdaExpression result = CapturedVariableHelper.combinePredicatesWithAnd(singletonList);

            // Then
            assertThat(result).isSameAs(single);
        }

        @Test
        @DisplayName("Two elements returns AND of both")
        void combinePredicates_twoElements_returnsAnd() {
            // Given
            List<LambdaExpression> predicates = List.of(Constant.TRUE, Constant.FALSE);

            // When
            LambdaExpression result = CapturedVariableHelper.combinePredicatesWithAnd(predicates);

            // Then
            assertThat(result).isInstanceOf(LambdaExpression.BinaryOp.class);
            LambdaExpression.BinaryOp binaryOp = (LambdaExpression.BinaryOp) result;
            assertThat(binaryOp.operator()).isEqualTo(LambdaExpression.BinaryOp.Operator.AND);
        }

        @Test
        @DisplayName("Three elements chains AND operations left-associatively")
        void combinePredicates_threeElements_chainsAnds() {
            // Given
            LambdaExpression p1 = Constant.TRUE;
            LambdaExpression p2 = Constant.FALSE;
            LambdaExpression p3 = Constant.TRUE;
            List<LambdaExpression> predicates = List.of(p1, p2, p3);

            // When
            LambdaExpression result = CapturedVariableHelper.combinePredicatesWithAnd(predicates);

            // Then: should be ((p1 AND p2) AND p3)
            assertThat(result).isInstanceOf(LambdaExpression.BinaryOp.class);
            LambdaExpression.BinaryOp outer = (LambdaExpression.BinaryOp) result;
            assertThat(outer.operator()).isEqualTo(LambdaExpression.BinaryOp.Operator.AND);
            assertThat(outer.right()).isSameAs(p3);
            assertThat(outer.left()).isInstanceOf(LambdaExpression.BinaryOp.class);
        }
    }

    @Nested
    @DisplayName("combinePredicatesWithAnd(expr, expr) edge cases")
    class CombinePredicatesPairTests {

        @Test
        @DisplayName("Both null returns null")
        void combinePredicates_bothNull_returnsNull() {
            LambdaExpression result = CapturedVariableHelper.combinePredicatesWithAnd(null, null);
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("First null returns second")
        void combinePredicates_firstNull_returnsSecond() {
            LambdaExpression second = Constant.TRUE;

            LambdaExpression result = CapturedVariableHelper.combinePredicatesWithAnd(null, second);

            assertThat(result).isSameAs(second);
        }

        @Test
        @DisplayName("Second null returns first")
        void combinePredicates_secondNull_returnsFirst() {
            LambdaExpression first = Constant.FALSE;

            LambdaExpression result = CapturedVariableHelper.combinePredicatesWithAnd(first, null);

            assertThat(result).isSameAs(first);
        }

        @Test
        @DisplayName("Both non-null returns AND")
        void combinePredicates_bothNonNull_returnsAnd() {
            LambdaExpression first = Constant.TRUE;
            LambdaExpression second = Constant.FALSE;

            LambdaExpression result = CapturedVariableHelper.combinePredicatesWithAnd(first, second);

            assertThat(result).isInstanceOf(LambdaExpression.BinaryOp.class);
            LambdaExpression.BinaryOp binaryOp = (LambdaExpression.BinaryOp) result;
            assertThat(binaryOp.operator()).isEqualTo(LambdaExpression.BinaryOp.Operator.AND);
            assertThat(binaryOp.left()).isSameAs(first);
            assertThat(binaryOp.right()).isSameAs(second);
        }
    }

    @Nested
    @DisplayName("renumberCapturedVariables edge cases")
    class RenumberCapturedVariablesTests {

        @Test
        @DisplayName("Null expression returns null")
        void renumber_nullExpression_returnsNull() {
            LambdaExpression result = CapturedVariableHelper.renumberCapturedVariables(null, 5);
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("Zero offset returns same expression")
        void renumber_zeroOffset_returnsSameExpression() {
            LambdaExpression expr = new LambdaExpression.CapturedVariable(0, String.class);

            LambdaExpression result = CapturedVariableHelper.renumberCapturedVariables(expr, 0);

            assertThat(result).isSameAs(expr);
        }

        @Test
        @DisplayName("Non-zero offset creates new CapturedVariable with adjusted index")
        void renumber_nonZeroOffset_adjustsIndex() {
            LambdaExpression.CapturedVariable original = new LambdaExpression.CapturedVariable(2, Integer.class);

            LambdaExpression result = CapturedVariableHelper.renumberCapturedVariables(original, 3);

            assertThat(result).isInstanceOf(LambdaExpression.CapturedVariable.class);
            LambdaExpression.CapturedVariable adjusted = (LambdaExpression.CapturedVariable) result;
            assertThat(adjusted.index()).isEqualTo(5); // 2 + 3
            assertThat(adjusted.type()).isEqualTo(Integer.class);
        }
    }

    @Nested
    @DisplayName("countCapturedVariables edge cases")
    class CountCapturedVariablesTests {

        @Test
        @DisplayName("Null expression returns zero")
        void count_nullExpression_returnsZero() {
            int count = CapturedVariableHelper.countCapturedVariables(null);
            assertThat(count).isZero();
        }

        @Test
        @DisplayName("Expression without captured variables returns zero")
        void count_noCapture_returnsZero() {
            LambdaExpression expr = new LambdaExpression.FieldAccess("name", String.class);

            int count = CapturedVariableHelper.countCapturedVariables(expr);

            assertThat(count).isZero();
        }

        @Test
        @DisplayName("Same captured variable used twice counts as one")
        void count_duplicateCaptured_countsOnce() {
            // Build: capturedVar(0) == capturedVar(0)
            LambdaExpression.CapturedVariable captured = new LambdaExpression.CapturedVariable(0, String.class);
            LambdaExpression expr = LambdaExpression.BinaryOp.eq(captured, captured);

            int count = CapturedVariableHelper.countCapturedVariables(expr);

            assertThat(count).isEqualTo(1); // Same index used twice, counts once
        }
    }

    @Nested
    @DisplayName("countCapturedVariablesInSortExpressions edge cases")
    class CountCapturedInSortTests {

        @Test
        @DisplayName("Null list returns zero")
        void count_nullList_returnsZero() {
            int count = CapturedVariableHelper.countCapturedVariablesInSortExpressions(null);
            assertThat(count).isZero();
        }

        @Test
        @DisplayName("Empty list returns zero")
        void count_emptyList_returnsZero() {
            int count = CapturedVariableHelper.countCapturedVariablesInSortExpressions(List.of());
            assertThat(count).isZero();
        }
    }

    @Nested
    @DisplayName("validateCapturedVariableIndices tests")
    class ValidateCapturedVariableIndicesTests {

        @Test
        @DisplayName("Null expression is valid (no-op)")
        void validate_nullExpression_noException() {
            // Should not throw
            CapturedVariableHelper.validateCapturedVariableIndices(null, 5);
        }

        @Test
        @DisplayName("Expression without captured variables is valid regardless of count")
        void validate_noCapturedVariables_valid() {
            LambdaExpression expr = new LambdaExpression.FieldAccess("name", String.class);

            // Should not throw even with expectedCount=0
            CapturedVariableHelper.validateCapturedVariableIndices(expr, 0);
            CapturedVariableHelper.validateCapturedVariableIndices(expr, 5);
        }

        @Test
        @DisplayName("Valid index within bounds passes")
        void validate_validIndex_passes() {
            LambdaExpression.CapturedVariable captured = new LambdaExpression.CapturedVariable(0, String.class);

            // Index 0 with expectedCount 1 is valid (0 < 1)
            CapturedVariableHelper.validateCapturedVariableIndices(captured, 1);
        }

        @Test
        @DisplayName("Multiple valid indices within bounds passes")
        void validate_multipleValidIndices_passes() {
            LambdaExpression.CapturedVariable cap0 = new LambdaExpression.CapturedVariable(0, String.class);
            LambdaExpression.CapturedVariable cap1 = new LambdaExpression.CapturedVariable(1, Integer.class);
            LambdaExpression expr = LambdaExpression.BinaryOp.eq(cap0, cap1);

            // Indices 0 and 1 with expectedCount 2 is valid
            CapturedVariableHelper.validateCapturedVariableIndices(expr, 2);
        }

        @Test
        @DisplayName("Index equal to expectedCount throws (out of bounds)")
        void validate_indexEqualsCount_throws() {
            LambdaExpression.CapturedVariable captured = new LambdaExpression.CapturedVariable(2, String.class);

            // Index 2 with expectedCount 2 is out of bounds (array indices 0,1)
            assertThatThrownBy(() -> CapturedVariableHelper.validateCapturedVariableIndices(captured, 2))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("out of bounds")
                    .hasMessageContaining("index 2")
                    .hasMessageContaining("expectedCount=2");
        }

        @Test
        @DisplayName("Index greater than expectedCount throws (out of bounds)")
        void validate_indexGreaterThanCount_throws() {
            LambdaExpression.CapturedVariable captured = new LambdaExpression.CapturedVariable(5, String.class);

            assertThatThrownBy(() -> CapturedVariableHelper.validateCapturedVariableIndices(captured, 3))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("out of bounds")
                    .hasMessageContaining("index 5")
                    .hasMessageContaining("expectedCount=3");
        }

        @Test
        @DisplayName("Captured variables with expectedCount=0 throws")
        void validate_capturedWithZeroExpected_throws() {
            LambdaExpression.CapturedVariable captured = new LambdaExpression.CapturedVariable(0, String.class);

            assertThatThrownBy(() -> CapturedVariableHelper.validateCapturedVariableIndices(captured, 0))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("1 captured variable(s) but expectedCount=0");
        }

        @Test
        @DisplayName("Varargs validation handles multiple expressions")
        void validate_varargs_validatesAll() {
            LambdaExpression.CapturedVariable cap0 = new LambdaExpression.CapturedVariable(0, String.class);
            LambdaExpression.CapturedVariable cap5 = new LambdaExpression.CapturedVariable(5, Integer.class);

            // First expression is valid, second is out of bounds
            assertThatThrownBy(() ->
                    CapturedVariableHelper.validateCapturedVariableIndices(2, cap0, cap5))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("out of bounds");
        }

        @Test
        @DisplayName("Varargs validation skips null expressions")
        void validate_varargs_skipsNulls() {
            LambdaExpression.CapturedVariable cap0 = new LambdaExpression.CapturedVariable(0, String.class);

            // Should not throw - nulls are skipped
            CapturedVariableHelper.validateCapturedVariableIndices(1, null, cap0, null);
        }

        @Test
        @DisplayName("Nested expressions are validated recursively")
        void validate_nestedExpressions_validatedRecursively() {
            LambdaExpression.CapturedVariable cap0 = new LambdaExpression.CapturedVariable(0, String.class);
            LambdaExpression.CapturedVariable cap5 = new LambdaExpression.CapturedVariable(5, Integer.class);
            // Create nested: (cap0 == cap5)
            LambdaExpression nested = LambdaExpression.BinaryOp.eq(cap0, cap5);

            // cap5 has index 5, which is out of bounds for expectedCount=2
            assertThatThrownBy(() -> CapturedVariableHelper.validateCapturedVariableIndices(nested, 2))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("out of bounds");
        }
    }
}
