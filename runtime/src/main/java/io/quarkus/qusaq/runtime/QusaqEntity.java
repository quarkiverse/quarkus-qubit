package io.quarkus.qusaq.runtime;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.MappedSuperclass;

import java.util.List;

/**
 * Optional ActiveRecord base class for lambda-based Panache queries.
 * Static query methods are injected at build time via bytecode enhancement.
 * <p>
 * Note: {@link QusaqRepository} is the recommended pattern. Use this only if you prefer static methods on entities.
 */
@MappedSuperclass
public abstract class QusaqEntity extends PanacheEntity {

    /**
     * Finds entities matching the specification. Implemented at build time.
     */
    public static <T extends QusaqEntity> List<T> findWhere(QuerySpec<T, Boolean> spec) {
        throw new IllegalStateException(
            "This method is normally automatically overridden in subclasses: " +
            "did you forget to annotate your entity with @Entity?");
    }

    /**
     * Counts entities matching the specification. Implemented at build time.
     */
    public static <T extends QusaqEntity> long countWhere(QuerySpec<T, Boolean> spec) {
        throw new IllegalStateException(
            "This method is normally automatically overridden in subclasses: " +
            "did you forget to annotate your entity with @Entity?");
    }

    /**
     * Checks if any entities match the specification. Implemented at build time.
     */
    public static <T extends QusaqEntity> boolean exists(QuerySpec<T, Boolean> spec) {
        throw new IllegalStateException(
            "This method is normally automatically overridden in subclasses: " +
            "did you forget to annotate your entity with @Entity?");
    }

}
