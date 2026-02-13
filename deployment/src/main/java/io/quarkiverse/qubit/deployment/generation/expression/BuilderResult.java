package io.quarkiverse.qubit.deployment.generation.expression;

import java.util.Objects;
import java.util.Optional;

import io.quarkus.gizmo2.Expr;

/**
 * Sealed result type for expression builder operations.
 * Success = expression generated; NotApplicable = this builder doesn't handle this operation.
 * Replaces null returns with explicit semantics for exhaustive switch expressions.
 */
public sealed interface BuilderResult permits
        BuilderResult.Success,
        BuilderResult.NotApplicable {

    /** Singleton instance for not applicable results. */
    NotApplicable NOT_APPLICABLE = new NotApplicable();

    /** Successful expression generation with a valid Expr. */
    record Success(Expr value) implements BuilderResult {
        public Success {
            Objects.requireNonNull(value, "Expr value cannot be null in Success");
        }
    }

    /** Builder doesn't handle this operation - try next in chain. */
    record NotApplicable() implements BuilderResult {
    }

    /** Returns true if this result is a Success. */
    default boolean isSuccess() {
        return this instanceof Success;
    }

    /** Returns the Expr if Success, or throws if NotApplicable. */
    default Expr getOrThrow() {
        return switch (this) {
            case Success(var value) -> value;
            case NotApplicable _ ->
                throw new IllegalStateException("Cannot get value from NotApplicable result");
        };
    }

    /** Converts to Optional - present for Success, empty for NotApplicable. */
    default Optional<Expr> toOptional() {
        return switch (this) {
            case Success(var value) -> Optional.of(value);
            case NotApplicable _ -> Optional.empty();
        };
    }

    /** Creates a Success result. */
    static BuilderResult success(Expr value) {
        return new Success(value);
    }

    /** Returns the singleton NotApplicable instance. */
    static BuilderResult notApplicable() {
        return NOT_APPLICABLE;
    }

    /** Migration helper: Success if non-null, NotApplicable if null. */
    static BuilderResult fromNullable(Expr value) {
        return value != null ? new Success(value) : NOT_APPLICABLE;
    }
}
