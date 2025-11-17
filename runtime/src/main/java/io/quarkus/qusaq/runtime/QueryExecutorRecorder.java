package io.quarkus.qusaq.runtime;

import io.quarkus.runtime.annotations.Recorder;
import org.jboss.logging.Logger;

import java.util.List;

/**
 * Registers build-time generated query executors during static initialization.
 */
@Recorder
public class QueryExecutorRecorder {

    private static final Logger log = Logger.getLogger(QueryExecutorRecorder.class);

    /**
     * Registers list query executor during static initialization.
     */
    public void registerListExecutor(String callSiteId, String executorClassName, int capturedVarCount) {
        try {
            log.debugf("Registering list executor: %s -> %s (captured vars: %d)",
                       callSiteId, executorClassName, capturedVarCount);

            Class<?> executorClass = Thread.currentThread()
                    .getContextClassLoader()
                    .loadClass(executorClassName);

            @SuppressWarnings("unchecked")
            QueryExecutor<List<?>> executor =
                    (QueryExecutor<List<?>>) executorClass
                            .getDeclaredConstructor()
                            .newInstance();

            QueryExecutorRegistry.registerListExecutor(callSiteId, executor, capturedVarCount);
            log.debugf("Successfully registered list executor: %s", callSiteId);

        } catch (Exception e) {
            log.errorf(e, "Failed to register list executor for call site: %s", callSiteId);
            throw new QueryExecutorRegistrationException(
                    "Failed to register list executor: " + callSiteId +
                    " (executor class: " + executorClassName + ")", e);
        }
    }

    /**
     * Registers count query executor during static initialization.
     */
    public void registerCountExecutor(String callSiteId, String executorClassName, int capturedVarCount) {
        try {
            log.debugf("Registering count executor: %s -> %s (captured vars: %d)",
                       callSiteId, executorClassName, capturedVarCount);

            Class<?> executorClass = Thread.currentThread()
                    .getContextClassLoader()
                    .loadClass(executorClassName);

            @SuppressWarnings("unchecked")
            QueryExecutor<Long> executor =
                    (QueryExecutor<Long>) executorClass
                            .getDeclaredConstructor()
                            .newInstance();

            QueryExecutorRegistry.registerCountExecutor(callSiteId, executor, capturedVarCount);
            log.debugf("Successfully registered count executor: %s", callSiteId);

        } catch (Exception e) {
            log.errorf(e, "Failed to register count executor for call site: %s", callSiteId);
            throw new QueryExecutorRegistrationException(
                    "Failed to register count executor: " + callSiteId +
                    " (executor class: " + executorClassName + ")", e);
        }
    }
}
