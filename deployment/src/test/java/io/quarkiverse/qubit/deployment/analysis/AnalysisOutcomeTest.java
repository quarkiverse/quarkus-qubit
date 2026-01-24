package io.quarkiverse.qubit.deployment.analysis;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link AnalysisOutcome} sealed interface and its implementations.
 */
class AnalysisOutcomeTest {

    private static final String TEST_CALL_SITE_ID = "com.example.Service:method:42:lambda$test$0";
    private static final String TEST_LAMBDA_HASH = "abc123def456";

    // ========================================================================
    // Success Tests
    // ========================================================================

    @Nested
    class SuccessTests {

        @Test
        void success_createsSuccessOutcome() {
            LambdaAnalysisResult.SimpleQueryResult result = new LambdaAnalysisResult.SimpleQueryResult(null, null, null, 0);
            AnalysisOutcome.Success success = new AnalysisOutcome.Success(result, TEST_CALL_SITE_ID, TEST_LAMBDA_HASH);

            assertThat(success.result()).isEqualTo(result);
            assertThat(success.callSiteId()).isEqualTo(TEST_CALL_SITE_ID);
            assertThat(success.lambdaHash()).isEqualTo(TEST_LAMBDA_HASH);
        }

        @Test
        void success_isSuccessReturnsTrue() {
            LambdaAnalysisResult.SimpleQueryResult result = new LambdaAnalysisResult.SimpleQueryResult(null, null, null, 0);
            AnalysisOutcome success = new AnalysisOutcome.Success(result, TEST_CALL_SITE_ID, TEST_LAMBDA_HASH);

            assertThat(success.isSuccess()).isTrue();
        }

        @Test
        void success_canContinueReturnsTrue() {
            LambdaAnalysisResult.SimpleQueryResult result = new LambdaAnalysisResult.SimpleQueryResult(null, null, null, 0);
            AnalysisOutcome success = new AnalysisOutcome.Success(result, TEST_CALL_SITE_ID, TEST_LAMBDA_HASH);

            assertThat(success.canContinue()).isTrue();
        }

        @Test
        void success_withNullResult_throwsNullPointerException() {
            assertThatThrownBy(() -> new AnalysisOutcome.Success(null, TEST_CALL_SITE_ID, TEST_LAMBDA_HASH))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("Result cannot be null");
        }

        @Test
        void success_withNullCallSiteId_throwsNullPointerException() {
            LambdaAnalysisResult.SimpleQueryResult result = new LambdaAnalysisResult.SimpleQueryResult(null, null, null, 0);

            assertThatThrownBy(() -> new AnalysisOutcome.Success(result, null, TEST_LAMBDA_HASH))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("Call site ID cannot be null");
        }

        @Test
        void success_withNullLambdaHash_throwsNullPointerException() {
            LambdaAnalysisResult.SimpleQueryResult result = new LambdaAnalysisResult.SimpleQueryResult(null, null, null, 0);

            assertThatThrownBy(() -> new AnalysisOutcome.Success(result, TEST_CALL_SITE_ID, null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("Lambda hash cannot be null");
        }

        @Test
        void success_ifSuccessExecutesAction() {
            LambdaAnalysisResult.SimpleQueryResult result = new LambdaAnalysisResult.SimpleQueryResult(null, null, null, 0);
            AnalysisOutcome success = new AnalysisOutcome.Success(result, TEST_CALL_SITE_ID, TEST_LAMBDA_HASH);
            AtomicBoolean actionExecuted = new AtomicBoolean(false);
            AtomicReference<LambdaAnalysisResult> capturedResult = new AtomicReference<>();

            AnalysisOutcome returnedOutcome = success.ifSuccess(r -> {
                actionExecuted.set(true);
                capturedResult.set(r);
            });

            assertThat(actionExecuted.get()).isTrue();
            assertThat(capturedResult.get()).isEqualTo(result);
            assertThat(returnedOutcome).isSameAs(success);
        }

        @Test
        void success_factoryMethod_createsSuccessOutcome() {
            LambdaAnalysisResult.SimpleQueryResult result = new LambdaAnalysisResult.SimpleQueryResult(null, null, null, 0);

            AnalysisOutcome.Success success = AnalysisOutcome.success(result, TEST_CALL_SITE_ID, TEST_LAMBDA_HASH);

            assertThat(success.result()).isEqualTo(result);
            assertThat(success.callSiteId()).isEqualTo(TEST_CALL_SITE_ID);
        }
    }

    // ========================================================================
    // UnsupportedPattern Tests
    // ========================================================================

    @Nested
    class UnsupportedPatternTests {

        @Test
        void unsupportedPattern_createsUnsupportedOutcome() {
            AnalysisOutcome.UnsupportedPattern unsupported = new AnalysisOutcome.UnsupportedPattern(
                    "Lambda too complex", TEST_CALL_SITE_ID, AnalysisOutcome.UnsupportedPattern.PatternType.UNRECOGNIZED_BYTECODE);

            assertThat(unsupported.reason()).isEqualTo("Lambda too complex");
            assertThat(unsupported.callSiteId()).isEqualTo(TEST_CALL_SITE_ID);
            assertThat(unsupported.patternType()).isEqualTo(AnalysisOutcome.UnsupportedPattern.PatternType.UNRECOGNIZED_BYTECODE);
        }

        @Test
        void unsupportedPattern_isSuccessReturnsFalse() {
            AnalysisOutcome unsupported = new AnalysisOutcome.UnsupportedPattern(
                    "reason", TEST_CALL_SITE_ID, AnalysisOutcome.UnsupportedPattern.PatternType.OTHER);

            assertThat(unsupported.isSuccess()).isFalse();
        }

        @Test
        void unsupportedPattern_canContinueReturnsTrue() {
            AnalysisOutcome unsupported = new AnalysisOutcome.UnsupportedPattern(
                    "reason", TEST_CALL_SITE_ID, AnalysisOutcome.UnsupportedPattern.PatternType.OTHER);

            assertThat(unsupported.canContinue()).isTrue();
        }

        @Test
        void unsupportedPattern_withNullPatternType_defaultsToOther() {
            AnalysisOutcome.UnsupportedPattern unsupported = new AnalysisOutcome.UnsupportedPattern(
                    "reason", TEST_CALL_SITE_ID, null);

            assertThat(unsupported.patternType()).isEqualTo(AnalysisOutcome.UnsupportedPattern.PatternType.OTHER);
        }

        @Test
        void unsupportedPattern_withNullReason_throwsNullPointerException() {
            assertThatThrownBy(() -> new AnalysisOutcome.UnsupportedPattern(null, TEST_CALL_SITE_ID, null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("Reason cannot be null");
        }

        @Test
        void unsupportedPattern_withNullCallSiteId_throwsNullPointerException() {
            assertThatThrownBy(() -> new AnalysisOutcome.UnsupportedPattern("reason", null, null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("Call site ID cannot be null");
        }

        @Test
        void unsupportedPattern_ifSuccessDoesNotExecuteAction() {
            AnalysisOutcome unsupported = new AnalysisOutcome.UnsupportedPattern(
                    "reason", TEST_CALL_SITE_ID, AnalysisOutcome.UnsupportedPattern.PatternType.OTHER);
            AtomicBoolean actionExecuted = new AtomicBoolean(false);

            AnalysisOutcome returnedOutcome = unsupported.ifSuccess(r -> actionExecuted.set(true));

            assertThat(actionExecuted.get()).isFalse();
            assertThat(returnedOutcome).isSameAs(unsupported);
        }

        @Test
        void unsupportedPattern_of_createsWithDefaultPatternType() {
            AnalysisOutcome.UnsupportedPattern unsupported = AnalysisOutcome.UnsupportedPattern.of("reason", TEST_CALL_SITE_ID);

            assertThat(unsupported.patternType()).isEqualTo(AnalysisOutcome.UnsupportedPattern.PatternType.OTHER);
        }

        @Test
        void unsupportedPattern_lambdaNotFound_createsCorrectPatternType() {
            AnalysisOutcome.UnsupportedPattern unsupported = AnalysisOutcome.UnsupportedPattern.lambdaNotFound("lambda$test$0", TEST_CALL_SITE_ID);

            assertThat(unsupported.patternType()).isEqualTo(AnalysisOutcome.UnsupportedPattern.PatternType.LAMBDA_NOT_FOUND);
            assertThat(unsupported.reason()).contains("lambda$test$0");
        }

        @Test
        void unsupportedPattern_missingRequiredLambda_createsCorrectPatternType() {
            AnalysisOutcome.UnsupportedPattern unsupported = AnalysisOutcome.UnsupportedPattern.missingRequiredLambda("groupBy key", TEST_CALL_SITE_ID);

            assertThat(unsupported.patternType()).isEqualTo(AnalysisOutcome.UnsupportedPattern.PatternType.MISSING_REQUIRED_LAMBDA);
            assertThat(unsupported.reason()).contains("groupBy key");
        }

        @Test
        void unsupportedPattern_factoryMethod_createsUnsupportedOutcome() {
            AnalysisOutcome.UnsupportedPattern unsupported = AnalysisOutcome.unsupported("reason", TEST_CALL_SITE_ID);

            assertThat(unsupported.reason()).isEqualTo("reason");
            assertThat(unsupported.callSiteId()).isEqualTo(TEST_CALL_SITE_ID);
        }
    }

    // ========================================================================
    // AnalysisError Tests
    // ========================================================================

    @Nested
    class AnalysisErrorTests {

        @Test
        void analysisError_createsErrorOutcome() {
            RuntimeException cause = new RuntimeException("Analysis failed");
            AnalysisOutcome.AnalysisError error = new AnalysisOutcome.AnalysisError(cause, TEST_CALL_SITE_ID, "processing where clause");

            assertThat(error.cause()).isEqualTo(cause);
            assertThat(error.callSiteId()).isEqualTo(TEST_CALL_SITE_ID);
            assertThat(error.context()).isEqualTo("processing where clause");
        }

        @Test
        void analysisError_isSuccessReturnsFalse() {
            RuntimeException cause = new RuntimeException("error");
            AnalysisOutcome error = new AnalysisOutcome.AnalysisError(cause, TEST_CALL_SITE_ID, null);

            assertThat(error.isSuccess()).isFalse();
        }

        @Test
        void analysisError_canContinueReturnsFalse() {
            RuntimeException cause = new RuntimeException("error");
            AnalysisOutcome error = new AnalysisOutcome.AnalysisError(cause, TEST_CALL_SITE_ID, null);

            assertThat(error.canContinue()).isFalse();
        }

        @Test
        void analysisError_withNullCause_throwsNullPointerException() {
            assertThatThrownBy(() -> new AnalysisOutcome.AnalysisError(null, TEST_CALL_SITE_ID, null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("Cause cannot be null");
        }

        @Test
        void analysisError_withNullCallSiteId_throwsNullPointerException() {
            assertThatThrownBy(() -> new AnalysisOutcome.AnalysisError(new RuntimeException(), null, null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("Call site ID cannot be null");
        }

        @Test
        void analysisError_ifSuccessDoesNotExecuteAction() {
            RuntimeException cause = new RuntimeException("error");
            AnalysisOutcome error = new AnalysisOutcome.AnalysisError(cause, TEST_CALL_SITE_ID, null);
            AtomicBoolean actionExecuted = new AtomicBoolean(false);

            AnalysisOutcome returnedOutcome = error.ifSuccess(r -> actionExecuted.set(true));

            assertThat(actionExecuted.get()).isFalse();
            assertThat(returnedOutcome).isSameAs(error);
        }

        @Test
        void analysisError_of_createsWithoutContext() {
            RuntimeException cause = new RuntimeException("error");

            AnalysisOutcome.AnalysisError error = AnalysisOutcome.AnalysisError.of(cause, TEST_CALL_SITE_ID);

            assertThat(error.context()).isNull();
        }

        @Test
        void analysisError_withContext_createsWithContext() {
            RuntimeException cause = new RuntimeException("error");

            AnalysisOutcome.AnalysisError error = AnalysisOutcome.AnalysisError.withContext(cause, TEST_CALL_SITE_ID, "context info");

            assertThat(error.context()).isEqualTo("context info");
        }

        @Test
        void analysisError_formattedMessage_withContext() {
            RuntimeException cause = new RuntimeException("NullPointerException");
            AnalysisOutcome.AnalysisError error = new AnalysisOutcome.AnalysisError(cause, TEST_CALL_SITE_ID, "processing where clause");

            String message = error.formattedMessage();

            assertThat(message).contains(TEST_CALL_SITE_ID);
            assertThat(message).contains("processing where clause");
            assertThat(message).contains("NullPointerException");
        }

        @Test
        void analysisError_formattedMessage_withoutContext() {
            RuntimeException cause = new RuntimeException("NullPointerException");
            AnalysisOutcome.AnalysisError error = new AnalysisOutcome.AnalysisError(cause, TEST_CALL_SITE_ID, null);

            String message = error.formattedMessage();

            assertThat(message).contains(TEST_CALL_SITE_ID);
            assertThat(message).contains("NullPointerException");
            assertThat(message).doesNotContain("(null)");
        }

        @Test
        void analysisError_formattedMessage_withBlankContext() {
            RuntimeException cause = new RuntimeException("error");
            AnalysisOutcome.AnalysisError error = new AnalysisOutcome.AnalysisError(cause, TEST_CALL_SITE_ID, "   ");

            String message = error.formattedMessage();

            assertThat(message).contains(TEST_CALL_SITE_ID);
            assertThat(message).doesNotContain("(   )");
        }

        @Test
        void analysisError_factoryMethod_createsErrorOutcome() {
            RuntimeException cause = new RuntimeException("error");

            AnalysisOutcome.AnalysisError error = AnalysisOutcome.error(cause, TEST_CALL_SITE_ID);

            assertThat(error.cause()).isEqualTo(cause);
            assertThat(error.callSiteId()).isEqualTo(TEST_CALL_SITE_ID);
        }
    }

    // ========================================================================
    // fold() Tests
    // ========================================================================

    @Nested
    class FoldTests {

        @Test
        void fold_withSuccess_callsOnSuccess() {
            LambdaAnalysisResult.SimpleQueryResult result = new LambdaAnalysisResult.SimpleQueryResult(null, null, null, 0);
            AnalysisOutcome success = new AnalysisOutcome.Success(result, TEST_CALL_SITE_ID, TEST_LAMBDA_HASH);

            String folded = success.fold(
                    s -> "success:" + s.lambdaHash(),
                    u -> "unsupported:" + u.reason(),
                    e -> "error:" + e.cause().getMessage()
            );

            assertThat(folded).isEqualTo("success:" + TEST_LAMBDA_HASH);
        }

        @Test
        void fold_withUnsupported_callsOnUnsupported() {
            AnalysisOutcome unsupported = new AnalysisOutcome.UnsupportedPattern(
                    "complex lambda", TEST_CALL_SITE_ID, AnalysisOutcome.UnsupportedPattern.PatternType.OTHER);

            String folded = unsupported.fold(
                    s -> "success",
                    u -> "unsupported:" + u.reason(),
                    e -> "error"
            );

            assertThat(folded).isEqualTo("unsupported:complex lambda");
        }

        @Test
        void fold_withError_callsOnError() {
            RuntimeException cause = new RuntimeException("boom");
            AnalysisOutcome error = new AnalysisOutcome.AnalysisError(cause, TEST_CALL_SITE_ID, null);

            String folded = error.fold(
                    s -> "success",
                    u -> "unsupported",
                    e -> "error:" + e.cause().getMessage()
            );

            assertThat(folded).isEqualTo("error:boom");
        }
    }
}
