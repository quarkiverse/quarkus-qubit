package io.quarkus.qusaq.runtime;

import java.util.List;
import java.util.Optional;

/**
 * Fluent query builder for join operations between two entities.
 * <p>
 * Extends the capabilities of {@link QusaqStream} to provide predicates
 * and projections that have access to both joined entities. All operations
 * are translated to JPA Criteria Queries at build time.
 * <p>
 * Example usage:
 * <pre>{@code
 * // Inner join with filtering
 * List<Person> peopleWithMobilePhones = Person
 *     .join((Person p) -> p.phones)
 *     .where((Person p, Phone ph) -> ph.type.equals("mobile"))
 *     .toList();
 *
 * // Left join (include persons without phones)
 * List<Person> allPeopleWithPhoneInfo = Person
 *     .leftJoin((Person p) -> p.phones)
 *     .toList();
 *
 * // Join with projection
 * List<PersonPhoneDTO> dtos = Person
 *     .join((Person p) -> p.phones)
 *     .select((Person p, Phone ph) -> new PersonPhoneDTO(p.firstName, ph.number))
 *     .toList();
 * }</pre>
 *
 * @param <T> the source entity type (left side of join)
 * @param <R> the joined entity type (right side of join)
 */
public interface JoinStream<T, R> {

    // =============================================================================================
    // JOIN CONDITIONS
    // =============================================================================================

    /**
     * Adds an ON clause condition to the join.
     * <p>
     * The ON clause specifies conditions that are part of the join itself,
     * affecting how rows are matched between tables. For LEFT joins, this
     * differs from WHERE in that it doesn't filter out NULL rows from the
     * left side.
     * <p>
     * Example:
     * <pre>{@code
     * Person.join((Person p) -> p.phones)
     *       .on((Person p, Phone ph) -> ph.verified)
     *       .toList();
     * }</pre>
     *
     * @param condition lambda expression for the ON clause (e.g., {@code (p, ph) -> ph.verified})
     * @return a new JoinStream with the ON condition applied
     */
    JoinStream<T, R> on(BiQuerySpec<T, R, Boolean> condition);

    // =============================================================================================
    // FILTERING
    // =============================================================================================

    /**
     * Filters joined results using a predicate with access to both entities.
     * <p>
     * Multiple {@code where()} calls are combined with AND logic.
     * <p>
     * Example:
     * <pre>{@code
     * Person.join((Person p) -> p.phones)
     *       .where((Person p, Phone ph) -> ph.type.equals("mobile"))
     *       .where((Person p, Phone ph) -> p.active)
     *       .toList();
     * }</pre>
     *
     * @param predicate lambda expression returning boolean
     * @return a new JoinStream with the filter applied
     */
    JoinStream<T, R> where(BiQuerySpec<T, R, Boolean> predicate);

    /**
     * Filters based on only the source entity.
     * Convenience method when the filter doesn't need the joined entity.
     *
     * @param predicate lambda expression for source entity
     * @return a new JoinStream with the filter applied
     */
    JoinStream<T, R> where(QuerySpec<T, Boolean> predicate);

    // =============================================================================================
    // PROJECTION
    // =============================================================================================

    /**
     * Projects the joined result to a new type using both entities.
     * <p>
     * Example:
     * <pre>{@code
     * List<PersonPhoneDTO> dtos = Person
     *     .join((Person p) -> p.phones)
     *     .select((Person p, Phone ph) -> new PersonPhoneDTO(p.firstName, ph.number))
     *     .toList();
     * }</pre>
     *
     * @param <S> the result type after projection
     * @param mapper lambda expression transforming both entities to projection
     * @return a new QusaqStream with the projection applied
     */
    <S> QusaqStream<S> select(BiQuerySpec<T, R, S> mapper);

    /**
     * Projects to source entity only.
     * Useful when you want to filter by joined data but return only the source.
     *
     * @return a QusaqStream of source entities
     */
    QusaqStream<T> selectSource();

    /**
     * Projects to joined entity only.
     *
     * @return a QusaqStream of joined entities
     */
    QusaqStream<R> selectJoined();

    // =============================================================================================
    // SORTING
    // =============================================================================================

    /**
     * Sorts results in ascending order using a key from either entity.
     *
     * @param <K> the type of the sort key (must be comparable)
     * @param keyExtractor lambda extracting sort key from both entities
     * @return a new JoinStream with the sort order applied
     */
    <K extends Comparable<K>> JoinStream<T, R> sortedBy(BiQuerySpec<T, R, K> keyExtractor);

    /**
     * Sorts results in descending order using a key from either entity.
     *
     * @param <K> the type of the sort key (must be comparable)
     * @param keyExtractor lambda extracting sort key from both entities
     * @return a new JoinStream with the sort order applied
     */
    <K extends Comparable<K>> JoinStream<T, R> sortedDescendingBy(BiQuerySpec<T, R, K> keyExtractor);

    // =============================================================================================
    // PAGINATION
    // =============================================================================================

    /**
     * Skips the first {@code n} results (SQL OFFSET).
     *
     * @param n number of results to skip (must be >= 0)
     * @return a new JoinStream with offset applied
     */
    JoinStream<T, R> skip(int n);

    /**
     * Limits results to {@code n} items (SQL LIMIT).
     *
     * @param n maximum number of results to return (must be >= 0)
     * @return a new JoinStream with limit applied
     */
    JoinStream<T, R> limit(int n);

    // =============================================================================================
    // DISTINCT
    // =============================================================================================

    /**
     * Returns only distinct results (SQL SELECT DISTINCT).
     *
     * @return a new JoinStream with distinct constraint applied
     */
    JoinStream<T, R> distinct();

    // =============================================================================================
    // TERMINAL OPERATIONS
    // =============================================================================================

    /**
     * Executes the query and returns all source entities as a list.
     * By default, returns the source entity (left side of join).
     *
     * @return list of source entities matching the join criteria
     */
    List<T> toList();

    /**
     * Executes the query expecting exactly one result.
     *
     * @return the single matching source entity
     * @throws jakarta.persistence.NoResultException if no result found
     * @throws jakarta.persistence.NonUniqueResultException if multiple results found
     */
    T getSingleResult();

    /**
     * Executes the query and returns the first result as an Optional.
     *
     * @return Optional containing the first source entity, or empty if no results
     */
    Optional<T> findFirst();

    /**
     * Counts the number of matching results.
     *
     * @return the count of matching join results
     */
    long count();

    /**
     * Checks if any result matches the join criteria.
     *
     * @return true if at least one result matches, false otherwise
     */
    boolean exists();
}
