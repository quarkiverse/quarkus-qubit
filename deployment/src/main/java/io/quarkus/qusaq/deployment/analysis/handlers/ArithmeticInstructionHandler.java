package io.quarkus.qusaq.deployment.analysis.handlers;

import io.quarkus.qusaq.deployment.LambdaExpression;
import io.quarkus.qusaq.deployment.analysis.BytecodeAnalysisException;
import io.quarkus.qusaq.deployment.analysis.BytecodeValidator;
import org.objectweb.asm.tree.AbstractInsnNode;

import static io.quarkus.qusaq.deployment.LambdaExpression.BinaryOp.*;
import static org.objectweb.asm.Opcodes.*;

/**
 * Handles arithmetic, logical, and comparison operations.
 */
public class ArithmeticInstructionHandler implements InstructionHandler {

    @Override
    public boolean canHandle(AbstractInsnNode insn) {
        int opcode = insn.getOpcode();
        return isArithmeticOpcode(opcode) ||
               isLogicalOpcode(opcode) ||
               isComparisonOpcode(opcode);
    }

    @Override
    public boolean handle(AbstractInsnNode insn, AnalysisContext ctx) {
        int opcode = insn.getOpcode();

        if (isArithmeticOpcode(opcode)) {
            handleArithmeticOperation(ctx, opcode);
        } else if (isLogicalOpcode(opcode)) {
            handleLogicalOperation(ctx, opcode);
        } else if (isComparisonOpcode(opcode)) {
            handleComparisonOperation(ctx, opcode);
        }

        return false;
    }

    /** Checks if opcode is arithmetic. */
    private boolean isArithmeticOpcode(int opcode) {
        return (opcode >= IADD && opcode <= DREM) && opcode != IAND && opcode != IOR;
    }

    /** Checks if opcode is logical. */
    private boolean isLogicalOpcode(int opcode) {
        return opcode == IAND || opcode == IOR;
    }

    /** Checks if opcode is comparison. */
    private boolean isComparisonOpcode(int opcode) {
        return opcode == DCMPL || opcode == DCMPG ||
               opcode == FCMPL || opcode == FCMPG ||
               opcode == LCMP;
    }

    /** Handles arithmetic operations. */
    private void handleArithmeticOperation(AnalysisContext ctx, int opcode) {
        BytecodeValidator.requireStackSize(ctx.getStack(), 2, getOpcodeName(opcode));

        LambdaExpression right = ctx.pop();
        LambdaExpression left = ctx.pop();

        LambdaExpression.BinaryOp result = switch (opcode) {
            case IADD, LADD, FADD, DADD -> add(left, right);
            case ISUB, LSUB, FSUB, DSUB -> sub(left, right);
            case IMUL, LMUL, FMUL, DMUL -> mul(left, right);
            case IDIV, LDIV, FDIV, DDIV -> div(left, right);
            case IREM, LREM -> mod(left, right);
            default -> throw BytecodeAnalysisException.unexpectedOpcode("arithmetic operation", opcode);
        };

        ctx.push(result);
    }

    /** Handles logical operations. */
    private void handleLogicalOperation(AnalysisContext ctx, int opcode) {
        BytecodeValidator.requireStackSize(ctx.getStack(), 2, getOpcodeName(opcode));

        LambdaExpression right = ctx.pop();
        LambdaExpression left = ctx.pop();

        LambdaExpression.BinaryOp result = opcode == IAND ? and(left, right) : or(left, right);
        ctx.push(result);
    }

    /** Handles comparison operations: leaves operands on stack for branch handler. */
    private void handleComparisonOperation(AnalysisContext ctx, int opcode) {
        BytecodeValidator.requireStackSize(ctx.getStack(), 2, getOpcodeName(opcode));
    }

    /** Returns opcode name for error messages. */
    private String getOpcodeName(int opcode) {
        return switch (opcode) {
            case IADD -> "IADD";
            case LADD -> "LADD";
            case FADD -> "FADD";
            case DADD -> "DADD";
            case ISUB -> "ISUB";
            case LSUB -> "LSUB";
            case FSUB -> "FSUB";
            case DSUB -> "DSUB";
            case IMUL -> "IMUL";
            case LMUL -> "LMUL";
            case FMUL -> "FMUL";
            case DMUL -> "DMUL";
            case IDIV -> "IDIV";
            case LDIV -> "LDIV";
            case FDIV -> "FDIV";
            case DDIV -> "DDIV";
            case IREM -> "IREM";
            case LREM -> "LREM";
            case IAND -> "IAND";
            case IOR -> "IOR";
            case DCMPL -> "DCMPL";
            case DCMPG -> "DCMPG";
            case FCMPL -> "FCMPL";
            case FCMPG -> "FCMPG";
            case LCMP -> "LCMP";
            default -> "UNKNOWN(" + opcode + ")";
        };
    }
}
