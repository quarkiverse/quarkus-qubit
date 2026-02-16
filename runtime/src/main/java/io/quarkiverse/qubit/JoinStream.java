package io.quarkiverse.qubit;

import java.util.List;
import java.util.Optional;

/**
 * Fluent query builder for join operations with access to both entities.
 *
 * <pre>{@code
 * Person.join(p -> p.phones)
 *         .where((p, ph) -> ph.type.equals("mobile"))
 *         .select((p, ph) -> new PersonPhoneDTO(p.firstName, ph.number))
 *         .toList();
 * }</pre>
 *
 * @param <T> the source entity type (left side of join)
 * @param <R> the joined entity type (right side of join)
 */
public interface JoinStream<T, R> {

    /** Adds ON clause condition (unlike WHERE, doesn't filter NULL rows in left joins). */
    JoinStream<T, R> on(BiQuerySpec<T, R, Boolean> condition);

    /** Filters joined results with access to both entities. Multiple calls combine with AND. */
    JoinStream<T, R> where(BiQuerySpec<T, R, Boolean> predicate);

    /** Filters based on only the source entity. */
    JoinStream<T, R> where(QuerySpec<T, Boolean> predicate);

    /** Projects joined result to a new type using both entities. */
    <S> QubitStream<S> select(BiQuerySpec<T, R, S> mapper);

    /** Projects to source entity only (left side of join). */
    QubitStream<T> selectSource();

    /** Projects to joined entity only (right side of join). */
    QubitStream<R> selectJoined();

    /** Sorts results ascending using a key from either entity. */
    <K extends Comparable<K>> JoinStream<T, R> sortedBy(BiQuerySpec<T, R, K> keyExtractor);

    /** Sorts results descending using a key from either entity. */
    <K extends Comparable<K>> JoinStream<T, R> sortedDescendingBy(BiQuerySpec<T, R, K> keyExtractor);

    /** Adds a secondary ascending sort. Lower priority than sortedBy(). */
    <K extends Comparable<K>> JoinStream<T, R> thenSortedBy(BiQuerySpec<T, R, K> keyExtractor);

    /** Adds a secondary descending sort. Lower priority than sortedDescendingBy(). */
    <K extends Comparable<K>> JoinStream<T, R> thenSortedDescendingBy(BiQuerySpec<T, R, K> keyExtractor);

    /**
     * Skips the first {@code n} results (SQL OFFSET).
     *
     * @throws IllegalArgumentException if {@code n < 0}
     */
    JoinStream<T, R> skip(int n);

    /**
     * Limits results to {@code n} items (SQL LIMIT).
     *
     * @throws IllegalArgumentException if {@code n < 0}
     */
    JoinStream<T, R> limit(int n);

    /** Returns only distinct results (SQL SELECT DISTINCT). */
    JoinStream<T, R> distinct();

    /** Executes query and returns source entities as a list. */
    List<T> toList();

    /**
     * Executes query expecting exactly one result.
     *
     * @throws jakarta.persistence.NoResultException if no result found
     * @throws jakarta.persistence.NonUniqueResultException if multiple results found
     */
    T getSingleResult();

    /** Executes query and returns first result as Optional, or empty if no results. */
    Optional<T> findFirst();

    /** Counts matching join results. */
    long count();

    /** Checks if any result matches the join criteria. */
    boolean exists();
}
