package io.quarkiverse.qubit.deployment.generation.expression;

import io.quarkus.gizmo2.Expr;
import io.quarkus.gizmo2.LocalVar;
import io.quarkus.gizmo2.creator.BlockCreator;
import io.quarkus.gizmo2.desc.MethodDesc;
import io.quarkiverse.qubit.deployment.ast.LambdaExpression;
import io.quarkiverse.qubit.deployment.generation.MethodDescriptors;
import org.hibernate.query.criteria.HibernateCriteriaBuilder;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static io.quarkiverse.qubit.runtime.internal.QubitConstants.METHOD_IS_AFTER;
import static io.quarkiverse.qubit.runtime.internal.QubitConstants.METHOD_IS_BEFORE;
import static io.quarkiverse.qubit.runtime.internal.QubitConstants.METHOD_IS_EQUAL;
import static io.quarkiverse.qubit.runtime.internal.QubitConstants.TEMPORAL_COMPARISON_METHOD_NAMES;

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
 *
 * <p>Uses Gizmo 2 API with BlockCreator and Expr types.
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
     * Generates bytecode for temporal accessor functions using HibernateCriteriaBuilder.
     * Uses database-agnostic methods (year, month, day, hour, minute, second).
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

        // Cast to HibernateCriteriaBuilder - Quarkus with Hibernate always provides this
        // Note: If using a non-Hibernate JPA provider, this will fail with ClassCastException at runtime
        // Use LocalVar for the fieldExpression since it's passed in from another context (Gizmo2 requirement)
        LocalVar fieldExprLocal = bc.localVar("fieldExpr", fieldExpression);
        LocalVar hcb = bc.localVar("hcb", bc.cast(cb, HibernateCriteriaBuilder.class));

        // Get the MethodDesc directly from the enum and invoke the HibernateCriteriaBuilder method
        MethodDesc md = temporalMethod.get().getMethodDesc();
        Expr result = bc.invokeInterface(md, hcb, fieldExprLocal);
        return BuilderResult.success(result);
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
