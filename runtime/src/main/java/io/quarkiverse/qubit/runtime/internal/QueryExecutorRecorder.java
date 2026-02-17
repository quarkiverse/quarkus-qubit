package io.quarkiverse.qubit.runtime.internal;

import java.util.List;
import java.util.function.Consumer;

import org.jboss.logging.Logger;

import io.quarkiverse.qubit.QueryExecutorRegistrationException;
import io.quarkus.runtime.annotations.Recorder;

/**
 * Registers build-time generated query executors during static initialization.
 */
@Recorder
public class QueryExecutorRecorder {

    private static final Logger LOG = Logger.getLogger(QueryExecutorRecorder.class);

    /**
     * Registers list query executor during static initialization.
     */
    public void registerListExecutor(String callSiteId, String executorClassName, int capturedVarCount) {
        registerExecutor(callSiteId, executorClassName, capturedVarCount, "list",
                (QueryExecutor<List<?>> executor) -> QueryExecutorRegistry.registerListExecutor(callSiteId, executor,
                        capturedVarCount));
    }

    /**
     * Registers count query executor during static initialization.
     */
    public void registerCountExecutor(String callSiteId, String executorClassName, int capturedVarCount) {
        registerExecutor(callSiteId, executorClassName, capturedVarCount, "count",
                (QueryExecutor<Long> executor) -> QueryExecutorRegistry.registerCountExecutor(callSiteId, executor,
                        capturedVarCount));
    }

    /**
     * Registers aggregation query executor during static initialization.
     * Supports MIN, MAX, AVG, SUM* aggregation operations.
     */
    public void registerAggregationExecutor(String callSiteId, String executorClassName, int capturedVarCount) {
        registerExecutor(callSiteId, executorClassName, capturedVarCount, "aggregation",
                (QueryExecutor<Object> executor) -> QueryExecutorRegistry.registerAggregationExecutor(callSiteId, executor,
                        capturedVarCount));
    }

    /**
     * Registers join list query executor during static initialization.
     */
    public void registerJoinListExecutor(String callSiteId, String executorClassName, int capturedVarCount) {
        registerExecutor(callSiteId, executorClassName, capturedVarCount, "join-list",
                (QueryExecutor<List<?>> executor) -> QueryExecutorRegistry.registerJoinListExecutor(callSiteId, executor,
                        capturedVarCount));
    }

    /**
     * Registers join count query executor during static initialization.
     */
    public void registerJoinCountExecutor(String callSiteId, String executorClassName, int capturedVarCount) {
        registerExecutor(callSiteId, executorClassName, capturedVarCount, "join-count",
                (QueryExecutor<Long> executor) -> QueryExecutorRegistry.registerJoinCountExecutor(callSiteId, executor,
                        capturedVarCount));
    }

    /**
     * Registers join selectJoined query executor during static initialization.
     * selectJoined() - returns joined entities instead of source entities.
     */
    public void registerJoinSelectJoinedExecutor(String callSiteId, String executorClassName, int capturedVarCount) {
        registerExecutor(callSiteId, executorClassName, capturedVarCount, "join-selectJoined",
                (QueryExecutor<List<?>> executor) -> QueryExecutorRegistry.registerJoinSelectJoinedExecutor(callSiteId,
                        executor, capturedVarCount));
    }

    /**
     * Registers join projection query executor during static initialization.
     * select() with BiQuerySpec - returns projected objects from both entities.
     */
    public void registerJoinProjectionExecutor(String callSiteId, String executorClassName, int capturedVarCount) {
        registerExecutor(callSiteId, executorClassName, capturedVarCount, "join-projection",
                (QueryExecutor<List<?>> executor) -> QueryExecutorRegistry.registerJoinProjectionExecutor(callSiteId, executor,
                        capturedVarCount));
    }

    /**
     * Registers group list query executor during static initialization.
     */
    public void registerGroupListExecutor(String callSiteId, String executorClassName, int capturedVarCount) {
        registerExecutor(callSiteId, executorClassName, capturedVarCount, "group-list",
                (QueryExecutor<List<?>> executor) -> QueryExecutorRegistry.registerGroupListExecutor(callSiteId, executor,
                        capturedVarCount));
    }

    /**
     * Registers group count query executor during static initialization.
     */
    public void registerGroupCountExecutor(String callSiteId, String executorClassName, int capturedVarCount) {
        registerExecutor(callSiteId, executorClassName, capturedVarCount, "group-count",
                (QueryExecutor<Long> executor) -> QueryExecutorRegistry.registerGroupCountExecutor(callSiteId, executor,
                        capturedVarCount));
    }

    /**
     * Resets all runtime state before re-registering executors.
     * Clears executor maps, captured variable counts, and cached CDI registry reference.
     * Called at STATIC_INIT start on both initial builds and dev mode hot reload.
     */
    public void resetRuntimeState() {
        LOG.debug("Resetting runtime state: clearing executors and cached registry");
        QueryExecutorRegistry.clearAllExecutors();
        LambdaReflectionUtils.clearCachedRegistry();
    }

    /**
     * Generic executor registration method that handles the common logic for all executor types.
     */
    private <T> void registerExecutor(
            String callSiteId,
            String executorClassName,
            int capturedVarCount,
            String executorType,
            Consumer<QueryExecutor<T>> registrar) {

        try {
            LOG.debugf("Registering %s executor: %s -> %s (captured vars: %d)",
                    executorType, callSiteId, executorClassName, capturedVarCount);

            Class<?> executorClass = Thread.currentThread()
                    .getContextClassLoader()
                    .loadClass(executorClassName);

            @SuppressWarnings("unchecked")
            QueryExecutor<T> executor = (QueryExecutor<T>) executorClass
                    .getDeclaredConstructor()
                    .newInstance();

            registrar.accept(executor);
            LOG.debugf("Successfully registered %s executor: %s", executorType, callSiteId);

        } catch (Exception e) {
            LOG.errorf(e, "Failed to register %s executor for call site: %s", executorType, callSiteId);
            throw new QueryExecutorRegistrationException(
                    "Failed to register " + executorType + " executor: " + callSiteId +
                            " (executor class: " + executorClassName + ")",
                    e);
        }
    }
}
