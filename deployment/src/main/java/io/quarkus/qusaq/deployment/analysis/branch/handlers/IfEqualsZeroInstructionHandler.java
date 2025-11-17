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

    private static final Logger logger = Logger.getLogger(IfEqualsZeroInstructionHandler.class);

    public IfEqualsZeroInstructionHandler() {
        super(logger);
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

        // Handle arithmetic comparison pattern: ISUB/LSUB → IFEQ
        // Transforms (a - b) == 0 to a != b
        if (patterns.isArithmeticComparisonPattern()) {
            LambdaExpression right = BytecodeValidator.popSafe(stack, "IFEQ-ArithComp");
            LambdaExpression left = BytecodeValidator.popSafe(stack, "IFEQ-ArithComp");
            stack.push(new LambdaExpression.BinaryOp(left, NE, right));
            log.tracef("IFEQ: Arithmetic comparison pattern - created NE comparison");
            return state;
        }

        // Handle double comparison pattern: DCMPL/DCMPG → IFEQ
        // Transforms dcmpl(a, b) == 0 to a != b
        if (patterns.isDcmplPattern()) {
            LambdaExpression right = BytecodeValidator.popSafe(stack, "IFEQ-DCMPL");
            LambdaExpression left = BytecodeValidator.popSafe(stack, "IFEQ-DCMPL");
            stack.push(new LambdaExpression.BinaryOp(left, NE, right));
            log.tracef("IFEQ: DCMPL pattern - created NE comparison");
            return state;
        }

        // Handle compareTo pattern: a.compareTo(b) → IFEQ
        // Transforms compareTo(a, b) == 0 to equals check
        if (patterns.isCompareToPattern()) {
            LambdaExpression expr = BytecodeValidator.popSafe(stack, "IFEQ-CompareTo");
            stack.push(new LambdaExpression.BinaryOp(expr, EQ,
                    new LambdaExpression.Constant(true, boolean.class)));
            log.tracef("IFEQ: CompareTo pattern - created EQ true comparison");
            return state;
        }

        // Handle arithmetic pattern: (arithmetic expr) → IFEQ
        // Transforms expr == 0
        if (patterns.isArithmeticPattern()) {
            LambdaExpression expr = BytecodeValidator.popSafe(stack, "IFEQ-Arithmetic");
            stack.push(new LambdaExpression.BinaryOp(expr, EQ,
                    new LambdaExpression.Constant(0, int.class)));
            log.tracef("IFEQ: Arithmetic pattern - created EQ 0 comparison");
            return state;
        }

        // Handle boolean field pattern: field.booleanValue → IFEQ
        // This is the complex case requiring AND/OR combination logic
        return handleBooleanFieldPattern(stack, jumpInsn, labelToValue, state);
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
        if (jumpTarget != null && jumpTarget) {
            // Jump to TRUE means field is false → NOT field
            return new LambdaExpression.UnaryOp(
                    LambdaExpression.UnaryOp.Operator.NOT, fieldAccess);
        } else {
            // Jump to FALSE means field is true → field EQ true
            return new LambdaExpression.BinaryOp(fieldAccess, EQ,
                    new LambdaExpression.Constant(true, boolean.class));
        }
    }
}
