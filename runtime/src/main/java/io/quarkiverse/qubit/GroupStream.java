package io.quarkiverse.qubit;

import java.util.List;

/**
 * Fluent query builder for GROUP BY operations with HAVING and aggregations.
 *
 * <pre>{@code
 * Person.groupBy(p -> p.department)
 *         .having(g -> g.count() > 5)
 *         .select(g -> new DeptStats(g.key(), g.count(), g.avg(p -> p.salary)))
 *         .toList();
 * }</pre>
 *
 * @param <T> the entity type being grouped
 * @param <K> the type of the grouping key
 */
public interface GroupStream<T, K> {

    /** Filters groups based on aggregate conditions (SQL HAVING). Multiple calls combine with AND. */
    GroupStream<T, K> having(GroupQuerySpec<T, K, Boolean> condition);

    /** Projects each group to a new type using {@link Group} methods: key(), count(), avg(), sum*(), min(), max(). */
    <R> QubitStream<R> select(GroupQuerySpec<T, K, R> mapper);

    /** Selects only the grouping keys. */
    QubitStream<K> selectKey();

    /** Sorts groups ascending by key or aggregate values. */
    <C extends Comparable<C>> GroupStream<T, K> sortedBy(GroupQuerySpec<T, K, C> keyExtractor);

    /** Sorts groups descending by key or aggregate values. */
    <C extends Comparable<C>> GroupStream<T, K> sortedDescendingBy(GroupQuerySpec<T, K, C> keyExtractor);

    /**
     * Skips the first {@code n} groups (SQL OFFSET).
     *
     * @throws IllegalArgumentException if {@code n < 0}
     */
    GroupStream<T, K> skip(int n);

    /**
     * Limits results to {@code n} groups (SQL LIMIT).
     *
     * @throws IllegalArgumentException if {@code n < 0}
     */
    GroupStream<T, K> limit(int n);

    /** Executes query and returns all grouping keys. Equivalent to {@code .selectKey().toList()}. */
    List<K> toList();

    /** Counts the number of distinct groups (not total entities). */
    long count();
}
