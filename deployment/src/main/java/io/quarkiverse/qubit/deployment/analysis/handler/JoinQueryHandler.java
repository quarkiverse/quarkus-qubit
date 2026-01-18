package io.quarkiverse.qubit.deployment.analysis.handler;

import io.quarkiverse.qubit.deployment.analysis.AnalysisOutcome;
import io.quarkiverse.qubit.deployment.analysis.InvokeDynamicScanner.LambdaCallSite;
import io.quarkiverse.qubit.deployment.analysis.LambdaAnalysisResult;
import io.quarkiverse.qubit.deployment.analysis.LambdaAnalysisResult.JoinQueryResult;
import io.quarkiverse.qubit.deployment.analysis.LambdaAnalysisResult.SortExpression;
import io.quarkiverse.qubit.deployment.analysis.LambdaDeduplicator;
import io.quarkiverse.qubit.deployment.ast.LambdaExpression;

import java.util.List;

/** Handler for join queries: inner/left join with bi-entity operations. */
public final class JoinQueryHandler extends AbstractQueryHandler {

    private static final JoinQueryHandler INSTANCE = new JoinQueryHandler();

    private JoinQueryHandler() {
        // Singleton
    }

    /** Returns the singleton instance. */
    public static JoinQueryHandler instance() {
        return INSTANCE;
    }

    @Override
    public String queryTypeName() {
        return "JOIN";
    }

    @Override
    public boolean canHandle(LambdaCallSite callSite) {
        return callSite.isJoinQuery();
    }

    @Override
    public AnalysisOutcome analyze(QueryAnalysisContext context) {
        return safeAnalyze(context, this::doAnalyze);
    }

    private AnalysisOutcome doAnalyze(QueryAnalysisContext context) {
        LambdaCallSite callSite = context.callSite();

        // Analyze join relationship lambda (required)
        LambdaExpression joinRelationshipExpr = analyzeSingleLambda(
                context,
                callSite.joinRelationshipLambdaMethodName(),
                callSite.joinRelationshipLambdaDescriptor());

        if (joinRelationshipExpr == null) {
            return unsupportedMissingLambda("join relationship", context.callSiteId());
        }

        // Analyze bi-entity predicate (WHERE on joined entities)
        LambdaExpression biEntityPredicateExpr = analyzeAndCombineLambdas(
                callSite.biEntityPredicateLambdas(),
                pair -> analyzeBiEntityLambda(context, pair.methodName(), pair.descriptor()),
                "bi-entity predicate",
                context.callSiteId());

        // Analyze bi-entity projection (SELECT on joined entities)
        LambdaExpression biEntityProjectionExpr = analyzeBiEntityLambda(
                context,
                callSite.biEntityProjectionLambdaMethodName(),
                callSite.biEntityProjectionLambdaDescriptor());

        // Analyze bi-entity sort expressions (join queries use bi-entity lambdas)
        List<SortExpression> sortExpressions = analyzeBiEntitySortLambdas(
                context, callSite.sortLambdas());

        // Count captured variables
        int totalCapturedVars = countTotalCapturedVariablesWithSort(
                biEntityPredicateExpr,
                sortExpressions,
                joinRelationshipExpr,
                biEntityProjectionExpr);

        // Build result
        JoinQueryResult result = new JoinQueryResult(
                joinRelationshipExpr,
                biEntityPredicateExpr,
                biEntityProjectionExpr,
                sortExpressions,
                callSite.joinType(),
                totalCapturedVars);

        // Compute hash for deduplication using shared deduplicator
        String lambdaHash = context.deduplicator().computeJoinHash(
                joinRelationshipExpr,
                biEntityPredicateExpr,
                biEntityProjectionExpr,
                sortExpressions,
                callSite.joinType().name(),
                callSite.isCountQuery(),
                callSite.isSelectJoinedQuery(),
                callSite.isJoinProjectionQuery());

        return AnalysisOutcome.success(result, context.callSiteId(), lambdaHash);
    }

    @Override
    public String computeHash(
            LambdaDeduplicator deduplicator,
            LambdaCallSite callSite,
            LambdaAnalysisResult result) {

        JoinQueryResult join = castResult(result, JoinQueryResult.class);
        return deduplicator.computeJoinHash(
                join.joinRelationshipExpression(),
                join.biEntityPredicateExpression(),
                join.biEntityProjectionExpression(),
                join.sortExpressions(),
                join.joinType().name(),
                callSite.isCountQuery(),
                callSite.isSelectJoinedQuery(),
                callSite.isJoinProjectionQuery());
    }
}
