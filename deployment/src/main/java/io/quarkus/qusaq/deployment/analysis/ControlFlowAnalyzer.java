package io.quarkus.qusaq.deployment.analysis;

import org.jboss.logging.Logger;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static io.quarkus.qusaq.deployment.analysis.BytecodeAnalysisConstants.CONDITIONAL_JUMP_LOOKAHEAD_LIMIT;
import static io.quarkus.qusaq.deployment.analysis.BytecodeAnalysisConstants.LABEL_CLASSIFICATION_LOOKAHEAD_LIMIT;
import static io.quarkus.qusaq.deployment.analysis.BytecodeAnalysisConstants.LABEL_TRACE_DEPTH_LIMIT;
import static io.quarkus.qusaq.deployment.analysis.ControlFlowAnalyzer.LabelClassification.FALSE_SINK;
import static io.quarkus.qusaq.deployment.analysis.ControlFlowAnalyzer.LabelClassification.INTERMEDIATE;
import static io.quarkus.qusaq.deployment.analysis.ControlFlowAnalyzer.LabelClassification.TRUE_SINK;
import static org.objectweb.asm.Opcodes.GOTO;
import static org.objectweb.asm.Opcodes.ICONST_0;
import static org.objectweb.asm.Opcodes.ICONST_1;

/**
 * Analyzes control flow patterns to reconstruct boolean expressions from bytecode branches.
 */
public final class ControlFlowAnalyzer {

    private static final Logger log = Logger.getLogger(ControlFlowAnalyzer.class);

    /**
     * Formats label for logging.
     */
    private static String formatLabelForLogging(LabelNode label) {
        return String.valueOf(System.identityHashCode(label));
    }

    /**
     * Label role in boolean expression evaluation.
     */
    public enum LabelClassification {
        TRUE_SINK("TRUE_SINK"),
        FALSE_SINK("FALSE_SINK"),
        INTERMEDIATE("INTERMEDIATE");

        private final String displayName;

        LabelClassification(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    /**
     * Classifies all labels in method instructions.
     */
    public Map<LabelNode, LabelClassification> classifyLabels(InsnList instructions) {
        Map<LabelNode, LabelClassification> classifications = new HashMap<>();

        int instructionCount = instructions.size();
        for (int i = 0; i < instructionCount; i++) {
            AbstractInsnNode insn = instructions.get(i);
            if (insn instanceof LabelNode labelNode) {
                LabelClassification classification = classifyLabel(labelNode, instructions, i, instructionCount);
                if (classification != null) {
                    classifications.put(labelNode, classification);
                }
            }
        }

        return classifications;
    }

    /**
     * Classifies label by examining following instructions.
     */
    private LabelClassification classifyLabel(LabelNode labelNode, InsnList instructions, int labelIndex, int instructionCount) {
        int limit = Math.min(labelIndex + LABEL_CLASSIFICATION_LOOKAHEAD_LIMIT, instructionCount);

        for (int j = labelIndex + 1; j < limit; j++) {
            AbstractInsnNode next = instructions.get(j);
            int opcode = next.getOpcode();

            LabelClassification classification = classifyIconstInstruction(opcode);
            if (classification != null) {
                log.debugf("Classified label %s as %s (offset %d)",
                          formatLabelForLogging(labelNode), classification.getDisplayName(), j - labelIndex);
                return classification;
            }

            if (opcode != -1) {
                log.debugf("Classified label %s as %s (next opcode: %d at offset %d)",
                          formatLabelForLogging(labelNode), INTERMEDIATE.getDisplayName(), opcode, j - labelIndex);
                return INTERMEDIATE;
            }
        }

        return null;
    }

    /**
     * Maps labels to their boolean destination values.
     */
    public Map<LabelNode, Boolean> traceLabelDestinations(InsnList instructions,
                                                          Map<LabelNode, LabelClassification> classifications) {
        Map<LabelNode, Integer> labelToIndex = buildLabelToIndexMap(instructions);
        Map<LabelNode, Boolean> labelToValue = new HashMap<>();

        classifications.forEach((label, classification) ->
            resolveLabelValue(label, classification, instructions, labelToIndex, classifications, labelToValue)
        );

        return labelToValue;
    }

    /**
     * Builds label-to-index map.
     */
    private Map<LabelNode, Integer> buildLabelToIndexMap(InsnList instructions) {
        Map<LabelNode, Integer> labelToIndex = new HashMap<>();
        int instructionCount = instructions.size();
        for (int i = 0; i < instructionCount; i++) {
            AbstractInsnNode insn = instructions.get(i);
            if (insn instanceof LabelNode labelNode) {
                labelToIndex.put(labelNode, i);
            }
        }
        return labelToIndex;
    }

    /**
     * Resolves label classification to boolean value.
     */
    private void resolveLabelValue(
            LabelNode label,
            LabelClassification classification,
            InsnList instructions,
            Map<LabelNode, Integer> labelToIndex,
            Map<LabelNode, LabelClassification> classifications,
            Map<LabelNode, Boolean> labelToValue) {

        switch (classification) {
            case TRUE_SINK -> labelToValue.put(label, true);
            case FALSE_SINK -> labelToValue.put(label, false);
            case INTERMEDIATE -> resolveIntermediateLabel(label, instructions, labelToIndex, classifications, labelToValue);
        }
    }

    /**
     * Resolves intermediate label by tracing to sink destination.
     */
    private void resolveIntermediateLabel(
            LabelNode label,
            InsnList instructions,
            Map<LabelNode, Integer> labelToIndex,
            Map<LabelNode, LabelClassification> classifications,
            Map<LabelNode, Boolean> labelToValue) {

        LabelClassification destination = traceLabelDestination(
            label, instructions, labelToIndex, classifications, new HashSet<>());

        if (destination == TRUE_SINK) {
            labelToValue.put(label, true);
            log.debugf("Traced intermediate label %s -> %s",
                      formatLabelForLogging(label), TRUE_SINK.getDisplayName());
        } else if (destination == FALSE_SINK) {
            labelToValue.put(label, false);
            log.debugf("Traced intermediate label %s -> %s",
                      formatLabelForLogging(label), FALSE_SINK.getDisplayName());
        } else {
            log.debugf("Could not trace label %s to %s/%s",
                      formatLabelForLogging(label),
                      TRUE_SINK.getDisplayName(), FALSE_SINK.getDisplayName());
        }
    }

    /**
     * Traces label to TRUE_SINK or FALSE_SINK destination.
     */
    private LabelClassification traceLabelDestination(
            LabelNode label,
            InsnList instructions,
            Map<LabelNode, Integer> labelToIndex,
            Map<LabelNode, LabelClassification> classifications,
            Set<LabelNode> visited) {

        if (visited.contains(label)) {
            return null;
        }
        visited.add(label);

        LabelClassification directSink = getDirectSinkClassification(label, classifications);
        if (directSink != null) {
            return directSink;
        }

        Integer startIndex = labelToIndex.get(label);
        if (startIndex == null) {
            return null;
        }

        return traceFromIndex(startIndex, instructions, labelToIndex, classifications, visited);
    }

    /**
     * Returns classification if label is already a sink, null otherwise.
     */
    private LabelClassification getDirectSinkClassification(
            LabelNode label,
            Map<LabelNode, LabelClassification> classifications) {

        LabelClassification classification = classifications.get(label);
        if (classification == TRUE_SINK || classification == FALSE_SINK) {
            return classification;
        }
        return null;
    }

    /**
     * Traces label destination from instruction index.
     */
    private LabelClassification traceFromIndex(
            int startIndex,
            InsnList instructions,
            Map<LabelNode, Integer> labelToIndex,
            Map<LabelNode, LabelClassification> classifications,
            Set<LabelNode> visited) {

        int instructionCount = instructions.size();
        for (int i = startIndex + 1; i < instructionCount; i++) {
            AbstractInsnNode insn = instructions.get(i);
            int opcode = insn.getOpcode();

            if (opcode == -1) {
                continue;
            }

            LabelClassification result = processInstruction(
                    insn, opcode, i, instructions, labelToIndex, classifications, visited);

            if (result != null) {
                return result;
            }

            if (i - startIndex > LABEL_TRACE_DEPTH_LIMIT) {
                return null;
            }
        }

        return null;
    }

    /**
     * Processes instruction during label tracing.
     */
    private LabelClassification processInstruction(
            AbstractInsnNode insn,
            int opcode,
            int currentIndex,
            InsnList instructions,
            Map<LabelNode, Integer> labelToIndex,
            Map<LabelNode, LabelClassification> classifications,
            Set<LabelNode> visited) {

        if (insn instanceof JumpInsnNode jumpInsn) {
            if (opcode == GOTO) {
                return traceLabelDestination(jumpInsn.label, instructions, labelToIndex, classifications, visited);
            }
            return handleConditionalJump(jumpInsn, currentIndex, instructions, labelToIndex, classifications, visited);
        }

        return classifyIconstInstruction(opcode);
    }

    /**
     * Handles conditional jump instructions by looking ahead for subsequent GOTO.
     */
    private LabelClassification handleConditionalJump(
            JumpInsnNode conditionalJump,
            int currentIndex,
            InsnList instructions,
            Map<LabelNode, Integer> labelToIndex,
            Map<LabelNode, LabelClassification> classifications,
            Set<LabelNode> visited) {

        LabelNode targetLabel = findSubsequentGotoTarget(currentIndex, instructions);
        if (targetLabel != null) {
            return traceLabelDestination(targetLabel, instructions, labelToIndex, classifications, visited);
        }

        return traceLabelDestination(conditionalJump.label, instructions, labelToIndex, classifications, visited);
    }

    /**
     * Finds subsequent GOTO target after conditional jump.
     */
    private LabelNode findSubsequentGotoTarget(int startIndex, InsnList instructions) {
        int instructionCount = instructions.size();
        int limit = Math.min(startIndex + CONDITIONAL_JUMP_LOOKAHEAD_LIMIT, instructionCount);

        for (int i = startIndex + 1; i < limit; i++) {
            AbstractInsnNode nextInsn = instructions.get(i);
            int nextOpcode = nextInsn.getOpcode();

            if (nextOpcode != -1) {
                if (nextInsn instanceof JumpInsnNode gotoInsn && nextOpcode == GOTO) {
                    return gotoInsn.label;
                }
                break;
            }
        }

        return null;
    }

    /**
     * Returns sink classification for ICONST instructions.
     */
    private LabelClassification classifyIconstInstruction(int opcode) {
        if (opcode == ICONST_0) {
            return FALSE_SINK;
        }
        if (opcode == ICONST_1) {
            return TRUE_SINK;
        }
        return null;
    }
}
