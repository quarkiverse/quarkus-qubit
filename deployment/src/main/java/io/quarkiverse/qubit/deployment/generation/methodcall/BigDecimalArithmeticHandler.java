package io.quarkiverse.qubit.deployment.generation.methodcall;

import static io.quarkiverse.qubit.runtime.internal.QubitConstants.BIG_DECIMAL_ARITHMETIC_METHODS;

import io.quarkiverse.qubit.deployment.generation.expression.BuilderResult;
import io.quarkus.gizmo.ResultHandle;

import java.util.Optional;

/** Handles BigDecimal add/subtract/multiply/divide via BigDecimalExpressionBuilder. */
public enum BigDecimalArithmeticHandler implements MethodCallHandler {
    INSTANCE;

    @Override
    public HandlerPriority priority() {
        return HandlerPriority.FAST_REJECT;
    }

    @Override
    public Optional<ResultHandle> handle(MethodCallDispatchContext context) {
        if (!context.isValidMethodWithArguments(BIG_DECIMAL_ARITHMETIC_METHODS)) {
            return Optional.empty();
        }

        ResultHandle fieldExpression = context.generateTargetAsJpaExpression();
        ResultHandle argument = context.generateArgumentAsJpaExpression(context.firstArgument());

        BuilderResult result = context.builderRegistry()
                .bigDecimalBuilder()
                .buildBigDecimalArithmetic(
                        context.method(),
                        context.methodCall(),
                        context.cb(),
                        fieldExpression,
                        argument,
                        context.builderRegistry().arithmeticBuilder());

        return result.toOptional();
    }
}
