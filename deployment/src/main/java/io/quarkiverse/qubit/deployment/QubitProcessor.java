package io.quarkiverse.qubit.deployment;

import static io.quarkiverse.qubit.runtime.QubitConstants.QUBIT_ENTITY_CLASS_NAME;
import static io.quarkiverse.qubit.runtime.QubitConstants.QUBIT_REPOSITORY_CLASS_NAME;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
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
import io.quarkiverse.qubit.deployment.analysis.CallSiteProcessor;
import io.quarkiverse.qubit.deployment.analysis.InvokeDynamicScanner;
import io.quarkiverse.qubit.deployment.analysis.LambdaBytecodeAnalyzer;
import io.quarkiverse.qubit.deployment.analysis.LambdaDeduplicator;
import io.quarkiverse.qubit.deployment.analysis.QueryCharacteristics;
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

        AtomicInteger generatedCount = new AtomicInteger(0);
        AtomicInteger deduplicatedCount = new AtomicInteger(0);

        allCallSites.stream()
                .forEach(callSite -> configuredProcessor.processCallSite(
                        callSite, applicationArchives,
                        generatedCount, deduplicatedCount,
                        generatedClass, queryTransformations,
                        config.logging()));

        Log.infof("Qubit extension initialized - Call sites: %d | Query executors: %d generated, %d deduplicated",
                allCallSites.size(), generatedCount.get(), deduplicatedCount.get());
    }

    /**
     * Determines if a class should be included in lambda scanning.
     *
     * @param classInfo the class to check
     * @param scanningConfig scanning configuration from QubitBuildTimeConfig
     * @return true if the class should be scanned, false if excluded
     */
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

            if (classBytes == null) {
                return Collections.emptyList();
            }

            List<InvokeDynamicScanner.LambdaCallSite> callSites = scanner.scanClass(classBytes, className);

            if (!callSites.isEmpty()) {
                Log.debugf("Found %d lambda call site(s) in %s", callSites.size(), className);
            }

            return callSites;

        } catch (Exception e) {
            Log.debugf(e, "Error scanning class: %s", classInfo.name());
            return Collections.emptyList();
        }
    }

    /** Register generated executors at STATIC_INIT time. */
    @BuildStep
    @Record(ExecutionTime.STATIC_INIT)
    void registerQueryExecutors(
            QueryExecutorRecorder recorder,
            List<QueryTransformationBuildItem> transformations) {

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
        private final Class<?> entityClass;
        private final QueryCharacteristics characteristics;
        private final int capturedVarCount;

        /**
         * Primary constructor using QueryCharacteristics.
         *
         * @param queryId unique query identifier (call site ID)
         * @param generatedClassName generated executor class name
         * @param entityClass entity class for this query
         * @param characteristics query type characteristics
         * @param capturedVarCount number of captured variables
         */
        public QueryTransformationBuildItem(
                String queryId,
                String generatedClassName,
                Class<?> entityClass,
                QueryCharacteristics characteristics,
                int capturedVarCount) {
            this.queryId = queryId;
            this.generatedClassName = generatedClassName;
            this.entityClass = entityClass;
            this.characteristics = characteristics;
            this.capturedVarCount = capturedVarCount;
        }

        /**
         * Convenience constructor for simple list/count queries.
         * Creates QueryCharacteristics with only isCountQuery set.
         */
        public QueryTransformationBuildItem(
                String queryId,
                String generatedClassName,
                Class<?> entityClass,
                boolean isCountQuery,
                int capturedVarCount) {
            this(queryId, generatedClassName, entityClass,
                    isCountQuery ? QueryCharacteristics.forCount() : QueryCharacteristics.forList(),
                    capturedVarCount);
        }

        /**
         * Convenience constructor for aggregation queries.
         */
        public QueryTransformationBuildItem(
                String queryId,
                String generatedClassName,
                Class<?> entityClass,
                boolean isCountQuery,
                boolean isAggregationQuery,
                int capturedVarCount) {
            this(queryId, generatedClassName, entityClass,
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

        /** Returns entity class for this query. */
        public Class<?> getEntityClass() {
            return entityClass;
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
    }

}
