package io.quarkiverse.qubit.runtime;

import java.util.function.Function;

/**
 * Functional interface for group lambda expressions.
 * <p>
 * Used in {@code GroupStream.having()} and {@code GroupStream.select()} operations
 * where the lambda has access to the {@link Group} context with aggregation functions.
 * <p>
 * Example usage:
 * <pre>{@code
 * // In having() clause
 * .having((Group<Person, String> g) -> g.count() > 5)
 *
 * // In select() clause
 * .select((Group<Person, String> g) -> new DeptStats(g.key(), g.count(), g.avg(p -> p.salary)))
 * }</pre>
 *
 * @param <T> the entity type being grouped
 * @param <K> the type of the grouping key
 * @param <U> the result type of the function
 */
@FunctionalInterface
public interface GroupQuerySpec<T, K, U> extends Function<Group<T, K>, U> {
    /**
     * Never called at runtime - exists only for lambda bytecode generation.
     * The actual query execution is handled by build-time generated code.
     */
    @Override
    U apply(Group<T, K> group);
}
