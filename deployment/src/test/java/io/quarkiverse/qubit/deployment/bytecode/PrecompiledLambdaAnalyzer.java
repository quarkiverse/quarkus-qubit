package io.quarkiverse.qubit.deployment.bytecode;

import static io.quarkiverse.qubit.runtime.internal.QubitConstants.QUERY_SPEC_DESCRIPTOR;

import io.quarkiverse.qubit.deployment.testutil.AbstractLambdaAnalyzer;

/**
 * Base class for bytecode analysis tests using pre-compiled lambda sources.
 *
 * <p>
 * This analyzer loads the LambdaTestSources class and extracts
 * lambda expressions from its pre-compiled methods for analysis.
 *
 * <p>
 * Extends {@link AbstractLambdaAnalyzer} to share common infrastructure
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

}
