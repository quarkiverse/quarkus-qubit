package io.quarkiverse.qubit.deployment.generation.expression;

import io.quarkus.gizmo.MethodCreator;
import io.quarkus.gizmo.ResultHandle;

/**
 * Context for bi-entity subquery generation methods.
 * Extends BiEntityContext with additional query handle for subquery creation.
 * Bundles common parameters to reduce method signatures from 8 params to 2.
 */
public record BiEntitySubqueryContext(
        MethodCreator method,
        ResultHandle cb,
        ResultHandle query,
        ResultHandle root,
        ResultHandle join,
        ResultHandle capturedValues,
        ExpressionGeneratorHelper helper) implements BiEntityContext {
}
