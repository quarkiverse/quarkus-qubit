package io.quarkiverse.qubit.deployment.generation.methodcall;

import static io.quarkiverse.qubit.deployment.generation.MethodDescriptors.CB_LIKE_STRING;
import static io.quarkiverse.qubit.deployment.generation.MethodDescriptors.CB_NOT;
import static io.quarkiverse.qubit.runtime.internal.QubitConstants.METHOD_LIKE;
import static io.quarkiverse.qubit.runtime.internal.QubitConstants.METHOD_NOT_LIKE;

import java.util.Optional;
import java.util.Set;

import io.quarkus.gizmo2.Expr;

/**
 * Handles {@code Qubit.like()} and {@code Qubit.notLike()} marker methods.
 *
 * <p>
 * Unlike {@link StringLikePatternHandler} which handles {@code startsWith}/{@code endsWith}/{@code contains}
 * and adds wildcards around the argument, this handler passes the LIKE pattern through unchanged,
 * allowing arbitrary patterns with {@code %} and {@code _} wildcards.
 *
 * <p>
 * Generates:
 * <ul>
 * <li>{@code like} → {@code cb.like(fieldExpr, pattern)}</li>
 * <li>{@code notLike} → {@code cb.not(cb.like(fieldExpr, pattern))}</li>
 * </ul>
 */
public enum QubitLikeHandler implements MethodCallHandler {
    INSTANCE;

    private static final Set<String> LIKE_METHODS = Set.of(METHOD_LIKE, METHOD_NOT_LIKE);

    @Override
    public HandlerPriority priority() {
        return HandlerPriority.FAST_REJECT;
    }

    @Override
    public Optional<Expr> handle(MethodCallDispatchContext context) {
        if (!LIKE_METHODS.contains(context.methodName())) {
            return Optional.empty();
        }
        if (!context.hasArguments()) {
            return Optional.empty();
        }

        // Target is the field expression (first arg of Qubit.like(field, pattern))
        Expr fieldExpression = context.generateTargetAsJpaExpression();

        // Pattern is the argument (second arg — the LIKE pattern string)
        // Use raw argument, NOT JPA expression — cb.like(Expression, String) takes a raw String
        Expr patternArg = context.generateArgument(context.firstArgument());

        // Generate: cb.like(fieldExpr, pattern)
        Expr likePredicate = context.bc().invokeInterface(CB_LIKE_STRING, context.cb(), fieldExpression, patternArg);

        // For notLike: wrap with cb.not()
        if (context.methodName().equals(METHOD_NOT_LIKE)) {
            likePredicate = context.bc().invokeInterface(CB_NOT, context.cb(), likePredicate);
        }

        return Optional.of(likePredicate);
    }
}
