package io.quarkiverse.qubit.deployment.generation.expression;

import io.quarkus.gizmo.MethodCreator;
import io.quarkus.gizmo.MethodDescriptor;
import io.quarkus.gizmo.ResultHandle;
import io.quarkiverse.qubit.deployment.ast.LambdaExpression;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import org.hibernate.query.criteria.HibernateCriteriaBuilder;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static io.quarkiverse.qubit.runtime.QubitConstants.CB_EQUAL;
import static io.quarkiverse.qubit.runtime.QubitConstants.CB_GREATER_THAN;
import static io.quarkiverse.qubit.runtime.QubitConstants.CB_LESS_THAN;
import static io.quarkiverse.qubit.runtime.QubitConstants.METHOD_IS_AFTER;
import static io.quarkiverse.qubit.runtime.QubitConstants.METHOD_IS_BEFORE;
import static io.quarkiverse.qubit.runtime.QubitConstants.METHOD_IS_EQUAL;
import static io.quarkiverse.qubit.runtime.QubitConstants.TEMPORAL_COMPARISON_METHOD_NAMES;

/**
 * Builds JPA Criteria API expressions for temporal (date/time) operations.
 *
 * <p>Supported operations:
 *
 * <ul>
 *   <li><b>Accessor Functions:</b> getYear(), getMonthValue(), getDayOfMonth(),
 *       getHour(), getMinute(), getSecond() → SQL YEAR, MONTH, DAY, HOUR, MINUTE, SECOND</li>
 *   <li><b>Comparisons:</b> isAfter() → greaterThan(), isBefore() → lessThan(),
 *       isEqual() → equal()</li>
 * </ul>
 *
 * <p><b>Supported Types:</b> LocalDate, LocalDateTime, LocalTime
 */
public class TemporalExpressionBuilder implements ExpressionBuilder {

    /**
     * Checks if a method call is a temporal comparison.
     *
     * @param methodCall the method call expression
     * @return true if temporal comparison (isAfter, isBefore, isEqual)
     */
    public boolean isTemporalComparison(LambdaExpression.MethodCall methodCall) {
        return TEMPORAL_COMPARISON_METHOD_NAMES.contains(methodCall.methodName());
    }

    /**
     * Checks if the target type is a supported temporal type.
     *
     * @param type the field type
     * @return true if the type is LocalDate, LocalDateTime, or LocalTime
     */
    public static boolean isSupportedTemporalType(Class<?> type) {
        return type == LocalDate.class ||
               type == LocalDateTime.class ||
               type == LocalTime.class;
    }

    /**
     * Generates bytecode for temporal accessor functions using HibernateCriteriaBuilder.
     *
     * <p>Uses database-agnostic HibernateCriteriaBuilder methods:
     * <ul>
     *   <li>{@code year()} - extracts year, generates EXTRACT(YEAR FROM ...) on PostgreSQL</li>
     *   <li>{@code month()} - extracts month, generates EXTRACT(MONTH FROM ...) on PostgreSQL</li>
     *   <li>{@code day()} - extracts day, generates EXTRACT(DAY FROM ...) on PostgreSQL</li>
     *   <li>{@code hour()} - extracts hour, generates EXTRACT(HOUR FROM ...) on PostgreSQL</li>
     *   <li>{@code minute()} - extracts minute, generates EXTRACT(MINUTE FROM ...) on PostgreSQL</li>
     *   <li>{@code second()} - extracts second, generates EXTRACT(SECOND FROM ...) on PostgreSQL</li>
     * </ul>
     *
     * @param method the Gizmo method creator
     * @param methodCall the method call expression
     * @param cb the CriteriaBuilder handle (will be cast to HibernateCriteriaBuilder)
     * @param fieldExpression the target field expression
     * @return the SQL function Expression, or null if target is not a supported temporal type
     */
    public ResultHandle buildTemporalAccessorFunction(
            MethodCreator method,
            LambdaExpression.MethodCall methodCall,
            ResultHandle cb,
            ResultHandle fieldExpression) {

        // Verify the target is a supported temporal type
        if (!(methodCall.target() instanceof LambdaExpression.FieldAccess fieldAccess)) {
            return null;
        }

        if (!isSupportedTemporalType(fieldAccess.fieldType())) {
            return null;
        }

        // Look up the temporal accessor method
        var temporalMethod = TemporalAccessorMethod.fromJavaMethod(methodCall.methodName());
        if (temporalMethod.isEmpty()) {
            return null;
        }

        // Cast CriteriaBuilder to HibernateCriteriaBuilder for database-agnostic temporal methods
        ResultHandle hcb = method.checkCast(cb, HibernateCriteriaBuilder.class);

        // Get the MethodDescriptor directly from the enum and invoke the HibernateCriteriaBuilder method
        MethodDescriptor md = temporalMethod.get().getMethodDescriptor();
        return method.invokeInterfaceMethod(md, hcb, fieldExpression);
    }

    /**
     * Generates bytecode for temporal comparisons: isAfter, isBefore, isEqual.
     *
     * @param method the Gizmo method creator
     * @param methodCall the method call expression
     * @param cb the CriteriaBuilder handle
     * @param fieldExpression the target field expression
     * @param argument the comparison argument
     * @return the comparison Predicate, or null if not a temporal comparison
     */
    public ResultHandle buildTemporalComparison(
            MethodCreator method,
            LambdaExpression.MethodCall methodCall,
            ResultHandle cb,
            ResultHandle fieldExpression,
            ResultHandle argument) {

        if (!TEMPORAL_COMPARISON_METHOD_NAMES.contains(methodCall.methodName())) {
            return null;
        }

        return switch (methodCall.methodName()) {
            case METHOD_IS_AFTER ->
                method.invokeInterfaceMethod(md(CB_GREATER_THAN), cb, fieldExpression, argument);
            case METHOD_IS_BEFORE ->
                method.invokeInterfaceMethod(md(CB_LESS_THAN), cb, fieldExpression, argument);
            case METHOD_IS_EQUAL ->
                method.invokeInterfaceMethod(md(CB_EQUAL), cb, fieldExpression, argument);
            default -> null;
        };
    }

    /**
     * Creates MethodDescriptor for CriteriaBuilder comparison methods.
     */
    private static MethodDescriptor md(String methodName) {
        if (methodName.equals(CB_GREATER_THAN)) {
            return MethodDescriptor.ofMethod(
                    CriteriaBuilder.class,
                    methodName,
                    Predicate.class,
                    Expression.class,
                    Comparable.class);
        }
        if (methodName.equals(CB_LESS_THAN)) {
            return MethodDescriptor.ofMethod(
                    CriteriaBuilder.class,
                    methodName,
                    Predicate.class,
                    Expression.class,
                    Comparable.class);
        }
        if (methodName.equals(CB_EQUAL)) {
            return MethodDescriptor.ofMethod(
                    CriteriaBuilder.class,
                    methodName,
                    Predicate.class,
                    Expression.class,
                    Object.class);
        }
        throw new IllegalArgumentException("Unknown method: " + methodName);
    }
}
