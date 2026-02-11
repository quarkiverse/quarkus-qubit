package io.quarkiverse.qubit.deployment.analysis;

import static io.quarkiverse.qubit.deployment.analysis.ControlFlowAnalyzer.LabelClassification.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.objectweb.asm.Opcodes.*;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;

import io.quarkiverse.qubit.deployment.analysis.ControlFlowAnalyzer.LabelClassification;

/**
 * Tests for {@link ControlFlowAnalyzer}.
 *
 * <p>
 * Tests control flow pattern analysis for reconstructing boolean expressions
 * from bytecode branches.
 */
class ControlFlowAnalyzerTest {

    private ControlFlowAnalyzer analyzer;

    @BeforeEach
    void setUp() {
        analyzer = new ControlFlowAnalyzer();
    }

    // ==================== LabelClassification Tests ====================

    @Nested
    class LabelClassificationEnumTests {

        @Test
        void trueSink_hasCorrectDisplayName() {
            assertThat(TRUE_SINK.getDisplayName())
                    .as("TRUE_SINK display name")
                    .isEqualTo("TRUE_SINK");
        }

        @Test
        void falseSink_hasCorrectDisplayName() {
            assertThat(FALSE_SINK.getDisplayName())
                    .as("FALSE_SINK display name")
                    .isEqualTo("FALSE_SINK");
        }

        @Test
        void intermediate_hasCorrectDisplayName() {
            assertThat(INTERMEDIATE.getDisplayName())
                    .as("INTERMEDIATE display name")
                    .isEqualTo("INTERMEDIATE");
        }
    }

    // ==================== classifyLabels Tests ====================

    @Nested
    class ClassifyLabelsTests {

        @Test
        void classifyLabels_withEmptyInstructions_returnsEmptyMap() {
            InsnList instructions = new InsnList();

            Map<LabelNode, LabelClassification> result = analyzer.classifyLabels(instructions);

            assertThat(result)
                    .as("Empty instructions should return empty classifications")
                    .isEmpty();
        }

        @Test
        void classifyLabels_withLabelFollowedByIconst0_returnsFalseSink() {
            InsnList instructions = new InsnList();
            LabelNode label = new LabelNode();
            instructions.add(label);
            instructions.add(new InsnNode(ICONST_0));
            instructions.add(new InsnNode(IRETURN));

            Map<LabelNode, LabelClassification> result = analyzer.classifyLabels(instructions);

            assertThat(result)
                    .as("Label followed by ICONST_0 should be classified as FALSE_SINK")
                    .containsEntry(label, FALSE_SINK);
        }

        @Test
        void classifyLabels_withLabelFollowedByIconst1_returnsTrueSink() {
            InsnList instructions = new InsnList();
            LabelNode label = new LabelNode();
            instructions.add(label);
            instructions.add(new InsnNode(ICONST_1));
            instructions.add(new InsnNode(IRETURN));

            Map<LabelNode, LabelClassification> result = analyzer.classifyLabels(instructions);

            assertThat(result)
                    .as("Label followed by ICONST_1 should be classified as TRUE_SINK")
                    .containsEntry(label, TRUE_SINK);
        }

        @Test
        void classifyLabels_withLabelFollowedByOtherOpcode_returnsIntermediate() {
            InsnList instructions = new InsnList();
            LabelNode label = new LabelNode();
            instructions.add(label);
            instructions.add(new InsnNode(ALOAD)); // Not ICONST_0 or ICONST_1
            instructions.add(new InsnNode(ARETURN));

            Map<LabelNode, LabelClassification> result = analyzer.classifyLabels(instructions);

            assertThat(result)
                    .as("Label followed by other opcode should be classified as INTERMEDIATE")
                    .containsEntry(label, INTERMEDIATE);
        }

        @Test
        void classifyLabels_withMultipleLabels_classifiesAll() {
            InsnList instructions = new InsnList();
            LabelNode trueLabel = new LabelNode();
            LabelNode falseLabel = new LabelNode();
            LabelNode intermediateLabel = new LabelNode();

            instructions.add(trueLabel);
            instructions.add(new InsnNode(ICONST_1));
            instructions.add(new InsnNode(GOTO));
            instructions.add(falseLabel);
            instructions.add(new InsnNode(ICONST_0));
            instructions.add(new InsnNode(IRETURN));
            instructions.add(intermediateLabel);
            instructions.add(new InsnNode(DUP));

            Map<LabelNode, LabelClassification> result = analyzer.classifyLabels(instructions);

            assertThat(result)
                    .as("Should classify multiple labels")
                    .containsEntry(trueLabel, TRUE_SINK)
                    .containsEntry(falseLabel, FALSE_SINK)
                    .containsEntry(intermediateLabel, INTERMEDIATE);
        }

        @Test
        void classifyLabels_withLabelAtEnd_returnsEmptyOrNoClassification() {
            InsnList instructions = new InsnList();
            LabelNode label = new LabelNode();
            instructions.add(new InsnNode(NOP));
            instructions.add(label);
            // No instructions follow the label

            Map<LabelNode, LabelClassification> result = analyzer.classifyLabels(instructions);

            // Label at end with no following instructions may not be classified
            assertThat(result.get(label))
                    .as("Label at end with no following instructions")
                    .isNull();
        }

        @Test
        void classifyLabels_withLabelFollowedByAnotherLabel_skipsToNextOpcode() {
            InsnList instructions = new InsnList();
            LabelNode label1 = new LabelNode();
            LabelNode label2 = new LabelNode();
            instructions.add(label1);
            instructions.add(label2); // Label has opcode -1
            instructions.add(new InsnNode(ICONST_1));
            instructions.add(new InsnNode(IRETURN));

            Map<LabelNode, LabelClassification> result = analyzer.classifyLabels(instructions);

            assertThat(result.get(label1))
                    .as("Should look ahead past intermediate labels")
                    .isEqualTo(TRUE_SINK);
        }
    }

    // ==================== traceLabelDestinations Tests ====================

    @Nested
    class TraceLabelDestinationsTests {

        @Test
        void traceLabelDestinations_withTrueSink_returnsTrue() {
            InsnList instructions = new InsnList();
            LabelNode trueLabel = new LabelNode();
            instructions.add(trueLabel);
            instructions.add(new InsnNode(ICONST_1));
            instructions.add(new InsnNode(IRETURN));

            Map<LabelNode, LabelClassification> classifications = analyzer.classifyLabels(instructions);
            Map<LabelNode, Boolean> destinations = analyzer.traceLabelDestinations(instructions, classifications);

            assertThat(destinations)
                    .as("TRUE_SINK should map to true")
                    .containsEntry(trueLabel, true);
        }

        @Test
        void traceLabelDestinations_withFalseSink_returnsFalse() {
            InsnList instructions = new InsnList();
            LabelNode falseLabel = new LabelNode();
            instructions.add(falseLabel);
            instructions.add(new InsnNode(ICONST_0));
            instructions.add(new InsnNode(IRETURN));

            Map<LabelNode, LabelClassification> classifications = analyzer.classifyLabels(instructions);
            Map<LabelNode, Boolean> destinations = analyzer.traceLabelDestinations(instructions, classifications);

            assertThat(destinations)
                    .as("FALSE_SINK should map to false")
                    .containsEntry(falseLabel, false);
        }

        @Test
        void traceLabelDestinations_withIntermediateGotoToTrueSink_returnsTrue() {
            InsnList instructions = new InsnList();
            LabelNode intermediateLabel = new LabelNode();
            LabelNode trueLabel = new LabelNode();

            instructions.add(intermediateLabel);
            instructions.add(new JumpInsnNode(GOTO, trueLabel));
            instructions.add(trueLabel);
            instructions.add(new InsnNode(ICONST_1));
            instructions.add(new InsnNode(IRETURN));

            Map<LabelNode, LabelClassification> classifications = analyzer.classifyLabels(instructions);
            Map<LabelNode, Boolean> destinations = analyzer.traceLabelDestinations(instructions, classifications);

            assertThat(destinations.get(trueLabel))
                    .as("TRUE_SINK should map to true")
                    .isTrue();
            // Intermediate should trace to TRUE_SINK
            assertThat(destinations.get(intermediateLabel))
                    .as("INTERMEDIATE with GOTO to TRUE_SINK should trace to true")
                    .isTrue();
        }

        @Test
        void traceLabelDestinations_withIntermediateGotoToFalseSink_returnsFalse() {
            InsnList instructions = new InsnList();
            LabelNode intermediateLabel = new LabelNode();
            LabelNode falseLabel = new LabelNode();

            instructions.add(intermediateLabel);
            instructions.add(new JumpInsnNode(GOTO, falseLabel));
            instructions.add(falseLabel);
            instructions.add(new InsnNode(ICONST_0));
            instructions.add(new InsnNode(IRETURN));

            Map<LabelNode, LabelClassification> classifications = analyzer.classifyLabels(instructions);
            Map<LabelNode, Boolean> destinations = analyzer.traceLabelDestinations(instructions, classifications);

            assertThat(destinations.get(intermediateLabel))
                    .as("INTERMEDIATE with GOTO to FALSE_SINK should trace to false")
                    .isFalse();
        }

        @Test
        void traceLabelDestinations_withEmptyClassifications_returnsEmptyMap() {
            InsnList instructions = new InsnList();
            instructions.add(new InsnNode(NOP));

            Map<LabelNode, LabelClassification> classifications = Map.of();
            Map<LabelNode, Boolean> destinations = analyzer.traceLabelDestinations(instructions, classifications);

            assertThat(destinations)
                    .as("Empty classifications should return empty destinations")
                    .isEmpty();
        }
    }

    // ==================== Edge Cases ====================

    @Nested
    class EdgeCaseTests {

        @Test
        void classifyLabels_withConditionalJump_handlesCorrectly() {
            InsnList instructions = new InsnList();
            LabelNode condLabel = new LabelNode();
            LabelNode targetLabel = new LabelNode();

            instructions.add(condLabel);
            instructions.add(new JumpInsnNode(IFEQ, targetLabel)); // Conditional jump
            instructions.add(new InsnNode(ICONST_1));
            instructions.add(new InsnNode(IRETURN));
            instructions.add(targetLabel);
            instructions.add(new InsnNode(ICONST_0));
            instructions.add(new InsnNode(IRETURN));

            Map<LabelNode, LabelClassification> result = analyzer.classifyLabels(instructions);

            // condLabel followed by conditional jump should be INTERMEDIATE
            assertThat(result.get(condLabel))
                    .as("Label followed by conditional jump should be INTERMEDIATE")
                    .isEqualTo(INTERMEDIATE);
            assertThat(result.get(targetLabel))
                    .as("Target of conditional jump followed by ICONST_0 should be FALSE_SINK")
                    .isEqualTo(FALSE_SINK);
        }

        @Test
        void traceLabelDestinations_withConditionalJumpToSink_tracesCorrectly() {
            InsnList instructions = new InsnList();
            LabelNode startLabel = new LabelNode();
            LabelNode trueLabel = new LabelNode();

            instructions.add(startLabel);
            instructions.add(new JumpInsnNode(IFNE, trueLabel)); // Conditional jump
            instructions.add(new InsnNode(ICONST_0));
            instructions.add(new InsnNode(IRETURN));
            instructions.add(trueLabel);
            instructions.add(new InsnNode(ICONST_1));
            instructions.add(new InsnNode(IRETURN));

            Map<LabelNode, LabelClassification> classifications = analyzer.classifyLabels(instructions);
            Map<LabelNode, Boolean> destinations = analyzer.traceLabelDestinations(instructions, classifications);

            assertThat(destinations.get(trueLabel))
                    .as("TRUE_SINK should map to true")
                    .isTrue();
        }

        @Test
        void classifyLabels_withLabelFollowedByNop_continuesLookahead() {
            InsnList instructions = new InsnList();
            LabelNode label = new LabelNode();
            instructions.add(label);
            // NOP has opcode -1 according to ASM
            instructions.add(new InsnNode(ICONST_1));
            instructions.add(new InsnNode(IRETURN));

            Map<LabelNode, LabelClassification> result = analyzer.classifyLabels(instructions);

            assertThat(result.get(label))
                    .as("Should classify based on first real opcode")
                    .isEqualTo(TRUE_SINK);
        }

        @Test
        void traceLabelDestinations_withChainedGotos_tracesToFinalDestination() {
            InsnList instructions = new InsnList();
            LabelNode start = new LabelNode();
            LabelNode mid = new LabelNode();
            LabelNode end = new LabelNode();

            instructions.add(start);
            instructions.add(new JumpInsnNode(GOTO, mid));
            instructions.add(mid);
            instructions.add(new JumpInsnNode(GOTO, end));
            instructions.add(end);
            instructions.add(new InsnNode(ICONST_1));
            instructions.add(new InsnNode(IRETURN));

            Map<LabelNode, LabelClassification> classifications = analyzer.classifyLabels(instructions);
            Map<LabelNode, Boolean> destinations = analyzer.traceLabelDestinations(instructions, classifications);

            assertThat(destinations.get(end))
                    .as("Final destination should be true")
                    .isTrue();
            assertThat(destinations.get(mid))
                    .as("Mid label should trace to true")
                    .isTrue();
            assertThat(destinations.get(start))
                    .as("Start label should trace to true through chain")
                    .isTrue();
        }

        @Test
        void traceLabelDestinations_withCycle_doesNotInfiniteLoop() {
            InsnList instructions = new InsnList();
            LabelNode label1 = new LabelNode();
            LabelNode label2 = new LabelNode();

            // Create a cycle: label1 -> GOTO label2, label2 -> GOTO label1
            instructions.add(label1);
            instructions.add(new JumpInsnNode(GOTO, label2));
            instructions.add(label2);
            instructions.add(new JumpInsnNode(GOTO, label1));

            Map<LabelNode, LabelClassification> classifications = analyzer.classifyLabels(instructions);
            // This should not infinite loop due to visited tracking
            Map<LabelNode, Boolean> destinations = analyzer.traceLabelDestinations(instructions, classifications);

            // Both labels are intermediate with cycles, so may not have destination values
            assertThat(destinations.get(label1))
                    .as("Cyclic label should not cause infinite loop")
                    .isNull();
        }

        // ==================== Kill mutations for findSubsequentGotoTarget ====================

        @Test
        void traceLabelDestinations_withConditionalJumpFollowedByGoto_usesGotoTarget() {
            // Test findSubsequentGotoTarget: conditional jump followed immediately by GOTO
            InsnList instructions = new InsnList();
            LabelNode startLabel = new LabelNode();
            LabelNode condTarget = new LabelNode();
            LabelNode gotoTarget = new LabelNode();

            instructions.add(startLabel);
            instructions.add(new JumpInsnNode(IFEQ, condTarget)); // Conditional jump
            instructions.add(new JumpInsnNode(GOTO, gotoTarget)); // Subsequent GOTO
            instructions.add(condTarget);
            instructions.add(new InsnNode(ICONST_0));
            instructions.add(new InsnNode(IRETURN));
            instructions.add(gotoTarget);
            instructions.add(new InsnNode(ICONST_1));
            instructions.add(new InsnNode(IRETURN));

            Map<LabelNode, LabelClassification> classifications = analyzer.classifyLabels(instructions);
            Map<LabelNode, Boolean> destinations = analyzer.traceLabelDestinations(instructions, classifications);

            // The intermediate label should use the GOTO target, not the conditional target
            assertThat(destinations.get(gotoTarget))
                    .as("GOTO target should be TRUE_SINK")
                    .isTrue();
            assertThat(destinations.get(condTarget))
                    .as("Conditional target should be FALSE_SINK")
                    .isFalse();
        }

        @Test
        void traceLabelDestinations_withConditionalJumpNoGoto_usesConditionalTarget() {
            // Test handleConditionalJump: no GOTO after conditional, falls through to condTarget
            InsnList instructions = new InsnList();
            LabelNode startLabel = new LabelNode();
            LabelNode condTarget = new LabelNode();

            instructions.add(startLabel);
            instructions.add(new JumpInsnNode(IFEQ, condTarget)); // Conditional jump
            instructions.add(new InsnNode(ICONST_1)); // No GOTO, just ICONST
            instructions.add(new InsnNode(IRETURN));
            instructions.add(condTarget);
            instructions.add(new InsnNode(ICONST_0));
            instructions.add(new InsnNode(IRETURN));

            Map<LabelNode, LabelClassification> classifications = analyzer.classifyLabels(instructions);
            Map<LabelNode, Boolean> destinations = analyzer.traceLabelDestinations(instructions, classifications);

            assertThat(destinations.get(condTarget))
                    .as("Conditional target should be FALSE_SINK")
                    .isFalse();
            // startLabel is INTERMEDIATE and should trace through conditional jump to false
            assertThat(destinations.get(startLabel))
                    .as("Start label with conditional jump should trace to target")
                    .isFalse();
        }

        // ==================== Kill mutations for traceFromIndex boundary ====================

        @Test
        void traceLabelDestinations_withDeepTrace_tracesToSink() {
            // Test traceFromIndex depth limit check at line 273
            // Labels with opcode -1 are skipped but still count in the loop
            InsnList instructions = new InsnList();
            LabelNode startLabel = new LabelNode();
            LabelNode targetLabel = new LabelNode();

            instructions.add(startLabel);
            instructions.add(new JumpInsnNode(GOTO, targetLabel)); // GOTO to distant label
            // Add intermediate labels (opcode -1, skipped in loops)
            for (int i = 0; i < 5; i++) {
                instructions.add(new LabelNode());
            }
            instructions.add(targetLabel);
            instructions.add(new InsnNode(ICONST_1));
            instructions.add(new InsnNode(IRETURN));

            Map<LabelNode, LabelClassification> classifications = analyzer.classifyLabels(instructions);
            Map<LabelNode, Boolean> destinations = analyzer.traceLabelDestinations(instructions, classifications);

            // INTERMEDIATE label should trace through GOTO to TRUE_SINK
            assertThat(classifications.get(startLabel))
                    .as("Start label with GOTO should be INTERMEDIATE")
                    .isEqualTo(INTERMEDIATE);
            assertThat(destinations.get(startLabel))
                    .as("Should trace through GOTO to true")
                    .isTrue();
        }

        // ==================== Kill mutations for processInstruction return values ====================

        @Test
        void traceLabelDestinations_withIntermediateIconst0_tracesFalse() {
            // Test processInstruction classifyIconstInstruction returning FALSE_SINK
            InsnList instructions = new InsnList();
            LabelNode intermediateLabel = new LabelNode();

            instructions.add(intermediateLabel);
            // No jump, just ICONST_0 directly - this tests processInstruction line 309
            instructions.add(new InsnNode(ICONST_0));
            instructions.add(new InsnNode(IRETURN));

            Map<LabelNode, LabelClassification> classifications = analyzer.classifyLabels(instructions);

            // Label should be classified as FALSE_SINK directly
            assertThat(classifications.get(intermediateLabel))
                    .as("Label followed by ICONST_0 should be FALSE_SINK")
                    .isEqualTo(FALSE_SINK);
        }

        @Test
        void traceLabelDestinations_withIntermediateIconst1_tracesTrue() {
            // Test processInstruction classifyIconstInstruction returning TRUE_SINK
            InsnList instructions = new InsnList();
            LabelNode intermediateLabel = new LabelNode();

            instructions.add(intermediateLabel);
            instructions.add(new InsnNode(ICONST_1));
            instructions.add(new InsnNode(IRETURN));

            Map<LabelNode, LabelClassification> classifications = analyzer.classifyLabels(instructions);

            assertThat(classifications.get(intermediateLabel))
                    .as("Label followed by ICONST_1 should be TRUE_SINK")
                    .isEqualTo(TRUE_SINK);
        }

        // ==================== Kill mutations for getDirectSinkClassification ====================

        @Test
        void traceLabelDestinations_withNonSinkClassification_tracesThrough() {
            // Test getDirectSinkClassification returning null for INTERMEDIATE
            InsnList instructions = new InsnList();
            LabelNode intermediateLabel = new LabelNode();
            LabelNode sinkLabel = new LabelNode();

            instructions.add(intermediateLabel);
            instructions.add(new JumpInsnNode(GOTO, sinkLabel));
            instructions.add(sinkLabel);
            instructions.add(new InsnNode(ICONST_1));
            instructions.add(new InsnNode(IRETURN));

            Map<LabelNode, LabelClassification> classifications = analyzer.classifyLabels(instructions);

            // intermediateLabel is INTERMEDIATE, should trace through to TRUE_SINK
            assertThat(classifications.get(intermediateLabel))
                    .as("Label with GOTO should be INTERMEDIATE")
                    .isEqualTo(INTERMEDIATE);
            assertThat(classifications.get(sinkLabel))
                    .as("Sink label should be TRUE_SINK")
                    .isEqualTo(TRUE_SINK);

            Map<LabelNode, Boolean> destinations = analyzer.traceLabelDestinations(instructions, classifications);
            assertThat(destinations.get(intermediateLabel))
                    .as("INTERMEDIATE label should trace to true")
                    .isTrue();
        }

        // ==================== Kill mutations for traceLabelDestination visited check ====================

        @Test
        void traceLabelDestinations_withSelfReference_returnsNull() {
            // Test traceLabelDestination visited check (line 204)
            InsnList instructions = new InsnList();
            LabelNode selfRefLabel = new LabelNode();

            instructions.add(selfRefLabel);
            instructions.add(new JumpInsnNode(GOTO, selfRefLabel)); // Self-reference

            Map<LabelNode, LabelClassification> classifications = analyzer.classifyLabels(instructions);
            Map<LabelNode, Boolean> destinations = analyzer.traceLabelDestinations(instructions, classifications);

            // Self-reference should hit visited check and return null
            assertThat(destinations.get(selfRefLabel))
                    .as("Self-referencing label should return null")
                    .isNull();
        }

        // ==================== Kill mutations for classifyLabel offset calculation ====================

        @Test
        void classifyLabels_withManyLabelsBeforeIconst_classifiesCorrectly() {
            // Test classifyLabel j - labelIndex offset calculation
            InsnList instructions = new InsnList();
            LabelNode label = new LabelNode();
            LabelNode filler1 = new LabelNode();
            LabelNode filler2 = new LabelNode();

            instructions.add(label); // index 0
            instructions.add(filler1); // index 1 (opcode -1, skipped)
            instructions.add(filler2); // index 2 (opcode -1, skipped)
            instructions.add(new InsnNode(ICONST_1)); // index 3, offset = 3
            instructions.add(new InsnNode(IRETURN));

            Map<LabelNode, LabelClassification> result = analyzer.classifyLabels(instructions);

            assertThat(result.get(label))
                    .as("Label should classify based on ICONST_1 after skipping labels")
                    .isEqualTo(TRUE_SINK);
        }

        // ==================== Kill mutations for findSubsequentGotoTarget with label-only gap ====================

        @Test
        void traceLabelDestinations_withLabelsBetweenCondAndGoto_findsGoto() {
            // Test findSubsequentGotoTarget skipping labels with opcode -1
            InsnList instructions = new InsnList();
            LabelNode startLabel = new LabelNode();
            LabelNode fillerLabel = new LabelNode();
            LabelNode condTarget = new LabelNode();
            LabelNode gotoTarget = new LabelNode();

            instructions.add(startLabel);
            instructions.add(new JumpInsnNode(IFEQ, condTarget)); // Conditional jump at index 1
            instructions.add(fillerLabel); // Label at index 2 (opcode -1, skipped)
            instructions.add(new JumpInsnNode(GOTO, gotoTarget)); // GOTO at index 3
            instructions.add(condTarget);
            instructions.add(new InsnNode(ICONST_0));
            instructions.add(new InsnNode(IRETURN));
            instructions.add(gotoTarget);
            instructions.add(new InsnNode(ICONST_1));
            instructions.add(new InsnNode(IRETURN));

            Map<LabelNode, LabelClassification> classifications = analyzer.classifyLabels(instructions);
            Map<LabelNode, Boolean> destinations = analyzer.traceLabelDestinations(instructions, classifications);

            assertThat(destinations.get(gotoTarget))
                    .as("GOTO target should be TRUE_SINK")
                    .isTrue();
        }

        @Test
        void traceLabelDestinations_withNonGotoAfterConditional_usesConditionalTarget() {
            // Test findSubsequentGotoTarget finding non-GOTO instruction (line 358 break)
            InsnList instructions = new InsnList();
            LabelNode startLabel = new LabelNode();
            LabelNode condTarget = new LabelNode();

            instructions.add(startLabel);
            instructions.add(new JumpInsnNode(IFEQ, condTarget));
            instructions.add(new InsnNode(ALOAD)); // Not a GOTO, triggers break
            instructions.add(new InsnNode(ARETURN));
            instructions.add(condTarget);
            instructions.add(new InsnNode(ICONST_0));
            instructions.add(new InsnNode(IRETURN));

            Map<LabelNode, LabelClassification> classifications = analyzer.classifyLabels(instructions);
            Map<LabelNode, Boolean> destinations = analyzer.traceLabelDestinations(instructions, classifications);

            // Without finding GOTO, handleConditionalJump uses conditional target
            assertThat(destinations.get(condTarget))
                    .as("Conditional target should be FALSE_SINK")
                    .isFalse();
        }

        // ==================== Boundary condition tests for mutation killing ====================

        @Test
        void classifyLabels_atLookaheadLimit_returnsNullClassification() {
            // Test classifyLabel lookahead limit (LABEL_CLASSIFICATION_LOOKAHEAD_LIMIT = 5)
            // Label at end of instructions with not enough following instructions
            InsnList instructions = new InsnList();
            LabelNode label = new LabelNode();

            // Add some instructions before the label
            instructions.add(new InsnNode(NOP));
            instructions.add(new InsnNode(NOP));
            instructions.add(label);
            // Only add labels (opcode -1) after, no real opcodes within lookahead
            instructions.add(new LabelNode());
            instructions.add(new LabelNode());
            instructions.add(new LabelNode());
            instructions.add(new LabelNode());
            instructions.add(new LabelNode());
            // No ICONST_0 or ICONST_1 within limit

            Map<LabelNode, LabelClassification> result = analyzer.classifyLabels(instructions);

            assertThat(result.get(label))
                    .as("Label with only labels (opcode -1) within lookahead should not be classified")
                    .isNull();
        }

        @Test
        void traceLabelDestinations_withLabelNotInMap_returnsNull() {
            // Test traceLabelDestination when labelToIndex.get(label) returns null
            InsnList instructions = new InsnList();
            LabelNode realLabel = new LabelNode();
            LabelNode phantomLabel = new LabelNode(); // Never added to instructions

            instructions.add(realLabel);
            instructions.add(new InsnNode(ICONST_1));
            instructions.add(new InsnNode(IRETURN));

            Map<LabelNode, LabelClassification> classifications = analyzer.classifyLabels(instructions);
            // Manually add the phantom label as INTERMEDIATE to test null handling
            classifications.put(phantomLabel, INTERMEDIATE);

            Map<LabelNode, Boolean> destinations = analyzer.traceLabelDestinations(instructions, classifications);

            // Phantom label is INTERMEDIATE but not in instructions, should not resolve
            assertThat(destinations.get(phantomLabel))
                    .as("Label not in instruction list should not have a destination")
                    .isNull();
        }

        @Test
        void traceLabelDestinations_withDepthLimitExceeded_returnsNull() {
            // Test traceFromIndex depth limit (LABEL_TRACE_DEPTH_LIMIT)
            // Create a long chain that exceeds depth limit without reaching sink
            InsnList instructions = new InsnList();
            LabelNode startLabel = new LabelNode();

            instructions.add(startLabel);
            // Add many non-opcode instructions followed by non-sink opcodes
            for (int i = 0; i < 50; i++) {
                instructions.add(new LabelNode()); // opcode -1, skipped but counts
            }
            // End with a non-sink opcode that doesn't resolve
            instructions.add(new InsnNode(POP));
            instructions.add(new InsnNode(POP));

            Map<LabelNode, LabelClassification> classifications = analyzer.classifyLabels(instructions);
            Map<LabelNode, Boolean> destinations = analyzer.traceLabelDestinations(instructions, classifications);

            // Should hit depth limit and return null
            assertThat(destinations.get(startLabel))
                    .as("Label exceeding trace depth limit should return null")
                    .isNull();
        }

        @Test
        void findSubsequentGotoTarget_atLookaheadLimit_returnsNull() {
            // Test findSubsequentGotoTarget lookahead limit (CONDITIONAL_JUMP_LOOKAHEAD_LIMIT = 3)
            InsnList instructions = new InsnList();
            LabelNode startLabel = new LabelNode();
            LabelNode condTarget = new LabelNode();
            LabelNode distantGoto = new LabelNode();

            instructions.add(startLabel);
            instructions.add(new JumpInsnNode(IFEQ, condTarget));
            // Add labels to push GOTO past lookahead limit
            instructions.add(new LabelNode());
            instructions.add(new LabelNode());
            instructions.add(new LabelNode());
            instructions.add(new LabelNode()); // Past limit of 3
            instructions.add(new JumpInsnNode(GOTO, distantGoto)); // Too far
            instructions.add(condTarget);
            instructions.add(new InsnNode(ICONST_0));
            instructions.add(new InsnNode(IRETURN));
            instructions.add(distantGoto);
            instructions.add(new InsnNode(ICONST_1));
            instructions.add(new InsnNode(IRETURN));

            Map<LabelNode, LabelClassification> classifications = analyzer.classifyLabels(instructions);
            Map<LabelNode, Boolean> destinations = analyzer.traceLabelDestinations(instructions, classifications);

            // GOTO is past lookahead, should use conditional target (FALSE_SINK)
            assertThat(destinations.get(startLabel))
                    .as("Intermediate label should use conditional target when GOTO is past lookahead")
                    .isFalse();
        }
    }
}
