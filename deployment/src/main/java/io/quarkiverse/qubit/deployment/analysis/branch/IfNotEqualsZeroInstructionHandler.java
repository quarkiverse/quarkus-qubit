package io.quarkiverse.qubit.deployment.analysis.branch;

import io.quarkiverse.qubit.deployment.ast.LambdaExpression;
import io.quarkiverse.qubit.deployment.common.BytecodeValidator;
import io.quarkiverse.qubit.deployment.analysis.ControlFlowAnalyzer;
import io.quarkiverse.qubit.deployment.common.PatternDetector;
import io.quarkus.logging.Log;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;

import java.util.Deque;
import java.util.Map;

import static io.quarkiverse.qubit.deployment.ast.LambdaExpression.BinaryOp.eq;
import static io.quarkiverse.qubit.deployment.ast.LambdaExpression.Constant.ZERO_INT;
import static io.quarkiverse.qubit.deployment.ast.LambdaExpression.UnaryOp.Operator.NOT;
import static java.lang.Boolean.FALSE;
import static org.objectweb.asm.Opcodes.IFNE;

/**
 * Handles IFNE (if not equals zero, jump) bytecode instruction.
 *
 * <p>The IFNE instruction pops a value from the stack and jumps if the value is not equal to zero.
 * This handler transforms these bytecode patterns into high-level lambda expressions.
 *
 * <p>Supports multiple patterns:
 * <ul>
 *   <li>Arithmetic comparison pattern (ISUB/LSUB followed by IFNE)</li>
 *   <li>Double comparison pattern (DCMPL/DCMPG followed by IFNE)</li>
 *   <li>CompareTo pattern (compareTo() call followed by IFNE)</li>
 *   <li>Arithmetic pattern (arithmetic operation followed by IFNE)</li>
 *   <li>Boolean field pattern (boolean field access followed by IFNE)</li>
 * </ul>
 */
public class IfNotEqualsZeroInstructionHandler extends AbstractZeroEqualityBranchHandler {

    @Override
    public boolean canHandle(JumpInsnNode jumpInsn) {
        return jumpInsn.getOpcode() == IFNE;
    }

    @Override
    public BranchState handle(
            Deque<LambdaExpression> stack,
            JumpInsnNode jumpInsn,
            Map<LabelNode, Boolean> labelToValue,
            Map<LabelNode, ControlFlowAnalyzer.LabelClassification> labelClassifications,
            BranchState state) {

        if (stack.isEmpty()) {
            Log.tracef("IFNE: Stack empty, skipping");
            return state;
        }

        PatternDetector.BranchPatternAnalysis patterns = PatternDetector.BranchPatternAnalysis.analyze(stack);

        return switch (patterns.pattern()) {
            case NUMERIC_COMPARISON -> {
                // Handle numeric comparison: ISUB/LSUB or DCMPL/DCMPG → IFNE
                // Transforms (a - b) != 0 to a == b
                LambdaExpression right = BytecodeValidator.popSafe(stack, "IFNE-NumericComp");
                LambdaExpression left = BytecodeValidator.popSafe(stack, "IFNE-NumericComp");
                stack.push(eq(left, right));
                Log.tracef("IFNE: Numeric comparison pattern - created EQ comparison");
                yield state;
            }
            case COMPARE_TO -> {
                // Handle compareTo pattern: a.compareTo(b) → IFNE
                // Transforms compareTo(a, b) != 0 to not-equals check
                LambdaExpression expr = BytecodeValidator.popSafe(stack, "IFNE-CompareTo");
                stack.push(eq(expr, ZERO_INT));
                Log.tracef("IFNE: CompareTo pattern - created EQ 0 comparison");
                yield state;
            }
            case ARITHMETIC -> {
                // Handle arithmetic pattern: (arithmetic expr) → IFNE
                // Transforms expr != 0
                LambdaExpression expr = BytecodeValidator.popSafe(stack, "IFNE-Arithmetic");
                stack.push(eq(expr, ZERO_INT));
                Log.tracef("IFNE: Arithmetic pattern - created EQ 0 comparison");
                yield state;
            }
            case OTHER -> {
                // Handle boolean field pattern: field.booleanValue → IFNE
                // This is the complex case requiring AND/OR combination logic
                yield handleBooleanFieldPattern(stack, jumpInsn, labelToValue, state);
            }
        };
    }

    @Override
    protected String getInstructionName() {
        return "IFNE";
    }

    @Override
    protected LambdaExpression createBooleanEvaluationExpression(LambdaExpression fieldAccess, Boolean jumpTarget) {
        // For IFNE on boolean field:
        // - Jump to TRUE → field is true
        // - Jump to FALSE → field is false (NOT field)

        // Extract common negate case
        if (FALSE.equals(jumpTarget)) {
            return new LambdaExpression.UnaryOp(NOT, fieldAccess);
        }

        // Handle non-negate cases - don't wrap predicates with == true
        return isPredicateExpression(fieldAccess) ?
                fieldAccess :
                eq(fieldAccess, LambdaExpression.Constant.TRUE);
    }
}
