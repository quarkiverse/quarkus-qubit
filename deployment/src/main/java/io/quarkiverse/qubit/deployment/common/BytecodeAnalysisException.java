package io.quarkiverse.qubit.deployment.common;

import java.util.Arrays;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;

/**
 * Unified exception for bytecode analysis failures with semantic factory methods.
 * Context is embedded in messages for logging. Use factory methods over constructors.
 */
public class BytecodeAnalysisException extends RuntimeException {

    public BytecodeAnalysisException(String message) {
        super(message);
    }

    public BytecodeAnalysisException(String message, Throwable cause) {
        super(message, cause);
    }

    /** Stack underflow during instruction processing. */
    public static BytecodeAnalysisException stackUnderflow(String instruction, int expected, int actual) {
        return new BytecodeAnalysisException(
                String.format("Stack underflow processing %s: expected %d elements, found %d",
                        instruction, expected, actual));
    }

    /** Unexpected opcode encountered in handler. */
    public static BytecodeAnalysisException unexpectedOpcode(String handlerContext, int opcode) {
        return new BytecodeAnalysisException(
                String.format("Unexpected opcode in %s: %d (0x%02X). " +
                        "This may indicate unsupported bytecode or a lambda expression that cannot be analyzed.",
                        handlerContext, opcode, opcode));
    }

    /** Invalid opcode where specific opcodes expected. */
    public static BytecodeAnalysisException invalidOpcode(int opcode, int... validOpcodes) {
        String opcodeList = Arrays.stream(validOpcodes)
                .mapToObj(String::valueOf)
                .collect(Collectors.joining(", "));
        return new BytecodeAnalysisException(
                String.format("Invalid opcode: %d, expected one of [%s]", opcode, opcodeList));
    }

    /** Unsupported operation or bytecode pattern. */
    public static BytecodeAnalysisException unsupported(String operation, String details) {
        return new BytecodeAnalysisException(
                String.format("Unsupported %s: %s. This operation cannot be translated to a database query.",
                        operation, details));
    }

    /** Unexpected null value during analysis. */
    public static BytecodeAnalysisException unexpectedNull(String context) {
        return new BytecodeAnalysisException(
                String.format("Unexpected null value: %s", context));
    }

    /** Class bytecode not found in application archives or classpath. */
    public static BytecodeAnalysisException bytecodeNotFound(String className) {
        return new BytecodeAnalysisException(
                "Could not load bytecode for class: " + className +
                        ". Ensure the class is compiled and in the application classpath.");
    }

    /** Lambda method not found in class. */
    public static BytecodeAnalysisException lambdaMethodNotFound(String className, String methodName, String descriptor) {
        return new BytecodeAnalysisException(
                String.format("Lambda method %s%s not found in class %s", methodName, descriptor, className));
    }

    /** Lambda analysis failed with context. */
    public static BytecodeAnalysisException analysisFailedWithContext(
            String message, @Nullable String className, String methodName, String descriptor,
            Throwable cause) {
        String formatted = formatContext(message, className, methodName, descriptor);
        return new BytecodeAnalysisException(formatted, cause);
    }

    /** Build-time analysis failure - use when failOnAnalysisError config is true. */
    public static BytecodeAnalysisException analysisError(String formattedMessage, Throwable cause) {
        return new BytecodeAnalysisException(formattedMessage, cause);
    }

    private static String formatContext(String message, @Nullable String className, String methodName,
            String descriptor) {
        StringBuilder sb = new StringBuilder(message);
        sb.append(" [");
        if (className != null) {
            sb.append("class=").append(className).append(", ");
        }
        sb.append("method=").append(methodName);
        sb.append(", descriptor=").append(descriptor);
        sb.append("]");
        return sb.toString();
    }
}
