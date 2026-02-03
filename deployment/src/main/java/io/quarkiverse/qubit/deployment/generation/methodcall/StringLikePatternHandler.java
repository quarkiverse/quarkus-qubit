package io.quarkiverse.qubit.deployment.generation.methodcall;

import static io.quarkiverse.qubit.runtime.internal.QubitConstants.STRING_PATTERN_METHOD_NAMES;

import io.quarkus.gizmo2.Expr;

import java.util.Optional;

/**
 * Handles startsWith/endsWith/contains as LIKE patterns via StringExpressionBuilder.
 *
 * <p>Uses Gizmo 2 API with Expr type.
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
