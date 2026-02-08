package io.quarkiverse.qubit.deployment.analysis.branch;

import io.quarkiverse.qubit.deployment.ast.LambdaExpression;
import io.quarkiverse.qubit.deployment.common.BytecodeValidator;
import io.quarkiverse.qubit.deployment.common.PatternDetector;
import io.quarkiverse.qubit.deployment.analysis.ControlFlowAnalyzer;
import io.quarkus.logging.Log;

import static io.quarkiverse.qubit.deployment.ast.LambdaExpression.BinaryOp.eq;
import static io.quarkiverse.qubit.deployment.ast.LambdaExpression.UnaryOp.Operator.NOT;
import static java.lang.Boolean.TRUE;

import java.util.Deque;
import java.util.Optional;

/**
 * Base class for IFEQ/IFNE instruction handlers.
 *
 * <p>Consolidates the common switch-based pattern handling used by both handlers,
 * delegating opcode-specific expression creation to abstract methods.
 */
public abstract class AbstractZeroEqualityBranchHandler implements BranchHandler {

    /** Creates boolean evaluation expression based on instruction semantics. */
    protected abstract LambdaExpression createBooleanEvaluationExpression(
            LambdaExpression fieldAccess,
            Boolean jumpTarget);

    /** Returns instruction name for logging. */
    protected abstract String getInstructionName();

    /**
     * Creates expression for numeric comparison (ISUB/LSUB/DCMPL/DCMPG).
     * IFEQ: (a-b)==0 → a!=b; IFNE: (a-b)!=0 → a==b
     */
    protected abstract LambdaExpression createNumericComparisonExpression(
            LambdaExpression left, LambdaExpression right);

    /**
     * Creates expression for compareTo pattern.
     * IFEQ: compareTo()==0 → equality; IFNE: compareTo()!=0 → non-equality
     */
    protected abstract LambdaExpression createCompareToExpression(LambdaExpression compareToExpr);

    /** Creates expression for arithmetic pattern (comparison with zero). */
    protected abstract LambdaExpression createArithmeticExpression(LambdaExpression arithmeticExpr);

    /**
     * Shared logic for boolean evaluation expression creation.
     * Subclasses pass the condition that triggers negation (TRUE for IFEQ, FALSE for IFNE).
     *
     * @param fieldAccess the boolean field or expression being evaluated
     * @param jumpTarget the jump target from the branch context
     * @param negateWhen the jump target value that should trigger NOT wrapping
     * @return the appropriate boolean expression
     */
    protected LambdaExpression createConditionalBooleanExpression(
            LambdaExpression fieldAccess,
            Boolean jumpTarget,
            Boolean negateWhen) {

        if (negateWhen.equals(jumpTarget)) {
            return new LambdaExpression.UnaryOp(NOT, fieldAccess);
        }

        // Don't wrap predicates with == true
        return isPredicateExpression(fieldAccess) ?
                fieldAccess :
                eq(fieldAccess, LambdaExpression.Constant.TRUE);
    }

    @Override
    public BranchState handle(BranchContext ctx) {
        Deque<LambdaExpression> stack = ctx.stack();

        if (stack.isEmpty()) {
            Log.tracef("%s: Stack empty, skipping", getInstructionName());
            return ctx.state();
        }

        PatternDetector.BranchPatternAnalysis patterns = PatternDetector.BranchPatternAnalysis.analyze(stack);

        return switch (patterns.pattern()) {
            case NUMERIC_COMPARISON -> {
                // Handle numeric comparison: ISUB/LSUB or DCMPL/DCMPG → instruction
                LambdaExpression right = BytecodeValidator.popSafe(stack, getInstructionName() + "-NumericComp");
                LambdaExpression left = BytecodeValidator.popSafe(stack, getInstructionName() + "-NumericComp");
                stack.push(createNumericComparisonExpression(left, right));
                Log.tracef("%s: Numeric comparison pattern - created comparison", getInstructionName());
                yield ctx.state();
            }
            case COMPARE_TO -> {
                // Handle compareTo pattern: a.compareTo(b) → instruction
                LambdaExpression expr = BytecodeValidator.popSafe(stack, getInstructionName() + "-CompareTo");
                stack.push(createCompareToExpression(expr));
                Log.tracef("%s: CompareTo pattern - created comparison", getInstructionName());
                yield ctx.state();
            }
            case ARITHMETIC -> {
                // Handle arithmetic pattern: (arithmetic expr) → instruction
                LambdaExpression expr = BytecodeValidator.popSafe(stack, getInstructionName() + "-Arithmetic");
                stack.push(createArithmeticExpression(expr));
                Log.tracef("%s: Arithmetic pattern - created comparison", getInstructionName());
                yield ctx.state();
            }
            case OTHER -> {
                // Handle boolean field pattern: field.booleanValue → instruction.
                // This is the complex case requiring AND/OR combination logic
                yield handleBooleanFieldPattern(ctx);
            }
        };
    }

    /** Handles boolean field pattern with AND/OR combination logic. */
    private BranchState handleBooleanFieldPattern(BranchContext ctx) {
        Deque<LambdaExpression> stack = ctx.stack();
        BranchState state = ctx.state();

        LambdaExpression fieldAccess = BytecodeValidator.popSafe(stack, getInstructionName() + "-Boolean");
        Boolean jumpTarget = ctx.jumpTarget();
        ControlFlowAnalyzer.LabelClassification jumpLabelClass = ctx.jumpLabelClass();

        LambdaExpression boolExpr = createBooleanEvaluationExpression(fieldAccess, jumpTarget);

        Log.debugf("%s boolean field: jumpTarget=%s, jumpLabelClass=%s, boolExpr=%s",
                getInstructionName(), jumpTarget, jumpLabelClass, boolExpr);

        Optional<Boolean> previousJumpTarget = state.getLastJumpTarget();

        LambdaExpression stackTop = stack.isEmpty() ? null : stack.peek();
        BranchState.BranchResult result = state.processBranch(TRUE.equals(jumpTarget), true, stackTop);

        // Delegate to shared combination logic (includes operator adjustment)
        BranchExpressionCombiner.CombinationContext combineCtx = new BranchExpressionCombiner.CombinationContext(
                getInstructionName(), state, result.newState(), result.combineOperator(),
                jumpTarget, previousJumpTarget, ctx.sameLabel(), stackTop,
                jumpLabelClass, ctx.completingAndGroup(), ctx.startingNewOrGroup());
        return BranchExpressionCombiner.performCombination(stack, boolExpr, combineCtx);
    }
}
