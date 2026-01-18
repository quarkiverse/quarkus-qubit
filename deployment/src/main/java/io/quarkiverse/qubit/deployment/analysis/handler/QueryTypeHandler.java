package io.quarkiverse.qubit.deployment.analysis.handler;

import io.quarkiverse.qubit.deployment.analysis.AnalysisOutcome;
import io.quarkiverse.qubit.deployment.analysis.InvokeDynamicScanner.LambdaCallSite;
import io.quarkiverse.qubit.deployment.analysis.LambdaAnalysisResult;
import io.quarkiverse.qubit.deployment.analysis.LambdaDeduplicator;

/**
 * Strategy interface for query type-specific analysis and hash computation.
 * Sealed interface ensures exhaustive handling of all query types.
 */
public sealed interface QueryTypeHandler permits AbstractQueryHandler {

    /** Returns query type name for logging (e.g., "SIMPLE", "JOIN"). */
    String queryTypeName();

    /** Returns true if this handler can process the call site. */
    boolean canHandle(LambdaCallSite callSite);

    /** Analyzes lambda bytecode; returns Success, UnsupportedPattern, or AnalysisError. */
    AnalysisOutcome analyze(QueryAnalysisContext context);

    /** Computes MD5 hash for deduplication based on query type-specific components. */
    String computeHash(LambdaDeduplicator deduplicator, LambdaCallSite callSite, LambdaAnalysisResult result);
}
