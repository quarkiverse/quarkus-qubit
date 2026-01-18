package io.quarkiverse.qubit.deployment.generation.methodcall;

import io.quarkus.gizmo.ResultHandle;

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
    public Optional<ResultHandle> handle(MethodCallDispatchContext context) {
        ResultHandle fieldExpression = context.generateTargetAsJpaExpression();
        return context.builderRegistry()
                .stringBuilder()
                .buildStringTransformation(
                        context.method(),
                        context.methodCall(),
                        context.cb(),
                        fieldExpression)
                .toOptional();
    }
}
