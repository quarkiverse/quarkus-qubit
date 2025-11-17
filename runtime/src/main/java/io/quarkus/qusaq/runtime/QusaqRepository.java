package io.quarkus.qusaq.runtime;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import io.quarkus.panache.common.impl.GenerateBridge;

import java.util.List;

import static io.quarkus.hibernate.orm.panache.common.runtime.AbstractJpaOperations.implementationInjectionMissing;

/**
 * Repository interface with lambda-based query methods extending PanacheRepositoryBase.
 */
public interface QusaqRepository<E extends PanacheEntity, I> extends PanacheRepositoryBase<E, I> {

    /**
     * Finds entities matching the specification.
     */
    @GenerateBridge
    default List<E> findWhere(QuerySpec<E, Boolean> spec) {
        throw implementationInjectionMissing();
    }

    /**
     * Counts entities matching the specification.
     */
    @GenerateBridge
    default long countWhere(QuerySpec<E, Boolean> spec) {
        throw implementationInjectionMissing();
    }

    /**
     * Checks if any entities match the specification.
     */
    @GenerateBridge
    default boolean exists(QuerySpec<E, Boolean> spec) {
        throw implementationInjectionMissing();
    }
}
