package io.quarkiverse.qubit.deployment.generation.methodcall;

import static io.quarkiverse.qubit.deployment.generation.MethodDescriptors.CB_LEFT;
import static io.quarkiverse.qubit.deployment.generation.MethodDescriptors.CB_LEFT_EXPR;
import static io.quarkiverse.qubit.deployment.generation.MethodDescriptors.CB_RIGHT;
import static io.quarkiverse.qubit.deployment.generation.MethodDescriptors.CB_RIGHT_EXPR;
import static io.quarkiverse.qubit.runtime.internal.QubitConstants.METHOD_LEFT;
import static io.quarkiverse.qubit.runtime.internal.QubitConstants.METHOD_RIGHT;

import java.util.Optional;
import java.util.Set;

import io.quarkiverse.qubit.deployment.ast.LambdaExpression;
import io.quarkus.gizmo2.Expr;
import io.quarkus.gizmo2.desc.MethodDesc;

/**
 * Handles {@code Qubit.left()} and {@code Qubit.right()} marker methods.
 *
 * <p>
 * Generates:
 * <ul>
 * <li>{@code left} → {@code cb.left(fieldExpr, length)} or {@code cb.left(fieldExpr, lengthExpr)}</li>
 * <li>{@code right} → {@code cb.right(fieldExpr, length)} or {@code cb.right(fieldExpr, lengthExpr)}</li>
 * </ul>
 *
 * <p>
 * When the length argument is a constant integer, uses the {@code cb.left(Expression, int)} overload.
 * When the length is a captured variable or expression, uses the {@code cb.left(Expression, Expression)} overload.
 */
public enum QubitLeftRightHandler implements MethodCallHandler {
    INSTANCE;

    private static final Set<String> LEFT_RIGHT_METHODS = Set.of(METHOD_LEFT, METHOD_RIGHT);

    @Override
    public HandlerPriority priority() {
        return HandlerPriority.FAST_REJECT;
    }

    @Override
    public Optional<Expr> handle(MethodCallDispatchContext context) {
        if (!LEFT_RIGHT_METHODS.contains(context.methodName())) {
            return Optional.empty();
        }
        if (!context.hasArguments()) {
            return Optional.empty();
        }

        // Target is the field expression (first arg of Qubit.left(field, length))
        Expr fieldExpression = context.generateTargetAsJpaExpression();

        // Length is the argument (second arg)
        LambdaExpression lengthArg = context.firstArgument();

        // Select the correct overload based on the argument type
        boolean isConstantInt = lengthArg instanceof LambdaExpression.Constant;
        boolean isLeft = context.methodName().equals(METHOD_LEFT);

        if (isConstantInt) {
            // Use cb.left(Expression, int) / cb.right(Expression, int)
            Expr lengthValue = context.generateArgument(lengthArg);
            MethodDesc md = isLeft ? CB_LEFT : CB_RIGHT;
            return Optional.of(context.bc().invokeInterface(md, context.cb(), fieldExpression, lengthValue));
        } else {
            // Use cb.left(Expression, Expression) / cb.right(Expression, Expression)
            Expr lengthExpr = context.generateArgumentAsJpaExpression(lengthArg);
            MethodDesc md = isLeft ? CB_LEFT_EXPR : CB_RIGHT_EXPR;
            return Optional.of(context.bc().invokeInterface(md, context.cb(), fieldExpression, lengthExpr));
        }
    }
}
