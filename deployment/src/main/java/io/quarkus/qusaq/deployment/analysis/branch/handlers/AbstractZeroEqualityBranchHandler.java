package io.quarkus.qusaq.deployment.analysis.branch.handlers;

import io.quarkus.qusaq.deployment.LambdaExpression;
import io.quarkus.qusaq.deployment.analysis.BytecodeValidator;
import io.quarkus.qusaq.deployment.analysis.branch.BranchHandler;
import io.quarkus.qusaq.deployment.analysis.branch.BranchState;
import org.jboss.logging.Logger;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;

import static java.lang.Boolean.TRUE;

import java.util.Deque;
import java.util.Map;
import java.util.Optional;

import static io.quarkus.qusaq.deployment.LambdaExpression.BinaryOp.Operator;

/**
 * Base class for IFEQ/IFNE instruction handlers.
 */
public abstract class AbstractZeroEqualityBranchHandler implements BranchHandler {

    protected final Logger log;

    protected AbstractZeroEqualityBranchHandler(Logger log) {
        this.log = log;
    }

    /**
     * Creates boolean evaluation expression based on instruction semantics.
     */
    protected abstract LambdaExpression createBooleanEvaluationExpression(
            LambdaExpression fieldAccess,
            Boolean jumpTarget);

    /**
     * Returns instruction name for logging.
     */
    protected abstract String getInstructionName();

    /**
     * Handles boolean field pattern with AND/OR combination logic.
     */
    protected BranchState handleBooleanFieldPattern(
            Deque<LambdaExpression> stack,
            JumpInsnNode jumpInsn,
            Map<LabelNode, Boolean> labelToValue,
            BranchState state) {

        LambdaExpression fieldAccess = BytecodeValidator.popSafe(stack, getInstructionName() + "-Boolean");
        Boolean jumpTarget = labelToValue.get(jumpInsn.label);

        LambdaExpression boolExpr = createBooleanEvaluationExpression(fieldAccess, jumpTarget);

        log.tracef("%s boolean field: jumpTarget=%s, boolExpr=%s", getInstructionName(), jumpTarget, boolExpr);

        Optional<Boolean> previousJumpTarget = state.getLastJumpTarget();

        LambdaExpression stackTop = stack.isEmpty() ? null : stack.peek();
        BranchState.BranchResult result = state.processBranch(TRUE.equals(jumpTarget), true, stackTop);
        Operator combineOp = result.combineOperator();
        BranchState newState = result.newState();

        if (combineOp != null && !stack.isEmpty() && stack.peek() instanceof LambdaExpression.BinaryOp) {
            LambdaExpression previousCondition = BytecodeValidator.popSafe(stack, getInstructionName() + "-Combine");
            LambdaExpression combined = combineAndRestructureIfNeeded(combineOp, previousCondition, boolExpr);
            stack.push(combined);
            newState = newState.afterCombination(TRUE.equals(jumpTarget), previousJumpTarget, combineOp);
            log.debugf("%s: Combined with %s: %s", getInstructionName(), combineOp, combined);
        } else {
            stack.push(boolExpr);
            log.debugf("%s: Pushed without combining: %s", getInstructionName(), boolExpr);
        }

        return newState;
    }
}
