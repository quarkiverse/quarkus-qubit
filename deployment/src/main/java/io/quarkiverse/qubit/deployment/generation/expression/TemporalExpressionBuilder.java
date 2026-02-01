package io.quarkiverse.qubit.deployment.generation.expression;

import io.quarkus.gizmo.BranchResult;
import io.quarkus.gizmo.BytecodeCreator;
import io.quarkus.gizmo.MethodCreator;
import io.quarkus.gizmo.MethodDescriptor;
import io.quarkus.gizmo.ResultHandle;
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
            MethodCreator method,
            LambdaExpression.MethodCall methodCall,
            ResultHandle cb,
            ResultHandle fieldExpression) {

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

        // Runtime guard: verify CriteriaBuilder is HibernateCriteriaBuilder before casting
        // This provides a clear error message instead of ClassCastException if using non-Hibernate JPA
        ResultHandle isHibernate = method.instanceOf(cb, HibernateCriteriaBuilder.class);
        BranchResult guardBranch = method.ifFalse(isHibernate);
        try (BytecodeCreator notHibernate = guardBranch.trueBranch()) {
            notHibernate.throwException(UnsupportedOperationException.class,
                    "Temporal accessor methods (getYear, getMonth, etc.) require Hibernate as JPA provider. " +
                    "The CriteriaBuilder is not a HibernateCriteriaBuilder. " +
                    "Ensure quarkus-hibernate-orm is configured as your JPA provider.");
        }

        // Safe cast after guard - CriteriaBuilder is guaranteed to be HibernateCriteriaBuilder
        ResultHandle hcb = method.checkCast(cb, HibernateCriteriaBuilder.class);

        // Get the MethodDescriptor directly from the enum and invoke the HibernateCriteriaBuilder method
        MethodDescriptor md = temporalMethod.get().getMethodDescriptor();
        ResultHandle result = method.invokeInterfaceMethod(md, hcb, fieldExpression);
        return BuilderResult.success(result);
    }

    /** Generates bytecode for temporal comparisons: isAfter, isBefore, isEqual. */
    public BuilderResult buildTemporalComparison(
            MethodCreator method,
            LambdaExpression.MethodCall methodCall,
            ResultHandle cb,
            ResultHandle fieldExpression,
            ResultHandle argument) {

        if (!TEMPORAL_COMPARISON_METHOD_NAMES.contains(methodCall.methodName())) {
            return BuilderResult.notApplicable();
        }

        MethodDescriptor comparisonMethod = switch (methodCall.methodName()) {
            case METHOD_IS_AFTER -> MethodDescriptors.CB_GREATER_THAN;
            case METHOD_IS_BEFORE -> MethodDescriptors.CB_LESS_THAN;
            case METHOD_IS_EQUAL -> MethodDescriptors.CB_EQUAL;
            default -> null;
        };

        if (comparisonMethod == null) {
            return BuilderResult.notApplicable();
        }

        ResultHandle result = method.invokeInterfaceMethod(comparisonMethod, cb, fieldExpression, argument);
        return BuilderResult.success(result);
    }
}
