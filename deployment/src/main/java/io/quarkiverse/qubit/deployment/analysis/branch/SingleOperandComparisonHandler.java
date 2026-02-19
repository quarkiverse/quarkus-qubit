package io.quarkiverse.qubit.deployment.analysis.branch;

import static io.quarkiverse.qubit.deployment.ast.LambdaExpression.BinaryOp.Operator;
import static org.objectweb.asm.Opcodes.*;

import java.util.Deque;

import org.objectweb.asm.tree.JumpInsnNode;

import io.quarkiverse.qubit.deployment.ast.LambdaExpression;
import io.quarkiverse.qubit.deployment.common.BytecodeValidator;
import io.quarkiverse.qubit.deployment.common.OpcodeOperatorMapper;
import io.quarkiverse.qubit.deployment.common.PatternDetector;
import io.quarkus.logging.Log;

/**
 * Handles single-operand comparison instructions (IFLE, IFLT, IFGE, IFGT).
 *
 * <p>
 * These instructions compare a single value on the stack against zero:
 * <ul>
 * <li>IFLE - if value <= 0, jump</li>
 * <li>IFLT - if value < 0, jump</li>
 * <li>IFGE - if value >= 0, jump</li>
 * <li>IFGT - if value > 0, jump</li>
 * </ul>
 *
 * <p>
 * Handles special patterns:
 * <ul>
 * <li>Arithmetic comparison pattern (ISUB/LSUB followed by comparison)</li>
 * <li>Double comparison pattern (DCMPL/DCMPG followed by comparison)</li>
 * <li>CompareTo pattern (compareTo() call followed by comparison)</li>
 * <li>Plain comparison (value compared to 0)</li>
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
    public BranchState handle(BranchContext ctx) {
        Deque<LambdaExpression> stack = ctx.stack();

        if (stack.isEmpty()) {
            Log.tracef(INSTRUCTION_NAME + ": Stack empty, skipping");
            return ctx.state();
        }

        // Debug: show label info at start
        Log.debugf(INSTRUCTION_NAME + ": ENTRY - sameLabel=%s, jumpTarget=%s, jumpLabelClass=%s, labelId=%d",
                ctx.sameLabel(), ctx.jumpTarget(), ctx.jumpLabelClass(),
                System.identityHashCode(ctx.jumpInsn().label));

        PatternDetector.BranchPatternAnalysis patterns = PatternDetector.BranchPatternAnalysis.analyze(stack);

        LambdaExpression right;
        LambdaExpression left;

        switch (patterns.pattern()) {
            case NUMERIC_COMPARISON -> {
                // Numeric comparison: ISUB/LSUB or DCMPL/DCMPG → comparison
                // Unwrap Cast nodes from CHECKCAST (type narrowing before unboxing)
                right = BranchHandler.unwrapCast(BytecodeValidator.popSafe(stack, INSTRUCTION_NAME + "-NumericComp-right"));
                left = BranchHandler.unwrapCast(BytecodeValidator.popSafe(stack, INSTRUCTION_NAME + "-NumericComp-left"));
            }
            case COMPARE_TO -> {
                // CompareTo pattern: a.compareTo(b) → comparison
                LambdaExpression.MethodCall methodCall = (LambdaExpression.MethodCall) BytecodeValidator.popSafe(stack,
                        INSTRUCTION_NAME + "-CompareTo");
                left = methodCall.target();
                right = methodCall.arguments().getFirst();
            }
            default -> {
                // Plain comparison: value → comparison with 0
                // Handles ARITHMETIC and OTHER patterns
                left = BytecodeValidator.popSafe(stack, INSTRUCTION_NAME + "-plain");
                right = LambdaExpression.Constant.ZERO_INT;
            }
        }

        Operator op = OpcodeOperatorMapper.determineSingleOperandOperator(
                ctx.jumpLabelClass(), ctx.jumpTarget(), ctx.opcode());
        LambdaExpression comparison = new LambdaExpression.BinaryOp(left, op, right);

        Log.tracef(INSTRUCTION_NAME + ": opcode=%d, jumpTarget=%s, operator=%s, comparison=%s",
                ctx.opcode(), ctx.jumpTarget(), op, comparison);

        // Determine jumpToTrue using consolidated helper
        boolean jumpToTrue = determineJumpToTrue(ctx.jumpTarget(), ctx.jumpLabelClass(), ctx.opcode(),
                OpcodeOperatorMapper::isSuccessJumpSingleOperand);

        // Delegate to shared branch processing and combination logic
        return processAndCombineBranch(ctx, comparison, INSTRUCTION_NAME, jumpToTrue);
    }
}
