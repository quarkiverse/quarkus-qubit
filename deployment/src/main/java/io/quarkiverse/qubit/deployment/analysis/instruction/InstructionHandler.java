package io.quarkiverse.qubit.deployment.analysis.instruction;

import org.objectweb.asm.tree.AbstractInsnNode;

import java.util.Set;

/**
 * Strategy interface for handling bytecode instructions during lambda analysis.
 *
 * <p>Each handler focuses on one instruction category (loads, arithmetic, method calls).
 * Handlers form a chain of responsibility: instructions are offered in sequence until one accepts.
 *
 * <p><b>Contract:</b> {@link #canHandle} must be pure; {@link #handle} only called when canHandle returns true.
 *
 * <p><b>Optimization:</b> Handlers should override {@link #supportedOpcodes()} to enable O(1) dispatch
 * table lookup instead of O(n) linear search through handlers.
 *
 * @see AnalysisContext
 */
public interface InstructionHandler {

    /**
     * Returns the set of opcodes this handler can process.
     * Used by {@link InstructionHandlerRegistry} for O(1) dispatch table lookup.
     *
     * <p>Override this method for optimal performance. Handlers that don't override
     * will fall back to linear search via {@link #canHandle}.
     *
     * @return set of supported opcodes, or empty set for linear-search fallback
     */
    default Set<Integer> supportedOpcodes() {
        return Set.of();
    }

    /**
     * Determines whether this handler can process the instruction.
     *
     * @param insn bytecode instruction
     * @return true if this handler recognizes and can process the instruction
     */
    boolean canHandle(AbstractInsnNode insn);

    /**
     * Processes the instruction and updates the analysis context (typically: pop operands, create AST nodes, push results).
     *
     * @param insn bytecode instruction to process
     * @param ctx analysis context
     * @return true if analysis should terminate (e.g., found final result)
     */
    boolean handle(AbstractInsnNode insn, AnalysisContext ctx);
}
