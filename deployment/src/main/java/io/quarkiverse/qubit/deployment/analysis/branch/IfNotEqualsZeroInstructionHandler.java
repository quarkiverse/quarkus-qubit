package io.quarkiverse.qubit.deployment.analysis.branch;

import io.quarkiverse.qubit.deployment.ast.LambdaExpression;
import org.objectweb.asm.tree.JumpInsnNode;

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
 * <p>Supports multiple patterns via {@link AbstractZeroEqualityBranchHandler}:
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
    protected String getInstructionName() {
        return "IFNE";
    }

    @Override
    protected LambdaExpression createNumericComparisonExpression(LambdaExpression left, LambdaExpression right) {
        // IFNE: (a - b) != 0 transforms to a == b
        return eq(left, right);
    }

    @Override
    protected LambdaExpression createCompareToExpression(LambdaExpression compareToExpr) {
        // IFNE: compareTo(a, b) != 0 transforms to non-equality check
        return eq(compareToExpr, ZERO_INT);
    }

    @Override
    protected LambdaExpression createArithmeticExpression(LambdaExpression arithmeticExpr) {
        // IFNE: expr != 0
        return eq(arithmeticExpr, ZERO_INT);
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
