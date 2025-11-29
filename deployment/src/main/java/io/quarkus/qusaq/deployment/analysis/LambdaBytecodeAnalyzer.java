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
import java.util.Objects;

import static io.quarkus.qusaq.deployment.LambdaExpression.BinaryOp.and;
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
     * Registry holding all instruction handlers for dependency injection (ARCH-005).
     */
    private final InstructionHandlerRegistry handlerRegistry;

    /**
     * Creates an analyzer with the default instruction handler registry.
     *
     * <p>This is the standard constructor for production use.
     */
    public LambdaBytecodeAnalyzer() {
        this(InstructionHandlerRegistry.createDefault());
    }

    /**
     * Creates an analyzer with a custom instruction handler registry.
     *
     * <p>This constructor enables testability by allowing injection of mock
     * or custom handler implementations.
     *
     * @param handlerRegistry the registry containing instruction handlers
     * @throws NullPointerException if handlerRegistry is null
     */
    public LambdaBytecodeAnalyzer(InstructionHandlerRegistry handlerRegistry) {
        this.handlerRegistry = Objects.requireNonNull(handlerRegistry,
                "handlerRegistry cannot be null");
    }

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
     * Analyzes synthetic lambda bytecode for group query lambdas (GroupQuerySpec).
     * <p>
     * Used for group query operations that take a Group parameter.
     * For example: {@code (Group<Person, String> g) -> g.count()}
     * <p>
     * Group lambdas support:
     * <ul>
     *   <li>{@code g.key()} - returns grouping key</li>
     *   <li>{@code g.count()} - returns count of entities in group</li>
     *   <li>{@code g.countDistinct(p -> p.field)} - returns distinct count</li>
     *   <li>{@code g.avg(p -> p.field)} - returns average of field values</li>
     *   <li>{@code g.min(p -> p.field)} - returns minimum value</li>
     *   <li>{@code g.max(p -> p.field)} - returns maximum value</li>
     *   <li>{@code g.sumInteger/sumLong/sumDouble(p -> p.field)} - returns sum</li>
     * </ul>
     *
     * @param classBytes bytecode of the lambda class
     * @param lambdaMethodName lambda method name (usually "lambda$methodName$N")
     * @param lambdaDescriptor method descriptor (e.g., "(LGroup;)J")
     * @return lambda expression AST with Group nodes, or null if analysis fails
     */
    public LambdaExpression analyzeGroupQuerySpec(byte[] classBytes, String lambdaMethodName, String lambdaDescriptor) {
        return analyzeGroupContext(classBytes, lambdaMethodName, lambdaDescriptor);
    }

    /**
     * Internal analyze method for group context lambdas (GroupQuerySpec).
     *
     * @param classBytes bytecode of the lambda class
     * @param lambdaMethodName lambda method name
     * @param lambdaDescriptor method descriptor
     * @return lambda expression AST with Group nodes, or null if analysis fails
     */
    private LambdaExpression analyzeGroupContext(byte[] classBytes, String lambdaMethodName, String lambdaDescriptor) {
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
                log.warnf("Could not find group lambda method %s%s in class", lambdaMethodName, lambdaDescriptor);
                return null;
            }

            // For group context, the Group parameter is at slot 0 (first parameter)
            // ARCH-006: Use constructor-based configuration for immutable state
            int groupParameterIndex = DescriptorParser.calculateEntityParameterSlotIndex(lambdaDescriptor);
            AnalysisContext.NestedLambdaSupport nestedLambdaSupport = createNestedLambdaSupport(classNode.methods);
            AnalysisContext ctx = new AnalysisContext(lambdaMethod, groupParameterIndex, nestedLambdaSupport);

            return processInstructions(ctx);

        } catch (Exception e) {
            log.warnf(e, "Failed to analyze group lambda method %s", lambdaMethodName);
            return null;
        }
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
                return analyzeMethodInstructions(lambdaMethod, biEntitySlots[0], biEntitySlots[1], classNode.methods);
            } else {
                int entityParameterIndex = DescriptorParser.calculateEntityParameterSlotIndex(lambdaDescriptor);
                return analyzeMethodInstructions(lambdaMethod, entityParameterIndex, classNode.methods);
            }

        } catch (Exception e) {
            log.warnf(e, "Failed to analyze lambda method %s", lambdaMethodName);
            return null;
        }
    }

    /**
     * Analyzes method instructions to build lambda expression AST (single-entity).
     * <p>
     * ARCH-006: Uses constructor-based configuration for immutable nested lambda support.
     *
     * @param method lambda method to analyze
     * @param entityParameterIndex local variable slot index of the entity parameter
     * @param classMethods list of all methods in the class (for nested lambda analysis)
     * @return lambda expression AST
     */
    private LambdaExpression analyzeMethodInstructions(MethodNode method, int entityParameterIndex,
                                                        List<MethodNode> classMethods) {
        AnalysisContext.NestedLambdaSupport nestedLambdaSupport = createNestedLambdaSupport(classMethods);
        AnalysisContext ctx = new AnalysisContext(method, entityParameterIndex, false, nestedLambdaSupport);
        return processInstructions(ctx);
    }

    /**
     * Analyzes method instructions to build lambda expression AST (bi-entity).
     * <p>
     * Used for BiQuerySpec lambdas in join queries with two entity parameters.
     * ARCH-006: Uses constructor-based configuration for immutable nested lambda support.
     *
     * @param method lambda method to analyze
     * @param firstEntityParameterIndex local variable slot index of the first entity parameter
     * @param secondEntityParameterIndex local variable slot index of the second entity parameter
     * @param classMethods list of all methods in the class (for nested lambda analysis)
     * @return lambda expression AST with BiEntity nodes
     */
    private LambdaExpression analyzeMethodInstructions(MethodNode method,
                                                        int firstEntityParameterIndex,
                                                        int secondEntityParameterIndex,
                                                        List<MethodNode> classMethods) {
        AnalysisContext.NestedLambdaSupport nestedLambdaSupport = createNestedLambdaSupport(classMethods);
        AnalysisContext ctx = new AnalysisContext(method, firstEntityParameterIndex, secondEntityParameterIndex, nestedLambdaSupport);
        return processInstructions(ctx);
    }

    /**
     * Creates nested lambda support configuration for the given class methods.
     * <p>
     * ARCH-006: Factory method to create immutable NestedLambdaSupport configuration.
     * This enables analysis of nested lambdas used in subqueries and group aggregations.
     * For example: {@code subquery(Person.class).avg(q -> q.salary)}
     *
     * @param classMethods list of all methods in the class
     * @return NestedLambdaSupport configuration
     */
    private AnalysisContext.NestedLambdaSupport createNestedLambdaSupport(List<MethodNode> classMethods) {
        return new AnalysisContext.NestedLambdaSupport(
                classMethods,
                (nestedMethod, entityParamIndex) -> {
                    AnalysisContext nestedCtx = new AnalysisContext(nestedMethod, entityParamIndex);
                    return processInstructions(nestedCtx);
                }
        );
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

        return finalizeExpressionStack(ctx);
    }

    /**
     * Delegates instruction processing to the appropriate handler.
     *
     * @param ctx analysis context
     * @param insn instruction to process
     * @return true if analysis should terminate early
     */
    private boolean delegateToHandlers(AnalysisContext ctx, AbstractInsnNode insn) {
        for (InstructionHandler handler : handlerRegistry.handlers()) {
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
     * Handles NEW, DUP, ANEWARRAY, and AASTORE instructions inline.
     * <p>
     * Iteration 7: Extended to support Object[] array creation for GROUP BY multi-value projections.
     *
     * @param ctx analysis context
     * @param insn instruction
     * @param opcode opcode
     * @return true if instruction was handled
     */
    private boolean handleNewAndDup(AnalysisContext ctx, AbstractInsnNode insn, int opcode) {
        if (opcode == NEW) {
            org.objectweb.asm.tree.TypeInsnNode newInsn = (org.objectweb.asm.tree.TypeInsnNode) insn;
            ctx.push(new LambdaExpression.Constant(newInsn.desc, String.class));
            return true;
        } else if (opcode == ANEWARRAY) {
            // Iteration 7: Handle array creation for Object[] projections
            org.objectweb.asm.tree.TypeInsnNode arrayInsn = (org.objectweb.asm.tree.TypeInsnNode) insn;
            ctx.startArrayCreation(arrayInsn.desc);
            // Pop the array size from stack (we don't need it)
            ctx.pop();
            // Push a marker for the array reference
            ctx.push(new LambdaExpression.Constant("__array__", String.class));
            return true;
        } else if (opcode == AASTORE && ctx.isInArrayCreation()) {
            // Iteration 7: Store value into array
            // Stack: [array_ref, index, value] (value on top)
            LambdaExpression value = ctx.pop();  // pop value
            ctx.pop();  // pop index (we track order implicitly)
            ctx.pop();  // pop array_ref (will be re-pushed by DUP before next element)
            ctx.addArrayElement(value);
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
     * <p>
     * Iteration 7: Extended to check for pending array creation and finalize it.
     *
     * @param ctx analysis context for array completion
     * @return final combined expression
     */
    private LambdaExpression finalizeExpressionStack(AnalysisContext ctx) {
        // Iteration 7: If we have a pending array, complete it
        if (ctx.isInArrayCreation()) {
            LambdaExpression.ArrayCreation arrayCreation = ctx.completeArrayCreation();
            if (arrayCreation != null) {
                return arrayCreation;
            }
        }

        Deque<LambdaExpression> stack = ctx.getStack();
        while (stack.size() > 1) {
            LambdaExpression right = stack.pop();
            LambdaExpression left = stack.pop();
            stack.push(and(left, right));
        }
        return stack.isEmpty() ? null : stack.peek();
    }
}
