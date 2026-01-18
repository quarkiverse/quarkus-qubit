package io.quarkiverse.qubit.deployment.generation.expression;

import static io.quarkiverse.qubit.deployment.common.ExceptionMessages.CANNOT_GET_VALUE_FROM_NOT_APPLICABLE;

import io.quarkus.gizmo.ResultHandle;

import java.util.Objects;
import java.util.Optional;

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

    /** Successful expression generation with a valid ResultHandle. */
    record Success(ResultHandle value) implements BuilderResult {
        public Success {
            Objects.requireNonNull(value, "ResultHandle value cannot be null in Success");
        }
    }

    /** Builder doesn't handle this operation - try next in chain. */
    record NotApplicable() implements BuilderResult {}

    /** Returns true if this result is a Success. */
    default boolean isSuccess() {
        return this instanceof Success;
    }

    /** Returns the ResultHandle if Success, or throws if NotApplicable. */
    default ResultHandle getOrThrow() {
        return switch (this) {
            case Success(var value) -> value;
            case NotApplicable notApplicable ->
                    throw new IllegalStateException(CANNOT_GET_VALUE_FROM_NOT_APPLICABLE);
        };
    }

    /** Converts to Optional - present for Success, empty for NotApplicable. */
    default Optional<ResultHandle> toOptional() {
        return switch (this) {
            case Success(var value) -> Optional.of(value);
            case NotApplicable notApplicable -> Optional.empty();
        };
    }

    /** Creates a Success result. */
    static BuilderResult success(ResultHandle value) {
        return new Success(value);
    }

    /** Returns the singleton NotApplicable instance. */
    static BuilderResult notApplicable() {
        return NOT_APPLICABLE;
    }

    /** Migration helper: Success if non-null, NotApplicable if null. */
    static BuilderResult fromNullable(ResultHandle value) {
        return value != null ? new Success(value) : NOT_APPLICABLE;
    }
}
