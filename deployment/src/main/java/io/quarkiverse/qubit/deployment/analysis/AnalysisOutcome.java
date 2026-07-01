package io.quarkiverse.qubit.deployment.analysis;

import static io.quarkiverse.qubit.deployment.common.ExceptionMessages.CALL_SITE_ID_NULL;
import static io.quarkiverse.qubit.deployment.common.ExceptionMessages.LAMBDA_HASH_NULL;

import java.util.Objects;

/**
 * Sealed result type for lambda analysis: Success, UnsupportedPattern, or AnalysisError.
 * Enables exhaustive switch handling and configurable build failure behavior.
 */
public sealed interface AnalysisOutcome {

    /** Returns the call site ID associated with this outcome. */
    String callSiteId();

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
            /** Bytecode pattern not recognized */
            UNRECOGNIZED_BYTECODE,
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
