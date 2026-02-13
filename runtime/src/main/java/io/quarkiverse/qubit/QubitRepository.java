package io.quarkiverse.qubit;

import static io.quarkus.hibernate.orm.panache.common.runtime.AbstractJpaOperations.implementationInjectionMissing;

import java.util.Collection;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import io.quarkus.panache.common.impl.GenerateBridge;

/**
 * Repository interface with fluent API query methods extending PanacheRepositoryBase.
 * <p>
 * Provides fluent, type-safe query composition using JINQ-inspired syntax.
 * All query operations are analyzed at build time and translated to JPA Criteria Queries.
 * <p>
 * <strong>Example usage:</strong>
 *
 * <pre>{@code
 * @ApplicationScoped
 * public class PersonRepository implements QubitRepository<Person, Long> {
 * }
 *
 * // Usage:
 * List<Person> adults = personRepository.where(p -> p.age >= 18).toList();
 * long count = personRepository.where(p -> p.active).count();
 * }</pre>
 */
public interface QubitRepository<E extends PanacheEntity, I> extends PanacheRepositoryBase<E, I> {

    /**
     * Creates a query filtered by the given predicate.
     * <p>
     * Entry point for fluent query composition. Multiple where() calls can be chained
     * and will be combined with AND logic.
     * <p>
     * <strong>Generated at build time</strong> via bytecode enhancement.
     *
     * @param spec filtering predicate returning boolean
     * @return a new QubitStream with the filter applied
     */
    @GenerateBridge
    default QubitStream<E> where(QuerySpec<E, Boolean> spec) {
        throw implementationInjectionMissing();
    }

    /**
     * Creates a query with field projection or transformation.
     * <p>
     * Entry point for projection-based queries.
     * <p>
     * <strong>Generated at build time</strong> via bytecode enhancement.
     *
     * @param <R> the projection result type
     * @param mapper lambda transforming entity to projection
     * @return a new QubitStream with the projection applied
     */
    @GenerateBridge
    default <R> QubitStream<R> select(QuerySpec<E, R> mapper) {
        throw implementationInjectionMissing();
    }

    /**
     * Creates a query sorted in ascending order.
     * <p>
     * <strong>Generated at build time</strong> via bytecode enhancement.
     *
     * @param <K> the type of the sort key
     * @param keyExtractor lambda extracting sort key
     * @return a new QubitStream with the sort order applied
     */
    @GenerateBridge
    default <K extends Comparable<K>> QubitStream<E> sortedBy(QuerySpec<E, K> keyExtractor) {
        throw implementationInjectionMissing();
    }

    /**
     * Creates a query sorted in descending order.
     * <p>
     * <strong>Generated at build time</strong> via bytecode enhancement.
     *
     * @param <K> the type of the sort key
     * @param keyExtractor lambda extracting sort key
     * @return a new QubitStream with the descending sort order applied
     */
    @GenerateBridge
    default <K extends Comparable<K>> QubitStream<E> sortedDescendingBy(QuerySpec<E, K> keyExtractor) {
        throw implementationInjectionMissing();
    }

    /**
     * Prepares a minimum value aggregation query.
     * <p>
     * This is an intermediate operation that sets up the aggregation but does not execute it.
     * Call {@link QubitStream#getSingleResult()} to execute and get the result.
     * <p>
     * <strong>Generated at build time</strong> via bytecode enhancement.
     * <p>
     * Example:
     *
     * <pre>{@code
     * // Find minimum age
     * Integer minAge = personRepository.min(p -> p.age).getSingleResult();
     *
     * // Find minimum salary for active employees
     * Integer minSalary = personRepository.where(p -> p.active).min(p -> p.salary).getSingleResult();
     * }</pre>
     *
     * @param <K> the type of the field (must be comparable)
     * @param mapper lambda extracting the field to minimize
     * @return a new stream configured for MIN aggregation
     */
    @GenerateBridge
    default <K extends Comparable<K>> QubitStream<K> min(QuerySpec<E, K> mapper) {
        throw implementationInjectionMissing();
    }

    /**
     * Prepares a maximum value aggregation query.
     * <p>
     * This is an intermediate operation that sets up the aggregation but does not execute it.
     * Call {@link QubitStream#getSingleResult()} to execute and get the result.
     * <p>
     * <strong>Generated at build time</strong> via bytecode enhancement.
     * <p>
     * Example:
     *
     * <pre>{@code
     * // Find maximum age
     * Integer maxAge = personRepository.max(p -> p.age).getSingleResult();
     *
     * // Find maximum salary for active employees
     * Integer maxSalary = personRepository.where(p -> p.active).max(p -> p.salary).getSingleResult();
     * }</pre>
     *
     * @param <K> the type of the field (must be comparable)
     * @param mapper lambda extracting the field to maximize
     * @return a new stream configured for MAX aggregation
     */
    @GenerateBridge
    default <K extends Comparable<K>> QubitStream<K> max(QuerySpec<E, K> mapper) {
        throw implementationInjectionMissing();
    }

    /**
     * Prepares an average aggregation query for numeric values.
     * <p>
     * This is an intermediate operation that sets up the aggregation but does not execute it.
     * Call {@link QubitStream#getSingleResult()} to execute and get the result.
     * <p>
     * <strong>Generated at build time</strong> via bytecode enhancement.
     * <p>
     * Example:
     *
     * <pre>{@code
     * // Calculate average age
     * Double avgAge = personRepository.avg(p -> p.age).getSingleResult();
     *
     * // Calculate average salary for active employees
     * Double avgSalary = personRepository.where(p -> p.active).avg(p -> p.salary).getSingleResult();
     * }</pre>
     *
     * @param mapper lambda extracting the numeric field to average
     * @return a new stream configured for AVG aggregation (returns Double)
     */
    @GenerateBridge
    default QubitStream<Double> avg(QuerySpec<E, ? extends Number> mapper) {
        throw implementationInjectionMissing();
    }

    /**
     * Prepares a sum aggregation query for Integer values.
     * <p>
     * This is an intermediate operation that sets up the aggregation but does not execute it.
     * Call {@link QubitStream#getSingleResult()} to execute and get the result.
     * <p>
     * <strong>Generated at build time</strong> via bytecode enhancement.
     * <p>
     * Example:
     *
     * <pre>{@code
     * // Sum all ages
     * Long totalAge = personRepository.sumInteger(p -> p.age).getSingleResult();
     *
     * // Sum ages for active employees
     * Long totalActiveAge = personRepository.where(p -> p.active).sumInteger(p -> p.age).getSingleResult();
     * }</pre>
     *
     * @param mapper lambda extracting the Integer field to sum
     * @return a new stream configured for SUM aggregation (returns Long)
     */
    @GenerateBridge
    default QubitStream<Long> sumInteger(QuerySpec<E, Integer> mapper) {
        throw implementationInjectionMissing();
    }

    /**
     * Prepares a sum aggregation query for Long values.
     * <p>
     * This is an intermediate operation that sets up the aggregation but does not execute it.
     * Call {@link QubitStream#getSingleResult()} to execute and get the result.
     * <p>
     * <strong>Generated at build time</strong> via bytecode enhancement.
     * <p>
     * Example:
     *
     * <pre>{@code
     * // Sum all employee IDs
     * Long totalEmployeeId = personRepository.sumLong(p -> p.employeeId).getSingleResult();
     *
     * // Sum employee IDs for active employees
     * Long totalActiveEmployeeId = personRepository.where(p -> p.active).sumLong(p -> p.employeeId).getSingleResult();
     * }</pre>
     *
     * @param mapper lambda extracting the Long field to sum
     * @return a new stream configured for SUM aggregation (returns Long)
     */
    @GenerateBridge
    default QubitStream<Long> sumLong(QuerySpec<E, Long> mapper) {
        throw implementationInjectionMissing();
    }

    /**
     * Prepares a sum aggregation query for Double values.
     * <p>
     * This is an intermediate operation that sets up the aggregation but does not execute it.
     * Call {@link QubitStream#getSingleResult()} to execute and get the result.
     * <p>
     * <strong>Generated at build time</strong> via bytecode enhancement.
     * <p>
     * Example:
     *
     * <pre>{@code
     * // Sum all salaries
     * Double totalSalary = personRepository.sumDouble(p -> p.salary).getSingleResult();
     *
     * // Sum salaries for active employees
     * Double totalActiveSalary = personRepository.where(p -> p.active).sumDouble(p -> p.salary).getSingleResult();
     * }</pre>
     *
     * @param mapper lambda extracting the Double field to sum
     * @return a new stream configured for SUM aggregation (returns Double)
     */
    @GenerateBridge
    default QubitStream<Double> sumDouble(QuerySpec<E, Double> mapper) {
        throw implementationInjectionMissing();
    }

    /**
     * Creates an inner join query with a related entity collection.
     * <p>
     * The join follows the relationship defined by the lambda expression,
     * typically accessing a collection field. Inner join excludes source
     * entities that have no matching joined entities.
     * <p>
     * <strong>Generated at build time</strong> via bytecode enhancement.
     * <p>
     * Example:
     *
     * <pre>{@code
     * // Find persons with mobile phones (excludes persons without phones)
     * List<Person> peopleWithMobilePhones = personRepository
     *         .join((Person p) -> p.phones)
     *         .where((Person p, Phone ph) -> ph.type.equals("mobile"))
     *         .toList();
     *
     * // Project both entities to DTO
     * List<PersonPhoneDTO> dtos = personRepository
     *         .join((Person p) -> p.phones)
     *         .select((Person p, Phone ph) -> new PersonPhoneDTO(p.firstName, ph.number))
     *         .toList();
     * }</pre>
     *
     * @param <R> the joined entity type
     * @param relationship lambda accessing the collection field to join
     * @return a JoinStream for composing join predicates and projections
     */
    @GenerateBridge
    default <R> JoinStream<E, R> join(QuerySpec<E, Collection<R>> relationship) {
        throw implementationInjectionMissing();
    }

    /**
     * Creates a left outer join query with a related entity collection.
     * <p>
     * Unlike inner join, left join includes source entities even when
     * there are no matching joined entities. The joined entity will be
     * null in such cases.
     * <p>
     * <strong>Generated at build time</strong> via bytecode enhancement.
     * <p>
     * Example:
     *
     * <pre>{@code
     * // Find all persons, including those without phones
     * List<Person> allPeopleWithPhoneInfo = personRepository
     *         .leftJoin((Person p) -> p.phones)
     *         .toList();
     *
     * // Filter with null handling
     * List<Person> peopleWithOptionalPhone = personRepository
     *         .leftJoin((Person p) -> p.phones)
     *         .where((Person p, Phone ph) -> ph == null || ph.type.equals("mobile"))
     *         .toList();
     * }</pre>
     *
     * @param <R> the joined entity type
     * @param relationship lambda accessing the collection field to join
     * @return a JoinStream for composing join predicates and projections
     */
    @GenerateBridge
    default <R> JoinStream<E, R> leftJoin(QuerySpec<E, Collection<R>> relationship) {
        throw implementationInjectionMissing();
    }
}
