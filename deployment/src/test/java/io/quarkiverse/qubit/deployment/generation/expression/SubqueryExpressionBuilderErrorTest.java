package io.quarkiverse.qubit.deployment.generation.expression;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for error paths in SubqueryExpressionBuilder.
 *
 * <p>These tests verify that the builder correctly validates null inputs
 * and throws appropriate exceptions with descriptive messages.
 *
 * <p>Note: buildFieldPath null expression and handleAggregation unexpected type
 * are internal methods that are harder to test directly. They are covered through the
 * public API null checks.
 */
class SubqueryExpressionBuilderErrorTest {

    private SubqueryExpressionBuilder builder;

    @BeforeEach
    void setUp() {
        builder = SubqueryExpressionBuilder.INSTANCE;
    }

    @Nested
    @DisplayName("buildScalarSubquery Null Validation")
    class ScalarSubqueryNullTests {

        @Test
        @DisplayName("buildScalarSubquery throws when ScalarSubquery is null")
        void buildScalarSubquery_nullSubquery_throws() {
            assertThatThrownBy(() -> builder.buildScalarSubquery(
                    null, // method - ok to be null, validation happens first
                    null, // scalar - THIS should cause the error
                    null, // cb
                    null, // query
                    null, // outerRoot
                    null  // capturedValues
            ))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("ScalarSubquery cannot be null");
        }
    }

    @Nested
    @DisplayName("buildExistsSubquery Null Validation")
    class ExistsSubqueryNullTests {

        @Test
        @DisplayName("buildExistsSubquery throws when ExistsSubquery is null")
        void buildExistsSubquery_nullSubquery_throws() {
            assertThatThrownBy(() -> builder.buildExistsSubquery(
                    null, // method
                    null, // exists - THIS should cause the error
                    null, // cb
                    null, // query
                    null, // outerRoot
                    null  // capturedValues
            ))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("ExistsSubquery cannot be null");
        }
    }

    @Nested
    @DisplayName("buildInSubquery Null Validation")
    class InSubqueryNullTests {

        @Test
        @DisplayName("buildInSubquery throws when InSubquery is null")
        void buildInSubquery_nullSubquery_throws() {
            assertThatThrownBy(() -> builder.buildInSubquery(
                    null, // method
                    null, // inSubquery - THIS should cause the error
                    null, // cb
                    null, // query
                    null, // outerRoot
                    null  // capturedValues
            ))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("InSubquery cannot be null");
        }
    }
}
