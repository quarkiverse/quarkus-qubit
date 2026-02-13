package io.quarkiverse.qubit.deployment.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import io.quarkiverse.qubit.SortDirection;
import io.quarkiverse.qubit.deployment.analysis.LambdaAnalysisResult.SortExpression;
import io.quarkiverse.qubit.deployment.ast.LambdaExpression;

/**
 * Unit tests for {@link LambdaDeduplicator} hash computation methods.
 *
 * <p>
 * Tests verify that:
 * <ul>
 * <li>Same expressions produce same hash</li>
 * <li>Different expressions produce different hashes</li>
 * <li>Query type flags affect hash</li>
 * <li>Executor registration and deduplication work correctly</li>
 * </ul>
 */
class LambdaDeduplicatorTest {

    private LambdaDeduplicator deduplicator;

    @BeforeEach
    void setUp() {
        deduplicator = new LambdaDeduplicator();
    }

    // Helper Methods

    private LambdaExpression.FieldAccess field(String name) {
        return new LambdaExpression.FieldAccess(name, String.class);
    }

    private LambdaExpression.Constant constant(int value) {
        return new LambdaExpression.Constant(value, int.class);
    }

    private LambdaExpression.BinaryOp comparison(String fieldName, int value) {
        return LambdaExpression.BinaryOp.gt(field(fieldName), constant(value));
    }

    private SortExpression sortAsc(String fieldName) {
        return new SortExpression(field(fieldName), SortDirection.ASCENDING);
    }

    private SortExpression sortDesc(String fieldName) {
        return new SortExpression(field(fieldName), SortDirection.DESCENDING);
    }

    // computeLambdaHash Tests

    @Nested
    class ComputeLambdaHashTests {

        @Test
        void sameExpression_sameQueryType_producesSameHash() {
            LambdaExpression expr = comparison("age", 25);

            String hash1 = deduplicator.computeLambdaHash(expr, false, false);
            String hash2 = deduplicator.computeLambdaHash(expr, false, false);

            assertThat(hash1).isEqualTo(hash2);
        }

        @Test
        void sameExpression_differentCountFlag_producesDifferentHash() {
            LambdaExpression expr = comparison("age", 25);

            String listHash = deduplicator.computeLambdaHash(expr, false, false);
            String countHash = deduplicator.computeLambdaHash(expr, true, false);

            assertThat(listHash).isNotEqualTo(countHash);
        }

        @Test
        void sameExpression_differentProjectionFlag_producesDifferentHash() {
            LambdaExpression expr = comparison("age", 25);

            String noProjectionHash = deduplicator.computeLambdaHash(expr, false, false);
            String projectionHash = deduplicator.computeLambdaHash(expr, false, true);

            assertThat(noProjectionHash).isNotEqualTo(projectionHash);
        }

        @Test
        void differentExpressions_producesDifferentHash() {
            LambdaExpression expr1 = comparison("age", 25);
            LambdaExpression expr2 = comparison("age", 30);

            String hash1 = deduplicator.computeLambdaHash(expr1, false, false);
            String hash2 = deduplicator.computeLambdaHash(expr2, false, false);

            assertThat(hash1).isNotEqualTo(hash2);
        }

        @Test
        void hash_isHexString() {
            LambdaExpression expr = comparison("age", 25);

            String hash = deduplicator.computeLambdaHash(expr, false, false);

            // FNV-1a 64-bit produces up to 16 hex characters
            assertThat(hash)
                    .hasSize(16)
                    .matches("[0-9a-f]+");
        }
    }

    // computeCombinedHash Tests

    @Nested
    class ComputeCombinedHashTests {

        @Test
        void samePredicateAndProjection_producesSameHash() {
            LambdaExpression predicate = comparison("age", 25);
            LambdaExpression projection = field("name");

            String hash1 = deduplicator.computeCombinedHash(predicate, projection, false);
            String hash2 = deduplicator.computeCombinedHash(predicate, projection, false);

            assertThat(hash1).isEqualTo(hash2);
        }

        @Test
        void differentPredicate_producesDifferentHash() {
            LambdaExpression predicate1 = comparison("age", 25);
            LambdaExpression predicate2 = comparison("age", 30);
            LambdaExpression projection = field("name");

            String hash1 = deduplicator.computeCombinedHash(predicate1, projection, false);
            String hash2 = deduplicator.computeCombinedHash(predicate2, projection, false);

            assertThat(hash1).isNotEqualTo(hash2);
        }

        @Test
        void differentProjection_producesDifferentHash() {
            LambdaExpression predicate = comparison("age", 25);
            LambdaExpression projection1 = field("name");
            LambdaExpression projection2 = field("email");

            String hash1 = deduplicator.computeCombinedHash(predicate, projection1, false);
            String hash2 = deduplicator.computeCombinedHash(predicate, projection2, false);

            assertThat(hash1).isNotEqualTo(hash2);
        }

        @Test
        void differentCountFlag_producesDifferentHash() {
            LambdaExpression predicate = comparison("age", 25);
            LambdaExpression projection = field("name");

            String listHash = deduplicator.computeCombinedHash(predicate, projection, false);
            String countHash = deduplicator.computeCombinedHash(predicate, projection, true);

            assertThat(listHash).isNotEqualTo(countHash);
        }
    }

    // computeSortingHash Tests

    @Nested
    class ComputeSortingHashTests {

        @Test
        void sameSortExpressions_producesSameHash() {
            List<SortExpression> sorts = List.of(sortAsc("name"), sortDesc("age"));

            String hash1 = deduplicator.computeSortingHash(sorts);
            String hash2 = deduplicator.computeSortingHash(sorts);

            assertThat(hash1).isEqualTo(hash2);
        }

        @Test
        void differentSortOrder_producesDifferentHash() {
            List<SortExpression> ascSort = List.of(sortAsc("name"));
            List<SortExpression> descSort = List.of(sortDesc("name"));

            String ascHash = deduplicator.computeSortingHash(ascSort);
            String descHash = deduplicator.computeSortingHash(descSort);

            assertThat(ascHash).isNotEqualTo(descHash);
        }

        @Test
        void differentSortFields_producesDifferentHash() {
            List<SortExpression> nameSort = List.of(sortAsc("name"));
            List<SortExpression> ageSort = List.of(sortAsc("age"));

            String nameHash = deduplicator.computeSortingHash(nameSort);
            String ageHash = deduplicator.computeSortingHash(ageSort);

            assertThat(nameHash).isNotEqualTo(ageHash);
        }
    }

    // computeQueryWithSortingHash Tests

    @Nested
    class ComputeQueryWithSortingHashTests {

        @Test
        void sameExpressionAndSort_producesSameHash() {
            LambdaExpression expr = comparison("age", 25);
            List<SortExpression> sorts = List.of(sortAsc("name"));

            String hash1 = deduplicator.computeQueryWithSortingHash(expr, sorts, false, false);
            String hash2 = deduplicator.computeQueryWithSortingHash(expr, sorts, false, false);

            assertThat(hash1).isEqualTo(hash2);
        }

        @Test
        void differentExpression_producesDifferentHash() {
            LambdaExpression expr1 = comparison("age", 25);
            LambdaExpression expr2 = comparison("age", 30);
            List<SortExpression> sorts = List.of(sortAsc("name"));

            String hash1 = deduplicator.computeQueryWithSortingHash(expr1, sorts, false, false);
            String hash2 = deduplicator.computeQueryWithSortingHash(expr2, sorts, false, false);

            assertThat(hash1).isNotEqualTo(hash2);
        }

        @Test
        void differentSort_producesDifferentHash() {
            LambdaExpression expr = comparison("age", 25);
            List<SortExpression> sorts1 = List.of(sortAsc("name"));
            List<SortExpression> sorts2 = List.of(sortDesc("name"));

            String hash1 = deduplicator.computeQueryWithSortingHash(expr, sorts1, false, false);
            String hash2 = deduplicator.computeQueryWithSortingHash(expr, sorts2, false, false);

            assertThat(hash1).isNotEqualTo(hash2);
        }
    }

    // computeFullQueryHash Tests

    @Nested
    class ComputeFullQueryHashTests {

        @Test
        void samePredicateProjectionSort_producesSameHash() {
            LambdaExpression predicate = comparison("age", 25);
            LambdaExpression projection = field("name");
            List<SortExpression> sorts = List.of(sortAsc("age"));

            String hash1 = deduplicator.computeFullQueryHash(predicate, projection, sorts, false);
            String hash2 = deduplicator.computeFullQueryHash(predicate, projection, sorts, false);

            assertThat(hash1).isEqualTo(hash2);
        }

        @Test
        void differentCountFlag_producesDifferentHash() {
            LambdaExpression predicate = comparison("age", 25);
            LambdaExpression projection = field("name");
            List<SortExpression> sorts = List.of(sortAsc("age"));

            String listHash = deduplicator.computeFullQueryHash(predicate, projection, sorts, false);
            String countHash = deduplicator.computeFullQueryHash(predicate, projection, sorts, true);

            assertThat(listHash).isNotEqualTo(countHash);
        }
    }

    // computeAggregationHash Tests

    @Nested
    class ComputeAggregationHashTests {

        @Test
        void sameAggregation_producesSameHash() {
            LambdaExpression predicate = comparison("age", 25);
            LambdaExpression aggregation = field("salary");

            String hash1 = deduplicator.computeAggregationHash(predicate, aggregation, "SUM_DOUBLE");
            String hash2 = deduplicator.computeAggregationHash(predicate, aggregation, "SUM_DOUBLE");

            assertThat(hash1).isEqualTo(hash2);
        }

        @Test
        void differentAggregationType_producesDifferentHash() {
            LambdaExpression predicate = comparison("age", 25);
            LambdaExpression aggregation = field("salary");

            String sumHash = deduplicator.computeAggregationHash(predicate, aggregation, "SUM_DOUBLE");
            String avgHash = deduplicator.computeAggregationHash(predicate, aggregation, "AVG");

            assertThat(sumHash).isNotEqualTo(avgHash);
        }

        @Test
        void nullPredicate_stillProducesValidHash() {
            LambdaExpression aggregation = field("salary");

            String hash = deduplicator.computeAggregationHash(null, aggregation, "MAX");

            // FNV-1a 64-bit produces up to 16 hex characters
            assertThat(hash)
                    .isNotNull()
                    .hasSize(16);
        }

        @Test
        void nullVsNonNullPredicate_producesDifferentHash() {
            LambdaExpression predicate = comparison("age", 25);
            LambdaExpression aggregation = field("salary");

            String withPredicate = deduplicator.computeAggregationHash(predicate, aggregation, "MAX");
            String withoutPredicate = deduplicator.computeAggregationHash(null, aggregation, "MAX");

            assertThat(withPredicate).isNotEqualTo(withoutPredicate);
        }
    }

    // computeJoinHash Tests

    @Nested
    class ComputeJoinHashTests {

        @Test
        void sameJoin_producesSameHash() {
            LambdaExpression joinRel = field("phones");
            LambdaExpression biPredicate = comparison("number", 100);

            String hash1 = deduplicator.computeJoinHash(joinRel, biPredicate, "INNER", false);
            String hash2 = deduplicator.computeJoinHash(joinRel, biPredicate, "INNER", false);

            assertThat(hash1).isEqualTo(hash2);
        }

        @Test
        void differentJoinType_producesDifferentHash() {
            LambdaExpression joinRel = field("phones");
            LambdaExpression biPredicate = comparison("number", 100);

            String innerHash = deduplicator.computeJoinHash(joinRel, biPredicate, "INNER", false);
            String leftHash = deduplicator.computeJoinHash(joinRel, biPredicate, "LEFT", false);

            assertThat(innerHash).isNotEqualTo(leftHash);
        }

        @Test
        void differentCountFlag_producesDifferentHash() {
            LambdaExpression joinRel = field("phones");

            String listHash = deduplicator.computeJoinHash(joinRel, null, "INNER", false);
            String countHash = deduplicator.computeJoinHash(joinRel, null, "INNER", true);

            assertThat(listHash).isNotEqualTo(countHash);
        }

        @Test
        void nullBiPredicate_stillProducesValidHash() {
            LambdaExpression joinRel = field("phones");

            String hash = deduplicator.computeJoinHash(joinRel, null, "INNER", false);

            // FNV-1a 64-bit produces up to 16 hex characters
            assertThat(hash)
                    .isNotNull()
                    .hasSize(16);
        }
    }

    // computeJoinHash with Sorting Tests

    @Nested
    class ComputeJoinHashWithSortingTests {

        @Test
        void sameSorting_producesSameHash() {
            LambdaExpression joinRel = field("phones");
            List<SortExpression> sorts = List.of(sortAsc("number"));

            String hash1 = deduplicator.computeJoinHash(joinRel, null, sorts, "INNER", false);
            String hash2 = deduplicator.computeJoinHash(joinRel, null, sorts, "INNER", false);

            assertThat(hash1).isEqualTo(hash2);
        }

        @Test
        void differentSorting_producesDifferentHash() {
            LambdaExpression joinRel = field("phones");
            List<SortExpression> sorts1 = List.of(sortAsc("number"));
            List<SortExpression> sorts2 = List.of(sortDesc("number"));

            String hash1 = deduplicator.computeJoinHash(joinRel, null, sorts1, "INNER", false);
            String hash2 = deduplicator.computeJoinHash(joinRel, null, sorts2, "INNER", false);

            assertThat(hash1).isNotEqualTo(hash2);
        }
    }

    // computeJoinHash with SelectJoined Tests

    @Nested
    class ComputeJoinHashWithSelectJoinedTests {

        @Test
        void selectJoinedFlag_affectsHash() {
            LambdaExpression joinRel = field("phones");

            String notSelectJoined = deduplicator.computeJoinHash(joinRel, null, null, "INNER", false, false);
            String selectJoined = deduplicator.computeJoinHash(joinRel, null, null, "INNER", false, true);

            assertThat(notSelectJoined).isNotEqualTo(selectJoined);
        }
    }

    // computeJoinHash with Projection Tests

    @Nested
    class ComputeJoinHashWithProjectionTests {

        @Test
        void projectionExpression_affectsHash() {
            LambdaExpression joinRel = field("phones");
            LambdaExpression projection = field("combined");

            String withoutProjection = deduplicator.computeJoinHash(
                    new LambdaDeduplicator.JoinHashRequest(
                            joinRel, null, null, null, "INNER", false, false, false));
            String withProjection = deduplicator.computeJoinHash(
                    new LambdaDeduplicator.JoinHashRequest(
                            joinRel, null, projection, null, "INNER", false, false, true));

            assertThat(withoutProjection).isNotEqualTo(withProjection);
        }

        @Test
        void joinProjectionFlag_affectsHash() {
            LambdaExpression joinRel = field("phones");
            LambdaExpression projection = field("combined");

            String notJoinProjection = deduplicator.computeJoinHash(
                    new LambdaDeduplicator.JoinHashRequest(
                            joinRel, null, projection, null, "INNER", false, false, false));
            String isJoinProjection = deduplicator.computeJoinHash(
                    new LambdaDeduplicator.JoinHashRequest(
                            joinRel, null, projection, null, "INNER", false, false, true));

            assertThat(notJoinProjection).isNotEqualTo(isJoinProjection);
        }
    }

    // computeGroupHash Tests

    @Nested
    class ComputeGroupHashTests {

        @Test
        void sameGroupQuery_producesSameHash() {
            LambdaExpression predicate = comparison("age", 25);
            LambdaExpression groupBy = field("department");
            LambdaExpression having = comparison("count", 5);

            String hash1 = deduplicator.computeGroupHash(predicate, groupBy, having, null, null, false);
            String hash2 = deduplicator.computeGroupHash(predicate, groupBy, having, null, null, false);

            assertThat(hash1).isEqualTo(hash2);
        }

        @Test
        void differentGroupByKey_producesDifferentHash() {
            LambdaExpression groupBy1 = field("department");
            LambdaExpression groupBy2 = field("location");

            String hash1 = deduplicator.computeGroupHash(null, groupBy1, null, null, null, false);
            String hash2 = deduplicator.computeGroupHash(null, groupBy2, null, null, null, false);

            assertThat(hash1).isNotEqualTo(hash2);
        }

        @Test
        void differentHaving_producesDifferentHash() {
            LambdaExpression groupBy = field("department");
            LambdaExpression having1 = comparison("count", 5);
            LambdaExpression having2 = comparison("count", 10);

            String hash1 = deduplicator.computeGroupHash(null, groupBy, having1, null, null, false);
            String hash2 = deduplicator.computeGroupHash(null, groupBy, having2, null, null, false);

            assertThat(hash1).isNotEqualTo(hash2);
        }

        @Test
        void nullVsNonNullPredicate_producesDifferentHash() {
            LambdaExpression predicate = comparison("age", 25);
            LambdaExpression groupBy = field("department");

            String withPredicate = deduplicator.computeGroupHash(predicate, groupBy, null, null, null, false);
            String withoutPredicate = deduplicator.computeGroupHash(null, groupBy, null, null, null, false);

            assertThat(withPredicate).isNotEqualTo(withoutPredicate);
        }

        @Test
        void countVsList_producesDifferentHash() {
            LambdaExpression groupBy = field("department");

            String listHash = deduplicator.computeGroupHash(null, groupBy, null, null, null, false);
            String countHash = deduplicator.computeGroupHash(null, groupBy, null, null, null, true);

            assertThat(listHash).isNotEqualTo(countHash);
        }

        @Test
        void withGroupSelect_producesDifferentHash() {
            LambdaExpression groupBy = field("department");
            LambdaExpression groupSelect = field("stats");

            String withoutSelect = deduplicator.computeGroupHash(null, groupBy, null, null, null, false);
            String withSelect = deduplicator.computeGroupHash(null, groupBy, null, groupSelect, null, false);

            assertThat(withoutSelect).isNotEqualTo(withSelect);
        }

        @Test
        void withGroupSort_producesDifferentHash() {
            LambdaExpression groupBy = field("department");
            List<SortExpression> sorts = List.of(sortDesc("count"));

            String withoutSort = deduplicator.computeGroupHash(null, groupBy, null, null, null, false);
            String withSort = deduplicator.computeGroupHash(null, groupBy, null, null, sorts, false);

            assertThat(withoutSort).isNotEqualTo(withSort);
        }
    }

    // Executor Registration Tests

    @Nested
    class ExecutorRegistrationTests {

        @Test
        void registerExecutor_increasesUniqueCount() {
            assertThat(deduplicator.getUniqueCount()).isZero();

            deduplicator.registerExecutor("hash1", "Executor1");
            assertThat(deduplicator.getUniqueCount()).isOne();

            deduplicator.registerExecutor("hash2", "Executor2");
            assertThat(deduplicator.getUniqueCount()).isEqualTo(2);
        }

        @Test
        void registerSameHashTwice_keepsFirstExecutor() {
            // First registration succeeds (returns null)
            String firstResult = deduplicator.registerExecutor("hash1", "Executor1");
            assertThat(firstResult).isNull();

            // Second registration is ignored (returns existing executor)
            String secondResult = deduplicator.registerExecutor("hash1", "Executor2");
            assertThat(secondResult).isEqualTo("Executor1");

            // Only one entry exists
            assertThat(deduplicator.getUniqueCount()).isOne();
        }
    }
}
