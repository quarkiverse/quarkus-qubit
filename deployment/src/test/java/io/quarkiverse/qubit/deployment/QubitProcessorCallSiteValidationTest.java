package io.quarkiverse.qubit.deployment;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import io.quarkiverse.qubit.deployment.analysis.CallSite;

/**
 * Tests for duplicate call site ID detection in QubitProcessor.
 * <p>
 * This validation prevents silent data corruption when multiple queries
 * appear on the same source line.
 */
class QubitProcessorCallSiteValidationTest {

    private QubitProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new QubitProcessor();
    }

    /**
     * Creates a SimpleCallSite for testing.
     * Uses the minimum required fields to generate a valid call site ID.
     */
    private CallSite createCallSite(String className, String methodName, int lineNumber) {
        CallSite.Common common = new CallSite.Common(
                className, methodName, lineNumber, "toList", 100, false, null, null);
        return new CallSite.SimpleCallSite(
                common,
                new CallSite.LambdaPair("lambda$0", "(Ljava/lang/Object;)Z"),
                "where",
                null, // predicateLambdas
                null, // projectionLambda
                null // sortLambdas
        );
    }

    @Nested
    class ValidateUniqueCallSiteIds {

        @Test
        void acceptsEmptyList() {
            assertThatCode(() -> processor.validateUniqueCallSiteIds(List.of()))
                    .doesNotThrowAnyException();
        }

        @Test
        void acceptsSingleCallSite() {
            CallSite callSite = createCallSite("com.example.MyClass", "myMethod", 42);

            assertThatCode(() -> processor.validateUniqueCallSiteIds(List.of(callSite)))
                    .doesNotThrowAnyException();
        }

        @Test
        void acceptsMultipleCallSitesOnDifferentLines() {
            CallSite callSite1 = createCallSite("com.example.MyClass", "myMethod", 10);
            CallSite callSite2 = createCallSite("com.example.MyClass", "myMethod", 20);
            CallSite callSite3 = createCallSite("com.example.MyClass", "myMethod", 30);

            assertThatCode(() -> processor.validateUniqueCallSiteIds(List.of(callSite1, callSite2, callSite3)))
                    .doesNotThrowAnyException();
        }

        @Test
        void acceptsMultipleCallSitesInDifferentMethods() {
            CallSite callSite1 = createCallSite("com.example.MyClass", "method1", 10);
            CallSite callSite2 = createCallSite("com.example.MyClass", "method2", 10);

            assertThatCode(() -> processor.validateUniqueCallSiteIds(List.of(callSite1, callSite2)))
                    .doesNotThrowAnyException();
        }

        @Test
        void acceptsMultipleCallSitesInDifferentClasses() {
            CallSite callSite1 = createCallSite("com.example.ClassA", "myMethod", 10);
            CallSite callSite2 = createCallSite("com.example.ClassB", "myMethod", 10);

            assertThatCode(() -> processor.validateUniqueCallSiteIds(List.of(callSite1, callSite2)))
                    .doesNotThrowAnyException();
        }

        @Test
        void rejectsDuplicateCallSitesOnSameLine() {
            CallSite callSite1 = createCallSite("com.example.MyClass", "myMethod", 42);
            CallSite callSite2 = createCallSite("com.example.MyClass", "myMethod", 42);

            assertThatThrownBy(() -> processor.validateUniqueCallSiteIds(List.of(callSite1, callSite2)))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("QUBIT BUILD ERROR: Duplicate call site IDs detected!")
                    .hasMessageContaining("com.example.MyClass:myMethod:42")
                    .hasMessageContaining("Found 2 queries on this line");
        }

        @Test
        void rejectsTripleDuplicateCallSitesOnSameLine() {
            CallSite callSite1 = createCallSite("com.example.MyClass", "myMethod", 42);
            CallSite callSite2 = createCallSite("com.example.MyClass", "myMethod", 42);
            CallSite callSite3 = createCallSite("com.example.MyClass", "myMethod", 42);

            assertThatThrownBy(() -> processor.validateUniqueCallSiteIds(List.of(callSite1, callSite2, callSite3)))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Found 3 queries on this line");
        }

        @Test
        void detectsMultipleDuplicateGroups() {
            // Two pairs of duplicates on different lines
            CallSite line10_a = createCallSite("com.example.MyClass", "myMethod", 10);
            CallSite line10_b = createCallSite("com.example.MyClass", "myMethod", 10);
            CallSite line20_a = createCallSite("com.example.MyClass", "myMethod", 20);
            CallSite line20_b = createCallSite("com.example.MyClass", "myMethod", 20);

            assertThatThrownBy(() -> processor.validateUniqueCallSiteIds(
                    List.of(line10_a, line10_b, line20_a, line20_b)))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("com.example.MyClass:myMethod:10")
                    .hasMessageContaining("com.example.MyClass:myMethod:20");
        }

        @Test
        void errorMessageContainsHelpfulInstructions() {
            CallSite callSite1 = createCallSite("com.example.MyClass", "myMethod", 42);
            CallSite callSite2 = createCallSite("com.example.MyClass", "myMethod", 42);

            assertThatThrownBy(() -> processor.validateUniqueCallSiteIds(List.of(callSite1, callSite2)))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("FIX: Move each query to a separate source line")
                    .hasMessageContaining("Example - WRONG:")
                    .hasMessageContaining("Example - CORRECT:");
        }

        @Test
        void mixedValidAndDuplicateCallSites() {
            // Some valid, some duplicate
            CallSite valid1 = createCallSite("com.example.MyClass", "method1", 10);
            CallSite valid2 = createCallSite("com.example.MyClass", "method2", 20);
            CallSite duplicate1 = createCallSite("com.example.MyClass", "method3", 30);
            CallSite duplicate2 = createCallSite("com.example.MyClass", "method3", 30);

            assertThatThrownBy(() -> processor.validateUniqueCallSiteIds(
                    List.of(valid1, valid2, duplicate1, duplicate2)))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("com.example.MyClass:method3:30");
        }
    }
}
