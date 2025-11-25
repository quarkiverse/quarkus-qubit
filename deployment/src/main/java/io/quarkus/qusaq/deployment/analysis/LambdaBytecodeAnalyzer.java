package io.quarkus.qusaq.deployment.analysis;

import io.quarkus.qusaq.deployment.LambdaExpression;
import io.quarkus.qusaq.deployment.analysis.handlers.*;
import io.quarkus.qusaq.deployment.util.DescriptorParser;
import org.jboss.logging.Logger;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.Deque;
import java.util.List;

import static io.quarkus.qusaq.deployment.LambdaExpression.BinaryOp.Operator.AND;
import static org.objectweb.asm.Opcodes.*;

/**
 * Converts synthetic lambda bytecode to expression AST using the Strategy pattern.
 *
 * <p>Architecture: Chain of responsibility where each instruction is offered to handlers
 * in sequence until one accepts it via {@code canHandle()}.
 *
 * <pre>
 * LambdaBytecodeAnalyzer (Coordinator)
 *   ├── LoadInstructionHandler (ALOAD, ILOAD, GETFIELD, etc.)
 *   ├── ConstantInstructionHandler (LDC, ICONST, BIPUSH, etc.)
 *   ├── ArithmeticInstructionHandler (IADD, ISUB, DCMPL, etc.)
 *   ├── TypeConversionHandler (I2L, L2F, D2I, etc.)
 *   ├── InvokeDynamicHandler (INVOKEDYNAMIC - Java 9+ string concatenation)
 *   ├── MethodInvocationHandler (INVOKEVIRTUAL, INVOKESTATIC, INVOKESPECIAL)
 *   └── BranchCoordinator (IF_ICMP*, IFEQ, IFNE, IFNULL, etc.)
 * </pre>
 *
 * <p>{@link AnalysisContext} encapsulates analysis state: expression stack, instruction list,
 * label classifications, branch coordinator, and method metadata.
 */
public class LambdaBytecodeAnalyzer {

    private static final Logger log = Logger.getLogger(LambdaBytecodeAnalyzer.class);

    /**
     * Ordered list of instruction handlers.
     * Handlers are checked in order until one accepts the instruction.
     */
    private final List<InstructionHandler> handlers = List.of(
        new LoadInstructionHandler(),
        new ConstantInstructionHandler(),
        new ArithmeticInstructionHandler(),
        new TypeConversionHandler(),
        new InvokeDynamicHandler(),  // Java 9+ string concatenation
        new MethodInvocationHandler()
    );

    /**
     * Analyzes synthetic lambda bytecode and returns expression AST.
     *
     * @param classBytes bytecode of the lambda class
     * @param lambdaMethodName lambda method name (usually "lambda$methodName$N")
     * @param lambdaDescriptor method descriptor (e.g., "(LPerson;)Z")
     * @return lambda expression AST, or null if analysis fails
     */
    public LambdaExpression analyze(byte[] classBytes, String lambdaMethodName, String lambdaDescriptor) {
        return analyze(classBytes, lambdaMethodName, lambdaDescriptor, false);
    }

    /**
     * Analyzes synthetic lambda bytecode for bi-entity lambdas (BiQuerySpec).
     * <p>
     * Used for join query predicates and projections that take two entity parameters.
     * For example: {@code (Person p, Phone ph) -> ph.type.equals("mobile")}
     *
     * @param classBytes bytecode of the lambda class
     * @param lambdaMethodName lambda method name (usually "lambda$methodName$N")
     * @param lambdaDescriptor method descriptor (e.g., "(LPerson;LPhone;)Z")
     * @return lambda expression AST with BiEntity nodes, or null if analysis fails
     */
    public LambdaExpression analyzeBiEntity(byte[] classBytes, String lambdaMethodName, String lambdaDescriptor) {
        return analyze(classBytes, lambdaMethodName, lambdaDescriptor, true);
    }

    /**
     * Internal analyze method that supports both single-entity and bi-entity lambdas.
     *
     * @param classBytes bytecode of the lambda class
     * @param lambdaMethodName lambda method name
     * @param lambdaDescriptor method descriptor
     * @param biEntityMode true for bi-entity lambdas (BiQuerySpec)
     * @return lambda expression AST, or null if analysis fails
     */
    private LambdaExpression analyze(byte[] classBytes, String lambdaMethodName,
                                      String lambdaDescriptor, boolean biEntityMode) {
        try {
            ClassReader reader = new ClassReader(classBytes);
            ClassNode classNode = new ClassNode();
            reader.accept(classNode, 0);

            MethodNode lambdaMethod = null;
            for (MethodNode method : classNode.methods) {
                if (method.name.equals(lambdaMethodName) && method.desc.equals(lambdaDescriptor)) {
                    lambdaMethod = method;
                    break;
                }
            }

            if (lambdaMethod == null) {
                log.warnf("Could not find lambda method %s%s in class", lambdaMethodName, lambdaDescriptor);
                return null;
            }

            if (biEntityMode) {
                int[] biEntitySlots = DescriptorParser.calculateBiEntityParameterSlotIndices(lambdaDescriptor);
                if (biEntitySlots == null) {
                    log.warnf("Bi-entity mode requires at least 2 parameters in descriptor: %s", lambdaDescriptor);
                    return null;
                }
                return analyzeMethodInstructions(lambdaMethod, biEntitySlots[0], biEntitySlots[1]);
            } else {
                int entityParameterIndex = DescriptorParser.calculateEntityParameterSlotIndex(lambdaDescriptor);
                return analyzeMethodInstructions(lambdaMethod, entityParameterIndex);
            }

        } catch (Exception e) {
            log.warnf(e, "Failed to analyze lambda method %s", lambdaMethodName);
            return null;
        }
    }

    /**
     * Analyzes method instructions to build lambda expression AST (single-entity).
     *
     * @param method lambda method to analyze
     * @param entityParameterIndex local variable slot index of the entity parameter
     * @return lambda expression AST
     */
    private LambdaExpression analyzeMethodInstructions(MethodNode method, int entityParameterIndex) {
        AnalysisContext ctx = new AnalysisContext(method, entityParameterIndex);
        return processInstructions(ctx);
    }

    /**
     * Analyzes method instructions to build lambda expression AST (bi-entity).
     * <p>
     * Used for BiQuerySpec lambdas in join queries with two entity parameters.
     *
     * @param method lambda method to analyze
     * @param firstEntityParameterIndex local variable slot index of the first entity parameter
     * @param secondEntityParameterIndex local variable slot index of the second entity parameter
     * @return lambda expression AST with BiEntity nodes
     */
    private LambdaExpression analyzeMethodInstructions(MethodNode method,
                                                        int firstEntityParameterIndex,
                                                        int secondEntityParameterIndex) {
        AnalysisContext ctx = new AnalysisContext(method, firstEntityParameterIndex, secondEntityParameterIndex);
        return processInstructions(ctx);
    }

    /**
     * Processes all instructions in the method to build the lambda expression AST.
     *
     * @param ctx analysis context (single or bi-entity)
     * @return lambda expression AST
     */
    private LambdaExpression processInstructions(AnalysisContext ctx) {
        // Process each instruction
        for (int i = 0; i < ctx.getInstructionCount(); i++) {
            AbstractInsnNode insn = ctx.getInstructions().get(i);
            int opcode = insn.getOpcode();

            ctx.setCurrentInstructionIndex(i);

            // Try branch instructions first (highest priority)
            boolean handled = handleBranchInstruction(ctx, insn, opcode);

            // If not a branch, try NEW/DUP instructions (inline handling for simplicity)
            if (!handled) {
                handled = handleNewAndDup(ctx, insn, opcode);
            }

            // If still not handled, delegate to handlers
            if (!handled) {
                boolean shouldTerminate = delegateToHandlers(ctx, insn);
                if (shouldTerminate) {
                    // Handler signaled early termination (e.g., found final boolean result)
                    return ctx.peek();
                }
            }
        }

        return finalizeExpressionStack(ctx.getStack());
    }

    /**
     * Delegates instruction processing to the appropriate handler.
     *
     * @param ctx analysis context
     * @param insn instruction to process
     * @return true if analysis should terminate early
     */
    private boolean delegateToHandlers(AnalysisContext ctx, AbstractInsnNode insn) {
        for (InstructionHandler handler : handlers) {
            if (handler.canHandle(insn)) {
                return handler.handle(insn, ctx); // Instruction handled, continue to next instruction
            }
        }

        // No handler accepted this instruction - log and continue
        if (insn.getOpcode() != -1) { // Ignore pseudo-instructions (labels, line numbers, etc.)
            log.tracef("Unhandled instruction: opcode=%d at index=%d",
                       insn.getOpcode(), ctx.getCurrentInstructionIndex());
        }

        return false;
    }

    /**
     * Handles branch instructions via {@link io.quarkus.qusaq.deployment.analysis.branch.BranchCoordinator}.
     *
     * @param ctx analysis context
     * @param insn instruction
     * @param opcode opcode
     * @return true if instruction was a branch instruction
     */
    private boolean handleBranchInstruction(AnalysisContext ctx, AbstractInsnNode insn, int opcode) {
        switch (opcode) {
            case IF_ICMPGT, IF_ICMPGE, IF_ICMPLT, IF_ICMPLE, IF_ICMPEQ, IF_ICMPNE,
                 IF_ACMPEQ, IF_ACMPNE,
                 IFEQ, IFNE, IFLE, IFLT, IFGE, IFGT,
                 IFNULL, IFNONNULL -> {
                ctx.markBranchSeen();
                ctx.getBranchCoordinator().processBranchInstruction(
                        ctx.getStack(),
                        (JumpInsnNode) insn,
                        ctx.getLabelToValue(),
                        ctx.getLabelClassifications()
                );
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    /**
     * Handles NEW and DUP instructions inline (object creation markers for constructor calls).
     *
     * @param ctx analysis context
     * @param insn instruction
     * @param opcode opcode
     * @return true if instruction was NEW or DUP
     */
    private boolean handleNewAndDup(AnalysisContext ctx, AbstractInsnNode insn, int opcode) {
        if (opcode == NEW) {
            org.objectweb.asm.tree.TypeInsnNode newInsn = (org.objectweb.asm.tree.TypeInsnNode) insn;
            ctx.push(new LambdaExpression.Constant(newInsn.desc, String.class));
            return true;
        } else if (opcode == DUP && !ctx.isStackEmpty()) {
            LambdaExpression top = ctx.peek();
            ctx.push(top);
            return true;
        }
        return false;
    }

    /**
     * Finalizes expression stack by combining remaining expressions with AND.
     *
     * @param stack expression stack
     * @return final combined expression
     */
    private LambdaExpression finalizeExpressionStack(Deque<LambdaExpression> stack) {
        while (stack.size() > 1) {
            LambdaExpression right = stack.pop();
            LambdaExpression left = stack.pop();
            stack.push(new LambdaExpression.BinaryOp(left, AND, right));
        }
        return stack.isEmpty() ? null : stack.peek();
    }
}
