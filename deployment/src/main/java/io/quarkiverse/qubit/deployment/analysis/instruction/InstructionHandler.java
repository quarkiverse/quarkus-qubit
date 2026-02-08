package io.quarkiverse.qubit.deployment.analysis.instruction;

import org.objectweb.asm.tree.AbstractInsnNode;

import java.util.Set;

/**
 * Strategy interface for handling bytecode instructions during lambda analysis.
 * Chain of responsibility: each handler focuses on one instruction category.
 * Override {@link #supportedOpcodes()} for O(1) dispatch instead of linear search.
 */
public interface InstructionHandler {

    /** Opcodes this handler can process; empty set falls back to linear search via canHandle. */
    default Set<Integer> supportedOpcodes() {
        return Set.of();
    }

    /** Returns true if this handler can process the instruction. */
    boolean canHandle(AbstractInsnNode insn);

    /** Processes the instruction and updates context. Returns true to terminate analysis early. */
    boolean handle(AbstractInsnNode insn, AnalysisContext ctx);
}
