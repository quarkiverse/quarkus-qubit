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
            List<InvokeDynamicScanner.LambdaCallSite> callSites = scanner.scanClass(fluentApiSourcesBytes,
                    FLUENT_API_SOURCES_CLASS);

            assertThat(callSites)
                    .isNotEmpty()
                    .as("Should find lambda call sites in FluentApiTestSources");
        }

        @Test
        void callSitesHaveOwnerClassName() {
            List<InvokeDynamicScanner.LambdaCallSite> callSites = scanner.scanClass(fluentApiSourcesBytes,
                    FLUENT_API_SOURCES_CLASS);

            assertThat(callSites)
                    .allSatisfy(cs -> assertThat(cs.ownerClassName())
                            .as("Owner class should be set")
                            .isNotBlank());
        }

        @Test
        void callSitesHaveMethodName() {
            List<InvokeDynamicScanner.LambdaCallSite> callSites = scanner.scanClass(fluentApiSourcesBytes,
                    FLUENT_API_SOURCES_CLASS);

            assertThat(callSites)
                    .allSatisfy(cs -> assertThat(cs.methodName())
                            .as("Method name should be set")
                            .isNotBlank());
        }

        @Test
        void callSitesHaveLambdaMethodName() {
            List<InvokeDynamicScanner.LambdaCallSite> callSites = scanner.scanClass(fluentApiSourcesBytes,
                    FLUENT_API_SOURCES_CLASS);

            assertThat(callSites)
                    .allSatisfy(cs -> assertThat(cs.lambdaMethodName())
                            .as("Lambda method name should be set")
                            .isNotBlank());
        }

        @Test
        void callSitesHaveLineNumbers() {
            List<InvokeDynamicScanner.LambdaCallSite> callSites = scanner.scanClass(fluentApiSourcesBytes,
                    FLUENT_API_SOURCES_CLASS);

            assertThat(callSites)
                    .allSatisfy(cs -> assertThat(cs.lineNumber())
                            .as("Line number should be positive")
                            .isGreaterThan(0));
        }

        @Test
        void callSitesHaveTargetMethodName() {
            List<InvokeDynamicScanner.LambdaCallSite> callSites = scanner.scanClass(fluentApiSourcesBytes,
                    FLUENT_API_SOURCES_CLASS);

            assertThat(callSites)
                    .allSatisfy(cs -> assertThat(cs.targetMethodName())
                            .as("Target method name should be set")
                            .isNotBlank());
        }

        @Test
        void getCallSiteIdReturnsUniqueIdentifier() {
            List<InvokeDynamicScanner.LambdaCallSite> callSites = scanner.scanClass(fluentApiSourcesBytes,
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
            List<InvokeDynamicScanner.LambdaCallSite> callSites = scanner.scanClass(null, "SomeClass");

            // Should handle null gracefully (caught in try-catch)
            assertThat(callSites).isEmpty();
        }

        @Test
        void returnsEmptyListForInvalidBytecode() {
            byte[] invalidBytecode = new byte[] { 0x00, 0x01, 0x02, 0x03 };

            List<InvokeDynamicScanner.LambdaCallSite> callSites = scanner.scanClass(invalidBytecode, "InvalidClass");

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
                    List<InvokeDynamicScanner.LambdaCallSite> callSites = scanner.scanClass(classBytes, className);

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
            List<InvokeDynamicScanner.LambdaCallSite> callSites = scanner.scanClass(fluentApiSourcesBytes,
                    FLUENT_API_SOURCES_CLASS);

            assertThat(callSites)
                    .anyMatch(cs -> "toList".equals(cs.targetMethodName()))
                    .as("Should detect toList() terminal operation");
        }

        @Test
        void detectsCountTerminal() {
            List<InvokeDynamicScanner.LambdaCallSite> callSites = scanner.scanClass(fluentApiSourcesBytes,
                    FLUENT_API_SOURCES_CLASS);

            assertThat(callSites)
                    .anyMatch(cs -> "count".equals(cs.targetMethodName()))
                    .as("Should detect count() terminal operation");
        }

        @Test
        void detectsExistsTerminal() {
            List<InvokeDynamicScanner.LambdaCallSite> callSites = scanner.scanClass(fluentApiSourcesBytes,
                    FLUENT_API_SOURCES_CLASS);

            assertThat(callSites)
                    .anyMatch(cs -> "exists".equals(cs.targetMethodName()))
                    .as("Should detect exists() terminal operation");
        }

        @Test
        void detectsGetSingleResultTerminal() {
            List<InvokeDynamicScanner.LambdaCallSite> callSites = scanner.scanClass(fluentApiSourcesBytes,
                    FLUENT_API_SOURCES_CLASS);

            assertThat(callSites)
                    .anyMatch(cs -> "getSingleResult".equals(cs.targetMethodName()))
                    .as("Should detect getSingleResult() terminal operation");
        }

        @Test
        void detectsFindFirstTerminal() {
            List<InvokeDynamicScanner.LambdaCallSite> callSites = scanner.scanClass(fluentApiSourcesBytes,
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
            List<InvokeDynamicScanner.LambdaCallSite> callSites = scanner.scanClass(fluentApiSourcesBytes,
                    FLUENT_API_SOURCES_CLASS);

            assertThat(callSites)
                    .anyMatch(cs -> "where".equals(cs.fluentMethodName()))
                    .as("Should detect where() fluent method");
        }

        @Test
        void detectsSelectFluentMethod() {
            List<InvokeDynamicScanner.LambdaCallSite> callSites = scanner.scanClass(fluentApiSourcesBytes,
                    FLUENT_API_SOURCES_CLASS);

            assertThat(callSites)
                    .anyMatch(cs -> "select".equals(cs.fluentMethodName()))
                    .as("Should detect select() fluent method");
        }

        @Test
        void detectsSortedByFluentMethod() {
            List<InvokeDynamicScanner.LambdaCallSite> callSites = scanner.scanClass(fluentApiSourcesBytes,
                    FLUENT_API_SOURCES_CLASS);

            assertThat(callSites)
                    .anyMatch(cs -> "sortedBy".equals(cs.fluentMethodName()))
                    .as("Should detect sortedBy() fluent method");
        }

        @Test
        void detectsSortedDescendingByFluentMethod() {
            List<InvokeDynamicScanner.LambdaCallSite> callSites = scanner.scanClass(fluentApiSourcesBytes,
                    FLUENT_API_SOURCES_CLASS);

            assertThat(callSites)
                    .anyMatch(cs -> "sortedDescendingBy".equals(cs.fluentMethodName()))
                    .as("Should detect sortedDescendingBy() fluent method");
        }
    }

    @Nested
    class QueryTypeDetection {

        @Test
        void isCountQueryReturnsTrueForCountTerminal() {
            List<InvokeDynamicScanner.LambdaCallSite> callSites = scanner.scanClass(fluentApiSourcesBytes,
                    FLUENT_API_SOURCES_CLASS);

            List<InvokeDynamicScanner.LambdaCallSite> countCallSites = callSites.stream()
                    .filter(cs -> "count".equals(cs.targetMethodName()))
                    .toList();

            assertThat(countCallSites)
                    .isNotEmpty()
                    .allMatch(InvokeDynamicScanner.LambdaCallSite::isCountQuery);
        }

        @Test
        void isCountQueryReturnsTrueForExistsTerminal() {
            List<InvokeDynamicScanner.LambdaCallSite> callSites = scanner.scanClass(fluentApiSourcesBytes,
                    FLUENT_API_SOURCES_CLASS);

            List<InvokeDynamicScanner.LambdaCallSite> existsCallSites = callSites.stream()
                    .filter(cs -> "exists".equals(cs.targetMethodName()))
                    .toList();

            assertThat(existsCallSites)
                    .isNotEmpty()
                    .allMatch(InvokeDynamicScanner.LambdaCallSite::isCountQuery);
        }

        @Test
        void isCountQueryReturnsFalseForToListTerminal() {
            List<InvokeDynamicScanner.LambdaCallSite> callSites = scanner.scanClass(fluentApiSourcesBytes,
                    FLUENT_API_SOURCES_CLASS);

            List<InvokeDynamicScanner.LambdaCallSite> listCallSites = callSites.stream()
                    .filter(cs -> "toList".equals(cs.targetMethodName()))
                    .toList();

            assertThat(listCallSites)
                    .isNotEmpty()
                    .noneMatch(InvokeDynamicScanner.LambdaCallSite::isCountQuery);
        }

        @Test
        void isProjectionQueryDetectsSelectClauses() {
            List<InvokeDynamicScanner.LambdaCallSite> callSites = scanner.scanClass(fluentApiSourcesBytes,
                    FLUENT_API_SOURCES_CLASS);

            // At least some call sites should be projections (select() calls)
            assertThat(callSites)
                    .anyMatch(InvokeDynamicScanner.LambdaCallSite::isProjectionQuery);
        }
    }

    @Nested
    class AggregationQueryDetection {

        @Test
        void isAggregationQueryReturnsTrueForMinTerminal() {
            List<InvokeDynamicScanner.LambdaCallSite> callSites = scanner.scanClass(fluentApiSourcesBytes,
                    FLUENT_API_SOURCES_CLASS);

            List<InvokeDynamicScanner.LambdaCallSite> minCallSites = callSites.stream()
                    .filter(cs -> "min".equals(cs.targetMethodName()))
                    .toList();

            assertThat(minCallSites)
                    .isNotEmpty()
                    .allMatch(InvokeDynamicScanner.LambdaCallSite::isAggregationQuery);
        }

        @Test
        void isAggregationQueryReturnsTrueForMaxTerminal() {
            List<InvokeDynamicScanner.LambdaCallSite> callSites = scanner.scanClass(fluentApiSourcesBytes,
                    FLUENT_API_SOURCES_CLASS);

            List<InvokeDynamicScanner.LambdaCallSite> maxCallSites = callSites.stream()
                    .filter(cs -> "max".equals(cs.targetMethodName()))
                    .toList();

            assertThat(maxCallSites)
                    .isNotEmpty()
                    .allMatch(InvokeDynamicScanner.LambdaCallSite::isAggregationQuery);
        }

        @Test
        void isAggregationQueryReturnsTrueForAvgTerminal() {
            List<InvokeDynamicScanner.LambdaCallSite> callSites = scanner.scanClass(fluentApiSourcesBytes,
                    FLUENT_API_SOURCES_CLASS);

            List<InvokeDynamicScanner.LambdaCallSite> avgCallSites = callSites.stream()
                    .filter(cs -> "avg".equals(cs.targetMethodName()))
                    .toList();

            assertThat(avgCallSites)
                    .isNotEmpty()
                    .allMatch(InvokeDynamicScanner.LambdaCallSite::isAggregationQuery);
        }

        @Test
        void isAggregationQueryReturnsTrueForSumIntegerTerminal() {
            List<InvokeDynamicScanner.LambdaCallSite> callSites = scanner.scanClass(fluentApiSourcesBytes,
                    FLUENT_API_SOURCES_CLASS);

            List<InvokeDynamicScanner.LambdaCallSite> sumCallSites = callSites.stream()
                    .filter(cs -> "sumInteger".equals(cs.targetMethodName()))
                    .toList();

            assertThat(sumCallSites)
                    .isNotEmpty()
                    .allMatch(InvokeDynamicScanner.LambdaCallSite::isAggregationQuery);
        }

        @Test
        void isAggregationQueryReturnsFalseForNonAggregationTerminal() {
            List<InvokeDynamicScanner.LambdaCallSite> callSites = scanner.scanClass(fluentApiSourcesBytes,
                    FLUENT_API_SOURCES_CLASS);

            List<InvokeDynamicScanner.LambdaCallSite> toListCallSites = callSites.stream()
                    .filter(cs -> "toList".equals(cs.targetMethodName()))
                    .toList();

            assertThat(toListCallSites)
                    .isNotEmpty()
                    .noneMatch(InvokeDynamicScanner.LambdaCallSite::isAggregationQuery);
        }
    }

    @Nested
    class ChainedQueryDetection {

        @Test
        void detectsChainedWherePredicates() {
            List<InvokeDynamicScanner.LambdaCallSite> callSites = scanner.scanClass(fluentApiSourcesBytes,
                    FLUENT_API_SOURCES_CLASS);

            // Find call sites with multiple predicates
            assertThat(callSites)
                    .anyMatch(cs -> cs.predicateLambdas() != null && cs.predicateLambdas().size() > 1)
                    .as("Should detect chained where() with multiple predicates");
        }

        @Test
        void detectsSortLambdas() {
            List<InvokeDynamicScanner.LambdaCallSite> callSites = scanner.scanClass(fluentApiSourcesBytes,
                    FLUENT_API_SOURCES_CLASS);

            // Find call sites with sort lambdas
            assertThat(callSites)
                    .anyMatch(cs -> cs.sortLambdas() != null && !cs.sortLambdas().isEmpty())
                    .as("Should detect sortedBy() with sort lambdas");
        }

        @Test
        void detectsCombinedWhereAndSelect() {
            List<InvokeDynamicScanner.LambdaCallSite> callSites = scanner.scanClass(fluentApiSourcesBytes,
                    FLUENT_API_SOURCES_CLASS);

            // Find combined queries (where + select)
            assertThat(callSites)
                    .anyMatch(InvokeDynamicScanner.LambdaCallSite::isCombinedQuery)
                    .as("Should detect combined where().select() patterns");
        }
    }

    @Nested
    class LambdaCallSiteRecordMethods {

        @Test
        void toStringReturnsReadableFormat() {
            List<InvokeDynamicScanner.LambdaCallSite> callSites = scanner.scanClass(fluentApiSourcesBytes,
                    FLUENT_API_SOURCES_CLASS);

            assertThat(callSites)
                    .isNotEmpty()
                    .allSatisfy(cs -> {
                        String str = cs.toString();
                        assertThat(str)
                                .as("toString should be readable")
                                .contains("LambdaCallSite")
                                .contains("line");
                    });
        }

        @Test
        void isJoinQueryReturnsFalseForNonJoinQueries() {
            List<InvokeDynamicScanner.LambdaCallSite> callSites = scanner.scanClass(fluentApiSourcesBytes,
                    FLUENT_API_SOURCES_CLASS);

            // FluentApiTestSources doesn't have join queries
            assertThat(callSites)
                    .allSatisfy(cs -> assertThat(cs.isJoinQuery())
                            .as("Non-join queries should return false")
                            .isFalse());
        }

        @Test
        void isGroupByQueryReturnsFalseForNonGroupQueries() {
            List<InvokeDynamicScanner.LambdaCallSite> callSites = scanner.scanClass(fluentApiSourcesBytes,
                    FLUENT_API_SOURCES_CLASS);

            // FluentApiTestSources doesn't have group queries
            assertThat(callSites)
                    .allSatisfy(cs -> assertThat(cs.isGroupByQuery())
                            .as("Non-group queries should return false")
                            .isFalse());
        }

        @Test
        void isSelectJoinedQueryReturnsFalseForNonJoinQueries() {
            List<InvokeDynamicScanner.LambdaCallSite> callSites = scanner.scanClass(fluentApiSourcesBytes,
                    FLUENT_API_SOURCES_CLASS);

            assertThat(callSites)
                    .allSatisfy(cs -> assertThat(cs.isSelectJoinedQuery())
                            .as("Non-join queries should return false for selectJoined")
                            .isFalse());
        }

        @Test
        void isJoinProjectionQueryReturnsFalseForNonJoinQueries() {
            List<InvokeDynamicScanner.LambdaCallSite> callSites = scanner.scanClass(fluentApiSourcesBytes,
                    FLUENT_API_SOURCES_CLASS);

            assertThat(callSites)
                    .allSatisfy(cs -> assertThat(cs.isJoinProjectionQuery())
                            .as("Non-join queries should return false for join projection")
                            .isFalse());
        }
    }
}
