package io.quarkiverse.qubit.deployment.testutil.fixtures;

import org.objectweb.asm.tree.MethodNode;

import io.quarkiverse.qubit.deployment.analysis.instruction.AnalysisContext;

/** Builders for AnalysisContext test fixtures. */
public final class AnalysisContextFixtures {

    private AnalysisContextFixtures() {
    }

    /** Creates an AnalysisContext with the given method and entity parameter index. */
    public static AnalysisContext contextFor(MethodNode method, int entityParamIndex) {
        return new AnalysisContext(method, entityParamIndex);
    }

}
