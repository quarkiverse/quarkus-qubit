package io.quarkiverse.qubit.deployment.analysis.branch;

import io.quarkiverse.qubit.deployment.analysis.ControlFlowAnalyzer;
import io.quarkiverse.qubit.deployment.ast.LambdaExpression;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;

import java.util.Deque;
import java.util.Map;

/**
 * Encapsulates all context needed for branch instruction handling.
 *
 * <p>This parameter object reduces the number of parameters passed to handler methods,
 * improving readability and making the API easier to evolve.
 *
 * @param stack the expression stack for pushing/popping operands
 * @param jumpInsn the branch instruction being processed
 * @param labelToValue maps labels to their boolean values (true/false sink)
 * @param labelClassifications maps labels to their semantic classification
 * @param state current branch state machine state
 * @param sameLabel true if jumping to same label as previous instruction
 * @param completingAndGroup true if completing an AND group (INTERMEDIATE → TRUE_SINK)
 * @param startingNewOrGroup true if starting a new OR group (FALSE_SINK → TRUE_SINK)
 *
 * @see BranchHandler
 * @see BranchState
 */
public record BranchContext(
        Deque<LambdaExpression> stack,
        JumpInsnNode jumpInsn,
        Map<LabelNode, Boolean> labelToValue,
        Map<LabelNode, ControlFlowAnalyzer.LabelClassification> labelClassifications,
        BranchState state,
        boolean sameLabel,
        boolean completingAndGroup,
        boolean startingNewOrGroup) {

    /**
     * Returns the jump target value for this instruction's label.
     * @return true if jumping to TRUE sink, false if FALSE sink, null if unknown
     */
    public Boolean jumpTarget() {
        return labelToValue.get(jumpInsn.label);
    }

    /**
     * Returns the classification of the jump target label.
     * @return label classification (TRUE_SINK, FALSE_SINK, INTERMEDIATE)
     */
    public ControlFlowAnalyzer.LabelClassification jumpLabelClass() {
        return labelClassifications.get(jumpInsn.label);
    }

    /**
     * Returns the opcode of the branch instruction.
     * @return opcode value
     */
    public int opcode() {
        return jumpInsn.getOpcode();
    }
}
