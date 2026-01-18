package io.quarkiverse.qubit.deployment.analysis.instruction;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.Handle;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.MethodNode;

import static io.quarkiverse.qubit.deployment.testutil.AstBuilders.*;
import static io.quarkiverse.qubit.runtime.QubitConstants.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.objectweb.asm.Opcodes.*;

/**
 * Error path tests for {@link InvokeDynamicHandler}.
 */
@DisplayName("InvokeDynamicHandler Error Path Tests")
class InvokeDynamicHandlerErrorTest {

    private InvokeDynamicHandler handler;
    private MethodNode testMethod;
    private AnalysisContext context;

    @BeforeEach
    void setUp() {
        handler = InvokeDynamicHandler.INSTANCE;
        testMethod = new MethodNode();
        testMethod.name = "testPredicate";
        testMethod.desc = "(Lio/quarkiverse/qubit/test/Person;)Z";
        testMethod.instructions = new InsnList();
        context = new AnalysisContext(testMethod, 0);
    }

    // ==================== canHandle Tests ====================

    @Nested
    @DisplayName("canHandle detection")
    class CanHandleTests {

        @Test
        @DisplayName("Handles INVOKEDYNAMIC opcode")
        void canHandle_invokeDynamic_returnsTrue() {
            InvokeDynamicInsnNode indy = createStringConcatIndy("makeConcatWithConstants", "\u0001");

            assertThat(handler.canHandle(indy))
                    .as("Should handle INVOKEDYNAMIC opcode")
                    .isTrue();
        }
    }

    // ==================== StringConcatFactory Error Tests ====================

    @Nested
    @DisplayName("StringConcatFactory error paths")
    class StringConcatFactoryErrorTests {

        @Test
        @DisplayName("No recipe in StringConcatFactory logs warning and returns false")
        void handleStringConcat_noRecipe_logsWarning() {
            // Create StringConcatFactory INVOKEDYNAMIC with null bsmArgs
            InvokeDynamicInsnNode indy = createStringConcatIndyNoRecipe();

            boolean result = handler.handle(indy, context);

            assertThat(result)
                    .as("Should return false when recipe is missing")
                    .isFalse();
            assertThat(context.isStackEmpty())
                    .as("Stack should remain empty")
                    .isTrue();
        }

        @Test
        @DisplayName("Empty bsmArgs in StringConcatFactory logs warning")
        void handleStringConcat_emptyBsmArgs_logsWarning() {
            // Create StringConcatFactory INVOKEDYNAMIC with empty bsmArgs
            InvokeDynamicInsnNode indy = new InvokeDynamicInsnNode(
                    "makeConcatWithConstants",
                    "(Ljava/lang/String;)Ljava/lang/String;",
                    new Handle(H_INVOKESTATIC,
                            JVM_JAVA_LANG_INVOKE_STRING_CONCAT_FACTORY,
                            "makeConcatWithConstants",
                            "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/invoke/CallSite;",
                            false));
            // bsmArgs defaults to empty array

            boolean result = handler.handle(indy, context);

            assertThat(result)
                    .as("Should return false when bsmArgs is empty")
                    .isFalse();
        }

        @Test
        @DisplayName("Non-string recipe in bsmArgs logs warning")
        void handleStringConcat_nonStringRecipe_logsWarning() {
            // Create StringConcatFactory INVOKEDYNAMIC with non-string as first bsmArg
            InvokeDynamicInsnNode indy = new InvokeDynamicInsnNode(
                    "makeConcatWithConstants",
                    "(Ljava/lang/String;)Ljava/lang/String;",
                    new Handle(H_INVOKESTATIC,
                            JVM_JAVA_LANG_INVOKE_STRING_CONCAT_FACTORY,
                            "makeConcatWithConstants",
                            "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/invoke/CallSite;",
                            false),
                    Integer.valueOf(42)); // Non-string recipe

            boolean result = handler.handle(indy, context);

            assertThat(result)
                    .as("Should return false when recipe is not a string")
                    .isFalse();
        }

        @Test
        @DisplayName("Stack underflow during string concatenation logs warning")
        void handleStringConcat_stackUnderflow_logsWarning() {
            // Create StringConcatFactory with recipe expecting 2 args, but stack is empty
            InvokeDynamicInsnNode indy = createStringConcatIndy("makeConcatWithConstants",
                    "\u0001 and \u0001"); // Expects 2 args

            // Don't push anything onto stack
            boolean result = handler.handle(indy, context);

            assertThat(result)
                    .as("Should return false on stack underflow")
                    .isFalse();
            assertThat(context.isStackEmpty())
                    .as("Stack should remain empty after underflow")
                    .isTrue();
        }

        @Test
        @DisplayName("Partial stack with fewer args than needed logs warning")
        void handleStringConcat_partialStack_logsWarning() {
            // Create StringConcatFactory with recipe expecting 2 args
            InvokeDynamicInsnNode indy = createStringConcatIndy("makeConcatWithConstants",
                    "\u0001 and \u0001"); // Expects 2 args

            // Push only 1 arg
            context.push(field("firstName", String.class));

            boolean result = handler.handle(indy, context);

            assertThat(result)
                    .as("Should return false on partial stack")
                    .isFalse();
        }
    }

    // ==================== Nested Lambda Error Tests ====================

    @Nested
    @DisplayName("Nested Lambda error paths")
    class NestedLambdaErrorTests {

        @Test
        @DisplayName("Nested lambda method not found logs warning")
        void handleNestedLambda_methodNotFound_logsWarning() {
            // Create LambdaMetafactory INVOKEDYNAMIC for a non-existent method
            InvokeDynamicInsnNode indy = createLambdaMetafactoryIndy(
                    "nonExistentMethod$lambda", "(LPerson;)Ljava/lang/Object;");

            boolean result = handler.handle(indy, context);

            assertThat(result)
                    .as("Should return false when nested lambda method not found")
                    .isFalse();
        }

        @Test
        @DisplayName("Impl method handle not extractable logs debug and returns false")
        void handleNestedLambda_noImplMethodHandle_logsDebug() {
            // Create LambdaMetafactory INVOKEDYNAMIC with insufficient bsmArgs
            InvokeDynamicInsnNode indy = new InvokeDynamicInsnNode(
                    "apply",
                    "()Lio/quarkiverse/qubit/runtime/QuerySpec;",
                    new Handle(H_INVOKESTATIC,
                            JVM_JAVA_LANG_INVOKE_LAMBDA_METAFACTORY,
                            "metafactory",
                            "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;",
                            false),
                    org.objectweb.asm.Type.getType("(Ljava/lang/Object;)Ljava/lang/Object;")); // Only 1 bsmArg

            boolean result = handler.handle(indy, context);

            assertThat(result)
                    .as("Should return false when impl method handle cannot be extracted")
                    .isFalse();
        }

        @Test
        @DisplayName("Non-QuerySpec lambda is ignored (not an error)")
        void handleLambdaMetafactory_nonQuerySpec_notHandled() {
            // Create LambdaMetafactory INVOKEDYNAMIC that returns something other than QuerySpec
            InvokeDynamicInsnNode indy = new InvokeDynamicInsnNode(
                    "apply",
                    "()Ljava/util/function/Function;", // Not QuerySpec
                    new Handle(H_INVOKESTATIC,
                            JVM_JAVA_LANG_INVOKE_LAMBDA_METAFACTORY,
                            "metafactory",
                            "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;",
                            false));

            boolean result = handler.handle(indy, context);

            assertThat(result)
                    .as("Should return false for non-QuerySpec lambda")
                    .isFalse();
        }
    }

    // ==================== Unhandled INVOKEDYNAMIC Tests ====================

    @Nested
    @DisplayName("Unhandled INVOKEDYNAMIC")
    class UnhandledInvokeDynamicTests {

        @Test
        @DisplayName("Unknown bootstrap method owner is traced and ignored")
        void handle_unknownBsm_logsTrace() {
            // Create INVOKEDYNAMIC with unknown bootstrap method
            InvokeDynamicInsnNode indy = new InvokeDynamicInsnNode(
                    "unknownMethod",
                    "()Ljava/lang/Object;",
                    new Handle(H_INVOKESTATIC,
                            "com/example/UnknownFactory",
                            "unknown",
                            "()Ljava/lang/invoke/CallSite;",
                            false));

            boolean result = handler.handle(indy, context);

            assertThat(result)
                    .as("Should return false for unknown bootstrap method")
                    .isFalse();
        }

        @Test
        @DisplayName("Null bootstrap method is handled gracefully")
        void handle_nullBsm_handledGracefully() {
            // Create INVOKEDYNAMIC with null bsm (edge case)
            InvokeDynamicInsnNode indy = new InvokeDynamicInsnNode(
                    "test",
                    "()V",
                    null); // null bsm

            boolean result = handler.handle(indy, context);

            assertThat(result)
                    .as("Should return false for null bootstrap method")
                    .isFalse();
        }
    }

    // ==================== Success Path Tests ====================

    @Nested
    @DisplayName("StringConcatFactory success paths")
    class StringConcatFactorySuccessTests {

        @Test
        @DisplayName("Simple string concat with one dynamic arg")
        void handleStringConcat_oneDynamicArg_buildsExpression() {
            // Recipe: "Hello, \u0001"
            InvokeDynamicInsnNode indy = createStringConcatIndy("makeConcatWithConstants",
                    "Hello, \u0001");

            context.push(field("name", String.class));

            boolean result = handler.handle(indy, context);

            assertThat(result).isFalse(); // Handler always returns false to continue
            assertThat(context.getStackSize())
                    .as("Should push concatenation result onto stack")
                    .isEqualTo(1);
        }

        @Test
        @DisplayName("String concat with two dynamic args")
        void handleStringConcat_twoDynamicArgs_buildsExpression() {
            // Recipe: "\u0001 \u0001"
            InvokeDynamicInsnNode indy = createStringConcatIndy("makeConcatWithConstants",
                    "\u0001 \u0001");

            context.push(field("firstName", String.class));
            context.push(field("lastName", String.class));

            boolean result = handler.handle(indy, context);

            assertThat(result).isFalse();
            assertThat(context.getStackSize())
                    .as("Should push concatenation result onto stack")
                    .isEqualTo(1);
        }

        @Test
        @DisplayName("String concat with only constants (no dynamic args)")
        void handleStringConcat_noArgs_buildsConstant() {
            // Recipe with no dynamic args - edge case (but valid)
            InvokeDynamicInsnNode indy = createStringConcatIndy("makeConcatWithConstants",
                    "Hello World");

            boolean result = handler.handle(indy, context);

            assertThat(result).isFalse();
            assertThat(context.getStackSize())
                    .as("Should push constant result onto stack")
                    .isEqualTo(1);
        }
    }

    // ==================== Helper Methods ====================

    private InvokeDynamicInsnNode createStringConcatIndy(String name, String recipe) {
        return new InvokeDynamicInsnNode(
                name,
                "(Ljava/lang/String;)Ljava/lang/String;",
                new Handle(H_INVOKESTATIC,
                        JVM_JAVA_LANG_INVOKE_STRING_CONCAT_FACTORY,
                        "makeConcatWithConstants",
                        "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/invoke/CallSite;",
                        false),
                recipe);
    }

    private InvokeDynamicInsnNode createStringConcatIndyNoRecipe() {
        return new InvokeDynamicInsnNode(
                "makeConcatWithConstants",
                "(Ljava/lang/String;)Ljava/lang/String;",
                new Handle(H_INVOKESTATIC,
                        JVM_JAVA_LANG_INVOKE_STRING_CONCAT_FACTORY,
                        "makeConcatWithConstants",
                        "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/invoke/CallSite;",
                        false));
        // No bsmArgs provided - recipe is null
    }

    private InvokeDynamicInsnNode createLambdaMetafactoryIndy(String implMethodName, String implMethodDesc) {
        return new InvokeDynamicInsnNode(
                "apply",
                "()Lio/quarkiverse/qubit/runtime/QuerySpec;",
                new Handle(H_INVOKESTATIC,
                        JVM_JAVA_LANG_INVOKE_LAMBDA_METAFACTORY,
                        "metafactory",
                        "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;",
                        false),
                org.objectweb.asm.Type.getType("(Ljava/lang/Object;)Ljava/lang/Object;"),
                new Handle(H_INVOKESTATIC, "TestClass", implMethodName, implMethodDesc, false),
                org.objectweb.asm.Type.getType("(Ljava/lang/Object;)Ljava/lang/Object;"));
    }
}
