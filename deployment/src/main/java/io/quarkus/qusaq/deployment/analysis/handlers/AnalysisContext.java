package io.quarkus.qusaq.deployment.analysis.handlers;

import io.quarkus.qusaq.deployment.LambdaExpression;
import io.quarkus.qusaq.deployment.analysis.ControlFlowAnalyzer;
import io.quarkus.qusaq.deployment.analysis.branch.BranchCoordinator;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

/**
 * Encapsulates all state and context needed during lambda bytecode analysis.
 *
 * <p>This class serves as a parameter object to avoid passing 10+ parameters
 * between instruction handlers. It provides:
 * <ul>
 *   <li>Evaluation stack for building lambda expression AST</li>
 *   <li>Bytecode instruction list and current position</li>
 *   <li>Control flow analysis results (label classifications)</li>
 *   <li>Branch handling coordination</li>
 *   <li>Method metadata (local variables, descriptor)</li>
 *   <li>Utility methods for common operations</li>
 * </ul>
 *
 * <p><b>State Categories (ARCH-006):</b>
 * <ul>
 *   <li><b>Configuration State</b>: Set at construction, immutable thereafter
 *       (groupContextMode, classMethods, nestedLambdaAnalyzer)</li>
 *   <li><b>Processing State</b>: Mutated during instruction analysis
 *       (currentInstructionIndex, hasSeenBranch, pendingArray*)</li>
 * </ul>
 *
 * <p>Handlers receive this context and can query/modify processing state as needed.
 *
 * @see NestedLambdaSupport
 */
public class AnalysisContext {

    // ==================== Nested Lambda Support Configuration ====================

    /**
     * Configuration record for nested lambda analysis support (ARCH-006).
     *
     * <p>Bundles the classMethods list and analyzer function together to ensure
     * they are always set consistently. This configuration is immutable once
     * the context is created.
     *
     * @param classMethods list of all methods in the class (for finding nested lambdas)
     * @param analyzer function that takes (MethodNode, entityParamIndex) and returns analyzed expression
     */
    public record NestedLambdaSupport(
            List<MethodNode> classMethods,
            BiFunction<MethodNode, Integer, LambdaExpression> analyzer) {

        /**
         * Creates nested lambda support with validation.
         *
         * @throws NullPointerException if classMethods or analyzer is null
         */
        public NestedLambdaSupport {
            java.util.Objects.requireNonNull(classMethods, "classMethods cannot be null");
            java.util.Objects.requireNonNull(analyzer, "analyzer cannot be null");
            classMethods = List.copyOf(classMethods);
        }
    }

    // ==================== Core State ====================

    /**
     * Evaluation stack holding lambda expression AST nodes being constructed.
     * Handlers pop operands from this stack and push results.
     */
    private final Deque<LambdaExpression> stack = new ArrayDeque<>();

    /**
     * The bytecode instruction list being analyzed.
     */
    private final InsnList instructions;

    /**
     * Total number of instructions in the method.
     */
    private final int instructionCount;

    /**
     * Classification of label nodes (TRUE/FALSE/INTERMEDIATE destinations).
     */
    private final Map<LabelNode, ControlFlowAnalyzer.LabelClassification> labelClassifications;

    /**
     * Mapping of labels to their boolean evaluation result (true/false).
     */
    private final Map<LabelNode, Boolean> labelToValue;

    /**
     * Coordinator for handling branch instructions (IFEQ, IFNE, IF_ICMP*, etc.).
     */
    private final BranchCoordinator branchCoordinator = new BranchCoordinator();

    /**
     * The method node being analyzed (for accessing local variable info).
     */
    private final MethodNode method;

    /**
     * Index of the entity parameter in the local variable table.
     * Used to distinguish entity field accesses from captured variables.
     * For single-entity lambdas (QuerySpec), this is the only entity parameter.
     * For bi-entity lambdas (BiQuerySpec), this is the FIRST entity parameter.
     */
    private final int entityParameterIndex;

    /**
     * Index of the second entity parameter for bi-entity lambdas (BiQuerySpec).
     * -1 if this is a single-entity lambda.
     */
    private final int secondEntityParameterIndex;

    /**
     * True if this is a bi-entity lambda (BiQuerySpec) with two entity parameters.
     */
    private final boolean biEntityMode;

    /**
     * True if this is a group context lambda (GroupQuerySpec).
     * In group context, the parameter is a Group<T, K> and supports aggregation methods.
     * Iteration 7: Added for GROUP BY query support.
     * ARCH-006: Made final - set at construction, immutable thereafter.
     */
    private final boolean groupContextMode;

    /**
     * Configuration for nested lambda analysis (ARCH-006).
     * Bundles classMethods and nestedLambdaAnalyzer together as immutable configuration.
     * Null when nested lambda analysis is not supported.
     */
    private final NestedLambdaSupport nestedLambdaSupport;

    /**
     * Current instruction index in the instruction list.
     */
    private int currentInstructionIndex;

    /**
     * Tracks whether any branch instruction has been encountered.
     * Used to detect boolean expressions vs simple field accesses.
     */
    private boolean hasSeenBranch = false;

    /**
     * Tracks array creation for Object[] projections.
     * Non-null when we're in the middle of building an array (after ANEWARRAY).
     * Iteration 7: Added for GROUP BY multi-value select projections.
     */
    private String pendingArrayElementType = null;

    /**
     * Collects elements for the pending array.
     * Iteration 7: Added for GROUP BY multi-value select projections.
     */
    private java.util.List<LambdaExpression> pendingArrayElements = null;

    // ==================== Constructors ====================

    /**
     * Creates a new analysis context for the given method (single-entity lambda).
     *
     * @param method the lambda method being analyzed
     * @param entityParameterIndex the slot index of the entity parameter
     */
    public AnalysisContext(MethodNode method, int entityParameterIndex) {
        this(method, entityParameterIndex, -1, false, false, null);
    }

    /**
     * Creates a new analysis context for bi-entity lambdas (BiQuerySpec).
     *
     * @param method the lambda method being analyzed
     * @param firstEntityParameterIndex the slot index of the first entity parameter
     * @param secondEntityParameterIndex the slot index of the second entity parameter
     */
    public AnalysisContext(MethodNode method, int firstEntityParameterIndex, int secondEntityParameterIndex) {
        this(method, firstEntityParameterIndex, secondEntityParameterIndex, true, false, null);
    }

    /**
     * Creates a new analysis context for group context lambdas (GroupQuerySpec).
     * <p>
     * ARCH-006: Constructor-based configuration for group context mode.
     *
     * @param method the lambda method being analyzed
     * @param entityParameterIndex the slot index of the entity parameter
     * @param nestedLambdaSupport configuration for nested lambda analysis
     */
    public AnalysisContext(MethodNode method, int entityParameterIndex, NestedLambdaSupport nestedLambdaSupport) {
        this(method, entityParameterIndex, -1, false, true, nestedLambdaSupport);
    }

    /**
     * Creates a new analysis context with nested lambda support (single-entity lambda).
     * <p>
     * ARCH-006: Constructor-based configuration for nested lambda analysis.
     *
     * @param method the lambda method being analyzed
     * @param entityParameterIndex the slot index of the entity parameter
     * @param nestedLambdaSupport configuration for nested lambda analysis
     * @param groupContextMode true if this is a group context lambda
     */
    public AnalysisContext(MethodNode method, int entityParameterIndex,
                           boolean groupContextMode, NestedLambdaSupport nestedLambdaSupport) {
        this(method, entityParameterIndex, -1, false, groupContextMode, nestedLambdaSupport);
    }

    /**
     * Creates a new analysis context for bi-entity lambdas with nested lambda support.
     * <p>
     * ARCH-006: Constructor-based configuration for bi-entity with nested lambda analysis.
     *
     * @param method the lambda method being analyzed
     * @param firstEntityParameterIndex the slot index of the first entity parameter
     * @param secondEntityParameterIndex the slot index of the second entity parameter
     * @param nestedLambdaSupport configuration for nested lambda analysis
     */
    public AnalysisContext(MethodNode method, int firstEntityParameterIndex,
                           int secondEntityParameterIndex, NestedLambdaSupport nestedLambdaSupport) {
        this(method, firstEntityParameterIndex, secondEntityParameterIndex, true, false, nestedLambdaSupport);
    }

    /**
     * Internal constructor for all cases (ARCH-006).
     * <p>
     * All configuration is set at construction time, making configuration state immutable.
     */
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

    /**
     * Returns the evaluation stack.
     *
     * @return the stack of lambda expressions
     */
    public Deque<LambdaExpression> getStack() {
        return stack;
    }

    /**
     * Pushes a lambda expression onto the evaluation stack.
     *
     * @param expr the expression to push
     */
    public void push(LambdaExpression expr) {
        stack.push(expr);
    }

    /**
     * Pops a lambda expression from the evaluation stack.
     *
     * @return the top expression, or null if stack is empty
     */
    public LambdaExpression pop() {
        return stack.isEmpty() ? null : stack.pop();
    }

    /**
     * Peeks at the top of the evaluation stack without removing it.
     *
     * @return the top expression, or null if stack is empty
     */
    public LambdaExpression peek() {
        return stack.isEmpty() ? null : stack.peek();
    }

    /**
     * Checks if the evaluation stack is empty.
     *
     * @return true if stack is empty
     */
    public boolean isStackEmpty() {
        return stack.isEmpty();
    }

    /**
     * Returns the current size of the evaluation stack.
     *
     * @return stack size
     */
    public int getStackSize() {
        return stack.size();
    }

    // ==================== Instruction Access ====================

    /**
     * Returns the bytecode instruction list.
     *
     * @return the instruction list
     */
    public InsnList getInstructions() {
        return instructions;
    }

    /**
     * Returns the total number of instructions.
     *
     * @return instruction count
     */
    public int getInstructionCount() {
        return instructionCount;
    }

    /**
     * Returns the current instruction index.
     *
     * @return current index
     */
    public int getCurrentInstructionIndex() {
        return currentInstructionIndex;
    }

    /**
     * Sets the current instruction index.
     *
     * @param index the new index
     */
    public void setCurrentInstructionIndex(int index) {
        this.currentInstructionIndex = index;
    }

    // ==================== Control Flow ====================

    /**
     * Returns the label classifications map.
     *
     * @return label classifications
     */
    public Map<LabelNode, ControlFlowAnalyzer.LabelClassification> getLabelClassifications() {
        return labelClassifications;
    }

    /**
     * Returns the label-to-boolean-value map.
     *
     * @return label value mappings
     */
    public Map<LabelNode, Boolean> getLabelToValue() {
        return labelToValue;
    }

    /**
     * Returns the branch coordinator.
     *
     * @return branch coordinator
     */
    public BranchCoordinator getBranchCoordinator() {
        return branchCoordinator;
    }

    /**
     * Checks if any branch instruction has been encountered.
     *
     * @return true if branch seen
     */
    public boolean hasSeenBranch() {
        return hasSeenBranch;
    }

    /**
     * Marks that a branch instruction has been encountered.
     */
    public void markBranchSeen() {
        this.hasSeenBranch = true;
    }

    // ==================== Method Metadata ====================

    /**
     * Returns the method node being analyzed.
     *
     * @return the method node
     */
    public MethodNode getMethod() {
        return method;
    }

    /**
     * Returns the entity parameter index (first entity for bi-entity lambdas).
     *
     * @return entity parameter slot index
     */
    public int getEntityParameterIndex() {
        return entityParameterIndex;
    }

    /**
     * Returns the first entity parameter index (alias for getEntityParameterIndex).
     * <p>
     * For bi-entity lambdas, use this method for clarity.
     *
     * @return first entity parameter slot index
     */
    public int getFirstEntityParameterIndex() {
        return entityParameterIndex;
    }

    /**
     * Returns the second entity parameter index for bi-entity lambdas.
     *
     * @return second entity parameter slot index, or -1 if single-entity
     */
    public int getSecondEntityParameterIndex() {
        return secondEntityParameterIndex;
    }

    /**
     * Returns true if this is a bi-entity lambda (BiQuerySpec) context.
     *
     * @return true for bi-entity, false for single-entity
     */
    public boolean isBiEntityMode() {
        return biEntityMode;
    }

    /**
     * Checks if the given slot index is an entity parameter.
     * <p>
     * In single-entity mode, checks against the single entity parameter.
     * In bi-entity mode, checks against both entity parameters.
     *
     * @param slotIndex the slot index to check
     * @return true if the slot is an entity parameter
     */
    public boolean isEntityParameter(int slotIndex) {
        if (slotIndex == entityParameterIndex) {
            return true;
        }
        return biEntityMode && slotIndex == secondEntityParameterIndex;
    }

    /**
     * Determines which entity (FIRST or SECOND) for a given slot index in bi-entity mode.
     *
     * @param slotIndex the slot index to check
     * @return EntityPosition.FIRST, EntityPosition.SECOND, or null if not an entity parameter
     */
    public LambdaExpression.EntityPosition getEntityPosition(int slotIndex) {
        if (slotIndex == entityParameterIndex) {
            return LambdaExpression.EntityPosition.FIRST;
        }
        if (biEntityMode && slotIndex == secondEntityParameterIndex) {
            return LambdaExpression.EntityPosition.SECOND;
        }
        return null;
    }

    // ==================== Group Context (Iteration 7) ====================

    /**
     * Returns true if this is a group context lambda (GroupQuerySpec).
     * <p>
     * In group context, the parameter is a Group&lt;T, K&gt; and supports
     * aggregation methods like key(), count(), avg(), min(), max(), etc.
     *
     * @return true for group context, false otherwise
     */
    public boolean isGroupContextMode() {
        return groupContextMode;
    }

    /**
     * Returns true if nested lambda analysis is supported.
     * <p>
     * ARCH-006: Replaced setter-based configuration with constructor injection.
     *
     * @return true if nested lambda support is configured
     */
    public boolean hasNestedLambdaSupport() {
        return nestedLambdaSupport != null;
    }

    /**
     * Finds a method in the class by name and descriptor.
     * <p>
     * Iteration 7: Used for locating nested lambda methods.
     * ARCH-006: Updated to use immutable NestedLambdaSupport configuration.
     *
     * @param name method name
     * @param descriptor method descriptor
     * @return the MethodNode if found, null otherwise
     */
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

    /**
     * Analyzes a nested lambda method and returns its expression.
     * <p>
     * Iteration 7: Used for analyzing field extractor lambdas in group aggregations
     * like {@code g.avg((Person p) -> p.salary)}.
     * ARCH-006: Updated to use immutable NestedLambdaSupport configuration.
     *
     * @param nestedMethod the nested lambda method to analyze
     * @param entityParamIndex the entity parameter slot index
     * @return the analyzed lambda expression, or null if analysis fails
     */
    public LambdaExpression analyzeNestedLambda(MethodNode nestedMethod, int entityParamIndex) {
        if (nestedLambdaSupport == null) {
            return null;
        }
        return nestedLambdaSupport.analyzer().apply(nestedMethod, entityParamIndex);
    }

    // ==================== Array Creation Tracking (Iteration 7) ====================

    /**
     * Starts tracking an array creation.
     * <p>
     * Called when ANEWARRAY instruction is encountered.
     *
     * @param elementType the internal name of the array element type (e.g., "java/lang/Object")
     */
    public void startArrayCreation(String elementType) {
        this.pendingArrayElementType = elementType;
        this.pendingArrayElements = new java.util.ArrayList<>();
    }

    /**
     * Returns true if we're currently building an array.
     *
     * @return true if in array creation mode
     */
    public boolean isInArrayCreation() {
        return pendingArrayElementType != null;
    }

    /**
     * Adds an element to the pending array.
     * <p>
     * Called when AASTORE instruction stores a value into the array.
     *
     * @param element the element expression to add
     * @throws IllegalStateException if called when not in array creation mode
     *         (i.e., startArrayCreation was not called first)
     */
    public void addArrayElement(LambdaExpression element) {
        if (pendingArrayElements == null) {
            throw new IllegalStateException(
                    "Cannot add array element: not in array creation mode. "
                            + "Call startArrayCreation() before adding elements.");
        }
        pendingArrayElements.add(element);
    }

    /**
     * Returns the pending array element type.
     *
     * @return the element type internal name, or null if not in array creation
     */
    public String getPendingArrayElementType() {
        return pendingArrayElementType;
    }

    /**
     * Returns the collected array elements.
     *
     * @return list of collected elements, or null if not in array creation
     */
    public java.util.List<LambdaExpression> getPendingArrayElements() {
        return pendingArrayElements;
    }

    /**
     * Completes array creation and returns an ArrayCreation expression.
     * <p>
     * Called when the array is being returned (ARETURN) or otherwise finalized.
     *
     * @return ArrayCreation expression, or null if not in array creation mode
     */
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
}
