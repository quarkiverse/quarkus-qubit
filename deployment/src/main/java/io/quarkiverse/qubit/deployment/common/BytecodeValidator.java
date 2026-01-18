package io.quarkiverse.qubit.deployment.common;

import java.util.Deque;

import org.jspecify.annotations.NonNull;

/**
 * Defensive validation utilities for bytecode analysis.
 * Provides clear error messages for common analysis failures.
 */
public class BytecodeValidator {

    private BytecodeValidator() {
        throw new UnsupportedOperationException("Utility class");
    }

    /** Ensures the evaluation stack has the required number of elements. */
    public static void requireStackSize(Deque<?> stack, int required, String instruction) {
        if (stack.size() < required) {
            throw BytecodeAnalysisException.stackUnderflow(instruction, required, stack.size());
        }
    }

    /** Ensures a value is not null, returning it if valid. */
    public static <T> T requireNonNull(T value, String context) {
        if (value == null) {
            throw BytecodeAnalysisException.unexpectedNull(context);
        }
        return value;
    }

    /** Validates that an opcode is one of the expected values. */
    public static void requireValidOpcode(int opcode, int... validOpcodes) {
        for (int valid : validOpcodes) {
            if (opcode == valid) {
                return;
            }
        }
        throw BytecodeAnalysisException.invalidOpcode(opcode, validOpcodes);
    }

    /** Safely pops an element from the stack with validation. */
    public static @NonNull <T> T popSafe(Deque<T> stack, String instruction) {
        requireStackSize(stack, 1, instruction);
        return stack.pop();
    }
}
