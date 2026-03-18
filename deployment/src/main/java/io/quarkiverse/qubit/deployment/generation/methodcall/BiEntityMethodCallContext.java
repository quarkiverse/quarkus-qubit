package io.quarkiverse.qubit.deployment.generation.methodcall;

import java.util.Objects;

import io.quarkiverse.qubit.deployment.ast.LambdaExpression;
import io.quarkiverse.qubit.deployment.ast.LambdaExpression.BiEntityFieldAccess;
import io.quarkiverse.qubit.deployment.ast.LambdaExpression.BiEntityPathExpression;
import io.quarkiverse.qubit.deployment.ast.LambdaExpression.EntityPosition;
import io.quarkiverse.qubit.deployment.generation.expression.BiEntityBaseContext;
import io.quarkiverse.qubit.deployment.generation.expression.BiEntityExpressionBuilder;
import io.quarkiverse.qubit.deployment.generation.expression.ExpressionBuilderRegistry;
import io.quarkiverse.qubit.deployment.generation.expression.ExpressionGeneratorHelper;
import io.quarkus.gizmo2.Expr;
import io.quarkus.gizmo2.creator.BlockCreator;

/**
 * Context for method call handling in bi-entity (join) queries with two entity handles.
 */
public record BiEntityMethodCallContext(
        BlockCreator bc,
        LambdaExpression.MethodCall methodCall,
        Expr cb,
        Expr root,
        Expr join,
        Expr capturedValues,
        ExpressionBuilderRegistry builderRegistry,
        ExpressionGeneratorHelper helper) implements MethodCallDispatchContext {

    public BiEntityMethodCallContext {
        Objects.requireNonNull(bc, "bc cannot be null");
        Objects.requireNonNull(methodCall, "methodCall cannot be null");
        Objects.requireNonNull(cb, "cb cannot be null");
        Objects.requireNonNull(root, "root cannot be null");
        Objects.requireNonNull(join, "join cannot be null");
        Objects.requireNonNull(capturedValues, "capturedValues cannot be null");
        Objects.requireNonNull(builderRegistry, "builderRegistry cannot be null");
        Objects.requireNonNull(helper, "helper cannot be null");
    }

    @Override
    public Expr generateTargetAsJpaExpression() {
        var ctx = new BiEntityBaseContext(bc, cb, root, join, capturedValues, helper);
        return BiEntityExpressionBuilder.INSTANCE.generateBiEntityExpressionAsJpaExpression(ctx, methodCall.target());
    }

    @Override
    public Expr generateTarget() {
        var ctx = new BiEntityBaseContext(bc, cb, root, join, capturedValues, helper);
        return BiEntityExpressionBuilder.INSTANCE.generateBiEntityExpression(ctx, methodCall.target());
    }

    @Override
    public Expr generateArgumentAsJpaExpression(LambdaExpression expression) {
        var ctx = new BiEntityBaseContext(bc, cb, root, join, capturedValues, helper);
        return BiEntityExpressionBuilder.INSTANCE.generateBiEntityExpressionAsJpaExpression(ctx, expression);
    }

    @Override
    public Expr generateArgument(LambdaExpression expression) {
        var ctx = new BiEntityBaseContext(bc, cb, root, join, capturedValues, helper);
        return BiEntityExpressionBuilder.INSTANCE.generateBiEntityExpression(ctx, expression);
    }

    @Override
    public Expr generateFieldAccess(LambdaExpression.FieldAccess fieldAccess, Expr path) {
        return helper.generateFieldAccess(bc, fieldAccess, path);
    }

    @Override
    public Expr defaultRoot() {
        LambdaExpression target = methodCall.target();

        // Check for bi-entity field access
        if (target instanceof BiEntityFieldAccess biField) {
            return getBaseForEntityPosition(biField.entityPosition());
        }

        // Check for bi-entity path expression
        if (target instanceof BiEntityPathExpression biPath) {
            return getBaseForEntityPosition(biPath.entityPosition());
        }

        // Default to root for other expression types
        return root;
    }

    private Expr getBaseForEntityPosition(EntityPosition position) {
        return position == EntityPosition.FIRST ? root : join;
    }
}
