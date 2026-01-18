package io.quarkiverse.qubit.deployment.generation.methodcall;

import io.quarkus.gizmo.ResultHandle;

import java.util.Optional;

/**
 * Handles getYear/getMonth/getDayOfMonth/getHour/etc. via TemporalExpressionBuilder.
 */
public enum TemporalAccessorHandler implements MethodCallHandler {
    INSTANCE;

    @Override
    public HandlerPriority priority() {
        return HandlerPriority.DELEGATING;
    }

    @Override
    public Optional<ResultHandle> handle(MethodCallDispatchContext context) {
        ResultHandle fieldExpression = context.generateTargetAsJpaExpression();
        return context.builderRegistry()
                .temporalBuilder()
                .buildTemporalAccessorFunction(
                        context.method(),
                        context.methodCall(),
                        context.cb(),
                        fieldExpression)
                .toOptional();
    }
}
