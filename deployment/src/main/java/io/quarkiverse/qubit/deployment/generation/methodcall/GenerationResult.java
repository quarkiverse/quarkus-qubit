package io.quarkiverse.qubit.deployment.generation.methodcall;

import static io.quarkiverse.qubit.deployment.common.ExceptionMessages.cannotGetValueFromUnsupported;

import io.quarkus.gizmo.ResultHandle;

import java.util.Objects;
import java.util.function.UnaryOperator;

/**
 * Sealed result type for method call expression generation.
 * Success = bytecode generated; Unsupported = no handler could process.
 * Replaces null returns with explicit semantics for exhaustive switch expressions.
 */
public sealed interface GenerationResult permits
        GenerationResult.Success,
        GenerationResult.Unsupported {

    /** Successful generation with a valid ResultHandle. */
    record Success(ResultHandle value) implements GenerationResult {
        public Success {
            Objects.requireNonNull(value, "ResultHandle value cannot be null in Success");
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

    /** Returns the ResultHandle if Success, or throws if Unsupported. */
    default ResultHandle getOrThrow() {
        return switch (this) {
            case Success(var value) -> value;
            case Unsupported(_, var reason) ->
                    throw new IllegalStateException(cannotGetValueFromUnsupported(reason));
        };
    }

    /** Maps the Success value, passing through Unsupported unchanged. */
    default GenerationResult map(UnaryOperator<ResultHandle> mapper) {
        return switch (this) {
            case Success(var value) -> new Success(mapper.apply(value));
            case Unsupported u -> u;
        };
    }

    /** Returns the ResultHandle if Success, or the fallback if Unsupported. */
    default ResultHandle orElse(ResultHandle fallback) {
        return switch (this) {
            case Success(var value) -> value;
            case Unsupported _ -> fallback;
        };
    }

    /** Creates a Success result. */
    static GenerationResult success(ResultHandle value) {
        return new Success(value);
    }

    /** Creates an Unsupported result. */
    static GenerationResult unsupported(String methodName, String reason) {
        return new Unsupported(methodName, reason);
    }
}
