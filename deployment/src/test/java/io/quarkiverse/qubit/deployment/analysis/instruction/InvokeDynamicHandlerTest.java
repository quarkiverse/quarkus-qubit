package io.quarkiverse.qubit.deployment.analysis.instruction;

import static io.quarkiverse.qubit.deployment.analysis.instruction.InvokeDynamicHandler.IndyCategory;
import static io.quarkiverse.qubit.deployment.analysis.instruction.InvokeDynamicHandler.IndyCategory.*;
import static io.quarkiverse.qubit.deployment.testutil.AstBuilders.*;
import static io.quarkiverse.qubit.deployment.testutil.fixtures.AnalysisContextFixtures.contextFor;
import static io.quarkiverse.qubit.deployment.testutil.fixtures.AsmFixtures.testMethod;
import static org.assertj.core.api.Assertions.assertThat;
import static org.objectweb.asm.Opcodes.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.Handle;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.MethodNode;

import io.quarkiverse.qubit.deployment.ast.LambdaExpression;

/**
 * Tests for {@link InvokeDynamicHandler}.
 *
 * <p>
 * Tests Java 9+ string concatenation via StringConcatFactory and nested lambda detection.
 */
class InvokeDynamicHandlerTest {

    private static final String STRING_CONCAT_FACTORY = "java/lang/invoke/StringConcatFactory";
    private static final String LAMBDA_METAFACTORY = "java/lang/invoke/LambdaMetafactory";
    private static final String QUERY_SPEC_RETURN = "()Lio/quarkiverse/qubit/QuerySpec;";
    private static final String FUNCTION_RETURN = "()Ljava/util/function/Function;";
    private static final String PREDICATE_RETURN = "()Ljava/util/function/Predicate;";

    private InvokeDynamicHandler handler;
    private MethodNode testMethod;
    private AnalysisContext context;

    @BeforeEach
    void setUp() {
        handler = InvokeDynamicHandler.INSTANCE;
        testMethod = testMethod().build();
        context = contextFor(testMethod, 0);
    }

    // ==================== canHandle Tests ====================

    @Nested
    class CanHandleTests {

        @Test
        void canHandle_withInvokeDynamic_returnsTrue() {
            InvokeDynamicInsnNode indy = createStringConcatIndy("Hello, \u0001");

            assertThat(handler.canHandle(indy))
                    .as("Should handle INVOKEDYNAMIC opcode")
                    .isTrue();
        }

        @Test
        void canHandle_withNonInvokeDynamic_returnsFalse() {
            InsnNode notIndy = new InsnNode(IADD);

            assertThat(handler.canHandle(notIndy))
                    .as("Should not handle non-INVOKEDYNAMIC opcode")
                    .isFalse();
        }

        @Test
        void canHandle_withInvokeVirtual_returnsFalse() {
            InsnNode invoke = new InsnNode(INVOKEVIRTUAL);

            assertThat(handler.canHandle(invoke))
                    .as("Should not handle INVOKEVIRTUAL")
                    .isFalse();
        }
    }

    // ==================== StringConcatFactory Tests ====================

    @Nested
    class StringConcatenationTests {

        @Test
        void handle_stringConcat_withSingleDynamicArg_buildsConcatExpression() {
            // Recipe: "Hello, \u0001" with one operand on stack
            InvokeDynamicInsnNode indy = createStringConcatIndy("Hello, \u0001");
            context.push(field("name", String.class));

            boolean terminated = handler.handle(indy, context);

            assertThat(terminated).isFalse();
            assertThat(context.getStackSize())
                    .as("Should push concatenation result")
                    .isEqualTo(1);
            assertThat(context.peek())
                    .as("Result should be BinaryOp with ADD")
                    .isInstanceOf(LambdaExpression.BinaryOp.class);
        }

        @Test
        void handle_stringConcat_withTwoDynamicArgs_buildsConcatExpression() {
            // Recipe: "\u0001 \u0001" (firstName + " " + lastName)
            InvokeDynamicInsnNode indy = createStringConcatIndy("\u0001 \u0001");
            context.push(field("firstName", String.class));
            context.push(field("lastName", String.class));

            boolean terminated = handler.handle(indy, context);

            assertThat(terminated).isFalse();
            assertThat(context.getStackSize())
                    .as("Should push concatenation result")
                    .isEqualTo(1);
        }

        @Test
        void handle_stringConcat_withPrefixAndTwoDynamicArgs_buildsConcatExpression() {
            // Recipe: "Mr. \u0001 \u0001"
            InvokeDynamicInsnNode indy = createStringConcatIndy("Mr. \u0001 \u0001");
            context.push(field("firstName", String.class));
            context.push(field("lastName", String.class));

            boolean terminated = handler.handle(indy, context);

            assertThat(terminated).isFalse();
            assertThat(context.getStackSize()).isEqualTo(1);
        }

        @Test
        void handle_stringConcat_withOnlyConstant_buildsConcatExpression() {
            // Recipe with no dynamic args, just a constant string
            InvokeDynamicInsnNode indy = createStringConcatIndy("Hello World");

            boolean terminated = handler.handle(indy, context);

            assertThat(terminated).isFalse();
            assertThat(context.getStackSize())
                    .as("Should push constant result")
                    .isEqualTo(1);
            assertThat(context.peek())
                    .as("Result should be Constant")
                    .isInstanceOf(LambdaExpression.Constant.class);
        }

        @Test
        void handle_stringConcat_withEmptyStack_returnsNull() {
            // Recipe expects dynamic arg but stack is empty
            InvokeDynamicInsnNode indy = createStringConcatIndy("\u0001");

            boolean terminated = handler.handle(indy, context);

            assertThat(terminated).isFalse();
            assertThat(context.isStackEmpty())
                    .as("Stack should remain empty when underflow occurs")
                    .isTrue();
        }

        @Test
        void handle_stringConcat_withNullRecipe_returnsFalse() {
            // bsmArgs is null
            Handle bsm = new Handle(H_INVOKESTATIC, STRING_CONCAT_FACTORY, "makeConcatWithConstants",
                    "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/invoke/CallSite;",
                    false);
            InvokeDynamicInsnNode indy = new InvokeDynamicInsnNode("makeConcatWithConstants",
                    "(Ljava/lang/String;)Ljava/lang/String;", bsm);

            boolean terminated = handler.handle(indy, context);

            assertThat(terminated).isFalse();
        }

        @Test
        void handle_stringConcat_withEmptyBsmArgs_returnsFalse() {
            // bsmArgs is empty array
            Handle bsm = new Handle(H_INVOKESTATIC, STRING_CONCAT_FACTORY, "makeConcatWithConstants",
                    "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/invoke/CallSite;",
                    false);
            InvokeDynamicInsnNode indy = new InvokeDynamicInsnNode("makeConcatWithConstants",
                    "(Ljava/lang/String;)Ljava/lang/String;", bsm, new Object[0]);

            boolean terminated = handler.handle(indy, context);

            assertThat(terminated).isFalse();
        }

        @Test
        void handle_stringConcat_withNonStringRecipe_returnsFalse() {
            // Recipe is not a String type
            Handle bsm = new Handle(H_INVOKESTATIC, STRING_CONCAT_FACTORY, "makeConcatWithConstants",
                    "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/invoke/CallSite;",
                    false);
            InvokeDynamicInsnNode indy = new InvokeDynamicInsnNode("makeConcatWithConstants",
                    "(Ljava/lang/String;)Ljava/lang/String;", bsm, Integer.valueOf(123));

            boolean terminated = handler.handle(indy, context);

            assertThat(terminated).isFalse();
        }

        @Test
        void handle_stringConcat_withTrailingConstant_buildsConcatExpression() {
            // Recipe: "\u0001!" (dynamic + constant)
            InvokeDynamicInsnNode indy = createStringConcatIndy("\u0001!");
            context.push(field("name", String.class));

            boolean terminated = handler.handle(indy, context);

            assertThat(terminated).isFalse();
            assertThat(context.getStackSize()).isEqualTo(1);
        }

        @Test
        void handle_stringConcat_withMultiplePlaceholders_consumesAllOperands() {
            // Recipe: "\u0001-\u0001-\u0001" (three dynamic args)
            InvokeDynamicInsnNode indy = createStringConcatIndy("\u0001-\u0001-\u0001");
            context.push(constant("A"));
            context.push(constant("B"));
            context.push(constant("C"));

            boolean terminated = handler.handle(indy, context);

            assertThat(terminated).isFalse();
            assertThat(context.getStackSize())
                    .as("Should consume all 3 operands and push 1 result")
                    .isEqualTo(1);
        }

        @Test
        void handle_stringConcat_withOnlyPlaceholder_returnsDynamicArg() {
            // Recipe: "\u0001" (single dynamic arg, no constants)
            InvokeDynamicInsnNode indy = createStringConcatIndy("\u0001");
            LambdaExpression.FieldAccess nameField = field("name", String.class);
            context.push(nameField);

            boolean terminated = handler.handle(indy, context);

            assertThat(terminated).isFalse();
            assertThat(context.getStackSize()).isEqualTo(1);
            // When there's only one operand, it should be returned directly (not wrapped in BinaryOp)
            assertThat(context.peek()).isEqualTo(nameField);
        }
    }

    // ==================== LambdaMetafactory Tests ====================

    @Nested
    class LambdaMetafactoryTests {

        @Test
        void handle_lambdaMetafactory_withNonQuerySpecReturn_returnsFalse() {
            // LambdaMetafactory with non-QuerySpec return type
            Handle bsm = new Handle(H_INVOKESTATIC, LAMBDA_METAFACTORY, "metafactory",
                    "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;",
                    false);
            InvokeDynamicInsnNode indy = new InvokeDynamicInsnNode("apply",
                    "()Ljava/util/function/Function;", bsm);

            boolean terminated = handler.handle(indy, context);

            assertThat(terminated).isFalse();
        }

        @Test
        void handle_lambdaMetafactory_withQuerySpecReturn_butNullBsmArgs_returnsFalse() {
            // LambdaMetafactory with QuerySpec but no bsmArgs
            Handle bsm = new Handle(H_INVOKESTATIC, LAMBDA_METAFACTORY, "metafactory",
                    "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;",
                    false);
            InvokeDynamicInsnNode indy = new InvokeDynamicInsnNode("apply",
                    "()Lio/quarkiverse/qubit/QuerySpec;", bsm);

            boolean terminated = handler.handle(indy, context);

            assertThat(terminated).isFalse();
        }

        @Test
        void handle_lambdaMetafactory_withQuerySpecReturn_butInsufficientBsmArgs_returnsFalse() {
            // LambdaMetafactory with QuerySpec but only 1 bsmArg (needs 2)
            Handle bsm = new Handle(H_INVOKESTATIC, LAMBDA_METAFACTORY, "metafactory",
                    "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;",
                    false);
            InvokeDynamicInsnNode indy = new InvokeDynamicInsnNode("apply",
                    "()Lio/quarkiverse/qubit/QuerySpec;", bsm, "onlyOneArg");

            boolean terminated = handler.handle(indy, context);

            assertThat(terminated).isFalse();
        }

        @Test
        void handle_lambdaMetafactory_withQuerySpecReturn_butNonHandleBsmArg_returnsFalse() {
            // LambdaMetafactory with QuerySpec but bsmArgs[1] is not a Handle
            Handle bsm = new Handle(H_INVOKESTATIC, LAMBDA_METAFACTORY, "metafactory",
                    "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;",
                    false);
            InvokeDynamicInsnNode indy = new InvokeDynamicInsnNode("apply",
                    "()Lio/quarkiverse/qubit/QuerySpec;", bsm, "arg0", "notAHandle");

            boolean terminated = handler.handle(indy, context);

            assertThat(terminated).isFalse();
        }

        @Test
        void handle_lambdaMetafactory_withCapturedVariables_popsFromStack() {
            // LambdaMetafactory with captured variables (desc has args)
            Handle bsm = new Handle(H_INVOKESTATIC, LAMBDA_METAFACTORY, "metafactory",
                    "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;",
                    false);
            Handle implHandle = new Handle(H_INVOKESTATIC, "TestClass", "lambda$test$0",
                    "(LPerson;LPerson;)Z", false);
            InvokeDynamicInsnNode indy = new InvokeDynamicInsnNode("apply",
                    "(LPerson;)Lio/quarkiverse/qubit/QuerySpec;", bsm, "arg0", implHandle);

            // Push captured variable on stack
            context.push(param("person", Object.class, 0));

            boolean terminated = handler.handle(indy, context);

            assertThat(terminated).isFalse();
            // Captured variable should be popped
            assertThat(context.isStackEmpty())
                    .as("Captured variable should be popped from stack")
                    .isTrue();
        }

        @Test
        void handle_lambdaMetafactory_withMultipleCapturedVariables_popsAllFromStack() {
            // LambdaMetafactory with multiple captured variables
            Handle bsm = new Handle(H_INVOKESTATIC, LAMBDA_METAFACTORY, "metafactory",
                    "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;",
                    false);
            Handle implHandle = new Handle(H_INVOKESTATIC, "TestClass", "lambda$test$0",
                    "(LPerson;LPerson;)Z", false);
            // Two captured variables: (LPerson;LString;)QuerySpec;
            InvokeDynamicInsnNode indy = new InvokeDynamicInsnNode("apply",
                    "(LPerson;Ljava/lang/String;)Lio/quarkiverse/qubit/QuerySpec;", bsm, "arg0", implHandle);

            // Push captured variables on stack
            context.push(param("person", Object.class, 0));
            context.push(constant("captured"));

            boolean terminated = handler.handle(indy, context);

            assertThat(terminated).isFalse();
            // Both captured variables should be popped
            assertThat(context.isStackEmpty())
                    .as("Both captured variables should be popped from stack")
                    .isTrue();
        }
    }

    // ==================== Non-Handled INVOKEDYNAMIC Tests ====================

    @Nested
    class UnhandledInvokeDynamicTests {

        @Test
        void handle_nonStringConcatNonLambdaFactory_returnsFalse() {
            // Some other bootstrap method
            Handle bsm = new Handle(H_INVOKESTATIC, "com/example/MyBootstrap", "bootstrap",
                    "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;",
                    false);
            InvokeDynamicInsnNode indy = new InvokeDynamicInsnNode("myDynamicCall",
                    "()Ljava/lang/Object;", bsm);

            boolean terminated = handler.handle(indy, context);

            assertThat(terminated).isFalse();
            assertThat(context.isStackEmpty()).isTrue();
        }

        @Test
        void handle_withNullBsm_returnsFalse() {
            // INVOKEDYNAMIC with null bsm (edge case)
            InvokeDynamicInsnNode indy = new InvokeDynamicInsnNode("test",
                    "()Ljava/lang/Object;", null);

            boolean terminated = handler.handle(indy, context);

            assertThat(terminated).isFalse();
        }
    }

    // ==================== Recipe Parsing Edge Cases ====================

    @Nested
    class RecipeParsingEdgeCases {

        @Test
        void handle_emptyRecipe_returnsNull() {
            InvokeDynamicInsnNode indy = createStringConcatIndy("");

            boolean terminated = handler.handle(indy, context);

            assertThat(terminated).isFalse();
            // Empty recipe produces null result, so stack remains empty
            assertThat(context.isStackEmpty()).isTrue();
        }

        @Test
        void handle_recipeWithSpecialChars_buildsConcatExpression() {
            // Recipe with newlines and special characters
            InvokeDynamicInsnNode indy = createStringConcatIndy("Line1\nLine2\t\u0001");
            context.push(field("value", String.class));

            boolean terminated = handler.handle(indy, context);

            assertThat(terminated).isFalse();
            assertThat(context.getStackSize()).isEqualTo(1);
        }

        @Test
        void handle_recipeWithUnicode_buildsConcatExpression() {
            // Recipe with unicode characters (not \u0001)
            InvokeDynamicInsnNode indy = createStringConcatIndy("こんにちは \u0001");
            context.push(field("name", String.class));

            boolean terminated = handler.handle(indy, context);

            assertThat(terminated).isFalse();
            assertThat(context.getStackSize()).isEqualTo(1);
        }

        @Test
        void handle_recipeWithAdjacentPlaceholders_buildsConcatExpression() {
            // Recipe: "\u0001\u0001" (two adjacent dynamic args)
            InvokeDynamicInsnNode indy = createStringConcatIndy("\u0001\u0001");
            context.push(constant("A"));
            context.push(constant("B"));

            boolean terminated = handler.handle(indy, context);

            assertThat(terminated).isFalse();
            assertThat(context.getStackSize()).isEqualTo(1);
        }

        @Test
        void handle_stackUnderflow_withPartialOperands_returnsNull() {
            // Recipe expects 2 operands, but stack has only 1
            InvokeDynamicInsnNode indy = createStringConcatIndy("\u0001 \u0001");
            context.push(field("name", String.class));
            // Only 1 operand, need 2

            boolean terminated = handler.handle(indy, context);

            assertThat(terminated).isFalse();
            // Stack underflow occurs mid-operation - operands are already popped
            // The handler pops operands in reverse order and fails when stack is empty
            assertThat(context.isStackEmpty())
                    .as("Stack should be empty after underflow (operands already consumed)")
                    .isTrue();
        }
    }

    // ==================== Recipe Buffer Clearing Tests ====================

    @Nested
    class BufferClearingTests {

        @Test
        void handle_stringConcat_withMultipleConstantSegments_clearsBufferBetweenSegments() {
            // Recipe: "prefix\u0001middle\u0001suffix" - tests buffer is cleared between segments
            InvokeDynamicInsnNode indy = createStringConcatIndy("prefix\u0001middle\u0001suffix");
            context.push(field("first", String.class));
            context.push(field("second", String.class));

            boolean terminated = handler.handle(indy, context);

            assertThat(terminated).isFalse();
            assertThat(context.getStackSize()).isEqualTo(1);

            // Verify the tree structure contains distinct constant segments
            LambdaExpression result = context.peek();
            assertThat(result).isInstanceOf(LambdaExpression.BinaryOp.class);
            String resultStr = result.toString();
            assertThat(resultStr)
                    .as("Should contain all constant segments (prefix, middle, suffix)")
                    .contains("prefix")
                    .contains("middle")
                    .contains("suffix");
        }

        @Test
        void handle_stringConcat_repeatedCalls_eachCallClearsBuffer() {
            // First call with recipe
            InvokeDynamicInsnNode indy1 = createStringConcatIndy("first:\u0001");
            context.push(field("a", String.class));
            handler.handle(indy1, context);

            LambdaExpression first = context.pop();
            assertThat(first.toString()).contains("first:");

            // Second call with different recipe - buffer should be clear from first call
            InvokeDynamicInsnNode indy2 = createStringConcatIndy("second:\u0001");
            context.push(field("b", String.class));
            handler.handle(indy2, context);

            LambdaExpression second = context.peek();
            assertThat(second.toString())
                    .as("Second call should only contain 'second:', not 'first:' - buffer must be cleared")
                    .contains("second:")
                    .doesNotContain("first:");
        }

        @Test
        void handle_stringConcat_threeConstantSections_allPreserved() {
            // Recipe: "A\u0001B\u0001C" - three constant sections that must all be preserved
            InvokeDynamicInsnNode indy = createStringConcatIndy("AAA\u0001BBB\u0001CCC");
            context.push(constant("X"));
            context.push(constant("Y"));

            handler.handle(indy, context);

            LambdaExpression result = context.peek();
            String resultStr = result.toString();
            // If buffer.setLength(0) is removed, constants would accumulate incorrectly
            assertThat(resultStr).contains("AAA").contains("BBB").contains("CCC");
        }

        @Test
        void handle_stringConcat_bufferMustBeClearedBetweenConstants_verifyExactValues() {
            // Recipe: "A\u0001B" with one dynamic arg
            // If setLength(0) is removed, "B" would be "AB" instead
            InvokeDynamicInsnNode indy = createStringConcatIndy("A\u0001B");
            context.push(constant("X"));

            handler.handle(indy, context);

            // Result: ((Constant("A") + Constant("X")) + Constant("B"))
            LambdaExpression result = context.peek();
            assertThat(result).isInstanceOf(LambdaExpression.BinaryOp.class);

            // Navigate to the rightmost constant - should be "B", not "AB"
            LambdaExpression.BinaryOp outerOp = (LambdaExpression.BinaryOp) result;
            assertThat(outerOp.right())
                    .as("Rightmost operand should be Constant('B'), not accumulated buffer")
                    .isInstanceOf(LambdaExpression.Constant.class);
            LambdaExpression.Constant rightConstant = (LambdaExpression.Constant) outerOp.right();
            assertThat(rightConstant.value())
                    .as("Buffer must be cleared - constant should be exactly 'B', not 'AB'")
                    .isEqualTo("B");
        }
    }

    // ==================== Return Value Verification Tests ====================

    @Nested
    class ReturnValueTests {

        @Test
        void handle_stringConcatenation_returnsFalse_neverTrue() {
            // Verifies handleStringConcatenation always returns false (continue processing)
            InvokeDynamicInsnNode indy = createStringConcatIndy("test\u0001");
            context.push(constant("value"));

            boolean terminated = handler.handle(indy, context);

            assertThat(terminated)
                    .as("String concatenation should never terminate (return false)")
                    .isFalse();
        }

        @Test
        void handle_lambdaMetafactory_returnsFalse_neverTrue() {
            // Verifies handleNestedLambda always returns false
            Handle bsm = new Handle(H_INVOKESTATIC, LAMBDA_METAFACTORY, "metafactory",
                    "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;",
                    false);
            Handle implHandle = new Handle(H_INVOKESTATIC, "TestClass", "lambda$test$0",
                    "(LPerson;)Z", false);
            InvokeDynamicInsnNode indy = new InvokeDynamicInsnNode("apply",
                    "()Lio/quarkiverse/qubit/QuerySpec;", bsm, "arg0", implHandle);

            boolean terminated = handler.handle(indy, context);

            assertThat(terminated)
                    .as("Lambda metafactory should never terminate (return false)")
                    .isFalse();
        }
    }

    // ==================== Boolean Method Tests ====================

    @Nested
    class BooleanMethodTests {

        @Test
        void isLambdaMetafactory_withStringConcatFactory_returnsFalse() {
            // StringConcatFactory is NOT LambdaMetafactory
            // Recipe "test" has no placeholders, so handler produces Constant("test")
            InvokeDynamicInsnNode indy = createStringConcatIndy("test");

            boolean terminated = handler.handle(indy, context);

            assertThat(terminated).isFalse();
            // String concat path pushes a Constant result (proving it wasn't treated as lambda)
            assertThat(context.getStackSize())
                    .as("String concat factory should produce a result")
                    .isEqualTo(1);
            assertThat(context.peek())
                    .as("Result should be a Constant with the recipe text")
                    .isInstanceOf(LambdaExpression.Constant.class);
        }

        @Test
        void isQuerySpecLambda_withFunctionReturnType_returnsFalse() {
            // LambdaMetafactory that returns Function, not QuerySpec
            Handle bsm = new Handle(H_INVOKESTATIC, LAMBDA_METAFACTORY, "metafactory",
                    "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;",
                    false);
            Handle implHandle = new Handle(H_INVOKESTATIC, "TestClass", "lambda$0",
                    "(Ljava/lang/Object;)Ljava/lang/Object;", false);
            // Return type is Function, not QuerySpec
            InvokeDynamicInsnNode indy = new InvokeDynamicInsnNode("apply",
                    "()Ljava/util/function/Function;", bsm, "arg0", implHandle);

            boolean terminated = handler.handle(indy, context);

            assertThat(terminated).isFalse();
            // Stack should remain unchanged since neither string concat nor QuerySpec lambda
            assertThat(context.isStackEmpty())
                    .as("Non-QuerySpec lambda should not modify stack")
                    .isTrue();
        }

        @Test
        void isQuerySpecLambda_withPredicateReturnType_returnsFalse() {
            // LambdaMetafactory that returns Predicate, not QuerySpec
            Handle bsm = new Handle(H_INVOKESTATIC, LAMBDA_METAFACTORY, "metafactory",
                    "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;",
                    false);
            Handle implHandle = new Handle(H_INVOKESTATIC, "TestClass", "lambda$0",
                    "(Ljava/lang/Object;)Z", false);
            InvokeDynamicInsnNode indy = new InvokeDynamicInsnNode("test",
                    "()Ljava/util/function/Predicate;", bsm, "arg0", implHandle);

            boolean terminated = handler.handle(indy, context);

            assertThat(terminated).isFalse();
            assertThat(context.isStackEmpty()).isTrue();
        }
    }

    // ==================== Extract Recipe Edge Cases ====================

    @Nested
    class ExtractRecipeTests {

        @Test
        void extractRecipe_withValidRecipe_extractsCorrectly() {
            // Verify extracted recipe is actually used in building expression
            String expectedRecipe = "Hello, World";
            InvokeDynamicInsnNode indy = createStringConcatIndy(expectedRecipe);

            handler.handle(indy, context);

            // If extractRecipe returned "" instead of actual recipe, constant would be empty
            assertThat(context.getStackSize()).isEqualTo(1);
            LambdaExpression result = context.peek();
            assertThat(result).isInstanceOf(LambdaExpression.Constant.class);
            LambdaExpression.Constant constant = (LambdaExpression.Constant) result;
            assertThat(constant.value())
                    .as("Extracted recipe should be used to create constant")
                    .isEqualTo(expectedRecipe);
        }

        @Test
        void extractRecipe_withNullBsmArgs_returnsNull() {
            Handle bsm = new Handle(H_INVOKESTATIC, STRING_CONCAT_FACTORY, "makeConcatWithConstants",
                    "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/invoke/CallSite;",
                    false);
            // bsmArgs is null (not passed to constructor explicitly)
            InvokeDynamicInsnNode indy = new InvokeDynamicInsnNode("makeConcatWithConstants",
                    "(Ljava/lang/String;)Ljava/lang/String;", bsm);

            handler.handle(indy, context);

            // extractRecipe returns null, so no expression is built
            assertThat(context.isStackEmpty()).isTrue();
        }
    }

    // ==================== Parse Recipe Boundary Tests ====================

    @Nested
    class ParseRecipeBoundaryTests {

        @Test
        void parseRecipe_withZeroOperands_handlesCorrectly() {
            // Recipe with 0 placeholders - boundary case
            InvokeDynamicInsnNode indy = createStringConcatIndy("NoPlaceholders");

            handler.handle(indy, context);

            assertThat(context.getStackSize()).isEqualTo(1);
        }

        @Test
        void parseRecipe_withOneOperand_handlesCorrectly() {
            // Recipe with exactly 1 placeholder - boundary case
            InvokeDynamicInsnNode indy = createStringConcatIndy("\u0001");
            LambdaExpression.FieldAccess field = field("name", String.class);
            context.push(field);

            handler.handle(indy, context);

            assertThat(context.getStackSize()).isEqualTo(1);
            assertThat(context.peek()).isEqualTo(field);
        }

        @Test
        void parseRecipe_operandIndexBoundary_handlesExactCount() {
            // Test operandIndex < operands.size() boundary
            // Recipe: "\u0001\u0001\u0001" - exactly 3 placeholders, exactly 3 operands
            InvokeDynamicInsnNode indy = createStringConcatIndy("\u0001\u0001\u0001");
            context.push(constant("1"));
            context.push(constant("2"));
            context.push(constant("3"));

            handler.handle(indy, context);

            assertThat(context.getStackSize()).isEqualTo(1);
            // All 3 operands should be consumed and combined
        }
    }

    // ==================== IndyCategory Categorization Tests ====================

    @Nested
    class IndyCategoryTests {

        @Test
        void categorize_stringConcatFactory_returnsStringConcat() {
            InvokeDynamicInsnNode indy = createStringConcatIndy("test");

            IndyCategory result = IndyCategory.categorize(indy);

            assertThat(result)
                    .as("StringConcatFactory should be categorized as STRING_CONCAT")
                    .isEqualTo(STRING_CONCAT);
        }

        @Test
        void categorize_lambdaMetafactoryWithQuerySpecReturn_returnsQuerySpecLambda() {
            Handle bsm = new Handle(H_INVOKESTATIC, LAMBDA_METAFACTORY, "metafactory",
                    "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;",
                    false);
            InvokeDynamicInsnNode indy = new InvokeDynamicInsnNode("apply",
                    QUERY_SPEC_RETURN, bsm);

            IndyCategory result = IndyCategory.categorize(indy);

            assertThat(result)
                    .as("LambdaMetafactory with QuerySpec return should be QUERY_SPEC_LAMBDA")
                    .isEqualTo(QUERY_SPEC_LAMBDA);
        }

        @Test
        void categorize_lambdaMetafactoryWithFunctionReturn_returnsUnhandled() {
            Handle bsm = new Handle(H_INVOKESTATIC, LAMBDA_METAFACTORY, "metafactory",
                    "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;",
                    false);
            InvokeDynamicInsnNode indy = new InvokeDynamicInsnNode("apply",
                    FUNCTION_RETURN, bsm);

            IndyCategory result = IndyCategory.categorize(indy);

            assertThat(result)
                    .as("LambdaMetafactory with non-QuerySpec return should be UNHANDLED")
                    .isEqualTo(UNHANDLED);
        }

        @Test
        void categorize_lambdaMetafactoryWithPredicateReturn_returnsUnhandled() {
            Handle bsm = new Handle(H_INVOKESTATIC, LAMBDA_METAFACTORY, "metafactory",
                    "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;",
                    false);
            InvokeDynamicInsnNode indy = new InvokeDynamicInsnNode("test",
                    PREDICATE_RETURN, bsm);

            IndyCategory result = IndyCategory.categorize(indy);

            assertThat(result)
                    .as("LambdaMetafactory with Predicate return should be UNHANDLED")
                    .isEqualTo(UNHANDLED);
        }

        @Test
        void categorize_nullBsm_returnsUnhandled() {
            InvokeDynamicInsnNode indy = new InvokeDynamicInsnNode("test",
                    "()Ljava/lang/Object;", null);

            IndyCategory result = IndyCategory.categorize(indy);

            assertThat(result)
                    .as("Null bsm should be categorized as UNHANDLED")
                    .isEqualTo(UNHANDLED);
        }

        @Test
        void categorize_unknownBootstrapMethod_returnsUnhandled() {
            Handle bsm = new Handle(H_INVOKESTATIC, "com/example/CustomFactory", "bootstrap",
                    "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;",
                    false);
            InvokeDynamicInsnNode indy = new InvokeDynamicInsnNode("customCall",
                    "()Ljava/lang/Object;", bsm);

            IndyCategory result = IndyCategory.categorize(indy);

            assertThat(result)
                    .as("Unknown bootstrap method should be UNHANDLED")
                    .isEqualTo(UNHANDLED);
        }

        @Test
        void categorize_stringConcatFactoryPriority_overLambdaMetafactory() {
            // StringConcatFactory should be checked before LambdaMetafactory
            // This tests the priority order in categorize()
            InvokeDynamicInsnNode stringConcat = createStringConcatIndy("\u0001");

            IndyCategory result = IndyCategory.categorize(stringConcat);

            assertThat(result)
                    .as("StringConcatFactory has higher priority")
                    .isEqualTo(STRING_CONCAT);
        }

        @Test
        void categorize_lambdaMetafactoryWithCapturedVars_stillQuerySpecLambda() {
            // Lambda with captured variables: (LPerson;)QuerySpec
            Handle bsm = new Handle(H_INVOKESTATIC, LAMBDA_METAFACTORY, "metafactory",
                    "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;",
                    false);
            InvokeDynamicInsnNode indy = new InvokeDynamicInsnNode("apply",
                    "(LPerson;)Lio/quarkiverse/qubit/QuerySpec;", bsm);

            IndyCategory result = IndyCategory.categorize(indy);

            assertThat(result)
                    .as("LambdaMetafactory with captured vars but QuerySpec return should be QUERY_SPEC_LAMBDA")
                    .isEqualTo(QUERY_SPEC_LAMBDA);
        }

        @Test
        void categorize_lambdaMetafactoryWithSubquerySpec_returnsQuerySpecLambda() {
            // SubquerySpec also implements QuerySpec - test the return type check
            Handle bsm = new Handle(H_INVOKESTATIC, LAMBDA_METAFACTORY, "metafactory",
                    "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;",
                    false);
            // Uses QuerySpec in the return type
            InvokeDynamicInsnNode indy = new InvokeDynamicInsnNode("apply",
                    "()Lio/quarkiverse/qubit/QuerySpec;", bsm);

            IndyCategory result = IndyCategory.categorize(indy);

            assertThat(result).isEqualTo(QUERY_SPEC_LAMBDA);
        }

        @Test
        void categorize_bsmOwnerEquality_usesEquals() {
            // Test that we use equals() for String comparison, not reference equality
            // This kills mutations that change equals() to ==
            Handle bsm = new Handle(H_INVOKESTATIC,
                    new String("java/lang/invoke/StringConcatFactory"), // new String to avoid interning
                    "makeConcatWithConstants",
                    "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/invoke/CallSite;",
                    false);
            InvokeDynamicInsnNode indy = new InvokeDynamicInsnNode("makeConcatWithConstants",
                    "(Ljava/lang/String;)Ljava/lang/String;", bsm, "test");

            IndyCategory result = IndyCategory.categorize(indy);

            assertThat(result)
                    .as("Should use equals() for owner comparison")
                    .isEqualTo(STRING_CONCAT);
        }

        @Test
        void categorize_lambdaMetafactoryOwnerEquality_usesEquals() {
            // Test equals() for LambdaMetafactory owner comparison
            Handle bsm = new Handle(H_INVOKESTATIC,
                    new String("java/lang/invoke/LambdaMetafactory"), // new String to avoid interning
                    "metafactory",
                    "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;",
                    false);
            InvokeDynamicInsnNode indy = new InvokeDynamicInsnNode("apply",
                    QUERY_SPEC_RETURN, bsm);

            IndyCategory result = IndyCategory.categorize(indy);

            assertThat(result)
                    .as("Should use equals() for LambdaMetafactory owner comparison")
                    .isEqualTo(QUERY_SPEC_LAMBDA);
        }

        @Test
        void categorize_allEnumValues_areTestedOrReturned() {
            // Verify all enum values can be returned by categorize
            assertThat(IndyCategory.values())
                    .as("IndyCategory should have exactly 3 values")
                    .hasSize(3)
                    .containsExactly(STRING_CONCAT, QUERY_SPEC_LAMBDA, UNHANDLED);
        }
    }

    // ==================== Helper Methods ====================

    /**
     * Creates an INVOKEDYNAMIC instruction for StringConcatFactory with the given recipe.
     */
    private InvokeDynamicInsnNode createStringConcatIndy(String recipe) {
        Handle bsm = new Handle(H_INVOKESTATIC, STRING_CONCAT_FACTORY, "makeConcatWithConstants",
                "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/invoke/CallSite;",
                false);
        return new InvokeDynamicInsnNode("makeConcatWithConstants",
                "(Ljava/lang/String;)Ljava/lang/String;", bsm, recipe);
    }
}
