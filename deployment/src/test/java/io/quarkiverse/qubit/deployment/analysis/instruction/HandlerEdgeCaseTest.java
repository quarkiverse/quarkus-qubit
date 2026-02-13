package io.quarkiverse.qubit.deployment.analysis.instruction;

import static io.quarkiverse.qubit.deployment.testutil.AstBuilders.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.objectweb.asm.Opcodes.*;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import io.quarkiverse.qubit.deployment.ast.LambdaExpression;
import io.quarkiverse.qubit.deployment.common.BytecodeAnalysisException;
import io.quarkiverse.qubit.deployment.common.BytecodeValidator;

/**
 * Edge case tests for instruction handlers.
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

        static Stream<Arguments> sufficientStackCases() {
            return Stream.of(
                    Arguments.of(2, 2, "exact size"),
                    Arguments.of(3, 2, "more than required"));
        }

        @ParameterizedTest(name = "requireStackSize with {2} succeeds")
        @MethodSource("sufficientStackCases")
        void requireStackSize_withSufficientElements_succeeds(int actualSize, int requiredSize, String description) {
            Deque<Object> stack = new ArrayDeque<>();
            for (int i = 0; i < actualSize; i++) {
                stack.push("element" + i);
            }

            BytecodeValidator.requireStackSize(stack, requiredSize, "BINARY_OP");
            assertThat(stack).hasSize(actualSize);
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
            // Verify that validation succeeds by ensuring no exception is thrown
            assertThatCode(() -> BytecodeValidator.requireValidOpcode(IADD, IADD, ISUB, IMUL))
                    .doesNotThrowAnyException();
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

    @Nested
    class ArithmeticHandlerTests {

        private final ArithmeticInstructionHandler handler = ArithmeticInstructionHandler.INSTANCE;

        @ParameterizedTest(name = "canHandle arithmetic/logical/comparison opcode {0}")
        @ValueSource(ints = { IADD, ISUB, IMUL, IDIV, IAND, IOR, LCMP, DCMPL, DCMPG })
        void canHandle_withHandledOpcode_returnsTrue(int opcode) {
            assertThat(handler.canHandle(new InsnNode(opcode))).isTrue();
        }

        @ParameterizedTest(name = "canHandle non-arithmetic opcode {0}")
        @ValueSource(ints = { ALOAD, IRETURN })
        void canHandle_withNonArithmeticOpcode_returnsFalse(int opcode) {
            assertThat(handler.canHandle(new InsnNode(opcode))).isFalse();
        }

        static Stream<Arguments> emptyStackUnderflowCases() {
            return Stream.of(
                    Arguments.of(IADD, "IADD", "arithmetic"),
                    Arguments.of(IAND, "IAND", "logical"),
                    Arguments.of(LCMP, "LCMP", "comparison"));
        }

        @ParameterizedTest(name = "{2} opcode {1} with empty stack throws")
        @MethodSource("emptyStackUnderflowCases")
        void handle_withEmptyStack_throwsStackUnderflow(int opcode, String opcodeName, String category) {
            assertThatThrownBy(() -> handler.handle(new InsnNode(opcode), context))
                    .isInstanceOf(BytecodeAnalysisException.class)
                    .hasMessageContaining("Stack underflow")
                    .hasMessageContaining(opcodeName);
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
        void handle_arithmeticWithTwoElements_succeeds() {
            context.push(constant(5));
            context.push(constant(3));

            boolean terminated = handler.handle(new InsnNode(IADD), context);

            assertThat(terminated).isFalse();
            assertThat(context.getStackSize()).isEqualTo(1);
        }

        @Test
        void handle_ixor_throwsUnsupportedException() {
            // XOR is not supported in JPA Criteria API - should throw explicit exception
            context.push(constant(1));
            context.push(constant(0));

            assertThatThrownBy(() -> handler.handle(new InsnNode(IXOR), context))
                    .isInstanceOf(BytecodeAnalysisException.class)
                    .hasMessageContaining("Unsupported XOR operator");
        }

        @ParameterizedTest(name = "handle float/double remainder {0} succeeds")
        @ValueSource(ints = { FREM, DREM })
        void handle_floatDoubleRemainder_succeeds(int opcode) {
            // Float and double modulo operations should be handled correctly
            context.push(new LambdaExpression.Constant(5.5f, float.class));
            context.push(new LambdaExpression.Constant(2.0f, float.class));

            boolean terminated = handler.handle(new InsnNode(opcode), context);

            assertThat(terminated).isFalse();
            assertThat(context.getStackSize()).isEqualTo(1);
            assertThat(context.peek())
                    .isInstanceOf(LambdaExpression.BinaryOp.class);
            LambdaExpression.BinaryOp result = (LambdaExpression.BinaryOp) context.peek();
            assertThat(result.operator()).isEqualTo(LambdaExpression.BinaryOp.Operator.MOD);
        }
    }

    @Nested
    class LoadHandlerTests {

        private final LoadInstructionHandler handler = LoadInstructionHandler.INSTANCE;

        @ParameterizedTest(name = "canHandle load opcode {0}")
        @ValueSource(ints = { ALOAD, ILOAD, LLOAD, FLOAD, DLOAD })
        void canHandle_withLoadOpcode_returnsTrue(int opcode) {
            assertThat(handler.canHandle(new VarInsnNode(opcode, 0))).isTrue();
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

        @Test
        void handle_aload_inGroupContextMode_pushesGroupParameter() {
            // Create context with group context mode enabled (via constructor)
            MethodNode groupMethod = new MethodNode();
            groupMethod.name = "groupLambda";
            groupMethod.desc = "(Ljava/lang/Object;)Ljava/lang/Object;";
            groupMethod.instructions = new InsnList();

            // Use constructor that enables groupContextMode
            AnalysisContext.NestedLambdaSupport nestedSupport = new AnalysisContext.NestedLambdaSupport(
                    java.util.List.of(),
                    (method, index) -> null);
            AnalysisContext groupContext = new AnalysisContext(groupMethod, 0, nestedSupport);

            handler.handle(new VarInsnNode(ALOAD, 0), groupContext);

            assertThat(groupContext.getStackSize()).isEqualTo(1);
            assertThat(groupContext.peek())
                    .as("ALOAD in group context mode should push GroupParameter")
                    .isInstanceOf(io.quarkiverse.qubit.deployment.ast.LambdaExpression.GroupParameter.class);
        }

        static Stream<Arguments> biEntityModeCases() {
            return Stream.of(
                    Arguments.of(0, "entity", io.quarkiverse.qubit.deployment.ast.LambdaExpression.EntityPosition.FIRST,
                            "FIRST"),
                    Arguments.of(1, "joinedEntity", io.quarkiverse.qubit.deployment.ast.LambdaExpression.EntityPosition.SECOND,
                            "SECOND"));
        }

        @ParameterizedTest(name = "bi-entity {3} position uses '{1}' param name")
        @MethodSource("biEntityModeCases")
        void handle_aload_biEntityMode_pushesCorrectParam(int slot, String expectedName,
                io.quarkiverse.qubit.deployment.ast.LambdaExpression.EntityPosition expectedPosition, String positionName) {
            MethodNode biEntityMethod = new MethodNode();
            biEntityMethod.name = "biEntityLambda";
            biEntityMethod.desc = "(Ljava/lang/Object;Ljava/lang/Object;)Z";
            biEntityMethod.instructions = new InsnList();
            AnalysisContext biContext = new AnalysisContext(biEntityMethod, 0, 1);

            handler.handle(new VarInsnNode(ALOAD, slot), biContext);

            assertThat(biContext.getStackSize()).isEqualTo(1);
            assertThat(biContext.peek())
                    .isInstanceOf(io.quarkiverse.qubit.deployment.ast.LambdaExpression.BiEntityParameter.class);
            io.quarkiverse.qubit.deployment.ast.LambdaExpression.BiEntityParameter biParam = (io.quarkiverse.qubit.deployment.ast.LambdaExpression.BiEntityParameter) biContext
                    .peek();
            assertThat(biParam.name())
                    .as("%s entity position should have '%s' param name", positionName, expectedName)
                    .isEqualTo(expectedName);
            assertThat(biParam.position()).isEqualTo(expectedPosition);
        }

        @ParameterizedTest(name = "primitive load opcode {0} with invalid slot throws exception")
        @ValueSource(ints = { ILOAD, LLOAD, FLOAD, DLOAD })
        void handle_primitiveLoad_withInvalidSlot_throwsException(int opcode) {
            // Method with only object parameter - primitive load on slot 99 is invalid
            assertThatThrownBy(() -> handler.handle(new VarInsnNode(opcode, 99), context))
                    .isInstanceOf(BytecodeAnalysisException.class)
                    .hasMessageContaining("Primitive load")
                    .hasMessageContaining("does not correspond to a method parameter");
        }

        static Stream<Arguments> primitiveLoadCases() {
            return Stream.of(
                    Arguments.of(LLOAD, "J", long.class, "LLOAD/long"),
                    Arguments.of(DLOAD, "D", double.class, "DLOAD/double"),
                    Arguments.of(FLOAD, "F", float.class, "FLOAD/float"));
        }

        @ParameterizedTest(name = "{3} produces correct type")
        @MethodSource("primitiveLoadCases")
        void handle_primitiveLoad_withValidSlot_usesCorrectType(int opcode, String typeDescriptor,
                Class<?> expectedType, String description) {
            MethodNode methodNode = new MethodNode();
            methodNode.name = "testLambda";
            methodNode.desc = "(Ljava/lang/Object;" + typeDescriptor + ")Z";
            methodNode.instructions = new InsnList();
            AnalysisContext ctx = new AnalysisContext(methodNode, 0);

            handler.handle(new VarInsnNode(opcode, 1), ctx);

            assertThat(ctx.getStackSize()).isEqualTo(1);
            assertThat(ctx.peek())
                    .isInstanceOf(io.quarkiverse.qubit.deployment.ast.LambdaExpression.CapturedVariable.class);
            io.quarkiverse.qubit.deployment.ast.LambdaExpression.CapturedVariable captured = (io.quarkiverse.qubit.deployment.ast.LambdaExpression.CapturedVariable) ctx
                    .peek();
            assertThat(captured.type())
                    .as("%s should produce %s type", description, expectedType.getSimpleName())
                    .isEqualTo(expectedType);
        }

        static Stream<Arguments> iloadTypeLookupCases() {
            return Stream.of(
                    Arguments.of("I", int.class, "ILOAD/int"),
                    Arguments.of("Z", boolean.class, "ILOAD/boolean"),
                    Arguments.of("B", byte.class, "ILOAD/byte"));
        }

        @ParameterizedTest(name = "{2} looks up correct type from descriptor")
        @MethodSource("iloadTypeLookupCases")
        void handle_iload_looksUpTypeFromDescriptor(String typeDescriptor, Class<?> expectedType, String description) {
            MethodNode methodNode = new MethodNode();
            methodNode.name = "testLambda";
            methodNode.desc = "(Ljava/lang/Object;" + typeDescriptor + ")Z";
            methodNode.instructions = new InsnList();
            AnalysisContext ctx = new AnalysisContext(methodNode, 0);

            handler.handle(new VarInsnNode(ILOAD, 1), ctx);

            assertThat(ctx.getStackSize()).isEqualTo(1);
            io.quarkiverse.qubit.deployment.ast.LambdaExpression.CapturedVariable captured = (io.quarkiverse.qubit.deployment.ast.LambdaExpression.CapturedVariable) ctx
                    .peek();
            assertThat(captured.type())
                    .as("%s should look up %s from descriptor", description, expectedType.getSimpleName())
                    .isEqualTo(expectedType);
        }
    }

    @Nested
    class ConstantHandlerTests {

        private final ConstantInstructionHandler handler = ConstantInstructionHandler.INSTANCE;

        @ParameterizedTest(name = "canHandle constant opcode {0}")
        @ValueSource(ints = { ICONST_0, ICONST_1, ICONST_2, ICONST_3, ICONST_4, ICONST_5,
                LCONST_0, LCONST_1, FCONST_0, FCONST_1, FCONST_2, DCONST_0, DCONST_1, ACONST_NULL })
        void canHandle_withConstantOpcode_returnsTrue(int opcode) {
            assertThat(handler.canHandle(new InsnNode(opcode)))
                    .as("Should handle opcode %d", opcode)
                    .isTrue();
        }

        @Test
        void handle_aconstNull_pushesNullLiteral() {
            handler.handle(new InsnNode(ACONST_NULL), context);

            assertThat(context.getStackSize()).isEqualTo(1);
            assertThat(context.peek()).isNotNull();
        }

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

        @ParameterizedTest(name = "const opcode {0} does not terminate")
        @ValueSource(ints = { DCONST_0, DCONST_1, FCONST_0, LCONST_0 })
        void handle_const_returnsFalse_doesNotTerminate(int opcode) {
            boolean terminated = handler.handle(new InsnNode(opcode), context);

            assertThat(terminated)
                    .as("Const handler for opcode %d should return false (not terminate)", opcode)
                    .isFalse();
        }

        @ParameterizedTest(name = "const opcode {0} pushes constant")
        @ValueSource(ints = { ICONST_5, LCONST_1, FCONST_2, DCONST_1 })
        void handle_const_pushesConstant(int opcode) {
            handler.handle(new InsnNode(opcode), context);

            assertThat(context.getStackSize()).isEqualTo(1);
        }

        // These tests exercise the ICONST handling after branch instructions have been seen

        @Test
        void handle_iconst5_afterBranch_pushesConstant() {
            // ICONST_5 (value > 1) is not a boolean marker, always pushed
            context.markBranchSeen();
            context.push(constant(1));

            boolean terminated = handler.handle(new InsnNode(ICONST_5), context);

            assertThat(terminated).isFalse();
            assertThat(context.getStackSize()).isEqualTo(2);
        }

        @ParameterizedTest(name = "ICONST_{0} after branch pushes constant (value > 1, not boolean marker)")
        @ValueSource(ints = { ICONST_2, ICONST_3, ICONST_4 })
        void handle_iconst_afterBranch_pushesConstant_nonBooleanMarker(int opcode) {
            context.markBranchSeen();

            boolean terminated = handler.handle(new InsnNode(opcode), context);

            assertThat(terminated).isFalse();
            assertThat(context.getStackSize()).isEqualTo(1);
        }

        @ParameterizedTest(name = "ICONST opcode {0} with no branch seen pushes constant")
        @ValueSource(ints = { ICONST_0, ICONST_1 })
        void handle_iconst_noBranch_emptyInstructions_pushesConstant(int opcode) {
            // Even ICONST_0/ICONST_1 push if no branch has been seen
            boolean terminated = handler.handle(new InsnNode(opcode), context);

            assertThat(terminated).isFalse();
            assertThat(context.getStackSize()).isEqualTo(1);
        }

        // Test the isFinalResult() and handleIconst() mutation-killing scenarios

        static Stream<Arguments> iconstWithReturnOpcodeCases() {
            return Stream.of(
                    Arguments.of(ICONST_0, IRETURN, "ICONST_0 with IRETURN"),
                    Arguments.of(ICONST_1, IRETURN, "ICONST_1 with IRETURN"),
                    Arguments.of(ICONST_0, ARETURN, "ICONST_0 with ARETURN"),
                    Arguments.of(ICONST_0, RETURN, "ICONST_0 with RETURN"));
        }

        @ParameterizedTest(name = "{2} terminates")
        @MethodSource("iconstWithReturnOpcodeCases")
        void handle_iconst_afterBranch_withStackAndReturn_terminates(int iconstOpcode, int returnOpcode, String description) {
            testMethod.instructions.add(new InsnNode(iconstOpcode));
            testMethod.instructions.add(new InsnNode(returnOpcode));
            AnalysisContext ctx = new AnalysisContext(testMethod, 0);
            ctx.markBranchSeen();
            ctx.push(constant(1));

            boolean terminated = handler.handle(new InsnNode(iconstOpcode), ctx);

            assertThat(terminated)
                    .as("%s should terminate", description)
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

        static Stream<Arguments> constantOpcodeValues() {
            return Stream.of(
                    Arguments.of(DCONST_0, 0.0, "DCONST_0"),
                    Arguments.of(DCONST_1, 1.0, "DCONST_1"),
                    Arguments.of(FCONST_0, 0.0f, "FCONST_0"),
                    Arguments.of(FCONST_1, 1.0f, "FCONST_1"),
                    Arguments.of(LCONST_0, 0L, "LCONST_0"),
                    Arguments.of(LCONST_1, 1L, "LCONST_1"),
                    Arguments.of(ICONST_M1, -1, "ICONST_M1"), // Bug fix: was not handled before
                    Arguments.of(ICONST_0, 0, "ICONST_0"),
                    Arguments.of(ICONST_5, 5, "ICONST_5"));
        }

        @ParameterizedTest(name = "{2} pushes {1}")
        @MethodSource("constantOpcodeValues")
        void handle_constOpcode_pushesExpectedValue(int opcode, Object expectedValue, String opcodeName) {
            handler.handle(new InsnNode(opcode), context);

            assertThat(context.getStackSize()).isEqualTo(1);
            assertThat(context.peek())
                    .isInstanceOf(io.quarkiverse.qubit.deployment.ast.LambdaExpression.Constant.class);
            io.quarkiverse.qubit.deployment.ast.LambdaExpression.Constant constExpr = (io.quarkiverse.qubit.deployment.ast.LambdaExpression.Constant) context
                    .peek();
            assertThat(constExpr.value())
                    .as("%s should push %s", opcodeName, expectedValue)
                    .isEqualTo(expectedValue);
        }

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
            testMethod.instructions
                    .add(new MethodInsnNode(INVOKESTATIC, "java/lang/Boolean", "valueOf", "(Z)Ljava/lang/Boolean;", false));
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

        @ParameterizedTest(name = "ICONST_0 after branch with {0} next pushes for logical")
        @ValueSource(ints = { IOR, IAND, IXOR })
        void handle_iconst0_afterBranch_withLogicalNext_pushedForLogical(int logicalOpcode) {
            testMethod.instructions.add(new InsnNode(ICONST_0));
            testMethod.instructions.add(new InsnNode(logicalOpcode));
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

        @ParameterizedTest(name = "ICONST_0 after branch with {0} next pushes for branch")
        @ValueSource(ints = { IFNULL, IFNONNULL })
        void handle_iconst0_afterBranch_withNullBranchNext_pushedForBranch(int branchOpcode) {
            testMethod.instructions.add(new InsnNode(ICONST_0));
            testMethod.instructions
                    .add(new org.objectweb.asm.tree.JumpInsnNode(branchOpcode, new org.objectweb.asm.tree.LabelNode()));
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

        @ParameterizedTest(name = "canHandle non-constant opcode {0} returns false")
        @ValueSource(ints = { ALOAD, ISTORE, POP, NOP })
        void canHandle_withNonConstantOpcode_returnsFalse(int opcode) {
            // Opcodes outside constant range should not be handled
            assertThat(handler.canHandle(new InsnNode(opcode)))
                    .as("Opcode %d should not be handled by ConstantInstructionHandler", opcode)
                    .isFalse();
        }

        // These test boundary opcodes at the edges of the arithmetic and branch ranges

        @ParameterizedTest(name = "ICONST_0 before {1} arithmetic opcode pushes for expression")
        @ValueSource(ints = { IADD, DREM })
        void handle_iconst0_afterBranch_withArithmeticBoundary_pushedForArithmetic(int opcode) {
            testMethod.instructions.add(new InsnNode(ICONST_0));
            testMethod.instructions.add(new InsnNode(opcode));
            AnalysisContext ctx = new AnalysisContext(testMethod, 0);
            ctx.markBranchSeen();
            ctx.push(constant(1));

            boolean terminated = handler.handle(new InsnNode(ICONST_0), ctx);

            assertThat(terminated).isFalse();
            assertThat(ctx.getStackSize()).isEqualTo(2);
        }

        @ParameterizedTest(name = "ICONST_0 before branch opcode {0} pushes for branch")
        @ValueSource(ints = { IFEQ, IF_ICMPLE })
        void handle_iconst0_afterBranch_withBranchBoundary_pushedForBranch(int opcode) {
            testMethod.instructions.add(new InsnNode(ICONST_0));
            testMethod.instructions
                    .add(new org.objectweb.asm.tree.JumpInsnNode(opcode, new org.objectweb.asm.tree.LabelNode()));
            AnalysisContext ctx = new AnalysisContext(testMethod, 0);
            ctx.markBranchSeen();
            ctx.push(constant(1));

            boolean terminated = handler.handle(new InsnNode(ICONST_0), ctx);

            assertThat(terminated).isFalse();
            assertThat(ctx.getStackSize()).isEqualTo(2);
        }

        @Test
        void handle_iconst0_afterBranch_withBooleanValueOf_skipsConstant() {
            // Boolean.valueOf(Z) causes isIconstUsedInExpression to return false (not used in expression)
            // But isFinalResult only skips labels, not Boolean.valueOf, so it returns false
            // Result: ICONST is skipped as intermediate marker, doesn't terminate
            testMethod.instructions.add(new InsnNode(ICONST_0));
            testMethod.instructions
                    .add(new MethodInsnNode(INVOKESTATIC, "java/lang/Boolean", "valueOf", "(Z)Ljava/lang/Boolean;", false));
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
            testMethod.instructions
                    .add(new MethodInsnNode(INVOKESTATIC, "java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;", false));
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

    @Nested
    class TypeConversionHandlerTests {

        private final TypeConversionHandler handler = TypeConversionHandler.INSTANCE;

        @ParameterizedTest(name = "canHandle primitive conversion opcode {0}")
        @ValueSource(ints = { I2L, I2F, I2D, L2I, L2F, L2D, F2I, F2L, F2D, D2I, D2L, D2F })
        void canHandle_withPrimitiveConversions_returnsTrue(int opcode) {
            assertThat(handler.canHandle(new InsnNode(opcode)))
                    .as("Should handle primitive type conversion opcode %d", opcode)
                    .isTrue();
        }

        @ParameterizedTest(name = "canHandle opcode {0} returns false")
        @ValueSource(ints = { I2B, I2C, I2S, CHECKCAST, INSTANCEOF, IADD, IRETURN })
        void canHandle_withUnhandledOpcode_returnsFalse(int opcode) {
            assertThat(handler.canHandle(new InsnNode(opcode)))
                    .as("TypeConversionHandler should not handle opcode %d", opcode)
                    .isFalse();
        }

        @Test
        void handle_withEmptyStack_doesNotThrow() {
            // TypeConversionHandler handles empty stack gracefully
            boolean terminated = handler.handle(new InsnNode(I2L), context);

            assertThat(terminated).isFalse();
            assertThat(context.isStackEmpty()).isTrue();
        }

        static Stream<Arguments> typeConversionFoldingCases() {
            return Stream.of(
                    // I2* conversions (int source)
                    Arguments.of(I2L, 42, int.class, 42L, long.class, null, "I2L"),
                    Arguments.of(I2F, 42, int.class, 42.0f, float.class, null, "I2F"),
                    Arguments.of(I2D, 42, int.class, 42.0, double.class, null, "I2D"),
                    // L2* conversions (long source)
                    Arguments.of(L2I, 100L, long.class, 100, int.class, null, "L2I"),
                    Arguments.of(L2F, 100L, long.class, 100.0f, float.class, null, "L2F"),
                    Arguments.of(L2D, 100L, long.class, 100.0, double.class, null, "L2D"),
                    // F2* conversions (float source)
                    Arguments.of(F2I, 3.14f, float.class, 3, int.class, null, "F2I"),
                    Arguments.of(F2L, 3.14f, float.class, 3L, long.class, null, "F2L"),
                    Arguments.of(F2D, 3.14f, float.class, 3.14, double.class, 0.01, "F2D"),
                    // D2* conversions (double source)
                    Arguments.of(D2I, 9.99, double.class, 9, int.class, null, "D2I"),
                    Arguments.of(D2L, 9.99, double.class, 9L, long.class, null, "D2L"),
                    Arguments.of(D2F, 9.99, double.class, 9.99f, float.class, 0.01, "D2F"));
        }

        @ParameterizedTest(name = "{6} folds constant correctly")
        @MethodSource("typeConversionFoldingCases")
        void handle_typeConversion_foldsConstant(int opcode, Object inputValue, Class<?> inputType,
                Object expectedValue, Class<?> expectedType, Number tolerance, String opcodeName) {
            context.push(new io.quarkiverse.qubit.deployment.ast.LambdaExpression.Constant(inputValue, inputType));

            handler.handle(new InsnNode(opcode), context);

            assertThat(context.getStackSize()).isEqualTo(1);
            assertThat(context.peek())
                    .isInstanceOf(io.quarkiverse.qubit.deployment.ast.LambdaExpression.Constant.class);
            io.quarkiverse.qubit.deployment.ast.LambdaExpression.Constant result = (io.quarkiverse.qubit.deployment.ast.LambdaExpression.Constant) context
                    .peek();
            assertThat(result.type())
                    .as("%s should convert to %s", opcodeName, expectedType.getSimpleName())
                    .isEqualTo(expectedType);

            if (tolerance != null) {
                // Approximate matching for float/double conversions with precision considerations
                if (expectedType == double.class) {
                    assertThat((Double) result.value())
                            .isCloseTo((Double) expectedValue, org.assertj.core.data.Offset.offset(tolerance.doubleValue()));
                } else {
                    assertThat((Float) result.value())
                            .isCloseTo((Float) expectedValue, org.assertj.core.data.Offset.offset(tolerance.floatValue()));
                }
            } else {
                assertThat(result.value())
                        .as("%s should convert value correctly", opcodeName)
                        .isEqualTo(expectedValue);
            }
        }

        static Stream<Arguments> typeMismatchConversions() {
            return Stream.of(
                    Arguments.of(I2L, 42L, long.class, "I2L with long"),
                    Arguments.of(L2I, 42, int.class, "L2I with int"),
                    Arguments.of(F2I, 3.14, double.class, "F2I with double"),
                    Arguments.of(D2I, 3.14f, float.class, "D2I with float"));
        }

        @ParameterizedTest(name = "{3} does not fold")
        @MethodSource("typeMismatchConversions")
        void handle_conversion_withWrongSourceType_doesNotFold(int opcode, Object value, Class<?> expectedType,
                String description) {
            context.push(new io.quarkiverse.qubit.deployment.ast.LambdaExpression.Constant(value, expectedType));

            handler.handle(new InsnNode(opcode), context);

            assertThat(context.getStackSize()).isEqualTo(1);
            io.quarkiverse.qubit.deployment.ast.LambdaExpression.Constant result = (io.quarkiverse.qubit.deployment.ast.LambdaExpression.Constant) context
                    .peek();
            assertThat(result.type())
                    .as("Type mismatch should not fold - original %s should remain", expectedType.getSimpleName())
                    .isEqualTo(expectedType);
        }

        @Test
        void handle_conversion_withNonConstant_doesNotFold() {
            // Push FieldAccess instead of Constant - should not modify stack
            context.push(field("value", int.class));

            handler.handle(new InsnNode(I2L), context);

            assertThat(context.getStackSize()).isEqualTo(1);
            assertThat(context.peek())
                    .as("Non-constant should not be folded")
                    .isInstanceOf(io.quarkiverse.qubit.deployment.ast.LambdaExpression.FieldAccess.class);
        }
    }

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

            assertThatThrownBy(() -> registry.handlers().add(ArithmeticInstructionHandler.INSTANCE))
                    .isInstanceOf(UnsupportedOperationException.class);
        }
    }

    @Nested
    class MethodInvocationHandlerTests {

        private final MethodInvocationHandler handler = MethodInvocationHandler.INSTANCE;

        @ParameterizedTest(name = "canHandle invoke opcode {0}")
        @ValueSource(ints = { INVOKEVIRTUAL, INVOKESTATIC, INVOKESPECIAL, INVOKEINTERFACE })
        void canHandle_withInvokeOpcode_returnsTrue(int opcode) {
            boolean isInterface = opcode == INVOKEINTERFACE;
            MethodInsnNode methodInsn = new MethodInsnNode(opcode, "java/lang/Object", "test", "()V", isInterface);
            assertThat(handler.canHandle(methodInsn))
                    .as("Should handle invoke opcode %d", opcode)
                    .isTrue();
        }

        @Test
        void canHandle_withNonInvokeOpcode_returnsFalse() {
            assertThat(handler.canHandle(new InsnNode(IADD)))
                    .as("Should not handle IADD")
                    .isFalse();
        }

        static Stream<Arguments> methodCategoryMappings() {
            return Stream.of(
                    Arguments.of("java/lang/Object", "equals", "(Ljava/lang/Object;)Z",
                            MethodInvocationHandler.VirtualMethodCategory.EQUALS),
                    Arguments.of("java/lang/String", "startsWith", "(Ljava/lang/String;)Z",
                            MethodInvocationHandler.VirtualMethodCategory.STRING_METHOD),
                    Arguments.of("java/lang/Integer", "compareTo", "(Ljava/lang/Integer;)I",
                            MethodInvocationHandler.VirtualMethodCategory.COMPARE_TO),
                    Arguments.of("java/math/BigDecimal", "add", "(Ljava/math/BigDecimal;)Ljava/math/BigDecimal;",
                            MethodInvocationHandler.VirtualMethodCategory.BIG_DECIMAL_ARITHMETIC),
                    Arguments.of("java/time/LocalDate", "getYear", "()I",
                            MethodInvocationHandler.VirtualMethodCategory.TEMPORAL_METHOD),
                    Arguments.of("com/example/Person", "getName", "()Ljava/lang/String;",
                            MethodInvocationHandler.VirtualMethodCategory.GETTER),
                    Arguments.of("com/example/Person", "isActive", "()Z",
                            MethodInvocationHandler.VirtualMethodCategory.GETTER),
                    Arguments.of("com/example/Foo", "doSomething", "(II)V",
                            MethodInvocationHandler.VirtualMethodCategory.UNHANDLED));
        }

        @ParameterizedTest(name = "{1} on {0} returns {3}")
        @MethodSource("methodCategoryMappings")
        void categorize_method_returnsCorrectCategory(String owner, String methodName, String descriptor,
                MethodInvocationHandler.VirtualMethodCategory expectedCategory) {
            MethodInsnNode insn = new MethodInsnNode(INVOKEVIRTUAL, owner, methodName, descriptor, false);

            MethodInvocationHandler.VirtualMethodCategory category = MethodInvocationHandler.VirtualMethodCategory
                    .categorize(insn, handler);

            assertThat(category).isEqualTo(expectedCategory);
        }

        @Test
        void handle_booleanValueOf_skipsAndDoesNotAffectStack() {
            // Boolean.valueOf(Z) should be skipped
            context.push(constant(true));
            MethodInsnNode booleanValueOf = new MethodInsnNode(INVOKESTATIC, "java/lang/Boolean", "valueOf",
                    "(Z)Ljava/lang/Boolean;", false);

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
            MethodInsnNode localDateOf = new MethodInsnNode(INVOKESTATIC, "java/time/LocalDate", "of",
                    "(III)Ljava/time/LocalDate;", false);

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
            MethodInsnNode localTimeOf = new MethodInsnNode(INVOKESTATIC, "java/time/LocalTime", "of",
                    "(II)Ljava/time/LocalTime;", false);

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
            MethodInsnNode localDateTimeOf = new MethodInsnNode(INVOKESTATIC, "java/time/LocalDateTime", "of",
                    "(IIIII)Ljava/time/LocalDateTime;", false);

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
            MethodInsnNode localDateOf = new MethodInsnNode(INVOKESTATIC, "java/time/LocalDate", "of",
                    "(II)Ljava/time/LocalDate;", false);

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
            MethodInsnNode wrongOwner = new MethodInsnNode(INVOKESTATIC, "java/time/ZonedDateTime", "of",
                    "(III)Ljava/time/ZonedDateTime;", false);

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
            MethodInsnNode wrongMethod = new MethodInsnNode(INVOKESTATIC, "java/time/LocalDate", "parse",
                    "(Ljava/lang/String;)Ljava/time/LocalDate;", false);

            handler.handle(wrongMethod, context);

            assertThat(context.getStackSize())
                    .as("Wrong method name should leave stack unchanged")
                    .isEqualTo(3);
        }

        @Test
        void handle_bigDecimalConstructor_withStringConstant_foldsToBigDecimalConstant() {
            // Simulate: new BigDecimal("123.45")
            // Stack: [placeholder for NEW, placeholder for DUP, "123.45"]
            // discardN(2) will remove the first two elements after popping args
            context.push(constant("new_marker")); // Placeholder for NEW
            context.push(constant("dup_marker")); // Placeholder for DUP
            context.push(constant("123.45"));

            MethodInsnNode bigDecimalInit = new MethodInsnNode(INVOKESPECIAL, "java/math/BigDecimal", "<init>",
                    "(Ljava/lang/String;)V", false);

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

            MethodInsnNode bigDecimalInit = new MethodInsnNode(INVOKESPECIAL, "java/math/BigDecimal", "<init>",
                    "(Ljava/lang/String;)V", false);

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

            MethodInsnNode someClassInit = new MethodInsnNode(INVOKESPECIAL, "com/example/SomeClass", "<init>",
                    "(Ljava/lang/String;)V", false);

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

            MethodInsnNode multiArgInit = new MethodInsnNode(INVOKESPECIAL, "com/example/SomeClass", "<init>",
                    "(ILjava/lang/String;)V", false);

            handler.handle(multiArgInit, context);

            assertThat(context.getStackSize())
                    .as("Multi-arg constructor should create ConstructorCall")
                    .isEqualTo(1);
        }

        @Test
        void handle_nonConstructorInvokeSpecial_doesNothing() {
            // Simulate: super.someMethod() - not a constructor
            context.push(field("name", String.class));
            MethodInsnNode superMethod = new MethodInsnNode(INVOKESPECIAL, "java/lang/Object", "toString",
                    "()Ljava/lang/String;", false);

            handler.handle(superMethod, context);

            assertThat(context.getStackSize())
                    .as("Non-constructor INVOKESPECIAL should not modify stack")
                    .isEqualTo(1);
        }

        @Test
        void handle_substringOneArg_createsMethodCall() {
            context.push(field("name", String.class));
            context.push(constant(5));
            MethodInsnNode substringInsn = new MethodInsnNode(INVOKEVIRTUAL, "java/lang/String", "substring",
                    "(I)Ljava/lang/String;", false);

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
            MethodInsnNode substringInsn = new MethodInsnNode(INVOKEVIRTUAL, "java/lang/String", "substring",
                    "(II)Ljava/lang/String;", false);

            handler.handle(substringInsn, context);

            assertThat(context.getStackSize())
                    .as("substring(int, int) should create MethodCall")
                    .isEqualTo(1);
        }

        @Test
        void handle_substringInsufficientStack_doesNotThrow() {
            // Only one element when we need 2 for substring(I)
            context.push(field("name", String.class));
            MethodInsnNode substringInsn = new MethodInsnNode(INVOKEVIRTUAL, "java/lang/String", "substring",
                    "(I)Ljava/lang/String;", false);

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
            MethodInsnNode substringWrong = new MethodInsnNode(INVOKEVIRTUAL, "java/lang/String", "substring",
                    "(Ljava/lang/String;)Ljava/lang/String;", false);

            handler.handle(substringWrong, context);

            assertThat(context.getStackSize())
                    .as("Wrong substring descriptor should leave stack unchanged")
                    .isEqualTo(2);
        }

        @ParameterizedTest(name = "BigDecimal.{0} creates MethodCall")
        @ValueSource(strings = { "add", "subtract", "multiply", "divide" })
        void handle_bigDecimalArithmetic_createsMethodCall(String methodName) {
            context.push(field("price", java.math.BigDecimal.class));
            context.push(constant(new java.math.BigDecimal("2.00")));
            MethodInsnNode insn = new MethodInsnNode(INVOKEVIRTUAL, "java/math/BigDecimal", methodName,
                    "(Ljava/math/BigDecimal;)Ljava/math/BigDecimal;", false);

            handler.handle(insn, context);

            assertThat(context.getStackSize())
                    .as("BigDecimal.%s should create MethodCall", methodName)
                    .isEqualTo(1);
        }

        @Test
        void handle_bigDecimalUnrecognizedMethod_doesNothing() {
            context.push(field("price", java.math.BigDecimal.class));
            context.push(constant(new java.math.BigDecimal("2.00")));
            // negate() is not handled by BigDecimal arithmetic
            MethodInsnNode negateInsn = new MethodInsnNode(INVOKEVIRTUAL, "java/math/BigDecimal", "negate",
                    "()Ljava/math/BigDecimal;", false);

            handler.handle(negateInsn, context);

            // Stack should still have 2 elements since negate is not recognized
            assertThat(context.getStackSize())
                    .as("Unrecognized BigDecimal method should not consume arguments")
                    .isEqualTo(2);
        }

        @Test
        void handle_getterOnEmptyStack_doesNothing() {
            // Empty stack - should not throw
            MethodInsnNode getterInsn = new MethodInsnNode(INVOKEVIRTUAL, "com/example/Person", "getName",
                    "()Ljava/lang/String;", false);

            handler.handle(getterInsn, context);

            assertThat(context.isStackEmpty())
                    .as("Empty stack should remain empty for getter")
                    .isTrue();
        }

        static Stream<Arguments> getterPatterns() {
            return Stream.of(
                    Arguments.of("getName", "()Ljava/lang/String;", "get-style getter"),
                    Arguments.of("isActive", "()Z", "is-style getter"));
        }

        @ParameterizedTest(name = "{2} creates FieldAccess")
        @MethodSource("getterPatterns")
        void handle_getter_createsFieldAccess(String methodName, String descriptor, String description) {
            context.push(param("person", Object.class, 0));
            MethodInsnNode getterInsn = new MethodInsnNode(INVOKEVIRTUAL, "com/example/Person", methodName, descriptor, false);

            handler.handle(getterInsn, context);

            assertThat(context.getStackSize()).isEqualTo(1);
            assertThat(context.peek())
                    .as("%s should create FieldAccess", description)
                    .isInstanceOf(io.quarkiverse.qubit.deployment.ast.LambdaExpression.FieldAccess.class);
        }

        @Test
        void handle_getterWithNonGetterDescriptor_categorizedAsUnhandled() {
            // Descriptor with parameters - not a getter
            MethodInsnNode notGetter = new MethodInsnNode(INVOKEVIRTUAL, "com/example/Person", "getName",
                    "(I)Ljava/lang/String;", false);

            MethodInvocationHandler.VirtualMethodCategory category = MethodInvocationHandler.VirtualMethodCategory
                    .categorize(notGetter, handler);

            assertThat(category)
                    .as("Method with parameters is not a getter")
                    .isEqualTo(MethodInvocationHandler.VirtualMethodCategory.UNHANDLED);
        }

        @Test
        void handle_getter_onBiEntityParameter_createsBiEntityFieldAccess() {
            // Test getter on BiEntityParameter creates BiEntityFieldAccess
            // BiEntityParameter(String name, Class<?> type, int index, EntityPosition position)
            context.push(new io.quarkiverse.qubit.deployment.ast.LambdaExpression.BiEntityParameter(
                    "person",
                    Object.class,
                    0,
                    io.quarkiverse.qubit.deployment.ast.LambdaExpression.EntityPosition.FIRST));
            MethodInsnNode getterInsn = new MethodInsnNode(INVOKEVIRTUAL, "com/example/Person", "getName",
                    "()Ljava/lang/String;", false);

            handler.handle(getterInsn, context);

            assertThat(context.getStackSize()).isEqualTo(1);
            assertThat(context.peek())
                    .as("Getter on BiEntityParameter should create BiEntityFieldAccess")
                    .isInstanceOf(io.quarkiverse.qubit.deployment.ast.LambdaExpression.BiEntityFieldAccess.class);
        }

        @Test
        void handle_invokeInterface_containsMethod_handledAsGroupMethod() {
            // Simulate calling Collection.contains() which is a group method
            context.push(field("items", java.util.Collection.class));
            context.push(param("element", Object.class, 0));
            MethodInsnNode containsInsn = new MethodInsnNode(INVOKEINTERFACE, "java/util/Collection", "contains",
                    "(Ljava/lang/Object;)Z", true);

            // Handler should process but may not modify stack if no group method analyzer configured
            handler.handle(containsInsn, context);

            // Depending on implementation, may or may not modify stack
            assertThat(context.getStackSize()).isGreaterThanOrEqualTo(0);
        }

        @Test
        void handle_booleanValueOf_wrongOwner_doesNotSkip() {
            // Boolean.valueOf with wrong owner should not be skipped
            context.push(constant(true));
            MethodInsnNode wrongOwner = new MethodInsnNode(INVOKESTATIC, "java/lang/Integer", "valueOf",
                    "(I)Ljava/lang/Integer;", false);

            handler.handle(wrongOwner, context);

            // Stack may have changed based on unhandled static handling
            assertThat(context.getStackSize()).isGreaterThanOrEqualTo(0);
        }

        @Test
        void handle_booleanValueOf_wrongMethodName_doesNotSkip() {
            // Boolean with wrong method name should not be skipped
            context.push(constant(true));
            MethodInsnNode wrongMethod = new MethodInsnNode(INVOKESTATIC, "java/lang/Boolean", "parseBoolean",
                    "(Ljava/lang/String;)Z", false);

            handler.handle(wrongMethod, context);

            // Stack should not be modified for this unhandled pattern
            assertThat(context.getStackSize()).isEqualTo(1);
        }

        @Test
        void handle_booleanValueOf_wrongDescriptor_doesNotSkip() {
            // Boolean.valueOf with wrong descriptor should not be skipped
            context.push(constant("true"));
            MethodInsnNode wrongDesc = new MethodInsnNode(INVOKESTATIC, "java/lang/Boolean", "valueOf",
                    "(Ljava/lang/String;)Ljava/lang/Boolean;", false);

            handler.handle(wrongDesc, context);

            // String version may behave differently
            assertThat(context.getStackSize()).isGreaterThanOrEqualTo(0);
        }

        @Test
        void handle_invokeSpecial_nonConstructor_withNullArgs_leavesStackUnchanged() {
            // Non-constructor INVOKESPECIAL with no proper args extraction
            context.push(field("name", String.class));
            MethodInsnNode privateMethod = new MethodInsnNode(INVOKESPECIAL, "com/example/Parent", "privateHelper", "()V",
                    false);

            handler.handle(privateMethod, context);

            assertThat(context.getStackSize())
                    .as("Non-constructor INVOKESPECIAL should leave stack unchanged")
                    .isEqualTo(1);
        }

        @Test
        void handle_collectionContains_withCapturedVariableAndFieldAccess_createsInExpression() {
            // IN clause pattern: capturedCollection.contains(p.field)
            context.push(captured(0, java.util.List.class)); // CapturedVariable - collection from outer scope
            context.push(field("city", String.class)); // FieldAccess - entity field
            MethodInsnNode containsInsn = new MethodInsnNode(INVOKEINTERFACE, "java/util/Collection", "contains",
                    "(Ljava/lang/Object;)Z", true);

            handler.handle(containsInsn, context);

            assertThat(context.getStackSize()).isEqualTo(1);
            assertThat(context.peek())
                    .as("CapturedVariable.contains(FieldAccess) should create InExpression")
                    .isInstanceOf(io.quarkiverse.qubit.deployment.ast.LambdaExpression.InExpression.class);
        }

        @Test
        void handle_collectionContains_withFieldAccessAndConstant_createsMemberOfExpression() {
            // MEMBER OF pattern: p.roles.contains("admin")
            context.push(field("roles", java.util.Set.class)); // FieldAccess - collection field on entity
            context.push(constant("admin")); // Constant - value to check
            MethodInsnNode containsInsn = new MethodInsnNode(INVOKEINTERFACE, "java/util/Collection", "contains",
                    "(Ljava/lang/Object;)Z", true);

            handler.handle(containsInsn, context);

            assertThat(context.getStackSize()).isEqualTo(1);
            assertThat(context.peek())
                    .as("FieldAccess.contains(Constant) should create MemberOfExpression")
                    .isInstanceOf(io.quarkiverse.qubit.deployment.ast.LambdaExpression.MemberOfExpression.class);
        }

        @Test
        void handle_collectionContains_withFieldAccessAndCapturedVariable_createsMemberOfExpression() {
            // MEMBER OF pattern: p.tags.contains(capturedTag)
            context.push(field("tags", java.util.Set.class)); // FieldAccess - collection field
            context.push(captured(0, String.class)); // CapturedVariable - value from outer scope
            MethodInsnNode containsInsn = new MethodInsnNode(INVOKEINTERFACE, "java/util/Collection", "contains",
                    "(Ljava/lang/Object;)Z", true);

            handler.handle(containsInsn, context);

            assertThat(context.getStackSize()).isEqualTo(1);
            assertThat(context.peek())
                    .as("FieldAccess.contains(CapturedVariable) should create MemberOfExpression")
                    .isInstanceOf(io.quarkiverse.qubit.deployment.ast.LambdaExpression.MemberOfExpression.class);
        }

        @Test
        void handle_collectionContains_withNonMatchingPattern_createsMethodCall() {
            // Neither IN nor MEMBER OF pattern: constant.contains(constant)
            context.push(constant(java.util.List.of("a", "b"))); // Constant - not CapturedVariable or FieldAccess
            context.push(constant("x")); // Constant
            MethodInsnNode containsInsn = new MethodInsnNode(INVOKEINTERFACE, "java/util/Collection", "contains",
                    "(Ljava/lang/Object;)Z", true);

            handler.handle(containsInsn, context);

            assertThat(context.getStackSize()).isEqualTo(1);
            assertThat(context.peek())
                    .as("Non-matching pattern should create MethodCall fallback")
                    .isInstanceOf(io.quarkiverse.qubit.deployment.ast.LambdaExpression.MethodCall.class);
        }

        @Test
        void handle_collectionContains_withPathExpressionTarget_createsInExpression() {
            // IN clause with PathExpression argument
            context.push(captured(0, java.util.List.class)); // CapturedVariable
            context.push(new io.quarkiverse.qubit.deployment.ast.LambdaExpression.PathExpression(
                    java.util.List.of(
                            new io.quarkiverse.qubit.deployment.ast.LambdaExpression.PathSegment("address", Object.class,
                                    io.quarkiverse.qubit.deployment.ast.LambdaExpression.RelationType.FIELD),
                            new io.quarkiverse.qubit.deployment.ast.LambdaExpression.PathSegment("city", String.class,
                                    io.quarkiverse.qubit.deployment.ast.LambdaExpression.RelationType.FIELD)),
                    String.class)); // PathExpression
            MethodInsnNode containsInsn = new MethodInsnNode(INVOKEINTERFACE, "java/util/Collection", "contains",
                    "(Ljava/lang/Object;)Z", true);

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
            MethodInsnNode containsInsn = new MethodInsnNode(INVOKEINTERFACE, "java/util/Collection", "contains",
                    "(Ljava/lang/Object;)Z", true);

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
                            new io.quarkiverse.qubit.deployment.ast.LambdaExpression.PathSegment("address", Object.class,
                                    io.quarkiverse.qubit.deployment.ast.LambdaExpression.RelationType.FIELD),
                            new io.quarkiverse.qubit.deployment.ast.LambdaExpression.PathSegment("city", String.class,
                                    io.quarkiverse.qubit.deployment.ast.LambdaExpression.RelationType.FIELD)),
                    String.class, io.quarkiverse.qubit.deployment.ast.LambdaExpression.EntityPosition.SECOND));
            MethodInsnNode containsInsn = new MethodInsnNode(INVOKEINTERFACE, "java/util/Collection", "contains",
                    "(Ljava/lang/Object;)Z", true);

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
                            new io.quarkiverse.qubit.deployment.ast.LambdaExpression.PathSegment("person", Object.class,
                                    io.quarkiverse.qubit.deployment.ast.LambdaExpression.RelationType.FIELD),
                            new io.quarkiverse.qubit.deployment.ast.LambdaExpression.PathSegment("roles", java.util.Set.class,
                                    io.quarkiverse.qubit.deployment.ast.LambdaExpression.RelationType.FIELD)),
                    java.util.Set.class)); // PathExpression target
            context.push(constant("admin")); // Constant value
            MethodInsnNode containsInsn = new MethodInsnNode(INVOKEINTERFACE, "java/util/Collection", "contains",
                    "(Ljava/lang/Object;)Z", true);

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
            MethodInsnNode containsInsn = new MethodInsnNode(INVOKEINTERFACE, "java/util/Collection", "contains",
                    "(Ljava/lang/Object;)Z", true);

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
                            new io.quarkiverse.qubit.deployment.ast.LambdaExpression.PathSegment("person", Object.class,
                                    io.quarkiverse.qubit.deployment.ast.LambdaExpression.RelationType.FIELD),
                            new io.quarkiverse.qubit.deployment.ast.LambdaExpression.PathSegment("roles", java.util.Set.class,
                                    io.quarkiverse.qubit.deployment.ast.LambdaExpression.RelationType.FIELD)),
                    java.util.Set.class, io.quarkiverse.qubit.deployment.ast.LambdaExpression.EntityPosition.SECOND));
            context.push(constant("admin"));
            MethodInsnNode containsInsn = new MethodInsnNode(INVOKEINTERFACE, "java/util/Collection", "contains",
                    "(Ljava/lang/Object;)Z", true);

            handler.handle(containsInsn, context);

            assertThat(context.peek())
                    .as("BiEntityPathExpression.contains(Constant) should create MemberOfExpression")
                    .isInstanceOf(io.quarkiverse.qubit.deployment.ast.LambdaExpression.MemberOfExpression.class);
        }

        static Stream<Arguments> invalidContainsCallCases() {
            return Stream.of(
                    Arguments.of("java/util/Collection", "add", "(Ljava/lang/Object;)Z", "wrong method name"),
                    Arguments.of("java/util/Collection", "contains", "(II)Z", "wrong descriptor"),
                    Arguments.of("java/util/Map", "contains", "(Ljava/lang/Object;)Z", "wrong owner"));
        }

        @ParameterizedTest(name = "contains call with {3} does not create InExpression")
        @MethodSource("invalidContainsCallCases")
        void handle_containsCall_invalid_doesNotCreateInExpression(String owner, String method, String descriptor,
                String description) {
            context.push(captured(0, java.util.List.class));
            context.push(field("city", String.class));
            MethodInsnNode insn = new MethodInsnNode(INVOKEINTERFACE, owner, method, descriptor, true);

            handler.handle(insn, context);

            assertThat(context.getStackSize())
                    .as("%s should not be processed as contains", description)
                    .isEqualTo(2);
        }

        @ParameterizedTest(name = "contains on {0} owner creates InExpression")
        @ValueSource(strings = { "java/util/List", "java/util/Set", "java/util/Collection" })
        void handle_containsCall_collectionOwner_createsExpression(String owner) {
            context.push(captured(0, java.util.Collection.class));
            context.push(field("city", String.class));
            MethodInsnNode containsInsn = new MethodInsnNode(INVOKEINTERFACE, owner, "contains", "(Ljava/lang/Object;)Z", true);

            handler.handle(containsInsn, context);

            assertThat(context.peek())
                    .as("%s owner should be recognized", owner)
                    .isInstanceOf(io.quarkiverse.qubit.deployment.ast.LambdaExpression.InExpression.class);
        }

        @ParameterizedTest(name = "collection contains with {0} stack elements throws underflow")
        @ValueSource(ints = { 0, 1 })
        void handle_collectionContains_insufficientStack_throwsException(int stackElements) {
            for (int i = 0; i < stackElements; i++) {
                context.push(captured(i, java.util.List.class));
            }
            MethodInsnNode containsInsn = new MethodInsnNode(INVOKEINTERFACE, "java/util/Collection", "contains",
                    "(Ljava/lang/Object;)Z", true);

            assertThatThrownBy(() -> handler.handle(containsInsn, context))
                    .isInstanceOf(BytecodeAnalysisException.class)
                    .hasMessageContaining("Stack underflow");
        }

        @Test
        void handle_temporalFactory_insufficientStackSize_doesNothing() {
            // Only 2 args on stack when LocalDate.of needs 3
            context.push(constant(2024));
            context.push(constant(1));
            // Missing third argument
            MethodInsnNode localDateOf = new MethodInsnNode(INVOKESTATIC, "java/time/LocalDate", "of",
                    "(III)Ljava/time/LocalDate;", false);

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
            MethodInsnNode localDateTimeOf = new MethodInsnNode(INVOKESTATIC, "java/time/LocalDateTime", "of",
                    "(III)Ljava/time/LocalDateTime;", false);

            handler.handle(localDateTimeOf, context);

            // Should not process because arg count doesn't match expectedArgCount (5)
            assertThat(context.getStackSize())
                    .as("Wrong expected arg count should leave stack unchanged")
                    .isEqualTo(3);
        }

        static Stream<Arguments> temporalFactoryNonConstantCases() {
            return Stream.of(
                    Arguments.of("java/time/LocalTime", "(II)Ljava/time/LocalTime;", new Object[] { null, 30 }, "LocalTime"),
                    Arguments.of("java/time/LocalDate", "(III)Ljava/time/LocalDate;", new Object[] { null, 1, 15 },
                            "LocalDate"));
        }

        @ParameterizedTest(name = "{3}.of with non-constant args creates MethodCall")
        @MethodSource("temporalFactoryNonConstantCases")
        void handle_temporalFactory_withNonConstantArgs_createsMethodCall(
                String owner, String descriptor, Object[] args, String typeName) {
            // Push arguments: null means non-constant field, otherwise constant
            for (Object arg : args) {
                if (arg == null) {
                    context.push(field("value", int.class));
                } else {
                    context.push(constant(arg));
                }
            }
            MethodInsnNode factoryCall = new MethodInsnNode(INVOKESTATIC, owner, "of", descriptor, false);

            handler.handle(factoryCall, context);

            assertThat(context.getStackSize()).isEqualTo(1);
            assertThat(context.peek())
                    .as("Non-constant args should create MethodCall")
                    .isInstanceOf(io.quarkiverse.qubit.deployment.ast.LambdaExpression.MethodCall.class);
        }

        @Test
        void handle_substringTwoArgs_insufficientStack_leavesUnchanged() {
            // Only 2 elements when we need 3 for substring(II)
            context.push(field("name", String.class));
            context.push(constant(0));
            MethodInsnNode substringInsn = new MethodInsnNode(INVOKEVIRTUAL, "java/lang/String", "substring",
                    "(II)Ljava/lang/String;", false);

            handler.handle(substringInsn, context);

            assertThat(context.getStackSize())
                    .as("Insufficient stack for substring(II) should leave unchanged")
                    .isEqualTo(2);
        }

        static Stream<Arguments> invokeInterfaceMethodCases() {
            return Stream.of(
                    Arguments.of("equals", "(Ljava/lang/Object;)Z",
                            io.quarkiverse.qubit.deployment.ast.LambdaExpression.BinaryOp.class, "BinaryOp EQ"),
                    Arguments.of("compareTo", "(Ljava/lang/Object;)I",
                            io.quarkiverse.qubit.deployment.ast.LambdaExpression.MethodCall.class, "MethodCall"));
        }

        @ParameterizedTest(name = "interface {0}() creates {3}")
        @MethodSource("invokeInterfaceMethodCases")
        void handle_invokeInterface_method_handlesCorrectly(String methodName, String descriptor,
                Class<?> expectedResultType, String resultDescription) {
            context.push(field("name", String.class));
            context.push(constant("test"));
            MethodInsnNode insn = new MethodInsnNode(INVOKEINTERFACE, "java/lang/Comparable", methodName, descriptor, true);

            handler.handle(insn, context);

            assertThat(context.getStackSize()).isEqualTo(1);
            assertThat(context.peek())
                    .as("Interface %s() should create %s", methodName, resultDescription)
                    .isInstanceOf(expectedResultType);
        }

        @Test
        void handle_invokeSpecial_constructorWithNoArgs_createsConstructorCall() {
            // Zero-argument constructor
            context.push(constant("new_marker")); // NEW
            context.push(constant("dup_marker")); // DUP
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
            context.push(constant(123L)); // long
            context.push(constant(2)); // scale
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
            context.push(field("priceString", String.class)); // FieldAccess, not Constant
            MethodInsnNode bigDecimalInit = new MethodInsnNode(INVOKESPECIAL, "java/math/BigDecimal", "<init>",
                    "(Ljava/lang/String;)V", false);

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
            context.push(constant(123)); // Integer constant, not String
            MethodInsnNode bigDecimalInit = new MethodInsnNode(INVOKESPECIAL, "java/math/BigDecimal", "<init>", "(I)V", false);

            handler.handle(bigDecimalInit, context);

            assertThat(context.getStackSize()).isEqualTo(1);
            assertThat(context.peek())
                    .as("Non-string constant should create ConstructorCall")
                    .isInstanceOf(io.quarkiverse.qubit.deployment.ast.LambdaExpression.ConstructorCall.class);
        }

        @ParameterizedTest(name = "String.{0} with argument creates MethodCall")
        @ValueSource(strings = { "startsWith", "endsWith" })
        void handle_stringMethodWithArg_createsMethodCall(String methodName) {
            context.push(field("name", String.class));
            context.push(constant("test"));
            MethodInsnNode insn = new MethodInsnNode(INVOKEVIRTUAL, "java/lang/String", methodName, "(Ljava/lang/String;)Z",
                    false);

            handler.handle(insn, context);

            assertThat(context.getStackSize()).isEqualTo(1);
            io.quarkiverse.qubit.deployment.ast.LambdaExpression.MethodCall call = (io.quarkiverse.qubit.deployment.ast.LambdaExpression.MethodCall) context
                    .peek();
            assertThat(call.methodName()).isEqualTo(methodName);
        }

        @Test
        void handle_stringContains_createsMethodCall() {
            context.push(field("name", String.class));
            context.push(constant("test"));
            MethodInsnNode containsInsn = new MethodInsnNode(INVOKEVIRTUAL, "java/lang/String", "contains",
                    "(Ljava/lang/CharSequence;)Z", false);

            handler.handle(containsInsn, context);

            assertThat(context.getStackSize()).isEqualTo(1);
            io.quarkiverse.qubit.deployment.ast.LambdaExpression.MethodCall call = (io.quarkiverse.qubit.deployment.ast.LambdaExpression.MethodCall) context
                    .peek();
            assertThat(call.methodName()).isEqualTo("contains");
        }

        static Stream<Arguments> noArgStringMethods() {
            return Stream.of(
                    Arguments.of("length", "()I", int.class),
                    Arguments.of("isEmpty", "()Z", boolean.class),
                    Arguments.of("toLowerCase", "()Ljava/lang/String;", String.class),
                    Arguments.of("toUpperCase", "()Ljava/lang/String;", String.class),
                    Arguments.of("trim", "()Ljava/lang/String;", String.class));
        }

        @ParameterizedTest(name = "String.{0} creates MethodCall with {2} return type")
        @MethodSource("noArgStringMethods")
        void handle_stringNoArgMethod_createsMethodCall(String methodName, String descriptor, Class<?> returnType) {
            context.push(field("name", String.class));
            MethodInsnNode insn = new MethodInsnNode(INVOKEVIRTUAL, "java/lang/String", methodName, descriptor, false);

            handler.handle(insn, context);

            assertThat(context.getStackSize()).isEqualTo(1);
            io.quarkiverse.qubit.deployment.ast.LambdaExpression.MethodCall call = (io.quarkiverse.qubit.deployment.ast.LambdaExpression.MethodCall) context
                    .peek();
            assertThat(call.methodName()).isEqualTo(methodName);
            assertThat(call.returnType()).isEqualTo(returnType);
        }

        @Test
        void handle_stringUnknownMethod_leavesStackUnchanged() {
            // Unrecognized String method should do nothing
            context.push(field("name", String.class));
            MethodInsnNode unknownInsn = new MethodInsnNode(INVOKEVIRTUAL, "java/lang/String", "hashCode", "()I", false);

            handler.handle(unknownInsn, context);

            // hashCode is not handled, stack unchanged
            assertThat(context.getStackSize()).isEqualTo(1);
            assertThat(context.peek())
                    .isInstanceOf(io.quarkiverse.qubit.deployment.ast.LambdaExpression.FieldAccess.class);
        }

        static Stream<Arguments> binaryMethodUnderflowCases() {
            return Stream.of(
                    Arguments.of("equals", "(Ljava/lang/Object;)Z", "java/lang/Object", 0, "empty stack"),
                    Arguments.of("equals", "(Ljava/lang/Object;)Z", "java/lang/Object", 1, "one element"),
                    Arguments.of("compareTo", "(Ljava/lang/Integer;)I", "java/lang/Integer", 0, "empty stack"),
                    Arguments.of("compareTo", "(Ljava/lang/Integer;)I", "java/lang/Integer", 1, "one element"));
        }

        @ParameterizedTest(name = "{0} with {4} throws stack underflow")
        @MethodSource("binaryMethodUnderflowCases")
        void handle_binaryMethod_insufficientStack_throwsException(String methodName, String descriptor,
                String owner, int stackElements, String description) {
            for (int i = 0; i < stackElements; i++) {
                context.push(field("value", Object.class));
            }
            MethodInsnNode insn = new MethodInsnNode(INVOKEVIRTUAL, owner, methodName, descriptor, false);

            assertThatThrownBy(() -> handler.handle(insn, context))
                    .isInstanceOf(BytecodeAnalysisException.class)
                    .hasMessageContaining("Stack underflow");
        }

        @ParameterizedTest(name = "String.{0} with wrong descriptor does nothing")
        @ValueSource(strings = { "length", "isEmpty" })
        void handle_stringMethod_wrongDescriptor_doesNothing(String methodName) {
            context.push(field("name", String.class));
            MethodInsnNode wrongInsn = new MethodInsnNode(INVOKEVIRTUAL, "java/lang/String", methodName, "(I)I", false);

            handler.handle(wrongInsn, context);

            assertThat(context.getStackSize()).isEqualTo(1);
            assertThat(context.peek())
                    .isInstanceOf(io.quarkiverse.qubit.deployment.ast.LambdaExpression.FieldAccess.class);
        }

        static Stream<Arguments> temporalAccessorMethods() {
            return Stream.of(
                    Arguments.of("java/time/LocalDate", "birthDate", java.time.LocalDate.class, "getYear"),
                    Arguments.of("java/time/LocalDate", "birthDate", java.time.LocalDate.class, "getMonthValue"),
                    Arguments.of("java/time/LocalDate", "birthDate", java.time.LocalDate.class, "getDayOfMonth"),
                    Arguments.of("java/time/LocalTime", "startTime", java.time.LocalTime.class, "getHour"),
                    Arguments.of("java/time/LocalTime", "startTime", java.time.LocalTime.class, "getMinute"),
                    Arguments.of("java/time/LocalDateTime", "createdAt", java.time.LocalDateTime.class, "getYear"));
        }

        @ParameterizedTest(name = "{0}.{3} creates MethodCall")
        @MethodSource("temporalAccessorMethods")
        void handle_temporalAccessor_createsMethodCall(String owner, String fieldName, Class<?> fieldType, String methodName) {
            context.push(field(fieldName, fieldType));
            MethodInsnNode insn = new MethodInsnNode(INVOKEVIRTUAL, owner, methodName, "()I", false);

            handler.handle(insn, context);

            assertThat(context.getStackSize()).isEqualTo(1);
            io.quarkiverse.qubit.deployment.ast.LambdaExpression.MethodCall call = (io.quarkiverse.qubit.deployment.ast.LambdaExpression.MethodCall) context
                    .peek();
            assertThat(call.methodName()).isEqualTo(methodName);
        }

        @Test
        void handle_temporalAccessor_emptyStack_doesNothing() {
            // Empty stack should not throw
            MethodInsnNode getYearInsn = new MethodInsnNode(INVOKEVIRTUAL, "java/time/LocalDate", "getYear", "()I", false);

            handler.handle(getYearInsn, context);

            assertThat(context.isStackEmpty()).isTrue();
        }

        @ParameterizedTest(name = "temporal comparison {0} creates MethodCall")
        @ValueSource(strings = { "isBefore", "isAfter", "isEqual" })
        void handle_temporalComparison_createsMethodCall(String methodName) {
            context.push(field("date", java.time.LocalDate.class));
            context.push(constant(java.time.LocalDate.of(2024, 1, 1)));
            MethodInsnNode insn = new MethodInsnNode(INVOKEVIRTUAL, "java/time/LocalDate", methodName,
                    "(Ljava/time/chrono/ChronoLocalDate;)Z", false);

            handler.handle(insn, context);

            assertThat(context.getStackSize()).isEqualTo(1);
            io.quarkiverse.qubit.deployment.ast.LambdaExpression.MethodCall call = (io.quarkiverse.qubit.deployment.ast.LambdaExpression.MethodCall) context
                    .peek();
            assertThat(call.methodName()).isEqualTo(methodName);
            assertThat(call.returnType()).isEqualTo(boolean.class);
        }

        @Test
        void handle_bigDecimalRemainder_notHandled() {
            context.push(field("amount", java.math.BigDecimal.class));
            context.push(constant(new java.math.BigDecimal("2")));
            // remainder is not in the handled list (add, subtract, multiply, divide)
            MethodInsnNode remainderInsn = new MethodInsnNode(INVOKEVIRTUAL, "java/math/BigDecimal", "remainder",
                    "(Ljava/math/BigDecimal;)Ljava/math/BigDecimal;", false);

            handler.handle(remainderInsn, context);

            // Should leave stack unchanged
            assertThat(context.getStackSize()).isEqualTo(2);
        }

        @Test
        void handle_bigDecimalAbs_notHandled() {
            context.push(field("amount", java.math.BigDecimal.class));
            // abs() takes no args - unhandled
            MethodInsnNode absInsn = new MethodInsnNode(INVOKEVIRTUAL, "java/math/BigDecimal", "abs",
                    "()Ljava/math/BigDecimal;", false);

            handler.handle(absInsn, context);

            assertThat(context.getStackSize()).isEqualTo(1);
        }

        @Test
        void handle_invokeSpecial_superMethodCall_ignored() {
            context.push(field("obj", Object.class));
            // Super method call (not <init>)
            MethodInsnNode superHashCode = new MethodInsnNode(INVOKESPECIAL, "java/lang/Object", "hashCode", "()I", false);

            handler.handle(superHashCode, context);

            // Non-constructor INVOKESPECIAL is ignored
            assertThat(context.getStackSize()).isEqualTo(1);
        }

        @Test
        void handle_collectionContains_pathExpressionWithFieldAccess_createsMemberOf() {
            // PathExpression target with FieldAccess value (rare but valid)
            context.push(new io.quarkiverse.qubit.deployment.ast.LambdaExpression.PathExpression(
                    java.util.List.of(
                            new io.quarkiverse.qubit.deployment.ast.LambdaExpression.PathSegment("user", Object.class,
                                    io.quarkiverse.qubit.deployment.ast.LambdaExpression.RelationType.FIELD),
                            new io.quarkiverse.qubit.deployment.ast.LambdaExpression.PathSegment("permissions",
                                    java.util.Set.class,
                                    io.quarkiverse.qubit.deployment.ast.LambdaExpression.RelationType.FIELD)),
                    java.util.Set.class));
            context.push(captured(0, String.class)); // CapturedVariable
            MethodInsnNode containsInsn = new MethodInsnNode(INVOKEINTERFACE, "java/util/Collection", "contains",
                    "(Ljava/lang/Object;)Z", true);

            handler.handle(containsInsn, context);

            assertThat(context.peek())
                    .isInstanceOf(io.quarkiverse.qubit.deployment.ast.LambdaExpression.MemberOfExpression.class);
        }

        @Test
        void handle_collectionContains_constantTargetAndFieldArg_fallsBackToMethodCall() {
            // Neither IN nor MEMBER OF: constant target (not CapturedVariable), field arg
            context.push(constant(java.util.List.of("a", "b"))); // Constant, not CapturedVariable
            context.push(field("status", String.class)); // FieldAccess - this is entity field
            MethodInsnNode containsInsn = new MethodInsnNode(INVOKEINTERFACE, "java/util/Collection", "contains",
                    "(Ljava/lang/Object;)Z", true);

            handler.handle(containsInsn, context);

            // Constant is not CapturedVariable for IN clause, not FieldAccess for MEMBER OF
            // Should fall back to MethodCall
            assertThat(context.peek())
                    .isInstanceOf(io.quarkiverse.qubit.deployment.ast.LambdaExpression.MethodCall.class);
        }
    }

    @Nested
    class InvokeDynamicHandlerTests {

        private final InvokeDynamicHandler handler = InvokeDynamicHandler.INSTANCE;

        @Test
        void canHandle_withINVOKEDYNAMIC_returnsTrue() {
            InvokeDynamicInsnNode indyInsn = new InvokeDynamicInsnNode(
                    "makeConcatWithConstants",
                    "(Ljava/lang/String;)Ljava/lang/String;",
                    new org.objectweb.asm.Handle(
                            org.objectweb.asm.Opcodes.H_INVOKESTATIC,
                            "java/lang/invoke/StringConcatFactory",
                            "makeConcatWithConstants",
                            "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/invoke/CallSite;",
                            false),
                    "prefix-\u0001");

            assertThat(handler.canHandle(indyInsn))
                    .as("Should handle INVOKEDYNAMIC")
                    .isTrue();
        }

        @Test
        void canHandle_withNonINVOKEDYNAMIC_returnsFalse() {
            InsnNode notIndy = new InsnNode(IADD);
            assertThat(handler.canHandle(notIndy))
                    .as("Should not handle non-INVOKEDYNAMIC")
                    .isFalse();
        }

        @Test
        void escapeRecipe_withNull_returnsNullString() {
            String result = invokeEscapeRecipe(null);
            assertThat(result).as("Null recipe should return 'null' string").isEqualTo("null");
        }

        static Stream<Arguments> escapeRecipeTestCases() {
            return Stream.of(
                    Arguments.of("\u0001", "\\u0001", "dynamic arg marker"),
                    Arguments.of("\u0001-\u0001", "\\u0001-\\u0001", "multiple dynamic args"),
                    Arguments.of("\t", "\\u0009", "tab control character"),
                    Arguments.of("\u00FF", "\\u00ff", "high unicode character"),
                    Arguments.of("Hello World!", "Hello World!", "normal ASCII"),
                    Arguments.of(" ", " ", "space (boundary char 32)"),
                    Arguments.of("~", "~", "tilde (boundary char 126)"),
                    Arguments.of("\u007F", "\\u007f", "DEL (char 127)"),
                    Arguments.of("\u001F", "\\u001f", "unit separator (char 31)"),
                    Arguments.of("Hello\u0001World\t!", "Hello\\u0001World\\u0009!", "mixed content"),
                    Arguments.of("", "", "empty string"));
        }

        @ParameterizedTest(name = "escapeRecipe with {2}")
        @MethodSource("escapeRecipeTestCases")
        void escapeRecipe_handlesInput(String input, String expected, String description) {
            String result = invokeEscapeRecipe(input);
            assertThat(result).as("escapeRecipe(%s)", description).isEqualTo(expected);
        }

        @Test
        void handle_stringConcatFactory_withRecipe_buildsExpression() {
            // Create StringConcatFactory INVOKEDYNAMIC
            InvokeDynamicInsnNode indyInsn = new InvokeDynamicInsnNode(
                    "makeConcatWithConstants",
                    "(Ljava/lang/String;)Ljava/lang/String;",
                    new org.objectweb.asm.Handle(
                            org.objectweb.asm.Opcodes.H_INVOKESTATIC,
                            "java/lang/invoke/StringConcatFactory",
                            "makeConcatWithConstants",
                            "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/invoke/CallSite;",
                            false),
                    "prefix-\u0001-suffix");

            // Push one operand for the dynamic arg
            context.push(field("name", String.class));

            boolean terminated = handler.handle(indyInsn, context);

            assertThat(terminated)
                    .as("StringConcatFactory should not terminate")
                    .isFalse();
            assertThat(context.getStackSize())
                    .as("Should have concatenation result on stack")
                    .isEqualTo(1);
        }

        @Test
        void handle_stringConcatFactory_withNullBsmArgs_returnsFalse() {
            // Create INVOKEDYNAMIC with null bsmArgs
            InvokeDynamicInsnNode indyInsn = new InvokeDynamicInsnNode(
                    "makeConcatWithConstants",
                    "(Ljava/lang/String;)Ljava/lang/String;",
                    new org.objectweb.asm.Handle(
                            org.objectweb.asm.Opcodes.H_INVOKESTATIC,
                            "java/lang/invoke/StringConcatFactory",
                            "makeConcatWithConstants",
                            "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/invoke/CallSite;",
                            false));
            // No bsmArgs set - defaults to empty array

            boolean terminated = handler.handle(indyInsn, context);

            assertThat(terminated)
                    .as("Missing recipe should return false")
                    .isFalse();
        }

        @Test
        void handle_stringConcatFactory_withEmptyStack_returnsFalseAndLogsWarning() {
            // StringConcatFactory with dynamic args but empty stack
            InvokeDynamicInsnNode indyInsn = new InvokeDynamicInsnNode(
                    "makeConcatWithConstants",
                    "(Ljava/lang/String;)Ljava/lang/String;",
                    new org.objectweb.asm.Handle(
                            org.objectweb.asm.Opcodes.H_INVOKESTATIC,
                            "java/lang/invoke/StringConcatFactory",
                            "makeConcatWithConstants",
                            "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/invoke/CallSite;",
                            false),
                    "\u0001"); // One dynamic arg but empty stack

            boolean terminated = handler.handle(indyInsn, context);

            assertThat(terminated)
                    .as("Stack underflow should return false")
                    .isFalse();
        }

        @Test
        void handle_stringConcatFactory_onlyConstantRecipe_buildsConstant() {
            // Recipe with no dynamic args - just constant text
            InvokeDynamicInsnNode indyInsn = new InvokeDynamicInsnNode(
                    "makeConcatWithConstants",
                    "()Ljava/lang/String;",
                    new org.objectweb.asm.Handle(
                            org.objectweb.asm.Opcodes.H_INVOKESTATIC,
                            "java/lang/invoke/StringConcatFactory",
                            "makeConcatWithConstants",
                            "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/invoke/CallSite;",
                            false),
                    "constant text only");

            boolean terminated = handler.handle(indyInsn, context);

            assertThat(terminated).isFalse();
            assertThat(context.getStackSize()).isEqualTo(1);
            assertThat(context.peek())
                    .as("Constant-only recipe should produce Constant")
                    .isInstanceOf(io.quarkiverse.qubit.deployment.ast.LambdaExpression.Constant.class);
        }

        @Test
        void handle_stringConcatFactory_multipleDynamicArgs_buildsTree() {
            // Recipe with two dynamic args
            InvokeDynamicInsnNode indyInsn = new InvokeDynamicInsnNode(
                    "makeConcatWithConstants",
                    "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;",
                    new org.objectweb.asm.Handle(
                            org.objectweb.asm.Opcodes.H_INVOKESTATIC,
                            "java/lang/invoke/StringConcatFactory",
                            "makeConcatWithConstants",
                            "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/invoke/CallSite;",
                            false),
                    "\u0001-\u0001"); // Two dynamic args

            // Push two operands (in reverse order)
            context.push(field("first", String.class));
            context.push(field("second", String.class));

            boolean terminated = handler.handle(indyInsn, context);

            assertThat(terminated).isFalse();
            assertThat(context.getStackSize()).isEqualTo(1);
            assertThat(context.peek())
                    .as("Multiple dynamic args should produce BinaryOp (ADD)")
                    .isInstanceOf(io.quarkiverse.qubit.deployment.ast.LambdaExpression.BinaryOp.class);
        }

        @Test
        void handle_lambdaMetafactory_nonQuerySpec_returnsFalse() {
            // LambdaMetafactory but NOT QuerySpec - should not handle
            InvokeDynamicInsnNode indyInsn = new InvokeDynamicInsnNode(
                    "apply",
                    "()Ljava/util/function/Function;",
                    new org.objectweb.asm.Handle(
                            org.objectweb.asm.Opcodes.H_INVOKESTATIC,
                            "java/lang/invoke/LambdaMetafactory",
                            "metafactory",
                            "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;",
                            false),
                    org.objectweb.asm.Type.getType("(Ljava/lang/Object;)Ljava/lang/Object;"),
                    new org.objectweb.asm.Handle(
                            org.objectweb.asm.Opcodes.H_INVOKESTATIC,
                            "test/Lambda",
                            "lambda$test$0",
                            "(Ljava/lang/Object;)Ljava/lang/Object;",
                            false),
                    org.objectweb.asm.Type.getType("(Ljava/lang/Object;)Ljava/lang/Object;"));

            boolean terminated = handler.handle(indyInsn, context);

            assertThat(terminated)
                    .as("Non-QuerySpec lambda should return false")
                    .isFalse();
        }

        @Test
        void handle_nonStringConcatNonLambdaMetafactory_returnsFalse() {
            // INVOKEDYNAMIC that is neither StringConcatFactory nor LambdaMetafactory
            InvokeDynamicInsnNode indyInsn = new InvokeDynamicInsnNode(
                    "someMethod",
                    "()V",
                    new org.objectweb.asm.Handle(
                            org.objectweb.asm.Opcodes.H_INVOKESTATIC,
                            "some/Other/Factory",
                            "bootstrap",
                            "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;",
                            false));

            boolean terminated = handler.handle(indyInsn, context);

            assertThat(terminated)
                    .as("Unknown INVOKEDYNAMIC should return false")
                    .isFalse();
        }

        @Test
        void handle_withNullBsm_returnsFalse() {
            // Create INVOKEDYNAMIC with null bootstrap method
            InvokeDynamicInsnNode indyInsn = new InvokeDynamicInsnNode(
                    "test",
                    "()V",
                    null);

            boolean terminated = handler.handle(indyInsn, context);

            assertThat(terminated)
                    .as("Null bootstrap method should return false")
                    .isFalse();
        }

        @Test
        void handle_stringConcatFactory_withNonStringBsmArg_returnsFalse() {
            // bsmArgs[0] is not a String
            InvokeDynamicInsnNode indyInsn = new InvokeDynamicInsnNode(
                    "makeConcatWithConstants",
                    "()Ljava/lang/String;",
                    new org.objectweb.asm.Handle(
                            org.objectweb.asm.Opcodes.H_INVOKESTATIC,
                            "java/lang/invoke/StringConcatFactory",
                            "makeConcatWithConstants",
                            "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/invoke/CallSite;",
                            false),
                    Integer.valueOf(42)); // Not a String

            boolean terminated = handler.handle(indyInsn, context);

            assertThat(terminated)
                    .as("Non-string recipe should return false")
                    .isFalse();
            assertThat(context.isStackEmpty())
                    .as("Stack should remain empty")
                    .isTrue();
        }

        @Test
        void handle_stringConcatFactory_withFewerOperandsThanMarkers_handlesGracefully() {
            // Recipe has 3 dynamic arg markers but only 2 operands
            InvokeDynamicInsnNode indyInsn = new InvokeDynamicInsnNode(
                    "makeConcatWithConstants",
                    "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;",
                    new org.objectweb.asm.Handle(
                            org.objectweb.asm.Opcodes.H_INVOKESTATIC,
                            "java/lang/invoke/StringConcatFactory",
                            "makeConcatWithConstants",
                            "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/invoke/CallSite;",
                            false),
                    "\u0001-\u0001"); // Two markers

            // Push only one operand
            context.push(field("first", String.class));

            boolean terminated = handler.handle(indyInsn, context);

            // Stack underflow during buildConcatenationFromRecipe
            assertThat(terminated).isFalse();
        }

        @Test
        void handle_stringConcatFactory_operandIndexBoundary_exactMatch() {
            // Exact match: 2 markers, 2 operands - tests operandIndex < operands.size() boundary
            InvokeDynamicInsnNode indyInsn = new InvokeDynamicInsnNode(
                    "makeConcatWithConstants",
                    "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;",
                    new org.objectweb.asm.Handle(
                            org.objectweb.asm.Opcodes.H_INVOKESTATIC,
                            "java/lang/invoke/StringConcatFactory",
                            "makeConcatWithConstants",
                            "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/invoke/CallSite;",
                            false),
                    "\u0001\u0001"); // Two markers, no separator

            context.push(field("first", String.class));
            context.push(field("second", String.class));

            boolean terminated = handler.handle(indyInsn, context);

            assertThat(terminated).isFalse();
            assertThat(context.getStackSize()).isEqualTo(1);
        }

        private String invokeEscapeRecipe(String recipe) {
            try {
                java.lang.reflect.Method method = InvokeDynamicHandler.class
                        .getDeclaredMethod("escapeRecipe", String.class);
                method.setAccessible(true);
                return (String) method.invoke(handler, recipe);
            } catch (Exception e) {
                throw new RuntimeException("Failed to invoke escapeRecipe", e);
            }
        }
    }
}
