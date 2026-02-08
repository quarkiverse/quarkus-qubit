package io.quarkiverse.qubit.deployment.analysis.instruction;

import static io.quarkiverse.qubit.deployment.common.ExceptionMessages.HANDLERS_CANNOT_BE_EMPTY;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.objectweb.asm.tree.AbstractInsnNode;

/**
 * Registry for instruction handlers with O(1) opcode dispatch.
 * First matching handler wins (chain of responsibility).
 */
public final class InstructionHandlerRegistry {

    private final List<InstructionHandler> handlers;
    private final Map<Integer, InstructionHandler> opcodeDispatch;

    /** Creates registry with opcode dispatch table. */
    public InstructionHandlerRegistry(List<InstructionHandler> handlers) {
        Objects.requireNonNull(handlers, "handlers cannot be null");
        if (handlers.isEmpty()) {
            throw new IllegalArgumentException(HANDLERS_CANNOT_BE_EMPTY);
        }
        this.handlers = List.copyOf(handlers);
        this.opcodeDispatch = buildDispatchTable(this.handlers);
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
     * Builds opcode dispatch table from handlers.
     * First handler in list wins for conflicting opcodes (maintains priority order).
     */
    private static Map<Integer, InstructionHandler> buildDispatchTable(List<InstructionHandler> handlers) {
        Map<Integer, InstructionHandler> dispatch = new HashMap<>();
        for (InstructionHandler handler : handlers) {
            for (Integer opcode : handler.supportedOpcodes()) {
                dispatch.putIfAbsent(opcode, handler);
            }
        }
        return dispatch;
    }

    /** Finds the handler for the instruction via O(1) dispatch, falling back to linear search. */
    public Optional<InstructionHandler> handlerFor(AbstractInsnNode insn) {
        int opcode = insn.getOpcode();

        // O(1) dispatch table lookup
        InstructionHandler handler = opcodeDispatch.get(opcode);
        if (handler != null) {
            return Optional.of(handler);
        }

        // Fallback: linear search for handlers without opcode declaration
        return handlers.stream()
                .filter(h -> h.canHandle(insn))
                .findFirst();
    }

    /** Returns the handler list (for testing). */
    public List<InstructionHandler> handlers() {
        return handlers;
    }
}
