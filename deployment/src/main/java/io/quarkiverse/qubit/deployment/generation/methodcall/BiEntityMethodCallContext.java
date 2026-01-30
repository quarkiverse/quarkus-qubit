package io.quarkiverse.qubit.deployment.generation.methodcall;

import io.quarkiverse.qubit.deployment.ast.LambdaExpression;
import io.quarkiverse.qubit.deployment.ast.LambdaExpression.BiEntityFieldAccess;
import io.quarkiverse.qubit.deployment.ast.LambdaExpression.BiEntityPathExpression;
import io.quarkiverse.qubit.deployment.ast.LambdaExpression.EntityPosition;
import io.quarkiverse.qubit.deployment.generation.expression.BiEntityBaseContext;
import io.quarkiverse.qubit.deployment.generation.expression.BiEntityExpressionBuilder;
import io.quarkiverse.qubit.deployment.generation.expression.ExpressionBuilderRegistry;
import io.quarkiverse.qubit.deployment.generation.expression.ExpressionGeneratorHelper;
import io.quarkus.gizmo.MethodCreator;
import io.quarkus.gizmo.ResultHandle;

import java.util.Objects;

/** Context for method call handling in bi-entity (join) queries with two entity handles. */
public record BiEntityMethodCallContext(
        MethodCreator method,
        LambdaExpression.MethodCall methodCall,
        ResultHandle cb,
        ResultHandle root,
        ResultHandle join,
        ResultHandle capturedValues,
        ExpressionBuilderRegistry builderRegistry,
        ExpressionGeneratorHelper helper
) implements MethodCallDispatchContext {

    public BiEntityMethodCallContext {
        Objects.requireNonNull(method, "method cannot be null");
        Objects.requireNonNull(methodCall, "methodCall cannot be null");
        Objects.requireNonNull(cb, "cb cannot be null");
        Objects.requireNonNull(root, "root cannot be null");
        Objects.requireNonNull(join, "join cannot be null");
        // capturedValues can be null for queries without captured variables
        Objects.requireNonNull(builderRegistry, "builderRegistry cannot be null");
        Objects.requireNonNull(helper, "helper cannot be null");
    }

    // ========== MethodCallDispatchContext Implementation ==========

    @Override
    public ResultHandle generateTargetAsJpaExpression() {
        var ctx = new BiEntityBaseContext(method, cb, root, join, capturedValues, helper);
        return BiEntityExpressionBuilder.INSTANCE.generateBiEntityExpressionAsJpaExpression(ctx, methodCall.target());
    }

    @Override
    public ResultHandle generateTarget() {
        var ctx = new BiEntityBaseContext(method, cb, root, join, capturedValues, helper);
        return BiEntityExpressionBuilder.INSTANCE.generateBiEntityExpression(ctx, methodCall.target());
    }

    @Override
    public ResultHandle generateArgumentAsJpaExpression(LambdaExpression expression) {
        var ctx = new BiEntityBaseContext(method, cb, root, join, capturedValues, helper);
        return BiEntityExpressionBuilder.INSTANCE.generateBiEntityExpressionAsJpaExpression(ctx, expression);
    }

    @Override
    public ResultHandle generateArgument(LambdaExpression expression) {
        var ctx = new BiEntityBaseContext(method, cb, root, join, capturedValues, helper);
        return BiEntityExpressionBuilder.INSTANCE.generateBiEntityExpression(ctx, expression);
    }

    @Override
    public ResultHandle generateFieldAccess(LambdaExpression.FieldAccess fieldAccess, ResultHandle path) {
        return helper.generateFieldAccess(method, fieldAccess, path);
    }

    @Override
    public ResultHandle defaultRoot() {
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

    private ResultHandle getBaseForEntityPosition(EntityPosition position) {
        return position == EntityPosition.FIRST ? root : join;
    }
}
