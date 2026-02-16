package io.quarkiverse.qubit.deployment.analysis;

import static io.quarkiverse.qubit.deployment.ast.LambdaExpression.BinaryOp.and;
import static io.quarkiverse.qubit.deployment.ast.LambdaExpression.BinaryOp.or;
import static org.objectweb.asm.Opcodes.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.MethodNode;

import io.quarkiverse.qubit.deployment.analysis.instruction.*;
import io.quarkiverse.qubit.deployment.ast.LambdaExpression;
import io.quarkiverse.qubit.deployment.common.BytecodeAnalysisException;
import io.quarkiverse.qubit.deployment.metrics.BuildMetricsCollector;
import io.quarkiverse.qubit.deployment.util.DescriptorParser;
import io.quarkus.logging.Log;

/**
 * Converts synthetic lambda bytecode to expression AST using the Strategy pattern.
 *
 * <p>
 * Architecture: Chain of responsibility where each instruction is offered to handlers
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
 * <p>
 * {@link AnalysisContext} encapsulates analysis state: expression stack, instruction list,
 * label classifications, branch coordinator, and method metadata.
 */
public class LambdaBytecodeAnalyzer {

    /** Clears the ClassNode cache. Used for dev mode hot reload support. */
    public static void clearCache() {
        ClassNodeCache.clear();
    }

    /** Pre-loads a ClassNode into the cache during warm-up to eliminate contention. */
    public static void preloadClassNode(byte[] classBytes, BuildMetricsCollector metricsCollector) {
        ClassNodeCache.preload(classBytes, metricsCollector);
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
     *
     * @throws BytecodeAnalysisException if bytecode cannot be read or lambda method not found
     */
    public LambdaExpression analyze(byte[] classBytes, String lambdaMethodName, String lambdaDescriptor) {
        return analyze(classBytes, lambdaMethodName, lambdaDescriptor, null);
    }

    /**
     * Analyzes synthetic lambda bytecode with optional metrics collection.
     *
     * @throws BytecodeAnalysisException if bytecode cannot be read or lambda method not found
     */
    public LambdaExpression analyze(byte[] classBytes, String lambdaMethodName, String lambdaDescriptor,
            BuildMetricsCollector metricsCollector) {
        return analyze(classBytes, lambdaMethodName, lambdaDescriptor, false, metricsCollector);
    }

    /**
     * Analyzes bi-entity lambda (BiQuerySpec) for join query predicates and projections.
     *
     * @throws BytecodeAnalysisException if bytecode cannot be read or lambda method not found
     */
    public LambdaExpression analyzeBiEntity(byte[] classBytes, String lambdaMethodName, String lambdaDescriptor) {
        return analyzeBiEntity(classBytes, lambdaMethodName, lambdaDescriptor, null);
    }

    /**
     * Analyzes bi-entity lambda with optional metrics collection.
     *
     * @throws BytecodeAnalysisException if bytecode cannot be read or lambda method not found
     */
    public LambdaExpression analyzeBiEntity(byte[] classBytes, String lambdaMethodName, String lambdaDescriptor,
            BuildMetricsCollector metricsCollector) {
        return analyze(classBytes, lambdaMethodName, lambdaDescriptor, true, metricsCollector);
    }

    /**
     * Analyzes group lambda (GroupQuerySpec) with aggregation methods (key, count, avg, etc).
     *
     * @throws BytecodeAnalysisException if bytecode cannot be read or lambda method not found
     */
    public LambdaExpression analyzeGroupQuerySpec(byte[] classBytes, String lambdaMethodName, String lambdaDescriptor) {
        return analyzeGroupQuerySpec(classBytes, lambdaMethodName, lambdaDescriptor, null);
    }

    /**
     * Analyzes group lambda with optional metrics collection.
     *
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
            classNode = ClassNodeCache.getOrParse(classBytes, metricsCollector);
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
            classNode = ClassNodeCache.getOrParse(classBytes, metricsCollector);
        } catch (Exception e) {
            throw BytecodeAnalysisException.analysisFailedWithContext(
                    "Failed to read class bytecode for lambda analysis",
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
        AnalysisContext ctx = new AnalysisContext(method, firstEntityParameterIndex, secondEntityParameterIndex,
                nestedLambdaSupport);
        return processInstructions(ctx);
    }

    /** Creates nested lambda support for subqueries and group aggregations. */
    private AnalysisContext.NestedLambdaSupport createNestedLambdaSupport(List<MethodNode> classMethods) {
        return new AnalysisContext.NestedLambdaSupport(
                classMethods,
                (nestedMethod, entityParamIndex) -> {
                    AnalysisContext nestedCtx = new AnalysisContext(nestedMethod, entityParamIndex);
                    return processInstructions(nestedCtx);
                });
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

            // Check if branch handling requested a skip (ternary pattern detected)
            if (ctx.hasSkipPending()) {
                i = ctx.consumeSkipToIndex() - 1; // -1 because loop will increment
                continue;
            }

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

    /** Handles branch instructions via BranchCoordinator or ternary pattern detection. */
    private boolean handleBranchInstruction(AnalysisContext ctx, AbstractInsnNode insn, int opcode) {
        switch (opcode) {
            case IF_ICMPGT, IF_ICMPGE, IF_ICMPLT, IF_ICMPLE, IF_ICMPEQ, IF_ICMPNE,
                    IF_ACMPEQ, IF_ACMPNE,
                    IFEQ, IFNE, IFLE, IFLT, IFGE, IFGT,
                    IFNULL, IFNONNULL -> {

                // Check if this branch starts a ternary pattern
                TernaryPatternDetector.TernaryPattern ternaryPattern = ctx
                        .getTernaryPatternAt(ctx.getCurrentInstructionIndex());

                if (ternaryPattern != null) {
                    // Handle ternary pattern: condition ? trueValue : falseValue
                    handleTernaryPattern(ctx, ternaryPattern, opcode);
                    return true;
                }

                // Not a ternary - delegate to BranchCoordinator for boolean patterns
                ctx.markBranchSeen();
                ctx.getBranchCoordinator().processBranchInstruction(
                        ctx.getStack(),
                        (JumpInsnNode) insn,
                        ctx.getLabelToValue(),
                        ctx.getLabelClassifications());
                // Skip past ICONST+GOTO boolean value instructions to prevent stack pollution
                skipBooleanValuePattern(ctx);
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    /**
     * Skips ICONST+GOTO boolean value instructions that follow a branch handled by BranchCoordinator.
     * Without this, the ICONST_0/1 and GOTO would be re-processed as regular instructions,
     * polluting the expression stack.
     */
    private void skipBooleanValuePattern(AnalysisContext ctx) {
        InsnList instructions = ctx.getInstructions();
        int idx = ctx.getCurrentInstructionIndex() + 1;

        // Find next real instruction after branch
        while (idx < ctx.getInstructionCount() && instructions.get(idx).getOpcode() == -1)
            idx++;
        if (idx >= ctx.getInstructionCount())
            return;

        int nextOpcode = instructions.get(idx).getOpcode();
        if (nextOpcode != ICONST_0 && nextOpcode != ICONST_1)
            return;

        // Found ICONST after branch — look for GOTO
        int gotoIdx = idx + 1;
        while (gotoIdx < ctx.getInstructionCount() && instructions.get(gotoIdx).getOpcode() == -1)
            gotoIdx++;
        if (gotoIdx >= ctx.getInstructionCount() || instructions.get(gotoIdx).getOpcode() != GOTO)
            return;

        // Found ICONST + GOTO pattern — skip to the GOTO's target (merge point)
        JumpInsnNode gotoInsn = (JumpInsnNode) instructions.get(gotoIdx);
        for (int i = 0; i < ctx.getInstructionCount(); i++) {
            if (instructions.get(i) == gotoInsn.label) {
                ctx.setSkipToIndex(i);
                Log.tracef("Skipping boolean value pattern: ICONST at %d, GOTO at %d, merge at %d", idx, gotoIdx, i);
                return;
            }
        }
    }

    /** Handles a ternary conditional pattern (IF_* / true-branch / GOTO / false-branch / merge). */
    private void handleTernaryPattern(
            AnalysisContext ctx,
            TernaryPatternDetector.TernaryPattern pattern,
            int opcode) {

        Log.debugf("Processing ternary pattern at index %d: true=[%d-%d], false=[%d-%d]",
                pattern.conditionJumpIndex(),
                pattern.trueBranchStart(), pattern.trueBranchEnd(),
                pattern.falseBranchStart(), pattern.falseBranchEnd());

        // Step 1: Extract condition from stack
        // The condition evaluation has already been done by prior instructions.
        // For two-operand comparisons (IF_ICMP*), we need to pop two values and create a comparison.
        // For single-operand comparisons (IFEQ, IFNE, etc.), we pop one value.
        LambdaExpression condition = extractConditionFromStack(ctx, opcode);

        // Step 2: Analyze true branch to get expression
        LambdaExpression trueValue = analyzeBranchInstructions(ctx, pattern.trueBranchStart(), pattern.trueBranchEnd());

        // Step 3: Analyze false branch to get expression
        LambdaExpression falseValue = analyzeBranchInstructions(ctx, pattern.falseBranchStart(), pattern.falseBranchEnd());

        // Step 4: Simplify boolean ternary (cond ? 1 : 0) → cond, (cond ? 0 : 1) → NOT(cond)
        LambdaExpression result = simplifyBooleanTernary(condition, trueValue, falseValue);

        // Step 5: Push result onto stack
        ctx.push(result);

        // Step 6: Set skip index to merge point
        ctx.setSkipToIndex(pattern.mergeIndex());
    }

    /** Simplifies boolean ternary: (cond ? 1 : 0) → cond, (cond ? 0 : 1) → NOT(cond). */
    private static LambdaExpression simplifyBooleanTernary(
            LambdaExpression condition, LambdaExpression trueValue, LambdaExpression falseValue) {
        if (trueValue instanceof LambdaExpression.Constant t
                && falseValue instanceof LambdaExpression.Constant f) {
            int tv = intValue(t);
            int fv = intValue(f);
            if (tv == 1 && fv == 0)
                return condition;
            if (tv == 0 && fv == 1)
                return new LambdaExpression.UnaryOp(LambdaExpression.UnaryOp.Operator.NOT, condition);
        }
        return new LambdaExpression.Conditional(condition, trueValue, falseValue);
    }

    /** Extracts int value from a Constant, or -1 if not an integer constant. */
    private static int intValue(LambdaExpression.Constant c) {
        return c.value() instanceof Integer i ? i : -1;
    }

    /** Extracts condition from stack; IF_* opcodes use inverted branch logic for ternary. */
    private LambdaExpression extractConditionFromStack(AnalysisContext ctx, int opcode) {
        return switch (opcode) {
            // Two-operand comparisons: pop two values and create comparison
            // IF_ICMPGT jumps when a <= b, so the condition for true branch is a > b
            case IF_ICMPGT -> {
                var pair = ctx.popPair();
                yield LambdaExpression.BinaryOp.le(pair.left(), pair.right());
            }
            case IF_ICMPGE -> {
                var pair = ctx.popPair();
                yield LambdaExpression.BinaryOp.lt(pair.left(), pair.right());
            }
            case IF_ICMPLT -> {
                var pair = ctx.popPair();
                yield LambdaExpression.BinaryOp.ge(pair.left(), pair.right());
            }
            case IF_ICMPLE -> {
                var pair = ctx.popPair();
                yield LambdaExpression.BinaryOp.gt(pair.left(), pair.right());
            }
            case IF_ICMPEQ, IF_ACMPEQ -> {
                var pair = ctx.popPair();
                yield LambdaExpression.BinaryOp.ne(pair.left(), pair.right());
            }
            case IF_ICMPNE, IF_ACMPNE -> {
                var pair = ctx.popPair();
                yield LambdaExpression.BinaryOp.eq(pair.left(), pair.right());
            }
            // Single-operand comparisons: compare with zero/null
            // Special case: if preceded by LCMP/DCMPL/DCMPG/FCMPL/FCMPG, pop both original
            // operands directly (the CMP handler leaves them on the stack for the branch handler)
            case IFEQ -> {
                if (previousInstructionIsCmp(ctx)) {
                    AnalysisContext.PopPairResult pair = ctx.popPair();
                    yield LambdaExpression.BinaryOp.ne(pair.left(), pair.right());
                }
                LambdaExpression value = ctx.pop();
                yield LambdaExpression.BinaryOp.ne(value, new LambdaExpression.Constant(0, int.class));
            }
            case IFNE -> {
                if (previousInstructionIsCmp(ctx)) {
                    AnalysisContext.PopPairResult pair = ctx.popPair();
                    yield LambdaExpression.BinaryOp.eq(pair.left(), pair.right());
                }
                LambdaExpression value = ctx.pop();
                yield LambdaExpression.BinaryOp.eq(value, new LambdaExpression.Constant(0, int.class));
            }
            case IFLT -> {
                if (previousInstructionIsCmp(ctx)) {
                    AnalysisContext.PopPairResult pair = ctx.popPair();
                    yield LambdaExpression.BinaryOp.ge(pair.left(), pair.right());
                }
                LambdaExpression value = ctx.pop();
                yield LambdaExpression.BinaryOp.ge(value, new LambdaExpression.Constant(0, int.class));
            }
            case IFLE -> {
                if (previousInstructionIsCmp(ctx)) {
                    AnalysisContext.PopPairResult pair = ctx.popPair();
                    yield LambdaExpression.BinaryOp.gt(pair.left(), pair.right());
                }
                LambdaExpression value = ctx.pop();
                yield LambdaExpression.BinaryOp.gt(value, new LambdaExpression.Constant(0, int.class));
            }
            case IFGT -> {
                if (previousInstructionIsCmp(ctx)) {
                    AnalysisContext.PopPairResult pair = ctx.popPair();
                    yield LambdaExpression.BinaryOp.le(pair.left(), pair.right());
                }
                LambdaExpression value = ctx.pop();
                yield LambdaExpression.BinaryOp.le(value, new LambdaExpression.Constant(0, int.class));
            }
            case IFGE -> {
                if (previousInstructionIsCmp(ctx)) {
                    AnalysisContext.PopPairResult pair = ctx.popPair();
                    yield LambdaExpression.BinaryOp.lt(pair.left(), pair.right());
                }
                LambdaExpression value = ctx.pop();
                yield LambdaExpression.BinaryOp.lt(value, new LambdaExpression.Constant(0, int.class));
            }
            case IFNULL -> {
                // IFNULL jumps when value == null, so true branch condition is value != null
                LambdaExpression value = ctx.pop();
                yield LambdaExpression.BinaryOp.ne(value, new LambdaExpression.Constant(null, Object.class));
            }
            case IFNONNULL -> {
                // IFNONNULL jumps when value != null, so true branch condition is value == null
                LambdaExpression value = ctx.pop();
                yield LambdaExpression.BinaryOp.eq(value, new LambdaExpression.Constant(null, Object.class));
            }
            default -> throw new BytecodeAnalysisException(
                    "Unexpected opcode in ternary condition: " + opcode);
        };
    }

    /** Checks if the previous real instruction was LCMP/DCMPL/DCMPG/FCMPL/FCMPG (comparison that leaves operands on stack). */
    private static boolean previousInstructionIsCmp(AnalysisContext ctx) {
        InsnList instructions = ctx.getInstructions();
        for (int i = ctx.getCurrentInstructionIndex() - 1; i >= 0; i--) {
            int prevOpcode = instructions.get(i).getOpcode();
            if (prevOpcode == -1)
                continue; // skip labels/line numbers
            return prevOpcode == LCMP || prevOpcode == DCMPL || prevOpcode == DCMPG
                    || prevOpcode == FCMPL || prevOpcode == FCMPG;
        }
        return false;
    }

    /** Analyzes a range of instructions to extract a single expression for ternary branches. */
    private LambdaExpression analyzeBranchInstructions(AnalysisContext ctx, int startIndex, int endIndex) {
        // Save current stack state
        Deque<LambdaExpression> savedStack = new java.util.ArrayDeque<>(ctx.getStack());

        // Clear stack for branch analysis
        ctx.getStack().clear();

        // Process instructions in the branch range
        for (int i = startIndex; i <= endIndex; i++) {
            AbstractInsnNode insn = ctx.getInstructions().get(i);
            int opcode = insn.getOpcode();

            ctx.setCurrentInstructionIndex(i);

            // Skip pseudo-instructions (labels, line numbers, frames)
            if (opcode == -1) {
                continue;
            }

            // Try NEW/DUP handling
            boolean handled = handleNewAndDup(ctx, insn, opcode);

            // If not handled, delegate to handlers
            if (!handled) {
                delegateToHandlers(ctx, insn);
            }
        }

        // Get the result from the stack (should be exactly one expression)
        LambdaExpression result = ctx.isStackEmpty() ? null : ctx.pop();

        // Restore original stack
        ctx.getStack().clear();
        ctx.getStack().addAll(savedStack);

        if (result == null) {
            throw new BytecodeAnalysisException(
                    "Failed to extract expression from ternary branch at instructions " + startIndex + "-" + endIndex);
        }

        return result;
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
            LambdaExpression value = ctx.pop(); // pop value
            ctx.pop(); // pop index (we track order implicitly)
            ctx.pop(); // pop array_ref (will be re-pushed by DUP before next element)
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
