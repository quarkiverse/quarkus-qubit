package io.quarkiverse.qubit.deployment.analysis.branch;

import io.quarkiverse.qubit.deployment.analysis.ControlFlowAnalyzer;
import io.quarkiverse.qubit.deployment.ast.LambdaExpression;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;

import java.util.Deque;
import java.util.Map;

/** Parameter object for branch instruction handling context. */
public record BranchContext(
        Deque<LambdaExpression> stack,
        JumpInsnNode jumpInsn,
        Map<LabelNode, Boolean> labelToValue,
        Map<LabelNode, ControlFlowAnalyzer.LabelClassification> labelClassifications,
        BranchState state,
        boolean sameLabel,
        boolean completingAndGroup,
        boolean startingNewOrGroup) {

    /** Returns jump target value: true=TRUE_SINK, false=FALSE_SINK, null=unknown. */
    public Boolean jumpTarget() {
        return labelToValue.get(jumpInsn.label);
    }

    /** Returns the classification of the jump target label. */
    public ControlFlowAnalyzer.LabelClassification jumpLabelClass() {
        return labelClassifications.get(jumpInsn.label);
    }

    /** Returns the opcode of the branch instruction. */
    public int opcode() {
        return jumpInsn.getOpcode();
    }
}
