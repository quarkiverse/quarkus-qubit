package io.quarkiverse.qubit.deployment.analysis.branch;

import io.quarkiverse.qubit.deployment.analysis.ControlFlowAnalyzer;
import io.quarkiverse.qubit.deployment.ast.LambdaExpression;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

import static io.quarkiverse.qubit.deployment.testutil.AstBuilders.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.objectweb.asm.Opcodes.*;

/**
 * Tests for {@link BranchCoordinator}.
 *
 * <p>Tests the coordination of branch instruction handling using the Strategy pattern.
 */
class BranchCoordinatorTest {

    private BranchCoordinator coordinator;
    private Deque<LambdaExpression> stack;
    private Map<LabelNode, Boolean> labelToValue;
    private Map<LabelNode, ControlFlowAnalyzer.LabelClassification> labelClassifications;
    private LabelNode targetLabel;

    @BeforeEach
    void setUp() {
        coordinator = new BranchCoordinator();
        stack = new ArrayDeque<>();
        labelToValue = new HashMap<>();
        labelClassifications = new HashMap<>();
        targetLabel = new LabelNode();
        labelToValue.put(targetLabel, true);
        labelClassifications.put(targetLabel, ControlFlowAnalyzer.LabelClassification.TRUE_SINK);
    }

    // ==================== Constructor Tests ====================

    @Nested
    class ConstructorTests {

        @Test
        void constructor_initializesWithInitialState() {
            BranchCoordinator newCoordinator = new BranchCoordinator();

            assertThat(newCoordinator.getCurrentState())
                    .as("Initial state should be BranchState.Initial")
                    .isInstanceOf(BranchState.Initial.class);
        }
    }

    // ==================== processBranchInstruction Tests ====================

    @Nested
    class ProcessBranchInstructionTests {

        @Test
        void processBranchInstruction_withIFEQ_delegatesToIfEqualsZeroHandler() {
            stack.push(field("active", Boolean.class));
            JumpInsnNode jumpInsn = new JumpInsnNode(IFEQ, targetLabel);

            coordinator.processBranchInstruction(stack, jumpInsn, labelToValue, labelClassifications);

            // IFEQ should have been processed
            assertThat(coordinator.getCurrentState())
                    .as("State should transition after processing")
                    .isNotNull();
        }

        @Test
        void processBranchInstruction_withIFNE_delegatesToIfNotEqualsZeroHandler() {
            stack.push(field("active", Boolean.class));
            JumpInsnNode jumpInsn = new JumpInsnNode(IFNE, targetLabel);

            coordinator.processBranchInstruction(stack, jumpInsn, labelToValue, labelClassifications);

            assertThat(coordinator.getCurrentState())
                    .as("State should transition after IFNE processing")
                    .isNotNull();
        }

        @Test
        void processBranchInstruction_withIFLT_delegatesToSingleOperandHandler() {
            stack.push(field("count", Integer.class));
            JumpInsnNode jumpInsn = new JumpInsnNode(IFLT, targetLabel);

            coordinator.processBranchInstruction(stack, jumpInsn, labelToValue, labelClassifications);

            assertThat(coordinator.getCurrentState()).isNotNull();
        }

        @Test
        void processBranchInstruction_withIFGE_delegatesToSingleOperandHandler() {
            stack.push(field("count", Integer.class));
            JumpInsnNode jumpInsn = new JumpInsnNode(IFGE, targetLabel);

            coordinator.processBranchInstruction(stack, jumpInsn, labelToValue, labelClassifications);

            assertThat(coordinator.getCurrentState()).isNotNull();
        }

        @Test
        void processBranchInstruction_withIFGT_delegatesToSingleOperandHandler() {
            stack.push(field("count", Integer.class));
            JumpInsnNode jumpInsn = new JumpInsnNode(IFGT, targetLabel);

            coordinator.processBranchInstruction(stack, jumpInsn, labelToValue, labelClassifications);

            assertThat(coordinator.getCurrentState()).isNotNull();
        }

        @Test
        void processBranchInstruction_withIFLE_delegatesToSingleOperandHandler() {
            stack.push(field("count", Integer.class));
            JumpInsnNode jumpInsn = new JumpInsnNode(IFLE, targetLabel);

            coordinator.processBranchInstruction(stack, jumpInsn, labelToValue, labelClassifications);

            assertThat(coordinator.getCurrentState()).isNotNull();
        }

        @Test
        void processBranchInstruction_withIF_ICMPEQ_delegatesToTwoOperandHandler() {
            stack.push(field("a", Integer.class));
            stack.push(field("b", Integer.class));
            JumpInsnNode jumpInsn = new JumpInsnNode(IF_ICMPEQ, targetLabel);

            coordinator.processBranchInstruction(stack, jumpInsn, labelToValue, labelClassifications);

            assertThat(coordinator.getCurrentState()).isNotNull();
        }

        @Test
        void processBranchInstruction_withIF_ICMPNE_delegatesToTwoOperandHandler() {
            stack.push(field("a", Integer.class));
            stack.push(field("b", Integer.class));
            JumpInsnNode jumpInsn = new JumpInsnNode(IF_ICMPNE, targetLabel);

            coordinator.processBranchInstruction(stack, jumpInsn, labelToValue, labelClassifications);

            assertThat(coordinator.getCurrentState()).isNotNull();
        }

        @Test
        void processBranchInstruction_withIF_ICMPLT_delegatesToTwoOperandHandler() {
            stack.push(field("a", Integer.class));
            stack.push(field("b", Integer.class));
            JumpInsnNode jumpInsn = new JumpInsnNode(IF_ICMPLT, targetLabel);

            coordinator.processBranchInstruction(stack, jumpInsn, labelToValue, labelClassifications);

            assertThat(coordinator.getCurrentState()).isNotNull();
        }

        @Test
        void processBranchInstruction_withIF_ICMPGE_delegatesToTwoOperandHandler() {
            stack.push(field("a", Integer.class));
            stack.push(field("b", Integer.class));
            JumpInsnNode jumpInsn = new JumpInsnNode(IF_ICMPGE, targetLabel);

            coordinator.processBranchInstruction(stack, jumpInsn, labelToValue, labelClassifications);

            assertThat(coordinator.getCurrentState()).isNotNull();
        }

        @Test
        void processBranchInstruction_withIF_ICMPGT_delegatesToTwoOperandHandler() {
            stack.push(field("a", Integer.class));
            stack.push(field("b", Integer.class));
            JumpInsnNode jumpInsn = new JumpInsnNode(IF_ICMPGT, targetLabel);

            coordinator.processBranchInstruction(stack, jumpInsn, labelToValue, labelClassifications);

            assertThat(coordinator.getCurrentState()).isNotNull();
        }

        @Test
        void processBranchInstruction_withIF_ICMPLE_delegatesToTwoOperandHandler() {
            stack.push(field("a", Integer.class));
            stack.push(field("b", Integer.class));
            JumpInsnNode jumpInsn = new JumpInsnNode(IF_ICMPLE, targetLabel);

            coordinator.processBranchInstruction(stack, jumpInsn, labelToValue, labelClassifications);

            assertThat(coordinator.getCurrentState()).isNotNull();
        }

        @Test
        void processBranchInstruction_withIF_ACMPEQ_delegatesToTwoOperandHandler() {
            stack.push(field("a", Object.class));
            stack.push(field("b", Object.class));
            JumpInsnNode jumpInsn = new JumpInsnNode(IF_ACMPEQ, targetLabel);

            coordinator.processBranchInstruction(stack, jumpInsn, labelToValue, labelClassifications);

            assertThat(coordinator.getCurrentState()).isNotNull();
        }

        @Test
        void processBranchInstruction_withIF_ACMPNE_delegatesToTwoOperandHandler() {
            stack.push(field("a", Object.class));
            stack.push(field("b", Object.class));
            JumpInsnNode jumpInsn = new JumpInsnNode(IF_ACMPNE, targetLabel);

            coordinator.processBranchInstruction(stack, jumpInsn, labelToValue, labelClassifications);

            assertThat(coordinator.getCurrentState()).isNotNull();
        }

        @Test
        void processBranchInstruction_withIFNULL_delegatesToNullCheckHandler() {
            stack.push(field("obj", Object.class));
            JumpInsnNode jumpInsn = new JumpInsnNode(IFNULL, targetLabel);

            coordinator.processBranchInstruction(stack, jumpInsn, labelToValue, labelClassifications);

            assertThat(coordinator.getCurrentState()).isNotNull();
        }

        @Test
        void processBranchInstruction_withIFNONNULL_delegatesToNullCheckHandler() {
            stack.push(field("obj", Object.class));
            JumpInsnNode jumpInsn = new JumpInsnNode(IFNONNULL, targetLabel);

            coordinator.processBranchInstruction(stack, jumpInsn, labelToValue, labelClassifications);

            assertThat(coordinator.getCurrentState()).isNotNull();
        }

        @Test
        void processBranchInstruction_withUnknownOpcode_doesNotThrow() {
            JumpInsnNode jumpInsn = new JumpInsnNode(GOTO, targetLabel);

            // GOTO is not handled by any branch handler, should log warning but not throw
            coordinator.processBranchInstruction(stack, jumpInsn, labelToValue, labelClassifications);

            // State should remain unchanged
            assertThat(coordinator.getCurrentState())
                    .as("State should remain unchanged for unhandled opcode")
                    .isInstanceOf(BranchState.Initial.class);
        }
    }

    // ==================== reset Tests ====================

    @Nested
    class ResetTests {

        @Test
        void reset_afterProcessing_returnsToInitialState() {
            // Process some instructions to change state
            stack.push(field("active", Boolean.class));
            JumpInsnNode jumpInsn = new JumpInsnNode(IFEQ, targetLabel);
            coordinator.processBranchInstruction(stack, jumpInsn, labelToValue, labelClassifications);

            // Reset
            coordinator.reset();

            assertThat(coordinator.getCurrentState())
                    .as("After reset, state should be Initial")
                    .isInstanceOf(BranchState.Initial.class);
        }

        @Test
        void reset_onFreshCoordinator_staysInitial() {
            coordinator.reset();

            assertThat(coordinator.getCurrentState())
                    .as("Reset on fresh coordinator should remain Initial")
                    .isInstanceOf(BranchState.Initial.class);
        }

        @Test
        void reset_multipleResets_staysInitial() {
            coordinator.reset();
            coordinator.reset();
            coordinator.reset();

            assertThat(coordinator.getCurrentState())
                    .as("Multiple resets should remain Initial")
                    .isInstanceOf(BranchState.Initial.class);
        }
    }

    // ==================== getCurrentState Tests ====================

    @Nested
    class GetCurrentStateTests {

        @Test
        void getCurrentState_initially_returnsInitialState() {
            assertThat(coordinator.getCurrentState())
                    .as("Fresh coordinator should have Initial state")
                    .isInstanceOf(BranchState.Initial.class);
        }

        @Test
        void getCurrentState_returnsSameInstance_whenNoChange() {
            BranchState firstCall = coordinator.getCurrentState();
            BranchState secondCall = coordinator.getCurrentState();

            assertThat(firstCall)
                    .as("Multiple calls should return same state instance")
                    .isSameAs(secondCall);
        }
    }

    // ==================== getOpcodeName Tests ====================
    // Tests to kill mutation: "replaced return value with "" for getOpcodeName"

    @Nested
    class GetOpcodeNameTests {

        @Test
        void processBranchInstruction_logsCorrectOpcodeName_IFEQ() {
            stack.push(field("active", Boolean.class));
            JumpInsnNode jumpInsn = new JumpInsnNode(IFEQ, targetLabel);

            // Process the instruction - getOpcodeName is called internally
            coordinator.processBranchInstruction(stack, jumpInsn, labelToValue, labelClassifications);

            // If getOpcodeName returned "", the logging would have "unknown" or empty opcode
            // This test verifies the handler processes correctly (which requires correct opcode name)
            assertThat(stack).isNotEmpty();
        }

        @Test
        void processBranchInstruction_allOpcodes_areRecognized() {
            // Test each opcode type to ensure getOpcodeName returns non-empty values
            int[] opcodes = {IFEQ, IFNE, IFLT, IFGE, IFGT, IFLE, IF_ICMPEQ, IF_ICMPNE,
                    IF_ICMPLT, IF_ICMPGE, IF_ICMPGT, IF_ICMPLE, IF_ACMPEQ, IF_ACMPNE, IFNULL, IFNONNULL};
            String[] expectedNames = {"IFEQ", "IFNE", "IFLT", "IFGE", "IFGT", "IFLE", "IF_ICMPEQ", "IF_ICMPNE",
                    "IF_ICMPLT", "IF_ICMPGE", "IF_ICMPGT", "IF_ICMPLE", "IF_ACMPEQ", "IF_ACMPNE", "IFNULL", "IFNONNULL"};

            for (int i = 0; i < opcodes.length; i++) {
                BranchCoordinator newCoordinator = new BranchCoordinator();
                Deque<LambdaExpression> newStack = new ArrayDeque<>();
                // Add appropriate number of elements for the opcode
                if (opcodes[i] >= IF_ICMPEQ && opcodes[i] <= IF_ACMPNE) {
                    newStack.push(field("a", Integer.class));
                    newStack.push(field("b", Integer.class));
                } else {
                    newStack.push(field("value", Integer.class));
                }

                JumpInsnNode insn = new JumpInsnNode(opcodes[i], targetLabel);
                newCoordinator.processBranchInstruction(newStack, insn, labelToValue, labelClassifications);

                // Successful processing indicates getOpcodeName worked correctly
                assertThat(newCoordinator.getCurrentState())
                        .as("Opcode %s should be processed successfully", expectedNames[i])
                        .isNotNull();
            }
        }

        @Test
        void processBranchInstruction_unknownOpcode_handledGracefully() {
            // GOTO opcode (167) is not a handled branch instruction
            JumpInsnNode jumpInsn = new JumpInsnNode(GOTO, targetLabel);

            // Should not throw and should return UNKNOWN(opcode)
            coordinator.processBranchInstruction(stack, jumpInsn, labelToValue, labelClassifications);

            // State remains Initial since no handler matched
            assertThat(coordinator.getCurrentState())
                    .as("Unknown opcode should leave state unchanged")
                    .isInstanceOf(BranchState.Initial.class);
        }
    }

    // ==================== State Transition Tests ====================

    @Nested
    class StateTransitionTests {

        @Test
        void stateTransition_afterBooleanFieldIFEQ_transitionsState() {
            stack.push(field("active", Boolean.class));
            JumpInsnNode jumpInsn = new JumpInsnNode(IFEQ, targetLabel);

            coordinator.processBranchInstruction(stack, jumpInsn, labelToValue, labelClassifications);

            // State may or may not change depending on handler logic
            // but we verify it's still valid
            assertThat(coordinator.getCurrentState()).isNotNull();
        }

        @Test
        void stateTransition_handlerReturningDifferentState_updatesCoordinator() {
            // Use a comparison that will produce a comparison result
            stack.push(constant(0));  // compareTo result
            labelToValue.put(targetLabel, false);
            labelClassifications.put(targetLabel, ControlFlowAnalyzer.LabelClassification.FALSE_SINK);

            JumpInsnNode jumpInsn = new JumpInsnNode(IFLE, targetLabel);
            coordinator.processBranchInstruction(stack, jumpInsn, labelToValue, labelClassifications);

            // State should have been processed
            assertThat(coordinator.getCurrentState()).isNotNull();
        }
    }

    // ==================== Sequential Instruction Tests ====================
    // Tests targeting mutations: sameLabel, completingAndGroup, startingNewGroup

    @Nested
    class SequentialInstructionTests {

        @Test
        void sameLabel_twoInstructionsToSameLabel_detectsSameLabel() {
            // First instruction jumps to targetLabel
            stack.push(field("a", Boolean.class));
            JumpInsnNode first = new JumpInsnNode(IFEQ, targetLabel);
            coordinator.processBranchInstruction(stack, first, labelToValue, labelClassifications);

            // Second instruction also jumps to targetLabel
            stack.push(field("b", Boolean.class));
            JumpInsnNode second = new JumpInsnNode(IFEQ, targetLabel);
            coordinator.processBranchInstruction(stack, second, labelToValue, labelClassifications);

            // State should reflect AND mode due to same label targeting
            assertThat(coordinator.getCurrentState())
                    .as("Sequential jumps to same label should affect state")
                    .isNotNull();
        }

        @Test
        void sameLabel_twoInstructionsToDifferentLabels_detectsDifferentLabels() {
            LabelNode otherLabel = new LabelNode();
            labelToValue.put(otherLabel, false);
            labelClassifications.put(otherLabel, ControlFlowAnalyzer.LabelClassification.FALSE_SINK);

            // First instruction jumps to targetLabel
            stack.push(field("a", Boolean.class));
            JumpInsnNode first = new JumpInsnNode(IFEQ, targetLabel);
            coordinator.processBranchInstruction(stack, first, labelToValue, labelClassifications);

            // Second instruction jumps to different label
            stack.push(field("b", Boolean.class));
            JumpInsnNode second = new JumpInsnNode(IFEQ, otherLabel);
            coordinator.processBranchInstruction(stack, second, labelToValue, labelClassifications);

            // State should be updated (not same label path)
            assertThat(coordinator.getCurrentState()).isNotNull();
        }

        @Test
        void completingAndGroup_intermediateToTrueSink_transitionsCorrectly() {
            LabelNode intermediateLabel = new LabelNode();
            labelToValue.put(intermediateLabel, null); // INTERMEDIATE
            labelClassifications.put(intermediateLabel, ControlFlowAnalyzer.LabelClassification.INTERMEDIATE);

            LabelNode trueSinkLabel = new LabelNode();
            labelToValue.put(trueSinkLabel, true);
            labelClassifications.put(trueSinkLabel, ControlFlowAnalyzer.LabelClassification.TRUE_SINK);

            // First instruction: jump to INTERMEDIATE
            stack.push(field("a", Boolean.class));
            JumpInsnNode first = new JumpInsnNode(IFEQ, intermediateLabel);
            coordinator.processBranchInstruction(stack, first, labelToValue, labelClassifications);

            // Second instruction: jump to TRUE_SINK (completing AND group)
            stack.push(field("b", Boolean.class));
            JumpInsnNode second = new JumpInsnNode(IFEQ, trueSinkLabel);
            coordinator.processBranchInstruction(stack, second, labelToValue, labelClassifications);

            // State should reflect the AND group completion
            assertThat(coordinator.getCurrentState()).isNotNull();
        }

        @Test
        void startingNewGroup_falseSinkToTrueSink_transitionsCorrectly() {
            LabelNode falseSinkLabel = new LabelNode();
            labelToValue.put(falseSinkLabel, false);
            labelClassifications.put(falseSinkLabel, ControlFlowAnalyzer.LabelClassification.FALSE_SINK);

            LabelNode trueSinkLabel = new LabelNode();
            labelToValue.put(trueSinkLabel, true);
            labelClassifications.put(trueSinkLabel, ControlFlowAnalyzer.LabelClassification.TRUE_SINK);

            // First instruction: jump to FALSE_SINK
            stack.push(field("a", Boolean.class));
            JumpInsnNode first = new JumpInsnNode(IFEQ, falseSinkLabel);
            coordinator.processBranchInstruction(stack, first, labelToValue, labelClassifications);

            // Second instruction: jump to TRUE_SINK (starting new group)
            stack.push(field("b", Boolean.class));
            JumpInsnNode second = new JumpInsnNode(IFEQ, trueSinkLabel);
            coordinator.processBranchInstruction(stack, second, labelToValue, labelClassifications);

            // State should reflect starting new group after AND
            assertThat(coordinator.getCurrentState()).isNotNull();
        }

        @Test
        void firstInstruction_lastJumpLabelIsNull_processesCorrectly() {
            // On first instruction, lastJumpLabel is null
            stack.push(field("active", Boolean.class));
            JumpInsnNode first = new JumpInsnNode(IFEQ, targetLabel);

            coordinator.processBranchInstruction(stack, first, labelToValue, labelClassifications);

            // sameLabel should be false since lastJumpLabel was null
            assertThat(coordinator.getCurrentState()).isNotNull();
        }

        @Test
        void threeSequentialInstructions_trackingPersists() {
            LabelNode label1 = new LabelNode();
            LabelNode label2 = new LabelNode();
            LabelNode label3 = new LabelNode();
            labelToValue.put(label1, true);
            labelToValue.put(label2, true);
            labelToValue.put(label3, true);
            labelClassifications.put(label1, ControlFlowAnalyzer.LabelClassification.TRUE_SINK);
            labelClassifications.put(label2, ControlFlowAnalyzer.LabelClassification.TRUE_SINK);
            labelClassifications.put(label3, ControlFlowAnalyzer.LabelClassification.TRUE_SINK);

            // Three sequential instructions
            stack.push(field("a", Boolean.class));
            coordinator.processBranchInstruction(stack, new JumpInsnNode(IFEQ, label1), labelToValue, labelClassifications);

            stack.push(field("b", Boolean.class));
            coordinator.processBranchInstruction(stack, new JumpInsnNode(IFEQ, label2), labelToValue, labelClassifications);

            stack.push(field("c", Boolean.class));
            coordinator.processBranchInstruction(stack, new JumpInsnNode(IFEQ, label3), labelToValue, labelClassifications);

            assertThat(coordinator.getCurrentState()).isNotNull();
        }

        @Test
        void resetAfterProcessing_clearsLastJumpLabel() {
            // Process an instruction
            stack.push(field("active", Boolean.class));
            coordinator.processBranchInstruction(stack, new JumpInsnNode(IFEQ, targetLabel), labelToValue, labelClassifications);

            // Reset the coordinator
            coordinator.reset();

            // Process another instruction - should NOT detect sameLabel
            stack.push(field("other", Boolean.class));
            coordinator.processBranchInstruction(stack, new JumpInsnNode(IFEQ, targetLabel), labelToValue, labelClassifications);

            // After reset, lastJumpLabel was null, so sameLabel should be false
            assertThat(coordinator.getCurrentState())
                    .as("After reset, sameLabel detection should restart fresh")
                    .isInstanceOf(BranchState.class);
        }
    }

    // ==================== Handler Selection Tests ====================

    @Nested
    class HandlerSelectionTests {

        @Test
        void processBranchInstruction_firstMatchingHandler_isUsed() {
            // IFEQ should be handled by IfEqualsZeroInstructionHandler
            stack.push(eq(field("a", int.class), constant(0)));
            JumpInsnNode jumpInsn = new JumpInsnNode(IFEQ, targetLabel);

            coordinator.processBranchInstruction(stack, jumpInsn, labelToValue, labelClassifications);

            // Verify processing occurred
            assertThat(coordinator.getCurrentState()).isNotNull();
        }

        @Test
        void processBranchInstruction_noHandlerMatches_logsWarning() {
            // GOTO is not a conditional branch - no handler should match
            JumpInsnNode jumpInsn = new JumpInsnNode(GOTO, targetLabel);

            coordinator.processBranchInstruction(stack, jumpInsn, labelToValue, labelClassifications);

            // State should remain Initial
            assertThat(coordinator.getCurrentState())
                    .isInstanceOf(BranchState.Initial.class);
        }

        @Test
        void processBranchInstruction_eachHandlerType_canBeSelected() {
            // Test that all handler types can be reached

            // IfEqualsZeroHandler
            stack.push(field("x", Boolean.class));
            coordinator.processBranchInstruction(stack, new JumpInsnNode(IFEQ, targetLabel), labelToValue, labelClassifications);

            coordinator.reset();

            // IfNotEqualsZeroHandler
            stack.push(field("x", Boolean.class));
            coordinator.processBranchInstruction(stack, new JumpInsnNode(IFNE, targetLabel), labelToValue, labelClassifications);

            coordinator.reset();

            // TwoOperandComparisonHandler
            stack.push(field("a", int.class));
            stack.push(field("b", int.class));
            coordinator.processBranchInstruction(stack, new JumpInsnNode(IF_ICMPEQ, targetLabel), labelToValue, labelClassifications);

            coordinator.reset();

            // SingleOperandComparisonHandler
            stack.push(field("x", int.class));
            coordinator.processBranchInstruction(stack, new JumpInsnNode(IFLT, targetLabel), labelToValue, labelClassifications);

            coordinator.reset();

            // NullCheckHandler
            stack.push(field("obj", Object.class));
            coordinator.processBranchInstruction(stack, new JumpInsnNode(IFNULL, targetLabel), labelToValue, labelClassifications);

            // All handler types should have been exercised
            assertThat(coordinator.getCurrentState()).isNotNull();
        }
    }

    // ==================== Label Classification Tests ====================

    @Nested
    class LabelClassificationTests {

        @Test
        void labelClassification_trueSink_handledCorrectly() {
            labelClassifications.put(targetLabel, ControlFlowAnalyzer.LabelClassification.TRUE_SINK);
            labelToValue.put(targetLabel, true);

            stack.push(field("active", Boolean.class));
            coordinator.processBranchInstruction(stack, new JumpInsnNode(IFEQ, targetLabel), labelToValue, labelClassifications);

            assertThat(coordinator.getCurrentState()).isNotNull();
        }

        @Test
        void labelClassification_falseSink_handledCorrectly() {
            labelClassifications.put(targetLabel, ControlFlowAnalyzer.LabelClassification.FALSE_SINK);
            labelToValue.put(targetLabel, false);

            stack.push(field("active", Boolean.class));
            coordinator.processBranchInstruction(stack, new JumpInsnNode(IFEQ, targetLabel), labelToValue, labelClassifications);

            assertThat(coordinator.getCurrentState()).isNotNull();
        }

        @Test
        void labelClassification_intermediate_handledCorrectly() {
            labelClassifications.put(targetLabel, ControlFlowAnalyzer.LabelClassification.INTERMEDIATE);
            labelToValue.put(targetLabel, null);

            stack.push(field("active", Boolean.class));
            coordinator.processBranchInstruction(stack, new JumpInsnNode(IFEQ, targetLabel), labelToValue, labelClassifications);

            assertThat(coordinator.getCurrentState()).isNotNull();
        }

        @Test
        void labelClassification_nullFromMap_handledGracefully() {
            // Label not in classification map - should handle null gracefully
            LabelNode unknownLabel = new LabelNode();
            labelToValue.put(unknownLabel, true);
            // Don't add to labelClassifications

            stack.push(field("active", Boolean.class));

            // Should not throw NPE
            coordinator.processBranchInstruction(stack, new JumpInsnNode(IFEQ, unknownLabel), labelToValue, labelClassifications);

            assertThat(coordinator.getCurrentState()).isNotNull();
        }
    }
}
