package io.quarkiverse.qubit.deployment.analysis.branch;

import io.quarkiverse.qubit.deployment.ast.LambdaExpression;
import io.quarkiverse.qubit.deployment.common.BytecodeValidator;
import io.quarkiverse.qubit.deployment.common.OpcodeOperatorMapper;
import io.quarkiverse.qubit.deployment.analysis.ControlFlowAnalyzer;
import io.quarkiverse.qubit.deployment.common.PatternDetector;
import io.quarkus.logging.Log;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;

import java.util.Deque;
import java.util.Map;

import static io.quarkiverse.qubit.deployment.ast.LambdaExpression.BinaryOp.Operator;
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
            BranchState state,
            boolean sameLabel,
            boolean completingAndGroup,
            boolean startingNewOrGroup) {

        if (stack.isEmpty()) {
            Log.tracef(INSTRUCTION_NAME + ": Stack empty, skipping");
            return state;
        }

        // Debug: show label info at start
        Boolean jumpTarget = labelToValue.get(jumpInsn.label);
        ControlFlowAnalyzer.LabelClassification jumpLabelClass = labelClassifications.get(jumpInsn.label);
        Log.debugf(INSTRUCTION_NAME + ": ENTRY - sameLabel=%s, jumpTarget=%s, jumpLabelClass=%s, labelId=%d",
                sameLabel, jumpTarget, jumpLabelClass, System.identityHashCode(jumpInsn.label));

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

        Operator op = OpcodeOperatorMapper.determineSingleOperandOperator(jumpLabelClass, jumpTarget, jumpInsn.getOpcode());
        LambdaExpression comparison = new LambdaExpression.BinaryOp(left, op, right);

        Log.tracef(INSTRUCTION_NAME + ": opcode=%d, jumpTarget=%s, operator=%s, comparison=%s",
                jumpInsn.getOpcode(), jumpTarget, op, comparison);

        // Determine jumpToTrue using consolidated helper
        boolean jumpToTrue = determineJumpToTrue(jumpTarget, jumpLabelClass, jumpInsn.getOpcode(),
                OpcodeOperatorMapper::isSuccessJumpSingleOperand);

        // Delegate to shared branch processing and combination logic
        return processAndCombineBranch(stack, comparison, INSTRUCTION_NAME, state,
                jumpTarget, jumpLabelClass, sameLabel, completingAndGroup, startingNewOrGroup, jumpToTrue);
    }
}
