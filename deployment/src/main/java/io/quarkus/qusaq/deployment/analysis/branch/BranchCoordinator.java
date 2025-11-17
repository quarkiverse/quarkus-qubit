package io.quarkus.qusaq.deployment.analysis.branch;

import io.quarkus.qusaq.deployment.LambdaExpression;
import io.quarkus.qusaq.deployment.analysis.ControlFlowAnalyzer;
import io.quarkus.qusaq.deployment.analysis.branch.handlers.*;
import org.jboss.logging.Logger;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;

import java.util.Deque;
import java.util.List;
import java.util.Map;

/**
 * Coordinates branch instruction handling using the Strategy pattern.
 *
 * <p>This class manages the lifecycle of {@link BranchState} and delegates
 * branch instruction processing to specialized handlers:
 * <ul>
 *   <li>{@link IfEqualsZeroInstructionHandler} - IFEQ (if equals zero, jump)</li>
 *   <li>{@link IfNotEqualsZeroInstructionHandler} - IFNE (if not equals zero, jump)</li>
 *   <li>{@link TwoOperandComparisonHandler} - IF_ICMP*, IF_ACMP*</li>
 *   <li>{@link SingleOperandComparisonHandler} - IFLE, IFLT, IFGE, IFGT</li>
 *   <li>{@link NullCheckHandler} - IFNULL, IFNONNULL</li>
 * </ul>
 *
 * <p>Replaces the monolithic {@code BranchInstructionHandler} (691 LOC, complexity 50+)
 * with focused, testable components and immutable state management.
 *
 * @see BranchState
 * @see BranchHandler
 */
public class BranchCoordinator {

    private static final Logger log = Logger.getLogger(BranchCoordinator.class);

    private final List<BranchHandler> handlers;
    private BranchState state;

    /**
     * Creates a new coordinator with default handlers and initial state.
     */
    public BranchCoordinator() {
        this.handlers = List.of(
            new IfEqualsZeroInstructionHandler(),
            new IfNotEqualsZeroInstructionHandler(),
            new TwoOperandComparisonHandler(),
            new SingleOperandComparisonHandler(),
            new NullCheckHandler()
        );
        this.state = new BranchState.Initial();
        log.tracef("BranchCoordinator initialized with %d handlers", handlers.size());
    }

    /**
     * Processes a branch instruction by delegating to the appropriate handler.
     *
     * @param stack expression stack
     * @param jumpInsn jump instruction to process
     * @param labelToValue mapping of labels to boolean values
     * @param labelClassifications mapping of labels to classifications
     */
    public void processBranchInstruction(
            Deque<LambdaExpression> stack,
            JumpInsnNode jumpInsn,
            Map<LabelNode, Boolean> labelToValue,
            Map<LabelNode, ControlFlowAnalyzer.LabelClassification> labelClassifications) {

        for (BranchHandler handler : handlers) {
            if (handler.canHandle(jumpInsn)) {
                log.tracef("Processing %s with %s (state: %s)",
                        getOpcodeName(jumpInsn.getOpcode()),
                        handler.getName(),
                        state.getClass().getSimpleName());

                // Delegate to handler and receive new immutable state
                BranchState newState = handler.handle(stack, jumpInsn, labelToValue, labelClassifications, state);

                if (newState != state) {
                    log.tracef("State transition: %s -> %s",
                            state.getClass().getSimpleName(),
                            newState.getClass().getSimpleName());
                    state = newState;
                }

                return;
            }
        }

        log.warnf("No handler found for branch instruction opcode: %d (%s)",
                jumpInsn.getOpcode(), getOpcodeName(jumpInsn.getOpcode()));
    }

    /**
     * Resets the coordinator state to initial state.
     * Call this when starting analysis of a new lambda expression.
     */
    public void reset() {
        this.state = new BranchState.Initial();
        log.tracef("BranchCoordinator reset to Initial state");
    }

    /**
     * Returns the current branch state (for testing/debugging).
     *
     * @return current immutable state
     */
    public BranchState getCurrentState() {
        return state;
    }

    /**
     * Returns a human-readable name for a bytecode opcode.
     */
    private String getOpcodeName(int opcode) {
        return switch (opcode) {
            case 153 -> "IFEQ";
            case 154 -> "IFNE";
            case 155 -> "IFLT";
            case 156 -> "IFGE";
            case 157 -> "IFGT";
            case 158 -> "IFLE";
            case 159 -> "IF_ICMPEQ";
            case 160 -> "IF_ICMPNE";
            case 161 -> "IF_ICMPLT";
            case 162 -> "IF_ICMPGE";
            case 163 -> "IF_ICMPGT";
            case 164 -> "IF_ICMPLE";
            case 165 -> "IF_ACMPEQ";
            case 166 -> "IF_ACMPNE";
            case 198 -> "IFNULL";
            case 199 -> "IFNONNULL";
            default -> "UNKNOWN(" + opcode + ")";
        };
    }
}
