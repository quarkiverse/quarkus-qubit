package io.quarkiverse.qubit;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Type-safe, fluent query builder translated to JPA Criteria Queries at build time.
 *
 * @param <T> the type of elements in this stream
 */
public interface QubitStream<T> {

    /** Filters entities matching the predicate. Multiple calls combine with AND. */
    QubitStream<T> where(QuerySpec<T, Boolean> predicate);

    /** Projects each entity to a new type. Supports field access, expressions, and DTO construction. */
    <R> QubitStream<R> select(QuerySpec<T, R> mapper);

    /** Sorts ascending. Last call becomes primary sort key. */
    <K extends Comparable<K>> QubitStream<T> sortedBy(QuerySpec<T, K> keyExtractor);

    /** Sorts descending. Last call becomes primary sort key. */
    <K extends Comparable<K>> QubitStream<T> sortedDescendingBy(QuerySpec<T, K> keyExtractor);

    /**
     * Skips the first {@code n} results (SQL OFFSET).
     *
     * @throws IllegalArgumentException if {@code n < 0}
     */
    QubitStream<T> skip(int n);

    /**
     * Limits results to {@code n} items (SQL LIMIT).
     *
     * @throws IllegalArgumentException if {@code n < 0}
     */
    QubitStream<T> limit(int n);

    /** Returns only distinct results (SQL SELECT DISTINCT). */
    QubitStream<T> distinct();

    /** Counts matching entities (terminal operation). */
    long count();

    /** Prepares MIN aggregation. Call {@link #getSingleResult()} to execute. */
    <K extends Comparable<K>> QubitStream<K> min(QuerySpec<T, K> mapper);

    /** Prepares MAX aggregation. Call {@link #getSingleResult()} to execute. */
    <K extends Comparable<K>> QubitStream<K> max(QuerySpec<T, K> mapper);

    /** Prepares SUM for Integer values (returns Long). Call {@link #getSingleResult()} to execute. */
    QubitStream<Long> sumInteger(QuerySpec<T, Integer> mapper);

    /** Prepares SUM for Long values (returns Long). Call {@link #getSingleResult()} to execute. */
    QubitStream<Long> sumLong(QuerySpec<T, Long> mapper);

    /** Prepares SUM for Double values (returns Double). Call {@link #getSingleResult()} to execute. */
    QubitStream<Double> sumDouble(QuerySpec<T, Double> mapper);

    /** Prepares AVG for numeric values (returns Double). Call {@link #getSingleResult()} to execute. */
    QubitStream<Double> avg(QuerySpec<T, ? extends Number> mapper);

    /** Executes query and returns all results as a list. Never null, may be empty. */
    List<T> toList();

    /**
     * Executes query expecting exactly one result (terminal operation).
     *
     * @return the single matching result
     * @throws jakarta.persistence.NoResultException if no result found
     * @throws jakarta.persistence.NonUniqueResultException if multiple results found
     */
    T getSingleResult();

    /** Executes query and returns first result as Optional, or empty if no results. */
    Optional<T> findFirst();

    /** Checks if any entity matches (terminal operation). */
    boolean exists();

    /**
     * Creates an inner join with a related collection.
     * Entities without matching related entities are excluded.
     *
     * @param <R> the related entity type
     * @param relationship accesses the collection relationship
     * @return JoinStream for composing join operations
     */
    <R> JoinStream<T, R> join(QuerySpec<T, Collection<R>> relationship);

    /**
     * Creates a left outer join with a related collection.
     * Returns all source entities, including those without matching related entities.
     *
     * @param <R> the related entity type
     * @param relationship accesses the collection relationship
     * @return JoinStream for composing join operations
     */
    <R> JoinStream<T, R> leftJoin(QuerySpec<T, Collection<R>> relationship);

    /**
     * Groups entities by the specified key (SQL GROUP BY).
     *
     * @param <K> the grouping key type
     * @param keyExtractor extracts grouping key (e.g., {@code p -> p.department})
     * @return GroupStream for composing HAVING and aggregations
     */
    <K> GroupStream<T, K> groupBy(QuerySpec<T, K> keyExtractor);
}
