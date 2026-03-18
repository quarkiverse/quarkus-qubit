package io.quarkiverse.qubit.deployment.generation.join;

import java.util.List;

import org.jspecify.annotations.Nullable;

import io.quarkus.gizmo2.Expr;
import io.quarkus.gizmo2.creator.BlockCreator;

/**
 * Strategy for applying query clauses (WHERE, ORDER BY, DISTINCT, pagination).
 * Decouples query builders from bytecode generation details.
 */
public interface QueryClauseApplier {

    void applyWherePredicate(BlockCreator bc, Expr query, @Nullable Expr predicate);

    /** Uses "last call wins" semantics for sort ordering. */
    void applyBiEntityOrderBy(BlockCreator bc, Expr query, Expr root,
            Expr join, Expr cb, @Nullable List<?> sortExpressions,
            Expr capturedValues);

    void applyDistinct(BlockCreator bc, Expr query, Expr distinct);

    void applyPagination(BlockCreator bc, Expr typedQuery,
            Expr offset, Expr limit);
}
