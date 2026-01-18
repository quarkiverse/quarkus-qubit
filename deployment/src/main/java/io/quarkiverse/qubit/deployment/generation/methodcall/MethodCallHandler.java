package io.quarkiverse.qubit.deployment.generation.methodcall;

import io.quarkus.gizmo.ResultHandle;

import java.util.Optional;

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
        StringSubstringHandler,
        StringUtilityHandler,
        GetterMethodHandler {

    /** Returns Optional with generated ResultHandle, or empty if this handler doesn't apply. */
    Optional<ResultHandle> handle(MethodCallDispatchContext context);

    /** Priority for chain ordering (lower = processed first). */
    HandlerPriority priority();
}
