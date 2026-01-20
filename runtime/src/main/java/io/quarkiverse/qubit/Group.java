package io.quarkiverse.qubit;

/**
 * Represents a group of entities with the same grouping key.
 * <p>
 * Used in {@code groupBy()} and {@code having()} lambdas to access the grouping key
 * and perform aggregate operations over the group's entities.
 * <p>
 * This is a marker interface used at compile time for lambda analysis.
 * The actual group operations are translated to JPA Criteria API aggregate functions.
 * <p>
 * Example usage:
 * <pre>{@code
 * // Group by department and get count per department
 * List<DeptCount> results = Person
 *     .groupBy((Person p) -> p.department)
 *     .select((Group<Person, String> g) -> new DeptCount(g.key(), g.count()))
 *     .toList();
 *
 * // Group by department with HAVING clause
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
 * }</pre>
 *
 * @param <T> the entity type being grouped
 * @param <K> the type of the grouping key
 */
public interface Group<T, K> {

    /** Returns the grouping key (maps to {@code groupBy()} expression). */
    K key();

    /** Counts entities in this group (JPA {@code cb.count(root)}). */
    long count();

    /** Counts distinct values for a field (JPA {@code cb.countDistinct(...)}). */
    <V> long countDistinct(QuerySpec<T, V> fieldExtractor);

    /** Averages a numeric field (JPA {@code cb.avg(...)}). Returns null if no non-null values. */
    <N extends Number> Double avg(QuerySpec<T, N> fieldExtractor);

    /** Sums Integer field values (returns Long). */
    Long sumInteger(QuerySpec<T, Integer> fieldExtractor);

    /** Sums Long field values (returns Long). */
    Long sumLong(QuerySpec<T, Long> fieldExtractor);

    /** Sums Double field values (returns Double). */
    Double sumDouble(QuerySpec<T, Double> fieldExtractor);

    /** Minimum value of a comparable field. */
    <C extends Comparable<C>> C min(QuerySpec<T, C> fieldExtractor);

    /** Maximum value of a comparable field. */
    <C extends Comparable<C>> C max(QuerySpec<T, C> fieldExtractor);
}
