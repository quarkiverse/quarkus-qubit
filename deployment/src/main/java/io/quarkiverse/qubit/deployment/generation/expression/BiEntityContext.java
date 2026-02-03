package io.quarkiverse.qubit.deployment.generation.expression;

import io.quarkus.gizmo2.Expr;
import io.quarkus.gizmo2.creator.BlockCreator;

/**
 * Sealed interface for bi-entity (join) query context.
 * Provides common accessors for expression generation parameters.
 *
 * <p>Two implementations exist:
 * <ul>
 *   <li>{@link BiEntityBaseContext} - for non-subquery methods (no query handle)</li>
 *   <li>{@link BiEntitySubqueryContext} - for subquery methods (includes query handle)</li>
 * </ul>
 *
 * <p>Uses Gizmo 2 API with BlockCreator and Expr types.
 */
public sealed interface BiEntityContext permits BiEntityBaseContext, BiEntitySubqueryContext {

    /** Gizmo 2 block creator for bytecode generation. */
    BlockCreator bc();

    /** CriteriaBuilder handle. */
    Expr cb();

    /** Root entity handle. */
    Expr root();

    /** Joined entity handle. */
    Expr join();

    /** Captured lambda variables array handle. */
    Expr capturedValues();

    /** Expression generator helper for delegating generation. */
    ExpressionGeneratorHelper helper();
}
