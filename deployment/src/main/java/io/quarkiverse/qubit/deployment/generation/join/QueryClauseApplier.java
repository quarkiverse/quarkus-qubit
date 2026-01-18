package io.quarkiverse.qubit.deployment.generation.join;

import io.quarkus.gizmo.MethodCreator;
import io.quarkus.gizmo.ResultHandle;

import java.util.List;

/**
 * Strategy for applying query clauses (WHERE, ORDER BY, DISTINCT, pagination).
 * Decouples query builders from bytecode generation details.
 */
public interface QueryClauseApplier {

    void applyWherePredicate(MethodCreator method, ResultHandle query, ResultHandle predicate);

    /** Uses "last call wins" semantics for sort ordering. */
    void applyBiEntityOrderBy(MethodCreator method, ResultHandle query, ResultHandle root,
            ResultHandle join, ResultHandle cb, List<?> sortExpressions,
            ResultHandle capturedValues);

    void applyDistinct(MethodCreator method, ResultHandle query, ResultHandle distinct);

    void applyPagination(MethodCreator method, ResultHandle typedQuery,
            ResultHandle offset, ResultHandle limit);
}
