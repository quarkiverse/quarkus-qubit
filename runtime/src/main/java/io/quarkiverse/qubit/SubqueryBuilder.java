package io.quarkiverse.qubit;

/**
 * Fluent builder for constructing subquery expressions.
 * <p>
 * Provides type-safe API for scalar aggregations (avg, sum, min, max, count),
 * existence checks (exists, notExists), and membership (in, notIn). All methods
 * are <strong>bytecode markers</strong> analyzed at build time, never called at runtime.
 * <p>
 * Usage:
 *
 * <pre>{@code
 * import static io.quarkiverse.qubit.Subqueries.*;
 *
 * // Scalar aggregation
 * Person.where(p -> p.salary > subquery(Person.class).avg(q -> q.salary)).toList();
 *
 * // With filtering
 * Person.where(p -> p.salary > subquery(Person.class)
 *     .where(q -> q.active)
 *     .avg(q -> q.salary)
 * ).toList();
 * }</pre>
 *
 * @param <T> the entity type for the subquery
 * @see Subqueries#subquery(Class)
 */
public final class SubqueryBuilder<T> {

    private final Class<T> entityClass;

    /**
     * Package-private constructor - use {@link Subqueries#subquery(Class)} instead.
     *
     * @param entityClass the entity class for the subquery
     */
    SubqueryBuilder(Class<T> entityClass) {
        this.entityClass = entityClass;
    }

    /**
     * Adds a filtering predicate to the subquery (WHERE clause).
     * <p>
     * Chainable - multiple calls combine with AND. Generates: {@code SELECT AGG(field) FROM Entity WHERE predicate}
     *
     * @param predicate the filtering predicate
     * @return this builder for method chaining (bytecode marker only)
     */
    public SubqueryBuilder<T> where(QuerySpec<T, Boolean> predicate) {
        throw new UnsupportedOperationException(
                "SubqueryBuilder.where() should never be called at runtime. " +
                        "This method exists only for bytecode analysis at build time.");
    }

    /**
     * Returns the average of a numeric field. Generates: {@code SELECT AVG(field) FROM Entity}
     *
     * @param selector field selector for the numeric field to average
     * @param <N> the numeric field type
     * @return the average value (bytecode marker only)
     */
    public <N extends Number> Double avg(QuerySpec<T, N> selector) {
        throw new UnsupportedOperationException(
                "SubqueryBuilder.avg() should never be called at runtime. " +
                        "This method exists only for bytecode analysis at build time.");
    }

    /**
     * Returns the sum of a numeric field. Generates: {@code SELECT SUM(field) FROM Entity}
     *
     * @param selector field selector for the numeric field to sum
     * @param <N> the numeric field type
     * @return the sum value (bytecode marker only)
     */
    public <N extends Number> N sum(QuerySpec<T, N> selector) {
        throw new UnsupportedOperationException(
                "SubqueryBuilder.sum() should never be called at runtime. " +
                        "This method exists only for bytecode analysis at build time.");
    }

    /**
     * Returns the minimum value of a comparable field. Generates: {@code SELECT MIN(field) FROM Entity}
     *
     * @param selector field selector for the field to find minimum of
     * @param <C> the comparable field type
     * @return the minimum value (bytecode marker only)
     */
    public <C extends Comparable<? super C>> C min(QuerySpec<T, C> selector) {
        throw new UnsupportedOperationException(
                "SubqueryBuilder.min() should never be called at runtime. " +
                        "This method exists only for bytecode analysis at build time.");
    }

    /**
     * Returns the maximum value of a comparable field. Generates: {@code SELECT MAX(field) FROM Entity}
     *
     * @param selector field selector for the field to find maximum of
     * @param <C> the comparable field type
     * @return the maximum value (bytecode marker only)
     */
    public <C extends Comparable<? super C>> C max(QuerySpec<T, C> selector) {
        throw new UnsupportedOperationException(
                "SubqueryBuilder.max() should never be called at runtime. " +
                        "This method exists only for bytecode analysis at build time.");
    }

    /**
     * Returns the count of entities matching the predicate. Generates: {@code SELECT COUNT(*) FROM Entity WHERE predicate}
     *
     * @param predicate filter predicate for the subquery
     * @return the count (bytecode marker only)
     */
    public Long count(QuerySpec<T, Boolean> predicate) {
        throw new UnsupportedOperationException(
                "SubqueryBuilder.count() should never be called at runtime. " +
                        "This method exists only for bytecode analysis at build time.");
    }

    /**
     * Returns the count of all entities. Generates: {@code SELECT COUNT(*) FROM Entity}
     *
     * @return the count (bytecode marker only)
     */
    public Long count() {
        throw new UnsupportedOperationException(
                "SubqueryBuilder.count() should never be called at runtime. " +
                        "This method exists only for bytecode analysis at build time.");
    }

    /**
     * Returns true if any entity matches the predicate. Generates: {@code EXISTS (SELECT 1 FROM Entity WHERE predicate)}
     *
     * @param predicate the existence predicate
     * @return true if exists (bytecode marker only)
     */
    public boolean exists(QuerySpec<T, Boolean> predicate) {
        throw new UnsupportedOperationException(
                "SubqueryBuilder.exists() should never be called at runtime. " +
                        "This method exists only for bytecode analysis at build time.");
    }

    /**
     * Returns true if no entity matches the predicate. Generates: {@code NOT EXISTS (SELECT 1 FROM Entity WHERE predicate)}
     *
     * @param predicate the existence predicate
     * @return true if not exists (bytecode marker only)
     */
    public boolean notExists(QuerySpec<T, Boolean> predicate) {
        throw new UnsupportedOperationException(
                "SubqueryBuilder.notExists() should never be called at runtime. " +
                        "This method exists only for bytecode analysis at build time.");
    }

    /**
     * Returns true if the field value is in the subquery results.
     * Generates: {@code field IN (SELECT selectExpr FROM Entity WHERE predicate)}
     *
     * @param field the field to check
     * @param selector what to select from each entity
     * @param predicate filter predicate for the subquery
     * @param <R> the field/selection type
     * @return true if in subquery result (bytecode marker only)
     */
    public <R> boolean in(R field, QuerySpec<T, R> selector, QuerySpec<T, Boolean> predicate) {
        throw new UnsupportedOperationException(
                "SubqueryBuilder.in() should never be called at runtime. " +
                        "This method exists only for bytecode analysis at build time.");
    }

    /**
     * Returns true if the field value is in the subquery results. Generates: {@code field IN (SELECT selectExpr FROM Entity)}
     *
     * @param field the field to check
     * @param selector what to select from each entity
     * @param <R> the field/selection type
     * @return true if in subquery result (bytecode marker only)
     */
    public <R> boolean in(R field, QuerySpec<T, R> selector) {
        throw new UnsupportedOperationException(
                "SubqueryBuilder.in() should never be called at runtime. " +
                        "This method exists only for bytecode analysis at build time.");
    }

    /**
     * Returns true if the field value is NOT in the subquery results.
     * Generates: {@code field NOT IN (SELECT selectExpr FROM Entity WHERE predicate)}
     *
     * @param field the field to check
     * @param selector what to select from each entity
     * @param predicate filter predicate for the subquery
     * @param <R> the field/selection type
     * @return true if not in subquery result (bytecode marker only)
     */
    public <R> boolean notIn(R field, QuerySpec<T, R> selector, QuerySpec<T, Boolean> predicate) {
        throw new UnsupportedOperationException(
                "SubqueryBuilder.notIn() should never be called at runtime. " +
                        "This method exists only for bytecode analysis at build time.");
    }

    /**
     * Returns true if the field value is NOT in the subquery results.
     * Generates: {@code field NOT IN (SELECT selectExpr FROM Entity)}
     *
     * @param field the field to check
     * @param selector what to select from each entity
     * @param <R> the field/selection type
     * @return true if not in subquery result (bytecode marker only)
     */
    public <R> boolean notIn(R field, QuerySpec<T, R> selector) {
        throw new UnsupportedOperationException(
                "SubqueryBuilder.notIn() should never be called at runtime. " +
                        "This method exists only for bytecode analysis at build time.");
    }

    /**
     * Returns the entity class for this subquery builder.
     * Used by bytecode analyzers at build time.
     *
     * @return the entity class
     */
    Class<T> getEntityClass() {
        return entityClass;
    }
}
