package io.quarkiverse.qubit;

import java.util.Optional;

import org.jspecify.annotations.Nullable;

/**
 * Result of a scalar aggregation query (min, max, avg, sum).
 * <p>
 * Unlike {@link QubitStream}, this interface only exposes terminal operations
 * that make sense for scalar results — preventing misuse of intermediate
 * operations like {@code .where()}, {@code .select()}, or {@code .sortedBy()}
 * on aggregation results.
 *
 * @param <T> the result type
 */
public interface ScalarResult<T> {

    /** Returns the single aggregation result, or null if no rows matched. */
    @Nullable
    T getSingleResult();

    /** Returns the aggregation result wrapped in Optional. */
    Optional<T> findFirst();
}
