package io.quarkiverse.qubit.deployment.analysis.handler;

import java.util.List;

import io.quarkiverse.qubit.deployment.analysis.AnalysisOutcome;
import io.quarkiverse.qubit.deployment.analysis.CallSite;
import io.quarkiverse.qubit.deployment.analysis.CapturedVariableHelper;
import io.quarkiverse.qubit.deployment.analysis.LambdaAnalysisResult;
import io.quarkiverse.qubit.deployment.analysis.LambdaAnalysisResult.GroupQueryResult;
import io.quarkiverse.qubit.deployment.analysis.LambdaAnalysisResult.SortExpression;
import io.quarkiverse.qubit.deployment.analysis.LambdaBytecodeAnalyzer;
import io.quarkiverse.qubit.deployment.analysis.LambdaDeduplicator;
import io.quarkiverse.qubit.deployment.ast.LambdaExpression;

/**
 * Handler for GROUP BY queries with HAVING, SELECT, and SORT.
 * Supports groupBy, having (GroupQuerySpec), select, sortedBy, and optional pre-grouping WHERE.
 */
public final class GroupQueryHandler extends AbstractQueryHandler {

    private static final GroupQueryHandler INSTANCE = new GroupQueryHandler();

    private GroupQueryHandler() {
        // Singleton
    }

    /** Returns the singleton instance. */
    public static GroupQueryHandler instance() {
        return INSTANCE;
    }

    @Override
    public String queryTypeName() {
        return "GROUP";
    }

    @Override
    public boolean canHandle(CallSite callSite) {
        return callSite instanceof CallSite.GroupCallSite;
    }

    @Override
    public AnalysisOutcome analyze(QueryAnalysisContext context) {
        return safeAnalyze(context, this::doAnalyze);
    }

    private AnalysisOutcome doAnalyze(QueryAnalysisContext context) {
        CallSite.GroupCallSite callSite = (CallSite.GroupCallSite) context.callSite();

        // Analyze groupBy key lambda (required)
        LambdaExpression groupByKeyExpr = analyzeSingleLambda(
                context,
                callSite.groupByLambdaMethodName(),
                callSite.groupByLambdaDescriptor());

        if (groupByKeyExpr == null) {
            return unsupportedMissingLambda("groupBy key", context.callSiteId());
        }

        // Analyze pre-grouping WHERE predicate
        LambdaExpression predicateExpr = analyzeAndCombinePredicates(
                context, callSite.predicateLambdas());

        // Create group-specific analyzer function
        LambdaBytecodeAnalyzer analyzer = context.bytecodeAnalyzer();
        byte[] classBytes = context.classBytes();

        // Analyze having clause (uses GroupQuerySpec - special mode)
        LambdaExpression havingExpr = analyzeAndCombineLambdas(
                callSite.havingLambdas(),
                pair -> analyzer.analyzeGroupQuerySpec(classBytes, pair.methodName(), pair.descriptor()),
                "group having",
                context.callSiteId());

        // Analyze group select clause (uses GroupQuerySpec)
        LambdaExpression groupSelectExpr = analyzeAndCombineLambdas(
                callSite.groupSelectLambdas(),
                pair -> analyzer.analyzeGroupQuerySpec(classBytes, pair.methodName(), pair.descriptor()),
                "group select",
                context.callSiteId());

        // Analyze group sort expressions
        List<SortExpression> groupSortExpressions = analyzeSortLambdasWithAnalyzer(
                callSite.groupSortLambdas(),
                sl -> analyzer.analyzeGroupQuerySpec(classBytes, sl.methodName(), sl.descriptor()),
                context.callSiteId());

        // Renumber captured variables for contiguous indexing across expressions.
        // Counting order: predicate → groupByKey → having → groupSelect → sorts
        int offset = CapturedVariableHelper.countCapturedVariables(predicateExpr);
        // groupByKeyExpr is guaranteed non-null (early return above)
        groupByKeyExpr = CapturedVariableHelper.renumberCapturedVariables(groupByKeyExpr, offset);
        offset += CapturedVariableHelper.countCapturedVariables(groupByKeyExpr);
        havingExpr = CapturedVariableHelper.renumberCapturedVariables(havingExpr, offset);
        offset += CapturedVariableHelper.countCapturedVariables(havingExpr);
        groupSelectExpr = CapturedVariableHelper.renumberCapturedVariables(groupSelectExpr, offset);
        offset += CapturedVariableHelper.countCapturedVariables(groupSelectExpr);
        groupSortExpressions = renumberSortExpressions(groupSortExpressions, offset);

        // Count captured variables (after renumbering)
        int totalCapturedVars = countGroupCapturedVariables(
                predicateExpr, groupByKeyExpr, havingExpr, groupSelectExpr, groupSortExpressions);

        // Build result
        GroupQueryResult result = new GroupQueryResult(
                predicateExpr,
                groupByKeyExpr,
                havingExpr,
                groupSelectExpr,
                groupSortExpressions,
                totalCapturedVars);

        // Compute hash for deduplication using shared deduplicator
        String lambdaHash = context.deduplicator().computeGroupHash(
                predicateExpr,
                groupByKeyExpr,
                havingExpr,
                groupSelectExpr,
                groupSortExpressions,
                callSite.isCountQuery());

        return AnalysisOutcome.success(result, context.callSiteId(), lambdaHash);
    }

    /** Counts captured variables across all group query expressions. */
    private int countGroupCapturedVariables(
            LambdaExpression predicateExpr,
            LambdaExpression groupByKeyExpr,
            LambdaExpression havingExpr,
            LambdaExpression groupSelectExpr,
            List<SortExpression> groupSortExpressions) {

        int count = countTotalCapturedVariables(predicateExpr, groupByKeyExpr, havingExpr, groupSelectExpr);
        count += CapturedVariableHelper.countCapturedVariablesInSortExpressions(groupSortExpressions);
        return count;
    }

    @Override
    public String computeHash(
            LambdaDeduplicator deduplicator,
            CallSite callSite,
            LambdaAnalysisResult result) {

        GroupQueryResult group = castResult(result, GroupQueryResult.class);
        return deduplicator.computeGroupHash(
                group.predicateExpression(),
                group.groupByKeyExpression(),
                group.havingExpression(),
                group.groupSelectExpression(),
                group.groupSortExpressions(),
                callSite.isCountQuery());
    }
}
