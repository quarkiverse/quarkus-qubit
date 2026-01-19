package io.quarkiverse.qubit.deployment.analysis.branch;

import io.quarkiverse.qubit.deployment.analysis.ControlFlowAnalyzer;
import io.quarkiverse.qubit.deployment.ast.LambdaExpression;
import io.quarkiverse.qubit.deployment.common.BytecodeValidator;
import io.quarkus.logging.Log;
import org.jspecify.annotations.Nullable;

import java.util.Deque;
import java.util.Optional;

import static io.quarkiverse.qubit.deployment.ast.LambdaExpression.BinaryOp.Operator;
import static io.quarkiverse.qubit.deployment.ast.LambdaExpression.BinaryOp.Operator.AND;
import static io.quarkiverse.qubit.deployment.ast.LambdaExpression.BinaryOp.Operator.OR;
import static io.quarkiverse.qubit.deployment.ast.LambdaExpression.BinaryOp.and;
import static io.quarkiverse.qubit.deployment.ast.LambdaExpression.BinaryOp.or;
import static io.quarkiverse.qubit.deployment.common.ExpressionTypeInferrer.isBooleanType;
import static java.lang.Boolean.FALSE;
import static java.lang.Boolean.TRUE;

/**
 * Encapsulates expression combination logic for branch analysis.
 *
 * <p>Extracted from BranchHandler default methods to follow composition over inheritance.
 * This class is stateless and all methods are static for easy testability.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Combining expressions with AND/OR operators</li>
 *   <li>Fixing operator precedence in combined expressions</li>
 *   <li>Adjusting combine operators based on label semantics</li>
 *   <li>Determining jump-to-true semantics</li>
 * </ul>
 *
 * @see BranchHandler
 * @see BranchState
 */
public final class BranchExpressionCombiner {

    private BranchExpressionCombiner() {
        // Static utility class
    }

    /**
     * Parameter object for {@link #performCombination}.
     * Encapsulates all context needed for expression combination.
     */
    public record CombinationContext(
            String instructionName,
            BranchState state,
            BranchState newState,
            @Nullable Operator combineOp,
            Boolean jumpTarget,
            Optional<Boolean> previousJumpTarget,
            boolean sameLabel,
            @Nullable LambdaExpression stackTop,
            ControlFlowAnalyzer.LabelClassification jumpLabelClass,
            boolean completingAndGroup,
            boolean startingNewOrGroup) {}

    /**
     * Combines expressions and restructures to fix precedence if needed.
     *
     * <p>When combining ((a OR b) AND c) OR d, restructures to (a OR b) AND (c OR d)
     * to maintain proper grouping.
     *
     * @param combineOp operator to combine with (AND or OR)
     * @param previousCondition expression already on stack
     * @param newExpression new expression to combine
     * @return combined expression, possibly restructured for precedence
     */
    public static LambdaExpression combineAndRestructureIfNeeded(
            Operator combineOp,
            LambdaExpression previousCondition,
            LambdaExpression newExpression) {

        LambdaExpression combined = (combineOp == AND)
                ? and(previousCondition, newExpression)
                : or(previousCondition, newExpression);

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
            LambdaExpression yOrZ = or(y, z);
            return and(x, yOrZ);
        }

        return combined;
    }

    /**
     * Returns true if expression is a predicate that can be combined with AND/OR.
     *
     * @param expr expression to check
     * @return true if expression is a combinable predicate
     */
    public static boolean isPredicateExpression(LambdaExpression expr) {
        return switch (expr) {
            case LambdaExpression.BinaryOp ignored -> true;
            case LambdaExpression.MethodCall methodCall -> isBooleanType(methodCall.returnType());
            case LambdaExpression.InExpression ignored -> true;
            case LambdaExpression.MemberOfExpression ignored -> true;
            case LambdaExpression.UnaryOp ignored -> true;
            default -> false;
        };
    }

    /**
     * Adjusts combine operator based on label semantics.
     *
     * <p>Same-label to FALSE_SINK uses AND; TRUE_SINK/INTERMEDIATE uses OR.
     *
     * @param combineOp initial combine operator
     * @param sameLabel true if jumping to same label as previous instruction
     * @param jumpLabelClass classification of jump target label
     * @param completingAndGroup true if completing an AND group
     * @param startingNewOrGroup true if starting a new OR group
     * @param hasPredicateOnStack true if stack has a predicate expression
     * @return adjusted operator, or null if combination should be deferred
     */
    @Nullable
    public static Operator adjustCombineOperator(
            @Nullable Operator combineOp,
            boolean sameLabel,
            ControlFlowAnalyzer.LabelClassification jumpLabelClass,
            boolean completingAndGroup,
            boolean startingNewOrGroup,
            boolean hasPredicateOnStack) {

        // Same-label semantics: override operator based on label type
        if (sameLabel && combineOp != null) {
            if (jumpLabelClass == ControlFlowAnalyzer.LabelClassification.FALSE_SINK) {
                combineOp = AND;
            } else if (jumpLabelClass == ControlFlowAnalyzer.LabelClassification.INTERMEDIATE ||
                       jumpLabelClass == ControlFlowAnalyzer.LabelClassification.TRUE_SINK) {
                combineOp = OR;
            }
        }

        // AND group transitions based on label classification patterns
        if (startingNewOrGroup && hasPredicateOnStack) {
            // FALSE_SINK → TRUE_SINK: Starting new OR group, defer combination
            return null;
        } else if (completingAndGroup && hasPredicateOnStack) {
            // INTERMEDIATE → TRUE_SINK: Completing AND group, force AND
            return AND;
        }

        return combineOp;
    }

    /**
     * Returns true if branch jumps to the "true" path.
     *
     * @param jumpTarget explicit jump target (true/false), or null
     * @param jumpLabelClass classification of jump target label
     * @param opcode branch instruction opcode
     * @param isSuccessOpcode predicate testing if opcode implies success jump
     * @return true if branch jumps to true path
     */
    public static boolean determineJumpToTrue(
            Boolean jumpTarget,
            ControlFlowAnalyzer.LabelClassification jumpLabelClass,
            int opcode,
            java.util.function.IntPredicate isSuccessOpcode) {
        if (jumpTarget != null) {
            return TRUE.equals(jumpTarget);
        }
        if (jumpLabelClass == ControlFlowAnalyzer.LabelClassification.INTERMEDIATE) {
            return isSuccessOpcode.test(opcode);
        }
        return false;
    }

    /**
     * Processes branch instruction and combines expression with stack.
     *
     * @param stack expression stack
     * @param expression new expression from branch instruction
     * @param instructionName name for logging
     * @param state current branch state
     * @param jumpTarget explicit jump target, or null
     * @param jumpLabelClass classification of jump target label
     * @param sameLabel true if jumping to same label as previous instruction
     * @param completingAndGroup true if completing an AND group
     * @param startingNewOrGroup true if starting a new OR group
     * @param jumpToTrue true if branch jumps to true path
     * @return new branch state after processing
     */
    public static BranchState processAndCombineBranch(
            Deque<LambdaExpression> stack,
            LambdaExpression expression,
            String instructionName,
            BranchState state,
            Boolean jumpTarget,
            ControlFlowAnalyzer.LabelClassification jumpLabelClass,
            boolean sameLabel,
            boolean completingAndGroup,
            boolean startingNewOrGroup,
            boolean jumpToTrue) {

        // Get previous jump target BEFORE processing current branch (needed for afterCombination)
        Optional<Boolean> previousJumpTarget = state.getLastJumpTarget();

        // Process branch instruction atomically (unified operator determination + state transition)
        LambdaExpression stackTop = stack.isEmpty() ? null : stack.peek();
        BranchState.BranchResult result = state.processBranch(jumpToTrue, false, stackTop);

        // Delegate to shared combination logic (includes operator adjustment)
        CombinationContext ctx = new CombinationContext(
                instructionName, state, result.newState(), result.combineOperator(),
                jumpTarget, previousJumpTarget, sameLabel, stackTop,
                jumpLabelClass, completingAndGroup, startingNewOrGroup);
        return performCombination(stack, expression, ctx);
    }

    /**
     * Performs expression combination with OrMode deferral and post-AND-group merge.
     *
     * @param stack expression stack
     * @param expression new expression to combine
     * @param ctx combination context with all parameters
     * @return new branch state after combination
     */
    public static BranchState performCombination(
            Deque<LambdaExpression> stack,
            LambdaExpression expression,
            CombinationContext ctx) {

        String instructionName = ctx.instructionName();
        BranchState state = ctx.state();
        BranchState newState = ctx.newState();
        Boolean jumpTarget = ctx.jumpTarget();
        boolean sameLabel = ctx.sameLabel();
        LambdaExpression stackTop = ctx.stackTop();

        // Adjust combine operator based on same-label semantics and AND group transitions
        boolean hasPredicateOnStack = !stack.isEmpty() && isPredicateExpression(stack.peek());
        Operator combineOp = adjustCombineOperator(ctx.combineOp(), sameLabel, ctx.jumpLabelClass(),
                ctx.completingAndGroup(), ctx.startingNewOrGroup(), hasPredicateOnStack);

        // OrMode deferral: (A && B) || (C && D) - wait for AND group to complete
        if (state instanceof BranchState.OrMode && combineOp == OR
                && FALSE.equals(jumpTarget) && !sameLabel
                && stackTop instanceof LambdaExpression.BinaryOp binOp && binOp.operator() == AND) {
            Log.debugf("%s: Starting new AND group in OrMode, deferring OR combination", instructionName);
            combineOp = null;  // Don't combine yet
        }

        if (combineOp != null && !stack.isEmpty() && isPredicateExpression(stack.peek())) {
            // Combine with previous condition
            LambdaExpression previousCondition = BytecodeValidator.popSafe(stack, instructionName + "-Combine");
            LambdaExpression combined = combineAndRestructureIfNeeded(combineOp, previousCondition, expression);
            stack.push(combined);
            // CRITICAL: Apply post-combination state transition (shouldEnterOrModeAfterAndGroup logic)
            newState = newState.afterCombination(TRUE.equals(jumpTarget), ctx.previousJumpTarget(), combineOp);
            Log.debugf("%s: Combined with %s: %s", instructionName, combineOp, combined);

            // Post-AND-group merge: (A && B) || (C && D) - combine AND groups with OR
            if (sameLabel && combineOp == AND && state instanceof BranchState.OrMode
                    && stack.size() >= 2 && isPredicateExpression(stack.peek())) {
                LambdaExpression rightAndGroup = BytecodeValidator.popSafe(stack, instructionName + "-OrCombine-right");
                if (!stack.isEmpty() && isPredicateExpression(stack.peek())) {
                    LambdaExpression leftAndGroup = BytecodeValidator.popSafe(stack, instructionName + "-OrCombine-left");
                    LambdaExpression orCombined = new LambdaExpression.BinaryOp(leftAndGroup, OR, rightAndGroup);
                    stack.push(orCombined);
                    Log.debugf("%s: Combined two AND groups with OR: %s", instructionName, orCombined);
                } else {
                    stack.push(rightAndGroup); // Put it back if can't combine
                }
            }
        } else {
            // Push standalone
            stack.push(expression);
            Log.debugf("%s: Pushed without combining: %s", instructionName, expression);
        }

        return newState;
    }
}
