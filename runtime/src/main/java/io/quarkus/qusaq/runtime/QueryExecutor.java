package io.quarkus.qusaq.runtime;

import jakarta.persistence.EntityManager;

/**
 * Functional interface for build-time generated query executors with captured variable support.
 * Phase 4: Added pagination and distinct support.
 */
@FunctionalInterface
public interface QueryExecutor<R> {

    /**
     * Executes query with captured variables, optional pagination, and distinct flag.
     * Phase 4: Added offset, limit, and distinct parameters for pagination and deduplication support.
     *
     * @param entityManager JPA EntityManager
     * @param entityClass entity class being queried
     * @param capturedValues captured variables from lambda closure
     * @param offset number of results to skip (null for no offset)
     * @param limit maximum number of results (null for no limit)
     * @param distinct whether to apply SELECT DISTINCT (null or false for no distinct)
     * @return query result
     */
    R execute(EntityManager entityManager, Class<?> entityClass, Object[] capturedValues,
              Integer offset, Integer limit, Boolean distinct);
}
