package io.quarkiverse.qubit.deployment.analysis.handler;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import io.quarkiverse.qubit.deployment.analysis.AnalysisOutcome;
import io.quarkiverse.qubit.deployment.analysis.CallSite.LambdaPair;
import io.quarkiverse.qubit.deployment.analysis.CallSite.SortLambda;
import io.quarkiverse.qubit.deployment.analysis.CapturedVariableHelper;
import io.quarkiverse.qubit.deployment.analysis.LambdaAnalysisResult;
import io.quarkiverse.qubit.deployment.analysis.LambdaAnalysisResult.SortExpression;
import io.quarkiverse.qubit.deployment.ast.LambdaExpression;
import io.quarkus.logging.Log;

/**
 * Base class for query handlers with shared lambda analysis utilities.
 */
public abstract sealed class AbstractQueryHandler implements QueryTypeHandler
        permits SimpleQueryHandler, AggregationQueryHandler, JoinQueryHandler, GroupQueryHandler {

    /** Analyzes a single lambda method. */
    protected LambdaExpression analyzeSingleLambda(
            QueryAnalysisContext context,
            String methodName,
            String descriptor) {

        if (methodName == null) {
            return null;
        }

        return context.bytecodeAnalyzer().analyze(
                context.classBytes(),
                methodName,
                descriptor,
                context.metricsCollector());
    }

    /** Analyzes a bi-entity lambda (for join queries with two parameters). */
    protected LambdaExpression analyzeBiEntityLambda(
            QueryAnalysisContext context,
            String methodName,
            String descriptor) {

        if (methodName == null) {
            return null;
        }

        return context.bytecodeAnalyzer().analyzeBiEntity(
                context.classBytes(),
                methodName,
                descriptor,
                context.metricsCollector());
    }

    /** Analyzes multiple predicates and combines with AND, renumbering captured variables. */
    protected LambdaExpression analyzeAndCombinePredicates(
            QueryAnalysisContext context,
            List<LambdaPair> lambdaPairs) {

        return analyzeAndCombineLambdas(
                lambdaPairs,
                pair -> analyzeSingleLambda(context, pair.methodName(), pair.descriptor()),
                "predicate",
                context.callSiteId());
    }

    /** Core method: combines lambdas with AND, handling empty lists and captured variable renumbering. */
    protected LambdaExpression analyzeAndCombineLambdas(
            List<LambdaPair> lambdaPairs,
            Function<LambdaPair, LambdaExpression> analyzer,
            String lambdaTypeForLogging,
            String callSiteId) {

        if (lambdaPairs == null || lambdaPairs.isEmpty()) {
            return null;
        }

        // Fast path for single lambda
        if (lambdaPairs.size() == 1) {
            return analyzer.apply(lambdaPairs.getFirst());
        }

        // Analyze all lambdas and track captured variable counts
        List<LambdaExpression> expressions = new ArrayList<>();
        int cumulativeOffset = 0;

        for (LambdaPair pair : lambdaPairs) {
            LambdaExpression expr = analyzer.apply(pair);
            if (expr == null) {
                Log.warnf("Failed to analyze %s lambda %s at %s",
                        lambdaTypeForLogging, pair.methodName(), callSiteId);
                continue;
            }

            // Renumber captured variables if needed
            if (cumulativeOffset > 0) {
                expr = CapturedVariableHelper.renumberCapturedVariables(expr, cumulativeOffset);
            }

            expressions.add(expr);
            cumulativeOffset += CapturedVariableHelper.countCapturedVariables(expr);
        }

        if (expressions.isEmpty()) {
            return null;
        }

        return CapturedVariableHelper.combinePredicatesWithAnd(expressions);
    }

    /** Analyzes sort lambdas and produces sort expressions. */
    protected List<SortExpression> analyzeSortLambdas(
            QueryAnalysisContext context,
            List<SortLambda> sortLambdas) {

        return analyzeSortLambdasWithAnalyzer(
                sortLambdas,
                sl -> analyzeSingleLambda(context, sl.methodName(), sl.descriptor()),
                context.callSiteId());
    }

    /** Analyzes bi-entity sort lambdas (referencing both root and joined entities). */
    protected List<SortExpression> analyzeBiEntitySortLambdas(
            QueryAnalysisContext context,
            List<SortLambda> sortLambdas) {

        return analyzeSortLambdasWithAnalyzer(
                sortLambdas,
                sl -> analyzeBiEntityLambda(context, sl.methodName(), sl.descriptor()),
                context.callSiteId());
    }

    /** Core method: analyzes sort lambdas with custom analyzer, building SortExpression with direction. */
    protected List<SortExpression> analyzeSortLambdasWithAnalyzer(
            List<SortLambda> sortLambdas,
            Function<SortLambda, LambdaExpression> analyzer,
            String callSiteId) {

        if (sortLambdas == null || sortLambdas.isEmpty()) {
            return List.of();
        }

        List<SortExpression> sortExpressions = new ArrayList<>();

        for (SortLambda sortLambda : sortLambdas) {
            LambdaExpression keyExtractor = analyzer.apply(sortLambda);

            if (keyExtractor == null) {
                Log.warnf("Failed to analyze sort lambda %s at %s",
                        sortLambda.methodName(), callSiteId);
                continue;
            }

            sortExpressions.add(new SortExpression(keyExtractor, sortLambda.direction()));
        }

        return sortExpressions;
    }

    /** Counts captured variables across predicate and other expressions. */
    @SafeVarargs
    protected final int countTotalCapturedVariables(
            LambdaExpression predicateExpr,
            LambdaExpression... otherExpressions) {

        int count = 0;

        if (predicateExpr != null) {
            count += CapturedVariableHelper.countCapturedVariables(predicateExpr);
        }

        for (LambdaExpression expr : otherExpressions) {
            if (expr != null) {
                count += CapturedVariableHelper.countCapturedVariables(expr);
            }
        }

        return count;
    }

    /** Counts captured variables including sort expressions. */
    protected int countTotalCapturedVariablesWithSort(
            LambdaExpression predicateExpr,
            List<SortExpression> sortExpressions,
            LambdaExpression... otherExpressions) {

        int count = countTotalCapturedVariables(predicateExpr, otherExpressions);
        count += CapturedVariableHelper.countCapturedVariablesInSortExpressions(sortExpressions);
        return count;
    }

    /** Renumbers captured variable indices in sort expressions by adding offset. */
    protected static List<SortExpression> renumberSortExpressions(
            List<SortExpression> sortExpressions, int offset) {
        if (offset == 0 || sortExpressions == null || sortExpressions.isEmpty()) {
            return sortExpressions;
        }
        return sortExpressions.stream()
                .map(se -> new SortExpression(
                        CapturedVariableHelper.renumberCapturedVariables(se.keyExtractor(), offset),
                        se.direction()))
                .toList();
    }

    /** Wraps analysis in try-catch, converting exceptions to AnalysisOutcome. */
    protected AnalysisOutcome safeAnalyze(
            QueryAnalysisContext context,
            Function<QueryAnalysisContext, AnalysisOutcome> analysisFunction) {

        try {
            return analysisFunction.apply(context);
        } catch (Exception e) {
            Log.errorf(e, "Exception during %s analysis at %s",
                    queryTypeName(), context.callSiteId());
            return AnalysisOutcome.error(e, context.callSiteId());
        }
    }

    /** Creates UnsupportedPattern outcome for missing lambda. */
    protected AnalysisOutcome unsupportedMissingLambda(String lambdaType, String callSiteId) {
        return AnalysisOutcome.UnsupportedPattern.missingRequiredLambda(lambdaType, callSiteId);
    }

    /** Creates UnsupportedPattern outcome for lambda not found. */
    protected AnalysisOutcome unsupportedLambdaNotFound(String methodName, String callSiteId) {
        return AnalysisOutcome.UnsupportedPattern.lambdaNotFound(methodName, callSiteId);
    }

    /** Casts LambdaAnalysisResult to expected subtype; throws if type mismatch. */
    protected <R extends LambdaAnalysisResult> R castResult(
            LambdaAnalysisResult result,
            Class<R> expectedType) {
        if (!expectedType.isInstance(result)) {
            throw new IllegalArgumentException(
                    "Expected " + expectedType.getSimpleName() + ", got: " + result.getClass());
        }
        return expectedType.cast(result);
    }
}
