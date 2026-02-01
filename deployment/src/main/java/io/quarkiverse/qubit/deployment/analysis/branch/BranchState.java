package io.quarkiverse.qubit.deployment.analysis.branch;

import java.util.Optional;

import io.quarkiverse.qubit.deployment.ast.LambdaExpression;
import io.quarkus.logging.Log;

import static io.quarkiverse.qubit.deployment.ast.LambdaExpression.BinaryOp.Operator;
import static io.quarkiverse.qubit.deployment.ast.LambdaExpression.BinaryOp.Operator.AND;
import static io.quarkiverse.qubit.deployment.ast.LambdaExpression.BinaryOp.Operator.OR;

/** Immutable state machine for AND/OR combination during branch analysis. */
public sealed interface BranchState permits BranchState.Initial, BranchState.AndMode, BranchState.OrMode {

    /**
     * Result of processing a branch instruction containing both the combine operator and new state.
     *
     * @param combineOperator operator to combine with previous expression (AND/OR), or null if shouldn't combine
     * @param newState the new state after processing this branch instruction
     */
    record BranchResult(Operator combineOperator, BranchState newState) {}

    /**
     * Updates state after combination. Handles mode transitions when completing expression groups.
     *
     * @param currentJumpTarget current instruction's jump target
     * @param previousJumpTarget previous instruction's jump target
     * @param combineOp operator used to combine (AND/OR), or null
     * @return new state after post-combination transition
     */
    BranchState afterCombination(boolean currentJumpTarget, Optional<Boolean> previousJumpTarget, Operator combineOp);

    /**
     * Processes a branch instruction, returning combine operator and new state atomically.
     *
     * <p>Default implementation delegates to testing API methods ({@code determineCombineOperator}
     * and {@code transition}). Implementations can override for custom behavior (e.g., {@link Initial}).
     *
     * @param jumpTarget true if jump targets true branch, false otherwise
     * @param isBooleanCheck true if boolean field check
     * @param stackTop top stack expression, or null
     * @return BranchResult with combine operator and new state
     */
    default BranchResult processBranch(boolean jumpTarget, boolean isBooleanCheck, LambdaExpression stackTop) {
        // All BranchState implementations also implement BranchStateTestingAPI
        BranchStateTestingAPI testingAPI = (BranchStateTestingAPI) this;
        Operator combineOp = testingAPI.determineCombineOperator(jumpTarget, stackTop);
        BranchState newState = testingAPI.transition(jumpTarget, isBooleanCheck);
        return new BranchResult(combineOp, newState);
    }

    /**
     * Returns true if this is the initial state (no jumps processed yet).
     */
    default boolean isInitial() {
        return this instanceof Initial;
    }

    /**
     * Returns the previous instruction's jump target, or empty if initial/first comparison.
     */
    default Optional<Boolean> getLastJumpTarget() {
        if (this instanceof AndMode(var lastJumpTarget, _)) {
            return lastJumpTarget;
        } else if (this instanceof OrMode(var lastJumpTarget, _)) {
            return lastJumpTarget;
        }
        return Optional.empty();
    }

/**
 * Package-private interface for testing-only methods.
 * These methods are used by tests to verify state machine logic in isolation.
 */
sealed interface BranchStateTestingAPI permits BranchState.Initial, BranchState.AndMode, BranchState.OrMode {

    /**
     * Transitions to new state based on jump target.
     * For testing purposes only. Production code should use {@link BranchState#processBranch}.
     */
    BranchState transition(boolean jumpTarget, boolean isBooleanCheck);

    /**
     * Determines the combine operator for the current branch.
     * For testing purposes only. Production code should use {@link BranchState#processBranch}.
     */
    Operator determineCombineOperator(boolean jumpTarget, LambdaExpression stackTop);
}

    /**
     * Initial state. First jump determines AND or OR mode.
     */
    record Initial() implements BranchState, BranchStateTestingAPI {

        @Override
        public BranchResult processBranch(boolean jumpTarget, boolean isBooleanCheck, LambdaExpression stackTop) {
            // First comparison doesn't combine, and first jump determines the mode
            // CRITICAL: Record the first jump target and boolean check flag for second comparison
            BranchState newState = jumpTarget ?
                    new OrMode(Optional.of(jumpTarget), isBooleanCheck) :
                    new AndMode(Optional.of(jumpTarget), isBooleanCheck);
            Log.tracef("BranchState Initial -> %s (jumpTarget=%b, isBooleanCheck=%b)",
                    newState.getClass().getSimpleName(), jumpTarget, isBooleanCheck);
            return new BranchResult(null, newState);
        }

        @Override
        public BranchState afterCombination(boolean currentJumpTarget, Optional<Boolean> previousJumpTarget, Operator combineOp) {
            // Initial state doesn't change after combination (no combination happens)
            return this;
        }

        @Override
        public BranchState transition(boolean jumpTarget, boolean isBooleanCheck) {
            return jumpTarget ?
                    new OrMode(Optional.empty(), false) :
                    new AndMode(Optional.empty(), false);
        }

        @Override
        public Operator determineCombineOperator(boolean jumpTarget, LambdaExpression stackTop) {
            return null; // First comparison, don't combine
        }
    }

    /**
     * AND mode: combines expressions with AND (jump-to-false pattern).
     */
    record AndMode(Optional<Boolean> lastJumpTarget, boolean prevWasBooleanCheck) implements BranchState, BranchStateTestingAPI {

        @Override
        public BranchState afterCombination(boolean currentJumpTarget, Optional<Boolean> previousJumpTarget, Operator combineOp) {
            // Post-combination transition logic for AND mode
            // Transitions to OR mode when completing a nested AND group that leads to OR alternatives
            // Does NOT transition when completing an OR group (even if we used AND to combine)

            // No transition if no combination happened
            if (combineOp == null) {
                return this;
            }

            boolean prevJumpFalse = previousJumpTarget.isPresent() && !previousJumpTarget.get();
            boolean usedAndOperator = combineOp == AND;

            // Only transition to OR mode if:
            // 1. Current jump is TRUE (completing a group)
            // 2. Previous jump was FALSE (was in AND chain)
            // 3. We combined with AND (not OR - if we used OR, we were already in an OR group!)
            if (currentJumpTarget && prevJumpFalse && usedAndOperator) {
                // Completed nested AND group, entering OR mode for next comparison
                Log.tracef("BranchState AndMode -> OrMode after combination (currentJump=%b, prevJump=%s, combineOp=%s)",
                        currentJumpTarget, previousJumpTarget, combineOp);
                return new OrMode(Optional.of(currentJumpTarget), prevWasBooleanCheck);
            }

            Log.tracef("BranchState AndMode stays (currentJump=%b, prevJump=%s, combineOp=%s)",
                    currentJumpTarget, previousJumpTarget, combineOp);
            return this; // No mode transition
        }

        @Override
        public BranchState transition(boolean jumpTarget, boolean isBooleanCheck) {
            // Transition from AND mode to OR mode when:
            // 1. Current jump is TRUE
            // 2. Previous jump was FALSE
            // 3. We just completed an AND group (jump pattern: FALSE then TRUE)
            if (jumpTarget && lastJumpTarget.isPresent() && !lastJumpTarget.get()) {
                // Completed nested AND group, enter OR mode for alternatives
                return new OrMode(Optional.of(jumpTarget), isBooleanCheck);
            }

            // Stay in AND mode, update history
            return new AndMode(Optional.of(jumpTarget), isBooleanCheck);
        }

        @Override
        public Operator determineCombineOperator(boolean jumpTarget, LambdaExpression stackTop) {
            if (lastJumpTarget.isEmpty()) {
                // Second comparison in lambda
                return AND;
            }

            // Both jumps to TRUE → OR (alternative conditions)
            if (jumpTarget && lastJumpTarget.get()) {
                return OR;
            }

            // Current TRUE, previous FALSE → completing nested AND group
            if (jumpTarget) {
                return determineOperatorForCompletingAndGroup(stackTop);
            }

            // Both FALSE or current FALSE, previous TRUE → AND
            return AND;
        }

        /**
         * Determines the combine operator when completing a nested AND group (jump to TRUE after FALSE).
         * Handles special cases based on previous boolean checks and stack operator presence.
         *
         * @param stackTop top stack expression to analyze
         * @return combine operator (AND, OR, or null if no combination needed)
         */
        private Operator determineOperatorForCompletingAndGroup(LambdaExpression stackTop) {
            boolean stackHasOr = containsOrOperator(stackTop);

            // If stack has OR, always combine with AND
            if (stackHasOr) {
                return AND;
            }

            // Stack doesn't have OR - handle based on previous comparison type
            if (prevWasBooleanCheck) {
                return null;  // Previous was boolean check, don't combine
            }

            // Previous wasn't boolean check - determine operator based on stack content
            boolean stackHasAnd = stackTop instanceof LambdaExpression.BinaryOp(_, var operator, _) &&
                                  operator == AND;
            return stackHasAnd ? OR : AND;
        }

        private boolean containsOrOperator(LambdaExpression expr) {
            if (expr instanceof LambdaExpression.BinaryOp(var left, var operator, var right)) {
                return operator == OR ||
                       containsOrOperator(left) ||
                       containsOrOperator(right);
            }
            return false;
        }
    }

    /**
     * OR mode: combines expressions with OR (jump-to-true pattern or after AND groups).
     */
    record OrMode(Optional<Boolean> lastJumpTarget, boolean prevWasBooleanCheck) implements BranchState, BranchStateTestingAPI {

        @Override
        public BranchState afterCombination(boolean currentJumpTarget, Optional<Boolean> previousJumpTarget, Operator combineOp) {
            // Post-combination transition logic for OR mode
            // No state transitions occur after combination in OR mode
            // Transitions from OR to AND happen before combination during processBranch/transition
            return this;
        }

        @Override
        public BranchState transition(boolean jumpTarget, boolean isBooleanCheck) {
            // Transition from OR mode back to AND mode when:
            // 1. Current jump is FALSE
            // 2. Previous jump was also FALSE
            // 3. Starting a new nested AND group
            if (!jumpTarget && lastJumpTarget.isPresent() && !lastJumpTarget.get()) {
                // Two FALSE jumps in a row → nested AND group
                Log.tracef("BranchState OrMode -> AndMode (jumpTarget=%b, lastJump=%s, isBooleanCheck=%b)",
                        jumpTarget, lastJumpTarget, isBooleanCheck);
                return new AndMode(Optional.of(jumpTarget), isBooleanCheck);
            }

            Log.tracef("BranchState OrMode stays (jumpTarget=%b, lastJump=%s, isBooleanCheck=%b)",
                    jumpTarget, lastJumpTarget, isBooleanCheck);
            // Stay in OR mode, update history
            return new OrMode(Optional.of(jumpTarget), isBooleanCheck);
        }

        @Override
        public Operator determineCombineOperator(boolean jumpTarget, LambdaExpression stackTop) {
            // In OR mode, typically combine with OR
            // EXCEPT when combining two OR groups with AND

            if (lastJumpTarget.isEmpty()) {
                // Second comparison
                return OR;
            }

            // Detect when combining two OR groups with AND
            // Pattern: (a OR b) AND (c OR ...)
            // When current jump is TRUE and stack has an OR expression, use AND to combine the groups
            if (jumpTarget && stackTop instanceof LambdaExpression.BinaryOp binOp && binOp.operator() == OR) {
                    // Stack has OR expression, jumping to TRUE → use AND
                    return AND;
            }

            // Both FALSE → transition to AND group is happening
            if (!jumpTarget && !lastJumpTarget.get()) {
                return OR; // Combine current before transitioning
            }

            // Default in OR mode: combine with OR
            return OR;
        }
    }
}
