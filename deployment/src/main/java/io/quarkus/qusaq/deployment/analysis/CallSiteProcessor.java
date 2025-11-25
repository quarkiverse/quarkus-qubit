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
     * Result of lambda bytecode analysis containing expressions and captured variable count.
     * Phase 3: Added sortExpressions list for sorting support.
     * Phase 5: Added aggregationExpression for aggregation queries (min/max/avg/sum*).
     */
    private record LambdaAnalysisResult(
            LambdaExpression predicateExpression,
            LambdaExpression projectionExpression,
            List<SortExpression> sortExpressions,
            LambdaExpression aggregationExpression,
            String aggregationType,  // Phase 5: "MIN", "MAX", "AVG", "SUM_INTEGER", "SUM_LONG", "SUM_DOUBLE"
            int totalCapturedVarCount) {}

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
            log.debugf("Processing fluent API call site: %s", callSite);

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

            if (result.totalCapturedVarCount > 0) {
                log.debugf("Lambda(s) at %s contain %d captured variable(s)",
                          callSiteId, result.totalCapturedVarCount);
            }

            String lambdaHash = computeHash(callSite, result);
            if (deduplicator.handleDuplicateLambda(callSiteId, lambdaHash,
                    callSite.isCountQuery(),
                    callSite.isAggregationQuery(),
                    result.totalCapturedVarCount, deduplicatedCount, queryTransformations)) {
                return;
            }

            String executorClassName = generateAndRegisterExecutor(
                    result.predicateExpression,
                    result.projectionExpression,
                    result.sortExpressions,
                    result.aggregationExpression,
                    result.aggregationType,
                    callSite.isCountQuery(),
                    callSite.isAggregationQuery(),
                    callSiteId,
                    generatedClass,
                    queryTransformations);

            deduplicator.registerExecutor(lambdaHash, executorClassName);
            generatedCount.incrementAndGet();

            log.debugf("Generated executor: %s for call site %s (hash: %s)",
                    executorClassName, callSiteId, lambdaHash.substring(0, 8));

        } catch (Exception e) {
            log.errorf(e, "Failed to process call site: %s", callSite);
        }
    }

    /**
     * Analyzes lambdas based on call site type (multiple predicates, combined, aggregation, or single).
     * Phase 5: Added aggregation query support.
     */
    private LambdaAnalysisResult analyzeLambdas(
            byte[] classBytes,
            InvokeDynamicScanner.LambdaCallSite callSite,
            String callSiteId) {

        // Phase 5: Handle aggregation queries first
        if (callSite.isAggregationQuery()) {
            return analyzeAggregationQuery(classBytes, callSite, callSiteId);
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
     */
    private String computeHash(InvokeDynamicScanner.LambdaCallSite callSite, LambdaAnalysisResult result) {
        // Phase 5: Aggregation queries have priority
        if (callSite.isAggregationQuery() && result.aggregationExpression != null) {
            return deduplicator.computeAggregationHash(
                    result.predicateExpression,
                    result.aggregationExpression,
                    result.aggregationType);
        }

        // Phase 3: Include sort expressions in hash if present
        boolean hasSorting = result.sortExpressions != null && !result.sortExpressions.isEmpty();

        if (callSite.isCombinedQuery()) {
            if (hasSorting) {
                return deduplicator.computeFullQueryHash(
                        result.predicateExpression,
                        result.projectionExpression,
                        result.sortExpressions,
                        callSite.isCountQuery());
            }
            return deduplicator.computeCombinedHash(
                    result.predicateExpression,
                    result.projectionExpression,
                    callSite.isCountQuery());
        }

        // Phase 3: For sorting-only queries, compute hash from sort expressions
        if (result.predicateExpression == null && result.projectionExpression == null && hasSorting) {
            return deduplicator.computeSortingHash(result.sortExpressions);
        }

        // Regular WHERE or SELECT query (possibly with sorting)
        LambdaExpression expr = result.predicateExpression != null
                ? result.predicateExpression
                : result.projectionExpression;

        if (hasSorting) {
            return deduplicator.computeQueryWithSortingHash(expr, result.sortExpressions,
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

        return new LambdaAnalysisResult(predicateExpression, projectionExpression, sortExpressions,
                                       null, null, totalCapturedVarCount);
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

        return new LambdaAnalysisResult(predicateExpression, projectionExpression, sortExpressions,
                                       null, null, totalCapturedVarCount);
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
            return new LambdaAnalysisResult(null, null, sortExpressions, null, null, totalCapturedVarCount);
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

        return new LambdaAnalysisResult(predicateExpression, projectionExpression, sortExpressions,
                                       null, null, totalCapturedVarCount);
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

        return new LambdaAnalysisResult(
                predicateExpression,
                null,  // No projection for aggregations
                Collections.emptyList(),  // No sorting for aggregations
                aggregationExpression,
                aggregationType,
                totalCapturedVarCount);
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
     */
    private void collectCapturedVariableIndices(LambdaExpression expression, Set<Integer> capturedIndices) {
        if (expression == null) {
            return;
        }

        if (expression instanceof LambdaExpression.CapturedVariable capturedVar) {
            capturedIndices.add(capturedVar.index());
        } else if (expression instanceof LambdaExpression.BinaryOp binOp) {
            collectCapturedVariableIndices(binOp.left(), capturedIndices);
            collectCapturedVariableIndices(binOp.right(), capturedIndices);
        } else if (expression instanceof LambdaExpression.UnaryOp unaryOp) {
            collectCapturedVariableIndices(unaryOp.operand(), capturedIndices);
        } else if (expression instanceof LambdaExpression.MethodCall methodCall) {
            collectCapturedVariableIndices(methodCall.target(), capturedIndices);
            for (LambdaExpression arg : methodCall.arguments()) {
                collectCapturedVariableIndices(arg, capturedIndices);
            }
        } else if (expression instanceof LambdaExpression.ConstructorCall constructorCall) {
            // Phase 2.4: Handle DTO constructor calls
            for (LambdaExpression arg : constructorCall.arguments()) {
                collectCapturedVariableIndices(arg, capturedIndices);
            }
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
     *
     * @param expression the lambda expression AST
     * @param offset the offset to add to each captured variable index
     * @return new expression tree with renumbered indices
     */
    private LambdaExpression renumberCapturedVariables(LambdaExpression expression, int offset) {
        if (expression == null || offset == 0) {
            return expression;
        }

        if (expression instanceof LambdaExpression.CapturedVariable capturedVar) {
            return new LambdaExpression.CapturedVariable(capturedVar.index() + offset, capturedVar.type());
        } else if (expression instanceof LambdaExpression.BinaryOp binOp) {
            return new LambdaExpression.BinaryOp(
                    renumberCapturedVariables(binOp.left(), offset),
                    binOp.operator(),
                    renumberCapturedVariables(binOp.right(), offset));
        } else if (expression instanceof LambdaExpression.UnaryOp unaryOp) {
            return new LambdaExpression.UnaryOp(
                    unaryOp.operator(),
                    renumberCapturedVariables(unaryOp.operand(), offset));
        } else if (expression instanceof LambdaExpression.MethodCall methodCall) {
            LambdaExpression newTarget = renumberCapturedVariables(methodCall.target(), offset);
            List<LambdaExpression> newArgs = new ArrayList<>();
            for (LambdaExpression arg : methodCall.arguments()) {
                newArgs.add(renumberCapturedVariables(arg, offset));
            }
            return new LambdaExpression.MethodCall(newTarget, methodCall.methodName(), newArgs, methodCall.returnType());
        } else if (expression instanceof LambdaExpression.ConstructorCall constructorCall) {
            List<LambdaExpression> newArgs = new ArrayList<>();
            for (LambdaExpression arg : constructorCall.arguments()) {
                newArgs.add(renumberCapturedVariables(arg, offset));
            }
            return new LambdaExpression.ConstructorCall(constructorCall.className(), newArgs, constructorCall.resultType());
        } else if (expression instanceof LambdaExpression.Cast cast) {
            return new LambdaExpression.Cast(renumberCapturedVariables(cast.expression(), offset), cast.targetType());
        } else if (expression instanceof LambdaExpression.InstanceOf instanceOf) {
            return new LambdaExpression.InstanceOf(renumberCapturedVariables(instanceOf.expression(), offset), instanceOf.targetType());
        } else if (expression instanceof LambdaExpression.Conditional conditional) {
            return new LambdaExpression.Conditional(
                    renumberCapturedVariables(conditional.condition(), offset),
                    renumberCapturedVariables(conditional.trueValue(), offset),
                    renumberCapturedVariables(conditional.falseValue(), offset));
        } else {
            // These expression types don't contain captured variables, return as-is:
            // FieldAccess, Constant, Parameter, NullLiteral
            return expression;
        }
    }
}
