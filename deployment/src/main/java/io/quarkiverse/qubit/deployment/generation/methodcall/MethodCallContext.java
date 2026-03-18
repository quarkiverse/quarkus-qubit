package io.quarkiverse.qubit.deployment.generation.methodcall;

import java.util.Objects;

import io.quarkiverse.qubit.deployment.ast.LambdaExpression;
import io.quarkiverse.qubit.deployment.generation.expression.ExpressionBuilderRegistry;
import io.quarkiverse.qubit.deployment.generation.expression.ExpressionGeneratorHelper;
import io.quarkus.gizmo2.Expr;
import io.quarkus.gizmo2.creator.BlockCreator;

/**
 * Context for method call handling in single-entity queries.
 * Implements {@link MethodCallDispatchContext} for handler reuse across query types.
 *
 * @see BiEntityMethodCallContext
 */
public record MethodCallContext(
        BlockCreator bc,
        LambdaExpression.MethodCall methodCall,
        Expr cb,
        Expr root,
        Expr capturedValues,
        ExpressionBuilderRegistry builderRegistry,
        ExpressionGeneratorHelper helper) implements MethodCallDispatchContext {

    public MethodCallContext {
        Objects.requireNonNull(bc, "bc cannot be null");
        Objects.requireNonNull(methodCall, "methodCall cannot be null");
        Objects.requireNonNull(cb, "cb cannot be null");
        Objects.requireNonNull(root, "root cannot be null");
        Objects.requireNonNull(capturedValues, "capturedValues cannot be null");
        Objects.requireNonNull(builderRegistry, "builderRegistry cannot be null");
        Objects.requireNonNull(helper, "helper cannot be null");
    }

    /** Generates a JPA Expression from the method call's target. */
    public Expr generateTargetAsJpaExpression() {
        return helper.generateExpressionAsJpaExpression(
                bc, methodCall.target(), cb, root, capturedValues);
    }

    /** Generates the method call's target, returning raw values where appropriate. */
    public Expr generateTarget() {
        return helper.generateExpression(
                bc, methodCall.target(), cb, root, capturedValues);
    }

    /** Generates a JPA Expression from a lambda expression argument. */
    public Expr generateArgumentAsJpaExpression(LambdaExpression expression) {
        return helper.generateExpressionAsJpaExpression(
                bc, expression, cb, root, capturedValues);
    }

    /** Generates an argument expression, returning raw values where appropriate. */
    public Expr generateArgument(LambdaExpression expression) {
        return helper.generateExpression(
                bc, expression, cb, root, capturedValues);
    }

    @Override
    public Expr generateFieldAccess(LambdaExpression.FieldAccess fieldAccess, Expr path) {
        return helper.generateFieldAccess(bc, fieldAccess, path);
    }

    @Override
    public Expr defaultRoot() {
        return root;
    }
}
