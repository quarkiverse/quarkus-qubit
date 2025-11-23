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
import static io.quarkus.qusaq.deployment.LambdaExpression.BinaryOp.Operator.GE;
import static io.quarkus.qusaq.deployment.LambdaExpression.BinaryOp.Operator.GT;
import static io.quarkus.qusaq.deployment.LambdaExpression.BinaryOp.Operator.LE;
import static io.quarkus.qusaq.deployment.LambdaExpression.BinaryOp.Operator.LT;
import static io.quarkus.qusaq.deployment.LambdaExpression.BinaryOp.Operator.NE;
import static io.quarkus.qusaq.deployment.analysis.ControlFlowAnalyzer.LabelClassification.INTERMEDIATE;
import static java.lang.Boolean.FALSE;
import static java.lang.Boolean.TRUE;
import static org.objectweb.asm.Opcodes.*;

/**
 * Handles two-operand comparison instructions (IF_ICMP*, IF_ACMP*).
 *
 * <p>Supported opcodes:
 * <ul>
 *   <li>IF_ICMPGT, IF_ICMPGE, IF_ICMPLT, IF_ICMPLE - Integer comparisons</li>
 *   <li>IF_ICMPEQ, IF_ICMPNE - Integer equality</li>
 *   <li>IF_ACMPEQ, IF_ACMPNE - Reference equality</li>
 * </ul>
 */
public class TwoOperandComparisonHandler implements BranchHandler {

    private static final Logger log = Logger.getLogger(TwoOperandComparisonHandler.class);
    private static final String INSTRUCTION_NAME = "IF_ICMP*/IF_ACMP*";

    @Override
    public boolean canHandle(JumpInsnNode jumpInsn) {
        int opcode = jumpInsn.getOpcode();
        return opcode == IF_ICMPGT || opcode == IF_ICMPGE ||
               opcode == IF_ICMPLT || opcode == IF_ICMPLE ||
               opcode == IF_ICMPEQ || opcode == IF_ICMPNE ||
               opcode == IF_ACMPEQ || opcode == IF_ACMPNE;
    }

    @Override
    public BranchState handle(
            Deque<LambdaExpression> stack,
            JumpInsnNode jumpInsn,
            Map<LabelNode, Boolean> labelToValue,
            Map<LabelNode, ControlFlowAnalyzer.LabelClassification> labelClassifications,
            BranchState state) {

        BytecodeValidator.requireStackSize(stack, 2, INSTRUCTION_NAME);

        LambdaExpression right = BytecodeValidator.popSafe(stack, INSTRUCTION_NAME + "-right");
        LambdaExpression left = BytecodeValidator.popSafe(stack, INSTRUCTION_NAME + "-left");

        Boolean jumpTarget = labelToValue.get(jumpInsn.label);
        log.debugf(INSTRUCTION_NAME + ": Jump target label %s -> %s",
                System.identityHashCode(jumpInsn.label), jumpTarget);

        ControlFlowAnalyzer.LabelClassification jumpLabelClass = labelClassifications.get(jumpInsn.label);
        boolean willCombine = !stack.isEmpty() && stack.peek() instanceof LambdaExpression.BinaryOp;

        Operator op = determineComparisonOperator(jumpLabelClass, jumpTarget, willCombine, state, jumpInsn.getOpcode());
        LambdaExpression result = new LambdaExpression.BinaryOp(left, op, right);

        // Get previous jump target BEFORE processing current branch (needed for afterCombination)
        Optional<Boolean> previousJumpTarget = state.getLastJumpTarget();

        // Process branch instruction atomically (unified operator determination + state transition)
        LambdaExpression stackTop = stack.isEmpty() ? null : stack.peek();
        BranchState.BranchResult branchResult = state.processBranch(TRUE.equals(jumpTarget), false, stackTop);
        Operator combineOp = branchResult.combineOperator();
        BranchState newState = branchResult.newState();

        if (combineOp != null && !stack.isEmpty() && stack.peek() instanceof LambdaExpression.BinaryOp) {
            // Combine with previous condition
            LambdaExpression previousCondition = BytecodeValidator.popSafe(stack, INSTRUCTION_NAME + "-Combine");
            LambdaExpression combined = combineAndRestructureIfNeeded(combineOp, previousCondition, result);
            stack.push(combined);
            // CRITICAL: Apply post-combination state transition (shouldEnterOrModeAfterAndGroup logic)
            newState = newState.afterCombination(TRUE.equals(jumpTarget), previousJumpTarget, combineOp);
            log.debugf(INSTRUCTION_NAME + ": Combined with %s: %s", combineOp, combined);
        } else {
            // Push standalone
            stack.push(result);
            log.debugf(INSTRUCTION_NAME + ": Pushed without combining: %s", result);
        }

        // Return state after post-combination transition
        return newState;
    }

    /**
     * Determines comparison operator based on jump target and context.
     *
     * @param jumpLabelClass label classification (INTERMEDIATE, TRUE_BRANCH, FALSE_BRANCH)
     * @param jumpTarget whether jump targets true (true) or false (false) branch
     * @param willCombine whether this comparison will be combined with previous one
     * @param state current branch state
     * @param opcode the bytecode opcode (IF_ICMPGT, IF_ICMPGE, etc.)
     * @return the appropriate comparison operator
     */
    private Operator determineComparisonOperator(
            ControlFlowAnalyzer.LabelClassification jumpLabelClass,
            Boolean jumpTarget,
            boolean willCombine,
            BranchState state,
            int opcode) {

        boolean invert;
        // INTERMEDIATE→TRUE inversion only applies when we're already in AndMode (not Initial state)
        // Initial state + INTERMEDIATE→TRUE = start of OR group (e.g., "age < 30 || ...")
        // AndMode + INTERMEDIATE→TRUE = AND group failing, jumping to OR alternative
        if (jumpLabelClass == INTERMEDIATE && TRUE.equals(jumpTarget) && !willCombine) {
            if (state instanceof BranchState.Initial) {
                // Starting an OR group - do NOT invert
                invert = false;
            } else {
                // In AndMode, jumping to OR alternative - invert
                invert = true;
            }
        } else if (jumpLabelClass == INTERMEDIATE && FALSE.equals(jumpTarget) && !willCombine) {
            // INTERMEDIATE→FALSE, not combining: OR alternative check after AND group
            invert = true;
        } else if (TRUE.equals(jumpTarget)) {
            invert = false;
        } else {
            invert = true;
        }

        return mapTwoOperandComparisonOp(opcode, invert);
    }

    /**
     * Maps two-operand comparison opcodes (IF_ICMP*, IF_ACMP*) to BinaryOp operators.
     *
     * @param opcode the bytecode opcode
     * @param invert whether to invert the comparison operator
     * @return the mapped operator
     */
    private Operator mapTwoOperandComparisonOp(int opcode, boolean invert) {
        return switch (opcode) {
            case IF_ICMPGT -> invert ? LE : GT;
            case IF_ICMPGE -> invert ? LT : GE;
            case IF_ICMPLT -> invert ? GE : LT;
            case IF_ICMPLE -> invert ? GT : LE;
            case IF_ICMPEQ, IF_ACMPEQ -> invert ? NE : EQ;
            case IF_ICMPNE, IF_ACMPNE -> invert ? EQ : NE;
            default -> EQ;
        };
    }
}
