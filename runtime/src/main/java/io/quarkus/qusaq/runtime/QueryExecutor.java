package io.quarkus.qusaq.runtime;

import jakarta.persistence.EntityManager;

/**
 * Functional interface for build-time generated query executors with captured variable support.
 */
@FunctionalInterface
public interface QueryExecutor<R> {

    /**
     * Executes query with captured variables and returns result.
     */
    R execute(EntityManager entityManager, Class<?> entityClass, Object[] capturedValues);
}
