package io.quarkiverse.qubit.deployment.generation.methodcall;

import static io.quarkiverse.qubit.runtime.internal.QubitConstants.METHOD_EQUALS;

import io.quarkus.gizmo2.Expr;

import java.util.Optional;

/**
 * Handles equals/isEmpty/isBlank/length via StringExpressionBuilder.
 *
 * <p>Uses Gizmo 2 API with Expr type.
 */
public enum StringUtilityHandler implements MethodCallHandler {
    INSTANCE;

    @Override
    public HandlerPriority priority() {
        return HandlerPriority.DELEGATING;
    }

    @Override
    public Optional<Expr> handle(MethodCallDispatchContext context) {
        Expr fieldExpression = context.generateTargetAsJpaExpression();

        // Special handling for equals() - needs raw argument, not JPA expression
        Expr argument = null;
        if (METHOD_EQUALS.equals(context.methodName()) && context.hasArguments()) {
            argument = context.generateArgument(context.firstArgument());
        }

        return context.builderRegistry()
                .stringBuilder()
                .buildStringUtility(
                        context.bc(),
                        context.methodCall(),
                        context.cb(),
                        fieldExpression,
                        argument)
                .toOptional();
    }
}
