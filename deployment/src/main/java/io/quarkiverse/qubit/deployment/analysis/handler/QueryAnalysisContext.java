package io.quarkiverse.qubit.deployment.analysis.handler;

import static io.quarkiverse.qubit.deployment.common.ExceptionMessages.CALL_SITE_ID_NULL;

import java.util.Arrays;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

import io.quarkiverse.qubit.deployment.analysis.InvokeDynamicScanner.LambdaCallSite;
import io.quarkiverse.qubit.deployment.analysis.LambdaBytecodeAnalyzer;
import io.quarkiverse.qubit.deployment.analysis.LambdaDeduplicator;
import io.quarkiverse.qubit.deployment.metrics.BuildMetricsCollector;

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
        @Nullable BuildMetricsCollector metricsCollector) {

    public QueryAnalysisContext {
        Objects.requireNonNull(classBytes, "Class bytes cannot be null");
        Objects.requireNonNull(callSite, "Call site cannot be null");
        Objects.requireNonNull(callSiteId, CALL_SITE_ID_NULL);
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

    // Override equals/hashCode/toString to handle byte[] array content properly

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof QueryAnalysisContext(var thatClassBytes, var thatCallSite, var thatCallSiteId, var thatBytecodeAnalyzer, var thatDeduplicator, var thatMetricsCollector))) {
            return false;
        }
        return Arrays.equals(classBytes, thatClassBytes) &&
                Objects.equals(callSite, thatCallSite) &&
                Objects.equals(callSiteId, thatCallSiteId) &&
                Objects.equals(bytecodeAnalyzer, thatBytecodeAnalyzer) &&
                Objects.equals(deduplicator, thatDeduplicator) &&
                Objects.equals(metricsCollector, thatMetricsCollector);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(callSite, callSiteId, bytecodeAnalyzer, deduplicator, metricsCollector);
        result = 31 * result + Arrays.hashCode(classBytes);
        return result;
    }

    @Override
    public String toString() {
        return "QueryAnalysisContext[" +
                "classBytes=" + classBytes.length + " bytes, " +
                "callSite=" + callSite + ", " +
                "callSiteId=" + callSiteId + ", " +
                "bytecodeAnalyzer=" + bytecodeAnalyzer + ", " +
                "deduplicator=" + deduplicator + ", " +
                "metricsCollector=" + metricsCollector + ']';
    }
}
