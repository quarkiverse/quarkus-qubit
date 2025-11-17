package io.quarkus.qusaq.deployment.analysis.handlers;

import io.quarkus.qusaq.deployment.LambdaExpression;
import org.jboss.logging.Logger;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;

import static io.quarkus.qusaq.deployment.analysis.BytecodeAnalysisConstants.LOOKAHEAD_WINDOW_SIZE;
import static org.objectweb.asm.Opcodes.*;

/**
 * Handles constant load instructions: BIPUSH/SIPUSH, LDC, ACONST_NULL, ICONST/FCONST/LCONST/DCONST.
 * Special handling for ICONST_0/1 after branches: uses lookahead to determine if constant is a boolean result marker (skip/terminate) or actual expression operand (include).
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

        // Handle simple constants
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

        // Handle ICONST with special post-branch logic
        if (opcode >= ICONST_0 && opcode <= ICONST_5) {
            return handleIconst(ctx, opcode);
        }

        // Handle other primitive constants
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

    /** Handles LDC: loads constant from constant pool. */
    private void handleLdc(AnalysisContext ctx, LdcInsnNode ldcInsn) {
        ctx.push(new LambdaExpression.Constant(ldcInsn.cst, ldcInsn.cst.getClass()));
    }

    /** Handles ACONST_NULL. */
    private void handleNullConstant(AnalysisContext ctx) {
        ctx.push(new LambdaExpression.NullLiteral(Object.class));
    }

    /** Handles ICONST with special logic for post-branch boolean markers. Returns true if should terminate. */
    private boolean handleIconst(AnalysisContext ctx, int opcode) {
        int constValue = opcode - ICONST_0;

        // Special handling for ICONST_0 and ICONST_1 after branch instructions
        if (ctx.hasSeenBranch() && (constValue == 0 || constValue == 1)) {
            boolean isUsedInExpression = isIconstUsedInExpression(ctx);

            // If not used in expression, this is a boolean result marker
            if (!isUsedInExpression) {
                // Only terminate if this is the final result (stack has content and no more useful instructions)
                if (ctx.getStackSize() >= 1 && isFinalResult(ctx)) {
                    log.tracef("ICONST_%d: Final boolean result marker, terminating analysis", constValue);
                    return true; // Signal to return early
                }

                // Otherwise, skip this boolean wrapper (don't push it onto stack)
                log.debugf("Skipping intermediate boolean marker ICONST_%d", constValue);
                return false; // Skip this instruction
            }
        }

        // Normal constant - push onto stack
        ctx.push(new LambdaExpression.Constant(constValue, int.class));
        return false;
    }

    /** Checks if final result (no more meaningful instructions). */
    private boolean isFinalResult(AnalysisContext ctx) {
        int currentIndex = ctx.getCurrentInstructionIndex();
        int instructionCount = ctx.getInstructionCount();

        // Look ahead to see if there are any more meaningful instructions
        for (int i = currentIndex + 1; i < instructionCount; i++) {
            AbstractInsnNode insn = ctx.getInstructions().get(i);
            int opcode = insn.getOpcode();

            // Skip pseudo-instructions (labels, line numbers, frames)
            if (opcode == -1) {
                continue;
            }

            // Return true if we find a return instruction, false for any other real instruction
            return (opcode == IRETURN || opcode == ARETURN || opcode == RETURN);
        }

        // Reached end of instructions
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

    /** Lookahead analysis: checks if ICONST is used in expression (vs. boolean result marker). */
    private boolean isIconstUsedInExpression(AnalysisContext ctx) {
        int currentIndex = ctx.getCurrentInstructionIndex();
        int instructionCount = ctx.getInstructionCount();

        // Look ahead to see how this constant is used
        for (int j = currentIndex + 1; j < Math.min(currentIndex + LOOKAHEAD_WINDOW_SIZE, instructionCount); j++) {
            AbstractInsnNode nextInsn = ctx.getInstructions().get(j);
            int nextOpcode = nextInsn.getOpcode();

            // Skip pseudo-instructions (labels, line numbers, frames)
            if (nextOpcode == -1) {
                continue;
            }

            // Check for GOTO (control flow that indicates this is a boolean result path)
            if (nextOpcode == GOTO) {
                return false;
            }

            // Check for method invocation
            if (isInvokeOpcode(nextOpcode)) {
                // If it's Boolean.valueOf, this is a wrapper → not used in expression
                return !isBooleanValueOfCall(nextInsn);
            }

            // Check for arithmetic/logical operations
            if (isArithmeticOrLogicalOpcode(nextOpcode)) {
                return true;
            }

            // Check for branch instructions
            if (isBranchOpcode(nextOpcode)) {
                return true;
            }

            // Return false if we hit a return, true for any other real instruction
            return !(nextOpcode == IRETURN || nextOpcode == ARETURN || nextOpcode == RETURN);
        }

        // Default: assume not used (conservative)
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

    /** Checks if opcode is arithmetic/logical operation. */
    private boolean isArithmeticOrLogicalOpcode(int opcode) {
        return (opcode >= IADD && opcode <= DREM) || // Arithmetic
               opcode == IAND || opcode == IOR || opcode == IXOR; // Logical
    }

    /** Checks if opcode is conditional branch. */
    private boolean isBranchOpcode(int opcode) {
        return (opcode >= IFEQ && opcode <= IF_ICMPLE) ||
               opcode == IFNULL || opcode == IFNONNULL;
    }

}
