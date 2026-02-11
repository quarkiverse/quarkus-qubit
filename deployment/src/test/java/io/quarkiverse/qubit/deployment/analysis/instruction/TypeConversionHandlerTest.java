package io.quarkiverse.qubit.deployment.analysis.instruction;

import static io.quarkiverse.qubit.deployment.testutil.AstBuilders.*;
import static io.quarkiverse.qubit.deployment.testutil.fixtures.AnalysisContextFixtures.contextFor;
import static io.quarkiverse.qubit.deployment.testutil.fixtures.AsmFixtures.testMethod;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.stream.Stream;

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

import io.quarkiverse.qubit.deployment.ast.LambdaExpression;
import io.quarkiverse.qubit.deployment.common.BytecodeAnalysisException;

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
    // Type Conversion Tests (Parameterized)
    // ========================================================================

    @Nested
    class TypeConversionTests {

        @ParameterizedTest(name = "{0}: {1} → {4}")
        @MethodSource("io.quarkiverse.qubit.deployment.analysis.instruction.TypeConversionHandlerTest#typeConversionCases")
        void handle_typeConversion_convertsCorrectly(
                String description,
                Object sourceValue,
                Class<?> sourceType,
                int opcode,
                Object expectedValue,
                Class<?> expectedType) {

            context.push(new LambdaExpression.Constant(sourceValue, sourceType));
            InsnNode insn = new InsnNode(opcode);

            boolean result = handler.handle(insn, context);

            assertThat(result).isFalse();
            assertThat(context.isStackEmpty()).isFalse();
            LambdaExpression top = context.pop();
            assertThat(top).isInstanceOf(LambdaExpression.Constant.class);
            LambdaExpression.Constant constant = (LambdaExpression.Constant) top;
            assertThat(constant.value()).isEqualTo(expectedValue);
            assertThat(constant.type()).isEqualTo(expectedType);
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
            InsnNode insn = new InsnNode(Opcodes.I2L); // Expects int source type

            boolean result = handler.handle(insn, context);

            assertThat(result).isFalse();
            LambdaExpression.Constant constant = (LambdaExpression.Constant) context.pop();
            assertThat(constant.value()).isEqualTo(42L); // Unchanged
            assertThat(constant.type()).isEqualTo(long.class); // Unchanged
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
            InsnNode insn = new InsnNode(Opcodes.I2B); // Byte conversion not supported

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
                Arguments.of(Opcodes.D2F));
    }

    /**
     * Test data for type conversion operations.
     * Each entry: description, sourceValue, sourceType, opcode, expectedValue, expectedType
     */
    static Stream<Arguments> typeConversionCases() {
        return Stream.of(
                // Int to other types
                Arguments.of("I2L", 42, int.class, Opcodes.I2L, 42L, long.class),
                Arguments.of("I2F", 42, int.class, Opcodes.I2F, 42.0f, float.class),
                Arguments.of("I2D", 42, int.class, Opcodes.I2D, 42.0, double.class),

                // Long to other types
                Arguments.of("L2I", 42L, long.class, Opcodes.L2I, 42, int.class),
                Arguments.of("L2F", 42L, long.class, Opcodes.L2F, 42.0f, float.class),
                Arguments.of("L2D", 42L, long.class, Opcodes.L2D, 42.0, double.class),

                // Float to other types (note: truncation for integer conversions)
                Arguments.of("F2I", 42.5f, float.class, Opcodes.F2I, 42, int.class),
                Arguments.of("F2L", 42.5f, float.class, Opcodes.F2L, 42L, long.class),
                Arguments.of("F2D", 42.5f, float.class, Opcodes.F2D, 42.5, double.class),

                // Double to other types (note: truncation for integer conversions)
                Arguments.of("D2I", 42.9, double.class, Opcodes.D2I, 42, int.class),
                Arguments.of("D2L", 42.9, double.class, Opcodes.D2L, 42L, long.class),
                Arguments.of("D2F", 42.5, double.class, Opcodes.D2F, 42.5f, float.class));
    }
}
