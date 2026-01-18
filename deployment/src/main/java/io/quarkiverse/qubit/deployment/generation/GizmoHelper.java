package io.quarkiverse.qubit.deployment.generation;

import static io.quarkiverse.qubit.deployment.generation.MethodDescriptors.*;
import static io.quarkiverse.qubit.runtime.QubitConstants.METHOD_OF;

import io.quarkiverse.qubit.deployment.analysis.InvokeDynamicScanner;
import io.quarkiverse.qubit.deployment.analysis.LambdaAnalysisResult.SortExpression;
import io.quarkiverse.qubit.deployment.ast.LambdaExpression;
import io.quarkiverse.qubit.runtime.SortDirection;
import io.quarkus.gizmo.FieldDescriptor;
import io.quarkus.gizmo.MethodCreator;
import io.quarkus.gizmo.MethodDescriptor;
import io.quarkus.gizmo.ResultHandle;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Selection;

import java.math.BigDecimal;
import java.util.List;
import java.util.function.Function;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * Utility class for common Gizmo bytecode generation patterns.
 *
 * <p>This class provides reusable methods for generating bytecode using
 * the Gizmo library, avoiding duplication across expression builders.
 */
public final class GizmoHelper {

    private GizmoHelper() {
        // Utility class
    }

    /** Loads entity class, using Class.forName() for placeholder class names. */
    public static ResultHandle loadEntityClass(MethodCreator method, Class<?> entityClass, String entityClassName) {
        // Placeholder case: Load class by name at runtime
        if (entityClassName != null) {
            ResultHandle classNameHandle = method.load(entityClassName);
            return method.invokeStaticMethod(CLASS_FOR_NAME, classNameHandle);
        }
        // Normal case: Direct class reference
        return method.loadClass(entityClass);
    }

    /** Loads JPA JoinType enum value (INNER or LEFT). */
    public static ResultHandle loadJpaJoinType(MethodCreator method, InvokeDynamicScanner.JoinType joinType) {
        String jpaJoinTypeName = (joinType == InvokeDynamicScanner.JoinType.LEFT) ? "LEFT" : "INNER";
        return method.readStaticField(
            FieldDescriptor.of(JoinType.class, jpaJoinTypeName, JoinType.class)
        );
    }

    /** Creates an array for JPA varargs methods like where(Predicate...). */
    public static ResultHandle createElementArray(MethodCreator method, Class<?> elementType, ResultHandle... elements) {
        ResultHandle array = method.newArray(elementType, elements.length);
        for (int i = 0; i < elements.length; i++) {
            method.writeArrayValue(array, i, elements[i]);
        }
        return array;
    }

    /** Unboxes Integer to int. */
    public static ResultHandle unboxInteger(MethodCreator method, ResultHandle boxedValue) {
        return method.invokeVirtualMethod(INTEGER_INT_VALUE, boxedValue);
    }

    /** Unboxes Boolean to boolean. */
    public static ResultHandle unboxBoolean(MethodCreator method, ResultHandle boxedValue) {
        return method.invokeVirtualMethod(BOOLEAN_BOOLEAN_VALUE, boxedValue);
    }

    /** Unboxes Long to long. */
    public static ResultHandle unboxLong(MethodCreator method, ResultHandle boxedValue) {
        return method.invokeVirtualMethod(LONG_LONG_VALUE, boxedValue);
    }

    /** Loads constant as bytecode (primitives, BigDecimal, LocalDate/Time). */
    public static ResultHandle loadConstant(MethodCreator method, Object value) {
        return switch (value) {
            case null -> method.loadNull();
            case String s -> method.load(s);
            case Integer i -> method.load(i);
            case Long l -> method.load(l);
            case Boolean b -> method.load(b);
            case Double d -> method.load(d);
            case Float f -> method.load(f);

            case BigDecimal bd -> {
                ResultHandle bdString = method.load(bd.toString());
                yield method.newInstance(MethodDescriptor.ofConstructor(BigDecimal.class, String.class), bdString);
            }

            case LocalDate ld -> {
                ResultHandle year = method.load(ld.getYear());
                ResultHandle month = method.load(ld.getMonthValue());
                ResultHandle day = method.load(ld.getDayOfMonth());
                yield method.invokeStaticMethod(
                        md(LocalDate.class, METHOD_OF, LocalDate.class, int.class, int.class, int.class),
                        year, month, day);
            }

            case LocalDateTime ldt -> {
                ResultHandle year = method.load(ldt.getYear());
                ResultHandle month = method.load(ldt.getMonthValue());
                ResultHandle day = method.load(ldt.getDayOfMonth());
                ResultHandle hour = method.load(ldt.getHour());
                ResultHandle minute = method.load(ldt.getMinute());
                yield method.invokeStaticMethod(
                        md(LocalDateTime.class, METHOD_OF, LocalDateTime.class,
                                int.class, int.class, int.class, int.class, int.class),
                        year, month, day, hour, minute);
            }

            case LocalTime lt -> {
                ResultHandle hour = method.load(lt.getHour());
                ResultHandle minute = method.load(lt.getMinute());
                yield method.invokeStaticMethod(
                        md(LocalTime.class, METHOD_OF, LocalTime.class, int.class, int.class),
                        hour, minute);
            }

            default -> method.loadNull();
        };
    }

    /** Builds cb.construct() for DTO projections from constructor arguments. */
    public static ResultHandle buildConstructorExpression(
            MethodCreator method,
            ResultHandle cb,
            ResultHandle resultClassHandle,
            List<LambdaExpression> arguments,
            Function<LambdaExpression, ResultHandle> expressionGenerator) {

        int argCount = arguments.size();
        ResultHandle selectionsArray = method.newArray(Selection.class, argCount);

        for (int i = 0; i < argCount; i++) {
            ResultHandle argExpression = expressionGenerator.apply(arguments.get(i));
            method.writeArrayValue(selectionsArray, i, argExpression);
        }

        return method.invokeInterfaceMethod(CB_CONSTRUCT, cb, resultClassHandle, selectionsArray);
    }

    /** Builds ORDER BY clause with "last call wins" semantics (reverse order). */
    public static void buildOrderByClause(
            MethodCreator method,
            ResultHandle query,
            ResultHandle cb,
            List<?> sortExpressions,
            Function<SortExpression, ResultHandle> sortKeyGenerator) {

        if (sortExpressions == null || sortExpressions.isEmpty()) {
            return; // No sorting
        }

        // Create array to hold Order objects
        ResultHandle ordersArray = method.newArray(Order.class, sortExpressions.size());

        // Reverse order for "last call wins" semantics (sortedBy(a).sortedBy(b) → ORDER BY b, a)
        for (int i = 0; i < sortExpressions.size(); i++) {
            int reverseIndex = sortExpressions.size() - 1 - i;
            Object sortExprObj = sortExpressions.get(reverseIndex);

            if (sortExprObj instanceof SortExpression sortExpr) {
                // Generate JPA Expression for the sort key using provided generator
                ResultHandle sortKeyExpr = sortKeyGenerator.apply(sortExpr);

                // Create Order object (ascending or descending)
                ResultHandle order;
                if (sortExpr.direction() == SortDirection.DESCENDING) {
                    order = method.invokeInterfaceMethod(CB_DESC, cb, sortKeyExpr);
                } else {
                    order = method.invokeInterfaceMethod(CB_ASC, cb, sortKeyExpr);
                }

                // Add to orders array at position i (forward order)
                method.writeArrayValue(ordersArray, i, order);
            }
        }

        // Apply orderBy to query
        method.invokeInterfaceMethod(CQ_ORDER_BY, query, ordersArray);
    }
}
