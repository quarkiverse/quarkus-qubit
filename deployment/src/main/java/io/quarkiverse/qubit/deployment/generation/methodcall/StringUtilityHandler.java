package io.quarkiverse.qubit.deployment.generation.methodcall;

import static io.quarkiverse.qubit.runtime.internal.QubitConstants.METHOD_EQUALS;

import io.quarkus.gizmo.ResultHandle;

import java.util.Optional;

/**
 * Handles equals/isEmpty/isBlank/length via StringExpressionBuilder.
 */
public enum StringUtilityHandler implements MethodCallHandler {
    INSTANCE;

    @Override
    public HandlerPriority priority() {
        return HandlerPriority.DELEGATING;
    }

    @Override
    public Optional<ResultHandle> handle(MethodCallDispatchContext context) {
        ResultHandle fieldExpression = context.generateTargetAsJpaExpression();

        // Special handling for equals() - needs raw argument, not JPA expression
        ResultHandle argument = null;
        if (METHOD_EQUALS.equals(context.methodName()) && context.hasArguments()) {
            argument = context.generateArgument(context.firstArgument());
        }

        return context.builderRegistry()
                .stringBuilder()
                .buildStringUtility(
                        context.method(),
                        context.methodCall(),
                        context.cb(),
                        fieldExpression,
                        argument)
                .toOptional();
    }
}
