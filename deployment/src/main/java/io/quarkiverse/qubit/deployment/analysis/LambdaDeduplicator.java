package io.quarkiverse.qubit.deployment.analysis;

import static io.quarkiverse.qubit.runtime.internal.QubitConstants.QUERY_TYPE_LIST;
import static java.util.Objects.requireNonNull;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import io.quarkiverse.qubit.deployment.QubitProcessor;
import io.quarkiverse.qubit.deployment.analysis.LambdaAnalysisResult.SortExpression;
import io.quarkiverse.qubit.deployment.ast.LambdaExpression;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.logging.Log;

/**
 * Deduplicates lambda expressions to reuse executors and reduce bytecode size.
 * Uses {@link HashBuilder} for consistent query hashing. Early deduplication checks
 * bytecode signatures before analysis; late deduplication catches semantic duplicates after.
 */
public class LambdaDeduplicator {

    private final Map<String, String> lambdaHashToExecutor = new ConcurrentHashMap<>();

    /** Bytecode signature -> lambda hash, for early deduplication. */
    private final Map<String, String> bytecodeSignatureToLambdaHash = new ConcurrentHashMap<>();

    /** Bytecode signature -> cached analysis result, for early deduplication hits. */
    private final Map<String, CachedAnalysisResult> cachedAnalysisResults = new ConcurrentHashMap<>();

    /** Cached analysis result for early deduplication. */
    public record CachedAnalysisResult(
            String lambdaHash,
            String executorClassName) {

        public CachedAnalysisResult {
            requireNonNull(lambdaHash, "lambdaHash must not be null");
            requireNonNull(executorClassName, "executorClassName must not be null");
        }
    }

    /**
     * Computes bytecode signature for early deduplication using structural elements only.
     * Excludes ownerClassName and methodName so identical lambdas across call sites deduplicate.
     */
    public String computeBytecodeSignature(CallSite callSite) {
        StringBuilder sb = new StringBuilder();
        switch (callSite) {
            case CallSite.SimpleCallSite s -> {
                appendLambdaPairs(sb, "W", s.predicateLambdas());
                appendSingleLambda(sb, "S", s.projectionLambdaMethodName(), s.projectionLambdaMethodDescriptor());
                appendSortLambdas(sb, "O", s.sortLambdas());
            }
            case CallSite.AggregationCallSite a -> {
                appendLambdaPairs(sb, "W", a.predicateLambdas());
                appendSingleLambda(sb, "A", a.aggregationLambdaMethodName(), a.aggregationLambdaMethodDescriptor());
            }
            case CallSite.JoinCallSite j -> {
                appendLambdaPairs(sb, "W", j.predicateLambdas());
                appendSortLambdas(sb, "O", j.sortLambdas());
                appendSingleLambda(sb, "J", j.joinRelationshipLambdaMethodName(), j.joinRelationshipLambdaDescriptor());
                appendLambdaPairs(sb, "BW", j.biEntityPredicateLambdas());
                appendSingleLambda(sb, "BS", j.biEntityProjectionLambdaMethodName(), j.biEntityProjectionLambdaDescriptor());
            }
            case CallSite.GroupCallSite g -> {
                appendLambdaPairs(sb, "W", g.predicateLambdas());
                appendSingleLambda(sb, "G", g.groupByLambdaMethodName(), g.groupByLambdaDescriptor());
                appendLambdaPairs(sb, "H", g.havingLambdas());
                appendLambdaPairs(sb, "GS", g.groupSelectLambdas());
                appendSortLambdas(sb, "GO", g.groupSortLambdas());
            }
        }
        appendQueryModifiers(sb, callSite);
        return HashBuilder.create().queryType(sb.toString()).buildHash();
    }

    /** Appends lambda pairs to signature builder. */
    private void appendLambdaPairs(StringBuilder sb, String prefix,
            List<CallSite.LambdaPair> pairs) {
        if (pairs == null) {
            return;
        }
        for (CallSite.LambdaPair pair : pairs) {
            sb.append(prefix).append(":").append(pair.methodName())
                    .append(":").append(pair.descriptor()).append("|");
        }
    }

    /** Appends sort lambdas to signature builder. */
    private void appendSortLambdas(StringBuilder sb, String prefix,
            List<CallSite.SortLambda> sortLambdas) {
        if (sortLambdas == null) {
            return;
        }
        for (CallSite.SortLambda sl : sortLambdas) {
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
    private void appendQueryModifiers(StringBuilder sb, CallSite callSite) {
        sb.append("count=").append(callSite.isCountQuery()).append("|");
        sb.append("distinct=").append(callSite.hasDistinct()).append("|");
        if (callSite instanceof CallSite.JoinCallSite j) {
            sb.append("joinType=").append(j.joinType()).append("|");
            sb.append("selectJoined=").append(j.isSelectJoined()).append("|");
        }
        if (callSite instanceof CallSite.GroupCallSite g) {
            sb.append("selectKey=").append(g.isGroupSelectKey()).append("|");
        }
    }

    /** Returns cached result for the given bytecode signature, or null if not found. */
    public CachedAnalysisResult getCachedResult(String bytecodeSignature) {
        return cachedAnalysisResults.get(bytecodeSignature);
    }

    /** Registers a bytecode signature with its analysis result for early deduplication. */
    public void registerBytecodeSignature(String bytecodeSignature, String lambdaHash, String executorClassName) {
        bytecodeSignatureToLambdaHash.putIfAbsent(bytecodeSignature, lambdaHash);
        cachedAnalysisResults.putIfAbsent(bytecodeSignature,
                new CachedAnalysisResult(lambdaHash, executorClassName));
    }

    /** Returns the number of early deduplication cache entries. */
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
        return computeJoinHash(new JoinHashRequest(joinRelationshipExpression, biEntityPredicateExpression,
                null, null, joinType, isCountQuery, false, false));
    }

    /** Computes MD5 hash for join query with sorting. */
    public String computeJoinHash(
            LambdaExpression joinRelationshipExpression,
            LambdaExpression biEntityPredicateExpression,
            List<SortExpression> sortExpressions,
            String joinType,
            boolean isCountQuery) {
        return computeJoinHash(new JoinHashRequest(joinRelationshipExpression, biEntityPredicateExpression,
                null, sortExpressions, joinType, isCountQuery, false, false));
    }

    /** Computes MD5 hash for join query with sorting and selectJoined flag. */
    public String computeJoinHash(
            LambdaExpression joinRelationshipExpression,
            LambdaExpression biEntityPredicateExpression,
            List<SortExpression> sortExpressions,
            String joinType,
            boolean isCountQuery,
            boolean isSelectJoined) {
        return computeJoinHash(new JoinHashRequest(joinRelationshipExpression, biEntityPredicateExpression,
                null, sortExpressions, joinType, isCountQuery, isSelectJoined, false));
    }

    /** Computes MD5 hash for join query using parameter object. */
    public String computeJoinHash(JoinHashRequest request) {
        return HashBuilder.create()
                .join(request.joinRelationshipExpression())
                .biWhere(request.biEntityPredicateExpression())
                .biSelect(request.biEntityProjectionExpression())
                .sort(request.sortExpressions())
                .joinType(request.joinType())
                .selectJoined(request.isSelectJoined())
                .joinProjection(request.isJoinProjection())
                .queryType(request.isCountQuery())
                .buildHash();
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

    /** Bundles join hash computation parameters. */
    public record JoinHashRequest(
            LambdaExpression joinRelationshipExpression,
            LambdaExpression biEntityPredicateExpression,
            LambdaExpression biEntityProjectionExpression,
            List<SortExpression> sortExpressions,
            String joinType,
            boolean isCountQuery,
            boolean isSelectJoined,
            boolean isJoinProjection) {
    }

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

    /** Returns true if lambda is duplicate and reuses existing executor. */
    public boolean handleDuplicateLambda(DeduplicationRequest request, DeduplicationContext context) {
        // Use atomic get instead of containsKey+get to avoid race conditions
        String existingExecutor = lambdaHashToExecutor.get(request.lambdaHash());
        if (existingExecutor != null) {

            if (context.logDeduplication()) {
                Log.debugf("Deduplicated lambda at %s (reusing %s)", request.callSiteId(), existingExecutor);
            }

            context.deduplicatedCount().incrementAndGet();

            context.queryTransformations().produce(
                    QubitProcessor.QueryTransformationBuildItem.builder()
                            .queryId(request.callSiteId())
                            .generatedClassName(existingExecutor)
                            .entityClassName(request.entityClassName())
                            .characteristics(request.characteristics())
                            .capturedVarCount(request.capturedVarCount())
                            .predicateExpression(request.predicateExpression())
                            .projectionExpression(request.projectionExpression())
                            .sortExpression(request.sortExpression())
                            .terminalMethodName(request.terminalMethodName())
                            .sortDescending(request.sortDescending())
                            .build());
            return true;
        }

        return false;
    }
}
