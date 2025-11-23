package io.quarkus.qusaq.deployment.analysis.branch;

import io.quarkus.qusaq.deployment.LambdaExpression;
import io.quarkus.qusaq.deployment.analysis.ControlFlowAnalyzer;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;

import java.util.Deque;
import java.util.Map;

import static io.quarkus.qusaq.deployment.LambdaExpression.BinaryOp.Operator.AND;
import static io.quarkus.qusaq.deployment.LambdaExpression.BinaryOp.Operator.OR;

/**
 * Handler for specific branch instruction types during bytecode analysis.
 *
 * <p>Each implementation handles a specific category of branch instructions
 * (IFEQ, IFNE, IF_ICMP*, IFLE/IFLT/IFGE/IFGT, IFNULL/IFNONNULL).
 *
 * <p>Handlers are responsible for:
 * <ul>
 *   <li>Determining if they can handle a specific jump instruction</li>
 *   <li>Converting bytecode patterns into expression AST nodes</li>
 *   <li>Managing expression combination using BranchState</li>
 *   <li>Updating the evaluation stack</li>
 * </ul>
 */
public interface BranchHandler {

    /**
     * Determines if this handler can process the given jump instruction.
     *
     * @param jumpInsn the jump instruction to check
     * @return true if this handler supports the instruction, false otherwise
     */
    boolean canHandle(JumpInsnNode jumpInsn);

    /**
     * Handles the branch instruction by converting it to expression AST.
     *
     * <p>This method should:
     * <ol>
     *   <li>Pop required operands from the stack</li>
     *   <li>Create appropriate LambdaExpression nodes</li>
     *   <li>Determine if combination with previous expression is needed</li>
     *   <li>Push result back onto the stack</li>
     *   <li>Return updated BranchState</li>
     * </ol>
     *
     * @param stack the evaluation stack containing LambdaExpression nodes
     * @param jumpInsn the jump instruction being processed
     * @param labelToValue map of labels to their boolean values (TRUE/FALSE sink)
     * @param labelClassifications map of labels to their classifications
     * @param state current immutable branch state
     * @return new immutable branch state after processing this instruction
     */
    BranchState handle(
        Deque<LambdaExpression> stack,
        JumpInsnNode jumpInsn,
        Map<LabelNode, Boolean> labelToValue,
        Map<LabelNode, ControlFlowAnalyzer.LabelClassification> labelClassifications,
        BranchState state
    );

    /**
     * Returns a descriptive name for this handler (for logging/debugging).
     *
     * @return handler name
     */
    default String getName() {
        return getClass().getSimpleName();
    }

    /**
     * Combines two expressions and restructures if needed to fix operator precedence.
     *
     * <p>Handles the pattern: {@code ((a OR b) AND c) OR d → (a OR b) AND (c OR d)}
     *
     * <p>This restructuring maintains proper grouping when combining OR expressions
     * that were previously part of an AND group. Without restructuring, the precedence
     * would be incorrect: {@code (a OR b) AND c} would be evaluated as one group, then
     * OR'd with {@code d}, when the intended logic is {@code (a OR b)} AND {@code (c OR d)}.
     *
     * @param combineOp the operator to use for combining (AND or OR)
     * @param previousCondition the previous expression on the stack
     * @param newExpression the new expression being added
     * @return the combined expression, restructured if necessary
     */
    default LambdaExpression combineAndRestructureIfNeeded(
            LambdaExpression.BinaryOp.Operator combineOp,
            LambdaExpression previousCondition,
            LambdaExpression newExpression) {

        LambdaExpression combined = new LambdaExpression.BinaryOp(
                previousCondition, combineOp, newExpression);

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
            LambdaExpression z = newExpression;     // d
            LambdaExpression yOrZ = new LambdaExpression.BinaryOp(y, OR, z);
            return new LambdaExpression.BinaryOp(x, AND, yOrZ);
        }

        return combined;
    }
}
