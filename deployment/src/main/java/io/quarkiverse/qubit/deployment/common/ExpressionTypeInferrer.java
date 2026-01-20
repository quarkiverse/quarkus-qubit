package io.quarkiverse.qubit.deployment.common;

import static io.quarkiverse.qubit.runtime.internal.QubitConstants.PREFIX_GET;
import static io.quarkiverse.qubit.runtime.internal.QubitConstants.PREFIX_IS;

import io.quarkiverse.qubit.deployment.ast.LambdaExpression;
import io.quarkiverse.qubit.deployment.util.TypeConverter;

/** Infers types from lambda expressions (FieldAccess, PathExpression). */
public final class ExpressionTypeInferrer {

    private ExpressionTypeInferrer() {
        // Utility class
    }

    /** Infers type from FieldAccess or PathExpression; returns Object.class otherwise. */
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

    /** Infers type with fallback default. */
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

    /** Checks if expression is numeric. */
    public static boolean isNumericType(LambdaExpression expression) {
        Class<?> type = inferFieldType(expression);
        return TypeConverter.isNumericType(type);
    }

    /** Checks if class is numeric. */
    public static boolean isNumericClass(Class<?> type) {
        return TypeConverter.isNumericType(type);
    }

    /** Checks if expression is comparable. */
    public static boolean isComparableType(LambdaExpression expression) {
        Class<?> type = inferFieldType(expression);
        return Comparable.class.isAssignableFrom(type) || type.isPrimitive();
    }

    /** Checks if class is boolean or Boolean. */
    public static boolean isBooleanType(Class<?> type) {
        return TypeConverter.isBooleanType(type);
    }

    /** Checks if name follows JavaBean getter conventions (getXxx or isXxx). */
    public static boolean isGetterMethodName(String methodName) {
        if (methodName == null) {
            return false;
        }
        return (methodName.startsWith(PREFIX_GET) && methodName.length() > PREFIX_GET.length()) ||
               (methodName.startsWith(PREFIX_IS) && methodName.length() > PREFIX_IS.length());
    }

    /** Extracts field name from getter (getAge→age, isActive→active). */
    public static String extractFieldName(String methodName) {
        if (methodName == null) {
            return null;
        }
        if (methodName.startsWith(PREFIX_GET) && methodName.length() > PREFIX_GET.length()) {
            String fieldName = methodName.substring(PREFIX_GET.length());
            return Character.toLowerCase(fieldName.charAt(0)) + fieldName.substring(1);
        }
        if (methodName.startsWith(PREFIX_IS) && methodName.length() > PREFIX_IS.length()) {
            String fieldName = methodName.substring(PREFIX_IS.length());
            return Character.toLowerCase(fieldName.charAt(0)) + fieldName.substring(1);
        }
        return methodName;
    }
}
