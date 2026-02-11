package io.quarkiverse.qubit.deployment.analysis.branch;

import java.util.function.IntPredicate;

import org.jspecify.annotations.Nullable;
import org.objectweb.asm.tree.JumpInsnNode;

import io.quarkiverse.qubit.deployment.analysis.ControlFlowAnalyzer;
import io.quarkiverse.qubit.deployment.ast.LambdaExpression;
import io.quarkiverse.qubit.deployment.ast.LambdaExpression.BinaryOp.Operator;

/**
 * Strategy interface for handling branch instructions.
 *
 * <p>
 * Handlers return new immutable {@link BranchState} rather than mutating input.
 * Expression combination logic is delegated to {@link BranchExpressionCombiner}
 * following composition over inheritance.
 */
public interface BranchHandler {

    /** Returns true if this handler can process the given jump instruction. */
    boolean canHandle(JumpInsnNode jumpInsn);

    /**
     * Converts branch instruction to expression AST.
     * Pops operands, creates expressions, combines with stack, returns updated state.
     *
     * @param ctx branch context containing stack, instruction, labels, and state
     * @return new branch state after processing
     */
    BranchState handle(BranchContext ctx);

    /** Returns handler name for logging. */
    default String getName() {
        return getClass().getSimpleName();
    }

    // ========================================================================
    // Convenience methods delegating to BranchExpressionCombiner
    // These provide cleaner call sites in handlers
    // ========================================================================

    /** Combines expressions and restructures to fix precedence if needed. */
    default LambdaExpression combineAndRestructureIfNeeded(
            Operator combineOp,
            LambdaExpression previousCondition,
            LambdaExpression newExpression) {
        return BranchExpressionCombiner.combineAndRestructureIfNeeded(combineOp, previousCondition, newExpression);
    }

    /** Returns true if expression is a predicate that can be combined with AND/OR. */
    default boolean isPredicateExpression(LambdaExpression expr) {
        return BranchExpressionCombiner.isPredicateExpression(expr);
    }

    /**
     * Adjusts combine operator based on label semantics.
     * Same-label to FALSE_SINK uses AND; TRUE_SINK/INTERMEDIATE uses OR.
     */
    @Nullable
    default Operator adjustCombineOperator(
            @Nullable Operator combineOp,
            boolean sameLabel,
            ControlFlowAnalyzer.LabelClassification jumpLabelClass,
            boolean completingAndGroup,
            boolean startingNewOrGroup,
            boolean hasPredicateOnStack) {
        return BranchExpressionCombiner.adjustCombineOperator(
                combineOp, sameLabel, jumpLabelClass, completingAndGroup, startingNewOrGroup, hasPredicateOnStack);
    }

    /** Returns true if branch jumps to the "true" path. */
    default boolean determineJumpToTrue(
            Boolean jumpTarget,
            ControlFlowAnalyzer.LabelClassification jumpLabelClass,
            int opcode,
            IntPredicate isSuccessOpcode) {
        return BranchExpressionCombiner.determineJumpToTrue(jumpTarget, jumpLabelClass, opcode, isSuccessOpcode);
    }

    /**
     * Processes branch instruction and combines expression with stack.
     *
     * @param ctx branch context
     * @param expression new expression from branch instruction
     * @param instructionName name for logging
     * @param jumpToTrue true if branch jumps to true path
     * @return new branch state after processing
     */
    default BranchState processAndCombineBranch(
            BranchContext ctx,
            LambdaExpression expression,
            String instructionName,
            boolean jumpToTrue) {
        var branchContext = new BranchExpressionCombiner.BranchProcessingContext(
                ctx.jumpTarget(), ctx.jumpLabelClass(), ctx.sameLabel(),
                ctx.completingAndGroup(), ctx.startingNewOrGroup(), jumpToTrue);
        return BranchExpressionCombiner.processAndCombineBranch(
                ctx.stack(), expression, instructionName, ctx.state(), branchContext);
    }
}
