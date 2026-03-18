package io.quarkiverse.qubit.deployment.analysis.handler;

import static io.quarkiverse.qubit.runtime.internal.QubitConstants.AGG_TYPE_AVG;
import static io.quarkiverse.qubit.runtime.internal.QubitConstants.AGG_TYPE_MAX;
import static io.quarkiverse.qubit.runtime.internal.QubitConstants.AGG_TYPE_MIN;
import static io.quarkiverse.qubit.runtime.internal.QubitConstants.AGG_TYPE_SUM_DOUBLE;
import static io.quarkiverse.qubit.runtime.internal.QubitConstants.AGG_TYPE_SUM_INTEGER;
import static io.quarkiverse.qubit.runtime.internal.QubitConstants.AGG_TYPE_SUM_LONG;
import static io.quarkiverse.qubit.runtime.internal.QubitConstants.METHOD_AVG;
import static io.quarkiverse.qubit.runtime.internal.QubitConstants.METHOD_MAX;
import static io.quarkiverse.qubit.runtime.internal.QubitConstants.METHOD_MIN;
import static io.quarkiverse.qubit.runtime.internal.QubitConstants.METHOD_SUM_DOUBLE;
import static io.quarkiverse.qubit.runtime.internal.QubitConstants.METHOD_SUM_INTEGER;
import static io.quarkiverse.qubit.runtime.internal.QubitConstants.METHOD_SUM_LONG;

import io.quarkiverse.qubit.deployment.analysis.AnalysisOutcome;
import io.quarkiverse.qubit.deployment.analysis.CallSite;
import io.quarkiverse.qubit.deployment.analysis.CapturedVariableHelper;
import io.quarkiverse.qubit.deployment.analysis.LambdaAnalysisResult;
import io.quarkiverse.qubit.deployment.analysis.LambdaAnalysisResult.AggregationQueryResult;
import io.quarkiverse.qubit.deployment.analysis.LambdaDeduplicator;
import io.quarkiverse.qubit.deployment.ast.LambdaExpression;

/** Handler for aggregation queries: min, max, avg, sumInteger/sumLong/sumDouble. */
public final class AggregationQueryHandler extends AbstractQueryHandler {

    private static final AggregationQueryHandler INSTANCE = new AggregationQueryHandler();

    private AggregationQueryHandler() {
        // Singleton
    }

    /** Returns the singleton instance. */
    public static AggregationQueryHandler instance() {
        return INSTANCE;
    }

    @Override
    public String queryTypeName() {
        return "AGGREGATION";
    }

    @Override
    public boolean canHandle(CallSite callSite) {
        return callSite instanceof CallSite.AggregationCallSite;
    }

    @Override
    public AnalysisOutcome analyze(QueryAnalysisContext context) {
        return safeAnalyze(context, this::doAnalyze);
    }

    private AnalysisOutcome doAnalyze(QueryAnalysisContext context) {
        CallSite.AggregationCallSite callSite = (CallSite.AggregationCallSite) context.callSite();

        // Analyze aggregation mapper lambda (always present — guaranteed by classify())
        LambdaExpression aggregationExpr = analyzeSingleLambda(
                context, callSite.aggregationLambda());

        // Analyze optional WHERE predicate
        LambdaExpression predicateExpr = analyzeAndCombinePredicates(
                context, callSite.predicateLambdas());

        // Determine aggregation type from terminal method
        String aggregationType = getAggregationType(callSite.targetMethodName());

        // Renumber aggregation captured variables to avoid index collision with predicate.
        // renumberCapturedVariables() handles offset=0 as a no-op.
        int predicateCapturedCount = CapturedVariableHelper.countCapturedVariables(predicateExpr);
        aggregationExpr = CapturedVariableHelper.renumberCapturedVariables(
                aggregationExpr, predicateCapturedCount);

        // Count captured variables (after renumbering)
        int totalCapturedVars = countTotalCapturedVariables(predicateExpr, aggregationExpr);

        // Build result
        AggregationQueryResult result = new AggregationQueryResult(
                predicateExpr,
                aggregationExpr,
                aggregationType,
                totalCapturedVars);

        // Compute hash for deduplication using shared deduplicator
        String lambdaHash = context.deduplicator().computeAggregationHash(
                predicateExpr, aggregationExpr, aggregationType);

        return AnalysisOutcome.success(result, context.callSiteId(), lambdaHash);
    }

    @Override
    public String computeHash(
            LambdaDeduplicator deduplicator,
            CallSite callSite,
            LambdaAnalysisResult result) {

        AggregationQueryResult agg = castResult(result, AggregationQueryResult.class);
        return deduplicator.computeAggregationHash(
                agg.predicateExpression(),
                agg.aggregationExpression(),
                agg.aggregationType());
    }

    private String getAggregationType(String methodName) {
        return switch (methodName) {
            case METHOD_MIN -> AGG_TYPE_MIN;
            case METHOD_MAX -> AGG_TYPE_MAX;
            case METHOD_AVG -> AGG_TYPE_AVG;
            case METHOD_SUM_INTEGER -> AGG_TYPE_SUM_INTEGER;
            case METHOD_SUM_LONG -> AGG_TYPE_SUM_LONG;
            case METHOD_SUM_DOUBLE -> AGG_TYPE_SUM_DOUBLE;
            default -> methodName.toUpperCase();
        };
    }
}
