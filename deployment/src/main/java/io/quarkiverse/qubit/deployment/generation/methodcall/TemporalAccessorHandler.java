package io.quarkiverse.qubit.deployment.generation.methodcall;

import io.quarkus.gizmo2.Expr;

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
    public Optional<Expr> handle(MethodCallDispatchContext context) {
        Expr fieldExpression = context.generateTargetAsJpaExpression();
        return context.builderRegistry()
                .temporalBuilder()
                .buildTemporalAccessorFunction(
                        context.bc(),
                        context.methodCall(),
                        context.cb(),
                        fieldExpression)
                .toOptional();
    }
}
