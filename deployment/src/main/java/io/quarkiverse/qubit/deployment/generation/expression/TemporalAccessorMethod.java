package io.quarkiverse.qubit.deployment.generation.expression;

import java.util.EnumSet;
import java.util.Optional;

import io.quarkiverse.qubit.deployment.generation.MethodDescriptors;
import io.quarkus.gizmo.MethodDescriptor;

/**
 * Enumeration of temporal accessor methods and their corresponding HibernateCriteriaBuilder methods.
 *
 * <p>This enum maps Java temporal accessor method names (e.g., "getYear", "getMonthValue")
 * to their corresponding HibernateCriteriaBuilder method descriptors for use in
 * JPA Criteria API bytecode generation.
 *
 * <p><b>Database Agnostic:</b> Uses HibernateCriteriaBuilder temporal methods which generate
 * database-specific SQL automatically:
 * <ul>
 *   <li>PostgreSQL: {@code EXTRACT(YEAR FROM ...)}</li>
 *   <li>MySQL: {@code YEAR(...)}</li>
 *   <li>H2: {@code EXTRACT(YEAR FROM ...)}</li>
 * </ul>
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
 * This enum encapsulates the Java→HibernateCriteriaBuilder mapping and provides type-safe lookup.
 *
 * @see TemporalExpressionBuilder
 * @see io.quarkiverse.qubit.runtime.QubitConstants
 */
public enum TemporalAccessorMethod {

    // =============================================================================================
    // DATE COMPONENT METHODS (supported by LocalDate, LocalDateTime)
    // =============================================================================================

    /**
     * Maps {@code getYear()} to {@code HibernateCriteriaBuilder.year()}.
     */
    GET_YEAR("getYear", MethodDescriptors.HCB_YEAR),

    /**
     * Maps {@code getMonthValue()} to {@code HibernateCriteriaBuilder.month()}.
     */
    GET_MONTH_VALUE("getMonthValue", MethodDescriptors.HCB_MONTH),

    /**
     * Maps {@code getDayOfMonth()} to {@code HibernateCriteriaBuilder.day()}.
     */
    GET_DAY_OF_MONTH("getDayOfMonth", MethodDescriptors.HCB_DAY),

    // =============================================================================================
    // TIME COMPONENT METHODS (supported by LocalTime, LocalDateTime)
    // =============================================================================================

    /**
     * Maps {@code getHour()} to {@code HibernateCriteriaBuilder.hour()}.
     */
    GET_HOUR("getHour", MethodDescriptors.HCB_HOUR),

    /**
     * Maps {@code getMinute()} to {@code HibernateCriteriaBuilder.minute()}.
     */
    GET_MINUTE("getMinute", MethodDescriptors.HCB_MINUTE),

    /**
     * Maps {@code getSecond()} to {@code HibernateCriteriaBuilder.second()}.
     */
    GET_SECOND("getSecond", MethodDescriptors.HCB_SECOND);

    // =============================================================================================
    // ENUM INFRASTRUCTURE
    // =============================================================================================

    private final String javaMethod;
    private final MethodDescriptor methodDescriptor;

    TemporalAccessorMethod(String javaMethod, MethodDescriptor methodDescriptor) {
        this.javaMethod = javaMethod;
        this.methodDescriptor = methodDescriptor;
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
     * Returns the HibernateCriteriaBuilder method descriptor for this temporal accessor.
     *
     * <p>The returned descriptor is used with Gizmo to generate bytecode that calls
     * the corresponding HibernateCriteriaBuilder method (e.g., {@code hcb.year(expression)}).
     *
     * @return the MethodDescriptor for the HibernateCriteriaBuilder temporal method
     */
    public MethodDescriptor getMethodDescriptor() {
        return methodDescriptor;
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
