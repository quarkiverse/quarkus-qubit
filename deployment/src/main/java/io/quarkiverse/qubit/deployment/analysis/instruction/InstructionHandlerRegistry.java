package io.quarkiverse.qubit.deployment.analysis.instruction;

import static io.quarkiverse.qubit.deployment.common.ExceptionMessages.HANDLERS_CANNOT_BE_EMPTY;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.objectweb.asm.tree.AbstractInsnNode;

/**
 * Registry for instruction handlers. Enables DI for testability.
 * Handler order matters: first matching handler wins (chain of responsibility).
 *
 * @see InstructionHandler
 */
public record InstructionHandlerRegistry(List<InstructionHandler> handlers) {

    /** Validates handlers list is not null or empty. */
    public InstructionHandlerRegistry {
        Objects.requireNonNull(handlers, "handlers cannot be null");
        if (handlers.isEmpty()) {
            throw new IllegalArgumentException(HANDLERS_CANNOT_BE_EMPTY);
        }
        // Create defensive immutable copy
        handlers = List.copyOf(handlers);
    }

    /** Creates default registry with all standard handlers in priority order. */
    public static InstructionHandlerRegistry createDefault() {
        return new InstructionHandlerRegistry(List.of(
                LoadInstructionHandler.INSTANCE,
                ConstantInstructionHandler.INSTANCE,
                ArithmeticInstructionHandler.INSTANCE,
                TypeConversionHandler.INSTANCE,
                InvokeDynamicHandler.INSTANCE,      // Java 9+ string concatenation
                MethodInvocationHandler.INSTANCE
        ));
    }

    /**
     * Finds the first handler that can process the given instruction.
     *
     * @param insn bytecode instruction to find handler for
     * @return handler if found, empty otherwise (unrecognized instructions are valid)
     */
    public Optional<InstructionHandler> handlerFor(AbstractInsnNode insn) {
        return handlers.stream()
                .filter(h -> h.canHandle(insn))
                .findFirst();
    }
}
