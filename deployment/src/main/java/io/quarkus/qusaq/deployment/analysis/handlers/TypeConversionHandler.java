package io.quarkus.qusaq.deployment.analysis.handlers;

import io.quarkus.qusaq.deployment.LambdaExpression;
import org.objectweb.asm.tree.AbstractInsnNode;

import static org.objectweb.asm.Opcodes.*;

/**
 * Handles primitive type conversion instructions (I2L, I2F, I2D, L2I, L2F, L2D, F2I, F2L, F2D, D2I, D2L, D2F).
 * Performs constant folding optimization: constants are converted at analysis time, non-constants handled implicitly during criteria generation.
 */
public class TypeConversionHandler implements InstructionHandler {

    @Override
    public boolean canHandle(AbstractInsnNode insn) {
        int opcode = insn.getOpcode();
        return isTypeConversionOpcode(opcode);
    }

    @Override
    public boolean handle(AbstractInsnNode insn, AnalysisContext ctx) {
        int opcode = insn.getOpcode();

        // Determine source and target types
        TypeConversionInfo conversionInfo = getConversionInfo(opcode);

        // Perform type conversion (with constant folding optimization)
        handleTypeConversion(ctx, conversionInfo.sourceType, conversionInfo.targetType);

        // Continue processing (don't terminate analysis)
        return false;
    }

    /** Checks if opcode is type conversion. */
    private boolean isTypeConversionOpcode(int opcode) {
        return opcode == I2L || opcode == I2F || opcode == I2D ||
               opcode == L2I || opcode == L2F || opcode == L2D ||
               opcode == F2I || opcode == F2L || opcode == F2D ||
               opcode == D2I || opcode == D2L || opcode == D2F;
    }

    /** Handles type conversion with constant folding: constants converted at analysis time, non-constants unchanged. */
    private void handleTypeConversion(AnalysisContext ctx, Class<?> sourceType, Class<?> targetType) {
        if (ctx.isStackEmpty()) {
            return;
        }

        // Peek at top of stack
        LambdaExpression top = ctx.peek();

        // Only perform constant folding for constant values
        if (top instanceof LambdaExpression.Constant constant && constant.type() == sourceType) {
            // Pop the constant
            ctx.pop();

            // Convert the value
            Number value = (Number) constant.value();
            Object convertedValue = convertValue(value, targetType);

            // Push the converted constant
            ctx.push(new LambdaExpression.Constant(convertedValue, targetType));
        }

        // For non-constants (field access, captured variables, etc.), do nothing.
        // Type conversions are handled implicitly during criteria query generation.
    }

    /** Converts numeric value to target primitive type. */
    private Object convertValue(Number value, Class<?> targetType) {
        return switch (targetType.getName()) {
            case "int" -> value.intValue();
            case "long" -> value.longValue();
            case "float" -> value.floatValue();
            case "double" -> value.doubleValue();
            default -> throw new IllegalArgumentException("Unsupported target type: " + targetType);
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
            default -> throw new IllegalStateException("Unexpected conversion opcode: " + opcode);
        };
    }

    /** Type conversion metadata. */
    private record TypeConversionInfo(Class<?> sourceType, Class<?> targetType) {}
}
