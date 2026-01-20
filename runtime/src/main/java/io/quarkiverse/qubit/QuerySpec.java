package io.quarkiverse.qubit;

import java.io.Serializable;
import java.util.function.Function;

/**
 * Functional interface for lambda-based queries transformed at build time into JPA Criteria Queries.
 * <p>
 * Extends {@link Serializable} to enable extraction of the lambda implementation method name
 * via {@link java.lang.invoke.SerializedLambda}. This is required to generate unique call site IDs
 * when multiple queries appear on the same source line.
 */
@FunctionalInterface
public interface QuerySpec<T, R> extends Function<T, R>, Serializable {
    /**
     * Never called at runtime - exists only for lambda bytecode generation.
     *
     * @param entity build-time marker, never read at runtime
     * @return build-time marker only
     */
    @Override
    R apply(T entity);
}
