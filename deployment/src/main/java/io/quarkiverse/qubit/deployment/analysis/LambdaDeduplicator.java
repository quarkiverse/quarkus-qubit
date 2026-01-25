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

import static io.quarkiverse.qubit.runtime.internal.QubitConstants.QUERY_TYPE_LIST;
import static java.util.Objects.requireNonNull;

/**
 * Deduplicates lambda expressions to reuse executors and reduce bytecode size.
 * <p>
 * Uses {@link HashBuilder} to construct query hashes in a consistent, fluent manner.
 * <p>
 * Supports two deduplication strategies:
 * <ul>
 *   <li><b>Early deduplication</b>: Uses bytecode signature (method name + descriptor + query type)
 *       to detect duplicates BEFORE expensive bytecode analysis. This is a fast check that
 *       avoids redundant analysis work.</li>
 *   <li><b>Late deduplication</b>: Uses analyzed expression hash to detect semantic duplicates
 *       AFTER analysis. This catches duplicates that have identical semantics but different
 *       bytecode signatures.</li>
 * </ul>
 */
public class LambdaDeduplicator {

    private final Map<String, String> lambdaHashToExecutor = new ConcurrentHashMap<>();

    /**
     * Maps bytecode signature to lambda hash for early deduplication.
     * Key: bytecode signature (computed before analysis)
     * Value: lambda hash (computed after analysis)
     */
    private final Map<String, String> bytecodeSignatureToLambdaHash = new ConcurrentHashMap<>();

    /**
     * Cached analysis results for early deduplication hits.
     * Key: bytecode signature
     * Value: cached AnalysisOutcome.Success
     */
    private final Map<String, CachedAnalysisResult> cachedAnalysisResults = new ConcurrentHashMap<>();

    /**
     * Cached analysis result for early deduplication.
     */
    public record CachedAnalysisResult(
            String lambdaHash,
            String executorClassName) {

        public CachedAnalysisResult {
            requireNonNull(lambdaHash, "lambdaHash must not be null");
            requireNonNull(executorClassName, "executorClassName must not be null");
        }
    }

    /**
     * Computes bytecode signature for early deduplication.
     * This hash is computed BEFORE bytecode analysis using structural elements only.
     *
     * @param callSite the lambda call site
     * @return bytecode signature hash
     */
    public String computeBytecodeSignature(InvokeDynamicScanner.LambdaCallSite callSite) {
        StringBuilder sb = new StringBuilder();

        // Include owner class and method for uniqueness
        sb.append(callSite.ownerClassName()).append("|");
        sb.append(callSite.methodName()).append("|");

        // Include all lambda components
        appendLambdaPairs(sb, "W", callSite.predicateLambdas());
        appendSingleLambda(sb, "S", callSite.projectionLambdaMethodName(),
                callSite.projectionLambdaMethodDescriptor());
        appendSortLambdas(sb, "O", callSite.sortLambdas());
        appendSingleLambda(sb, "A", callSite.aggregationLambdaMethodName(),
                callSite.aggregationLambdaMethodDescriptor());
        appendSingleLambda(sb, "J", callSite.joinRelationshipLambdaMethodName(),
                callSite.joinRelationshipLambdaDescriptor());
        appendLambdaPairs(sb, "BW", callSite.biEntityPredicateLambdas());
        appendSingleLambda(sb, "BS", callSite.biEntityProjectionLambdaMethodName(),
                callSite.biEntityProjectionLambdaDescriptor());
        appendSingleLambda(sb, "G", callSite.groupByLambdaMethodName(),
                callSite.groupByLambdaDescriptor());
        appendLambdaPairs(sb, "H", callSite.havingLambdas());
        appendLambdaPairs(sb, "GS", callSite.groupSelectLambdas());
        appendSortLambdas(sb, "GO", callSite.groupSortLambdas());

        // Include query modifiers
        appendQueryModifiers(sb, callSite);

        return HashBuilder.create().queryType(sb.toString()).buildHash();
    }

    /** Appends lambda pairs to signature builder. */
    private void appendLambdaPairs(StringBuilder sb, String prefix,
            List<InvokeDynamicScanner.LambdaPair> pairs) {
        if (pairs == null) {
            return;
        }
        for (InvokeDynamicScanner.LambdaPair pair : pairs) {
            sb.append(prefix).append(":").append(pair.methodName())
              .append(":").append(pair.descriptor()).append("|");
        }
    }

    /** Appends sort lambdas to signature builder. */
    private void appendSortLambdas(StringBuilder sb, String prefix,
            List<InvokeDynamicScanner.SortLambda> sortLambdas) {
        if (sortLambdas == null) {
            return;
        }
        for (InvokeDynamicScanner.SortLambda sl : sortLambdas) {
            sb.append(prefix).append(":").append(sl.methodName())
              .append(":").append(sl.descriptor())
              .append(":").append(sl.direction()).append("|");
        }
    }

    /** Appends single lambda to signature builder. */
    private void appendSingleLambda(StringBuilder sb, String prefix,
            String methodName, String descriptor) {
        if (methodName == null) {
            return;
        }
        sb.append(prefix).append(":").append(methodName)
          .append(":").append(descriptor).append("|");
    }

    /** Appends query modifiers to signature builder. */
    private void appendQueryModifiers(StringBuilder sb, InvokeDynamicScanner.LambdaCallSite callSite) {
        sb.append("count=").append(callSite.isCountQuery()).append("|");
        sb.append("distinct=").append(callSite.hasDistinct()).append("|");
        if (callSite.joinType() != null) {
            sb.append("joinType=").append(callSite.joinType()).append("|");
        }
        sb.append("selectJoined=").append(callSite.isSelectJoined()).append("|");
        sb.append("selectKey=").append(callSite.isGroupSelectKey()).append("|");
    }

    /**
     * Checks if a bytecode signature has already been analyzed.
     *
     * @param bytecodeSignature the bytecode signature
     * @return cached result if found, null otherwise
     */
    public CachedAnalysisResult getCachedResult(String bytecodeSignature) {
        return cachedAnalysisResults.get(bytecodeSignature);
    }

    /**
     * Registers a bytecode signature with its analysis result for early deduplication.
     *
     * @param bytecodeSignature the bytecode signature
     * @param lambdaHash the lambda hash (computed after analysis)
     * @param executorClassName the generated executor class name
     */
    public void registerBytecodeSignature(String bytecodeSignature, String lambdaHash, String executorClassName) {
        bytecodeSignatureToLambdaHash.putIfAbsent(bytecodeSignature, lambdaHash);
        cachedAnalysisResults.putIfAbsent(bytecodeSignature,
                new CachedAnalysisResult(lambdaHash, executorClassName));
    }

    /**
     * Returns the number of early deduplication cache entries.
     */
    public int getEarlyDeduplicationCacheSize() {
        return cachedAnalysisResults.size();
    }

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
