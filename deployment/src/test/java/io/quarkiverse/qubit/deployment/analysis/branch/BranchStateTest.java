package io.quarkiverse.qubit.deployment.analysis.branch;

import io.quarkiverse.qubit.deployment.ast.LambdaExpression;
import org.junit.jupiter.api.Test;

import java.util.Optional;

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

    // ==================== getLastJumpTarget Tests (kill instanceof mutations) ====================

    @Test
    void getLastJumpTarget_initial_returnsEmpty() {
        BranchState state = new BranchState.Initial();

        Optional<Boolean> result = state.getLastJumpTarget();

        assertThat(result).isEmpty();
    }

    @Test
    void getLastJumpTarget_andMode_withEmptyHistory_returnsEmpty() {
        BranchState state = new BranchState.AndMode(Optional.empty(), false);

        Optional<Boolean> result = state.getLastJumpTarget();

        assertThat(result).isEmpty();
    }

    @Test
    void getLastJumpTarget_andMode_withTrueHistory_returnsTrue() {
        BranchState state = new BranchState.AndMode(Optional.of(true), false);

        Optional<Boolean> result = state.getLastJumpTarget();

        assertThat(result).isPresent();
        assertThat(result.get()).isTrue();
    }

    @Test
    void getLastJumpTarget_andMode_withFalseHistory_returnsFalse() {
        BranchState state = new BranchState.AndMode(Optional.of(false), false);

        Optional<Boolean> result = state.getLastJumpTarget();

        assertThat(result).isPresent();
        assertThat(result.get()).isFalse();
    }

    @Test
    void getLastJumpTarget_orMode_withEmptyHistory_returnsEmpty() {
        BranchState state = new BranchState.OrMode(Optional.empty(), false);

        Optional<Boolean> result = state.getLastJumpTarget();

        assertThat(result).isEmpty();
    }

    @Test
    void getLastJumpTarget_orMode_withTrueHistory_returnsTrue() {
        BranchState state = new BranchState.OrMode(Optional.of(true), false);

        Optional<Boolean> result = state.getLastJumpTarget();

        assertThat(result).isPresent();
        assertThat(result.get()).isTrue();
    }

    @Test
    void getLastJumpTarget_orMode_withFalseHistory_returnsFalse() {
        BranchState state = new BranchState.OrMode(Optional.of(false), false);

        Optional<Boolean> result = state.getLastJumpTarget();

        assertThat(result).isPresent();
        assertThat(result.get()).isFalse();
    }

    // ==================== OrMode.determineCombineOperator Edge Cases ====================

    @Test
    void orMode_determineCombineOperator_currentFalsePreviousFalse_returnsOr() {
        // Tests line 276: both FALSE case in OR mode
        BranchState.OrMode orMode = new BranchState.OrMode(Optional.of(false), false);

        Operator combineOp = orMode.determineCombineOperator(false, null);

        assertThat(combineOp).isEqualTo(OR);
    }

    @Test
    void orMode_determineCombineOperator_currentFalsePreviousTrue_returnsOr() {
        BranchState.OrMode orMode = new BranchState.OrMode(Optional.of(true), false);

        Operator combineOp = orMode.determineCombineOperator(false, null);

        assertThat(combineOp).isEqualTo(OR);
    }

    @Test
    void orMode_determineCombineOperator_currentTruePreviousFalse_returnsOr() {
        BranchState.OrMode orMode = new BranchState.OrMode(Optional.of(false), false);

        Operator combineOp = orMode.determineCombineOperator(true, null);

        assertThat(combineOp).isEqualTo(OR);
    }

    // ==================== AfterCombination Tests ====================

    @Test
    void initial_afterCombination_returnsSelf() {
        BranchState.Initial initial = new BranchState.Initial();

        BranchState result = initial.afterCombination(true, Optional.empty(), null);

        assertThat(result).isSameAs(initial);
    }

    @Test
    void andMode_afterCombination_withNullOperator_returnsSelf() {
        BranchState.AndMode andMode = new BranchState.AndMode(Optional.of(false), false);

        BranchState result = andMode.afterCombination(true, Optional.of(false), null);

        assertThat(result).isSameAs(andMode);
    }

    @Test
    void andMode_afterCombination_completingAndGroup_transitionsToOrMode() {
        // Tests lines 142-152: transition to OR mode when completing AND group
        BranchState.AndMode andMode = new BranchState.AndMode(Optional.of(false), false);

        BranchState result = andMode.afterCombination(true, Optional.of(false), AND);

        assertThat(result).isInstanceOf(BranchState.OrMode.class);
    }

    @Test
    void andMode_afterCombination_notCompletingGroup_returnsSelf() {
        BranchState.AndMode andMode = new BranchState.AndMode(Optional.of(false), false);

        BranchState result = andMode.afterCombination(false, Optional.of(false), AND);

        assertThat(result).isSameAs(andMode);
    }

    @Test
    void andMode_afterCombination_currentFalsePreviousTrue_returnsSelf() {
        BranchState.AndMode andMode = new BranchState.AndMode(Optional.of(true), false);

        BranchState result = andMode.afterCombination(false, Optional.of(true), AND);

        assertThat(result).isSameAs(andMode);
    }

    @Test
    void andMode_afterCombination_usedOrOperator_returnsSelf() {
        // When we used OR to combine (not AND), we should NOT transition
        BranchState.AndMode andMode = new BranchState.AndMode(Optional.of(false), false);

        BranchState result = andMode.afterCombination(true, Optional.of(false), OR);

        assertThat(result).isSameAs(andMode);
    }

    @Test
    void orMode_afterCombination_returnsSelf() {
        BranchState.OrMode orMode = new BranchState.OrMode(Optional.of(true), false);

        BranchState result = orMode.afterCombination(true, Optional.of(true), OR);

        assertThat(result).isSameAs(orMode);
    }

    // ==================== OrMode.determineCombineOperator Edge Cases ====================

    @Test
    void orMode_determineCombineOperator_emptyHistory_returnsOr() {
        // Test line 262: lastJumpTarget.isEmpty() returns OR
        BranchState.OrMode orMode = new BranchState.OrMode(Optional.empty(), false);

        Operator result = orMode.determineCombineOperator(true, null);

        assertThat(result).isEqualTo(OR);
    }

    @Test
    void orMode_determineCombineOperator_jumpTrueWithOrStackTop_returnsAnd() {
        // Test line 270: jumpTarget && stackTop is BinaryOp with OR -> returns AND
        BranchState.OrMode orMode = new BranchState.OrMode(Optional.of(true), false);
        LambdaExpression.BinaryOp orExpression = LambdaExpression.BinaryOp.or(
                new LambdaExpression.Constant(true, boolean.class),
                new LambdaExpression.Constant(false, boolean.class)
        );

        Operator result = orMode.determineCombineOperator(true, orExpression);

        assertThat(result).isEqualTo(AND);
    }

    @Test
    void orMode_determineCombineOperator_jumpTrueWithAndStackTop_returnsOr() {
        // Test line 270 false branch: stackTop is AND, not OR
        BranchState.OrMode orMode = new BranchState.OrMode(Optional.of(true), false);
        LambdaExpression.BinaryOp andExpression = LambdaExpression.BinaryOp.and(
                new LambdaExpression.Constant(true, boolean.class),
                new LambdaExpression.Constant(false, boolean.class)
        );

        Operator result = orMode.determineCombineOperator(true, andExpression);

        assertThat(result).isEqualTo(OR);
    }

    @Test
    void orMode_determineCombineOperator_jumpFalseWithOrStackTop_returnsOr() {
        // Test line 270 false branch: jumpTarget is false
        BranchState.OrMode orMode = new BranchState.OrMode(Optional.of(true), false);
        LambdaExpression.BinaryOp orExpression = LambdaExpression.BinaryOp.or(
                new LambdaExpression.Constant(true, boolean.class),
                new LambdaExpression.Constant(false, boolean.class)
        );

        Operator result = orMode.determineCombineOperator(false, orExpression);

        assertThat(result).isEqualTo(OR);
    }

    @Test
    void orMode_determineCombineOperator_jumpTrueWithNullStackTop_returnsOr() {
        // Test line 270 false branch: stackTop is null
        BranchState.OrMode orMode = new BranchState.OrMode(Optional.of(true), false);

        Operator result = orMode.determineCombineOperator(true, null);

        assertThat(result).isEqualTo(OR);
    }

    @Test
    void orMode_determineCombineOperator_jumpTrueWithConstantStackTop_returnsOr() {
        // Test line 270 false branch: stackTop is not BinaryOp
        BranchState.OrMode orMode = new BranchState.OrMode(Optional.of(true), false);
        LambdaExpression constant = new LambdaExpression.Constant(true, boolean.class);

        Operator result = orMode.determineCombineOperator(true, constant);

        assertThat(result).isEqualTo(OR);
    }

    @Test
    void orMode_determineCombineOperator_bothFalse_returnsOr() {
        // Test line 276: !jumpTarget && !lastJumpTarget.get()
        BranchState.OrMode orMode = new BranchState.OrMode(Optional.of(false), false);

        Operator result = orMode.determineCombineOperator(false, null);

        assertThat(result).isEqualTo(OR);
    }

    @Test
    void orMode_determineCombineOperator_jumpFalsePreviousTrue_returnsOr() {
        // Test line 276 false branch: previous was TRUE
        BranchState.OrMode orMode = new BranchState.OrMode(Optional.of(true), false);

        Operator result = orMode.determineCombineOperator(false, null);

        assertThat(result).isEqualTo(OR);
    }

    @Test
    void orMode_determineCombineOperator_jumpTruePreviousFalse_returnsOr() {
        // Test line 276 false branch: current jump is TRUE
        BranchState.OrMode orMode = new BranchState.OrMode(Optional.of(false), false);

        Operator result = orMode.determineCombineOperator(true, null);

        assertThat(result).isEqualTo(OR);
    }

    // ==================== AndMode.determineOperatorForCompletingAndGroup Tests ====================
    // (Lines 200-217: stackHasOr, prevWasBooleanCheck, stackHasAnd branches)

    @Test
    void andMode_determineCombineOperator_completingAndGroupWithOrStack_returnsAnd() {
        // Test line 204: stackHasOr is true - should return AND
        BranchState.AndMode andMode = new BranchState.AndMode(Optional.of(false), false);
        LambdaExpression.BinaryOp orExpression = LambdaExpression.BinaryOp.or(
                new LambdaExpression.Constant(true, boolean.class),
                new LambdaExpression.Constant(false, boolean.class)
        );

        // jumpTarget=true triggers determineOperatorForCompletingAndGroup
        Operator result = andMode.determineCombineOperator(true, orExpression);

        assertThat(result).isEqualTo(AND);
    }

    @Test
    void andMode_determineCombineOperator_completingAndGroupWithNestedOrInLeft_returnsAnd() {
        // Test containsOrOperator recursive case: OR nested in left subtree
        BranchState.AndMode andMode = new BranchState.AndMode(Optional.of(false), false);
        LambdaExpression.BinaryOp nestedOr = LambdaExpression.BinaryOp.or(
                new LambdaExpression.Constant(1, int.class),
                new LambdaExpression.Constant(2, int.class)
        );
        LambdaExpression.BinaryOp andWithNestedOr = LambdaExpression.BinaryOp.and(
                nestedOr, // OR in left subtree
                new LambdaExpression.Constant(3, int.class)
        );

        Operator result = andMode.determineCombineOperator(true, andWithNestedOr);

        assertThat(result).isEqualTo(AND);
    }

    @Test
    void andMode_determineCombineOperator_completingAndGroupWithNestedOrInRight_returnsAnd() {
        // Test containsOrOperator recursive case: OR nested in right subtree
        BranchState.AndMode andMode = new BranchState.AndMode(Optional.of(false), false);
        LambdaExpression.BinaryOp nestedOr = LambdaExpression.BinaryOp.or(
                new LambdaExpression.Constant(1, int.class),
                new LambdaExpression.Constant(2, int.class)
        );
        LambdaExpression.BinaryOp andWithNestedOr = LambdaExpression.BinaryOp.and(
                new LambdaExpression.Constant(3, int.class),
                nestedOr // OR in right subtree
        );

        Operator result = andMode.determineCombineOperator(true, andWithNestedOr);

        assertThat(result).isEqualTo(AND);
    }

    @Test
    void andMode_determineCombineOperator_completingAndGroupWithPrevBooleanCheck_returnsNull() {
        // Test line 209: prevWasBooleanCheck is true - should return null
        BranchState.AndMode andMode = new BranchState.AndMode(Optional.of(false), true); // prevWasBooleanCheck=true

        Operator result = andMode.determineCombineOperator(true, null);

        assertThat(result).isNull();
    }

    @Test
    void andMode_determineCombineOperator_completingAndGroupWithAndStack_returnsOr() {
        // Test line 214-216: stackHasAnd is true (no OR, no boolean check) - should return OR
        BranchState.AndMode andMode = new BranchState.AndMode(Optional.of(false), false);
        LambdaExpression.BinaryOp andExpression = LambdaExpression.BinaryOp.and(
                new LambdaExpression.Constant(true, boolean.class),
                new LambdaExpression.Constant(false, boolean.class)
        );

        Operator result = andMode.determineCombineOperator(true, andExpression);

        assertThat(result).isEqualTo(OR);
    }

    @Test
    void andMode_determineCombineOperator_completingAndGroupWithNullStack_returnsAnd() {
        // Test line 214-216: stackTop is null (not BinaryOp with AND) - should return AND
        BranchState.AndMode andMode = new BranchState.AndMode(Optional.of(false), false);

        Operator result = andMode.determineCombineOperator(true, null);

        assertThat(result).isEqualTo(AND);
    }

    @Test
    void andMode_determineCombineOperator_completingAndGroupWithConstantStack_returnsAnd() {
        // Test line 214-216: stackTop is not BinaryOp - should return AND
        BranchState.AndMode andMode = new BranchState.AndMode(Optional.of(false), false);
        LambdaExpression constant = new LambdaExpression.Constant(true, boolean.class);

        Operator result = andMode.determineCombineOperator(true, constant);

        assertThat(result).isEqualTo(AND);
    }

    @Test
    void andMode_determineCombineOperator_completingAndGroupWithComparisonStack_returnsAnd() {
        // Test line 214-216: stackTop is BinaryOp with comparison operator (not AND/OR)
        BranchState.AndMode andMode = new BranchState.AndMode(Optional.of(false), false);
        LambdaExpression.BinaryOp eqExpression = LambdaExpression.BinaryOp.eq(
                new LambdaExpression.Constant(1, int.class),
                new LambdaExpression.Constant(2, int.class)
        );

        Operator result = andMode.determineCombineOperator(true, eqExpression);

        assertThat(result).isEqualTo(AND);
    }

    // ==================== AndMode.afterCombination Edge Cases ====================

    @Test
    void andMode_afterCombination_emptyPreviousJumpTarget_returnsSelf() {
        // Test line 142: previousJumpTarget.isEmpty() - should stay in same mode
        BranchState.AndMode andMode = new BranchState.AndMode(Optional.of(false), false);

        BranchState result = andMode.afterCombination(true, Optional.empty(), AND);

        assertThat(result).isSameAs(andMode);
    }

    @Test
    void andMode_afterCombination_previousJumpTargetTrue_returnsSelf() {
        // Test line 142: previousJumpTarget.get() is true (not false) - should stay in same mode
        BranchState.AndMode andMode = new BranchState.AndMode(Optional.of(false), false);

        BranchState result = andMode.afterCombination(true, Optional.of(true), AND);

        assertThat(result).isSameAs(andMode);
    }

    @Test
    void andMode_afterCombination_currentJumpFalse_returnsSelf() {
        // Test line 149: currentJumpTarget is false - should stay in same mode
        BranchState.AndMode andMode = new BranchState.AndMode(Optional.of(false), false);

        BranchState result = andMode.afterCombination(false, Optional.of(false), AND);

        assertThat(result).isSameAs(andMode);
    }

    // ==================== containsOrOperator Edge Cases ====================

    @Test
    void andMode_determineCombineOperator_deeplyNestedOr_returnsAnd() {
        // Test containsOrOperator with deeply nested OR (3 levels)
        BranchState.AndMode andMode = new BranchState.AndMode(Optional.of(false), false);
        LambdaExpression.BinaryOp deepOr = LambdaExpression.BinaryOp.or(
                new LambdaExpression.Constant(1, int.class),
                new LambdaExpression.Constant(2, int.class)
        );
        LambdaExpression.BinaryOp level2 = LambdaExpression.BinaryOp.and(
                deepOr,
                new LambdaExpression.Constant(3, int.class)
        );
        LambdaExpression.BinaryOp level1 = LambdaExpression.BinaryOp.and(
                level2,
                new LambdaExpression.Constant(4, int.class)
        );

        Operator result = andMode.determineCombineOperator(true, level1);

        assertThat(result).isEqualTo(AND);
    }

    @Test
    void andMode_determineCombineOperator_noOrAnywhere_withNoAndStack_returnsAnd() {
        // Test containsOrOperator returning false with non-BinaryOp (FieldAccess)
        BranchState.AndMode andMode = new BranchState.AndMode(Optional.of(false), false);
        LambdaExpression fieldAccess = new LambdaExpression.FieldAccess("name", String.class);

        Operator result = andMode.determineCombineOperator(true, fieldAccess);

        assertThat(result).isEqualTo(AND);
    }

    // ==================== AndMode.determineCombineOperator Additional Edge Cases ====================

    @Test
    void andMode_determineCombineOperator_previousTrueCurrentFalse_returnsAnd() {
        // Test line 189: previous TRUE, current FALSE -> AND
        BranchState.AndMode andMode = new BranchState.AndMode(Optional.of(true), false);

        Operator result = andMode.determineCombineOperator(false, null);

        assertThat(result).isEqualTo(AND);
    }

    @Test
    void andMode_determineCombineOperator_bothTrueWithEmptyHistory_returnsAnd() {
        // Edge case: empty history should return AND (second comparison)
        BranchState.AndMode andMode = new BranchState.AndMode(Optional.empty(), false);

        Operator result = andMode.determineCombineOperator(true, null);

        assertThat(result).isEqualTo(AND);
    }
}
