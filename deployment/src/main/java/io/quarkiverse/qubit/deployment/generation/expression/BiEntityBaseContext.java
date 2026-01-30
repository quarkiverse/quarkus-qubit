package io.quarkiverse.qubit.deployment.generation.expression;

import io.quarkus.gizmo.MethodCreator;
import io.quarkus.gizmo.ResultHandle;

/**
 * Context for bi-entity expression generation methods that don't require subquery support.
 * Bundles common parameters to reduce method signatures from 7 params to 2.
 */
public record BiEntityBaseContext(
        MethodCreator method,
        ResultHandle cb,
        ResultHandle root,
        ResultHandle join,
        ResultHandle capturedValues,
        ExpressionGeneratorHelper helper) implements BiEntityContext {
}
