package io.quarkiverse.qubit.deployment.analysis;

import static io.quarkiverse.qubit.runtime.internal.QubitConstants.HASH_CHARS_FOR_CLASS_NAME;
import static io.quarkiverse.qubit.runtime.internal.QubitConstants.HASH_CHARS_FOR_LOG;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import io.quarkiverse.qubit.deployment.QubitBuildTimeConfig;
import io.quarkiverse.qubit.deployment.QubitProcessor;
import io.quarkiverse.qubit.deployment.analysis.LambdaAnalysisResult.SortExpression;
import io.quarkiverse.qubit.deployment.analysis.LambdaDeduplicator.DeduplicationContext;
import io.quarkiverse.qubit.deployment.analysis.LambdaDeduplicator.DeduplicationRequest;
import io.quarkiverse.qubit.deployment.analysis.handler.QueryAnalysisContext;
import io.quarkiverse.qubit.deployment.analysis.handler.QueryTypeHandler;
import io.quarkiverse.qubit.deployment.analysis.handler.QueryTypeHandlerRegistry;
import io.quarkiverse.qubit.deployment.ast.LambdaExpression;
import io.quarkiverse.qubit.deployment.common.BytecodeAnalysisException;
import io.quarkiverse.qubit.deployment.generation.QueryExecutorClassGenerator;
import io.quarkiverse.qubit.deployment.jfr.QubitAnalysisEvent;
import io.quarkiverse.qubit.deployment.metrics.BuildMetricsCollector;
import io.quarkiverse.qubit.deployment.util.BytecodeLoader;
import io.quarkiverse.qubit.deployment.util.DescriptorParser;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.builditem.ApplicationArchivesBuildItem;
import io.quarkus.deployment.builditem.GeneratedClassBuildItem;
import io.quarkus.logging.Log;

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
    private final ExecutorRegistrationHelper registrationHelper;

    /** Creates a CallSiteProcessor with default configuration. */
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
        this.registrationHelper = new ExecutorRegistrationHelper(classGenerator, classNamePrefix, targetPackage,
                metricsCollector);
    }

    /**
     * Processes a lambda call site using Strategy pattern via {@link QueryTypeHandler}.
     * Returns explicit {@link AnalysisOutcome} (Success/UnsupportedPattern/AnalysisError).
     */
    public AnalysisOutcome processCallSiteWithHandlers(
            CallSite callSite,
            CallSiteProcessingContext processingContext) {

        // Start JFR event for this analysis
        QubitAnalysisEvent jfrEvent = QubitAnalysisEvent.start(
                callSite.getCallSiteId(), callSite.ownerClassName());

        // Load bytecode
        byte[] classBytes;
        try {
            classBytes = BytecodeLoader.loadClassBytecode(callSite.ownerClassName(),
                    processingContext.applicationArchives(), metricsCollector);
        } catch (BytecodeAnalysisException e) {
            Log.warnf("Could not load bytecode for class: %s - %s", callSite.ownerClassName(), e.getMessage());
            jfrEvent.fail(e.getMessage());
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
        QueryAnalysisContext context = QueryAnalysisContext.of(classBytes, callSite, bytecodeAnalyzer, deduplicator,
                metricsCollector);

        // Check early deduplication before expensive analysis
        EarlyDedupResult bytecodeSignature = computeAndCheckEarlyDedup(callSite);
        if (bytecodeSignature.outcome() != null) {
            processingContext.deduplicatedCount().incrementAndGet();
            registerEarlyDeduplicatedQuery(callSite, bytecodeSignature.outcome().executorClassName(),
                    processingContext.queryTransformations(), processingContext.loggingConfig());
            jfrEvent.complete("DEDUPLICATED", true, true, bytecodeSignature.outcome().lambdaHash());
            return AnalysisOutcome.earlyDeduplicated(callSiteId,
                    bytecodeSignature.outcome().lambdaHash(), bytecodeSignature.outcome().executorClassName());
        }

        // Analyze using the handler with timing for query type breakdown
        String queryType = handler.queryTypeName();
        long analysisStartTime = System.nanoTime();
        AnalysisOutcome outcome = handler.analyze(context);
        long analysisTimeNanos = System.nanoTime() - analysisStartTime;

        if (metricsCollector != null) {
            metricsCollector.recordQueryType(queryType);
            metricsCollector.addQueryTypeAnalysisTime(queryType, analysisTimeNanos);
            metricsCollector.addClassAnalysisTime(callSite.ownerClassName(), analysisTimeNanos);
            metricsCollector.addClassLambdaCount(callSite.ownerClassName(), 1);
        }

        // Handle the outcome, passing bytecode signature for registration
        AnalysisOutcome result = handleAnalysisOutcome(outcome, callSite, callSiteId, bytecodeSignature.signature(), queryType,
                processingContext);

        // Complete JFR event based on outcome
        completeJfrEvent(jfrEvent, result, queryType);

        return result;
    }

    /** Completes the JFR analysis event based on the outcome. */
    private void completeJfrEvent(QubitAnalysisEvent event, AnalysisOutcome outcome, String queryType) {
        switch (outcome) {
            case AnalysisOutcome.Success success ->
                event.complete(queryType, true, false, success.lambdaHash());
            case AnalysisOutcome.EarlyDeduplicated dedup ->
                event.complete(queryType, true, true, dedup.lambdaHash());
            case AnalysisOutcome.UnsupportedPattern _ ->
                event.complete(queryType, false, false, null);
            case AnalysisOutcome.AnalysisError _ ->
                event.fail("Analysis error");
        }
    }

    /** Result of early deduplication check: signature is always set, outcome is non-null on cache hit. */
    private record EarlyDedupResult(String signature, LambdaDeduplicator.CachedAnalysisResult outcome) {
    }

    /** Computes bytecode signature and checks early dedup cache. */
    private EarlyDedupResult computeAndCheckEarlyDedup(CallSite callSite) {
        long startTime = System.nanoTime();
        try {
            String signature = deduplicator.computeBytecodeSignature(callSite);
            LambdaDeduplicator.CachedAnalysisResult cached = deduplicator.getCachedResult(signature);
            if (cached != null && metricsCollector != null) {
                metricsCollector.incrementEarlyDeduplicationHits();
                metricsCollector.incrementDuplicateCount();
            }
            return new EarlyDedupResult(signature, cached);
        } finally {
            if (metricsCollector != null) {
                metricsCollector.addEarlyDeduplicationCheckTime(System.nanoTime() - startTime);
            }
        }
    }

    /** Handles analysis outcome after handler execution. */
    private AnalysisOutcome handleAnalysisOutcome(
            AnalysisOutcome outcome,
            CallSite callSite,
            String callSiteId,
            String bytecodeSignature,
            String queryType,
            CallSiteProcessingContext ctx) {

        return switch (outcome) {
            case AnalysisOutcome.Success success ->
                handleSuccessOutcome(success, callSite, callSiteId, bytecodeSignature, queryType, ctx);

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
            CallSite callSite,
            String callSiteId,
            String bytecodeSignature,
            String queryType,
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
            return success; // Deduplicated, no generation needed
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
            long codeGenStartTime = System.nanoTime();
            generateExecutorFromResult(success.result(), success.lambdaHash(), callSite,
                    ctx.generatedClass(), ctx.queryTransformations(), ctx.loggingConfig());
            long codeGenTimeNanos = System.nanoTime() - codeGenStartTime;

            // Record code generation time by query type
            if (metricsCollector != null) {
                metricsCollector.addQueryTypeCodeGenTime(queryType, codeGenTimeNanos);
            }

            // Register bytecode signature for early deduplication
            deduplicator.registerBytecodeSignature(bytecodeSignature, success.lambdaHash(), executorClassName);
            ctx.generatedCount().incrementAndGet();

            if (ctx.loggingConfig().logGeneratedClasses()) {
                Log.debugf("Generated executor for call site %s (hash: %s)",
                        success.callSiteId(), success.lambdaHash().substring(0, HASH_CHARS_FOR_LOG));
            }
        } catch (Exception e) {
            if (metricsCollector != null) {
                metricsCollector.recordFailedClass(callSite.ownerClassName());
            }
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
            CallSite callSite,
            BuildProducer<GeneratedClassBuildItem> generatedClass,
            BuildProducer<QubitProcessor.QueryTransformationBuildItem> queryTransformations,
            QubitBuildTimeConfig.LoggingConfig loggingConfig) {

        String callSiteId = callSite.getCallSiteId();

        // Count expression types for metrics before code generation
        if (metricsCollector != null) {
            registrationHelper.countExpressionTypes(result, metricsCollector);
        }

        // Delegate to generation methods based on result type
        switch (result) {
            case LambdaAnalysisResult.GroupQueryResult group -> {
                CallSite.GroupCallSite g = (CallSite.GroupCallSite) callSite;
                var ctx = new ExecutorRegistrationHelper.ExecutorRegistrationContext(
                        lambdaHash, callSiteId,
                        DescriptorParser.getEntityClassName(g.groupByLambdaDescriptor()),
                        callSite.targetMethodName(),
                        callSite.isCountQuery(), callSite.hasDistinct(),
                        callSite.skipValue(), callSite.limitValue(),
                        generatedClass, queryTransformations, loggingConfig);
                registrationHelper.generateAndRegisterGroupExecutor(group, g.isGroupSelectKey(), ctx);
            }

            case LambdaAnalysisResult.JoinQueryResult join -> {
                CallSite.JoinCallSite j = (CallSite.JoinCallSite) callSite;
                var ctx = new ExecutorRegistrationHelper.ExecutorRegistrationContext(
                        lambdaHash, callSiteId,
                        DescriptorParser.getEntityClassName(j.joinRelationshipLambdaDescriptor()),
                        callSite.targetMethodName(),
                        callSite.isCountQuery(), callSite.hasDistinct(),
                        callSite.skipValue(), callSite.limitValue(),
                        generatedClass, queryTransformations, loggingConfig);
                registrationHelper.generateAndRegisterJoinExecutor(join, j.isSelectJoined(),
                        j.isJoinProjectionQuery(), ctx);
            }

            case LambdaAnalysisResult.AggregationQueryResult agg -> {
                var ctx = new ExecutorRegistrationHelper.ExecutorRegistrationContext(
                        lambdaHash, callSiteId,
                        DescriptorParser.getEntityClassName(getEntityDescriptor(callSite)),
                        callSite.targetMethodName(),
                        callSite.isCountQuery(), callSite.hasDistinct(),
                        callSite.skipValue(), callSite.limitValue(),
                        generatedClass, queryTransformations, loggingConfig);
                registrationHelper.generateAndRegisterSimpleExecutor(agg.predicateExpression(), null,
                        List.of(), agg.aggregationExpression(), agg.aggregationType(),
                        true, ctx);
            }

            case LambdaAnalysisResult.SimpleQueryResult simple -> {
                var ctx = new ExecutorRegistrationHelper.ExecutorRegistrationContext(
                        lambdaHash, callSiteId,
                        DescriptorParser.getEntityClassName(getEntityDescriptor(callSite)),
                        callSite.targetMethodName(),
                        callSite.isCountQuery(), callSite.hasDistinct(),
                        callSite.skipValue(), callSite.limitValue(),
                        generatedClass, queryTransformations, loggingConfig);
                registrationHelper.generateAndRegisterSimpleExecutor(simple.predicateExpression(),
                        simple.projectionExpression(), simple.sortExpressions(),
                        null, null, false, ctx);
            }
        }
    }

    /**
     * Registers a duplicate call site detected by bytecode signature pre-grouping. Returns false if fallback analysis needed.
     */
    public boolean registerEarlyDeduplicated(
            CallSite duplicate,
            CallSite representative,
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
            CallSite callSite,
            String executorClassName,
            BuildProducer<QubitProcessor.QueryTransformationBuildItem> queryTransformations,
            QubitBuildTimeConfig.LoggingConfig loggingConfig) {

        String callSiteId = callSite.getCallSiteId();

        // Extract entity class name from call site
        String entityClassName = DescriptorParser.getEntityClassName(getEntityDescriptor(callSite));

        // Build characteristics from call site flags
        QueryCharacteristics characteristics = QueryCharacteristics.fromCallSite(callSite);

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
            CallSite callSite,
            AtomicInteger deduplicatedCount,
            BuildProducer<QubitProcessor.QueryTransformationBuildItem> queryTransformations,
            QubitBuildTimeConfig.LoggingConfig loggingConfig) {

        QueryCharacteristics characteristics = QueryCharacteristics.fromCallSite(callSite);

        // Extract entity class name and expressions from the analysis result for DevUI display
        String entityClassName = extractEntityClassName(callSite, analysis.result());
        LambdaExpression predicateExpr = extractPredicateExpression(analysis.result());
        LambdaExpression projectionExpr = extractProjectionExpression(analysis.result());

        // Extract sort expression for DevUI display (first sort expression if any)
        List<SortExpression> sortExprs = extractSortExpressions(analysis.result());
        ExecutorRegistrationHelper.SortDisplayInfo sortInfo = ExecutorRegistrationHelper.extractSortDisplayInfo(sortExprs);

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
    private String extractEntityClassName(CallSite callSite, LambdaAnalysisResult result) {
        return switch (result) {
            case LambdaAnalysisResult.GroupQueryResult _ ->
                DescriptorParser.getEntityClassName(((CallSite.GroupCallSite) callSite).groupByLambdaDescriptor());
            case LambdaAnalysisResult.JoinQueryResult _ ->
                DescriptorParser.getEntityClassName(((CallSite.JoinCallSite) callSite).joinRelationshipLambdaDescriptor());
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
    private String getEntityDescriptor(CallSite callSite) {
        return switch (callSite) {
            case CallSite.SimpleCallSite s -> {
                // Try primary lambda descriptor first
                if (s.lambdaMethodDescriptor() != null) {
                    yield s.lambdaMethodDescriptor();
                }
                // Fall back to first sort lambda descriptor if available
                var sortLambdas = s.sortLambdas();
                if (sortLambdas != null && !sortLambdas.isEmpty()) {
                    yield sortLambdas.getFirst().descriptor();
                }
                yield null;
            }
            case CallSite.AggregationCallSite a -> a.aggregationLambdaMethodDescriptor();
            case CallSite.GroupCallSite g -> g.groupByLambdaDescriptor();
            case CallSite.JoinCallSite j -> j.joinRelationshipLambdaDescriptor();
        };
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

    /** Groups analysis result, call site ID, and lambda hash. */
    private record LambdaAnalysis(
            LambdaAnalysisResult result,
            String callSiteId,
            String lambdaHash) {
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
