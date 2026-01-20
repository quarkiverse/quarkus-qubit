package io.quarkiverse.qubit.deployment.bytecode;

import io.quarkiverse.qubit.deployment.ast.LambdaExpression;
import io.quarkiverse.qubit.deployment.analysis.LambdaBytecodeAnalyzer;
import io.quarkiverse.qubit.deployment.testutil.AbstractLambdaAnalyzer;
import org.objectweb.asm.Handle;

import static io.quarkiverse.qubit.runtime.internal.QubitConstants.QUERY_SPEC_DESCRIPTOR;

/**
 * Base class for bytecode analysis tests using pre-compiled lambda sources.
 *
 * <p>This analyzer loads the LambdaTestSources class and extracts
 * lambda expressions from its pre-compiled methods for analysis.
 *
 * <p>Extends {@link AbstractLambdaAnalyzer} to share common infrastructure
 * with other analyzer base classes.
 */
public abstract class PrecompiledLambdaAnalyzer extends AbstractLambdaAnalyzer {

    private static final String SOURCES_CLASS_NAME = "io.quarkiverse.qubit.deployment.testutil.LambdaTestSources";

    @Override
    protected String getSourcesClassName() {
        return SOURCES_CLASS_NAME;
    }

    @Override
    protected String getDescriptorPattern() {
        return QUERY_SPEC_DESCRIPTOR;
    }

    /**
     * Analyzes a pre-compiled lambda expression by method name.
     *
     * @param methodName the name of the method in LambdaTestSources containing the lambda
     * @return the parsed LambdaExpression AST
     */
    protected LambdaExpression analyzeLambda(String methodName) {
        try {
            Handle lambdaHandle = getLambdaHandle(methodName);
            byte[] classBytes = getSourceClassBytes();

            LambdaBytecodeAnalyzer analyzer = new LambdaBytecodeAnalyzer();
            return analyzer.analyze(classBytes, lambdaHandle.getName(), lambdaHandle.getDesc());
        } catch (Exception e) {
            throw new RuntimeException("Failed to analyze lambda from method: " + methodName, e);
        }
    }
}
