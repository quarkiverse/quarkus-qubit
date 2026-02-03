package io.quarkiverse.qubit.deployment.generation.methodcall;

import io.quarkus.gizmo2.Const;
import io.quarkus.gizmo2.Expr;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link GenerationResult} sealed interface and its implementations.
 */
class GenerationResultTest {

    // ========================================================================
    // Success Tests
    // ========================================================================

    @Nested
    class SuccessTests {

        @Test
        void success_isSuccessReturnsTrue() {
            Expr handle = Const.of("test");
            GenerationResult result = new GenerationResult.Success(handle);

            assertThat(result.isSuccess()).isTrue();
        }

        @Test
        void success_withNullHandle_throwsNullPointerException() {
            assertThatThrownBy(() -> new GenerationResult.Success(null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("cannot be null");
        }

        @Test
        void success_getOrThrow_returnsValue() {
            Expr handle = Const.of("test");
            GenerationResult result = new GenerationResult.Success(handle);

            Expr returned = result.getOrThrow();

            assertThat(returned).isSameAs(handle);
        }

        @Test
        void success_orElse_returnsValue() {
            Expr handle = Const.of("handle");
            Expr fallback = Const.of("fallback");
            GenerationResult result = new GenerationResult.Success(handle);

            Expr returned = result.orElse(fallback);

            assertThat(returned).isSameAs(handle);
        }

        @Test
        void success_map_appliesMapper() {
            Expr original = Const.of("original");
            Expr mapped = Const.of("mapped");
            GenerationResult result = new GenerationResult.Success(original);

            GenerationResult mappedResult = result.map(rh -> mapped);

            assertThat(mappedResult).isInstanceOf(GenerationResult.Success.class);
            assertThat(((GenerationResult.Success) mappedResult).value()).isSameAs(mapped);
        }

        @Test
        void success_factoryMethod_createsSuccess() {
            Expr handle = Const.of("test");

            GenerationResult result = GenerationResult.success(handle);

            assertThat(result).isInstanceOf(GenerationResult.Success.class);
            assertThat(((GenerationResult.Success) result).value()).isSameAs(handle);
        }
    }

    // ========================================================================
    // Unsupported Tests
    // ========================================================================

    @Nested
    class UnsupportedTests {

        @Test
        void unsupported_isSuccessReturnsFalse() {
            GenerationResult result = new GenerationResult.Unsupported("method", "reason");

            assertThat(result.isSuccess()).isFalse();
        }

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
        void unsupported_getOrThrow_throwsIllegalStateException() {
            GenerationResult result = new GenerationResult.Unsupported("getValue", "Not supported");

            assertThatThrownBy(result::getOrThrow)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Cannot get value from Unsupported");
        }

        @Test
        void unsupported_orElse_returnsFallback() {
            Expr fallback = Const.of("test");
            GenerationResult result = new GenerationResult.Unsupported("method", "reason");

            Expr returned = result.orElse(fallback);

            assertThat(returned).isSameAs(fallback);
        }

        @Test
        void unsupported_map_returnsSameUnsupported() {
            GenerationResult.Unsupported original = new GenerationResult.Unsupported("method", "reason");

            GenerationResult mappedResult = original.map(rh -> Const.of("test"));

            assertThat(mappedResult).isSameAs(original);
        }

        @Test
        void unsupported_noHandlerFound_createsWithStandardReason() {
            GenerationResult.Unsupported unsupported = GenerationResult.Unsupported.noHandlerFound("customMethod");

            assertThat(unsupported.methodName()).isEqualTo("customMethod");
            assertThat(unsupported.reason()).contains("No handler found");
            assertThat(unsupported.reason()).contains("customMethod");
        }

        @Test
        void unsupported_factoryMethod_createsUnsupported() {
            GenerationResult result = GenerationResult.unsupported("method", "reason");

            assertThat(result).isInstanceOf(GenerationResult.Unsupported.class);
            GenerationResult.Unsupported unsupported = (GenerationResult.Unsupported) result;
            assertThat(unsupported.methodName()).isEqualTo("method");
            assertThat(unsupported.reason()).isEqualTo("reason");
        }
    }

}
