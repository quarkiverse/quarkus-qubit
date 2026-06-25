package io.quarkiverse.qubit.deployment.testutil.fixtures;

import static io.quarkiverse.qubit.deployment.testutil.fixtures.AsmFixtures.testMethod;

import org.objectweb.asm.tree.MethodNode;

import io.quarkiverse.qubit.deployment.analysis.instruction.AnalysisContext;

/**
 * Fluent builders for AnalysisContext test fixtures.
 *
 * <p>
 * Usage:
 *
 * <pre>{@code
 * import static ...fixtures.AnalysisContextFixtures.*;
 *
 * AnalysisContext ctx = simpleContext();
 * AnalysisContext biEntity = biEntityContext();
 * }</pre>
 */
public final class AnalysisContextFixtures {

    private AnalysisContextFixtures() {
    }

    /** Creates a simple single-entity AnalysisContext with default method. */
    public static AnalysisContext simpleContext() {
        return new AnalysisContext(testMethod().build(), 0);
    }

    /** Creates an AnalysisContext with the given method and entity parameter index. */
    public static AnalysisContext contextFor(MethodNode method, int entityParamIndex) {
        return new AnalysisContext(method, entityParamIndex);
    }

    /** Creates a bi-entity AnalysisContext (joins, subqueries). */
    public static AnalysisContext biEntityContext() {
        MethodNode method = testMethod()
                .withDesc("(Ljava/lang/Object;Ljava/lang/Object;)Z")
                .build();
        return new AnalysisContext(method, 0, 1);
    }

    /** Creates a bi-entity AnalysisContext with custom method. */
    public static AnalysisContext biEntityContext(MethodNode method) {
        return new AnalysisContext(method, 0, 1);
    }

}
