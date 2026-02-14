package io.quarkiverse.qubit.runtime.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import jakarta.persistence.NoResultException;
import jakarta.persistence.NonUniqueResultException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link LambdaReflectionUtils} validation utility methods.
 * <p>
 * Tests the pure utility methods that don't require CDI context or complex lambda reflection.
 * Methods requiring Arc container or real lambda instances are tested via integration tests.
 */
@DisplayName("LambdaReflectionUtils")
class LambdaReflectionUtilsTest {

    @Nested
    @DisplayName("requireNonNullLambda()")
    class RequireNonNullLambdaTests {

        @Test
        @DisplayName("returns lambda when not null")
        void returnsLambdaWhenNotNull() {
            String lambda = "test-lambda";

            String result = LambdaReflectionUtils.requireNonNullLambda(lambda, "predicate", "where");

            assertThat(result).isEqualTo("test-lambda");
        }

        @Test
        @DisplayName("throws IllegalArgumentException when null")
        void throwsIllegalArgumentExceptionWhenNull() {
            assertThatThrownBy(() -> LambdaReflectionUtils.requireNonNullLambda(null, "predicate", "where"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("predicate")
                    .hasMessageContaining("null")
                    .hasMessageContaining("where");
        }

        @Test
        @DisplayName("includes param name and method name in error message")
        void includesParamNameAndMethodNameInErrorMessage() {
            assertThatThrownBy(() -> LambdaReflectionUtils.requireNonNullLambda(null, "mapper", "select"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("mapper")
                    .hasMessageContaining("select");
        }
    }

    @Nested
    @DisplayName("countCapturedFields()")
    class CountCapturedFieldsTests {

        @Test
        @DisplayName("returns 0 for null instance")
        void returnsZeroForNullInstance() {
            int count = LambdaReflectionUtils.countCapturedFields(null);

            assertThat(count).isZero();
        }

        @Test
        @DisplayName("returns 0 for class with only static fields")
        void returnsZeroForClassWithOnlyStaticFields() {
            int count = LambdaReflectionUtils.countCapturedFields(new ClassWithStaticFieldsOnly());

            assertThat(count).isZero();
        }

        @Test
        @DisplayName("counts non-static instance fields")
        void countsNonStaticInstanceFields() {
            int count = LambdaReflectionUtils.countCapturedFields(new ClassWithInstanceFields());

            // ClassWithInstanceFields has 3 instance fields
            assertThat(count).isEqualTo(3);
        }

        @Test
        @DisplayName("excludes static fields from count")
        void excludesStaticFieldsFromCount() {
            int count = LambdaReflectionUtils.countCapturedFields(new ClassWithMixedFields());

            // ClassWithMixedFields has 2 instance fields and 1 static field
            assertThat(count).isEqualTo(2);
        }

        // Test helper classes
        @SuppressWarnings("unused")
        static class ClassWithStaticFieldsOnly {
            private static final String CONSTANT = "value";
            private static int counter = 0;
        }

        @SuppressWarnings("unused")
        static class ClassWithInstanceFields {
            private String field1 = "a";
            private int field2 = 1;
            private Object field3 = new Object();
        }

        @SuppressWarnings("unused")
        static class ClassWithMixedFields {
            private static final String CONSTANT = "value";
            private String instanceField1 = "a";
            private int instanceField2 = 1;
        }
    }

    @Nested
    @DisplayName("validateSkipCount()")
    class ValidateSkipCountTests {

        @Test
        @DisplayName("returns count when non-negative")
        void returnsCountWhenNonNegative() {
            assertThat(LambdaReflectionUtils.validateSkipCount(0)).isZero();
            assertThat(LambdaReflectionUtils.validateSkipCount(1)).isEqualTo(1);
            assertThat(LambdaReflectionUtils.validateSkipCount(100)).isEqualTo(100);
        }

        @Test
        @DisplayName("throws IllegalArgumentException for negative count")
        void throwsIllegalArgumentExceptionForNegativeCount() {
            assertThatThrownBy(() -> LambdaReflectionUtils.validateSkipCount(-1))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("skip count")
                    .hasMessageContaining("-1");
        }

        @Test
        @DisplayName("error message contains the invalid value")
        void errorMessageContainsTheInvalidValue() {
            assertThatThrownBy(() -> LambdaReflectionUtils.validateSkipCount(-42))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("-42");
        }
    }

    @Nested
    @DisplayName("validateLimitCount()")
    class ValidateLimitCountTests {

        @Test
        @DisplayName("returns count when non-negative")
        void returnsCountWhenNonNegative() {
            assertThat(LambdaReflectionUtils.validateLimitCount(0)).isZero();
            assertThat(LambdaReflectionUtils.validateLimitCount(1)).isEqualTo(1);
            assertThat(LambdaReflectionUtils.validateLimitCount(100)).isEqualTo(100);
        }

        @Test
        @DisplayName("throws IllegalArgumentException for negative count")
        void throwsIllegalArgumentExceptionForNegativeCount() {
            assertThatThrownBy(() -> LambdaReflectionUtils.validateLimitCount(-1))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("limit count")
                    .hasMessageContaining("-1");
        }

        @Test
        @DisplayName("error message contains the invalid value")
        void errorMessageContainsTheInvalidValue() {
            assertThatThrownBy(() -> LambdaReflectionUtils.validateLimitCount(-99))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("-99");
        }
    }

    @Nested
    @DisplayName("requireSingleResult()")
    class RequireSingleResultTests {

        @Test
        @DisplayName("returns single element from list")
        void returnsSingleElementFromList() {
            List<String> singleElement = List.of("only");

            String result = LambdaReflectionUtils.requireSingleResult(singleElement);

            assertThat(result).isEqualTo("only");
        }

        @Test
        @DisplayName("throws NoResultException for empty list")
        void throwsNoResultExceptionForEmptyList() {
            List<String> empty = List.of();

            assertThatThrownBy(() -> LambdaReflectionUtils.requireSingleResult(empty))
                    .isInstanceOf(NoResultException.class)
                    .hasMessageContaining("none");
        }

        @Test
        @DisplayName("throws NonUniqueResultException for multiple elements")
        void throwsNonUniqueResultExceptionForMultipleElements() {
            List<String> multiple = List.of("a", "b");

            assertThatThrownBy(() -> LambdaReflectionUtils.requireSingleResult(multiple))
                    .isInstanceOf(NonUniqueResultException.class)
                    .hasMessageContaining("2");
        }

        @Test
        @DisplayName("error message contains actual count for multiple elements")
        void errorMessageContainsActualCountForMultipleElements() {
            List<String> fiveElements = List.of("a", "b", "c", "d", "e");

            assertThatThrownBy(() -> LambdaReflectionUtils.requireSingleResult(fiveElements))
                    .isInstanceOf(NonUniqueResultException.class)
                    .hasMessageContaining("5");
        }

        @Test
        @DisplayName("handles null element in single-element list")
        void handlesNullElementInSingleElementList() {
            List<String> singleNull = new ArrayList<>();
            singleNull.add(null);

            String result = LambdaReflectionUtils.requireSingleResult(singleNull);

            assertThat(result).isNull();
        }
    }

    @Nested
    @DisplayName("extractFromLambdas()")
    class ExtractFromLambdasTests {

        @Test
        @DisplayName("does not modify destination when no lambdas")
        void doesNotModifyDestinationWhenNoLambdas() {
            List<Object> destination = new ArrayList<>();

            LambdaReflectionUtils.extractFromLambdas(List.of(), destination);

            assertThat(destination).isEmpty();
        }

        @Test
        @DisplayName("extracts captured args from all lambdas in list")
        void extractsCapturedArgsFromAllLambdas() {
            String first = "a";
            String second = "b";
            Supplier<String> lambda1 = (Supplier<String> & Serializable) () -> first;
            Supplier<String> lambda2 = (Supplier<String> & Serializable) () -> second;
            List<Object> destination = new ArrayList<>();

            LambdaReflectionUtils.extractFromLambdas(List.of(lambda1, lambda2), destination);

            assertThat(destination).containsExactly("a", "b");
        }
    }

    @Nested
    @DisplayName("extractFromSingleLambda()")
    class ExtractFromSingleLambdaTests {

        @Test
        @DisplayName("does not modify destination when lambda has no captures")
        void doesNotModifyDestinationWhenNoCapturedFields() {
            List<Object> destination = new ArrayList<>();
            Supplier<String> lambda = (Supplier<String> & Serializable) () -> "hello";

            LambdaReflectionUtils.extractFromSingleLambda(lambda, destination);

            assertThat(destination).isEmpty();
        }

        @Test
        @DisplayName("adds captured args to destination")
        void addsCapturedArgsToDestination() {
            List<Object> destination = new ArrayList<>();
            String captured = "value";
            Supplier<String> lambda = (Supplier<String> & Serializable) () -> captured;

            LambdaReflectionUtils.extractFromSingleLambda(lambda, destination);

            assertThat(destination).containsExactly("value");
        }
    }

    @Nested
    @DisplayName("extractCapturedArgs()")
    class ExtractCapturedArgsTests {

        @Test
        @DisplayName("returns empty array for null")
        void returnsEmptyArrayForNull() {
            Object[] result = LambdaReflectionUtils.extractCapturedArgs(null);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("returns empty array for lambda with no captures")
        void returnsEmptyArrayForLambdaWithNoCaptures() {
            Supplier<String> lambda = (Supplier<String> & Serializable) () -> "constant";

            Object[] result = LambdaReflectionUtils.extractCapturedArgs(lambda);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("extracts captured variable from lambda")
        void extractsCapturedVariable() {
            String captured = "test-value";
            Supplier<String> lambda = (Supplier<String> & Serializable) () -> captured;

            Object[] result = LambdaReflectionUtils.extractCapturedArgs(lambda);

            assertThat(result).containsExactly("test-value");
        }

        @Test
        @DisplayName("extracts multiple captured variables")
        void extractsMultipleCapturedVariables() {
            String first = "hello";
            int second = 42;
            Supplier<String> lambda = (Supplier<String> & Serializable) () -> first + second;

            Object[] result = LambdaReflectionUtils.extractCapturedArgs(lambda);

            assertThat(result).containsExactly("hello", 42);
        }
    }

    @Nested
    @DisplayName("LambdaInfo")
    class LambdaInfoTests {

        @Test
        @DisplayName("extract returns null method name for null instance")
        void extractReturnsNullMethodNameForNullInstance() {
            LambdaReflectionUtils.LambdaInfo info = LambdaReflectionUtils.LambdaInfo.extract(null);

            assertThat(info.implMethodName()).isEqualTo("null");
            assertThat(info.capturedArgs()).isEmpty();
        }

        @Test
        @DisplayName("extract returns method name and captured args")
        void extractReturnsMethodNameAndCapturedArgs() {
            String value = "captured";
            Supplier<String> lambda = (Supplier<String> & Serializable) () -> value;

            LambdaReflectionUtils.LambdaInfo info = LambdaReflectionUtils.LambdaInfo.extract(lambda);

            assertThat(info.implMethodName()).startsWith("lambda$");
            assertThat(info.capturedArgs()).containsExactly("captured");
        }
    }

    @Nested
    @DisplayName("clearCachedRegistry()")
    class ClearCachedRegistryTests {

        @Test
        @DisplayName("does not throw when called")
        void doesNotThrowWhenCalled() {
            // This just verifies the method can be called without error
            // The actual cache clearing is verified by integration tests
            LambdaReflectionUtils.clearCachedRegistry();
            // No exception means success
        }

        @Test
        @DisplayName("can be called multiple times")
        void canBeCalledMultipleTimes() {
            LambdaReflectionUtils.clearCachedRegistry();
            LambdaReflectionUtils.clearCachedRegistry();
            LambdaReflectionUtils.clearCachedRegistry();
            // No exception means success
        }
    }

    @Nested
    @DisplayName("extractLambdaMethodName()")
    class ExtractLambdaMethodNameTests {

        @Test
        @DisplayName("returns 'null' string for null input")
        void returnsNullStringForNullInput() {
            String result = LambdaReflectionUtils.extractLambdaMethodName(null);

            assertThat(result).isEqualTo("null");
        }

        @Test
        @DisplayName("throws for non-Serializable lambda")
        void throwsForNonSerializableLambda() {
            Object nonSerializable = new Object();

            assertThatThrownBy(() -> LambdaReflectionUtils.extractLambdaMethodName(nonSerializable))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Serializable");
        }
    }
}
