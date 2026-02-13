package io.quarkiverse.qubit.deployment.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link BytecodeAnalysisException} factory methods.
 */
class BytecodeAnalysisExceptionTest {

    // Stack Operations Factory Methods

    @Nested
    class StackOperationsTests {

        @Test
        void stackUnderflow_includesAllParameters() {
            BytecodeAnalysisException ex = BytecodeAnalysisException.stackUnderflow("pop", 2, 0);

            assertThat(ex.getMessage())
                    .contains("Stack underflow")
                    .contains("pop")
                    .contains("2")
                    .contains("0");
        }

        @Test
        void stackUnderflow_isInstanceOfBytecodeAnalysisException() {
            BytecodeAnalysisException ex = BytecodeAnalysisException.stackUnderflow("instruction", 1, 0);

            assertThat(ex).isInstanceOf(BytecodeAnalysisException.class);
            assertThat(ex).isInstanceOf(RuntimeException.class);
        }
    }

    // Opcode Validation Factory Methods

    @Nested
    class OpcodeValidationTests {

        @Test
        void unexpectedOpcode_includesHandlerContextAndOpcode() {
            BytecodeAnalysisException ex = BytecodeAnalysisException.unexpectedOpcode("type conversion", 182);

            assertThat(ex.getMessage())
                    .contains("type conversion")
                    .contains("182")
                    .contains("0xB6"); // Hex value
        }

        @Test
        void unexpectedOpcode_containsHelpfulHint() {
            BytecodeAnalysisException ex = BytecodeAnalysisException.unexpectedOpcode("handler", 100);

            assertThat(ex.getMessage()).containsAnyOf("unsupported", "cannot be analyzed");
        }

        @Test
        void invalidOpcode_includesAllValidOpcodes() {
            BytecodeAnalysisException ex = BytecodeAnalysisException.invalidOpcode(100, 10, 20, 30);

            assertThat(ex.getMessage())
                    .contains("100")
                    .contains("10")
                    .contains("20")
                    .contains("30");
        }

        @Test
        void invalidOpcode_withSingleValidOpcode_formatsCorrectly() {
            BytecodeAnalysisException ex = BytecodeAnalysisException.invalidOpcode(50, 42);

            assertThat(ex.getMessage())
                    .contains("50")
                    .contains("42");
        }

        @Test
        void invalidOpcode_withNoValidOpcodes_formatsCorrectly() {
            BytecodeAnalysisException ex = BytecodeAnalysisException.invalidOpcode(50);

            assertThat(ex.getMessage()).contains("50");
        }
    }

    // Unsupported Patterns Factory Methods

    @Nested
    class UnsupportedPatternsTests {

        @Test
        void unsupported_includesOperationAndDetails() {
            BytecodeAnalysisException ex = BytecodeAnalysisException.unsupported("binary operation", "bitwise XOR");

            assertThat(ex.getMessage())
                    .contains("binary operation")
                    .contains("bitwise XOR")
                    .contains("Unsupported");
        }

        @Test
        void unsupported_containsHelpfulHint() {
            BytecodeAnalysisException ex = BytecodeAnalysisException.unsupported("op", "details");

            assertThat(ex.getMessage()).contains("cannot be translated");
        }
    }

    // Null Safety Factory Methods

    @Nested
    class NullSafetyTests {

        @Test
        void unexpectedNull_includesContext() {
            BytecodeAnalysisException ex = BytecodeAnalysisException.unexpectedNull("method parameter");

            assertThat(ex.getMessage())
                    .contains("null")
                    .contains("method parameter");
        }
    }

    // Class Loading Factory Methods

    @Nested
    class ClassLoadingTests {

        @Test
        void bytecodeNotFound_includesClassName() {
            BytecodeAnalysisException ex = BytecodeAnalysisException.bytecodeNotFound("com.example.MyClass");

            assertThat(ex.getMessage())
                    .contains("com.example.MyClass")
                    .contains("bytecode");
        }

        @Test
        void bytecodeNotFound_containsHelpfulHint() {
            BytecodeAnalysisException ex = BytecodeAnalysisException.bytecodeNotFound("SomeClass");

            assertThat(ex.getMessage()).containsAnyOf("compiled", "classpath");
        }
    }

    // Lambda Resolution Factory Methods

    @Nested
    class LambdaResolutionTests {

        @Test
        void lambdaMethodNotFound_includesAllParameters() {
            BytecodeAnalysisException ex = BytecodeAnalysisException.lambdaMethodNotFound(
                    "com.example.Service", "lambda$process$0", "(Ljava/lang/Object;)Z");

            assertThat(ex.getMessage())
                    .contains("com.example.Service")
                    .contains("lambda$process$0")
                    .contains("(Ljava/lang/Object;)Z")
                    .contains("not found");
        }

        @Test
        void analysisFailedWithContext_includesAllParameters() {
            RuntimeException cause = new RuntimeException("root cause");
            BytecodeAnalysisException ex = BytecodeAnalysisException.analysisFailedWithContext(
                    "Analysis failed",
                    "com.example.Service",
                    "process",
                    "(Ljava/lang/Object;)V",
                    cause);

            assertThat(ex.getMessage())
                    .contains("Analysis failed")
                    .contains("com.example.Service")
                    .contains("process")
                    .contains("(Ljava/lang/Object;)V");
            assertThat(ex.getCause()).isSameAs(cause);
        }

        @Test
        void analysisFailedWithContext_withNullClassName_formatsCorrectly() {
            RuntimeException cause = new RuntimeException("cause");
            BytecodeAnalysisException ex = BytecodeAnalysisException.analysisFailedWithContext(
                    "Error", null, "method", "desc", cause);

            assertThat(ex.getMessage())
                    .contains("method")
                    .contains("desc")
                    .doesNotContain("class=");
        }

        @Test
        void analysisFailedWithContext_withNullMethodName_formatsCorrectly() {
            RuntimeException cause = new RuntimeException("cause");
            BytecodeAnalysisException ex = BytecodeAnalysisException.analysisFailedWithContext(
                    "Error", "Class", null, "desc", cause);

            assertThat(ex.getMessage())
                    .contains("Class")
                    .contains("desc")
                    .doesNotContain("method=");
        }

        @Test
        void analysisFailedWithContext_withNullDescriptor_formatsCorrectly() {
            RuntimeException cause = new RuntimeException("cause");
            BytecodeAnalysisException ex = BytecodeAnalysisException.analysisFailedWithContext(
                    "Error", "Class", "method", null, cause);

            assertThat(ex.getMessage())
                    .contains("Class")
                    .contains("method")
                    .doesNotContain("descriptor=");
        }

        @Test
        void analysisFailedWithContext_withAllNullContext_formatsCorrectly() {
            RuntimeException cause = new RuntimeException("cause");
            BytecodeAnalysisException ex = BytecodeAnalysisException.analysisFailedWithContext(
                    "Error message", null, null, null, cause);

            assertThat(ex.getMessage()).contains("Error message");
            assertThat(ex.getCause()).isSameAs(cause);
        }
    }

    // Exception Hierarchy Tests

    @Nested
    class ExceptionHierarchyTests {

        @Test
        void isRuntimeException() {
            BytecodeAnalysisException ex = new BytecodeAnalysisException("test");
            assertThat(ex).isInstanceOf(RuntimeException.class);
        }

        @Test
        void canBeCaughtAsRuntimeException() {
            try {
                throw BytecodeAnalysisException.unexpectedNull("test");
            } catch (RuntimeException e) {
                assertThat(e).isInstanceOf(BytecodeAnalysisException.class);
            }
        }
    }
}
