package io.quarkiverse.qubit.deployment.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for BytecodeAnalysisException factory methods.
 * Verifies that error messages are correctly formatted with all context.
 */
@DisplayName("BytecodeAnalysisException Factory Method Tests")
class BytecodeAnalysisExceptionTest {

    @Nested
    @DisplayName("stackUnderflow factory method")
    class StackUnderflowTests {

        @Test
        @DisplayName("A1: Creates message with instruction, expected, and actual counts")
        void stackUnderflow_createsMessageWithDetails() {
            // When
            BytecodeAnalysisException exception = BytecodeAnalysisException.stackUnderflow("IADD", 2, 1);

            // Then
            assertThat(exception)
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Stack underflow")
                    .hasMessageContaining("IADD")
                    .hasMessageContaining("expected 2")
                    .hasMessageContaining("found 1");
        }

        @Test
        @DisplayName("stackUnderflow with zero actual elements")
        void stackUnderflow_withZeroActual_includesZeroInMessage() {
            BytecodeAnalysisException exception = BytecodeAnalysisException.stackUnderflow("GETFIELD", 1, 0);

            assertThat(exception.getMessage())
                    .contains("expected 1")
                    .contains("found 0");
        }
    }

    @Nested
    @DisplayName("invalidOpcode factory method")
    class InvalidOpcodeTests {

        @Test
        @DisplayName("A2: Includes invalid opcode and list of valid opcodes")
        void invalidOpcode_includesValidOpcodes() {
            // When
            BytecodeAnalysisException exception = BytecodeAnalysisException.invalidOpcode(99, 96, 100, 104);

            // Then
            assertThat(exception.getMessage())
                    .contains("Invalid opcode")
                    .contains("99")
                    .contains("96")
                    .contains("100")
                    .contains("104");
        }

        @Test
        @DisplayName("invalidOpcode with single valid opcode")
        void invalidOpcode_withSingleValidOpcode_formatsCorrectly() {
            BytecodeAnalysisException exception = BytecodeAnalysisException.invalidOpcode(50, 100);

            assertThat(exception.getMessage())
                    .contains("Invalid opcode: 50")
                    .contains("expected one of [100]");
        }

        @Test
        @DisplayName("invalidOpcode with no valid opcodes")
        void invalidOpcode_withNoValidOpcodes_formatsEmptyList() {
            BytecodeAnalysisException exception = BytecodeAnalysisException.invalidOpcode(50);

            assertThat(exception.getMessage())
                    .contains("Invalid opcode: 50")
                    .contains("expected one of []");
        }
    }

    @Nested
    @DisplayName("unexpectedNull factory method")
    class UnexpectedNullTests {

        @Test
        @DisplayName("A3: Includes context in message")
        void unexpectedNull_includesContext() {
            // When
            BytecodeAnalysisException exception = BytecodeAnalysisException.unexpectedNull("expression target");

            // Then
            assertThat(exception.getMessage())
                    .contains("Unexpected null value")
                    .contains("expression target");
        }

        @Test
        @DisplayName("unexpectedNull with detailed context")
        void unexpectedNull_withDetailedContext_includesFullContext() {
            BytecodeAnalysisException exception = BytecodeAnalysisException.unexpectedNull(
                    "method call target in ComparisonHandler at line 45");

            assertThat(exception.getMessage())
                    .contains("method call target")
                    .contains("ComparisonHandler");
        }
    }

    @Nested
    @DisplayName("unexpectedOpcode factory method")
    class UnexpectedOpcodeTests {

        @Test
        @DisplayName("A4: Includes handler context and opcode with hex format")
        void unexpectedOpcode_includesHandlerContext() {
            // When
            BytecodeAnalysisException exception = BytecodeAnalysisException.unexpectedOpcode(
                    "ArithmeticHandler", 182);

            // Then
            assertThat(exception.getMessage())
                    .contains("Unexpected opcode")
                    .contains("ArithmeticHandler")
                    .contains("182")
                    .contains("0xB6") // hex format
                    .contains("unsupported bytecode");
        }

        @Test
        @DisplayName("unexpectedOpcode formats low opcodes correctly")
        void unexpectedOpcode_withLowOpcode_formatsHexWithLeadingZero() {
            BytecodeAnalysisException exception = BytecodeAnalysisException.unexpectedOpcode(
                    "LoadHandler", 10);

            assertThat(exception.getMessage())
                    .contains("10")
                    .contains("0x0A");
        }
    }

    @Nested
    @DisplayName("unsupported factory method")
    class UnsupportedTests {

        @Test
        @DisplayName("A5: Includes operation and details")
        void unsupported_includesOperationAndDetails() {
            // When
            BytecodeAnalysisException exception = BytecodeAnalysisException.unsupported(
                    "nested lambda", "lambda captures 'this' reference");

            // Then
            assertThat(exception.getMessage())
                    .contains("Unsupported")
                    .contains("nested lambda")
                    .contains("lambda captures 'this' reference")
                    .contains("cannot be translated to a database query");
        }

        @Test
        @DisplayName("unsupported with method call context")
        void unsupported_withMethodCallContext_includesMethodDetails() {
            BytecodeAnalysisException exception = BytecodeAnalysisException.unsupported(
                    "method call", "String.format() is not a JPA-translatable operation");

            assertThat(exception.getMessage())
                    .contains("Unsupported method call")
                    .contains("String.format()");
        }
    }

    @Nested
    @DisplayName("Constructor tests")
    class ConstructorTests {

        @Test
        @DisplayName("Constructor with message only")
        void constructor_withMessage_setsMessage() {
            BytecodeAnalysisException exception = new BytecodeAnalysisException("Test message");

            assertThat(exception.getMessage()).isEqualTo("Test message");
            assertThat(exception.getCause()).isNull();
        }

        @Test
        @DisplayName("Constructor with message and cause")
        void constructor_withMessageAndCause_setsBoth() {
            Throwable cause = new RuntimeException("Root cause");
            BytecodeAnalysisException exception = new BytecodeAnalysisException("Wrapper message", cause);

            assertThat(exception.getMessage()).isEqualTo("Wrapper message");
            assertThat(exception.getCause()).isSameAs(cause);
        }
    }
}
