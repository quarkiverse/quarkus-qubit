package io.quarkiverse.qubit.runtime;

import java.util.List;
import java.util.Optional;

/**
 * Fluent query builder for join operations with access to both entities.
 * <pre>{@code
 * Person.join(p -> p.phones)
 *       .where((p, ph) -> ph.type.equals("mobile"))
 *       .select((p, ph) -> new PersonPhoneDTO(p.firstName, ph.number))
 *       .toList();
 * }</pre>
 *
 * @param <T> the source entity type (left side of join)
 * @param <R> the joined entity type (right side of join)
 */
public interface JoinStream<T, R> {

    // ========== Join Conditions ==========

    /**
     * Adds an ON clause condition to the join.
     * Unlike WHERE, ON conditions don't filter NULL rows in left joins.
     *
     * @param condition ON clause condition
     * @return JoinStream with ON condition applied
     */
    JoinStream<T, R> on(BiQuerySpec<T, R, Boolean> condition);

    // ========== Filtering ==========

    /**
     * Filters joined results with access to both entities. Multiple calls combine with AND.
     *
     * @param predicate filter condition
     * @return JoinStream with filter applied
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

    // ========== Projection ==========

    /**
     * Projects joined result to a new type using both entities.
     *
     * @param <S> result type
     * @param mapper transforms both entities to projection
     * @return QubitStream with projection applied
     */
    <S> QubitStream<S> select(BiQuerySpec<T, R, S> mapper);

    /**
     * Projects to source entity only.
     * Useful when you want to filter by joined data but return only the source.
     *
     * @return a QubitStream of source entities
     */
    QubitStream<T> selectSource();

    /**
     * Projects to joined entity only.
     *
     * @return a QubitStream of joined entities
     */
    QubitStream<R> selectJoined();

    // ========== Sorting ==========

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

    // ========== Pagination ==========

    /**
     * Skips the first {@code n} results (SQL OFFSET).
     *
     * @param n number of results to skip (must be >= 0)
     * @return a new JoinStream with offset applied
     * @throws IllegalArgumentException if {@code n < 0}
     */
    JoinStream<T, R> skip(int n);

    /**
     * Limits results to {@code n} items (SQL LIMIT).
     *
     * @param n maximum number of results to return (must be >= 0)
     * @return a new JoinStream with limit applied
     * @throws IllegalArgumentException if {@code n < 0}
     */
    JoinStream<T, R> limit(int n);

    // ========== Distinct ==========

    /**
     * Returns only distinct results (SQL SELECT DISTINCT).
     *
     * @return a new JoinStream with distinct constraint applied
     */
    JoinStream<T, R> distinct();

    // ========== Terminal Operations ==========

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
