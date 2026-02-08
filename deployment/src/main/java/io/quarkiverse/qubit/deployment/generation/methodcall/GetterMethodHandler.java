package io.quarkiverse.qubit.deployment.generation.methodcall;

import static io.quarkiverse.qubit.deployment.common.ExpressionTypeInferrer.extractFieldName;
import static io.quarkiverse.qubit.deployment.common.ExpressionTypeInferrer.isGetterMethodName;

import io.quarkiverse.qubit.deployment.ast.LambdaExpression;
import io.quarkus.gizmo2.Expr;

import java.util.Optional;

/**
 * Fallback handler: converts getter calls (getXxx, isXxx) to field access.
 * getName() → root.get("name"), isActive() → root.get("active")
 */
public enum GetterMethodHandler implements MethodCallHandler {
    INSTANCE;

    @Override
    public HandlerPriority priority() {
        return HandlerPriority.FALLBACK;
    }

    @Override
    public Optional<Expr> handle(MethodCallDispatchContext context) {
        String methodName = context.methodName();

        if (!isGetterMethodName(methodName)) {
            return Optional.empty();
        }

        String fieldName = extractFieldName(methodName);

        LambdaExpression.FieldAccess fieldAccess = new LambdaExpression.FieldAccess(
                fieldName,
                context.methodCall().returnType());

        // Use polymorphic interface methods (not context.helper()/root())
        Expr result = context.generateFieldAccess(fieldAccess, context.defaultRoot());

        return Optional.of(result);
    }
}
