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

import static io.quarkus.qusaq.deployment.analysis.BytecodeAnalysisConstants.LABEL_TRACE_DEPTH_LIMIT;
import static org.objectweb.asm.Opcodes.GOTO;
import static org.objectweb.asm.Opcodes.ICONST_0;
import static org.objectweb.asm.Opcodes.ICONST_1;

/**
 * Analyzes control flow patterns to reconstruct boolean expressions from bytecode branches.
 */
public final class ControlFlowAnalyzer {

    private static final Logger log = Logger.getLogger(ControlFlowAnalyzer.class);

    private static final int LABEL_CLASSIFICATION_LOOKAHEAD_LIMIT = 10;
    private static final int CONDITIONAL_JUMP_LOOKAHEAD_LIMIT = 5;

    /**
     * Label role in boolean expression evaluation.
     */
    public enum LabelClassification {
        TRUE_SINK,
        FALSE_SINK,
        INTERMEDIATE
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
     * Classifies a single label by examining following instructions.
     */
    private LabelClassification classifyLabel(LabelNode labelNode, InsnList instructions, int labelIndex, int instructionCount) {
        int limit = Math.min(labelIndex + LABEL_CLASSIFICATION_LOOKAHEAD_LIMIT, instructionCount);

        for (int j = labelIndex + 1; j < limit; j++) {
            AbstractInsnNode next = instructions.get(j);
            int opcode = next.getOpcode();

            if (opcode == ICONST_0) {
                log.debugf("Classified label %s as FALSE_SINK (offset %d)",
                          System.identityHashCode(labelNode), j - labelIndex);
                return LabelClassification.FALSE_SINK;
            }

            if (opcode == ICONST_1) {
                log.debugf("Classified label %s as TRUE_SINK (offset %d)",
                          System.identityHashCode(labelNode), j - labelIndex);
                return LabelClassification.TRUE_SINK;
            }

            if (opcode != -1) {
                log.debugf("Classified label %s as INTERMEDIATE (next opcode: %d at offset %d)",
                          System.identityHashCode(labelNode), opcode, j - labelIndex);
                return LabelClassification.INTERMEDIATE;
            }
        }

        return null;
    }

    /**
     * Maps labels to their boolean destination values.
     */
    public Map<LabelNode, Boolean> traceLabelDestinations(InsnList instructions,
                                                          Map<LabelNode, LabelClassification> classifications) {
        Map<LabelNode, Integer> labelToIndex = new HashMap<>();
        int instructionCount = instructions.size();
        for (int i = 0; i < instructionCount; i++) {
            AbstractInsnNode insn = instructions.get(i);
            if (insn instanceof LabelNode labelNode) {
                labelToIndex.put(labelNode, i);
            }
        }

        Map<LabelNode, Boolean> labelToValue = new HashMap<>();
        classifications.forEach((label, classification) -> {
            if (classification == LabelClassification.TRUE_SINK) {
                labelToValue.put(label, true);
            } else if (classification == LabelClassification.FALSE_SINK) {
                labelToValue.put(label, false);
            } else if (classification == LabelClassification.INTERMEDIATE) {
                LabelClassification destination = traceLabelDestination(
                    label, instructions, labelToIndex, classifications, new HashSet<>());
                if (destination == LabelClassification.TRUE_SINK) {
                    labelToValue.put(label, true);
                    log.debugf("Traced intermediate label %s -> TRUE", System.identityHashCode(label));
                } else if (destination == LabelClassification.FALSE_SINK) {
                    labelToValue.put(label, false);
                    log.debugf("Traced intermediate label %s -> FALSE", System.identityHashCode(label));
                } else {
                    log.debugf("Could not trace label %s to TRUE/FALSE", System.identityHashCode(label));
                }
            }
        });

        return labelToValue;
    }

    /**
     * Traces intermediate label to its ultimate TRUE_SINK or FALSE_SINK destination.
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
        if (classification == LabelClassification.TRUE_SINK || classification == LabelClassification.FALSE_SINK) {
            return classification;
        }
        return null;
    }

    /**
     * Traces label destination starting from a specific instruction index.
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
     * Processes a single instruction during label tracing.
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
     * Looks ahead after a conditional jump to find a subsequent GOTO instruction.
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
            return LabelClassification.FALSE_SINK;
        }
        if (opcode == ICONST_1) {
            return LabelClassification.TRUE_SINK;
        }
        return null;
    }
}
