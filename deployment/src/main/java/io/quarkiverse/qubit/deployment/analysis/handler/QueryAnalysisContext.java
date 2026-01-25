package io.quarkiverse.qubit.deployment.analysis.handler;

import io.quarkiverse.qubit.deployment.analysis.InvokeDynamicScanner.LambdaCallSite;
import io.quarkiverse.qubit.deployment.analysis.LambdaBytecodeAnalyzer;
import io.quarkiverse.qubit.deployment.analysis.LambdaDeduplicator;
import io.quarkiverse.qubit.deployment.metrics.BuildMetricsCollector;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

/**
 * Context for query-level lambda bytecode analysis.
 * Renamed from AnalysisContext to avoid collision with instruction.AnalysisContext.
 */
public record QueryAnalysisContext(
        byte[] classBytes,
        LambdaCallSite callSite,
        String callSiteId,
        LambdaBytecodeAnalyzer bytecodeAnalyzer,
        LambdaDeduplicator deduplicator,
        @Nullable BuildMetricsCollector metricsCollector
) {

    public QueryAnalysisContext {
        Objects.requireNonNull(classBytes, "Class bytes cannot be null");
        Objects.requireNonNull(callSite, "Call site cannot be null");
        Objects.requireNonNull(callSiteId, "Call site ID cannot be null");
        Objects.requireNonNull(bytecodeAnalyzer, "Bytecode analyzer cannot be null");
        Objects.requireNonNull(deduplicator, "Deduplicator cannot be null");
        // metricsCollector can be null
    }

    /** Creates context from components, deriving callSiteId from callSite. */
    public static QueryAnalysisContext of(
            byte[] classBytes,
            LambdaCallSite callSite,
            LambdaBytecodeAnalyzer bytecodeAnalyzer,
            LambdaDeduplicator deduplicator) {
        return new QueryAnalysisContext(
                classBytes,
                callSite,
                callSite.getCallSiteId(),
                bytecodeAnalyzer,
                deduplicator,
                null);
    }

    /** Creates context from components with metrics collector. */
    public static QueryAnalysisContext of(
            byte[] classBytes,
            LambdaCallSite callSite,
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
