package io.quarkus.qusaq.deployment.analysis.handlers;

import io.quarkus.qusaq.deployment.LambdaExpression;
import io.quarkus.qusaq.deployment.analysis.BytecodeAnalysisException;
import io.quarkus.qusaq.deployment.util.DescriptorParser;
import io.quarkus.qusaq.deployment.util.TypeConverter;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

import static org.objectweb.asm.Opcodes.*;

/**
 * Handles load instructions: ALOAD, primitives, GETFIELD.
 */
public class LoadInstructionHandler implements InstructionHandler {

    @Override
    public boolean canHandle(AbstractInsnNode insn) {
        int opcode = insn.getOpcode();
        return opcode == ALOAD || opcode == ILOAD || opcode == LLOAD ||
               opcode == FLOAD || opcode == DLOAD || opcode == GETFIELD;
    }

    @Override
    public boolean handle(AbstractInsnNode insn, AnalysisContext ctx) {
        int opcode = insn.getOpcode();

        switch (opcode) {
            case ALOAD -> handleALoad(ctx, (VarInsnNode) insn);
            case ILOAD, LLOAD, FLOAD, DLOAD -> handlePrimitiveLoad(ctx, opcode, (VarInsnNode) insn);
            case GETFIELD -> handleGetField(ctx, (FieldInsnNode) insn);
        }

        return false;
    }

    /** Handles ALOAD: entity parameter or captured variable. */
    private void handleALoad(AnalysisContext ctx, VarInsnNode varInsn) {
        if (varInsn.var == ctx.getEntityParameterIndex()) {
            // This is the entity parameter (e.g., 'p' in "p -> p.age > 18")
            ctx.push(new LambdaExpression.Parameter("entity", Object.class, varInsn.var));
        } else {
            // This is a captured variable from the enclosing scope
            int paramIndex = DescriptorParser.slotIndexToParameterIndex(
                    ctx.getMethod().desc, varInsn.var);
            Class<?> varType = paramIndex >= 0
                    ? DescriptorParser.getParameterType(ctx.getMethod().desc, paramIndex)
                    : Object.class;
            ctx.push(new LambdaExpression.CapturedVariable(paramIndex, varType));
        }
    }

    /** Handles primitive loads. */
    private void handlePrimitiveLoad(AnalysisContext ctx, int opcode, VarInsnNode varInsn) {
        Class<?> primitiveType = switch (opcode) {
            case ILOAD -> int.class;
            case LLOAD -> long.class;
            case FLOAD -> float.class;
            case DLOAD -> double.class;
            default -> throw BytecodeAnalysisException.unexpectedOpcode("primitive load", opcode);
        };

        int paramIndex = DescriptorParser.slotIndexToParameterIndex(
                ctx.getMethod().desc, varInsn.var);

        Class<?> actualType = primitiveType;
        if (primitiveType == int.class && paramIndex >= 0) {
            actualType = DescriptorParser.getParameterType(ctx.getMethod().desc, paramIndex);
        }

        ctx.push(new LambdaExpression.CapturedVariable(paramIndex, actualType));
    }

    /** Handles GETFIELD: converts to FieldAccess node. */
    private void handleGetField(AnalysisContext ctx, FieldInsnNode fieldInsn) {
        if (!ctx.isStackEmpty()) {
            ctx.pop();
        }

        Class<?> fieldType = TypeConverter.descriptorToClass(fieldInsn.desc);
        ctx.push(new LambdaExpression.FieldAccess(fieldInsn.name, fieldType));
    }
}
