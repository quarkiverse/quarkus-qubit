package io.quarkiverse.qubit.deployment.analysis.handler;

import java.util.List;

import io.quarkiverse.qubit.deployment.analysis.InvokeDynamicScanner.LambdaCallSite;

/**
 * Registry for query type handlers. Immutable and thread-safe.
 * Handlers checked in order: Group, Join, Aggregation, Simple (default).
 */
public record QueryTypeHandlerRegistry(List<QueryTypeHandler> handlers) {

    private static final QueryTypeHandlerRegistry DEFAULT = new QueryTypeHandlerRegistry(
            List.of(
                    GroupQueryHandler.instance(),
                    JoinQueryHandler.instance(),
                    AggregationQueryHandler.instance(),
                    SimpleQueryHandler.instance()));

    /** Creates registry with handlers in priority order (specific handlers first). */
    public QueryTypeHandlerRegistry {
        handlers = List.copyOf(handlers);
    }

    /** Returns the default registry with all standard handlers. */
    public static QueryTypeHandlerRegistry getDefault() {
        return DEFAULT;
    }

    /**
     * Finds handler for call site.
     *
     * @throws IllegalStateException if no handler found
     */
    public QueryTypeHandler handlerFor(LambdaCallSite callSite) {
        for (QueryTypeHandler handler : handlers) {
            if (handler.canHandle(callSite)) {
                return handler;
            }
        }

        // This should never happen since SimpleQueryHandler handles everything else
        throw new IllegalStateException(
                "No handler found for call site: " + callSite.getCallSiteId());
    }
}
