package io.quarkiverse.qubit.deployment.generation;

import org.jspecify.annotations.Nullable;

import io.quarkiverse.qubit.deployment.ast.LambdaExpression;

/** Thrown when encountering an unsupported expression during JPA code generation. */
public class UnsupportedExpressionException extends RuntimeException {

    private final String expressionType;
    private final @Nullable String context;

    public UnsupportedExpressionException(LambdaExpression expression) {
        super(formatMessage(expression, null));
        this.expressionType = expression.getClass().getSimpleName();
        this.context = null;
    }

    public UnsupportedExpressionException(LambdaExpression expression, String context) {
        super(formatMessage(expression, context));
        this.expressionType = expression.getClass().getSimpleName();
        this.context = context;
    }

    public UnsupportedExpressionException(String message) {
        super(message);
        this.expressionType = "unknown";
        this.context = null;
    }

    public String getExpressionType() {
        return expressionType;
    }

    public @Nullable String getContext() {
        return context;
    }

    private static String formatMessage(LambdaExpression expression, @Nullable String context) {
        StringBuilder sb = new StringBuilder();
        sb.append("Unsupported expression type in JPA query generation: ");
        sb.append(expression.getClass().getSimpleName());
        sb.append("\nExpression details: ").append(formatExpression(expression));

        if (context != null) {
            sb.append("\nContext: ").append(context);
        }

        sb.append("\n\nThis lambda pattern cannot be converted to a JPA Criteria query. ");
        sb.append("Consider simplifying the expression or using a supported pattern.");

        return sb.toString();
    }

    private static String formatExpression(LambdaExpression expression) {
        return switch (expression) {
            case LambdaExpression.FieldAccess f -> "field access: " + f.fieldName();
            case LambdaExpression.MethodCall m -> "method call: " + m.methodName() + "()";
            case LambdaExpression.BinaryOp b -> "binary operation: " + b.operator();
            case LambdaExpression.UnaryOp u -> "unary operation: " + u.operator();
            case LambdaExpression.Constant c -> "constant: " + c.value();
            case LambdaExpression.CapturedVariable cv -> "captured variable at index " + cv.index();
            case LambdaExpression.Parameter p -> "lambda parameter: " + p.name();
            case LambdaExpression.ConstructorCall cc -> "constructor: new " + cc.className() + "(...)";
            default -> expression.toString();
        };
    }

    public static UnsupportedExpressionException inConstructorArgument(
            LambdaExpression expression, int argIndex, String constructorClass) {
        return new UnsupportedExpressionException(expression,
                String.format("constructor argument at position %d for %s", argIndex, constructorClass));
    }
}
