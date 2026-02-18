package io.quarkiverse.qubit.deployment.generation.methodcall;

import java.util.Optional;

import io.quarkus.gizmo2.Expr;

/**
 * Chain of Responsibility handler for method call → JPA expression generation.
 * Each handler specializes in a category (temporal, string, arithmetic).
 * Priority ordering ensures specific handlers run before broad ones (e.g., getters).
 */
public sealed interface MethodCallHandler permits
        TemporalAccessorHandler,
        TemporalComparisonHandler,
        StringTransformationHandler,
        BigDecimalArithmeticHandler,
        StringLikePatternHandler,
        QubitLikeHandler,
        StringSubstringHandler,
        StringIndexOfHandler,
        StringUtilityHandler,
        GetterMethodHandler {

    /** Returns Optional with generated Expr, or empty if this handler doesn't apply. */
    Optional<Expr> handle(MethodCallDispatchContext context);

    /** Priority for chain ordering (lower = processed first). */
    HandlerPriority priority();
}
