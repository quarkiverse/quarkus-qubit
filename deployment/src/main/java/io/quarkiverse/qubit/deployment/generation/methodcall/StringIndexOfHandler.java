package io.quarkiverse.qubit.deployment.generation.methodcall;

import static io.quarkiverse.qubit.runtime.internal.QubitConstants.METHOD_INDEX_OF;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import io.quarkiverse.qubit.deployment.ast.LambdaExpression;
import io.quarkus.gizmo2.Expr;
import io.quarkus.gizmo2.LocalVar;

/**
 * Handles indexOf(String) and indexOf(String, int) via StringExpressionBuilder.
 * Maps to JPA LOCATE with 0-based to 1-based offset conversion.
 */
public enum StringIndexOfHandler implements MethodCallHandler {
    INSTANCE;

    @Override
    public HandlerPriority priority() {
        return HandlerPriority.FAST_REJECT;
    }

    @Override
    public Optional<Expr> handle(MethodCallDispatchContext context) {
        if (!context.isMethod(METHOD_INDEX_OF)) {
            return Optional.empty();
        }

        Expr fieldExpression = context.generateTargetAsJpaExpression();

        // Store each argument in a LocalVar to ensure proper bytecode ordering (Gizmo2 requirement)
        List<Expr> arguments = new ArrayList<>();
        int argIndex = 0;
        for (LambdaExpression arg : context.methodCall().arguments()) {
            LocalVar argVar = context.bc().localVar("indexOfArg" + argIndex,
                    context.generateArgumentAsJpaExpression(arg));
            arguments.add(argVar);
            argIndex++;
        }

        return context.builderRegistry()
                .stringBuilder()
                .buildStringIndexOf(
                        context.bc(),
                        context.methodCall(),
                        context.cb(),
                        fieldExpression,
                        arguments)
                .toOptional();
    }
}
