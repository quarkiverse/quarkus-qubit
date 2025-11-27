package io.quarkus.qusaq.runtime;

/**
 * Utility class providing static methods for subquery expressions in Qusaq queries.
 * <p>
 * These methods are <strong>never actually invoked at runtime</strong>. They exist solely
 * to provide a type-safe API that generates recognizable bytecode patterns. The Qusaq
 * build-time processor analyzes lambda bytecode and generates JPA Criteria subquery code.
 * <p>
 * <h2>Scalar Aggregation Subqueries</h2>
 * <pre>{@code
 * // Find persons earning above average salary
 * Person.where(p -> p.salary > Subqueries.avg(Person.class, q -> q.salary)).toList();
 *
 * // Find persons earning the maximum salary
 * Person.where(p -> p.salary.equals(Subqueries.max(Person.class, q -> q.salary))).toList();
 *
 * // Find departments with more than 10 employees
 * Department.where(d -> Subqueries.count(Person.class, p -> p.department.id.equals(d.id)) > 10).toList();
 * }</pre>
 *
 * <h2>EXISTS Subqueries</h2>
 * <pre>{@code
 * // Find persons who have at least one phone
 * Person.where(p -> Subqueries.exists(Phone.class, ph -> ph.owner.id.equals(p.id))).toList();
 *
 * // Find persons who have no phones
 * Person.where(p -> Subqueries.notExists(Phone.class, ph -> ph.owner.id.equals(p.id))).toList();
 * }</pre>
 *
 * <h2>IN Subqueries</h2>
 * <pre>{@code
 * // Find persons in high-budget departments
 * Person.where(p -> Subqueries.in(
 *     p.department.id,
 *     Department.class,
 *     d -> d.id,
 *     d -> d.budget > 1000000
 * )).toList();
 * }</pre>
 *
 * @see QuerySpec
 */
public final class Subqueries {

    private Subqueries() {
        // Utility class - no instantiation
    }

    // =============================================================================================
    // SCALAR AGGREGATION SUBQUERIES
    // =============================================================================================

    /**
     * Returns the average of a numeric field across all entities.
     * <p>
     * Generates: {@code SELECT AVG(field) FROM Entity}
     * <p>
     * Example:
     * <pre>{@code
     * Person.where(p -> p.salary > Subqueries.avg(Person.class, q -> q.salary)).toList();
     * }</pre>
     *
     * @param entityClass the entity class for the subquery
     * @param selector field selector returning the numeric field to average
     * @param <T> the entity type
     * @param <N> the numeric field type
     * @return the average value (never actually called - bytecode marker only)
     */
    public static <T, N extends Number> Double avg(Class<T> entityClass, QuerySpec<T, N> selector) {
        throw new UnsupportedOperationException(
                "Subqueries.avg() should never be called at runtime. " +
                "This method exists only for bytecode analysis at build time.");
    }

    /**
     * Returns the average of a numeric field across entities matching a predicate.
     * <p>
     * Generates: {@code SELECT AVG(field) FROM Entity WHERE predicate}
     * <p>
     * Example:
     * <pre>{@code
     * Person.where(p -> p.salary > Subqueries.avg(Person.class, q -> q.salary, q -> q.department.equals("IT"))).toList();
     * }</pre>
     *
     * @param entityClass the entity class for the subquery
     * @param selector field selector returning the numeric field to average
     * @param predicate filter predicate for the subquery
     * @param <T> the entity type
     * @param <N> the numeric field type
     * @return the average value (never actually called - bytecode marker only)
     */
    public static <T, N extends Number> Double avg(Class<T> entityClass, QuerySpec<T, N> selector, QuerySpec<T, Boolean> predicate) {
        throw new UnsupportedOperationException(
                "Subqueries.avg() should never be called at runtime. " +
                "This method exists only for bytecode analysis at build time.");
    }

    /**
     * Returns the sum of a numeric field across all entities.
     * <p>
     * Generates: {@code SELECT SUM(field) FROM Entity}
     *
     * @param entityClass the entity class for the subquery
     * @param selector field selector returning the numeric field to sum
     * @param <T> the entity type
     * @param <N> the numeric field type
     * @return the sum value (never actually called - bytecode marker only)
     */
    public static <T, N extends Number> N sum(Class<T> entityClass, QuerySpec<T, N> selector) {
        throw new UnsupportedOperationException(
                "Subqueries.sum() should never be called at runtime. " +
                "This method exists only for bytecode analysis at build time.");
    }

    /**
     * Returns the sum of a numeric field across entities matching a predicate.
     * <p>
     * Generates: {@code SELECT SUM(field) FROM Entity WHERE predicate}
     *
     * @param entityClass the entity class for the subquery
     * @param selector field selector returning the numeric field to sum
     * @param predicate filter predicate for the subquery
     * @param <T> the entity type
     * @param <N> the numeric field type
     * @return the sum value (never actually called - bytecode marker only)
     */
    public static <T, N extends Number> N sum(Class<T> entityClass, QuerySpec<T, N> selector, QuerySpec<T, Boolean> predicate) {
        throw new UnsupportedOperationException(
                "Subqueries.sum() should never be called at runtime. " +
                "This method exists only for bytecode analysis at build time.");
    }

    /**
     * Returns the minimum value of a comparable field across all entities.
     * <p>
     * Generates: {@code SELECT MIN(field) FROM Entity}
     *
     * @param entityClass the entity class for the subquery
     * @param selector field selector returning the field to find minimum of
     * @param <T> the entity type
     * @param <C> the comparable field type
     * @return the minimum value (never actually called - bytecode marker only)
     */
    public static <T, C extends Comparable<? super C>> C min(Class<T> entityClass, QuerySpec<T, C> selector) {
        throw new UnsupportedOperationException(
                "Subqueries.min() should never be called at runtime. " +
                "This method exists only for bytecode analysis at build time.");
    }

    /**
     * Returns the minimum value of a comparable field across entities matching a predicate.
     * <p>
     * Generates: {@code SELECT MIN(field) FROM Entity WHERE predicate}
     *
     * @param entityClass the entity class for the subquery
     * @param selector field selector returning the field to find minimum of
     * @param predicate filter predicate for the subquery
     * @param <T> the entity type
     * @param <C> the comparable field type
     * @return the minimum value (never actually called - bytecode marker only)
     */
    public static <T, C extends Comparable<? super C>> C min(Class<T> entityClass, QuerySpec<T, C> selector, QuerySpec<T, Boolean> predicate) {
        throw new UnsupportedOperationException(
                "Subqueries.min() should never be called at runtime. " +
                "This method exists only for bytecode analysis at build time.");
    }

    /**
     * Returns the maximum value of a comparable field across all entities.
     * <p>
     * Generates: {@code SELECT MAX(field) FROM Entity}
     *
     * @param entityClass the entity class for the subquery
     * @param selector field selector returning the field to find maximum of
     * @param <T> the entity type
     * @param <C> the comparable field type
     * @return the maximum value (never actually called - bytecode marker only)
     */
    public static <T, C extends Comparable<? super C>> C max(Class<T> entityClass, QuerySpec<T, C> selector) {
        throw new UnsupportedOperationException(
                "Subqueries.max() should never be called at runtime. " +
                "This method exists only for bytecode analysis at build time.");
    }

    /**
     * Returns the maximum value of a comparable field across entities matching a predicate.
     * <p>
     * Generates: {@code SELECT MAX(field) FROM Entity WHERE predicate}
     *
     * @param entityClass the entity class for the subquery
     * @param selector field selector returning the field to find maximum of
     * @param predicate filter predicate for the subquery
     * @param <T> the entity type
     * @param <C> the comparable field type
     * @return the maximum value (never actually called - bytecode marker only)
     */
    public static <T, C extends Comparable<? super C>> C max(Class<T> entityClass, QuerySpec<T, C> selector, QuerySpec<T, Boolean> predicate) {
        throw new UnsupportedOperationException(
                "Subqueries.max() should never be called at runtime. " +
                "This method exists only for bytecode analysis at build time.");
    }

    /**
     * Returns the count of all entities.
     * <p>
     * Generates: {@code SELECT COUNT(*) FROM Entity}
     *
     * @param entityClass the entity class for the subquery
     * @param <T> the entity type
     * @return the count (never actually called - bytecode marker only)
     */
    public static <T> Long count(Class<T> entityClass) {
        throw new UnsupportedOperationException(
                "Subqueries.count() should never be called at runtime. " +
                "This method exists only for bytecode analysis at build time.");
    }

    /**
     * Returns the count of entities matching a predicate.
     * <p>
     * Generates: {@code SELECT COUNT(*) FROM Entity WHERE predicate}
     * <p>
     * Example (correlated subquery):
     * <pre>{@code
     * // Find departments with more than 10 employees
     * Department.where(d -> Subqueries.count(Person.class, p -> p.department.id.equals(d.id)) > 10).toList();
     * }</pre>
     *
     * @param entityClass the entity class for the subquery
     * @param predicate filter predicate for the subquery
     * @param <T> the entity type
     * @return the count (never actually called - bytecode marker only)
     */
    public static <T> Long count(Class<T> entityClass, QuerySpec<T, Boolean> predicate) {
        throw new UnsupportedOperationException(
                "Subqueries.count() should never be called at runtime. " +
                "This method exists only for bytecode analysis at build time.");
    }

    // =============================================================================================
    // EXISTS SUBQUERIES
    // =============================================================================================

    /**
     * Returns true if any entity matches the predicate.
     * <p>
     * Generates: {@code EXISTS (SELECT 1 FROM Entity WHERE predicate)}
     * <p>
     * Example (correlated subquery):
     * <pre>{@code
     * // Find persons who have at least one phone
     * Person.where(p -> Subqueries.exists(Phone.class, ph -> ph.owner.id.equals(p.id))).toList();
     * }</pre>
     *
     * @param entityClass the entity class for the subquery
     * @param predicate the existence predicate
     * @param <T> the entity type
     * @return true if exists (never actually called - bytecode marker only)
     */
    public static <T> boolean exists(Class<T> entityClass, QuerySpec<T, Boolean> predicate) {
        throw new UnsupportedOperationException(
                "Subqueries.exists() should never be called at runtime. " +
                "This method exists only for bytecode analysis at build time.");
    }

    /**
     * Returns true if no entity matches the predicate.
     * <p>
     * Generates: {@code NOT EXISTS (SELECT 1 FROM Entity WHERE predicate)}
     * <p>
     * Example (correlated subquery):
     * <pre>{@code
     * // Find persons who have no phones
     * Person.where(p -> Subqueries.notExists(Phone.class, ph -> ph.owner.id.equals(p.id))).toList();
     * }</pre>
     *
     * @param entityClass the entity class for the subquery
     * @param predicate the existence predicate
     * @param <T> the entity type
     * @return true if not exists (never actually called - bytecode marker only)
     */
    public static <T> boolean notExists(Class<T> entityClass, QuerySpec<T, Boolean> predicate) {
        throw new UnsupportedOperationException(
                "Subqueries.notExists() should never be called at runtime. " +
                "This method exists only for bytecode analysis at build time.");
    }

    // =============================================================================================
    // IN SUBQUERIES
    // =============================================================================================

    /**
     * Returns true if the field value is in the set of values from the subquery.
     * <p>
     * Generates: {@code field IN (SELECT selectExpr FROM Entity)}
     * <p>
     * Example:
     * <pre>{@code
     * // Find persons in high-budget departments
     * Person.where(p -> Subqueries.in(
     *     p.department.id,
     *     Department.class,
     *     d -> d.id,
     *     d -> d.budget > 1000000
     * )).toList();
     * }</pre>
     *
     * @param field the field to check
     * @param entityClass the entity class for the subquery
     * @param selector what to select from each entity
     * @param <T> the entity type
     * @param <R> the field/selection type
     * @return true if in subquery result (never actually called - bytecode marker only)
     */
    public static <T, R> boolean in(R field, Class<T> entityClass, QuerySpec<T, R> selector) {
        throw new UnsupportedOperationException(
                "Subqueries.in() should never be called at runtime. " +
                "This method exists only for bytecode analysis at build time.");
    }

    /**
     * Returns true if the field value is in the filtered set of values from the subquery.
     * <p>
     * Generates: {@code field IN (SELECT selectExpr FROM Entity WHERE predicate)}
     *
     * @param field the field to check
     * @param entityClass the entity class for the subquery
     * @param selector what to select from each entity
     * @param predicate filter predicate for the subquery
     * @param <T> the entity type
     * @param <R> the field/selection type
     * @return true if in subquery result (never actually called - bytecode marker only)
     */
    public static <T, R> boolean in(R field, Class<T> entityClass, QuerySpec<T, R> selector, QuerySpec<T, Boolean> predicate) {
        throw new UnsupportedOperationException(
                "Subqueries.in() should never be called at runtime. " +
                "This method exists only for bytecode analysis at build time.");
    }

    /**
     * Returns true if the field value is NOT in the set of values from the subquery.
     * <p>
     * Generates: {@code field NOT IN (SELECT selectExpr FROM Entity)}
     *
     * @param field the field to check
     * @param entityClass the entity class for the subquery
     * @param selector what to select from each entity
     * @param <T> the entity type
     * @param <R> the field/selection type
     * @return true if not in subquery result (never actually called - bytecode marker only)
     */
    public static <T, R> boolean notIn(R field, Class<T> entityClass, QuerySpec<T, R> selector) {
        throw new UnsupportedOperationException(
                "Subqueries.notIn() should never be called at runtime. " +
                "This method exists only for bytecode analysis at build time.");
    }

    /**
     * Returns true if the field value is NOT in the filtered set of values from the subquery.
     * <p>
     * Generates: {@code field NOT IN (SELECT selectExpr FROM Entity WHERE predicate)}
     *
     * @param field the field to check
     * @param entityClass the entity class for the subquery
     * @param selector what to select from each entity
     * @param predicate filter predicate for the subquery
     * @param <T> the entity type
     * @param <R> the field/selection type
     * @return true if not in subquery result (never actually called - bytecode marker only)
     */
    public static <T, R> boolean notIn(R field, Class<T> entityClass, QuerySpec<T, R> selector, QuerySpec<T, Boolean> predicate) {
        throw new UnsupportedOperationException(
                "Subqueries.notIn() should never be called at runtime. " +
                "This method exists only for bytecode analysis at build time.");
    }
}
