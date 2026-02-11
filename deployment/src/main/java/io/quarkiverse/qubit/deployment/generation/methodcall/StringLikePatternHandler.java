package io.quarkiverse.qubit.deployment.generation.methodcall;

import static io.quarkiverse.qubit.runtime.internal.QubitConstants.STRING_PATTERN_METHOD_NAMES;

import java.util.Optional;

import io.quarkus.gizmo2.Expr;

/**
 * Handles startsWith/endsWith/contains as LIKE patterns via StringExpressionBuilder.
 */
public enum StringLikePatternHandler implements MethodCallHandler {
    INSTANCE;

    @Override
    public HandlerPriority priority() {
        return HandlerPriority.FAST_REJECT;
    }

    @Override
    public Optional<Expr> handle(MethodCallDispatchContext context) {
        if (!context.isValidMethodWithArguments(STRING_PATTERN_METHOD_NAMES)) {
            return Optional.empty();
        }

        // Use generateTarget (not AsJpaExpression) - LIKE patterns need raw values
        Expr fieldExpression = context.generateTarget();
        Expr argument = context.generateArgument(context.firstArgument());

        return context.builderRegistry()
                .stringBuilder()
                .buildStringPattern(
                        context.bc(),
                        context.methodCall(),
                        context.cb(),
                        fieldExpression,
                        argument)
                .toOptional();
    }
}
