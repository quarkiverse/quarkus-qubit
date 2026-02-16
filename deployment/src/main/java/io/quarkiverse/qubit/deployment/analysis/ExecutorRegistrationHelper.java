package io.quarkiverse.qubit.deployment.analysis;

import static io.quarkiverse.qubit.deployment.analysis.CapturedVariableHelper.countCapturedVariables;
import static io.quarkiverse.qubit.deployment.analysis.CapturedVariableHelper.countCapturedVariablesInSortExpressions;
import static io.quarkiverse.qubit.deployment.analysis.CapturedVariableHelper.validateCapturedVariableIndices;
import static io.quarkiverse.qubit.runtime.internal.QubitConstants.HASH_CHARS_FOR_CLASS_NAME;
import static io.quarkiverse.qubit.runtime.internal.QubitConstants.QUERY_TYPE_COMBINED;
import static io.quarkiverse.qubit.runtime.internal.QubitConstants.QUERY_TYPE_COUNT;
import static io.quarkiverse.qubit.runtime.internal.QubitConstants.QUERY_TYPE_LIST;
import static io.quarkiverse.qubit.runtime.internal.QubitConstants.QUERY_TYPE_PROJECTION;

import java.util.List;

import io.quarkiverse.qubit.deployment.QubitBuildTimeConfig;
import io.quarkiverse.qubit.deployment.QubitProcessor;
import io.quarkiverse.qubit.deployment.analysis.LambdaAnalysisResult.SortExpression;
import io.quarkiverse.qubit.deployment.ast.LambdaExpression;
import io.quarkiverse.qubit.deployment.generation.QueryExecutorClassGenerator;
import io.quarkiverse.qubit.deployment.metrics.BuildMetricsCollector;
import io.quarkiverse.qubit.deployment.metrics.ExpressionTypeCounter;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.builditem.GeneratedClassBuildItem;
import io.quarkus.logging.Log;

/**
 * Generates executor classes and registers query transformation build items.
 * <p>
 * Extracted from {@link CallSiteProcessor} to separate executor generation/registration
 * concerns from call site analysis orchestration.
 */
class ExecutorRegistrationHelper {

    private final QueryExecutorClassGenerator classGenerator;
    private final String classNamePrefix;
    private final String targetPackage;
    private final BuildMetricsCollector metricsCollector;

    ExecutorRegistrationHelper(
            QueryExecutorClassGenerator classGenerator,
            String classNamePrefix,
            String targetPackage,
            BuildMetricsCollector metricsCollector) {
        this.classGenerator = classGenerator;
        this.classNamePrefix = classNamePrefix;
        this.targetPackage = targetPackage;
        this.metricsCollector = metricsCollector;
    }

    /** Generates and registers executor with predicate, projection, sort, and aggregation expressions. */
    String generateAndRegisterSimpleExecutor(
            LambdaExpression predicateExpression,
            LambdaExpression projectionExpression,
            List<SortExpression> sortExpressions,
            LambdaExpression aggregationExpression,
            String aggregationType,
            boolean isAggregationQuery,
            ExecutorRegistrationContext ctx) {

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

        // Build-time validation: ensure all CapturedVariable indices are within bounds
        validateCapturedVariableIndices(capturedVarCount,
                predicateExpression, projectionExpression, aggregationExpression);

        String className = ctx.generateClassName(targetPackage, classNamePrefix);

        byte[] bytecode = classGenerator.generateQueryExecutorClass(
                predicateExpression, projectionExpression, sortExpressions,
                aggregationExpression, aggregationType, className,
                ctx.isCountQuery(), isAggregationQuery);

        ctx.generatedClass().produce(new GeneratedClassBuildItem(true, className, bytecode));

        // Extract first sort expression for DevUI display
        SortDisplayInfo sortInfo = extractSortDisplayInfo(sortExpressions);

        QueryCharacteristics characteristics = new QueryCharacteristics(
                ctx.isCountQuery(), isAggregationQuery, false, false, false, false);
        ctx.queryTransformations().produce(
                QubitProcessor.QueryTransformationBuildItem.builder()
                        .queryId(ctx.queryId())
                        .generatedClassName(className)
                        .entityClassName(ctx.entityClassName())
                        .characteristics(characteristics)
                        .capturedVarCount(capturedVarCount)
                        .predicateExpression(predicateExpression)
                        .projectionExpression(projectionExpression)
                        .sortExpression(sortInfo.sortKey())
                        .aggregationExpression(aggregationExpression)
                        .terminalMethodName(ctx.terminalMethodName())
                        .hasDistinct(ctx.hasDistinct())
                        .sortDescending(sortInfo.descending())
                        .aggregationType(aggregationType)
                        .skipValue(ctx.skipValue())
                        .limitValue(ctx.limitValue())
                        .build());

        if (ctx.loggingConfig().logGeneratedClasses()) {
            String queryTypeDesc = getQueryTypeDescription(predicateExpression, projectionExpression, sortExpressions,
                    aggregationType, ctx.isCountQuery(), isAggregationQuery);
            Log.debugf("Generated query executor: %s (%s, %d captured vars)",
                    className, queryTypeDesc, capturedVarCount);
        }

        return className;
    }

    /** Generates and registers JOIN query executor. */
    String generateAndRegisterJoinExecutor(
            LambdaAnalysisResult.JoinQueryResult join,
            boolean isSelectJoined,
            boolean isJoinProjection,
            ExecutorRegistrationContext ctx) {

        int capturedVarCount = join.totalCapturedVarCount();

        // Build-time validation: ensure all CapturedVariable indices are within bounds
        validateCapturedVariableIndices(capturedVarCount,
                join.joinRelationshipExpression(), join.biEntityPredicateExpression(),
                join.biEntityProjectionExpression());

        String className = ctx.generateClassName(targetPackage, classNamePrefix);

        byte[] bytecode = classGenerator.generateJoinQueryExecutorClass(
                join.joinRelationshipExpression(),
                join.biEntityPredicateExpression(),
                join.biEntityProjectionExpression(),
                join.sortExpressions(),
                join.joinType(),
                className,
                ctx.isCountQuery(),
                isSelectJoined,
                isJoinProjection);

        ctx.generatedClass().produce(new GeneratedClassBuildItem(true, className, bytecode));

        // Extract first sort expression for DevUI display
        SortDisplayInfo sortInfo = extractSortDisplayInfo(join.sortExpressions());

        QueryCharacteristics joinCharacteristics = new QueryCharacteristics(
                ctx.isCountQuery(), false, true, isSelectJoined, isJoinProjection, false);
        ctx.queryTransformations().produce(
                QubitProcessor.QueryTransformationBuildItem.builder()
                        .queryId(ctx.queryId())
                        .generatedClassName(className)
                        .entityClassName(ctx.entityClassName())
                        .characteristics(joinCharacteristics)
                        .capturedVarCount(capturedVarCount)
                        .predicateExpression(join.biEntityPredicateExpression())
                        .projectionExpression(join.biEntityProjectionExpression())
                        .sortExpression(sortInfo.sortKey())
                        .joinRelationshipExpression(join.joinRelationshipExpression())
                        .terminalMethodName(ctx.terminalMethodName())
                        .hasDistinct(ctx.hasDistinct())
                        .sortDescending(sortInfo.descending())
                        .skipValue(ctx.skipValue())
                        .limitValue(ctx.limitValue())
                        .build());

        if (ctx.loggingConfig().logGeneratedClasses()) {
            String joinTypeDesc = (join.joinType() == CallSite.JoinType.LEFT)
                    ? "LEFT JOIN"
                    : "INNER JOIN";
            String queryTypeDesc;
            if (ctx.isCountQuery()) {
                queryTypeDesc = joinTypeDesc + " COUNT";
            } else if (isJoinProjection) {
                queryTypeDesc = joinTypeDesc + " PROJECTION";
            } else if (isSelectJoined) {
                queryTypeDesc = joinTypeDesc + " SELECT JOINED";
            } else {
                queryTypeDesc = joinTypeDesc;
            }
            Log.debugf("Generated join query executor: %s (%s, %d captured vars)",
                    className, queryTypeDesc, capturedVarCount);
        }

        return className;
    }

    /** Generates and registers GROUP BY query executor with HAVING, aggregations, and sorting. */
    String generateAndRegisterGroupExecutor(
            LambdaAnalysisResult.GroupQueryResult group,
            boolean isGroupSelectKey,
            ExecutorRegistrationContext ctx) {

        int capturedVarCount = group.totalCapturedVarCount();

        // Build-time validation: ensure all CapturedVariable indices are within bounds
        validateCapturedVariableIndices(capturedVarCount,
                group.predicateExpression(), group.groupByKeyExpression(),
                group.havingExpression(), group.groupSelectExpression());

        String className = ctx.generateClassName(targetPackage, classNamePrefix);

        byte[] bytecode = classGenerator.generateGroupQueryExecutorClass(
                group.predicateExpression(),
                group.groupByKeyExpression(),
                group.havingExpression(),
                group.groupSelectExpression(),
                group.groupSortExpressions(),
                className,
                ctx.isCountQuery());

        ctx.generatedClass().produce(new GeneratedClassBuildItem(true, className, bytecode));

        // Extract first sort expression for DevUI display
        SortDisplayInfo sortInfo = extractSortDisplayInfo(group.groupSortExpressions());

        QueryCharacteristics groupCharacteristics = ctx.isCountQuery()
                ? QueryCharacteristics.forGroupCount()
                : QueryCharacteristics.forGroupList();

        boolean isSelectKey = isGroupSelectKey;

        ctx.queryTransformations().produce(
                QubitProcessor.QueryTransformationBuildItem.builder()
                        .queryId(ctx.queryId())
                        .generatedClassName(className)
                        .entityClassName(ctx.entityClassName())
                        .characteristics(groupCharacteristics)
                        .capturedVarCount(capturedVarCount)
                        .predicateExpression(group.predicateExpression())
                        .projectionExpression(group.groupSelectExpression())
                        .sortExpression(sortInfo.sortKey())
                        .groupByKeyExpression(group.groupByKeyExpression())
                        .havingExpression(group.havingExpression())
                        .terminalMethodName(ctx.terminalMethodName())
                        .hasDistinct(ctx.hasDistinct())
                        .sortDescending(sortInfo.descending())
                        .isSelectKey(isSelectKey)
                        .skipValue(ctx.skipValue())
                        .limitValue(ctx.limitValue())
                        .build());

        if (ctx.loggingConfig().logGeneratedClasses()) {
            String queryTypeDesc = ctx.isCountQuery() ? "GROUP BY COUNT" : "GROUP BY";
            if (group.havingExpression() != null) {
                queryTypeDesc += "+HAVING";
            }
            if (group.groupSelectExpression() != null) {
                queryTypeDesc += "+SELECT";
            }
            Log.debugf("Generated group query executor: %s (%s, %d captured vars)",
                    className, queryTypeDesc, capturedVarCount);
        }

        return className;
    }

    /** Returns query type: "COUNT", "COMBINED", "PROJECTION", or "LIST". */
    static String getQueryType(boolean isCountQuery, boolean hasPredicate, boolean hasProjection) {
        if (isCountQuery) {
            return QUERY_TYPE_COUNT;
        }
        if (hasPredicate && hasProjection) {
            return QUERY_TYPE_COMBINED;
        }
        if (hasProjection) {
            return QUERY_TYPE_PROJECTION;
        }
        return QUERY_TYPE_LIST;
    }

    /** Returns formatted query type description for logging (includes sorting/aggregation info). */
    String getQueryTypeDescription(
            LambdaExpression predicateExpression,
            LambdaExpression projectionExpression,
            List<SortExpression> sortExpressions,
            String aggregationType,
            boolean isCountQuery,
            boolean isAggregationQuery) {

        // Aggregation queries have priority in description
        if (isAggregationQuery && aggregationType != null) {
            String typeDesc = aggregationType; // "MIN", "MAX", "AVG", etc.
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

        // Append sorting info if present
        if (sortExpressions != null && !sortExpressions.isEmpty()) {
            typeDesc += "+SORT(" + sortExpressions.size() + ")";
        }

        return typeDesc;
    }

    /** Extracts first sort expression for DevUI display. */
    static SortDisplayInfo extractSortDisplayInfo(List<SortExpression> sortExpressions) {
        if (sortExpressions == null || sortExpressions.isEmpty()) {
            return new SortDisplayInfo(null, false);
        }
        SortExpression first = sortExpressions.getFirst();
        boolean descending = first.direction() == io.quarkiverse.qubit.SortDirection.DESCENDING;
        return new SortDisplayInfo(first.keyExtractor(), descending);
    }

    /** First sort expression for DevUI display. */
    record SortDisplayInfo(LambdaExpression sortKey, boolean descending) {
    }

    /** Counts expression types from analysis result for metrics. */
    void countExpressionTypes(LambdaAnalysisResult result, BuildMetricsCollector collector) {
        switch (result) {
            case LambdaAnalysisResult.GroupQueryResult group -> {
                ExpressionTypeCounter.countAndRecord(group.predicateExpression(), collector);
                ExpressionTypeCounter.countAndRecord(group.groupByKeyExpression(), collector);
                ExpressionTypeCounter.countAndRecord(group.havingExpression(), collector);
                ExpressionTypeCounter.countAndRecord(group.groupSelectExpression(), collector);
                countSortExpressionTypes(group.groupSortExpressions(), collector);
            }
            case LambdaAnalysisResult.JoinQueryResult join -> {
                ExpressionTypeCounter.countAndRecord(join.joinRelationshipExpression(), collector);
                ExpressionTypeCounter.countAndRecord(join.biEntityPredicateExpression(), collector);
                ExpressionTypeCounter.countAndRecord(join.biEntityProjectionExpression(), collector);
                countSortExpressionTypes(join.sortExpressions(), collector);
            }
            case LambdaAnalysisResult.AggregationQueryResult agg -> {
                ExpressionTypeCounter.countAndRecord(agg.predicateExpression(), collector);
                ExpressionTypeCounter.countAndRecord(agg.aggregationExpression(), collector);
            }
            case LambdaAnalysisResult.SimpleQueryResult simple -> {
                ExpressionTypeCounter.countAndRecord(simple.predicateExpression(), collector);
                ExpressionTypeCounter.countAndRecord(simple.projectionExpression(), collector);
                countSortExpressionTypes(simple.sortExpressions(), collector);
            }
        }
    }

    /** Counts expression types from sort expressions. */
    void countSortExpressionTypes(List<SortExpression> sortExpressions, BuildMetricsCollector collector) {
        if (sortExpressions != null) {
            for (SortExpression sort : sortExpressions) {
                ExpressionTypeCounter.countAndRecord(sort.keyExtractor(), collector);
            }
        }
    }

    /** Consolidates executor generation parameters into a single context object. */
    record ExecutorRegistrationContext(
            String lambdaHash,
            String queryId,
            String entityClassName,
            String terminalMethodName,
            boolean isCountQuery,
            boolean hasDistinct,
            Integer skipValue,
            Integer limitValue,
            BuildProducer<GeneratedClassBuildItem> generatedClass,
            BuildProducer<QubitProcessor.QueryTransformationBuildItem> queryTransformations,
            QubitBuildTimeConfig.LoggingConfig loggingConfig) {

        /** Generates class name using standard naming convention. */
        String generateClassName(String targetPackage, String classNamePrefix) {
            return targetPackage + "." + classNamePrefix + lambdaHash.substring(0, HASH_CHARS_FOR_CLASS_NAME);
        }
    }
}
