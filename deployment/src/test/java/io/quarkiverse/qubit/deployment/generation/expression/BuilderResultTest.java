package io.quarkiverse.qubit.deployment.generation.expression;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import io.quarkus.gizmo.ResultHandle;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link BuilderResult} sealed interface.
 */
@DisplayName("BuilderResult")
class BuilderResultTest {

    @Nested
    @DisplayName("Success record")
    class SuccessTests {

        @Test
        @DisplayName("throws NullPointerException for null value")
        void throwsForNullValue() {
            assertThatThrownBy(() -> new BuilderResult.Success(null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("cannot be null");
        }

        @Test
        @DisplayName("isSuccess returns true")
        void isSuccessReturnsTrue() {
            ResultHandle handle = mock(ResultHandle.class);
            BuilderResult result = new BuilderResult.Success(handle);

            assertThat(result.isSuccess()).isTrue();
        }

        @Test
        @DisplayName("getOrThrow returns the value")
        void getOrThrowReturnsValue() {
            ResultHandle handle = mock(ResultHandle.class);
            BuilderResult result = new BuilderResult.Success(handle);

            assertThat(result.getOrThrow()).isSameAs(handle);
        }

        @Test
        @DisplayName("toOptional returns present Optional")
        void toOptionalReturnsPresent() {
            ResultHandle handle = mock(ResultHandle.class);
            BuilderResult result = new BuilderResult.Success(handle);

            assertThat(result.toOptional())
                    .isPresent()
                    .containsSame(handle);
        }
    }

    @Nested
    @DisplayName("NotApplicable record")
    class NotApplicableTests {

        @Test
        @DisplayName("isSuccess returns false")
        void isSuccessReturnsFalse() {
            BuilderResult result = BuilderResult.NOT_APPLICABLE;

            assertThat(result.isSuccess()).isFalse();
        }

        @Test
        @DisplayName("getOrThrow throws IllegalStateException")
        void getOrThrowThrows() {
            BuilderResult result = BuilderResult.NOT_APPLICABLE;

            assertThatThrownBy(result::getOrThrow)
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("toOptional returns empty Optional")
        void toOptionalReturnsEmpty() {
            BuilderResult result = BuilderResult.NOT_APPLICABLE;

            assertThat(result.toOptional()).isEmpty();
        }
    }

    @Nested
    @DisplayName("Factory methods")
    class FactoryMethodTests {

        @Test
        @DisplayName("success() creates Success instance")
        void successFactory() {
            ResultHandle handle = mock(ResultHandle.class);

            BuilderResult result = BuilderResult.success(handle);

            assertThat(result).isInstanceOf(BuilderResult.Success.class);
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getOrThrow()).isSameAs(handle);
        }

        @Test
        @DisplayName("notApplicable() returns singleton")
        void notApplicableFactory() {
            BuilderResult result = BuilderResult.notApplicable();

            assertThat(result)
                    .isSameAs(BuilderResult.NOT_APPLICABLE)
                    .isInstanceOf(BuilderResult.NotApplicable.class);
        }

        @Test
        @DisplayName("fromNullable with non-null returns Success")
        void fromNullableWithValue() {
            ResultHandle handle = mock(ResultHandle.class);

            BuilderResult result = BuilderResult.fromNullable(handle);

            assertThat(result).isInstanceOf(BuilderResult.Success.class);
            assertThat(result.getOrThrow()).isSameAs(handle);
        }

        @Test
        @DisplayName("fromNullable with null returns NotApplicable")
        void fromNullableWithNull() {
            BuilderResult result = BuilderResult.fromNullable(null);

            assertThat(result).isSameAs(BuilderResult.NOT_APPLICABLE);
        }
    }

}
