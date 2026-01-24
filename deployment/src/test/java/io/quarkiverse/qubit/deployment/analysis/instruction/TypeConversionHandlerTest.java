package io.quarkiverse.qubit.deployment.analysis.instruction;

import io.quarkiverse.qubit.deployment.ast.LambdaExpression;
import io.quarkiverse.qubit.deployment.common.BytecodeAnalysisException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.util.stream.Stream;

import static io.quarkiverse.qubit.deployment.testutil.AstBuilders.*;
import static io.quarkiverse.qubit.deployment.testutil.fixtures.AsmFixtures.testMethod;
import static io.quarkiverse.qubit.deployment.testutil.fixtures.AnalysisContextFixtures.contextFor;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link TypeConversionHandler} covering all type conversion opcodes
 * and constant folding optimization.
 */
class TypeConversionHandlerTest {

    private TypeConversionHandler handler;
    private AnalysisContext context;
    private MethodNode testMethod;

    @BeforeEach
    void setUp() {
        handler = TypeConversionHandler.INSTANCE;
        testMethod = testMethod().build();
        context = contextFor(testMethod, 0);
    }

    // ========================================================================
    // canHandle() Tests
    // ========================================================================

    @Nested
    class CanHandleTests {

        @ParameterizedTest
        @MethodSource("io.quarkiverse.qubit.deployment.analysis.instruction.TypeConversionHandlerTest#typeConversionOpcodes")
        void canHandle_typeConversionOpcodes_returnsTrue(int opcode) {
            InsnNode insn = new InsnNode(opcode);
            assertThat(handler.canHandle(insn)).isTrue();
        }

        @Test
        void canHandle_nonTypeConversionOpcode_returnsFalse() {
            InsnNode insn = new InsnNode(Opcodes.ICONST_0);
            assertThat(handler.canHandle(insn)).isFalse();
        }

        @Test
        void canHandle_loadInstruction_returnsFalse() {
            VarInsnNode insn = new VarInsnNode(Opcodes.ILOAD, 0);
            assertThat(handler.canHandle(insn)).isFalse();
        }
    }

    // ========================================================================
    // Int to Other Type Conversions (I2L, I2F, I2D)
    // ========================================================================

    @Nested
    class IntConversionTests {

        @Test
        void handle_I2L_convertsIntToLong() {
            context.push(new LambdaExpression.Constant(42, int.class));
            InsnNode insn = new InsnNode(Opcodes.I2L);

            boolean result = handler.handle(insn, context);

            assertThat(result).isFalse();
            assertThat(context.isStackEmpty()).isFalse();
            LambdaExpression top = context.pop();
            assertThat(top).isInstanceOf(LambdaExpression.Constant.class);
            LambdaExpression.Constant constant = (LambdaExpression.Constant) top;
            assertThat(constant.value()).isEqualTo(42L);
            assertThat(constant.type()).isEqualTo(long.class);
        }

        @Test
        void handle_I2F_convertsIntToFloat() {
            context.push(new LambdaExpression.Constant(42, int.class));
            InsnNode insn = new InsnNode(Opcodes.I2F);

            boolean result = handler.handle(insn, context);

            assertThat(result).isFalse();
            LambdaExpression.Constant constant = (LambdaExpression.Constant) context.pop();
            assertThat(constant.value()).isEqualTo(42.0f);
            assertThat(constant.type()).isEqualTo(float.class);
        }

        @Test
        void handle_I2D_convertsIntToDouble() {
            context.push(new LambdaExpression.Constant(42, int.class));
            InsnNode insn = new InsnNode(Opcodes.I2D);

            boolean result = handler.handle(insn, context);

            assertThat(result).isFalse();
            LambdaExpression.Constant constant = (LambdaExpression.Constant) context.pop();
            assertThat(constant.value()).isEqualTo(42.0);
            assertThat(constant.type()).isEqualTo(double.class);
        }
    }

    // ========================================================================
    // Long to Other Type Conversions (L2I, L2F, L2D)
    // ========================================================================

    @Nested
    class LongConversionTests {

        @Test
        void handle_L2I_convertsLongToInt() {
            context.push(new LambdaExpression.Constant(42L, long.class));
            InsnNode insn = new InsnNode(Opcodes.L2I);

            boolean result = handler.handle(insn, context);

            assertThat(result).isFalse();
            LambdaExpression.Constant constant = (LambdaExpression.Constant) context.pop();
            assertThat(constant.value()).isEqualTo(42);
            assertThat(constant.type()).isEqualTo(int.class);
        }

        @Test
        void handle_L2F_convertsLongToFloat() {
            context.push(new LambdaExpression.Constant(42L, long.class));
            InsnNode insn = new InsnNode(Opcodes.L2F);

            boolean result = handler.handle(insn, context);

            assertThat(result).isFalse();
            LambdaExpression.Constant constant = (LambdaExpression.Constant) context.pop();
            assertThat(constant.value()).isEqualTo(42.0f);
            assertThat(constant.type()).isEqualTo(float.class);
        }

        @Test
        void handle_L2D_convertsLongToDouble() {
            context.push(new LambdaExpression.Constant(42L, long.class));
            InsnNode insn = new InsnNode(Opcodes.L2D);

            boolean result = handler.handle(insn, context);

            assertThat(result).isFalse();
            LambdaExpression.Constant constant = (LambdaExpression.Constant) context.pop();
            assertThat(constant.value()).isEqualTo(42.0);
            assertThat(constant.type()).isEqualTo(double.class);
        }
    }

    // ========================================================================
    // Float to Other Type Conversions (F2I, F2L, F2D)
    // ========================================================================

    @Nested
    class FloatConversionTests {

        @Test
        void handle_F2I_convertsFloatToInt() {
            context.push(new LambdaExpression.Constant(42.5f, float.class));
            InsnNode insn = new InsnNode(Opcodes.F2I);

            boolean result = handler.handle(insn, context);

            assertThat(result).isFalse();
            LambdaExpression.Constant constant = (LambdaExpression.Constant) context.pop();
            assertThat(constant.value()).isEqualTo(42);
            assertThat(constant.type()).isEqualTo(int.class);
        }

        @Test
        void handle_F2L_convertsFloatToLong() {
            context.push(new LambdaExpression.Constant(42.5f, float.class));
            InsnNode insn = new InsnNode(Opcodes.F2L);

            boolean result = handler.handle(insn, context);

            assertThat(result).isFalse();
            LambdaExpression.Constant constant = (LambdaExpression.Constant) context.pop();
            assertThat(constant.value()).isEqualTo(42L);
            assertThat(constant.type()).isEqualTo(long.class);
        }

        @Test
        void handle_F2D_convertsFloatToDouble() {
            context.push(new LambdaExpression.Constant(42.5f, float.class));
            InsnNode insn = new InsnNode(Opcodes.F2D);

            boolean result = handler.handle(insn, context);

            assertThat(result).isFalse();
            LambdaExpression.Constant constant = (LambdaExpression.Constant) context.pop();
            assertThat(constant.value()).isEqualTo(42.5);
            assertThat(constant.type()).isEqualTo(double.class);
        }
    }

    // ========================================================================
    // Double to Other Type Conversions (D2I, D2L, D2F)
    // ========================================================================

    @Nested
    class DoubleConversionTests {

        @Test
        void handle_D2I_convertsDoubleToInt() {
            context.push(new LambdaExpression.Constant(42.9, double.class));
            InsnNode insn = new InsnNode(Opcodes.D2I);

            boolean result = handler.handle(insn, context);

            assertThat(result).isFalse();
            LambdaExpression.Constant constant = (LambdaExpression.Constant) context.pop();
            assertThat(constant.value()).isEqualTo(42);
            assertThat(constant.type()).isEqualTo(int.class);
        }

        @Test
        void handle_D2L_convertsDoubleToLong() {
            context.push(new LambdaExpression.Constant(42.9, double.class));
            InsnNode insn = new InsnNode(Opcodes.D2L);

            boolean result = handler.handle(insn, context);

            assertThat(result).isFalse();
            LambdaExpression.Constant constant = (LambdaExpression.Constant) context.pop();
            assertThat(constant.value()).isEqualTo(42L);
            assertThat(constant.type()).isEqualTo(long.class);
        }

        @Test
        void handle_D2F_convertsDoubleToFloat() {
            context.push(new LambdaExpression.Constant(42.5, double.class));
            InsnNode insn = new InsnNode(Opcodes.D2F);

            boolean result = handler.handle(insn, context);

            assertThat(result).isFalse();
            LambdaExpression.Constant constant = (LambdaExpression.Constant) context.pop();
            assertThat(constant.value()).isEqualTo(42.5f);
            assertThat(constant.type()).isEqualTo(float.class);
        }
    }

    // ========================================================================
    // Edge Cases and Error Handling
    // ========================================================================

    @Nested
    class EdgeCaseTests {

        @Test
        void handle_emptyStack_doesNotThrow() {
            InsnNode insn = new InsnNode(Opcodes.I2L);

            boolean result = handler.handle(insn, context);

            assertThat(result).isFalse();
            assertThat(context.isStackEmpty()).isTrue();
        }

        @Test
        void handle_nonConstantOnStack_leavesStackUnchanged() {
            LambdaExpression.FieldAccess fieldAccess = field("age", int.class);
            context.push(fieldAccess);
            InsnNode insn = new InsnNode(Opcodes.I2L);

            boolean result = handler.handle(insn, context);

            assertThat(result).isFalse();
            assertThat(context.pop()).isSameAs(fieldAccess);
        }

        @Test
        void handle_constantWithWrongType_leavesStackUnchanged() {
            // Push a long constant but try to convert from int
            context.push(new LambdaExpression.Constant(42L, long.class));
            InsnNode insn = new InsnNode(Opcodes.I2L);  // Expects int source type

            boolean result = handler.handle(insn, context);

            assertThat(result).isFalse();
            LambdaExpression.Constant constant = (LambdaExpression.Constant) context.pop();
            assertThat(constant.value()).isEqualTo(42L);  // Unchanged
            assertThat(constant.type()).isEqualTo(long.class);  // Unchanged
        }

        @Test
        void handle_negativeValues_convertsCorrectly() {
            context.push(new LambdaExpression.Constant(-100, int.class));
            InsnNode insn = new InsnNode(Opcodes.I2L);

            handler.handle(insn, context);

            LambdaExpression.Constant constant = (LambdaExpression.Constant) context.pop();
            assertThat(constant.value()).isEqualTo(-100L);
        }

        @Test
        void handle_largeValues_convertsCorrectly() {
            context.push(new LambdaExpression.Constant(Long.MAX_VALUE, long.class));
            InsnNode insn = new InsnNode(Opcodes.L2D);

            handler.handle(insn, context);

            LambdaExpression.Constant constant = (LambdaExpression.Constant) context.pop();
            assertThat(constant.value()).isEqualTo((double) Long.MAX_VALUE);
        }

        @Test
        void handle_zeroValue_convertsCorrectly() {
            context.push(new LambdaExpression.Constant(0, int.class));
            InsnNode insn = new InsnNode(Opcodes.I2D);

            handler.handle(insn, context);

            LambdaExpression.Constant constant = (LambdaExpression.Constant) context.pop();
            assertThat(constant.value()).isEqualTo(0.0);
        }

        @Test
        void handle_unsupportedOpcode_throwsException() {
            InsnNode insn = new InsnNode(Opcodes.I2B);  // Byte conversion not supported

            assertThatThrownBy(() -> handler.handle(insn, context))
                    .isInstanceOf(BytecodeAnalysisException.class)
                    .hasMessageContaining("type conversion");
        }
    }

    // ========================================================================
    // Test Data Providers
    // ========================================================================

    static Stream<Arguments> typeConversionOpcodes() {
        return Stream.of(
                Arguments.of(Opcodes.I2L),
                Arguments.of(Opcodes.I2F),
                Arguments.of(Opcodes.I2D),
                Arguments.of(Opcodes.L2I),
                Arguments.of(Opcodes.L2F),
                Arguments.of(Opcodes.L2D),
                Arguments.of(Opcodes.F2I),
                Arguments.of(Opcodes.F2L),
                Arguments.of(Opcodes.F2D),
                Arguments.of(Opcodes.D2I),
                Arguments.of(Opcodes.D2L),
                Arguments.of(Opcodes.D2F)
        );
    }
}
