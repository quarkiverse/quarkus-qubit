package io.quarkiverse.qubit.deployment.generation.methodcall;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class GenerationResultTest {

    @Nested
    class SuccessTests {

        @Test
        void success_withNullHandle_throwsNullPointerException() {
            assertThatThrownBy(() -> new GenerationResult.Success(null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("cannot be null");
        }

    }

    @Nested
    class UnsupportedTests {

        @Test
        void unsupported_withNullMethodName_throwsNullPointerException() {
            assertThatThrownBy(() -> new GenerationResult.Unsupported(null, "reason"))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("methodName cannot be null");
        }

        @Test
        void unsupported_withNullReason_throwsNullPointerException() {
            assertThatThrownBy(() -> new GenerationResult.Unsupported("method", null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("reason cannot be null");
        }

        @Test
        void unsupported_noHandlerFound_createsWithStandardReason() {
            GenerationResult.Unsupported unsupported = GenerationResult.Unsupported.noHandlerFound("customMethod");

            assertThat(unsupported.methodName()).isEqualTo("customMethod");
            assertThat(unsupported.reason()).contains("No handler found");
            assertThat(unsupported.reason()).contains("customMethod");
        }
    }
}
