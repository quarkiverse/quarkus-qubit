package io.quarkiverse.qubit.deployment.analysis.branch;

import io.quarkiverse.qubit.deployment.ast.LambdaExpression;
import org.objectweb.asm.tree.JumpInsnNode;

import static io.quarkiverse.qubit.deployment.ast.LambdaExpression.BinaryOp.eq;
import static io.quarkiverse.qubit.deployment.ast.LambdaExpression.BinaryOp.ne;
import static io.quarkiverse.qubit.deployment.ast.LambdaExpression.Constant.ZERO_INT;
import static io.quarkiverse.qubit.deployment.ast.LambdaExpression.UnaryOp.Operator.NOT;
import static java.lang.Boolean.TRUE;
import static org.objectweb.asm.Opcodes.IFEQ;

/**
 * Handles IFEQ (if equals zero, jump) bytecode instruction.
 *
 * <p>The IFEQ instruction pops a value from the stack and jumps if the value equals zero.
 * This handler transforms these bytecode patterns into high-level lambda expressions.
 *
 * <p>Supports multiple patterns via {@link AbstractZeroEqualityBranchHandler}:
 * <ul>
 *   <li>Arithmetic comparison pattern (ISUB/LSUB followed by IFEQ)</li>
 *   <li>Double comparison pattern (DCMPL/DCMPG followed by IFEQ)</li>
 *   <li>CompareTo pattern (compareTo() call followed by IFEQ)</li>
 *   <li>Arithmetic pattern (arithmetic operation followed by IFEQ)</li>
 *   <li>Boolean field pattern (boolean field access followed by IFEQ)</li>
 * </ul>
 */
public class IfEqualsZeroInstructionHandler extends AbstractZeroEqualityBranchHandler {

    @Override
    public boolean canHandle(JumpInsnNode jumpInsn) {
        return jumpInsn.getOpcode() == IFEQ;
    }

    @Override
    protected String getInstructionName() {
        return "IFEQ";
    }

    @Override
    protected LambdaExpression createNumericComparisonExpression(LambdaExpression left, LambdaExpression right) {
        // IFEQ: (a - b) == 0 transforms to a != b
        return ne(left, right);
    }

    @Override
    protected LambdaExpression createCompareToExpression(LambdaExpression compareToExpr) {
        // IFEQ: compareTo(a, b) == 0 transforms to equality check
        return eq(compareToExpr, LambdaExpression.Constant.TRUE);
    }

    @Override
    protected LambdaExpression createArithmeticExpression(LambdaExpression arithmeticExpr) {
        // IFEQ: expr == 0
        return eq(arithmeticExpr, ZERO_INT);
    }

    @Override
    protected LambdaExpression createBooleanEvaluationExpression(LambdaExpression fieldAccess, Boolean jumpTarget) {
        // For IFEQ on boolean field:
        // - Jump to TRUE → field is false (NOT field)
        // - Jump to FALSE → field is true (field EQ true)
        if (TRUE.equals(jumpTarget)) {
            // Jump to TRUE means field is false → NOT field
            return new LambdaExpression.UnaryOp(NOT, fieldAccess);
        }
        // Jump to FALSE means field is true
        // Don't wrap predicates with == true
        return isPredicateExpression(fieldAccess) ?
                fieldAccess :
                eq(fieldAccess, LambdaExpression.Constant.TRUE);
    }
}
