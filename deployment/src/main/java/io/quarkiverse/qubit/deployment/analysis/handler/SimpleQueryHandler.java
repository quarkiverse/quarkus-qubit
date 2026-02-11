package io.quarkiverse.qubit.deployment.analysis.handler;

import static io.quarkiverse.qubit.runtime.internal.QubitConstants.METHOD_SELECT;

import java.util.List;

import io.quarkiverse.qubit.deployment.analysis.AnalysisOutcome;
import io.quarkiverse.qubit.deployment.analysis.InvokeDynamicScanner.LambdaCallSite;
import io.quarkiverse.qubit.deployment.analysis.LambdaAnalysisResult;
import io.quarkiverse.qubit.deployment.analysis.LambdaAnalysisResult.SimpleQueryResult;
import io.quarkiverse.qubit.deployment.analysis.LambdaAnalysisResult.SortExpression;
import io.quarkiverse.qubit.deployment.analysis.LambdaDeduplicator;
import io.quarkiverse.qubit.deployment.ast.LambdaExpression;

/** Handler for simple queries: WHERE, SELECT, sorting, and combinations. */
public final class SimpleQueryHandler extends AbstractQueryHandler {

    private static final SimpleQueryHandler INSTANCE = new SimpleQueryHandler();

    private SimpleQueryHandler() {
        // Singleton
    }

    /** Returns the singleton instance. */
    public static SimpleQueryHandler instance() {
        return INSTANCE;
    }

    @Override
    public String queryTypeName() {
        return "SIMPLE";
    }

    @Override
    public boolean canHandle(LambdaCallSite callSite) {
        // Simple handler is the default - handles anything not handled by others
        return !callSite.isGroupQuery() &&
                !callSite.isJoinQuery() &&
                !callSite.isAggregationQuery();
    }

    @Override
    public AnalysisOutcome analyze(QueryAnalysisContext context) {
        return safeAnalyze(context, this::doAnalyze);
    }

    private AnalysisOutcome doAnalyze(QueryAnalysisContext context) {
        LambdaCallSite callSite = context.callSite();

        // Analyze predicate (WHERE clause) - may be null for SELECT-only queries
        LambdaExpression predicateExpr = analyzeAndCombinePredicates(
                context, callSite.predicateLambdas());

        // Analyze projection (SELECT clause) - may be null for WHERE-only queries
        LambdaExpression projectionExpr = analyzeSingleLambda(
                context,
                callSite.projectionLambdaMethodName(),
                callSite.projectionLambdaMethodDescriptor());

        // Analyze sort expressions
        List<SortExpression> sortExpressions = analyzeSortLambdas(
                context, callSite.sortLambdas());

        // At least one expression should be present
        if (predicateExpr == null && projectionExpr == null && sortExpressions.isEmpty()) {
            // Check if there's a primary lambda for backward compatibility
            LambdaExpression primaryExpr = analyzeSingleLambda(
                    context,
                    callSite.lambdaMethodName(),
                    callSite.lambdaMethodDescriptor());

            if (primaryExpr == null) {
                return AnalysisOutcome.unsupported(
                        "No analyzable lambda expressions found",
                        context.callSiteId());
            }

            // Determine if primary lambda is predicate or projection based on fluent method
            if (isProjectionMethod(callSite.fluentMethodName())) {
                projectionExpr = primaryExpr;
            } else {
                predicateExpr = primaryExpr;
            }
        }

        // Count captured variables
        int totalCapturedVars = countTotalCapturedVariablesWithSort(
                predicateExpr, sortExpressions, projectionExpr);

        // Build result
        SimpleQueryResult result = new SimpleQueryResult(
                predicateExpr,
                projectionExpr,
                sortExpressions,
                totalCapturedVars);

        // Compute hash for deduplication using shared deduplicator
        String lambdaHash = computeHashWithDeduplicator(
                context.deduplicator(),
                context.callSite(),
                new SimpleQueryResult(predicateExpr, projectionExpr, sortExpressions, 0));

        return AnalysisOutcome.success(result, context.callSiteId(), lambdaHash);
    }

    @Override
    public String computeHash(
            LambdaDeduplicator deduplicator,
            LambdaCallSite callSite,
            LambdaAnalysisResult result) {

        SimpleQueryResult simple = castResult(result, SimpleQueryResult.class);
        return computeHashWithDeduplicator(deduplicator, callSite, simple);
    }

    private String computeHashWithDeduplicator(
            LambdaDeduplicator deduplicator,
            LambdaCallSite callSite,
            SimpleQueryResult result) {

        boolean isCountQuery = callSite.isCountQuery();
        boolean hasPredicate = result.predicateExpression() != null;
        boolean hasProjection = result.projectionExpression() != null;
        boolean hasSort = result.sortExpressions() != null && !result.sortExpressions().isEmpty();

        // Sorting-only query
        if (!hasPredicate && !hasProjection && hasSort) {
            return deduplicator.computeSortingHash(result.sortExpressions());
        }

        // Combined WHERE + SELECT + SORT
        if (hasPredicate && hasProjection && hasSort) {
            return deduplicator.computeFullQueryHash(
                    result.predicateExpression(),
                    result.projectionExpression(),
                    result.sortExpressions(),
                    isCountQuery);
        }

        // Combined WHERE + SELECT (no sort)
        if (hasPredicate && hasProjection) {
            return deduplicator.computeCombinedHash(
                    result.predicateExpression(),
                    result.projectionExpression(),
                    isCountQuery);
        }

        // WHERE or SELECT with sort
        if (hasSort) {
            LambdaExpression expr = hasPredicate ? result.predicateExpression() : result.projectionExpression();
            return deduplicator.computeQueryWithSortingHash(
                    expr, result.sortExpressions(), isCountQuery, hasProjection);
        }

        // Single expression (WHERE or SELECT)
        LambdaExpression expr = hasPredicate ? result.predicateExpression() : result.projectionExpression();
        return deduplicator.computeLambdaHash(expr, isCountQuery, hasProjection);
    }

    private boolean isProjectionMethod(String methodName) {
        return METHOD_SELECT.equals(methodName);
    }
}
