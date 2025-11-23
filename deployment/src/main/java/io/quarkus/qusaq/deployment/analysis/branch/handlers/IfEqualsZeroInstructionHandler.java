package io.quarkus.qusaq.deployment.analysis.branch.handlers;

import io.quarkus.qusaq.deployment.LambdaExpression;
import io.quarkus.qusaq.deployment.analysis.BytecodeValidator;
import io.quarkus.qusaq.deployment.analysis.ControlFlowAnalyzer;
import io.quarkus.qusaq.deployment.analysis.PatternDetector;
import io.quarkus.qusaq.deployment.analysis.branch.BranchState;
import org.jboss.logging.Logger;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;

import java.util.Deque;
import java.util.Map;

import static io.quarkus.qusaq.deployment.LambdaExpression.BinaryOp.Operator.EQ;
import static io.quarkus.qusaq.deployment.LambdaExpression.BinaryOp.Operator.NE;
import static java.lang.Boolean.TRUE;
import static org.objectweb.asm.Opcodes.IFEQ;

/**
 * Handles IFEQ (if equals zero, jump) bytecode instruction.
 *
 * <p>The IFEQ instruction pops a value from the stack and jumps if the value equals zero.
 * This handler transforms these bytecode patterns into high-level lambda expressions.
 *
 * <p>Supports multiple patterns:
 * <ul>
 *   <li>Arithmetic comparison pattern (ISUB/LSUB followed by IFEQ)</li>
 *   <li>Double comparison pattern (DCMPL/DCMPG followed by IFEQ)</li>
 *   <li>CompareTo pattern (compareTo() call followed by IFEQ)</li>
 *   <li>Arithmetic pattern (arithmetic operation followed by IFEQ)</li>
 *   <li>Boolean field pattern (boolean field access followed by IFEQ)</li>
 * </ul>
 */
public class IfEqualsZeroInstructionHandler extends AbstractZeroEqualityBranchHandler {

    private static final Logger log = Logger.getLogger(IfEqualsZeroInstructionHandler.class);

    public IfEqualsZeroInstructionHandler() {
        super(log);
    }

    @Override
    public boolean canHandle(JumpInsnNode jumpInsn) {
        return jumpInsn.getOpcode() == IFEQ;
    }

    @Override
    public BranchState handle(
            Deque<LambdaExpression> stack,
            JumpInsnNode jumpInsn,
            Map<LabelNode, Boolean> labelToValue,
            Map<LabelNode, ControlFlowAnalyzer.LabelClassification> labelClassifications,
            BranchState state) {

        if (stack.isEmpty()) {
            log.tracef("IFEQ: Stack empty, skipping");
            return state;
        }

        PatternDetector.BranchPatternAnalysis patterns = PatternDetector.BranchPatternAnalysis.analyze(stack);

        return switch (patterns.pattern()) {
            case NUMERIC_COMPARISON -> {
                // Handle numeric comparison: ISUB/LSUB or DCMPL/DCMPG → IFEQ
                // Transforms (a - b) == 0 to a != b
                LambdaExpression right = BytecodeValidator.popSafe(stack, "IFEQ-NumericComp");
                LambdaExpression left = BytecodeValidator.popSafe(stack, "IFEQ-NumericComp");
                stack.push(new LambdaExpression.BinaryOp(left, NE, right));
                log.tracef("IFEQ: Numeric comparison pattern - created NE comparison");
                yield state;
            }
            case COMPARE_TO -> {
                // Handle compareTo pattern: a.compareTo(b) → IFEQ
                // Transforms compareTo(a, b) == 0 to equals check
                LambdaExpression expr = BytecodeValidator.popSafe(stack, "IFEQ-CompareTo");
                stack.push(new LambdaExpression.BinaryOp(expr, EQ, LambdaExpression.Constant.TRUE));
                log.tracef("IFEQ: CompareTo pattern - created EQ true comparison");
                yield state;
            }
            case ARITHMETIC -> {
                // Handle arithmetic pattern: (arithmetic expr) → IFEQ
                // Transforms expr == 0
                LambdaExpression expr = BytecodeValidator.popSafe(stack, "IFEQ-Arithmetic");
                stack.push(new LambdaExpression.BinaryOp(expr, EQ, LambdaExpression.Constant.ZERO_INT));
                log.tracef("IFEQ: Arithmetic pattern - created EQ 0 comparison");
                yield state;
            }
            case OTHER -> {
                // Handle boolean field pattern: field.booleanValue → IFEQ
                // This is the complex case requiring AND/OR combination logic
                yield handleBooleanFieldPattern(stack, jumpInsn, labelToValue, state);
            }
        };
    }

    @Override
    protected String getInstructionName() {
        return "IFEQ";
    }

    @Override
    protected LambdaExpression createBooleanEvaluationExpression(LambdaExpression fieldAccess, Boolean jumpTarget) {
        // For IFEQ on boolean field:
        // - Jump to TRUE → field is false (NOT field)
        // - Jump to FALSE → field is true (field EQ true)
        if (TRUE.equals(jumpTarget)) {
            // Jump to TRUE means field is false → NOT field
            return new LambdaExpression.UnaryOp(
                    LambdaExpression.UnaryOp.Operator.NOT, fieldAccess);
        }
        // Jump to FALSE means field is true → field EQ true
        return new LambdaExpression.BinaryOp(fieldAccess, EQ, LambdaExpression.Constant.TRUE);
    }
}
