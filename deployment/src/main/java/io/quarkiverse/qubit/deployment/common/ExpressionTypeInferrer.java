package io.quarkiverse.qubit.deployment.common;

import io.quarkiverse.qubit.deployment.ast.LambdaExpression;

/**
 * Utility class for inferring types from lambda expressions.
 *
 * <p>This class provides a centralized location for type inference logic
 * used across the bytecode analysis and code generation phases.
 *
 * <p>Extracted from multiple classes (ARCH-008 continuation) to eliminate
 * duplication and provide a single source of truth for type inference:
 * <ul>
 *   <li>{@code GroupMethodAnalyzer.inferFieldType()}</li>
 *   <li>{@code SubqueryAnalyzer.inferResultType()}</li>
 *   <li>{@code SubqueryExpressionBuilder.inferExpressionType()}</li>
 * </ul>
 *
 * @see LambdaExpression
 */
public final class ExpressionTypeInferrer {

    private ExpressionTypeInferrer() {
        // Utility class
    }

    /**
     * Infers the type of a field expression.
     *
     * <p>Handles:
     * <ul>
     *   <li>{@link LambdaExpression.FieldAccess} - returns {@code fieldType()}</li>
     *   <li>{@link LambdaExpression.PathExpression} - returns {@code resultType()}</li>
     *   <li>All other expressions - returns {@code Object.class}</li>
     * </ul>
     *
     * @param expression the expression to infer the type from
     * @return the inferred type, or {@code Object.class} if unknown
     */
    public static Class<?> inferFieldType(LambdaExpression expression) {
        if (expression == null) {
            return Object.class;
        }

        return switch (expression) {
            case LambdaExpression.FieldAccess field -> field.fieldType();
            case LambdaExpression.PathExpression path -> path.resultType();
            default -> Object.class;
        };
    }

    /**
     * Infers the result type for an expression, with a fallback default.
     *
     * <p>This is useful when a specific default type should be returned
     * if the expression type cannot be determined.
     *
     * @param expression the expression to infer the type from
     * @param defaultType the default type to return if inference fails
     * @return the inferred type, or {@code defaultType} if unknown
     */
    public static Class<?> inferFieldType(LambdaExpression expression, Class<?> defaultType) {
        if (expression == null) {
            return defaultType;
        }

        return switch (expression) {
            case LambdaExpression.FieldAccess field -> field.fieldType();
            case LambdaExpression.PathExpression path -> path.resultType();
            default -> defaultType;
        };
    }

    /**
     * Checks if the expression represents a numeric type.
     *
     * @param expression the expression to check
     * @return true if the expression type is numeric
     */
    public static boolean isNumericType(LambdaExpression expression) {
        Class<?> type = inferFieldType(expression);
        return isNumericClass(type);
    }

    /**
     * Checks if the class represents a numeric type.
     *
     * @param type the class to check
     * @return true if the class is a numeric type
     */
    public static boolean isNumericClass(Class<?> type) {
        return type == int.class || type == Integer.class ||
               type == long.class || type == Long.class ||
               type == double.class || type == Double.class ||
               type == float.class || type == Float.class ||
               type == short.class || type == Short.class ||
               type == byte.class || type == Byte.class ||
               Number.class.isAssignableFrom(type);
    }

    /**
     * Checks if the expression represents a comparable type.
     *
     * @param expression the expression to check
     * @return true if the expression type is comparable
     */
    public static boolean isComparableType(LambdaExpression expression) {
        Class<?> type = inferFieldType(expression);
        return Comparable.class.isAssignableFrom(type) || type.isPrimitive();
    }
}
