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

    /** Helper to create BranchContext from individual parameters. */
    private static BranchContext ctx(
            Deque<LambdaExpression> stack,
            JumpInsnNode jumpInsn,
            Map<LabelNode, Boolean> labelToValue,
            Map<LabelNode, ControlFlowAnalyzer.LabelClassification> labelClassifications,
            BranchState state,
            boolean sameLabel,
            boolean completingAndGroup,
            boolean startingNewOrGroup) {
        return new BranchContext(stack, jumpInsn, labelToValue, labelClassifications,
                state, sameLabel, completingAndGroup, startingNewOrGroup);
    }

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

            BranchState result = handler.handle(ctx(stack, jumpInsn, labelToValue, labelClassifications, initialState, false, false, false));

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

            handler.handle(ctx(stack, jumpInsn, labelToValue, labelClassifications, initialState, false, false, false));

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

            handler.handle(ctx(stack, jumpInsn, labelToValue, labelClassifications, initialState, false, false, false));

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

            handler.handle(ctx(stack, jumpInsn, labelToValue, labelClassifications, initialState, false, false, false));

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

            handler.handle(ctx(stack, jumpInsn, labelToValue, labelClassifications, initialState, false, false, false));

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

            handler.handle(ctx(stack, jumpInsn, labelToValue, labelClassifications, andMode, false, false, false));

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

            BranchState result = handler.handle(ctx(stack, jumpInsn, labelToValue, labelClassifications, initialState, false, false, false));

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

            handler.handle(ctx(stack, jumpInsn, labelToValue, labelClassifications, initialState, false, false, false));

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

            handler.handle(ctx(stack, jumpInsn, labelToValue, labelClassifications, initialState, false, false, false));

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

            handler.handle(ctx(stack, jumpInsn, labelToValue, labelClassifications, initialState, false, false, false));

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

            handler.handle(ctx(stack, jumpInsn, labelToValue, labelClassifications, initialState, false, false, false));

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

            handler.handle(ctx(stack, jumpInsn, labelToValue, labelClassifications, initialState, false, false, false));

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

            handler.handle(ctx(stack, jumpInsn, labelToValue, labelClassifications, initialState, false, false, false));

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

            handler.handle(ctx(stack, jumpInsn, labelToValue, labelClassifications, initialState, false, false, false));

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

            handler.handle(ctx(stack, jumpInsn, labelToValue, labelClassifications, initialState, false, false, false));

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

            handler.handle(ctx(stack, jumpInsn, labelToValue, labelClassifications, andMode, false, false, false));

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

            handler.handle(ctx(stack, jumpInsn, labelToValue, labelClassifications, andMode, false, false, false));

            assertThat(stack).hasSize(1);
            LambdaExpression.BinaryOp binOp = (LambdaExpression.BinaryOp) stack.peek();
            assertThat(binOp.operator())
                    .as("IFLE in AndMode with INTERMEDIATE→TRUE should invert to GT")
                    .isEqualTo(LambdaExpression.BinaryOp.Operator.GT);
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

            handler.handle(ctx(stack, jumpInsn, labelToValue, labelClassifications, initialState, false, false, false));

            assertThat(stack).hasSize(1);
            LambdaExpression.BinaryOp binOp = (LambdaExpression.BinaryOp) stack.peek();
            assertThat(binOp.operator())
                    .as("IFGE with INTERMEDIATE→FALSE should invert to LT")
                    .isEqualTo(LambdaExpression.BinaryOp.Operator.LT);
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

            handler.handle(ctx(stack, jumpInsn, labelToValue, labelClassifications, initialState, false, false, false));

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

            handler.handle(ctx(stack, jumpInsn, labelToValue, labelClassifications, initialState, false, false, false));

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

            handler.handle(ctx(stack, jumpInsn, labelToValue, labelClassifications, initialState, false, false, false));

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

            handler.handle(ctx(stack, jumpInsn, labelToValue, labelClassifications, initialState, false, false, false));

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

            handler.handle(ctx(stack, jumpInsn, labelToValue, labelClassifications, initialState, false, false, false));

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

            handler.handle(ctx(stack, jumpInsn, labelToValue, labelClassifications, initialState, false, false, false));

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

            handler.handle(ctx(stack, jumpInsn, labelToValue, labelClassifications, initialState, false, false, false));

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

            handler.handle(ctx(stack, jumpInsn, labelToValue, labelClassifications, initialState, false, false, false));

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

            handler.handle(ctx(stack, jumpInsn, labelToValue, labelClassifications, initialState, false, false, false));

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

            handler.handle(ctx(stack, jumpInsn, labelToValue, labelClassifications, initialState, false, false, false));

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

            handler.handle(ctx(stack, jumpInsn, labelToValue, labelClassifications, initialState, false, false, false));

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

            handler.handle(ctx(stack, jumpInsn, labelToValue, labelClassifications, initialState, false, false, false));

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

            handler.handle(ctx(stack, jumpInsn, labelToValue, labelClassifications, initialState, false, false, false));

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

            handler.handle(ctx(stack, jumpInsn, labelToValue, labelClassifications, initialState, false, false, false));

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

            handler.handle(ctx(stack, jumpInsn, labelToValue, labelClassifications, andMode, false, false, false));

            assertThat(stack).hasSize(1);
            assertThat(stack.peek())
                    .as("Should combine into a BinaryOp")
                    .isInstanceOf(LambdaExpression.BinaryOp.class);
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

            handler.handle(ctx(stack, jumpInsn, labelToValue, labelClassifications, initialState, false, false, false));

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

            handler.handle(ctx(stack, jumpInsn, labelToValue, labelClassifications, initialState, false, false, false));

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

            handler.handle(ctx(stack, jumpInsn, labelToValue, labelClassifications, initialState, false, false, false));

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
                    handler.handle(ctx(stack, jumpInsn, labelToValue, labelClassifications, initialState, false, false, false)))
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
                    handler.handle(ctx(stack, jumpInsn, labelToValue, labelClassifications, initialState, false, false, false)))
                    .as("Should throw BytecodeAnalysisException for empty stack")
                    .isInstanceOf(io.quarkiverse.qubit.deployment.common.BytecodeAnalysisException.class);
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

            BranchState result = handler.handle(ctx(stack, jumpInsn, labelToValue, labelClassifications, initialState, false, false, false));

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

            handler.handle(ctx(stack, jumpInsn, labelToValue, labelClassifications, initialState, false, false, false));

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

            handler.handle(ctx(stack, jumpInsn, labelToValue, labelClassifications, initialState, false, false, false));

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

            handler.handle(ctx(stack, jumpInsn, labelToValue, labelClassifications, initialState, false, false, false));

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

            handler.handle(ctx(stack, jumpInsn, labelToValue, labelClassifications, andMode, false, false, false));

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

            BranchState result = handler.handle(ctx(stack, jumpInsn, labelToValue, labelClassifications, initialState, false, false, false));

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

            handler.handle(ctx(stack, jumpInsn, labelToValue, labelClassifications, initialState, false, false, false));

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

            handler.handle(ctx(stack, jumpInsn, labelToValue, labelClassifications, initialState, false, false, false));

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

            handler.handle(ctx(stack, jumpInsn, labelToValue, labelClassifications, initialState, false, false, false));

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

            handler.handle(ctx(stack, jumpInsn, labelToValue, labelClassifications, andMode, false, false, false));

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
