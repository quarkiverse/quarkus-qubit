package io.quarkiverse.qubit.deployment.analysis;

import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkiverse.qubit.deployment.ast.LambdaExpression;
import io.quarkiverse.qubit.deployment.QubitProcessor;
import io.quarkiverse.qubit.deployment.analysis.LambdaAnalysisResult.SortExpression;
import io.quarkus.logging.Log;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Deduplicates lambda expressions to reuse executors and reduce bytecode size.
 */
public class LambdaDeduplicator {

    private static final String QUERY_TYPE = "|queryType=";
    private static final String WHERE_PREFIX = "WHERE=";
    private static final String SELECT_PREFIX = "|SELECT=";
    private static final String SORT_PREFIX = "SORT=";
    private static final String SORT_SEPARATOR = "|SORT=";

    private final Map<String, String> lambdaHashToExecutor = new HashMap<>();

    /**
     * Computes MD5 hash of input string.
     * Falls back to hashCode() if MD5 algorithm is unavailable.
     */
    private static String computeMd5Hash(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(input.getBytes(UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            return String.valueOf(input.hashCode());
        }
    }

    /**
     * Converts sort expressions to comma-separated string representation.
     */
    private static String buildSortString(List<SortExpression> sortExpressions) {
        return sortExpressions.stream()
                .map(s -> s.keyExtractor().toString() + s.direction().getSuffix())
                .collect(Collectors.joining(","));
    }

    /**
     * Computes MD5 hash for lambda expression and query type.
     */
    public String computeLambdaHash(LambdaExpression expression, boolean isCountQuery, boolean isProjectionQuery) {
        String queryType = CallSiteProcessor.getQueryType(isCountQuery, !isProjectionQuery, isProjectionQuery);
        String astString = expression.toString() + QUERY_TYPE + queryType;
        return computeMd5Hash(astString);
    }

    /**
     * Computes MD5 hash for combined where + select query (Phase 2.2).
     */
    public String computeCombinedHash(LambdaExpression predicateExpression,
                                     LambdaExpression projectionExpression,
                                     boolean isCountQuery) {
        String queryType = CallSiteProcessor.getQueryType(isCountQuery, true, true);
        String astString = WHERE_PREFIX + predicateExpression.toString() +
                          SELECT_PREFIX + projectionExpression.toString() +
                          QUERY_TYPE + queryType;
        return computeMd5Hash(astString);
    }

    /**
     * Computes MD5 hash for sorting-only query (Phase 3).
     */
    public String computeSortingHash(List<SortExpression> sortExpressions) {
        String sortString = buildSortString(sortExpressions);
        String astString = SORT_PREFIX + sortString + QUERY_TYPE + "LIST";
        return computeMd5Hash(astString);
    }

    /**
     * Computes MD5 hash for WHERE+SORT or SELECT+SORT query (Phase 3).
     */
    public String computeQueryWithSortingHash(
            LambdaExpression expression,
            List<SortExpression> sortExpressions,
            boolean isCountQuery,
            boolean isProjectionQuery) {

        String queryType = CallSiteProcessor.getQueryType(isCountQuery, !isProjectionQuery, isProjectionQuery);
        String sortString = buildSortString(sortExpressions);
        String astString = expression.toString() + SORT_SEPARATOR + sortString + QUERY_TYPE + queryType;
        return computeMd5Hash(astString);
    }

    /**
     * Computes MD5 hash for WHERE+SELECT+SORT query (Phase 3).
     */
    public String computeFullQueryHash(
            LambdaExpression predicateExpression,
            LambdaExpression projectionExpression,
            List<SortExpression> sortExpressions,
            boolean isCountQuery) {

        String queryType = CallSiteProcessor.getQueryType(isCountQuery, true, true);
        String sortString = buildSortString(sortExpressions);
        String astString = WHERE_PREFIX + predicateExpression.toString() +
                          SELECT_PREFIX + projectionExpression.toString() +
                          SORT_SEPARATOR + sortString + QUERY_TYPE + queryType;
        return computeMd5Hash(astString);
    }

    /**
     * Computes MD5 hash for aggregation query (Phase 5).
     * Supports optional WHERE predicate before aggregation.
     *
     * @param predicateExpression WHERE clause (null if no filtering)
     * @param aggregationExpression Aggregation mapper lambda (e.g., p -> p.salary)
     * @param aggregationType Aggregation type: MIN, MAX, AVG, SUM_INTEGER, SUM_LONG, SUM_DOUBLE
     * @return MD5 hash uniquely identifying this aggregation query
     */
    public String computeAggregationHash(
            LambdaExpression predicateExpression,
            LambdaExpression aggregationExpression,
            String aggregationType) {

        StringBuilder astString = new StringBuilder();

        // Include WHERE predicate if present
        if (predicateExpression != null) {
            astString.append(WHERE_PREFIX).append(predicateExpression.toString());
        }

        // Include aggregation mapper (e.g., "p -> p.salary")
        astString.append("|AGG=").append(aggregationExpression.toString());

        // Include aggregation type (MIN/MAX/AVG/SUM_*)
        astString.append("|TYPE=").append(aggregationType);

        return computeMd5Hash(astString.toString());
    }

    /**
     * Computes MD5 hash for join query (Iteration 6).
     * Supports optional bi-entity predicate after join.
     *
     * @param joinRelationshipExpression Join relationship lambda (e.g., p -> p.phones)
     * @param biEntityPredicateExpression Bi-entity WHERE clause (null if no filtering)
     * @param joinType Join type: INNER or LEFT
     * @param isCountQuery True if this is a count query (JoinStream.count())
     * @return MD5 hash uniquely identifying this join query
     */
    public String computeJoinHash(
            LambdaExpression joinRelationshipExpression,
            LambdaExpression biEntityPredicateExpression,
            String joinType,
            boolean isCountQuery) {
        return computeJoinHash(joinRelationshipExpression, biEntityPredicateExpression,
                null, joinType, isCountQuery);
    }

    /**
     * Computes MD5 hash for join query with sorting (Iteration 6.5).
     * Supports optional bi-entity predicate and sort expressions after join.
     *
     * @param joinRelationshipExpression Join relationship lambda (e.g., p -> p.phones)
     * @param biEntityPredicateExpression Bi-entity WHERE clause (null if no filtering)
     * @param sortExpressions List of sort expressions (null or empty if no sorting)
     * @param joinType Join type: INNER or LEFT
     * @param isCountQuery True if this is a count query (JoinStream.count())
     * @return MD5 hash uniquely identifying this join query
     */
    public String computeJoinHash(
            LambdaExpression joinRelationshipExpression,
            LambdaExpression biEntityPredicateExpression,
            List<SortExpression> sortExpressions,
            String joinType,
            boolean isCountQuery) {
        return computeJoinHash(joinRelationshipExpression, biEntityPredicateExpression,
                sortExpressions, joinType, isCountQuery, false);
    }

    /**
     * Computes MD5 hash for join query with sorting and selectJoined (Iteration 6.5).
     * Supports optional bi-entity predicate, sort expressions, and selectJoined flag.
     *
     * @param joinRelationshipExpression Join relationship lambda (e.g., p -> p.phones)
     * @param biEntityPredicateExpression Bi-entity WHERE clause (null if no filtering)
     * @param sortExpressions List of sort expressions (null or empty if no sorting)
     * @param joinType Join type: INNER or LEFT
     * @param isCountQuery True if this is a count query (JoinStream.count())
     * @param isSelectJoined True if selectJoined() was called (returns joined entities)
     * @return MD5 hash uniquely identifying this join query
     */
    public String computeJoinHash(
            LambdaExpression joinRelationshipExpression,
            LambdaExpression biEntityPredicateExpression,
            List<SortExpression> sortExpressions,
            String joinType,
            boolean isCountQuery,
            boolean isSelectJoined) {
        return computeJoinHash(joinRelationshipExpression, biEntityPredicateExpression,
                null, sortExpressions, joinType, isCountQuery, isSelectJoined, false);
    }

    /**
     * Computes MD5 hash for join query with projection (Iteration 6.6).
     * Supports optional bi-entity predicate, bi-entity projection, sort expressions.
     *
     * @param joinRelationshipExpression Join relationship lambda (e.g., p -> p.phones)
     * @param biEntityPredicateExpression Bi-entity WHERE clause (null if no filtering)
     * @param biEntityProjectionExpression Bi-entity SELECT projection (null if no projection)
     * @param sortExpressions List of sort expressions (null or empty if no sorting)
     * @param joinType Join type: INNER or LEFT
     * @param isCountQuery True if this is a count query (JoinStream.count())
     * @param isSelectJoined True if selectJoined() was called (returns joined entities)
     * @param isJoinProjection True if select() with BiQuerySpec was called (returns projected objects)
     * @return MD5 hash uniquely identifying this join query
     */
    public String computeJoinHash(
            LambdaExpression joinRelationshipExpression,
            LambdaExpression biEntityPredicateExpression,
            LambdaExpression biEntityProjectionExpression,
            List<SortExpression> sortExpressions,
            String joinType,
            boolean isCountQuery,
            boolean isSelectJoined,
            boolean isJoinProjection) {

        StringBuilder astString = new StringBuilder();

        // Include join relationship (e.g., "p -> p.phones")
        astString.append("JOIN=");
        if (joinRelationshipExpression != null) {
            astString.append(joinRelationshipExpression.toString());
        }

        // Include bi-entity predicate if present
        if (biEntityPredicateExpression != null) {
            astString.append("|BI_WHERE=").append(biEntityPredicateExpression.toString());
        }

        // Iteration 6.6: Include bi-entity projection if present
        if (biEntityProjectionExpression != null) {
            astString.append("|BI_SELECT=").append(biEntityProjectionExpression.toString());
        }

        // Include sort expressions if present
        if (sortExpressions != null && !sortExpressions.isEmpty()) {
            String sortString = buildSortString(sortExpressions);
            astString.append(SORT_SEPARATOR).append(sortString);
        }

        // Include join type (INNER/LEFT)
        astString.append("|JOIN_TYPE=").append(joinType);

        // Include selectJoined flag
        if (isSelectJoined) {
            astString.append("|SELECT_JOINED=true");
        }

        // Iteration 6.6: Include joinProjection flag
        if (isJoinProjection) {
            astString.append("|JOIN_PROJECTION=true");
        }

        // Include query type (LIST or COUNT) to differentiate
        astString.append("|QUERY_TYPE=").append(isCountQuery ? "COUNT" : "LIST");

        return computeMd5Hash(astString.toString());
    }

    /**
     * Returns true if lambda is duplicate and reuses existing executor.
     * Query type information is already encoded in the lambdaHash parameter.
     *
     * <p>CS-006: Refactored to use QueryCharacteristics parameter object instead of
     * 6 boolean parameters (isCountQuery, isAggregationQuery, isJoinQuery,
     * isSelectJoined, isJoinProjection, isGroupQuery).
     *
     * @param callSiteId unique identifier for the call site
     * @param lambdaHash MD5 hash of the lambda expression
     * @param characteristics query type characteristics (CS-006: replaces 6 boolean parameters)
     * @param capturedVarCount number of captured variables
     * @param deduplicatedCount counter for deduplicated lambdas
     * @param queryTransformations build producer for query transformations
     * @return true if this is a duplicate lambda and an existing executor was reused
     */
    public boolean handleDuplicateLambda(
            String callSiteId,
            String lambdaHash,
            QueryCharacteristics characteristics,
            int capturedVarCount,
            AtomicInteger deduplicatedCount,
            BuildProducer<QubitProcessor.QueryTransformationBuildItem> queryTransformations) {

        if (lambdaHashToExecutor.containsKey(lambdaHash)) {
            String existingExecutor = lambdaHashToExecutor.get(lambdaHash);
            Log.debugf("Deduplicated lambda at %s (reusing %s)", callSiteId, existingExecutor);
            deduplicatedCount.incrementAndGet();

            queryTransformations.produce(
                    new QubitProcessor.QueryTransformationBuildItem(
                            callSiteId, existingExecutor, Object.class, characteristics, capturedVarCount));
            return true;
        }

        return false;
    }

    /**
     * Computes MD5 hash for group query (Iteration 7).
     * Supports groupBy(), having(), select(), and sortedBy() in group context.
     *
     * @param predicateExpression Pre-grouping WHERE clause (null if no filtering)
     * @param groupByKeyExpression groupBy() key extractor lambda (e.g., p -> p.department)
     * @param havingExpression having() predicate (null if no having)
     * @param groupSelectExpression select() projection in group context (null if no select)
     * @param groupSortExpressions sortedBy() in group context (null or empty if no sorting)
     * @param isCountQuery True if this is a count query (counting groups)
     * @return MD5 hash uniquely identifying this group query
     */
    public String computeGroupHash(
            LambdaExpression predicateExpression,
            LambdaExpression groupByKeyExpression,
            LambdaExpression havingExpression,
            LambdaExpression groupSelectExpression,
            List<SortExpression> groupSortExpressions,
            boolean isCountQuery) {

        StringBuilder astString = new StringBuilder();

        // Include WHERE predicate if present (pre-grouping filter)
        if (predicateExpression != null) {
            astString.append(WHERE_PREFIX).append(predicateExpression.toString());
        }

        // Include groupBy key expression
        astString.append("|GROUP_BY=");
        if (groupByKeyExpression != null) {
            astString.append(groupByKeyExpression.toString());
        }

        // Include having predicate if present
        if (havingExpression != null) {
            astString.append("|HAVING=").append(havingExpression.toString());
        }

        // Include group select expression if present
        if (groupSelectExpression != null) {
            astString.append("|GROUP_SELECT=").append(groupSelectExpression.toString());
        }

        // Include group sort expressions if present
        if (groupSortExpressions != null && !groupSortExpressions.isEmpty()) {
            String sortString = buildSortString(groupSortExpressions);
            astString.append(SORT_SEPARATOR).append(sortString);
        }

        // Include query type (LIST or COUNT) to differentiate
        astString.append("|QUERY_TYPE=").append(isCountQuery ? "COUNT" : "LIST");

        return computeMd5Hash(astString.toString());
    }

    /**
     * Registers executor class for lambda hash.
     */
    public void registerExecutor(String lambdaHash, String executorClassName) {
        lambdaHashToExecutor.put(lambdaHash, executorClassName);
    }

    /**
     * Returns number of unique lambda expressions.
     */
    public int getUniqueCount() {
        return lambdaHashToExecutor.size();
    }
}
