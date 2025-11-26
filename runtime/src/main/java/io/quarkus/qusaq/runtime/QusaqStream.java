package io.quarkus.qusaq.runtime;

import java.util.List;
import java.util.Optional;

/**
 * Type-safe, fluent query builder for entity queries.
 * All operations are translated to JPA Criteria Queries at build time.
 * <p>
 * This interface provides a JINQ-inspired fluent API for composing database queries
 * using method chaining. All query operations are analyzed at build time through
 * bytecode analysis and translated into optimized JPA Criteria Queries, ensuring
 * zero runtime overhead.
 * <p>
 * Example usage:
 * <pre>{@code
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
 * }</pre>
 *
 * @param <T> the type of elements in this stream
 */
public interface QusaqStream<T> {

    // =============================================================================================
    // FILTERING
    // =============================================================================================

    /**
     * Filters entities matching the given predicate.
     * Multiple {@code where()} calls are combined with AND logic.
     * <p>
     * Example:
     * <pre>{@code
     * // Single condition
     * Person.where(p -> p.age >= 18).toList();
     *
     * // Multiple conditions (AND)
     * Person.where(p -> p.age > 25)
     *       .where(p -> p.active)
     *       .toList();
     * }</pre>
     *
     * @param predicate lambda expression returning boolean (e.g., {@code p -> p.age > 18})
     * @return a new stream with the filter applied
     */
    QusaqStream<T> where(QuerySpec<T, Boolean> predicate);

    // =============================================================================================
    // PROJECTION
    // =============================================================================================

    /**
     * Projects each entity to a new type using the given mapper function.
     * This changes the type of the stream from {@code T} to {@code R}.
     * <p>
     * Supports:
     * <ul>
     *   <li>Field access: {@code p -> p.firstName}</li>
     *   <li>Expressions: {@code p -> p.firstName + " " + p.lastName}</li>
     *   <li>DTO construction: {@code p -> new PersonDTO(p.firstName, p.age)}</li>
     * </ul>
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
     * @param <R> the result type after projection
     * @param mapper lambda expression transforming entity to projection (e.g., {@code p -> p.firstName})
     * @return a new stream with the projection applied
     */
    <R> QusaqStream<R> select(QuerySpec<T, R> mapper);

    // =============================================================================================
    // SORTING
    // =============================================================================================

    /**
     * Sorts results in ascending order by the given key extractor.
     * <p>
     * For multi-level sorting, call {@code sortedBy()} multiple times.
     * The <strong>last call</strong> becomes the primary sort key (JINQ approach).
     * <p>
     * Example:
     * <pre>{@code
     * // Single-level sort
     * Person.sortedBy(p -> p.age).toList();
     *
     * // Multi-level sort (last call wins)
     * Person.sortedBy(p -> p.firstName)  // Secondary sort
     *       .sortedBy(p -> p.lastName)   // Primary sort
     *       .toList();
     * // SQL: ORDER BY lastName, firstName
     * }</pre>
     *
     * @param <K> the type of the sort key (must be comparable)
     * @param keyExtractor lambda expression extracting sort key (e.g., {@code p -> p.age})
     * @return a new stream with the sort order applied
     */
    <K extends Comparable<K>> QusaqStream<T> sortedBy(QuerySpec<T, K> keyExtractor);

    /**
     * Sorts results in descending order by the given key extractor.
     * <p>
     * Example:
     * <pre>{@code
     * // Descending sort
     * Person.sortedDescendingBy(p -> p.age).toList();
     *
     * // Highest salaries first
     * Person.sortedDescendingBy(p -> p.salary).limit(5).toList();
     * }</pre>
     *
     * @param <K> the type of the sort key (must be comparable)
     * @param keyExtractor lambda expression extracting sort key (e.g., {@code p -> p.salary})
     * @return a new stream with the descending sort order applied
     */
    <K extends Comparable<K>> QusaqStream<T> sortedDescendingBy(QuerySpec<T, K> keyExtractor);

    // =============================================================================================
    // PAGINATION
    // =============================================================================================

    /**
     * Skips the first {@code n} results (SQL OFFSET).
     * <p>
     * Example:
     * <pre>{@code
     * // Skip first 20 results
     * Person.sortedBy(p -> p.id).skip(20).toList();
     *
     * // Page 3 (skip 20, take 10)
     * Person.sortedBy(p -> p.id).skip(20).limit(10).toList();
     * }</pre>
     *
     * @param n number of results to skip (must be >= 0)
     * @return a new stream with offset applied
     * @throws IllegalArgumentException if {@code n < 0}
     */
    QusaqStream<T> skip(int n);

    /**
     * Limits results to {@code n} items (SQL LIMIT).
     * <p>
     * Example:
     * <pre>{@code
     * // Top 10 results
     * Person.sortedDescendingBy(p -> p.salary).limit(10).toList();
     *
     * // First 5 active users
     * Person.where(p -> p.active).limit(5).toList();
     * }</pre>
     *
     * @param n maximum number of results to return (must be >= 0)
     * @return a new stream with limit applied
     * @throws IllegalArgumentException if {@code n < 0}
     */
    QusaqStream<T> limit(int n);

    // =============================================================================================
    // DISTINCT
    // =============================================================================================

    /**
     * Returns only distinct results (SQL SELECT DISTINCT).
     * <p>
     * Example:
     * <pre>{@code
     * // Unique last names
     * List<String> uniqueLastNames = Person.select(p -> p.lastName)
     *                                      .distinct()
     *                                      .toList();
     *
     * // Distinct entities
     * Person.where(p -> p.active).distinct().toList();
     * }</pre>
     *
     * @return a new stream with distinct constraint applied
     */
    QusaqStream<T> distinct();

    // =============================================================================================
    // AGGREGATION OPERATIONS
    // =============================================================================================

    /**
     * Counts the number of entities matching the current query.
     * This is a terminal operation that executes the query.
     * <p>
     * Example:
     * <pre>{@code
     * // Count all
     * long total = Person.where(p -> p.age != null).count();
     *
     * // Count active users
     * long activeCount = Person.where(p -> p.active).count();
     * }</pre>
     *
     * @return the count of matching entities
     */
    long count();

    /**
     * Prepares a minimum value aggregation query.
     * This is an intermediate operation that sets up the aggregation but does not execute it.
     * Call {@link #getSingleResult()} to execute and get the result.
     * <p>
     * Example:
     * <pre>{@code
     * // Minimum age
     * Integer minAge = Person.where(p -> p.age != null).min(p -> p.age).getSingleResult();
     *
     * // Direct call (without where)
     * Integer minAge = Person.min(p -> p.age).getSingleResult();
     *
     * // Earliest birth date
     * LocalDate earliest = Person.where(p -> p.birthDate != null).min(p -> p.birthDate).getSingleResult();
     * }</pre>
     *
     * @param <K> the type of the value (must be comparable)
     * @param mapper lambda expression extracting the value to minimize
     * @return a new stream configured for MIN aggregation
     */
    <K extends Comparable<K>> QusaqStream<K> min(QuerySpec<T, K> mapper);

    /**
     * Prepares a maximum value aggregation query.
     * This is an intermediate operation that sets up the aggregation but does not execute it.
     * Call {@link #getSingleResult()} to execute and get the result.
     * <p>
     * Example:
     * <pre>{@code
     * // Maximum age
     * Integer maxAge = Person.where(p -> p.age != null).max(p -> p.age).getSingleResult();
     *
     * // Direct call (without where)
     * Integer maxAge = Person.max(p -> p.age).getSingleResult();
     *
     * // Highest salary
     * Double topSalary = Person.where(p -> p.active).max(p -> p.salary).getSingleResult();
     * }</pre>
     *
     * @param <K> the type of the value (must be comparable)
     * @param mapper lambda expression extracting the value to maximize
     * @return a new stream configured for MAX aggregation
     */
    <K extends Comparable<K>> QusaqStream<K> max(QuerySpec<T, K> mapper);

    /**
     * Prepares a sum aggregation query for Integer values.
     * This is an intermediate operation that sets up the aggregation but does not execute it.
     * Call {@link #getSingleResult()} to execute and get the result.
     * <p>
     * Example:
     * <pre>{@code
     * // Sum of all ages
     * Long totalAge = Person.where(p -> p.age != null).sumInteger(p -> p.age).getSingleResult();
     *
     * // Direct call (without where)
     * Long totalAge = Person.sumInteger(p -> p.age).getSingleResult();
     * }</pre>
     *
     * @param mapper lambda expression extracting integer values to sum
     * @return a new stream configured for SUM aggregation (returns Long)
     */
    QusaqStream<Long> sumInteger(QuerySpec<T, Integer> mapper);

    /**
     * Prepares a sum aggregation query for Long values.
     * This is an intermediate operation that sets up the aggregation but does not execute it.
     * Call {@link #getSingleResult()} to execute and get the result.
     * <p>
     * Example:
     * <pre>{@code
     * // Sum of employee IDs
     * Long totalIds = Person.where(p -> p.employeeId != null).sumLong(p -> p.employeeId).getSingleResult();
     *
     * // Direct call (without where)
     * Long totalIds = Person.sumLong(p -> p.employeeId).getSingleResult();
     * }</pre>
     *
     * @param mapper lambda expression extracting long values to sum
     * @return a new stream configured for SUM aggregation (returns Long)
     */
    QusaqStream<Long> sumLong(QuerySpec<T, Long> mapper);

    /**
     * Prepares a sum aggregation query for Double values.
     * This is an intermediate operation that sets up the aggregation but does not execute it.
     * Call {@link #getSingleResult()} to execute and get the result.
     * <p>
     * Example:
     * <pre>{@code
     * // Total salaries
     * Double totalSalaries = Person.where(p -> p.active).sumDouble(p -> p.salary).getSingleResult();
     *
     * // Direct call (without where)
     * Double totalSalaries = Person.sumDouble(p -> p.salary).getSingleResult();
     * }</pre>
     *
     * @param mapper lambda expression extracting double values to sum
     * @return a new stream configured for SUM aggregation (returns Double)
     */
    QusaqStream<Double> sumDouble(QuerySpec<T, Double> mapper);

    /**
     * Prepares an average aggregation query for numeric values.
     * This is an intermediate operation that sets up the aggregation but does not execute it.
     * Call {@link #getSingleResult()} to execute and get the result.
     * <p>
     * Example:
     * <pre>{@code
     * // Average age
     * Double avgAge = Person.where(p -> p.age != null).avg(p -> p.age).getSingleResult();
     *
     * // Direct call (without where)
     * Double avgAge = Person.avg(p -> p.age).getSingleResult();
     *
     * // Average salary
     * Double avgSalary = Person.where(p -> p.active).avg(p -> p.salary).getSingleResult();
     * }</pre>
     *
     * @param mapper lambda expression extracting numeric values to average
     * @return a new stream configured for AVG aggregation (returns Double)
     */
    QusaqStream<Double> avg(QuerySpec<T, ? extends Number> mapper);

    // =============================================================================================
    // TERMINAL OPERATIONS
    // =============================================================================================

    /**
     * Executes the query and returns all results as a list.
     * This is a terminal operation.
     * <p>
     * Example:
     * <pre>{@code
     * // Get all adults
     * List<Person> adults = Person.where(p -> p.age >= 18).toList();
     *
     * // Get top 10 cities
     * List<String> cities = Person.select(p -> p.city)
     *                             .distinct()
     *                             .sortedBy(c -> c)
     *                             .limit(10)
     *                             .toList();
     * }</pre>
     *
     * @return list of all matching results (never null, may be empty)
     */
    List<T> toList();

    /**
     * Executes the query expecting exactly one result.
     * This is a terminal operation.
     * <p>
     * Example:
     * <pre>{@code
     * // Get specific person by unique email
     * Person person = Person.where(p -> p.email.equals("john@example.com"))
     *                       .getSingleResult();
     * }</pre>
     *
     * @return the single matching result
     * @throws jakarta.persistence.NoResultException if no result found
     * @throws jakarta.persistence.NonUniqueResultException if multiple results found
     */
    T getSingleResult();

    /**
     * Executes the query and returns the first result as an Optional.
     * This is a terminal operation.
     * <p>
     * Example:
     * <pre>{@code
     * // Find first active person
     * Optional<Person> first = Person.where(p -> p.active).findFirst();
     *
     * // Find oldest person
     * Optional<Person> oldest = Person.sortedDescendingBy(p -> p.age)
     *                                 .findFirst();
     * }</pre>
     *
     * @return Optional containing the first result, or empty if no results
     */
    Optional<T> findFirst();

    /**
     * Checks if any entity matches the current query.
     * This is a terminal operation that executes the query.
     * <p>
     * Equivalent to {@code count() > 0} but may be optimized by the database.
     * <p>
     * Example:
     * <pre>{@code
     * // Check if any active users exist
     * boolean hasActive = Person.where(p -> p.active).exists();
     *
     * // Check if email is taken
     * boolean emailExists = Person.where(p -> p.email.equals("test@example.com"))
     *                             .exists();
     * }</pre>
     *
     * @return true if at least one entity matches, false otherwise
     */
    boolean exists();

    // =============================================================================================
    // JOIN OPERATIONS
    // =============================================================================================

    /**
     * Creates an inner join with a related collection.
     * <p>
     * Inner join returns only entities that have matching related entities.
     * Entities without any related entities are excluded from results.
     * <p>
     * Example:
     * <pre>{@code
     * // Get persons who have phones
     * List<Person> peopleWithPhones = Person
     *     .join((Person p) -> p.phones)
     *     .toList();
     *
     * // Filter by joined entity
     * List<Person> peopleWithMobile = Person
     *     .join((Person p) -> p.phones)
     *     .where((Person p, Phone ph) -> ph.type.equals("mobile"))
     *     .toList();
     * }</pre>
     *
     * @param <R> the type of the related entity
     * @param relationship lambda expression accessing the collection relationship
     * @return a JoinStream for composing join operations
     */
    <R> JoinStream<T, R> join(QuerySpec<T, java.util.Collection<R>> relationship);

    /**
     * Creates a left outer join with a related collection.
     * <p>
     * Left join returns all entities from the source, including those without
     * any matching related entities (with NULL for joined entity fields).
     * <p>
     * Example:
     * <pre>{@code
     * // Get all persons, including those without phones
     * List<Person> allPeople = Person
     *     .leftJoin((Person p) -> p.phones)
     *     .toList();
     *
     * // Filter while preserving persons without phones
     * List<Person> people = Person
     *     .leftJoin((Person p) -> p.phones)
     *     .where((Person p, Phone ph) -> ph == null || ph.type.equals("mobile"))
     *     .toList();
     * }</pre>
     *
     * @param <R> the type of the related entity
     * @param relationship lambda expression accessing the collection relationship
     * @return a JoinStream for composing join operations
     */
    <R> JoinStream<T, R> leftJoin(QuerySpec<T, java.util.Collection<R>> relationship);

    // =============================================================================================
    // GROUPING OPERATIONS
    // =============================================================================================

    /**
     * Groups entities by the specified key extractor.
     * <p>
     * Creates a GROUP BY query where entities are grouped based on the value
     * extracted by the key extractor lambda. Returns a {@link GroupStream}
     * for composing group operations like HAVING and aggregations.
     * <p>
     * Example usage:
     * <pre>{@code
     * // Group by department
     * List<DeptCount> results = Person
     *     .groupBy((Person p) -> p.department)
     *     .select((Group<Person, String> g) -> new DeptCount(g.key(), g.count()))
     *     .toList();
     *
     * // Pre-filter before grouping
     * List<DeptCount> activeResults = Person
     *     .where((Person p) -> p.active)
     *     .groupBy((Person p) -> p.department)
     *     .select((Group<Person, String> g) -> new DeptCount(g.key(), g.count()))
     *     .toList();
     *
     * // Group with HAVING
     * List<String> largeDepts = Person
     *     .groupBy((Person p) -> p.department)
     *     .having((Group<Person, String> g) -> g.count() > 5)
     *     .select((Group<Person, String> g) -> g.key())
     *     .toList();
     * }</pre>
     *
     * @param <K> the type of the grouping key
     * @param keyExtractor lambda expression extracting the grouping key (e.g., {@code p -> p.department})
     * @return a GroupStream for composing group operations
     */
    <K> GroupStream<T, K> groupBy(QuerySpec<T, K> keyExtractor);
}
