package io.quarkus.qusaq.deployment.analysis.handlers;

import io.quarkus.qusaq.deployment.LambdaExpression;
import io.quarkus.qusaq.deployment.analysis.BytecodeValidator;
import org.objectweb.asm.tree.AbstractInsnNode;

import static io.quarkus.qusaq.deployment.LambdaExpression.BinaryOp.Operator.*;
import static org.objectweb.asm.Opcodes.*;

/**
 * Handles arithmetic (ADD, SUB, MUL, DIV, MOD), logical (IAND, IOR), and comparison (DCMPL/G, FCMPL/G, LCMP) operations.
 * Comparison operations leave operands on stack for branch instruction consumption.
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

        // Continue processing (don't terminate analysis)
        return false;
    }

    /** Checks if opcode is arithmetic. */
    private boolean isArithmeticOpcode(int opcode) {
        return (opcode >= IADD && opcode <= DREM) && opcode != IAND && opcode != IOR;
    }

    /** Checks if opcode is logical (IAND, IOR). */
    private boolean isLogicalOpcode(int opcode) {
        return opcode == IAND || opcode == IOR;
    }

    /** Checks if opcode is comparison (DCMPL/G, FCMPL/G, LCMP). */
    private boolean isComparisonOpcode(int opcode) {
        return opcode == DCMPL || opcode == DCMPG ||
               opcode == FCMPL || opcode == FCMPG ||
               opcode == LCMP;
    }

    /** Handles arithmetic operations: pops operands, creates BinaryOp, pushes result. */
    private void handleArithmeticOperation(AnalysisContext ctx, int opcode) {
        // Validate stack has at least 2 elements
        BytecodeValidator.requireStackSize(ctx.getStack(), 2, getOpcodeName(opcode));

        LambdaExpression right = ctx.pop();
        LambdaExpression left = ctx.pop();

        LambdaExpression.BinaryOp.Operator arithmeticOp = switch (opcode) {
            case IADD, LADD, FADD, DADD -> ADD;
            case ISUB, LSUB, FSUB, DSUB -> SUB;
            case IMUL, LMUL, FMUL, DMUL -> MUL;
            case IDIV, LDIV, FDIV, DDIV -> DIV;
            case IREM, LREM -> MOD;
            default -> throw new IllegalStateException("Unexpected arithmetic opcode: " + opcode);
        };

        ctx.push(new LambdaExpression.BinaryOp(left, arithmeticOp, right));
    }

    /** Handles logical operations: creates BinaryOp with AND/OR operator. */
    private void handleLogicalOperation(AnalysisContext ctx, int opcode) {
        // Validate stack has at least 2 elements
        BytecodeValidator.requireStackSize(ctx.getStack(), 2, getOpcodeName(opcode));

        LambdaExpression right = ctx.pop();
        LambdaExpression left = ctx.pop();

        LambdaExpression.BinaryOp.Operator logicalOp = (opcode == IAND) ? AND : OR;
        ctx.push(new LambdaExpression.BinaryOp(left, logicalOp, right));
    }

    /** Handles comparison operations: leaves operands on stack for branch handler to consume. */
    private void handleComparisonOperation(AnalysisContext ctx, int opcode) {
        // Validate stack has at least 2 elements
        BytecodeValidator.requireStackSize(ctx.getStack(), 2, getOpcodeName(opcode));

        // Do nothing - leave operands on stack for branch instruction to consume
        // The branch handler will recognize the two-operand pattern and create the comparison
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
