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

import java.util.HashMap;
import java.util.Map;

import io.quarkiverse.qubit.deployment.analysis.ControlFlowAnalyzer.LabelClassification;

/**
 * Maps bytecode comparison opcodes to Operator enum values with inversion support (GT↔LE, GE↔LT, EQ↔NE).
 *
 * <p>
 * Uses data-driven registries to consolidate opcode-to-operator mappings,
 * eliminating duplicated switch expressions.
 */
public final class OpcodeOperatorMapper {

    private OpcodeOperatorMapper() {
        // Utility class
    }

    // ========== Data-Driven Opcode Specifications ==========

    /**
     * Specification for a comparison opcode: maps to operators and success jump status.
     *
     * @param normalOperator operator when not inverted
     * @param invertedOperator operator when inverted
     * @param isSuccessJump true for direct/success jumps (IFLT, IFGT), false for negated (IFLE, IFGE)
     */
    private record OpcodeSpec(Operator normalOperator, Operator invertedOperator, boolean isSuccessJump) {

        /** Returns the appropriate operator based on invert flag. */
        Operator getOperator(boolean invert) {
            return invert ? invertedOperator : normalOperator;
        }
    }

    /** Registry for single-operand opcodes (IFLE, IFLT, IFGE, IFGT). */
    private static final Map<Integer, OpcodeSpec> SINGLE_OPERAND_SPECS;

    /** Registry for two-operand opcodes (IF_ICMP*, IF_ACMP*). */
    private static final Map<Integer, OpcodeSpec> TWO_OPERAND_SPECS;

    static {
        // Single-operand: compare stack top with zero
        Map<Integer, OpcodeSpec> single = new HashMap<>();
        single.put(IFLE, new OpcodeSpec(LE, GT, false)); // Often negated comparison
        single.put(IFLT, new OpcodeSpec(LT, GE, true)); // Direct strict comparison
        single.put(IFGE, new OpcodeSpec(GE, LT, false)); // Often negated comparison
        single.put(IFGT, new OpcodeSpec(GT, LE, true)); // Direct strict comparison
        SINGLE_OPERAND_SPECS = Map.copyOf(single);

        // Two-operand: compare two stack values
        Map<Integer, OpcodeSpec> two = new HashMap<>();
        two.put(IF_ICMPGT, new OpcodeSpec(GT, LE, true));
        two.put(IF_ICMPGE, new OpcodeSpec(GE, LT, false));
        two.put(IF_ICMPLT, new OpcodeSpec(LT, GE, true));
        two.put(IF_ICMPLE, new OpcodeSpec(LE, GT, false));
        two.put(IF_ICMPEQ, new OpcodeSpec(EQ, NE, true));
        two.put(IF_ICMPNE, new OpcodeSpec(NE, EQ, true));
        two.put(IF_ACMPEQ, new OpcodeSpec(EQ, NE, true));
        two.put(IF_ACMPNE, new OpcodeSpec(NE, EQ, true));
        TWO_OPERAND_SPECS = Map.copyOf(two);
    }

    // ========== Opcode Mapping ==========

    /** Maps single-operand opcodes (IFLE, IFLT, IFGE, IFGT) to operators. */
    public static Operator mapSingleOperandOp(int opcode, boolean invert) {
        OpcodeSpec spec = SINGLE_OPERAND_SPECS.get(opcode);
        if (spec == null) {
            throw BytecodeAnalysisException.unexpectedOpcode("single-operand comparison", opcode);
        }
        return spec.getOperator(invert);
    }

    /** Maps two-operand opcodes (IF_ICMP*, IF_ACMP*) to operators. */
    public static Operator mapTwoOperandOp(int opcode, boolean invert) {
        OpcodeSpec spec = TWO_OPERAND_SPECS.get(opcode);
        // Default to EQ for unknown opcodes (preserves original behavior)
        return spec != null ? spec.getOperator(invert) : EQ;
    }

    // ========== Success Jump Opcode Detection ==========

    /** Returns true if opcode is a success/direct jump (IFLT, IFGT) vs negated (IFLE, IFGE). */
    public static boolean isSuccessJumpSingleOperand(int opcode) {
        OpcodeSpec spec = SINGLE_OPERAND_SPECS.get(opcode);
        return spec != null && spec.isSuccessJump();
    }

    /** Returns true if opcode is a success/direct jump (IF_ICMPLT, etc) vs negated (IF_ICMPLE, IF_ICMPGE). */
    public static boolean isSuccessJumpTwoOperand(int opcode) {
        OpcodeSpec spec = TWO_OPERAND_SPECS.get(opcode);
        return spec != null && spec.isSuccessJump();
    }

    // ========== Comparison Operator Determination ==========

    /** Determines operator for single-operand instructions based on jump context. */
    public static Operator determineSingleOperandOperator(
            LabelClassification jumpLabelClass,
            Boolean jumpTarget,
            int opcode) {

        boolean invert = determineInvert(jumpLabelClass, jumpTarget, isSuccessJumpSingleOperand(opcode));
        return mapSingleOperandOp(opcode, invert);
    }

    /** Determines operator for two-operand instructions based on jump context. */
    public static Operator determineTwoOperandOperator(
            LabelClassification jumpLabelClass,
            Boolean jumpTarget,
            int opcode) {

        boolean invert = determineInvert(jumpLabelClass, jumpTarget, isSuccessJumpTwoOperand(opcode));
        return mapTwoOperandOp(opcode, invert);
    }

    /**
     * Determines whether to invert the operator based on jump context.
     * Extracted common logic from both single and two-operand paths.
     */
    private static boolean determineInvert(
            LabelClassification jumpLabelClass,
            Boolean jumpTarget,
            boolean isSuccessJump) {

        return (jumpLabelClass == LabelClassification.INTERMEDIATE)
                ? !isSuccessJump
                : FALSE.equals(jumpTarget);
    }
}
