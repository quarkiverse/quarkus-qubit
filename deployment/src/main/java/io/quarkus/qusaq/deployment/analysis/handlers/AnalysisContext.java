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
 * <p>Handlers receive this context and can query/modify state as needed.
 */
public class AnalysisContext {

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
     */
    private boolean groupContextMode = false;

    /**
     * List of all methods in the class (for finding nested lambdas).
     * Iteration 7: Added for nested lambda analysis in group aggregations.
     */
    private List<MethodNode> classMethods;

    /**
     * Analyzer function for nested lambdas.
     * Iteration 7: Added for nested lambda analysis in group aggregations.
     */
    private BiFunction<MethodNode, Integer, LambdaExpression> nestedLambdaAnalyzer;

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
        this(method, entityParameterIndex, -1, false);
    }

    /**
     * Creates a new analysis context for bi-entity lambdas (BiQuerySpec).
     *
     * @param method the lambda method being analyzed
     * @param firstEntityParameterIndex the slot index of the first entity parameter
     * @param secondEntityParameterIndex the slot index of the second entity parameter
     */
    public AnalysisContext(MethodNode method, int firstEntityParameterIndex, int secondEntityParameterIndex) {
        this(method, firstEntityParameterIndex, secondEntityParameterIndex, true);
    }

    /**
     * Internal constructor for all cases.
     */
    private AnalysisContext(MethodNode method, int entityParameterIndex,
                            int secondEntityParameterIndex, boolean biEntityMode) {
        this.method = method;
        this.entityParameterIndex = entityParameterIndex;
        this.secondEntityParameterIndex = secondEntityParameterIndex;
        this.biEntityMode = biEntityMode;
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
     * Sets the group context mode.
     * <p>
     * Called during analysis setup for GroupQuerySpec lambdas.
     *
     * @param groupContextMode true to enable group context mode
     */
    public void setGroupContextMode(boolean groupContextMode) {
        this.groupContextMode = groupContextMode;
    }

    /**
     * Sets the list of all methods in the class for nested lambda lookup.
     * <p>
     * Iteration 7: Required for analyzing nested lambdas in group aggregations.
     *
     * @param methods list of all methods in the class
     */
    public void setClassMethods(List<MethodNode> methods) {
        this.classMethods = methods;
    }

    /**
     * Sets the analyzer function for nested lambdas.
     * <p>
     * Iteration 7: Required for analyzing nested lambdas in group aggregations.
     *
     * @param analyzer function that takes (MethodNode, entityParamIndex) and returns analyzed expression
     */
    public void setNestedLambdaAnalyzer(BiFunction<MethodNode, Integer, LambdaExpression> analyzer) {
        this.nestedLambdaAnalyzer = analyzer;
    }

    /**
     * Finds a method in the class by name and descriptor.
     * <p>
     * Iteration 7: Used for locating nested lambda methods.
     *
     * @param name method name
     * @param descriptor method descriptor
     * @return the MethodNode if found, null otherwise
     */
    public MethodNode findMethod(String name, String descriptor) {
        if (classMethods == null) {
            return null;
        }
        for (MethodNode m : classMethods) {
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
     *
     * @param nestedMethod the nested lambda method to analyze
     * @param entityParamIndex the entity parameter slot index
     * @return the analyzed lambda expression, or null if analysis fails
     */
    public LambdaExpression analyzeNestedLambda(MethodNode nestedMethod, int entityParamIndex) {
        if (nestedLambdaAnalyzer == null) {
            return null;
        }
        return nestedLambdaAnalyzer.apply(nestedMethod, entityParamIndex);
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
     */
    public void addArrayElement(LambdaExpression element) {
        if (pendingArrayElements != null) {
            pendingArrayElements.add(element);
        }
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
