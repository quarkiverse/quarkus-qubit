package io.quarkiverse.qubit.deployment.generation.expression;

import java.util.EnumSet;
import java.util.Optional;

/**
 * Enumeration of temporal accessor methods and their corresponding SQL functions.
 *
 * <p>This enum maps Java temporal accessor method names (e.g., "getYear", "getMonthValue")
 * to their corresponding SQL function names (e.g., "YEAR", "MONTH") for use in
 * JPA Criteria API bytecode generation.
 *
 * <p><b>Supported Temporal Types:</b>
 * <ul>
 *   <li>{@code LocalDate} - supports {@link #DATE_METHODS} (year, month, day)</li>
 *   <li>{@code LocalTime} - supports {@link #TIME_METHODS} (hour, minute, second)</li>
 *   <li>{@code LocalDateTime} - supports all methods ({@link #ALL})</li>
 * </ul>
 *
 * <p><b>Design Rationale:</b> String constants for method names are retained in
 * {@code QubitConstants} for use as switch case labels in bytecode analysis code
 * (e.g., {@code MethodInvocationHandler}), which requires compile-time constants.
 * This enum encapsulates the Java→SQL mapping and provides type-safe lookup.
 *
 * @see TemporalExpressionBuilder
 * @see io.quarkiverse.qubit.runtime.QubitConstants
 */
public enum TemporalAccessorMethod {

    // =============================================================================================
    // DATE COMPONENT METHODS (supported by LocalDate, LocalDateTime)
    // =============================================================================================

    /**
     * Maps {@code getYear()} to SQL {@code YEAR()} function.
     */
    GET_YEAR("getYear", "YEAR"),

    /**
     * Maps {@code getMonthValue()} to SQL {@code MONTH()} function.
     */
    GET_MONTH_VALUE("getMonthValue", "MONTH"),

    /**
     * Maps {@code getDayOfMonth()} to SQL {@code DAY()} function.
     */
    GET_DAY_OF_MONTH("getDayOfMonth", "DAY"),

    // =============================================================================================
    // TIME COMPONENT METHODS (supported by LocalTime, LocalDateTime)
    // =============================================================================================

    /**
     * Maps {@code getHour()} to SQL {@code HOUR()} function.
     */
    GET_HOUR("getHour", "HOUR"),

    /**
     * Maps {@code getMinute()} to SQL {@code MINUTE()} function.
     */
    GET_MINUTE("getMinute", "MINUTE"),

    /**
     * Maps {@code getSecond()} to SQL {@code SECOND()} function.
     */
    GET_SECOND("getSecond", "SECOND");

    // =============================================================================================
    // ENUM INFRASTRUCTURE
    // =============================================================================================

    private final String javaMethod;
    private final String sqlFunction;

    TemporalAccessorMethod(String javaMethod, String sqlFunction) {
        this.javaMethod = javaMethod;
        this.sqlFunction = sqlFunction;
    }

    /**
     * Returns the Java method name (e.g., "getYear").
     *
     * @return the Java method name
     */
    public String getJavaMethod() {
        return javaMethod;
    }

    /**
     * Returns the corresponding SQL function name (e.g., "YEAR").
     *
     * @return the SQL function name
     */
    public String getSqlFunction() {
        return sqlFunction;
    }

    /**
     * Looks up a TemporalAccessorMethod by its Java method name.
     *
     * @param methodName the Java method name to look up (e.g., "getYear")
     * @return an Optional containing the enum value, or empty if not found
     */
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

    /**
     * Checks if the given method name is a temporal accessor method.
     *
     * @param methodName the Java method name to check
     * @return true if the method is a temporal accessor, false otherwise
     */
    public static boolean isTemporalAccessor(String methodName) {
        return fromJavaMethod(methodName).isPresent();
    }

    /**
     * Maps a Java temporal accessor method name to its SQL function name.
     *
     * <p>This method provides a convenient way to get the SQL function name
     * without dealing with Optional.
     *
     * @param methodName the Java method name (e.g., "getYear")
     * @return the SQL function name (e.g., "YEAR"), or null if not a temporal accessor
     */
    public static String toSqlFunction(String methodName) {
        return fromJavaMethod(methodName)
                .map(TemporalAccessorMethod::getSqlFunction)
                .orElse(null);
    }

    // =============================================================================================
    // ENUMSETS FOR TYPE-SPECIFIC METHODS
    // =============================================================================================

    /**
     * Date component accessor methods (supported by LocalDate and LocalDateTime).
     * <p>Includes: getYear, getMonthValue, getDayOfMonth
     */
    public static final EnumSet<TemporalAccessorMethod> DATE_METHODS = EnumSet.of(
            GET_YEAR, GET_MONTH_VALUE, GET_DAY_OF_MONTH
    );

    /**
     * Time component accessor methods (supported by LocalTime and LocalDateTime).
     * <p>Includes: getHour, getMinute, getSecond
     */
    public static final EnumSet<TemporalAccessorMethod> TIME_METHODS = EnumSet.of(
            GET_HOUR, GET_MINUTE, GET_SECOND
    );

    /**
     * All temporal accessor methods.
     */
    public static final EnumSet<TemporalAccessorMethod> ALL =
            EnumSet.allOf(TemporalAccessorMethod.class);
}
