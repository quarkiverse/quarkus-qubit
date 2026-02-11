package io.quarkiverse.qubit.runtime.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import io.quarkiverse.qubit.CapturedVariableExtractionException;

/**
 * Unit tests for {@link CapturedVariableExtractor}.
 */
@DisplayName("CapturedVariableExtractor")
class CapturedVariableExtractorTest {

    @AfterEach
    void clearCache() {
        CapturedVariableExtractor.clearCache();
    }

    @Nested
    @DisplayName("extract()")
    class ExtractTests {

        @Test
        @DisplayName("throws on null lambda instance")
        void throwsOnNullLambda() {
            assertThatThrownBy(() -> CapturedVariableExtractor.extract(null, 1))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("null");
        }

        @Test
        @DisplayName("returns empty array for count 0")
        void returnsEmptyArrayForZeroCount() {
            Object[] result = CapturedVariableExtractor.extract("any object", 0);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("returns same empty array instance for count 0")
        void returnsSameEmptyArrayInstance() {
            Object[] result1 = CapturedVariableExtractor.extract("any", 0);
            Object[] result2 = CapturedVariableExtractor.extract("other", 0);

            assertThat(result1).isSameAs(result2);
        }
    }

    @Nested
    @DisplayName("Cache operations")
    class CacheOperationTests {

        @Test
        @DisplayName("clearCache sets size to zero")
        void clearCacheSetsSizeToZero() {
            CapturedVariableExtractor.clearCache();

            assertThat(CapturedVariableExtractor.getCacheSize()).isZero();
        }

        @Test
        @DisplayName("getCacheSize returns current cache size")
        void getCacheSizeReturnsCurrentSize() {
            CapturedVariableExtractor.clearCache();

            int size = CapturedVariableExtractor.getCacheSize();

            assertThat(size).isGreaterThanOrEqualTo(0);
        }
    }

    @Nested
    @DisplayName("Error handling")
    class ErrorHandlingTests {

        @Test
        @DisplayName("throws CapturedVariableExtractionException for unsupported class")
        void throwsForUnsupportedClass() {
            // A simple object without captured variable fields
            Object regularObject = new Object();

            assertThatThrownBy(() -> CapturedVariableExtractor.extract(regularObject, 1))
                    .isInstanceOf(CapturedVariableExtractionException.class);
        }
    }
}
