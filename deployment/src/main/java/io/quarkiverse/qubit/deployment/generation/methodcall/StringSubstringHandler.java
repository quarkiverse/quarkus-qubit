package io.quarkiverse.qubit.deployment.generation.methodcall;

import static io.quarkiverse.qubit.runtime.QubitConstants.METHOD_SUBSTRING;

import io.quarkiverse.qubit.deployment.ast.LambdaExpression;
import io.quarkus.gizmo.ResultHandle;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Handles substring(beginIndex) and substring(beginIndex, endIndex) via StringExpressionBuilder. */
public enum StringSubstringHandler implements MethodCallHandler {
    INSTANCE;

    @Override
    public HandlerPriority priority() {
        return HandlerPriority.FAST_REJECT;
    }

    @Override
    public Optional<ResultHandle> handle(MethodCallDispatchContext context) {
        if (!context.isMethod(METHOD_SUBSTRING)) {
            return Optional.empty();
        }

        ResultHandle fieldExpression = context.generateTargetAsJpaExpression();

        List<ResultHandle> arguments = new ArrayList<>();
        for (LambdaExpression arg : context.methodCall().arguments()) {
            arguments.add(context.generateArgumentAsJpaExpression(arg));
        }

        return context.builderRegistry()
                .stringBuilder()
                .buildStringSubstring(
                        context.method(),
                        context.methodCall(),
                        context.cb(),
                        fieldExpression,
                        arguments)
                .toOptional();
    }
}
