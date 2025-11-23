package io.quarkus.qusaq.runtime;

import jakarta.persistence.EntityManager;

/**
 * Functional interface for build-time generated query executors with captured variable support.
 * Phase 4: Added pagination support via offset/limit parameters.
 */
@FunctionalInterface
public interface QueryExecutor<R> {

    /**
     * Executes query with captured variables and optional pagination.
     * Phase 4: Added offset and limit parameters for pagination support.
     *
     * @param entityManager JPA EntityManager
     * @param entityClass entity class being queried
     * @param capturedValues captured variables from lambda closure
     * @param offset number of results to skip (null for no offset)
     * @param limit maximum number of results (null for no limit)
     * @return query result
     */
    R execute(EntityManager entityManager, Class<?> entityClass, Object[] capturedValues,
              Integer offset, Integer limit);
}
