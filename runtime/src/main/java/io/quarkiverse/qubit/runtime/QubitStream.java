package io.quarkiverse.qubit.runtime;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Type-safe, fluent query builder translated to JPA Criteria Queries at build time.
 * <pre>{@code
 * Person.where(p -> p.active && p.salary > 100000)
 *       .select(p -> p.city).distinct().sortedBy(c -> c).limit(10).toList();
 * }</pre>
 *
 * @param <T> the type of elements in this stream
 */
public interface QubitStream<T> {

    // =============================================================================================
    // FILTERING
    // =============================================================================================

    /**
     * Filters entities matching the predicate. Multiple calls combine with AND.
     *
     * @param predicate filter condition
     * @return filtered stream
     */
    QubitStream<T> where(QuerySpec<T, Boolean> predicate);

    // =============================================================================================
    // PROJECTION
    // =============================================================================================

    /**
     * Projects each entity to a new type. Supports field access, expressions, and DTO construction.
     *
     * @param <R> result type
     * @param mapper projection function
     * @return projected stream
     */
    <R> QubitStream<R> select(QuerySpec<T, R> mapper);

    // =============================================================================================
    // SORTING
    // =============================================================================================

    /**
     * Sorts ascending. Last call becomes primary sort key.
     *
     * @param <K> comparable sort key type
     * @param keyExtractor sort key function
     * @return sorted stream
     */
    <K extends Comparable<K>> QubitStream<T> sortedBy(QuerySpec<T, K> keyExtractor);

    /**
     * Sorts descending.
     *
     * @param <K> comparable sort key type
     * @param keyExtractor sort key function
     * @return sorted stream
     */
    <K extends Comparable<K>> QubitStream<T> sortedDescendingBy(QuerySpec<T, K> keyExtractor);

    // =============================================================================================
    // PAGINATION
    // =============================================================================================

    /**
     * Skips the first {@code n} results (SQL OFFSET).
     *
     * @param n number of results to skip (must be >= 0)
     * @return a new stream with offset applied
     * @throws IllegalArgumentException if {@code n < 0}
     */
    QubitStream<T> skip(int n);

    /**
     * Limits results to {@code n} items (SQL LIMIT).
     *
     * @param n maximum number of results (must be >= 0)
     * @return a new stream with limit applied
     * @throws IllegalArgumentException if {@code n < 0}
     */
    QubitStream<T> limit(int n);

    // =============================================================================================
    // DISTINCT
    // =============================================================================================

    /**
     * Returns only distinct results (SQL SELECT DISTINCT).
     *
     * @return a new stream with distinct constraint applied
     */
    QubitStream<T> distinct();

    // =============================================================================================
    // AGGREGATION OPERATIONS
    // =============================================================================================

    /**
     * Counts matching entities (terminal operation).
     *
     * @return the count of matching entities
     */
    long count();

    /**
     * Prepares MIN aggregation. Call {@link #getSingleResult()} to execute.
     *
     * @param <K> comparable value type
     * @param mapper extracts value to minimize
     * @return stream configured for MIN aggregation
     */
    <K extends Comparable<K>> QubitStream<K> min(QuerySpec<T, K> mapper);

    /**
     * Prepares MAX aggregation. Call {@link #getSingleResult()} to execute.
     *
     * @param <K> comparable value type
     * @param mapper extracts value to maximize
     * @return stream configured for MAX aggregation
     */
    <K extends Comparable<K>> QubitStream<K> max(QuerySpec<T, K> mapper);

    /**
     * Prepares SUM aggregation for Integer values. Call {@link #getSingleResult()} to execute.
     *
     * @param mapper extracts integer values to sum
     * @return stream configured for SUM (returns Long)
     */
    QubitStream<Long> sumInteger(QuerySpec<T, Integer> mapper);

    /**
     * Prepares SUM aggregation for Long values. Call {@link #getSingleResult()} to execute.
     *
     * @param mapper extracts long values to sum
     * @return stream configured for SUM (returns Long)
     */
    QubitStream<Long> sumLong(QuerySpec<T, Long> mapper);

    /**
     * Prepares SUM aggregation for Double values. Call {@link #getSingleResult()} to execute.
     *
     * @param mapper extracts double values to sum
     * @return stream configured for SUM (returns Double)
     */
    QubitStream<Double> sumDouble(QuerySpec<T, Double> mapper);

    /**
     * Prepares AVG aggregation for numeric values. Call {@link #getSingleResult()} to execute.
     *
     * @param mapper extracts numeric values to average
     * @return stream configured for AVG (returns Double)
     */
    QubitStream<Double> avg(QuerySpec<T, ? extends Number> mapper);

    // =============================================================================================
    // TERMINAL OPERATIONS
    // =============================================================================================

    /**
     * Executes query and returns all results as a list (terminal operation).
     *
     * @return list of matching results (never null, may be empty)
     */
    List<T> toList();

    /**
     * Executes query expecting exactly one result (terminal operation).
     *
     * @return the single matching result
     * @throws jakarta.persistence.NoResultException if no result found
     * @throws jakarta.persistence.NonUniqueResultException if multiple results found
     */
    T getSingleResult();

    /**
     * Executes query and returns first result as Optional (terminal operation).
     *
     * @return Optional with first result, or empty if no results
     */
    Optional<T> findFirst();

    /**
     * Checks if any entity matches (terminal operation).
     *
     * @return true if at least one entity matches
     */
    boolean exists();

    // =============================================================================================
    // JOIN OPERATIONS
    // =============================================================================================

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

    // =============================================================================================
    // GROUPING OPERATIONS
    // =============================================================================================

    /**
     * Groups entities by the specified key (SQL GROUP BY).
     *
     * @param <K> the grouping key type
     * @param keyExtractor extracts grouping key (e.g., {@code p -> p.department})
     * @return GroupStream for composing HAVING and aggregations
     */
    <K> GroupStream<T, K> groupBy(QuerySpec<T, K> keyExtractor);
}
