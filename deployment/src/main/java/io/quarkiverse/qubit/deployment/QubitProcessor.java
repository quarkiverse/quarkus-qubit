package io.quarkiverse.qubit.deployment;

import static io.quarkiverse.qubit.deployment.common.ExceptionMessages.CHARACTERISTICS_REQUIRED;
import static io.quarkiverse.qubit.deployment.common.ExceptionMessages.GENERATED_CLASS_NAME_REQUIRED;
import static io.quarkiverse.qubit.deployment.common.ExceptionMessages.QUERY_ID_REQUIRED;
import static io.quarkiverse.qubit.runtime.QubitConstants.QUBIT_ENTITY_CLASS_NAME;
import static io.quarkiverse.qubit.runtime.QubitConstants.QUBIT_REPOSITORY_CLASS_NAME;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.IndexView;
import io.quarkus.logging.Log;
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
import io.quarkiverse.qubit.runtime.QueryExecutorRecorder;
import io.quarkiverse.qubit.runtime.QueryExecutorRegistry;
import io.quarkiverse.qubit.runtime.QubitEntity;
import io.quarkiverse.qubit.deployment.common.BytecodeAnalysisException;
import io.quarkiverse.qubit.deployment.analysis.CallSiteProcessor;
import io.quarkiverse.qubit.deployment.analysis.InvokeDynamicScanner;
import io.quarkiverse.qubit.deployment.analysis.LambdaBytecodeAnalyzer;
import io.quarkiverse.qubit.deployment.analysis.LambdaDeduplicator;
import io.quarkiverse.qubit.deployment.analysis.QueryCharacteristics;
import io.quarkiverse.qubit.deployment.ast.LambdaExpression;
import io.quarkiverse.qubit.deployment.generation.QueryExecutorClassGenerator;
import io.quarkiverse.qubit.deployment.util.BytecodeLoader;

/**
 * Qubit extension build processor. Generates query executor classes at build time from lambda expressions.
 */
public class QubitProcessor {

    private static final String FEATURE = "qubit";

    private final QueryExecutorClassGenerator classGenerator = new QueryExecutorClassGenerator();
    private final LambdaBytecodeAnalyzer bytecodeAnalyzer = new LambdaBytecodeAnalyzer();
    private final LambdaDeduplicator deduplicator = new LambdaDeduplicator();

    /** Registers Qubit feature. */
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

        Log.debugf("Qubit: Scanning for lambda call sites using invokedynamic analysis");

        IndexView index = combinedIndex.getIndex();
        InvokeDynamicScanner scanner = new InvokeDynamicScanner();

        Collection<ClassInfo> allClasses = index.getKnownClasses();

        Log.debugf("Qubit: Scanning %d classes for lambda call sites", allClasses.size());

        List<ClassInfo> filteredClasses = allClasses.stream()
                .filter(classInfo -> isNotExcludedClass(classInfo, config.scanning()))
                .toList();

        Log.infof("Qubit: Filtered to %d application classes (from %d total)",
                filteredClasses.size(), allClasses.size());

        // Log test classes found
        long testClassCount = filteredClasses.stream()
                .filter(c -> c.name().toString().contains(".it.") || c.name().toString().contains(".test."))
                .count();
        if (config.scanning().scanTestClasses()) {
            Log.infof("Qubit: Found %d test classes (scanning enabled)", testClassCount);
        } else {
            Log.debugf("Qubit: Skipped %d test classes (scanning disabled)", testClassCount);
        }

        CallSiteProcessor configuredProcessor = new CallSiteProcessor(
                bytecodeAnalyzer, deduplicator, classGenerator, config.generation());

        List<InvokeDynamicScanner.LambdaCallSite> allCallSites = filteredClasses.stream()
                .flatMap(classInfo -> scanClassForCallSites(classInfo, scanner, applicationArchives, config.logging()).stream())
                .peek(c -> Log.tracef("Qubit: Found callSite %s", c.getCallSiteId()))
                .toList();

        Log.debugf("Qubit: Found %d total lambda call site(s)", allCallSites.size());

        validateUniqueCallSiteIds(allCallSites);

        AtomicInteger generatedCount = new AtomicInteger(0);
        AtomicInteger deduplicatedCount = new AtomicInteger(0);

        allCallSites.stream()
                .forEach(callSite -> configuredProcessor.processCallSiteWithHandlers(
                        callSite, applicationArchives,
                        generatedCount, deduplicatedCount,
                        generatedClass, queryTransformations,
                        config.logging(),
                        true));

        Log.infof("Qubit extension initialized - Call sites: %d | Query executors: %d generated, %d deduplicated",
                allCallSites.size(), generatedCount.get(), deduplicatedCount.get());
    }

    /** Determines if a class should be included in lambda scanning. */
    private boolean isNotExcludedClass(ClassInfo classInfo, QubitBuildTimeConfig.ScanningConfig scanningConfig) {
        String className = classInfo.name().toString();

        // Check include packages first (override excludes)
        if (scanningConfig.includePackages().isPresent()) {
            for (String includePrefix : scanningConfig.includePackages().get()) {
                if (className.startsWith(includePrefix)) {
                    return true;
                }
            }
        }

        // Check exclude packages
        for (String excludePrefix : scanningConfig.excludePackages()) {
            if (className.startsWith(excludePrefix)) {
                return false;
            }
        }

        // Handle test classes based on config
        boolean isTestClass = className.contains(".it.") || className.contains(".test.");
        if (isTestClass && !scanningConfig.scanTestClasses()) {
            return false;
        }

        // Always include qubit extension classes
        if (className.startsWith("io.quarkiverse.qubit.")) {
            return true;
        }

        // For io.quarkus.* classes, only include test classes (if test scanning is enabled)
        if (className.startsWith("io.quarkus.")) {
            return isTestClass && scanningConfig.scanTestClasses();
        }

        return true;
    }

    /**
     * Scans a class for lambda call sites.
     */
    private List<InvokeDynamicScanner.LambdaCallSite> scanClassForCallSites(
            ClassInfo classInfo,
            InvokeDynamicScanner scanner,
            ApplicationArchivesBuildItem applicationArchives,
            QubitBuildTimeConfig.LoggingConfig loggingConfig) {
        try {
            String className = classInfo.name().toString();

            if (loggingConfig.logScannedClasses()) {
                Log.debugf("Qubit: Scanning class: %s", className);
            }

            byte[] classBytes = BytecodeLoader.loadClassBytecode(className, applicationArchives);

            List<InvokeDynamicScanner.LambdaCallSite> callSites = scanner.scanClass(classBytes, className);

            if (!callSites.isEmpty()) {
                Log.debugf("Found %d lambda call site(s) in %s", callSites.size(), className);
            }

            return callSites;

        } catch (BytecodeAnalysisException e) {
            // Expected: bytecode analysis failed (e.g., class not found, unsupported bytecode)
            Log.debugf(e, "Could not analyze class %s: %s", classInfo.name(), e.getMessage());
            return Collections.emptyList();
        } catch (Exception e) {
            // Unexpected: log at warn level to surface potential bugs
            Log.warnf(e, "Unexpected error scanning class %s - this may indicate a bug in Qubit",
                    classInfo.name());
            return Collections.emptyList();
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
            callSiteIdToSites.computeIfAbsent(callSiteId, k -> new ArrayList<>()).add(callSite);
        }

        // Find duplicates
        List<Map.Entry<String, List<InvokeDynamicScanner.LambdaCallSite>>> duplicates = callSiteIdToSites.entrySet()
                .stream()
                .filter(e -> e.getValue().size() > 1)
                .toList();

        if (!duplicates.isEmpty()) {
            StringBuilder errorMessage = new StringBuilder();
            errorMessage.append("QUBIT BUILD ERROR: Duplicate call site IDs detected!\n\n");
            errorMessage.append("Multiple Qubit query expressions on the same source line will cause silent data corruption.\n");
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
            String callSiteId = transformation.getQueryId();

            if (transformation.isGroupQuery()) {
                // Register group executors
                if (transformation.isCountQuery()) {
                    recorder.registerGroupCountExecutor(
                            callSiteId,
                            transformation.getGeneratedClassName(),
                            transformation.getCapturedVarCount());
                } else {
                    recorder.registerGroupListExecutor(
                            callSiteId,
                            transformation.getGeneratedClassName(),
                            transformation.getCapturedVarCount());
                }
            } else if (transformation.isJoinQuery()) {
                // Register join executors
                if (transformation.isCountQuery()) {
                    recorder.registerJoinCountExecutor(
                            callSiteId,
                            transformation.getGeneratedClassName(),
                            transformation.getCapturedVarCount());
                } else if (transformation.isJoinProjection()) {
                    // Register join projection executor
                    recorder.registerJoinProjectionExecutor(
                            callSiteId,
                            transformation.getGeneratedClassName(),
                            transformation.getCapturedVarCount());
                } else if (transformation.isSelectJoined()) {
                    // Register selectJoined executor
                    recorder.registerJoinSelectJoinedExecutor(
                            callSiteId,
                            transformation.getGeneratedClassName(),
                            transformation.getCapturedVarCount());
                } else {
                    recorder.registerJoinListExecutor(
                            callSiteId,
                            transformation.getGeneratedClassName(),
                            transformation.getCapturedVarCount());
                }
            } else if (transformation.isAggregationQuery()) {
                // Register aggregation executors (min, max, avg, sum*)
                recorder.registerAggregationExecutor(
                        callSiteId,
                        transformation.getGeneratedClassName(),
                        transformation.getCapturedVarCount());
            } else if (transformation.isCountQuery()) {
                recorder.registerCountExecutor(
                        callSiteId,
                        transformation.getGeneratedClassName(),
                        transformation.getCapturedVarCount());
            } else {
                recorder.registerListExecutor(
                        callSiteId,
                        transformation.getGeneratedClassName(),
                        transformation.getCapturedVarCount());
            }

            Log.tracef("Registered executor for call site: %s → %s (captured variables: %d)",
                    callSiteId,
                    transformation.getGeneratedClassName(),
                    transformation.getCapturedVarCount());
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
        // Optional expressions for DevUI JPQL generation (only populated in dev mode)
        private final LambdaExpression predicateExpression;
        private final LambdaExpression projectionExpression;
        // Additional expressions for enhanced DevUI display
        private final LambdaExpression sortExpression;
        private final LambdaExpression aggregationExpression;
        private final LambdaExpression groupByKeyExpression;
        private final LambdaExpression havingExpression;
        private final LambdaExpression joinRelationshipExpression;
        private final String terminalMethodName;
        private final boolean hasDistinct;
        private final boolean sortDescending;
        private final boolean isSelectKey;  // True if selectKey() was used instead of select() in group queries
        private final String aggregationType;  // MIN, MAX, AVG, SUM_INTEGER, SUM_LONG, SUM_DOUBLE
        private final Integer skipValue;  // Value passed to skip(), null if not called
        private final Integer limitValue;  // Value passed to limit(), null if not called

        /** Primary constructor using QueryCharacteristics. */
        public QueryTransformationBuildItem(
                String queryId,
                String generatedClassName,
                String entityClassName,
                QueryCharacteristics characteristics,
                int capturedVarCount) {
            this(queryId, generatedClassName, entityClassName, characteristics, capturedVarCount, null, null);
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
                 predicateExpression, projectionExpression, null, null, null, null, null, null, false, false, false, null, null, null);
        }

        /**
         * Extended constructor including all optional expressions for enhanced DevUI display.
         */
        public QueryTransformationBuildItem(
                String queryId,
                String generatedClassName,
                String entityClassName,
                QueryCharacteristics characteristics,
                int capturedVarCount,
                LambdaExpression predicateExpression,
                LambdaExpression projectionExpression,
                LambdaExpression sortExpression,
                LambdaExpression aggregationExpression,
                LambdaExpression groupByKeyExpression,
                LambdaExpression havingExpression,
                LambdaExpression joinRelationshipExpression,
                String terminalMethodName,
                boolean hasDistinct,
                boolean sortDescending,
                boolean isSelectKey,
                String aggregationType,
                Integer skipValue,
                Integer limitValue) {
            this.queryId = queryId;
            this.generatedClassName = generatedClassName;
            this.entityClassName = entityClassName;
            this.characteristics = characteristics;
            this.capturedVarCount = capturedVarCount;
            this.predicateExpression = predicateExpression;
            this.projectionExpression = projectionExpression;
            this.sortExpression = sortExpression;
            this.aggregationExpression = aggregationExpression;
            this.groupByKeyExpression = groupByKeyExpression;
            this.havingExpression = havingExpression;
            this.joinRelationshipExpression = joinRelationshipExpression;
            this.terminalMethodName = terminalMethodName;
            this.hasDistinct = hasDistinct;
            this.sortDescending = sortDescending;
            this.isSelectKey = isSelectKey;
            this.aggregationType = aggregationType;
            this.skipValue = skipValue;
            this.limitValue = limitValue;
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
            return predicateExpression;
        }

        /** Returns the projection expression (for DevUI, may be null). */
        public LambdaExpression getProjectionExpression() {
            return projectionExpression;
        }

        /** Returns the sort expression (for DevUI, may be null). */
        public LambdaExpression getSortExpression() {
            return sortExpression;
        }

        /** Returns the aggregation expression (for DevUI, may be null). */
        public LambdaExpression getAggregationExpression() {
            return aggregationExpression;
        }

        /** Returns the groupBy key expression (for DevUI, may be null). */
        public LambdaExpression getGroupByKeyExpression() {
            return groupByKeyExpression;
        }

        /** Returns the HAVING clause expression (for DevUI, may be null). */
        public LambdaExpression getHavingExpression() {
            return havingExpression;
        }

        /** Returns the join relationship expression (for DevUI, may be null). */
        public LambdaExpression getJoinRelationshipExpression() {
            return joinRelationshipExpression;
        }

        /** Returns true if selectKey() was used instead of select() in group queries. */
        public boolean isSelectKey() {
            return isSelectKey;
        }

        /** Returns the terminal method name (toList, count, findFirst, etc.). */
        public String getTerminalMethodName() {
            return terminalMethodName;
        }

        /** Returns true if distinct() was called. */
        public boolean hasDistinct() {
            return hasDistinct;
        }

        /** Returns true if sorting is descending. */
        public boolean isSortDescending() {
            return sortDescending;
        }

        /** Returns the aggregation type (MIN, MAX, AVG, SUM_*, etc.). */
        public String getAggregationType() {
            return aggregationType;
        }

        /** Returns the skip value (null if skip() was not called). */
        public Integer getSkipValue() {
            return skipValue;
        }

        /** Returns the limit value (null if limit() was not called). */
        public Integer getLimitValue() {
            return limitValue;
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

            private Builder() {}

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
                    throw new IllegalStateException(GENERATED_CLASS_NAME_REQUIRED);
                }
                if (characteristics == null) {
                    throw new IllegalStateException(CHARACTERISTICS_REQUIRED);
                }
                return new QueryTransformationBuildItem(
                        queryId, generatedClassName, entityClassName, characteristics, capturedVarCount,
                        predicateExpression, projectionExpression, sortExpression, aggregationExpression,
                        groupByKeyExpression, havingExpression, joinRelationshipExpression,
                        terminalMethodName, hasDistinct, sortDescending, isSelectKey, aggregationType,
                        skipValue, limitValue);
            }
        }
    }

}
