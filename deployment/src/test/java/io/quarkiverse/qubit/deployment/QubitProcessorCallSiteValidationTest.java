package io.quarkiverse.qubit.deployment;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import io.quarkiverse.qubit.deployment.analysis.InvokeDynamicScanner.LambdaCallSite;

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
     * Creates a mock LambdaCallSite for testing.
     * Uses the minimum required fields to generate a valid call site ID.
     */
    private LambdaCallSite createCallSite(String className, String methodName, int lineNumber) {
        return new LambdaCallSite(
                className, // ownerClassName
                methodName, // methodName
                "lambda$0", // lambdaMethodName
                "(Ljava/lang/Object;)Z", // lambdaMethodDescriptor
                "where", // fluentMethodName
                "toList", // targetMethodName
                lineNumber, // lineNumber
                100, // terminalInsnIndex
                null, // projectionLambdaMethodName
                null, // projectionLambdaMethodDescriptor
                null, // predicateLambdas
                null, // sortLambdas
                null, // aggregationLambdaMethodName
                null, // aggregationLambdaMethodDescriptor
                null, // joinType
                null, // joinRelationshipLambdaMethodName
                null, // joinRelationshipLambdaDescriptor
                null, // biEntityPredicateLambdas
                false, // isSelectJoined
                null, // biEntityProjectionLambdaMethodName
                null, // biEntityProjectionLambdaDescriptor
                false, // isGroupQuery
                null, // groupByLambdaMethodName
                null, // groupByLambdaDescriptor
                null, // havingLambdas
                null, // groupSelectLambdas
                null, // groupSortLambdas
                false, // isGroupSelectKey
                false, // hasDistinct
                null, // skipValue
                null // limitValue
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
            LambdaCallSite callSite = createCallSite("com.example.MyClass", "myMethod", 42);

            assertThatCode(() -> processor.validateUniqueCallSiteIds(List.of(callSite)))
                    .doesNotThrowAnyException();
        }

        @Test
        void acceptsMultipleCallSitesOnDifferentLines() {
            LambdaCallSite callSite1 = createCallSite("com.example.MyClass", "myMethod", 10);
            LambdaCallSite callSite2 = createCallSite("com.example.MyClass", "myMethod", 20);
            LambdaCallSite callSite3 = createCallSite("com.example.MyClass", "myMethod", 30);

            assertThatCode(() -> processor.validateUniqueCallSiteIds(List.of(callSite1, callSite2, callSite3)))
                    .doesNotThrowAnyException();
        }

        @Test
        void acceptsMultipleCallSitesInDifferentMethods() {
            LambdaCallSite callSite1 = createCallSite("com.example.MyClass", "method1", 10);
            LambdaCallSite callSite2 = createCallSite("com.example.MyClass", "method2", 10);

            assertThatCode(() -> processor.validateUniqueCallSiteIds(List.of(callSite1, callSite2)))
                    .doesNotThrowAnyException();
        }

        @Test
        void acceptsMultipleCallSitesInDifferentClasses() {
            LambdaCallSite callSite1 = createCallSite("com.example.ClassA", "myMethod", 10);
            LambdaCallSite callSite2 = createCallSite("com.example.ClassB", "myMethod", 10);

            assertThatCode(() -> processor.validateUniqueCallSiteIds(List.of(callSite1, callSite2)))
                    .doesNotThrowAnyException();
        }

        @Test
        void rejectsDuplicateCallSitesOnSameLine() {
            LambdaCallSite callSite1 = createCallSite("com.example.MyClass", "myMethod", 42);
            LambdaCallSite callSite2 = createCallSite("com.example.MyClass", "myMethod", 42);

            assertThatThrownBy(() -> processor.validateUniqueCallSiteIds(List.of(callSite1, callSite2)))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("QUBIT BUILD ERROR: Duplicate call site IDs detected!")
                    .hasMessageContaining("com.example.MyClass:myMethod:42")
                    .hasMessageContaining("Found 2 queries on this line");
        }

        @Test
        void rejectsTripleDuplicateCallSitesOnSameLine() {
            LambdaCallSite callSite1 = createCallSite("com.example.MyClass", "myMethod", 42);
            LambdaCallSite callSite2 = createCallSite("com.example.MyClass", "myMethod", 42);
            LambdaCallSite callSite3 = createCallSite("com.example.MyClass", "myMethod", 42);

            assertThatThrownBy(() -> processor.validateUniqueCallSiteIds(List.of(callSite1, callSite2, callSite3)))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Found 3 queries on this line");
        }

        @Test
        void detectsMultipleDuplicateGroups() {
            // Two pairs of duplicates on different lines
            LambdaCallSite line10_a = createCallSite("com.example.MyClass", "myMethod", 10);
            LambdaCallSite line10_b = createCallSite("com.example.MyClass", "myMethod", 10);
            LambdaCallSite line20_a = createCallSite("com.example.MyClass", "myMethod", 20);
            LambdaCallSite line20_b = createCallSite("com.example.MyClass", "myMethod", 20);

            assertThatThrownBy(() -> processor.validateUniqueCallSiteIds(
                    List.of(line10_a, line10_b, line20_a, line20_b)))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("com.example.MyClass:myMethod:10")
                    .hasMessageContaining("com.example.MyClass:myMethod:20");
        }

        @Test
        void errorMessageContainsHelpfulInstructions() {
            LambdaCallSite callSite1 = createCallSite("com.example.MyClass", "myMethod", 42);
            LambdaCallSite callSite2 = createCallSite("com.example.MyClass", "myMethod", 42);

            assertThatThrownBy(() -> processor.validateUniqueCallSiteIds(List.of(callSite1, callSite2)))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("FIX: Move each query to a separate source line")
                    .hasMessageContaining("Example - WRONG:")
                    .hasMessageContaining("Example - CORRECT:");
        }

        @Test
        void mixedValidAndDuplicateCallSites() {
            // Some valid, some duplicate
            LambdaCallSite valid1 = createCallSite("com.example.MyClass", "method1", 10);
            LambdaCallSite valid2 = createCallSite("com.example.MyClass", "method2", 20);
            LambdaCallSite duplicate1 = createCallSite("com.example.MyClass", "method3", 30);
            LambdaCallSite duplicate2 = createCallSite("com.example.MyClass", "method3", 30);

            assertThatThrownBy(() -> processor.validateUniqueCallSiteIds(
                    List.of(valid1, valid2, duplicate1, duplicate2)))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("com.example.MyClass:method3:30");
        }
    }
}
