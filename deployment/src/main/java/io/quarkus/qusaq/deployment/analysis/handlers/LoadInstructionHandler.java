package io.quarkus.qusaq.deployment.analysis.handlers;

import io.quarkus.qusaq.deployment.LambdaExpression;
import io.quarkus.qusaq.deployment.util.DescriptorParser;
import io.quarkus.qusaq.deployment.util.TypeConverter;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

import static org.objectweb.asm.Opcodes.*;

/**
 * Handles load instructions: ALOAD, ILOAD/LLOAD/FLOAD/DLOAD (primitives), GETFIELD (field access).
 * Distinguishes entity parameter from captured variables. Converts GETFIELD to FieldAccess nodes.
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

        // Continue processing (don't terminate analysis)
        return false;
    }

    /** Handles ALOAD: entity parameter → Parameter, captured variables → CapturedVariable. */
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

    /** Handles primitive loads (ILOAD/LLOAD/FLOAD/DLOAD): creates CapturedVariable with correct type. */
    private void handlePrimitiveLoad(AnalysisContext ctx, int opcode, VarInsnNode varInsn) {
        Class<?> primitiveType = switch (opcode) {
            case ILOAD -> int.class;
            case LLOAD -> long.class;
            case FLOAD -> float.class;
            case DLOAD -> double.class;
            default -> throw new IllegalStateException("Unexpected load opcode: " + opcode);
        };

        int paramIndex = DescriptorParser.slotIndexToParameterIndex(
                ctx.getMethod().desc, varInsn.var);

        // For int loads, check if the actual parameter type is different (e.g., boolean, byte)
        Class<?> actualType = primitiveType;
        if (primitiveType == int.class && paramIndex >= 0) {
            actualType = DescriptorParser.getParameterType(ctx.getMethod().desc, paramIndex);
        }

        ctx.push(new LambdaExpression.CapturedVariable(paramIndex, actualType));
    }

    /** Handles GETFIELD: pops entity reference, pushes FieldAccess node. */
    private void handleGetField(AnalysisContext ctx, FieldInsnNode fieldInsn) {
        // Pop the entity reference (implicit in the FieldAccess node)
        if (!ctx.isStackEmpty()) {
            ctx.pop();
        }

        // Determine field type from bytecode descriptor
        Class<?> fieldType = TypeConverter.descriptorToClass(fieldInsn.desc);

        // Push field access AST node
        ctx.push(new LambdaExpression.FieldAccess(fieldInsn.name, fieldType));
    }
}
