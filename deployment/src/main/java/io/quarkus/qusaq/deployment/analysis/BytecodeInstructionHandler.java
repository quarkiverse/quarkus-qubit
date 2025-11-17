package io.quarkus.qusaq.deployment.analysis;

import java.util.Deque;
import java.util.List;

import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import io.quarkus.qusaq.deployment.LambdaExpression;
import io.quarkus.qusaq.deployment.util.DescriptorParser;

/**
 * Handles bytecode instruction processing: type conversions, loads, constants, method calls.
 */
public class BytecodeInstructionHandler {

    /**
     * Handles numeric type conversion (I2L, L2F, D2I, etc.).
     */
    public void handleTypeConversion(Deque<LambdaExpression> stack, Class<?> sourceType, Class<?> targetType) {
        if (stack.isEmpty()) {
            return;
        }

        if (stack.peek() instanceof LambdaExpression.Constant constant && constant.type() == sourceType) {
            stack.pop();
            Number value = (Number) constant.value();

            Object convertedValue = switch (targetType.getName()) {
                case "int" -> value.intValue();
                case "long" -> value.longValue();
                case "float" -> value.floatValue();
                case "double" -> value.doubleValue();
                default -> throw new IllegalArgumentException("Unsupported target type: " + targetType);
            };

            stack.push(new LambdaExpression.Constant(convertedValue, targetType));
        }
    }

    /**
     * Handles primitive loads (ILOAD, LLOAD, FLOAD, DLOAD) from local variable slots.
     */
    public void handlePrimitiveLoad(Deque<LambdaExpression> stack, VarInsnNode insn,
                                     MethodNode method, Class<?> primitiveType) {
        int paramIndex = DescriptorParser.slotIndexToParameterIndex(method.desc, insn.var);

        Class<?> actualType = primitiveType;
        if (primitiveType == int.class && paramIndex >= 0) {
            actualType = DescriptorParser.getParameterType(method.desc, paramIndex);
        }

        stack.push(new LambdaExpression.CapturedVariable(paramIndex, actualType));
    }

    /**
     * Handles primitive constants (FCONST_*, LCONST_*, DCONST_*).
     */
    public void handlePrimitiveConstant(Deque<LambdaExpression> stack, int opcode,
                                        int baseOpcode, Class<?> constantType) {
        Number value = switch (constantType.getName()) {
            case "float" -> (float) (opcode - baseOpcode);
            case "long" -> (long) (opcode - baseOpcode);
            case "double" -> (double) (opcode - baseOpcode);
            default -> throw new IllegalArgumentException("Unsupported constant type: " + constantType);
        };

        stack.push(new LambdaExpression.Constant(value, constantType));
    }

    /**
     * Handles temporal accessor methods (getYear, getMonthValue, etc.).
     */
    public void handleTemporalAccessorMethod(Deque<LambdaExpression> stack, MethodInsnNode methodInsn,
                                              String ownerType, String... validMethods) {
        if (!methodInsn.owner.equals(ownerType)) {
            return;
        }

        for (String validMethod : validMethods) {
            if (methodInsn.name.equals(validMethod)) {
                if (!stack.isEmpty()) {
                    LambdaExpression target = stack.pop();
                    stack.push(new LambdaExpression.MethodCall(
                        target,
                        methodInsn.name,
                        List.of(),
                        int.class
                    ));
                }
                return;
            }
        }
    }

    /**
     * Handles zero-argument method calls (e.g., length(), isEmpty()).
     */
    public void handleNoArgumentMethodCall(Deque<LambdaExpression> stack, String methodName, Class<?> returnType) {
        if (!stack.isEmpty()) {
            LambdaExpression target = stack.pop();
            stack.push(new LambdaExpression.MethodCall(
                target,
                methodName,
                List.of(),
                returnType
            ));
        }
    }

    /**
     * Handles single-argument method calls (e.g., startsWith(), compareTo()).
     */
    public void handleSingleArgumentMethodCall(Deque<LambdaExpression> stack, String methodName, Class<?> returnType) {
        if (stack.size() >= 2) {
            LambdaExpression argument = stack.pop();  // The method argument
            LambdaExpression target = stack.pop();    // The object calling the method
            stack.push(new LambdaExpression.MethodCall(
                target,
                methodName,
                List.of(argument),
                returnType
            ));
        }
    }
}
