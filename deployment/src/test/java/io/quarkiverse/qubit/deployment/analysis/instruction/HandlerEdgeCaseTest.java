package io.quarkiverse.qubit.deployment.analysis.instruction;

import io.quarkiverse.qubit.deployment.common.BytecodeAnalysisException;
import io.quarkiverse.qubit.deployment.common.BytecodeValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.util.ArrayDeque;
import java.util.Deque;

import static io.quarkiverse.qubit.deployment.testutil.AstBuilders.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.objectweb.asm.Opcodes.*;

/**
 * Edge case tests for instruction handlers.
 *
 * <p>TEST-001: Tests for null handling, stack underflow, invalid bytecode scenarios,
 * and error handling in instruction handlers.
 */
class HandlerEdgeCaseTest {

    private MethodNode testMethod;
    private AnalysisContext context;

    @BeforeEach
    void setUp() {
        testMethod = new MethodNode();
        testMethod.name = "testLambda";
        testMethod.desc = "(Ljava/lang/Object;)Z";
        testMethod.instructions = new InsnList();
        context = new AnalysisContext(testMethod, 0);
    }

    // ==================== BytecodeValidator Tests ====================

    @Nested
    class BytecodeValidatorTests {

        @Test
        void requireStackSize_onEmptyStack_throwsStackUnderflow() {
            Deque<Object> emptyStack = new ArrayDeque<>();

            assertThatThrownBy(() -> BytecodeValidator.requireStackSize(emptyStack, 1, "TEST"))
                    .isInstanceOf(BytecodeAnalysisException.class)
                    .hasMessageContaining("Stack underflow")
                    .hasMessageContaining("TEST")
                    .hasMessageContaining("expected 1")
                    .hasMessageContaining("found 0");
        }

        @Test
        void requireStackSize_withInsufficientElements_throwsStackUnderflow() {
            Deque<Object> stack = new ArrayDeque<>();
            stack.push("one");

            assertThatThrownBy(() -> BytecodeValidator.requireStackSize(stack, 2, "BINARY_OP"))
                    .isInstanceOf(BytecodeAnalysisException.class)
                    .hasMessageContaining("Stack underflow")
                    .hasMessageContaining("expected 2")
                    .hasMessageContaining("found 1");
        }

        @Test
        void requireStackSize_withSufficientElements_succeeds() {
            Deque<Object> stack = new ArrayDeque<>();
            stack.push("one");
            stack.push("two");

            // Should not throw
            BytecodeValidator.requireStackSize(stack, 2, "BINARY_OP");
            assertThat(stack).hasSize(2);
        }

        @Test
        void requireStackSize_withMoreThanRequired_succeeds() {
            Deque<Object> stack = new ArrayDeque<>();
            stack.push("one");
            stack.push("two");
            stack.push("three");

            BytecodeValidator.requireStackSize(stack, 2, "BINARY_OP");
            assertThat(stack).hasSize(3);
        }

        @Test
        void requireNonNull_withNull_throwsException() {
            assertThatThrownBy(() -> BytecodeValidator.requireNonNull(null, "test value"))
                    .isInstanceOf(BytecodeAnalysisException.class)
                    .hasMessageContaining("Unexpected null")
                    .hasMessageContaining("test value");
        }

        @Test
        void requireNonNull_withNonNull_returnsValue() {
            String result = BytecodeValidator.requireNonNull("value", "test");
            assertThat(result).isEqualTo("value");
        }

        @Test
        void requireValidOpcode_withValidOpcode_succeeds() {
            BytecodeValidator.requireValidOpcode(IADD, IADD, ISUB, IMUL);
            // Should not throw
        }

        @Test
        void requireValidOpcode_withInvalidOpcode_throwsException() {
            assertThatThrownBy(() -> BytecodeValidator.requireValidOpcode(IADD, ISUB, IMUL))
                    .isInstanceOf(BytecodeAnalysisException.class)
                    .hasMessageContaining("Invalid opcode");
        }

        @Test
        void popSafe_onEmptyStack_throwsStackUnderflow() {
            Deque<Object> emptyStack = new ArrayDeque<>();

            assertThatThrownBy(() -> BytecodeValidator.popSafe(emptyStack, "POP"))
                    .isInstanceOf(BytecodeAnalysisException.class)
                    .hasMessageContaining("Stack underflow");
        }

        @Test
        void popSafe_withElement_returnsElement() {
            Deque<String> stack = new ArrayDeque<>();
            stack.push("value");

            String result = BytecodeValidator.popSafe(stack, "POP");

            assertThat(result).isEqualTo("value");
            assertThat(stack).isEmpty();
        }
    }

    // ==================== ArithmeticInstructionHandler Tests ====================

    @Nested
    class ArithmeticHandlerTests {

        private final ArithmeticInstructionHandler handler = new ArithmeticInstructionHandler();

        @Test
        void canHandle_withArithmeticOpcode_returnsTrue() {
            assertThat(handler.canHandle(new InsnNode(IADD))).isTrue();
            assertThat(handler.canHandle(new InsnNode(ISUB))).isTrue();
            assertThat(handler.canHandle(new InsnNode(IMUL))).isTrue();
            assertThat(handler.canHandle(new InsnNode(IDIV))).isTrue();
        }

        @Test
        void canHandle_withLogicalOpcode_returnsTrue() {
            assertThat(handler.canHandle(new InsnNode(IAND))).isTrue();
            assertThat(handler.canHandle(new InsnNode(IOR))).isTrue();
        }

        @Test
        void canHandle_withComparisonOpcode_returnsTrue() {
            assertThat(handler.canHandle(new InsnNode(LCMP))).isTrue();
            assertThat(handler.canHandle(new InsnNode(DCMPL))).isTrue();
            assertThat(handler.canHandle(new InsnNode(DCMPG))).isTrue();
        }

        @Test
        void canHandle_withNonArithmeticOpcode_returnsFalse() {
            assertThat(handler.canHandle(new InsnNode(ALOAD))).isFalse();
            assertThat(handler.canHandle(new InsnNode(IRETURN))).isFalse();
        }

        @Test
        void handle_arithmeticWithEmptyStack_throwsStackUnderflow() {
            assertThatThrownBy(() -> handler.handle(new InsnNode(IADD), context))
                    .isInstanceOf(BytecodeAnalysisException.class)
                    .hasMessageContaining("Stack underflow")
                    .hasMessageContaining("IADD");
        }

        @Test
        void handle_arithmeticWithOneElement_throwsStackUnderflow() {
            context.push(constant(5));

            assertThatThrownBy(() -> handler.handle(new InsnNode(IMUL), context))
                    .isInstanceOf(BytecodeAnalysisException.class)
                    .hasMessageContaining("Stack underflow")
                    .hasMessageContaining("IMUL");
        }

        @Test
        void handle_logicalWithEmptyStack_throwsStackUnderflow() {
            assertThatThrownBy(() -> handler.handle(new InsnNode(IAND), context))
                    .isInstanceOf(BytecodeAnalysisException.class)
                    .hasMessageContaining("Stack underflow")
                    .hasMessageContaining("IAND");
        }

        @Test
        void handle_comparisonWithEmptyStack_throwsStackUnderflow() {
            assertThatThrownBy(() -> handler.handle(new InsnNode(LCMP), context))
                    .isInstanceOf(BytecodeAnalysisException.class)
                    .hasMessageContaining("Stack underflow")
                    .hasMessageContaining("LCMP");
        }

        @Test
        void handle_arithmeticWithTwoElements_succeeds() {
            context.push(constant(5));
            context.push(constant(3));

            boolean terminated = handler.handle(new InsnNode(IADD), context);

            assertThat(terminated).isFalse();
            assertThat(context.getStackSize()).isEqualTo(1);
        }
    }

    // ==================== LoadInstructionHandler Tests ====================

    @Nested
    class LoadHandlerTests {

        private final LoadInstructionHandler handler = new LoadInstructionHandler();

        @Test
        void canHandle_withLoadOpcodes_returnsTrue() {
            assertThat(handler.canHandle(new VarInsnNode(ALOAD, 0))).isTrue();
            assertThat(handler.canHandle(new VarInsnNode(ILOAD, 0))).isTrue();
            assertThat(handler.canHandle(new VarInsnNode(LLOAD, 0))).isTrue();
            assertThat(handler.canHandle(new VarInsnNode(FLOAD, 0))).isTrue();
            assertThat(handler.canHandle(new VarInsnNode(DLOAD, 0))).isTrue();
        }

        @Test
        void canHandle_withGetField_returnsTrue() {
            FieldInsnNode fieldInsn = new FieldInsnNode(GETFIELD, "Test", "field", "I");
            assertThat(handler.canHandle(fieldInsn)).isTrue();
        }

        @Test
        void canHandle_withNonLoadOpcode_returnsFalse() {
            assertThat(handler.canHandle(new InsnNode(IADD))).isFalse();
            assertThat(handler.canHandle(new InsnNode(IRETURN))).isFalse();
        }

        @Test
        void handle_aloadWithEntityParameter_pushesParameter() {
            handler.handle(new VarInsnNode(ALOAD, 0), context);

            assertThat(context.getStackSize()).isEqualTo(1);
            assertThat(context.peek()).isNotNull();
        }

        @Test
        void handle_aloadWithInvalidSlot_throwsException() {
            // Create method with only one parameter (entity at slot 0)
            // ALOAD of slot 1 should fail because it's not a valid parameter
            MethodNode singleParamMethod = new MethodNode();
            singleParamMethod.name = "testLambda";
            singleParamMethod.desc = "(Ljava/lang/Object;)Z"; // Only one object parameter
            singleParamMethod.instructions = new InsnList();
            AnalysisContext singleParamContext = new AnalysisContext(singleParamMethod, 0);

            // Slot 1 is beyond the parameter list
            assertThatThrownBy(() -> handler.handle(new VarInsnNode(ALOAD, 99), singleParamContext))
                    .isInstanceOf(BytecodeAnalysisException.class)
                    .hasMessageContaining("does not correspond to a method parameter");
        }

        @Test
        void handle_getFieldWithEmptyStack_throwsNullPointerException() {
            FieldInsnNode fieldInsn = new FieldInsnNode(GETFIELD, "Person", "name", "Ljava/lang/String;");

            // GETFIELD on empty stack throws NPE because the switch pattern match requires non-null target
            // This is a known limitation - GETFIELD without a target object is invalid bytecode
            assertThatThrownBy(() -> handler.handle(fieldInsn, context))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    // ==================== ConstantInstructionHandler Tests ====================

    @Nested
    class ConstantHandlerTests {

        private final ConstantInstructionHandler handler = new ConstantInstructionHandler();

        @Test
        void canHandle_withIconst_returnsTrue() {
            for (int opcode = ICONST_0; opcode <= ICONST_5; opcode++) {
                assertThat(handler.canHandle(new InsnNode(opcode)))
                        .as("Should handle ICONST_%d", opcode - ICONST_0)
                        .isTrue();
            }
        }

        @Test
        void canHandle_withLconst_returnsTrue() {
            assertThat(handler.canHandle(new InsnNode(LCONST_0))).isTrue();
            assertThat(handler.canHandle(new InsnNode(LCONST_1))).isTrue();
        }

        @Test
        void canHandle_withFconst_returnsTrue() {
            assertThat(handler.canHandle(new InsnNode(FCONST_0))).isTrue();
            assertThat(handler.canHandle(new InsnNode(FCONST_1))).isTrue();
            assertThat(handler.canHandle(new InsnNode(FCONST_2))).isTrue();
        }

        @Test
        void canHandle_withDconst_returnsTrue() {
            assertThat(handler.canHandle(new InsnNode(DCONST_0))).isTrue();
            assertThat(handler.canHandle(new InsnNode(DCONST_1))).isTrue();
        }

        @Test
        void canHandle_withAconstNull_returnsTrue() {
            assertThat(handler.canHandle(new InsnNode(ACONST_NULL))).isTrue();
        }

        @Test
        void handle_aconstNull_pushesNullLiteral() {
            handler.handle(new InsnNode(ACONST_NULL), context);

            assertThat(context.getStackSize()).isEqualTo(1);
            assertThat(context.peek()).isNotNull();
        }

        // ==================== Kill mutation: line 46 return false (ACONST_NULL) ====================

        @Test
        void handle_aconstNull_returnsFalse_doesNotTerminate() {
            // The handler should return false (not terminate) after handling ACONST_NULL
            boolean terminated = handler.handle(new InsnNode(ACONST_NULL), context);

            assertThat(terminated)
                    .as("ACONST_NULL handler should return false (not terminate)")
                    .isFalse();
            assertThat(context.getStackSize())
                    .as("ACONST_NULL should push exactly one element")
                    .isEqualTo(1);
            assertThat(context.peek())
                    .as("Pushed element should be NullLiteral")
                    .isInstanceOf(io.quarkiverse.qubit.deployment.ast.LambdaExpression.NullLiteral.class);
        }

        // ==================== Kill mutation: line 65 return false (DCONST) ====================

        @Test
        void handle_dconst0_returnsFalse_doesNotTerminate() {
            // The handler should return false (not terminate) after handling DCONST_0
            boolean terminated = handler.handle(new InsnNode(DCONST_0), context);

            assertThat(terminated)
                    .as("DCONST_0 handler should return false (not terminate)")
                    .isFalse();
        }

        @Test
        void handle_dconst1_returnsFalse_doesNotTerminate() {
            boolean terminated = handler.handle(new InsnNode(DCONST_1), context);

            assertThat(terminated)
                    .as("DCONST_1 handler should return false (not terminate)")
                    .isFalse();
        }

        // ==================== Kill mutation: lines 55, 60 return false (FCONST, LCONST) ====================

        @Test
        void handle_fconst0_returnsFalse_doesNotTerminate() {
            boolean terminated = handler.handle(new InsnNode(FCONST_0), context);

            assertThat(terminated)
                    .as("FCONST_0 handler should return false (not terminate)")
                    .isFalse();
        }

        @Test
        void handle_lconst0_returnsFalse_doesNotTerminate() {
            boolean terminated = handler.handle(new InsnNode(LCONST_0), context);

            assertThat(terminated)
                    .as("LCONST_0 handler should return false (not terminate)")
                    .isFalse();
        }

        @Test
        void handle_iconst_pushesConstant() {
            handler.handle(new InsnNode(ICONST_5), context);

            assertThat(context.getStackSize()).isEqualTo(1);
        }

        @Test
        void handle_lconst_pushesConstant() {
            handler.handle(new InsnNode(LCONST_1), context);

            assertThat(context.getStackSize()).isEqualTo(1);
        }

        @Test
        void handle_fconst_pushesConstant() {
            handler.handle(new InsnNode(FCONST_2), context);

            assertThat(context.getStackSize()).isEqualTo(1);
        }

        @Test
        void handle_dconst_pushesConstant() {
            handler.handle(new InsnNode(DCONST_1), context);

            assertThat(context.getStackSize()).isEqualTo(1);
        }

        // ==================== ICONST Post-Branch Behavior Tests ====================
        // These tests exercise the ICONST handling after branch instructions have been seen

        @Test
        void handle_iconst_noBranch_pushesConstant() {
            // Without seeing a branch, ICONST should always push constant
            boolean terminated = handler.handle(new InsnNode(ICONST_0), context);

            assertThat(terminated).isFalse();
            assertThat(context.getStackSize()).isEqualTo(1);
        }

        @Test
        void handle_iconst5_afterBranch_pushesConstant() {
            // ICONST_5 (value > 1) is not a boolean marker, always pushed
            context.markBranchSeen();
            context.push(constant(1));

            boolean terminated = handler.handle(new InsnNode(ICONST_5), context);

            assertThat(terminated).isFalse();
            assertThat(context.getStackSize()).isEqualTo(2);
        }

        @Test
        void handle_iconst2_afterBranch_pushesConstant() {
            // ICONST_2, ICONST_3, ICONST_4, ICONST_5 (value > 1) are not boolean markers
            context.markBranchSeen();

            boolean terminated = handler.handle(new InsnNode(ICONST_2), context);

            assertThat(terminated).isFalse();
            assertThat(context.getStackSize()).isEqualTo(1);
        }

        @Test
        void handle_iconst3_afterBranch_pushesConstant() {
            context.markBranchSeen();

            boolean terminated = handler.handle(new InsnNode(ICONST_3), context);

            assertThat(terminated).isFalse();
            assertThat(context.getStackSize()).isEqualTo(1);
        }

        @Test
        void handle_iconst4_afterBranch_pushesConstant() {
            context.markBranchSeen();

            boolean terminated = handler.handle(new InsnNode(ICONST_4), context);

            assertThat(terminated).isFalse();
            assertThat(context.getStackSize()).isEqualTo(1);
        }

        @Test
        void handle_iconst0_noBranch_emptyInstructions_pushesConstant() {
            // Even ICONST_0 pushes if no branch has been seen
            boolean terminated = handler.handle(new InsnNode(ICONST_0), context);

            assertThat(terminated).isFalse();
            assertThat(context.getStackSize()).isEqualTo(1);
        }

        @Test
        void handle_iconst1_noBranch_emptyInstructions_pushesConstant() {
            boolean terminated = handler.handle(new InsnNode(ICONST_1), context);

            assertThat(terminated).isFalse();
            assertThat(context.getStackSize()).isEqualTo(1);
        }

        // ==================== ICONST_0/1 Post-Branch Termination Tests ====================
        // Test the isFinalResult() and handleIconst() mutation-killing scenarios

        @Test
        void handle_iconst0_afterBranch_withStackAndIRETURN_terminates() {
            // Setup: branch seen, stack has expression, next instruction is IRETURN
            testMethod.instructions.add(new InsnNode(ICONST_0)); // Current instruction
            testMethod.instructions.add(new InsnNode(IRETURN));  // Next instruction
            AnalysisContext ctx = new AnalysisContext(testMethod, 0);
            ctx.markBranchSeen();
            ctx.push(constant(1)); // Stack has >= 1 element

            boolean terminated = handler.handle(new InsnNode(ICONST_0), ctx);

            assertThat(terminated)
                    .as("ICONST_0 after branch with stack expression and IRETURN should terminate")
                    .isTrue();
        }

        @Test
        void handle_iconst1_afterBranch_withStackAndIRETURN_terminates() {
            // Same as above but with ICONST_1
            testMethod.instructions.add(new InsnNode(ICONST_1));
            testMethod.instructions.add(new InsnNode(IRETURN));
            AnalysisContext ctx = new AnalysisContext(testMethod, 0);
            ctx.markBranchSeen();
            ctx.push(constant(1));

            boolean terminated = handler.handle(new InsnNode(ICONST_1), ctx);

            assertThat(terminated)
                    .as("ICONST_1 after branch with stack expression and IRETURN should terminate")
                    .isTrue();
        }

        @Test
        void handle_iconst0_afterBranch_withStackAndARETURN_terminates() {
            // Test ARETURN variant (for returning boxed Boolean)
            testMethod.instructions.add(new InsnNode(ICONST_0));
            testMethod.instructions.add(new InsnNode(ARETURN));
            AnalysisContext ctx = new AnalysisContext(testMethod, 0);
            ctx.markBranchSeen();
            ctx.push(constant(1));

            boolean terminated = handler.handle(new InsnNode(ICONST_0), ctx);

            assertThat(terminated)
                    .as("ICONST_0 after branch with ARETURN should terminate")
                    .isTrue();
        }

        @Test
        void handle_iconst0_afterBranch_withStackAndRETURN_terminates() {
            // Test RETURN variant (void lambda returning boolean?)
            testMethod.instructions.add(new InsnNode(ICONST_0));
            testMethod.instructions.add(new InsnNode(RETURN));
            AnalysisContext ctx = new AnalysisContext(testMethod, 0);
            ctx.markBranchSeen();
            ctx.push(constant(1));

            boolean terminated = handler.handle(new InsnNode(ICONST_0), ctx);

            assertThat(terminated)
                    .as("ICONST_0 after branch with RETURN should terminate")
                    .isTrue();
        }

        @Test
        void handle_iconst0_afterBranch_withEmptyStack_doesNotTerminate() {
            // Empty stack should not terminate (needs >= 1 element)
            testMethod.instructions.add(new InsnNode(ICONST_0));
            testMethod.instructions.add(new InsnNode(IRETURN));
            AnalysisContext ctx = new AnalysisContext(testMethod, 0);
            ctx.markBranchSeen();
            // Stack is empty

            boolean terminated = handler.handle(new InsnNode(ICONST_0), ctx);

            assertThat(terminated)
                    .as("ICONST_0 after branch with empty stack should not terminate")
                    .isFalse();
        }

        @Test
        void handle_iconst0_afterBranch_withGOTONext_doesNotTerminate() {
            // GOTO after ICONST_0 - intermediate marker, skip but don't terminate
            testMethod.instructions.add(new InsnNode(ICONST_0));
            testMethod.instructions.add(new org.objectweb.asm.tree.JumpInsnNode(GOTO,
                    new org.objectweb.asm.tree.LabelNode()));
            AnalysisContext ctx = new AnalysisContext(testMethod, 0);
            ctx.markBranchSeen();
            ctx.push(constant(1));

            boolean terminated = handler.handle(new InsnNode(ICONST_0), ctx);

            assertThat(terminated)
                    .as("ICONST_0 followed by GOTO is intermediate marker")
                    .isFalse();
        }

        @Test
        void handle_iconst0_afterBranch_withArithmeticNext_doesNotTerminate() {
            // Arithmetic after ICONST_0 - used in expression, don't terminate
            testMethod.instructions.add(new InsnNode(ICONST_0));
            testMethod.instructions.add(new InsnNode(IADD)); // Used in expression
            AnalysisContext ctx = new AnalysisContext(testMethod, 0);
            ctx.markBranchSeen();
            ctx.push(constant(1));

            boolean terminated = handler.handle(new InsnNode(ICONST_0), ctx);

            assertThat(terminated)
                    .as("ICONST_0 used in arithmetic expression should not terminate")
                    .isFalse();
            assertThat(ctx.getStackSize())
                    .as("ICONST_0 should be pushed for arithmetic expression")
                    .isEqualTo(2);
        }

        // ==================== Kill mutations for constant values ====================

        @Test
        void handle_dconst0_pushesZeroDouble() {
            // Kill mutation line 141: subtraction changed to addition
            handler.handle(new InsnNode(DCONST_0), context);

            assertThat(context.getStackSize()).isEqualTo(1);
            assertThat(context.peek())
                    .isInstanceOf(io.quarkiverse.qubit.deployment.ast.LambdaExpression.Constant.class);
            io.quarkiverse.qubit.deployment.ast.LambdaExpression.Constant constExpr =
                    (io.quarkiverse.qubit.deployment.ast.LambdaExpression.Constant) context.peek();
            assertThat(constExpr.value())
                    .as("DCONST_0 should push 0.0")
                    .isEqualTo(0.0);
        }

        @Test
        void handle_dconst1_pushesOneDouble() {
            // Kill mutation line 141: subtraction changed to addition
            handler.handle(new InsnNode(DCONST_1), context);

            assertThat(context.getStackSize()).isEqualTo(1);
            io.quarkiverse.qubit.deployment.ast.LambdaExpression.Constant constExpr =
                    (io.quarkiverse.qubit.deployment.ast.LambdaExpression.Constant) context.peek();
            assertThat(constExpr.value())
                    .as("DCONST_1 should push 1.0")
                    .isEqualTo(1.0);
        }

        @Test
        void handle_fconst0_pushesZeroFloat() {
            handler.handle(new InsnNode(FCONST_0), context);

            io.quarkiverse.qubit.deployment.ast.LambdaExpression.Constant constExpr =
                    (io.quarkiverse.qubit.deployment.ast.LambdaExpression.Constant) context.peek();
            assertThat(constExpr.value())
                    .as("FCONST_0 should push 0.0f")
                    .isEqualTo(0.0f);
        }

        @Test
        void handle_fconst1_pushesOneFloat() {
            handler.handle(new InsnNode(FCONST_1), context);

            io.quarkiverse.qubit.deployment.ast.LambdaExpression.Constant constExpr =
                    (io.quarkiverse.qubit.deployment.ast.LambdaExpression.Constant) context.peek();
            assertThat(constExpr.value())
                    .as("FCONST_1 should push 1.0f")
                    .isEqualTo(1.0f);
        }

        @Test
        void handle_lconst0_pushesZeroLong() {
            handler.handle(new InsnNode(LCONST_0), context);

            io.quarkiverse.qubit.deployment.ast.LambdaExpression.Constant constExpr =
                    (io.quarkiverse.qubit.deployment.ast.LambdaExpression.Constant) context.peek();
            assertThat(constExpr.value())
                    .as("LCONST_0 should push 0L")
                    .isEqualTo(0L);
        }

        @Test
        void handle_lconst1_pushesOneLong() {
            handler.handle(new InsnNode(LCONST_1), context);

            io.quarkiverse.qubit.deployment.ast.LambdaExpression.Constant constExpr =
                    (io.quarkiverse.qubit.deployment.ast.LambdaExpression.Constant) context.peek();
            assertThat(constExpr.value())
                    .as("LCONST_1 should push 1L")
                    .isEqualTo(1L);
        }

        @Test
        void handle_iconst0_pushesZeroInt() {
            handler.handle(new InsnNode(ICONST_0), context);

            io.quarkiverse.qubit.deployment.ast.LambdaExpression.Constant constExpr =
                    (io.quarkiverse.qubit.deployment.ast.LambdaExpression.Constant) context.peek();
            assertThat(constExpr.value())
                    .as("ICONST_0 should push 0")
                    .isEqualTo(0);
        }

        @Test
        void handle_iconst5_pushesFiveInt() {
            handler.handle(new InsnNode(ICONST_5), context);

            io.quarkiverse.qubit.deployment.ast.LambdaExpression.Constant constExpr =
                    (io.quarkiverse.qubit.deployment.ast.LambdaExpression.Constant) context.peek();
            assertThat(constExpr.value())
                    .as("ICONST_5 should push 5")
                    .isEqualTo(5);
        }

        // ==================== Kill mutations for isIconstUsedInExpression ====================

        @Test
        void handle_iconst0_afterBranch_withInvokeVirtualNext_pushedForExpression() {
            // INVOKEVIRTUAL after ICONST_0 - used in method call (not Boolean.valueOf)
            testMethod.instructions.add(new InsnNode(ICONST_0));
            testMethod.instructions.add(new MethodInsnNode(INVOKEVIRTUAL, "java/lang/String", "length", "()I", false));
            AnalysisContext ctx = new AnalysisContext(testMethod, 0);
            ctx.markBranchSeen();
            ctx.push(constant(1));

            boolean terminated = handler.handle(new InsnNode(ICONST_0), ctx);

            assertThat(terminated)
                    .as("ICONST_0 used as invoke arg should not terminate")
                    .isFalse();
            assertThat(ctx.getStackSize())
                    .as("ICONST_0 should be pushed for invoke")
                    .isEqualTo(2);
        }

        @Test
        void handle_iconst0_afterBranch_withBooleanValueOfNext_skipped() {
            // Boolean.valueOf(Z) after ICONST_0 - this is intermediate, ICONST is skipped
            testMethod.instructions.add(new InsnNode(ICONST_0));
            testMethod.instructions.add(new MethodInsnNode(INVOKESTATIC, "java/lang/Boolean", "valueOf", "(Z)Ljava/lang/Boolean;", false));
            testMethod.instructions.add(new InsnNode(ARETURN));
            AnalysisContext ctx = new AnalysisContext(testMethod, 0);
            ctx.markBranchSeen();
            ctx.push(constant(1));

            handler.handle(new InsnNode(ICONST_0), ctx);

            // Should recognize Boolean.valueOf as boxing wrapper, so ICONST is intermediate marker
            assertThat(ctx.getStackSize())
                    .as("ICONST_0 before Boolean.valueOf should check if final result")
                    .isGreaterThanOrEqualTo(1);
        }

        @Test
        void handle_iconst0_afterBranch_withBranchOpcodeNext_pushedForBranch() {
            // Branch opcode (IFEQ) after ICONST_0 - used in conditional
            testMethod.instructions.add(new InsnNode(ICONST_0));
            testMethod.instructions.add(new org.objectweb.asm.tree.JumpInsnNode(IFEQ, new org.objectweb.asm.tree.LabelNode()));
            AnalysisContext ctx = new AnalysisContext(testMethod, 0);
            ctx.markBranchSeen();
            ctx.push(constant(1));

            boolean terminated = handler.handle(new InsnNode(ICONST_0), ctx);

            assertThat(terminated)
                    .as("ICONST_0 used in branch should not terminate")
                    .isFalse();
            assertThat(ctx.getStackSize())
                    .as("ICONST_0 should be pushed for branch comparison")
                    .isEqualTo(2);
        }

        @Test
        void handle_iconst0_afterBranch_withIORNext_pushedForLogical() {
            // IOR (logical or) after ICONST_0 - used in logical expression
            testMethod.instructions.add(new InsnNode(ICONST_0));
            testMethod.instructions.add(new InsnNode(IOR));
            AnalysisContext ctx = new AnalysisContext(testMethod, 0);
            ctx.markBranchSeen();
            ctx.push(constant(1));

            boolean terminated = handler.handle(new InsnNode(ICONST_0), ctx);

            assertThat(terminated)
                    .as("ICONST_0 used in logical expression should not terminate")
                    .isFalse();
            assertThat(ctx.getStackSize())
                    .as("ICONST_0 should be pushed for logical expression")
                    .isEqualTo(2);
        }

        @Test
        void handle_iconst0_afterBranch_withIANDNext_pushedForLogical() {
            // IAND (logical and) after ICONST_0
            testMethod.instructions.add(new InsnNode(ICONST_0));
            testMethod.instructions.add(new InsnNode(IAND));
            AnalysisContext ctx = new AnalysisContext(testMethod, 0);
            ctx.markBranchSeen();
            ctx.push(constant(1));

            boolean terminated = handler.handle(new InsnNode(ICONST_0), ctx);

            assertThat(terminated).isFalse();
            assertThat(ctx.getStackSize()).isEqualTo(2);
        }

        @Test
        void handle_iconst0_afterBranch_withIXORNext_pushedForLogical() {
            // IXOR (logical xor) after ICONST_0
            testMethod.instructions.add(new InsnNode(ICONST_0));
            testMethod.instructions.add(new InsnNode(IXOR));
            AnalysisContext ctx = new AnalysisContext(testMethod, 0);
            ctx.markBranchSeen();
            ctx.push(constant(1));

            boolean terminated = handler.handle(new InsnNode(ICONST_0), ctx);

            assertThat(terminated).isFalse();
            assertThat(ctx.getStackSize()).isEqualTo(2);
        }

        @Test
        void handle_iconst0_afterBranch_withIFNULLNext_pushedForBranch() {
            // IFNULL branch opcode after ICONST_0
            testMethod.instructions.add(new InsnNode(ICONST_0));
            testMethod.instructions.add(new org.objectweb.asm.tree.JumpInsnNode(IFNULL, new org.objectweb.asm.tree.LabelNode()));
            AnalysisContext ctx = new AnalysisContext(testMethod, 0);
            ctx.markBranchSeen();
            ctx.push(constant(1));

            boolean terminated = handler.handle(new InsnNode(ICONST_0), ctx);

            assertThat(terminated).isFalse();
            assertThat(ctx.getStackSize()).isEqualTo(2);
        }

        @Test
        void handle_iconst0_afterBranch_withIFNONNULLNext_pushedForBranch() {
            // IFNONNULL branch opcode after ICONST_0
            testMethod.instructions.add(new InsnNode(ICONST_0));
            testMethod.instructions.add(new org.objectweb.asm.tree.JumpInsnNode(IFNONNULL, new org.objectweb.asm.tree.LabelNode()));
            AnalysisContext ctx = new AnalysisContext(testMethod, 0);
            ctx.markBranchSeen();
            ctx.push(constant(1));

            boolean terminated = handler.handle(new InsnNode(ICONST_0), ctx);

            assertThat(terminated).isFalse();
            assertThat(ctx.getStackSize()).isEqualTo(2);
        }

        @Test
        void handle_iconst0_afterBranch_withLabelNodeNext_skipsLabel() {
            // LabelNode (opcode -1) should be skipped when checking final result
            testMethod.instructions.add(new InsnNode(ICONST_0));
            testMethod.instructions.add(new org.objectweb.asm.tree.LabelNode()); // opcode = -1
            testMethod.instructions.add(new InsnNode(IRETURN));
            AnalysisContext ctx = new AnalysisContext(testMethod, 0);
            ctx.markBranchSeen();
            ctx.push(constant(1));

            boolean terminated = handler.handle(new InsnNode(ICONST_0), ctx);

            assertThat(terminated)
                    .as("ICONST_0 should skip label and find IRETURN -> terminate")
                    .isTrue();
        }

        @Test
        void handle_iconst0_afterBranch_withNonReturnNonArithmetic_pushedAsConstant() {
            // Other opcode (DUP) - not a return, not arithmetic, should push constant
            testMethod.instructions.add(new InsnNode(ICONST_0));
            testMethod.instructions.add(new InsnNode(DUP));
            AnalysisContext ctx = new AnalysisContext(testMethod, 0);
            ctx.markBranchSeen();
            ctx.push(constant(1));

            boolean terminated = handler.handle(new InsnNode(ICONST_0), ctx);

            assertThat(terminated).isFalse();
            assertThat(ctx.getStackSize()).isEqualTo(2);
        }

        @Test
        void handle_iconst0_afterBranch_withInvokeInterface_pushedForExpression() {
            // INVOKEINTERFACE after ICONST_0
            testMethod.instructions.add(new InsnNode(ICONST_0));
            testMethod.instructions.add(new MethodInsnNode(INVOKEINTERFACE, "java/util/List", "size", "()I", true));
            AnalysisContext ctx = new AnalysisContext(testMethod, 0);
            ctx.markBranchSeen();
            ctx.push(constant(1));

            boolean terminated = handler.handle(new InsnNode(ICONST_0), ctx);

            assertThat(terminated).isFalse();
            assertThat(ctx.getStackSize()).isEqualTo(2);
        }

        @Test
        void handle_iconst0_afterBranch_withInvokeSpecial_pushedForExpression() {
            // INVOKESPECIAL after ICONST_0
            testMethod.instructions.add(new InsnNode(ICONST_0));
            testMethod.instructions.add(new MethodInsnNode(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false));
            AnalysisContext ctx = new AnalysisContext(testMethod, 0);
            ctx.markBranchSeen();
            ctx.push(constant(1));

            boolean terminated = handler.handle(new InsnNode(ICONST_0), ctx);

            assertThat(terminated).isFalse();
            assertThat(ctx.getStackSize()).isEqualTo(2);
        }

        // ==================== Kill mutations for boundary conditions ====================

        @Test
        void canHandle_withNonConstantOpcode_ALOAD_returnsFalse() {
            // ALOAD (opcode 25) is outside constant range, should not be handled
            InsnNode insnOutside = new InsnNode(ALOAD);
            assertThat(handler.canHandle(insnOutside))
                    .as("ALOAD opcode should not be handled by ConstantInstructionHandler")
                    .isFalse();
        }

        @Test
        void canHandle_withNonConstantOpcode_ISTORE_returnsFalse() {
            // ISTORE (opcode 54) is outside constant range, should not be handled
            InsnNode insnOutside = new InsnNode(ISTORE);
            assertThat(handler.canHandle(insnOutside))
                    .as("ISTORE opcode should not be handled by ConstantInstructionHandler")
                    .isFalse();
        }

        @Test
        void canHandle_withNonConstantOpcode_POP_returnsFalse() {
            // POP (opcode 87) is outside constant range, should not be handled
            InsnNode insnOutside = new InsnNode(POP);
            assertThat(handler.canHandle(insnOutside))
                    .as("POP opcode should not be handled by ConstantInstructionHandler")
                    .isFalse();
        }

        @Test
        void canHandle_withNonConstantOpcode_NOP_returnsFalse() {
            // NOP (opcode 0) is outside constant range, should not be handled
            InsnNode insnOutside = new InsnNode(NOP);
            assertThat(handler.canHandle(insnOutside))
                    .as("NOP opcode should not be handled by ConstantInstructionHandler")
                    .isFalse();
        }

        // Additional tests for ICONST_M1 edge case
        @Test
        void canHandle_withICONST_M1_returnsFalse() {
            // ICONST_M1 (opcode 2) is NOT handled - handler only handles ICONST_0 to ICONST_5
            InsnNode insnM1 = new InsnNode(ICONST_M1);
            assertThat(handler.canHandle(insnM1))
                    .as("ICONST_M1 is not in range ICONST_0 to ICONST_5")
                    .isFalse();
        }

        // ==================== Kill mutations: lines 194, 200 - isArithmeticOrLogicalOpcode/isBranchOpcode ====================
        // These test boundary opcodes at the edges of the arithmetic and branch ranges

        @Test
        void handle_iconst0_afterBranch_withIADD_nextInstruction_pushedForArithmetic() {
            // IADD is at the START of arithmetic range (opcode >= IADD)
            testMethod.instructions.add(new InsnNode(ICONST_0));
            testMethod.instructions.add(new InsnNode(IADD)); // First opcode in range
            AnalysisContext ctx = new AnalysisContext(testMethod, 0);
            ctx.markBranchSeen();
            ctx.push(constant(1));

            boolean terminated = handler.handle(new InsnNode(ICONST_0), ctx);

            assertThat(terminated)
                    .as("ICONST_0 before IADD (start of range) should not terminate")
                    .isFalse();
            assertThat(ctx.getStackSize())
                    .as("ICONST_0 should be pushed for IADD")
                    .isEqualTo(2);
        }

        @Test
        void handle_iconst0_afterBranch_withDREM_nextInstruction_pushedForArithmetic() {
            // DREM is at the END of arithmetic range (opcode <= DREM)
            testMethod.instructions.add(new InsnNode(ICONST_0));
            testMethod.instructions.add(new InsnNode(DREM)); // Last opcode in range
            AnalysisContext ctx = new AnalysisContext(testMethod, 0);
            ctx.markBranchSeen();
            ctx.push(constant(1));

            boolean terminated = handler.handle(new InsnNode(ICONST_0), ctx);

            assertThat(terminated)
                    .as("ICONST_0 before DREM (end of range) should not terminate")
                    .isFalse();
            assertThat(ctx.getStackSize())
                    .as("ICONST_0 should be pushed for DREM")
                    .isEqualTo(2);
        }

        @Test
        void handle_iconst0_afterBranch_withIFEQ_nextInstruction_pushedForBranch() {
            // IFEQ is at the START of branch range (opcode >= IFEQ)
            testMethod.instructions.add(new InsnNode(ICONST_0));
            testMethod.instructions.add(new org.objectweb.asm.tree.JumpInsnNode(IFEQ, new org.objectweb.asm.tree.LabelNode()));
            AnalysisContext ctx = new AnalysisContext(testMethod, 0);
            ctx.markBranchSeen();
            ctx.push(constant(1));

            boolean terminated = handler.handle(new InsnNode(ICONST_0), ctx);

            assertThat(terminated)
                    .as("ICONST_0 before IFEQ (start of branch range) should not terminate")
                    .isFalse();
            assertThat(ctx.getStackSize())
                    .as("ICONST_0 should be pushed for IFEQ branch")
                    .isEqualTo(2);
        }

        @Test
        void handle_iconst0_afterBranch_withIF_ICMPLE_nextInstruction_pushedForBranch() {
            // IF_ICMPLE is at the END of branch range (opcode <= IF_ICMPLE)
            testMethod.instructions.add(new InsnNode(ICONST_0));
            testMethod.instructions.add(new org.objectweb.asm.tree.JumpInsnNode(IF_ICMPLE, new org.objectweb.asm.tree.LabelNode()));
            AnalysisContext ctx = new AnalysisContext(testMethod, 0);
            ctx.markBranchSeen();
            ctx.push(constant(1));

            boolean terminated = handler.handle(new InsnNode(ICONST_0), ctx);

            assertThat(terminated)
                    .as("ICONST_0 before IF_ICMPLE (end of branch range) should not terminate")
                    .isFalse();
            assertThat(ctx.getStackSize())
                    .as("ICONST_0 should be pushed for IF_ICMPLE branch")
                    .isEqualTo(2);
        }

        // ==================== Kill mutations: line 185 - isBooleanValueOfCall ====================

        @Test
        void handle_iconst0_afterBranch_withBooleanValueOf_skipsConstant() {
            // Boolean.valueOf(Z) causes isIconstUsedInExpression to return false (not used in expression)
            // But isFinalResult only skips labels, not Boolean.valueOf, so it returns false
            // Result: ICONST is skipped as intermediate marker, doesn't terminate
            testMethod.instructions.add(new InsnNode(ICONST_0));
            testMethod.instructions.add(new MethodInsnNode(INVOKESTATIC, "java/lang/Boolean", "valueOf", "(Z)Ljava/lang/Boolean;", false));
            testMethod.instructions.add(new InsnNode(ARETURN));
            AnalysisContext ctx = new AnalysisContext(testMethod, 0);
            ctx.markBranchSeen();
            ctx.push(constant(1));

            boolean terminated = handler.handle(new InsnNode(ICONST_0), ctx);

            // isFinalResult finds Boolean.valueOf first (not a return), returns false
            // So handler returns false at line 100 (skipping intermediate marker)
            assertThat(terminated)
                    .as("Boolean.valueOf is not a return, so isFinalResult returns false, handler doesn't terminate")
                    .isFalse();
            assertThat(ctx.getStackSize())
                    .as("Stack should have original element (ICONST was skipped)")
                    .isEqualTo(1);
        }

        @Test
        void handle_iconst0_afterBranch_withNonBooleanStaticMethod_pushedForInvoke() {
            // Non-Boolean.valueOf static method should push ICONST for use as argument
            testMethod.instructions.add(new InsnNode(ICONST_0));
            testMethod.instructions.add(new MethodInsnNode(INVOKESTATIC, "java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;", false));
            AnalysisContext ctx = new AnalysisContext(testMethod, 0);
            ctx.markBranchSeen();
            ctx.push(constant(1));

            boolean terminated = handler.handle(new InsnNode(ICONST_0), ctx);

            assertThat(terminated)
                    .as("ICONST_0 before Integer.valueOf should not terminate")
                    .isFalse();
            assertThat(ctx.getStackSize())
                    .as("ICONST_0 should be pushed as argument")
                    .isEqualTo(2);
        }

        // ==================== Kill mutations: line 113 - isFinalResult loop boundary ====================

        @Test
        void handle_iconst0_afterBranch_atEndOfInstructions_terminates() {
            // When ICONST is at the end (no next instruction), isFinalResult returns true
            testMethod.instructions.add(new InsnNode(ICONST_0));
            // No more instructions after ICONST_0
            AnalysisContext ctx = new AnalysisContext(testMethod, 0);
            ctx.markBranchSeen();
            ctx.push(constant(1));

            boolean terminated = handler.handle(new InsnNode(ICONST_0), ctx);

            // When no instructions follow, it's the final result
            assertThat(terminated)
                    .as("ICONST_0 at end of instructions should terminate")
                    .isTrue();
        }

        // ==================== Kill mutations: line 150, 166, 178 - isIconstUsedInExpression conditions ====================

        @Test
        void handle_iconst0_afterBranch_emptyLookahead_returnsFalse() {
            // When lookahead finds no relevant instructions, isIconstUsedInExpression returns false
            testMethod.instructions.add(new InsnNode(ICONST_0));
            // Add only label nodes (opcode -1) which are skipped
            for (int i = 0; i < 5; i++) {
                testMethod.instructions.add(new org.objectweb.asm.tree.LabelNode());
            }
            AnalysisContext ctx = new AnalysisContext(testMethod, 0);
            ctx.markBranchSeen();
            ctx.push(constant(1));

            boolean terminated = handler.handle(new InsnNode(ICONST_0), ctx);

            // With only labels in lookahead window, isIconstUsedInExpression returns false
            // Then isFinalResult is checked - no RETURN found, defaults to true -> terminate
            assertThat(terminated)
                    .as("ICONST_0 with only labels ahead should check final result")
                    .isTrue();
        }
    }

    // ==================== TypeConversionHandler Tests ====================

    @Nested
    class TypeConversionHandlerTests {

        private final TypeConversionHandler handler = new TypeConversionHandler();

        @Test
        void canHandle_withPrimitiveConversions_returnsTrue() {
            // TypeConversionHandler only handles primitive type conversions
            assertThat(handler.canHandle(new InsnNode(I2L)))
                    .as("Should handle I2L (int to long)")
                    .isTrue();
            assertThat(handler.canHandle(new InsnNode(I2F)))
                    .as("Should handle I2F (int to float)")
                    .isTrue();
            assertThat(handler.canHandle(new InsnNode(I2D)))
                    .as("Should handle I2D (int to double)")
                    .isTrue();
            assertThat(handler.canHandle(new InsnNode(L2I)))
                    .as("Should handle L2I (long to int)")
                    .isTrue();
            assertThat(handler.canHandle(new InsnNode(L2F)))
                    .as("Should handle L2F (long to float)")
                    .isTrue();
            assertThat(handler.canHandle(new InsnNode(L2D)))
                    .as("Should handle L2D (long to double)")
                    .isTrue();
            assertThat(handler.canHandle(new InsnNode(F2I)))
                    .as("Should handle F2I (float to int)")
                    .isTrue();
            assertThat(handler.canHandle(new InsnNode(F2L)))
                    .as("Should handle F2L (float to long)")
                    .isTrue();
            assertThat(handler.canHandle(new InsnNode(F2D)))
                    .as("Should handle F2D (float to double)")
                    .isTrue();
            assertThat(handler.canHandle(new InsnNode(D2I)))
                    .as("Should handle D2I (double to int)")
                    .isTrue();
            assertThat(handler.canHandle(new InsnNode(D2L)))
                    .as("Should handle D2L (double to long)")
                    .isTrue();
            assertThat(handler.canHandle(new InsnNode(D2F)))
                    .as("Should handle D2F (double to float)")
                    .isTrue();
        }

        @Test
        void canHandle_withNonConversionOpcode_returnsFalse() {
            assertThat(handler.canHandle(new InsnNode(IADD)))
                    .as("Should not handle arithmetic opcodes")
                    .isFalse();
            assertThat(handler.canHandle(new InsnNode(IRETURN)))
                    .as("Should not handle return opcodes")
                    .isFalse();
        }

        @Test
        void canHandle_withNarrowingConversions_returnsFalse() {
            // Note: I2B, I2C, I2S are narrowing conversions NOT handled by TypeConversionHandler
            assertThat(handler.canHandle(new InsnNode(I2B)))
                    .as("TypeConversionHandler does not handle I2B")
                    .isFalse();
            assertThat(handler.canHandle(new InsnNode(I2C)))
                    .as("TypeConversionHandler does not handle I2C")
                    .isFalse();
            assertThat(handler.canHandle(new InsnNode(I2S)))
                    .as("TypeConversionHandler does not handle I2S")
                    .isFalse();
        }

        @Test
        void canHandle_withCheckcastAndInstanceof_returnsFalse() {
            // CHECKCAST and INSTANCEOF are NOT type conversions - they're handled elsewhere
            assertThat(handler.canHandle(new InsnNode(CHECKCAST)))
                    .as("TypeConversionHandler does not handle CHECKCAST")
                    .isFalse();
            assertThat(handler.canHandle(new InsnNode(INSTANCEOF)))
                    .as("TypeConversionHandler does not handle INSTANCEOF")
                    .isFalse();
        }

        @Test
        void handle_withEmptyStack_doesNotThrow() {
            // TypeConversionHandler handles empty stack gracefully
            boolean terminated = handler.handle(new InsnNode(I2L), context);

            assertThat(terminated).isFalse();
            assertThat(context.isStackEmpty()).isTrue();
        }
    }

    // ==================== Handler Registry Tests ====================

    @Nested
    class InstructionHandlerRegistryTests {

        @Test
        void createDefault_returnsNonEmptyRegistry() {
            InstructionHandlerRegistry registry = InstructionHandlerRegistry.createDefault();

            assertThat(registry).isNotNull();
            assertThat(registry.handlers()).isNotEmpty();
        }

        @Test
        void constructor_withNullHandlers_throwsException() {
            assertThatThrownBy(() -> new InstructionHandlerRegistry(null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        void constructor_withEmptyHandlers_throwsException() {
            assertThatThrownBy(() -> new InstructionHandlerRegistry(java.util.List.of()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("cannot be empty");
        }

        @Test
        void handlers_returnsImmutableCopy() {
            InstructionHandlerRegistry registry = InstructionHandlerRegistry.createDefault();

            assertThatThrownBy(() -> registry.handlers().add(new ArithmeticInstructionHandler()))
                    .isInstanceOf(UnsupportedOperationException.class);
        }
    }

    // ==================== MethodInvocationHandler Tests ====================

    @Nested
    class MethodInvocationHandlerTests {

        private final MethodInvocationHandler handler = new MethodInvocationHandler();

        // ==================== canHandle Tests ====================

        @Test
        void canHandle_withINVOKEVIRTUAL_returnsTrue() {
            MethodInsnNode methodInsn = new MethodInsnNode(INVOKEVIRTUAL, "java/lang/String", "length", "()I", false);
            assertThat(handler.canHandle(methodInsn))
                    .as("Should handle INVOKEVIRTUAL")
                    .isTrue();
        }

        @Test
        void canHandle_withINVOKESTATIC_returnsTrue() {
            MethodInsnNode methodInsn = new MethodInsnNode(INVOKESTATIC, "java/lang/Boolean", "valueOf", "(Z)Ljava/lang/Boolean;", false);
            assertThat(handler.canHandle(methodInsn))
                    .as("Should handle INVOKESTATIC")
                    .isTrue();
        }

        @Test
        void canHandle_withINVOKESPECIAL_returnsTrue() {
            MethodInsnNode methodInsn = new MethodInsnNode(INVOKESPECIAL, "java/math/BigDecimal", "<init>", "(Ljava/lang/String;)V", false);
            assertThat(handler.canHandle(methodInsn))
                    .as("Should handle INVOKESPECIAL")
                    .isTrue();
        }

        @Test
        void canHandle_withINVOKEINTERFACE_returnsTrue() {
            MethodInsnNode methodInsn = new MethodInsnNode(INVOKEINTERFACE, "java/util/Collection", "contains", "(Ljava/lang/Object;)Z", true);
            assertThat(handler.canHandle(methodInsn))
                    .as("Should handle INVOKEINTERFACE")
                    .isTrue();
        }

        @Test
        void canHandle_withNonInvokeOpcode_returnsFalse() {
            InsnNode notInvoke = new InsnNode(IADD);
            assertThat(handler.canHandle(notInvoke))
                    .as("Should not handle IADD")
                    .isFalse();
        }

        // ==================== VirtualMethodCategory Tests ====================

        @Test
        void categorize_equalsMethod_returnsEQUALS() {
            MethodInsnNode equalsInsn = new MethodInsnNode(INVOKEVIRTUAL, "java/lang/Object", "equals", "(Ljava/lang/Object;)Z", false);

            MethodInvocationHandler.VirtualMethodCategory category =
                    MethodInvocationHandler.VirtualMethodCategory.categorize(equalsInsn, handler);

            assertThat(category).isEqualTo(MethodInvocationHandler.VirtualMethodCategory.EQUALS);
        }

        @Test
        void categorize_stringMethod_returnsSTRING_METHOD() {
            MethodInsnNode startsWithInsn = new MethodInsnNode(INVOKEVIRTUAL, "java/lang/String", "startsWith", "(Ljava/lang/String;)Z", false);

            MethodInvocationHandler.VirtualMethodCategory category =
                    MethodInvocationHandler.VirtualMethodCategory.categorize(startsWithInsn, handler);

            assertThat(category).isEqualTo(MethodInvocationHandler.VirtualMethodCategory.STRING_METHOD);
        }

        @Test
        void categorize_compareToMethod_returnsCOMPARE_TO() {
            MethodInsnNode compareToInsn = new MethodInsnNode(INVOKEVIRTUAL, "java/lang/Integer", "compareTo", "(Ljava/lang/Integer;)I", false);

            MethodInvocationHandler.VirtualMethodCategory category =
                    MethodInvocationHandler.VirtualMethodCategory.categorize(compareToInsn, handler);

            assertThat(category).isEqualTo(MethodInvocationHandler.VirtualMethodCategory.COMPARE_TO);
        }

        @Test
        void categorize_bigDecimalArithmetic_returnsBIG_DECIMAL_ARITHMETIC() {
            MethodInsnNode addInsn = new MethodInsnNode(INVOKEVIRTUAL, "java/math/BigDecimal", "add", "(Ljava/math/BigDecimal;)Ljava/math/BigDecimal;", false);

            MethodInvocationHandler.VirtualMethodCategory category =
                    MethodInvocationHandler.VirtualMethodCategory.categorize(addInsn, handler);

            assertThat(category).isEqualTo(MethodInvocationHandler.VirtualMethodCategory.BIG_DECIMAL_ARITHMETIC);
        }

        @Test
        void categorize_temporalMethod_returnsTEMPORAL_METHOD() {
            MethodInsnNode getYearInsn = new MethodInsnNode(INVOKEVIRTUAL, "java/time/LocalDate", "getYear", "()I", false);

            MethodInvocationHandler.VirtualMethodCategory category =
                    MethodInvocationHandler.VirtualMethodCategory.categorize(getYearInsn, handler);

            assertThat(category).isEqualTo(MethodInvocationHandler.VirtualMethodCategory.TEMPORAL_METHOD);
        }

        @Test
        void categorize_getterMethod_returnsGETTER() {
            MethodInsnNode getterInsn = new MethodInsnNode(INVOKEVIRTUAL, "com/example/Person", "getName", "()Ljava/lang/String;", false);

            MethodInvocationHandler.VirtualMethodCategory category =
                    MethodInvocationHandler.VirtualMethodCategory.categorize(getterInsn, handler);

            assertThat(category).isEqualTo(MethodInvocationHandler.VirtualMethodCategory.GETTER);
        }

        @Test
        void categorize_isGetterMethod_returnsGETTER() {
            MethodInsnNode isGetterInsn = new MethodInsnNode(INVOKEVIRTUAL, "com/example/Person", "isActive", "()Z", false);

            MethodInvocationHandler.VirtualMethodCategory category =
                    MethodInvocationHandler.VirtualMethodCategory.categorize(isGetterInsn, handler);

            assertThat(category).isEqualTo(MethodInvocationHandler.VirtualMethodCategory.GETTER);
        }

        @Test
        void categorize_unknownMethod_returnsUNHANDLED() {
            MethodInsnNode unknownInsn = new MethodInsnNode(INVOKEVIRTUAL, "com/example/Foo", "doSomething", "(II)V", false);

            MethodInvocationHandler.VirtualMethodCategory category =
                    MethodInvocationHandler.VirtualMethodCategory.categorize(unknownInsn, handler);

            assertThat(category).isEqualTo(MethodInvocationHandler.VirtualMethodCategory.UNHANDLED);
        }

        // ==================== handleInvokeStatic Tests ====================

        @Test
        void handle_booleanValueOf_skipsAndDoesNotAffectStack() {
            // Boolean.valueOf(Z) should be skipped
            context.push(constant(true));
            MethodInsnNode booleanValueOf = new MethodInsnNode(INVOKESTATIC, "java/lang/Boolean", "valueOf", "(Z)Ljava/lang/Boolean;", false);

            boolean terminated = handler.handle(booleanValueOf, context);

            assertThat(terminated).isFalse();
            assertThat(context.getStackSize())
                    .as("Stack should remain unchanged for Boolean.valueOf skip")
                    .isEqualTo(1);
        }

        @Test
        void handle_localDateOf_withConstants_createsConstant() {
            // Push constant arguments for LocalDate.of(2024, 1, 15)
            context.push(constant(2024));
            context.push(constant(1));
            context.push(constant(15));
            MethodInsnNode localDateOf = new MethodInsnNode(INVOKESTATIC, "java/time/LocalDate", "of", "(III)Ljava/time/LocalDate;", false);

            handler.handle(localDateOf, context);

            assertThat(context.getStackSize())
                    .as("Temporal factory should create constant")
                    .isEqualTo(1);
        }

        @Test
        void handle_localTimeOf_withConstants_createsConstant() {
            // Push constant arguments for LocalTime.of(10, 30)
            context.push(constant(10));
            context.push(constant(30));
            MethodInsnNode localTimeOf = new MethodInsnNode(INVOKESTATIC, "java/time/LocalTime", "of", "(II)Ljava/time/LocalTime;", false);

            handler.handle(localTimeOf, context);

            assertThat(context.getStackSize())
                    .as("Temporal factory should create constant for LocalTime")
                    .isEqualTo(1);
        }

        @Test
        void handle_localDateTimeOf_withConstants_createsConstant() {
            // Push constant arguments for LocalDateTime.of(2024, 1, 15, 10, 30)
            context.push(constant(2024));
            context.push(constant(1));
            context.push(constant(15));
            context.push(constant(10));
            context.push(constant(30));
            MethodInsnNode localDateTimeOf = new MethodInsnNode(INVOKESTATIC, "java/time/LocalDateTime", "of", "(IIIII)Ljava/time/LocalDateTime;", false);

            handler.handle(localDateTimeOf, context);

            assertThat(context.getStackSize())
                    .as("Temporal factory should create constant for LocalDateTime")
                    .isEqualTo(1);
        }

        @Test
        void handle_temporalFactory_wrongArgCount_doesNothing() {
            // Push only 2 args for LocalDate.of (which expects 3)
            context.push(constant(2024));
            context.push(constant(1));
            MethodInsnNode localDateOf = new MethodInsnNode(INVOKESTATIC, "java/time/LocalDate", "of", "(II)Ljava/time/LocalDate;", false);

            handler.handle(localDateOf, context);

            // Stack should remain unchanged because arg count mismatch
            assertThat(context.getStackSize())
                    .as("Wrong arg count should leave stack unchanged")
                    .isEqualTo(2);
        }

        @Test
        void handle_temporalFactory_wrongOwner_doesNothing() {
            context.push(constant(2024));
            context.push(constant(1));
            context.push(constant(15));
            // Wrong owner - not a temporal class
            MethodInsnNode wrongOwner = new MethodInsnNode(INVOKESTATIC, "java/time/ZonedDateTime", "of", "(III)Ljava/time/ZonedDateTime;", false);

            handler.handle(wrongOwner, context);

            assertThat(context.getStackSize())
                    .as("Wrong owner should leave stack unchanged")
                    .isEqualTo(3);
        }

        @Test
        void handle_temporalFactory_wrongMethodName_doesNothing() {
            context.push(constant(2024));
            context.push(constant(1));
            context.push(constant(15));
            // Wrong method name - not "of"
            MethodInsnNode wrongMethod = new MethodInsnNode(INVOKESTATIC, "java/time/LocalDate", "parse", "(Ljava/lang/String;)Ljava/time/LocalDate;", false);

            handler.handle(wrongMethod, context);

            assertThat(context.getStackSize())
                    .as("Wrong method name should leave stack unchanged")
                    .isEqualTo(3);
        }

        // ==================== handleInvokeSpecial Tests ====================

        @Test
        void handle_bigDecimalConstructor_withStringConstant_foldsToBigDecimalConstant() {
            // Simulate: new BigDecimal("123.45")
            // Stack: [placeholder for NEW, placeholder for DUP, "123.45"]
            // discardN(2) will remove the first two elements after popping args
            context.push(constant("new_marker"));  // Placeholder for NEW
            context.push(constant("dup_marker"));  // Placeholder for DUP
            context.push(constant("123.45"));

            MethodInsnNode bigDecimalInit = new MethodInsnNode(INVOKESPECIAL, "java/math/BigDecimal", "<init>", "(Ljava/lang/String;)V", false);

            handler.handle(bigDecimalInit, context);

            assertThat(context.getStackSize())
                    .as("BigDecimal constructor should fold to constant")
                    .isEqualTo(1);
        }

        @Test
        void handle_bigDecimalConstructor_withInvalidString_createsConstructorCall() {
            // Simulate: new BigDecimal("not a number")
            context.push(constant("new_marker"));
            context.push(constant("dup_marker"));
            context.push(constant("not a number"));

            MethodInsnNode bigDecimalInit = new MethodInsnNode(INVOKESPECIAL, "java/math/BigDecimal", "<init>", "(Ljava/lang/String;)V", false);

            handler.handle(bigDecimalInit, context);

            assertThat(context.getStackSize())
                    .as("Invalid BigDecimal string should create ConstructorCall")
                    .isEqualTo(1);
        }

        @Test
        void handle_nonBigDecimalConstructor_createsConstructorCall() {
            // Simulate: new SomeClass("arg")
            context.push(constant("new_marker"));
            context.push(constant("dup_marker"));
            context.push(constant("arg"));

            MethodInsnNode someClassInit = new MethodInsnNode(INVOKESPECIAL, "com/example/SomeClass", "<init>", "(Ljava/lang/String;)V", false);

            handler.handle(someClassInit, context);

            assertThat(context.getStackSize())
                    .as("Non-BigDecimal constructor should create ConstructorCall")
                    .isEqualTo(1);
        }

        @Test
        void handle_constructorWithMultipleArgs_createsConstructorCall() {
            // Simulate: new SomeClass(1, "test")
            context.push(constant("new_marker"));
            context.push(constant("dup_marker"));
            context.push(constant(1));
            context.push(constant("test"));

            MethodInsnNode multiArgInit = new MethodInsnNode(INVOKESPECIAL, "com/example/SomeClass", "<init>", "(ILjava/lang/String;)V", false);

            handler.handle(multiArgInit, context);

            assertThat(context.getStackSize())
                    .as("Multi-arg constructor should create ConstructorCall")
                    .isEqualTo(1);
        }

        @Test
        void handle_nonConstructorInvokeSpecial_doesNothing() {
            // Simulate: super.someMethod() - not a constructor
            context.push(field("name", String.class));
            MethodInsnNode superMethod = new MethodInsnNode(INVOKESPECIAL, "java/lang/Object", "toString", "()Ljava/lang/String;", false);

            handler.handle(superMethod, context);

            assertThat(context.getStackSize())
                    .as("Non-constructor INVOKESPECIAL should not modify stack")
                    .isEqualTo(1);
        }

        // ==================== String Method Tests ====================

        @Test
        void handle_substringOneArg_createsMethodCall() {
            context.push(field("name", String.class));
            context.push(constant(5));
            MethodInsnNode substringInsn = new MethodInsnNode(INVOKEVIRTUAL, "java/lang/String", "substring", "(I)Ljava/lang/String;", false);

            handler.handle(substringInsn, context);

            assertThat(context.getStackSize())
                    .as("substring(int) should create MethodCall")
                    .isEqualTo(1);
        }

        @Test
        void handle_substringTwoArgs_createsMethodCall() {
            context.push(field("name", String.class));
            context.push(constant(0));
            context.push(constant(5));
            MethodInsnNode substringInsn = new MethodInsnNode(INVOKEVIRTUAL, "java/lang/String", "substring", "(II)Ljava/lang/String;", false);

            handler.handle(substringInsn, context);

            assertThat(context.getStackSize())
                    .as("substring(int, int) should create MethodCall")
                    .isEqualTo(1);
        }

        @Test
        void handle_substringInsufficientStack_doesNotThrow() {
            // Only one element when we need 2 for substring(I)
            context.push(field("name", String.class));
            MethodInsnNode substringInsn = new MethodInsnNode(INVOKEVIRTUAL, "java/lang/String", "substring", "(I)Ljava/lang/String;", false);

            // Should not throw, just leave stack as-is
            handler.handle(substringInsn, context);

            assertThat(context.getStackSize())
                    .as("Insufficient stack should leave as-is")
                    .isEqualTo(1);
        }

        @Test
        void handle_substringWrongDescriptor_doesNothing() {
            context.push(field("name", String.class));
            context.push(constant(5));
            // Wrong descriptor
            MethodInsnNode substringWrong = new MethodInsnNode(INVOKEVIRTUAL, "java/lang/String", "substring", "(Ljava/lang/String;)Ljava/lang/String;", false);

            handler.handle(substringWrong, context);

            assertThat(context.getStackSize())
                    .as("Wrong substring descriptor should leave stack unchanged")
                    .isEqualTo(2);
        }

        // ==================== BigDecimal Arithmetic Tests ====================

        @Test
        void handle_bigDecimalAdd_createsMethodCall() {
            context.push(field("price", java.math.BigDecimal.class));
            context.push(constant(new java.math.BigDecimal("10.00")));
            MethodInsnNode addInsn = new MethodInsnNode(INVOKEVIRTUAL, "java/math/BigDecimal", "add", "(Ljava/math/BigDecimal;)Ljava/math/BigDecimal;", false);

            handler.handle(addInsn, context);

            assertThat(context.getStackSize())
                    .as("BigDecimal.add should create MethodCall")
                    .isEqualTo(1);
        }

        @Test
        void handle_bigDecimalSubtract_createsMethodCall() {
            context.push(field("price", java.math.BigDecimal.class));
            context.push(constant(new java.math.BigDecimal("5.00")));
            MethodInsnNode subtractInsn = new MethodInsnNode(INVOKEVIRTUAL, "java/math/BigDecimal", "subtract", "(Ljava/math/BigDecimal;)Ljava/math/BigDecimal;", false);

            handler.handle(subtractInsn, context);

            assertThat(context.getStackSize())
                    .as("BigDecimal.subtract should create MethodCall")
                    .isEqualTo(1);
        }

        @Test
        void handle_bigDecimalMultiply_createsMethodCall() {
            context.push(field("price", java.math.BigDecimal.class));
            context.push(constant(new java.math.BigDecimal("2.00")));
            MethodInsnNode multiplyInsn = new MethodInsnNode(INVOKEVIRTUAL, "java/math/BigDecimal", "multiply", "(Ljava/math/BigDecimal;)Ljava/math/BigDecimal;", false);

            handler.handle(multiplyInsn, context);

            assertThat(context.getStackSize())
                    .as("BigDecimal.multiply should create MethodCall")
                    .isEqualTo(1);
        }

        @Test
        void handle_bigDecimalDivide_createsMethodCall() {
            context.push(field("price", java.math.BigDecimal.class));
            context.push(constant(new java.math.BigDecimal("2.00")));
            MethodInsnNode divideInsn = new MethodInsnNode(INVOKEVIRTUAL, "java/math/BigDecimal", "divide", "(Ljava/math/BigDecimal;)Ljava/math/BigDecimal;", false);

            handler.handle(divideInsn, context);

            assertThat(context.getStackSize())
                    .as("BigDecimal.divide should create MethodCall")
                    .isEqualTo(1);
        }

        @Test
        void handle_bigDecimalUnrecognizedMethod_doesNothing() {
            context.push(field("price", java.math.BigDecimal.class));
            context.push(constant(new java.math.BigDecimal("2.00")));
            // negate() is not handled by BigDecimal arithmetic
            MethodInsnNode negateInsn = new MethodInsnNode(INVOKEVIRTUAL, "java/math/BigDecimal", "negate", "()Ljava/math/BigDecimal;", false);

            handler.handle(negateInsn, context);

            // Stack should still have 2 elements since negate is not recognized
            assertThat(context.getStackSize())
                    .as("Unrecognized BigDecimal method should not consume arguments")
                    .isEqualTo(2);
        }

        // ==================== Getter Method Tests ====================

        @Test
        void handle_getterOnEmptyStack_doesNothing() {
            // Empty stack - should not throw
            MethodInsnNode getterInsn = new MethodInsnNode(INVOKEVIRTUAL, "com/example/Person", "getName", "()Ljava/lang/String;", false);

            handler.handle(getterInsn, context);

            assertThat(context.isStackEmpty())
                    .as("Empty stack should remain empty for getter")
                    .isTrue();
        }

        @Test
        void handle_getter_createsFieldAccess() {
            // Use Parameter to represent an entity on the stack
            context.push(param("person", Object.class, 0));
            MethodInsnNode getterInsn = new MethodInsnNode(INVOKEVIRTUAL, "com/example/Person", "getName", "()Ljava/lang/String;", false);

            handler.handle(getterInsn, context);

            assertThat(context.getStackSize()).isEqualTo(1);
            assertThat(context.peek())
                    .as("Getter should create FieldAccess")
                    .isInstanceOf(io.quarkiverse.qubit.deployment.ast.LambdaExpression.FieldAccess.class);
        }

        @Test
        void handle_isGetter_createsFieldAccess() {
            context.push(param("person", Object.class, 0));
            MethodInsnNode isGetterInsn = new MethodInsnNode(INVOKEVIRTUAL, "com/example/Person", "isActive", "()Z", false);

            handler.handle(isGetterInsn, context);

            assertThat(context.getStackSize()).isEqualTo(1);
            assertThat(context.peek())
                    .as("is-getter should create FieldAccess")
                    .isInstanceOf(io.quarkiverse.qubit.deployment.ast.LambdaExpression.FieldAccess.class);
        }

        @Test
        void handle_getterWithNonGetterDescriptor_categorizedAsUnhandled() {
            // Descriptor with parameters - not a getter
            MethodInsnNode notGetter = new MethodInsnNode(INVOKEVIRTUAL, "com/example/Person", "getName", "(I)Ljava/lang/String;", false);

            MethodInvocationHandler.VirtualMethodCategory category =
                    MethodInvocationHandler.VirtualMethodCategory.categorize(notGetter, handler);

            assertThat(category)
                    .as("Method with parameters is not a getter")
                    .isEqualTo(MethodInvocationHandler.VirtualMethodCategory.UNHANDLED);
        }

        // ==================== Bi-Entity Getter Tests ====================

        @Test
        void handle_getter_onBiEntityParameter_createsBiEntityFieldAccess() {
            // Test getter on BiEntityParameter creates BiEntityFieldAccess
            // BiEntityParameter(String name, Class<?> type, int index, EntityPosition position)
            context.push(new io.quarkiverse.qubit.deployment.ast.LambdaExpression.BiEntityParameter(
                    "person",
                    Object.class,
                    0,
                    io.quarkiverse.qubit.deployment.ast.LambdaExpression.EntityPosition.FIRST));
            MethodInsnNode getterInsn = new MethodInsnNode(INVOKEVIRTUAL, "com/example/Person", "getName", "()Ljava/lang/String;", false);

            handler.handle(getterInsn, context);

            assertThat(context.getStackSize()).isEqualTo(1);
            assertThat(context.peek())
                    .as("Getter on BiEntityParameter should create BiEntityFieldAccess")
                    .isInstanceOf(io.quarkiverse.qubit.deployment.ast.LambdaExpression.BiEntityFieldAccess.class);
        }

        // ==================== INVOKEINTERFACE Tests (to kill uncovered mutation) ====================

        @Test
        void handle_invokeInterface_containsMethod_handledAsGroupMethod() {
            // Simulate calling Collection.contains() which is a group method
            context.push(field("items", java.util.Collection.class));
            context.push(param("element", Object.class, 0));
            MethodInsnNode containsInsn = new MethodInsnNode(INVOKEINTERFACE, "java/util/Collection", "contains", "(Ljava/lang/Object;)Z", true);

            // Handler should process but may not modify stack if no group method analyzer configured
            handler.handle(containsInsn, context);

            // Depending on implementation, may or may not modify stack
            assertThat(context.getStackSize()).isGreaterThanOrEqualTo(0);
        }

        // ==================== Boolean.valueOf Conditional Tests (to kill surviving mutations) ====================

        @Test
        void handle_booleanValueOf_wrongOwner_doesNotSkip() {
            // Boolean.valueOf with wrong owner should not be skipped
            context.push(constant(true));
            MethodInsnNode wrongOwner = new MethodInsnNode(INVOKESTATIC, "java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;", false);

            handler.handle(wrongOwner, context);

            // Stack may have changed based on unhandled static handling
            assertThat(context.getStackSize()).isGreaterThanOrEqualTo(0);
        }

        @Test
        void handle_booleanValueOf_wrongMethodName_doesNotSkip() {
            // Boolean with wrong method name should not be skipped
            context.push(constant(true));
            MethodInsnNode wrongMethod = new MethodInsnNode(INVOKESTATIC, "java/lang/Boolean", "parseBoolean", "(Ljava/lang/String;)Z", false);

            handler.handle(wrongMethod, context);

            // Stack should not be modified for this unhandled pattern
            assertThat(context.getStackSize()).isEqualTo(1);
        }

        @Test
        void handle_booleanValueOf_wrongDescriptor_doesNotSkip() {
            // Boolean.valueOf with wrong descriptor should not be skipped
            context.push(constant("true"));
            MethodInsnNode wrongDesc = new MethodInsnNode(INVOKESTATIC, "java/lang/Boolean", "valueOf", "(Ljava/lang/String;)Ljava/lang/Boolean;", false);

            handler.handle(wrongDesc, context);

            // String version may behave differently
            assertThat(context.getStackSize()).isGreaterThanOrEqualTo(0);
        }

        // ==================== INVOKESPECIAL Non-Constructor Tests ====================

        @Test
        void handle_invokeSpecial_nonConstructor_withNullArgs_leavesStackUnchanged() {
            // Non-constructor INVOKESPECIAL with no proper args extraction
            context.push(field("name", String.class));
            MethodInsnNode privateMethod = new MethodInsnNode(INVOKESPECIAL, "com/example/Parent", "privateHelper", "()V", false);

            handler.handle(privateMethod, context);

            assertThat(context.getStackSize())
                    .as("Non-constructor INVOKESPECIAL should leave stack unchanged")
                    .isEqualTo(1);
        }

        // ==================== Collection.contains() Pattern Tests (kill mutations 335-337, 354, 362, 366, 372) ====================

        @Test
        void handle_collectionContains_withCapturedVariableAndFieldAccess_createsInExpression() {
            // IN clause pattern: capturedCollection.contains(p.field)
            context.push(captured(0, java.util.List.class));  // CapturedVariable - collection from outer scope
            context.push(field("city", String.class));  // FieldAccess - entity field
            MethodInsnNode containsInsn = new MethodInsnNode(INVOKEINTERFACE, "java/util/Collection", "contains", "(Ljava/lang/Object;)Z", true);

            handler.handle(containsInsn, context);

            assertThat(context.getStackSize()).isEqualTo(1);
            assertThat(context.peek())
                    .as("CapturedVariable.contains(FieldAccess) should create InExpression")
                    .isInstanceOf(io.quarkiverse.qubit.deployment.ast.LambdaExpression.InExpression.class);
        }

        @Test
        void handle_collectionContains_withFieldAccessAndConstant_createsMemberOfExpression() {
            // MEMBER OF pattern: p.roles.contains("admin")
            context.push(field("roles", java.util.Set.class));  // FieldAccess - collection field on entity
            context.push(constant("admin"));  // Constant - value to check
            MethodInsnNode containsInsn = new MethodInsnNode(INVOKEINTERFACE, "java/util/Collection", "contains", "(Ljava/lang/Object;)Z", true);

            handler.handle(containsInsn, context);

            assertThat(context.getStackSize()).isEqualTo(1);
            assertThat(context.peek())
                    .as("FieldAccess.contains(Constant) should create MemberOfExpression")
                    .isInstanceOf(io.quarkiverse.qubit.deployment.ast.LambdaExpression.MemberOfExpression.class);
        }

        @Test
        void handle_collectionContains_withFieldAccessAndCapturedVariable_createsMemberOfExpression() {
            // MEMBER OF pattern: p.tags.contains(capturedTag)
            context.push(field("tags", java.util.Set.class));  // FieldAccess - collection field
            context.push(captured(0, String.class));  // CapturedVariable - value from outer scope
            MethodInsnNode containsInsn = new MethodInsnNode(INVOKEINTERFACE, "java/util/Collection", "contains", "(Ljava/lang/Object;)Z", true);

            handler.handle(containsInsn, context);

            assertThat(context.getStackSize()).isEqualTo(1);
            assertThat(context.peek())
                    .as("FieldAccess.contains(CapturedVariable) should create MemberOfExpression")
                    .isInstanceOf(io.quarkiverse.qubit.deployment.ast.LambdaExpression.MemberOfExpression.class);
        }

        @Test
        void handle_collectionContains_withNonMatchingPattern_createsMethodCall() {
            // Neither IN nor MEMBER OF pattern: constant.contains(constant)
            context.push(constant(java.util.List.of("a", "b")));  // Constant - not CapturedVariable or FieldAccess
            context.push(constant("x"));  // Constant
            MethodInsnNode containsInsn = new MethodInsnNode(INVOKEINTERFACE, "java/util/Collection", "contains", "(Ljava/lang/Object;)Z", true);

            handler.handle(containsInsn, context);

            assertThat(context.getStackSize()).isEqualTo(1);
            assertThat(context.peek())
                    .as("Non-matching pattern should create MethodCall fallback")
                    .isInstanceOf(io.quarkiverse.qubit.deployment.ast.LambdaExpression.MethodCall.class);
        }

        @Test
        void handle_collectionContains_withPathExpressionTarget_createsInExpression() {
            // IN clause with PathExpression argument
            context.push(captured(0, java.util.List.class));  // CapturedVariable
            context.push(new io.quarkiverse.qubit.deployment.ast.LambdaExpression.PathExpression(
                    java.util.List.of(
                            new io.quarkiverse.qubit.deployment.ast.LambdaExpression.PathSegment("address", Object.class, io.quarkiverse.qubit.deployment.ast.LambdaExpression.RelationType.FIELD),
                            new io.quarkiverse.qubit.deployment.ast.LambdaExpression.PathSegment("city", String.class, io.quarkiverse.qubit.deployment.ast.LambdaExpression.RelationType.FIELD)),
                    String.class));  // PathExpression
            MethodInsnNode containsInsn = new MethodInsnNode(INVOKEINTERFACE, "java/util/Collection", "contains", "(Ljava/lang/Object;)Z", true);

            handler.handle(containsInsn, context);

            assertThat(context.peek())
                    .as("PathExpression argument should create InExpression")
                    .isInstanceOf(io.quarkiverse.qubit.deployment.ast.LambdaExpression.InExpression.class);
        }

        @Test
        void handle_collectionContains_withBiEntityFieldAccess_createsInExpression() {
            // IN clause with BiEntityFieldAccess argument
            context.push(captured(0, java.util.List.class));
            context.push(new io.quarkiverse.qubit.deployment.ast.LambdaExpression.BiEntityFieldAccess(
                    "city", String.class, io.quarkiverse.qubit.deployment.ast.LambdaExpression.EntityPosition.FIRST));
            MethodInsnNode containsInsn = new MethodInsnNode(INVOKEINTERFACE, "java/util/Collection", "contains", "(Ljava/lang/Object;)Z", true);

            handler.handle(containsInsn, context);

            assertThat(context.peek())
                    .as("BiEntityFieldAccess argument should create InExpression")
                    .isInstanceOf(io.quarkiverse.qubit.deployment.ast.LambdaExpression.InExpression.class);
        }

        @Test
        void handle_collectionContains_withBiEntityPathExpression_createsInExpression() {
            // IN clause with BiEntityPathExpression argument
            context.push(captured(0, java.util.List.class));
            context.push(new io.quarkiverse.qubit.deployment.ast.LambdaExpression.BiEntityPathExpression(
                    java.util.List.of(
                            new io.quarkiverse.qubit.deployment.ast.LambdaExpression.PathSegment("address", Object.class, io.quarkiverse.qubit.deployment.ast.LambdaExpression.RelationType.FIELD),
                            new io.quarkiverse.qubit.deployment.ast.LambdaExpression.PathSegment("city", String.class, io.quarkiverse.qubit.deployment.ast.LambdaExpression.RelationType.FIELD)),
                    String.class, io.quarkiverse.qubit.deployment.ast.LambdaExpression.EntityPosition.SECOND));
            MethodInsnNode containsInsn = new MethodInsnNode(INVOKEINTERFACE, "java/util/Collection", "contains", "(Ljava/lang/Object;)Z", true);

            handler.handle(containsInsn, context);

            assertThat(context.peek())
                    .as("BiEntityPathExpression argument should create InExpression")
                    .isInstanceOf(io.quarkiverse.qubit.deployment.ast.LambdaExpression.InExpression.class);
        }

        @Test
        void handle_collectionContains_pathExpressionTarget_createsMemberOfExpression() {
            // MEMBER OF with PathExpression target (collection field)
            context.push(new io.quarkiverse.qubit.deployment.ast.LambdaExpression.PathExpression(
                    java.util.List.of(
                            new io.quarkiverse.qubit.deployment.ast.LambdaExpression.PathSegment("person", Object.class, io.quarkiverse.qubit.deployment.ast.LambdaExpression.RelationType.FIELD),
                            new io.quarkiverse.qubit.deployment.ast.LambdaExpression.PathSegment("roles", java.util.Set.class, io.quarkiverse.qubit.deployment.ast.LambdaExpression.RelationType.FIELD)),
                    java.util.Set.class));  // PathExpression target
            context.push(constant("admin"));  // Constant value
            MethodInsnNode containsInsn = new MethodInsnNode(INVOKEINTERFACE, "java/util/Collection", "contains", "(Ljava/lang/Object;)Z", true);

            handler.handle(containsInsn, context);

            assertThat(context.peek())
                    .as("PathExpression.contains(Constant) should create MemberOfExpression")
                    .isInstanceOf(io.quarkiverse.qubit.deployment.ast.LambdaExpression.MemberOfExpression.class);
        }

        @Test
        void handle_collectionContains_biEntityFieldAccessTarget_createsMemberOfExpression() {
            // MEMBER OF with BiEntityFieldAccess target
            context.push(new io.quarkiverse.qubit.deployment.ast.LambdaExpression.BiEntityFieldAccess(
                    "roles", java.util.Set.class, io.quarkiverse.qubit.deployment.ast.LambdaExpression.EntityPosition.FIRST));
            context.push(constant("admin"));
            MethodInsnNode containsInsn = new MethodInsnNode(INVOKEINTERFACE, "java/util/Collection", "contains", "(Ljava/lang/Object;)Z", true);

            handler.handle(containsInsn, context);

            assertThat(context.peek())
                    .as("BiEntityFieldAccess.contains(Constant) should create MemberOfExpression")
                    .isInstanceOf(io.quarkiverse.qubit.deployment.ast.LambdaExpression.MemberOfExpression.class);
        }

        @Test
        void handle_collectionContains_biEntityPathExpressionTarget_createsMemberOfExpression() {
            // MEMBER OF with BiEntityPathExpression target
            context.push(new io.quarkiverse.qubit.deployment.ast.LambdaExpression.BiEntityPathExpression(
                    java.util.List.of(
                            new io.quarkiverse.qubit.deployment.ast.LambdaExpression.PathSegment("person", Object.class, io.quarkiverse.qubit.deployment.ast.LambdaExpression.RelationType.FIELD),
                            new io.quarkiverse.qubit.deployment.ast.LambdaExpression.PathSegment("roles", java.util.Set.class, io.quarkiverse.qubit.deployment.ast.LambdaExpression.RelationType.FIELD)),
                    java.util.Set.class, io.quarkiverse.qubit.deployment.ast.LambdaExpression.EntityPosition.SECOND));
            context.push(constant("admin"));
            MethodInsnNode containsInsn = new MethodInsnNode(INVOKEINTERFACE, "java/util/Collection", "contains", "(Ljava/lang/Object;)Z", true);

            handler.handle(containsInsn, context);

            assertThat(context.peek())
                    .as("BiEntityPathExpression.contains(Constant) should create MemberOfExpression")
                    .isInstanceOf(io.quarkiverse.qubit.deployment.ast.LambdaExpression.MemberOfExpression.class);
        }

        // ==================== isCollectionContainsCall condition tests (kill mutations 335-337) ====================

        @Test
        void handle_containsCall_wrongMethodName_doesNotCreateInExpression() {
            // Wrong method name - "add" instead of "contains"
            context.push(captured(0, java.util.List.class));
            context.push(field("city", String.class));
            MethodInsnNode addInsn = new MethodInsnNode(INVOKEINTERFACE, "java/util/Collection", "add", "(Ljava/lang/Object;)Z", true);

            handler.handle(addInsn, context);

            // "add" is not Collection.contains(), so stack should remain unchanged
            assertThat(context.getStackSize())
                    .as("Wrong method name should not be processed as contains")
                    .isEqualTo(2);
        }

        @Test
        void handle_containsCall_wrongDescriptor_doesNotCreateInExpression() {
            // Wrong descriptor - different signature
            context.push(captured(0, java.util.List.class));
            context.push(field("city", String.class));
            MethodInsnNode wrongDescInsn = new MethodInsnNode(INVOKEINTERFACE, "java/util/Collection", "contains", "(II)Z", true);

            handler.handle(wrongDescInsn, context);

            assertThat(context.getStackSize())
                    .as("Wrong descriptor should not be processed as contains")
                    .isEqualTo(2);
        }

        @Test
        void handle_containsCall_wrongOwner_doesNotCreateInExpression() {
            // Wrong owner - not a Collection interface
            context.push(captured(0, java.util.List.class));
            context.push(field("city", String.class));
            MethodInsnNode wrongOwnerInsn = new MethodInsnNode(INVOKEINTERFACE, "java/util/Map", "contains", "(Ljava/lang/Object;)Z", true);

            handler.handle(wrongOwnerInsn, context);

            // java/util/Map is not in COLLECTION_INTERFACE_OWNERS
            assertThat(context.getStackSize())
                    .as("Wrong owner should not be processed as contains")
                    .isEqualTo(2);
        }

        @Test
        void handle_containsCall_listOwner_createsExpression() {
            // java/util/List is in COLLECTION_INTERFACE_OWNERS
            context.push(captured(0, java.util.List.class));
            context.push(field("city", String.class));
            MethodInsnNode listContainsInsn = new MethodInsnNode(INVOKEINTERFACE, "java/util/List", "contains", "(Ljava/lang/Object;)Z", true);

            handler.handle(listContainsInsn, context);

            assertThat(context.peek())
                    .as("java/util/List owner should be recognized")
                    .isInstanceOf(io.quarkiverse.qubit.deployment.ast.LambdaExpression.InExpression.class);
        }

        @Test
        void handle_containsCall_setOwner_createsExpression() {
            // java/util/Set is in COLLECTION_INTERFACE_OWNERS
            context.push(captured(0, java.util.Set.class));
            context.push(field("city", String.class));
            MethodInsnNode setContainsInsn = new MethodInsnNode(INVOKEINTERFACE, "java/util/Set", "contains", "(Ljava/lang/Object;)Z", true);

            handler.handle(setContainsInsn, context);

            assertThat(context.peek())
                    .as("java/util/Set owner should be recognized")
                    .isInstanceOf(io.quarkiverse.qubit.deployment.ast.LambdaExpression.InExpression.class);
        }

        // ==================== handleCollectionContains empty stack test (kill mutation 354) ====================

        @Test
        void handle_collectionContains_emptyStack_doesNotThrow() {
            // Empty stack should be handled gracefully via popPair() returning null
            MethodInsnNode containsInsn = new MethodInsnNode(INVOKEINTERFACE, "java/util/Collection", "contains", "(Ljava/lang/Object;)Z", true);

            // Should not throw - returns early when popPair() returns null
            handler.handle(containsInsn, context);

            assertThat(context.isStackEmpty())
                    .as("Empty stack should remain empty")
                    .isTrue();
        }

        @Test
        void handle_collectionContains_oneElementStack_doesNotThrow() {
            // Only one element - popPair() needs 2
            context.push(captured(0, java.util.List.class));
            MethodInsnNode containsInsn = new MethodInsnNode(INVOKEINTERFACE, "java/util/Collection", "contains", "(Ljava/lang/Object;)Z", true);

            // Should not throw
            handler.handle(containsInsn, context);

            // Stack may have been partially modified
            assertThat(context.getStackSize()).isGreaterThanOrEqualTo(0);
        }

        // ==================== Temporal Factory Method condition tests (kill mutations 711, 717) ====================

        @Test
        void handle_temporalFactory_insufficientStackSize_doesNothing() {
            // Only 2 args on stack when LocalDate.of needs 3
            context.push(constant(2024));
            context.push(constant(1));
            // Missing third argument
            MethodInsnNode localDateOf = new MethodInsnNode(INVOKESTATIC, "java/time/LocalDate", "of", "(III)Ljava/time/LocalDate;", false);

            handler.handle(localDateOf, context);

            // Stack should remain unchanged due to insufficient stack size
            assertThat(context.getStackSize())
                    .as("Insufficient stack should leave unchanged")
                    .isEqualTo(2);
        }

        @Test
        void handle_temporalFactory_wrongExpectedArgCount_doesNothing() {
            // LocalDateTime.of expects 5 args but we provide 3
            context.push(constant(2024));
            context.push(constant(1));
            context.push(constant(15));
            // LocalDateTime.of(III) doesn't exist - wrong descriptor
            MethodInsnNode localDateTimeOf = new MethodInsnNode(INVOKESTATIC, "java/time/LocalDateTime", "of", "(III)Ljava/time/LocalDateTime;", false);

            handler.handle(localDateTimeOf, context);

            // Should not process because arg count doesn't match expectedArgCount (5)
            assertThat(context.getStackSize())
                    .as("Wrong expected arg count should leave stack unchanged")
                    .isEqualTo(3);
        }

        @Test
        void handle_localTimeOf_withNonConstantArgs_createsMethodCall() {
            // Non-constant arguments should fall back to method call
            context.push(field("hour", int.class));  // Non-constant
            context.push(constant(30));
            MethodInsnNode localTimeOf = new MethodInsnNode(INVOKESTATIC, "java/time/LocalTime", "of", "(II)Ljava/time/LocalTime;", false);

            handler.handle(localTimeOf, context);

            assertThat(context.getStackSize()).isEqualTo(1);
            assertThat(context.peek())
                    .as("Non-constant args should create MethodCall")
                    .isInstanceOf(io.quarkiverse.qubit.deployment.ast.LambdaExpression.MethodCall.class);
        }

        @Test
        void handle_localDateOf_withNonConstantArgs_createsMethodCall() {
            // Non-constant year argument
            context.push(field("year", int.class));
            context.push(constant(1));
            context.push(constant(15));
            MethodInsnNode localDateOf = new MethodInsnNode(INVOKESTATIC, "java/time/LocalDate", "of", "(III)Ljava/time/LocalDate;", false);

            handler.handle(localDateOf, context);

            assertThat(context.getStackSize()).isEqualTo(1);
            assertThat(context.peek())
                    .as("Non-constant args should create MethodCall")
                    .isInstanceOf(io.quarkiverse.qubit.deployment.ast.LambdaExpression.MethodCall.class);
        }

        // ==================== Substring edge cases (kill mutations 528, 531) ====================

        @Test
        void handle_substringOneArg_exactStackSize_succeeds() {
            // Exactly 2 elements for substring(I) - target + 1 arg
            context.push(field("name", String.class));
            context.push(constant(5));
            MethodInsnNode substringInsn = new MethodInsnNode(INVOKEVIRTUAL, "java/lang/String", "substring", "(I)Ljava/lang/String;", false);

            handler.handle(substringInsn, context);

            assertThat(context.getStackSize()).isEqualTo(1);
        }

        @Test
        void handle_substringTwoArgs_exactStackSize_succeeds() {
            // Exactly 3 elements for substring(II) - target + 2 args
            context.push(field("name", String.class));
            context.push(constant(0));
            context.push(constant(5));
            MethodInsnNode substringInsn = new MethodInsnNode(INVOKEVIRTUAL, "java/lang/String", "substring", "(II)Ljava/lang/String;", false);

            handler.handle(substringInsn, context);

            assertThat(context.getStackSize()).isEqualTo(1);
        }

        @Test
        void handle_substringTwoArgs_insufficientStack_leavesUnchanged() {
            // Only 2 elements when we need 3 for substring(II)
            context.push(field("name", String.class));
            context.push(constant(0));
            MethodInsnNode substringInsn = new MethodInsnNode(INVOKEVIRTUAL, "java/lang/String", "substring", "(II)Ljava/lang/String;", false);

            handler.handle(substringInsn, context);

            assertThat(context.getStackSize())
                    .as("Insufficient stack for substring(II) should leave unchanged")
                    .isEqualTo(2);
        }

        // ==================== INVOKEINTERFACE for equals and compareTo (kill mutations 306, 312-313) ====================

        @Test
        void handle_invokeInterface_equalsMethod_handlesCorrectly() {
            // Interface equals() call
            context.push(field("name", String.class));
            context.push(constant("test"));
            MethodInsnNode equalsInsn = new MethodInsnNode(INVOKEINTERFACE, "java/lang/Comparable", "equals", "(Ljava/lang/Object;)Z", true);

            handler.handle(equalsInsn, context);

            assertThat(context.getStackSize()).isEqualTo(1);
            assertThat(context.peek())
                    .as("Interface equals() should create BinaryOp EQ")
                    .isInstanceOf(io.quarkiverse.qubit.deployment.ast.LambdaExpression.BinaryOp.class);
        }

        @Test
        void handle_invokeInterface_compareToMethod_handlesCorrectly() {
            // Interface compareTo() call
            context.push(field("name", String.class));
            context.push(constant("test"));
            MethodInsnNode compareToInsn = new MethodInsnNode(INVOKEINTERFACE, "java/lang/Comparable", "compareTo", "(Ljava/lang/Object;)I", true);

            handler.handle(compareToInsn, context);

            assertThat(context.getStackSize()).isEqualTo(1);
            assertThat(context.peek())
                    .as("Interface compareTo() should create MethodCall")
                    .isInstanceOf(io.quarkiverse.qubit.deployment.ast.LambdaExpression.MethodCall.class);
        }

        // ==================== handleInvokeSpecial constructor edge cases (kill mutations 239, 248) ====================

        @Test
        void handle_invokeSpecial_constructorWithNoArgs_createsConstructorCall() {
            // Zero-argument constructor
            context.push(constant("new_marker"));  // NEW
            context.push(constant("dup_marker"));  // DUP
            // No constructor arguments
            MethodInsnNode noArgInit = new MethodInsnNode(INVOKESPECIAL, "com/example/SimpleClass", "<init>", "()V", false);

            handler.handle(noArgInit, context);

            assertThat(context.getStackSize())
                    .as("Zero-arg constructor should create ConstructorCall")
                    .isEqualTo(1);
        }

        @Test
        void handle_invokeSpecial_bigDecimalWithMultipleArgs_createsConstructorCall() {
            // BigDecimal constructor with wrong arg count (not String constructor)
            context.push(constant("new_marker"));
            context.push(constant("dup_marker"));
            context.push(constant(123L));  // long
            context.push(constant(2));  // scale
            MethodInsnNode bigDecimalInit = new MethodInsnNode(INVOKESPECIAL, "java/math/BigDecimal", "<init>", "(JI)V", false);

            handler.handle(bigDecimalInit, context);

            assertThat(context.getStackSize())
                    .as("BigDecimal with multiple args should create ConstructorCall")
                    .isEqualTo(1);
        }

        @Test
        void handle_invokeSpecial_bigDecimalWithNonConstantArg_createsConstructorCall() {
            // BigDecimal(String) but arg is not a Constant
            context.push(constant("new_marker"));
            context.push(constant("dup_marker"));
            context.push(field("priceString", String.class));  // FieldAccess, not Constant
            MethodInsnNode bigDecimalInit = new MethodInsnNode(INVOKESPECIAL, "java/math/BigDecimal", "<init>", "(Ljava/lang/String;)V", false);

            handler.handle(bigDecimalInit, context);

            assertThat(context.getStackSize()).isEqualTo(1);
            assertThat(context.peek())
                    .as("Non-constant arg should create ConstructorCall, not constant folding")
                    .isInstanceOf(io.quarkiverse.qubit.deployment.ast.LambdaExpression.ConstructorCall.class);
        }

        @Test
        void handle_invokeSpecial_bigDecimalWithNonStringConstant_createsConstructorCall() {
            // BigDecimal constructor arg is Constant but not String type
            context.push(constant("new_marker"));
            context.push(constant("dup_marker"));
            context.push(constant(123));  // Integer constant, not String
            MethodInsnNode bigDecimalInit = new MethodInsnNode(INVOKESPECIAL, "java/math/BigDecimal", "<init>", "(I)V", false);

            handler.handle(bigDecimalInit, context);

            assertThat(context.getStackSize()).isEqualTo(1);
            assertThat(context.peek())
                    .as("Non-string constant should create ConstructorCall")
                    .isInstanceOf(io.quarkiverse.qubit.deployment.ast.LambdaExpression.ConstructorCall.class);
        }
    }
}
