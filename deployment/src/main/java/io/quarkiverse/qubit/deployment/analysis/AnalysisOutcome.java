package io.quarkiverse.qubit.deployment.analysis;

import static io.quarkiverse.qubit.deployment.common.ExceptionMessages.CALL_SITE_ID_NULL;
import static io.quarkiverse.qubit.deployment.common.ExceptionMessages.LAMBDA_HASH_NULL;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Sealed result type for lambda analysis: Success, UnsupportedPattern, or AnalysisError.
 * Enables exhaustive switch handling and configurable build failure behavior.
 */
public sealed interface AnalysisOutcome {

    /** Returns the call site ID associated with this outcome. */
    String callSiteId();

    /** Returns true if this outcome represents a successful analysis. */
    default boolean isSuccess() {
        return this instanceof Success;
    }

    /** Returns true if processing can continue (Success, EarlyDeduplicated, or UnsupportedPattern). */
    default boolean canContinue() {
        return this instanceof Success || this instanceof EarlyDeduplicated || this instanceof UnsupportedPattern;
    }

    /** Executes the given action if this is a Success; returns this for chaining. */
    default AnalysisOutcome ifSuccess(Consumer<LambdaAnalysisResult> action) {
        if (this instanceof Success success) {
            action.accept(success.result());
        }
        return this;
    }

    /** Maps this outcome to a value using the provided functions for each case. */
    default <T> T fold(
            Function<Success, T> onSuccess,
            Function<UnsupportedPattern, T> onUnsupported,
            Function<AnalysisError, T> onError) {
        return switch (this) {
            case EarlyDeduplicated ed -> onSuccess.apply(
                    // Convert EarlyDeduplicated to Success for backward compatibility
                    // Use a minimal SimpleQueryResult since we don't have the full analysis
                    new Success(LambdaAnalysisResult.SimpleQueryResult.empty(), ed.callSiteId(), ed.lambdaHash()));
            case Success s -> onSuccess.apply(s);
            case UnsupportedPattern u -> onUnsupported.apply(u);
            case AnalysisError e -> onError.apply(e);
        };
    }

    /**
     * Early deduplication hit: analysis was skipped because an identical lambda
     * was already processed and a cached executor exists.
     */
    record EarlyDeduplicated(
            String callSiteId,
            String lambdaHash,
            String executorClassName) implements AnalysisOutcome {

        public EarlyDeduplicated {
            Objects.requireNonNull(callSiteId, CALL_SITE_ID_NULL);
            Objects.requireNonNull(lambdaHash, LAMBDA_HASH_NULL);
            Objects.requireNonNull(executorClassName, "Executor class name cannot be null");
        }

        @Override
        public boolean isSuccess() {
            return true; // Early deduplication is a successful outcome
        }
    }

    /** Successful analysis with a valid result. */
    record Success(
            LambdaAnalysisResult result,
            String callSiteId,
            String lambdaHash) implements AnalysisOutcome {

        public Success {
            Objects.requireNonNull(result, "Result cannot be null");
            Objects.requireNonNull(callSiteId, CALL_SITE_ID_NULL);
            Objects.requireNonNull(lambdaHash, LAMBDA_HASH_NULL);
        }
    }

    /**
     * Analysis skipped due to unsupported lambda pattern (falls back to runtime interpretation).
     * Not an error - indicates a pattern Qubit intentionally does not support.
     */
    record UnsupportedPattern(
            String reason,
            String callSiteId,
            PatternType patternType) implements AnalysisOutcome {

        /** Classification of unsupported patterns for analytics and debugging. */
        public enum PatternType {
            /** Lambda method not found in bytecode */
            LAMBDA_NOT_FOUND,
            /** Missing required lambda (e.g., groupBy key) */
            MISSING_REQUIRED_LAMBDA,
            /** Bi-entity descriptor requires 2+ parameters */
            INVALID_BI_ENTITY_DESCRIPTOR,
            /** Bytecode pattern not recognized */
            UNRECOGNIZED_BYTECODE,
            /** Multiple predicates failed to combine */
            PREDICATE_COMBINATION_FAILED,
            /** Other unsupported pattern */
            OTHER
        }

        public UnsupportedPattern {
            Objects.requireNonNull(reason, "Reason cannot be null");
            Objects.requireNonNull(callSiteId, CALL_SITE_ID_NULL);
            if (patternType == null) {
                patternType = PatternType.OTHER;
            }
        }

        /** Creates an UnsupportedPattern with default pattern type. */
        public static UnsupportedPattern of(String reason, String callSiteId) {
            return new UnsupportedPattern(reason, callSiteId, PatternType.OTHER);
        }

        /** Creates an UnsupportedPattern for lambda not found. */
        public static UnsupportedPattern lambdaNotFound(String lambdaMethodName, String callSiteId) {
            return new UnsupportedPattern(
                    "Lambda method not found: " + lambdaMethodName,
                    callSiteId,
                    PatternType.LAMBDA_NOT_FOUND);
        }

        /** Creates an UnsupportedPattern for missing required lambda. */
        public static UnsupportedPattern missingRequiredLambda(String lambdaType, String callSiteId) {
            return new UnsupportedPattern(
                    "Missing required " + lambdaType + " lambda",
                    callSiteId,
                    PatternType.MISSING_REQUIRED_LAMBDA);
        }
    }

    /**
     * Analysis failed due to an unexpected error (potential bug or unexpected bytecode).
     * May be configured to fail the build.
     */
    record AnalysisError(
            Exception cause,
            String callSiteId,
            String context) implements AnalysisOutcome {

        public AnalysisError {
            Objects.requireNonNull(cause, "Cause cannot be null");
            Objects.requireNonNull(callSiteId, CALL_SITE_ID_NULL);
        }

        /** Creates an AnalysisError without additional context. */
        public static AnalysisError of(Exception cause, String callSiteId) {
            return new AnalysisError(cause, callSiteId, null);
        }

        /** Creates an AnalysisError with context. */
        public static AnalysisError withContext(Exception cause, String callSiteId, String context) {
            return new AnalysisError(cause, callSiteId, context);
        }

        /** Returns a formatted error message including context if available. */
        public String formattedMessage() {
            if (context != null && !context.isBlank()) {
                return String.format("Analysis failed at %s (%s): %s",
                        callSiteId, context, cause.getMessage());
            }
            return String.format("Analysis failed at %s: %s",
                    callSiteId, cause.getMessage());
        }
    }

    /** Creates a successful outcome. */
    static Success success(LambdaAnalysisResult result, String callSiteId, String lambdaHash) {
        return new Success(result, callSiteId, lambdaHash);
    }

    /** Creates an unsupported pattern outcome. */
    static UnsupportedPattern unsupported(String reason, String callSiteId) {
        return UnsupportedPattern.of(reason, callSiteId);
    }

    /** Creates an error outcome. */
    static AnalysisError error(Exception cause, String callSiteId) {
        return AnalysisError.of(cause, callSiteId);
    }

    /** Creates an early deduplicated outcome. */
    static EarlyDeduplicated earlyDeduplicated(String callSiteId, String lambdaHash, String executorClassName) {
        return new EarlyDeduplicated(callSiteId, lambdaHash, executorClassName);
    }
}
