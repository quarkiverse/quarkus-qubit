package io.quarkus.qusaq.deployment.analysis;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Exception thrown when bytecode analysis encounters an error.
 * Provides clear, actionable error messages for build-time failures.
 */
public class BytecodeAnalysisException extends RuntimeException {

    public BytecodeAnalysisException(String message) {
        super(message);
    }

    public BytecodeAnalysisException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Creates exception for stack underflow errors.
     */
    public static BytecodeAnalysisException stackUnderflow(String instruction, int expected, int actual) {
        return new BytecodeAnalysisException(
            String.format("Stack underflow processing %s: expected %d elements, found %d",
                instruction, expected, actual)
        );
    }

    /**
     * Creates exception for invalid opcode errors.
     */
    public static BytecodeAnalysisException invalidOpcode(int opcode, int... validOpcodes) {
        String opcodeList = Arrays.stream(validOpcodes)
                .mapToObj(String::valueOf)
                .collect(Collectors.joining(", "));
        return new BytecodeAnalysisException(
            String.format("Invalid opcode: %d, expected one of [%s]", opcode, opcodeList)
        );
    }

    /**
     * Creates exception for unexpected null values.
     */
    public static BytecodeAnalysisException unexpectedNull(String context) {
        return new BytecodeAnalysisException(
            String.format("Unexpected null value: %s", context)
        );
    }

    /**
     * Creates exception for unexpected opcode in specific handler.
     */
    public static BytecodeAnalysisException unexpectedOpcode(String handlerContext, int opcode) {
        return new BytecodeAnalysisException(
            String.format("Unexpected opcode in %s: %d (0x%02X). " +
                         "This may indicate unsupported bytecode or a lambda expression that cannot be analyzed.",
                handlerContext, opcode, opcode)
        );
    }

    /**
     * Creates exception for unsupported operation or type.
     */
    public static BytecodeAnalysisException unsupported(String operation, String details) {
        return new BytecodeAnalysisException(
            String.format("Unsupported %s: %s. " +
                         "This operation cannot be translated to a database query.",
                operation, details)
        );
    }

    /**
     * Creates exception for unexpected state during analysis.
     */
    public static BytecodeAnalysisException unexpectedState(String context, String details) {
        return new BytecodeAnalysisException(
            String.format("Unexpected state during %s: %s. " +
                         "This may indicate malformed bytecode or an internal analysis error.",
                context, details)
        );
    }
}
