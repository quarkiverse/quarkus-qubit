package io.quarkiverse.qubit.deployment.generation.methodcall;

import static io.quarkiverse.qubit.runtime.internal.QubitConstants.TEMPORAL_COMPARISON_METHOD_NAMES;

import java.util.Optional;

import io.quarkus.gizmo2.Expr;

/**
 * Handles isBefore(), isAfter(), isEqual() via TemporalExpressionBuilder.
 */
public enum TemporalComparisonHandler implements MethodCallHandler {
    INSTANCE;

    @Override
    public HandlerPriority priority() {
        return HandlerPriority.FAST_REJECT;
    }

    @Override
    public Optional<Expr> handle(MethodCallDispatchContext context) {
        if (!context.isValidMethodWithArguments(TEMPORAL_COMPARISON_METHOD_NAMES)) {
            return Optional.empty();
        }

        Expr fieldExpression = context.generateTargetAsJpaExpression();
        Expr argument = context.generateArgument(context.firstArgument());

        return context.builderRegistry()
                .temporalBuilder()
                .buildTemporalComparison(
                        context.bc(),
                        context.methodCall(),
                        context.cb(),
                        fieldExpression,
                        argument)
                .toOptional();
    }
}
