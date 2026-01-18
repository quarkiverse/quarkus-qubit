package io.quarkiverse.qubit.deployment.analysis;

import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkiverse.qubit.deployment.ast.LambdaExpression;
import io.quarkiverse.qubit.deployment.QubitProcessor;
import io.quarkiverse.qubit.deployment.analysis.LambdaAnalysisResult.SortExpression;
import io.quarkus.logging.Log;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static io.quarkiverse.qubit.runtime.QubitConstants.QUERY_TYPE_LIST;
import static java.util.Objects.requireNonNull;

/**
 * Deduplicates lambda expressions to reuse executors and reduce bytecode size.
 * <p>
 * Uses {@link HashBuilder} to construct query hashes in a consistent, fluent manner.
 */
public class LambdaDeduplicator {

    private final Map<String, String> lambdaHashToExecutor = new ConcurrentHashMap<>();

    /** Computes MD5 hash for lambda expression and query type. */
    public String computeLambdaHash(LambdaExpression expression, boolean isCountQuery, boolean isProjectionQuery) {
        String queryType = CallSiteProcessor.getQueryType(isCountQuery, !isProjectionQuery, isProjectionQuery);
        return HashBuilder.create()
                .expression(expression)
                .queryType(queryType)
                .buildHash();
    }

    /** Computes MD5 hash for combined WHERE + SELECT query. */
    public String computeCombinedHash(LambdaExpression predicateExpression,
                                     LambdaExpression projectionExpression,
                                     boolean isCountQuery) {
        String queryType = CallSiteProcessor.getQueryType(isCountQuery, true, true);
        return HashBuilder.create()
                .where(predicateExpression)
                .select(projectionExpression)
                .queryType(queryType)
                .buildHash();
    }

    /** Computes MD5 hash for sorting-only query. */
    public String computeSortingHash(List<SortExpression> sortExpressions) {
        return HashBuilder.create()
                .sort(sortExpressions)
                .queryType(QUERY_TYPE_LIST)
                .buildHash();
    }

    /** Computes MD5 hash for WHERE+SORT or SELECT+SORT query. */
    public String computeQueryWithSortingHash(
            LambdaExpression expression,
            List<SortExpression> sortExpressions,
            boolean isCountQuery,
            boolean isProjectionQuery) {

        String queryType = CallSiteProcessor.getQueryType(isCountQuery, !isProjectionQuery, isProjectionQuery);
        return HashBuilder.create()
                .expression(expression)
                .sort(sortExpressions)
                .queryType(queryType)
                .buildHash();
    }

    /** Computes MD5 hash for WHERE+SELECT+SORT query. */
    public String computeFullQueryHash(
            LambdaExpression predicateExpression,
            LambdaExpression projectionExpression,
            List<SortExpression> sortExpressions,
            boolean isCountQuery) {

        String queryType = CallSiteProcessor.getQueryType(isCountQuery, true, true);
        return HashBuilder.create()
                .where(predicateExpression)
                .select(projectionExpression)
                .sort(sortExpressions)
                .queryType(queryType)
                .buildHash();
    }

    /** Computes MD5 hash for aggregation query with optional WHERE. */
    public String computeAggregationHash(
            LambdaExpression predicateExpression,
            LambdaExpression aggregationExpression,
            String aggregationType) {

        return HashBuilder.create()
                .where(predicateExpression)
                .aggregation(aggregationExpression)
                .aggregationType(aggregationType)
                .buildHash();
    }

    /** Computes MD5 hash for join query with optional bi-entity predicate. */
    public String computeJoinHash(
            LambdaExpression joinRelationshipExpression,
            LambdaExpression biEntityPredicateExpression,
            String joinType,
            boolean isCountQuery) {
        return computeJoinHash(joinRelationshipExpression, biEntityPredicateExpression,
                null, joinType, isCountQuery);
    }

    /** Computes MD5 hash for join query with sorting. */
    public String computeJoinHash(
            LambdaExpression joinRelationshipExpression,
            LambdaExpression biEntityPredicateExpression,
            List<SortExpression> sortExpressions,
            String joinType,
            boolean isCountQuery) {
        return computeJoinHash(joinRelationshipExpression, biEntityPredicateExpression,
                sortExpressions, joinType, isCountQuery, false);
    }

    /** Computes MD5 hash for join query with sorting and selectJoined flag. */
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

    /** Computes MD5 hash for join query with bi-entity projection. */
    public String computeJoinHash(
            LambdaExpression joinRelationshipExpression,
            LambdaExpression biEntityPredicateExpression,
            LambdaExpression biEntityProjectionExpression,
            List<SortExpression> sortExpressions,
            String joinType,
            boolean isCountQuery,
            boolean isSelectJoined,
            boolean isJoinProjection) {

        return HashBuilder.create()
                .join(joinRelationshipExpression)
                .biWhere(biEntityPredicateExpression)
                .biSelect(biEntityProjectionExpression)
                .sort(sortExpressions)
                .joinType(joinType)
                .selectJoined(isSelectJoined)
                .joinProjection(isJoinProjection)
                .queryType(isCountQuery)
                .buildHash();
    }

    /** Returns true if lambda is duplicate and reuses existing executor. */
    public boolean handleDuplicateLambda(
            String callSiteId,
            String lambdaHash,
            QueryCharacteristics characteristics,
            int capturedVarCount,
            String entityClassName,
            LambdaExpression predicateExpression,
            LambdaExpression projectionExpression,
            LambdaExpression sortExpression,
            String terminalMethodName,
            boolean sortDescending,
            AtomicInteger deduplicatedCount,
            BuildProducer<QubitProcessor.QueryTransformationBuildItem> queryTransformations,
            boolean logDeduplication) {

        // Use atomic get instead of containsKey+get to avoid race conditions
        String existingExecutor = lambdaHashToExecutor.get(lambdaHash);
        if (existingExecutor != null) {

            if (logDeduplication) {
                Log.debugf("Deduplicated lambda at %s (reusing %s)", callSiteId, existingExecutor);
            }

            deduplicatedCount.incrementAndGet();

            queryTransformations.produce(
                    QubitProcessor.QueryTransformationBuildItem.builder()
                            .queryId(callSiteId)
                            .generatedClassName(existingExecutor)
                            .entityClassName(entityClassName)
                            .characteristics(characteristics)
                            .capturedVarCount(capturedVarCount)
                            .predicateExpression(predicateExpression)
                            .projectionExpression(projectionExpression)
                            .sortExpression(sortExpression)
                            .terminalMethodName(terminalMethodName)
                            .sortDescending(sortDescending)
                            .build());
            return true;
        }

        return false;
    }

    /** Computes MD5 hash for GROUP BY query with HAVING, select, and sort. */
    public String computeGroupHash(
            LambdaExpression predicateExpression,
            LambdaExpression groupByKeyExpression,
            LambdaExpression havingExpression,
            LambdaExpression groupSelectExpression,
            List<SortExpression> groupSortExpressions,
            boolean isCountQuery) {

        return HashBuilder.create()
                .where(predicateExpression)
                .groupBy(groupByKeyExpression)
                .having(havingExpression)
                .groupSelect(groupSelectExpression)
                .sort(groupSortExpressions)
                .queryType(isCountQuery)
                .buildHash();
    }

    /** Registers executor class for lambda hash (atomic putIfAbsent). */
    public String registerExecutor(String lambdaHash, String executorClassName) {
        return lambdaHashToExecutor.putIfAbsent(lambdaHash, executorClassName);
    }

    /** Returns number of unique lambda expressions. */
    public int getUniqueCount() {
        return lambdaHashToExecutor.size();
    }

    // ========== Parameter Objects ==========

    /** Bundles deduplication request parameters to reduce method parameter count. */
    public record DeduplicationRequest(
            String callSiteId,
            String lambdaHash,
            QueryCharacteristics characteristics,
            int capturedVarCount,
            String entityClassName,
            LambdaExpression predicateExpression,
            LambdaExpression projectionExpression,
            LambdaExpression sortExpression,
            String terminalMethodName,
            boolean sortDescending) {

        /** Validates required fields. */
        public DeduplicationRequest {
            requireNonNull(callSiteId, "callSiteId must not be null");
            requireNonNull(lambdaHash, "lambdaHash must not be null");
            requireNonNull(characteristics, "characteristics must not be null");
            requireNonNull(entityClassName, "entityClassName must not be null");
        }
    }

    /** Bundles deduplication processing context parameters. */
    public record DeduplicationContext(
            AtomicInteger deduplicatedCount,
            BuildProducer<QubitProcessor.QueryTransformationBuildItem> queryTransformations,
            boolean logDeduplication) {

        /** Validates required fields. */
        public DeduplicationContext {
            requireNonNull(deduplicatedCount, "deduplicatedCount must not be null");
            requireNonNull(queryTransformations, "queryTransformations must not be null");
        }
    }

    /** Returns true if lambda is duplicate (overload using parameter objects). */
    public boolean handleDuplicateLambda(DeduplicationRequest request, DeduplicationContext context) {
        return handleDuplicateLambda(
                request.callSiteId(),
                request.lambdaHash(),
                request.characteristics(),
                request.capturedVarCount(),
                request.entityClassName(),
                request.predicateExpression(),
                request.projectionExpression(),
                request.sortExpression(),
                request.terminalMethodName(),
                request.sortDescending(),
                context.deduplicatedCount(),
                context.queryTransformations(),
                context.logDeduplication());
    }
}
