package io.quarkiverse.qubit.deployment.analysis;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static io.quarkiverse.qubit.deployment.common.ExceptionMessages.LAMBDA_HASH_REQUIRED;
import static io.quarkiverse.qubit.deployment.common.ExceptionMessages.QUERY_ID_REQUIRED;
import static io.quarkiverse.qubit.runtime.internal.QubitConstants.HASH_CHARS_FOR_CLASS_NAME;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link QueryExecutionPlan} record and its Builder.
 */
class QueryExecutionPlanTest {

    private static final String TEST_LAMBDA_HASH = "abc123def456ghi789";
    private static final String TEST_QUERY_ID = "com.example.PersonRepository:findByAge:42";
    private static final String TEST_ENTITY_CLASS = "com.example.Person";
    private static final String TEST_TERMINAL_METHOD = "toList";

    // ========================================================================
    // Record Constructor Tests
    // ========================================================================

    @Nested
    class RecordTests {

        @Test
        void generateClassName_usesHashPrefix() {
            QueryExecutionPlan plan = new QueryExecutionPlan(
                    TEST_LAMBDA_HASH,
                    TEST_QUERY_ID,
                    TEST_ENTITY_CLASS,
                    TEST_TERMINAL_METHOD,
                    false,
                    null,
                    null,
                    0
            );

            String className = plan.generateClassName("io.quarkiverse.qubit.generated", "QueryExecutor_");

            String expectedPrefix = TEST_LAMBDA_HASH.substring(0, HASH_CHARS_FOR_CLASS_NAME);
            assertThat(className).isEqualTo("io.quarkiverse.qubit.generated.QueryExecutor_" + expectedPrefix);
        }

        @Test
        void generateClassName_withDifferentPackages() {
            QueryExecutionPlan plan = new QueryExecutionPlan(
                    "abcd1234efgh5678ijkl", TEST_QUERY_ID, null, null, false, null, null, 0);

            String className1 = plan.generateClassName("com.example.generated", "QE_");
            String className2 = plan.generateClassName("org.test.gen", "Executor_");

            assertThat(className1).startsWith("com.example.generated.QE_");
            assertThat(className2).startsWith("org.test.gen.Executor_");
        }
    }

    // ========================================================================
    // Builder Tests
    // ========================================================================

    @Nested
    class BuilderTests {

        @Test
        void builder_createsValidPlan() {
            QueryExecutionPlan plan = QueryExecutionPlan.builder()
                    .lambdaHash(TEST_LAMBDA_HASH)
                    .queryId(TEST_QUERY_ID)
                    .entityClassName(TEST_ENTITY_CLASS)
                    .terminalMethodName(TEST_TERMINAL_METHOD)
                    .hasDistinct(true)
                    .skipValue(10)
                    .limitValue(100)
                    .capturedVarCount(5)
                    .build();

            assertThat(plan.lambdaHash()).isEqualTo(TEST_LAMBDA_HASH);
            assertThat(plan.queryId()).isEqualTo(TEST_QUERY_ID);
            assertThat(plan.entityClassName()).isEqualTo(TEST_ENTITY_CLASS);
            assertThat(plan.terminalMethodName()).isEqualTo(TEST_TERMINAL_METHOD);
            assertThat(plan.hasDistinct()).isTrue();
            assertThat(plan.skipValue()).isEqualTo(10);
            assertThat(plan.limitValue()).isEqualTo(100);
            assertThat(plan.capturedVarCount()).isEqualTo(5);
        }

        @Test
        void builder_withMinimalRequiredFields() {
            QueryExecutionPlan plan = QueryExecutionPlan.builder()
                    .lambdaHash(TEST_LAMBDA_HASH)
                    .queryId(TEST_QUERY_ID)
                    .build();

            assertThat(plan.lambdaHash()).isEqualTo(TEST_LAMBDA_HASH);
            assertThat(plan.queryId()).isEqualTo(TEST_QUERY_ID);
            assertThat(plan.entityClassName()).isNull();
            assertThat(plan.terminalMethodName()).isNull();
            assertThat(plan.hasDistinct()).isFalse();
            assertThat(plan.skipValue()).isNull();
            assertThat(plan.limitValue()).isNull();
            assertThat(plan.capturedVarCount()).isZero();
        }

        @Test
        void builder_withoutLambdaHash_throwsIllegalStateException() {
            assertThatThrownBy(() -> QueryExecutionPlan.builder()
                    .queryId(TEST_QUERY_ID)
                    .build())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage(LAMBDA_HASH_REQUIRED);
        }

        @Test
        void builder_withEmptyLambdaHash_throwsIllegalStateException() {
            assertThatThrownBy(() -> QueryExecutionPlan.builder()
                    .lambdaHash("")
                    .queryId(TEST_QUERY_ID)
                    .build())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage(LAMBDA_HASH_REQUIRED);
        }

        @Test
        void builder_withoutQueryId_throwsIllegalStateException() {
            assertThatThrownBy(() -> QueryExecutionPlan.builder()
                    .lambdaHash(TEST_LAMBDA_HASH)
                    .build())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage(QUERY_ID_REQUIRED);
        }

        @Test
        void builder_withEmptyQueryId_throwsIllegalStateException() {
            assertThatThrownBy(() -> QueryExecutionPlan.builder()
                    .lambdaHash(TEST_LAMBDA_HASH)
                    .queryId("")
                    .build())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage(QUERY_ID_REQUIRED);
        }

        @Test
        void builder_isFluent() {
            QueryExecutionPlan.Builder builder = QueryExecutionPlan.builder();

            assertThat(builder.lambdaHash(TEST_LAMBDA_HASH)).isSameAs(builder);
            assertThat(builder.queryId(TEST_QUERY_ID)).isSameAs(builder);
            assertThat(builder.entityClassName(TEST_ENTITY_CLASS)).isSameAs(builder);
            assertThat(builder.terminalMethodName(TEST_TERMINAL_METHOD)).isSameAs(builder);
            assertThat(builder.hasDistinct(true)).isSameAs(builder);
            assertThat(builder.skipValue(5)).isSameAs(builder);
            assertThat(builder.limitValue(10)).isSameAs(builder);
            assertThat(builder.capturedVarCount(2)).isSameAs(builder);
        }

        @Test
        void builder_canOverrideValues() {
            QueryExecutionPlan plan = QueryExecutionPlan.builder()
                    .lambdaHash("initial")
                    .queryId("initial")
                    .hasDistinct(true)
                    .lambdaHash(TEST_LAMBDA_HASH)
                    .queryId(TEST_QUERY_ID)
                    .hasDistinct(false)
                    .build();

            assertThat(plan.lambdaHash()).isEqualTo(TEST_LAMBDA_HASH);
            assertThat(plan.queryId()).isEqualTo(TEST_QUERY_ID);
            assertThat(plan.hasDistinct()).isFalse();
        }
    }

}
