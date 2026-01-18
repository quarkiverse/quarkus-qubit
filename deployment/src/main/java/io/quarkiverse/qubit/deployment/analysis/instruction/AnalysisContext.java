package io.quarkiverse.qubit.deployment.analysis.instruction;

import io.quarkiverse.qubit.deployment.ast.LambdaExpression;
import io.quarkiverse.qubit.deployment.analysis.ControlFlowAnalyzer;
import io.quarkiverse.qubit.deployment.analysis.branch.BranchCoordinator;
import io.quarkiverse.qubit.deployment.common.BytecodeAnalysisException;

import org.jspecify.annotations.Nullable;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LocalVariableNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;

/**
 * Parameter object for lambda bytecode analysis, avoiding 10+ method parameters.
 * Contains evaluation stack, instruction state, control flow, and method metadata.
 */
public class AnalysisContext {

    // ==================== Nested Lambda Support Configuration ====================

    /** Configuration for nested lambda analysis (classMethods + analyzer function). */
    public record NestedLambdaSupport(
            List<MethodNode> classMethods,
            BiFunction<MethodNode, Integer, LambdaExpression> analyzer) {

        /** Creates nested lambda support with validation. */
        public NestedLambdaSupport {
            Objects.requireNonNull(classMethods, "classMethods cannot be null");
            Objects.requireNonNull(analyzer, "analyzer cannot be null");
            classMethods = List.copyOf(classMethods);
        }
    }

    // ==================== Core State ====================

    /** Evaluation stack for building lambda expression AST. */
    private final Deque<LambdaExpression> stack = new ArrayDeque<>();

    /** The bytecode instruction list being analyzed. */
    private final InsnList instructions;

    /** Total number of instructions in the method. */
    private final int instructionCount;

    /** Label classifications (TRUE/FALSE/INTERMEDIATE destinations). */
    private final Map<LabelNode, ControlFlowAnalyzer.LabelClassification> labelClassifications;

    /** Mapping of labels to their boolean evaluation result. */
    private final Map<LabelNode, Boolean> labelToValue;

    /** Coordinator for branch instructions (IFEQ, IFNE, IF_ICMP*, etc.). */
    private final BranchCoordinator branchCoordinator = new BranchCoordinator();

    /** The method node being analyzed. */
    private final MethodNode method;

    /** Entity parameter slot index (first entity for bi-entity lambdas). */
    private final int entityParameterIndex;

    /** Second entity parameter index for bi-entity lambdas (-1 if single-entity). */
    private final int secondEntityParameterIndex;

    /** True if this is a bi-entity lambda (BiQuerySpec). */
    private final boolean biEntityMode;

    /** True if this is a group context lambda (GroupQuerySpec). */
    private final boolean groupContextMode;

    /** Configuration for nested lambda analysis (null if not supported). */
    private final NestedLambdaSupport nestedLambdaSupport;

    /** Current instruction index in the instruction list. */
    private int currentInstructionIndex;

    /** Tracks whether any branch instruction has been encountered. */
    private boolean hasSeenBranch = false;

    /** Pending array element type for GROUP BY multi-value projections. */
    private String pendingArrayElementType = null;

    /** Collected elements for pending array creation. */
    private java.util.List<LambdaExpression> pendingArrayElements = null;

    // ==================== Constructors ====================

    /** Creates context for single-entity lambda. */
    public AnalysisContext(MethodNode method, int entityParameterIndex) {
        this(method, entityParameterIndex, -1, false, false, null);
    }

    /** Creates context for bi-entity lambda (BiQuerySpec). */
    public AnalysisContext(MethodNode method, int firstEntityParameterIndex, int secondEntityParameterIndex) {
        this(method, firstEntityParameterIndex, secondEntityParameterIndex, true, false, null);
    }

    /** Creates context for group lambda (GroupQuerySpec). */
    public AnalysisContext(MethodNode method, int entityParameterIndex, NestedLambdaSupport nestedLambdaSupport) {
        this(method, entityParameterIndex, -1, false, true, nestedLambdaSupport);
    }

    /** Creates context for single-entity lambda with nested support. */
    public AnalysisContext(MethodNode method, int entityParameterIndex,
                           boolean groupContextMode, NestedLambdaSupport nestedLambdaSupport) {
        this(method, entityParameterIndex, -1, false, groupContextMode, nestedLambdaSupport);
    }

    /** Creates context for bi-entity lambda with nested support. */
    public AnalysisContext(MethodNode method, int firstEntityParameterIndex,
                           int secondEntityParameterIndex, NestedLambdaSupport nestedLambdaSupport) {
        this(method, firstEntityParameterIndex, secondEntityParameterIndex, true, false, nestedLambdaSupport);
    }

    /** Internal constructor for all cases. */
    private AnalysisContext(MethodNode method, int entityParameterIndex,
                            int secondEntityParameterIndex, boolean biEntityMode,
                            boolean groupContextMode, NestedLambdaSupport nestedLambdaSupport) {
        this.method = method;
        this.entityParameterIndex = entityParameterIndex;
        this.secondEntityParameterIndex = secondEntityParameterIndex;
        this.biEntityMode = biEntityMode;
        this.groupContextMode = groupContextMode;
        this.nestedLambdaSupport = nestedLambdaSupport;
        this.instructions = method.instructions;
        this.instructionCount = instructions.size();

        // Perform control flow analysis
        ControlFlowAnalyzer controlFlowAnalyzer = new ControlFlowAnalyzer();
        this.labelClassifications = controlFlowAnalyzer.classifyLabels(instructions);
        this.labelToValue = controlFlowAnalyzer.traceLabelDestinations(instructions, labelClassifications);
    }

    // ==================== Stack Operations ====================

    /** Returns the evaluation stack. */
    public Deque<LambdaExpression> getStack() {
        return stack;
    }

    /** Pushes expression onto evaluation stack. */
    public void push(LambdaExpression expr) {
        stack.push(expr);
    }

    /** Pops expression from stack. Throws BytecodeAnalysisException if empty. */
    public LambdaExpression pop() {
        if (stack.isEmpty()) {
            throw BytecodeAnalysisException.stackUnderflow("pop", 1, 0);
        }
        return stack.pop();
    }

    /** Peeks at top of stack without removing. Returns null if empty. */
    public LambdaExpression peek() {
        return stack.isEmpty() ? null : stack.peek();
    }

    /** Checks if evaluation stack is empty. */
    public boolean isStackEmpty() {
        return stack.isEmpty();
    }

    /** Returns current stack size. */
    public int getStackSize() {
        return stack.size();
    }

    /** Pops two expressions for binary ops. Returns [left, right]. */
    public PopPairResult popPair() {
        if (stack.size() < 2) {
            throw BytecodeAnalysisException.stackUnderflow("popPair", 2, stack.size());
        }
        LambdaExpression right = stack.pop();
        LambdaExpression left = stack.pop();
        return new PopPairResult(left, right);
    }

    /** Result of popPair(): left (second-to-top), right (top). */
    public record PopPairResult(LambdaExpression left, LambdaExpression right) {}

    /** Pops N expressions in reverse stack order (first was deepest). */
    public List<LambdaExpression> popN(int n) {
        if (stack.size() < n) {
            throw BytecodeAnalysisException.stackUnderflow("popN(" + n + ")", n, stack.size());
        }
        List<LambdaExpression> result = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            result.add(0, stack.pop()); // Insert at beginning to reverse order
        }
        return result;
    }

    /** Discards up to N elements. Returns actual count discarded. */
    public int discardN(int n) {
        int discarded = 0;
        while (discarded < n && !stack.isEmpty()) {
            stack.pop();
            discarded++;
        }
        return discarded;
    }

    // ==================== Instruction Access ====================

    /** Returns the bytecode instruction list. */
    public InsnList getInstructions() {
        return instructions;
    }

    /** Returns total instruction count. */
    public int getInstructionCount() {
        return instructionCount;
    }

    /** Returns current instruction index. */
    public int getCurrentInstructionIndex() {
        return currentInstructionIndex;
    }

    /** Sets current instruction index. */
    public void setCurrentInstructionIndex(int index) {
        this.currentInstructionIndex = index;
    }

    // ==================== Control Flow ====================

    /** Returns label classifications map. */
    public Map<LabelNode, ControlFlowAnalyzer.LabelClassification> getLabelClassifications() {
        return labelClassifications;
    }

    /** Returns label-to-boolean-value map. */
    public Map<LabelNode, Boolean> getLabelToValue() {
        return labelToValue;
    }

    /** Returns branch coordinator. */
    public BranchCoordinator getBranchCoordinator() {
        return branchCoordinator;
    }

    /** Checks if any branch instruction has been encountered. */
    public boolean hasSeenBranch() {
        return hasSeenBranch;
    }

    /** Marks that a branch instruction has been encountered. */
    public void markBranchSeen() {
        this.hasSeenBranch = true;
    }

    // ==================== Method Metadata ====================

    /** Returns the method node being analyzed. */
    public MethodNode getMethod() {
        return method;
    }

    /** Returns entity parameter index (first entity for bi-entity). */
    public int getEntityParameterIndex() {
        return entityParameterIndex;
    }

    /** Returns first entity parameter index (alias for bi-entity clarity). */
    public int getFirstEntityParameterIndex() {
        return entityParameterIndex;
    }

    /** Returns second entity parameter index (-1 if single-entity). */
    public int getSecondEntityParameterIndex() {
        return secondEntityParameterIndex;
    }

    /** Returns true if this is a bi-entity lambda (BiQuerySpec). */
    public boolean isBiEntityMode() {
        return biEntityMode;
    }

    /** Checks if slot index is an entity parameter. */
    public boolean isEntityParameter(int slotIndex) {
        if (slotIndex == entityParameterIndex) {
            return true;
        }
        return biEntityMode && slotIndex == secondEntityParameterIndex;
    }

    /** Returns FIRST, SECOND, or null for slot index in bi-entity mode. */
    public LambdaExpression.EntityPosition getEntityPosition(int slotIndex) {
        if (slotIndex == entityParameterIndex) {
            return LambdaExpression.EntityPosition.FIRST;
        }
        if (biEntityMode && slotIndex == secondEntityParameterIndex) {
            return LambdaExpression.EntityPosition.SECOND;
        }
        return null;
    }

    // ==================== Group Context ====================

    /** Returns true if this is a group context lambda (GroupQuerySpec). */
    public boolean isGroupContextMode() {
        return groupContextMode;
    }

    /** Returns true if nested lambda analysis is supported. */
    public boolean hasNestedLambdaSupport() {
        return nestedLambdaSupport != null;
    }

    /** Finds method by name and descriptor. Returns null if not found. */
    public MethodNode findMethod(String name, String descriptor) {
        if (nestedLambdaSupport == null) {
            return null;
        }
        for (MethodNode m : nestedLambdaSupport.classMethods()) {
            if (m.name.equals(name) && m.desc.equals(descriptor)) {
                return m;
            }
        }
        return null;
    }

    /** Analyzes nested lambda method. Returns null if analysis fails. */
    public LambdaExpression analyzeNestedLambda(MethodNode nestedMethod, int entityParamIndex) {
        if (nestedLambdaSupport == null) {
            return null;
        }
        return nestedLambdaSupport.analyzer().apply(nestedMethod, entityParamIndex);
    }

    // ==================== Array Creation Tracking ====================

    /** Starts tracking array creation (called on ANEWARRAY). */
    public void startArrayCreation(String elementType) {
        this.pendingArrayElementType = elementType;
        this.pendingArrayElements = new ArrayList<>();
    }

    /** Returns true if currently building an array. */
    public boolean isInArrayCreation() {
        return pendingArrayElementType != null;
    }

    /** Adds element to pending array (on AASTORE). Throws if not in array mode. */
    public void addArrayElement(LambdaExpression element) {
        if (pendingArrayElements == null) {
            throw new IllegalStateException(
                    "Cannot add array element: not in array creation mode. "
                            + "Call startArrayCreation() before adding elements.");
        }
        pendingArrayElements.add(element);
    }

    /** Returns pending array element type (null if not in array creation). */
    public String getPendingArrayElementType() {
        return pendingArrayElementType;
    }

    /** Returns collected array elements (null if not in array creation). */
    public java.util.List<LambdaExpression> getPendingArrayElements() {
        return pendingArrayElements;
    }

    /** Completes array creation. Returns null if not in array mode. */
    public LambdaExpression.ArrayCreation completeArrayCreation() {
        if (pendingArrayElementType == null || pendingArrayElements == null) {
            return null;
        }
        Class<?> arrayType = Object[].class; // Default to Object[]
        LambdaExpression.ArrayCreation result = new LambdaExpression.ArrayCreation(
                pendingArrayElementType, pendingArrayElements, arrayType);
        pendingArrayElementType = null;
        pendingArrayElements = null;
        return result;
    }

    // ==================== Variable Name Lookup ====================

    /** Looks up variable name from debug info (null if unavailable). */
    public @Nullable String getVariableNameForSlot(int slotIndex) {
        if (method.localVariables == null) {
            return null;
        }

        for (LocalVariableNode localVar : method.localVariables) {
            if (localVar.index == slotIndex) {
                return localVar.name;
            }
        }

        return null;
    }
}
