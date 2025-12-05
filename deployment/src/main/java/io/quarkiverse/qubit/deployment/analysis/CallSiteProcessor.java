package io.quarkiverse.qubit.deployment.analysis;

import static io.quarkiverse.qubit.deployment.analysis.CapturedVariableHelper.combinePredicatesWithAnd;
import static io.quarkiverse.qubit.deployment.analysis.CapturedVariableHelper.countCapturedVariables;
import static io.quarkiverse.qubit.deployment.analysis.CapturedVariableHelper.countCapturedVariablesInSortExpressions;
import static io.quarkiverse.qubit.deployment.analysis.CapturedVariableHelper.renumberCapturedVariables;

import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.builditem.ApplicationArchivesBuildItem;
import io.quarkus.deployment.builditem.GeneratedClassBuildItem;
import io.quarkiverse.qubit.deployment.analysis.InvokeDynamicScanner.SortLambda;
import io.quarkiverse.qubit.deployment.ast.LambdaExpression;
import io.quarkiverse.qubit.deployment.QubitProcessor;
import io.quarkiverse.qubit.deployment.analysis.LambdaAnalysisResult.SortExpression;
import io.quarkiverse.qubit.deployment.generation.QueryExecutorClassGenerator;
import io.quarkiverse.qubit.deployment.util.BytecodeLoader;
import io.quarkus.logging.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Processes lambda call sites: analyzes bytecode, deduplicates, generates executors.
 * <p>
 * ARCH-001: Extracted LambdaAnalysisResult to separate file.
 * ARCH-001: Extracted captured variable utilities to CapturedVariableHelper.
 * BR-002: Removed queryCounter dependency - class names now use deterministic hash-based naming.
 */
public class CallSiteProcessor {

    private final LambdaBytecodeAnalyzer bytecodeAnalyzer;
    private final LambdaDeduplicator deduplicator;
    private final QueryExecutorClassGenerator classGenerator;

    public CallSiteProcessor(
            LambdaBytecodeAnalyzer bytecodeAnalyzer,
            LambdaDeduplicator deduplicator,
            QueryExecutorClassGenerator classGenerator) {
        this.bytecodeAnalyzer = bytecodeAnalyzer;
        this.deduplicator = deduplicator;
        this.classGenerator = classGenerator;
    }

    /**
     * Analyzes lambda at call site and generates query executor class.
     * <p>
     * Phase 2.2: Supports combined where() + select() queries with both predicate and projection.
     * Phase 2.5: Supports multiple where() predicates combined with AND.
     * MAINT-006: Refactored to extract helper methods for improved readability.
     */
    public void processCallSite(
            InvokeDynamicScanner.LambdaCallSite callSite,
            ApplicationArchivesBuildItem applicationArchives,
            AtomicInteger generatedCount,
            AtomicInteger deduplicatedCount,
            BuildProducer<GeneratedClassBuildItem> generatedClass,
            BuildProducer<QubitProcessor.QueryTransformationBuildItem> queryTransformations) {
        try {
            // MAINT-006: Extract bytecode loading and lambda analysis
            LambdaAnalysis analysis = loadAndAnalyzeLambdas(callSite, applicationArchives);
            if (analysis == null) {
                return;
            }

            // MAINT-006: Extract deduplication check
            if (checkAndHandleDuplicate(analysis, callSite, deduplicatedCount, queryTransformations)) {
                return;
            }

            // ARCH-002: Pattern matching switch with sealed interface (Java 21)
            // Compiler guarantees exhaustiveness - no default case needed
            // BR-002: Pass lambdaHash for deterministic class naming
            String executorClassName = switch (analysis.result()) {
                case LambdaAnalysisResult.GroupQueryResult group -> generateAndRegisterGroupExecutor(
                        group.predicateExpression(),
                        group.groupByKeyExpression(),
                        group.havingExpression(),
                        group.groupSelectExpression(),
                        group.groupSortExpressions(),
                        analysis.lambdaHash(),
                        analysis.callSiteId(),
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
                        analysis.lambdaHash(),
                        analysis.callSiteId(),
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
                        analysis.lambdaHash(),
                        analysis.callSiteId(),
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
                        analysis.lambdaHash(),
                        analysis.callSiteId(),
                        generatedClass,
                        queryTransformations);
            };

            deduplicator.registerExecutor(analysis.lambdaHash(), executorClassName);
            generatedCount.incrementAndGet();

            Log.debugf("Generated executor: %s for call site %s (hash: %s)",
                    executorClassName, analysis.callSiteId(), analysis.lambdaHash().substring(0, 8));

        } catch (Exception e) {
            Log.errorf(e, "Failed to process call site: %s", callSite);
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

        // MAINT-006: Use early returns to reduce nesting instead of if-else chain
        var predicateLambdas = callSite.predicateLambdas();
        boolean hasMultiplePredicates = predicateLambdas != null && predicateLambdas.size() > 1;

        if (hasMultiplePredicates) {
            return analyzeMultiplePredicates(classBytes, callSite, callSiteId);
        }

        if (callSite.isCombinedQuery()) {
            return analyzeCombinedQuery(classBytes, callSite, callSiteId);
        }

        return analyzeSingleLambda(classBytes, callSite, callSiteId, callSite.isProjectionQuery());
    }

    /**
     * MAINT-006: Loads bytecode, analyzes lambdas, and computes hash for a call site.
     * Extracts the first part of processCallSite to reduce method size and improve clarity.
     *
     * @param callSite The lambda call site to process
     * @param applicationArchives Application archives for bytecode loading
     * @return LambdaAnalysis containing result, callSiteId, and hash; null if loading/analysis fails
     */
    private LambdaAnalysis loadAndAnalyzeLambdas(
            InvokeDynamicScanner.LambdaCallSite callSite,
            ApplicationArchivesBuildItem applicationArchives) {

        byte[] classBytes = BytecodeLoader.loadClassBytecode(callSite.ownerClassName(), applicationArchives);
        if (classBytes == null) {
            Log.warnf("Could not load bytecode for class: %s", callSite.ownerClassName());
            return null;
        }

        String callSiteId = callSite.getCallSiteId();
        LambdaAnalysisResult result = analyzeLambdas(classBytes, callSite, callSiteId);
        if (result == null) {
            return null;
        }

        if (result.totalCapturedVarCount() > 0) {
            Log.debugf("Lambda(s) at %s contain %d captured variable(s)",
                      callSiteId, result.totalCapturedVarCount());
        }

        String lambdaHash = computeHash(callSite, result);
        return new LambdaAnalysis(result, callSiteId, lambdaHash);
    }

    /**
     * MAINT-006: Checks if this lambda is a duplicate and handles registration if so.
     * Extracts the deduplication check from processCallSite to reduce method size.
     *
     * @param analysis The lambda analysis containing result and hash
     * @param callSite The original call site for query type detection
     * @param deduplicatedCount Counter to increment on successful deduplication
     * @param queryTransformations Build producer for query transformations
     * @return true if this was a duplicate (already handled), false if new executor needed
     */
    private boolean checkAndHandleDuplicate(
            LambdaAnalysis analysis,
            InvokeDynamicScanner.LambdaCallSite callSite,
            AtomicInteger deduplicatedCount,
            BuildProducer<QubitProcessor.QueryTransformationBuildItem> queryTransformations) {

        boolean isGroupQuery = analysis.result() instanceof LambdaAnalysisResult.GroupQueryResult;

        // CS-006: Use QueryCharacteristics parameter object instead of 6 boolean parameters
        QueryCharacteristics characteristics = QueryCharacteristics.fromCallSite(callSite, isGroupQuery);
        return deduplicator.handleDuplicateLambda(
                analysis.callSiteId(),
                analysis.lambdaHash(),
                characteristics,
                analysis.result().totalCapturedVarCount(),
                deduplicatedCount,
                queryTransformations);
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
     * BR-002: Uses lambdaHash for deterministic class naming (reproducible builds).
     *
     * @param lambdaHash MD5 hash of the lambda expression (used for deterministic class naming)
     */
    private String generateAndRegisterExecutor(
            LambdaExpression predicateExpression,
            LambdaExpression projectionExpression,
            List<SortExpression> sortExpressions,
            LambdaExpression aggregationExpression,
            String aggregationType,
            boolean isCountQuery,
            boolean isAggregationQuery,
            String lambdaHash,
            String queryId,
            BuildProducer<GeneratedClassBuildItem> generatedClass,
            BuildProducer<QubitProcessor.QueryTransformationBuildItem> queryTransformations) {

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

        // BR-002: Use hash prefix for deterministic class naming instead of counter
        // 16 hex chars = 64 bits of entropy, effectively collision-free
        String className = "io.quarkiverse.qubit.generated.QueryExecutor_" +
                           lambdaHash.substring(0, 16);

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
                new QubitProcessor.QueryTransformationBuildItem(queryId, className, Object.class, isCountQuery, isAggregationQuery, capturedVarCount));

        String queryTypeDesc = getQueryTypeDescription(predicateExpression, projectionExpression, sortExpressions,
                                                       aggregationExpression, aggregationType, isCountQuery, isAggregationQuery);

        Log.debugf("Generated query executor: %s (%s, %d captured vars)",
                   className, queryTypeDesc, capturedVarCount);

        return className;
    }

    /**
     * Generates and registers a JOIN query executor class (Iteration 6).
     * <p>
     * Creates a query executor that performs a JPA join between two related entities.
     * BR-002: Uses lambdaHash for deterministic class naming (reproducible builds).
     *
     * @param joinRelationshipExpression Lambda for the join relationship (e.g., p -> p.phones)
     * @param biEntityPredicateExpression Lambda for bi-entity predicate (e.g., (p, ph) -> ph.type.equals("mobile"))
     * @param biEntityProjectionExpression Iteration 6.6: BiQuerySpec SELECT projection (e.g., (p, ph) -> new DTO(...))
     * @param sortExpressions Iteration 6.5: List of sort expressions for ORDER BY
     * @param joinType The type of join (INNER or LEFT)
     * @param lambdaHash MD5 hash of the lambda expression (used for deterministic class naming)
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
            String lambdaHash,
            String queryId,
            int capturedVarCount,
            boolean isCountQuery,
            boolean isSelectJoined,
            boolean isJoinProjection,
            BuildProducer<GeneratedClassBuildItem> generatedClass,
            BuildProducer<QubitProcessor.QueryTransformationBuildItem> queryTransformations) {

        // BR-002: Use hash prefix for deterministic class naming instead of counter
        String className = "io.quarkiverse.qubit.generated.QueryExecutor_" +
                           lambdaHash.substring(0, 16);

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
        // CS-006: Use QueryCharacteristics instead of boolean parameters
        QueryCharacteristics joinCharacteristics = new QueryCharacteristics(
                isCountQuery, false, true, isSelectJoined, isJoinProjection, false);
        queryTransformations.produce(
                new QubitProcessor.QueryTransformationBuildItem(queryId, className, Object.class, joinCharacteristics, capturedVarCount));

        String joinTypeDesc = (joinType == InvokeDynamicScanner.JoinType.LEFT) ? "LEFT JOIN" : "INNER JOIN";
        String queryTypeDesc = isCountQuery ? joinTypeDesc + " COUNT" :
                (isJoinProjection ? joinTypeDesc + " PROJECTION" :
                (isSelectJoined ? joinTypeDesc + " SELECT JOINED" : joinTypeDesc));
        Log.debugf("Generated join query executor: %s (%s, %d captured vars)",
                   className, queryTypeDesc, capturedVarCount);

        return className;
    }

    /**
     * Generates and registers a GROUP BY query executor class (Iteration 7).
     * <p>
     * Creates a query executor that performs JPA GROUP BY operations with optional
     * HAVING clause, aggregations, and sorting.
     * BR-002: Uses lambdaHash for deterministic class naming (reproducible builds).
     *
     * @param predicateExpression Pre-grouping WHERE clause (null if no filtering)
     * @param groupByKeyExpression groupBy() key extractor lambda (e.g., p -> p.department)
     * @param havingExpression having() predicate (null if no having)
     * @param groupSelectExpression select() projection in group context (null if no select)
     * @param groupSortExpressions sortedBy() in group context (null or empty if no sorting)
     * @param lambdaHash MD5 hash of the lambda expression (used for deterministic class naming)
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
            String lambdaHash,
            String queryId,
            int capturedVarCount,
            boolean isCountQuery,
            BuildProducer<GeneratedClassBuildItem> generatedClass,
            BuildProducer<QubitProcessor.QueryTransformationBuildItem> queryTransformations) {

        // BR-002: Use hash prefix for deterministic class naming instead of counter
        String className = "io.quarkiverse.qubit.generated.QueryExecutor_" +
                           lambdaHash.substring(0, 16);

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
        // CS-006: Use QueryCharacteristics instead of boolean parameters
        QueryCharacteristics groupCharacteristics = isCountQuery
                ? QueryCharacteristics.forGroupCount()
                : QueryCharacteristics.forGroupList();
        queryTransformations.produce(
                new QubitProcessor.QueryTransformationBuildItem(queryId, className, Object.class, groupCharacteristics, capturedVarCount));

        String queryTypeDesc = isCountQuery ? "GROUP BY COUNT" : "GROUP BY";
        if (havingExpression != null) {
            queryTypeDesc += "+HAVING";
        }
        if (groupSelectExpression != null) {
            queryTypeDesc += "+SELECT";
        }
        Log.debugf("Generated group query executor: %s (%s, %d captured vars)",
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

    /**
     * MAINT-006: Bundles lambda analysis results with computed hash for processing.
     * This record groups the analysis result, call site ID, and lambda hash to reduce
     * parameter passing between methods.
     *
     * @param result The analyzed lambda result (sealed interface with 4 specialized types)
     * @param callSiteId Unique identifier for the call site
     * @param lambdaHash MD5 hash of the lambda for deduplication and deterministic class naming
     */
    private record LambdaAnalysis(
            LambdaAnalysisResult result,
            String callSiteId,
            String lambdaHash) {}

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
                Log.warnf("Could not analyze predicate lambda %s at: %s", lambdaPair.methodName(), callSiteId);
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

        Log.debugf("Combined %d predicates with AND at %s (total %d captured variables)",
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
                Log.warnf("Could not analyze projection lambda at: %s", callSiteId);
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
            Log.warnf("Combined query without predicates at: %s", callSiteId);
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
            Log.warnf("Could not analyze projection lambda at: %s", callSiteId);
            return null;
        }

        Log.debugf("Analyzed combined query at %s: WHERE=%s, SELECT=%s",
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
            Log.debugf("Analyzed sorting-only query at %s: %d sort expression(s)", callSiteId, sortExpressions.size());
            return new LambdaAnalysisResult.SimpleQueryResult(
                    null, null, sortExpressions, totalCapturedVarCount);
        }

        // Regular WHERE or SELECT query
        LambdaExpression lambdaExpression = bytecodeAnalyzer.analyze(
                classBytes,
                callSite.lambdaMethodName(),
                callSite.lambdaMethodDescriptor());

        if (lambdaExpression == null) {
            Log.warnf("Could not analyze lambda expression at: %s", callSiteId);
            return null;
        }

        Log.debugf("Analyzed lambda at %s: %s", callSiteId, lambdaExpression);

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
            Log.warnf("Aggregation query missing mapper lambda at: %s", callSiteId);
            return null;
        }

        LambdaExpression aggregationExpression = bytecodeAnalyzer.analyze(
                classBytes,
                callSite.aggregationLambdaMethodName(),
                callSite.aggregationLambdaMethodDescriptor());

        if (aggregationExpression == null) {
            Log.warnf("Could not analyze aggregation mapper lambda at: %s", callSiteId);
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

        Log.debugf("Analyzed aggregation query at %s: type=%s, mapper=%s, predicate=%s",
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
                Log.warnf("Could not analyze join relationship lambda at: %s", callSiteId);
                return null;
            }
            totalCapturedVarCount += countCapturedVariables(joinRelationshipExpression);
        }

        // Analyze bi-entity predicate lambdas if present (e.g., (p, ph) -> ph.type.equals("mobile"))
        LambdaExpression biEntityPredicateExpression = null;
        var biEntityPredicateLambdas = callSite.biEntityPredicateLambdas();
        if (biEntityPredicateLambdas != null && !biEntityPredicateLambdas.isEmpty()) {
            // Analyze each bi-entity predicate and combine with AND
            List<LambdaExpression> biPredicates = new ArrayList<>();
            for (var lambdaPair : biEntityPredicateLambdas) {
                LambdaExpression expr = bytecodeAnalyzer.analyzeBiEntity(
                        classBytes,
                        lambdaPair.methodName(),
                        lambdaPair.descriptor());

                if (expr == null) {
                    Log.warnf("Could not analyze bi-entity predicate lambda %s at: %s",
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
                Log.warnf("Could not analyze bi-entity projection lambda at: %s", callSiteId);
                return null;
            }
            totalCapturedVarCount += countCapturedVariables(biEntityProjectionExpression);
        }

        Log.debugf("Analyzed join query at %s: type=%s, relationship=%s, biPredicate=%s, biProjection=%s, sortExpressions=%d",
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
                Log.warnf("Could not analyze groupBy key lambda at: %s", callSiteId);
                return null;
            }
            totalCapturedVarCount += countCapturedVariables(groupByKeyExpression);
        }

        // Analyze having lambdas if present (e.g., g -> g.count() > 5)
        LambdaExpression havingExpression = null;
        var havingLambdas = callSite.havingLambdas();
        if (havingLambdas != null && !havingLambdas.isEmpty()) {
            List<LambdaExpression> havingPredicates = new ArrayList<>();
            for (var lambdaPair : havingLambdas) {
                LambdaExpression expr = bytecodeAnalyzer.analyzeGroupQuerySpec(
                        classBytes,
                        lambdaPair.methodName(),
                        lambdaPair.descriptor());

                if (expr == null) {
                    Log.warnf("Could not analyze having lambda %s at: %s",
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
                Log.warnf("Could not analyze group select lambda at: %s", callSiteId);
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

        Log.debugf("Analyzed group query at %s: key=%s, having=%s, select=%s, sortCount=%d",
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
                Log.warnf("Could not analyze group sort lambda %s at: %s", sortLambda.methodName(), callSiteId);
                return Collections.emptyList();
            }

            sortExpressions.add(new SortExpression(keyExtractor, sortLambda.direction()));
            Log.debugf("Analyzed group sort lambda at %s: %s (direction=%s)",
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
                Log.warnf("Could not analyze sort lambda %s at: %s", sortLambda.methodName(), callSiteId);
                return Collections.emptyList();
            }

            sortExpressions.add(new SortExpression(keyExtractor, sortLambda.direction()));
            Log.debugf("Analyzed sort lambda at %s: %s (direction=%s)",
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
                Log.warnf("Could not analyze bi-entity sort lambda %s at: %s", sortLambda.methodName(), callSiteId);
                return Collections.emptyList();
            }

            sortExpressions.add(new SortExpression(keyExtractor, sortLambda.direction()));
            Log.debugf("Analyzed bi-entity sort lambda at %s: %s (direction=%s)",
                    callSiteId, keyExtractor, sortLambda.direction());
        }

        return sortExpressions;
    }
}
