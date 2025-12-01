package io.quarkiverse.qubit.runtime;

import java.util.List;

/**
 * Fluent query builder for grouped query operations.
 * <p>
 * Provides GROUP BY functionality with support for HAVING clause and
 * group-level aggregations. All operations are translated to JPA Criteria
 * Queries at build time.
 * <p>
 * Example usage:
 * <pre>{@code
 * // Simple grouping with count
 * List<DeptCount> results = Person
 *     .groupBy((Person p) -> p.department)
 *     .select((Group<Person, String> g) -> new DeptCount(g.key(), g.count()))
 *     .toList();
 *
 * // Grouping with HAVING clause
 * List<String> largeDepts = Person
 *     .groupBy((Person p) -> p.department)
 *     .having((Group<Person, String> g) -> g.count() > 5)
 *     .select((Group<Person, String> g) -> g.key())
 *     .toList();
 *
 * // Multiple aggregations
 * List<DeptStats> stats = Person
 *     .groupBy((Person p) -> p.department)
 *     .select((Group<Person, String> g) -> new DeptStats(
 *         g.key(),
 *         g.count(),
 *         g.avg((Person p) -> p.salary),
 *         g.min((Person p) -> p.salary),
 *         g.max((Person p) -> p.salary)
 *     ))
 *     .toList();
 *
 * // Pre-filter before grouping
 * List<DeptCount> activeResults = Person
 *     .where((Person p) -> p.active)
 *     .groupBy((Person p) -> p.department)
 *     .select((Group<Person, String> g) -> new DeptCount(g.key(), g.count()))
 *     .toList();
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
     * Filters groups based on aggregate conditions (SQL HAVING clause).
     * <p>
     * Unlike {@code where()}, which filters individual rows before grouping,
     * {@code having()} filters entire groups based on aggregate values.
     * Multiple {@code having()} calls are combined with AND logic.
     * <p>
     * Example:
     * <pre>{@code
     * // Only groups with more than 5 members
     * .having((Group<Person, String> g) -> g.count() > 5)
     *
     * // Groups with average salary above threshold
     * .having((Group<Person, String> g) -> g.avg(p -> p.salary) > 50000.0)
     *
     * // Combined conditions
     * .having((Group<Person, String> g) -> g.count() > 5)
     * .having((Group<Person, String> g) -> g.avg(p -> p.salary) > 50000.0)
     * }</pre>
     *
     * @param condition lambda expression returning boolean based on group aggregates
     * @return a new GroupStream with the HAVING condition applied
     */
    GroupStream<T, K> having(GroupQuerySpec<T, K, Boolean> condition);

    // =============================================================================================
    // PROJECTION
    // =============================================================================================

    /**
     * Projects each group to a new type.
     * <p>
     * The projection lambda has access to the {@link Group} context which provides:
     * <ul>
     *   <li>{@code g.key()} - the grouping key value</li>
     *   <li>{@code g.count()} - count of entities in group</li>
     *   <li>{@code g.avg(field)} - average of numeric field</li>
     *   <li>{@code g.sum*(field)} - sum of numeric field</li>
     *   <li>{@code g.min(field)} - minimum value of comparable field</li>
     *   <li>{@code g.max(field)} - maximum value of comparable field</li>
     * </ul>
     * <p>
     * Example:
     * <pre>{@code
     * // Project to DTO
     * .select((Group<Person, String> g) -> new DeptStats(
     *     g.key(),
     *     g.count(),
     *     g.avg((Person p) -> p.salary)
     * ))
     *
     * // Project to key only
     * .select((Group<Person, String> g) -> g.key())
     * }</pre>
     *
     * @param <R> the result type after projection
     * @param mapper lambda expression transforming group to projection
     * @return a new QubitStream with the projection applied
     */
    <R> QubitStream<R> select(GroupQuerySpec<T, K, R> mapper);

    /**
     * Selects only the grouping keys.
     * <p>
     * Convenience method equivalent to:
     * {@code .select((Group<T, K> g) -> g.key())}
     *
     * @return a QubitStream of grouping keys
     */
    QubitStream<K> selectKey();

    // =============================================================================================
    // SORTING
    // =============================================================================================

    /**
     * Sorts groups in ascending order by the specified key extractor.
     * <p>
     * Can sort by grouping key or by aggregate values.
     * <p>
     * Example:
     * <pre>{@code
     * // Sort by grouping key
     * .sortedBy((Group<Person, String> g) -> g.key())
     *
     * // Sort by count
     * .sortedBy((Group<Person, String> g) -> g.count())
     *
     * // Sort by aggregate value
     * .sortedBy((Group<Person, String> g) -> g.avg(p -> p.salary))
     * }</pre>
     *
     * @param <C> the type of the sort key (must be comparable)
     * @param keyExtractor lambda extracting sort key from group
     * @return a new GroupStream with the sort order applied
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
     */
    GroupStream<T, K> skip(int n);

    /**
     * Limits results to {@code n} groups (SQL LIMIT).
     *
     * @param n maximum number of groups to return (must be >= 0)
     * @return a new GroupStream with limit applied
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
