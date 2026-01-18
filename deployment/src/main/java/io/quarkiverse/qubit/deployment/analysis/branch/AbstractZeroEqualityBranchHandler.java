package io.quarkiverse.qubit.deployment.analysis.branch;

import io.quarkiverse.qubit.deployment.ast.LambdaExpression;
import io.quarkiverse.qubit.deployment.common.BytecodeValidator;
import io.quarkiverse.qubit.deployment.common.PatternDetector;
import io.quarkiverse.qubit.deployment.analysis.ControlFlowAnalyzer;
import io.quarkus.logging.Log;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;

import static java.lang.Boolean.TRUE;

import java.util.Deque;
import java.util.Map;
import java.util.Optional;

/**
 * Base class for IFEQ/IFNE instruction handlers.
 * <p>
 * Consolidates the common switch-based pattern handling used by both handlers,
 * delegating opcode-specific expression creation to abstract methods.
 */
public abstract class AbstractZeroEqualityBranchHandler implements BranchHandler {

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

    /**
     * Creates expression for arithmetic pattern (comparison with zero).
     */
    protected abstract LambdaExpression createArithmeticExpression(LambdaExpression arithmeticExpr);

    @Override
    public BranchState handle(
            Deque<LambdaExpression> stack,
            JumpInsnNode jumpInsn,
            Map<LabelNode, Boolean> labelToValue,
            Map<LabelNode, ControlFlowAnalyzer.LabelClassification> labelClassifications,
            BranchState state,
            boolean sameLabel,
            boolean completingAndGroup,
            boolean startingNewOrGroup) {

        if (stack.isEmpty()) {
            Log.tracef("%s: Stack empty, skipping", getInstructionName());
            return state;
        }

        PatternDetector.BranchPatternAnalysis patterns = PatternDetector.BranchPatternAnalysis.analyze(stack);

        return switch (patterns.pattern()) {
            case NUMERIC_COMPARISON -> {
                // Handle numeric comparison: ISUB/LSUB or DCMPL/DCMPG → instruction
                LambdaExpression right = BytecodeValidator.popSafe(stack, getInstructionName() + "-NumericComp");
                LambdaExpression left = BytecodeValidator.popSafe(stack, getInstructionName() + "-NumericComp");
                stack.push(createNumericComparisonExpression(left, right));
                Log.tracef("%s: Numeric comparison pattern - created comparison", getInstructionName());
                yield state;
            }
            case COMPARE_TO -> {
                // Handle compareTo pattern: a.compareTo(b) → instruction
                LambdaExpression expr = BytecodeValidator.popSafe(stack, getInstructionName() + "-CompareTo");
                stack.push(createCompareToExpression(expr));
                Log.tracef("%s: CompareTo pattern - created comparison", getInstructionName());
                yield state;
            }
            case ARITHMETIC -> {
                // Handle arithmetic pattern: (arithmetic expr) → instruction
                LambdaExpression expr = BytecodeValidator.popSafe(stack, getInstructionName() + "-Arithmetic");
                stack.push(createArithmeticExpression(expr));
                Log.tracef("%s: Arithmetic pattern - created comparison", getInstructionName());
                yield state;
            }
            case OTHER -> {
                // Handle boolean field pattern: field.booleanValue → instruction.
                // This is the complex case requiring AND/OR combination logic
                yield handleBooleanFieldPattern(stack, jumpInsn, labelToValue, labelClassifications,
                        state, sameLabel, completingAndGroup, startingNewOrGroup);
            }
        };
    }

    /**
     * Handles boolean field pattern with AND/OR combination logic.
     */
    private BranchState handleBooleanFieldPattern(
            Deque<LambdaExpression> stack,
            JumpInsnNode jumpInsn,
            Map<LabelNode, Boolean> labelToValue,
            Map<LabelNode, ControlFlowAnalyzer.LabelClassification> labelClassifications,
            BranchState state,
            boolean sameLabel,
            boolean completingAndGroup,
            boolean startingNewOrGroup) {

        LambdaExpression fieldAccess = BytecodeValidator.popSafe(stack, getInstructionName() + "-Boolean");
        Boolean jumpTarget = labelToValue.get(jumpInsn.label);
        ControlFlowAnalyzer.LabelClassification jumpLabelClass = labelClassifications.get(jumpInsn.label);

        LambdaExpression boolExpr = createBooleanEvaluationExpression(fieldAccess, jumpTarget);

        Log.debugf("%s boolean field: jumpTarget=%s, jumpLabelClass=%s, boolExpr=%s",
                getInstructionName(), jumpTarget, jumpLabelClass, boolExpr);

        Optional<Boolean> previousJumpTarget = state.getLastJumpTarget();

        LambdaExpression stackTop = stack.isEmpty() ? null : stack.peek();
        BranchState.BranchResult result = state.processBranch(TRUE.equals(jumpTarget), true, stackTop);

        // Delegate to shared combination logic (includes operator adjustment)
        CombinationContext ctx = new CombinationContext(
                getInstructionName(), state, result.newState(), result.combineOperator(),
                jumpTarget, previousJumpTarget, sameLabel, stackTop,
                jumpLabelClass, completingAndGroup, startingNewOrGroup);
        return performCombination(stack, boolExpr, ctx);
    }

}
