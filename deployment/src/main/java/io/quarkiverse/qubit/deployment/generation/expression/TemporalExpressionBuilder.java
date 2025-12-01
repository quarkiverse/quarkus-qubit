package io.quarkiverse.qubit.deployment.generation.expression;

import io.quarkus.gizmo.MethodCreator;
import io.quarkus.gizmo.MethodDescriptor;

import io.quarkus.gizmo.ResultHandle;
import io.quarkiverse.qubit.deployment.ast.LambdaExpression;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Set;

import static io.quarkiverse.qubit.runtime.QubitConstants.*;

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
     * Temporal accessor methods that extract components from date/time values.
     */
    private static final Set<String> TEMPORAL_ACCESSOR_METHODS = Set.of(
        METHOD_GET_YEAR,
        METHOD_GET_MONTH_VALUE,
        METHOD_GET_DAY_OF_MONTH,
        METHOD_GET_HOUR,
        METHOD_GET_MINUTE,
        METHOD_GET_SECOND
    );

    /**
     * Temporal comparison methods (isAfter, isBefore, isEqual).
     */
    private static final Set<String> TEMPORAL_COMPARISON_METHODS = TEMPORAL_COMPARISON_METHOD_NAMES;

    /**
     * Maps temporal accessor method names to SQL function names.
     *
     * @param methodName the Java method name (e.g., "getYear")
     * @return the SQL function name (e.g., "YEAR"), or null
     */
    public static String mapTemporalAccessorToSqlFunction(String methodName) {
        return switch (methodName) {
            case METHOD_GET_YEAR -> SQL_YEAR;
            case METHOD_GET_MONTH_VALUE -> SQL_MONTH;
            case METHOD_GET_DAY_OF_MONTH -> SQL_DAY;
            case METHOD_GET_HOUR -> SQL_HOUR;
            case METHOD_GET_MINUTE -> SQL_MINUTE;
            case METHOD_GET_SECOND -> SQL_SECOND;
            default -> null;
        };
    }

    /**
     * Checks if a method call is a temporal accessor function.
     *
     * @param methodCall the method call expression
     * @return true if temporal accessor (getYear, getMonth, etc.)
     */
    public boolean isTemporalAccessor(LambdaExpression.MethodCall methodCall) {
        return TEMPORAL_ACCESSOR_METHODS.contains(methodCall.methodName());
    }

    /**
     * Checks if a method call is a temporal comparison.
     *
     * @param methodCall the method call expression
     * @return true if temporal comparison (isAfter, isBefore, isEqual)
     */
    public boolean isTemporalComparison(LambdaExpression.MethodCall methodCall) {
        return TEMPORAL_COMPARISON_METHODS.contains(methodCall.methodName());
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
     * Generates bytecode for temporal accessor functions: YEAR, MONTH, DAY, HOUR, MINUTE, SECOND.
     *
     * @param method the Gizmo method creator
     * @param methodCall the method call expression
     * @param cb the CriteriaBuilder handle
     * @param fieldExpression the target field expression
     * @return the SQL function Expression
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

        String functionName = mapTemporalAccessorToSqlFunction(methodCall.methodName());
        if (functionName == null) {
            return null;
        }

        ResultHandle functionNameHandle = method.load(functionName);
        ResultHandle integerClass = method.loadClass(Integer.class);

        ResultHandle expressionArray = method.newArray(Expression.class, 1);
        method.writeArrayValue(expressionArray, 0, fieldExpression);

        return method.invokeInterfaceMethod(
                md(CB_FUNCTION),
                cb, functionNameHandle, integerClass, expressionArray);
    }

    /**
     * Generates bytecode for temporal comparisons: isAfter, isBefore, isEqual.
     *
     * @param method the Gizmo method creator
     * @param methodCall the method call expression
     * @param cb the CriteriaBuilder handle
     * @param fieldExpression the target field expression
     * @param argument the comparison argument
     * @return the comparison Predicate
     */
    public ResultHandle buildTemporalComparison(
            MethodCreator method,
            LambdaExpression.MethodCall methodCall,
            ResultHandle cb,
            ResultHandle fieldExpression,
            ResultHandle argument) {

        if (!TEMPORAL_COMPARISON_METHODS.contains(methodCall.methodName())) {
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
     * Creates MethodDescriptor for CriteriaBuilder methods.
     */
    private static MethodDescriptor md(String methodName) {
        if (methodName.equals(CB_FUNCTION)) {
            return MethodDescriptor.ofMethod(
                    CriteriaBuilder.class,
                    methodName,
                    Expression.class,
                    String.class,
                    Class.class,
                    Expression[].class);
        }
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
