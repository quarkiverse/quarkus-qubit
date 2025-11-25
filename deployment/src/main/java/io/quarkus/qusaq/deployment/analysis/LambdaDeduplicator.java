package io.quarkus.qusaq.deployment.analysis;

import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.qusaq.deployment.LambdaExpression;
import io.quarkus.qusaq.deployment.QusaqProcessor;
import org.jboss.logging.Logger;

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

    private static final Logger log = Logger.getLogger(LambdaDeduplicator.class);

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
    private static String buildSortString(List<CallSiteProcessor.SortExpression> sortExpressions) {
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
    public String computeSortingHash(List<CallSiteProcessor.SortExpression> sortExpressions) {
        String sortString = buildSortString(sortExpressions);
        String astString = SORT_PREFIX + sortString + QUERY_TYPE + "LIST";
        return computeMd5Hash(astString);
    }

    /**
     * Computes MD5 hash for WHERE+SORT or SELECT+SORT query (Phase 3).
     */
    public String computeQueryWithSortingHash(
            LambdaExpression expression,
            List<CallSiteProcessor.SortExpression> sortExpressions,
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
            List<CallSiteProcessor.SortExpression> sortExpressions,
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
     * Returns true if lambda is duplicate and reuses existing executor.
     * Query type information is already encoded in the lambdaHash parameter.
     */
    public boolean handleDuplicateLambda(
            String callSiteId,
            String lambdaHash,
            boolean isCountQuery,
            boolean isAggregationQuery,
            int capturedVarCount,
            AtomicInteger deduplicatedCount,
            BuildProducer<QusaqProcessor.QueryTransformationBuildItem> queryTransformations) {

        if (lambdaHashToExecutor.containsKey(lambdaHash)) {
            String existingExecutor = lambdaHashToExecutor.get(lambdaHash);
            log.debugf("Deduplicated lambda at %s (reusing %s)", callSiteId, existingExecutor);
            deduplicatedCount.incrementAndGet();

            queryTransformations.produce(
                    new QusaqProcessor.QueryTransformationBuildItem(callSiteId, existingExecutor,
                            Object.class, isCountQuery, isAggregationQuery, capturedVarCount));
            return true;
        }

        return false;
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
