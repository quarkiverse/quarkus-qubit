package io.quarkiverse.qubit.runtime;

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

    /**
     * Returns the grouping key for this group.
     * <p>
     * Maps to the expression used in {@code groupBy()}.
     *
     * @return the grouping key value
     */
    K key();

    /**
     * Returns the count of entities in this group.
     * <p>
     * Maps to JPA {@code cb.count(root)}.
     *
     * @return the count of entities in the group
     */
    long count();

    /**
     * Returns the count of distinct values for the specified field in this group.
     * <p>
     * Maps to JPA {@code cb.countDistinct(root.get("field"))}.
     *
     * @param <V> the field type
     * @param fieldExtractor lambda expression extracting the field
     * @return the count of distinct values
     */
    <V> long countDistinct(QuerySpec<T, V> fieldExtractor);

    /**
     * Returns the average of a numeric field across entities in this group.
     * <p>
     * Maps to JPA {@code cb.avg(root.get("field"))}.
     *
     * @param <N> the numeric field type
     * @param fieldExtractor lambda expression extracting the numeric field
     * @return the average value (always Double, null if no non-null values)
     */
    <N extends Number> Double avg(QuerySpec<T, N> fieldExtractor);

    /**
     * Returns the sum of an integer field across entities in this group.
     * <p>
     * Maps to JPA {@code cb.sum(root.get("field"))}.
     *
     * @param fieldExtractor lambda expression extracting the integer field
     * @return the sum (as Long)
     */
    Long sumInteger(QuerySpec<T, Integer> fieldExtractor);

    /**
     * Returns the sum of a long field across entities in this group.
     * <p>
     * Maps to JPA {@code cb.sum(root.get("field"))}.
     *
     * @param fieldExtractor lambda expression extracting the long field
     * @return the sum (as Long)
     */
    Long sumLong(QuerySpec<T, Long> fieldExtractor);

    /**
     * Returns the sum of a double field across entities in this group.
     * <p>
     * Maps to JPA {@code cb.sum(root.get("field"))}.
     *
     * @param fieldExtractor lambda expression extracting the double field
     * @return the sum (as Double)
     */
    Double sumDouble(QuerySpec<T, Double> fieldExtractor);

    /**
     * Returns the minimum value of a comparable field across entities in this group.
     * <p>
     * Maps to JPA {@code cb.min(root.get("field"))} or {@code cb.least(root.get("field"))}.
     *
     * @param <C> the comparable field type
     * @param fieldExtractor lambda expression extracting the comparable field
     * @return the minimum value
     */
    <C extends Comparable<C>> C min(QuerySpec<T, C> fieldExtractor);

    /**
     * Returns the maximum value of a comparable field across entities in this group.
     * <p>
     * Maps to JPA {@code cb.max(root.get("field"))} or {@code cb.greatest(root.get("field"))}.
     *
     * @param <C> the comparable field type
     * @param fieldExtractor lambda expression extracting the comparable field
     * @return the maximum value
     */
    <C extends Comparable<C>> C max(QuerySpec<T, C> fieldExtractor);
}
