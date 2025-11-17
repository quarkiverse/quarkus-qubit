package io.quarkus.qusaq.deployment.analysis.branch.handlers;

import io.quarkus.qusaq.deployment.LambdaExpression;
import io.quarkus.qusaq.deployment.analysis.BytecodeValidator;
import io.quarkus.qusaq.deployment.analysis.ControlFlowAnalyzer;
import io.quarkus.qusaq.deployment.analysis.branch.BranchHandler;
import io.quarkus.qusaq.deployment.analysis.branch.BranchState;
import org.jboss.logging.Logger;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;

import java.util.Deque;
import java.util.Map;
import java.util.Optional;

import static io.quarkus.qusaq.deployment.LambdaExpression.BinaryOp.Operator;
import static io.quarkus.qusaq.deployment.LambdaExpression.BinaryOp.Operator.EQ;
import static io.quarkus.qusaq.deployment.LambdaExpression.BinaryOp.Operator.NE;
import static org.objectweb.asm.Opcodes.IFNONNULL;
import static org.objectweb.asm.Opcodes.IFNULL;

/**
 * Handles null reference check instructions (IFNULL, IFNONNULL).
 *
 * <p>These instructions check if a reference on the stack is null:
 * <ul>
 *   <li>IFNULL - if reference == null, jump</li>
 *   <li>IFNONNULL - if reference != null, jump</li>
 * </ul>
 */
public class NullCheckHandler implements BranchHandler {

    private static final Logger log = Logger.getLogger(NullCheckHandler.class);

    @Override
    public boolean canHandle(JumpInsnNode jumpInsn) {
        int opcode = jumpInsn.getOpcode();
        return opcode == IFNULL || opcode == IFNONNULL;
    }

    @Override
    public BranchState handle(
            Deque<LambdaExpression> stack,
            JumpInsnNode jumpInsn,
            Map<LabelNode, Boolean> labelToValue,
            Map<LabelNode, ControlFlowAnalyzer.LabelClassification> labelClassifications,
            BranchState state) {

        if (stack.isEmpty()) {
            log.tracef("IFNULL/IFNONNULL: Stack empty, skipping");
            return state;
        }

        LambdaExpression fieldAccess = BytecodeValidator.popSafe(stack, "IFNULL/IFNONNULL");
        LambdaExpression nullLiteral = new LambdaExpression.NullLiteral(Object.class);

        // Determine the null check operator based on the opcode
        // IFNULL uses NE, IFNONNULL uses EQ (counterintuitive but matches original behavior)
        Operator operator = (jumpInsn.getOpcode() == IFNULL) ? NE : EQ;

        LambdaExpression comparison = new LambdaExpression.BinaryOp(
            fieldAccess,
            operator,
            nullLiteral
        );

        Boolean jumpTarget = labelToValue.get(jumpInsn.label);

        log.tracef("IFNULL/IFNONNULL: opcode=%d, jumpTarget=%s, operator=%s, comparison=%s",
                jumpInsn.getOpcode(), jumpTarget, operator, comparison);

        // Get previous jump target BEFORE processing current branch (needed for afterCombination)
        Optional<Boolean> previousJumpTarget = state.getLastJumpTarget();

        // Process branch instruction atomically (unified operator determination + state transition)
        LambdaExpression stackTop = stack.isEmpty() ? null : stack.peek();
        BranchState.BranchResult result = state.processBranch(jumpTarget != null && jumpTarget, false, stackTop);
        Operator combineOp = result.combineOperator();
        BranchState newState = result.newState();

        if (combineOp != null && !stack.isEmpty() && stack.peek() instanceof LambdaExpression.BinaryOp) {
            // Combine with previous condition
            LambdaExpression previousCondition = BytecodeValidator.popSafe(stack, "IFNULL/IFNONNULL-Combine");
            LambdaExpression combined = new LambdaExpression.BinaryOp(
                    previousCondition, combineOp, comparison);
            stack.push(combined);
            // CRITICAL: Apply post-combination state transition (shouldEnterOrModeAfterAndGroup logic)
            newState = newState.afterCombination(jumpTarget != null && jumpTarget, previousJumpTarget, combineOp);
            log.debugf("IFNULL/IFNONNULL: Combined with %s: %s", combineOp, combined);
        } else {
            // Push standalone
            stack.push(comparison);
            log.debugf("IFNULL/IFNONNULL: Pushed without combining: %s", comparison);
        }

        // Return state after post-combination transition
        return newState;
    }
}
