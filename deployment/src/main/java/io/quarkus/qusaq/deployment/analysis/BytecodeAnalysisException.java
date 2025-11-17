package io.quarkus.qusaq.deployment.analysis;

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
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < validOpcodes.length; i++) {
            if (i > 0) sb.append(", ");
            sb.append(validOpcodes[i]);
        }
        return new BytecodeAnalysisException(
            String.format("Invalid opcode: %d, expected one of [%s]", opcode, sb.toString())
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
}
