package io.quarkiverse.qubit.deployment.util;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * Converts between JVM type descriptors and Java Class objects.
 */
public final class TypeConverter {

    private TypeConverter() {
    }

    /**
     * Converts primitive type descriptor character to Class.
     */
    public static Class<?> primitiveCharToClass(char typeChar) {
        return switch (typeChar) {
            case 'Z' -> boolean.class;
            case 'B' -> byte.class;
            case 'C' -> char.class;
            case 'S' -> short.class;
            case 'I' -> int.class;
            case 'J' -> long.class;
            case 'F' -> float.class;
            case 'D' -> double.class;
            default -> Object.class;
        };
    }

    /**
     * Converts type descriptor string to Class.
     */
    public static Class<?> descriptorToClass(String descriptor) {
        if (descriptor.length() == 1) {
            return primitiveCharToClass(descriptor.charAt(0));
        }

        return switch (descriptor) {
            case "Ljava/lang/String;" -> String.class;
            case "Ljava/lang/Integer;" -> Integer.class;
            case "Ljava/lang/Long;" -> Long.class;
            case "Ljava/lang/Boolean;" -> Boolean.class;
            case "Ljava/lang/Double;" -> Double.class;
            case "Ljava/lang/Float;" -> Float.class;
            case "Ljava/math/BigDecimal;" -> BigDecimal.class;
            case "Ljava/time/LocalDate;" -> LocalDate.class;
            case "Ljava/time/LocalDateTime;" -> LocalDateTime.class;
            case "Ljava/time/LocalTime;" -> LocalTime.class;
            default -> Object.class;
        };
    }

    /**
     * Returns boxed type for primitive type.
     */
    public static Class<?> getBoxedType(Class<?> type) {
        if (type == int.class) {
            return Integer.class;
        } else if (type == long.class) {
            return Long.class;
        } else if (type == double.class) {
            return Double.class;
        } else if (type == float.class) {
            return Float.class;
        } else if (type == boolean.class) {
            return Boolean.class;
        } else if (type == byte.class) {
            return Byte.class;
        } else if (type == short.class) {
            return Short.class;
        } else if (type == char.class) {
            return Character.class;
        } else {
            return type;
        }
    }

    /**
     * Returns true if type is boolean (primitive or wrapper).
     *
     * <p>Extracted from PatternDetector (ARCH-008 continuation) for reuse.
     *
     * @param type the class to check
     * @return true if type is boolean or Boolean
     */
    public static boolean isBooleanType(Class<?> type) {
        return type == boolean.class || type == Boolean.class;
    }

    /**
     * Returns true if type is numeric (primitive or wrapper).
     *
     * @param type the class to check
     * @return true if type is a numeric type
     */
    public static boolean isNumericType(Class<?> type) {
        return type == int.class || type == Integer.class ||
               type == long.class || type == Long.class ||
               type == double.class || type == Double.class ||
               type == float.class || type == Float.class ||
               type == short.class || type == Short.class ||
               type == byte.class || type == Byte.class ||
               Number.class.isAssignableFrom(type);
    }

    /**
     * Returns true if type is a temporal type (LocalDate, LocalDateTime, LocalTime).
     *
     * @param type the class to check
     * @return true if type is a temporal type
     */
    public static boolean isTemporalType(Class<?> type) {
        return type == LocalDate.class ||
               type == LocalDateTime.class ||
               type == LocalTime.class;
    }
}
