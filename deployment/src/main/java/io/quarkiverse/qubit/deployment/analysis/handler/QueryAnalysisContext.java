package io.quarkiverse.qubit.deployment.analysis.handler;

import static io.quarkiverse.qubit.deployment.common.ExceptionMessages.CALL_SITE_ID_NULL;

import java.util.Objects;

import org.jspecify.annotations.Nullable;

import io.quarkiverse.qubit.deployment.analysis.CallSite;
import io.quarkiverse.qubit.deployment.analysis.LambdaBytecodeAnalyzer;
import io.quarkiverse.qubit.deployment.analysis.LambdaDeduplicator;
import io.quarkiverse.qubit.deployment.metrics.BuildMetricsCollector;

/**
 * Context for query-level lambda bytecode analysis.
 * Renamed from AnalysisContext to avoid collision with instruction.AnalysisContext.
 */
public record QueryAnalysisContext(
        byte[] classBytes,
        CallSite callSite,
        String callSiteId,
        LambdaBytecodeAnalyzer bytecodeAnalyzer,
        LambdaDeduplicator deduplicator,
        @Nullable BuildMetricsCollector metricsCollector) {

    public QueryAnalysisContext {
        Objects.requireNonNull(classBytes, "Class bytes cannot be null");
        Objects.requireNonNull(callSite, "Call site cannot be null");
        Objects.requireNonNull(callSiteId, CALL_SITE_ID_NULL);
        Objects.requireNonNull(bytecodeAnalyzer, "Bytecode analyzer cannot be null");
        Objects.requireNonNull(deduplicator, "Deduplicator cannot be null");
        // metricsCollector can be null
    }

    /** Creates context from components with metrics collector. */
    public static QueryAnalysisContext of(
            byte[] classBytes,
            CallSite callSite,
            LambdaBytecodeAnalyzer bytecodeAnalyzer,
            LambdaDeduplicator deduplicator,
            @Nullable BuildMetricsCollector metricsCollector) {
        return new QueryAnalysisContext(
                classBytes,
                callSite,
                callSite.getCallSiteId(),
                bytecodeAnalyzer,
                deduplicator,
                metricsCollector);
    }

}
