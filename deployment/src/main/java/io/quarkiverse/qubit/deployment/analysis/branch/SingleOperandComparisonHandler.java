package io.quarkiverse.qubit.deployment.analysis.branch;

import io.quarkiverse.qubit.deployment.ast.LambdaExpression;
import io.quarkiverse.qubit.deployment.common.BytecodeAnalysisException;
import io.quarkiverse.qubit.deployment.common.BytecodeValidator;
import io.quarkiverse.qubit.deployment.analysis.ControlFlowAnalyzer;
import io.quarkiverse.qubit.deployment.common.PatternDetector;
import org.jboss.logging.Logger;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;

import java.util.Deque;
import java.util.Map;
import java.util.Optional;

import static io.quarkiverse.qubit.deployment.ast.LambdaExpression.BinaryOp.Operator;
import static io.quarkiverse.qubit.deployment.ast.LambdaExpression.BinaryOp.Operator.GE;
import static io.quarkiverse.qubit.deployment.ast.LambdaExpression.BinaryOp.Operator.GT;
import static io.quarkiverse.qubit.deployment.ast.LambdaExpression.BinaryOp.Operator.LE;
import static io.quarkiverse.qubit.deployment.ast.LambdaExpression.BinaryOp.Operator.LT;
import static io.quarkiverse.qubit.deployment.analysis.ControlFlowAnalyzer.LabelClassification.INTERMEDIATE;
import static java.lang.Boolean.FALSE;
import static java.lang.Boolean.TRUE;
import static org.objectweb.asm.Opcodes.*;

/**
 * Handles single-operand comparison instructions (IFLE, IFLT, IFGE, IFGT).
 *
 * <p>These instructions compare a single value on the stack against zero:
 * <ul>
 *   <li>IFLE - if value <= 0, jump</li>
 *   <li>IFLT - if value < 0, jump</li>
 *   <li>IFGE - if value >= 0, jump</li>
 *   <li>IFGT - if value > 0, jump</li>
 * </ul>
 *
 * <p>Handles special patterns:
 * <ul>
 *   <li>Arithmetic comparison pattern (ISUB/LSUB followed by comparison)</li>
 *   <li>Double comparison pattern (DCMPL/DCMPG followed by comparison)</li>
 *   <li>CompareTo pattern (compareTo() call followed by comparison)</li>
 *   <li>Plain comparison (value compared to 0)</li>
 * </ul>
 */
public class SingleOperandComparisonHandler implements BranchHandler {

    private static final Logger log = Logger.getLogger(SingleOperandComparisonHandler.class);
    private static final String INSTRUCTION_NAME = "IFLE/IFLT/IFGE/IFGT";

    @Override
    public boolean canHandle(JumpInsnNode jumpInsn) {
        int opcode = jumpInsn.getOpcode();
        return opcode == IFLE || opcode == IFLT || opcode == IFGE || opcode == IFGT;
    }

    @Override
    public BranchState handle(
            Deque<LambdaExpression> stack,
            JumpInsnNode jumpInsn,
            Map<LabelNode, Boolean> labelToValue,
            Map<LabelNode, ControlFlowAnalyzer.LabelClassification> labelClassifications,
            BranchState state) {

        if (stack.isEmpty()) {
            log.tracef(INSTRUCTION_NAME + ": Stack empty, skipping");
            return state;
        }

        PatternDetector.BranchPatternAnalysis patterns = PatternDetector.BranchPatternAnalysis.analyze(stack);

        LambdaExpression right;
        LambdaExpression left;

        switch (patterns.pattern()) {
            case NUMERIC_COMPARISON -> {
                // Numeric comparison: ISUB/LSUB or DCMPL/DCMPG → comparison
                right = BytecodeValidator.popSafe(stack, INSTRUCTION_NAME + "-NumericComp-right");
                left = BytecodeValidator.popSafe(stack, INSTRUCTION_NAME + "-NumericComp-left");
            }
            case COMPARE_TO -> {
                // CompareTo pattern: a.compareTo(b) → comparison
                LambdaExpression.MethodCall methodCall = (LambdaExpression.MethodCall) BytecodeValidator.popSafe(stack, INSTRUCTION_NAME + "-CompareTo");
                left = methodCall.target();
                right = methodCall.arguments().get(0);
            }
            default -> {
                // Plain comparison: value → comparison with 0
                // Handles ARITHMETIC and OTHER patterns
                left = BytecodeValidator.popSafe(stack, INSTRUCTION_NAME + "-plain");
                right = LambdaExpression.Constant.ZERO_INT;
            }
        }

        Boolean jumpTarget = labelToValue.get(jumpInsn.label);
        ControlFlowAnalyzer.LabelClassification jumpLabelClass = labelClassifications.get(jumpInsn.label);
        boolean willCombine = !stack.isEmpty() && stack.peek() instanceof LambdaExpression.BinaryOp;

        Operator op = determineComparisonOperator(jumpLabelClass, jumpTarget, willCombine, state, jumpInsn.getOpcode());
        LambdaExpression comparison = new LambdaExpression.BinaryOp(left, op, right);

        log.tracef(INSTRUCTION_NAME + ": opcode=%d, jumpTarget=%s, operator=%s, comparison=%s",
                jumpInsn.getOpcode(), jumpTarget, op, comparison);

        // Get previous jump target BEFORE processing current branch (needed for afterCombination)
        Optional<Boolean> previousJumpTarget = state.getLastJumpTarget();

        // Process branch instruction atomically (unified operator determination + state transition)
        LambdaExpression stackTop = stack.isEmpty() ? null : stack.peek();
        BranchState.BranchResult result = state.processBranch(TRUE.equals(jumpTarget), false, stackTop);
        Operator combineOp = result.combineOperator();
        BranchState newState = result.newState();

        if (combineOp != null && !stack.isEmpty() && stack.peek() instanceof LambdaExpression.BinaryOp) {
            // Combine with previous condition
            LambdaExpression previousCondition = BytecodeValidator.popSafe(stack, INSTRUCTION_NAME + "-Combine");
            LambdaExpression combined = combineAndRestructureIfNeeded(combineOp, previousCondition, comparison);
            stack.push(combined);
            // CRITICAL: Apply post-combination state transition (shouldEnterOrModeAfterAndGroup logic)
            newState = newState.afterCombination(TRUE.equals(jumpTarget), previousJumpTarget, combineOp);
            log.debugf(INSTRUCTION_NAME + ": Combined with %s: %s", combineOp, combined);
        } else {
            // Push standalone
            stack.push(comparison);
            log.debugf(INSTRUCTION_NAME + ": Pushed without combining: %s", comparison);
        }

        // Return state after post-combination transition
        return newState;
    }

    /**
     * Determines comparison operator based on jump target and context.
     */
    private Operator determineComparisonOperator(
            ControlFlowAnalyzer.LabelClassification jumpLabelClass,
            Boolean jumpTarget,
            boolean willCombine,
            BranchState state,
            int opcode) {

        // INTERMEDIATE→TRUE inversion only applies when we're already in AndMode (not Initial state)
        // Initial state + INTERMEDIATE→TRUE = start of OR group (e.g., "age < 30 || ...")
        // AndMode + INTERMEDIATE→TRUE = AND group failing, jumping to OR alternative
        if (jumpLabelClass == INTERMEDIATE && TRUE.equals(jumpTarget) && !willCombine) {
            // Invert only when NOT in Initial state (i.e., in AndMode)
            return mapSingleOperandComparisonOp(opcode, !(state instanceof BranchState.Initial));
        }

        // INTERMEDIATE→FALSE, not combining: OR alternative check after AND group
        if (jumpLabelClass == INTERMEDIATE && FALSE.equals(jumpTarget) && !willCombine) {
            return mapSingleOperandComparisonOp(opcode, true);
        }

        // Normal cases: jump to TRUE = no invert, otherwise invert
        return mapSingleOperandComparisonOp(opcode, FALSE.equals(jumpTarget));
    }

    /**
     * Maps single-operand comparison opcodes (IFLE, IFLT, IFGE, IFGT) to BinaryOp operators.
     */
    private Operator mapSingleOperandComparisonOp(int opcode, boolean invert) {
        return switch (opcode) {
            case IFLE -> invert ? GT : LE;
            case IFLT -> invert ? GE : LT;
            case IFGE -> invert ? LT : GE;
            case IFGT -> invert ? LE : GT;
            default -> throw BytecodeAnalysisException.unexpectedOpcode("single-operand comparison", opcode);
        };
    }
}
