package io.quarkiverse.qubit.runtime;

import java.io.Serializable;
import java.util.function.Function;

/** Group lambda for having/select operations. Serializable for unique call site ID generation. */
@FunctionalInterface
public interface GroupQuerySpec<T, K, U> extends Function<Group<T, K>, U>, Serializable {
    /** Never called at runtime - exists only for lambda bytecode generation. */
    @Override
    U apply(Group<T, K> group);
}
