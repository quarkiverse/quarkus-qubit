package io.quarkus.qusaq.deployment.analysis.handlers;

import io.quarkus.qusaq.deployment.LambdaExpression;
import org.jboss.logging.Logger;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;

import static io.quarkus.qusaq.deployment.analysis.BytecodeAnalysisConstants.LOOKAHEAD_WINDOW_SIZE;
import static org.objectweb.asm.Opcodes.*;

/**
 * Handles constant load instructions with special ICONST_0/1 post-branch handling.
 */
public class ConstantInstructionHandler implements InstructionHandler {

    private static final Logger log = Logger.getLogger(ConstantInstructionHandler.class);

    @Override
    public boolean canHandle(AbstractInsnNode insn) {
        int opcode = insn.getOpcode();
        return opcode == BIPUSH || opcode == SIPUSH || opcode == LDC ||
               opcode == ACONST_NULL ||
               (opcode >= ICONST_0 && opcode <= ICONST_5) ||
               (opcode >= FCONST_0 && opcode <= FCONST_2) ||
               (opcode >= LCONST_0 && opcode <= LCONST_1) ||
               (opcode >= DCONST_0 && opcode <= DCONST_1);
    }

    @Override
    public boolean handle(AbstractInsnNode insn, AnalysisContext ctx) {
        int opcode = insn.getOpcode();

        if (opcode == BIPUSH || opcode == SIPUSH) {
            handleIntConstant(ctx, (IntInsnNode) insn);
            return false;
        }

        if (opcode == LDC) {
            handleLdc(ctx, (LdcInsnNode) insn);
            return false;
        }

        if (opcode == ACONST_NULL) {
            handleNullConstant(ctx);
            return false;
        }

        if (opcode >= ICONST_0 && opcode <= ICONST_5) {
            return handleIconst(ctx, opcode);
        }

        if (opcode >= FCONST_0 && opcode <= FCONST_2) {
            handleFloatConstant(ctx, opcode);
            return false;
        }

        if (opcode >= LCONST_0 && opcode <= LCONST_1) {
            handleLongConstant(ctx, opcode);
            return false;
        }

        if (opcode >= DCONST_0 && opcode <= DCONST_1) {
            handleDoubleConstant(ctx, opcode);
            return false;
        }

        return false;
    }

    /** Handles BIPUSH/SIPUSH. */
    private void handleIntConstant(AnalysisContext ctx, IntInsnNode intInsn) {
        ctx.push(new LambdaExpression.Constant(intInsn.operand, int.class));
    }

    /** Handles LDC. */
    private void handleLdc(AnalysisContext ctx, LdcInsnNode ldcInsn) {
        ctx.push(new LambdaExpression.Constant(ldcInsn.cst, ldcInsn.cst.getClass()));
    }

    /** Handles ACONST_NULL. */
    private void handleNullConstant(AnalysisContext ctx) {
        ctx.push(new LambdaExpression.NullLiteral(Object.class));
    }

    /** Handles ICONST with post-branch boolean marker detection. */
    private boolean handleIconst(AnalysisContext ctx, int opcode) {
        int constValue = opcode - ICONST_0;

        if (ctx.hasSeenBranch() && (constValue == 0 || constValue == 1)) {
            boolean isUsedInExpression = isIconstUsedInExpression(ctx);

            if (!isUsedInExpression) {
                if (ctx.getStackSize() >= 1 && isFinalResult(ctx)) {
                    log.tracef("ICONST_%d: Final boolean result marker, terminating analysis", constValue);
                    return true;
                }

                log.debugf("Skipping intermediate boolean marker ICONST_%d", constValue);
                return false;
            }
        }

        ctx.push(new LambdaExpression.Constant(constValue, int.class));
        return false;
    }

    /** Checks if final result. */
    private boolean isFinalResult(AnalysisContext ctx) {
        int currentIndex = ctx.getCurrentInstructionIndex();
        int instructionCount = ctx.getInstructionCount();

        for (int i = currentIndex + 1; i < instructionCount; i++) {
            AbstractInsnNode insn = ctx.getInstructions().get(i);
            int opcode = insn.getOpcode();

            if (opcode == -1) {
                continue;
            }

            return (opcode == IRETURN || opcode == ARETURN || opcode == RETURN);
        }

        return true;
    }

    /** Handles FCONST. */
    private void handleFloatConstant(AnalysisContext ctx, int opcode) {
        float value = (opcode - FCONST_0);
        ctx.push(new LambdaExpression.Constant(value, float.class));
    }

    /** Handles LCONST. */
    private void handleLongConstant(AnalysisContext ctx, int opcode) {
        long value = (opcode - LCONST_0);
        ctx.push(new LambdaExpression.Constant(value, long.class));
    }

    /** Handles DCONST. */
    private void handleDoubleConstant(AnalysisContext ctx, int opcode) {
        double value = (opcode - DCONST_0);
        ctx.push(new LambdaExpression.Constant(value, double.class));
    }

    /** Lookahead: checks if ICONST is used in expression. */
    private boolean isIconstUsedInExpression(AnalysisContext ctx) {
        int currentIndex = ctx.getCurrentInstructionIndex();
        int instructionCount = ctx.getInstructionCount();

        for (int j = currentIndex + 1; j < Math.min(currentIndex + LOOKAHEAD_WINDOW_SIZE, instructionCount); j++) {
            AbstractInsnNode nextInsn = ctx.getInstructions().get(j);
            int nextOpcode = nextInsn.getOpcode();

            if (nextOpcode == -1) {
                continue;
            }

            if (nextOpcode == GOTO) {
                return false;
            }

            if (isInvokeOpcode(nextOpcode)) {
                return !isBooleanValueOfCall(nextInsn);
            }

            if (isArithmeticOrLogicalOpcode(nextOpcode) || isBranchOpcode(nextOpcode)) {
                return true;
            }

            return !(nextOpcode == IRETURN || nextOpcode == ARETURN || nextOpcode == RETURN);
        }

        return false;
    }

    /** Checks if opcode is invoke instruction. */
    private boolean isInvokeOpcode(int opcode) {
        return opcode == INVOKEVIRTUAL || opcode == INVOKESTATIC ||
               opcode == INVOKESPECIAL || opcode == INVOKEINTERFACE;
    }

    /** Checks if instruction is Boolean.valueOf(). */
    private boolean isBooleanValueOfCall(AbstractInsnNode insn) {
        if (insn.getOpcode() == INVOKESTATIC && insn instanceof org.objectweb.asm.tree.MethodInsnNode methodInsn) {
            return methodInsn.owner.equals("java/lang/Boolean") &&
                   methodInsn.name.equals("valueOf") &&
                   methodInsn.desc.equals("(Z)Ljava/lang/Boolean;");
        }
        return false;
    }

    /** Checks if opcode is arithmetic or logical. */
    private boolean isArithmeticOrLogicalOpcode(int opcode) {
        return (opcode >= IADD && opcode <= DREM) ||
               opcode == IAND || opcode == IOR || opcode == IXOR;
    }

    /** Checks if opcode is conditional branch. */
    private boolean isBranchOpcode(int opcode) {
        return (opcode >= IFEQ && opcode <= IF_ICMPLE) ||
               opcode == IFNULL || opcode == IFNONNULL;
    }

}
