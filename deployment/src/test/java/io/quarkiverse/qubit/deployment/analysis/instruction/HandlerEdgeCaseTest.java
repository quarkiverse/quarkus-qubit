package io.quarkiverse.qubit.deployment.analysis.instruction;

import io.quarkiverse.qubit.deployment.common.BytecodeAnalysisException;
import io.quarkiverse.qubit.deployment.common.BytecodeValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
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
}
