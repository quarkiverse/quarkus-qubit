package io.quarkiverse.qubit.deployment.common;

import static io.quarkiverse.qubit.deployment.ast.LambdaExpression.BinaryOp.Operator;
import static io.quarkiverse.qubit.deployment.ast.LambdaExpression.BinaryOp.Operator.EQ;
import static io.quarkiverse.qubit.deployment.ast.LambdaExpression.BinaryOp.Operator.GE;
import static io.quarkiverse.qubit.deployment.ast.LambdaExpression.BinaryOp.Operator.GT;
import static io.quarkiverse.qubit.deployment.ast.LambdaExpression.BinaryOp.Operator.LE;
import static io.quarkiverse.qubit.deployment.ast.LambdaExpression.BinaryOp.Operator.LT;
import static io.quarkiverse.qubit.deployment.ast.LambdaExpression.BinaryOp.Operator.NE;
import static java.lang.Boolean.FALSE;
import static org.objectweb.asm.Opcodes.IFGE;
import static org.objectweb.asm.Opcodes.IFGT;
import static org.objectweb.asm.Opcodes.IFLE;
import static org.objectweb.asm.Opcodes.IFLT;
import static org.objectweb.asm.Opcodes.IF_ACMPEQ;
import static org.objectweb.asm.Opcodes.IF_ACMPNE;
import static org.objectweb.asm.Opcodes.IF_ICMPEQ;
import static org.objectweb.asm.Opcodes.IF_ICMPGE;
import static org.objectweb.asm.Opcodes.IF_ICMPGT;
import static org.objectweb.asm.Opcodes.IF_ICMPLE;
import static org.objectweb.asm.Opcodes.IF_ICMPLT;
import static org.objectweb.asm.Opcodes.IF_ICMPNE;

import io.quarkiverse.qubit.deployment.analysis.ControlFlowAnalyzer.LabelClassification;

/**
 * Maps bytecode comparison opcodes to Operator enum values with inversion support (GT↔LE, GE↔LT, EQ↔NE).
 */
public final class OpcodeOperatorMapper {

    private OpcodeOperatorMapper() {
        // Utility class
    }

    /** Maps single-operand opcodes (IFLE, IFLT, IFGE, IFGT) to operators. */
    public static Operator mapSingleOperandOp(int opcode, boolean invert) {
        return switch (opcode) {
            case IFLE -> invert ? GT : LE;
            case IFLT -> invert ? GE : LT;
            case IFGE -> invert ? LT : GE;
            case IFGT -> invert ? LE : GT;
            default -> throw BytecodeAnalysisException.unexpectedOpcode("single-operand comparison", opcode);
        };
    }

    /** Maps two-operand opcodes (IF_ICMP*, IF_ACMP*) to operators. */
    public static Operator mapTwoOperandOp(int opcode, boolean invert) {
        return switch (opcode) {
            case IF_ICMPGT -> invert ? LE : GT;
            case IF_ICMPGE -> invert ? LT : GE;
            case IF_ICMPLT -> invert ? GE : LT;
            case IF_ICMPLE -> invert ? GT : LE;
            case IF_ICMPEQ, IF_ACMPEQ -> invert ? NE : EQ;
            case IF_ICMPNE, IF_ACMPNE -> invert ? EQ : NE;
            default -> EQ;
        };
    }

    // ========== Success Jump Opcode Detection ==========

    /** Returns true if opcode is a success/direct jump (IFLT, IFGT) vs negated (IFLE, IFGE). */
    public static boolean isSuccessJumpSingleOperand(int opcode) {
        return switch (opcode) {
            case IFLT, IFGT -> true;  // Direct strict comparisons
            case IFLE, IFGE -> false; // Often negated comparisons
            default -> false;
        };
    }

    /** Returns true if opcode is a success/direct jump (IF_ICMPLT, etc) vs negated (IF_ICMPLE, IF_ICMPGE). */
    public static boolean isSuccessJumpTwoOperand(int opcode) {
        return switch (opcode) {
            case IF_ICMPLT, IF_ICMPGT, IF_ICMPEQ, IF_ICMPNE, IF_ACMPEQ, IF_ACMPNE -> true;
            case IF_ICMPLE, IF_ICMPGE -> false;
            default -> false;
        };
    }

    // ========== Comparison Operator Determination ==========

    /** Determines operator for single-operand instructions based on jump context. */
    public static Operator determineSingleOperandOperator(
            LabelClassification jumpLabelClass,
            Boolean jumpTarget,
            int opcode) {

        if (jumpLabelClass == LabelClassification.INTERMEDIATE) {
            boolean isSuccessJump = isSuccessJumpSingleOperand(opcode);
            return mapSingleOperandOp(opcode, !isSuccessJump);
        }

        return mapSingleOperandOp(opcode, FALSE.equals(jumpTarget));
    }

    /** Determines operator for two-operand instructions based on jump context. */
    public static Operator determineTwoOperandOperator(
            LabelClassification jumpLabelClass,
            Boolean jumpTarget,
            int opcode) {

        if (jumpLabelClass == LabelClassification.INTERMEDIATE) {
            boolean isSuccessJump = isSuccessJumpTwoOperand(opcode);
            return mapTwoOperandOp(opcode, !isSuccessJump);
        }

        return mapTwoOperandOp(opcode, FALSE.equals(jumpTarget));
    }
}
