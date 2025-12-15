package io.quarkiverse.qubit.runtime;

import java.util.List;

/**
 * Fluent query builder for GROUP BY operations with HAVING and aggregations.
 * <pre>{@code
 * Person.groupBy(p -> p.department)
 *       .having(g -> g.count() > 5)
 *       .select(g -> new DeptStats(g.key(), g.count(), g.avg(p -> p.salary)))
 *       .toList();
 * }</pre>
 *
 * @param <T> the entity type being grouped
 * @param <K> the type of the grouping key
 */
public interface GroupStream<T, K> {

    // =============================================================================================
    // HAVING CLAUSE
    // =============================================================================================

    /**
     * Filters groups based on aggregate conditions (SQL HAVING).
     * Multiple calls combine with AND.
     *
     * @param condition aggregate-based filter
     * @return GroupStream with HAVING condition applied
     */
    GroupStream<T, K> having(GroupQuerySpec<T, K, Boolean> condition);

    // =============================================================================================
    // PROJECTION
    // =============================================================================================

    /**
     * Projects each group to a new type.
     * Access {@link Group} methods: key(), count(), avg(), sum*(), min(), max().
     *
     * @param <R> result type
     * @param mapper transforms group to projection
     * @return QubitStream with projection applied
     */
    <R> QubitStream<R> select(GroupQuerySpec<T, K, R> mapper);

    /**
     * Selects only the grouping keys.
     *
     * @return QubitStream of grouping keys
     */
    QubitStream<K> selectKey();

    // =============================================================================================
    // SORTING
    // =============================================================================================

    /**
     * Sorts groups ascending. Can sort by key or aggregate values.
     *
     * @param <C> comparable sort key type
     * @param keyExtractor extracts sort key from group
     * @return GroupStream with sort applied
     */
    <C extends Comparable<C>> GroupStream<T, K> sortedBy(GroupQuerySpec<T, K, C> keyExtractor);

    /**
     * Sorts groups in descending order by the specified key extractor.
     *
     * @param <C> the type of the sort key (must be comparable)
     * @param keyExtractor lambda extracting sort key from group
     * @return a new GroupStream with the descending sort order applied
     */
    <C extends Comparable<C>> GroupStream<T, K> sortedDescendingBy(GroupQuerySpec<T, K, C> keyExtractor);

    // =============================================================================================
    // PAGINATION
    // =============================================================================================

    /**
     * Skips the first {@code n} groups (SQL OFFSET).
     *
     * @param n number of groups to skip (must be >= 0)
     * @return a new GroupStream with offset applied
     * @throws IllegalArgumentException if {@code n < 0}
     */
    GroupStream<T, K> skip(int n);

    /**
     * Limits results to {@code n} groups (SQL LIMIT).
     *
     * @param n maximum number of groups to return (must be >= 0)
     * @return a new GroupStream with limit applied
     * @throws IllegalArgumentException if {@code n < 0}
     */
    GroupStream<T, K> limit(int n);

    // =============================================================================================
    // TERMINAL OPERATIONS
    // =============================================================================================

    /**
     * Executes the query and returns all grouping keys as a list.
     * <p>
     * Equivalent to {@code .selectKey().toList()}.
     *
     * @return list of all grouping keys
     */
    List<K> toList();

    /**
     * Counts the number of groups.
     * <p>
     * Note: This counts the number of distinct groups, not the total
     * number of entities across all groups.
     *
     * @return the count of groups
     */
    long count();
}
