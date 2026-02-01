package io.quarkiverse.qubit.deployment.analysis;

import io.quarkiverse.qubit.deployment.ast.LambdaExpression;
import io.quarkiverse.qubit.deployment.analysis.instruction.*;
import io.quarkiverse.qubit.deployment.common.BytecodeAnalysisException;
import io.quarkiverse.qubit.deployment.metrics.BuildMetricsCollector;
import io.quarkiverse.qubit.deployment.util.DescriptorParser;
import io.quarkus.logging.Log;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static io.quarkiverse.qubit.deployment.ast.LambdaExpression.BinaryOp.and;
import static io.quarkiverse.qubit.deployment.ast.LambdaExpression.BinaryOp.or;
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

    /** Cache to avoid repeatedly parsing the same class bytecode into ClassNode. */
    private static final ConcurrentHashMap<String, ClassNode> CLASS_NODE_CACHE = new ConcurrentHashMap<>();

    /** Clears the ClassNode cache. Used for dev mode hot reload support. */
    public static void clearCache() {
        CLASS_NODE_CACHE.clear();
        Log.debug("LambdaBytecodeAnalyzer ClassNode cache cleared");
    }

    /**
     * Gets or parses a ClassNode from bytecode, using cache to avoid repeated parsing.
     *
     * @param classBytes the class bytecode
     * @param metricsCollector optional metrics collector (may be null)
     * @return the parsed ClassNode (from cache or freshly parsed)
     */
    private static ClassNode getOrParseClassNode(byte[] classBytes, BuildMetricsCollector metricsCollector) {
        // Get class name without full parsing (ClassReader reads constant pool header only)
        ClassReader reader = new ClassReader(classBytes);
        String className = reader.getClassName();

        return CLASS_NODE_CACHE.computeIfAbsent(className, key -> {
            long asmStartTime = System.nanoTime();
            try {
                ClassNode classNode = new ClassNode();
                // Skip debug info (local vars, line numbers) and frames - not needed for lambda analysis
                reader.accept(classNode, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
                return classNode;
            } finally {
                if (metricsCollector != null) {
                    metricsCollector.addAsmParsingTime(System.nanoTime() - asmStartTime);
                    metricsCollector.incrementUniqueClassesLoaded();
                }
            }
        });
    }

    /**
     * Registry holding all instruction handlers for dependency injection.
     */
    private final InstructionHandlerRegistry handlerRegistry;

    /** Creates an analyzer with the default instruction handler registry. */
    public LambdaBytecodeAnalyzer() {
        this(InstructionHandlerRegistry.createDefault());
    }

    /** Creates an analyzer with custom registry for testing. */
    public LambdaBytecodeAnalyzer(InstructionHandlerRegistry handlerRegistry) {
        this.handlerRegistry = Objects.requireNonNull(handlerRegistry,
                "handlerRegistry cannot be null");
    }

    /**
     * Analyzes synthetic lambda bytecode and returns expression AST.
     * @throws BytecodeAnalysisException if bytecode cannot be read or lambda method not found
     */
    public LambdaExpression analyze(byte[] classBytes, String lambdaMethodName, String lambdaDescriptor) {
        return analyze(classBytes, lambdaMethodName, lambdaDescriptor, null);
    }

    /**
     * Analyzes synthetic lambda bytecode with optional metrics collection.
     * @throws BytecodeAnalysisException if bytecode cannot be read or lambda method not found
     */
    public LambdaExpression analyze(byte[] classBytes, String lambdaMethodName, String lambdaDescriptor,
                                    BuildMetricsCollector metricsCollector) {
        return analyze(classBytes, lambdaMethodName, lambdaDescriptor, false, metricsCollector);
    }

    /**
     * Analyzes bi-entity lambda (BiQuerySpec) for join query predicates and projections.
     * @throws BytecodeAnalysisException if bytecode cannot be read or lambda method not found
     */
    public LambdaExpression analyzeBiEntity(byte[] classBytes, String lambdaMethodName, String lambdaDescriptor) {
        return analyzeBiEntity(classBytes, lambdaMethodName, lambdaDescriptor, null);
    }

    /**
     * Analyzes bi-entity lambda with optional metrics collection.
     * @throws BytecodeAnalysisException if bytecode cannot be read or lambda method not found
     */
    public LambdaExpression analyzeBiEntity(byte[] classBytes, String lambdaMethodName, String lambdaDescriptor,
                                            BuildMetricsCollector metricsCollector) {
        return analyze(classBytes, lambdaMethodName, lambdaDescriptor, true, metricsCollector);
    }

    /**
     * Analyzes group lambda (GroupQuerySpec) with aggregation methods (key, count, avg, etc).
     * @throws BytecodeAnalysisException if bytecode cannot be read or lambda method not found
     */
    public LambdaExpression analyzeGroupQuerySpec(byte[] classBytes, String lambdaMethodName, String lambdaDescriptor) {
        return analyzeGroupQuerySpec(classBytes, lambdaMethodName, lambdaDescriptor, null);
    }

    /**
     * Analyzes group lambda with optional metrics collection.
     * @throws BytecodeAnalysisException if bytecode cannot be read or lambda method not found
     */
    public LambdaExpression analyzeGroupQuerySpec(byte[] classBytes, String lambdaMethodName, String lambdaDescriptor,
                                                  BuildMetricsCollector metricsCollector) {
        return analyzeGroupContext(classBytes, lambdaMethodName, lambdaDescriptor, metricsCollector);
    }

    /** Internal: analyzes group context lambda (fail-fast on error). */
    private LambdaExpression analyzeGroupContext(byte[] classBytes, String lambdaMethodName, String lambdaDescriptor,
                                                 BuildMetricsCollector metricsCollector) {
        ClassNode classNode;
        try {
            classNode = getOrParseClassNode(classBytes, metricsCollector);
        } catch (Exception e) {
            throw BytecodeAnalysisException.analysisFailedWithContext(
                    "Failed to read class bytecode for group lambda analysis",
                    null, lambdaMethodName, lambdaDescriptor, e);
        }

        MethodNode lambdaMethod = null;
        for (MethodNode method : classNode.methods) {
            if (method.name.equals(lambdaMethodName) && method.desc.equals(lambdaDescriptor)) {
                lambdaMethod = method;
                break;
            }
        }

        if (lambdaMethod == null) {
            String availableMethods = classNode.methods.stream()
                    .map(m -> m.name + m.desc)
                    .collect(Collectors.joining(", "));
            throw BytecodeAnalysisException.lambdaMethodNotFound(
                    classNode.name + " (group lambda). Available methods: [" + availableMethods + "]",
                    lambdaMethodName, lambdaDescriptor);
        }

        try {
            // For group context, the Group parameter is at slot 0 (first parameter)
            int groupParameterIndex = DescriptorParser.calculateEntityParameterSlotIndex(lambdaDescriptor);
            AnalysisContext.NestedLambdaSupport nestedLambdaSupport = createNestedLambdaSupport(classNode.methods);
            AnalysisContext ctx = new AnalysisContext(lambdaMethod, groupParameterIndex, nestedLambdaSupport);

            long instructionStartTime = System.nanoTime();
            try {
                return processInstructions(ctx);
            } finally {
                if (metricsCollector != null) {
                    metricsCollector.addInstructionAnalysisTime(System.nanoTime() - instructionStartTime);
                }
            }
        } catch (BytecodeAnalysisException e) {
            throw e; // Re-throw our own exceptions
        } catch (Exception e) {
            throw BytecodeAnalysisException.analysisFailedWithContext(
                    String.format("Failed to analyze group lambda method %s%s in class %s",
                            lambdaMethodName, lambdaDescriptor, classNode.name),
                    classNode.name, lambdaMethodName, lambdaDescriptor, e);
        }
    }

    /** Internal: analyzes single or bi-entity lambda (fail-fast on error). */
    private LambdaExpression analyze(byte[] classBytes, String lambdaMethodName,
                                      String lambdaDescriptor, boolean biEntityMode,
                                      BuildMetricsCollector metricsCollector) {
        ClassNode classNode;
        try {
            classNode = getOrParseClassNode(classBytes, metricsCollector);
        } catch (Exception e) {
            throw new BytecodeAnalysisException(
                    "Failed to read class bytecode for lambda analysis: " + e.getMessage(), e);
        }

        MethodNode lambdaMethod = null;
        for (MethodNode method : classNode.methods) {
            if (method.name.equals(lambdaMethodName) && method.desc.equals(lambdaDescriptor)) {
                lambdaMethod = method;
                break;
            }
        }

        if (lambdaMethod == null) {
            String availableMethods = classNode.methods.stream()
                    .map(m -> m.name + m.desc)
                    .collect(Collectors.joining(", "));
            throw BytecodeAnalysisException.lambdaMethodNotFound(
                    classNode.name + ". Available methods: [" + availableMethods + "]",
                    lambdaMethodName, lambdaDescriptor);
        }

        try {
            long instructionStartTime = System.nanoTime();
            try {
                if (biEntityMode) {
                    int[] biEntitySlots = DescriptorParser.calculateBiEntityParameterSlotIndices(lambdaDescriptor);
                    if (biEntitySlots == null) {
                        throw new BytecodeAnalysisException(
                                "Bi-entity mode requires at least 2 parameters in descriptor: " + lambdaDescriptor);
                    }
                    return analyzeMethodInstructions(lambdaMethod, biEntitySlots[0], biEntitySlots[1], classNode.methods);
                } else {
                    int entityParameterIndex = DescriptorParser.calculateEntityParameterSlotIndex(lambdaDescriptor);
                    return analyzeMethodInstructions(lambdaMethod, entityParameterIndex, classNode.methods);
                }
            } finally {
                if (metricsCollector != null) {
                    metricsCollector.addInstructionAnalysisTime(System.nanoTime() - instructionStartTime);
                }
            }
        } catch (BytecodeAnalysisException e) {
            throw e; // Re-throw our own exceptions
        } catch (Exception e) {
            throw BytecodeAnalysisException.analysisFailedWithContext(
                    String.format("Failed to analyze lambda method %s%s in class %s",
                            lambdaMethodName, lambdaDescriptor, classNode.name),
                    classNode.name, lambdaMethodName, lambdaDescriptor, e);
        }
    }

    /** Analyzes single-entity lambda instructions. */
    private LambdaExpression analyzeMethodInstructions(MethodNode method, int entityParameterIndex,
                                                        List<MethodNode> classMethods) {
        AnalysisContext.NestedLambdaSupport nestedLambdaSupport = createNestedLambdaSupport(classMethods);
        AnalysisContext ctx = new AnalysisContext(method, entityParameterIndex, false, nestedLambdaSupport);
        return processInstructions(ctx);
    }

    /** Analyzes bi-entity lambda instructions. */
    private LambdaExpression analyzeMethodInstructions(MethodNode method,
                                                        int firstEntityParameterIndex,
                                                        int secondEntityParameterIndex,
                                                        List<MethodNode> classMethods) {
        AnalysisContext.NestedLambdaSupport nestedLambdaSupport = createNestedLambdaSupport(classMethods);
        AnalysisContext ctx = new AnalysisContext(method, firstEntityParameterIndex, secondEntityParameterIndex, nestedLambdaSupport);
        return processInstructions(ctx);
    }

    /** Creates nested lambda support for subqueries and group aggregations. */
    private AnalysisContext.NestedLambdaSupport createNestedLambdaSupport(List<MethodNode> classMethods) {
        return new AnalysisContext.NestedLambdaSupport(
                classMethods,
                (nestedMethod, entityParamIndex) -> {
                    AnalysisContext nestedCtx = new AnalysisContext(nestedMethod, entityParamIndex);
                    return processInstructions(nestedCtx);
                }
        );
    }

    /** Processes all instructions to build the lambda expression AST. */
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

    /** Delegates instruction to appropriate handler. Returns true to terminate early. */
    private boolean delegateToHandlers(AnalysisContext ctx, AbstractInsnNode insn) {
        return handlerRegistry.handlerFor(insn)
                .map(handler -> handler.handle(insn, ctx))
                .orElseGet(() -> {
                    // No handler accepted this instruction - log and continue
                    if (insn.getOpcode() != -1) { // Ignore pseudo-instructions (labels, line numbers, etc.)
                        Log.tracef("Unhandled instruction: opcode=%d at index=%d",
                                insn.getOpcode(), ctx.getCurrentInstructionIndex());
                    }
                    return false;
                });
    }

    /** Handles branch instructions via BranchCoordinator. */
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

    /** Handles NEW, DUP, ANEWARRAY, AASTORE for GROUP BY multi-value projections. */
    private boolean handleNewAndDup(AnalysisContext ctx, AbstractInsnNode insn, int opcode) {
        if (opcode == NEW) {
            org.objectweb.asm.tree.TypeInsnNode newInsn = (org.objectweb.asm.tree.TypeInsnNode) insn;
            ctx.push(new LambdaExpression.Constant(newInsn.desc, String.class));
            return true;
        } else if (opcode == ANEWARRAY) {
            // Handle array creation for Object[] projections
            org.objectweb.asm.tree.TypeInsnNode arrayInsn = (org.objectweb.asm.tree.TypeInsnNode) insn;
            ctx.startArrayCreation(arrayInsn.desc);
            // Pop the array size from stack (we don't need it)
            ctx.pop();
            // Push a marker for the array reference
            ctx.push(new LambdaExpression.Constant("__array__", String.class));
            return true;
        } else if (opcode == AASTORE && ctx.isInArrayCreation()) {
            // Store value into array
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

    /** Finalizes stack by combining expressions (AND/OR) and completing pending arrays. */
    private LambdaExpression finalizeExpressionStack(AnalysisContext ctx) {
        // If we have a pending array, complete it
        if (ctx.isInArrayCreation()) {
            LambdaExpression.ArrayCreation arrayCreation = ctx.completeArrayCreation();
            if (arrayCreation != null) {
                return arrayCreation;
            }
        }

        Deque<LambdaExpression> stack = ctx.getStack();

        // Convert to list for left-to-right (bottom-to-top) processing
        // Stack stores top at front, so we reverse to get bottom-to-top order
        List<LambdaExpression> exprs = new ArrayList<>(stack);
        Collections.reverse(exprs);

        // Process expressions left-to-right (in evaluation order)
        // Operator selection based on left operand type:
        // - [AND, _] → OR  (pattern: (A && B) || ...)
        // - [OR, _] → AND  (pattern: (A || B) && ...)
        // - [comparison, _] → AND  (default AND chain)
        while (exprs.size() > 1) {
            LambdaExpression left = exprs.remove(0);
            LambdaExpression right = exprs.remove(0);

            boolean leftIsAnd = left instanceof LambdaExpression.BinaryOp binOp
                    && binOp.operator() == LambdaExpression.BinaryOp.Operator.AND;

            LambdaExpression combined;
            if (leftIsAnd) {
                combined = or(left, right);
            } else {
                combined = and(left, right);
            }

            exprs.add(0, combined);
        }
        return exprs.isEmpty() ? null : exprs.getFirst();
    }
}
