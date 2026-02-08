package io.quarkiverse.qubit.deployment.generation.methodcall;

import io.quarkus.gizmo2.Expr;

import java.util.Optional;

/**
 * Handles toUpperCase/toLowerCase/trim/length via StringExpressionBuilder.
 */
public enum StringTransformationHandler implements MethodCallHandler {
    INSTANCE;

    @Override
    public HandlerPriority priority() {
        return HandlerPriority.DELEGATING;
    }

    @Override
    public Optional<Expr> handle(MethodCallDispatchContext context) {
        Expr fieldExpression = context.generateTargetAsJpaExpression();
        return context.builderRegistry()
                .stringBuilder()
                .buildStringTransformation(
                        context.bc(),
                        context.methodCall(),
                        context.cb(),
                        fieldExpression)
                .toOptional();
    }
}
