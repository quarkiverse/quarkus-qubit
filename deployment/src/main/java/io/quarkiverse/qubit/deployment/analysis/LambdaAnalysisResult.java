package io.quarkiverse.qubit.deployment.analysis;

import io.quarkiverse.qubit.deployment.ast.LambdaExpression;
import io.quarkiverse.qubit.SortDirection;

import java.util.List;

/**
 * Result of lambda bytecode analysis - sealed interface with specialized result types.
 * <p>
 * Each query type has its own result record with only the relevant fields:
 * <ul>
 *   <li>{@link SimpleQueryResult}: where, select, combined, sorting-only queries</li>
 *   <li>{@link AggregationQueryResult}: min, max, avg, sum* queries</li>
 *   <li>{@link JoinQueryResult}: join, leftJoin with BiQuerySpec</li>
 *   <li>{@link GroupQueryResult}: groupBy with GroupQuerySpec</li>
 * </ul>
 *
 * @see CallSiteProcessor
 */
public sealed interface LambdaAnalysisResult {

    /** Total number of captured variables across all expressions in this result. */
    int totalCapturedVarCount();

    /**
     * Simple queries: where, select, combined, sorting-only.
     * Supports basic filtering, projection, and sorting.
     */
    record SimpleQueryResult(
            LambdaExpression predicateExpression,
            LambdaExpression projectionExpression,
            List<SortExpression> sortExpressions,
            int totalCapturedVarCount
    ) implements LambdaAnalysisResult {}

    /**
     * Aggregation queries: min, max, avg, sum*.
     * Aggregation terminals with optional WHERE predicates.
     */
    record AggregationQueryResult(
            LambdaExpression predicateExpression,
            LambdaExpression aggregationExpression,
            String aggregationType,  // "MIN", "MAX", "AVG", "SUM_INTEGER", "SUM_LONG", "SUM_DOUBLE"
            int totalCapturedVarCount
    ) implements LambdaAnalysisResult {}

    /**
     * Join queries: join, leftJoin with BiQuerySpec.
     * Join relationship, bi-entity predicates/projections.
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
     * GROUP BY with having, select, and sort in group context.
     */
    record GroupQueryResult(
            LambdaExpression predicateExpression,  // Pre-grouping WHERE clause
            LambdaExpression groupByKeyExpression,
            LambdaExpression havingExpression,
            LambdaExpression groupSelectExpression,
            List<SortExpression> groupSortExpressions,
            int totalCapturedVarCount
    ) implements LambdaAnalysisResult {}

    /**
     * Sort expression with direction (ascending/descending).
     * Represents analyzed sort key extractor lambda with direction.
     */
    record SortExpression(
            LambdaExpression keyExtractor,
            SortDirection direction) {}
}
