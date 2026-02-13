package io.quarkiverse.qubit.deployment;

import static io.quarkiverse.qubit.deployment.common.ExceptionMessages.QUERY_ID_REQUIRED;
import static io.quarkiverse.qubit.runtime.internal.QubitConstants.QUBIT_ENTITY_CLASS_NAME;
import static io.quarkiverse.qubit.runtime.internal.QubitConstants.QUBIT_REPOSITORY_CLASS_NAME;

import java.lang.reflect.Modifier;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.IndexView;

import io.quarkiverse.qubit.QubitEntity;
import io.quarkiverse.qubit.deployment.analysis.CallSiteProcessor;
import io.quarkiverse.qubit.deployment.analysis.InvokeDynamicQuickCheck;
import io.quarkiverse.qubit.deployment.analysis.InvokeDynamicScanner;
import io.quarkiverse.qubit.deployment.analysis.LambdaBytecodeAnalyzer;
import io.quarkiverse.qubit.deployment.analysis.LambdaDeduplicator;
import io.quarkiverse.qubit.deployment.analysis.QueryCharacteristics;
import io.quarkiverse.qubit.deployment.ast.LambdaExpression;
import io.quarkiverse.qubit.deployment.common.BytecodeAnalysisException;
import io.quarkiverse.qubit.deployment.generation.QueryExecutorClassGenerator;
import io.quarkiverse.qubit.deployment.jfr.QubitPhaseEvent;
import io.quarkiverse.qubit.deployment.jfr.QubitScanEvent;
import io.quarkiverse.qubit.deployment.metrics.BuildMetricsCollector;
import io.quarkiverse.qubit.deployment.util.BytecodeLoader;
import io.quarkiverse.qubit.runtime.internal.QueryExecutorRecorder;
import io.quarkiverse.qubit.runtime.internal.QueryExecutorRegistry;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.builder.item.MultiBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.AdditionalIndexedClassesBuildItem;
import io.quarkus.deployment.builditem.ApplicationArchivesBuildItem;
import io.quarkus.deployment.builditem.BytecodeTransformerBuildItem;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.GeneratedClassBuildItem;
import io.quarkus.hibernate.orm.deployment.spi.AdditionalJpaModelBuildItem;
import io.quarkus.hibernate.orm.panache.deployment.PanacheEntityClassBuildItem;
import io.quarkus.logging.Log;

/**
 * Qubit extension build processor. Generates query executor classes at build time from lambda expressions.
 */
public class QubitProcessor {

    private static final String FEATURE = "qubit";

    private final QueryExecutorClassGenerator classGenerator = new QueryExecutorClassGenerator();
    private final LambdaBytecodeAnalyzer bytecodeAnalyzer = new LambdaBytecodeAnalyzer();
    private final LambdaDeduplicator deduplicator = new LambdaDeduplicator();

    /** Registers Qubit feature. Capability declared in runtime pom.xml extension-descriptor. */
    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    /** Registers QueryExecutorRegistry as unremovable bean. */
    @BuildStep
    AdditionalBeanBuildItem registerBeans() {
        return AdditionalBeanBuildItem.unremovableOf(QueryExecutorRegistry.class);
    }

    /** Add QubitEntity to Jandex index for Panache type parameter resolution. */
    @BuildStep
    AdditionalIndexedClassesBuildItem indexQubitEntity() {
        return new AdditionalIndexedClassesBuildItem(QubitEntity.class.getName());
    }

    /** Registers QubitEntity with JPA. */
    @BuildStep
    AdditionalJpaModelBuildItem registerQubitEntityWithJpa() {
        return new AdditionalJpaModelBuildItem(QUBIT_ENTITY_CLASS_NAME, Set.of());
    }

    /** Inform Panache about QubitEntity subclasses for enhancement. */
    @BuildStep
    void collectQubitEntityClasses(
            CombinedIndexBuildItem combinedIndex,
            BuildProducer<PanacheEntityClassBuildItem> panacheEntities) {

        IndexView index = combinedIndex.getIndex();
        DotName qubitEntityName = DotName.createSimple(QUBIT_ENTITY_CLASS_NAME);

        // Find all entities extending QubitEntity
        Collection<ClassInfo> entities = index.getAllKnownSubclasses(qubitEntityName);

        Log.debugf("Qubit: Informing Panache about %d QubitEntity subclasses for enhancement", entities.size());

        for (ClassInfo entity : entities) {
            panacheEntities.produce(new PanacheEntityClassBuildItem(entity));
            Log.tracef("Qubit: Registered %s for Panache enhancement", entity.name());
        }
    }

    /** Enhance QubitEntity subclasses with static query methods (ActiveRecord pattern). */
    @BuildStep
    void enhanceQubitEntities(
            CombinedIndexBuildItem combinedIndex,
            BuildProducer<BytecodeTransformerBuildItem> transformers) {

        IndexView index = combinedIndex.getIndex();
        DotName qubitEntityName = DotName.createSimple(QUBIT_ENTITY_CLASS_NAME);

        // Find all entities extending QubitEntity
        Collection<ClassInfo> entities = index.getAllKnownSubclasses(qubitEntityName);

        Log.debugf("Qubit: Enhancing %d QubitEntity subclasses with lambda-based query methods", entities.size());

        QubitEntityEnhancer enhancer = new QubitEntityEnhancer();

        for (ClassInfo entity : entities) {
            String entityClassName = entity.name().toString();
            Log.tracef("Qubit: Replacing abstract methods in entity: %s", entityClassName);

            transformers.produce(new BytecodeTransformerBuildItem(entityClassName, enhancer));
        }
    }

    /** Enhance QubitRepository implementations with @GenerateBridge methods (Repository pattern). */
    @BuildStep
    void enhanceQubitRepositories(
            CombinedIndexBuildItem combinedIndex,
            BuildProducer<BytecodeTransformerBuildItem> transformers) {

        IndexView index = combinedIndex.getIndex();
        DotName qubitRepositoryName = DotName.createSimple(QUBIT_REPOSITORY_CLASS_NAME);

        Collection<ClassInfo> repositories = index.getAllKnownImplementations(qubitRepositoryName);

        if (repositories.isEmpty()) {
            Log.debugf("Qubit: No QubitRepository implementations found");
            return;
        }

        Log.debugf("Qubit: Enhancing %d QubitRepository implementations with @GenerateBridge methods",
                repositories.size());

        QubitRepositoryEnhancer enhancer = new QubitRepositoryEnhancer(index);

        for (ClassInfo repository : repositories) {
            String repositoryClassName = repository.name().toString();
            Log.tracef("Qubit: Generating bridge methods for repository: %s", repositoryClassName);

            transformers.produce(new BytecodeTransformerBuildItem(repositoryClassName, enhancer));
        }
    }

    /**
     * Scan for lambda call sites and generate query executor classes.
     */
    @BuildStep
    void generateQueryExecutors(
            QubitBuildTimeConfig config,
            CombinedIndexBuildItem combinedIndex,
            ApplicationArchivesBuildItem applicationArchives,
            BuildProducer<GeneratedClassBuildItem> generatedClass,
            BuildProducer<QueryTransformationBuildItem> queryTransformations) {

        // Initialize metrics collector if enabled
        BuildMetricsCollector metricsCollector = config.metrics().enabled()
                ? new BuildMetricsCollector()
                : null;

        // Set metrics collector on class generator for Gizmo2 timing
        classGenerator.setMetricsCollector(metricsCollector);

        // Clear caches for fresh metrics collection (also essential for dev mode hot reload)
        BytecodeLoader.clearCache();
        LambdaBytecodeAnalyzer.clearCache();
        QueryExecutorClassGenerator.clearCache();

        Log.debugf("Qubit: Scanning for lambda call sites using invokedynamic analysis");

        IndexView index = combinedIndex.getIndex();

        // Record entity/repository enhancement counts for metrics
        if (metricsCollector != null) {
            recordEnhancementCounts(index, metricsCollector);
        }
        InvokeDynamicScanner scanner = new InvokeDynamicScanner();

        Collection<ClassInfo> allClasses = index.getKnownClasses();

        Log.debugf("Qubit: Scanning %d classes for lambda call sites", allClasses.size());

        // Phase: Lambda Discovery
        QubitPhaseEvent discoveryEvent = QubitPhaseEvent.start("lambda_discovery");
        if (metricsCollector != null) {
            metricsCollector.startPhase("lambda_discovery");
        }

        List<ClassInfo> filteredClasses = allClasses.stream()
                .filter(classInfo -> isNotExcludedClass(classInfo, config.scanning()))
                .toList();

        Log.infof("Qubit: Filtered to %d application classes (from %d total)",
                filteredClasses.size(), allClasses.size());

        // Log test classes found
        long testClassCount = filteredClasses.stream()
                .filter(c -> isTestClass(c.name().toString()))
                .count();
        if (config.scanning().scanTestClasses()) {
            Log.infof("Qubit: Found %d test classes (scanning enabled)", testClassCount);
        } else {
            Log.debugf("Qubit: Skipped %d test classes (scanning disabled)", testClassCount);
        }

        CallSiteProcessor configuredProcessor = new CallSiteProcessor(
                bytecodeAnalyzer, deduplicator, classGenerator, config.generation(), metricsCollector);

        // Parallel class scanning: safe because InvokeDynamicScanner.scanClass() creates
        // fresh local state per invocation and BytecodeLoader uses ConcurrentHashMap cache
        List<InvokeDynamicScanner.LambdaCallSite> allCallSites = filteredClasses.parallelStream()
                .flatMap(classInfo -> scanClassForCallSites(classInfo, scanner, applicationArchives, config.logging(),
                        metricsCollector).stream())
                .peek(c -> Log.tracef("Qubit: Found callSite %s", c.getCallSiteId()))
                .toList();

        if (metricsCollector != null) {
            metricsCollector.endPhase("lambda_discovery");
        }
        discoveryEvent.complete(allClasses.size(), allCallSites.size(), 0);

        Log.debugf("Qubit: Found %d total lambda call site(s)", allCallSites.size());

        validateUniqueCallSiteIds(allCallSites);

        AtomicInteger generatedCount = new AtomicInteger(0);
        AtomicInteger deduplicatedCount = new AtomicInteger(0);

        // Phase: Cache Warming - pre-load bytecode and ClassNodes for classes with call sites
        // This reduces I/O contention during parallel analysis by warming both caches
        Set<String> classesWithCallSites = allCallSites.stream()
                .map(InvokeDynamicScanner.LambdaCallSite::ownerClassName)
                .collect(Collectors.toSet());

        Log.debugf("Qubit: Warming bytecode and ClassNode caches for %d classes with call sites", classesWithCallSites.size());
        long warmupStart = System.nanoTime();
        classesWithCallSites.forEach(className -> {
            try {
                // Load bytecode (file I/O)
                byte[] bytecode = BytecodeLoader.loadClassBytecode(className, applicationArchives, metricsCollector);
                // Pre-parse ClassNode (ASM parsing) - avoids contention during parallel analysis
                LambdaBytecodeAnalyzer.preloadClassNode(bytecode, metricsCollector);
            } catch (Exception e) {
                // Ignore errors during warmup - will be handled during actual processing
                Log.tracef("Cache warmup skipped for %s: %s", className, e.getMessage());
            }
        });
        long warmupTimeMs = (System.nanoTime() - warmupStart) / 1_000_000;
        Log.debugf("Qubit: Cache warmup completed in %dms for %d classes", warmupTimeMs, classesWithCallSites.size());

        // Phase: Bytecode Analysis and Code Generation
        QubitPhaseEvent analysisEvent = QubitPhaseEvent.start("bytecode_analysis");
        if (metricsCollector != null) {
            metricsCollector.startPhase("bytecode_analysis");
        }

        CallSiteProcessor.CallSiteProcessingContext processingContext = new CallSiteProcessor.CallSiteProcessingContext(
                applicationArchives, generatedCount, deduplicatedCount,
                generatedClass, queryTransformations, config.logging(), true);

        // JIT warm-up: process first call site sequentially to prime Gizmo2 hot paths.
        // Without this, each ForkJoinPool thread pays ~150ms JIT penalty on its first task.
        List<InvokeDynamicScanner.LambdaCallSite> remainingCallSites = jitWarmUpAndGetRemaining(
                allCallSites, configuredProcessor, processingContext, metricsCollector);

        // Parallel processing with JIT-primed code paths
        remainingCallSites.parallelStream()
                .forEach(callSite -> {
                    configuredProcessor.processCallSiteWithHandlers(callSite, processingContext);
                    if (metricsCollector != null) {
                        metricsCollector.incrementQueryCount();
                    }
                });

        if (metricsCollector != null) {
            metricsCollector.endPhase("bytecode_analysis");
        }
        analysisEvent.complete(allCallSites.size(), generatedCount.get(), deduplicatedCount.get());

        Log.infof("Qubit extension initialized - Call sites: %d | Query executors: %d generated, %d deduplicated",
                allCallSites.size(), generatedCount.get(), deduplicatedCount.get());

        // Write metrics reports if enabled
        if (metricsCollector != null) {
            writeMetricsReports(config, metricsCollector);
        }
    }

    /** Processes first call site sequentially to prime JIT, returns remaining for parallel processing. */
    private List<InvokeDynamicScanner.LambdaCallSite> jitWarmUpAndGetRemaining(
            List<InvokeDynamicScanner.LambdaCallSite> allCallSites,
            CallSiteProcessor processor,
            CallSiteProcessor.CallSiteProcessingContext ctx,
            BuildMetricsCollector metricsCollector) {

        if (allCallSites.isEmpty()) {
            return allCallSites;
        }
        InvokeDynamicScanner.LambdaCallSite warmupCallSite = allCallSites.getFirst();
        processor.processCallSiteWithHandlers(warmupCallSite, ctx);
        if (metricsCollector != null) {
            metricsCollector.incrementQueryCount();
        }
        Log.debugf("Qubit: JIT warm-up completed with %s", warmupCallSite.getCallSiteId());
        return allCallSites.subList(1, allCallSites.size());
    }

    /** Records entity and repository counts for enhancement metrics. */
    private void recordEnhancementCounts(IndexView index, BuildMetricsCollector metricsCollector) {
        DotName qubitEntityName = DotName.createSimple(QUBIT_ENTITY_CLASS_NAME);
        DotName qubitRepositoryName = DotName.createSimple(QUBIT_REPOSITORY_CLASS_NAME);

        int entityCount = index.getAllKnownSubclasses(qubitEntityName).size();
        int repositoryCount = index.getAllKnownImplementations(qubitRepositoryName).size();

        for (int i = 0; i < entityCount; i++) {
            metricsCollector.incrementEntityClassesEnhanced();
        }
        for (int i = 0; i < repositoryCount; i++) {
            metricsCollector.incrementRepositoriesEnhanced();
        }
    }

    /** Writes JSON metrics and optionally flame graph output. */
    private void writeMetricsReports(QubitBuildTimeConfig config, BuildMetricsCollector metricsCollector) {
        // Write JSON report
        try {
            Path outputPath = Path.of(config.metrics().outputPath());
            metricsCollector.writeReport(outputPath);
            Log.infof("Qubit: Build metrics written to %s", outputPath);
        } catch (Exception e) {
            Log.warnf(e, "Qubit: Failed to write build metrics");
        }

        // Write flame graph output if enabled
        if (config.metrics().flameGraph()) {
            try {
                Path flameGraphPath = Path.of(config.metrics().flameGraphPath());
                metricsCollector.writeFlameGraph(flameGraphPath);
                Log.infof("Qubit: Flame graph stacks written to %s", flameGraphPath);
            } catch (Exception e) {
                Log.warnf(e, "Qubit: Failed to write flame graph output");
            }
        }
    }

    /** Determines if a class should be included in lambda scanning. */
    private boolean isNotExcludedClass(ClassInfo classInfo, QubitBuildTimeConfig.ScanningConfig scanningConfig) {
        String className = classInfo.name().toString();

        // Always include qubit extension classes (needed for internal functionality)
        if (className.startsWith("io.quarkiverse.qubit.")) {
            return true;
        }

        // Whitelist mode: when includePackages is configured (non-empty), ONLY scan matching packages
        // This dramatically improves performance by skipping framework classes (Narayana, Mutiny, Vert.x, etc.)
        Optional<List<String>> includePackagesOpt = scanningConfig.includePackages();
        if (includePackagesOpt.isPresent() && !includePackagesOpt.get().isEmpty()) {
            return isIncludedByWhitelist(className, includePackagesOpt.get(), scanningConfig);
        }

        // Legacy mode (no whitelist configured): use exclusion-based filtering
        return isIncludedByExclusionRules(className, scanningConfig);
    }

    /** Checks if class matches whitelist packages and passes test filter. */
    private boolean isIncludedByWhitelist(String className, List<String> includePackages,
            QubitBuildTimeConfig.ScanningConfig scanningConfig) {
        boolean matchesInclude = includePackages.stream().anyMatch(className::startsWith);
        if (!matchesInclude) {
            return false; // Strict whitelist: reject classes not in include list
        }
        // Class matches whitelist - still apply test class filter
        return !isTestClass(className) || scanningConfig.scanTestClasses();
    }

    /** Checks if class passes exclusion-based filtering (legacy mode). */
    private boolean isIncludedByExclusionRules(String className, QubitBuildTimeConfig.ScanningConfig scanningConfig) {
        // Check exclude packages
        for (String excludePrefix : scanningConfig.excludePackages()) {
            if (className.startsWith(excludePrefix)) {
                return false;
            }
        }

        // Handle test classes based on config
        if (isTestClass(className) && !scanningConfig.scanTestClasses()) {
            return false;
        }

        // For io.quarkus.* classes, only include test classes (if test scanning is enabled)
        if (className.startsWith("io.quarkus.")) {
            return isTestClass(className) && scanningConfig.scanTestClasses();
        }

        return true;
    }

    /** Checks if class name indicates a test class. */
    private static boolean isTestClass(String className) {
        return className.contains(".it.") || className.contains(".test.");
    }

    /** Returns true if Jandex metadata proves this class cannot contain lambda call sites. */
    private static boolean shouldSkipByStructure(ClassInfo classInfo) {
        // Annotations cannot contain lambda expressions
        if (classInfo.isAnnotation()) {
            return true;
        }
        // Pure interfaces without default methods have no code bodies
        if (classInfo.isInterface()) {
            return classInfo.methods().stream()
                    .allMatch(m -> Modifier.isAbstract(m.flags()));
        }
        return false;
    }

    /** Scans a class for lambda call sites with Jandex pre-filter and constant pool quick check. */
    private List<InvokeDynamicScanner.LambdaCallSite> scanClassForCallSites(
            ClassInfo classInfo,
            InvokeDynamicScanner scanner,
            ApplicationArchivesBuildItem applicationArchives,
            QubitBuildTimeConfig.LoggingConfig loggingConfig,
            BuildMetricsCollector metricsCollector) {

        String className = classInfo.name().toString();
        QubitScanEvent scanEvent = QubitScanEvent.start(className);

        try {
            if (loggingConfig.logScannedClasses()) {
                Log.debugf("Qubit: Scanning class: %s", className);
            }

            if (metricsCollector != null) {
                metricsCollector.incrementClassesScanned();
            }

            // Jandex pre-filter: skip classes that structurally cannot contain lambdas (no IO cost)
            if (shouldSkipByStructure(classInfo)) {
                if (metricsCollector != null) {
                    metricsCollector.incrementJandexPreFilterSkips();
                }
                scanEvent.complete(false, false, 0);
                return List.of();
            }

            byte[] classBytes = BytecodeLoader.loadClassBytecode(className, applicationArchives, metricsCollector);

            // Quick check: skip full ASM parsing if no CONSTANT_InvokeDynamic in constant pool
            if (!InvokeDynamicQuickCheck.mightContainInvokeDynamic(classBytes)) {
                if (metricsCollector != null) {
                    metricsCollector.incrementQuickCheckSkips();
                }
                scanEvent.complete(false, false, 0);
                return List.of();
            }

            if (metricsCollector != null) {
                metricsCollector.incrementQuickCheckPasses();
            }

            // Full ASM parsing for classes that might contain invokedynamic
            List<InvokeDynamicScanner.LambdaCallSite> callSites = scanner.scanClass(classBytes, className);

            if (!callSites.isEmpty()) {
                Log.debugf("Found %d lambda call site(s) in %s", callSites.size(), className);
            }

            scanEvent.complete(true, true, callSites.size());
            return callSites;

        } catch (BytecodeAnalysisException e) {
            // Expected: bytecode analysis failed (e.g., class not found, unsupported bytecode)
            Log.debugf(e, "Could not analyze class %s: %s", classInfo.name(), e.getMessage());
            scanEvent.skip("bytecode_load_failed");
            return List.of();
        } catch (Exception e) {
            // Unexpected: log at warn level to surface potential bugs
            Log.warnf(e, "Unexpected error scanning class %s - this may indicate a bug in Qubit",
                    classInfo.name());
            scanEvent.skip("unexpected_error");
            return List.of();
        }
    }

    /**
     * Validates unique call site IDs. Duplicate IDs (multiple queries on same line)
     * cause silent data corruption - second registration overwrites first.
     */
    // Package-private for testing
    void validateUniqueCallSiteIds(List<InvokeDynamicScanner.LambdaCallSite> callSites) {
        Map<String, List<InvokeDynamicScanner.LambdaCallSite>> callSiteIdToSites = new HashMap<>();

        for (InvokeDynamicScanner.LambdaCallSite callSite : callSites) {
            String callSiteId = callSite.getCallSiteId();
            callSiteIdToSites.computeIfAbsent(callSiteId, _ -> new ArrayList<>()).add(callSite);
        }

        // Find duplicates
        List<Map.Entry<String, List<InvokeDynamicScanner.LambdaCallSite>>> duplicates = callSiteIdToSites.entrySet()
                .stream()
                .filter(e -> e.getValue().size() > 1)
                .toList();

        if (!duplicates.isEmpty()) {
            StringBuilder errorMessage = new StringBuilder();
            errorMessage.append("QUBIT BUILD ERROR: Duplicate call site IDs detected!\n\n");
            errorMessage
                    .append("Multiple Qubit query expressions on the same source line will cause silent data corruption.\n");
            errorMessage.append("Each query must be on a separate line to ensure unique call site identification.\n\n");

            for (Map.Entry<String, List<InvokeDynamicScanner.LambdaCallSite>> duplicate : duplicates) {
                String callSiteId = duplicate.getKey();
                List<InvokeDynamicScanner.LambdaCallSite> sites = duplicate.getValue();

                errorMessage.append("Call site ID: ").append(callSiteId).append("\n");
                errorMessage.append("  Found ").append(sites.size()).append(" queries on this line:\n");
                for (InvokeDynamicScanner.LambdaCallSite site : sites) {
                    errorMessage.append("    - ").append(site).append("\n");
                }
                errorMessage.append("\n");
            }

            errorMessage.append("FIX: Move each query to a separate source line.\n");
            errorMessage.append("Example - WRONG:\n");
            errorMessage.append("  process(Qubit.stream(A.class).toList(), Qubit.stream(B.class).toList());\n\n");
            errorMessage.append("Example - CORRECT:\n");
            errorMessage.append("  List<A> listA = Qubit.stream(A.class).toList();\n");
            errorMessage.append("  List<B> listB = Qubit.stream(B.class).toList();\n");
            errorMessage.append("  process(listA, listB);");

            throw new IllegalStateException(errorMessage.toString());
        }
    }

    /** Register generated executors at STATIC_INIT time. */
    @BuildStep
    @Record(ExecutionTime.STATIC_INIT)
    void registerQueryExecutors(
            QueryExecutorRecorder recorder,
            List<QueryTransformationBuildItem> transformations) {

        // Clear stale executors before registration (essential for dev mode hot reload)
        recorder.clearAllExecutors();

        Log.debugf("Qubit: Registering %d query executors in registry", transformations.size());

        for (QueryTransformationBuildItem transformation : transformations) {
            registerExecutorForTransformation(recorder, transformation);
        }
    }

    /** Registers the appropriate executor type based on transformation characteristics. */
    private void registerExecutorForTransformation(
            QueryExecutorRecorder recorder,
            QueryTransformationBuildItem transformation) {

        String callSiteId = transformation.getQueryId();
        String className = transformation.getGeneratedClassName();
        int capturedVarCount = transformation.getCapturedVarCount();

        if (transformation.isGroupQuery()) {
            registerGroupExecutor(recorder, transformation, callSiteId, className, capturedVarCount);
        } else if (transformation.isJoinQuery()) {
            registerJoinExecutor(recorder, transformation, callSiteId, className, capturedVarCount);
        } else if (transformation.isAggregationQuery()) {
            recorder.registerAggregationExecutor(callSiteId, className, capturedVarCount);
        } else if (transformation.isCountQuery()) {
            recorder.registerCountExecutor(callSiteId, className, capturedVarCount);
        } else {
            recorder.registerListExecutor(callSiteId, className, capturedVarCount);
        }

        Log.tracef("Registered executor for call site: %s → %s (captured variables: %d)",
                callSiteId, className, capturedVarCount);
    }

    /** Registers group query executor (count or list). */
    private void registerGroupExecutor(
            QueryExecutorRecorder recorder,
            QueryTransformationBuildItem transformation,
            String callSiteId, String className, int capturedVarCount) {

        if (transformation.isCountQuery()) {
            recorder.registerGroupCountExecutor(callSiteId, className, capturedVarCount);
        } else {
            recorder.registerGroupListExecutor(callSiteId, className, capturedVarCount);
        }
    }

    /** Registers join query executor (count, projection, selectJoined, or list). */
    private void registerJoinExecutor(
            QueryExecutorRecorder recorder,
            QueryTransformationBuildItem transformation,
            String callSiteId, String className, int capturedVarCount) {

        if (transformation.isCountQuery()) {
            recorder.registerJoinCountExecutor(callSiteId, className, capturedVarCount);
        } else if (transformation.isJoinProjection()) {
            recorder.registerJoinProjectionExecutor(callSiteId, className, capturedVarCount);
        } else if (transformation.isSelectJoined()) {
            recorder.registerJoinSelectJoinedExecutor(callSiteId, className, capturedVarCount);
        } else {
            recorder.registerJoinListExecutor(callSiteId, className, capturedVarCount);
        }
    }

    /**
     * Build item representing a query transformation.
     */
    public static final class QueryTransformationBuildItem extends MultiBuildItem {
        private final String queryId;
        private final String generatedClassName;
        private final String entityClassName;
        private final QueryCharacteristics characteristics;
        private final int capturedVarCount;
        private final DevUIExpressions devUIExpressions;
        private final DevUIMetadata devUIMetadata;

        /** Groups optional LambdaExpression fields for DevUI JPQL generation. */
        public record DevUIExpressions(
                LambdaExpression predicateExpression,
                LambdaExpression projectionExpression,
                LambdaExpression sortExpression,
                LambdaExpression aggregationExpression,
                LambdaExpression groupByKeyExpression,
                LambdaExpression havingExpression,
                LambdaExpression joinRelationshipExpression) {

            /** Creates empty expressions (all null). */
            public static DevUIExpressions empty() {
                return new DevUIExpressions(null, null, null, null, null, null, null);
            }
        }

        /** Groups optional metadata fields for DevUI display. */
        public record DevUIMetadata(
                String terminalMethodName,
                boolean hasDistinct,
                boolean sortDescending,
                boolean isSelectKey,
                String aggregationType,
                Integer skipValue,
                Integer limitValue) {

            /** Creates empty metadata (all defaults). */
            public static DevUIMetadata empty() {
                return new DevUIMetadata(null, false, false, false, null, null, null);
            }
        }

        /** Primary constructor using QueryCharacteristics. */
        public QueryTransformationBuildItem(
                String queryId,
                String generatedClassName,
                String entityClassName,
                QueryCharacteristics characteristics,
                int capturedVarCount) {
            this(queryId, generatedClassName, entityClassName, characteristics, capturedVarCount,
                    DevUIExpressions.empty(), DevUIMetadata.empty());
        }

        /** Full constructor with optional expressions for DevUI. */
        public QueryTransformationBuildItem(
                String queryId,
                String generatedClassName,
                String entityClassName,
                QueryCharacteristics characteristics,
                int capturedVarCount,
                LambdaExpression predicateExpression,
                LambdaExpression projectionExpression) {
            this(queryId, generatedClassName, entityClassName, characteristics, capturedVarCount,
                    new DevUIExpressions(predicateExpression, projectionExpression, null, null, null, null, null),
                    DevUIMetadata.empty());
        }

        /** Canonical constructor with parameter objects. */
        private QueryTransformationBuildItem(
                String queryId,
                String generatedClassName,
                String entityClassName,
                QueryCharacteristics characteristics,
                int capturedVarCount,
                DevUIExpressions devUIExpressions,
                DevUIMetadata devUIMetadata) {
            this.queryId = queryId;
            this.generatedClassName = generatedClassName;
            this.entityClassName = entityClassName;
            this.characteristics = characteristics;
            this.capturedVarCount = capturedVarCount;
            this.devUIExpressions = devUIExpressions;
            this.devUIMetadata = devUIMetadata;
        }

        /**
         * Convenience constructor for simple list/count queries.
         * Creates QueryCharacteristics with only isCountQuery set.
         */
        public QueryTransformationBuildItem(
                String queryId,
                String generatedClassName,
                String entityClassName,
                boolean isCountQuery,
                int capturedVarCount) {
            this(queryId, generatedClassName, entityClassName,
                    isCountQuery ? QueryCharacteristics.forCount() : QueryCharacteristics.forList(),
                    capturedVarCount);
        }

        /**
         * Convenience constructor for aggregation queries.
         */
        public QueryTransformationBuildItem(
                String queryId,
                String generatedClassName,
                String entityClassName,
                boolean isCountQuery,
                boolean isAggregationQuery,
                int capturedVarCount) {
            this(queryId, generatedClassName, entityClassName,
                    new QueryCharacteristics(isCountQuery, isAggregationQuery, false, false, false, false),
                    capturedVarCount);
        }

        /** Returns unique query identifier (call site ID). */
        public String getQueryId() {
            return queryId;
        }

        /** Returns generated executor class name. */
        public String getGeneratedClassName() {
            return generatedClassName;
        }

        /** Returns entity class name for this query (fully qualified). */
        public String getEntityClassName() {
            return entityClassName;
        }

        /** Returns query characteristics */
        public QueryCharacteristics getCharacteristics() {
            return characteristics;
        }

        /** Returns true if this is a count query. */
        public boolean isCountQuery() {
            return characteristics.isCountQuery();
        }

        /** Returns true if this is an aggregation query. */
        public boolean isAggregationQuery() {
            return characteristics.isAggregationQuery();
        }

        /** Returns true if this is a join query. */
        public boolean isJoinQuery() {
            return characteristics.isJoinQuery();
        }

        /** Returns true if this is a selectJoined query. */
        public boolean isSelectJoined() {
            return characteristics.isSelectJoined();
        }

        /** Returns true if this is a join projection query. */
        public boolean isJoinProjection() {
            return characteristics.isJoinProjection();
        }

        /** Returns true if this is a group query. */
        public boolean isGroupQuery() {
            return characteristics.isGroupQuery();
        }

        /** Returns number of captured variables. */
        public int getCapturedVarCount() {
            return capturedVarCount;
        }

        /** Returns the predicate expression (for DevUI, may be null). */
        public LambdaExpression getPredicateExpression() {
            return devUIExpressions.predicateExpression();
        }

        /** Returns the projection expression (for DevUI, may be null). */
        public LambdaExpression getProjectionExpression() {
            return devUIExpressions.projectionExpression();
        }

        /** Returns the sort expression (for DevUI, may be null). */
        public LambdaExpression getSortExpression() {
            return devUIExpressions.sortExpression();
        }

        /** Returns the aggregation expression (for DevUI, may be null). */
        public LambdaExpression getAggregationExpression() {
            return devUIExpressions.aggregationExpression();
        }

        /** Returns the groupBy key expression (for DevUI, may be null). */
        public LambdaExpression getGroupByKeyExpression() {
            return devUIExpressions.groupByKeyExpression();
        }

        /** Returns the HAVING clause expression (for DevUI, may be null). */
        public LambdaExpression getHavingExpression() {
            return devUIExpressions.havingExpression();
        }

        /** Returns the join relationship expression (for DevUI, may be null). */
        public LambdaExpression getJoinRelationshipExpression() {
            return devUIExpressions.joinRelationshipExpression();
        }

        /** Returns true if selectKey() was used instead of select() in group queries. */
        public boolean isSelectKey() {
            return devUIMetadata.isSelectKey();
        }

        /** Returns the terminal method name (toList, count, findFirst, etc.). */
        public String getTerminalMethodName() {
            return devUIMetadata.terminalMethodName();
        }

        /** Returns true if distinct() was called. */
        public boolean hasDistinct() {
            return devUIMetadata.hasDistinct();
        }

        /** Returns true if sorting is descending. */
        public boolean isSortDescending() {
            return devUIMetadata.sortDescending();
        }

        /** Returns the aggregation type (MIN, MAX, AVG, SUM_*, etc.). */
        public String getAggregationType() {
            return devUIMetadata.aggregationType();
        }

        /** Returns the skip value (null if skip() was not called). */
        public Integer getSkipValue() {
            return devUIMetadata.skipValue();
        }

        /** Returns the limit value (null if limit() was not called). */
        public Integer getLimitValue() {
            return devUIMetadata.limitValue();
        }

        /** Creates a new builder (avoids error-prone 19-parameter constructor). */
        public static Builder builder() {
            return new Builder();
        }

        /** Builder with named setters. Required fields validated in build(). */
        public static final class Builder {
            // Required fields
            private String queryId;
            private String generatedClassName;
            private String entityClassName;
            private QueryCharacteristics characteristics;
            private int capturedVarCount;

            // Optional fields with defaults
            private LambdaExpression predicateExpression;
            private LambdaExpression projectionExpression;
            private LambdaExpression sortExpression;
            private LambdaExpression aggregationExpression;
            private LambdaExpression groupByKeyExpression;
            private LambdaExpression havingExpression;
            private LambdaExpression joinRelationshipExpression;
            private String terminalMethodName;
            private boolean hasDistinct;
            private boolean sortDescending;
            private boolean isSelectKey;
            private String aggregationType;
            private Integer skipValue;
            private Integer limitValue;

            private Builder() {
            }

            // Required field setters

            public Builder queryId(String queryId) {
                this.queryId = queryId;
                return this;
            }

            public Builder generatedClassName(String generatedClassName) {
                this.generatedClassName = generatedClassName;
                return this;
            }

            public Builder entityClassName(String entityClassName) {
                this.entityClassName = entityClassName;
                return this;
            }

            public Builder characteristics(QueryCharacteristics characteristics) {
                this.characteristics = characteristics;
                return this;
            }

            public Builder capturedVarCount(int capturedVarCount) {
                this.capturedVarCount = capturedVarCount;
                return this;
            }

            // Optional field setters

            public Builder predicateExpression(LambdaExpression predicateExpression) {
                this.predicateExpression = predicateExpression;
                return this;
            }

            public Builder projectionExpression(LambdaExpression projectionExpression) {
                this.projectionExpression = projectionExpression;
                return this;
            }

            public Builder sortExpression(LambdaExpression sortExpression) {
                this.sortExpression = sortExpression;
                return this;
            }

            public Builder aggregationExpression(LambdaExpression aggregationExpression) {
                this.aggregationExpression = aggregationExpression;
                return this;
            }

            public Builder groupByKeyExpression(LambdaExpression groupByKeyExpression) {
                this.groupByKeyExpression = groupByKeyExpression;
                return this;
            }

            public Builder havingExpression(LambdaExpression havingExpression) {
                this.havingExpression = havingExpression;
                return this;
            }

            public Builder joinRelationshipExpression(LambdaExpression joinRelationshipExpression) {
                this.joinRelationshipExpression = joinRelationshipExpression;
                return this;
            }

            public Builder terminalMethodName(String terminalMethodName) {
                this.terminalMethodName = terminalMethodName;
                return this;
            }

            public Builder hasDistinct(boolean hasDistinct) {
                this.hasDistinct = hasDistinct;
                return this;
            }

            public Builder sortDescending(boolean sortDescending) {
                this.sortDescending = sortDescending;
                return this;
            }

            public Builder isSelectKey(boolean isSelectKey) {
                this.isSelectKey = isSelectKey;
                return this;
            }

            public Builder aggregationType(String aggregationType) {
                this.aggregationType = aggregationType;
                return this;
            }

            public Builder skipValue(Integer skipValue) {
                this.skipValue = skipValue;
                return this;
            }

            public Builder limitValue(Integer limitValue) {
                this.limitValue = limitValue;
                return this;
            }

            /** Builds the item. Throws IllegalStateException if required fields are missing. */
            public QueryTransformationBuildItem build() {
                if (queryId == null) {
                    throw new IllegalStateException(QUERY_ID_REQUIRED);
                }
                if (generatedClassName == null) {
                    throw new IllegalStateException("generatedClassName is required");
                }
                if (characteristics == null) {
                    throw new IllegalStateException("characteristics is required");
                }
                DevUIExpressions expressions = new DevUIExpressions(
                        predicateExpression, projectionExpression, sortExpression, aggregationExpression,
                        groupByKeyExpression, havingExpression, joinRelationshipExpression);
                DevUIMetadata metadata = new DevUIMetadata(
                        terminalMethodName, hasDistinct, sortDescending, isSelectKey, aggregationType,
                        skipValue, limitValue);
                return new QueryTransformationBuildItem(
                        queryId, generatedClassName, entityClassName, characteristics, capturedVarCount,
                        expressions, metadata);
            }
        }
    }

}
