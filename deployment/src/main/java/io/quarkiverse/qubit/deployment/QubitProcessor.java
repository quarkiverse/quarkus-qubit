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
import io.quarkiverse.qubit.deployment.generation.QueryExecutorClassGenerator;
import io.quarkiverse.qubit.deployment.util.BytecodeLoader;

/** Qubit extension build processor. Generates query executor classes at build time from lambda expressions. */
public class QubitProcessor {

    private static final String FEATURE = "qubit";
    private static final AtomicInteger queryCounter = new AtomicInteger(0);

    private final QueryExecutorClassGenerator classGenerator = new QueryExecutorClassGenerator();
    private final LambdaBytecodeAnalyzer bytecodeAnalyzer = new LambdaBytecodeAnalyzer();
    private final LambdaDeduplicator deduplicator = new LambdaDeduplicator();
    private final CallSiteProcessor callSiteProcessor = new CallSiteProcessor(
            bytecodeAnalyzer, deduplicator, classGenerator, queryCounter);

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

    /** Scan for lambda call sites and generate query executor classes. */
    @BuildStep
    void generateQueryExecutors(
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
                .filter(QubitProcessor::isNotFrameworkClass)
                .toList();

        Log.infof("Qubit: Filtered to %d application classes (from %d total)",
                filteredClasses.size(), allClasses.size());

        // Log test classes found
        long testClassCount = filteredClasses.stream()
                .filter(c -> c.name().toString().contains(".it."))
                .count();
        Log.infof("Qubit: Found %d integration test classes", testClassCount);

        List<InvokeDynamicScanner.LambdaCallSite> allCallSites = filteredClasses.stream()
                .flatMap(classInfo -> scanClassForCallSites(classInfo, scanner, applicationArchives).stream())
                .peek(c -> Log.tracef("Qubit: Found callSite %s", c.getCallSiteId()))
                .toList();

        Log.debugf("Qubit: Found %d total lambda call site(s)", allCallSites.size());

        AtomicInteger generatedCount = new AtomicInteger(0);
        AtomicInteger deduplicatedCount = new AtomicInteger(0);

        allCallSites.stream()
                .forEach(callSite -> callSiteProcessor.processCallSite(
                        callSite, applicationArchives,
                        generatedCount, deduplicatedCount,
                        generatedClass, queryTransformations));

        Log.infof("Qubit extension initialized - Call sites: %d | Query executors: %d generated, %d deduplicated",
                allCallSites.size(), generatedCount.get(), deduplicatedCount.get());
    }

    private static boolean isNotFrameworkClass(ClassInfo classInfo) {
        String className = classInfo.name().toString();

        if (className.startsWith("java.") || className.startsWith("jakarta.")) {
            return false;
        }

        if (className.startsWith("io.quarkiverse.qubit.")) {
            return true;
        }

        if (className.startsWith("io.quarkus.")) {
            return className.contains(".it.");
        }

        return true;
    }

    private List<InvokeDynamicScanner.LambdaCallSite> scanClassForCallSites(
            ClassInfo classInfo,
            InvokeDynamicScanner scanner,
            ApplicationArchivesBuildItem applicationArchives) {
        try {
            String className = classInfo.name().toString();
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
                // Iteration 7: Register group executors
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
                // Iteration 6: Register join executors
                if (transformation.isCountQuery()) {
                    recorder.registerJoinCountExecutor(
                            callSiteId,
                            transformation.getGeneratedClassName(),
                            transformation.getCapturedVarCount());
                } else if (transformation.isJoinProjection()) {
                    // Iteration 6.6: Register join projection executor
                    recorder.registerJoinProjectionExecutor(
                            callSiteId,
                            transformation.getGeneratedClassName(),
                            transformation.getCapturedVarCount());
                } else if (transformation.isSelectJoined()) {
                    // Iteration 6.5: Register selectJoined executor
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
                // Phase 5: Register aggregation executors (min, max, avg, sum*)
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

    /** Build item linking call site to generated executor class. */
    public static final class QueryTransformationBuildItem extends MultiBuildItem {
        private final String queryId;
        private final String generatedClassName;
        private final Class<?> entityClass;
        private final boolean isCountQuery;
        private final boolean isAggregationQuery;
        private final boolean isJoinQuery;  // Iteration 6: Join Queries
        private final boolean isSelectJoined; // Iteration 6.5: selectJoined() queries
        private final boolean isJoinProjection; // Iteration 6.6: join projection queries
        private final boolean isGroupQuery; // Iteration 7: Group Queries
        private final int capturedVarCount;

        public QueryTransformationBuildItem(
                String queryId,
                String generatedClassName,
                Class<?> entityClass,
                boolean isCountQuery,
                int capturedVarCount) {
            this(queryId, generatedClassName, entityClass, isCountQuery, false, false, false, false, false, capturedVarCount);
        }

        public QueryTransformationBuildItem(
                String queryId,
                String generatedClassName,
                Class<?> entityClass,
                boolean isCountQuery,
                boolean isAggregationQuery,
                int capturedVarCount) {
            this(queryId, generatedClassName, entityClass, isCountQuery, isAggregationQuery, false, false, false, false, capturedVarCount);
        }

        /**
         * Constructor with join query support.
         * Iteration 6: Added isJoinQuery parameter.
         */
        public QueryTransformationBuildItem(
                String queryId,
                String generatedClassName,
                Class<?> entityClass,
                boolean isCountQuery,
                boolean isAggregationQuery,
                boolean isJoinQuery,
                int capturedVarCount) {
            this(queryId, generatedClassName, entityClass, isCountQuery, isAggregationQuery, isJoinQuery, false, false, false, capturedVarCount);
        }

        /**
         * Constructor with join query and selectJoined support.
         * Iteration 6.5: Added isSelectJoined parameter.
         */
        public QueryTransformationBuildItem(
                String queryId,
                String generatedClassName,
                Class<?> entityClass,
                boolean isCountQuery,
                boolean isAggregationQuery,
                boolean isJoinQuery,
                boolean isSelectJoined,
                int capturedVarCount) {
            this(queryId, generatedClassName, entityClass, isCountQuery, isAggregationQuery, isJoinQuery, isSelectJoined, false, false, capturedVarCount);
        }

        /**
         * Constructor with join query, selectJoined, and joinProjection support.
         * Iteration 6.6: Added isJoinProjection parameter.
         */
        public QueryTransformationBuildItem(
                String queryId,
                String generatedClassName,
                Class<?> entityClass,
                boolean isCountQuery,
                boolean isAggregationQuery,
                boolean isJoinQuery,
                boolean isSelectJoined,
                boolean isJoinProjection,
                int capturedVarCount) {
            this(queryId, generatedClassName, entityClass, isCountQuery, isAggregationQuery, isJoinQuery, isSelectJoined, isJoinProjection, false, capturedVarCount);
        }

        /**
         * Full constructor with all flags including group query support.
         * Iteration 7: Added isGroupQuery parameter.
         */
        public QueryTransformationBuildItem(
                String queryId,
                String generatedClassName,
                Class<?> entityClass,
                boolean isCountQuery,
                boolean isAggregationQuery,
                boolean isJoinQuery,
                boolean isSelectJoined,
                boolean isJoinProjection,
                boolean isGroupQuery,
                int capturedVarCount) {
            this.queryId = queryId;
            this.generatedClassName = generatedClassName;
            this.entityClass = entityClass;
            this.isCountQuery = isCountQuery;
            this.isAggregationQuery = isAggregationQuery;
            this.isJoinQuery = isJoinQuery;
            this.isSelectJoined = isSelectJoined;
            this.isJoinProjection = isJoinProjection;
            this.isGroupQuery = isGroupQuery;
            this.capturedVarCount = capturedVarCount;
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

        /** Returns true if this is a count query. */
        public boolean isCountQuery() {
            return isCountQuery;
        }

        /** Returns true if this is an aggregation query (Phase 5). */
        public boolean isAggregationQuery() {
            return isAggregationQuery;
        }

        /** Returns true if this is a join query (Iteration 6). */
        public boolean isJoinQuery() {
            return isJoinQuery;
        }

        /** Returns true if this is a selectJoined query (Iteration 6.5). */
        public boolean isSelectJoined() {
            return isSelectJoined;
        }

        /** Returns true if this is a join projection query (Iteration 6.6). */
        public boolean isJoinProjection() {
            return isJoinProjection;
        }

        /** Returns true if this is a group query (Iteration 7). */
        public boolean isGroupQuery() {
            return isGroupQuery;
        }

        /** Returns number of captured variables. */
        public int getCapturedVarCount() {
            return capturedVarCount;
        }
    }

}
