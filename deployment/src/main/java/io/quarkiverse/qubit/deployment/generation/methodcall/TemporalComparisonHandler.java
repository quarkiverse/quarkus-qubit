package io.quarkiverse.qubit.deployment.generation.methodcall;

import static io.quarkiverse.qubit.runtime.QubitConstants.TEMPORAL_COMPARISON_METHOD_NAMES;

import io.quarkus.gizmo.ResultHandle;

import java.util.Optional;

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
    public Optional<ResultHandle> handle(MethodCallDispatchContext context) {
        if (!context.isValidMethodWithArguments(TEMPORAL_COMPARISON_METHOD_NAMES)) {
            return Optional.empty();
        }

        ResultHandle fieldExpression = context.generateTargetAsJpaExpression();
        ResultHandle argument = context.generateArgument(context.firstArgument());

        return context.builderRegistry()
                .temporalBuilder()
                .buildTemporalComparison(
                        context.method(),
                        context.methodCall(),
                        context.cb(),
                        fieldExpression,
                        argument)
                .toOptional();
    }
}
