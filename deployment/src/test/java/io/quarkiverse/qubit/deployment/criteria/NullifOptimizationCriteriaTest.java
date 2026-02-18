package io.quarkiverse.qubit.deployment.criteria;

import static io.quarkiverse.qubit.runtime.internal.QubitConstants.QUERY_SPEC_DESCRIPTOR;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Root;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import io.quarkiverse.qubit.deployment.analysis.LambdaBytecodeAnalyzer;
import io.quarkiverse.qubit.deployment.ast.LambdaExpression;
import io.quarkiverse.qubit.deployment.generation.CriteriaExpressionGenerator;
import io.quarkiverse.qubit.deployment.testutil.AbstractLambdaAnalyzer;
import io.quarkus.gizmo2.Expr;
import io.quarkus.gizmo2.Gizmo;
import io.quarkus.gizmo2.ParamVar;

/**
 * Criteria generation tests for NULLIF optimization.
 *
 * <p>
 * NULLIF lambdas are projections (return String, not Boolean), so they use
 * {@code generateExpressionAsJpaExpression()} rather than {@code generatePredicate()}.
 */
@DisplayName("NULLIF optimization criteria generation")
class NullifOptimizationCriteriaTest extends AbstractLambdaAnalyzer {

    private static final String SOURCES_CLASS_NAME = "io.quarkiverse.qubit.deployment.testutil.LambdaTestSources";

    @Override
    protected String getSourcesClassName() {
        return SOURCES_CLASS_NAME;
    }

    @Override
    protected String getDescriptorPattern() {
        return QUERY_SPEC_DESCRIPTOR;
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {
            "nullifCanonical", "nullifNotEquals", "nullifCapturedSentinel",
            "notNullifDifferentFields", "notNullifNonNullTrue"
    })
    @DisplayName("generates criteria for NULLIF operations")
    void nullifOperation_generatesCriteria(String lambdaMethodName) {
        LambdaExpression expression = analyzeLambda(lambdaMethodName);
        assertExpressionGenerationSucceeds(expression);
    }

    private LambdaExpression analyzeLambda(String methodName) {
        try {
            var lambdaHandle = getLambdaHandle(methodName);
            byte[] classBytes = getSourceClassBytes();
            LambdaBytecodeAnalyzer analyzer = new LambdaBytecodeAnalyzer();
            return analyzer.analyze(classBytes, lambdaHandle.getName(), lambdaHandle.getDesc());
        } catch (Exception e) {
            throw new RuntimeException("Failed to analyze lambda: " + methodName, e);
        }
    }

    /**
     * Verifies that expression generation succeeds for projection-type expressions.
     * Uses {@code generateExpressionAsJpaExpression()} instead of {@code generatePredicate()}.
     */
    private void assertExpressionGenerationSucceeds(LambdaExpression expression) {
        try {
            String className = "io.quarkiverse.qubit.test.NullifTestExecutor_" + System.nanoTime();
            Map<String, byte[]> classData = new HashMap<>();
            final boolean[] expressionGenerated = { false };

            Gizmo gizmo = Gizmo.create(classData::put);

            gizmo.class_(className, cc -> {
                cc.public_();
                cc.method("buildExpression", mc -> {
                    mc.public_();
                    mc.returning(Expression.class);
                    ParamVar cb = mc.parameter("cb", CriteriaBuilder.class);
                    ParamVar root = mc.parameter("root", Root.class);
                    ParamVar capturedValues = mc.parameter("capturedValues", Object[].class);

                    mc.body(bc -> {
                        CriteriaExpressionGenerator generator = new CriteriaExpressionGenerator();
                        Expr result = generator.generateExpressionAsJpaExpression(
                                bc, expression, cb, root, capturedValues);
                        expressionGenerated[0] = result != null;
                        bc.return_(result);
                    });
                });
            });

            String classFileName = className.replace('.', '/') + ".class";
            byte[] bytecode = classData.get(classFileName);

            assertThat(bytecode)
                    .as("Bytecode should be generated for: " + expression)
                    .isNotNull();
            assertThat(expressionGenerated[0])
                    .as("Expression should be generated for: " + expression)
                    .isTrue();
        } catch (Exception e) {
            throw new AssertionError("Expression generation failed for: " + expression, e);
        }
    }
}
