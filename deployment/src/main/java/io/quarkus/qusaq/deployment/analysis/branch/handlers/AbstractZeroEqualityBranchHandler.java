package io.quarkus.qusaq.deployment.analysis.branch.handlers;

import io.quarkus.qusaq.deployment.LambdaExpression;
import io.quarkus.qusaq.deployment.analysis.BytecodeValidator;
import io.quarkus.qusaq.deployment.analysis.branch.BranchHandler;
import io.quarkus.qusaq.deployment.analysis.branch.BranchState;
import org.jboss.logging.Logger;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;

import java.util.Deque;
import java.util.Map;
import java.util.Optional;

import static io.quarkus.qusaq.deployment.LambdaExpression.BinaryOp.Operator;
import static io.quarkus.qusaq.deployment.LambdaExpression.BinaryOp.Operator.AND;
import static io.quarkus.qusaq.deployment.LambdaExpression.BinaryOp.Operator.OR;

/**
 * Abstract base class for IFEQ/IFNE instruction handlers.
 *
 * <p>Provides shared logic for handling boolean field patterns with AND/OR combination,
 * including expression restructuring for correct operator precedence.
 *
 * <p>Subclasses must implement:
 * <ul>
 *   <li>{@link #createBooleanEvaluationExpression} - instruction-specific boolean expression creation</li>
 *   <li>{@link #getInstructionName} - for logging purposes</li>
 * </ul>
 */
public abstract class AbstractZeroEqualityBranchHandler implements BranchHandler {

    protected final Logger log;

    protected AbstractZeroEqualityBranchHandler(Logger log) {
        this.log = log;
    }

    /**
     * Creates a boolean evaluation expression based on instruction semantics.
     *
     * @param fieldAccess the field access expression
     * @param jumpTarget true if jump targets true branch, false otherwise
     * @return boolean evaluation expression
     */
    protected abstract LambdaExpression createBooleanEvaluationExpression(
            LambdaExpression fieldAccess,
            Boolean jumpTarget);

    /**
     * Returns the instruction name for logging.
     *
     * @return instruction name (e.g., "IFEQ", "IFNE")
     */
    protected abstract String getInstructionName();

    /**
     * Handles boolean field pattern with complex AND/OR combination logic.
     *
     * <p>This method is shared by both IFEQ and IFNE handlers. The only difference
     * is in the {@link #createBooleanEvaluationExpression} logic.
     *
     * @param stack expression stack
     * @param jumpInsn jump instruction
     * @param labelToValue mapping of labels to boolean values
     * @param state current branch state
     * @return new branch state after processing
     */
    protected BranchState handleBooleanFieldPattern(
            Deque<LambdaExpression> stack,
            JumpInsnNode jumpInsn,
            Map<LabelNode, Boolean> labelToValue,
            BranchState state) {

        LambdaExpression fieldAccess = BytecodeValidator.popSafe(stack, getInstructionName() + "-Boolean");
        Boolean jumpTarget = labelToValue.get(jumpInsn.label);

        // Create boolean evaluation expression (instruction-specific logic)
        LambdaExpression boolExpr = createBooleanEvaluationExpression(fieldAccess, jumpTarget);

        log.tracef("%s boolean field: jumpTarget=%s, boolExpr=%s", getInstructionName(), jumpTarget, boolExpr);

        // Get previous jump target BEFORE processing current branch (needed for afterCombination)
        Optional<Boolean> previousJumpTarget = state.getLastJumpTarget();

        // Process branch instruction atomically (unified operator determination + state transition)
        LambdaExpression stackTop = stack.isEmpty() ? null : stack.peek();
        BranchState.BranchResult result = state.processBranch(jumpTarget != null && jumpTarget, true, stackTop);
        Operator combineOp = result.combineOperator();
        BranchState newState = result.newState();

        if (combineOp != null && !stack.isEmpty() && stack.peek() instanceof LambdaExpression.BinaryOp) {
            // Combine with previous condition
            LambdaExpression previousCondition = BytecodeValidator.popSafe(stack, getInstructionName() + "-Combine");
            LambdaExpression combined = new LambdaExpression.BinaryOp(
                    previousCondition, combineOp, boolExpr);

            // RESTRUCTURING: Fix precedence when combining ((a OR b) AND c) OR d
            // Should be: (a OR b) AND (c OR d) to maintain proper grouping
            // ONLY restructure if X is itself an OR expression
            if (combineOp == OR &&
                previousCondition instanceof LambdaExpression.BinaryOp prevBinOp &&
                prevBinOp.operator() == AND &&
                prevBinOp.left() instanceof LambdaExpression.BinaryOp xBinOp &&
                xBinOp.operator() == OR) {
                // Restructure: ((a OR b) AND c) OR d → (a OR b) AND (c OR d)
                LambdaExpression x = prevBinOp.left();  // (a OR b)
                LambdaExpression y = prevBinOp.right(); // c
                LambdaExpression z = boolExpr;           // d
                LambdaExpression yOrZ = new LambdaExpression.BinaryOp(y, OR, z);
                combined = new LambdaExpression.BinaryOp(x, AND, yOrZ);
                log.debugf("%s: Restructured ((a OR b) AND c) OR d to (a OR b) AND (c OR d): %s",
                        getInstructionName(), combined);
            }

            stack.push(combined);
            // CRITICAL: Apply post-combination state transition (shouldEnterOrModeAfterAndGroup logic)
            newState = newState.afterCombination(jumpTarget != null && jumpTarget, previousJumpTarget, combineOp);
            log.debugf("%s: Combined with %s: %s", getInstructionName(), combineOp, combined);
        } else {
            // Push standalone
            stack.push(boolExpr);
            log.debugf("%s: Pushed without combining: %s", getInstructionName(), boolExpr);
        }

        // Return state after post-combination transition
        return newState;
    }
}
