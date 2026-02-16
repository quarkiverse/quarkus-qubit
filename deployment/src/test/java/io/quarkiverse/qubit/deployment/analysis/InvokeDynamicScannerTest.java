package io.quarkiverse.qubit.deployment.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for InvokeDynamicScanner.
 *
 * <p>
 * These tests verify that the scanner correctly identifies lambda call sites
 * in compiled bytecode containing QubitEntity fluent API patterns.
 */
class InvokeDynamicScannerTest {

    private static final String FLUENT_API_SOURCES_CLASS = "io.quarkiverse.qubit.deployment.testutil.FluentApiTestSources";
    private static final String FLUENT_API_SOURCES_FILE = FLUENT_API_SOURCES_CLASS.replace('.', '/') + ".class";

    private static byte[] fluentApiSourcesBytes;
    private static InvokeDynamicScanner scanner;

    @BeforeAll
    static void loadTestSources() throws Exception {
        try (InputStream is = InvokeDynamicScannerTest.class.getClassLoader()
                .getResourceAsStream(FLUENT_API_SOURCES_FILE)) {
            if (is == null) {
                throw new RuntimeException("Cannot find test sources: " + FLUENT_API_SOURCES_FILE);
            }
            fluentApiSourcesBytes = is.readAllBytes();
        }
        scanner = new InvokeDynamicScanner();
    }

    @Nested
    class ScanClass {

        @Test
        void scansClassAndFindsCallSites() {
            List<CallSite> callSites = scanner.scanClass(fluentApiSourcesBytes,
                    FLUENT_API_SOURCES_CLASS);

            assertThat(callSites)
                    .isNotEmpty()
                    .as("Should find lambda call sites in FluentApiTestSources");
        }

        @Test
        void doesNotProduceCallSiteForOrphanedLambda() {
            List<CallSite> callSites = scanner.scanClass(fluentApiSourcesBytes,
                    FLUENT_API_SOURCES_CLASS);

            assertThat(callSites)
                    .noneMatch(cs -> "withOrphanedLambda".equals(cs.methodName()));
        }

        @Test
        void callSitesHaveOwnerClassName() {
            List<CallSite> callSites = scanner.scanClass(fluentApiSourcesBytes,
                    FLUENT_API_SOURCES_CLASS);

            assertThat(callSites)
                    .allSatisfy(cs -> assertThat(cs.ownerClassName())
                            .as("Owner class should be set")
                            .isNotBlank());
        }

        @Test
        void callSitesHaveMethodName() {
            List<CallSite> callSites = scanner.scanClass(fluentApiSourcesBytes,
                    FLUENT_API_SOURCES_CLASS);

            assertThat(callSites)
                    .allSatisfy(cs -> assertThat(cs.methodName())
                            .as("Method name should be set")
                            .isNotBlank());
        }

        @Test
        void callSitesHaveLambdaMethodName() {
            List<CallSite> callSites = scanner.scanClass(fluentApiSourcesBytes,
                    FLUENT_API_SOURCES_CLASS);

            assertThat(callSites)
                    .allSatisfy(cs -> {
                        if (cs instanceof CallSite.SimpleCallSite simple) {
                            assertThat(simple.lambdaMethodName())
                                    .as("Lambda method name should be set")
                                    .isNotBlank();
                        }
                    });
        }

        @Test
        void callSitesHaveLineNumbers() {
            List<CallSite> callSites = scanner.scanClass(fluentApiSourcesBytes,
                    FLUENT_API_SOURCES_CLASS);

            assertThat(callSites)
                    .allSatisfy(cs -> assertThat(cs.lineNumber())
                            .as("Line number should be positive")
                            .isGreaterThan(0));
        }

        @Test
        void callSitesHaveTargetMethodName() {
            List<CallSite> callSites = scanner.scanClass(fluentApiSourcesBytes,
                    FLUENT_API_SOURCES_CLASS);

            assertThat(callSites)
                    .allSatisfy(cs -> assertThat(cs.targetMethodName())
                            .as("Target method name should be set")
                            .isNotBlank());
        }

        @Test
        void getCallSiteIdReturnsUniqueIdentifier() {
            List<CallSite> callSites = scanner.scanClass(fluentApiSourcesBytes,
                    FLUENT_API_SOURCES_CLASS);

            assertThat(callSites)
                    .allSatisfy(cs -> {
                        String id = cs.getCallSiteId();
                        assertThat(id)
                                .as("Call site ID should contain class:method:line")
                                .contains(":")
                                .isNotBlank();
                    });
        }

        @Test
        void returnsEmptyListForNullBytes() {
            List<CallSite> callSites = scanner.scanClass(null, "SomeClass");

            // Should handle null gracefully (caught in try-catch)
            assertThat(callSites).isEmpty();
        }

        @Test
        void returnsEmptyListForInvalidBytecode() {
            byte[] invalidBytecode = new byte[] { 0x00, 0x01, 0x02, 0x03 };

            List<CallSite> callSites = scanner.scanClass(invalidBytecode, "InvalidClass");

            // Should handle invalid bytecode gracefully
            assertThat(callSites).isEmpty();
        }

        @Test
        void returnsEmptyListForClassWithoutLambdas() throws Exception {
            // Load a simple class without lambdas (String.class)
            String className = "java.lang.String";
            try (InputStream is = InvokeDynamicScannerTest.class.getClassLoader()
                    .getResourceAsStream(className.replace('.', '/') + ".class")) {
                if (is != null) {
                    byte[] classBytes = is.readAllBytes();
                    List<CallSite> callSites = scanner.scanClass(classBytes, className);

                    assertThat(callSites)
                            .as("String class should have no QuerySpec lambdas")
                            .isEmpty();
                }
            }
        }
    }

    @Nested
    class TerminalOperationDetection {

        @Test
        void detectsToListTerminal() {
            List<CallSite> callSites = scanner.scanClass(fluentApiSourcesBytes,
                    FLUENT_API_SOURCES_CLASS);

            assertThat(callSites)
                    .anyMatch(cs -> "toList".equals(cs.targetMethodName()))
                    .as("Should detect toList() terminal operation");
        }

        @Test
        void detectsCountTerminal() {
            List<CallSite> callSites = scanner.scanClass(fluentApiSourcesBytes,
                    FLUENT_API_SOURCES_CLASS);

            assertThat(callSites)
                    .anyMatch(cs -> "count".equals(cs.targetMethodName()))
                    .as("Should detect count() terminal operation");
        }

        @Test
        void detectsExistsTerminal() {
            List<CallSite> callSites = scanner.scanClass(fluentApiSourcesBytes,
                    FLUENT_API_SOURCES_CLASS);

            assertThat(callSites)
                    .anyMatch(cs -> "exists".equals(cs.targetMethodName()))
                    .as("Should detect exists() terminal operation");
        }

        @Test
        void detectsGetSingleResultTerminal() {
            List<CallSite> callSites = scanner.scanClass(fluentApiSourcesBytes,
                    FLUENT_API_SOURCES_CLASS);

            assertThat(callSites)
                    .anyMatch(cs -> "getSingleResult".equals(cs.targetMethodName()))
                    .as("Should detect getSingleResult() terminal operation");
        }

        @Test
        void detectsFindFirstTerminal() {
            List<CallSite> callSites = scanner.scanClass(fluentApiSourcesBytes,
                    FLUENT_API_SOURCES_CLASS);

            assertThat(callSites)
                    .anyMatch(cs -> "findFirst".equals(cs.targetMethodName()))
                    .as("Should detect findFirst() terminal operation");
        }
    }

    @Nested
    class FluentMethodDetection {

        @Test
        void detectsWhereFluentMethod() {
            List<CallSite> callSites = scanner.scanClass(fluentApiSourcesBytes,
                    FLUENT_API_SOURCES_CLASS);

            assertThat(callSites)
                    .anyMatch(cs -> cs instanceof CallSite.SimpleCallSite simple
                            && "where".equals(simple.fluentMethodName()))
                    .as("Should detect where() fluent method");
        }

        @Test
        void detectsSelectFluentMethod() {
            List<CallSite> callSites = scanner.scanClass(fluentApiSourcesBytes,
                    FLUENT_API_SOURCES_CLASS);

            assertThat(callSites)
                    .anyMatch(cs -> cs instanceof CallSite.SimpleCallSite simple
                            && "select".equals(simple.fluentMethodName()))
                    .as("Should detect select() fluent method");
        }

        @Test
        void detectsSortedByFluentMethod() {
            List<CallSite> callSites = scanner.scanClass(fluentApiSourcesBytes,
                    FLUENT_API_SOURCES_CLASS);

            assertThat(callSites)
                    .anyMatch(cs -> cs instanceof CallSite.SimpleCallSite simple
                            && "sortedBy".equals(simple.fluentMethodName()))
                    .as("Should detect sortedBy() fluent method");
        }

        @Test
        void detectsSortedDescendingByFluentMethod() {
            List<CallSite> callSites = scanner.scanClass(fluentApiSourcesBytes,
                    FLUENT_API_SOURCES_CLASS);

            assertThat(callSites)
                    .anyMatch(cs -> cs instanceof CallSite.SimpleCallSite simple
                            && "sortedDescendingBy".equals(simple.fluentMethodName()))
                    .as("Should detect sortedDescendingBy() fluent method");
        }
    }

    @Nested
    class QueryTypeDetection {

        @Test
        void isCountQueryReturnsTrueForCountTerminal() {
            List<CallSite> callSites = scanner.scanClass(fluentApiSourcesBytes,
                    FLUENT_API_SOURCES_CLASS);

            List<CallSite> countCallSites = callSites.stream()
                    .filter(cs -> "count".equals(cs.targetMethodName()))
                    .toList();

            assertThat(countCallSites)
                    .isNotEmpty()
                    .allMatch(CallSite::isCountQuery);
        }

        @Test
        void isCountQueryReturnsTrueForExistsTerminal() {
            List<CallSite> callSites = scanner.scanClass(fluentApiSourcesBytes,
                    FLUENT_API_SOURCES_CLASS);

            List<CallSite> existsCallSites = callSites.stream()
                    .filter(cs -> "exists".equals(cs.targetMethodName()))
                    .toList();

            assertThat(existsCallSites)
                    .isNotEmpty()
                    .allMatch(CallSite::isCountQuery);
        }

        @Test
        void isCountQueryReturnsFalseForToListTerminal() {
            List<CallSite> callSites = scanner.scanClass(fluentApiSourcesBytes,
                    FLUENT_API_SOURCES_CLASS);

            List<CallSite> listCallSites = callSites.stream()
                    .filter(cs -> "toList".equals(cs.targetMethodName()))
                    .toList();

            assertThat(listCallSites)
                    .isNotEmpty()
                    .noneMatch(CallSite::isCountQuery);
        }

        @Test
        void isProjectionQueryDetectsSelectClauses() {
            List<CallSite> callSites = scanner.scanClass(fluentApiSourcesBytes,
                    FLUENT_API_SOURCES_CLASS);

            // At least some call sites should be projections (select() calls)
            assertThat(callSites)
                    .anyMatch(cs -> cs instanceof CallSite.SimpleCallSite simple
                            && simple.isProjectionQuery());
        }
    }

    @Nested
    class AggregationQueryDetection {

        @Test
        void isAggregationQueryReturnsTrueForMinTerminal() {
            List<CallSite> callSites = scanner.scanClass(fluentApiSourcesBytes,
                    FLUENT_API_SOURCES_CLASS);

            List<CallSite> minCallSites = callSites.stream()
                    .filter(cs -> "min".equals(cs.targetMethodName()))
                    .toList();

            assertThat(minCallSites)
                    .isNotEmpty()
                    .allMatch(cs -> cs instanceof CallSite.AggregationCallSite);
        }

        @Test
        void isAggregationQueryReturnsTrueForMaxTerminal() {
            List<CallSite> callSites = scanner.scanClass(fluentApiSourcesBytes,
                    FLUENT_API_SOURCES_CLASS);

            List<CallSite> maxCallSites = callSites.stream()
                    .filter(cs -> "max".equals(cs.targetMethodName()))
                    .toList();

            assertThat(maxCallSites)
                    .isNotEmpty()
                    .allMatch(cs -> cs instanceof CallSite.AggregationCallSite);
        }

        @Test
        void isAggregationQueryReturnsTrueForAvgTerminal() {
            List<CallSite> callSites = scanner.scanClass(fluentApiSourcesBytes,
                    FLUENT_API_SOURCES_CLASS);

            List<CallSite> avgCallSites = callSites.stream()
                    .filter(cs -> "avg".equals(cs.targetMethodName()))
                    .toList();

            assertThat(avgCallSites)
                    .isNotEmpty()
                    .allMatch(cs -> cs instanceof CallSite.AggregationCallSite);
        }

        @Test
        void isAggregationQueryReturnsTrueForSumIntegerTerminal() {
            List<CallSite> callSites = scanner.scanClass(fluentApiSourcesBytes,
                    FLUENT_API_SOURCES_CLASS);

            List<CallSite> sumCallSites = callSites.stream()
                    .filter(cs -> "sumInteger".equals(cs.targetMethodName()))
                    .toList();

            assertThat(sumCallSites)
                    .isNotEmpty()
                    .allMatch(cs -> cs instanceof CallSite.AggregationCallSite);
        }

        @Test
        void isAggregationQueryReturnsFalseForNonAggregationTerminal() {
            List<CallSite> callSites = scanner.scanClass(fluentApiSourcesBytes,
                    FLUENT_API_SOURCES_CLASS);

            List<CallSite> toListCallSites = callSites.stream()
                    .filter(cs -> "toList".equals(cs.targetMethodName()))
                    .toList();

            assertThat(toListCallSites)
                    .isNotEmpty()
                    .noneMatch(cs -> cs instanceof CallSite.AggregationCallSite);
        }
    }

    @Nested
    class ChainedQueryDetection {

        @Test
        void detectsChainedWherePredicates() {
            List<CallSite> callSites = scanner.scanClass(fluentApiSourcesBytes,
                    FLUENT_API_SOURCES_CLASS);

            // Find call sites with multiple predicates
            assertThat(callSites)
                    .anyMatch(cs -> cs instanceof CallSite.SimpleCallSite simple
                            && simple.predicateLambdas() != null && simple.predicateLambdas().size() > 1)
                    .as("Should detect chained where() with multiple predicates");
        }

        @Test
        void detectsSortLambdas() {
            List<CallSite> callSites = scanner.scanClass(fluentApiSourcesBytes,
                    FLUENT_API_SOURCES_CLASS);

            // Find call sites with sort lambdas
            assertThat(callSites)
                    .anyMatch(cs -> cs instanceof CallSite.SimpleCallSite simple
                            && simple.sortLambdas() != null && !simple.sortLambdas().isEmpty())
                    .as("Should detect sortedBy() with sort lambdas");
        }

        @Test
        void detectsCombinedWhereAndSelect() {
            List<CallSite> callSites = scanner.scanClass(fluentApiSourcesBytes,
                    FLUENT_API_SOURCES_CLASS);

            // Find combined queries (where + select)
            assertThat(callSites)
                    .anyMatch(cs -> cs instanceof CallSite.SimpleCallSite simple
                            && simple.isCombinedQuery())
                    .as("Should detect combined where().select() patterns");
        }
    }

    @Nested
    class CallSiteRecordMethods {

        @Test
        void toStringReturnsReadableFormat() {
            List<CallSite> callSites = scanner.scanClass(fluentApiSourcesBytes,
                    FLUENT_API_SOURCES_CLASS);

            assertThat(callSites)
                    .isNotEmpty()
                    .allSatisfy(cs -> {
                        String str = cs.toString();
                        assertThat(str)
                                .as("toString should be readable")
                                .contains("CallSite")
                                .contains("line");
                    });
        }

        @Test
        void isJoinQueryReturnsFalseForNonJoinQueries() {
            List<CallSite> callSites = scanner.scanClass(fluentApiSourcesBytes,
                    FLUENT_API_SOURCES_CLASS);

            // FluentApiTestSources doesn't have join queries
            assertThat(callSites)
                    .allSatisfy(cs -> assertThat(cs)
                            .as("Non-join queries should return false")
                            .isNotInstanceOf(CallSite.JoinCallSite.class));
        }

        @Test
        void isGroupByQueryReturnsFalseForNonGroupQueries() {
            List<CallSite> callSites = scanner.scanClass(fluentApiSourcesBytes,
                    FLUENT_API_SOURCES_CLASS);

            // FluentApiTestSources doesn't have group queries
            assertThat(callSites)
                    .allSatisfy(cs -> assertThat(cs)
                            .as("Non-group queries should return false")
                            .isNotInstanceOf(CallSite.GroupCallSite.class));
        }

        @Test
        void isSelectJoinedQueryReturnsFalseForNonJoinQueries() {
            List<CallSite> callSites = scanner.scanClass(fluentApiSourcesBytes,
                    FLUENT_API_SOURCES_CLASS);

            assertThat(callSites)
                    .allSatisfy(cs -> {
                        if (cs instanceof CallSite.JoinCallSite join) {
                            assertThat(join.isSelectJoined())
                                    .as("Non-join queries should return false for selectJoined")
                                    .isFalse();
                        }
                        // Non-JoinCallSite instances cannot be selectJoined, so the assertion passes trivially
                    });
        }

        @Test
        void isJoinProjectionQueryReturnsFalseForNonJoinQueries() {
            List<CallSite> callSites = scanner.scanClass(fluentApiSourcesBytes,
                    FLUENT_API_SOURCES_CLASS);

            assertThat(callSites)
                    .allSatisfy(cs -> {
                        if (cs instanceof CallSite.JoinCallSite join) {
                            assertThat(join.isJoinProjectionQuery())
                                    .as("Non-join queries should return false for join projection")
                                    .isFalse();
                        }
                        // Non-JoinCallSite instances cannot have join projections, so the assertion passes trivially
                    });
        }
    }
}
