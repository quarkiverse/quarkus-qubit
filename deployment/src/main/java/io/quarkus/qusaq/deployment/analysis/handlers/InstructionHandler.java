package io.quarkus.qusaq.deployment.analysis.handlers;

import org.objectweb.asm.tree.AbstractInsnNode;

/**
 * Strategy interface for handling bytecode instructions during lambda expression analysis.
 * Specialized handlers recognize and process specific instruction categories (loads, arithmetic, method invocations, etc.).
 *
 * @see io.quarkus.qusaq.deployment.analysis.LambdaBytecodeAnalyzer
 */
public interface InstructionHandler {

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
