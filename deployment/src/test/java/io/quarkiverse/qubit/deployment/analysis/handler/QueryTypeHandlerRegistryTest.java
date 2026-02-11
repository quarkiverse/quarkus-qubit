package io.quarkiverse.qubit.deployment.analysis.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import io.quarkiverse.qubit.deployment.analysis.InvokeDynamicScanner.LambdaCallSite;

/**
 * Unit tests for {@link QueryTypeHandlerRegistry}.
 */
@DisplayName("QueryTypeHandlerRegistry")
class QueryTypeHandlerRegistryTest {

    @Nested
    @DisplayName("getDefault()")
    class GetDefaultTests {

        @Test
        @DisplayName("returns non-null registry")
        void returnsNonNull() {
            QueryTypeHandlerRegistry registry = QueryTypeHandlerRegistry.getDefault();

            assertThat(registry).isNotNull();
        }

        @Test
        @DisplayName("contains all four standard handlers")
        void containsAllHandlers() {
            QueryTypeHandlerRegistry registry = QueryTypeHandlerRegistry.getDefault();

            assertThat(registry.handlers()).hasSize(4);
        }

        @Test
        @DisplayName("handlers are in correct priority order")
        void handlersInCorrectOrder() {
            QueryTypeHandlerRegistry registry = QueryTypeHandlerRegistry.getDefault();
            List<QueryTypeHandler> handlers = registry.handlers();

            assertThat(handlers.getFirst()).isInstanceOf(GroupQueryHandler.class);
            assertThat(handlers.get(1)).isInstanceOf(JoinQueryHandler.class);
            assertThat(handlers.get(2)).isInstanceOf(AggregationQueryHandler.class);
            assertThat(handlers.get(3)).isInstanceOf(SimpleQueryHandler.class);
        }

    }

    @Nested
    @DisplayName("handlers()")
    class HandlersTests {

        @Test
        @DisplayName("returns immutable list")
        void returnsImmutableList() {
            QueryTypeHandlerRegistry registry = QueryTypeHandlerRegistry.getDefault();
            List<QueryTypeHandler> handlers = registry.handlers();

            // Use concrete handler since QueryTypeHandler is a sealed interface
            assertThatThrownBy(() -> handlers.add(SimpleQueryHandler.instance()))
                    .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        @DisplayName("defensive copy made at construction")
        void defensiveCopyMade() {
            List<QueryTypeHandler> mutableList = new ArrayList<>();
            mutableList.add(SimpleQueryHandler.instance());

            QueryTypeHandlerRegistry registry = new QueryTypeHandlerRegistry(mutableList);

            // Modify original list
            mutableList.add(GroupQueryHandler.instance());

            // Registry should not be affected
            assertThat(registry.handlers()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("handlerFor()")
    class HandlerForTests {

        @Test
        @DisplayName("returns SimpleQueryHandler for simple queries")
        void returnsSimpleHandlerForSimpleQueries() {
            LambdaCallSite callSite = mock(LambdaCallSite.class);
            when(callSite.isGroupQuery()).thenReturn(false);
            when(callSite.isJoinQuery()).thenReturn(false);
            when(callSite.isAggregationQuery()).thenReturn(false);

            QueryTypeHandlerRegistry registry = QueryTypeHandlerRegistry.getDefault();
            QueryTypeHandler handler = registry.handlerFor(callSite);

            assertThat(handler).isInstanceOf(SimpleQueryHandler.class);
        }

        @Test
        @DisplayName("returns GroupQueryHandler for group queries")
        void returnsGroupHandlerForGroupQueries() {
            LambdaCallSite callSite = mock(LambdaCallSite.class);
            when(callSite.isGroupQuery()).thenReturn(true);

            QueryTypeHandlerRegistry registry = QueryTypeHandlerRegistry.getDefault();
            QueryTypeHandler handler = registry.handlerFor(callSite);

            assertThat(handler).isInstanceOf(GroupQueryHandler.class);
        }

        @Test
        @DisplayName("returns JoinQueryHandler for join queries")
        void returnsJoinHandlerForJoinQueries() {
            LambdaCallSite callSite = mock(LambdaCallSite.class);
            when(callSite.isGroupQuery()).thenReturn(false);
            when(callSite.isJoinQuery()).thenReturn(true);

            QueryTypeHandlerRegistry registry = QueryTypeHandlerRegistry.getDefault();
            QueryTypeHandler handler = registry.handlerFor(callSite);

            assertThat(handler).isInstanceOf(JoinQueryHandler.class);
        }

        @Test
        @DisplayName("returns AggregationQueryHandler for aggregation queries")
        void returnsAggregationHandlerForAggregationQueries() {
            LambdaCallSite callSite = mock(LambdaCallSite.class);
            when(callSite.isGroupQuery()).thenReturn(false);
            when(callSite.isJoinQuery()).thenReturn(false);
            when(callSite.isAggregationQuery()).thenReturn(true);

            QueryTypeHandlerRegistry registry = QueryTypeHandlerRegistry.getDefault();
            QueryTypeHandler handler = registry.handlerFor(callSite);

            assertThat(handler).isInstanceOf(AggregationQueryHandler.class);
        }

        @Test
        @DisplayName("throws IllegalStateException when no handler found")
        void throwsWhenNoHandlerFound() {
            // Create a registry with no handlers
            QueryTypeHandlerRegistry emptyRegistry = new QueryTypeHandlerRegistry(List.of());

            LambdaCallSite callSite = mock(LambdaCallSite.class);
            when(callSite.getCallSiteId()).thenReturn("test-call-site-123");

            assertThatThrownBy(() -> emptyRegistry.handlerFor(callSite))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("No handler found")
                    .hasMessageContaining("test-call-site-123");
        }
    }

    @Nested
    @DisplayName("Constructor")
    class ConstructorTests {

        @Test
        @DisplayName("creates registry with empty list")
        void createsWithEmptyList() {
            QueryTypeHandlerRegistry registry = new QueryTypeHandlerRegistry(List.of());

            assertThat(registry.handlers()).isEmpty();
        }

        @Test
        @DisplayName("creates registry with single handler")
        void createsWithSingleHandler() {
            QueryTypeHandlerRegistry registry = new QueryTypeHandlerRegistry(
                    List.of(SimpleQueryHandler.instance()));

            assertThat(registry.handlers())
                    .hasSize(1)
                    .containsExactly(SimpleQueryHandler.instance());
        }

        @Test
        @DisplayName("creates registry with multiple handlers")
        void createsWithMultipleHandlers() {
            QueryTypeHandlerRegistry registry = new QueryTypeHandlerRegistry(
                    List.of(GroupQueryHandler.instance(), SimpleQueryHandler.instance()));

            assertThat(registry.handlers()).hasSize(2);
        }
    }
}
