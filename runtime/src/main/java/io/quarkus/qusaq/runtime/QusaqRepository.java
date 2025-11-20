package io.quarkus.qusaq.runtime;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import io.quarkus.panache.common.impl.GenerateBridge;

import static io.quarkus.hibernate.orm.panache.common.runtime.AbstractJpaOperations.implementationInjectionMissing;

/**
 * Repository interface with fluent API query methods extending PanacheRepositoryBase.
 * <p>
 * Provides fluent, type-safe query composition using JINQ-inspired syntax.
 * All query operations are analyzed at build time and translated to JPA Criteria Queries.
 * <p>
 * <strong>Example usage:</strong>
 * <pre>{@code
 * @ApplicationScoped
 * public class PersonRepository implements QusaqRepository<Person, Long> {
 * }
 *
 * // Usage:
 * List<Person> adults = personRepository.where(p -> p.age >= 18).toList();
 * long count = personRepository.where(p -> p.active).count();
 * }</pre>
 */
public interface QusaqRepository<E extends PanacheEntity, I> extends PanacheRepositoryBase<E, I> {

    /**
     * Creates a query filtered by the given predicate.
     * <p>
     * Entry point for fluent query composition. Multiple where() calls can be chained
     * and will be combined with AND logic.
     * <p>
     * <strong>Generated at build time</strong> via bytecode enhancement.
     *
     * @param spec filtering predicate returning boolean
     * @return a new QusaqStream with the filter applied
     */
    @GenerateBridge
    default QusaqStream<E> where(QuerySpec<E, Boolean> spec) {
        throw implementationInjectionMissing();
    }

    /**
     * Creates a query with field projection or transformation.
     * <p>
     * Entry point for projection-based queries.
     * <p>
     * <strong>Generated at build time</strong> via bytecode enhancement.
     *
     * @param <R> the projection result type
     * @param mapper lambda transforming entity to projection
     * @return a new QusaqStream with the projection applied
     */
    @GenerateBridge
    default <R> QusaqStream<R> select(QuerySpec<E, R> mapper) {
        throw implementationInjectionMissing();
    }

    /**
     * Creates a query sorted in ascending order.
     * <p>
     * <strong>Generated at build time</strong> via bytecode enhancement.
     *
     * @param <K> the type of the sort key
     * @param keyExtractor lambda extracting sort key
     * @return a new QusaqStream with the sort order applied
     */
    @GenerateBridge
    default <K extends Comparable<K>> QusaqStream<E> sortedBy(QuerySpec<E, K> keyExtractor) {
        throw implementationInjectionMissing();
    }

    /**
     * Creates a query sorted in descending order.
     * <p>
     * <strong>Generated at build time</strong> via bytecode enhancement.
     *
     * @param <K> the type of the sort key
     * @param keyExtractor lambda extracting sort key
     * @return a new QusaqStream with the descending sort order applied
     */
    @GenerateBridge
    default <K extends Comparable<K>> QusaqStream<E> sortedDescendingBy(QuerySpec<E, K> keyExtractor) {
        throw implementationInjectionMissing();
    }
}
