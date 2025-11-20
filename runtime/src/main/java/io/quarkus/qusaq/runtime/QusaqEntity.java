package io.quarkus.qusaq.runtime;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.MappedSuperclass;

/**
 * Optional ActiveRecord base class for fluent, type-safe lambda-based queries.
 * <p>
 * Entities extending this class gain access to a JINQ-inspired fluent query API
 * that is fully translated to JPA Criteria Queries at build time. All query
 * operations are analyzed through bytecode inspection and optimized at compile time,
 * ensuring zero runtime overhead.
 * <p>
 * <strong>Entry points for query composition:</strong>
 * <ul>
 *   <li>{@link #where(QuerySpec)} - Start with filtering</li>
 *   <li>{@link #select(QuerySpec)} - Start with projection</li>
 *   <li>{@link #sortedBy(QuerySpec)} - Start with sorting (ascending)</li>
 *   <li>{@link #sortedDescendingBy(QuerySpec)} - Start with sorting (descending)</li>
 * </ul>
 * <p>
 * <strong>Note:</strong> For counting all entities or finding all entities, use the inherited
 * Panache methods {@code count()} and {@code findAll()} from {@code PanacheEntityBase}.
 * <p>
 * <strong>Example usage:</strong>
 * <pre>{@code
 * @Entity
 * public class Person extends QusaqEntity {
 *     public String firstName;
 *     public String lastName;
 *     public int age;
 *     public boolean active;
 * }
 *
 * // Simple filtering
 * List<Person> adults = Person.where(p -> p.age >= 18).toList();
 *
 * // Complex composition
 * List<String> topCities = Person.where(p -> p.active && p.salary > 100000)
 *                                .select(p -> p.city)
 *                                .distinct()
 *                                .sortedBy(city -> city)
 *                                .limit(10)
 *                                .toList();
 *
 * // Aggregation
 * long activeCount = Person.where(p -> p.active).count();
 * }</pre>
 * <p>
 * <strong>Note:</strong> {@code QusaqRepository} is the recommended pattern for most use cases.
 * Use this ActiveRecord style only if you prefer static methods on entities.
 * <p>
 * <strong>Build-time enhancement:</strong> All static query methods are implemented at build time
 * via bytecode enhancement. If you see "IllegalStateException" at runtime, ensure your entity
 * is properly annotated with {@code @Entity} and that the Qusaq build-time processor is configured.
 */
@MappedSuperclass
public abstract class QusaqEntity extends PanacheEntity {

    // =============================================================================================
    // QUERY ENTRY POINTS
    // =============================================================================================

    /**
     * Creates a query filtered by the given predicate.
     * <p>
     * This is an entry point for fluent query composition. Multiple {@code where()} calls
     * can be chained and will be combined with AND logic.
     * <p>
     * <strong>Implemented at build time</strong> - the bytecode enhancement processor will
     * replace this method body in each entity subclass with the actual query execution code.
     * <p>
     * Example:
     * <pre>{@code
     * // Simple filter
     * List<Person> adults = Person.where(p -> p.age >= 18).toList();
     *
     * // Multiple conditions
     * List<Person> results = Person.where(p -> p.age > 25)
     *                              .where(p -> p.active)
     *                              .toList();
     * }</pre>
     *
     * @param <T> the entity type
     * @param spec filtering predicate returning boolean
     * @return a new QusaqStream with the filter applied
     * @throws IllegalStateException if called at runtime without build-time enhancement
     */
    public static <T extends QusaqEntity> QusaqStream<T> where(QuerySpec<T, Boolean> spec) {
        throw new IllegalStateException(
                "This method is normally automatically overridden in subclasses at build time. " +
                "Did you forget to annotate your entity with @Entity? " +
                "Or is the Qusaq build-time processor not properly configured?");
    }

    /**
     * Creates a query with field projection or transformation.
     * <p>
     * This is an entry point for projection-based queries. Use this when you want to select
     * specific fields, compute values, or construct DTOs instead of loading full entities.
     * <p>
     * <strong>Implemented at build time</strong> - the bytecode enhancement processor will
     * replace this method body in each entity subclass with the actual query execution code.
     * <p>
     * Example:
     * <pre>{@code
     * // Select single field
     * List<String> names = Person.select(p -> p.firstName).toList();
     *
     * // Select with transformation
     * List<String> fullNames = Person.select(p -> p.firstName + " " + p.lastName).toList();
     *
     * // Select DTO
     * List<PersonDTO> dtos = Person.select(p -> new PersonDTO(p.firstName, p.age)).toList();
     * }</pre>
     *
     * @param <T> the entity type
     * @param <R> the projection result type
     * @param mapper lambda transforming entity to projection
     * @return a new QusaqStream with the projection applied
     * @throws IllegalStateException if called at runtime without build-time enhancement
     */
    public static <T extends QusaqEntity, R> QusaqStream<R> select(QuerySpec<T, R> mapper) {
        throw new IllegalStateException(
                "This method is normally automatically overridden in subclasses at build time. " +
                "Did you forget to annotate your entity with @Entity? " +
                "Or is the Qusaq build-time processor not properly configured?");
    }

    /**
     * Creates a query sorted in ascending order by the given key extractor.
     * <p>
     * This is an entry point for sorted queries. For multi-level sorting, call {@code sortedBy()}
     * multiple times - the <strong>last call</strong> becomes the primary sort key.
     * <p>
     * <strong>Implemented at build time</strong> - the bytecode enhancement processor will
     * replace this method body in each entity subclass with the actual query execution code.
     * <p>
     * Example:
     * <pre>{@code
     * // Single-level sort
     * List<Person> byAge = Person.sortedBy(p -> p.age).toList();
     *
     * // Multi-level sort (last call wins)
     * List<Person> sorted = Person.sortedBy(p -> p.firstName)  // Secondary
     *                             .sortedBy(p -> p.lastName)   // Primary
     *                             .toList();
     * }</pre>
     *
     * @param <T> the entity type
     * @param <K> the type of the sort key (must be comparable)
     * @param keyExtractor lambda extracting sort key
     * @return a new QusaqStream with the sort order applied
     * @throws IllegalStateException if called at runtime without build-time enhancement
     */
    public static <T extends QusaqEntity, K extends Comparable<K>> QusaqStream<T> sortedBy(
            QuerySpec<T, K> keyExtractor) {
        throw new IllegalStateException(
                "This method is normally automatically overridden in subclasses at build time. " +
                "Did you forget to annotate your entity with @Entity? " +
                "Or is the Qusaq build-time processor not properly configured?");
    }

    /**
     * Creates a query sorted in descending order by the given key extractor.
     * <p>
     * This is an entry point for reverse-sorted queries.
     * <p>
     * <strong>Implemented at build time</strong> - the bytecode enhancement processor will
     * replace this method body in each entity subclass with the actual query execution code.
     * <p>
     * Example:
     * <pre>{@code
     * // Descending sort
     * List<Person> oldestFirst = Person.sortedDescendingBy(p -> p.age).toList();
     *
     * // Top 5 by salary
     * List<Person> top5 = Person.sortedDescendingBy(p -> p.salary)
     *                           .limit(5)
     *                           .toList();
     * }</pre>
     *
     * @param <T> the entity type
     * @param <K> the type of the sort key (must be comparable)
     * @param keyExtractor lambda extracting sort key
     * @return a new QusaqStream with the descending sort order applied
     * @throws IllegalStateException if called at runtime without build-time enhancement
     */
    public static <T extends QusaqEntity, K extends Comparable<K>> QusaqStream<T> sortedDescendingBy(
            QuerySpec<T, K> keyExtractor) {
        throw new IllegalStateException(
                "This method is normally automatically overridden in subclasses at build time. " +
                "Did you forget to annotate your entity with @Entity? " +
                "Or is the Qusaq build-time processor not properly configured?");
    }
}
