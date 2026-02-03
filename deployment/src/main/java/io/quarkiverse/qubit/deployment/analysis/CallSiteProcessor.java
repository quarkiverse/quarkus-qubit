package io.quarkiverse.qubit.deployment.analysis;

import static io.quarkiverse.qubit.deployment.analysis.CapturedVariableHelper.countCapturedVariables;
import static io.quarkiverse.qubit.deployment.analysis.CapturedVariableHelper.countCapturedVariablesInSortExpressions;
import static io.quarkiverse.qubit.deployment.analysis.CapturedVariableHelper.validateCapturedVariableIndices;
import static io.quarkiverse.qubit.runtime.internal.QubitConstants.HASH_CHARS_FOR_CLASS_NAME;
import static io.quarkiverse.qubit.runtime.internal.QubitConstants.HASH_CHARS_FOR_LOG;
import static io.quarkiverse.qubit.runtime.internal.QubitConstants.QUERY_TYPE_COMBINED;
import static io.quarkiverse.qubit.runtime.internal.QubitConstants.QUERY_TYPE_COUNT;
import static io.quarkiverse.qubit.runtime.internal.QubitConstants.QUERY_TYPE_LIST;
import static io.quarkiverse.qubit.runtime.internal.QubitConstants.QUERY_TYPE_PROJECTION;

import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.builditem.ApplicationArchivesBuildItem;
import io.quarkus.deployment.builditem.GeneratedClassBuildItem;
import io.quarkiverse.qubit.deployment.analysis.handler.QueryAnalysisContext;
import io.quarkiverse.qubit.deployment.analysis.handler.QueryTypeHandler;
import io.quarkiverse.qubit.deployment.analysis.handler.QueryTypeHandlerRegistry;
import io.quarkiverse.qubit.deployment.analysis.LambdaDeduplicator.DeduplicationContext;
import io.quarkiverse.qubit.deployment.analysis.LambdaDeduplicator.DeduplicationRequest;
import io.quarkiverse.qubit.deployment.ast.LambdaExpression;
import io.quarkiverse.qubit.deployment.common.BytecodeAnalysisException;
import io.quarkiverse.qubit.deployment.metrics.BuildMetricsCollector;
import io.quarkiverse.qubit.deployment.QubitBuildTimeConfig;
import io.quarkiverse.qubit.deployment.QubitProcessor;
import io.quarkiverse.qubit.deployment.analysis.LambdaAnalysisResult.SortExpression;
import io.quarkiverse.qubit.deployment.generation.QueryExecutorClassGenerator;
import io.quarkiverse.qubit.deployment.util.BytecodeLoader;
import io.quarkiverse.qubit.deployment.util.DescriptorParser;
import io.quarkus.logging.Log;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Processes lambda call sites: analyzes bytecode, deduplicates, generates executors.
 */
public class CallSiteProcessor {

    /** Default package for generated executor classes. */
    private static final String DEFAULT_GENERATED_PACKAGE = "io.quarkiverse.qubit.generated";

    private final LambdaBytecodeAnalyzer bytecodeAnalyzer;
    private final LambdaDeduplicator deduplicator;
    private final QueryExecutorClassGenerator classGenerator;
    private final String classNamePrefix;
    private final String targetPackage;
    private final BuildMetricsCollector metricsCollector;

    /**
     * Creates a CallSiteProcessor with default configuration.
     * Used for backward compatibility.
     */
    public CallSiteProcessor(
            LambdaBytecodeAnalyzer bytecodeAnalyzer,
            LambdaDeduplicator deduplicator,
            QueryExecutorClassGenerator classGenerator) {
        this(bytecodeAnalyzer, deduplicator, classGenerator, "QueryExecutor_", DEFAULT_GENERATED_PACKAGE, null);
    }

    /**
     * Creates a CallSiteProcessor with generation configuration.
     */
    public CallSiteProcessor(
            LambdaBytecodeAnalyzer bytecodeAnalyzer,
            LambdaDeduplicator deduplicator,
            QueryExecutorClassGenerator classGenerator,
            QubitBuildTimeConfig.GenerationConfig generationConfig) {
        this(bytecodeAnalyzer, deduplicator, classGenerator,
                generationConfig.classNamePrefix(),
                generationConfig.targetPackage().orElse(DEFAULT_GENERATED_PACKAGE),
                null);
    }

    /**
     * Creates a CallSiteProcessor with generation configuration and metrics collector.
     */
    public CallSiteProcessor(
            LambdaBytecodeAnalyzer bytecodeAnalyzer,
            LambdaDeduplicator deduplicator,
            QueryExecutorClassGenerator classGenerator,
            QubitBuildTimeConfig.GenerationConfig generationConfig,
            BuildMetricsCollector metricsCollector) {
        this(bytecodeAnalyzer, deduplicator, classGenerator,
                generationConfig.classNamePrefix(),
                generationConfig.targetPackage().orElse(DEFAULT_GENERATED_PACKAGE),
                metricsCollector);
    }

    /**
     * Creates a CallSiteProcessor with explicit class naming parameters.
     */
    public CallSiteProcessor(
            LambdaBytecodeAnalyzer bytecodeAnalyzer,
            LambdaDeduplicator deduplicator,
            QueryExecutorClassGenerator classGenerator,
            String classNamePrefix,
            String targetPackage) {
        this(bytecodeAnalyzer, deduplicator, classGenerator, classNamePrefix, targetPackage, null);
    }

    /**
     * Creates a CallSiteProcessor with explicit class naming parameters and metrics collector.
     */
    public CallSiteProcessor(
            LambdaBytecodeAnalyzer bytecodeAnalyzer,
            LambdaDeduplicator deduplicator,
            QueryExecutorClassGenerator classGenerator,
            String classNamePrefix,
            String targetPackage,
            BuildMetricsCollector metricsCollector) {
        this.bytecodeAnalyzer = bytecodeAnalyzer;
        this.deduplicator = deduplicator;
        this.classGenerator = classGenerator;
        this.classNamePrefix = classNamePrefix;
        this.targetPackage = targetPackage;
        this.metricsCollector = metricsCollector;
    }

    /**
     * Processes a lambda call site using Strategy pattern via {@link QueryTypeHandler}.
     * Returns explicit {@link AnalysisOutcome} (Success/UnsupportedPattern/AnalysisError).
     */
    public AnalysisOutcome processCallSiteWithHandlers(
            InvokeDynamicScanner.LambdaCallSite callSite,
            CallSiteProcessingContext processingContext) {

        // Load bytecode
        byte[] classBytes;
        try {
            classBytes = BytecodeLoader.loadClassBytecode(callSite.ownerClassName(),
                    processingContext.applicationArchives(), metricsCollector);
        } catch (BytecodeAnalysisException e) {
            Log.warnf("Could not load bytecode for class: %s - %s", callSite.ownerClassName(), e.getMessage());
            return AnalysisOutcome.unsupported(e.getMessage(), callSite.getCallSiteId());
        }

        String callSiteId = callSite.getCallSiteId();

        if (processingContext.loggingConfig().logBytecodeAnalysis()) {
            Log.debugf("Qubit: Analyzing bytecode for call site: %s", callSiteId);
        }

        // Get handler for this query type
        QueryTypeHandlerRegistry registry = QueryTypeHandlerRegistry.getDefault();
        QueryTypeHandler handler = registry.handlerFor(callSite);

        if (processingContext.loggingConfig().logBytecodeAnalysis()) {
            Log.debugf("Using %s handler for call site: %s", handler.queryTypeName(), callSiteId);
        }

        // Create analysis context with shared deduplicator and metrics collector
        QueryAnalysisContext context = QueryAnalysisContext.of(classBytes, callSite, bytecodeAnalyzer, deduplicator, metricsCollector);

        // Compute bytecode signature once for both early deduplication check and later registration
        long earlyDeduplicationStartTime = System.nanoTime();
        String bytecodeSignature = deduplicator.computeBytecodeSignature(callSite);

        // Check for early deduplication hit
        LambdaDeduplicator.CachedAnalysisResult cachedResult = deduplicator.getCachedResult(bytecodeSignature);
        if (metricsCollector != null) {
            metricsCollector.addEarlyDeduplicationCheckTime(System.nanoTime() - earlyDeduplicationStartTime);
        }

        if (cachedResult != null) {
            // Early deduplication hit: skip analysis and reuse existing executor
            if (processingContext.loggingConfig().logDeduplication()) {
                Log.debugf("Early deduplication hit for call site %s (reusing %s)",
                        callSiteId, cachedResult.executorClassName());
            }
            if (metricsCollector != null) {
                metricsCollector.incrementEarlyDeduplicationHits();
                metricsCollector.incrementDuplicateCount();
            }
            processingContext.deduplicatedCount().incrementAndGet();
            registerEarlyDeduplicatedQuery(callSite, cachedResult.executorClassName(),
                    processingContext.queryTransformations(), processingContext.loggingConfig());
            return AnalysisOutcome.earlyDeduplicated(callSiteId, cachedResult.lambdaHash(),
                    cachedResult.executorClassName());
        }

        // Analyze using the handler
        AnalysisOutcome outcome = handler.analyze(context);

        // Handle the outcome, passing bytecode signature for registration
        return handleAnalysisOutcome(outcome, callSite, callSiteId, bytecodeSignature, processingContext);
    }

    /** Handles analysis outcome after handler execution. */
    private AnalysisOutcome handleAnalysisOutcome(
            AnalysisOutcome outcome,
            InvokeDynamicScanner.LambdaCallSite callSite,
            String callSiteId,
            String bytecodeSignature,
            CallSiteProcessingContext ctx) {

        return switch (outcome) {
            case AnalysisOutcome.Success success ->
                    handleSuccessOutcome(success, callSite, callSiteId, bytecodeSignature, ctx);

            case AnalysisOutcome.UnsupportedPattern unsupported -> {
                Log.debugf("Skipping unsupported pattern at %s: %s",
                        unsupported.callSiteId(), unsupported.reason());
                yield unsupported;
            }

            case AnalysisOutcome.AnalysisError error -> {
                if (ctx.failOnAnalysisError()) {
                    throw BytecodeAnalysisException.analysisError(error.formattedMessage(), error.cause());
                }
                Log.errorf(error.cause(), "Analysis error at %s: %s",
                        error.callSiteId(), error.context());
                yield error;
            }

            case AnalysisOutcome.EarlyDeduplicated earlyDedup -> {
                // This case shouldn't occur here since early deduplication is checked before analysis
                // But we need to handle it for exhaustiveness
                Log.warnf("Unexpected EarlyDeduplicated outcome from handler at %s", earlyDedup.callSiteId());
                yield earlyDedup;
            }
        };
    }

    /** Handles successful analysis: checks deduplication and generates executor. */
    private AnalysisOutcome handleSuccessOutcome(
            AnalysisOutcome.Success success,
            InvokeDynamicScanner.LambdaCallSite callSite,
            String callSiteId,
            String bytecodeSignature,
            CallSiteProcessingContext ctx) {

        // Check for deduplication first
        LambdaAnalysis analysis = new LambdaAnalysis(
                success.result(), success.callSiteId(), success.lambdaHash());

        long deduplicationStartTime = System.nanoTime();
        boolean isDuplicate;
        try {
            isDuplicate = checkAndHandleDuplicate(analysis, callSite, ctx.deduplicatedCount(),
                    ctx.queryTransformations(), ctx.loggingConfig());
        } finally {
            if (metricsCollector != null) {
                metricsCollector.addDeduplicationCheckTime(System.nanoTime() - deduplicationStartTime);
            }
        }

        if (isDuplicate) {
            if (metricsCollector != null) {
                metricsCollector.incrementDuplicateCount();
            }
            return success;  // Deduplicated, no generation needed
        }

        // Build executor class name
        String executorClassName = targetPackage + "." + classNamePrefix
                + success.lambdaHash().substring(0, HASH_CHARS_FOR_CLASS_NAME);

        // Atomic registration BEFORE generation - prevents duplicate class generation in parallel processing
        String existingExecutor = deduplicator.registerExecutor(success.lambdaHash(), executorClassName);
        if (existingExecutor != null) {
            // Another thread already registered this lambda hash - treat as late dedup hit
            if (metricsCollector != null) {
                metricsCollector.incrementDuplicateCount();
            }
            ctx.deduplicatedCount().incrementAndGet();
            // Still need to register for this call site's runtime lookup
            deduplicator.registerBytecodeSignature(bytecodeSignature, success.lambdaHash(), existingExecutor);
            registerEarlyDeduplicatedQuery(callSite, existingExecutor, ctx.queryTransformations(), ctx.loggingConfig());
            return success;
        }

        // We won the race - generate the executor
        try {
            generateExecutorFromResult(success.result(), success.lambdaHash(), callSite,
                    ctx.generatedClass(), ctx.queryTransformations(), ctx.loggingConfig());

            // Register bytecode signature for early deduplication
            deduplicator.registerBytecodeSignature(bytecodeSignature, success.lambdaHash(), executorClassName);
            ctx.generatedCount().incrementAndGet();

            if (ctx.loggingConfig().logGeneratedClasses()) {
                Log.debugf("Generated executor for call site %s (hash: %s)",
                        success.callSiteId(), success.lambdaHash().substring(0, HASH_CHARS_FOR_LOG));
            }
        } catch (Exception e) {
            Log.errorf(e, "Failed to generate executor for call site: %s", callSiteId);
            return AnalysisOutcome.error(e, callSiteId);
        }

        return success;
    }

    /**
     * Generates executor from analysis result using existing generation logic.
     */
    private void generateExecutorFromResult(
            LambdaAnalysisResult result,
            String lambdaHash,
            InvokeDynamicScanner.LambdaCallSite callSite,
            BuildProducer<GeneratedClassBuildItem> generatedClass,
            BuildProducer<QubitProcessor.QueryTransformationBuildItem> queryTransformations,
            QubitBuildTimeConfig.LoggingConfig loggingConfig) {

        String callSiteId = callSite.getCallSiteId();

        // Delegate to generation methods based on result type
        switch (result) {
            case LambdaAnalysisResult.GroupQueryResult group -> {
                ExecutorRegistrationContext ctx = new ExecutorRegistrationContext(
                        lambdaHash, callSiteId,
                        DescriptorParser.getEntityClassName(callSite.groupByLambdaDescriptor()),
                        callSite.targetMethodName(),
                        callSite.isCountQuery(), callSite.hasDistinct(),
                        callSite.skipValue(), callSite.limitValue(),
                        generatedClass, queryTransformations, loggingConfig);
                generateAndRegisterGroupExecutor(group, callSite, ctx);
            }

            case LambdaAnalysisResult.JoinQueryResult join -> {
                ExecutorRegistrationContext ctx = new ExecutorRegistrationContext(
                        lambdaHash, callSiteId,
                        DescriptorParser.getEntityClassName(callSite.joinRelationshipLambdaDescriptor()),
                        callSite.targetMethodName(),
                        callSite.isCountQuery(), callSite.hasDistinct(),
                        callSite.skipValue(), callSite.limitValue(),
                        generatedClass, queryTransformations, loggingConfig);
                generateAndRegisterJoinExecutor(join, callSite.isSelectJoinedQuery(),
                        callSite.isJoinProjectionQuery(), ctx);
            }

            case LambdaAnalysisResult.AggregationQueryResult agg -> {
                ExecutorRegistrationContext ctx = new ExecutorRegistrationContext(
                        lambdaHash, callSiteId,
                        DescriptorParser.getEntityClassName(callSite.lambdaMethodDescriptor()),
                        callSite.targetMethodName(),
                        callSite.isCountQuery(), callSite.hasDistinct(),
                        callSite.skipValue(), callSite.limitValue(),
                        generatedClass, queryTransformations, loggingConfig);
                generateAndRegisterSimpleExecutor(agg.predicateExpression(), null,
                        List.of(), agg.aggregationExpression(), agg.aggregationType(),
                        true, ctx);
            }

            case LambdaAnalysisResult.SimpleQueryResult simple -> {
                ExecutorRegistrationContext ctx = new ExecutorRegistrationContext(
                        lambdaHash, callSiteId,
                        DescriptorParser.getEntityClassName(callSite.lambdaMethodDescriptor()),
                        callSite.targetMethodName(),
                        callSite.isCountQuery(), callSite.hasDistinct(),
                        callSite.skipValue(), callSite.limitValue(),
                        generatedClass, queryTransformations, loggingConfig);
                generateAndRegisterSimpleExecutor(simple.predicateExpression(),
                        simple.projectionExpression(), simple.sortExpressions(),
                        null, null, false, ctx);
            }
        }
    }

    /**
     * Registers a duplicate call site that was detected by pre-grouping by bytecode signature.
     * This method is called for call sites that have the same signature as an already-processed representative.
     *
     * @param duplicate the duplicate call site to register
     * @param representative the already-processed call site with the same signature
     * @param ctx the processing context
     * @return true if registration succeeded, false if fallback to full analysis is needed
     */
    public boolean registerEarlyDeduplicated(
            InvokeDynamicScanner.LambdaCallSite duplicate,
            InvokeDynamicScanner.LambdaCallSite representative,
            CallSiteProcessingContext ctx) {

        // Get the executor class name from the representative's signature
        String bytecodeSignature = deduplicator.computeBytecodeSignature(representative);
        LambdaDeduplicator.CachedAnalysisResult cachedResult = deduplicator.getCachedResult(bytecodeSignature);

        if (cachedResult == null) {
            // Representative failed analysis - caller should process duplicate independently
            Log.debugf("Early deduplication: no cached result for representative %s, will process duplicate independently",
                    representative.getCallSiteId());
            return false;
        }

        String executorClassName = cachedResult.executorClassName();
        ctx.deduplicatedCount().incrementAndGet();

        registerEarlyDeduplicatedQuery(duplicate, executorClassName,
                ctx.queryTransformations(), ctx.loggingConfig());

        if (ctx.loggingConfig().logDeduplication()) {
            Log.debugf("Early pre-grouped deduplication: %s -> %s (same as %s)",
                    duplicate.getCallSiteId(), executorClassName, representative.getCallSiteId());
        }
        return true;
    }

    /**
     * Registers a query transformation for an early-deduplicated call site.
     * Uses minimal QueryCharacteristics since we don't have the full analysis result.
     */
    private void registerEarlyDeduplicatedQuery(
            InvokeDynamicScanner.LambdaCallSite callSite,
            String executorClassName,
            BuildProducer<QubitProcessor.QueryTransformationBuildItem> queryTransformations,
            QubitBuildTimeConfig.LoggingConfig loggingConfig) {

        String callSiteId = callSite.getCallSiteId();

        // Extract entity class name from call site
        String entityClassName = DescriptorParser.getEntityClassName(getEntityDescriptor(callSite));

        // Build characteristics from call site flags
        QueryCharacteristics characteristics = QueryCharacteristics.fromCallSite(callSite, false);

        // Produce the build item for registry registration
        queryTransformations.produce(
                QubitProcessor.QueryTransformationBuildItem.builder()
                        .queryId(callSiteId)
                        .generatedClassName(executorClassName)
                        .entityClassName(entityClassName)
                        .characteristics(characteristics)
                        .capturedVarCount(0) // Unknown for early deduplication
                        .terminalMethodName(callSite.targetMethodName())
                        .build());

        if (loggingConfig.logDeduplication()) {
            Log.debugf("Registered early-deduplicated query: %s -> %s", callSiteId, executorClassName);
        }
    }

    /** Returns true if lambda was deduplicated (existing executor reused). */
    private boolean checkAndHandleDuplicate(
            LambdaAnalysis analysis,
            InvokeDynamicScanner.LambdaCallSite callSite,
            AtomicInteger deduplicatedCount,
            BuildProducer<QubitProcessor.QueryTransformationBuildItem> queryTransformations,
            QubitBuildTimeConfig.LoggingConfig loggingConfig) {

        boolean isGroupQuery = analysis.result() instanceof LambdaAnalysisResult.GroupQueryResult;

        QueryCharacteristics characteristics = QueryCharacteristics.fromCallSite(callSite, isGroupQuery);

        // Extract entity class name and expressions from the analysis result for DevUI display
        String entityClassName = extractEntityClassName(callSite, analysis.result());
        LambdaExpression predicateExpr = extractPredicateExpression(analysis.result());
        LambdaExpression projectionExpr = extractProjectionExpression(analysis.result());

        // Extract sort expression for DevUI display (first sort expression if any)
        List<SortExpression> sortExprs = extractSortExpressions(analysis.result());
        SortDisplayInfo sortInfo = extractSortDisplayInfo(sortExprs);

        // Use parameter objects for cleaner method invocation
        DeduplicationRequest request = new DeduplicationRequest(
                analysis.callSiteId(),
                analysis.lambdaHash(),
                characteristics,
                analysis.result().totalCapturedVarCount(),
                entityClassName,
                predicateExpr,
                projectionExpr,
                sortInfo.sortKey(),
                callSite.targetMethodName(),
                sortInfo.descending());

        DeduplicationContext context = new DeduplicationContext(
                deduplicatedCount,
                queryTransformations,
                loggingConfig.logDeduplication());

        return deduplicator.handleDuplicateLambda(request, context);
    }

    /**
     * Extracts the entity class name from the call site based on query type.
     */
    private String extractEntityClassName(InvokeDynamicScanner.LambdaCallSite callSite, LambdaAnalysisResult result) {
        return switch (result) {
            case LambdaAnalysisResult.GroupQueryResult _ ->
                    DescriptorParser.getEntityClassName(callSite.groupByLambdaDescriptor());
            case LambdaAnalysisResult.JoinQueryResult _ ->
                    DescriptorParser.getEntityClassName(callSite.joinRelationshipLambdaDescriptor());
            case LambdaAnalysisResult.AggregationQueryResult _ ->
                    DescriptorParser.getEntityClassName(getEntityDescriptor(callSite));
            case LambdaAnalysisResult.SimpleQueryResult _ ->
                    DescriptorParser.getEntityClassName(getEntityDescriptor(callSite));
        };
    }

    /**
     * Gets the best available descriptor for entity class extraction.
     * Prefers predicate/projection descriptor, falls back to sort lambda descriptor.
     */
    private String getEntityDescriptor(InvokeDynamicScanner.LambdaCallSite callSite) {
        // Try primary lambda descriptor first
        if (callSite.lambdaMethodDescriptor() != null) {
            return callSite.lambdaMethodDescriptor();
        }
        // Fall back to first sort lambda descriptor if available
        var sortLambdas = callSite.sortLambdas();
        if (sortLambdas != null && !sortLambdas.isEmpty()) {
            return sortLambdas.getFirst().descriptor();
        }
        // Final fallback
        return null;
    }

    /**
     * Extracts the predicate expression from the analysis result.
     */
    private LambdaExpression extractPredicateExpression(LambdaAnalysisResult result) {
        return switch (result) {
            case LambdaAnalysisResult.GroupQueryResult group -> group.predicateExpression();
            case LambdaAnalysisResult.JoinQueryResult join -> join.biEntityPredicateExpression();
            case LambdaAnalysisResult.AggregationQueryResult agg -> agg.predicateExpression();
            case LambdaAnalysisResult.SimpleQueryResult simple -> simple.predicateExpression();
        };
    }

    /**
     * Extracts the projection expression from the analysis result.
     * Returns null for sorting-only queries (sort is displayed separately in DevUI).
     */
    private LambdaExpression extractProjectionExpression(LambdaAnalysisResult result) {
        return switch (result) {
            case LambdaAnalysisResult.GroupQueryResult group -> group.groupSelectExpression();
            case LambdaAnalysisResult.JoinQueryResult join -> join.biEntityProjectionExpression();
            case LambdaAnalysisResult.AggregationQueryResult agg -> agg.aggregationExpression();
            case LambdaAnalysisResult.SimpleQueryResult simple -> simple.projectionExpression();
        };
    }

    /**
     * Extracts the sort expressions from the analysis result.
     * Returns null for queries without sorting.
     */
    private List<SortExpression> extractSortExpressions(LambdaAnalysisResult result) {
        return switch (result) {
            case LambdaAnalysisResult.GroupQueryResult group -> group.groupSortExpressions();
            case LambdaAnalysisResult.JoinQueryResult join -> join.sortExpressions();
            case LambdaAnalysisResult.AggregationQueryResult _ -> null;
            case LambdaAnalysisResult.SimpleQueryResult simple -> simple.sortExpressions();
        };
    }

    /** Generates and registers executor with predicate, projection, sort, and aggregation expressions. */
    private String generateAndRegisterSimpleExecutor(
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
    private String generateAndRegisterJoinExecutor(
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
            String joinTypeDesc = (join.joinType() == InvokeDynamicScanner.JoinType.LEFT)
                    ? "LEFT JOIN" : "INNER JOIN";
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
    private String generateAndRegisterGroupExecutor(
            LambdaAnalysisResult.GroupQueryResult group,
            InvokeDynamicScanner.LambdaCallSite callSite,
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

        // Use the explicit isGroupSelectKey flag from the scanner instead of heuristic
        boolean isSelectKey = callSite.isGroupSelectKey();

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
    private String getQueryTypeDescription(
            LambdaExpression predicateExpression,
            LambdaExpression projectionExpression,
            List<SortExpression> sortExpressions,
            String aggregationType,
            boolean isCountQuery,
            boolean isAggregationQuery) {

        // Aggregation queries have priority in description
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

        // Append sorting info if present
        if (sortExpressions != null && !sortExpressions.isEmpty()) {
            typeDesc += "+SORT(" + sortExpressions.size() + ")";
        }

        return typeDesc;
    }

    /** Extracts first sort expression for DevUI display. */
    private static SortDisplayInfo extractSortDisplayInfo(List<SortExpression> sortExpressions) {
        if (sortExpressions == null || sortExpressions.isEmpty()) {
            return new SortDisplayInfo(null, false);
        }
        SortExpression first = sortExpressions.getFirst();
        boolean descending = first.direction() == io.quarkiverse.qubit.SortDirection.DESCENDING;
        return new SortDisplayInfo(first.keyExtractor(), descending);
    }

    /** First sort expression for DevUI display. */
    private record SortDisplayInfo(LambdaExpression sortKey, boolean descending) {}

    /** Groups analysis result, call site ID, and lambda hash. */
    private record LambdaAnalysis(
            LambdaAnalysisResult result,
            String callSiteId,
            String lambdaHash) {}

    /** Consolidates executor generation parameters into a single context object. */
    private record ExecutorRegistrationContext(
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

    /** Consolidates call site processing parameters into a single context object. */
    public record CallSiteProcessingContext(
            ApplicationArchivesBuildItem applicationArchives,
            AtomicInteger generatedCount,
            AtomicInteger deduplicatedCount,
            BuildProducer<GeneratedClassBuildItem> generatedClass,
            BuildProducer<QubitProcessor.QueryTransformationBuildItem> queryTransformations,
            QubitBuildTimeConfig.LoggingConfig loggingConfig,
            boolean failOnAnalysisError) {
    }

}
