package io.quarkiverse.qubit.deployment.analysis.branch;

import static io.quarkiverse.qubit.deployment.ast.LambdaExpression.BinaryOp.Operator;
import static org.objectweb.asm.Opcodes.*;

import java.util.Deque;

import org.objectweb.asm.tree.JumpInsnNode;

import io.quarkiverse.qubit.deployment.ast.LambdaExpression;
import io.quarkiverse.qubit.deployment.common.BytecodeValidator;
import io.quarkiverse.qubit.deployment.common.OpcodeOperatorMapper;
import io.quarkus.logging.Log;

/**
 * Handles two-operand comparison instructions (IF_ICMP*, IF_ACMP*).
 *
 * <p>
 * Supported opcodes:
 * <ul>
 * <li>IF_ICMPGT, IF_ICMPGE, IF_ICMPLT, IF_ICMPLE - Integer comparisons</li>
 * <li>IF_ICMPEQ, IF_ICMPNE - Integer equality</li>
 * <li>IF_ACMPEQ, IF_ACMPNE - Reference equality</li>
 * </ul>
 */
public class TwoOperandComparisonHandler implements BranchHandler {

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
    public BranchState handle(BranchContext ctx) {
        Deque<LambdaExpression> stack = ctx.stack();

        BytecodeValidator.requireStackSize(stack, 2, INSTRUCTION_NAME);

        LambdaExpression right = BytecodeValidator.popSafe(stack, INSTRUCTION_NAME + "-right");
        LambdaExpression left = BytecodeValidator.popSafe(stack, INSTRUCTION_NAME + "-left");

        Log.debugf(INSTRUCTION_NAME + ": Jump target label %s -> %s",
                System.identityHashCode(ctx.jumpInsn().label), ctx.jumpTarget());

        Operator op = OpcodeOperatorMapper.determineTwoOperandOperator(
                ctx.jumpLabelClass(), ctx.jumpTarget(), ctx.opcode());
        LambdaExpression result = new LambdaExpression.BinaryOp(left, op, right);

        // Determine jumpToTrue using consolidated helper
        boolean jumpToTrue = determineJumpToTrue(ctx.jumpTarget(), ctx.jumpLabelClass(), ctx.opcode(),
                OpcodeOperatorMapper::isSuccessJumpTwoOperand);

        // Delegate to shared branch processing and combination logic
        return processAndCombineBranch(ctx, result, INSTRUCTION_NAME, jumpToTrue);
    }
}
