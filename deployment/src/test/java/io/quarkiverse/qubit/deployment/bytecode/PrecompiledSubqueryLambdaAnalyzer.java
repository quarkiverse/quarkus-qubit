package io.quarkiverse.qubit.deployment.bytecode;

import io.quarkiverse.qubit.deployment.ast.LambdaExpression;
import io.quarkiverse.qubit.deployment.analysis.LambdaBytecodeAnalyzer;
import io.quarkiverse.qubit.deployment.testutil.AbstractLambdaAnalyzer;
import org.objectweb.asm.Handle;

import static io.quarkiverse.qubit.runtime.internal.QubitConstants.QUERY_SPEC_DESCRIPTOR;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Base class for subquery lambda bytecode analysis tests.
 *
 * <p>This analyzer loads the SubqueryLambdaTestSources class and extracts
 * lambda expressions containing Subqueries.* calls from its pre-compiled methods.
 *
 * <p>Extends {@link AbstractLambdaAnalyzer} to share common infrastructure.
 */
public abstract class PrecompiledSubqueryLambdaAnalyzer extends AbstractLambdaAnalyzer {

    private static final String SOURCES_CLASS_NAME = "io.quarkiverse.qubit.deployment.testutil.SubqueryLambdaTestSources";

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
     * @param methodName the name of the method in SubqueryLambdaTestSources containing the lambda
     * @return the parsed LambdaExpression AST
     */
    protected LambdaExpression analyzeSubqueryLambda(String methodName) {
        try {
            Handle lambdaHandle = getLambdaHandle(methodName);
            byte[] classBytes = getSourceClassBytes();

            LambdaBytecodeAnalyzer analyzer = new LambdaBytecodeAnalyzer();
            return analyzer.analyze(classBytes, lambdaHandle.getName(), lambdaHandle.getDesc());
        } catch (Exception e) {
            throw new RuntimeException("Failed to analyze subquery lambda from method: " + methodName, e);
        }
    }

    // ==================== SUBQUERY SPECIFIC ASSERTION HELPERS ====================

    /**
     * Asserts that an expression is a ScalarSubquery with the expected aggregation type.
     */
    protected void assertScalarSubquery(LambdaExpression expr,
                                         LambdaExpression.SubqueryAggregationType expectedType) {
        assertThat(expr)
                .as("Expression should be a ScalarSubquery but was %s", expr == null ? "null" : expr.getClass().getSimpleName())
                .isInstanceOf(LambdaExpression.ScalarSubquery.class);
        var subquery = (LambdaExpression.ScalarSubquery) expr;
        assertThat(subquery.aggregationType())
                .as("ScalarSubquery aggregation type should be %s", expectedType)
                .isEqualTo(expectedType);
    }

    /**
     * Asserts that an expression is an ExistsSubquery.
     */
    protected void assertExistsSubquery(LambdaExpression expr, boolean expectedNegated) {
        assertThat(expr)
                .as("Expression should be an ExistsSubquery but was %s", expr == null ? "null" : expr.getClass().getSimpleName())
                .isInstanceOf(LambdaExpression.ExistsSubquery.class);
        var subquery = (LambdaExpression.ExistsSubquery) expr;
        assertThat(subquery.negated())
                .as("ExistsSubquery negated flag should be %s", expectedNegated)
                .isEqualTo(expectedNegated);
    }

    /**
     * Asserts that an expression is an InSubquery.
     */
    protected void assertInSubquery(LambdaExpression expr, boolean expectedNegated) {
        assertThat(expr)
                .as("Expression should be an InSubquery but was %s", expr == null ? "null" : expr.getClass().getSimpleName())
                .isInstanceOf(LambdaExpression.InSubquery.class);
        var subquery = (LambdaExpression.InSubquery) expr;
        assertThat(subquery.negated())
                .as("InSubquery negated flag should be %s", expectedNegated)
                .isEqualTo(expectedNegated);
    }
}
