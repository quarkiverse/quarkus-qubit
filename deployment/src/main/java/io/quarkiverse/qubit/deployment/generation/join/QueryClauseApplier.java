package io.quarkiverse.qubit.deployment.generation.join;

import io.quarkus.gizmo2.Expr;
import io.quarkus.gizmo2.creator.BlockCreator;

import java.util.List;

/**
 * Strategy for applying query clauses (WHERE, ORDER BY, DISTINCT, pagination).
 * Decouples query builders from bytecode generation details.
 *
 * <p>Uses Gizmo 2 API with BlockCreator and Expr types.
 */
public interface QueryClauseApplier {

    void applyWherePredicate(BlockCreator bc, Expr query, Expr predicate);

    /** Uses "last call wins" semantics for sort ordering. */
    void applyBiEntityOrderBy(BlockCreator bc, Expr query, Expr root,
            Expr join, Expr cb, List<?> sortExpressions,
            Expr capturedValues);

    void applyDistinct(BlockCreator bc, Expr query, Expr distinct);

    void applyPagination(BlockCreator bc, Expr typedQuery,
            Expr offset, Expr limit);
}
