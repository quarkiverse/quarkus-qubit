package io.quarkus.qusaq.deployment.analysis.handlers;

import io.quarkus.qusaq.deployment.LambdaExpression;
import io.quarkus.qusaq.deployment.analysis.ControlFlowAnalyzer;
import io.quarkus.qusaq.deployment.analysis.branch.BranchCoordinator;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;

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
     * Current instruction index in the instruction list.
     */
    private int currentInstructionIndex;

    /**
     * Tracks whether any branch instruction has been encountered.
     * Used to detect boolean expressions vs simple field accesses.
     */
    private boolean hasSeenBranch = false;

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
}
