package io.quarkiverse.qubit;

import java.util.Collection;

import jakarta.persistence.MappedSuperclass;

import io.quarkus.hibernate.orm.panache.PanacheEntity;

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
 * <li>{@link #where(QuerySpec)} - Start with filtering</li>
 * <li>{@link #select(QuerySpec)} - Start with projection</li>
 * <li>{@link #sortedBy(QuerySpec)} - Start with sorting (ascending)</li>
 * <li>{@link #sortedDescendingBy(QuerySpec)} - Start with sorting (descending)</li>
 * </ul>
 * <p>
 * <strong>Note:</strong> For counting all entities or finding all entities, use the inherited
 * Panache methods {@code count()} and {@code findAll()} from {@code PanacheEntityBase}.
 * <p>
 * <strong>Example usage:</strong>
 *
 * <pre>{@code
 * @Entity
 * public class Person extends QubitEntity {
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
 *         .select(p -> p.city)
 *         .distinct()
 *         .sortedBy(city -> city)
 *         .limit(10)
 *         .toList();
 *
 * // Aggregation
 * long activeCount = Person.where(p -> p.active).count();
 * }</pre>
 * <p>
 * <strong>Note:</strong> {@code QubitRepository} is the recommended pattern for most use cases.
 * Use this ActiveRecord style only if you prefer static methods on entities.
 * <p>
 * <strong>Build-time enhancement:</strong> All static query methods are implemented at build time
 * via bytecode enhancement. If you see "IllegalStateException" at runtime, ensure your entity
 * is properly annotated with {@code @Entity} and that the Qubit build-time processor is configured.
 */
@MappedSuperclass
public abstract class QubitEntity extends PanacheEntity {

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
     *
     * <pre>{@code
     * // Simple filter
     * List<Person> adults = Person.where(p -> p.age >= 18).toList();
     *
     * // Multiple conditions
     * List<Person> results = Person.where(p -> p.age > 25)
     *         .where(p -> p.active)
     *         .toList();
     * }</pre>
     *
     * @param <T> the entity type
     * @param spec filtering predicate returning boolean
     * @return a new QubitStream with the filter applied
     * @throws IllegalStateException if called at runtime without build-time enhancement
     */
    public static <T extends QubitEntity> QubitStream<T> where(QuerySpec<T, Boolean> spec) {
        throw new IllegalStateException(
                "This method is normally automatically overridden in subclasses at build time. " +
                        "Did you forget to annotate your entity with @Entity? " +
                        "Or is the Qubit build-time processor not properly configured?");
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
     *
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
     * @return a new QubitStream with the projection applied
     * @throws IllegalStateException if called at runtime without build-time enhancement
     */
    public static <T extends QubitEntity, R> QubitStream<R> select(QuerySpec<T, R> mapper) {
        throw new IllegalStateException(
                "This method is normally automatically overridden in subclasses at build time. " +
                        "Did you forget to annotate your entity with @Entity? " +
                        "Or is the Qubit build-time processor not properly configured?");
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
     *
     * <pre>{@code
     * // Single-level sort
     * List<Person> byAge = Person.sortedBy(p -> p.age).toList();
     *
     * // Multi-level sort (last call wins)
     * List<Person> sorted = Person.sortedBy(p -> p.firstName) // Secondary
     *         .sortedBy(p -> p.lastName) // Primary
     *         .toList();
     * }</pre>
     *
     * @param <T> the entity type
     * @param <K> the type of the sort key (must be comparable)
     * @param keyExtractor lambda extracting sort key
     * @return a new QubitStream with the sort order applied
     * @throws IllegalStateException if called at runtime without build-time enhancement
     */
    public static <T extends QubitEntity, K extends Comparable<K>> QubitStream<T> sortedBy(
            QuerySpec<T, K> keyExtractor) {
        throw new IllegalStateException(
                "This method is normally automatically overridden in subclasses at build time. " +
                        "Did you forget to annotate your entity with @Entity? " +
                        "Or is the Qubit build-time processor not properly configured?");
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
     *
     * <pre>{@code
     * // Descending sort
     * List<Person> oldestFirst = Person.sortedDescendingBy(p -> p.age).toList();
     *
     * // Top 5 by salary
     * List<Person> top5 = Person.sortedDescendingBy(p -> p.salary)
     *         .limit(5)
     *         .toList();
     * }</pre>
     *
     * @param <T> the entity type
     * @param <K> the type of the sort key (must be comparable)
     * @param keyExtractor lambda extracting sort key
     * @return a new QubitStream with the descending sort order applied
     * @throws IllegalStateException if called at runtime without build-time enhancement
     */
    public static <T extends QubitEntity, K extends Comparable<K>> QubitStream<T> sortedDescendingBy(
            QuerySpec<T, K> keyExtractor) {
        throw new IllegalStateException(
                "This method is normally automatically overridden in subclasses at build time. " +
                        "Did you forget to annotate your entity with @Entity? " +
                        "Or is the Qubit build-time processor not properly configured?");
    }

    /**
     * Prepares a minimum value aggregation query.
     * <p>
     * This is an entry point for finding the minimum value of a comparable field.
     * Can be combined with {@code where()} for conditional aggregation.
     * <p>
     * <strong>Implemented at build time</strong> - the bytecode enhancement processor will
     * replace this method body in each entity subclass with the actual query execution code.
     * <p>
     * Example:
     *
     * <pre>{@code
     * // Minimum age across all persons
     * Integer minAge = Person.min(p -> p.age).getSingleResult();
     *
     * // Minimum salary for active employees
     * Double minActiveSalary = Person.where(p -> p.active)
     *         .min(p -> p.salary)
     *         .getSingleResult();
     * }</pre>
     *
     * @param <T> the entity type
     * @param <K> the type of the field (must be comparable)
     * @param mapper lambda selecting the field to aggregate
     * @return a QubitStream that will yield the minimum value
     * @throws IllegalStateException if called at runtime without build-time enhancement
     */
    public static <T extends QubitEntity, K extends Comparable<K>> ScalarResult<K> min(QuerySpec<T, K> mapper) {
        throw new IllegalStateException(
                "This method is normally automatically overridden in subclasses at build time. " +
                        "Did you forget to annotate your entity with @Entity? " +
                        "Or is the Qubit build-time processor not properly configured?");
    }

    /**
     * Prepares a maximum value aggregation query.
     * <p>
     * This is an entry point for finding the maximum value of a comparable field.
     * Can be combined with {@code where()} for conditional aggregation.
     * <p>
     * <strong>Implemented at build time</strong> - the bytecode enhancement processor will
     * replace this method body in each entity subclass with the actual query execution code.
     * <p>
     * Example:
     *
     * <pre>{@code
     * // Maximum age across all persons
     * Integer maxAge = Person.max(p -> p.age).getSingleResult();
     *
     * // Maximum salary for active employees
     * Double maxActiveSalary = Person.where(p -> p.active)
     *         .max(p -> p.salary)
     *         .getSingleResult();
     * }</pre>
     *
     * @param <T> the entity type
     * @param <K> the type of the field (must be comparable)
     * @param mapper lambda selecting the field to aggregate
     * @return a QubitStream that will yield the maximum value
     * @throws IllegalStateException if called at runtime without build-time enhancement
     */
    public static <T extends QubitEntity, K extends Comparable<K>> ScalarResult<K> max(QuerySpec<T, K> mapper) {
        throw new IllegalStateException(
                "This method is normally automatically overridden in subclasses at build time. " +
                        "Did you forget to annotate your entity with @Entity? " +
                        "Or is the Qubit build-time processor not properly configured?");
    }

    /**
     * Prepares an average aggregation query for numeric values.
     * <p>
     * This is an entry point for computing the average value of a numeric field.
     * Can be combined with {@code where()} for conditional aggregation.
     * <p>
     * <strong>Implemented at build time</strong> - the bytecode enhancement processor will
     * replace this method body in each entity subclass with the actual query execution code.
     * <p>
     * Example:
     *
     * <pre>{@code
     * // Average age across all persons
     * Double avgAge = Person.avg(p -> p.age).getSingleResult();
     *
     * // Average salary for active employees
     * Double avgActiveSalary = Person.where(p -> p.active)
     *         .avg(p -> p.salary)
     *         .getSingleResult();
     * }</pre>
     *
     * @param <T> the entity type
     * @param mapper lambda selecting the numeric field to aggregate
     * @return a QubitStream that will yield the average as a Double
     * @throws IllegalStateException if called at runtime without build-time enhancement
     */
    public static <T extends QubitEntity> ScalarResult<Double> avg(QuerySpec<T, ? extends Number> mapper) {
        throw new IllegalStateException(
                "This method is normally automatically overridden in subclasses at build time. " +
                        "Did you forget to annotate your entity with @Entity? " +
                        "Or is the Qubit build-time processor not properly configured?");
    }

    /**
     * Prepares a sum aggregation query for Integer values.
     * <p>
     * This is an entry point for computing the sum of Integer fields.
     * Returns Long to prevent overflow. Can be combined with {@code where()} for conditional aggregation.
     * <p>
     * <strong>Implemented at build time</strong> - the bytecode enhancement processor will
     * replace this method body in each entity subclass with the actual query execution code.
     * <p>
     * Example:
     *
     * <pre>{@code
     * // Total age of all persons
     * Long totalAge = Person.sumInteger(p -> p.age).getSingleResult();
     *
     * // Total age of active persons
     * Long totalActiveAge = Person.where(p -> p.active)
     *         .sumInteger(p -> p.age)
     *         .getSingleResult();
     * }</pre>
     *
     * @param <T> the entity type
     * @param mapper lambda selecting the Integer field to sum
     * @return a QubitStream that will yield the sum as a Long
     * @throws IllegalStateException if called at runtime without build-time enhancement
     */
    public static <T extends QubitEntity> ScalarResult<Long> sumInteger(QuerySpec<T, Integer> mapper) {
        throw new IllegalStateException(
                "This method is normally automatically overridden in subclasses at build time. " +
                        "Did you forget to annotate your entity with @Entity? " +
                        "Or is the Qubit build-time processor not properly configured?");
    }

    /**
     * Prepares a sum aggregation query for Long values.
     * <p>
     * This is an entry point for computing the sum of Long fields.
     * Can be combined with {@code where()} for conditional aggregation.
     * <p>
     * <strong>Implemented at build time</strong> - the bytecode enhancement processor will
     * replace this method body in each entity subclass with the actual query execution code.
     * <p>
     * Example:
     *
     * <pre>{@code
     * // Total employee IDs (hypothetical use case)
     * Long totalIds = Person.sumLong(p -> p.employeeId).getSingleResult();
     *
     * // Total IDs for active employees
     * Long totalActiveIds = Person.where(p -> p.active)
     *         .sumLong(p -> p.employeeId)
     *         .getSingleResult();
     * }</pre>
     *
     * @param <T> the entity type
     * @param mapper lambda selecting the Long field to sum
     * @return a QubitStream that will yield the sum as a Long
     * @throws IllegalStateException if called at runtime without build-time enhancement
     */
    public static <T extends QubitEntity> ScalarResult<Long> sumLong(QuerySpec<T, Long> mapper) {
        throw new IllegalStateException(
                "This method is normally automatically overridden in subclasses at build time. " +
                        "Did you forget to annotate your entity with @Entity? " +
                        "Or is the Qubit build-time processor not properly configured?");
    }

    /**
     * Prepares a sum aggregation query for Double values.
     * <p>
     * This is an entry point for computing the sum of Double fields.
     * Can be combined with {@code where()} for conditional aggregation.
     * <p>
     * <strong>Implemented at build time</strong> - the bytecode enhancement processor will
     * replace this method body in each entity subclass with the actual query execution code.
     * <p>
     * Example:
     *
     * <pre>{@code
     * // Total salary of all persons
     * Double totalSalary = Person.sumDouble(p -> p.salary).getSingleResult();
     *
     * // Total salary of active employees
     * Double totalActiveSalary = Person.where(p -> p.active)
     *         .sumDouble(p -> p.salary)
     *         .getSingleResult();
     * }</pre>
     *
     * @param <T> the entity type
     * @param mapper lambda selecting the Double field to sum
     * @return a QubitStream that will yield the sum as a Double
     * @throws IllegalStateException if called at runtime without build-time enhancement
     */
    public static <T extends QubitEntity> ScalarResult<Double> sumDouble(QuerySpec<T, Double> mapper) {
        throw new IllegalStateException(
                "This method is normally automatically overridden in subclasses at build time. " +
                        "Did you forget to annotate your entity with @Entity? " +
                        "Or is the Qubit build-time processor not properly configured?");
    }

    /**
     * Creates an inner join query with a related entity collection.
     * <p>
     * This is an entry point for join-based queries. The join follows the relationship
     * defined by the lambda expression, typically accessing a collection field.
     * <p>
     * <strong>Implemented at build time</strong> - the bytecode enhancement processor will
     * replace this method body in each entity subclass with the actual query execution code.
     * <p>
     * Example:
     *
     * <pre>{@code
     * // Find persons with mobile phones (inner join - excludes persons without phones)
     * List<Person> peopleWithMobilePhones = Person
     *         .join((Person p) -> p.phones)
     *         .where((Person p, Phone ph) -> ph.type.equals("mobile"))
     *         .toList();
     *
     * // Project both entities
     * List<PersonPhoneDTO> dtos = Person
     *         .join((Person p) -> p.phones)
     *         .select((Person p, Phone ph) -> new PersonPhoneDTO(p.firstName, ph.number))
     *         .toList();
     * }</pre>
     *
     * @param <T> the source entity type
     * @param <R> the joined entity type
     * @param relationship lambda accessing the collection field to join
     * @return a JoinStream for composing join predicates and projections
     * @throws IllegalStateException if called at runtime without build-time enhancement
     */
    public static <T extends QubitEntity, R> JoinStream<T, R> join(
            QuerySpec<T, Collection<R>> relationship) {
        throw new IllegalStateException(
                "This method is normally automatically overridden in subclasses at build time. " +
                        "Did you forget to annotate your entity with @Entity? " +
                        "Or is the Qubit build-time processor not properly configured?");
    }

    /**
     * Creates a left outer join query with a related entity collection.
     * <p>
     * Unlike inner join, left join includes entities from the left side (source)
     * even when there are no matching entities on the right side (joined).
     * <p>
     * <strong>Implemented at build time</strong> - the bytecode enhancement processor will
     * replace this method body in each entity subclass with the actual query execution code.
     * <p>
     * Example:
     *
     * <pre>{@code
     * // Find all persons, including those without phones
     * List<Person> allPeopleWithPhoneInfo = Person
     *         .leftJoin((Person p) -> p.phones)
     *         .toList();
     *
     * // Filter with null handling for left join
     * List<Person> peopleWithOptionalPhone = Person
     *         .leftJoin((Person p) -> p.phones)
     *         .where((Person p, Phone ph) -> ph == null || ph.type.equals("mobile"))
     *         .toList();
     * }</pre>
     *
     * @param <T> the source entity type
     * @param <R> the joined entity type
     * @param relationship lambda accessing the collection field to join
     * @return a JoinStream for composing join predicates and projections
     * @throws IllegalStateException if called at runtime without build-time enhancement
     */
    public static <T extends QubitEntity, R> JoinStream<T, R> leftJoin(
            QuerySpec<T, Collection<R>> relationship) {
        throw new IllegalStateException(
                "This method is normally automatically overridden in subclasses at build time. " +
                        "Did you forget to annotate your entity with @Entity? " +
                        "Or is the Qubit build-time processor not properly configured?");
    }

    /**
     * Creates a GROUP BY query grouped by the specified key extractor.
     * <p>
     * This is an entry point for grouped aggregation queries. The grouping key can be
     * any field, relationship navigation, or expression that returns a comparable value.
     * <p>
     * <strong>Implemented at build time</strong> - the bytecode enhancement processor will
     * replace this method body in each entity subclass with the actual query execution code.
     * <p>
     * Example:
     *
     * <pre>{@code
     * // Group by department and count employees
     * List<DeptCount> counts = Person
     *         .groupBy((Person p) -> p.department.name)
     *         .select((Group<Person, String> g) -> new DeptCount(g.key(), g.count()))
     *         .toList();
     *
     * // Group with HAVING clause
     * List<String> largeDepts = Person
     *         .groupBy((Person p) -> p.department.name)
     *         .having((Group<Person, String> g) -> g.count() > 5)
     *         .selectKey()
     *         .toList();
     *
     * // Group with multiple aggregations
     * List<DeptStats> stats = Person
     *         .groupBy((Person p) -> p.department.name)
     *         .select((Group<Person, String> g) -> new DeptStats(
     *                 g.key(),
     *                 g.count(),
     *                 g.avg((Person p) -> p.salary),
     *                 g.min((Person p) -> p.salary),
     *                 g.max((Person p) -> p.salary)))
     *         .toList();
     *
     * // Pre-filter before grouping
     * List<DeptCount> activeCounts = Person
     *         .where((Person p) -> p.active)
     *         .groupBy((Person p) -> p.department.name)
     *         .select((Group<Person, String> g) -> new DeptCount(g.key(), g.count()))
     *         .toList();
     * }</pre>
     *
     * @param <T> the entity type
     * @param <K> the type of the grouping key
     * @param keyExtractor lambda extracting the grouping key from an entity
     * @return a GroupStream for composing HAVING, SELECT, and ORDER BY operations
     * @throws IllegalStateException if called at runtime without build-time enhancement
     */
    public static <T extends QubitEntity, K> GroupStream<T, K> groupBy(QuerySpec<T, K> keyExtractor) {
        throw new IllegalStateException(
                "This method is normally automatically overridden in subclasses at build time. " +
                        "Did you forget to annotate your entity with @Entity? " +
                        "Or is the Qubit build-time processor not properly configured?");
    }
}
