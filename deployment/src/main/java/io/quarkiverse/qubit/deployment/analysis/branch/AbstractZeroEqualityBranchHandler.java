package io.quarkiverse.qubit.deployment.analysis.branch;

import io.quarkiverse.qubit.deployment.ast.LambdaExpression;
import io.quarkiverse.qubit.deployment.common.BytecodeValidator;
import io.quarkus.logging.Log;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;

import static java.lang.Boolean.TRUE;

import java.util.Deque;
import java.util.Map;
import java.util.Optional;

import static io.quarkiverse.qubit.deployment.ast.LambdaExpression.BinaryOp.Operator;
import static io.quarkiverse.qubit.deployment.common.ExpressionTypeInferrer.isBooleanType;

/**
 * Base class for IFEQ/IFNE instruction handlers.
 */
public abstract class AbstractZeroEqualityBranchHandler implements BranchHandler {

    /**
     * Creates boolean evaluation expression based on instruction semantics.
     *
     * @param fieldAccess the field access expression from the stack
     * @param jumpTarget the target value from label-to-value mapping (null if label not found)
     * @return the boolean evaluation expression
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

        Log.tracef("%s boolean field: jumpTarget=%s, boolExpr=%s", getInstructionName(), jumpTarget, boolExpr);

        Optional<Boolean> previousJumpTarget = state.getLastJumpTarget();

        LambdaExpression stackTop = stack.isEmpty() ? null : stack.peek();
        BranchState.BranchResult result = state.processBranch(TRUE.equals(jumpTarget), true, stackTop);
        Operator combineOp = result.combineOperator();
        BranchState newState = result.newState();

        if (combineOp != null && !stack.isEmpty() && isPredicateExpression(stack.peek())) {
            LambdaExpression previousCondition = BytecodeValidator.popSafe(stack, getInstructionName() + "-Combine");
            LambdaExpression combined = combineAndRestructureIfNeeded(combineOp, previousCondition, boolExpr);
            stack.push(combined);
            newState = newState.afterCombination(TRUE.equals(jumpTarget), previousJumpTarget, combineOp);
            Log.debugf("%s: Combined with %s: %s", getInstructionName(), combineOp, combined);
        } else {
            stack.push(boolExpr);
            Log.debugf("%s: Pushed without combining: %s", getInstructionName(), boolExpr);
        }

        return newState;
    }

    /**
     * Checks if expression is a predicate that can be combined with AND/OR.
     * <p>
     * These expression types can be combined:
     * <ul>
     *   <li>{@link LambdaExpression.BinaryOp} (comparisons, logical ops)</li>
     *   <li>{@link LambdaExpression.MethodCall} returning boolean (e.g., String.equals())</li>
     *   <li>{@link LambdaExpression.InExpression} (already a predicate)</li>
     *   <li>{@link LambdaExpression.MemberOfExpression} (already a predicate)</li>
     *   <li>{@link LambdaExpression.UnaryOp} (e.g., NOT expressions)</li>
     * </ul>
     */
    protected boolean isPredicateExpression(LambdaExpression expr) {
        return switch (expr) {
            case LambdaExpression.BinaryOp ignored -> true;
            case LambdaExpression.MethodCall methodCall -> isBooleanType(methodCall.returnType());
            case LambdaExpression.InExpression ignored -> true;
            case LambdaExpression.MemberOfExpression ignored -> true;
            case LambdaExpression.UnaryOp ignored -> true;
            default -> false;
        };
    }
}
