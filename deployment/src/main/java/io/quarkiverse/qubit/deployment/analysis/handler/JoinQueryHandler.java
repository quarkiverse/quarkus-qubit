package io.quarkiverse.qubit.deployment.analysis.handler;

import java.util.List;

import io.quarkiverse.qubit.deployment.analysis.AnalysisOutcome;
import io.quarkiverse.qubit.deployment.analysis.CallSite;
import io.quarkiverse.qubit.deployment.analysis.CapturedVariableHelper;
import io.quarkiverse.qubit.deployment.analysis.LambdaAnalysisResult;
import io.quarkiverse.qubit.deployment.analysis.LambdaAnalysisResult.JoinQueryResult;
import io.quarkiverse.qubit.deployment.analysis.LambdaAnalysisResult.SortExpression;
import io.quarkiverse.qubit.deployment.analysis.LambdaDeduplicator;
import io.quarkiverse.qubit.deployment.ast.LambdaExpression;

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
    public boolean canHandle(CallSite callSite) {
        return callSite instanceof CallSite.JoinCallSite;
    }

    @Override
    public AnalysisOutcome analyze(QueryAnalysisContext context) {
        return safeAnalyze(context, this::doAnalyze);
    }

    private AnalysisOutcome doAnalyze(QueryAnalysisContext context) {
        CallSite.JoinCallSite callSite = (CallSite.JoinCallSite) context.callSite();

        // Analyze join relationship lambda (required)
        LambdaExpression joinRelationshipExpr = analyzeSingleLambda(
                context, callSite.joinRelationshipLambda());

        if (joinRelationshipExpr == null) {
            return unsupportedMissingLambda("join relationship", context.callSiteId());
        }

        // Analyze bi-entity predicate (WHERE on joined entities)
        LambdaExpression biEntityPredicateExpr = analyzeAndCombineLambdas(
                callSite.biEntityPredicateLambdas(),
                pair -> analyzeBiEntityLambda(context, pair),
                "bi-entity predicate",
                context.callSiteId());

        // Analyze bi-entity projection (SELECT on joined entities)
        LambdaExpression biEntityProjectionExpr = analyzeBiEntityLambda(
                context, callSite.biEntityProjectionLambda());

        // Analyze source-only predicates (single-entity WHERE on source)
        LambdaExpression sourcePredicateExpr = analyzeAndCombinePredicates(
                context, callSite.predicateLambdas());

        // Analyze bi-entity sort expressions (join queries use bi-entity lambdas)
        List<SortExpression> sortExpressions = analyzeBiEntitySortLambdas(
                context, callSite.sortLambdas());

        // Renumber captured variables for contiguous indexing across expressions.
        // Counting order must match runtime extraction order in JoinStreamImpl.extractCapturedVariables():
        // biEntityPredicate → sourcePredicate → joinRelationship → biEntityProjection → sorts
        int offset = CapturedVariableHelper.countCapturedVariables(biEntityPredicateExpr);
        sourcePredicateExpr = CapturedVariableHelper.renumberCapturedVariables(sourcePredicateExpr, offset);
        offset += CapturedVariableHelper.countCapturedVariables(sourcePredicateExpr);
        // joinRelationshipExpr is guaranteed non-null (early return above)
        joinRelationshipExpr = CapturedVariableHelper.renumberCapturedVariables(joinRelationshipExpr, offset);
        offset += CapturedVariableHelper.countCapturedVariables(joinRelationshipExpr);
        biEntityProjectionExpr = CapturedVariableHelper.renumberCapturedVariables(biEntityProjectionExpr, offset);
        offset += CapturedVariableHelper.countCapturedVariables(biEntityProjectionExpr);
        sortExpressions = renumberSortExpressions(sortExpressions, offset);

        // Count captured variables (after renumbering)
        int totalCapturedVars = countTotalCapturedVariablesWithSort(
                biEntityPredicateExpr,
                sortExpressions,
                sourcePredicateExpr,
                joinRelationshipExpr,
                biEntityProjectionExpr);

        // Build result
        JoinQueryResult result = new JoinQueryResult(
                joinRelationshipExpr,
                sourcePredicateExpr,
                biEntityPredicateExpr,
                biEntityProjectionExpr,
                sortExpressions,
                callSite.joinType(),
                totalCapturedVars);

        // Compute hash for deduplication using shared deduplicator
        String lambdaHash = context.deduplicator().computeJoinHash(
                new LambdaDeduplicator.JoinHashRequest(
                        joinRelationshipExpr,
                        sourcePredicateExpr,
                        biEntityPredicateExpr,
                        biEntityProjectionExpr,
                        sortExpressions,
                        callSite.joinType().name(),
                        callSite.isCountQuery(),
                        callSite.isSelectJoined(),
                        callSite.isJoinProjectionQuery()));

        return AnalysisOutcome.success(result, context.callSiteId(), lambdaHash);
    }

    @Override
    public String computeHash(
            LambdaDeduplicator deduplicator,
            CallSite callSite,
            LambdaAnalysisResult result) {

        CallSite.JoinCallSite joinCallSite = (CallSite.JoinCallSite) callSite;
        JoinQueryResult join = castResult(result, JoinQueryResult.class);
        return deduplicator.computeJoinHash(
                new LambdaDeduplicator.JoinHashRequest(
                        join.joinRelationshipExpression(),
                        join.sourcePredicateExpression(),
                        join.biEntityPredicateExpression(),
                        join.biEntityProjectionExpression(),
                        join.sortExpressions(),
                        join.joinType().name(),
                        joinCallSite.isCountQuery(),
                        joinCallSite.isSelectJoined(),
                        joinCallSite.isJoinProjectionQuery()));
    }
}
