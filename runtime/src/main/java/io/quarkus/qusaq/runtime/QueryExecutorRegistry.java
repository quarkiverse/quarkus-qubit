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
     * Returns number of captured variables for call site.
     */
    public static int getCapturedVariableCount(String callSiteId) {
        return CAPTURED_VAR_COUNTS.getOrDefault(callSiteId, 0);
    }

    /**
     * Executes list query for call site.
     */
    @SuppressWarnings("unchecked")
    public <T> List<T> executeListQuery(String callSiteId, Class<T> entityClass, Object[] capturedValues) {
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

        log.tracef("Executing list query for call site: %s with %d captured variables",
                   callSiteId, capturedValues.length);

        return (List<T>) executor.execute(entityManager, entityClass, capturedValues);
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

        return executor.execute(entityManager, entityClass, capturedValues);
    }

    /**
     * Executes exists query for call site.
     */
    public <T> boolean executeExistsQuery(String callSiteId, Class<T> entityClass, Object[] capturedValues) {
        return executeCountQuery(callSiteId, entityClass, capturedValues) > 0;
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
}
