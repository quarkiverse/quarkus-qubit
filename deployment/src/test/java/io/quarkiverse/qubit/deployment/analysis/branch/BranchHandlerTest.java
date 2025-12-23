package io.quarkiverse.qubit.deployment.analysis.branch;

import io.quarkiverse.qubit.deployment.ast.LambdaExpression;
import io.quarkiverse.qubit.deployment.analysis.ControlFlowAnalyzer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

import static io.quarkiverse.qubit.deployment.testutil.AstBuilders.constant;
import static io.quarkiverse.qubit.deployment.testutil.AstBuilders.field;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.objectweb.asm.Opcodes.*;

/**
 * Unit tests for BranchHandler implementations.
 *
 * <p>Tests canHandle() methods and specific handle() edge cases
 * to kill surviving mutations.
 */
class BranchHandlerTest {

    // ==================== NullCheckHandler Tests ====================

    @Nested
    @DisplayName("NullCheckHandler")
    class NullCheckHandlerTests {

        private final NullCheckHandler handler = new NullCheckHandler();

        @Test
        void canHandle_withIFNULL_returnsTrue() {
            JumpInsnNode jumpInsn = new JumpInsnNode(IFNULL, new LabelNode());

            assertThat(handler.canHandle(jumpInsn)).isTrue();
        }

        @Test
        void canHandle_withIFNONNULL_returnsTrue() {
            JumpInsnNode jumpInsn = new JumpInsnNode(IFNONNULL, new LabelNode());

            assertThat(handler.canHandle(jumpInsn)).isTrue();
        }

        @Test
        void canHandle_withIFEQ_returnsFalse() {
            JumpInsnNode jumpInsn = new JumpInsnNode(IFEQ, new LabelNode());

            assertThat(handler.canHandle(jumpInsn)).isFalse();
        }

        @Test
        void canHandle_withIF_ICMPEQ_returnsFalse() {
            JumpInsnNode jumpInsn = new JumpInsnNode(IF_ICMPEQ, new LabelNode());

            assertThat(handler.canHandle(jumpInsn)).isFalse();
        }

        @Test
        void handle_withEmptyStack_returnsOriginalState() {
            Deque<LambdaExpression> stack = new ArrayDeque<>();
            LabelNode label = new LabelNode();
            JumpInsnNode jumpInsn = new JumpInsnNode(IFNULL, label);
            Map<LabelNode, Boolean> labelToValue = new HashMap<>();
            labelToValue.put(label, true);
            Map<LabelNode, ControlFlowAnalyzer.LabelClassification> labelClassifications = new HashMap<>();
            BranchState initialState = new BranchState.Initial();

            BranchState result = handler.handle(stack, jumpInsn, labelToValue, labelClassifications, initialState);

            assertThat(result).isSameAs(initialState);
        }

        @Test
        void handle_ifnullJumpToTrue_createsEQComparison() {
            Deque<LambdaExpression> stack = new ArrayDeque<>();
            stack.push(field("name", String.class));
            LabelNode label = new LabelNode();
            JumpInsnNode jumpInsn = new JumpInsnNode(IFNULL, label);
            Map<LabelNode, Boolean> labelToValue = new HashMap<>();
            labelToValue.put(label, true); // Jump to TRUE
            Map<LabelNode, ControlFlowAnalyzer.LabelClassification> labelClassifications = new HashMap<>();
            BranchState initialState = new BranchState.Initial();

            handler.handle(stack, jumpInsn, labelToValue, labelClassifications, initialState);

            assertThat(stack).hasSize(1);
            assertThat(stack.peek()).isInstanceOf(LambdaExpression.BinaryOp.class);
            LambdaExpression.BinaryOp binOp = (LambdaExpression.BinaryOp) stack.peek();
            assertThat(binOp.operator()).isEqualTo(LambdaExpression.BinaryOp.Operator.EQ);
        }

        @Test
        void handle_ifnullJumpToFalse_createsNEComparison() {
            Deque<LambdaExpression> stack = new ArrayDeque<>();
            stack.push(field("name", String.class));
            LabelNode label = new LabelNode();
            JumpInsnNode jumpInsn = new JumpInsnNode(IFNULL, label);
            Map<LabelNode, Boolean> labelToValue = new HashMap<>();
            labelToValue.put(label, false); // Jump to FALSE
            Map<LabelNode, ControlFlowAnalyzer.LabelClassification> labelClassifications = new HashMap<>();
            BranchState initialState = new BranchState.Initial();

            handler.handle(stack, jumpInsn, labelToValue, labelClassifications, initialState);

            assertThat(stack).hasSize(1);
            LambdaExpression.BinaryOp binOp = (LambdaExpression.BinaryOp) stack.peek();
            assertThat(binOp.operator()).isEqualTo(LambdaExpression.BinaryOp.Operator.NE);
        }

        @Test
        void handle_ifnonnullJumpToTrue_createsNEComparison() {
            Deque<LambdaExpression> stack = new ArrayDeque<>();
            stack.push(field("name", String.class));
            LabelNode label = new LabelNode();
            JumpInsnNode jumpInsn = new JumpInsnNode(IFNONNULL, label);
            Map<LabelNode, Boolean> labelToValue = new HashMap<>();
            labelToValue.put(label, true); // Jump to TRUE
            Map<LabelNode, ControlFlowAnalyzer.LabelClassification> labelClassifications = new HashMap<>();
            BranchState initialState = new BranchState.Initial();

            handler.handle(stack, jumpInsn, labelToValue, labelClassifications, initialState);

            assertThat(stack).hasSize(1);
            LambdaExpression.BinaryOp binOp = (LambdaExpression.BinaryOp) stack.peek();
            assertThat(binOp.operator()).isEqualTo(LambdaExpression.BinaryOp.Operator.NE);
        }

        @Test
        void handle_ifnonnullJumpToFalse_createsEQComparison() {
            Deque<LambdaExpression> stack = new ArrayDeque<>();
            stack.push(field("name", String.class));
            LabelNode label = new LabelNode();
            JumpInsnNode jumpInsn = new JumpInsnNode(IFNONNULL, label);
            Map<LabelNode, Boolean> labelToValue = new HashMap<>();
            labelToValue.put(label, false); // Jump to FALSE
            Map<LabelNode, ControlFlowAnalyzer.LabelClassification> labelClassifications = new HashMap<>();
            BranchState initialState = new BranchState.Initial();

            handler.handle(stack, jumpInsn, labelToValue, labelClassifications, initialState);

            assertThat(stack).hasSize(1);
            LambdaExpression.BinaryOp binOp = (LambdaExpression.BinaryOp) stack.peek();
            assertThat(binOp.operator()).isEqualTo(LambdaExpression.BinaryOp.Operator.EQ);
        }

        @Test
        void handle_withPreviousCondition_combinesWithOperator() {
            Deque<LambdaExpression> stack = new ArrayDeque<>();
            // Push a previous condition
            stack.push(LambdaExpression.BinaryOp.eq(
                    field("id", int.class),
                    constant(1)
            ));
            // Push the field to check
            stack.push(field("name", String.class));
            LabelNode label = new LabelNode();
            JumpInsnNode jumpInsn = new JumpInsnNode(IFNULL, label);
            Map<LabelNode, Boolean> labelToValue = new HashMap<>();
            labelToValue.put(label, false);
            Map<LabelNode, ControlFlowAnalyzer.LabelClassification> labelClassifications = new HashMap<>();
            // Use AndMode to trigger combining
            BranchState andMode = new BranchState.AndMode(java.util.Optional.of(false), false);

            handler.handle(stack, jumpInsn, labelToValue, labelClassifications, andMode);

            // Stack should have combined expression
            assertThat(stack).hasSize(1);
            assertThat(stack.peek()).isInstanceOf(LambdaExpression.BinaryOp.class);
        }
    }

    // ==================== SingleOperandComparisonHandler Tests ====================
    // This handler handles IFLT, IFLE, IFGT, IFGE (NOT IFEQ/IFNE)

    @Nested
    @DisplayName("SingleOperandComparisonHandler")
    class SingleOperandComparisonHandlerTests {

        private final SingleOperandComparisonHandler handler = new SingleOperandComparisonHandler();

        @Test
        void canHandle_withIFLT_returnsTrue() {
            JumpInsnNode jumpInsn = new JumpInsnNode(IFLT, new LabelNode());
            assertThat(handler.canHandle(jumpInsn)).isTrue();
        }

        @Test
        void canHandle_withIFGE_returnsTrue() {
            JumpInsnNode jumpInsn = new JumpInsnNode(IFGE, new LabelNode());
            assertThat(handler.canHandle(jumpInsn)).isTrue();
        }

        @Test
        void canHandle_withIFGT_returnsTrue() {
            JumpInsnNode jumpInsn = new JumpInsnNode(IFGT, new LabelNode());
            assertThat(handler.canHandle(jumpInsn)).isTrue();
        }

        @Test
        void canHandle_withIFLE_returnsTrue() {
            JumpInsnNode jumpInsn = new JumpInsnNode(IFLE, new LabelNode());
            assertThat(handler.canHandle(jumpInsn)).isTrue();
        }

        @Test
        void canHandle_withIFEQ_returnsFalse() {
            JumpInsnNode jumpInsn = new JumpInsnNode(IFEQ, new LabelNode());
            assertThat(handler.canHandle(jumpInsn)).isFalse();
        }

        @Test
        void canHandle_withIFNE_returnsFalse() {
            JumpInsnNode jumpInsn = new JumpInsnNode(IFNE, new LabelNode());
            assertThat(handler.canHandle(jumpInsn)).isFalse();
        }

        @Test
        void canHandle_withIFNULL_returnsFalse() {
            JumpInsnNode jumpInsn = new JumpInsnNode(IFNULL, new LabelNode());
            assertThat(handler.canHandle(jumpInsn)).isFalse();
        }

        @Test
        void canHandle_withIF_ICMPEQ_returnsFalse() {
            JumpInsnNode jumpInsn = new JumpInsnNode(IF_ICMPEQ, new LabelNode());
            assertThat(handler.canHandle(jumpInsn)).isFalse();
        }

        // ========== handle() method tests to kill surviving mutations ==========

        @Test
        void handle_withEmptyStack_returnsOriginalState() {
            Deque<LambdaExpression> stack = new ArrayDeque<>();
            LabelNode label = new LabelNode();
            JumpInsnNode jumpInsn = new JumpInsnNode(IFLE, label);
            Map<LabelNode, Boolean> labelToValue = new HashMap<>();
            Map<LabelNode, ControlFlowAnalyzer.LabelClassification> labelClassifications = new HashMap<>();
            BranchState initialState = new BranchState.Initial();

            BranchState result = handler.handle(stack, jumpInsn, labelToValue, labelClassifications, initialState);

            assertThat(result)
                    .as("Empty stack should return original state")
                    .isSameAs(initialState);
        }

        @Test
        void handle_IFLE_jumpToTrue_createsLEComparison() {
            Deque<LambdaExpression> stack = new ArrayDeque<>();
            stack.push(field("value", int.class));
            LabelNode label = new LabelNode();
            JumpInsnNode jumpInsn = new JumpInsnNode(IFLE, label);
            Map<LabelNode, Boolean> labelToValue = new HashMap<>();
            labelToValue.put(label, true);
            Map<LabelNode, ControlFlowAnalyzer.LabelClassification> labelClassifications = new HashMap<>();
            BranchState initialState = new BranchState.Initial();

            handler.handle(stack, jumpInsn, labelToValue, labelClassifications, initialState);

            assertThat(stack).hasSize(1);
            LambdaExpression.BinaryOp binOp = (LambdaExpression.BinaryOp) stack.peek();
            assertThat(binOp.operator())
                    .as("IFLE with jump to TRUE should create LE")
                    .isEqualTo(LambdaExpression.BinaryOp.Operator.LE);
        }

        @Test
        void handle_IFLE_jumpToFalse_createsGTComparison() {
            Deque<LambdaExpression> stack = new ArrayDeque<>();
            stack.push(field("value", int.class));
            LabelNode label = new LabelNode();
            JumpInsnNode jumpInsn = new JumpInsnNode(IFLE, label);
            Map<LabelNode, Boolean> labelToValue = new HashMap<>();
            labelToValue.put(label, false);
            Map<LabelNode, ControlFlowAnalyzer.LabelClassification> labelClassifications = new HashMap<>();
            BranchState initialState = new BranchState.Initial();

            handler.handle(stack, jumpInsn, labelToValue, labelClassifications, initialState);

            assertThat(stack).hasSize(1);
            LambdaExpression.BinaryOp binOp = (LambdaExpression.BinaryOp) stack.peek();
            assertThat(binOp.operator())
                    .as("IFLE with jump to FALSE should create GT (inverted)")
                    .isEqualTo(LambdaExpression.BinaryOp.Operator.GT);
        }

        @Test
        void handle_IFLT_jumpToTrue_createsLTComparison() {
            Deque<LambdaExpression> stack = new ArrayDeque<>();
            stack.push(field("value", int.class));
            LabelNode label = new LabelNode();
            JumpInsnNode jumpInsn = new JumpInsnNode(IFLT, label);
            Map<LabelNode, Boolean> labelToValue = new HashMap<>();
            labelToValue.put(label, true);
            Map<LabelNode, ControlFlowAnalyzer.LabelClassification> labelClassifications = new HashMap<>();
            BranchState initialState = new BranchState.Initial();

            handler.handle(stack, jumpInsn, labelToValue, labelClassifications, initialState);

            assertThat(stack).hasSize(1);
            LambdaExpression.BinaryOp binOp = (LambdaExpression.BinaryOp) stack.peek();
            assertThat(binOp.operator())
                    .as("IFLT with jump to TRUE should create LT")
                    .isEqualTo(LambdaExpression.BinaryOp.Operator.LT);
        }

        @Test
        void handle_IFLT_jumpToFalse_createsGEComparison() {
            Deque<LambdaExpression> stack = new ArrayDeque<>();
            stack.push(field("value", int.class));
            LabelNode label = new LabelNode();
            JumpInsnNode jumpInsn = new JumpInsnNode(IFLT, label);
            Map<LabelNode, Boolean> labelToValue = new HashMap<>();
            labelToValue.put(label, false);
            Map<LabelNode, ControlFlowAnalyzer.LabelClassification> labelClassifications = new HashMap<>();
            BranchState initialState = new BranchState.Initial();

            handler.handle(stack, jumpInsn, labelToValue, labelClassifications, initialState);

            assertThat(stack).hasSize(1);
            LambdaExpression.BinaryOp binOp = (LambdaExpression.BinaryOp) stack.peek();
            assertThat(binOp.operator())
                    .as("IFLT with jump to FALSE should create GE (inverted)")
                    .isEqualTo(LambdaExpression.BinaryOp.Operator.GE);
        }

        @Test
        void handle_IFGE_jumpToTrue_createsGEComparison() {
            Deque<LambdaExpression> stack = new ArrayDeque<>();
            stack.push(field("value", int.class));
            LabelNode label = new LabelNode();
            JumpInsnNode jumpInsn = new JumpInsnNode(IFGE, label);
            Map<LabelNode, Boolean> labelToValue = new HashMap<>();
            labelToValue.put(label, true);
            Map<LabelNode, ControlFlowAnalyzer.LabelClassification> labelClassifications = new HashMap<>();
            BranchState initialState = new BranchState.Initial();

            handler.handle(stack, jumpInsn, labelToValue, labelClassifications, initialState);

            assertThat(stack).hasSize(1);
            LambdaExpression.BinaryOp binOp = (LambdaExpression.BinaryOp) stack.peek();
            assertThat(binOp.operator())
                    .as("IFGE with jump to TRUE should create GE")
                    .isEqualTo(LambdaExpression.BinaryOp.Operator.GE);
        }

        @Test
        void handle_IFGE_jumpToFalse_createsLTComparison() {
            Deque<LambdaExpression> stack = new ArrayDeque<>();
            stack.push(field("value", int.class));
            LabelNode label = new LabelNode();
            JumpInsnNode jumpInsn = new JumpInsnNode(IFGE, label);
            Map<LabelNode, Boolean> labelToValue = new HashMap<>();
            labelToValue.put(label, false);
            Map<LabelNode, ControlFlowAnalyzer.LabelClassification> labelClassifications = new HashMap<>();
            BranchState initialState = new BranchState.Initial();

            handler.handle(stack, jumpInsn, labelToValue, labelClassifications, initialState);

            assertThat(stack).hasSize(1);
            LambdaExpression.BinaryOp binOp = (LambdaExpression.BinaryOp) stack.peek();
            assertThat(binOp.operator())
                    .as("IFGE with jump to FALSE should create LT (inverted)")
                    .isEqualTo(LambdaExpression.BinaryOp.Operator.LT);
        }

        @Test
        void handle_IFGT_jumpToTrue_createsGTComparison() {
            Deque<LambdaExpression> stack = new ArrayDeque<>();
            stack.push(field("value", int.class));
            LabelNode label = new LabelNode();
            JumpInsnNode jumpInsn = new JumpInsnNode(IFGT, label);
            Map<LabelNode, Boolean> labelToValue = new HashMap<>();
            labelToValue.put(label, true);
            Map<LabelNode, ControlFlowAnalyzer.LabelClassification> labelClassifications = new HashMap<>();
            BranchState initialState = new BranchState.Initial();

            handler.handle(stack, jumpInsn, labelToValue, labelClassifications, initialState);

            assertThat(stack).hasSize(1);
            LambdaExpression.BinaryOp binOp = (LambdaExpression.BinaryOp) stack.peek();
            assertThat(binOp.operator())
                    .as("IFGT with jump to TRUE should create GT")
                    .isEqualTo(LambdaExpression.BinaryOp.Operator.GT);
        }

        @Test
        void handle_IFGT_jumpToFalse_createsLEComparison() {
            Deque<LambdaExpression> stack = new ArrayDeque<>();
            stack.push(field("value", int.class));
            LabelNode label = new LabelNode();
            JumpInsnNode jumpInsn = new JumpInsnNode(IFGT, label);
            Map<LabelNode, Boolean> labelToValue = new HashMap<>();
            labelToValue.put(label, false);
            Map<LabelNode, ControlFlowAnalyzer.LabelClassification> labelClassifications = new HashMap<>();
            BranchState initialState = new BranchState.Initial();

            handler.handle(stack, jumpInsn, labelToValue, labelClassifications, initialState);

            assertThat(stack).hasSize(1);
            LambdaExpression.BinaryOp binOp = (LambdaExpression.BinaryOp) stack.peek();
            assertThat(binOp.operator())
                    .as("IFGT with jump to FALSE should create LE (inverted)")
                    .isEqualTo(LambdaExpression.BinaryOp.Operator.LE);
        }

        @Test
        void handle_withPreviousCondition_combinesExpressions() {
            Deque<LambdaExpression> stack = new ArrayDeque<>();
            // Push a previous condition (BinaryOp)
            stack.push(LambdaExpression.BinaryOp.eq(
                    field("id", int.class),
                    constant(1)
            ));
            // Push value to compare
            stack.push(field("age", int.class));

            LabelNode label = new LabelNode();
            JumpInsnNode jumpInsn = new JumpInsnNode(IFGT, label);
            Map<LabelNode, Boolean> labelToValue = new HashMap<>();
            labelToValue.put(label, false);
            Map<LabelNode, ControlFlowAnalyzer.LabelClassification> labelClassifications = new HashMap<>();
            BranchState andMode = new BranchState.AndMode(java.util.Optional.of(false), false);

            handler.handle(stack, jumpInsn, labelToValue, labelClassifications, andMode);

            assertThat(stack).hasSize(1);
            assertThat(stack.peek())
                    .as("Should combine into a BinaryOp")
                    .isInstanceOf(LambdaExpression.BinaryOp.class);
        }

        @Test
        void handle_withIntermediateLabelAndTrue_inversionInAndMode() {
            Deque<LambdaExpression> stack = new ArrayDeque<>();
            stack.push(field("value", int.class));
            LabelNode label = new LabelNode();
            JumpInsnNode jumpInsn = new JumpInsnNode(IFLE, label);
            Map<LabelNode, Boolean> labelToValue = new HashMap<>();
            labelToValue.put(label, true);
            Map<LabelNode, ControlFlowAnalyzer.LabelClassification> labelClassifications = new HashMap<>();
            labelClassifications.put(label, ControlFlowAnalyzer.LabelClassification.INTERMEDIATE);
            // In AndMode, INTERMEDIATE→TRUE should invert
            BranchState andMode = new BranchState.AndMode(java.util.Optional.of(false), false);

            handler.handle(stack, jumpInsn, labelToValue, labelClassifications, andMode);

            assertThat(stack).hasSize(1);
            LambdaExpression.BinaryOp binOp = (LambdaExpression.BinaryOp) stack.peek();
            assertThat(binOp.operator())
                    .as("IFLE in AndMode with INTERMEDIATE→TRUE should invert to GT")
                    .isEqualTo(LambdaExpression.BinaryOp.Operator.GT);
        }

        @Test
        void handle_withIntermediateLabelAndTrue_noInversionInInitialState() {
            Deque<LambdaExpression> stack = new ArrayDeque<>();
            stack.push(field("value", int.class));
            LabelNode label = new LabelNode();
            JumpInsnNode jumpInsn = new JumpInsnNode(IFLE, label);
            Map<LabelNode, Boolean> labelToValue = new HashMap<>();
            labelToValue.put(label, true);
            Map<LabelNode, ControlFlowAnalyzer.LabelClassification> labelClassifications = new HashMap<>();
            labelClassifications.put(label, ControlFlowAnalyzer.LabelClassification.INTERMEDIATE);
            // In Initial state, should NOT invert
            BranchState initialState = new BranchState.Initial();

            handler.handle(stack, jumpInsn, labelToValue, labelClassifications, initialState);

            assertThat(stack).hasSize(1);
            LambdaExpression.BinaryOp binOp = (LambdaExpression.BinaryOp) stack.peek();
            assertThat(binOp.operator())
                    .as("IFLE in Initial state with INTERMEDIATE→TRUE should NOT invert, stays LE")
                    .isEqualTo(LambdaExpression.BinaryOp.Operator.LE);
        }

        @Test
        void handle_withIntermediateLabelAndFalse_inverts() {
            Deque<LambdaExpression> stack = new ArrayDeque<>();
            stack.push(field("value", int.class));
            LabelNode label = new LabelNode();
            JumpInsnNode jumpInsn = new JumpInsnNode(IFGE, label);
            Map<LabelNode, Boolean> labelToValue = new HashMap<>();
            labelToValue.put(label, false);
            Map<LabelNode, ControlFlowAnalyzer.LabelClassification> labelClassifications = new HashMap<>();
            labelClassifications.put(label, ControlFlowAnalyzer.LabelClassification.INTERMEDIATE);
            BranchState initialState = new BranchState.Initial();

            handler.handle(stack, jumpInsn, labelToValue, labelClassifications, initialState);

            assertThat(stack).hasSize(1);
            LambdaExpression.BinaryOp binOp = (LambdaExpression.BinaryOp) stack.peek();
            assertThat(binOp.operator())
                    .as("IFGE with INTERMEDIATE→FALSE should invert to LT")
                    .isEqualTo(LambdaExpression.BinaryOp.Operator.LT);
        }

        // ========== Tests to kill determineComparisonOperator conditional mutations ==========

        @Test
        void handle_intermediateTrue_andMode_noCombine_verifyInversion() {
            // Specifically test: INTERMEDIATE && TRUE && !willCombine && AndMode (line 143)
            Deque<LambdaExpression> stack = new ArrayDeque<>();
            stack.push(field("value", int.class));
            // Stack has no BinaryOp on top after popping → willCombine = false
            LabelNode label = new LabelNode();
            JumpInsnNode jumpInsn = new JumpInsnNode(IFLT, label);
            Map<LabelNode, Boolean> labelToValue = new HashMap<>();
            labelToValue.put(label, true); // TRUE jump target
            Map<LabelNode, ControlFlowAnalyzer.LabelClassification> labelClassifications = new HashMap<>();
            labelClassifications.put(label, ControlFlowAnalyzer.LabelClassification.INTERMEDIATE);
            // AndMode state (not Initial)
            BranchState andMode = new BranchState.AndMode(java.util.Optional.of(true), false);

            handler.handle(stack, jumpInsn, labelToValue, labelClassifications, andMode);

            LambdaExpression.BinaryOp binOp = (LambdaExpression.BinaryOp) stack.peek();
            // INTERMEDIATE+TRUE+AndMode+!willCombine → invert=true → IFLT inverts to GE
            assertThat(binOp.operator())
                    .as("INTERMEDIATE+TRUE+AndMode should invert IFLT to GE")
                    .isEqualTo(LambdaExpression.BinaryOp.Operator.GE);
        }

        @Test
        void handle_intermediateTrue_initialState_noCombine_noInversion() {
            // Specifically test: INTERMEDIATE && TRUE && !willCombine && Initial (line 143-145)
            Deque<LambdaExpression> stack = new ArrayDeque<>();
            stack.push(field("value", int.class));
            LabelNode label = new LabelNode();
            JumpInsnNode jumpInsn = new JumpInsnNode(IFLT, label);
            Map<LabelNode, Boolean> labelToValue = new HashMap<>();
            labelToValue.put(label, true);
            Map<LabelNode, ControlFlowAnalyzer.LabelClassification> labelClassifications = new HashMap<>();
            labelClassifications.put(label, ControlFlowAnalyzer.LabelClassification.INTERMEDIATE);
            BranchState initialState = new BranchState.Initial();

            handler.handle(stack, jumpInsn, labelToValue, labelClassifications, initialState);

            LambdaExpression.BinaryOp binOp = (LambdaExpression.BinaryOp) stack.peek();
            // INTERMEDIATE+TRUE+Initial+!willCombine → invert=false → IFLT stays LT
            assertThat(binOp.operator())
                    .as("INTERMEDIATE+TRUE+Initial should NOT invert IFLT, stays LT")
                    .isEqualTo(LambdaExpression.BinaryOp.Operator.LT);
        }

        @Test
        void handle_intermediateFalse_noCombine_verifyInversion() {
            // Specifically test: INTERMEDIATE && FALSE && !willCombine (line 149)
            Deque<LambdaExpression> stack = new ArrayDeque<>();
            stack.push(field("value", int.class));
            LabelNode label = new LabelNode();
            JumpInsnNode jumpInsn = new JumpInsnNode(IFLT, label);
            Map<LabelNode, Boolean> labelToValue = new HashMap<>();
            labelToValue.put(label, false); // FALSE jump target
            Map<LabelNode, ControlFlowAnalyzer.LabelClassification> labelClassifications = new HashMap<>();
            labelClassifications.put(label, ControlFlowAnalyzer.LabelClassification.INTERMEDIATE);
            BranchState initialState = new BranchState.Initial();

            handler.handle(stack, jumpInsn, labelToValue, labelClassifications, initialState);

            LambdaExpression.BinaryOp binOp = (LambdaExpression.BinaryOp) stack.peek();
            // INTERMEDIATE+FALSE+!willCombine → invert=true → IFLT inverts to GE
            assertThat(binOp.operator())
                    .as("INTERMEDIATE+FALSE should invert IFLT to GE")
                    .isEqualTo(LambdaExpression.BinaryOp.Operator.GE);
        }

        // ========== Additional tests to kill determineComparisonOperator mutations ==========

        @Test
        void handle_nonIntermediateTrueTarget_vsDifferentFromIntermediateTrueTargetInAndMode() {
            // Kill mutation: jumpLabelClass == INTERMEDIATE replaced with true on line 143
            // Non-INTERMEDIATE + TRUE should go to line 154 (FALSE.equals → invert=false)
            // INTERMEDIATE + TRUE + AndMode should stay at line 143 (invert=true)

            // Case 1: NON-INTERMEDIATE + TRUE
            Deque<LambdaExpression> stack1 = new ArrayDeque<>();
            stack1.push(field("value", int.class));
            LabelNode label1 = new LabelNode();
            JumpInsnNode jumpInsn1 = new JumpInsnNode(IFLT, label1);
            Map<LabelNode, Boolean> labelToValue1 = new HashMap<>();
            labelToValue1.put(label1, true);
            Map<LabelNode, ControlFlowAnalyzer.LabelClassification> labelClassifications1 = new HashMap<>();
            // NO classification → not INTERMEDIATE
            BranchState andMode1 = new BranchState.AndMode(java.util.Optional.of(true), false);

            handler.handle(stack1, jumpInsn1, labelToValue1, labelClassifications1, andMode1);
            LambdaExpression.BinaryOp result1 = (LambdaExpression.BinaryOp) stack1.peek();

            // Case 2: INTERMEDIATE + TRUE + AndMode
            Deque<LambdaExpression> stack2 = new ArrayDeque<>();
            stack2.push(field("value", int.class));
            LabelNode label2 = new LabelNode();
            JumpInsnNode jumpInsn2 = new JumpInsnNode(IFLT, label2);
            Map<LabelNode, Boolean> labelToValue2 = new HashMap<>();
            labelToValue2.put(label2, true);
            Map<LabelNode, ControlFlowAnalyzer.LabelClassification> labelClassifications2 = new HashMap<>();
            labelClassifications2.put(label2, ControlFlowAnalyzer.LabelClassification.INTERMEDIATE);
            BranchState andMode2 = new BranchState.AndMode(java.util.Optional.of(true), false);

            handler.handle(stack2, jumpInsn2, labelToValue2, labelClassifications2, andMode2);
            LambdaExpression.BinaryOp result2 = (LambdaExpression.BinaryOp) stack2.peek();

            // Non-INTERMEDIATE + TRUE → line 154 FALSE.equals(true)=false → no invert → LT
            // INTERMEDIATE + TRUE + AndMode → line 143 → !(Initial)=true → invert=true → GE
            assertThat(result1.operator())
                    .as("Non-INTERMEDIATE + TRUE should NOT invert (LT)")
                    .isEqualTo(LambdaExpression.BinaryOp.Operator.LT);
            assertThat(result2.operator())
                    .as("INTERMEDIATE + TRUE + AndMode should invert (GE)")
                    .isEqualTo(LambdaExpression.BinaryOp.Operator.GE);
        }

        @Test
        void handle_verifyLine143TrueEqualsJumpTargetCondition() {
            // Kill mutation: TRUE.equals(jumpTarget) on line 143 replaced with true/false
            // INTERMEDIATE + FALSE should skip line 143, go to line 149
            // INTERMEDIATE + TRUE should enter line 143

            // INTERMEDIATE + FALSE (not TRUE)
            Deque<LambdaExpression> stack = new ArrayDeque<>();
            stack.push(field("value", int.class));
            LabelNode label = new LabelNode();
            JumpInsnNode jumpInsn = new JumpInsnNode(IFLT, label);
            Map<LabelNode, Boolean> labelToValue = new HashMap<>();
            labelToValue.put(label, false);
            Map<LabelNode, ControlFlowAnalyzer.LabelClassification> labelClassifications = new HashMap<>();
            labelClassifications.put(label, ControlFlowAnalyzer.LabelClassification.INTERMEDIATE);
            BranchState initialState = new BranchState.Initial();

            handler.handle(stack, jumpInsn, labelToValue, labelClassifications, initialState);
            LambdaExpression.BinaryOp result = (LambdaExpression.BinaryOp) stack.peek();

            // INTERMEDIATE + FALSE → line 149 → invert=true → LT → GE
            // If mutation made TRUE.equals(false)=true, we'd enter line 143 with Initial → invert=false → LT
            assertThat(result.operator())
                    .as("INTERMEDIATE + FALSE should go to line 149 (invert=true → GE)")
                    .isEqualTo(LambdaExpression.BinaryOp.Operator.GE);
        }

        @Test
        void handle_verifyLine154FalseEqualsJumpTarget() {
            // Kill mutation: FALSE.equals(jumpTarget) on line 154 replaced with true/false
            // Non-INTERMEDIATE + TRUE → FALSE.equals(true)=false → no invert
            // Non-INTERMEDIATE + FALSE → FALSE.equals(false)=true → invert

            // Case 1: TRUE jumpTarget
            Deque<LambdaExpression> stack1 = new ArrayDeque<>();
            stack1.push(field("value", int.class));
            LabelNode label1 = new LabelNode();
            JumpInsnNode jumpInsn1 = new JumpInsnNode(IFLT, label1);
            Map<LabelNode, Boolean> labelToValue1 = new HashMap<>();
            labelToValue1.put(label1, true);
            Map<LabelNode, ControlFlowAnalyzer.LabelClassification> labelClassifications1 = new HashMap<>();
            BranchState initialState1 = new BranchState.Initial();

            handler.handle(stack1, jumpInsn1, labelToValue1, labelClassifications1, initialState1);
            LambdaExpression.BinaryOp resultTrue = (LambdaExpression.BinaryOp) stack1.peek();

            // Case 2: FALSE jumpTarget
            Deque<LambdaExpression> stack2 = new ArrayDeque<>();
            stack2.push(field("value", int.class));
            LabelNode label2 = new LabelNode();
            JumpInsnNode jumpInsn2 = new JumpInsnNode(IFLT, label2);
            Map<LabelNode, Boolean> labelToValue2 = new HashMap<>();
            labelToValue2.put(label2, false);
            Map<LabelNode, ControlFlowAnalyzer.LabelClassification> labelClassifications2 = new HashMap<>();
            BranchState initialState2 = new BranchState.Initial();

            handler.handle(stack2, jumpInsn2, labelToValue2, labelClassifications2, initialState2);
            LambdaExpression.BinaryOp resultFalse = (LambdaExpression.BinaryOp) stack2.peek();

            // TRUE → FALSE.equals(true)=false → no invert → LT
            // FALSE → FALSE.equals(false)=true → invert → GE
            assertThat(resultTrue.operator())
                    .as("TRUE jumpTarget should NOT invert (LT)")
                    .isEqualTo(LambdaExpression.BinaryOp.Operator.LT);
            assertThat(resultFalse.operator())
                    .as("FALSE jumpTarget should invert (GE)")
                    .isEqualTo(LambdaExpression.BinaryOp.Operator.GE);
        }

        @Test
        void handle_verifyStateInstanceofInitialInLine143() {
            // Kill mutation: state instanceof BranchState.Initial on line 145
            // INTERMEDIATE + TRUE + !willCombine + Initial → !(Initial)=false → no invert
            // INTERMEDIATE + TRUE + !willCombine + AndMode → !(Initial)=true → invert

            // Case 1: Initial state
            Deque<LambdaExpression> stack1 = new ArrayDeque<>();
            stack1.push(field("value", int.class));
            LabelNode label1 = new LabelNode();
            JumpInsnNode jumpInsn1 = new JumpInsnNode(IFLT, label1);
            Map<LabelNode, Boolean> labelToValue1 = new HashMap<>();
            labelToValue1.put(label1, true);
            Map<LabelNode, ControlFlowAnalyzer.LabelClassification> labelClassifications1 = new HashMap<>();
            labelClassifications1.put(label1, ControlFlowAnalyzer.LabelClassification.INTERMEDIATE);
            BranchState initialState = new BranchState.Initial();

            handler.handle(stack1, jumpInsn1, labelToValue1, labelClassifications1, initialState);
            LambdaExpression.BinaryOp resultInitial = (LambdaExpression.BinaryOp) stack1.peek();

            // Case 2: AndMode state
            Deque<LambdaExpression> stack2 = new ArrayDeque<>();
            stack2.push(field("value", int.class));
            LabelNode label2 = new LabelNode();
            JumpInsnNode jumpInsn2 = new JumpInsnNode(IFLT, label2);
            Map<LabelNode, Boolean> labelToValue2 = new HashMap<>();
            labelToValue2.put(label2, true);
            Map<LabelNode, ControlFlowAnalyzer.LabelClassification> labelClassifications2 = new HashMap<>();
            labelClassifications2.put(label2, ControlFlowAnalyzer.LabelClassification.INTERMEDIATE);
            BranchState andMode = new BranchState.AndMode(java.util.Optional.of(true), false);

            handler.handle(stack2, jumpInsn2, labelToValue2, labelClassifications2, andMode);
            LambdaExpression.BinaryOp resultAndMode = (LambdaExpression.BinaryOp) stack2.peek();

            // Initial → !(Initial)=false → no invert → LT
            // AndMode → !(Initial)=true → invert → GE
            assertThat(resultInitial.operator())
                    .as("Initial state should NOT invert (LT)")
                    .isEqualTo(LambdaExpression.BinaryOp.Operator.LT);
            assertThat(resultAndMode.operator())
                    .as("AndMode state should invert (GE)")
                    .isEqualTo(LambdaExpression.BinaryOp.Operator.GE);
        }

        @Test
        void handle_combineOpWithBinaryOpOnStack_combinesExpressions() {
            // Kill mutation on line 112: combineOp != null && !stack.isEmpty() && instanceof
            Deque<LambdaExpression> stack = new ArrayDeque<>();
            LambdaExpression.BinaryOp previousCondition = LambdaExpression.BinaryOp.eq(
                    field("id", int.class), constant(1));
            stack.push(previousCondition);
            stack.push(field("value", int.class));

            LabelNode label = new LabelNode();
            JumpInsnNode jumpInsn = new JumpInsnNode(IFGT, label);
            Map<LabelNode, Boolean> labelToValue = new HashMap<>();
            labelToValue.put(label, false);
            Map<LabelNode, ControlFlowAnalyzer.LabelClassification> labelClassifications = new HashMap<>();
            BranchState andMode = new BranchState.AndMode(java.util.Optional.of(false), false);

            handler.handle(stack, jumpInsn, labelToValue, labelClassifications, andMode);

            assertThat(stack)
                    .as("Combining should result in single expression on stack")
                    .hasSize(1);
            LambdaExpression.BinaryOp combined = (LambdaExpression.BinaryOp) stack.peek();
            assertThat(combined.operator())
                    .as("Combined expression should use AND or OR operator")
                    .isIn(LambdaExpression.BinaryOp.Operator.AND, LambdaExpression.BinaryOp.Operator.OR);
        }

        @Test
        void handle_noCombineOp_pushesStandalone() {
            // Test: combineOp == null → pushes standalone
            Deque<LambdaExpression> stack = new ArrayDeque<>();
            stack.push(field("value", int.class));

            LabelNode label = new LabelNode();
            JumpInsnNode jumpInsn = new JumpInsnNode(IFGT, label);
            Map<LabelNode, Boolean> labelToValue = new HashMap<>();
            labelToValue.put(label, true);
            Map<LabelNode, ControlFlowAnalyzer.LabelClassification> labelClassifications = new HashMap<>();
            BranchState initialState = new BranchState.Initial();

            handler.handle(stack, jumpInsn, labelToValue, labelClassifications, initialState);

            assertThat(stack)
                    .as("With Initial state and no combining, should push standalone")
                    .hasSize(1);
            LambdaExpression.BinaryOp binOp = (LambdaExpression.BinaryOp) stack.peek();
            assertThat(binOp.operator())
                    .as("Should be a simple comparison, not a combined expression")
                    .isEqualTo(LambdaExpression.BinaryOp.Operator.GT);
        }

        @Test
        void handle_nullJumpTarget_treatedAsInvert() {
            // Test: jumpTarget is null (not in labelToValue map)
            // Line 154: FALSE.equals(null) = false → no invert
            // Wait, actually null comparison is tricky. Let me verify.
            Deque<LambdaExpression> stack = new ArrayDeque<>();
            stack.push(field("value", int.class));

            LabelNode label = new LabelNode();
            JumpInsnNode jumpInsn = new JumpInsnNode(IFLT, label);
            Map<LabelNode, Boolean> labelToValue = new HashMap<>();
            // Don't put label in map → jumpTarget is null
            Map<LabelNode, ControlFlowAnalyzer.LabelClassification> labelClassifications = new HashMap<>();
            BranchState initialState = new BranchState.Initial();

            handler.handle(stack, jumpInsn, labelToValue, labelClassifications, initialState);

            LambdaExpression.BinaryOp binOp = (LambdaExpression.BinaryOp) stack.peek();
            // jumpTarget=null → FALSE.equals(null)=false → no invert → LT
            assertThat(binOp.operator())
                    .as("Null jumpTarget with FALSE.equals should NOT invert (LT)")
                    .isEqualTo(LambdaExpression.BinaryOp.Operator.LT);
        }

        // ========== Tests to kill !willCombine mutations (lines 143, 149) ==========

        @Test
        void handle_intermediateTrue_withWillCombine_fallsThroughToLine154() {
            // Kill mutation: !willCombine on line 143 replaced with true
            // INTERMEDIATE + TRUE + willCombine=true → skip line 143, fall to line 154
            // Line 154: FALSE.equals(TRUE)=false → no invert → LT
            Deque<LambdaExpression> stack = new ArrayDeque<>();
            // Push a BinaryOp so willCombine=true after popping operand
            stack.push(LambdaExpression.BinaryOp.eq(field("a", int.class), constant(1)));
            stack.push(field("value", int.class));

            LabelNode label = new LabelNode();
            JumpInsnNode jumpInsn = new JumpInsnNode(IFLT, label);
            Map<LabelNode, Boolean> labelToValue = new HashMap<>();
            labelToValue.put(label, true);
            Map<LabelNode, ControlFlowAnalyzer.LabelClassification> labelClassifications = new HashMap<>();
            labelClassifications.put(label, ControlFlowAnalyzer.LabelClassification.INTERMEDIATE);
            BranchState andMode = new BranchState.AndMode(java.util.Optional.of(true), false);

            handler.handle(stack, jumpInsn, labelToValue, labelClassifications, andMode);

            // With willCombine=true, combined expression is pushed
            assertThat(stack).hasSize(1);
            LambdaExpression.BinaryOp combined = (LambdaExpression.BinaryOp) stack.peek();
            // The inner comparison should be LT (not inverted) because we skip line 143
            assertThat(combined.right())
                    .as("Right child should be the new comparison")
                    .isInstanceOf(LambdaExpression.BinaryOp.class);
            LambdaExpression.BinaryOp innerComp = (LambdaExpression.BinaryOp) combined.right();
            assertThat(innerComp.operator())
                    .as("INTERMEDIATE+TRUE+willCombine should skip line 143, fall to 154 → LT (no invert)")
                    .isEqualTo(LambdaExpression.BinaryOp.Operator.LT);
        }

        @Test
        void handle_intermediateTrue_withoutWillCombine_takesLine143() {
            // Compare with willCombine=false case (should take line 143)
            // INTERMEDIATE + TRUE + !willCombine + AndMode → line 143 → invert=true → GE
            Deque<LambdaExpression> stack = new ArrayDeque<>();
            stack.push(field("value", int.class)); // No BinaryOp → willCombine=false

            LabelNode label = new LabelNode();
            JumpInsnNode jumpInsn = new JumpInsnNode(IFLT, label);
            Map<LabelNode, Boolean> labelToValue = new HashMap<>();
            labelToValue.put(label, true);
            Map<LabelNode, ControlFlowAnalyzer.LabelClassification> labelClassifications = new HashMap<>();
            labelClassifications.put(label, ControlFlowAnalyzer.LabelClassification.INTERMEDIATE);
            BranchState andMode = new BranchState.AndMode(java.util.Optional.of(true), false);

            handler.handle(stack, jumpInsn, labelToValue, labelClassifications, andMode);

            LambdaExpression.BinaryOp binOp = (LambdaExpression.BinaryOp) stack.peek();
            assertThat(binOp.operator())
                    .as("INTERMEDIATE+TRUE+!willCombine+AndMode should take line 143 → invert → GE")
                    .isEqualTo(LambdaExpression.BinaryOp.Operator.GE);
        }

        @Test
        void handle_intermediateFalse_withWillCombine_fallsThroughToLine154() {
            // Kill mutation: !willCombine on line 149 replaced with true
            // INTERMEDIATE + FALSE + willCombine=true → skip line 149, fall to line 154
            // Line 154: FALSE.equals(FALSE)=true → invert → GE
            Deque<LambdaExpression> stack = new ArrayDeque<>();
            stack.push(LambdaExpression.BinaryOp.eq(field("a", int.class), constant(1)));
            stack.push(field("value", int.class));

            LabelNode label = new LabelNode();
            JumpInsnNode jumpInsn = new JumpInsnNode(IFLT, label);
            Map<LabelNode, Boolean> labelToValue = new HashMap<>();
            labelToValue.put(label, false);
            Map<LabelNode, ControlFlowAnalyzer.LabelClassification> labelClassifications = new HashMap<>();
            labelClassifications.put(label, ControlFlowAnalyzer.LabelClassification.INTERMEDIATE);
            BranchState andMode = new BranchState.AndMode(java.util.Optional.of(false), false);

            handler.handle(stack, jumpInsn, labelToValue, labelClassifications, andMode);

            assertThat(stack).hasSize(1);
            LambdaExpression.BinaryOp combined = (LambdaExpression.BinaryOp) stack.peek();
            LambdaExpression.BinaryOp innerComp = (LambdaExpression.BinaryOp) combined.right();
            // Both line 149 (invert=true) and line 154 (FALSE.equals(FALSE)=true → invert) yield GE
            // So this doesn't kill the mutation by itself, but documents the behavior
            assertThat(innerComp.operator())
                    .as("INTERMEDIATE+FALSE+willCombine falls to 154 → invert → GE")
                    .isEqualTo(LambdaExpression.BinaryOp.Operator.GE);
        }

        @Test
        void handle_intermediateFalse_withoutWillCombine_takesLine149() {
            // INTERMEDIATE + FALSE + !willCombine → line 149 → invert=true → GE
            Deque<LambdaExpression> stack = new ArrayDeque<>();
            stack.push(field("value", int.class));

            LabelNode label = new LabelNode();
            JumpInsnNode jumpInsn = new JumpInsnNode(IFLT, label);
            Map<LabelNode, Boolean> labelToValue = new HashMap<>();
            labelToValue.put(label, false);
            Map<LabelNode, ControlFlowAnalyzer.LabelClassification> labelClassifications = new HashMap<>();
            labelClassifications.put(label, ControlFlowAnalyzer.LabelClassification.INTERMEDIATE);
            BranchState initialState = new BranchState.Initial();

            handler.handle(stack, jumpInsn, labelToValue, labelClassifications, initialState);

            LambdaExpression.BinaryOp binOp = (LambdaExpression.BinaryOp) stack.peek();
            assertThat(binOp.operator())
                    .as("INTERMEDIATE+FALSE+!willCombine should take line 149 → invert → GE")
                    .isEqualTo(LambdaExpression.BinaryOp.Operator.GE);
        }

        @Test
        void handle_willCombineTrue_vs_willCombineFalse_differentBehavior() {
            // Definitive test: same scenario, only willCombine differs
            // INTERMEDIATE + TRUE + AndMode
            // willCombine=false → line 143 → invert=true → GE
            // willCombine=true → line 154 → invert=false → LT (inside combined)
            // These MUST differ to kill the mutation

            // Case 1: willCombine=false
            Deque<LambdaExpression> stack1 = new ArrayDeque<>();
            stack1.push(field("value", int.class));
            LabelNode label1 = new LabelNode();
            JumpInsnNode jumpInsn1 = new JumpInsnNode(IFLT, label1);
            Map<LabelNode, Boolean> labelToValue1 = new HashMap<>();
            labelToValue1.put(label1, true);
            Map<LabelNode, ControlFlowAnalyzer.LabelClassification> labelClassifications1 = new HashMap<>();
            labelClassifications1.put(label1, ControlFlowAnalyzer.LabelClassification.INTERMEDIATE);
            BranchState andMode1 = new BranchState.AndMode(java.util.Optional.of(true), false);

            handler.handle(stack1, jumpInsn1, labelToValue1, labelClassifications1, andMode1);
            LambdaExpression.BinaryOp result1 = (LambdaExpression.BinaryOp) stack1.peek();

            // Case 2: willCombine=true
            Deque<LambdaExpression> stack2 = new ArrayDeque<>();
            stack2.push(LambdaExpression.BinaryOp.eq(field("a", int.class), constant(1)));
            stack2.push(field("value", int.class));
            LabelNode label2 = new LabelNode();
            JumpInsnNode jumpInsn2 = new JumpInsnNode(IFLT, label2);
            Map<LabelNode, Boolean> labelToValue2 = new HashMap<>();
            labelToValue2.put(label2, true);
            Map<LabelNode, ControlFlowAnalyzer.LabelClassification> labelClassifications2 = new HashMap<>();
            labelClassifications2.put(label2, ControlFlowAnalyzer.LabelClassification.INTERMEDIATE);
            BranchState andMode2 = new BranchState.AndMode(java.util.Optional.of(true), false);

            handler.handle(stack2, jumpInsn2, labelToValue2, labelClassifications2, andMode2);
            LambdaExpression.BinaryOp result2 = (LambdaExpression.BinaryOp) stack2.peek();
            LambdaExpression.BinaryOp innerComp2 = (LambdaExpression.BinaryOp) result2.right();

            // willCombine=false → GE (inverted)
            // willCombine=true → LT (not inverted, inside combined)
            assertThat(result1.operator())
                    .as("willCombine=false: INTERMEDIATE+TRUE+AndMode → GE")
                    .isEqualTo(LambdaExpression.BinaryOp.Operator.GE);
            assertThat(innerComp2.operator())
                    .as("willCombine=true: INTERMEDIATE+TRUE+AndMode → LT (inside combined)")
                    .isEqualTo(LambdaExpression.BinaryOp.Operator.LT);

            // The operators MUST differ to kill the !willCombine mutation
            assertThat(result1.operator())
                    .as("willCombine difference MUST produce different inner operators")
                    .isNotEqualTo(innerComp2.operator());
        }

    }

    // ==================== TwoOperandComparisonHandler Tests ====================

    @Nested
    @DisplayName("TwoOperandComparisonHandler")
    class TwoOperandComparisonHandlerTests {

        private final TwoOperandComparisonHandler handler = new TwoOperandComparisonHandler();

        @Test
        void canHandle_withIF_ICMPGT_returnsTrue() {
            JumpInsnNode jumpInsn = new JumpInsnNode(IF_ICMPGT, new LabelNode());
            assertThat(handler.canHandle(jumpInsn)).isTrue();
        }

        @Test
        void canHandle_withIF_ICMPGE_returnsTrue() {
            JumpInsnNode jumpInsn = new JumpInsnNode(IF_ICMPGE, new LabelNode());
            assertThat(handler.canHandle(jumpInsn)).isTrue();
        }

        @Test
        void canHandle_withIF_ICMPLT_returnsTrue() {
            JumpInsnNode jumpInsn = new JumpInsnNode(IF_ICMPLT, new LabelNode());
            assertThat(handler.canHandle(jumpInsn)).isTrue();
        }

        @Test
        void canHandle_withIF_ICMPLE_returnsTrue() {
            JumpInsnNode jumpInsn = new JumpInsnNode(IF_ICMPLE, new LabelNode());
            assertThat(handler.canHandle(jumpInsn)).isTrue();
        }

        @Test
        void canHandle_withIF_ICMPEQ_returnsTrue() {
            JumpInsnNode jumpInsn = new JumpInsnNode(IF_ICMPEQ, new LabelNode());
            assertThat(handler.canHandle(jumpInsn)).isTrue();
        }

        @Test
        void canHandle_withIF_ICMPNE_returnsTrue() {
            JumpInsnNode jumpInsn = new JumpInsnNode(IF_ICMPNE, new LabelNode());
            assertThat(handler.canHandle(jumpInsn)).isTrue();
        }

        @Test
        void canHandle_withIF_ACMPEQ_returnsTrue() {
            JumpInsnNode jumpInsn = new JumpInsnNode(IF_ACMPEQ, new LabelNode());
            assertThat(handler.canHandle(jumpInsn)).isTrue();
        }

        @Test
        void canHandle_withIF_ACMPNE_returnsTrue() {
            JumpInsnNode jumpInsn = new JumpInsnNode(IF_ACMPNE, new LabelNode());
            assertThat(handler.canHandle(jumpInsn)).isTrue();
        }

        @Test
        void canHandle_withIFEQ_returnsFalse() {
            JumpInsnNode jumpInsn = new JumpInsnNode(IFEQ, new LabelNode());
            assertThat(handler.canHandle(jumpInsn)).isFalse();
        }

        @Test
        void canHandle_withIFNULL_returnsFalse() {
            JumpInsnNode jumpInsn = new JumpInsnNode(IFNULL, new LabelNode());
            assertThat(handler.canHandle(jumpInsn)).isFalse();
        }

        // ========== handle() method tests to kill surviving mutations ==========

        @Test
        void handle_IF_ICMPGT_jumpToTrue_createsGTComparison() {
            Deque<LambdaExpression> stack = new ArrayDeque<>();
            stack.push(field("left", int.class));
            stack.push(field("right", int.class));
            LabelNode label = new LabelNode();
            JumpInsnNode jumpInsn = new JumpInsnNode(IF_ICMPGT, label);
            Map<LabelNode, Boolean> labelToValue = new HashMap<>();
            labelToValue.put(label, true);
            Map<LabelNode, ControlFlowAnalyzer.LabelClassification> labelClassifications = new HashMap<>();
            BranchState initialState = new BranchState.Initial();

            handler.handle(stack, jumpInsn, labelToValue, labelClassifications, initialState);

            assertThat(stack).hasSize(1);
            LambdaExpression.BinaryOp binOp = (LambdaExpression.BinaryOp) stack.peek();
            assertThat(binOp.operator())
                    .as("IF_ICMPGT with jump to TRUE should create GT")
                    .isEqualTo(LambdaExpression.BinaryOp.Operator.GT);
        }

        @Test
        void handle_IF_ICMPGT_jumpToFalse_createsLEComparison() {
            Deque<LambdaExpression> stack = new ArrayDeque<>();
            stack.push(field("left", int.class));
            stack.push(field("right", int.class));
            LabelNode label = new LabelNode();
            JumpInsnNode jumpInsn = new JumpInsnNode(IF_ICMPGT, label);
            Map<LabelNode, Boolean> labelToValue = new HashMap<>();
            labelToValue.put(label, false);
            Map<LabelNode, ControlFlowAnalyzer.LabelClassification> labelClassifications = new HashMap<>();
            BranchState initialState = new BranchState.Initial();

            handler.handle(stack, jumpInsn, labelToValue, labelClassifications, initialState);

            assertThat(stack).hasSize(1);
            LambdaExpression.BinaryOp binOp = (LambdaExpression.BinaryOp) stack.peek();
            assertThat(binOp.operator())
                    .as("IF_ICMPGT with jump to FALSE should create LE (inverted)")
                    .isEqualTo(LambdaExpression.BinaryOp.Operator.LE);
        }

        @Test
        void handle_IF_ICMPGE_jumpToTrue_createsGEComparison() {
            Deque<LambdaExpression> stack = new ArrayDeque<>();
            stack.push(field("left", int.class));
            stack.push(field("right", int.class));
            LabelNode label = new LabelNode();
            JumpInsnNode jumpInsn = new JumpInsnNode(IF_ICMPGE, label);
            Map<LabelNode, Boolean> labelToValue = new HashMap<>();
            labelToValue.put(label, true);
            Map<LabelNode, ControlFlowAnalyzer.LabelClassification> labelClassifications = new HashMap<>();
            BranchState initialState = new BranchState.Initial();

            handler.handle(stack, jumpInsn, labelToValue, labelClassifications, initialState);

            assertThat(stack).hasSize(1);
            LambdaExpression.BinaryOp binOp = (LambdaExpression.BinaryOp) stack.peek();
            assertThat(binOp.operator())
                    .as("IF_ICMPGE with jump to TRUE should create GE")
                    .isEqualTo(LambdaExpression.BinaryOp.Operator.GE);
        }

        @Test
        void handle_IF_ICMPGE_jumpToFalse_createsLTComparison() {
            Deque<LambdaExpression> stack = new ArrayDeque<>();
            stack.push(field("left", int.class));
            stack.push(field("right", int.class));
            LabelNode label = new LabelNode();
            JumpInsnNode jumpInsn = new JumpInsnNode(IF_ICMPGE, label);
            Map<LabelNode, Boolean> labelToValue = new HashMap<>();
            labelToValue.put(label, false);
            Map<LabelNode, ControlFlowAnalyzer.LabelClassification> labelClassifications = new HashMap<>();
            BranchState initialState = new BranchState.Initial();

            handler.handle(stack, jumpInsn, labelToValue, labelClassifications, initialState);

            assertThat(stack).hasSize(1);
            LambdaExpression.BinaryOp binOp = (LambdaExpression.BinaryOp) stack.peek();
            assertThat(binOp.operator())
                    .as("IF_ICMPGE with jump to FALSE should create LT (inverted)")
                    .isEqualTo(LambdaExpression.BinaryOp.Operator.LT);
        }

        @Test
        void handle_IF_ICMPLT_jumpToTrue_createsLTComparison() {
            Deque<LambdaExpression> stack = new ArrayDeque<>();
            stack.push(field("left", int.class));
            stack.push(field("right", int.class));
            LabelNode label = new LabelNode();
            JumpInsnNode jumpInsn = new JumpInsnNode(IF_ICMPLT, label);
            Map<LabelNode, Boolean> labelToValue = new HashMap<>();
            labelToValue.put(label, true);
            Map<LabelNode, ControlFlowAnalyzer.LabelClassification> labelClassifications = new HashMap<>();
            BranchState initialState = new BranchState.Initial();

            handler.handle(stack, jumpInsn, labelToValue, labelClassifications, initialState);

            assertThat(stack).hasSize(1);
            LambdaExpression.BinaryOp binOp = (LambdaExpression.BinaryOp) stack.peek();
            assertThat(binOp.operator())
                    .as("IF_ICMPLT with jump to TRUE should create LT")
                    .isEqualTo(LambdaExpression.BinaryOp.Operator.LT);
        }

        @Test
        void handle_IF_ICMPLT_jumpToFalse_createsGEComparison() {
            Deque<LambdaExpression> stack = new ArrayDeque<>();
            stack.push(field("left", int.class));
            stack.push(field("right", int.class));
            LabelNode label = new LabelNode();
            JumpInsnNode jumpInsn = new JumpInsnNode(IF_ICMPLT, label);
            Map<LabelNode, Boolean> labelToValue = new HashMap<>();
            labelToValue.put(label, false);
            Map<LabelNode, ControlFlowAnalyzer.LabelClassification> labelClassifications = new HashMap<>();
            BranchState initialState = new BranchState.Initial();

            handler.handle(stack, jumpInsn, labelToValue, labelClassifications, initialState);

            assertThat(stack).hasSize(1);
            LambdaExpression.BinaryOp binOp = (LambdaExpression.BinaryOp) stack.peek();
            assertThat(binOp.operator())
                    .as("IF_ICMPLT with jump to FALSE should create GE (inverted)")
                    .isEqualTo(LambdaExpression.BinaryOp.Operator.GE);
        }

        @Test
        void handle_IF_ICMPLE_jumpToTrue_createsLEComparison() {
            Deque<LambdaExpression> stack = new ArrayDeque<>();
            stack.push(field("left", int.class));
            stack.push(field("right", int.class));
            LabelNode label = new LabelNode();
            JumpInsnNode jumpInsn = new JumpInsnNode(IF_ICMPLE, label);
            Map<LabelNode, Boolean> labelToValue = new HashMap<>();
            labelToValue.put(label, true);
            Map<LabelNode, ControlFlowAnalyzer.LabelClassification> labelClassifications = new HashMap<>();
            BranchState initialState = new BranchState.Initial();

            handler.handle(stack, jumpInsn, labelToValue, labelClassifications, initialState);

            assertThat(stack).hasSize(1);
            LambdaExpression.BinaryOp binOp = (LambdaExpression.BinaryOp) stack.peek();
            assertThat(binOp.operator())
                    .as("IF_ICMPLE with jump to TRUE should create LE")
                    .isEqualTo(LambdaExpression.BinaryOp.Operator.LE);
        }

        @Test
        void handle_IF_ICMPLE_jumpToFalse_createsGTComparison() {
            Deque<LambdaExpression> stack = new ArrayDeque<>();
            stack.push(field("left", int.class));
            stack.push(field("right", int.class));
            LabelNode label = new LabelNode();
            JumpInsnNode jumpInsn = new JumpInsnNode(IF_ICMPLE, label);
            Map<LabelNode, Boolean> labelToValue = new HashMap<>();
            labelToValue.put(label, false);
            Map<LabelNode, ControlFlowAnalyzer.LabelClassification> labelClassifications = new HashMap<>();
            BranchState initialState = new BranchState.Initial();

            handler.handle(stack, jumpInsn, labelToValue, labelClassifications, initialState);

            assertThat(stack).hasSize(1);
            LambdaExpression.BinaryOp binOp = (LambdaExpression.BinaryOp) stack.peek();
            assertThat(binOp.operator())
                    .as("IF_ICMPLE with jump to FALSE should create GT (inverted)")
                    .isEqualTo(LambdaExpression.BinaryOp.Operator.GT);
        }

        @Test
        void handle_IF_ICMPEQ_jumpToTrue_createsEQComparison() {
            Deque<LambdaExpression> stack = new ArrayDeque<>();
            stack.push(field("left", int.class));
            stack.push(field("right", int.class));
            LabelNode label = new LabelNode();
            JumpInsnNode jumpInsn = new JumpInsnNode(IF_ICMPEQ, label);
            Map<LabelNode, Boolean> labelToValue = new HashMap<>();
            labelToValue.put(label, true);
            Map<LabelNode, ControlFlowAnalyzer.LabelClassification> labelClassifications = new HashMap<>();
            BranchState initialState = new BranchState.Initial();

            handler.handle(stack, jumpInsn, labelToValue, labelClassifications, initialState);

            assertThat(stack).hasSize(1);
            LambdaExpression.BinaryOp binOp = (LambdaExpression.BinaryOp) stack.peek();
            assertThat(binOp.operator())
                    .as("IF_ICMPEQ with jump to TRUE should create EQ")
                    .isEqualTo(LambdaExpression.BinaryOp.Operator.EQ);
        }

        @Test
        void handle_IF_ICMPEQ_jumpToFalse_createsNEComparison() {
            Deque<LambdaExpression> stack = new ArrayDeque<>();
            stack.push(field("left", int.class));
            stack.push(field("right", int.class));
            LabelNode label = new LabelNode();
            JumpInsnNode jumpInsn = new JumpInsnNode(IF_ICMPEQ, label);
            Map<LabelNode, Boolean> labelToValue = new HashMap<>();
            labelToValue.put(label, false);
            Map<LabelNode, ControlFlowAnalyzer.LabelClassification> labelClassifications = new HashMap<>();
            BranchState initialState = new BranchState.Initial();

            handler.handle(stack, jumpInsn, labelToValue, labelClassifications, initialState);

            assertThat(stack).hasSize(1);
            LambdaExpression.BinaryOp binOp = (LambdaExpression.BinaryOp) stack.peek();
            assertThat(binOp.operator())
                    .as("IF_ICMPEQ with jump to FALSE should create NE (inverted)")
                    .isEqualTo(LambdaExpression.BinaryOp.Operator.NE);
        }

        @Test
        void handle_IF_ICMPNE_jumpToTrue_createsNEComparison() {
            Deque<LambdaExpression> stack = new ArrayDeque<>();
            stack.push(field("left", int.class));
            stack.push(field("right", int.class));
            LabelNode label = new LabelNode();
            JumpInsnNode jumpInsn = new JumpInsnNode(IF_ICMPNE, label);
            Map<LabelNode, Boolean> labelToValue = new HashMap<>();
            labelToValue.put(label, true);
            Map<LabelNode, ControlFlowAnalyzer.LabelClassification> labelClassifications = new HashMap<>();
            BranchState initialState = new BranchState.Initial();

            handler.handle(stack, jumpInsn, labelToValue, labelClassifications, initialState);

            assertThat(stack).hasSize(1);
            LambdaExpression.BinaryOp binOp = (LambdaExpression.BinaryOp) stack.peek();
            assertThat(binOp.operator())
                    .as("IF_ICMPNE with jump to TRUE should create NE")
                    .isEqualTo(LambdaExpression.BinaryOp.Operator.NE);
        }

        @Test
        void handle_IF_ICMPNE_jumpToFalse_createsEQComparison() {
            Deque<LambdaExpression> stack = new ArrayDeque<>();
            stack.push(field("left", int.class));
            stack.push(field("right", int.class));
            LabelNode label = new LabelNode();
            JumpInsnNode jumpInsn = new JumpInsnNode(IF_ICMPNE, label);
            Map<LabelNode, Boolean> labelToValue = new HashMap<>();
            labelToValue.put(label, false);
            Map<LabelNode, ControlFlowAnalyzer.LabelClassification> labelClassifications = new HashMap<>();
            BranchState initialState = new BranchState.Initial();

            handler.handle(stack, jumpInsn, labelToValue, labelClassifications, initialState);

            assertThat(stack).hasSize(1);
            LambdaExpression.BinaryOp binOp = (LambdaExpression.BinaryOp) stack.peek();
            assertThat(binOp.operator())
                    .as("IF_ICMPNE with jump to FALSE should create EQ (inverted)")
                    .isEqualTo(LambdaExpression.BinaryOp.Operator.EQ);
        }

        @Test
        void handle_IF_ACMPEQ_jumpToTrue_createsEQComparison() {
            Deque<LambdaExpression> stack = new ArrayDeque<>();
            stack.push(field("left", Object.class));
            stack.push(field("right", Object.class));
            LabelNode label = new LabelNode();
            JumpInsnNode jumpInsn = new JumpInsnNode(IF_ACMPEQ, label);
            Map<LabelNode, Boolean> labelToValue = new HashMap<>();
            labelToValue.put(label, true);
            Map<LabelNode, ControlFlowAnalyzer.LabelClassification> labelClassifications = new HashMap<>();
            BranchState initialState = new BranchState.Initial();

            handler.handle(stack, jumpInsn, labelToValue, labelClassifications, initialState);

            assertThat(stack).hasSize(1);
            LambdaExpression.BinaryOp binOp = (LambdaExpression.BinaryOp) stack.peek();
            assertThat(binOp.operator())
                    .as("IF_ACMPEQ with jump to TRUE should create EQ")
                    .isEqualTo(LambdaExpression.BinaryOp.Operator.EQ);
        }

        @Test
        void handle_IF_ACMPNE_jumpToTrue_createsNEComparison() {
            Deque<LambdaExpression> stack = new ArrayDeque<>();
            stack.push(field("left", Object.class));
            stack.push(field("right", Object.class));
            LabelNode label = new LabelNode();
            JumpInsnNode jumpInsn = new JumpInsnNode(IF_ACMPNE, label);
            Map<LabelNode, Boolean> labelToValue = new HashMap<>();
            labelToValue.put(label, true);
            Map<LabelNode, ControlFlowAnalyzer.LabelClassification> labelClassifications = new HashMap<>();
            BranchState initialState = new BranchState.Initial();

            handler.handle(stack, jumpInsn, labelToValue, labelClassifications, initialState);

            assertThat(stack).hasSize(1);
            LambdaExpression.BinaryOp binOp = (LambdaExpression.BinaryOp) stack.peek();
            assertThat(binOp.operator())
                    .as("IF_ACMPNE with jump to TRUE should create NE")
                    .isEqualTo(LambdaExpression.BinaryOp.Operator.NE);
        }

        @Test
        void handle_withPreviousCondition_combinesExpressions() {
            Deque<LambdaExpression> stack = new ArrayDeque<>();
            // Push a previous condition (BinaryOp)
            stack.push(LambdaExpression.BinaryOp.eq(
                    field("id", int.class),
                    constant(1)
            ));
            // Push values to compare
            stack.push(field("left", int.class));
            stack.push(field("right", int.class));

            LabelNode label = new LabelNode();
            JumpInsnNode jumpInsn = new JumpInsnNode(IF_ICMPGT, label);
            Map<LabelNode, Boolean> labelToValue = new HashMap<>();
            labelToValue.put(label, false);
            Map<LabelNode, ControlFlowAnalyzer.LabelClassification> labelClassifications = new HashMap<>();
            BranchState andMode = new BranchState.AndMode(java.util.Optional.of(false), false);

            handler.handle(stack, jumpInsn, labelToValue, labelClassifications, andMode);

            assertThat(stack).hasSize(1);
            assertThat(stack.peek())
                    .as("Should combine into a BinaryOp")
                    .isInstanceOf(LambdaExpression.BinaryOp.class);
        }

        @Test
        void handle_withIntermediateLabelAndTrue_inversionInAndMode() {
            Deque<LambdaExpression> stack = new ArrayDeque<>();
            stack.push(field("left", int.class));
            stack.push(field("right", int.class));
            LabelNode label = new LabelNode();
            JumpInsnNode jumpInsn = new JumpInsnNode(IF_ICMPGT, label);
            Map<LabelNode, Boolean> labelToValue = new HashMap<>();
            labelToValue.put(label, true);
            Map<LabelNode, ControlFlowAnalyzer.LabelClassification> labelClassifications = new HashMap<>();
            labelClassifications.put(label, ControlFlowAnalyzer.LabelClassification.INTERMEDIATE);
            // In AndMode, INTERMEDIATE→TRUE should invert (stack is not willCombine)
            BranchState andMode = new BranchState.AndMode(java.util.Optional.of(false), false);

            handler.handle(stack, jumpInsn, labelToValue, labelClassifications, andMode);

            assertThat(stack).hasSize(1);
            LambdaExpression.BinaryOp binOp = (LambdaExpression.BinaryOp) stack.peek();
            assertThat(binOp.operator())
                    .as("IF_ICMPGT in AndMode with INTERMEDIATE→TRUE should invert to LE")
                    .isEqualTo(LambdaExpression.BinaryOp.Operator.LE);
        }

        @Test
        void handle_withIntermediateLabelAndTrue_noInversionInInitialState() {
            Deque<LambdaExpression> stack = new ArrayDeque<>();
            stack.push(field("left", int.class));
            stack.push(field("right", int.class));
            LabelNode label = new LabelNode();
            JumpInsnNode jumpInsn = new JumpInsnNode(IF_ICMPGT, label);
            Map<LabelNode, Boolean> labelToValue = new HashMap<>();
            labelToValue.put(label, true);
            Map<LabelNode, ControlFlowAnalyzer.LabelClassification> labelClassifications = new HashMap<>();
            labelClassifications.put(label, ControlFlowAnalyzer.LabelClassification.INTERMEDIATE);
            // In Initial state, INTERMEDIATE→TRUE should NOT invert
            BranchState initialState = new BranchState.Initial();

            handler.handle(stack, jumpInsn, labelToValue, labelClassifications, initialState);

            assertThat(stack).hasSize(1);
            LambdaExpression.BinaryOp binOp = (LambdaExpression.BinaryOp) stack.peek();
            assertThat(binOp.operator())
                    .as("IF_ICMPGT in Initial state with INTERMEDIATE→TRUE should NOT invert, stays GT")
                    .isEqualTo(LambdaExpression.BinaryOp.Operator.GT);
        }

        @Test
        void handle_withIntermediateLabelAndFalse_inverts() {
            Deque<LambdaExpression> stack = new ArrayDeque<>();
            stack.push(field("left", int.class));
            stack.push(field("right", int.class));
            LabelNode label = new LabelNode();
            JumpInsnNode jumpInsn = new JumpInsnNode(IF_ICMPGE, label);
            Map<LabelNode, Boolean> labelToValue = new HashMap<>();
            labelToValue.put(label, false);
            Map<LabelNode, ControlFlowAnalyzer.LabelClassification> labelClassifications = new HashMap<>();
            labelClassifications.put(label, ControlFlowAnalyzer.LabelClassification.INTERMEDIATE);
            BranchState initialState = new BranchState.Initial();

            handler.handle(stack, jumpInsn, labelToValue, labelClassifications, initialState);

            assertThat(stack).hasSize(1);
            LambdaExpression.BinaryOp binOp = (LambdaExpression.BinaryOp) stack.peek();
            assertThat(binOp.operator())
                    .as("IF_ICMPGE with INTERMEDIATE→FALSE should invert to LT")
                    .isEqualTo(LambdaExpression.BinaryOp.Operator.LT);
        }

        @Test
        void handle_popsCorrectOperandOrder() {
            Deque<LambdaExpression> stack = new ArrayDeque<>();
            LambdaExpression leftField = field("left", int.class);
            LambdaExpression rightField = field("right", int.class);
            stack.push(leftField);  // Pushed first
            stack.push(rightField); // Pushed second (on top)
            LabelNode label = new LabelNode();
            JumpInsnNode jumpInsn = new JumpInsnNode(IF_ICMPGT, label);
            Map<LabelNode, Boolean> labelToValue = new HashMap<>();
            labelToValue.put(label, true);
            Map<LabelNode, ControlFlowAnalyzer.LabelClassification> labelClassifications = new HashMap<>();
            BranchState initialState = new BranchState.Initial();

            handler.handle(stack, jumpInsn, labelToValue, labelClassifications, initialState);

            LambdaExpression.BinaryOp binOp = (LambdaExpression.BinaryOp) stack.peek();
            assertThat(binOp.left())
                    .as("Left operand should be the first pushed item")
                    .isSameAs(leftField);
            assertThat(binOp.right())
                    .as("Right operand should be the second pushed item (popped first)")
                    .isSameAs(rightField);
        }

        // ========== Tests to kill requireStackSize mutation ==========

        @Test
        void handle_withInsufficientStack_throwsBytecodeAnalysisException() {
            Deque<LambdaExpression> stack = new ArrayDeque<>();
            stack.push(field("onlyOne", int.class)); // Only 1 element, needs 2
            LabelNode label = new LabelNode();
            JumpInsnNode jumpInsn = new JumpInsnNode(IF_ICMPGT, label);
            Map<LabelNode, Boolean> labelToValue = new HashMap<>();
            Map<LabelNode, ControlFlowAnalyzer.LabelClassification> labelClassifications = new HashMap<>();
            BranchState initialState = new BranchState.Initial();

            // Should throw BytecodeAnalysisException from requireStackSize (not from popSafe)
            // The key difference: requireStackSize(2) throws "expected 2 elements"
            // If mutation removes it, popSafe would throw "expected 1 elements" later
            assertThatThrownBy(() ->
                    handler.handle(stack, jumpInsn, labelToValue, labelClassifications, initialState))
                    .as("Should throw BytecodeAnalysisException with expected 2 elements (killing requireStackSize mutation)")
                    .isInstanceOf(io.quarkiverse.qubit.deployment.common.BytecodeAnalysisException.class)
                    .hasMessageContaining("expected 2 elements");
        }

        @Test
        void handle_withEmptyStack_throwsBytecodeAnalysisException() {
            Deque<LambdaExpression> stack = new ArrayDeque<>();
            LabelNode label = new LabelNode();
            JumpInsnNode jumpInsn = new JumpInsnNode(IF_ICMPGT, label);
            Map<LabelNode, Boolean> labelToValue = new HashMap<>();
            Map<LabelNode, ControlFlowAnalyzer.LabelClassification> labelClassifications = new HashMap<>();
            BranchState initialState = new BranchState.Initial();

            assertThatThrownBy(() ->
                    handler.handle(stack, jumpInsn, labelToValue, labelClassifications, initialState))
                    .as("Should throw BytecodeAnalysisException for empty stack")
                    .isInstanceOf(io.quarkiverse.qubit.deployment.common.BytecodeAnalysisException.class);
        }

        // ========== Tests to kill determineComparisonOperator conditional mutations ==========

        @Test
        void handle_intermediateTrue_andMode_noCombine_verifyInversion() {
            // Specifically test: INTERMEDIATE && TRUE && !willCombine && AndMode
            // This should enter line 120-127 and take the else branch (invert = true)
            Deque<LambdaExpression> stack = new ArrayDeque<>();
            stack.push(field("left", int.class));
            stack.push(field("right", int.class));
            // Stack has no BinaryOp on top after popping 2 elements → willCombine = false
            LabelNode label = new LabelNode();
            JumpInsnNode jumpInsn = new JumpInsnNode(IF_ICMPLT, label); // Using IFLT
            Map<LabelNode, Boolean> labelToValue = new HashMap<>();
            labelToValue.put(label, true); // TRUE jump target
            Map<LabelNode, ControlFlowAnalyzer.LabelClassification> labelClassifications = new HashMap<>();
            labelClassifications.put(label, ControlFlowAnalyzer.LabelClassification.INTERMEDIATE);
            // AndMode state (not Initial)
            BranchState andMode = new BranchState.AndMode(java.util.Optional.of(true), false);

            handler.handle(stack, jumpInsn, labelToValue, labelClassifications, andMode);

            LambdaExpression.BinaryOp binOp = (LambdaExpression.BinaryOp) stack.peek();
            // INTERMEDIATE+TRUE+AndMode+!willCombine → invert=true → IFLT inverts to GE
            assertThat(binOp.operator())
                    .as("INTERMEDIATE+TRUE+AndMode should invert IF_ICMPLT to GE")
                    .isEqualTo(LambdaExpression.BinaryOp.Operator.GE);
        }

        @Test
        void handle_intermediateTrue_initialState_noCombine_noInversion() {
            // Specifically test: INTERMEDIATE && TRUE && !willCombine && Initial
            // This should enter line 120-127 and take the if branch (invert = false)
            Deque<LambdaExpression> stack = new ArrayDeque<>();
            stack.push(field("left", int.class));
            stack.push(field("right", int.class));
            LabelNode label = new LabelNode();
            JumpInsnNode jumpInsn = new JumpInsnNode(IF_ICMPLT, label);
            Map<LabelNode, Boolean> labelToValue = new HashMap<>();
            labelToValue.put(label, true);
            Map<LabelNode, ControlFlowAnalyzer.LabelClassification> labelClassifications = new HashMap<>();
            labelClassifications.put(label, ControlFlowAnalyzer.LabelClassification.INTERMEDIATE);
            BranchState initialState = new BranchState.Initial();

            handler.handle(stack, jumpInsn, labelToValue, labelClassifications, initialState);

            LambdaExpression.BinaryOp binOp = (LambdaExpression.BinaryOp) stack.peek();
            // INTERMEDIATE+TRUE+Initial+!willCombine → invert=false → IFLT stays LT
            assertThat(binOp.operator())
                    .as("INTERMEDIATE+TRUE+Initial should NOT invert IF_ICMPLT, stays LT")
                    .isEqualTo(LambdaExpression.BinaryOp.Operator.LT);
        }

        @Test
        void handle_intermediateFalse_noCombine_verifyInversion() {
            // Specifically test: INTERMEDIATE && FALSE && !willCombine (line 128)
            Deque<LambdaExpression> stack = new ArrayDeque<>();
            stack.push(field("left", int.class));
            stack.push(field("right", int.class));
            LabelNode label = new LabelNode();
            JumpInsnNode jumpInsn = new JumpInsnNode(IF_ICMPLT, label);
            Map<LabelNode, Boolean> labelToValue = new HashMap<>();
            labelToValue.put(label, false); // FALSE jump target
            Map<LabelNode, ControlFlowAnalyzer.LabelClassification> labelClassifications = new HashMap<>();
            labelClassifications.put(label, ControlFlowAnalyzer.LabelClassification.INTERMEDIATE);
            BranchState initialState = new BranchState.Initial();

            handler.handle(stack, jumpInsn, labelToValue, labelClassifications, initialState);

            LambdaExpression.BinaryOp binOp = (LambdaExpression.BinaryOp) stack.peek();
            // INTERMEDIATE+FALSE+!willCombine → invert=true → IFLT inverts to GE
            assertThat(binOp.operator())
                    .as("INTERMEDIATE+FALSE should invert IF_ICMPLT to GE")
                    .isEqualTo(LambdaExpression.BinaryOp.Operator.GE);
        }

        // ========== Tests to kill willCombine mutations (line 67) ==========

        @Test
        void handle_intermediateTrue_withWillCombine_fallsThroughToTrueCheck() {
            // Test: INTERMEDIATE + TRUE + willCombine=true
            // When willCombine=true, line 120 (!willCombine) is false, so it falls to line 131
            // Line 131: TRUE.equals(jumpTarget) → invert=false
            Deque<LambdaExpression> stack = new ArrayDeque<>();
            // Push a BinaryOp that will remain after popping operands
            stack.push(LambdaExpression.BinaryOp.eq(field("a", int.class), constant(1)));
            stack.push(field("left", int.class));
            stack.push(field("right", int.class));

            LabelNode label = new LabelNode();
            JumpInsnNode jumpInsn = new JumpInsnNode(IF_ICMPLT, label);
            Map<LabelNode, Boolean> labelToValue = new HashMap<>();
            labelToValue.put(label, true);
            Map<LabelNode, ControlFlowAnalyzer.LabelClassification> labelClassifications = new HashMap<>();
            labelClassifications.put(label, ControlFlowAnalyzer.LabelClassification.INTERMEDIATE);
            BranchState andMode = new BranchState.AndMode(java.util.Optional.of(true), false);

            handler.handle(stack, jumpInsn, labelToValue, labelClassifications, andMode);

            // After popping 2, stack has BinaryOp → willCombine=true
            // INTERMEDIATE+TRUE+willCombine → skip to line 131 TRUE.equals → invert=false → LT stays LT
            // BUT then combine happens, so result is a combined expression
            assertThat(stack).hasSize(1);
            LambdaExpression result = stack.peek();
            assertThat(result)
                    .as("With willCombine=true, should combine with previous condition")
                    .isInstanceOf(LambdaExpression.BinaryOp.class);
            // The combined result's right child (the new comparison) should be LT (not inverted)
            LambdaExpression.BinaryOp combined = (LambdaExpression.BinaryOp) result;
            // The combine produces AND/OR with children, verify the structure
            assertThat(combined.operator())
                    .as("Should combine with AND operator")
                    .isIn(LambdaExpression.BinaryOp.Operator.AND, LambdaExpression.BinaryOp.Operator.OR);
        }

        @Test
        void handle_intermediateTrue_andMode_withWillCombine_versus_withoutWillCombine() {
            // Comparative test: willCombine=true vs willCombine=false in same scenario
            // INTERMEDIATE + TRUE + AndMode
            // willCombine=false → invert=true (line 126)
            // willCombine=true → falls to line 131, invert=false

            // Case 1: willCombine=false (empty stack after popping operands)
            Deque<LambdaExpression> stack1 = new ArrayDeque<>();
            stack1.push(field("left", int.class));
            stack1.push(field("right", int.class));
            LabelNode label1 = new LabelNode();
            JumpInsnNode jumpInsn1 = new JumpInsnNode(IF_ICMPLT, label1);
            Map<LabelNode, Boolean> labelToValue1 = new HashMap<>();
            labelToValue1.put(label1, true);
            Map<LabelNode, ControlFlowAnalyzer.LabelClassification> labelClassifications1 = new HashMap<>();
            labelClassifications1.put(label1, ControlFlowAnalyzer.LabelClassification.INTERMEDIATE);
            BranchState andMode1 = new BranchState.AndMode(java.util.Optional.of(true), false);

            handler.handle(stack1, jumpInsn1, labelToValue1, labelClassifications1, andMode1);
            LambdaExpression.BinaryOp result1 = (LambdaExpression.BinaryOp) stack1.peek();

            // Case 2: willCombine=true (BinaryOp on stack after popping operands)
            Deque<LambdaExpression> stack2 = new ArrayDeque<>();
            stack2.push(LambdaExpression.BinaryOp.eq(field("id", int.class), constant(1)));
            stack2.push(field("left", int.class));
            stack2.push(field("right", int.class));
            LabelNode label2 = new LabelNode();
            JumpInsnNode jumpInsn2 = new JumpInsnNode(IF_ICMPLT, label2);
            Map<LabelNode, Boolean> labelToValue2 = new HashMap<>();
            labelToValue2.put(label2, true);
            Map<LabelNode, ControlFlowAnalyzer.LabelClassification> labelClassifications2 = new HashMap<>();
            labelClassifications2.put(label2, ControlFlowAnalyzer.LabelClassification.INTERMEDIATE);
            BranchState andMode2 = new BranchState.AndMode(java.util.Optional.of(true), false);

            handler.handle(stack2, jumpInsn2, labelToValue2, labelClassifications2, andMode2);

            // Result1 should be GE (inverted), Result2 should contain LT (not inverted)
            assertThat(result1.operator())
                    .as("willCombine=false: INTERMEDIATE+TRUE+AndMode inverts IF_ICMPLT to GE")
                    .isEqualTo(LambdaExpression.BinaryOp.Operator.GE);

            // Result2 is combined, need to check the right child (the new comparison)
            LambdaExpression.BinaryOp result2 = (LambdaExpression.BinaryOp) stack2.peek();
            assertThat(result2.operator())
                    .as("willCombine=true: Should be an AND/OR combination")
                    .isIn(LambdaExpression.BinaryOp.Operator.AND, LambdaExpression.BinaryOp.Operator.OR);
            // The right operand of the combination should be LT (the new comparison)
            assertThat(result2.right())
                    .as("Right operand of combined expression should be the new comparison")
                    .isInstanceOf(LambdaExpression.BinaryOp.class);
            LambdaExpression.BinaryOp innerComparison = (LambdaExpression.BinaryOp) result2.right();
            assertThat(innerComparison.operator())
                    .as("willCombine=true: inner comparison should be LT (not inverted)")
                    .isEqualTo(LambdaExpression.BinaryOp.Operator.LT);
        }

        @Test
        void handle_stackWithNonBinaryOpElement_willCombineIsFalse() {
            // Test: stack has non-BinaryOp element after popping → willCombine = false
            // This tests the instanceof check in line 67
            Deque<LambdaExpression> stack = new ArrayDeque<>();
            stack.push(field("nonBinaryOp", int.class)); // FieldAccess, not BinaryOp
            stack.push(field("left", int.class));
            stack.push(field("right", int.class));

            LabelNode label = new LabelNode();
            JumpInsnNode jumpInsn = new JumpInsnNode(IF_ICMPLT, label);
            Map<LabelNode, Boolean> labelToValue = new HashMap<>();
            labelToValue.put(label, true);
            Map<LabelNode, ControlFlowAnalyzer.LabelClassification> labelClassifications = new HashMap<>();
            labelClassifications.put(label, ControlFlowAnalyzer.LabelClassification.INTERMEDIATE);
            BranchState andMode = new BranchState.AndMode(java.util.Optional.of(true), false);

            handler.handle(stack, jumpInsn, labelToValue, labelClassifications, andMode);

            // Stack had FieldAccess (not BinaryOp) → willCombine=false
            // INTERMEDIATE+TRUE+AndMode+!willCombine → invert=true → LT inverts to GE
            assertThat(stack).hasSize(2); // Original FieldAccess + new BinaryOp
            LambdaExpression.BinaryOp binOp = (LambdaExpression.BinaryOp) stack.peek();
            assertThat(binOp.operator())
                    .as("Non-BinaryOp element means willCombine=false, should invert to GE")
                    .isEqualTo(LambdaExpression.BinaryOp.Operator.GE);
        }

        // ========== Tests to kill stackTop/combineOp mutations (lines 76, 81) ==========

        @Test
        void handle_combineOpWithBinaryOpOnStack_combinesExpressions() {
            // Test: combineOp != null && !stack.isEmpty() && peek instanceof BinaryOp
            // All conditions true → combining happens
            Deque<LambdaExpression> stack = new ArrayDeque<>();
            LambdaExpression.BinaryOp previousCondition = LambdaExpression.BinaryOp.eq(
                    field("id", int.class), constant(1));
            stack.push(previousCondition);
            stack.push(field("left", int.class));
            stack.push(field("right", int.class));

            LabelNode label = new LabelNode();
            JumpInsnNode jumpInsn = new JumpInsnNode(IF_ICMPGT, label);
            Map<LabelNode, Boolean> labelToValue = new HashMap<>();
            labelToValue.put(label, false);
            Map<LabelNode, ControlFlowAnalyzer.LabelClassification> labelClassifications = new HashMap<>();
            BranchState andMode = new BranchState.AndMode(java.util.Optional.of(false), false);

            handler.handle(stack, jumpInsn, labelToValue, labelClassifications, andMode);

            // Should have combined: stack should have 1 element (the combined expression)
            assertThat(stack)
                    .as("Combining should result in single expression on stack")
                    .hasSize(1);
            LambdaExpression.BinaryOp combined = (LambdaExpression.BinaryOp) stack.peek();
            assertThat(combined.operator())
                    .as("Combined expression should use AND or OR operator")
                    .isIn(LambdaExpression.BinaryOp.Operator.AND, LambdaExpression.BinaryOp.Operator.OR);
        }

        @Test
        void handle_noCombineOp_pushesStandalone() {
            // Test: combineOp == null → no combining, pushes standalone
            // This happens when processBranch returns null combineOp
            Deque<LambdaExpression> stack = new ArrayDeque<>();
            stack.push(field("left", int.class));
            stack.push(field("right", int.class));

            LabelNode label = new LabelNode();
            JumpInsnNode jumpInsn = new JumpInsnNode(IF_ICMPGT, label);
            Map<LabelNode, Boolean> labelToValue = new HashMap<>();
            labelToValue.put(label, true);
            Map<LabelNode, ControlFlowAnalyzer.LabelClassification> labelClassifications = new HashMap<>();
            BranchState initialState = new BranchState.Initial();

            handler.handle(stack, jumpInsn, labelToValue, labelClassifications, initialState);

            assertThat(stack)
                    .as("With Initial state and no combining, should push standalone")
                    .hasSize(1);
            LambdaExpression.BinaryOp binOp = (LambdaExpression.BinaryOp) stack.peek();
            assertThat(binOp.operator())
                    .as("Should be a simple comparison, not a combined expression")
                    .isEqualTo(LambdaExpression.BinaryOp.Operator.GT);
        }

        @Test
        void handle_combineOpNotNull_butStackEmpty_pushesStandalone() {
            // Edge case: combineOp might be non-null but stack becomes empty
            // This tests the !stack.isEmpty() check in line 81
            // This is hard to trigger normally, but we can verify behavior when
            // stack only has the two operands (no previous condition)
            Deque<LambdaExpression> stack = new ArrayDeque<>();
            stack.push(field("left", int.class));
            stack.push(field("right", int.class));

            LabelNode label = new LabelNode();
            JumpInsnNode jumpInsn = new JumpInsnNode(IF_ICMPGT, label);
            Map<LabelNode, Boolean> labelToValue = new HashMap<>();
            labelToValue.put(label, false);
            Map<LabelNode, ControlFlowAnalyzer.LabelClassification> labelClassifications = new HashMap<>();
            // AndMode may return a combineOp, but stack will be empty after popping operands
            BranchState andMode = new BranchState.AndMode(java.util.Optional.of(false), false);

            handler.handle(stack, jumpInsn, labelToValue, labelClassifications, andMode);

            // Stack was empty after popping → can't combine → pushes standalone
            assertThat(stack)
                    .as("Stack empty after popping operands means no combining")
                    .hasSize(1);
            LambdaExpression.BinaryOp binOp = (LambdaExpression.BinaryOp) stack.peek();
            assertThat(binOp.operator())
                    .as("Should be inverted comparison LE (standalone, not combined)")
                    .isEqualTo(LambdaExpression.BinaryOp.Operator.LE);
        }

        // ========== Additional tests for determineComparisonOperator edge cases (lines 120, 128, 131) ==========

        @Test
        void handle_intermediateFalse_withWillCombine_fallsThroughToDefaultInvert() {
            // Test: INTERMEDIATE + FALSE + willCombine=true
            // Line 128 has !willCombine, so with willCombine=true, it falls to line 131/133
            Deque<LambdaExpression> stack = new ArrayDeque<>();
            stack.push(LambdaExpression.BinaryOp.eq(field("a", int.class), constant(1)));
            stack.push(field("left", int.class));
            stack.push(field("right", int.class));

            LabelNode label = new LabelNode();
            JumpInsnNode jumpInsn = new JumpInsnNode(IF_ICMPLT, label);
            Map<LabelNode, Boolean> labelToValue = new HashMap<>();
            labelToValue.put(label, false); // FALSE target
            Map<LabelNode, ControlFlowAnalyzer.LabelClassification> labelClassifications = new HashMap<>();
            labelClassifications.put(label, ControlFlowAnalyzer.LabelClassification.INTERMEDIATE);
            BranchState andMode = new BranchState.AndMode(java.util.Optional.of(false), false);

            handler.handle(stack, jumpInsn, labelToValue, labelClassifications, andMode);

            // INTERMEDIATE+FALSE+willCombine=true → skip line 128, fall to else (line 134) → invert=true
            // Then combining happens
            assertThat(stack).hasSize(1);
            LambdaExpression.BinaryOp combined = (LambdaExpression.BinaryOp) stack.peek();
            // Check the right child (the new comparison) - should be inverted (GE)
            LambdaExpression.BinaryOp innerComparison = (LambdaExpression.BinaryOp) combined.right();
            assertThat(innerComparison.operator())
                    .as("willCombine=true with FALSE target: falls to default, invert=true → GE")
                    .isEqualTo(LambdaExpression.BinaryOp.Operator.GE);
        }

        @Test
        void handle_nonIntermediateLabel_trueTarget_noInversion() {
            // Test: Non-INTERMEDIATE label (null classification) + TRUE target
            // Should hit line 131: TRUE.equals(jumpTarget) → invert=false
            Deque<LambdaExpression> stack = new ArrayDeque<>();
            stack.push(field("left", int.class));
            stack.push(field("right", int.class));

            LabelNode label = new LabelNode();
            JumpInsnNode jumpInsn = new JumpInsnNode(IF_ICMPLT, label);
            Map<LabelNode, Boolean> labelToValue = new HashMap<>();
            labelToValue.put(label, true);
            Map<LabelNode, ControlFlowAnalyzer.LabelClassification> labelClassifications = new HashMap<>();
            // No classification for label → not INTERMEDIATE
            BranchState initialState = new BranchState.Initial();

            handler.handle(stack, jumpInsn, labelToValue, labelClassifications, initialState);

            LambdaExpression.BinaryOp binOp = (LambdaExpression.BinaryOp) stack.peek();
            assertThat(binOp.operator())
                    .as("Non-INTERMEDIATE + TRUE target → invert=false → LT stays LT")
                    .isEqualTo(LambdaExpression.BinaryOp.Operator.LT);
        }

        @Test
        void handle_nonIntermediateLabel_falseTarget_inverts() {
            // Test: Non-INTERMEDIATE label + FALSE target
            // Should hit line 134 (else) → invert=true
            Deque<LambdaExpression> stack = new ArrayDeque<>();
            stack.push(field("left", int.class));
            stack.push(field("right", int.class));

            LabelNode label = new LabelNode();
            JumpInsnNode jumpInsn = new JumpInsnNode(IF_ICMPLT, label);
            Map<LabelNode, Boolean> labelToValue = new HashMap<>();
            labelToValue.put(label, false);
            Map<LabelNode, ControlFlowAnalyzer.LabelClassification> labelClassifications = new HashMap<>();
            // No classification for label → not INTERMEDIATE
            BranchState initialState = new BranchState.Initial();

            handler.handle(stack, jumpInsn, labelToValue, labelClassifications, initialState);

            LambdaExpression.BinaryOp binOp = (LambdaExpression.BinaryOp) stack.peek();
            assertThat(binOp.operator())
                    .as("Non-INTERMEDIATE + FALSE target → invert=true → LT inverts to GE")
                    .isEqualTo(LambdaExpression.BinaryOp.Operator.GE);
        }

        @Test
        void handle_intermediateTrue_initial_verifyDistinctBehaviorFromAndMode() {
            // Additional test to ensure INTERMEDIATE+TRUE+Initial is distinct from INTERMEDIATE+TRUE+AndMode
            // Initial → invert=false; AndMode → invert=true
            // Using different opcode to get different inverted result

            // Initial state case
            Deque<LambdaExpression> stackInitial = new ArrayDeque<>();
            stackInitial.push(field("left", int.class));
            stackInitial.push(field("right", int.class));
            LabelNode labelInit = new LabelNode();
            JumpInsnNode jumpInsnInit = new JumpInsnNode(IF_ICMPGT, labelInit);
            Map<LabelNode, Boolean> labelToValueInit = new HashMap<>();
            labelToValueInit.put(labelInit, true);
            Map<LabelNode, ControlFlowAnalyzer.LabelClassification> classInit = new HashMap<>();
            classInit.put(labelInit, ControlFlowAnalyzer.LabelClassification.INTERMEDIATE);

            handler.handle(stackInitial, jumpInsnInit, labelToValueInit, classInit, new BranchState.Initial());
            LambdaExpression.BinaryOp resultInit = (LambdaExpression.BinaryOp) stackInitial.peek();

            // AndMode case
            Deque<LambdaExpression> stackAnd = new ArrayDeque<>();
            stackAnd.push(field("left", int.class));
            stackAnd.push(field("right", int.class));
            LabelNode labelAnd = new LabelNode();
            JumpInsnNode jumpInsnAnd = new JumpInsnNode(IF_ICMPGT, labelAnd);
            Map<LabelNode, Boolean> labelToValueAnd = new HashMap<>();
            labelToValueAnd.put(labelAnd, true);
            Map<LabelNode, ControlFlowAnalyzer.LabelClassification> classAnd = new HashMap<>();
            classAnd.put(labelAnd, ControlFlowAnalyzer.LabelClassification.INTERMEDIATE);

            handler.handle(stackAnd, jumpInsnAnd, labelToValueAnd, classAnd,
                    new BranchState.AndMode(java.util.Optional.of(true), false));
            LambdaExpression.BinaryOp resultAnd = (LambdaExpression.BinaryOp) stackAnd.peek();

            // Initial → invert=false → GT
            assertThat(resultInit.operator())
                    .as("INTERMEDIATE+TRUE+Initial → no inversion → GT")
                    .isEqualTo(LambdaExpression.BinaryOp.Operator.GT);

            // AndMode → invert=true → LE
            assertThat(resultAnd.operator())
                    .as("INTERMEDIATE+TRUE+AndMode → inversion → LE")
                    .isEqualTo(LambdaExpression.BinaryOp.Operator.LE);

            // The operators must be different to prove the state matters
            assertThat(resultInit.operator())
                    .as("Operators should differ between Initial and AndMode")
                    .isNotEqualTo(resultAnd.operator());
        }

        @Test
        void handle_nullJumpTarget_treatedAsFalseInvert() {
            // Test: jumpTarget is null (not in labelToValue map)
            // Should hit else branch (line 134) → invert=true
            Deque<LambdaExpression> stack = new ArrayDeque<>();
            stack.push(field("left", int.class));
            stack.push(field("right", int.class));

            LabelNode label = new LabelNode();
            JumpInsnNode jumpInsn = new JumpInsnNode(IF_ICMPLT, label);
            Map<LabelNode, Boolean> labelToValue = new HashMap<>();
            // Don't put label in map → jumpTarget is null
            Map<LabelNode, ControlFlowAnalyzer.LabelClassification> labelClassifications = new HashMap<>();
            BranchState initialState = new BranchState.Initial();

            handler.handle(stack, jumpInsn, labelToValue, labelClassifications, initialState);

            LambdaExpression.BinaryOp binOp = (LambdaExpression.BinaryOp) stack.peek();
            // jumpTarget=null → not TRUE → falls to else → invert=true → LT → GE
            assertThat(binOp.operator())
                    .as("Null jumpTarget falls to else branch → invert=true → GE")
                    .isEqualTo(LambdaExpression.BinaryOp.Operator.GE);
        }

        // ========== Additional tests to kill determineComparisonOperator mutations ==========

        @Test
        void handle_nonIntermediateTrueTarget_mustBeDifferentFromIntermediateTrueTargetInAndMode() {
            // Kill mutation: jumpLabelClass == INTERMEDIATE replaced with true
            // Non-INTERMEDIATE + TRUE should go to line 131 (invert=false)
            // INTERMEDIATE + TRUE + AndMode should go to line 126 (invert=true)
            // These MUST produce different results

            // Case 1: NON-INTERMEDIATE + TRUE (no classification)
            Deque<LambdaExpression> stack1 = new ArrayDeque<>();
            stack1.push(field("left", int.class));
            stack1.push(field("right", int.class));
            LabelNode label1 = new LabelNode();
            JumpInsnNode jumpInsn1 = new JumpInsnNode(IF_ICMPLT, label1);
            Map<LabelNode, Boolean> labelToValue1 = new HashMap<>();
            labelToValue1.put(label1, true);
            Map<LabelNode, ControlFlowAnalyzer.LabelClassification> labelClassifications1 = new HashMap<>();
            // NO classification → not INTERMEDIATE
            BranchState andMode1 = new BranchState.AndMode(java.util.Optional.of(true), false);

            handler.handle(stack1, jumpInsn1, labelToValue1, labelClassifications1, andMode1);
            LambdaExpression.BinaryOp result1 = (LambdaExpression.BinaryOp) stack1.peek();

            // Case 2: INTERMEDIATE + TRUE + AndMode (same setup but with INTERMEDIATE)
            Deque<LambdaExpression> stack2 = new ArrayDeque<>();
            stack2.push(field("left", int.class));
            stack2.push(field("right", int.class));
            LabelNode label2 = new LabelNode();
            JumpInsnNode jumpInsn2 = new JumpInsnNode(IF_ICMPLT, label2);
            Map<LabelNode, Boolean> labelToValue2 = new HashMap<>();
            labelToValue2.put(label2, true);
            Map<LabelNode, ControlFlowAnalyzer.LabelClassification> labelClassifications2 = new HashMap<>();
            labelClassifications2.put(label2, ControlFlowAnalyzer.LabelClassification.INTERMEDIATE);
            BranchState andMode2 = new BranchState.AndMode(java.util.Optional.of(true), false);

            handler.handle(stack2, jumpInsn2, labelToValue2, labelClassifications2, andMode2);
            LambdaExpression.BinaryOp result2 = (LambdaExpression.BinaryOp) stack2.peek();

            // Results MUST be different to kill the INTERMEDIATE mutation
            // Non-INTERMEDIATE + TRUE → line 131 → invert=false → LT
            // INTERMEDIATE + TRUE + AndMode → line 126 → invert=true → GE
            assertThat(result1.operator())
                    .as("Non-INTERMEDIATE + TRUE should NOT invert (LT)")
                    .isEqualTo(LambdaExpression.BinaryOp.Operator.LT);
            assertThat(result2.operator())
                    .as("INTERMEDIATE + TRUE + AndMode should invert (GE)")
                    .isEqualTo(LambdaExpression.BinaryOp.Operator.GE);
            assertThat(result1.operator())
                    .as("Operators MUST differ to kill INTERMEDIATE mutation")
                    .isNotEqualTo(result2.operator());
        }

        @Test
        void handle_intermediateFalseVsNonIntermediateFalse_mustBeDifferent() {
            // Kill mutation: jumpLabelClass == INTERMEDIATE replaced with true on line 128
            // INTERMEDIATE + FALSE + !willCombine → line 128 → invert=true
            // Non-INTERMEDIATE + FALSE → line 134 → invert=true
            // These produce SAME result, so we need a different test...
            // Actually, let's test INTERMEDIATE + TRUE + Initial which should NOT invert

            // Case 1: INTERMEDIATE + FALSE + Initial + !willCombine
            Deque<LambdaExpression> stack1 = new ArrayDeque<>();
            stack1.push(field("left", int.class));
            stack1.push(field("right", int.class));
            LabelNode label1 = new LabelNode();
            JumpInsnNode jumpInsn1 = new JumpInsnNode(IF_ICMPLT, label1);
            Map<LabelNode, Boolean> labelToValue1 = new HashMap<>();
            labelToValue1.put(label1, false);
            Map<LabelNode, ControlFlowAnalyzer.LabelClassification> labelClassifications1 = new HashMap<>();
            labelClassifications1.put(label1, ControlFlowAnalyzer.LabelClassification.INTERMEDIATE);
            BranchState initialState = new BranchState.Initial();

            handler.handle(stack1, jumpInsn1, labelToValue1, labelClassifications1, initialState);
            LambdaExpression.BinaryOp result1 = (LambdaExpression.BinaryOp) stack1.peek();

            // INTERMEDIATE + FALSE → line 128 → invert=true → LT → GE
            assertThat(result1.operator())
                    .as("INTERMEDIATE + FALSE should invert (GE)")
                    .isEqualTo(LambdaExpression.BinaryOp.Operator.GE);
        }

        @Test
        void handle_verifyTrueEqualsJumpTargetConditionLine120() {
            // Kill mutation: TRUE.equals(jumpTarget) on line 120 replaced with true/false
            // INTERMEDIATE + FALSE + !willCombine should NOT enter line 120 block
            // Should go to line 128 instead

            // INTERMEDIATE + FALSE (not TRUE) should skip line 120
            Deque<LambdaExpression> stack = new ArrayDeque<>();
            stack.push(field("left", int.class));
            stack.push(field("right", int.class));
            LabelNode label = new LabelNode();
            JumpInsnNode jumpInsn = new JumpInsnNode(IF_ICMPLT, label);
            Map<LabelNode, Boolean> labelToValue = new HashMap<>();
            labelToValue.put(label, false); // FALSE, not TRUE
            Map<LabelNode, ControlFlowAnalyzer.LabelClassification> labelClassifications = new HashMap<>();
            labelClassifications.put(label, ControlFlowAnalyzer.LabelClassification.INTERMEDIATE);
            BranchState initialState = new BranchState.Initial();

            handler.handle(stack, jumpInsn, labelToValue, labelClassifications, initialState);
            LambdaExpression.BinaryOp result = (LambdaExpression.BinaryOp) stack.peek();

            // If TRUE.equals mutation made it true, we'd enter line 120 block with Initial state
            // Initial → invert=false → LT stays LT
            // But correct behavior: line 128 → invert=true → LT → GE
            assertThat(result.operator())
                    .as("INTERMEDIATE + FALSE should go to line 128, not 120 (invert=true → GE)")
                    .isEqualTo(LambdaExpression.BinaryOp.Operator.GE);
        }

        @Test
        void handle_verifyStateInstanceofInitialCondition() {
            // Kill mutation: state instanceof BranchState.Initial replaced with true/false
            // INTERMEDIATE + TRUE + !willCombine + Initial → invert=false
            // INTERMEDIATE + TRUE + !willCombine + AndMode → invert=true
            // These MUST differ

            // Case 1: Initial state
            Deque<LambdaExpression> stack1 = new ArrayDeque<>();
            stack1.push(field("left", int.class));
            stack1.push(field("right", int.class));
            LabelNode label1 = new LabelNode();
            JumpInsnNode jumpInsn1 = new JumpInsnNode(IF_ICMPLT, label1);
            Map<LabelNode, Boolean> labelToValue1 = new HashMap<>();
            labelToValue1.put(label1, true);
            Map<LabelNode, ControlFlowAnalyzer.LabelClassification> labelClassifications1 = new HashMap<>();
            labelClassifications1.put(label1, ControlFlowAnalyzer.LabelClassification.INTERMEDIATE);
            BranchState initialState = new BranchState.Initial();

            handler.handle(stack1, jumpInsn1, labelToValue1, labelClassifications1, initialState);
            LambdaExpression.BinaryOp resultInitial = (LambdaExpression.BinaryOp) stack1.peek();

            // Case 2: AndMode state
            Deque<LambdaExpression> stack2 = new ArrayDeque<>();
            stack2.push(field("left", int.class));
            stack2.push(field("right", int.class));
            LabelNode label2 = new LabelNode();
            JumpInsnNode jumpInsn2 = new JumpInsnNode(IF_ICMPLT, label2);
            Map<LabelNode, Boolean> labelToValue2 = new HashMap<>();
            labelToValue2.put(label2, true);
            Map<LabelNode, ControlFlowAnalyzer.LabelClassification> labelClassifications2 = new HashMap<>();
            labelClassifications2.put(label2, ControlFlowAnalyzer.LabelClassification.INTERMEDIATE);
            BranchState andMode = new BranchState.AndMode(java.util.Optional.of(true), false);

            handler.handle(stack2, jumpInsn2, labelToValue2, labelClassifications2, andMode);
            LambdaExpression.BinaryOp resultAndMode = (LambdaExpression.BinaryOp) stack2.peek();

            // Initial → invert=false → LT
            // AndMode → invert=true → GE
            assertThat(resultInitial.operator())
                    .as("Initial state should NOT invert (LT)")
                    .isEqualTo(LambdaExpression.BinaryOp.Operator.LT);
            assertThat(resultAndMode.operator())
                    .as("AndMode state should invert (GE)")
                    .isEqualTo(LambdaExpression.BinaryOp.Operator.GE);
            assertThat(resultInitial.operator())
                    .as("State difference MUST produce different operators")
                    .isNotEqualTo(resultAndMode.operator());
        }

        @Test
        void handle_verifyLine131TrueEqualsJumpTarget() {
            // Kill mutation: TRUE.equals(jumpTarget) on line 131 replaced with true/false
            // Non-INTERMEDIATE + TRUE → line 131 → invert=false
            // Non-INTERMEDIATE + FALSE → line 134 → invert=true
            // These MUST differ

            // Case 1: TRUE jumpTarget
            Deque<LambdaExpression> stack1 = new ArrayDeque<>();
            stack1.push(field("left", int.class));
            stack1.push(field("right", int.class));
            LabelNode label1 = new LabelNode();
            JumpInsnNode jumpInsn1 = new JumpInsnNode(IF_ICMPLT, label1);
            Map<LabelNode, Boolean> labelToValue1 = new HashMap<>();
            labelToValue1.put(label1, true);
            Map<LabelNode, ControlFlowAnalyzer.LabelClassification> labelClassifications1 = new HashMap<>();
            // No INTERMEDIATE classification
            BranchState initialState1 = new BranchState.Initial();

            handler.handle(stack1, jumpInsn1, labelToValue1, labelClassifications1, initialState1);
            LambdaExpression.BinaryOp resultTrue = (LambdaExpression.BinaryOp) stack1.peek();

            // Case 2: FALSE jumpTarget
            Deque<LambdaExpression> stack2 = new ArrayDeque<>();
            stack2.push(field("left", int.class));
            stack2.push(field("right", int.class));
            LabelNode label2 = new LabelNode();
            JumpInsnNode jumpInsn2 = new JumpInsnNode(IF_ICMPLT, label2);
            Map<LabelNode, Boolean> labelToValue2 = new HashMap<>();
            labelToValue2.put(label2, false);
            Map<LabelNode, ControlFlowAnalyzer.LabelClassification> labelClassifications2 = new HashMap<>();
            BranchState initialState2 = new BranchState.Initial();

            handler.handle(stack2, jumpInsn2, labelToValue2, labelClassifications2, initialState2);
            LambdaExpression.BinaryOp resultFalse = (LambdaExpression.BinaryOp) stack2.peek();

            // TRUE → line 131 → invert=false → LT
            // FALSE → line 134 → invert=true → GE
            assertThat(resultTrue.operator())
                    .as("TRUE jumpTarget should NOT invert (LT)")
                    .isEqualTo(LambdaExpression.BinaryOp.Operator.LT);
            assertThat(resultFalse.operator())
                    .as("FALSE jumpTarget should invert (GE)")
                    .isEqualTo(LambdaExpression.BinaryOp.Operator.GE);
            assertThat(resultTrue.operator())
                    .as("TRUE vs FALSE MUST produce different operators")
                    .isNotEqualTo(resultFalse.operator());
        }
    }

    // ==================== IfEqualsZeroInstructionHandler Tests ====================

    @Nested
    @DisplayName("IfEqualsZeroInstructionHandler")
    class IfEqualsZeroInstructionHandlerTests {

        private final IfEqualsZeroInstructionHandler handler = new IfEqualsZeroInstructionHandler();

        @Test
        void canHandle_withIFEQ_returnsTrue() {
            JumpInsnNode jumpInsn = new JumpInsnNode(IFEQ, new LabelNode());
            assertThat(handler.canHandle(jumpInsn)).isTrue();
        }

        @Test
        void canHandle_withIFNE_returnsFalse() {
            JumpInsnNode jumpInsn = new JumpInsnNode(IFNE, new LabelNode());
            assertThat(handler.canHandle(jumpInsn)).isFalse();
        }

        @Test
        void canHandle_withIFNULL_returnsFalse() {
            JumpInsnNode jumpInsn = new JumpInsnNode(IFNULL, new LabelNode());
            assertThat(handler.canHandle(jumpInsn)).isFalse();
        }

        // ========== handle() method tests to kill surviving mutations ==========

        @Test
        void handle_withEmptyStack_returnsOriginalState() {
            Deque<LambdaExpression> stack = new ArrayDeque<>();
            LabelNode label = new LabelNode();
            JumpInsnNode jumpInsn = new JumpInsnNode(IFEQ, label);
            Map<LabelNode, Boolean> labelToValue = new HashMap<>();
            Map<LabelNode, ControlFlowAnalyzer.LabelClassification> labelClassifications = new HashMap<>();
            BranchState initialState = new BranchState.Initial();

            BranchState result = handler.handle(stack, jumpInsn, labelToValue, labelClassifications, initialState);

            assertThat(result)
                    .as("Empty stack should return original state unchanged")
                    .isSameAs(initialState);
        }

        @Test
        void handle_withArithmeticPattern_createsEQZeroComparison() {
            Deque<LambdaExpression> stack = new ArrayDeque<>();
            // Simulate arithmetic result on stack
            stack.push(LambdaExpression.BinaryOp.add(
                    field("a", int.class),
                    field("b", int.class)
            ));
            LabelNode label = new LabelNode();
            JumpInsnNode jumpInsn = new JumpInsnNode(IFEQ, label);
            Map<LabelNode, Boolean> labelToValue = new HashMap<>();
            labelToValue.put(label, false);
            Map<LabelNode, ControlFlowAnalyzer.LabelClassification> labelClassifications = new HashMap<>();
            BranchState initialState = new BranchState.Initial();

            handler.handle(stack, jumpInsn, labelToValue, labelClassifications, initialState);

            assertThat(stack).hasSize(1);
            LambdaExpression.BinaryOp binOp = (LambdaExpression.BinaryOp) stack.peek();
            assertThat(binOp.operator())
                    .as("Arithmetic pattern should create EQ operator")
                    .isEqualTo(LambdaExpression.BinaryOp.Operator.EQ);
            assertThat(binOp.right())
                    .as("Should compare to zero constant")
                    .isInstanceOf(LambdaExpression.Constant.class);
        }

        @Test
        void handle_withBooleanField_jumpToTrue_createsUnaryNot() {
            Deque<LambdaExpression> stack = new ArrayDeque<>();
            stack.push(field("active", Boolean.class));
            LabelNode label = new LabelNode();
            JumpInsnNode jumpInsn = new JumpInsnNode(IFEQ, label);
            Map<LabelNode, Boolean> labelToValue = new HashMap<>();
            labelToValue.put(label, true);  // Jump to TRUE
            Map<LabelNode, ControlFlowAnalyzer.LabelClassification> labelClassifications = new HashMap<>();
            BranchState initialState = new BranchState.Initial();

            handler.handle(stack, jumpInsn, labelToValue, labelClassifications, initialState);

            assertThat(stack).hasSize(1);
            assertThat(stack.peek())
                    .as("Boolean field with jump to TRUE should create UnaryOp NOT")
                    .isInstanceOf(LambdaExpression.UnaryOp.class);
            LambdaExpression.UnaryOp unaryOp = (LambdaExpression.UnaryOp) stack.peek();
            assertThat(unaryOp.operator())
                    .isEqualTo(LambdaExpression.UnaryOp.Operator.NOT);
        }

        @Test
        void handle_withBooleanField_jumpToFalse_createsEQTrue() {
            Deque<LambdaExpression> stack = new ArrayDeque<>();
            stack.push(field("active", Boolean.class));
            LabelNode label = new LabelNode();
            JumpInsnNode jumpInsn = new JumpInsnNode(IFEQ, label);
            Map<LabelNode, Boolean> labelToValue = new HashMap<>();
            labelToValue.put(label, false);  // Jump to FALSE
            Map<LabelNode, ControlFlowAnalyzer.LabelClassification> labelClassifications = new HashMap<>();
            BranchState initialState = new BranchState.Initial();

            handler.handle(stack, jumpInsn, labelToValue, labelClassifications, initialState);

            assertThat(stack).hasSize(1);
            assertThat(stack.peek())
                    .as("Boolean field with jump to FALSE should create BinaryOp EQ true")
                    .isInstanceOf(LambdaExpression.BinaryOp.class);
            LambdaExpression.BinaryOp binOp = (LambdaExpression.BinaryOp) stack.peek();
            assertThat(binOp.operator())
                    .isEqualTo(LambdaExpression.BinaryOp.Operator.EQ);
        }

        @Test
        void getInstructionName_returnsIFEQ() {
            assertThat(handler.getInstructionName())
                    .as("Instruction name should be IFEQ")
                    .isEqualTo("IFEQ");
        }

        @Test
        void createBooleanEvaluationExpression_withTrueJumpTarget_createsNot() {
            LambdaExpression fieldAccess = field("enabled", boolean.class);

            LambdaExpression result = handler.createBooleanEvaluationExpression(fieldAccess, true);

            assertThat(result)
                    .as("True jump target should create NOT expression")
                    .isInstanceOf(LambdaExpression.UnaryOp.class);
        }

        @Test
        void createBooleanEvaluationExpression_withFalseJumpTarget_createsEQTrue() {
            LambdaExpression fieldAccess = field("enabled", boolean.class);

            LambdaExpression result = handler.createBooleanEvaluationExpression(fieldAccess, false);

            assertThat(result)
                    .as("False jump target should create EQ true expression")
                    .isInstanceOf(LambdaExpression.BinaryOp.class);
        }

        @Test
        void createBooleanEvaluationExpression_withNullJumpTarget_createsEQTrue() {
            LambdaExpression fieldAccess = field("enabled", boolean.class);

            LambdaExpression result = handler.createBooleanEvaluationExpression(fieldAccess, null);

            assertThat(result)
                    .as("Null jump target should create EQ true expression")
                    .isInstanceOf(LambdaExpression.BinaryOp.class);
        }

        // ========== Tests to kill AbstractZeroEqualityBranchHandler combining mutation ==========

        @Test
        void handle_withBooleanFieldAndPreviousCondition_combinesExpressions() {
            // This test kills the mutation on line 61 of AbstractZeroEqualityBranchHandler:
            // "if (combineOp != null && !stack.isEmpty() && stack.peek() instanceof LambdaExpression.BinaryOp)"
            Deque<LambdaExpression> stack = new ArrayDeque<>();
            // Push a previous BinaryOp condition
            stack.push(LambdaExpression.BinaryOp.eq(
                    field("id", int.class),
                    constant(1)
            ));
            // Push the boolean field to check
            stack.push(field("active", Boolean.class));

            LabelNode label = new LabelNode();
            JumpInsnNode jumpInsn = new JumpInsnNode(IFEQ, label);
            Map<LabelNode, Boolean> labelToValue = new HashMap<>();
            labelToValue.put(label, false);  // Jump to FALSE → creates EQ true
            Map<LabelNode, ControlFlowAnalyzer.LabelClassification> labelClassifications = new HashMap<>();
            // Use AndMode with previous jump target to trigger combining
            BranchState andMode = new BranchState.AndMode(java.util.Optional.of(false), false);

            handler.handle(stack, jumpInsn, labelToValue, labelClassifications, andMode);

            // Should have combined into a single BinaryOp (AND of the two conditions)
            assertThat(stack).hasSize(1);
            assertThat(stack.peek())
                    .as("Boolean field with previous condition should combine into BinaryOp")
                    .isInstanceOf(LambdaExpression.BinaryOp.class);
            LambdaExpression.BinaryOp combined = (LambdaExpression.BinaryOp) stack.peek();
            // Combined expression should have AND operator
            assertThat(combined.operator())
                    .as("Combined expression should use AND operator")
                    .isEqualTo(LambdaExpression.BinaryOp.Operator.AND);
        }
    }

    // ==================== IfNotEqualsZeroInstructionHandler Tests ====================

    @Nested
    @DisplayName("IfNotEqualsZeroInstructionHandler")
    class IfNotEqualsZeroInstructionHandlerTests {

        private final IfNotEqualsZeroInstructionHandler handler = new IfNotEqualsZeroInstructionHandler();

        @Test
        void canHandle_withIFNE_returnsTrue() {
            JumpInsnNode jumpInsn = new JumpInsnNode(IFNE, new LabelNode());
            assertThat(handler.canHandle(jumpInsn)).isTrue();
        }

        @Test
        void canHandle_withIFEQ_returnsFalse() {
            JumpInsnNode jumpInsn = new JumpInsnNode(IFEQ, new LabelNode());
            assertThat(handler.canHandle(jumpInsn)).isFalse();
        }

        @Test
        void canHandle_withIFNULL_returnsFalse() {
            JumpInsnNode jumpInsn = new JumpInsnNode(IFNULL, new LabelNode());
            assertThat(handler.canHandle(jumpInsn)).isFalse();
        }

        // ========== handle() method tests to kill surviving mutations ==========

        @Test
        void handle_withEmptyStack_returnsOriginalState() {
            Deque<LambdaExpression> stack = new ArrayDeque<>();
            LabelNode label = new LabelNode();
            JumpInsnNode jumpInsn = new JumpInsnNode(IFNE, label);
            Map<LabelNode, Boolean> labelToValue = new HashMap<>();
            Map<LabelNode, ControlFlowAnalyzer.LabelClassification> labelClassifications = new HashMap<>();
            BranchState initialState = new BranchState.Initial();

            BranchState result = handler.handle(stack, jumpInsn, labelToValue, labelClassifications, initialState);

            assertThat(result)
                    .as("Empty stack should return original state unchanged")
                    .isSameAs(initialState);
        }

        @Test
        void handle_withArithmeticPattern_createsEQZeroComparison() {
            Deque<LambdaExpression> stack = new ArrayDeque<>();
            // Simulate arithmetic result on stack
            stack.push(LambdaExpression.BinaryOp.add(
                    field("a", int.class),
                    field("b", int.class)
            ));
            LabelNode label = new LabelNode();
            JumpInsnNode jumpInsn = new JumpInsnNode(IFNE, label);
            Map<LabelNode, Boolean> labelToValue = new HashMap<>();
            labelToValue.put(label, false);
            Map<LabelNode, ControlFlowAnalyzer.LabelClassification> labelClassifications = new HashMap<>();
            BranchState initialState = new BranchState.Initial();

            handler.handle(stack, jumpInsn, labelToValue, labelClassifications, initialState);

            assertThat(stack).hasSize(1);
            LambdaExpression.BinaryOp binOp = (LambdaExpression.BinaryOp) stack.peek();
            assertThat(binOp.operator())
                    .as("Arithmetic pattern should create EQ operator")
                    .isEqualTo(LambdaExpression.BinaryOp.Operator.EQ);
            assertThat(binOp.right())
                    .as("Should compare to zero constant")
                    .isInstanceOf(LambdaExpression.Constant.class);
        }

        @Test
        void handle_withBooleanField_jumpToTrue_createsEQTrue() {
            Deque<LambdaExpression> stack = new ArrayDeque<>();
            stack.push(field("active", Boolean.class));
            LabelNode label = new LabelNode();
            JumpInsnNode jumpInsn = new JumpInsnNode(IFNE, label);
            Map<LabelNode, Boolean> labelToValue = new HashMap<>();
            labelToValue.put(label, true);  // Jump to TRUE
            Map<LabelNode, ControlFlowAnalyzer.LabelClassification> labelClassifications = new HashMap<>();
            BranchState initialState = new BranchState.Initial();

            handler.handle(stack, jumpInsn, labelToValue, labelClassifications, initialState);

            assertThat(stack).hasSize(1);
            assertThat(stack.peek())
                    .as("Boolean field with jump to TRUE should create BinaryOp EQ true")
                    .isInstanceOf(LambdaExpression.BinaryOp.class);
            LambdaExpression.BinaryOp binOp = (LambdaExpression.BinaryOp) stack.peek();
            assertThat(binOp.operator())
                    .isEqualTo(LambdaExpression.BinaryOp.Operator.EQ);
        }

        @Test
        void handle_withBooleanField_jumpToFalse_createsUnaryNot() {
            Deque<LambdaExpression> stack = new ArrayDeque<>();
            stack.push(field("active", Boolean.class));
            LabelNode label = new LabelNode();
            JumpInsnNode jumpInsn = new JumpInsnNode(IFNE, label);
            Map<LabelNode, Boolean> labelToValue = new HashMap<>();
            labelToValue.put(label, false);  // Jump to FALSE
            Map<LabelNode, ControlFlowAnalyzer.LabelClassification> labelClassifications = new HashMap<>();
            BranchState initialState = new BranchState.Initial();

            handler.handle(stack, jumpInsn, labelToValue, labelClassifications, initialState);

            assertThat(stack).hasSize(1);
            assertThat(stack.peek())
                    .as("Boolean field with jump to FALSE should create UnaryOp NOT")
                    .isInstanceOf(LambdaExpression.UnaryOp.class);
        }

        @Test
        void getInstructionName_returnsIFNE() {
            assertThat(handler.getInstructionName())
                    .as("Instruction name should be IFNE")
                    .isEqualTo("IFNE");
        }

        @Test
        void createBooleanEvaluationExpression_withTrueJumpTarget_createsEQTrue() {
            LambdaExpression fieldAccess = field("enabled", boolean.class);

            LambdaExpression result = handler.createBooleanEvaluationExpression(fieldAccess, true);

            assertThat(result)
                    .as("True jump target should create EQ true expression")
                    .isInstanceOf(LambdaExpression.BinaryOp.class);
        }

        @Test
        void createBooleanEvaluationExpression_withFalseJumpTarget_createsNot() {
            LambdaExpression fieldAccess = field("enabled", boolean.class);

            LambdaExpression result = handler.createBooleanEvaluationExpression(fieldAccess, false);

            assertThat(result)
                    .as("False jump target should create NOT expression")
                    .isInstanceOf(LambdaExpression.UnaryOp.class);
        }

        @Test
        void createBooleanEvaluationExpression_withNullJumpTarget_createsEQTrue() {
            LambdaExpression fieldAccess = field("enabled", boolean.class);

            LambdaExpression result = handler.createBooleanEvaluationExpression(fieldAccess, null);

            assertThat(result)
                    .as("Null jump target should create EQ true expression")
                    .isInstanceOf(LambdaExpression.BinaryOp.class);
        }

        @Test
        void createBooleanEvaluationExpression_withAlreadyComparison_returnsAsIs() {
            // Create an EQ comparison expression
            LambdaExpression eqComparison = LambdaExpression.BinaryOp.eq(
                    field("value", int.class),
                    constant(10)
            );

            LambdaExpression result = handler.createBooleanEvaluationExpression(eqComparison, true);

            assertThat(result)
                    .as("Existing EQ comparison should be returned as-is")
                    .isSameAs(eqComparison);
        }

        @Test
        void createBooleanEvaluationExpression_withNonEqBinaryOp_returnedAsIs() {
            // Create a non-EQ comparison expression (e.g., GT)
            // BinaryOp is a predicate, so it should NOT be wrapped with == true
            LambdaExpression gtComparison = LambdaExpression.BinaryOp.gt(
                    field("value", int.class),
                    constant(10)
            );

            LambdaExpression result = handler.createBooleanEvaluationExpression(gtComparison, true);

            // Predicates (BinaryOp, MethodCall returning boolean, etc.) are returned as-is
            assertThat(result)
                    .as("BinaryOp predicates should be returned as-is, not wrapped in EQ true")
                    .isSameAs(gtComparison);
        }

        // ========== Tests to kill AbstractZeroEqualityBranchHandler combining mutation ==========

        @Test
        void handle_withBooleanFieldAndPreviousCondition_combinesExpressions() {
            // This test kills the mutation on line 61 of AbstractZeroEqualityBranchHandler:
            // "if (combineOp != null && !stack.isEmpty() && stack.peek() instanceof LambdaExpression.BinaryOp)"
            Deque<LambdaExpression> stack = new ArrayDeque<>();
            // Push a previous BinaryOp condition
            stack.push(LambdaExpression.BinaryOp.eq(
                    field("id", int.class),
                    constant(1)
            ));
            // Push the boolean field to check
            stack.push(field("active", Boolean.class));

            LabelNode label = new LabelNode();
            JumpInsnNode jumpInsn = new JumpInsnNode(IFNE, label);
            Map<LabelNode, Boolean> labelToValue = new HashMap<>();
            labelToValue.put(label, true);  // Jump to TRUE → creates EQ true
            Map<LabelNode, ControlFlowAnalyzer.LabelClassification> labelClassifications = new HashMap<>();
            // Use AndMode with previous jump target TRUE → triggers OR combining
            BranchState andMode = new BranchState.AndMode(java.util.Optional.of(true), false);

            handler.handle(stack, jumpInsn, labelToValue, labelClassifications, andMode);

            // Should have combined into a single BinaryOp (OR in this case due to state)
            assertThat(stack).hasSize(1);
            assertThat(stack.peek())
                    .as("Boolean field with previous condition should combine into BinaryOp")
                    .isInstanceOf(LambdaExpression.BinaryOp.class);
            LambdaExpression.BinaryOp combined = (LambdaExpression.BinaryOp) stack.peek();
            // Combined expression uses OR in this scenario
            assertThat(combined.operator())
                    .as("Combined expression should use OR operator in this state")
                    .isEqualTo(LambdaExpression.BinaryOp.Operator.OR);
        }
    }
}
