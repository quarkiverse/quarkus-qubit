package io.quarkiverse.qubit.deployment.generation.expression;

import java.util.EnumSet;
import java.util.Optional;

import io.quarkiverse.qubit.deployment.generation.MethodDescriptors;
import io.quarkus.gizmo.MethodDescriptor;

/** Maps temporal accessor methods (getYear, getHour, etc.) to HibernateCriteriaBuilder methods. */
public enum TemporalAccessorMethod {

    // ========== Date Methods (LocalDate, LocalDateTime) ==========
    GET_YEAR("getYear", MethodDescriptors.HCB_YEAR),
    GET_MONTH_VALUE("getMonthValue", MethodDescriptors.HCB_MONTH),
    GET_DAY_OF_MONTH("getDayOfMonth", MethodDescriptors.HCB_DAY),

    // ========== Time Methods (LocalTime, LocalDateTime) ==========
    GET_HOUR("getHour", MethodDescriptors.HCB_HOUR),
    GET_MINUTE("getMinute", MethodDescriptors.HCB_MINUTE),
    GET_SECOND("getSecond", MethodDescriptors.HCB_SECOND);

    private final String javaMethod;
    private final MethodDescriptor methodDescriptor;

    TemporalAccessorMethod(String javaMethod, MethodDescriptor methodDescriptor) {
        this.javaMethod = javaMethod;
        this.methodDescriptor = methodDescriptor;
    }

    public String getJavaMethod() {
        return javaMethod;
    }

    public MethodDescriptor getMethodDescriptor() {
        return methodDescriptor;
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

    // ========== EnumSets for Type-Specific Methods ==========
    public static final EnumSet<TemporalAccessorMethod> DATE_METHODS = EnumSet.of(
            GET_YEAR, GET_MONTH_VALUE, GET_DAY_OF_MONTH);
    public static final EnumSet<TemporalAccessorMethod> TIME_METHODS = EnumSet.of(
            GET_HOUR, GET_MINUTE, GET_SECOND);
    public static final EnumSet<TemporalAccessorMethod> ALL = EnumSet.allOf(TemporalAccessorMethod.class);
}
