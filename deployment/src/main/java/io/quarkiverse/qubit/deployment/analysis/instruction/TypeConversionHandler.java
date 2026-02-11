package io.quarkiverse.qubit.deployment.analysis.instruction;

import static org.objectweb.asm.Opcodes.*;

import java.util.Set;

import org.objectweb.asm.tree.AbstractInsnNode;

import io.quarkiverse.qubit.deployment.ast.LambdaExpression;
import io.quarkiverse.qubit.deployment.common.BytecodeAnalysisException;

/**
 * Handles primitive type conversion instructions with constant folding optimization.
 */
public enum TypeConversionHandler implements InstructionHandler {
    INSTANCE;

    /** Opcodes handled by this handler for O(1) dispatch. */
    private static final Set<Integer> SUPPORTED_OPCODES = Set.of(
            I2L, I2F, I2D,
            L2I, L2F, L2D,
            F2I, F2L, F2D,
            D2I, D2L, D2F);

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
        TypeConversionInfo conversionInfo = getConversionInfo(opcode);
        handleTypeConversion(ctx, conversionInfo.sourceType, conversionInfo.targetType);
        return false;
    }

    /** Handles type conversion with constant folding. */
    private void handleTypeConversion(AnalysisContext ctx, Class<?> sourceType, Class<?> targetType) {
        if (ctx.isStackEmpty()) {
            return;
        }

        LambdaExpression top = ctx.peek();

        if (top instanceof LambdaExpression.Constant constant && constant.type() == sourceType) {
            ctx.pop();
            Number value = (Number) constant.value();
            Object convertedValue = convertValue(value, targetType);
            ctx.push(new LambdaExpression.Constant(convertedValue, targetType));
        }
    }

    /** Converts numeric value to target primitive type. */
    private Object convertValue(Number value, Class<?> targetType) {
        return switch (targetType.getName()) {
            case "int" -> value.intValue();
            case "long" -> value.longValue();
            case "float" -> value.floatValue();
            case "double" -> value.doubleValue();
            default -> throw BytecodeAnalysisException.unsupported("type conversion target", targetType.getName());
        };
    }

    /** Returns conversion information for opcode. */
    private TypeConversionInfo getConversionInfo(int opcode) {
        return switch (opcode) {
            case I2L -> new TypeConversionInfo(int.class, long.class);
            case I2F -> new TypeConversionInfo(int.class, float.class);
            case I2D -> new TypeConversionInfo(int.class, double.class);
            case L2I -> new TypeConversionInfo(long.class, int.class);
            case L2F -> new TypeConversionInfo(long.class, float.class);
            case L2D -> new TypeConversionInfo(long.class, double.class);
            case F2I -> new TypeConversionInfo(float.class, int.class);
            case F2L -> new TypeConversionInfo(float.class, long.class);
            case F2D -> new TypeConversionInfo(float.class, double.class);
            case D2I -> new TypeConversionInfo(double.class, int.class);
            case D2L -> new TypeConversionInfo(double.class, long.class);
            case D2F -> new TypeConversionInfo(double.class, float.class);
            default -> throw BytecodeAnalysisException.unexpectedOpcode("type conversion", opcode);
        };
    }

    /** Type conversion metadata. */
    private record TypeConversionInfo(Class<?> sourceType, Class<?> targetType) {
    }
}
