package io.quarkiverse.qubit.deployment.testutil.fixtures;

import java.util.Optional;

import io.quarkiverse.qubit.deployment.analysis.branch.BranchState;

/**
 * Pre-configured BranchState fixtures for testing.
 *
 * <p>
 * Usage:
 *
 * <pre>{@code
 * import static ...fixtures.BranchStateFixtures.*;
 *
 * BranchState state = initialState();
 * BranchState andMode = andModeJumpedFalse();
 * BranchState orMode = orModeJumpedTrue();
 * }</pre>
 */
public final class BranchStateFixtures {

    private BranchStateFixtures() {
    }

    /** Creates a fresh Initial state. */
    public static BranchState.Initial initialState() {
        return new BranchState.Initial();
    }

    /** AndMode with no previous jump target. */
    public static BranchState.AndMode andModeNoJump() {
        return new BranchState.AndMode(Optional.empty(), false);
    }

    /** AndMode after jumping to false path. */
    public static BranchState.AndMode andModeJumpedFalse() {
        return new BranchState.AndMode(Optional.of(false), false);
    }

    /** AndMode after jumping to true path. */
    public static BranchState.AndMode andModeJumpedTrue() {
        return new BranchState.AndMode(Optional.of(true), false);
    }

    /** AndMode that entered from OR group. */
    public static BranchState.AndMode andModeEnteredFromOrGroup() {
        return new BranchState.AndMode(Optional.of(false), true);
    }

    /** OrMode with no previous jump target. */
    public static BranchState.OrMode orModeNoJump() {
        return new BranchState.OrMode(Optional.empty(), false);
    }

    /** OrMode after jumping to true path. */
    public static BranchState.OrMode orModeJumpedTrue() {
        return new BranchState.OrMode(Optional.of(true), false);
    }

    /** OrMode after jumping to false path. */
    public static BranchState.OrMode orModeJumpedFalse() {
        return new BranchState.OrMode(Optional.of(false), false);
    }

    /** OrMode that entered from AND group. */
    public static BranchState.OrMode orModeEnteredFromAndGroup() {
        return new BranchState.OrMode(Optional.of(true), true);
    }

    /** Builder for custom BranchState configurations. */
    public static AndModeBuilder andMode() {
        return new AndModeBuilder();
    }

    /** Builder for custom OrMode configurations. */
    public static OrModeBuilder orMode() {
        return new OrModeBuilder();
    }

    public static class AndModeBuilder {
        private Optional<Boolean> lastJumpTarget = Optional.empty();
        private boolean enteredFromOrGroup = false;

        public AndModeBuilder jumpedTo(boolean target) {
            this.lastJumpTarget = Optional.of(target);
            return this;
        }

        public AndModeBuilder enteredFromOrGroup() {
            this.enteredFromOrGroup = true;
            return this;
        }

        public BranchState.AndMode build() {
            return new BranchState.AndMode(lastJumpTarget, enteredFromOrGroup);
        }
    }

    public static class OrModeBuilder {
        private Optional<Boolean> lastJumpTarget = Optional.empty();
        private boolean enteredFromAndGroup = false;

        public OrModeBuilder jumpedTo(boolean target) {
            this.lastJumpTarget = Optional.of(target);
            return this;
        }

        public OrModeBuilder enteredFromAndGroup() {
            this.enteredFromAndGroup = true;
            return this;
        }

        public BranchState.OrMode build() {
            return new BranchState.OrMode(lastJumpTarget, enteredFromAndGroup);
        }
    }
}
