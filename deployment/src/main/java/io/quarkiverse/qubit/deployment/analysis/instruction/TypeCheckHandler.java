package io.quarkiverse.qubit.deployment.analysis.instruction;

import static org.objectweb.asm.Opcodes.CHECKCAST;
import static org.objectweb.asm.Opcodes.INSTANCEOF;

import java.util.Set;

import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.TypeInsnNode;

import io.quarkiverse.qubit.deployment.ast.LambdaExpression;
import io.quarkiverse.qubit.deployment.common.BytecodeValidator;

import io.quarkus.logging.Log;

/** Handles INSTANCEOF and CHECKCAST type-checking instructions. */
public enum TypeCheckHandler implements InstructionHandler {
    INSTANCE;

    private static final Set<Integer> SUPPORTED_OPCODES = Set.of(INSTANCEOF, CHECKCAST);

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
        TypeInsnNode typeInsn = (TypeInsnNode) insn;
        // TypeInsnNode.desc is an internal name (e.g., "com/example/Dog"), not a descriptor.
        // Use Class.forName() to resolve the actual class (needed for entity subclasses).
        Class<?> targetType = resolveClass(typeInsn.desc);

        switch (insn.getOpcode()) {
            case INSTANCEOF -> handleInstanceOf(ctx, targetType);
            case CHECKCAST -> handleCheckCast(ctx, targetType);
        }
        return false;
    }

    /** Resolves an internal class name to a Class object. Falls back to Object.class if not loadable. */
    private static Class<?> resolveClass(String internalName) {
        String className = internalName.replace('/', '.');
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException _) {
            Log.debugf("Could not load class %s for type check instruction, using Object.class", className);
            return Object.class;
        }
    }

    /** INSTANCEOF: pops object reference, pushes InstanceOf expression (boolean predicate). */
    private void handleInstanceOf(AnalysisContext ctx, Class<?> targetType) {
        BytecodeValidator.requireStackSize(ctx.getStack(), 1, "INSTANCEOF");
        LambdaExpression operand = ctx.pop();
        ctx.push(new LambdaExpression.InstanceOf(operand, targetType));
    }

    /** CHECKCAST: pops object reference, pushes Cast expression (type-narrowed reference). */
    private void handleCheckCast(AnalysisContext ctx, Class<?> targetType) {
        BytecodeValidator.requireStackSize(ctx.getStack(), 1, "CHECKCAST");
        LambdaExpression operand = ctx.pop();
        ctx.push(new LambdaExpression.Cast(operand, targetType));
    }
}
