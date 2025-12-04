package io.quarkiverse.qubit.deployment.common;

import java.util.Deque;

import org.jspecify.annotations.NonNull;

/**
 * Defensive validation utilities for bytecode analysis.
 * Provides clear error messages for common analysis failures.
 */
public class BytecodeValidator {

    /**
     * Private constructor to prevent instantiation of utility class.
     */
    private BytecodeValidator() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Ensures the evaluation stack has the required number of elements.
     *
     * @param stack the evaluation stack
     * @param required the number of required elements
     * @param instruction the name of the instruction being processed (for error messages)
     * @throws BytecodeAnalysisException if stack size is insufficient
     */
    public static void requireStackSize(Deque<?> stack, int required, String instruction) {
        if (stack.size() < required) {
            throw BytecodeAnalysisException.stackUnderflow(instruction, required, stack.size());
        }
    }

    /**
     * Ensures a value is not null.
     *
     * @param value the value to check
     * @param context description of what was expected (for error messages)
     * @param <T> the type of the value
     * @return the non-null value
     * @throws BytecodeAnalysisException if value is null
     */
    public static <T> T requireNonNull(T value, String context) {
        if (value == null) {
            throw BytecodeAnalysisException.unexpectedNull(context);
        }
        return value;
    }

    /**
     * Validates that an opcode is one of the expected values.
     *
     * @param opcode the opcode to validate
     * @param validOpcodes the valid opcodes
     * @throws BytecodeAnalysisException if opcode is not in the valid set
     */
    public static void requireValidOpcode(int opcode, int... validOpcodes) {
        for (int valid : validOpcodes) {
            if (opcode == valid) {
                return;
            }
        }
        throw BytecodeAnalysisException.invalidOpcode(opcode, validOpcodes);
    }

    /**
     * Safely pops an element from the stack with validation.
     *
     * @param stack the evaluation stack
     * @param instruction the name of the instruction (for error messages)
     * @param <T> the type of elements in the stack
     * @return the popped element
     * @throws BytecodeAnalysisException if stack is empty
     */
    public static @NonNull <T> T popSafe(Deque<T> stack, String instruction) {
        requireStackSize(stack, 1, instruction);
        return stack.pop();
    }
}
