package io.quarkiverse.qubit.deployment.analysis.branch;

import io.quarkiverse.qubit.deployment.ast.LambdaExpression;
import io.quarkiverse.qubit.deployment.analysis.ControlFlowAnalyzer;
import io.quarkiverse.qubit.deployment.common.OpcodeNames;
import io.quarkus.logging.Log;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;

import java.util.Deque;
import java.util.List;
import java.util.Map;

/**
 * Coordinates branch instruction handling using the Strategy pattern.
 * Delegates to specialized handlers and manages {@link BranchState} lifecycle.
 */
public class BranchCoordinator {

    private final List<BranchHandler> handlers;
    private BranchState state;
    private LabelNode lastJumpLabel;
    private ControlFlowAnalyzer.LabelClassification lastJumpLabelClass;

    public BranchCoordinator() {
        this.handlers = List.of(
            new IfEqualsZeroInstructionHandler(),
            new IfNotEqualsZeroInstructionHandler(),
            new TwoOperandComparisonHandler(),
            new SingleOperandComparisonHandler(),
            new NullCheckHandler()
        );
        this.state = new BranchState.Initial();
        this.lastJumpLabel = null;
        this.lastJumpLabelClass = null;
        Log.tracef("BranchCoordinator initialized with %d handlers", handlers.size());
    }

    /** Processes a branch instruction by delegating to the appropriate handler. */
    public void processBranchInstruction(
            Deque<LambdaExpression> stack,
            JumpInsnNode jumpInsn,
            Map<LabelNode, Boolean> labelToValue,
            Map<LabelNode, ControlFlowAnalyzer.LabelClassification> labelClassifications) {

        for (BranchHandler handler : handlers) {
            if (handler.canHandle(jumpInsn)) {
                LabelNode currentLabel = jumpInsn.label;
                ControlFlowAnalyzer.LabelClassification currentLabelClass = labelClassifications.get(currentLabel);
                boolean sameLabel = lastJumpLabel != null && lastJumpLabel == currentLabel;

                // Completing AND group: INTERMEDIATE → TRUE_SINK (e.g., (A && B) || C)
                boolean completingAndGroupFromIntermediate =
                        lastJumpLabelClass == ControlFlowAnalyzer.LabelClassification.INTERMEDIATE &&
                        currentLabelClass == ControlFlowAnalyzer.LabelClassification.TRUE_SINK;

                // Starting new group: FALSE_SINK → TRUE_SINK (e.g., (A || B) && (C || D))
                boolean startingNewGroupAfterAnd =
                        lastJumpLabelClass == ControlFlowAnalyzer.LabelClassification.FALSE_SINK &&
                        currentLabelClass == ControlFlowAnalyzer.LabelClassification.TRUE_SINK;

                Log.tracef("Processing %s with %s (state: %s, sameLabel: %s, completingAndGroup: %s, startingNewGroupAfterAnd: %s)",
                        OpcodeNames.get(jumpInsn.getOpcode()),
                        handler.getName(),
                        state.getClass().getSimpleName(),
                        sameLabel,
                        completingAndGroupFromIntermediate,
                        startingNewGroupAfterAnd);

                // Delegate to handler and receive new immutable state
                BranchState newState = handler.handle(stack, jumpInsn, labelToValue, labelClassifications, state,
                        sameLabel, completingAndGroupFromIntermediate, startingNewGroupAfterAnd);

                if (newState != state) {
                    Log.tracef("State transition: %s -> %s",
                            state.getClass().getSimpleName(),
                            newState.getClass().getSimpleName());
                    state = newState;
                }

                // Track current label and classification for next instruction
                lastJumpLabel = currentLabel;
                lastJumpLabelClass = currentLabelClass;

                return;
            }
        }

        Log.warnf("No handler found for branch instruction opcode: %d (%s)",
                jumpInsn.getOpcode(), OpcodeNames.get(jumpInsn.getOpcode()));
    }

    /** Resets to initial state for new lambda analysis. */
    public void reset() {
        this.state = new BranchState.Initial();
        this.lastJumpLabel = null;
        Log.tracef("BranchCoordinator reset to Initial state");
    }

    /** Returns the current branch state (for testing/debugging). */
    public BranchState getCurrentState() {
        return state;
    }
}
