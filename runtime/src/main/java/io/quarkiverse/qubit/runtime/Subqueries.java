package io.quarkiverse.qubit.runtime;

/** Entry point for subquery expressions (bytecode markers, never called at runtime). */
public final class Subqueries {

    private Subqueries() {}

    /** Creates a subquery builder for the specified entity type. */
    public static <T> SubqueryBuilder<T> subquery(Class<T> entityClass) {
        throw new UnsupportedOperationException(
                "Subqueries.subquery() should never be called at runtime. " +
                "This method exists only for bytecode analysis at build time.");
    }
}
