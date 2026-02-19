package io.quarkiverse.qubit.deployment.generation.expression;

import static io.quarkiverse.qubit.runtime.internal.QubitConstants.METHOD_IS_AFTER;
import static io.quarkiverse.qubit.runtime.internal.QubitConstants.METHOD_IS_BEFORE;
import static io.quarkiverse.qubit.runtime.internal.QubitConstants.METHOD_IS_EQUAL;
import static io.quarkiverse.qubit.runtime.internal.QubitConstants.TEMPORAL_COMPARISON_METHOD_NAMES;

import java.lang.constant.ClassDesc;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import jakarta.persistence.criteria.LocalDateField;
import jakarta.persistence.criteria.LocalDateTimeField;
import jakarta.persistence.criteria.LocalTimeField;

import io.quarkiverse.qubit.deployment.ast.LambdaExpression;
import io.quarkiverse.qubit.deployment.generation.MethodDescriptors;
import io.quarkus.gizmo2.Expr;
import io.quarkus.gizmo2.LocalVar;
import io.quarkus.gizmo2.creator.BlockCreator;
import io.quarkus.gizmo2.desc.FieldDesc;
import io.quarkus.gizmo2.desc.MethodDesc;

/**
 * Builds JPA Criteria API expressions for temporal (date/time) operations.
 *
 * <p>
 * Supported operations:
 *
 * <ul>
 * <li><b>Accessor Functions:</b> getYear(), getMonthValue(), getDayOfMonth(),
 * getHour(), getMinute(), getSecond() → SQL EXTRACT(YEAR/MONTH/DAY/HOUR/MINUTE/SECOND FROM ...)</li>
 * <li><b>Extended Extractions:</b> Qubit.quarter(), Qubit.week() → SQL EXTRACT(QUARTER/WEEK FROM ...)</li>
 * <li><b>Comparisons:</b> isAfter() → greaterThan(), isBefore() → lessThan(),
 * isEqual() → equal()</li>
 * </ul>
 *
 * <p>
 * Uses the standard JPA 3.2 {@code CriteriaBuilder.extract(TemporalField, Expression)} API
 * with {@code LocalDateField}, {@code LocalDateTimeField}, and {@code LocalTimeField} constants.
 *
 * <p>
 * <b>Supported Types:</b> LocalDate, LocalDateTime, LocalTime
 */
public enum TemporalExpressionBuilder implements ExpressionBuilder {
    INSTANCE;

    /** Checks if a method call is a temporal comparison (isAfter, isBefore, isEqual). */
    public boolean isTemporalComparison(LambdaExpression.MethodCall methodCall) {
        return TEMPORAL_COMPARISON_METHOD_NAMES.contains(methodCall.methodName());
    }

    /** Checks if the type is LocalDate, LocalDateTime, or LocalTime. */
    public static boolean isSupportedTemporalType(Class<?> type) {
        return type == LocalDate.class ||
                type == LocalDateTime.class ||
                type == LocalTime.class;
    }

    /**
     * Generates bytecode for temporal accessor functions using standard JPA 3.2 extract().
     * Maps Java temporal methods to {@code cb.extract(TemporalField, Expression)}.
     */
    public BuilderResult buildTemporalAccessorFunction(
            BlockCreator bc,
            LambdaExpression.MethodCall methodCall,
            Expr cb,
            Expr fieldExpression) {

        // Verify the target is a supported temporal type
        if (!(methodCall.target() instanceof LambdaExpression.FieldAccess(_, var fieldType))) {
            return BuilderResult.notApplicable();
        }

        if (!isSupportedTemporalType(fieldType)) {
            return BuilderResult.notApplicable();
        }

        // Look up the temporal accessor method
        var temporalMethod = TemporalAccessorMethod.fromJavaMethod(methodCall.methodName());
        if (temporalMethod.isEmpty()) {
            return BuilderResult.notApplicable();
        }

        // Determine the JPA 3.2 TemporalField class based on the entity field's temporal type
        Class<?> temporalFieldClass = getTemporalFieldClass(fieldType);
        if (temporalFieldClass == null) {
            return BuilderResult.notApplicable();
        }

        // Load the static TemporalField constant (e.g., LocalDateField.YEAR, LocalDateTimeField.HOUR)
        String extractFieldName = temporalMethod.get().getExtractFieldName();
        ClassDesc temporalFieldClassDesc = ClassDesc.of(temporalFieldClass.getName());
        Expr temporalField = Expr.staticField(
                FieldDesc.of(temporalFieldClassDesc, extractFieldName, temporalFieldClassDesc));

        // Call cb.extract(temporalField, fieldExpression) — standard JPA 3.2, no Hibernate dependency
        // Use LocalVar for the fieldExpression since it's passed in from another context (Gizmo2 requirement)
        LocalVar fieldExprLocal = bc.localVar("fieldExpr", fieldExpression);
        Expr result = bc.invokeInterface(MethodDescriptors.CB_EXTRACT, cb, temporalField, fieldExprLocal);
        return BuilderResult.success(result);
    }

    /**
     * Returns the JPA 3.2 TemporalField class for the given entity field type.
     * <ul>
     * <li>LocalDate → LocalDateField</li>
     * <li>LocalDateTime → LocalDateTimeField</li>
     * <li>LocalTime → LocalTimeField</li>
     * </ul>
     */
    @org.jspecify.annotations.Nullable
    static Class<?> getTemporalFieldClass(Class<?> entityFieldType) {
        if (entityFieldType == LocalDate.class) {
            return LocalDateField.class;
        }
        if (entityFieldType == LocalDateTime.class) {
            return LocalDateTimeField.class;
        }
        if (entityFieldType == LocalTime.class) {
            return LocalTimeField.class;
        }
        return null;
    }

    /** Generates bytecode for temporal comparisons: isAfter, isBefore, isEqual. */
    public BuilderResult buildTemporalComparison(
            BlockCreator bc,
            LambdaExpression.MethodCall methodCall,
            Expr cb,
            Expr fieldExpression,
            Expr argument) {

        if (!TEMPORAL_COMPARISON_METHOD_NAMES.contains(methodCall.methodName())) {
            return BuilderResult.notApplicable();
        }

        MethodDesc comparisonMethod = switch (methodCall.methodName()) {
            case METHOD_IS_AFTER -> MethodDescriptors.CB_GREATER_THAN;
            case METHOD_IS_BEFORE -> MethodDescriptors.CB_LESS_THAN;
            case METHOD_IS_EQUAL -> MethodDescriptors.CB_EQUAL;
            default -> null;
        };

        if (comparisonMethod == null) {
            return BuilderResult.notApplicable();
        }

        Expr result = bc.invokeInterface(comparisonMethod, cb, fieldExpression, argument);
        return BuilderResult.success(result);
    }
}
