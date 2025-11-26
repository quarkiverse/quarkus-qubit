package io.quarkus.qusaq.runtime;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.jboss.logging.Logger;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry of build-time generated query executors keyed by call site ID.
 */
@ApplicationScoped
public class QueryExecutorRegistry {

    private static final Logger log = Logger.getLogger(QueryExecutorRegistry.class);

    private static final Map<String, QueryExecutor<List<?>>> LIST_EXECUTORS = new ConcurrentHashMap<>();
    private static final Map<String, QueryExecutor<Long>> COUNT_EXECUTORS = new ConcurrentHashMap<>();
    private static final Map<String, QueryExecutor<Object>> AGGREGATION_EXECUTORS = new ConcurrentHashMap<>();
    private static final Map<String, QueryExecutor<List<?>>> JOIN_LIST_EXECUTORS = new ConcurrentHashMap<>();
    private static final Map<String, QueryExecutor<Long>> JOIN_COUNT_EXECUTORS = new ConcurrentHashMap<>();
    private static final Map<String, QueryExecutor<List<?>>> JOIN_SELECT_JOINED_EXECUTORS = new ConcurrentHashMap<>();
    private static final Map<String, QueryExecutor<List<?>>> GROUP_LIST_EXECUTORS = new ConcurrentHashMap<>();
    private static final Map<String, QueryExecutor<Long>> GROUP_COUNT_EXECUTORS = new ConcurrentHashMap<>();
    private static final Map<String, Integer> CAPTURED_VAR_COUNTS = new ConcurrentHashMap<>();

    @Inject
    EntityManager entityManager;

    /**
     * Registers list query executor for call site.
     */
    public static void registerListExecutor(
            String callSiteId,
            QueryExecutor<List<?>> executor,
            int capturedVarCount) {
        LIST_EXECUTORS.put(callSiteId, executor);
        CAPTURED_VAR_COUNTS.put(callSiteId, capturedVarCount);
        log.debugf("Registered list executor for call site: %s (captured variables: %d)",
                   callSiteId, capturedVarCount);
    }

    /**
     * Registers count query executor for call site.
     */
    public static void registerCountExecutor(
            String callSiteId,
            QueryExecutor<Long> executor,
            int capturedVarCount) {
        COUNT_EXECUTORS.put(callSiteId, executor);
        CAPTURED_VAR_COUNTS.put(callSiteId, capturedVarCount);
        log.debugf("Registered count executor for call site: %s (captured variables: %d)",
                   callSiteId, capturedVarCount);
    }

    /**
     * Registers aggregation query executor for call site.
     * Phase 5: Supports MIN, MAX, AVG, SUM* aggregation operations.
     */
    public static void registerAggregationExecutor(
            String callSiteId,
            QueryExecutor<Object> executor,
            int capturedVarCount) {
        AGGREGATION_EXECUTORS.put(callSiteId, executor);
        CAPTURED_VAR_COUNTS.put(callSiteId, capturedVarCount);
        log.debugf("Registered aggregation executor for call site: %s (captured variables: %d)",
                   callSiteId, capturedVarCount);
    }

    /**
     * Returns number of captured variables for call site.
     */
    public static int getCapturedVariableCount(String callSiteId) {
        return CAPTURED_VAR_COUNTS.getOrDefault(callSiteId, 0);
    }

    /**
     * Executes list query for call site.
     * Phase 4: Added offset, limit, and distinct parameters for pagination and deduplication support.
     */
    @SuppressWarnings("unchecked")
    public <T> List<T> executeListQuery(String callSiteId, Class<T> entityClass, Object[] capturedValues,
                                         Integer offset, Integer limit, Boolean distinct) {
        QueryExecutor<List<?>> executor = LIST_EXECUTORS.get(callSiteId);

        if (executor == null) {
            throw new IllegalStateException(String.format(
                    "No query executor found for call site: %s%n" +
                    "%n" +
                    "Possible causes:%n" +
                    "  1. Lambda expression was not analyzed during build-time processing%n" +
                    "  2. Lambda is in test code (only application code is analyzed)%n" +
                    "  3. Incremental compilation didn't detect changes%n" +
                    "%n" +
                    "Solutions:%n" +
                    "  - Run a clean build: 'mvn clean compile' or 'gradle clean build'%n" +
                    "  - Check build logs for 'QusaqProcessor' messages%n" +
                    "  - Verify lambda is in src/main/java (not src/test/java)%n" +
                    "  - Ensure query is reachable from application code%n" +
                    "%n" +
                    "Registered executors: %d list, %d count",
                    callSiteId, getListExecutorCount(), getCountExecutorCount()));
        }

        if (entityManager == null) {
            throw new IllegalStateException("EntityManager not available");
        }

        log.tracef("Executing list query for call site: %s with %d captured variables (offset=%s, limit=%s, distinct=%s)",
                   callSiteId, capturedValues.length, offset, limit, distinct);

        // Execute query and apply pagination and distinct parameters
        return (List<T>) executor.execute(entityManager, entityClass, capturedValues, offset, limit, distinct);
    }

    /**
     * Executes count query for call site.
     */
    public <T> long executeCountQuery(String callSiteId, Class<T> entityClass, Object[] capturedValues) {
        QueryExecutor<Long> executor = COUNT_EXECUTORS.get(callSiteId);

        if (executor == null) {
            throw new IllegalStateException(String.format(
                    "No query executor found for call site: %s%n" +
                    "%n" +
                    "Possible causes:%n" +
                    "  1. Lambda expression was not analyzed during build-time processing%n" +
                    "  2. Lambda is in test code (only application code is analyzed)%n" +
                    "  3. Incremental compilation didn't detect changes%n" +
                    "%n" +
                    "Solutions:%n" +
                    "  - Run a clean build: 'mvn clean compile' or 'gradle clean build'%n" +
                    "  - Check build logs for 'QusaqProcessor' messages%n" +
                    "  - Verify lambda is in src/main/java (not src/test/java)%n" +
                    "  - Ensure query is reachable from application code%n" +
                    "%n" +
                    "Registered executors: %d list, %d count",
                    callSiteId, getListExecutorCount(), getCountExecutorCount()));
        }

        if (entityManager == null) {
            throw new IllegalStateException("EntityManager not available");
        }

        log.tracef("Executing count query for call site: %s with %d captured variables",
                   callSiteId, capturedValues.length);

        // Count queries don't use pagination or distinct (count is always distinct counts)
        return executor.execute(entityManager, entityClass, capturedValues, null, null, null);
    }

    /**
     * Executes exists query for call site.
     */
    public <T> boolean executeExistsQuery(String callSiteId, Class<T> entityClass, Object[] capturedValues) {
        return executeCountQuery(callSiteId, entityClass, capturedValues) > 0;
    }

    /**
     * Executes aggregation query for call site.
     * Phase 5: Supports MIN, MAX, AVG, SUM* aggregation operations.
     *
     * @param callSiteId Unique identifier for the call site
     * @param entityClass Entity class being queried
     * @param capturedValues Captured variables from lambda expressions
     * @param <T> Entity type
     * @param <R> Result type (e.g., Double for avg, Long for sum, Object for min/max)
     * @return Aggregation result
     */
    @SuppressWarnings("unchecked")
    public <T, R> R executeAggregationQuery(String callSiteId, Class<T> entityClass, Object[] capturedValues) {
        QueryExecutor<Object> executor = AGGREGATION_EXECUTORS.get(callSiteId);

        if (executor == null) {
            throw new IllegalStateException(String.format(
                    "No aggregation executor found for call site: %s%n" +
                    "%n" +
                    "Possible causes:%n" +
                    "  1. Lambda expression was not analyzed during build-time processing%n" +
                    "  2. Lambda is in test code (only application code is analyzed)%n" +
                    "  3. Incremental compilation didn't detect changes%n" +
                    "%n" +
                    "Solutions:%n" +
                    "  - Run a clean build: 'mvn clean compile' or 'gradle clean build'%n" +
                    "  - Check build logs for 'QusaqProcessor' messages%n" +
                    "  - Verify lambda is in src/main/java (not src/test/java)%n" +
                    "  - Ensure query is reachable from application code%n" +
                    "%n" +
                    "Registered executors: %d list, %d count, %d aggregation",
                    callSiteId, getListExecutorCount(), getCountExecutorCount(), getAggregationExecutorCount()));
        }

        if (entityManager == null) {
            throw new IllegalStateException("EntityManager not available");
        }

        log.tracef("Executing aggregation query for call site: %s with %d captured variables",
                   callSiteId, capturedValues.length);

        // Aggregation queries don't use pagination or distinct
        Object result = executor.execute(entityManager, entityClass, capturedValues, null, null, null);
        return (R) result;
    }

    /**
     * Returns number of registered list executors.
     */
    public static int getListExecutorCount() {
        return LIST_EXECUTORS.size();
    }

    /**
     * Returns number of registered count executors.
     */
    public static int getCountExecutorCount() {
        return COUNT_EXECUTORS.size();
    }

    /**
     * Returns number of registered aggregation executors.
     * Phase 5: Tracks MIN, MAX, AVG, SUM* executors.
     */
    public static int getAggregationExecutorCount() {
        return AGGREGATION_EXECUTORS.size();
    }

    // =============================================================================================
    // JOIN QUERY SUPPORT (Iteration 6)
    // =============================================================================================

    /**
     * Registers join list query executor for call site.
     * Iteration 6: Supports join() and leftJoin() operations.
     */
    public static void registerJoinListExecutor(
            String callSiteId,
            QueryExecutor<List<?>> executor,
            int capturedVarCount) {
        JOIN_LIST_EXECUTORS.put(callSiteId, executor);
        CAPTURED_VAR_COUNTS.put(callSiteId, capturedVarCount);
        log.debugf("Registered join list executor for call site: %s (captured variables: %d)",
                   callSiteId, capturedVarCount);
    }

    /**
     * Registers join count query executor for call site.
     * Iteration 6: Supports count() on join queries.
     */
    public static void registerJoinCountExecutor(
            String callSiteId,
            QueryExecutor<Long> executor,
            int capturedVarCount) {
        JOIN_COUNT_EXECUTORS.put(callSiteId, executor);
        CAPTURED_VAR_COUNTS.put(callSiteId, capturedVarCount);
        log.debugf("Registered join count executor for call site: %s (captured variables: %d)",
                   callSiteId, capturedVarCount);
    }

    /**
     * Executes join list query for call site.
     * Iteration 6: Handles join() and leftJoin() queries returning source entities.
     */
    @SuppressWarnings("unchecked")
    public <T> List<T> executeJoinListQuery(String callSiteId, Class<T> entityClass, Object[] capturedValues,
                                             Integer offset, Integer limit, Boolean distinct) {
        QueryExecutor<List<?>> executor = JOIN_LIST_EXECUTORS.get(callSiteId);

        if (executor == null) {
            throw new IllegalStateException(String.format(
                    "No join query executor found for call site: %s%n" +
                    "%n" +
                    "Possible causes:%n" +
                    "  1. Join expression was not analyzed during build-time processing%n" +
                    "  2. Lambda is in test code (only application code is analyzed)%n" +
                    "  3. Incremental compilation didn't detect changes%n" +
                    "%n" +
                    "Solutions:%n" +
                    "  - Run a clean build: 'mvn clean compile' or 'gradle clean build'%n" +
                    "  - Check build logs for 'QusaqProcessor' messages%n" +
                    "  - Verify lambda is in src/main/java (not src/test/java)%n" +
                    "%n" +
                    "Registered executors: %d list, %d count, %d join list, %d join count",
                    callSiteId, getListExecutorCount(), getCountExecutorCount(),
                    getJoinListExecutorCount(), getJoinCountExecutorCount()));
        }

        if (entityManager == null) {
            throw new IllegalStateException("EntityManager not available");
        }

        log.tracef("Executing join list query for call site: %s with %d captured variables (offset=%s, limit=%s, distinct=%s)",
                   callSiteId, capturedValues.length, offset, limit, distinct);

        return (List<T>) executor.execute(entityManager, entityClass, capturedValues, offset, limit, distinct);
    }

    /**
     * Executes join count query for call site.
     * Iteration 6: Handles count() on join queries.
     */
    public <T> long executeJoinCountQuery(String callSiteId, Class<T> entityClass, Object[] capturedValues) {
        QueryExecutor<Long> executor = JOIN_COUNT_EXECUTORS.get(callSiteId);

        if (executor == null) {
            throw new IllegalStateException(String.format(
                    "No join count executor found for call site: %s%n" +
                    "%n" +
                    "Possible causes:%n" +
                    "  1. Join expression was not analyzed during build-time processing%n" +
                    "  2. Lambda is in test code (only application code is analyzed)%n" +
                    "  3. Incremental compilation didn't detect changes%n" +
                    "%n" +
                    "Solutions:%n" +
                    "  - Run a clean build: 'mvn clean compile' or 'gradle clean build'%n" +
                    "  - Check build logs for 'QusaqProcessor' messages%n" +
                    "  - Verify lambda is in src/main/java (not src/test/java)%n" +
                    "%n" +
                    "Registered executors: %d list, %d count, %d join list, %d join count",
                    callSiteId, getListExecutorCount(), getCountExecutorCount(),
                    getJoinListExecutorCount(), getJoinCountExecutorCount()));
        }

        if (entityManager == null) {
            throw new IllegalStateException("EntityManager not available");
        }

        log.tracef("Executing join count query for call site: %s with %d captured variables",
                   callSiteId, capturedValues.length);

        return executor.execute(entityManager, entityClass, capturedValues, null, null, null);
    }

    /**
     * Returns number of registered join list executors.
     */
    public static int getJoinListExecutorCount() {
        return JOIN_LIST_EXECUTORS.size();
    }

    /**
     * Returns number of registered join count executors.
     */
    public static int getJoinCountExecutorCount() {
        return JOIN_COUNT_EXECUTORS.size();
    }

    /**
     * Registers join selectJoined query executor for call site.
     * Iteration 6.5: Supports selectJoined() operations returning joined entities.
     */
    public static void registerJoinSelectJoinedExecutor(
            String callSiteId,
            QueryExecutor<List<?>> executor,
            int capturedVarCount) {
        JOIN_SELECT_JOINED_EXECUTORS.put(callSiteId, executor);
        CAPTURED_VAR_COUNTS.put(callSiteId, capturedVarCount);
        log.debugf("Registered join selectJoined executor for call site: %s (captured variables: %d)",
                   callSiteId, capturedVarCount);
    }

    /**
     * Executes join selectJoined query for call site.
     * Iteration 6.5: Handles selectJoined() queries returning joined entities instead of source entities.
     */
    @SuppressWarnings("unchecked")
    public <T, R> List<R> executeJoinSelectJoinedQuery(String callSiteId, Class<T> entityClass, Object[] capturedValues,
                                                        Integer offset, Integer limit, Boolean distinct) {
        QueryExecutor<List<?>> executor = JOIN_SELECT_JOINED_EXECUTORS.get(callSiteId);

        if (executor == null) {
            throw new IllegalStateException(String.format(
                    "No join selectJoined executor found for call site: %s%n" +
                    "%n" +
                    "Possible causes:%n" +
                    "  1. Join expression was not analyzed during build-time processing%n" +
                    "  2. Lambda is in test code (only application code is analyzed)%n" +
                    "  3. Incremental compilation didn't detect changes%n" +
                    "%n" +
                    "Solutions:%n" +
                    "  - Run a clean build: 'mvn clean compile' or 'gradle clean build'%n" +
                    "  - Check build logs for 'QusaqProcessor' messages%n" +
                    "  - Verify lambda is in src/main/java (not src/test/java)%n" +
                    "%n" +
                    "Registered executors: %d list, %d count, %d join list, %d join count, %d join selectJoined",
                    callSiteId, getListExecutorCount(), getCountExecutorCount(),
                    getJoinListExecutorCount(), getJoinCountExecutorCount(), getJoinSelectJoinedExecutorCount()));
        }

        if (entityManager == null) {
            throw new IllegalStateException("EntityManager not available");
        }

        log.tracef("Executing join selectJoined query for call site: %s with %d captured variables (offset=%s, limit=%s, distinct=%s)",
                   callSiteId, capturedValues.length, offset, limit, distinct);

        return (List<R>) executor.execute(entityManager, entityClass, capturedValues, offset, limit, distinct);
    }

    /**
     * Returns number of registered join selectJoined executors.
     */
    public static int getJoinSelectJoinedExecutorCount() {
        return JOIN_SELECT_JOINED_EXECUTORS.size();
    }

    // =============================================================================================
    // GROUP QUERY SUPPORT (Iteration 7)
    // =============================================================================================

    /**
     * Registers group list query executor for call site.
     * Iteration 7: Supports groupBy() operations with projections.
     */
    public static void registerGroupListExecutor(
            String callSiteId,
            QueryExecutor<List<?>> executor,
            int capturedVarCount) {
        GROUP_LIST_EXECUTORS.put(callSiteId, executor);
        CAPTURED_VAR_COUNTS.put(callSiteId, capturedVarCount);
        log.debugf("Registered group list executor for call site: %s (captured variables: %d)",
                   callSiteId, capturedVarCount);
    }

    /**
     * Registers group count query executor for call site.
     * Iteration 7: Supports count() on group queries (counts number of groups).
     */
    public static void registerGroupCountExecutor(
            String callSiteId,
            QueryExecutor<Long> executor,
            int capturedVarCount) {
        GROUP_COUNT_EXECUTORS.put(callSiteId, executor);
        CAPTURED_VAR_COUNTS.put(callSiteId, capturedVarCount);
        log.debugf("Registered group count executor for call site: %s (captured variables: %d)",
                   callSiteId, capturedVarCount);
    }

    /**
     * Executes group list query for call site with projection.
     * Iteration 7: Handles groupBy().select() queries returning projected results.
     */
    @SuppressWarnings("unchecked")
    public <T, R> List<R> executeGroupQuery(String callSiteId, Class<T> entityClass, Object[] capturedValues,
                                             Integer offset, Integer limit) {
        QueryExecutor<List<?>> executor = GROUP_LIST_EXECUTORS.get(callSiteId);

        if (executor == null) {
            throw new IllegalStateException(String.format(
                    "No group query executor found for call site: %s%n" +
                    "%n" +
                    "Possible causes:%n" +
                    "  1. Group expression was not analyzed during build-time processing%n" +
                    "  2. Lambda is in test code (only application code is analyzed)%n" +
                    "  3. Incremental compilation didn't detect changes%n" +
                    "%n" +
                    "Solutions:%n" +
                    "  - Run a clean build: 'mvn clean compile' or 'gradle clean build'%n" +
                    "  - Check build logs for 'QusaqProcessor' messages%n" +
                    "  - Verify lambda is in src/main/java (not src/test/java)%n" +
                    "%n" +
                    "Registered executors: %d list, %d count, %d group list, %d group count",
                    callSiteId, getListExecutorCount(), getCountExecutorCount(),
                    getGroupListExecutorCount(), getGroupCountExecutorCount()));
        }

        if (entityManager == null) {
            throw new IllegalStateException("EntityManager not available");
        }

        log.tracef("Executing group list query for call site: %s with %d captured variables (offset=%s, limit=%s)",
                   callSiteId, capturedValues.length, offset, limit);

        List<?> rawResults = executor.execute(entityManager, entityClass, capturedValues, offset, limit, null);

        // Iteration 7: Convert Tuple results to Object[] if needed (for Object[] projections)
        if (!rawResults.isEmpty() && rawResults.get(0) instanceof jakarta.persistence.Tuple) {
            List<jakarta.persistence.Tuple> tuples = rawResults.stream()
                    .map(o -> (jakarta.persistence.Tuple) o)
                    .collect(java.util.stream.Collectors.toList());
            return (List<R>) tuples.stream()
                    .map(jakarta.persistence.Tuple::toArray)
                    .collect(java.util.stream.Collectors.toList());
        }

        return (List<R>) rawResults;
    }

    /**
     * Executes group key query for call site (returns only grouping keys).
     * Iteration 7: Handles groupBy().toList() and groupBy().selectKey() queries.
     */
    @SuppressWarnings("unchecked")
    public <T, K> List<K> executeGroupKeyQuery(String callSiteId, Class<T> entityClass, Object[] capturedValues,
                                                Integer offset, Integer limit) {
        QueryExecutor<List<?>> executor = GROUP_LIST_EXECUTORS.get(callSiteId);

        if (executor == null) {
            throw new IllegalStateException(String.format(
                    "No group key executor found for call site: %s%n" +
                    "%n" +
                    "Possible causes:%n" +
                    "  1. Group expression was not analyzed during build-time processing%n" +
                    "  2. Lambda is in test code (only application code is analyzed)%n" +
                    "  3. Incremental compilation didn't detect changes%n" +
                    "%n" +
                    "Solutions:%n" +
                    "  - Run a clean build: 'mvn clean compile' or 'gradle clean build'%n" +
                    "  - Check build logs for 'QusaqProcessor' messages%n" +
                    "  - Verify lambda is in src/main/java (not src/test/java)%n" +
                    "%n" +
                    "Registered executors: %d list, %d count, %d group list, %d group count",
                    callSiteId, getListExecutorCount(), getCountExecutorCount(),
                    getGroupListExecutorCount(), getGroupCountExecutorCount()));
        }

        if (entityManager == null) {
            throw new IllegalStateException("EntityManager not available");
        }

        log.tracef("Executing group key query for call site: %s with %d captured variables (offset=%s, limit=%s)",
                   callSiteId, capturedValues.length, offset, limit);

        return (List<K>) executor.execute(entityManager, entityClass, capturedValues, offset, limit, null);
    }

    /**
     * Executes group count query for call site (counts number of groups).
     * Iteration 7: Handles groupBy().count() queries.
     */
    public <T> long executeGroupCountQuery(String callSiteId, Class<T> entityClass, Object[] capturedValues) {
        QueryExecutor<Long> executor = GROUP_COUNT_EXECUTORS.get(callSiteId);

        if (executor == null) {
            throw new IllegalStateException(String.format(
                    "No group count executor found for call site: %s%n" +
                    "%n" +
                    "Possible causes:%n" +
                    "  1. Group expression was not analyzed during build-time processing%n" +
                    "  2. Lambda is in test code (only application code is analyzed)%n" +
                    "  3. Incremental compilation didn't detect changes%n" +
                    "%n" +
                    "Solutions:%n" +
                    "  - Run a clean build: 'mvn clean compile' or 'gradle clean build'%n" +
                    "  - Check build logs for 'QusaqProcessor' messages%n" +
                    "  - Verify lambda is in src/main/java (not src/test/java)%n" +
                    "%n" +
                    "Registered executors: %d list, %d count, %d group list, %d group count",
                    callSiteId, getListExecutorCount(), getCountExecutorCount(),
                    getGroupListExecutorCount(), getGroupCountExecutorCount()));
        }

        if (entityManager == null) {
            throw new IllegalStateException("EntityManager not available");
        }

        log.tracef("Executing group count query for call site: %s with %d captured variables",
                   callSiteId, capturedValues.length);

        return executor.execute(entityManager, entityClass, capturedValues, null, null, null);
    }

    /**
     * Returns number of registered group list executors.
     */
    public static int getGroupListExecutorCount() {
        return GROUP_LIST_EXECUTORS.size();
    }

    /**
     * Returns number of registered group count executors.
     */
    public static int getGroupCountExecutorCount() {
        return GROUP_COUNT_EXECUTORS.size();
    }
}
