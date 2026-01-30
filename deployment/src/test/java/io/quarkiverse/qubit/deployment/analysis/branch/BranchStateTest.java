package io.quarkiverse.qubit.deployment.analysis.branch;

import io.quarkiverse.qubit.deployment.ast.LambdaExpression;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Optional;
import java.util.stream.Stream;

import static io.quarkiverse.qubit.deployment.ast.LambdaExpression.BinaryOp.Operator;
import static io.quarkiverse.qubit.deployment.ast.LambdaExpression.BinaryOp.Operator.AND;
import static io.quarkiverse.qubit.deployment.ast.LambdaExpression.BinaryOp.Operator.OR;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for BranchState sealed interface and state machine transitions.
 *
 * <p>Tests verify the immutable state machine logic for AND/OR combination
 * during branch instruction analysis.
 *
 * <p>Note: This test class uses the package-private {@link BranchStateTestingAPI} interface
 * to test the state machine logic in isolation.
 */
@DisplayName("BranchState")
class BranchStateTest {

    // ==================== Test Fixtures ====================

    private static final LambdaExpression.Constant TRUE_CONST =
            new LambdaExpression.Constant(true, boolean.class);
    private static final LambdaExpression.Constant FALSE_CONST =
            new LambdaExpression.Constant(false, boolean.class);
    private static final LambdaExpression.Constant INT_CONST =
            new LambdaExpression.Constant(1, int.class);

    private static final LambdaExpression.BinaryOp OR_EXPR =
            LambdaExpression.BinaryOp.or(TRUE_CONST, FALSE_CONST);
    private static final LambdaExpression.BinaryOp AND_EXPR =
            LambdaExpression.BinaryOp.and(TRUE_CONST, FALSE_CONST);
    private static final LambdaExpression.BinaryOp EQ_EXPR =
            LambdaExpression.BinaryOp.eq(INT_CONST, INT_CONST);

    // ==================== Initial State Tests ====================

    @Nested
    @DisplayName("Initial State")
    class InitialStateTests {

        @Test
        @DisplayName("isInitial returns true")
        void isInitialReturnsTrue() {
            assertThat(new BranchState.Initial().isInitial()).isTrue();
        }

        @Test
        @DisplayName("determineCombineOperator returns null")
        void determineCombineOperatorReturnsNull() {
            assertThat(new BranchState.Initial().determineCombineOperator(true, null)).isNull();
        }

        @ParameterizedTest(name = "transition({0}) → {1}")
        @MethodSource("io.quarkiverse.qubit.deployment.analysis.branch.BranchStateTest#initialTransitions")
        @DisplayName("transitions correctly")
        void transitionsCorrectly(boolean jumpTarget, Class<? extends BranchState> expectedType) {
            BranchState next = new BranchState.Initial().transition(jumpTarget, false);
            assertThat(next).isInstanceOf(expectedType);
            assertThat(next.isInitial()).isFalse();
        }

        @Test
        @DisplayName("afterCombination returns self")
        void afterCombinationReturnsSelf() {
            BranchState.Initial initial = new BranchState.Initial();
            assertThat(initial.afterCombination(true, Optional.empty(), null)).isSameAs(initial);
        }
    }

    static Stream<Arguments> initialTransitions() {
        return Stream.of(
                Arguments.of(true, BranchState.OrMode.class),
                Arguments.of(false, BranchState.AndMode.class)
        );
    }

    // ==================== AND Mode Tests ====================

    @Nested
    @DisplayName("AndMode")
    class AndModeTests {

        @ParameterizedTest(name = "history={0}, jumpTarget={1} → {2}")
        @MethodSource("io.quarkiverse.qubit.deployment.analysis.branch.BranchStateTest#andModeDetermineOperator")
        @DisplayName("determineCombineOperator without stack")
        void determineCombineOperator(Optional<Boolean> history, boolean jumpTarget, Operator expected) {
            BranchState.AndMode andMode = new BranchState.AndMode(history, false);
            assertThat(andMode.determineCombineOperator(jumpTarget, null)).isEqualTo(expected);
        }

        @ParameterizedTest(name = "history={0}, jumpTarget={1} → {2}")
        @MethodSource("io.quarkiverse.qubit.deployment.analysis.branch.BranchStateTest#andModeTransitions")
        @DisplayName("transitions correctly")
        void transitionsCorrectly(Optional<Boolean> history, boolean jumpTarget,
                                  Class<? extends BranchState> expectedType) {
            BranchState next = new BranchState.AndMode(history, false).transition(jumpTarget, false);
            assertThat(next).isInstanceOf(expectedType);
        }

        @Test
        @DisplayName("transition does not mutate original")
        void transitionDoesNotMutate() {
            BranchState.AndMode original = new BranchState.AndMode(Optional.empty(), false);
            BranchState next = original.transition(false, false);
            assertThat(original.lastJumpTarget()).isEmpty();
            assertThat(next).isNotSameAs(original);
        }

        @ParameterizedTest(name = "stackTop={0} → {1}")
        @MethodSource("io.quarkiverse.qubit.deployment.analysis.branch.BranchStateTest#andModeCompletingWithStack")
        @DisplayName("completing AND group with stack context")
        void completingAndGroupWithStack(String stackDesc, LambdaExpression stackTop, Operator expected) {
            BranchState.AndMode andMode = new BranchState.AndMode(Optional.of(false), false);
            assertThat(andMode.determineCombineOperator(true, stackTop)).isEqualTo(expected);
        }

        @Test
        @DisplayName("completing AND group with prevBooleanCheck returns null")
        void completingWithPrevBooleanCheckReturnsNull() {
            BranchState.AndMode andMode = new BranchState.AndMode(Optional.of(false), true);
            assertThat(andMode.determineCombineOperator(true, null)).isNull();
        }

        @ParameterizedTest(name = "nested OR in {0} subtree → AND")
        @MethodSource("io.quarkiverse.qubit.deployment.analysis.branch.BranchStateTest#nestedOrExpressions")
        @DisplayName("detects nested OR expressions")
        void detectsNestedOr(String position, LambdaExpression.BinaryOp expr) {
            BranchState.AndMode andMode = new BranchState.AndMode(Optional.of(false), false);
            assertThat(andMode.determineCombineOperator(true, expr)).isEqualTo(AND);
        }
    }

    static Stream<Arguments> andModeDetermineOperator() {
        return Stream.of(
                // (history, jumpTarget, expectedOperator)
                Arguments.of(Optional.empty(), false, AND),        // noHistory
                Arguments.of(Optional.of(false), false, AND),      // bothFalse
                Arguments.of(Optional.of(false), true, AND),       // previousFalseCurrentTrue
                Arguments.of(Optional.of(true), true, OR),         // bothTrue
                Arguments.of(Optional.of(true), false, AND),       // previousTrueCurrentFalse
                Arguments.of(Optional.empty(), true, AND)          // emptyHistoryCurrentTrue
        );
    }

    static Stream<Arguments> andModeTransitions() {
        return Stream.of(
                Arguments.of(Optional.of(false), true, BranchState.OrMode.class),
                Arguments.of(Optional.of(false), false, BranchState.AndMode.class),
                Arguments.of(Optional.of(true), false, BranchState.AndMode.class)
        );
    }

    static Stream<Arguments> andModeCompletingWithStack() {
        return Stream.of(
                Arguments.of("null", null, AND),
                Arguments.of("OR expression", OR_EXPR, AND),
                Arguments.of("AND expression", AND_EXPR, OR),
                Arguments.of("EQ expression", EQ_EXPR, AND),
                Arguments.of("constant", TRUE_CONST, AND),
                Arguments.of("field access", new LambdaExpression.FieldAccess("name", String.class), AND)
        );
    }

    static Stream<Arguments> nestedOrExpressions() {
        LambdaExpression.BinaryOp nestedOr = LambdaExpression.BinaryOp.or(INT_CONST, INT_CONST);
        return Stream.of(
                Arguments.of("left", LambdaExpression.BinaryOp.and(nestedOr, INT_CONST)),
                Arguments.of("right", LambdaExpression.BinaryOp.and(INT_CONST, nestedOr)),
                Arguments.of("deep left", LambdaExpression.BinaryOp.and(
                        LambdaExpression.BinaryOp.and(nestedOr, INT_CONST), INT_CONST))
        );
    }

    // ==================== OR Mode Tests ====================

    @Nested
    @DisplayName("OrMode")
    class OrModeTests {

        @ParameterizedTest(name = "history={0}, jumpTarget={1} → {2}")
        @MethodSource("io.quarkiverse.qubit.deployment.analysis.branch.BranchStateTest#orModeDetermineOperator")
        @DisplayName("determineCombineOperator without stack")
        void determineCombineOperator(Optional<Boolean> history, boolean jumpTarget, Operator expected) {
            BranchState.OrMode orMode = new BranchState.OrMode(history, false);
            assertThat(orMode.determineCombineOperator(jumpTarget, null)).isEqualTo(expected);
        }

        @ParameterizedTest(name = "history={0}, jumpTarget={1} → {2}")
        @MethodSource("io.quarkiverse.qubit.deployment.analysis.branch.BranchStateTest#orModeTransitions")
        @DisplayName("transitions correctly")
        void transitionsCorrectly(Optional<Boolean> history, boolean jumpTarget,
                                  Class<? extends BranchState> expectedType) {
            BranchState next = new BranchState.OrMode(history, false).transition(jumpTarget, false);
            assertThat(next).isInstanceOf(expectedType);
        }

        @Test
        @DisplayName("transition does not mutate original")
        void transitionDoesNotMutate() {
            BranchState.OrMode original = new BranchState.OrMode(Optional.empty(), false);
            BranchState next = original.transition(true, false);
            assertThat(original.lastJumpTarget()).isEmpty();
            assertThat(next).isNotSameAs(original);
        }

        @ParameterizedTest(name = "jumpTarget={0}, stackTop={1} → {2}")
        @MethodSource("io.quarkiverse.qubit.deployment.analysis.branch.BranchStateTest#orModeWithStack")
        @DisplayName("determineCombineOperator with stack context")
        void determineCombineOperatorWithStack(boolean jumpTarget, String stackDesc,
                                               LambdaExpression stackTop, Operator expected) {
            BranchState.OrMode orMode = new BranchState.OrMode(Optional.of(true), false);
            assertThat(orMode.determineCombineOperator(jumpTarget, stackTop)).isEqualTo(expected);
        }

        @Test
        @DisplayName("afterCombination returns self")
        void afterCombinationReturnsSelf() {
            BranchState.OrMode orMode = new BranchState.OrMode(Optional.of(true), false);
            assertThat(orMode.afterCombination(true, Optional.of(true), OR)).isSameAs(orMode);
        }
    }

    static Stream<Arguments> orModeDetermineOperator() {
        return Stream.of(
                Arguments.of(Optional.empty(), true, OR),          // noHistory
                Arguments.of(Optional.of(true), true, OR),         // bothTrue
                Arguments.of(Optional.of(false), false, OR),       // bothFalse
                Arguments.of(Optional.of(true), false, OR),        // previousTrueCurrentFalse
                Arguments.of(Optional.of(false), true, OR)         // previousFalseCurrentTrue
        );
    }

    static Stream<Arguments> orModeTransitions() {
        return Stream.of(
                Arguments.of(Optional.of(false), false, BranchState.AndMode.class),
                Arguments.of(Optional.of(true), false, BranchState.OrMode.class),
                Arguments.of(Optional.of(true), true, BranchState.OrMode.class)
        );
    }

    static Stream<Arguments> orModeWithStack() {
        return Stream.of(
                Arguments.of(true, "OR expression", OR_EXPR, AND),
                Arguments.of(true, "AND expression", AND_EXPR, OR),
                Arguments.of(true, "null", null, OR),
                Arguments.of(true, "constant", TRUE_CONST, OR),
                Arguments.of(false, "OR expression", OR_EXPR, OR)
        );
    }

    // ==================== getLastJumpTarget Tests ====================

    @Nested
    @DisplayName("getLastJumpTarget")
    class GetLastJumpTargetTests {

        @ParameterizedTest(name = "{0} → {1}")
        @MethodSource("io.quarkiverse.qubit.deployment.analysis.branch.BranchStateTest#lastJumpTargetCases")
        @DisplayName("returns correct value")
        void returnsCorrectValue(String stateDesc, BranchState state, Optional<Boolean> expected) {
            assertThat(state.getLastJumpTarget()).isEqualTo(expected);
        }
    }

    static Stream<Arguments> lastJumpTargetCases() {
        return Stream.of(
                Arguments.of("Initial", new BranchState.Initial(), Optional.empty()),
                Arguments.of("AndMode(empty)", new BranchState.AndMode(Optional.empty(), false), Optional.empty()),
                Arguments.of("AndMode(true)", new BranchState.AndMode(Optional.of(true), false), Optional.of(true)),
                Arguments.of("AndMode(false)", new BranchState.AndMode(Optional.of(false), false), Optional.of(false)),
                Arguments.of("OrMode(empty)", new BranchState.OrMode(Optional.empty(), false), Optional.empty()),
                Arguments.of("OrMode(true)", new BranchState.OrMode(Optional.of(true), false), Optional.of(true)),
                Arguments.of("OrMode(false)", new BranchState.OrMode(Optional.of(false), false), Optional.of(false))
        );
    }

    // ==================== afterCombination Tests ====================

    @Nested
    @DisplayName("AndMode.afterCombination")
    class AndModeAfterCombinationTests {

        @ParameterizedTest(name = "current={0}, prev={1}, op={2} → {3}")
        @MethodSource("io.quarkiverse.qubit.deployment.analysis.branch.BranchStateTest#andModeAfterCombinationCases")
        @DisplayName("transitions correctly")
        void transitionsCorrectly(boolean currentJump, Optional<Boolean> prevJump,
                                  Operator usedOp, boolean shouldTransition) {
            BranchState.AndMode andMode = new BranchState.AndMode(Optional.of(false), false);
            BranchState result = andMode.afterCombination(currentJump, prevJump, usedOp);

            if (shouldTransition) {
                assertThat(result).isInstanceOf(BranchState.OrMode.class);
            } else {
                assertThat(result).isSameAs(andMode);
            }
        }

        @Test
        @DisplayName("with null operator returns self")
        void withNullOperatorReturnsSelf() {
            BranchState.AndMode andMode = new BranchState.AndMode(Optional.of(false), false);
            assertThat(andMode.afterCombination(true, Optional.of(false), null)).isSameAs(andMode);
        }
    }

    static Stream<Arguments> andModeAfterCombinationCases() {
        return Stream.of(
                // (currentJump, prevJump, usedOp, shouldTransition)
                Arguments.of(true, Optional.of(false), AND, true),    // completing AND group
                Arguments.of(false, Optional.of(false), AND, false),  // not completing
                Arguments.of(true, Optional.of(true), AND, false),    // prev was true
                Arguments.of(true, Optional.empty(), AND, false),     // no prev
                Arguments.of(true, Optional.of(false), OR, false)     // used OR, not AND
        );
    }

    // ==================== Complex Scenario Tests ====================

    @Nested
    @DisplayName("Complex Scenarios")
    class ComplexScenarioTests {

        @Test
        @DisplayName("simple AND chain: (a > 5) && (b > 10)")
        void simpleAndChain() {
            BranchState.Initial initial = new BranchState.Initial();
            assertThat(initial.determineCombineOperator(false, null)).isNull();

            BranchState state = initial.transition(false, false);
            assertThat(state).isInstanceOf(BranchState.AndMode.class);

            BranchState.AndMode andState = (BranchState.AndMode) state;
            assertThat(andState.determineCombineOperator(false, null)).isEqualTo(AND);
            assertThat(andState.transition(false, false)).isInstanceOf(BranchState.AndMode.class);
        }

        @Test
        @DisplayName("simple OR chain: (a > 5) || (b > 10)")
        void simpleOrChain() {
            BranchState.Initial initial = new BranchState.Initial();
            assertThat(initial.determineCombineOperator(true, null)).isNull();

            BranchState state = initial.transition(true, false);
            assertThat(state).isInstanceOf(BranchState.OrMode.class);

            BranchState.OrMode orState = (BranchState.OrMode) state;
            assertThat(orState.determineCombineOperator(true, null)).isEqualTo(OR);
            assertThat(orState.transition(true, false)).isInstanceOf(BranchState.OrMode.class);
        }

        @Test
        @DisplayName("AND then OR: (a > 5 && b > 10) || (c > 20)")
        void andThenOr() {
            BranchState state = new BranchState.Initial().transition(false, false);
            assertThat(state).isInstanceOf(BranchState.AndMode.class);

            BranchState.AndMode andState1 = (BranchState.AndMode) state;
            assertThat(andState1.determineCombineOperator(false, null)).isEqualTo(AND);
            state = andState1.transition(false, false);

            BranchState.AndMode andState2 = (BranchState.AndMode) state;
            state = andState2.transition(true, false);
            assertThat(state).isInstanceOf(BranchState.OrMode.class);

            BranchState.OrMode orState = (BranchState.OrMode) state;
            assertThat(orState.determineCombineOperator(true, null)).isEqualTo(OR);
        }

        @Test
        @DisplayName("OR then AND: (a > 5 || b > 10) && (c > 20)")
        void orThenAnd() {
            BranchState state = new BranchState.Initial().transition(true, false);
            assertThat(state).isInstanceOf(BranchState.OrMode.class);

            BranchState.OrMode orState1 = (BranchState.OrMode) state;
            assertThat(orState1.determineCombineOperator(true, null)).isEqualTo(OR);
            state = orState1.transition(true, false);
            assertThat(state).isInstanceOf(BranchState.OrMode.class);

            BranchState.OrMode orState2 = (BranchState.OrMode) state;
            state = orState2.transition(false, false);

            BranchState.OrMode orState3 = (BranchState.OrMode) state;
            state = orState3.transition(false, false);
            assertThat(state).isInstanceOf(BranchState.AndMode.class);
        }

        @Test
        @DisplayName("three condition AND: (a > 5) && (b > 10) && (c > 20)")
        void threeConditionAnd() {
            BranchState state = new BranchState.Initial().transition(false, false);

            BranchState.AndMode andState1 = (BranchState.AndMode) state;
            assertThat(andState1.determineCombineOperator(false, null)).isEqualTo(AND);
            state = andState1.transition(false, false);

            BranchState.AndMode andState2 = (BranchState.AndMode) state;
            assertThat(andState2.determineCombineOperator(false, null)).isEqualTo(AND);
        }

        @Test
        @DisplayName("three condition OR: (a > 5) || (b > 10) || (c > 20)")
        void threeConditionOr() {
            BranchState state = new BranchState.Initial().transition(true, false);

            BranchState.OrMode orState1 = (BranchState.OrMode) state;
            assertThat(orState1.determineCombineOperator(true, null)).isEqualTo(OR);
            state = orState1.transition(true, false);

            BranchState.OrMode orState2 = (BranchState.OrMode) state;
            assertThat(orState2.determineCombineOperator(true, null)).isEqualTo(OR);
        }
    }
}
