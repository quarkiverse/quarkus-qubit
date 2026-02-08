package io.quarkiverse.qubit.deployment.generation.methodcall;

import static io.quarkiverse.qubit.runtime.internal.QubitConstants.METHOD_SUBSTRING;

import io.quarkiverse.qubit.deployment.ast.LambdaExpression;
import io.quarkus.gizmo2.Expr;
import io.quarkus.gizmo2.LocalVar;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Handles substring(beginIndex) and substring(beginIndex, endIndex) via StringExpressionBuilder.
 */
public enum StringSubstringHandler implements MethodCallHandler {
    INSTANCE;

    @Override
    public HandlerPriority priority() {
        return HandlerPriority.FAST_REJECT;
    }

    @Override
    public Optional<Expr> handle(MethodCallDispatchContext context) {
        if (!context.isMethod(METHOD_SUBSTRING)) {
            return Optional.empty();
        }

        Expr fieldExpression = context.generateTargetAsJpaExpression();

        // Store each argument in a LocalVar to ensure proper bytecode ordering (Gizmo2 requirement)
        List<Expr> arguments = new ArrayList<>();
        int argIndex = 0;
        for (LambdaExpression arg : context.methodCall().arguments()) {
            LocalVar argVar = context.bc().localVar("substringArg" + argIndex,
                    context.generateArgumentAsJpaExpression(arg));
            arguments.add(argVar);
            argIndex++;
        }

        return context.builderRegistry()
                .stringBuilder()
                .buildStringSubstring(
                        context.bc(),
                        context.methodCall(),
                        context.cb(),
                        fieldExpression,
                        arguments)
                .toOptional();
    }
}
