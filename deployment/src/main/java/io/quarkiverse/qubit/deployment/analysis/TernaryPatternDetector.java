package io.quarkiverse.qubit.deployment.analysis;

import io.quarkus.logging.Log;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.objectweb.asm.Opcodes.*;

/**
 * Detects ternary conditional expression patterns in bytecode.
 *
 * <p>Java compiles ternary expressions ({@code condition ? trueExpr : falseExpr}) into:
 * <pre>
 * [condition evaluation]
 * IF_* L_FALSE           // Jump to false branch if condition is false
 * [true branch computation]
 * GOTO L_MERGE           // Skip false branch (KEY SIGNATURE)
 * L_FALSE:
 * [false branch computation]
 * L_MERGE:
 * [result on stack]
 * </pre>
 *
 * <p>This is distinct from boolean expressions which end with ICONST_0/ICONST_1.
 */
public final class TernaryPatternDetector {

    private TernaryPatternDetector() {
    }

    /** Detected ternary expression pattern: IF_* → true branch → GOTO → false branch → merge. */
    public record TernaryPattern(
            int conditionJumpIndex,
            int trueBranchStart,
            int trueBranchEnd,
            int falseBranchStart,
            int falseBranchEnd,
            int mergeIndex,
            JumpInsnNode conditionJump,
            LabelNode falseBranchLabel,
            LabelNode mergeLabel
    ) {
        /** Returns the end index (exclusive) of the entire ternary pattern. */
        public int patternEndIndex() {
            return mergeIndex;
        }
    }

    /** Detects all ternary patterns in the instruction list, ordered by conditionJumpIndex. */
    public static List<TernaryPattern> detectAll(InsnList instructions) {
        List<TernaryPattern> patterns = new ArrayList<>();
        Map<LabelNode, Integer> labelToIndex = buildLabelIndex(instructions);

        int count = instructions.size();
        for (int i = 0; i < count; i++) {
            AbstractInsnNode insn = instructions.get(i);
            if (isConditionalJump(insn)) {
                Optional<TernaryPattern> pattern = detectAt(instructions, i, labelToIndex);
                if (pattern.isPresent()) {
                    patterns.add(pattern.get());
                    Log.debugf("Detected ternary pattern at index %d: true=[%d-%d], false=[%d-%d], merge=%d",
                            i,
                            pattern.get().trueBranchStart(), pattern.get().trueBranchEnd(),
                            pattern.get().falseBranchStart(), pattern.get().falseBranchEnd(),
                            pattern.get().mergeIndex());
                }
            }
        }

        return patterns;
    }

    /** Attempts to detect a ternary pattern starting at the given conditional jump index. */
    public static Optional<TernaryPattern> detectAt(
            InsnList instructions,
            int condJumpIndex,
            Map<LabelNode, Integer> labelToIndex) {

        AbstractInsnNode condInsn = instructions.get(condJumpIndex);
        if (!isConditionalJump(condInsn)) {
            return Optional.empty();
        }

        JumpInsnNode condJump = (JumpInsnNode) condInsn;
        LabelNode falseBranchLabel = condJump.label;

        // Find where the false branch starts
        Integer falseBranchLabelIndex = labelToIndex.get(falseBranchLabel);
        if (falseBranchLabelIndex == null) {
            return Optional.empty();
        }

        // Look for GOTO just before the false branch label - this ends the true branch
        int gotoIndex = findGotoBeforeLabel(instructions, condJumpIndex + 1, falseBranchLabelIndex);
        if (gotoIndex < 0) {
            return Optional.empty();
        }

        JumpInsnNode gotoInsn = (JumpInsnNode) instructions.get(gotoIndex);
        LabelNode mergeLabel = gotoInsn.label;
        Integer mergeIndex = labelToIndex.get(mergeLabel);
        if (mergeIndex == null) {
            return Optional.empty();
        }

        // Skip boolean patterns (ICONST_0/1 before GOTO) — handled by BranchCoordinator
        if (isBooleanPattern(instructions, gotoIndex)) {
            Log.tracef("Skipping boolean pattern at index %d (ICONST before GOTO)", condJumpIndex);
            return Optional.empty();
        }

        // Calculate branch ranges
        int trueBranchStart = skipLabelsAndLineNumbers(instructions, condJumpIndex + 1);
        int trueBranchEnd = gotoIndex - 1; // Exclude GOTO itself
        int falseBranchStart = skipLabelsAndLineNumbers(instructions, falseBranchLabelIndex + 1);
        int falseBranchEnd = mergeIndex - 1; // Exclude merge label

        // Validate ranges
        if (trueBranchStart > trueBranchEnd || falseBranchStart > falseBranchEnd) {
            Log.tracef("Invalid branch ranges at index %d: true=[%d-%d], false=[%d-%d]",
                    condJumpIndex, trueBranchStart, trueBranchEnd, falseBranchStart, falseBranchEnd);
            return Optional.empty();
        }

        return Optional.of(new TernaryPattern(
                condJumpIndex,
                trueBranchStart,
                trueBranchEnd,
                falseBranchStart,
                falseBranchEnd,
                mergeIndex,
                condJump,
                falseBranchLabel,
                mergeLabel
        ));
    }

    /**
     * Finds a GOTO instruction between startIndex (exclusive) and labelIndex (exclusive).
     * Returns the index of GOTO if found, -1 otherwise.
     */
    private static int findGotoBeforeLabel(InsnList instructions, int startIndex, int labelIndex) {
        // Search backwards from label to find GOTO
        for (int i = labelIndex - 1; i >= startIndex; i--) {
            AbstractInsnNode insn = instructions.get(i);
            int opcode = insn.getOpcode();

            if (opcode == GOTO) {
                return i;
            }

            // Skip pseudo-instructions (labels, line numbers, frames)
            if (opcode == -1) {
                continue;
            }

            // Found a real instruction that's not GOTO - not a ternary pattern
            // (In a ternary, GOTO must be immediately before the false branch label)
            break;
        }
        return -1;
    }

    /** Checks if ICONST_0 or ICONST_1 appears before GOTO (boolean expression pattern). */
    private static boolean isBooleanPattern(InsnList instructions, int gotoIndex) {
        for (int i = gotoIndex - 1; i >= 0; i--) {
            int opcode = instructions.get(i).getOpcode();
            if (opcode == -1) continue;
            return opcode == ICONST_0 || opcode == ICONST_1;
        }
        return false;
    }

    /**
     * Skips label nodes and line number nodes to find the first real instruction.
     */
    private static int skipLabelsAndLineNumbers(InsnList instructions, int startIndex) {
        int count = instructions.size();
        for (int i = startIndex; i < count; i++) {
            AbstractInsnNode insn = instructions.get(i);
            if (insn.getOpcode() != -1) {
                return i;
            }
        }
        return startIndex;
    }

    /**
     * Checks if the instruction is a conditional jump (IF_* family).
     */
    private static boolean isConditionalJump(AbstractInsnNode insn) {
        if (!(insn instanceof JumpInsnNode)) {
            return false;
        }
        int opcode = insn.getOpcode();
        return opcode == IFEQ || opcode == IFNE || opcode == IFLT || opcode == IFGE ||
               opcode == IFGT || opcode == IFLE || opcode == IF_ICMPEQ || opcode == IF_ICMPNE ||
               opcode == IF_ICMPLT || opcode == IF_ICMPGE || opcode == IF_ICMPGT ||
               opcode == IF_ICMPLE || opcode == IF_ACMPEQ || opcode == IF_ACMPNE ||
               opcode == IFNULL || opcode == IFNONNULL;
    }

    /**
     * Builds a map from labels to their instruction indices.
     */
    private static Map<LabelNode, Integer> buildLabelIndex(InsnList instructions) {
        Map<LabelNode, Integer> labelToIndex = new HashMap<>();
        int count = instructions.size();
        for (int i = 0; i < count; i++) {
            AbstractInsnNode insn = instructions.get(i);
            if (insn instanceof LabelNode label) {
                labelToIndex.put(label, i);
            }
        }
        return labelToIndex;
    }

    /** Finds a ternary pattern that starts at the given instruction index. */
    public static Optional<TernaryPattern> findPatternStartingAt(
            List<TernaryPattern> patterns,
            int instructionIndex) {
        for (TernaryPattern pattern : patterns) {
            if (pattern.conditionJumpIndex() == instructionIndex) {
                return Optional.of(pattern);
            }
        }
        return Optional.empty();
    }

    /**
     * Checks if the given instruction index is within any ternary pattern.
     * Used to skip instructions that are part of a ternary being processed.
     */
    public static boolean isWithinPattern(List<TernaryPattern> patterns, int instructionIndex) {
        for (TernaryPattern pattern : patterns) {
            if (instructionIndex >= pattern.conditionJumpIndex() &&
                instructionIndex < pattern.patternEndIndex()) {
                return true;
            }
        }
        return false;
    }
}
