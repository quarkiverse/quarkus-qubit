package io.quarkiverse.qubit.runtime;

import java.util.function.BiFunction;

/**
 * Functional interface for two-entity lambda expressions.
 * <p>
 * Used in join operations where predicates or projections need access to both
 * entities being joined. The lambda bytecode is analyzed at build time and
 * converted to JPA Criteria Queries.
 * <p>
 * Example usage:
 * <pre>{@code
 * // Join predicate
 * Person.join(Person::getPhones)
 *       .where((Person p, Phone ph) -> ph.type.equals("mobile"))
 *       .toList();
 *
 * // Join projection
 * Person.join(Person::getPhones)
 *       .select((Person p, Phone ph) -> new PersonPhoneDTO(p.firstName, ph.number))
 *       .toList();
 * }</pre>
 *
 * @param <T> the first entity type (source/left side of join)
 * @param <R> the second entity type (target/right side of join)
 * @param <U> the result type of the function
 */
@FunctionalInterface
public interface BiQuerySpec<T, R, U> extends BiFunction<T, R, U> {
    /**
     * Never called at runtime - exists only for lambda bytecode generation.
     * The actual query execution is handled by build-time generated code.
     */
    @Override
    U apply(T first, R second);
}
