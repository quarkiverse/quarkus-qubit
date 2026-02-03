package io.quarkiverse.qubit.deployment.generation.methodcall;

import static io.quarkiverse.qubit.deployment.common.ExceptionMessages.cannotGetValueFromUnsupported;

import io.quarkus.gizmo2.Expr;

import java.util.Objects;
import java.util.function.UnaryOperator;

/**
 * Sealed result type for method call expression generation.
 * Success = bytecode generated; Unsupported = no handler could process.
 * Replaces null returns with explicit semantics for exhaustive switch expressions.
 *
 * <p>Uses Gizmo 2 API with Expr type.
 */
public sealed interface GenerationResult permits
        GenerationResult.Success,
        GenerationResult.Unsupported {

    /** Successful generation with a valid Expr. */
    record Success(Expr value) implements GenerationResult {
        public Success {
            Objects.requireNonNull(value, "Expr value cannot be null in Success");
        }
    }

    /** No handler could process this method call pattern (not an error). */
    record Unsupported(String methodName, String reason) implements GenerationResult {
        public Unsupported {
            Objects.requireNonNull(methodName, "methodName cannot be null");
            Objects.requireNonNull(reason, "reason cannot be null");
        }

        /** Creates Unsupported result for no matching handler. */
        public static Unsupported noHandlerFound(String methodName) {
            return new Unsupported(methodName,
                    "No handler found for method: " + methodName);
        }
    }

    /** Returns true if this result is a Success. */
    default boolean isSuccess() {
        return this instanceof Success;
    }

    /** Returns the Expr if Success, or throws if Unsupported. */
    default Expr getOrThrow() {
        return switch (this) {
            case Success(var value) -> value;
            case Unsupported(_, var reason) ->
                    throw new IllegalStateException(cannotGetValueFromUnsupported(reason));
        };
    }

    /** Maps the Success value, passing through Unsupported unchanged. */
    default GenerationResult map(UnaryOperator<Expr> mapper) {
        return switch (this) {
            case Success(var value) -> new Success(mapper.apply(value));
            case Unsupported u -> u;
        };
    }

    /** Returns the Expr if Success, or the fallback if Unsupported. */
    default Expr orElse(Expr fallback) {
        return switch (this) {
            case Success(var value) -> value;
            case Unsupported _ -> fallback;
        };
    }

    /** Creates a Success result. */
    static GenerationResult success(Expr value) {
        return new Success(value);
    }

    /** Creates an Unsupported result. */
    static GenerationResult unsupported(String methodName, String reason) {
        return new Unsupported(methodName, reason);
    }
}
