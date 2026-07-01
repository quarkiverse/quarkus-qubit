package io.quarkiverse.qubit.deployment.generation.methodcall;

import java.util.Objects;

import io.quarkus.gizmo2.Expr;

/**
 * Sealed result type for method call expression generation.
 * Success = bytecode generated; Unsupported = no handler could process.
 * Replaces null returns with explicit semantics for exhaustive switch expressions.
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

    /** Creates a Success result. */
    static GenerationResult success(Expr value) {
        return new Success(value);
    }
}
