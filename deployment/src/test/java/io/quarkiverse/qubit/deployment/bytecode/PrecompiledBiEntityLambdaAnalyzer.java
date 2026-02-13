package io.quarkiverse.qubit.deployment.bytecode;

import static io.quarkiverse.qubit.runtime.internal.QubitConstants.BI_QUERY_SPEC_DESCRIPTOR;
import static org.assertj.core.api.Assertions.assertThat;

import org.objectweb.asm.Handle;

import io.quarkiverse.qubit.deployment.analysis.LambdaBytecodeAnalyzer;
import io.quarkiverse.qubit.deployment.ast.LambdaExpression;
import io.quarkiverse.qubit.deployment.testutil.AbstractLambdaAnalyzer;

/**
 * Base class for bi-entity lambda bytecode analysis tests.
 *
 * <p>
 * This analyzer loads the BiEntityLambdaTestSources class and extracts
 * bi-entity lambda expressions (BiQuerySpec) from its pre-compiled methods.
 *
 * <p>
 * Unlike single-entity lambdas, bi-entity lambdas take two parameters
 * (source and joined entity) and produce BiEntityFieldAccess, BiEntityParameter,
 * and BiEntityPathExpression AST nodes.
 *
 * <p>
 * Extends {@link AbstractLambdaAnalyzer} to share common infrastructure.
 */
public abstract class PrecompiledBiEntityLambdaAnalyzer extends AbstractLambdaAnalyzer {

    private static final String SOURCES_CLASS_NAME = "io.quarkiverse.qubit.deployment.testutil.BiEntityLambdaTestSources";

    @Override
    protected String getSourcesClassName() {
        return SOURCES_CLASS_NAME;
    }

    @Override
    protected String getDescriptorPattern() {
        return BI_QUERY_SPEC_DESCRIPTOR;
    }

    /**
     * Analyzes a pre-compiled bi-entity lambda expression by method name.
     * Uses analyzeBiEntity() to produce BiEntity-aware AST nodes.
     *
     * @param methodName the name of the method in BiEntityLambdaTestSources containing the lambda
     * @return the parsed LambdaExpression AST with BiEntity nodes
     */
    protected LambdaExpression analyzeBiEntityLambda(String methodName) {
        try {
            Handle lambdaHandle = getLambdaHandle(methodName);
            byte[] classBytes = getSourceClassBytes();

            LambdaBytecodeAnalyzer analyzer = new LambdaBytecodeAnalyzer();
            return analyzer.analyzeBiEntity(classBytes, lambdaHandle.getName(), lambdaHandle.getDesc());
        } catch (Exception e) {
            throw new RuntimeException("Failed to analyze bi-entity lambda from method: " + methodName, e);
        }
    }

    /**
     * Asserts that an expression is a BiEntityFieldAccess with the expected field name.
     */
    protected void assertBiEntityFieldAccess(LambdaExpression expr, String expectedFieldName) {
        assertThat(expr)
                .as("Expression should be a BiEntityFieldAccess but was %s",
                        expr == null ? "null" : expr.getClass().getSimpleName())
                .isInstanceOf(LambdaExpression.BiEntityFieldAccess.class);
        var fieldAccess = (LambdaExpression.BiEntityFieldAccess) expr;
        assertThat(fieldAccess.fieldName())
                .as("BiEntityFieldAccess field name should be '%s'", expectedFieldName)
                .isEqualTo(expectedFieldName);
    }

    /**
     * Asserts that an expression is a BiEntityFieldAccess with expected field and position.
     */
    protected void assertBiEntityFieldAccess(LambdaExpression expr, String expectedFieldName,
            LambdaExpression.EntityPosition expectedPosition) {
        assertThat(expr)
                .as("Expression should be a BiEntityFieldAccess but was %s",
                        expr == null ? "null" : expr.getClass().getSimpleName())
                .isInstanceOf(LambdaExpression.BiEntityFieldAccess.class);
        var fieldAccess = (LambdaExpression.BiEntityFieldAccess) expr;
        assertThat(fieldAccess.fieldName())
                .as("BiEntityFieldAccess field name should be '%s'", expectedFieldName)
                .isEqualTo(expectedFieldName);
        assertThat(fieldAccess.entityPosition())
                .as("BiEntityFieldAccess entity position should be %s for field '%s'", expectedPosition, expectedFieldName)
                .isEqualTo(expectedPosition);
    }
}
