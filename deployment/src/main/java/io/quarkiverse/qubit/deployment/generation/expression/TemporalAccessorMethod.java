package io.quarkiverse.qubit.deployment.generation.expression;

import java.util.Optional;

/**
 * Maps temporal accessor methods to JPA 3.2 {@code CriteriaBuilder.extract()} field names.
 *
 * <p>
 * Each enum value represents a temporal extraction operation that maps a Java method name
 * (e.g., "getYear") to the corresponding JPA 3.2 {@code TemporalField} constant name
 * (e.g., "YEAR") used with {@code CriteriaBuilder.extract(TemporalField, Expression)}.
 *
 * <p>
 * Standard Java temporal accessors (getYear, getMonthValue, etc.) are detected from
 * bytecode analysis. QUARTER and WEEK are additional extractions exposed via
 * {@code Qubit.quarter()} and {@code Qubit.week()} marker methods.
 */
public enum TemporalAccessorMethod {

    GET_YEAR("getYear", "YEAR"),
    GET_MONTH_VALUE("getMonthValue", "MONTH"),
    GET_DAY_OF_MONTH("getDayOfMonth", "DAY"),

    GET_HOUR("getHour", "HOUR"),
    GET_MINUTE("getMinute", "MINUTE"),
    GET_SECOND("getSecond", "SECOND"),

    QUARTER("quarter", "QUARTER"),
    WEEK("week", "WEEK");

    private final String javaMethod;
    private final String extractFieldName;

    TemporalAccessorMethod(String javaMethod, String extractFieldName) {
        this.javaMethod = javaMethod;
        this.extractFieldName = extractFieldName;
    }

    public String getJavaMethod() {
        return javaMethod;
    }

    /**
     * Returns the JPA 3.2 TemporalField constant name (e.g., "YEAR", "MONTH", "QUARTER").
     * Used with {@code Expr.staticField()} to load the appropriate
     * {@code LocalDateField.YEAR}, {@code LocalDateTimeField.HOUR}, etc.
     */
    public String getExtractFieldName() {
        return extractFieldName;
    }

    public static Optional<TemporalAccessorMethod> fromJavaMethod(String methodName) {
        if (methodName == null) {
            return Optional.empty();
        }
        for (TemporalAccessorMethod method : values()) {
            if (method.javaMethod.equals(methodName)) {
                return Optional.of(method);
            }
        }
        return Optional.empty();
    }

    public static boolean isTemporalAccessor(String methodName) {
        return fromJavaMethod(methodName).isPresent();
    }
}
