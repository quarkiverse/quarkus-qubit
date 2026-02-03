package io.quarkiverse.qubit.deployment.generation.expression;

import io.quarkus.gizmo2.Expr;
import io.quarkus.gizmo2.creator.BlockCreator;

/**
 * Context for bi-entity subquery generation methods.
 * Extends BiEntityContext with additional query handle for subquery creation.
 * Bundles common parameters to reduce method signatures from 8 params to 2.
 *
 * <p>Uses Gizmo 2 API with BlockCreator and Expr types.
 */
public record BiEntitySubqueryContext(
        BlockCreator bc,
        Expr cb,
        Expr query,
        Expr root,
        Expr join,
        Expr capturedValues,
        ExpressionGeneratorHelper helper) implements BiEntityContext {
}
