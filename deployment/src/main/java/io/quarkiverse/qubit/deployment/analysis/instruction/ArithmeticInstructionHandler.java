package io.quarkiverse.qubit.deployment.analysis.instruction;

import io.quarkiverse.qubit.deployment.ast.LambdaExpression;
import io.quarkiverse.qubit.deployment.common.BytecodeAnalysisException;
import io.quarkiverse.qubit.deployment.common.BytecodeValidator;
import io.quarkiverse.qubit.deployment.common.OpcodeClassifier;
import io.quarkiverse.qubit.deployment.common.OpcodeNames;
import org.objectweb.asm.tree.AbstractInsnNode;

import java.util.Set;

import static io.quarkiverse.qubit.deployment.ast.LambdaExpression.BinaryOp.*;
import static org.objectweb.asm.Opcodes.*;

/**
 * Handles arithmetic, logical, and comparison operations.
 */
public enum ArithmeticInstructionHandler implements InstructionHandler {
    INSTANCE;

    /** Opcodes handled by this handler for O(1) dispatch. */
    private static final Set<Integer> SUPPORTED_OPCODES = Set.of(
            // Arithmetic: ADD, SUB, MUL, DIV, REM for int, long, float, double
            IADD, LADD, FADD, DADD,
            ISUB, LSUB, FSUB, DSUB,
            IMUL, LMUL, FMUL, DMUL,
            IDIV, LDIV, FDIV, DDIV,
            IREM, LREM, FREM, DREM,
            // Logical: AND, OR, XOR
            IAND, IOR, IXOR,
            // Comparison: CMP for long, float, double
            LCMP, FCMPL, FCMPG, DCMPL, DCMPG
    );

    @Override
    public Set<Integer> supportedOpcodes() {
        return SUPPORTED_OPCODES;
    }

    @Override
    public boolean canHandle(AbstractInsnNode insn) {
        return SUPPORTED_OPCODES.contains(insn.getOpcode());
    }

    @Override
    public boolean handle(AbstractInsnNode insn, AnalysisContext ctx) {
        int opcode = insn.getOpcode();

        if (OpcodeClassifier.isArithmeticOpcode(opcode)) {
            handleArithmeticOperation(ctx, opcode);
        } else if (OpcodeClassifier.isLogicalOpcode(opcode)) {
            handleLogicalOperation(ctx, opcode);
        } else if (OpcodeClassifier.isComparisonOpcode(opcode)) {
            handleComparisonOperation(ctx, opcode);
        }

        return false;
    }

    /** Handles arithmetic operations. */
    private void handleArithmeticOperation(AnalysisContext ctx, int opcode) {
        BytecodeValidator.requireStackSize(ctx.getStack(), 2, OpcodeNames.get(opcode));

        LambdaExpression right = ctx.pop();
        LambdaExpression left = ctx.pop();

        LambdaExpression.BinaryOp result = switch (opcode) {
            case IADD, LADD, FADD, DADD -> add(left, right);
            case ISUB, LSUB, FSUB, DSUB -> sub(left, right);
            case IMUL, LMUL, FMUL, DMUL -> mul(left, right);
            case IDIV, LDIV, FDIV, DDIV -> div(left, right);
            case IREM, LREM, FREM, DREM -> mod(left, right);
            default -> throw BytecodeAnalysisException.unexpectedOpcode("arithmetic operation", opcode);
        };

        ctx.push(result);
    }

    /** Handles logical operations. */
    private void handleLogicalOperation(AnalysisContext ctx, int opcode) {
        BytecodeValidator.requireStackSize(ctx.getStack(), 2, OpcodeNames.get(opcode));

        LambdaExpression right = ctx.pop();
        LambdaExpression left = ctx.pop();

        LambdaExpression.BinaryOp result = switch (opcode) {
            case IAND -> and(left, right);
            case IOR -> or(left, right);
            case IXOR -> throw BytecodeAnalysisException.unsupported(
                    "XOR operator (^)", "JPA Criteria API does not support bitwise XOR; use && and || instead");
            default -> throw BytecodeAnalysisException.unexpectedOpcode("logical operation", opcode);
        };
        ctx.push(result);
    }

    /** Handles comparison operations: leaves operands on stack for branch handler. */
    private void handleComparisonOperation(AnalysisContext ctx, int opcode) {
        BytecodeValidator.requireStackSize(ctx.getStack(), 2, OpcodeNames.get(opcode));
    }
}
