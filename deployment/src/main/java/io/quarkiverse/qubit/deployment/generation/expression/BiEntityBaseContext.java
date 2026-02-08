package io.quarkiverse.qubit.deployment.generation.expression;

import io.quarkus.gizmo2.Expr;
import io.quarkus.gizmo2.creator.BlockCreator;

/**
 * Context for bi-entity expression generation methods that don't require subquery support.
 * Bundles common parameters to reduce method signatures from 7 params to 2.
 */
public record BiEntityBaseContext(
        BlockCreator bc,
        Expr cb,
        Expr root,
        Expr join,
        Expr capturedValues,
        ExpressionGeneratorHelper helper) implements BiEntityContext {
}
