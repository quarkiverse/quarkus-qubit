package io.quarkus.qusaq.deployment.analysis.branch;

import java.util.Optional;

import io.quarkus.qusaq.deployment.LambdaExpression;

import static io.quarkus.qusaq.deployment.LambdaExpression.BinaryOp.Operator;
import static io.quarkus.qusaq.deployment.LambdaExpression.BinaryOp.Operator.AND;
import static io.quarkus.qusaq.deployment.LambdaExpression.BinaryOp.Operator.OR;

/**
 * Immutable state machine for AND/OR combination during branch analysis.
 * Sealed interface with three states: {@link Initial}, {@link AndMode}, {@link OrMode}.
 */
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
     * @param jumpTarget true if jump targets true branch, false otherwise
     * @param isBooleanCheck true if boolean field check
     * @param stackTop top stack expression, or null
     * @return BranchResult with combine operator and new state
     */
    BranchResult processBranch(boolean jumpTarget, boolean isBooleanCheck, LambdaExpression stackTop);

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
        if (this instanceof AndMode andMode) {
            return andMode.lastJumpTarget();
        } else if (this instanceof OrMode orMode) {
            return orMode.lastJumpTarget();
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
                    new OrMode(jumpTarget, isBooleanCheck) :
                    new AndMode(jumpTarget, isBooleanCheck);
            return new BranchResult(null, newState);
        }

        @Override
        public BranchState afterCombination(boolean currentJumpTarget, Optional<Boolean> previousJumpTarget, Operator combineOp) {
            // Initial state doesn't change after combination (no combination happens)
            return this;
        }

        @Override
        public BranchState transition(boolean jumpTarget, boolean isBooleanCheck) {
            return jumpTarget ? new OrMode() : new AndMode();
        }

        @Override
        public Operator determineCombineOperator(boolean jumpTarget, LambdaExpression stackTop) {
            return null; // First comparison, don't combine
        }
    }

    /**
     * AND mode: combines expressions with AND (jump-to-false pattern).
     */
    record AndMode(Optional<Boolean> lastJumpTarget, Optional<Boolean> secondLastJumpTarget, boolean prevWasBooleanCheck) implements BranchState, BranchStateTestingAPI {

        /**
         * Creates new AND mode with no jump history.
         */
        public AndMode() {
            this(Optional.empty(), Optional.empty(), false);
        }

        /**
         * Creates AND mode with last jump target recorded.
         */
        public AndMode(boolean lastJumpTarget) {
            this(Optional.of(lastJumpTarget), Optional.empty(), false);
        }

        /**
         * Creates AND mode with last jump target and boolean check flag.
         */
        public AndMode(boolean lastJumpTarget, boolean prevWasBooleanCheck) {
            this(Optional.of(lastJumpTarget), Optional.empty(), prevWasBooleanCheck);
        }

        @Override
        public BranchResult processBranch(boolean jumpTarget, boolean isBooleanCheck, LambdaExpression stackTop) {
            // Call deprecated methods separately to ensure exact same logic
            Operator combineOp = determineCombineOperator(jumpTarget, stackTop);
            BranchState newState = transition(jumpTarget, isBooleanCheck);
            return new BranchResult(combineOp, newState);
        }

        @Override
        public BranchState afterCombination(boolean currentJumpTarget, Optional<Boolean> previousJumpTarget, Operator combineOp) {
            // Original logic: shouldEnterOrModeAfterAndGroup
            // This should only trigger when completing a nested AND group that should lead to OR alternatives
            // It should NOT trigger when completing an OR group (even if we used AND to add to it)

            // No transition if no combination happened
            if (combineOp == null) {
                return this;
            }

            boolean currentJumpTrue = currentJumpTarget;
            boolean prevJumpFalse = previousJumpTarget.isPresent() && !previousJumpTarget.get();
            boolean usedAndOperator = combineOp == AND;

            // Only transition to OR mode if:
            // 1. Current jump is TRUE (completing a group)
            // 2. Previous jump was FALSE (was in AND chain)
            // 3. We combined with AND (not OR - if we used OR, we were already in an OR group!)
            if (currentJumpTrue && prevJumpFalse && usedAndOperator) {
                // Completed nested AND group, entering OR mode for next comparison
                return new OrMode(currentJumpTarget, prevWasBooleanCheck);
            }

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
                return new OrMode(jumpTarget, isBooleanCheck);
            }

            // Transition from OR back to AND when:
            // Both current and previous jump are FALSE (starting new AND group)
            if (!jumpTarget && lastJumpTarget.isPresent() && !lastJumpTarget.get()) {
                // Stay in AND mode but update history
                return new AndMode(Optional.of(jumpTarget), lastJumpTarget, isBooleanCheck);
            }

            // Stay in AND mode, update history
            return new AndMode(Optional.of(jumpTarget), lastJumpTarget, isBooleanCheck);
        }

        @Override
        public Operator determineCombineOperator(boolean jumpTarget, LambdaExpression stackTop) {
            if (lastJumpTarget.isEmpty()) {
                // Second comparison in lambda
                return AND;
            }

            boolean prevJumpedToTrue = lastJumpTarget.get();

            // Both jumps to TRUE → OR (alternative conditions)
            if (jumpTarget && prevJumpedToTrue) {
                return OR;
            }

            // Current TRUE, previous FALSE → completing nested AND group
            if (jumpTarget) {
                boolean stackHasOr = hasOrOperator(stackTop);

                // If previous was a boolean check and stack doesn't have OR, don't combine
                if (prevWasBooleanCheck && !stackHasOr) {
                    return null;
                }

                // If previous wasn't a boolean check and stack doesn't have OR
                if (!prevWasBooleanCheck && !stackHasOr) {
                    boolean stackHasAnd = hasAndOperator(stackTop);
                    if (stackHasAnd) {
                        return OR;
                    }
                    return AND;
                }

                return AND;
            }

            // Both FALSE or current FALSE, previous TRUE → AND
            return AND;
        }

        private boolean hasOrOperator(LambdaExpression expr) {
            return expr instanceof LambdaExpression.BinaryOp topOp &&
                   containsOrOperator(topOp);
        }

        private boolean hasAndOperator(LambdaExpression expr) {
            return expr instanceof LambdaExpression.BinaryOp topOp &&
                   topOp.operator() == AND;
        }

        private boolean containsOrOperator(LambdaExpression expr) {
            if (expr instanceof LambdaExpression.BinaryOp binOp) {
                if (binOp.operator() == OR) {
                    return true;
                }
                return containsOrOperator(binOp.left()) || containsOrOperator(binOp.right());
            }
            return false;
        }
    }

    /**
     * OR mode: combines expressions with OR (jump-to-true pattern or after AND groups).
     */
    record OrMode(Optional<Boolean> lastJumpTarget, Optional<Boolean> secondLastJumpTarget, boolean prevWasBooleanCheck) implements BranchState, BranchStateTestingAPI {

        /**
         * Creates new OR mode with no jump history.
         */
        public OrMode() {
            this(Optional.empty(), Optional.empty(), false);
        }

        /**
         * Creates OR mode with last jump target recorded.
         */
        public OrMode(boolean lastJumpTarget) {
            this(Optional.of(lastJumpTarget), Optional.empty(), false);
        }

        /**
         * Creates OR mode with last jump target and boolean check flag.
         */
        public OrMode(boolean lastJumpTarget, boolean prevWasBooleanCheck) {
            this(Optional.of(lastJumpTarget), Optional.empty(), prevWasBooleanCheck);
        }

        @Override
        public BranchResult processBranch(boolean jumpTarget, boolean isBooleanCheck, LambdaExpression stackTop) {
            // Call deprecated methods separately to ensure exact same logic
            Operator combineOp = determineCombineOperator(jumpTarget, stackTop);
            BranchState newState = transition(jumpTarget, isBooleanCheck);
            return new BranchResult(combineOp, newState);
        }

        @Override
        public BranchState afterCombination(boolean currentJumpTarget, Optional<Boolean> previousJumpTarget, Operator combineOp) {
            // In OR mode, no post-combination transitions occur in the original code
            // The transition from OR to AND happens BEFORE combination (in shouldResetOrMode)
            // which is handled by the processBranch/transition methods
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
                return new AndMode(Optional.of(jumpTarget), lastJumpTarget, isBooleanCheck);
            }

            // Stay in OR mode, update history
            return new OrMode(Optional.of(jumpTarget), lastJumpTarget, isBooleanCheck);
        }

        @Override
        public Operator determineCombineOperator(boolean jumpTarget, LambdaExpression stackTop) {
            // In OR mode, typically combine with OR
            // EXCEPT when combining two OR groups with AND

            if (lastJumpTarget.isEmpty()) {
                // Second comparison
                return OR;
            }

            boolean prevJumpedToTrue = lastJumpTarget.get();

            // CRITICAL FIX: Detect when combining two OR groups with AND
            // Pattern: (a OR b) AND (c OR ...)
            // When we see:
            // - Current jump is TRUE (entering/completing OR group)
            // - Stack has an OR expression (first OR group complete)
            // We need to use AND to combine them
            // BUT we must be careful not to use AND too early (see restructuring below)
            if (jumpTarget && stackTop instanceof LambdaExpression.BinaryOp binOp && binOp.operator() == OR) {
                    // Stack has OR expression, jumping to TRUE → use AND
                    return AND;
            }

            // Both FALSE → transition to AND group is happening
            if (!jumpTarget && !prevJumpedToTrue) {
                return OR; // Combine current before transitioning
            }

            // Default in OR mode: combine with OR
            return OR;
        }
    }
}
