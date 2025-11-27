package io.quarkus.qusaq.runtime;

/**
 * Entry point for creating subquery expressions in Qusaq queries.
 * <p>
 * This class provides a fluent API for building scalar subqueries using the builder pattern.
 * All methods are <strong>never actually invoked at runtime</strong> - they exist solely to
 * provide a type-safe API that generates recognizable bytecode patterns for the Qusaq
 * build-time processor.
 * <p>
 * <h2>Usage</h2>
 * <pre>{@code
 * import static io.quarkus.qusaq.runtime.Subqueries.*;
 *
 * // Find persons earning above average salary
 * Person.where(p -> p.salary > subquery(Person.class).avg(q -> q.salary)).toList();
 *
 * // Find persons earning the maximum salary
 * Person.where(p -> p.salary.equals(subquery(Person.class).max(q -> q.salary))).toList();
 *
 * // Find persons who have at least one phone (EXISTS)
 * Person.where(p -> subquery(Phone.class).exists(ph -> ph.owner.id.equals(p.id))).toList();
 *
 * // Find departments with more than 10 employees (COUNT)
 * Department.where(d -> subquery(Person.class).count(p -> p.department.id.equals(d.id)) > 10).toList();
 * }</pre>
 *
 * @see SubqueryBuilder
 * @see QuerySpec
 */
public final class Subqueries {

    private Subqueries() {
        // Utility class - no instantiation
    }

    /**
     * Creates a fluent subquery builder for the specified entity type.
     * <p>
     * This is the entry point for all subquery operations. After calling this method,
     * chain aggregation methods like {@code .avg()}, {@code .max()}, {@code .exists()}, etc.
     * <p>
     * <strong>Examples:</strong>
     * <pre>{@code
     * // Scalar aggregation
     * subquery(Person.class).avg(q -> q.salary)
     * subquery(Person.class).max(q -> q.age)
     * subquery(Person.class).min(q -> q.salary)
     * subquery(Person.class).sum(q -> q.salary)
     *
     * // Count
     * subquery(Person.class).count(p -> p.department.id.equals(d.id))
     * subquery(Person.class).count()
     *
     * // Existence
     * subquery(Phone.class).exists(ph -> ph.owner.id.equals(p.id))
     * subquery(Phone.class).notExists(ph -> ph.owner.id.equals(p.id))
     *
     * // IN/NOT IN
     * subquery(Department.class).in(p.department.id, d -> d.id, d -> d.budget > 1000000)
     * }</pre>
     *
     * @param entityClass the entity class for the subquery
     * @param <T> the entity type
     * @return a SubqueryBuilder for fluent chaining (never actually called - bytecode marker only)
     */
    public static <T> SubqueryBuilder<T> subquery(Class<T> entityClass) {
        throw new UnsupportedOperationException(
                "Subqueries.subquery() should never be called at runtime. " +
                "This method exists only for bytecode analysis at build time.");
    }
}
