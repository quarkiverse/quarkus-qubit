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

    private static final Logger logger = Logger.getLogger(IfNotEqualsZeroInstructionHandler.class);

    public IfNotEqualsZeroInstructionHandler() {
        super(logger);
    }

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
            log.tracef("IFNE: Stack empty, skipping");
            return state;
        }

        PatternDetector.BranchPatternAnalysis patterns = PatternDetector.BranchPatternAnalysis.analyze(stack);

        // Handle arithmetic comparison pattern: ISUB/LSUB → IFNE
        // Transforms (a - b) != 0 to a == b
        if (patterns.isArithmeticComparisonPattern() || patterns.isDcmplPattern()) {
            LambdaExpression right = BytecodeValidator.popSafe(stack, "IFNE-ArithComp");
            LambdaExpression left = BytecodeValidator.popSafe(stack, "IFNE-ArithComp");
            stack.push(new LambdaExpression.BinaryOp(left, EQ, right));
            log.tracef("IFNE: Arithmetic/DCMPL comparison pattern - created EQ comparison");
            return state;
        }

        // Handle compareTo pattern: a.compareTo(b) → IFNE
        // Transforms compareTo(a, b) != 0 to not-equals check
        if (patterns.isCompareToPattern()) {
            LambdaExpression expr = BytecodeValidator.popSafe(stack, "IFNE-CompareTo");
            stack.push(new LambdaExpression.BinaryOp(expr, EQ,
                    new LambdaExpression.Constant(0, int.class)));
            log.tracef("IFNE: CompareTo pattern - created EQ 0 comparison");
            return state;
        }

        // Handle arithmetic pattern: (arithmetic expr) → IFNE
        // Transforms expr != 0
        if (patterns.isArithmeticPattern()) {
            LambdaExpression expr = BytecodeValidator.popSafe(stack, "IFNE-Arithmetic");
            stack.push(new LambdaExpression.BinaryOp(expr, EQ,
                    new LambdaExpression.Constant(0, int.class)));
            log.tracef("IFNE: Arithmetic pattern - created EQ 0 comparison");
            return state;
        }

        // Handle boolean field pattern: field.booleanValue → IFNE
        // This is the complex case requiring AND/OR combination logic
        return handleBooleanFieldPattern(stack, jumpInsn, labelToValue, state);
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
        boolean isAlreadyComparison = (fieldAccess instanceof LambdaExpression.BinaryOp binOp) &&
                                      binOp.operator() == EQ;

        boolean shouldNegate = (jumpTarget != null && !jumpTarget);

        if (isAlreadyComparison) {
            return shouldNegate ?
                    new LambdaExpression.UnaryOp(LambdaExpression.UnaryOp.Operator.NOT, fieldAccess) :
                    fieldAccess;
        }

        return shouldNegate ?
                new LambdaExpression.UnaryOp(LambdaExpression.UnaryOp.Operator.NOT, fieldAccess) :
                new LambdaExpression.BinaryOp(fieldAccess, EQ,
                        new LambdaExpression.Constant(true, boolean.class));
    }
}
