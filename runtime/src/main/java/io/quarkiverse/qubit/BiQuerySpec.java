package io.quarkiverse.qubit;

import java.io.Serializable;
import java.util.function.BiFunction;

/** Two-entity lambda for join operations. Serializable for unique call site ID generation. */
@FunctionalInterface
public interface BiQuerySpec<T, R, U> extends BiFunction<T, R, U>, Serializable {
    /**
     * Never called at runtime - exists only for lambda bytecode generation.
     *
     * @param first build-time marker, never read at runtime
     * @param second build-time marker, never read at runtime
     * @return build-time marker only
     */
    @Override
    U apply(T first, R second);
}
