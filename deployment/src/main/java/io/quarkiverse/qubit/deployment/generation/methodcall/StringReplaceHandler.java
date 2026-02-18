package io.quarkiverse.qubit.deployment.generation.methodcall;

import static io.quarkiverse.qubit.runtime.internal.QubitConstants.METHOD_REPLACE;

import java.util.Optional;

import io.quarkiverse.qubit.deployment.ast.LambdaExpression;
import io.quarkus.gizmo2.Expr;
import io.quarkus.gizmo2.LocalVar;

/**
 * Handles String.replace() -> cb.replace() (JPA 3.2).
 * Maps {@code p.name.replace("old", "new")} to {@code cb.replace(root.get("name"), "old", "new")}.
 */
public enum StringReplaceHandler implements MethodCallHandler {
    INSTANCE;

    @Override
    public HandlerPriority priority() {
        return HandlerPriority.FAST_REJECT;
    }

    @Override
    public Optional<Expr> handle(MethodCallDispatchContext context) {
        if (!context.isMethod(METHOD_REPLACE)) {
            return Optional.empty();
        }
        if (context.methodCall().arguments().size() != 2) {
            return Optional.empty();
        }

        Expr fieldExpression = context.generateTargetAsJpaExpression();

        // Both arguments are raw String values (not JPA Expressions)
        // cb.replace(Expression<String>, String, String)
        LambdaExpression targetArg = context.methodCall().arguments().get(0);
        LambdaExpression replacementArg = context.methodCall().arguments().get(1);

        LocalVar targetVal = context.bc().localVar("replaceTarget",
                context.generateArgument(targetArg));
        LocalVar replacementVal = context.bc().localVar("replaceReplacement",
                context.generateArgument(replacementArg));

        return context.builderRegistry()
                .stringBuilder()
                .buildStringReplace(
                        context.bc(),
                        context.cb(),
                        fieldExpression,
                        targetVal,
                        replacementVal)
                .toOptional();
    }
}
