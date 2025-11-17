package io.quarkus.qusaq.deployment.analysis.branch.handlers;

import io.quarkus.qusaq.deployment.LambdaExpression;
import io.quarkus.qusaq.deployment.analysis.BytecodeValidator;
import io.quarkus.qusaq.deployment.analysis.ControlFlowAnalyzer;
import io.quarkus.qusaq.deployment.analysis.PatternDetector;
import io.quarkus.qusaq.deployment.analysis.branch.BranchHandler;
import io.quarkus.qusaq.deployment.analysis.branch.BranchState;
import org.jboss.logging.Logger;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;

import java.util.Deque;
import java.util.Map;
import java.util.Optional;

import static io.quarkus.qusaq.deployment.LambdaExpression.BinaryOp.Operator;
import static io.quarkus.qusaq.deployment.LambdaExpression.BinaryOp.Operator.*;
import static io.quarkus.qusaq.deployment.analysis.ControlFlowAnalyzer.LabelClassification.INTERMEDIATE;
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
            log.tracef("IFLE/IFLT/IFGE/IFGT: Stack empty, skipping");
            return state;
        }

        PatternDetector.BranchPatternAnalysis patterns = PatternDetector.BranchPatternAnalysis.analyze(stack);

        LambdaExpression right;
        LambdaExpression left;

        if (patterns.isArithmeticComparisonPattern() || patterns.isDcmplPattern()) {
            // Arithmetic comparison: ISUB/LSUB or DCMPL/DCMPG → comparison
            right = BytecodeValidator.popSafe(stack, "IFLE/IFLT/IFGE/IFGT-ArithComp-right");
            left = BytecodeValidator.popSafe(stack, "IFLE/IFLT/IFGE/IFGT-ArithComp-left");
        } else if (patterns.isCompareToPattern()) {
            // CompareTo pattern: a.compareTo(b) → comparison
            LambdaExpression.MethodCall methodCall = (LambdaExpression.MethodCall) BytecodeValidator.popSafe(stack, "IFLE/IFLT/IFGE/IFGT-CompareTo");
            left = methodCall.target();
            right = methodCall.arguments().get(0);
        } else {
            // Plain comparison: value → comparison with 0
            left = BytecodeValidator.popSafe(stack, "IFLE/IFLT/IFGE/IFGT-plain");
            right = new LambdaExpression.Constant(0, int.class);
        }

        Boolean jumpTarget = labelToValue.get(jumpInsn.label);
        ControlFlowAnalyzer.LabelClassification jumpLabelClass = labelClassifications.get(jumpInsn.label);
        boolean willCombine = !stack.isEmpty() && stack.peek() instanceof LambdaExpression.BinaryOp;

        Operator op = determineComparisonOperator(jumpLabelClass, jumpTarget, willCombine, state, jumpInsn.getOpcode());
        LambdaExpression comparison = new LambdaExpression.BinaryOp(left, op, right);

        log.tracef("IFLE/IFLT/IFGE/IFGT: opcode=%d, jumpTarget=%s, operator=%s, comparison=%s",
                jumpInsn.getOpcode(), jumpTarget, op, comparison);

        // Get previous jump target BEFORE processing current branch (needed for afterCombination)
        Optional<Boolean> previousJumpTarget = state.getLastJumpTarget();

        // Process branch instruction atomically (unified operator determination + state transition)
        LambdaExpression stackTop = stack.isEmpty() ? null : stack.peek();
        BranchState.BranchResult result = state.processBranch(jumpTarget != null && jumpTarget, false, stackTop);
        Operator combineOp = result.combineOperator();
        BranchState newState = result.newState();

        if (combineOp != null && !stack.isEmpty() && stack.peek() instanceof LambdaExpression.BinaryOp) {
            // Combine with previous condition
            LambdaExpression previousCondition = BytecodeValidator.popSafe(stack, "IFLE/IFLT/IFGE/IFGT-Combine");
            LambdaExpression combined = new LambdaExpression.BinaryOp(
                    previousCondition, combineOp, comparison);

            // RESTRUCTURING: Fix precedence when combining ((a OR b) AND c) OR d
            // Should be: (a OR b) AND (c OR d) to maintain proper grouping
            // ONLY restructure if X is itself an OR expression
            if (combineOp == Operator.OR &&
                previousCondition instanceof LambdaExpression.BinaryOp prevBinOp &&
                prevBinOp.operator() == Operator.AND &&
                prevBinOp.left() instanceof LambdaExpression.BinaryOp xBinOp &&
                xBinOp.operator() == Operator.OR) {
                // Restructure: ((a OR b) AND c) OR d → (a OR b) AND (c OR d)
                LambdaExpression x = prevBinOp.left();  // (a OR b)
                LambdaExpression y = prevBinOp.right(); // c
                LambdaExpression z = comparison;         // d
                LambdaExpression yOrZ = new LambdaExpression.BinaryOp(y, Operator.OR, z);
                combined = new LambdaExpression.BinaryOp(x, Operator.AND, yOrZ);
                log.debugf("IFLE/IFLT/IFGE/IFGT: Restructured ((a OR b) AND c) OR d to (a OR b) AND (c OR d): %s", combined);
            }

            stack.push(combined);
            // CRITICAL: Apply post-combination state transition (shouldEnterOrModeAfterAndGroup logic)
            newState = newState.afterCombination(jumpTarget != null && jumpTarget, previousJumpTarget, combineOp);
            log.debugf("IFLE/IFLT/IFGE/IFGT: Combined with %s: %s", combineOp, combined);
        } else {
            // Push standalone
            stack.push(comparison);
            log.debugf("IFLE/IFLT/IFGE/IFGT: Pushed without combining: %s", comparison);
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

        boolean invert;
        // INTERMEDIATE→TRUE inversion only applies when we're already in AndMode (not Initial state)
        // Initial state + INTERMEDIATE→TRUE = start of OR group (e.g., "age < 30 || ...")
        // AndMode + INTERMEDIATE→TRUE = AND group failing, jumping to OR alternative
        if (jumpLabelClass == INTERMEDIATE && jumpTarget != null && jumpTarget && !willCombine) {
            if (state instanceof BranchState.Initial) {
                // Starting an OR group - do NOT invert
                invert = false;
            } else {
                // In AndMode, jumping to OR alternative - invert
                invert = true;
            }
        } else if (jumpLabelClass == INTERMEDIATE && jumpTarget != null && !jumpTarget && !willCombine) {
            // INTERMEDIATE→FALSE, not combining: OR alternative check after AND group
            invert = true;
        } else if (jumpTarget != null && jumpTarget) {
            invert = false;
        } else {
            invert = true;
        }

        return mapSingleOperandComparisonOp(opcode, invert);
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
            default -> throw new IllegalStateException("Unexpected single-operand opcode: " + opcode);
        };
    }
}
