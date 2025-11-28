package io.quarkus.qusaq.deployment.analysis;

import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.builditem.ApplicationArchivesBuildItem;
import io.quarkus.deployment.builditem.GeneratedClassBuildItem;
import io.quarkus.qusaq.deployment.InvokeDynamicScanner;
import io.quarkus.qusaq.deployment.InvokeDynamicScanner.SortLambda;
import io.quarkus.qusaq.deployment.LambdaExpression;
import io.quarkus.qusaq.deployment.QusaqProcessor;
import io.quarkus.qusaq.deployment.generation.QueryExecutorClassGenerator;
import io.quarkus.qusaq.deployment.util.BytecodeLoader;
import io.quarkus.qusaq.runtime.SortDirection;
import org.jboss.logging.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Processes lambda call sites: analyzes bytecode, deduplicates, generates executors.
 */
public class CallSiteProcessor {

    private static final Logger log = Logger.getLogger(CallSiteProcessor.class);

    /**
     * Result of lambda bytecode analysis - sealed interface with specialized result types.
     * <p>
     * Refactored from a single 15-field record to a sealed interface hierarchy (ARCH-002).
     * Each query type now has its own result record with only the relevant fields:
     * <ul>
     *   <li>{@link SimpleQueryResult}: where, select, combined, sorting-only queries</li>
     *   <li>{@link AggregationQueryResult}: min, max, avg, sum* queries</li>
     *   <li>{@link JoinQueryResult}: join, leftJoin with BiQuerySpec</li>
     *   <li>{@link GroupQueryResult}: groupBy with GroupQuerySpec</li>
     * </ul>
     */
    private sealed interface LambdaAnalysisResult {
        /** Total number of captured variables across all expressions in this result. */
        int totalCapturedVarCount();

        /**
         * Simple queries: where, select, combined, sorting-only.
         * Phase 1-3: Basic filtering, projection, and sorting.
         */
        record SimpleQueryResult(
                LambdaExpression predicateExpression,
                LambdaExpression projectionExpression,
                List<SortExpression> sortExpressions,
                int totalCapturedVarCount
        ) implements LambdaAnalysisResult {}

        /**
         * Aggregation queries: min, max, avg, sum*.
         * Phase 5: Aggregation terminals with optional WHERE predicates.
         */
        record AggregationQueryResult(
                LambdaExpression predicateExpression,
                LambdaExpression aggregationExpression,
                String aggregationType,  // "MIN", "MAX", "AVG", "SUM_INTEGER", "SUM_LONG", "SUM_DOUBLE"
                int totalCapturedVarCount
        ) implements LambdaAnalysisResult {}

        /**
         * Join queries: join, leftJoin with BiQuerySpec.
         * Iteration 6: Join relationship, bi-entity predicates/projections.
         */
        record JoinQueryResult(
                LambdaExpression joinRelationshipExpression,
                LambdaExpression biEntityPredicateExpression,
                LambdaExpression biEntityProjectionExpression,
                List<SortExpression> sortExpressions,
                InvokeDynamicScanner.JoinType joinType,
                int totalCapturedVarCount
        ) implements LambdaAnalysisResult {}

        /**
         * Group queries: groupBy with GroupQuerySpec.
         * Iteration 7: GROUP BY with having, select, and sort in group context.
         */
        record GroupQueryResult(
                LambdaExpression predicateExpression,  // Pre-grouping WHERE clause
                LambdaExpression groupByKeyExpression,
                LambdaExpression havingExpression,
                LambdaExpression groupSelectExpression,
                List<SortExpression> groupSortExpressions,
                int totalCapturedVarCount
        ) implements LambdaAnalysisResult {}
    }

    /**
     * Sort expression with direction (ascending/descending).
     * Phase 3: Represents analyzed sort key extractor lambda with direction.
     * Public to allow access from QueryExecutorClassGenerator.
     */
    public record SortExpression(
            LambdaExpression keyExtractor,
            SortDirection direction) {}

    private final LambdaBytecodeAnalyzer bytecodeAnalyzer;
    private final LambdaDeduplicator deduplicator;
    private final QueryExecutorClassGenerator classGenerator;
    private final AtomicInteger queryCounter;

    public CallSiteProcessor(
            LambdaBytecodeAnalyzer bytecodeAnalyzer,
            LambdaDeduplicator deduplicator,
            QueryExecutorClassGenerator classGenerator,
            AtomicInteger queryCounter) {
        this.bytecodeAnalyzer = bytecodeAnalyzer;
        this.deduplicator = deduplicator;
        this.classGenerator = classGenerator;
        this.queryCounter = queryCounter;
    }

    /**
     * Analyzes lambda at call site and generates query executor class.
     * <p>
     * Phase 2.2: Supports combined where() + select() queries with both predicate and projection.
     * Phase 2.5: Supports multiple where() predicates combined with AND.
     */
    public void processCallSite(
            InvokeDynamicScanner.LambdaCallSite callSite,
            ApplicationArchivesBuildItem applicationArchives,
            AtomicInteger generatedCount,
            AtomicInteger deduplicatedCount,
            BuildProducer<GeneratedClassBuildItem> generatedClass,
            BuildProducer<QusaqProcessor.QueryTransformationBuildItem> queryTransformations) {
        try {
            byte[] classBytes = BytecodeLoader.loadClassBytecode(callSite.ownerClassName(), applicationArchives);
            if (classBytes == null) {
                log.warnf("Could not load bytecode for class: %s", callSite.ownerClassName());
                return;
            }

            String callSiteId = callSite.getCallSiteId();
            LambdaAnalysisResult result = analyzeLambdas(classBytes, callSite, callSiteId);
            if (result == null) {
                return;
            }

            if (result.totalCapturedVarCount() > 0) {
                log.debugf("Lambda(s) at %s contain %d captured variable(s)",
                          callSiteId, result.totalCapturedVarCount());
            }

            String lambdaHash = computeHash(callSite, result);
            boolean isGroupQuery = result instanceof LambdaAnalysisResult.GroupQueryResult;
            if (deduplicator.handleDuplicateLambda(callSiteId, lambdaHash,
                    callSite.isCountQuery(),
                    callSite.isAggregationQuery(),
                    callSite.isJoinQuery(),
                    callSite.isSelectJoinedQuery(),  // Iteration 6.5: Pass selectJoined flag
                    callSite.isJoinProjectionQuery(),  // Iteration 6.6: Pass joinProjection flag
                    isGroupQuery,
                    result.totalCapturedVarCount(), deduplicatedCount, queryTransformations)) {
                return;
            }

            // ARCH-002: Pattern matching switch with sealed interface (Java 21)
            // Compiler guarantees exhaustiveness - no default case needed
            String executorClassName = switch (result) {
                case LambdaAnalysisResult.GroupQueryResult group -> generateAndRegisterGroupExecutor(
                        group.predicateExpression(),
                        group.groupByKeyExpression(),
                        group.havingExpression(),
                        group.groupSelectExpression(),
                        group.groupSortExpressions(),
                        callSiteId,
                        group.totalCapturedVarCount(),
                        callSite.isCountQuery(),
                        generatedClass,
                        queryTransformations);

                case LambdaAnalysisResult.JoinQueryResult join -> generateAndRegisterJoinExecutor(
                        join.joinRelationshipExpression(),
                        join.biEntityPredicateExpression(),
                        join.biEntityProjectionExpression(),
                        join.sortExpressions(),
                        join.joinType(),
                        callSiteId,
                        join.totalCapturedVarCount(),
                        callSite.isCountQuery(),
                        callSite.isSelectJoinedQuery(),
                        callSite.isJoinProjectionQuery(),
                        generatedClass,
                        queryTransformations);

                case LambdaAnalysisResult.AggregationQueryResult agg -> generateAndRegisterExecutor(
                        agg.predicateExpression(),
                        null,  // No projection for aggregations
                        Collections.emptyList(),  // No sorting for aggregations
                        agg.aggregationExpression(),
                        agg.aggregationType(),
                        callSite.isCountQuery(),
                        true,  // isAggregationQuery
                        callSiteId,
                        generatedClass,
                        queryTransformations);

                case LambdaAnalysisResult.SimpleQueryResult simple -> generateAndRegisterExecutor(
                        simple.predicateExpression(),
                        simple.projectionExpression(),
                        simple.sortExpressions(),
                        null,  // No aggregation
                        null,  // No aggregation type
                        callSite.isCountQuery(),
                        false,  // Not an aggregation query
                        callSiteId,
                        generatedClass,
                        queryTransformations);
            };

            deduplicator.registerExecutor(lambdaHash, executorClassName);
            generatedCount.incrementAndGet();

            log.debugf("Generated executor: %s for call site %s (hash: %s)",
                    executorClassName, callSiteId, lambdaHash.substring(0, 8));

        } catch (Exception e) {
            log.errorf(e, "Failed to process call site: %s", callSite);
        }
    }

    /**
     * Analyzes lambdas based on call site type (multiple predicates, combined, aggregation, join, group, or single).
     * Phase 5: Added aggregation query support.
     * Iteration 6: Added join query support.
     * Iteration 7: Added group query support.
     */
    private LambdaAnalysisResult analyzeLambdas(
            byte[] classBytes,
            InvokeDynamicScanner.LambdaCallSite callSite,
            String callSiteId) {

        // Phase 5: Handle aggregation queries first
        if (callSite.isAggregationQuery()) {
            return analyzeAggregationQuery(classBytes, callSite, callSiteId);
        }

        // Iteration 6: Handle join queries
        if (callSite.isJoinQuery()) {
            return analyzeJoinQuery(classBytes, callSite, callSiteId);
        }

        // Iteration 7: Handle group queries
        if (callSite.isGroupByQuery()) {
            return analyzeGroupQuery(classBytes, callSite, callSiteId);
        }

        var predicateLambdas = callSite.predicateLambdas();
        boolean hasMultiplePredicates = predicateLambdas != null && predicateLambdas.size() > 1;

        if (hasMultiplePredicates) {
            return analyzeMultiplePredicates(classBytes, callSite, callSiteId);
        } else if (callSite.isCombinedQuery()) {
            return analyzeCombinedQuery(classBytes, callSite, callSiteId);
        } else {
            return analyzeSingleLambda(classBytes, callSite, callSiteId, callSite.isProjectionQuery());
        }
    }

    /**
     * Computes deduplication hash based on query type.
     * Phase 3: Handles sorting-only queries and includes sort expressions in hash.
     * Phase 5: Handles aggregation queries with optional WHERE predicates.
     * Iteration 6: Handles join queries with optional bi-entity predicates.
     * Iteration 7: Handles group queries with groupBy, having, select, and sort.
     * <p>
     * Refactored for ARCH-002: Uses pattern matching with sealed interface types.
     */
    private String computeHash(InvokeDynamicScanner.LambdaCallSite callSite, LambdaAnalysisResult result) {
        // ARCH-002: Pattern matching switch with sealed interface (Java 21)
        return switch (result) {
            case LambdaAnalysisResult.GroupQueryResult group -> deduplicator.computeGroupHash(
                    group.predicateExpression(),
                    group.groupByKeyExpression(),
                    group.havingExpression(),
                    group.groupSelectExpression(),
                    group.groupSortExpressions(),
                    callSite.isCountQuery());

            case LambdaAnalysisResult.JoinQueryResult join -> {
                String joinTypeStr = join.joinType().name();  // INNER or LEFT
                yield deduplicator.computeJoinHash(
                        join.joinRelationshipExpression(),
                        join.biEntityPredicateExpression(),
                        join.biEntityProjectionExpression(),
                        join.sortExpressions(),
                        joinTypeStr,
                        callSite.isCountQuery(),
                        callSite.isSelectJoinedQuery(),
                        callSite.isJoinProjectionQuery());
            }

            case LambdaAnalysisResult.AggregationQueryResult agg -> deduplicator.computeAggregationHash(
                    agg.predicateExpression(),
                    agg.aggregationExpression(),
                    agg.aggregationType());

            case LambdaAnalysisResult.SimpleQueryResult simple -> computeSimpleQueryHash(callSite, simple);
        };
    }

    /**
     * Computes hash for simple queries (WHERE, SELECT, combined, sorting-only).
     */
    private String computeSimpleQueryHash(InvokeDynamicScanner.LambdaCallSite callSite,
                                          LambdaAnalysisResult.SimpleQueryResult simple) {
        boolean hasSorting = simple.sortExpressions() != null && !simple.sortExpressions().isEmpty();

        if (callSite.isCombinedQuery()) {
            if (hasSorting) {
                return deduplicator.computeFullQueryHash(
                        simple.predicateExpression(),
                        simple.projectionExpression(),
                        simple.sortExpressions(),
                        callSite.isCountQuery());
            }
            return deduplicator.computeCombinedHash(
                    simple.predicateExpression(),
                    simple.projectionExpression(),
                    callSite.isCountQuery());
        }

        // Phase 3: For sorting-only queries, compute hash from sort expressions
        if (simple.predicateExpression() == null && simple.projectionExpression() == null && hasSorting) {
            return deduplicator.computeSortingHash(simple.sortExpressions());
        }

        // Regular WHERE or SELECT query (possibly with sorting)
        LambdaExpression expr = simple.predicateExpression() != null
                ? simple.predicateExpression()
                : simple.projectionExpression();

        if (hasSorting) {
            return deduplicator.computeQueryWithSortingHash(expr, simple.sortExpressions(),
                    callSite.isCountQuery(), callSite.isProjectionQuery());
        }

        return deduplicator.computeLambdaHash(expr, callSite.isCountQuery(), callSite.isProjectionQuery());
    }

    /**
     * Generates executor class and registers it.
     * Phase 2.2: Accepts both predicate and projection expressions.
     * Phase 3: Accepts sort expressions for ORDER BY generation.
     * Phase 5: Accepts aggregation expression and type for aggregation queries.
     */
    private String generateAndRegisterExecutor(
            LambdaExpression predicateExpression,
            LambdaExpression projectionExpression,
            List<SortExpression> sortExpressions,
            LambdaExpression aggregationExpression,
            String aggregationType,
            boolean isCountQuery,
            boolean isAggregationQuery,
            String queryId,
            BuildProducer<GeneratedClassBuildItem> generatedClass,
            BuildProducer<QusaqProcessor.QueryTransformationBuildItem> queryTransformations) {

        // Count captured variables from all expressions
        int capturedVarCount = 0;
        if (predicateExpression != null) {
            capturedVarCount += countCapturedVariables(predicateExpression);
        }
        if (projectionExpression != null) {
            capturedVarCount += countCapturedVariables(projectionExpression);
        }
        capturedVarCount += countCapturedVariablesInSortExpressions(sortExpressions);
        if (aggregationExpression != null) {
            capturedVarCount += countCapturedVariables(aggregationExpression);
        }

        String className = "io.quarkus.qusaq.generated.QueryExecutor_" +
                           queryCounter.getAndIncrement();

        byte[] bytecode = classGenerator.generateQueryExecutorClass(
                predicateExpression,
                projectionExpression,
                sortExpressions,
                aggregationExpression,
                aggregationType,
                className,
                isCountQuery,
                isAggregationQuery);

        generatedClass.produce(new GeneratedClassBuildItem(true, className, bytecode));
        queryTransformations.produce(
                new QusaqProcessor.QueryTransformationBuildItem(queryId, className, Object.class, isCountQuery, isAggregationQuery, capturedVarCount));

        String queryTypeDesc = getQueryTypeDescription(predicateExpression, projectionExpression, sortExpressions,
                                                       aggregationExpression, aggregationType, isCountQuery, isAggregationQuery);

        log.debugf("Generated query executor: %s (%s, %d captured vars)",
                   className, queryTypeDesc, capturedVarCount);

        return className;
    }

    /**
     * Generates and registers a JOIN query executor class (Iteration 6).
     * <p>
     * Creates a query executor that performs a JPA join between two related entities.
     *
     * @param joinRelationshipExpression Lambda for the join relationship (e.g., p -> p.phones)
     * @param biEntityPredicateExpression Lambda for bi-entity predicate (e.g., (p, ph) -> ph.type.equals("mobile"))
     * @param biEntityProjectionExpression Iteration 6.6: BiQuerySpec SELECT projection (e.g., (p, ph) -> new DTO(...))
     * @param sortExpressions Iteration 6.5: List of sort expressions for ORDER BY
     * @param joinType The type of join (INNER or LEFT)
     * @param queryId Unique identifier for the query
     * @param capturedVarCount Number of captured variables
     * @param isCountQuery True if this is a count query (JoinStream.count())
     * @param isSelectJoined Iteration 6.5: True if selectJoined() was called (returns joined entities)
     * @param isJoinProjection Iteration 6.6: True if select() with BiQuerySpec was called
     * @param generatedClass Build producer for generated classes
     * @param queryTransformations Build producer for query transformations
     * @return The generated class name
     */
    private String generateAndRegisterJoinExecutor(
            LambdaExpression joinRelationshipExpression,
            LambdaExpression biEntityPredicateExpression,
            LambdaExpression biEntityProjectionExpression,
            List<SortExpression> sortExpressions,
            InvokeDynamicScanner.JoinType joinType,
            String queryId,
            int capturedVarCount,
            boolean isCountQuery,
            boolean isSelectJoined,
            boolean isJoinProjection,
            BuildProducer<GeneratedClassBuildItem> generatedClass,
            BuildProducer<QusaqProcessor.QueryTransformationBuildItem> queryTransformations) {

        String className = "io.quarkus.qusaq.generated.QueryExecutor_" +
                           queryCounter.getAndIncrement();

        byte[] bytecode = classGenerator.generateJoinQueryExecutorClass(
                joinRelationshipExpression,
                biEntityPredicateExpression,
                biEntityProjectionExpression,
                sortExpressions,
                joinType,
                className,
                isCountQuery,
                isSelectJoined,
                isJoinProjection);

        generatedClass.produce(new GeneratedClassBuildItem(true, className, bytecode));
        // Iteration 6: Create join query build item with isJoinQuery=true
        // Iteration 6.5: Create selectJoined query build item if isSelectJoined is true
        // Iteration 6.6: Create join projection query build item if isJoinProjection is true
        queryTransformations.produce(
                new QusaqProcessor.QueryTransformationBuildItem(queryId, className, Object.class, isCountQuery, false, true, isSelectJoined, isJoinProjection, capturedVarCount));

        String joinTypeDesc = (joinType == InvokeDynamicScanner.JoinType.LEFT) ? "LEFT JOIN" : "INNER JOIN";
        String queryTypeDesc = isCountQuery ? joinTypeDesc + " COUNT" :
                (isJoinProjection ? joinTypeDesc + " PROJECTION" :
                (isSelectJoined ? joinTypeDesc + " SELECT JOINED" : joinTypeDesc));
        log.debugf("Generated join query executor: %s (%s, %d captured vars)",
                   className, queryTypeDesc, capturedVarCount);

        return className;
    }

    /**
     * Generates and registers a GROUP BY query executor class (Iteration 7).
     * <p>
     * Creates a query executor that performs JPA GROUP BY operations with optional
     * HAVING clause, aggregations, and sorting.
     *
     * @param predicateExpression Pre-grouping WHERE clause (null if no filtering)
     * @param groupByKeyExpression groupBy() key extractor lambda (e.g., p -> p.department)
     * @param havingExpression having() predicate (null if no having)
     * @param groupSelectExpression select() projection in group context (null if no select)
     * @param groupSortExpressions sortedBy() in group context (null or empty if no sorting)
     * @param queryId Unique identifier for the query
     * @param capturedVarCount Number of captured variables
     * @param isCountQuery True if this is a count query (GroupStream.count())
     * @param generatedClass Build producer for generated classes
     * @param queryTransformations Build producer for query transformations
     * @return The generated class name
     */
    private String generateAndRegisterGroupExecutor(
            LambdaExpression predicateExpression,
            LambdaExpression groupByKeyExpression,
            LambdaExpression havingExpression,
            LambdaExpression groupSelectExpression,
            List<SortExpression> groupSortExpressions,
            String queryId,
            int capturedVarCount,
            boolean isCountQuery,
            BuildProducer<GeneratedClassBuildItem> generatedClass,
            BuildProducer<QusaqProcessor.QueryTransformationBuildItem> queryTransformations) {

        String className = "io.quarkus.qusaq.generated.QueryExecutor_" +
                           queryCounter.getAndIncrement();

        byte[] bytecode = classGenerator.generateGroupQueryExecutorClass(
                predicateExpression,
                groupByKeyExpression,
                havingExpression,
                groupSelectExpression,
                groupSortExpressions,
                className,
                isCountQuery);

        generatedClass.produce(new GeneratedClassBuildItem(true, className, bytecode));
        // Iteration 7: Create group query build item with isGroupQuery=true
        // Iteration 6.6: Updated to include isJoinProjection=false parameter before isGroupQuery
        queryTransformations.produce(
                new QusaqProcessor.QueryTransformationBuildItem(queryId, className, Object.class, isCountQuery, false, false, false, false, true, capturedVarCount));

        String queryTypeDesc = isCountQuery ? "GROUP BY COUNT" : "GROUP BY";
        if (havingExpression != null) {
            queryTypeDesc += "+HAVING";
        }
        if (groupSelectExpression != null) {
            queryTypeDesc += "+SELECT";
        }
        log.debugf("Generated group query executor: %s (%s, %d captured vars)",
                   className, queryTypeDesc, capturedVarCount);

        return className;
    }

    /**
     * Determines query type identifier based on query characteristics.
     * This is the canonical method for query type determination used across the codebase.
     *
     * @param isCountQuery true if this is a count/exists query
     * @param hasPredicate true if query has WHERE clause
     * @param hasProjection true if query has SELECT clause
     * @return query type: "COUNT", "COMBINED", "PROJECTION", or "LIST"
     */
    static String getQueryType(boolean isCountQuery, boolean hasPredicate, boolean hasProjection) {
        if (isCountQuery) {
            return "COUNT";
        }
        if (hasPredicate && hasProjection) {
            return "COMBINED";
        }
        if (hasProjection) {
            return "PROJECTION";
        }
        return "LIST";
    }

    /**
     * Determines the query type description for logging based on expressions and flags.
     * Adds decorative formatting to the base query type for better log readability.
     * Phase 3: Enhanced to show sorting in query type description.
     * Phase 5: Enhanced to show aggregation type in query description.
     */
    private String getQueryTypeDescription(
            LambdaExpression predicateExpression,
            LambdaExpression projectionExpression,
            List<SortExpression> sortExpressions,
            LambdaExpression aggregationExpression,
            String aggregationType,
            boolean isCountQuery,
            boolean isAggregationQuery) {

        // Phase 5: Aggregation queries have priority in description
        if (isAggregationQuery && aggregationType != null) {
            String typeDesc = aggregationType;  // "MIN", "MAX", "AVG", etc.
            if (predicateExpression != null) {
                typeDesc += "+WHERE";
            }
            return typeDesc;
        }

        String baseType = getQueryType(
                isCountQuery,
                predicateExpression != null,
                projectionExpression != null);

        // Add decorative formatting for combined queries in logs
        String typeDesc = "COMBINED".equals(baseType) ? "COMBINED(WHERE+SELECT)" : baseType;

        // Phase 3: Append sorting info if present
        if (sortExpressions != null && !sortExpressions.isEmpty()) {
            typeDesc += "+SORT(" + sortExpressions.size() + ")";
        }

        return typeDesc;
    }

    /**
     * Analyzes and combines multiple predicate lambdas with AND operation.
     * Handles captured variable renumbering to ensure sequential indices.
     *
     * @return pair of (combined expression, total captured variable count), or null if analysis fails
     */
    private record PredicateAnalysisResult(LambdaExpression expression, int capturedVarCount) {}

    private PredicateAnalysisResult analyzeAndCombinePredicates(
            byte[] classBytes,
            List<InvokeDynamicScanner.LambdaPair> predicateLambdas,
            String callSiteId) {

        List<LambdaExpression> predicateExpressions = new ArrayList<>();
        int indexOffset = 0;

        for (var lambdaPair : predicateLambdas) {
            LambdaExpression expr = bytecodeAnalyzer.analyze(
                    classBytes,
                    lambdaPair.methodName(),
                    lambdaPair.descriptor());

            if (expr == null) {
                log.warnf("Could not analyze predicate lambda %s at: %s", lambdaPair.methodName(), callSiteId);
                return null;
            }

            // Renumber this predicate's CapturedVariable indices by the offset
            // to ensure sequential indices across all predicates
            int capturedCount = countCapturedVariables(expr);
            LambdaExpression renumberedExpr = renumberCapturedVariables(expr, indexOffset);

            predicateExpressions.add(renumberedExpr);
            indexOffset += capturedCount;
        }

        int totalCapturedVarCount = indexOffset; // Total from all predicates
        LambdaExpression combinedExpression = combinePredicatesWithAnd(predicateExpressions);

        log.debugf("Combined %d predicates with AND at %s (total %d captured variables)",
                Integer.valueOf(predicateExpressions.size()), callSiteId, Integer.valueOf(totalCapturedVarCount));

        return new PredicateAnalysisResult(combinedExpression, totalCapturedVarCount);
    }

    /**
     * Analyzes multiple where() predicates and combines them with AND.
     * Phase 2.5: Handles multiple where() chaining.
     *
     * IMPORTANT: Renumbers CapturedVariable indices before combining to ensure correct ordering.
     * Each lambda has indices starting from 0. When combined, indices must be sequential:
     * - Lambda 1: CapturedVariable(0) stays as index 0
     * - Lambda 2: CapturedVariable(0) becomes index 1 (offset by lambda 1 count)
     * - Lambda 3: CapturedVariable(0) becomes index 2 (offset by lambda 1+2 count)
     */
    private LambdaAnalysisResult analyzeMultiplePredicates(
            byte[] classBytes,
            InvokeDynamicScanner.LambdaCallSite callSite,
            String callSiteId) {

        var predicateLambdas = callSite.predicateLambdas();
        PredicateAnalysisResult predicateResult = analyzeAndCombinePredicates(classBytes, predicateLambdas, callSiteId);
        if (predicateResult == null) {
            return null;
        }

        LambdaExpression predicateExpression = predicateResult.expression;
        int totalCapturedVarCount = predicateResult.capturedVarCount;

        // Check if there's also a select
        LambdaExpression projectionExpression = null;
        if (callSite.projectionLambdaMethodName() != null) {
            projectionExpression = bytecodeAnalyzer.analyze(
                    classBytes,
                    callSite.projectionLambdaMethodName(),
                    callSite.projectionLambdaMethodDescriptor());

            if (projectionExpression == null) {
                log.warnf("Could not analyze projection lambda at: %s", callSiteId);
                return null;
            }
            totalCapturedVarCount += countCapturedVariables(projectionExpression);
        }

        // Phase 3: Analyze sort lambdas if present
        List<SortExpression> sortExpressions = analyzeSortLambdas(classBytes, callSite, callSiteId);
        totalCapturedVarCount += countCapturedVariablesInSortExpressions(sortExpressions);

        return new LambdaAnalysisResult.SimpleQueryResult(
                predicateExpression, projectionExpression, sortExpressions, totalCapturedVarCount);
    }

    /**
     * Analyzes combined where() + select() query.
     * Phase 2.2: Handles combined queries.
     * Updated: Uses predicateLambdas list for consistency with multiple where() support.
     */
    private LambdaAnalysisResult analyzeCombinedQuery(
            byte[] classBytes,
            InvokeDynamicScanner.LambdaCallSite callSite,
            String callSiteId) {

        // Analyze predicate lambda(s) (WHERE clause)
        // For combined queries, use the first predicate (or combine if multiple)
        var predicateLambdas = callSite.predicateLambdas();
        if (predicateLambdas == null || predicateLambdas.isEmpty()) {
            log.warnf("Combined query without predicates at: %s", callSiteId);
            return null;
        }

        PredicateAnalysisResult predicateResult = analyzeAndCombinePredicates(classBytes, predicateLambdas, callSiteId);
        if (predicateResult == null) {
            return null;
        }

        LambdaExpression predicateExpression = predicateResult.expression;
        int totalCapturedVarCount = predicateResult.capturedVarCount;

        // Analyze projection lambda (SELECT clause)
        LambdaExpression projectionExpression = bytecodeAnalyzer.analyze(
                classBytes,
                callSite.projectionLambdaMethodName(),
                callSite.projectionLambdaMethodDescriptor());

        if (projectionExpression == null) {
            log.warnf("Could not analyze projection lambda at: %s", callSiteId);
            return null;
        }

        log.debugf("Analyzed combined query at %s: WHERE=%s, SELECT=%s",
                callSiteId, predicateExpression, projectionExpression);

        totalCapturedVarCount += countCapturedVariables(projectionExpression);

        // Phase 3: Analyze sort lambdas if present
        List<SortExpression> sortExpressions = analyzeSortLambdas(classBytes, callSite, callSiteId);
        totalCapturedVarCount += countCapturedVariablesInSortExpressions(sortExpressions);

        return new LambdaAnalysisResult.SimpleQueryResult(
                predicateExpression, projectionExpression, sortExpressions, totalCapturedVarCount);
    }

    /**
     * Analyzes single lambda (where-only, select-only, or sorting-only).
     * Phase 2.1 or Phase 1: Single lambda support.
     * Phase 3: Handles sorting-only queries.
     */
    private LambdaAnalysisResult analyzeSingleLambda(
            byte[] classBytes,
            InvokeDynamicScanner.LambdaCallSite callSite,
            String callSiteId,
            boolean isProjectionQuery) {

        // Phase 3: Check if this is a sorting-only query
        List<SortExpression> sortExpressions = analyzeSortLambdas(classBytes, callSite, callSiteId);
        boolean hasSortLambdas = sortExpressions != null && !sortExpressions.isEmpty();

        // Check if there are predicate or projection lambdas
        boolean hasPredicates = callSite.predicateLambdas() != null && !callSite.predicateLambdas().isEmpty();
        boolean hasProjection = callSite.projectionLambdaMethodName() != null;

        // Phase 3: For sorting-only queries, don't analyze the primary lambda as predicate/projection
        if (hasSortLambdas && !hasPredicates && !hasProjection) {
            // Sorting-only query - no WHERE or SELECT clauses
            int totalCapturedVarCount = countCapturedVariablesInSortExpressions(sortExpressions);
            log.debugf("Analyzed sorting-only query at %s: %d sort expression(s)", callSiteId, sortExpressions.size());
            return new LambdaAnalysisResult.SimpleQueryResult(
                    null, null, sortExpressions, totalCapturedVarCount);
        }

        // Regular WHERE or SELECT query
        LambdaExpression lambdaExpression = bytecodeAnalyzer.analyze(
                classBytes,
                callSite.lambdaMethodName(),
                callSite.lambdaMethodDescriptor());

        if (lambdaExpression == null) {
            log.warnf("Could not analyze lambda expression at: %s", callSiteId);
            return null;
        }

        log.debugf("Analyzed lambda at %s: %s", callSiteId, lambdaExpression);

        // Assign to appropriate expression based on query type
        LambdaExpression predicateExpression = isProjectionQuery ? null : lambdaExpression;
        LambdaExpression projectionExpression = isProjectionQuery ? lambdaExpression : null;
        int totalCapturedVarCount = countCapturedVariables(lambdaExpression);

        // Phase 3: Add captured variables from sort expressions
        totalCapturedVarCount += countCapturedVariablesInSortExpressions(sortExpressions);

        return new LambdaAnalysisResult.SimpleQueryResult(
                predicateExpression, projectionExpression, sortExpressions, totalCapturedVarCount);
    }

    /**
     * Analyzes aggregation query (min, max, avg, sum*).
     * Phase 5: Handles aggregation terminals with optional WHERE predicates.
     *
     * Example: Person.where(p -> p.active).min(p -> p.salary)
     * - Predicate: p.active
     * - Aggregation mapper: p.salary
     * - Aggregation type: MIN
     */
    private LambdaAnalysisResult analyzeAggregationQuery(
            byte[] classBytes,
            InvokeDynamicScanner.LambdaCallSite callSite,
            String callSiteId) {

        // Analyze aggregation mapper lambda (e.g., p -> p.salary)
        if (callSite.aggregationLambdaMethodName() == null) {
            log.warnf("Aggregation query missing mapper lambda at: %s", callSiteId);
            return null;
        }

        LambdaExpression aggregationExpression = bytecodeAnalyzer.analyze(
                classBytes,
                callSite.aggregationLambdaMethodName(),
                callSite.aggregationLambdaMethodDescriptor());

        if (aggregationExpression == null) {
            log.warnf("Could not analyze aggregation mapper lambda at: %s", callSiteId);
            return null;
        }

        int totalCapturedVarCount = countCapturedVariables(aggregationExpression);

        // Analyze predicate lambda(s) if present (WHERE clause)
        LambdaExpression predicateExpression = null;
        var predicateLambdas = callSite.predicateLambdas();
        if (predicateLambdas != null && !predicateLambdas.isEmpty()) {
            PredicateAnalysisResult predicateResult = analyzeAndCombinePredicates(
                    classBytes, predicateLambdas, callSiteId);
            if (predicateResult == null) {
                return null;
            }
            predicateExpression = predicateResult.expression;
            totalCapturedVarCount += predicateResult.capturedVarCount;
        }

        // Determine aggregation type from terminal method name
        // Convert camelCase method names to UPPER_SNAKE_CASE constants
        String methodName = callSite.targetMethodName();
        String aggregationType = switch (methodName) {
            case "min" -> "MIN";
            case "max" -> "MAX";
            case "avg" -> "AVG";
            case "sumInteger" -> "SUM_INTEGER";
            case "sumLong" -> "SUM_LONG";
            case "sumDouble" -> "SUM_DOUBLE";
            default -> methodName.toUpperCase(); // Fallback
        };

        log.debugf("Analyzed aggregation query at %s: type=%s, mapper=%s, predicate=%s",
                callSiteId, aggregationType, aggregationExpression, predicateExpression);

        return new LambdaAnalysisResult.AggregationQueryResult(
                predicateExpression, aggregationExpression, aggregationType, totalCapturedVarCount);
    }

    /**
     * Analyzes join query (join/leftJoin with BiQuerySpec lambdas).
     * Iteration 6: Handles join queries with bi-entity predicates.
     *
     * Example: Person.join(p -> p.phones).where((p, ph) -> ph.type.equals("mobile"))
     * - Join relationship: p.phones
     * - Bi-entity predicate: ph.type.equals("mobile")
     * - Join type: INNER
     */
    private LambdaAnalysisResult analyzeJoinQuery(
            byte[] classBytes,
            InvokeDynamicScanner.LambdaCallSite callSite,
            String callSiteId) {

        int totalCapturedVarCount = 0;

        // Analyze join relationship lambda (e.g., p -> p.phones)
        LambdaExpression joinRelationshipExpression = null;
        if (callSite.joinRelationshipLambdaMethodName() != null) {
            joinRelationshipExpression = bytecodeAnalyzer.analyze(
                    classBytes,
                    callSite.joinRelationshipLambdaMethodName(),
                    callSite.joinRelationshipLambdaDescriptor());

            if (joinRelationshipExpression == null) {
                log.warnf("Could not analyze join relationship lambda at: %s", callSiteId);
                return null;
            }
            totalCapturedVarCount += countCapturedVariables(joinRelationshipExpression);
        }

        // Analyze bi-entity predicate lambdas if present (e.g., (p, ph) -> ph.type.equals("mobile"))
        LambdaExpression biEntityPredicateExpression = null;
        var biEntityPredicateLambdas = callSite.biEntityPredicateLambdas();
        if (biEntityPredicateLambdas != null && !biEntityPredicateLambdas.isEmpty()) {
            // Analyze each bi-entity predicate and combine with AND
            List<LambdaExpression> biPredicates = new java.util.ArrayList<>();
            for (var lambdaPair : biEntityPredicateLambdas) {
                LambdaExpression expr = bytecodeAnalyzer.analyzeBiEntity(
                        classBytes,
                        lambdaPair.methodName(),
                        lambdaPair.descriptor());

                if (expr == null) {
                    log.warnf("Could not analyze bi-entity predicate lambda %s at: %s",
                            lambdaPair.methodName(), callSiteId);
                    return null;
                }
                biPredicates.add(expr);
                totalCapturedVarCount += countCapturedVariables(expr);
            }

            // Combine multiple bi-entity predicates with AND
            biEntityPredicateExpression = combinePredicatesWithAnd(biPredicates);
        }

        // Iteration 6.5: Analyze bi-entity sort lambdas for join queries
        List<SortExpression> sortExpressions = analyzeBiEntitySortLambdas(classBytes, callSite, callSiteId);
        totalCapturedVarCount += countCapturedVariablesInSortExpressions(sortExpressions);

        // Iteration 6.6: Analyze bi-entity projection lambda if present (e.g., (p, ph) -> new PersonPhoneDTO(p.firstName, ph.number))
        LambdaExpression biEntityProjectionExpression = null;
        if (callSite.biEntityProjectionLambdaMethodName() != null) {
            biEntityProjectionExpression = bytecodeAnalyzer.analyzeBiEntity(
                    classBytes,
                    callSite.biEntityProjectionLambdaMethodName(),
                    callSite.biEntityProjectionLambdaDescriptor());

            if (biEntityProjectionExpression == null) {
                log.warnf("Could not analyze bi-entity projection lambda at: %s", callSiteId);
                return null;
            }
            totalCapturedVarCount += countCapturedVariables(biEntityProjectionExpression);
        }

        log.debugf("Analyzed join query at %s: type=%s, relationship=%s, biPredicate=%s, biProjection=%s, sortExpressions=%d",
                callSiteId, callSite.joinType(), joinRelationshipExpression, biEntityPredicateExpression,
                biEntityProjectionExpression, sortExpressions.size());

        return new LambdaAnalysisResult.JoinQueryResult(
                joinRelationshipExpression,
                biEntityPredicateExpression,
                biEntityProjectionExpression,
                sortExpressions,
                callSite.joinType(),
                totalCapturedVarCount);
    }

    /**
     * Analyzes group query (groupBy with GroupQuerySpec lambdas).
     * Iteration 7: Handles groupBy queries with having, select, and sort in group context.
     *
     * Example: Person.groupBy(p -> p.department).select(g -> new DeptStats(g.key(), g.count()))
     * - Group key: p.department
     * - Group select: new DeptStats(g.key(), g.count())
     */
    private LambdaAnalysisResult analyzeGroupQuery(
            byte[] classBytes,
            InvokeDynamicScanner.LambdaCallSite callSite,
            String callSiteId) {

        int totalCapturedVarCount = 0;

        // Analyze groupBy key extractor lambda (e.g., p -> p.department)
        LambdaExpression groupByKeyExpression = null;
        if (callSite.groupByLambdaMethodName() != null) {
            groupByKeyExpression = bytecodeAnalyzer.analyze(
                    classBytes,
                    callSite.groupByLambdaMethodName(),
                    callSite.groupByLambdaDescriptor());

            if (groupByKeyExpression == null) {
                log.warnf("Could not analyze groupBy key lambda at: %s", callSiteId);
                return null;
            }
            totalCapturedVarCount += countCapturedVariables(groupByKeyExpression);
        }

        // Analyze having lambdas if present (e.g., g -> g.count() > 5)
        LambdaExpression havingExpression = null;
        var havingLambdas = callSite.havingLambdas();
        if (havingLambdas != null && !havingLambdas.isEmpty()) {
            List<LambdaExpression> havingPredicates = new java.util.ArrayList<>();
            for (var lambdaPair : havingLambdas) {
                LambdaExpression expr = bytecodeAnalyzer.analyzeGroupQuerySpec(
                        classBytes,
                        lambdaPair.methodName(),
                        lambdaPair.descriptor());

                if (expr == null) {
                    log.warnf("Could not analyze having lambda %s at: %s",
                            lambdaPair.methodName(), callSiteId);
                    return null;
                }
                havingPredicates.add(expr);
                totalCapturedVarCount += countCapturedVariables(expr);
            }
            havingExpression = combinePredicatesWithAnd(havingPredicates);
        }

        // Analyze group select lambdas if present (e.g., g -> new DeptStats(g.key(), g.count()))
        LambdaExpression groupSelectExpression = null;
        var groupSelectLambdas = callSite.groupSelectLambdas();
        if (groupSelectLambdas != null && !groupSelectLambdas.isEmpty()) {
            // For now, support single select projection
            var firstSelect = groupSelectLambdas.get(0);
            groupSelectExpression = bytecodeAnalyzer.analyzeGroupQuerySpec(
                    classBytes,
                    firstSelect.methodName(),
                    firstSelect.descriptor());

            if (groupSelectExpression == null) {
                log.warnf("Could not analyze group select lambda at: %s", callSiteId);
                return null;
            }
            totalCapturedVarCount += countCapturedVariables(groupSelectExpression);
        }

        // Analyze group sort lambdas if present
        List<SortExpression> groupSortExpressions = analyzeGroupSortLambdas(classBytes, callSite, callSiteId);
        totalCapturedVarCount += countCapturedVariablesInSortExpressions(groupSortExpressions);

        // Also analyze pre-grouping WHERE predicates if present
        LambdaExpression predicateExpression = null;
        var predicateLambdas = callSite.predicateLambdas();
        if (predicateLambdas != null && !predicateLambdas.isEmpty()) {
            PredicateAnalysisResult predicateResult = analyzeAndCombinePredicates(
                    classBytes, predicateLambdas, callSiteId);
            if (predicateResult != null) {
                predicateExpression = predicateResult.expression;
                totalCapturedVarCount += predicateResult.capturedVarCount;
            }
        }

        log.debugf("Analyzed group query at %s: key=%s, having=%s, select=%s, sortCount=%d",
                callSiteId, groupByKeyExpression, havingExpression, groupSelectExpression,
                groupSortExpressions.size());

        return new LambdaAnalysisResult.GroupQueryResult(
                predicateExpression,  // Pre-grouping WHERE clause
                groupByKeyExpression,
                havingExpression,
                groupSelectExpression,
                groupSortExpressions,
                totalCapturedVarCount);
    }

    /**
     * Analyzes group sort lambdas from call site.
     * Iteration 7: Handles sortedBy() and sortedDescendingBy() on GroupStream.
     * Uses analyzeGroupQuerySpec() since group sort lambdas take Group parameter.
     *
     * @return list of SortExpression objects with group key extractors, or empty list if no sorting
     */
    private List<SortExpression> analyzeGroupSortLambdas(
            byte[] classBytes,
            InvokeDynamicScanner.LambdaCallSite callSite,
            String callSiteId) {

        List<SortLambda> sortLambdas = callSite.groupSortLambdas();

        if (sortLambdas == null || sortLambdas.isEmpty()) {
            return Collections.emptyList();
        }

        List<SortExpression> sortExpressions = new ArrayList<>(sortLambdas.size());

        for (SortLambda sortLambda : sortLambdas) {
            // Use analyzeGroupQuerySpec() for group sort lambdas
            LambdaExpression keyExtractor = bytecodeAnalyzer.analyzeGroupQuerySpec(
                    classBytes,
                    sortLambda.methodName(),
                    sortLambda.descriptor());

            if (keyExtractor == null) {
                log.warnf("Could not analyze group sort lambda %s at: %s", sortLambda.methodName(), callSiteId);
                return Collections.emptyList();
            }

            sortExpressions.add(new SortExpression(keyExtractor, sortLambda.direction()));
            log.debugf("Analyzed group sort lambda at %s: %s (direction=%s)",
                    callSiteId, keyExtractor, sortLambda.direction());
        }

        return sortExpressions;
    }

    /**
     * Analyzes sort lambdas from call site.
     * Phase 3: Handles sortedBy() and sortedDescendingBy() operations.
     *
     * @return list of SortExpression objects, or null if no sorting
     */
    private List<SortExpression> analyzeSortLambdas(
            byte[] classBytes,
            InvokeDynamicScanner.LambdaCallSite callSite,
            String callSiteId) {

        List<SortLambda> sortLambdas = callSite.sortLambdas();

        if (sortLambdas == null || sortLambdas.isEmpty()) {
            return Collections.emptyList();
        }

        List<SortExpression> sortExpressions = new ArrayList<>(sortLambdas.size());

        for (SortLambda sortLambda : sortLambdas) {
            LambdaExpression keyExtractor = bytecodeAnalyzer.analyze(
                    classBytes,
                    sortLambda.methodName(),
                    sortLambda.descriptor());

            if (keyExtractor == null) {
                log.warnf("Could not analyze sort lambda %s at: %s", sortLambda.methodName(), callSiteId);
                return Collections.emptyList();
            }

            sortExpressions.add(new SortExpression(keyExtractor, sortLambda.direction()));
            log.debugf("Analyzed sort lambda at %s: %s (direction=%s)",
                    callSiteId, keyExtractor, sortLambda.direction());
        }

        return sortExpressions;
    }

    /**
     * Analyzes bi-entity sort lambdas from call site for join queries.
     * Iteration 6.5: Handles sortedBy() and sortedDescendingBy() on JoinStream.
     * Uses analyzeBiEntity() since join sort lambdas take two entity parameters.
     *
     * @return list of SortExpression objects with bi-entity key extractors, or empty list if no sorting
     */
    private List<SortExpression> analyzeBiEntitySortLambdas(
            byte[] classBytes,
            InvokeDynamicScanner.LambdaCallSite callSite,
            String callSiteId) {

        List<SortLambda> sortLambdas = callSite.sortLambdas();

        if (sortLambdas == null || sortLambdas.isEmpty()) {
            return Collections.emptyList();
        }

        List<SortExpression> sortExpressions = new ArrayList<>(sortLambdas.size());

        for (SortLambda sortLambda : sortLambdas) {
            // Use analyzeBiEntity() for bi-entity sort lambdas in join queries
            LambdaExpression keyExtractor = bytecodeAnalyzer.analyzeBiEntity(
                    classBytes,
                    sortLambda.methodName(),
                    sortLambda.descriptor());

            if (keyExtractor == null) {
                log.warnf("Could not analyze bi-entity sort lambda %s at: %s", sortLambda.methodName(), callSiteId);
                return Collections.emptyList();
            }

            sortExpressions.add(new SortExpression(keyExtractor, sortLambda.direction()));
            log.debugf("Analyzed bi-entity sort lambda at %s: %s (direction=%s)",
                    callSiteId, keyExtractor, sortLambda.direction());
        }

        return sortExpressions;
    }

    /**
     * Combines multiple predicates with AND operation.
     * Phase 2.5: Supports multiple where() chaining.
     */
    private LambdaExpression combinePredicatesWithAnd(List<LambdaExpression> predicates) {
        if (predicates.isEmpty()) {
            throw new IllegalArgumentException("Cannot combine empty predicate list");
        }

        if (predicates.size() == 1) {
            return predicates.get(0);
        }

        // Chain predicates with AND: (p1 AND p2 AND p3 AND ...)
        LambdaExpression combined = predicates.get(0);
        for (int i = 1; i < predicates.size(); i++) {
            combined = new LambdaExpression.BinaryOp(
                    combined,
                    LambdaExpression.BinaryOp.Operator.AND,
                    predicates.get(i));
        }

        return combined;
    }

    /**
     * Counts distinct captured variables in lambda expression.
     */
    private int countCapturedVariables(LambdaExpression expression) {
        Set<Integer> capturedIndices = new HashSet<>();
        collectCapturedVariableIndices(expression, capturedIndices);

        return capturedIndices.size();
    }

    /**
     * Counts total captured variables across all sort expressions.
     * Returns 0 if sortExpressions is null or empty.
     */
    private int countCapturedVariablesInSortExpressions(List<SortExpression> sortExpressions) {
        if (sortExpressions == null || sortExpressions.isEmpty()) {
            return 0;
        }

        int count = 0;
        for (SortExpression sortExpr : sortExpressions) {
            count += countCapturedVariables(sortExpr.keyExtractor);
        }
        return count;
    }

    /**
     * Recursively collects captured variable indices.
     * <p>
     * Refactored for Java 21: Uses pattern matching switch for cleaner type dispatch.
     */
    private void collectCapturedVariableIndices(LambdaExpression expression, Set<Integer> capturedIndices) {
        if (expression == null) {
            return;
        }

        // Java 21 pattern matching switch for type dispatch
        switch (expression) {
            case LambdaExpression.CapturedVariable capturedVar ->
                capturedIndices.add(capturedVar.index());

            case LambdaExpression.BinaryOp binOp -> {
                collectCapturedVariableIndices(binOp.left(), capturedIndices);
                collectCapturedVariableIndices(binOp.right(), capturedIndices);
            }

            case LambdaExpression.UnaryOp unaryOp ->
                collectCapturedVariableIndices(unaryOp.operand(), capturedIndices);

            case LambdaExpression.MethodCall methodCall -> {
                collectCapturedVariableIndices(methodCall.target(), capturedIndices);
                for (LambdaExpression arg : methodCall.arguments()) {
                    collectCapturedVariableIndices(arg, capturedIndices);
                }
            }

            case LambdaExpression.ConstructorCall constructorCall -> {
                // Phase 2.4: Handle DTO constructor calls
                for (LambdaExpression arg : constructorCall.arguments()) {
                    collectCapturedVariableIndices(arg, capturedIndices);
                }
            }

            case LambdaExpression.InExpression inExpr -> {
                // Iteration 5: Handle IN clause expressions
                collectCapturedVariableIndices(inExpr.field(), capturedIndices);
                collectCapturedVariableIndices(inExpr.collection(), capturedIndices);
            }

            case LambdaExpression.MemberOfExpression memberOfExpr -> {
                // Iteration 5: Handle MEMBER OF expressions
                collectCapturedVariableIndices(memberOfExpr.value(), capturedIndices);
                collectCapturedVariableIndices(memberOfExpr.collectionField(), capturedIndices);
            }

            // These expression types don't contain captured variables - use separate cases
            // because multi-pattern cases with unnamed `_` require preview features in Java 21
            case LambdaExpression.PathExpression ignored1 -> { /* no captured variables */ }
            case LambdaExpression.BiEntityFieldAccess ignored2 -> { /* no captured variables */ }
            case LambdaExpression.BiEntityPathExpression ignored3 -> { /* no captured variables */ }
            case LambdaExpression.BiEntityParameter ignored4 -> { /* no captured variables */ }

            // Other expression types: FieldAccess, Constant, Parameter, NullLiteral, etc.
            default -> { /* no captured variables */ }
        }
    }

    /**
     * Renumbers captured variable indices in lambda expression tree by adding offset.
     * This ensures sequential indices when combining multiple lambdas.
     * <p>
     * Example: For second predicate with offset=1:
     * - CapturedVariable(0) becomes CapturedVariable(1)
     * - CapturedVariable(1) becomes CapturedVariable(2)
     * <p>
     * Recursively walks the entire AST tree to renumber all CapturedVariable nodes.
     * <p>
     * Refactored for Java 21: Uses pattern matching switch expression for cleaner type dispatch.
     *
     * @param expression the lambda expression AST
     * @param offset the offset to add to each captured variable index
     * @return new expression tree with renumbered indices
     */
    private LambdaExpression renumberCapturedVariables(LambdaExpression expression, int offset) {
        if (expression == null || offset == 0) {
            return expression;
        }

        // Java 21 pattern matching switch expression for type dispatch
        return switch (expression) {
            case LambdaExpression.CapturedVariable capturedVar ->
                new LambdaExpression.CapturedVariable(capturedVar.index() + offset, capturedVar.type());

            case LambdaExpression.BinaryOp binOp ->
                new LambdaExpression.BinaryOp(
                        renumberCapturedVariables(binOp.left(), offset),
                        binOp.operator(),
                        renumberCapturedVariables(binOp.right(), offset));

            case LambdaExpression.UnaryOp unaryOp ->
                new LambdaExpression.UnaryOp(
                        unaryOp.operator(),
                        renumberCapturedVariables(unaryOp.operand(), offset));

            case LambdaExpression.MethodCall methodCall -> {
                LambdaExpression newTarget = renumberCapturedVariables(methodCall.target(), offset);
                List<LambdaExpression> newArgs = new ArrayList<>();
                for (LambdaExpression arg : methodCall.arguments()) {
                    newArgs.add(renumberCapturedVariables(arg, offset));
                }
                yield new LambdaExpression.MethodCall(newTarget, methodCall.methodName(), newArgs, methodCall.returnType());
            }

            case LambdaExpression.ConstructorCall constructorCall -> {
                List<LambdaExpression> newArgs = new ArrayList<>();
                for (LambdaExpression arg : constructorCall.arguments()) {
                    newArgs.add(renumberCapturedVariables(arg, offset));
                }
                yield new LambdaExpression.ConstructorCall(constructorCall.className(), newArgs, constructorCall.resultType());
            }

            case LambdaExpression.Cast cast ->
                new LambdaExpression.Cast(renumberCapturedVariables(cast.expression(), offset), cast.targetType());

            case LambdaExpression.InstanceOf instanceOf ->
                new LambdaExpression.InstanceOf(renumberCapturedVariables(instanceOf.expression(), offset), instanceOf.targetType());

            case LambdaExpression.Conditional conditional ->
                new LambdaExpression.Conditional(
                        renumberCapturedVariables(conditional.condition(), offset),
                        renumberCapturedVariables(conditional.trueValue(), offset),
                        renumberCapturedVariables(conditional.falseValue(), offset));

            case LambdaExpression.InExpression inExpr ->
                // Iteration 5: Handle IN clause expressions
                new LambdaExpression.InExpression(
                        renumberCapturedVariables(inExpr.field(), offset),
                        renumberCapturedVariables(inExpr.collection(), offset),
                        inExpr.negated());

            case LambdaExpression.MemberOfExpression memberOfExpr ->
                // Iteration 5: Handle MEMBER OF expressions
                new LambdaExpression.MemberOfExpression(
                        renumberCapturedVariables(memberOfExpr.value(), offset),
                        renumberCapturedVariables(memberOfExpr.collectionField(), offset),
                        memberOfExpr.negated());

            // These expression types don't contain captured variables, return as-is
            // Using separate cases because multi-pattern with `_` requires Java 21 preview
            case LambdaExpression.PathExpression ignored1 -> expression;
            case LambdaExpression.BiEntityFieldAccess ignored2 -> expression;
            case LambdaExpression.BiEntityPathExpression ignored3 -> expression;
            case LambdaExpression.BiEntityParameter ignored4 -> expression;

            // Other expression types: FieldAccess, Constant, Parameter, NullLiteral, etc.
            default -> expression;
        };
    }
}
