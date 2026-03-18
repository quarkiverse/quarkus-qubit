package io.quarkiverse.qubit.deployment.analysis.handler;

import static io.quarkiverse.qubit.runtime.internal.QubitConstants.METHOD_SELECT;

import java.util.List;

import io.quarkiverse.qubit.deployment.analysis.AnalysisOutcome;
import io.quarkiverse.qubit.deployment.analysis.CallSite;
import io.quarkiverse.qubit.deployment.analysis.CapturedVariableHelper;
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
    public boolean canHandle(CallSite callSite) {
        // Simple handler is the default - handles anything not handled by others
        return callSite instanceof CallSite.SimpleCallSite;
    }

    @Override
    public AnalysisOutcome analyze(QueryAnalysisContext context) {
        return safeAnalyze(context, this::doAnalyze);
    }

    private AnalysisOutcome doAnalyze(QueryAnalysisContext context) {
        CallSite.SimpleCallSite callSite = (CallSite.SimpleCallSite) context.callSite();

        // Analyze predicate (WHERE clause) - may be null for SELECT-only queries
        LambdaExpression predicateExpr = analyzeAndCombinePredicates(
                context, callSite.predicateLambdas());

        // Analyze projection (SELECT clause) - may be null for WHERE-only queries
        LambdaExpression projectionExpr = analyzeSingleLambda(
                context, callSite.projectionLambda());

        // Analyze sort expressions
        List<SortExpression> sortExpressions = analyzeSortLambdas(
                context, callSite.sortLambdas());

        // If no classified lambdas produced results, fall back to the primary lambda.
        // primaryLambda is always present (guaranteed by isTerminalOperation guard).
        if (predicateExpr == null && projectionExpr == null && sortExpressions.isEmpty()) {
            LambdaExpression primaryExpr = analyzeSingleLambda(
                    context, callSite.primaryLambda());

            // Determine if primary lambda is predicate or projection based on fluent method
            if (isProjectionMethod(callSite.fluentMethodName())) {
                projectionExpr = primaryExpr;
            } else {
                predicateExpr = primaryExpr;
            }
        }

        // Renumber captured variables for contiguous indexing across expressions.
        // Each lambda's captured variables start at index 0, but the generated executor
        // uses a single flat capturedValues[] array. Without renumbering, predicate and
        // projection captured variables would collide at index 0.
        // Note: renumberCapturedVariables() handles null expressions and offset=0 as no-ops.
        int offset = CapturedVariableHelper.countCapturedVariables(predicateExpr);
        projectionExpr = CapturedVariableHelper.renumberCapturedVariables(projectionExpr, offset);
        offset += CapturedVariableHelper.countCapturedVariables(projectionExpr);
        sortExpressions = renumberSortExpressions(sortExpressions, offset);

        // Count captured variables (after renumbering — count is the same, indices are adjusted)
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
            CallSite callSite,
            LambdaAnalysisResult result) {

        SimpleQueryResult simple = castResult(result, SimpleQueryResult.class);
        return computeHashWithDeduplicator(deduplicator, callSite, simple);
    }

    private String computeHashWithDeduplicator(
            LambdaDeduplicator deduplicator,
            CallSite callSite,
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
