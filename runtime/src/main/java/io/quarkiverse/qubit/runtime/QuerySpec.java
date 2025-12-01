package io.quarkiverse.qubit.runtime;

import java.util.function.Function;

/**
 * Functional interface for lambda-based queries transformed at build time into JPA Criteria Queries.
 */
@FunctionalInterface
public interface QuerySpec<T, R> extends Function<T, R> {
    /**
     * Never called at runtime - exists only for lambda bytecode generation.
     */
    @Override
    R apply(T entity);
}
