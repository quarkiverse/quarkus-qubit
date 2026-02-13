package io.quarkiverse.qubit.runtime.internal;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Supplier;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;

import org.jboss.logging.Logger;

/**
 * Registry of build-time generated query executors keyed by call site ID.
 * <p>
 * This registry stores query executors generated during build-time bytecode analysis
 * and provides execution methods for different query types (list, count, join, group, aggregation).
 */
@ApplicationScoped
public class QueryExecutorRegistry {

    private static final Logger LOG = Logger.getLogger(QueryExecutorRegistry.class);

    /**
     * Enumeration of all query executor types with their metadata.
     * This eliminates the need for separate maps and registration methods per type.
     */
    public enum ExecutorType {
        LIST("list", "query"),
        COUNT("count", "query"),
        AGGREGATION("aggregation", "aggregation"),
        JOIN_LIST("join list", "join"),
        JOIN_COUNT("join count", "join"),
        JOIN_SELECT_JOINED("join selectJoined", "join"),
        JOIN_PROJECTION("join projection", "join"),
        GROUP_LIST("group list", "group"),
        GROUP_COUNT("group count", "group");

        private final String displayName;
        private final String expressionType;

        ExecutorType(String displayName, String expressionType) {
            this.displayName = displayName;
            this.expressionType = expressionType;
        }

        public String displayName() {
            return displayName;
        }

        public String expressionType() {
            return expressionType;
        }
    }

    private static final Map<String, QueryExecutor<List<?>>> LIST_EXECUTORS = new ConcurrentHashMap<>();
    private static final Map<String, QueryExecutor<Long>> COUNT_EXECUTORS = new ConcurrentHashMap<>();
    private static final Map<String, QueryExecutor<Object>> AGGREGATION_EXECUTORS = new ConcurrentHashMap<>();
    private static final Map<String, QueryExecutor<List<?>>> JOIN_LIST_EXECUTORS = new ConcurrentHashMap<>();
    private static final Map<String, QueryExecutor<Long>> JOIN_COUNT_EXECUTORS = new ConcurrentHashMap<>();
    private static final Map<String, QueryExecutor<List<?>>> JOIN_SELECT_JOINED_EXECUTORS = new ConcurrentHashMap<>();
    private static final Map<String, QueryExecutor<List<?>>> JOIN_PROJECTION_EXECUTORS = new ConcurrentHashMap<>();
    private static final Map<String, QueryExecutor<List<?>>> GROUP_LIST_EXECUTORS = new ConcurrentHashMap<>();
    private static final Map<String, QueryExecutor<Long>> GROUP_COUNT_EXECUTORS = new ConcurrentHashMap<>();
    private static final Map<String, Integer> CAPTURED_VAR_COUNTS = new ConcurrentHashMap<>();

    /** RW lock for atomic clear during dev mode hot reload. Read lock for queries, write lock for clear. */
    private static final ReadWriteLock EXECUTOR_LOCK = new ReentrantReadWriteLock();

    // Format strings for executor count reporting (eliminates duplication)
    private static final String COUNT_FORMAT_STANDARD = "%d list, %d count";
    private static final String COUNT_FORMAT_WITH_AGGREGATION = "%d list, %d count, %d aggregation";
    private static final String COUNT_FORMAT_JOIN = "%d list, %d count, %d join list, %d join count";
    private static final String COUNT_FORMAT_JOIN_SELECT = "%d list, %d count, %d join list, %d join count, %d join selectJoined";
    private static final String COUNT_FORMAT_JOIN_PROJECTION = "%d list, %d count, %d join list, %d join count, %d join selectJoined, %d join projection";
    private static final String COUNT_FORMAT_GROUP = "%d list, %d count, %d group list, %d group count";

    @Inject
    EntityManager entityManager;

    /** Registers executor with write lock for atomic executor+captured-var-count update. */
    private static <T> void registerExecutor(
            String callSiteId,
            QueryExecutor<T> executor,
            int capturedVarCount,
            Map<String, QueryExecutor<T>> executorMap,
            ExecutorType type) {
        EXECUTOR_LOCK.writeLock().lock();
        try {
            executorMap.put(callSiteId, executor);
            CAPTURED_VAR_COUNTS.put(callSiteId, capturedVarCount);
            LOG.debugf("Registered %s executor for call site: %s (captured variables: %d)",
                    type.displayName(), callSiteId, capturedVarCount);
        } finally {
            EXECUTOR_LOCK.writeLock().unlock();
        }
    }

    /** Looks up executor with read lock; throws if not found. */
    private <T> QueryExecutor<T> getExecutor(
            Map<String, QueryExecutor<T>> executorMap,
            String callSiteId,
            ExecutorType type,
            Supplier<String> countFormat) {
        EXECUTOR_LOCK.readLock().lock();
        try {
            QueryExecutor<T> executor = executorMap.get(callSiteId);
            if (executor == null) {
                throw new IllegalStateException(buildExecutorNotFoundError(callSiteId, type, countFormat));
            }
            return executor;
        } finally {
            EXECUTOR_LOCK.readLock().unlock();
        }
    }

    /** Returns map size with read lock. */
    private static int getExecutorCount(Map<String, ?> executorMap) {
        EXECUTOR_LOCK.readLock().lock();
        try {
            return executorMap.size();
        } finally {
            EXECUTOR_LOCK.readLock().unlock();
        }
    }

    /** Builds error message for missing executor. */
    private static String buildExecutorNotFoundError(String callSiteId, ExecutorType type, Supplier<String> registeredCounts) {
        return String.format(
                "No %s executor found for call site: %s%n" +
                        "%n" +
                        "Possible causes:%n" +
                        "  1. %s expression was not analyzed during build-time processing%n" +
                        "  2. Lambda is in test code (only application code is analyzed)%n" +
                        "  3. Incremental compilation didn't detect changes%n" +
                        "%n" +
                        "Solutions:%n" +
                        "  - Run a clean build: 'mvn clean compile' or 'gradle clean build'%n" +
                        "  - Check build logs for 'QubitProcessor' messages%n" +
                        "  - Verify lambda is in src/main/java (not src/test/java)%n" +
                        "  - Ensure query is reachable from application code%n" +
                        "%n" +
                        "Registered executors: %s",
                type.displayName(),
                callSiteId,
                capitalize(type.expressionType()),
                registeredCounts.get());
    }

    private static String capitalize(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    /** Fail-fast check for EntityManager availability. */
    private void requireEntityManager() {
        if (entityManager == null) {
            throw new IllegalStateException(
                    "EntityManager not available. Ensure CDI injection is working and " +
                            "you are within a transaction context.");
        }
    }

    /**
     * Registers list query executor for call site.
     */
    public static void registerListExecutor(String callSiteId, QueryExecutor<List<?>> executor, int capturedVarCount) {
        registerExecutor(callSiteId, executor, capturedVarCount, LIST_EXECUTORS, ExecutorType.LIST);
    }

    /**
     * Registers count query executor for call site.
     */
    public static void registerCountExecutor(String callSiteId, QueryExecutor<Long> executor, int capturedVarCount) {
        registerExecutor(callSiteId, executor, capturedVarCount, COUNT_EXECUTORS, ExecutorType.COUNT);
    }

    /**
     * Registers aggregation query executor for call site (MIN, MAX, AVG, SUM).
     */
    public static void registerAggregationExecutor(String callSiteId, QueryExecutor<Object> executor, int capturedVarCount) {
        registerExecutor(callSiteId, executor, capturedVarCount, AGGREGATION_EXECUTORS, ExecutorType.AGGREGATION);
    }

    /** Returns captured var count with read lock; throws if not registered (fail-fast). */
    public static int getCapturedVariableCount(String callSiteId) {
        EXECUTOR_LOCK.readLock().lock();
        try {
            Integer count = CAPTURED_VAR_COUNTS.get(callSiteId);
            if (count == null) {
                throw new IllegalStateException(String.format(
                        "No captured variable count registered for call site: %s%n" +
                                "%n" +
                                "This typically indicates:%n" +
                                "  1. The query was not analyzed during build-time processing%n" +
                                "  2. Dev mode hot reload cleared executors before re-registration completed%n" +
                                "  3. Call site ID mismatch between build-time and runtime%n" +
                                "%n" +
                                "Solutions:%n" +
                                "  - Run a clean build: 'mvn clean compile'%n" +
                                "  - If in dev mode, retry the request after reload completes%n" +
                                "  - Check build logs for QubitProcessor registration messages",
                        callSiteId));
            }
            return count;
        } finally {
            EXECUTOR_LOCK.readLock().unlock();
        }
    }

    /** Executes list query (lock held only during lookup, not DB execution). */
    @SuppressWarnings("unchecked")
    public <T> List<T> executeListQuery(String callSiteId, Class<T> entityClass, Object[] capturedValues,
            Integer offset, Integer limit, Boolean distinct) {
        requireEntityManager();

        QueryExecutor<List<?>> executor = getExecutor(LIST_EXECUTORS, callSiteId, ExecutorType.LIST,
                () -> String.format(COUNT_FORMAT_STANDARD, getListExecutorCount(), getCountExecutorCount()));

        if (LOG.isTraceEnabled()) {
            LOG.tracef("Executing list query for call site: %s with %d captured variables (offset=%s, limit=%s, distinct=%s)",
                    callSiteId, capturedValues.length, offset, limit, distinct);
        }

        return (List<T>) executor.execute(entityManager, entityClass, capturedValues, offset, limit, distinct);
    }

    /**
     * Executes count query for call site.
     */
    public <T> long executeCountQuery(String callSiteId, Class<T> entityClass, Object[] capturedValues) {
        requireEntityManager();

        QueryExecutor<Long> executor = getExecutor(COUNT_EXECUTORS, callSiteId, ExecutorType.COUNT,
                () -> String.format(COUNT_FORMAT_STANDARD, getListExecutorCount(), getCountExecutorCount()));

        if (LOG.isTraceEnabled()) {
            LOG.tracef("Executing count query for call site: %s with %d captured variables",
                    callSiteId, capturedValues.length);
        }

        return executor.execute(entityManager, entityClass, capturedValues, null, null, null);
    }

    /**
     * Executes exists query for call site.
     */
    public <T> boolean executeExistsQuery(String callSiteId, Class<T> entityClass, Object[] capturedValues) {
        return executeCountQuery(callSiteId, entityClass, capturedValues) > 0;
    }

    /**
     * Executes aggregation query for call site (MIN, MAX, AVG, SUM).
     */
    @SuppressWarnings("unchecked")
    public <T, R> R executeAggregationQuery(String callSiteId, Class<T> entityClass, Object[] capturedValues) {
        requireEntityManager();

        QueryExecutor<Object> executor = getExecutor(AGGREGATION_EXECUTORS, callSiteId, ExecutorType.AGGREGATION,
                () -> String.format(COUNT_FORMAT_WITH_AGGREGATION,
                        getListExecutorCount(), getCountExecutorCount(), getAggregationExecutorCount()));

        if (LOG.isTraceEnabled()) {
            LOG.tracef("Executing aggregation query for call site: %s with %d captured variables",
                    callSiteId, capturedValues.length);
        }

        return (R) executor.execute(entityManager, entityClass, capturedValues, null, null, null);
    }

    // Acquires read locks to prevent data races with clearAllExecutors() during dev mode hot reload.

    public static int getListExecutorCount() {
        return getExecutorCount(LIST_EXECUTORS);
    }

    public static int getCountExecutorCount() {
        return getExecutorCount(COUNT_EXECUTORS);
    }

    public static int getAggregationExecutorCount() {
        return getExecutorCount(AGGREGATION_EXECUTORS);
    }

    public static int getJoinListExecutorCount() {
        return getExecutorCount(JOIN_LIST_EXECUTORS);
    }

    public static int getJoinCountExecutorCount() {
        return getExecutorCount(JOIN_COUNT_EXECUTORS);
    }

    public static int getJoinSelectJoinedExecutorCount() {
        return getExecutorCount(JOIN_SELECT_JOINED_EXECUTORS);
    }

    public static int getJoinProjectionExecutorCount() {
        return getExecutorCount(JOIN_PROJECTION_EXECUTORS);
    }

    public static int getGroupListExecutorCount() {
        return getExecutorCount(GROUP_LIST_EXECUTORS);
    }

    public static int getGroupCountExecutorCount() {
        return getExecutorCount(GROUP_COUNT_EXECUTORS);
    }

    /**
     * Registers join list query executor for call site (join() and leftJoin()).
     */
    public static void registerJoinListExecutor(String callSiteId, QueryExecutor<List<?>> executor, int capturedVarCount) {
        registerExecutor(callSiteId, executor, capturedVarCount, JOIN_LIST_EXECUTORS, ExecutorType.JOIN_LIST);
    }

    /**
     * Registers join count query executor for call site.
     */
    public static void registerJoinCountExecutor(String callSiteId, QueryExecutor<Long> executor, int capturedVarCount) {
        registerExecutor(callSiteId, executor, capturedVarCount, JOIN_COUNT_EXECUTORS, ExecutorType.JOIN_COUNT);
    }

    /**
     * Registers join selectJoined query executor for call site.
     */
    public static void registerJoinSelectJoinedExecutor(String callSiteId, QueryExecutor<List<?>> executor,
            int capturedVarCount) {
        registerExecutor(callSiteId, executor, capturedVarCount, JOIN_SELECT_JOINED_EXECUTORS, ExecutorType.JOIN_SELECT_JOINED);
    }

    /**
     * Registers join projection query executor for call site.
     */
    public static void registerJoinProjectionExecutor(String callSiteId, QueryExecutor<List<?>> executor,
            int capturedVarCount) {
        registerExecutor(callSiteId, executor, capturedVarCount, JOIN_PROJECTION_EXECUTORS, ExecutorType.JOIN_PROJECTION);
    }

    /**
     * Executes join list query for call site, returning source entities.
     */
    @SuppressWarnings("unchecked")
    public <T> List<T> executeJoinListQuery(String callSiteId, Class<T> entityClass, Object[] capturedValues,
            Integer offset, Integer limit, Boolean distinct) {
        requireEntityManager();

        QueryExecutor<List<?>> executor = getExecutor(JOIN_LIST_EXECUTORS, callSiteId, ExecutorType.JOIN_LIST,
                () -> String.format(COUNT_FORMAT_JOIN,
                        getListExecutorCount(), getCountExecutorCount(),
                        getJoinListExecutorCount(), getJoinCountExecutorCount()));

        if (LOG.isTraceEnabled()) {
            LOG.tracef(
                    "Executing join list query for call site: %s with %d captured variables (offset=%s, limit=%s, distinct=%s)",
                    callSiteId, capturedValues.length, offset, limit, distinct);
        }

        return (List<T>) executor.execute(entityManager, entityClass, capturedValues, offset, limit, distinct);
    }

    /**
     * Executes join count query for call site.
     */
    public <T> long executeJoinCountQuery(String callSiteId, Class<T> entityClass, Object[] capturedValues) {
        requireEntityManager();

        QueryExecutor<Long> executor = getExecutor(JOIN_COUNT_EXECUTORS, callSiteId, ExecutorType.JOIN_COUNT,
                () -> String.format(COUNT_FORMAT_JOIN,
                        getListExecutorCount(), getCountExecutorCount(),
                        getJoinListExecutorCount(), getJoinCountExecutorCount()));

        if (LOG.isTraceEnabled()) {
            LOG.tracef("Executing join count query for call site: %s with %d captured variables",
                    callSiteId, capturedValues.length);
        }

        return executor.execute(entityManager, entityClass, capturedValues, null, null, null);
    }

    /**
     * Executes join selectJoined query for call site, returning joined entities.
     */
    @SuppressWarnings("unchecked")
    public <T, R> List<R> executeJoinSelectJoinedQuery(String callSiteId, Class<T> entityClass, Object[] capturedValues,
            Integer offset, Integer limit, Boolean distinct) {
        requireEntityManager();

        QueryExecutor<List<?>> executor = getExecutor(JOIN_SELECT_JOINED_EXECUTORS, callSiteId, ExecutorType.JOIN_SELECT_JOINED,
                () -> String.format(COUNT_FORMAT_JOIN_SELECT,
                        getListExecutorCount(), getCountExecutorCount(),
                        getJoinListExecutorCount(), getJoinCountExecutorCount(), getJoinSelectJoinedExecutorCount()));

        if (LOG.isTraceEnabled()) {
            LOG.tracef(
                    "Executing join selectJoined query for call site: %s with %d captured variables (offset=%s, limit=%s, distinct=%s)",
                    callSiteId, capturedValues.length, offset, limit, distinct);
        }

        return (List<R>) executor.execute(entityManager, entityClass, capturedValues, offset, limit, distinct);
    }

    /**
     * Executes join projection query for call site, returning projected objects from both entities.
     */
    @SuppressWarnings("unchecked")
    public <T, S> List<S> executeJoinProjectionQuery(String callSiteId, Class<T> entityClass, Object[] capturedValues,
            Integer offset, Integer limit, Boolean distinct) {
        requireEntityManager();

        QueryExecutor<List<?>> executor = getExecutor(JOIN_PROJECTION_EXECUTORS, callSiteId, ExecutorType.JOIN_PROJECTION,
                () -> String.format(COUNT_FORMAT_JOIN_PROJECTION,
                        getListExecutorCount(), getCountExecutorCount(),
                        getJoinListExecutorCount(), getJoinCountExecutorCount(),
                        getJoinSelectJoinedExecutorCount(), getJoinProjectionExecutorCount()));

        if (LOG.isTraceEnabled()) {
            LOG.tracef(
                    "Executing join projection query for call site: %s with %d captured variables (offset=%s, limit=%s, distinct=%s)",
                    callSiteId, capturedValues.length, offset, limit, distinct);
        }

        return (List<S>) executor.execute(entityManager, entityClass, capturedValues, offset, limit, distinct);
    }

    /**
     * Registers group list query executor for call site.
     */
    public static void registerGroupListExecutor(String callSiteId, QueryExecutor<List<?>> executor, int capturedVarCount) {
        registerExecutor(callSiteId, executor, capturedVarCount, GROUP_LIST_EXECUTORS, ExecutorType.GROUP_LIST);
    }

    /**
     * Registers group count query executor for call site (counts number of groups).
     */
    public static void registerGroupCountExecutor(String callSiteId, QueryExecutor<Long> executor, int capturedVarCount) {
        registerExecutor(callSiteId, executor, capturedVarCount, GROUP_COUNT_EXECUTORS, ExecutorType.GROUP_COUNT);
    }

    /**
     * Executes group list query for call site with projection.
     */
    @SuppressWarnings("unchecked")
    public <T, R> List<R> executeGroupQuery(String callSiteId, Class<T> entityClass, Object[] capturedValues,
            Integer offset, Integer limit) {
        requireEntityManager();

        QueryExecutor<List<?>> executor = getExecutor(GROUP_LIST_EXECUTORS, callSiteId, ExecutorType.GROUP_LIST,
                () -> String.format(COUNT_FORMAT_GROUP,
                        getListExecutorCount(), getCountExecutorCount(),
                        getGroupListExecutorCount(), getGroupCountExecutorCount()));

        if (LOG.isTraceEnabled()) {
            LOG.tracef("Executing group list query for call site: %s with %d captured variables (offset=%s, limit=%s)",
                    callSiteId, capturedValues.length, offset, limit);
        }

        List<?> rawResults = executor.execute(entityManager, entityClass, capturedValues, offset, limit, null);

        // Convert Tuple results to Object[] if needed (for Object[] projections)
        if (!rawResults.isEmpty() && rawResults.getFirst() instanceof Tuple) {
            return (List<R>) rawResults.stream()
                    .map(o -> ((Tuple) o).toArray())
                    .toList();
        }

        return (List<R>) rawResults;
    }

    /**
     * Executes group key query for call site (returns only grouping keys).
     */
    @SuppressWarnings("unchecked")
    public <T, K> List<K> executeGroupKeyQuery(String callSiteId, Class<T> entityClass, Object[] capturedValues,
            Integer offset, Integer limit) {
        requireEntityManager();

        QueryExecutor<List<?>> executor = getExecutor(GROUP_LIST_EXECUTORS, callSiteId, ExecutorType.GROUP_LIST,
                () -> String.format(COUNT_FORMAT_GROUP,
                        getListExecutorCount(), getCountExecutorCount(),
                        getGroupListExecutorCount(), getGroupCountExecutorCount()));

        if (LOG.isTraceEnabled()) {
            LOG.tracef("Executing group key query for call site: %s with %d captured variables (offset=%s, limit=%s)",
                    callSiteId, capturedValues.length, offset, limit);
        }

        return (List<K>) executor.execute(entityManager, entityClass, capturedValues, offset, limit, null);
    }

    /**
     * Executes group count query for call site (counts number of groups).
     */
    public <T> long executeGroupCountQuery(String callSiteId, Class<T> entityClass, Object[] capturedValues) {
        requireEntityManager();

        QueryExecutor<Long> executor = getExecutor(GROUP_COUNT_EXECUTORS, callSiteId, ExecutorType.GROUP_COUNT,
                () -> String.format(COUNT_FORMAT_GROUP,
                        getListExecutorCount(), getCountExecutorCount(),
                        getGroupListExecutorCount(), getGroupCountExecutorCount()));

        if (LOG.isTraceEnabled()) {
            LOG.tracef("Executing group count query for call site: %s with %d captured variables",
                    callSiteId, capturedValues.length);
        }

        return executor.execute(entityManager, entityClass, capturedValues, null, null, null);
    }

    /** Clears all executors (dev mode hot reload). Acquires exclusive write lock. */
    public static void clearAllExecutors() {
        EXECUTOR_LOCK.writeLock().lock();
        try {
            int totalCleared = LIST_EXECUTORS.size() + COUNT_EXECUTORS.size() +
                    AGGREGATION_EXECUTORS.size() + JOIN_LIST_EXECUTORS.size() +
                    JOIN_COUNT_EXECUTORS.size() + JOIN_SELECT_JOINED_EXECUTORS.size() +
                    JOIN_PROJECTION_EXECUTORS.size() + GROUP_LIST_EXECUTORS.size() +
                    GROUP_COUNT_EXECUTORS.size() + CAPTURED_VAR_COUNTS.size();

            LIST_EXECUTORS.clear();
            COUNT_EXECUTORS.clear();
            AGGREGATION_EXECUTORS.clear();
            JOIN_LIST_EXECUTORS.clear();
            JOIN_COUNT_EXECUTORS.clear();
            JOIN_SELECT_JOINED_EXECUTORS.clear();
            JOIN_PROJECTION_EXECUTORS.clear();
            GROUP_LIST_EXECUTORS.clear();
            GROUP_COUNT_EXECUTORS.clear();
            CAPTURED_VAR_COUNTS.clear();

            LOG.debugf("Cleared %d executor registrations (dev mode reload)", totalCleared);
        } finally {
            EXECUTOR_LOCK.writeLock().unlock();
        }
    }

    /** Returns total executor count across all types (with read lock). */
    public static int getTotalExecutorCount() {
        EXECUTOR_LOCK.readLock().lock();
        try {
            return LIST_EXECUTORS.size() + COUNT_EXECUTORS.size() +
                    AGGREGATION_EXECUTORS.size() + JOIN_LIST_EXECUTORS.size() +
                    JOIN_COUNT_EXECUTORS.size() + JOIN_SELECT_JOINED_EXECUTORS.size() +
                    JOIN_PROJECTION_EXECUTORS.size() + GROUP_LIST_EXECUTORS.size() +
                    GROUP_COUNT_EXECUTORS.size();
        } finally {
            EXECUTOR_LOCK.readLock().unlock();
        }
    }

    /**
     * Returns total count of registered executors for performance metrics.
     * Alias for getTotalExecutorCount() for clarity in metrics context.
     */
    public static int getRegisteredExecutorCount() {
        return getTotalExecutorCount();
    }
}
