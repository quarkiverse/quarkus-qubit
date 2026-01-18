package io.quarkiverse.qubit.deployment.generation.methodcall;

import io.quarkus.gizmo.MethodCreator;
import io.quarkus.gizmo.ResultHandle;
import io.quarkiverse.qubit.deployment.ast.LambdaExpression;
import io.quarkiverse.qubit.deployment.generation.expression.ExpressionBuilderRegistry;
import io.quarkiverse.qubit.deployment.generation.expression.ExpressionGeneratorHelper;

import java.util.Objects;

/**
 * Context for method call handling in single-entity queries.
 * Implements {@link MethodCallDispatchContext} for handler reuse across query types.
 *
 * @see BiEntityMethodCallContext
 */
public record MethodCallContext(
        MethodCreator method,
        LambdaExpression.MethodCall methodCall,
        ResultHandle cb,
        ResultHandle root,
        ResultHandle capturedValues,
        ExpressionBuilderRegistry builderRegistry,
        ExpressionGeneratorHelper helper
) implements MethodCallDispatchContext {

    public MethodCallContext {
        Objects.requireNonNull(method, "method cannot be null");
        Objects.requireNonNull(methodCall, "methodCall cannot be null");
        Objects.requireNonNull(cb, "cb cannot be null");
        Objects.requireNonNull(root, "root cannot be null");
        // capturedValues can be null for queries without captured variables
        Objects.requireNonNull(builderRegistry, "builderRegistry cannot be null");
        Objects.requireNonNull(helper, "helper cannot be null");
    }

    /** Returns the method name from the method call. */
    public String methodName() {
        return methodCall.methodName();
    }

    /** Generates a JPA Expression from the method call's target. */
    public ResultHandle generateTargetAsJpaExpression() {
        return helper.generateExpressionAsJpaExpression(
                method, methodCall.target(), cb, root, capturedValues);
    }

    /** Generates the method call's target, returning raw values where appropriate. */
    public ResultHandle generateTarget() {
        return helper.generateExpression(
                method, methodCall.target(), cb, root, capturedValues);
    }

    /** Generates a JPA Expression from a lambda expression argument. */
    public ResultHandle generateArgumentAsJpaExpression(LambdaExpression expression) {
        return helper.generateExpressionAsJpaExpression(
                method, expression, cb, root, capturedValues);
    }

    /** Generates an argument expression, returning raw values where appropriate. */
    public ResultHandle generateArgument(LambdaExpression expression) {
        return helper.generateExpression(
                method, expression, cb, root, capturedValues);
    }

    /** Returns true if the method call has arguments. */
    public boolean hasArguments() {
        return !methodCall.arguments().isEmpty();
    }

    @Override
    public LambdaExpression firstArgument() {
        return hasArguments() ? methodCall.arguments().get(0) : null;
    }

    @Override
    public ResultHandle generateFieldAccess(LambdaExpression.FieldAccess fieldAccess, ResultHandle path) {
        return helper.generateFieldAccess(method, fieldAccess, path);
    }

    @Override
    public ResultHandle defaultRoot() {
        return root;
    }
}
