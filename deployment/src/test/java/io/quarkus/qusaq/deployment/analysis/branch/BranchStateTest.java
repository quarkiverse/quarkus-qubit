package io.quarkus.qusaq.deployment.analysis.branch;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static io.quarkus.qusaq.deployment.LambdaExpression.BinaryOp.Operator;
import static io.quarkus.qusaq.deployment.LambdaExpression.BinaryOp.Operator.AND;
import static io.quarkus.qusaq.deployment.LambdaExpression.BinaryOp.Operator.OR;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for BranchState sealed interface and state machine transitions.
 *
 * <p>Tests verify the immutable state machine logic for AND/OR combination
 * during branch instruction analysis.
 *
 * <p>Note: This test class uses the package-private {@link BranchStateTestingAPI} interface
 * to test the state machine logic in isolation. The {@code transition()} and
 * {@code determineCombineOperator()} methods are not part of the public {@link BranchState}
 * API and are accessible only for testing purposes within the same package.
 */
class BranchStateTest {

    // ==================== Initial State Tests ====================

    @Test
    void initialState_isInitialReturnsTrue() {
        BranchState state = new BranchState.Initial();
        assertThat(state.isInitial()).isTrue();
    }

    @Test
    void initialState_determineCombineOperatorReturnsNull() {
        BranchState.Initial state = new BranchState.Initial();

        Operator combineOp = state.determineCombineOperator(true, null);

        assertThat(combineOp).isNull();
    }

    @Test
    void initialState_transitionToTrue_entersOrMode() {
        BranchState.Initial initial = new BranchState.Initial();

        BranchState next = initial.transition(true, false);

        assertThat(next).isInstanceOf(BranchState.OrMode.class);
        assertThat(next.isInitial()).isFalse();
    }

    @Test
    void initialState_transitionToFalse_entersAndMode() {
        BranchState.Initial initial = new BranchState.Initial();

        BranchState next = initial.transition(false, false);

        assertThat(next).isInstanceOf(BranchState.AndMode.class);
        assertThat(next.isInitial()).isFalse();
    }

    // ==================== AND Mode Tests ====================

    @Test
    void andMode_noHistory_determineCombineOperatorReturnsAnd() {
        BranchState.AndMode andMode = new BranchState.AndMode(Optional.empty(), false);

        Operator combineOp = andMode.determineCombineOperator(false, null);

        assertThat(combineOp).isEqualTo(AND);
    }

    @Test
    void andMode_bothFalse_combinedWithAnd() {
        // Simulates: (a > 5) && (b > 10)
        // Both jump to FALSE (fall-through continues)
        BranchState.AndMode andMode = new BranchState.AndMode(Optional.of(false), false);

        Operator combineOp = andMode.determineCombineOperator(false, null);

        assertThat(combineOp).isEqualTo(AND);
    }

    @Test
    void andMode_previousFalseCurrentTrue_completesAndGroup() {
        // Completing nested AND group
        // Previous: jump to FALSE (first condition in AND)
        // Current: jump to TRUE (last condition in AND)
        BranchState.AndMode andMode = new BranchState.AndMode(Optional.of(false), false);

        Operator combineOp = andMode.determineCombineOperator(true, null);

        assertThat(combineOp).isEqualTo(AND);
    }

    @Test
    void andMode_bothTrue_combinedWithOr() {
        // Alternative branches (OR)
        // Previous: jump to TRUE
        // Current: jump to TRUE
        BranchState.AndMode andMode = new BranchState.AndMode(Optional.of(true), false);

        Operator combineOp = andMode.determineCombineOperator(true, null);

        assertThat(combineOp).isEqualTo(OR);
    }

    @Test
    void andMode_previousFalseCurrentTrue_transitionsToOrMode() {
        // After completing AND group, enter OR mode for alternatives
        BranchState.AndMode andMode = new BranchState.AndMode(Optional.of(false), false);

        BranchState next = andMode.transition(true, false);

        assertThat(next).isInstanceOf(BranchState.OrMode.class);
    }

    @Test
    void andMode_bothFalse_staysInAndMode() {
        BranchState.AndMode andMode = new BranchState.AndMode(Optional.of(false), false);

        BranchState next = andMode.transition(false, false);

        assertThat(next).isInstanceOf(BranchState.AndMode.class);
    }

    @Test
    void andMode_previousTrueCurrentFalse_staysInAndMode() {
        BranchState.AndMode andMode = new BranchState.AndMode(Optional.of(true), false);

        BranchState next = andMode.transition(false, false);

        assertThat(next).isInstanceOf(BranchState.AndMode.class);
    }

    // ==================== OR Mode Tests ====================

    @Test
    void orMode_noHistory_determineCombineOperatorReturnsOr() {
        BranchState.OrMode orMode = new BranchState.OrMode(Optional.empty(), false);

        Operator combineOp = orMode.determineCombineOperator(true, null);

        assertThat(combineOp).isEqualTo(OR);
    }

    @Test
    void orMode_bothTrue_combinedWithOr() {
        // Alternative conditions (OR chain)
        BranchState.OrMode orMode = new BranchState.OrMode(Optional.of(true), false);

        Operator combineOp = orMode.determineCombineOperator(true, null);

        assertThat(combineOp).isEqualTo(OR);
    }

    @Test
    void orMode_bothFalse_combinedWithOr() {
        // Transitioning to nested AND group, but still combine with OR first
        BranchState.OrMode orMode = new BranchState.OrMode(Optional.of(false), false);

        Operator combineOp = orMode.determineCombineOperator(false, null);

        assertThat(combineOp).isEqualTo(OR);
    }

    @Test
    void orMode_bothFalse_transitionsToAndMode() {
        // Two FALSE jumps in a row → nested AND group
        BranchState.OrMode orMode = new BranchState.OrMode(Optional.of(false), false);

        BranchState next = orMode.transition(false, false);

        assertThat(next).isInstanceOf(BranchState.AndMode.class);
    }

    @Test
    void orMode_previousTrueCurrentFalse_staysInOrMode() {
        BranchState.OrMode orMode = new BranchState.OrMode(Optional.of(true), false);

        BranchState next = orMode.transition(false, false);

        assertThat(next).isInstanceOf(BranchState.OrMode.class);
    }

    @Test
    void orMode_bothTrue_staysInOrMode() {
        BranchState.OrMode orMode = new BranchState.OrMode(Optional.of(true), false);

        BranchState next = orMode.transition(true, false);

        assertThat(next).isInstanceOf(BranchState.OrMode.class);
    }

    // ==================== Complex Scenario Tests ====================

    @Test
    void scenario_simpleAndChain() {
        // Expression: (a > 5) && (b > 10)
        // Both conditions jump to FALSE (fall-through continues)

        // Start: Initial
        BranchState.Initial initialState = new BranchState.Initial();

        // First comparison (a > 5) jumps to FALSE
        Operator op1 = initialState.determineCombineOperator(false, null);
        assertThat(op1).isNull(); // Don't combine, this is first condition (still in Initial)

        BranchState state = initialState.transition(false, false);
        assertThat(state).isInstanceOf(BranchState.AndMode.class);

        // Second comparison (b > 10) jumps to FALSE
        BranchState.AndMode andState = (BranchState.AndMode) state;
        Operator op2 = andState.determineCombineOperator(false, null);
        assertThat(op2).isEqualTo(AND); // Combine with AND

        state = andState.transition(false, false);
        assertThat(state).isInstanceOf(BranchState.AndMode.class);
    }

    @Test
    void scenario_simpleOrChain() {
        // Expression: (a > 5) || (b > 10)
        // Both conditions jump to TRUE (short-circuit success)

        // Start: Initial
        BranchState.Initial initialState = new BranchState.Initial();

        // First comparison (a > 5) jumps to TRUE
        Operator op1 = initialState.determineCombineOperator(true, null);
        assertThat(op1).isNull(); // Don't combine, this is first condition (still in Initial)

        BranchState state = initialState.transition(true, false);
        assertThat(state).isInstanceOf(BranchState.OrMode.class);

        // Second comparison (b > 10) jumps to TRUE
        BranchState.OrMode orState = (BranchState.OrMode) state;
        Operator op2 = orState.determineCombineOperator(true, null);
        assertThat(op2).isEqualTo(OR); // Combine with OR

        state = orState.transition(true, false);
        assertThat(state).isInstanceOf(BranchState.OrMode.class);
    }

    @Test
    void scenario_complexExpression_andThenOr() {
        // Expression: (a > 5 && b > 10) || (c > 20)
        // Pattern: FALSE, FALSE, TRUE, TRUE

        BranchState.Initial initialState = new BranchState.Initial();

        // First: a > 5 (jump FALSE)
        BranchState state = initialState.transition(false, false);
        assertThat(state).isInstanceOf(BranchState.AndMode.class);

        // Second: b > 10 (jump FALSE) - completes first AND group
        BranchState.AndMode andState1 = (BranchState.AndMode) state;
        Operator op1 = andState1.determineCombineOperator(false, null);
        assertThat(op1).isEqualTo(AND);
        state = andState1.transition(false, false);

        // Third: transition to TRUE (end of first AND group)
        // This should transition to OR mode
        BranchState.AndMode andState2 = (BranchState.AndMode) state;
        state = andState2.transition(true, false);
        assertThat(state).isInstanceOf(BranchState.OrMode.class);

        // Fourth: c > 20 (jump TRUE) - OR alternative
        BranchState.OrMode orState = (BranchState.OrMode) state;
        Operator op2 = orState.determineCombineOperator(true, null);
        assertThat(op2).isEqualTo(OR);
    }

    @Test
    void scenario_complexExpression_orThenAnd() {
        // Expression: (a > 5 || b > 10) && (c > 20)
        // Pattern: TRUE, TRUE, FALSE, FALSE

        BranchState.Initial initialState = new BranchState.Initial();

        // First: a > 5 (jump TRUE) - enters OR mode
        BranchState state = initialState.transition(true, false);
        assertThat(state).isInstanceOf(BranchState.OrMode.class);

        // Second: b > 10 (jump TRUE) - continues OR chain
        BranchState.OrMode orState1 = (BranchState.OrMode) state;
        Operator op1 = orState1.determineCombineOperator(true, null);
        assertThat(op1).isEqualTo(OR);
        state = orState1.transition(true, false);
        assertThat(state).isInstanceOf(BranchState.OrMode.class);

        // Third: transition to FALSE (start of AND group)
        BranchState.OrMode orState2 = (BranchState.OrMode) state;
        state = orState2.transition(false, false);
        // Should still be in OR mode or transition

        // Fourth: c > 20 (jump FALSE) - nested AND group
        BranchState.OrMode orState3 = (BranchState.OrMode) state;
        state = orState3.transition(false, false);
        assertThat(state).isInstanceOf(BranchState.AndMode.class);
    }

    @Test
    void scenario_threeConditionAnd() {
        // Expression: (a > 5) && (b > 10) && (c > 20)
        // All jump to FALSE

        BranchState.Initial initialState = new BranchState.Initial();

        // First condition
        BranchState state = initialState.transition(false, false);
        assertThat(state).isInstanceOf(BranchState.AndMode.class);

        // Second condition
        BranchState.AndMode andState1 = (BranchState.AndMode) state;
        Operator op1 = andState1.determineCombineOperator(false, null);
        assertThat(op1).isEqualTo(AND);
        state = andState1.transition(false, false);

        // Third condition
        BranchState.AndMode andState2 = (BranchState.AndMode) state;
        Operator op2 = andState2.determineCombineOperator(false, null);
        assertThat(op2).isEqualTo(AND);
    }

    @Test
    void scenario_threeConditionOr() {
        // Expression: (a > 5) || (b > 10) || (c > 20)
        // All jump to TRUE

        BranchState.Initial initialState = new BranchState.Initial();

        // First condition
        BranchState state = initialState.transition(true, false);
        assertThat(state).isInstanceOf(BranchState.OrMode.class);

        // Second condition
        BranchState.OrMode orState1 = (BranchState.OrMode) state;
        Operator op1 = orState1.determineCombineOperator(true, null);
        assertThat(op1).isEqualTo(OR);
        state = orState1.transition(true, false);

        // Third condition
        BranchState.OrMode orState2 = (BranchState.OrMode) state;
        Operator op2 = orState2.determineCombineOperator(true, null);
        assertThat(op2).isEqualTo(OR);
    }

    // ==================== Immutability Tests ====================

    @Test
    void andMode_transitionDoesNotMutateOriginal() {
        BranchState.AndMode original = new BranchState.AndMode(Optional.empty(), false);

        BranchState next = original.transition(false, false);

        // Original should be unchanged
        assertThat(original.lastJumpTarget()).isEmpty();
        // Next should have history
        assertThat(next).isNotSameAs(original);
    }

    @Test
    void orMode_transitionDoesNotMutateOriginal() {
        BranchState.OrMode original = new BranchState.OrMode(Optional.empty(), false);

        BranchState next = original.transition(true, false);

        // Original should be unchanged
        assertThat(original.lastJumpTarget()).isEmpty();
        // Next should have history
        assertThat(next).isNotSameAs(original);
    }
}
