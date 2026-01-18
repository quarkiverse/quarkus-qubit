package io.quarkiverse.qubit.runtime;

import java.io.Serializable;
import java.util.function.BiFunction;

/** Two-entity lambda for join operations. Serializable for unique call site ID generation. */
@FunctionalInterface
public interface BiQuerySpec<T, R, U> extends BiFunction<T, R, U>, Serializable {
    /** Never called at runtime - exists only for lambda bytecode generation. */
    @Override
    U apply(T first, R second);
}
