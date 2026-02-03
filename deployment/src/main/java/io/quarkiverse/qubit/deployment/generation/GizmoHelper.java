package io.quarkiverse.qubit.deployment.generation;

import static io.quarkiverse.qubit.deployment.generation.MethodDescriptors.*;
import static io.quarkiverse.qubit.runtime.internal.QubitConstants.METHOD_OF;

import io.quarkiverse.qubit.deployment.analysis.InvokeDynamicScanner;
import io.quarkiverse.qubit.deployment.analysis.LambdaAnalysisResult.SortExpression;
import io.quarkiverse.qubit.deployment.ast.LambdaExpression;
import io.quarkiverse.qubit.SortDirection;
import io.quarkus.gizmo2.Const;
import io.quarkus.gizmo2.Expr;
import io.quarkus.gizmo2.LocalVar;
import io.quarkus.gizmo2.creator.BlockCreator;
import io.quarkus.gizmo2.desc.ConstructorDesc;
import io.quarkus.gizmo2.desc.FieldDesc;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Selection;

import java.lang.constant.ClassDesc;
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
    public static Expr loadEntityClass(BlockCreator bc, Class<?> entityClass, String entityClassName) {
        // Placeholder case: Load class by name at runtime
        if (entityClassName != null) {
            return bc.invokeStatic(CLASS_FOR_NAME, Const.of(entityClassName));
        }
        // Normal case: Direct class reference
        return Const.of(entityClass);
    }

    /** Loads JPA JoinType enum value (INNER or LEFT). */
    public static Expr loadJpaJoinType(InvokeDynamicScanner.JoinType joinType) {
        String jpaJoinTypeName = (joinType == InvokeDynamicScanner.JoinType.LEFT) ? "LEFT" : "INNER";
        ClassDesc joinTypeDesc = ClassDesc.of(JoinType.class.getName());
        return Expr.staticField(FieldDesc.of(joinTypeDesc, jpaJoinTypeName, joinTypeDesc));
    }

    /** Creates an array for JPA varargs methods like where(Predicate...). */
    public static Expr createElementArray(BlockCreator bc, Class<?> elementType, Expr... elements) {
        return bc.newArray(elementType, elements);
    }

    /** Unboxes Integer to int. */
    public static Expr unboxInteger(BlockCreator bc, Expr boxedValue) {
        return bc.invokeVirtual(INTEGER_INT_VALUE, boxedValue);
    }

    /** Unboxes Boolean to boolean. */
    public static Expr unboxBoolean(BlockCreator bc, Expr boxedValue) {
        return bc.invokeVirtual(BOOLEAN_BOOLEAN_VALUE, boxedValue);
    }

    /** Unboxes Long to long. */
    public static Expr unboxLong(BlockCreator bc, Expr boxedValue) {
        return bc.invokeVirtual(LONG_LONG_VALUE, boxedValue);
    }

    /** Loads constant as bytecode (primitives, BigDecimal, LocalDate/Time). */
    public static Expr loadConstant(BlockCreator bc, Object value) {
        return switch (value) {
            case null -> Const.ofNull(Object.class);
            case String s -> Const.of(s);
            case Integer i -> Const.of(i);
            case Long l -> Const.of(l);
            case Boolean b -> Const.of(b);
            case Double d -> Const.of(d);
            case Float f -> Const.of(f);

            case BigDecimal bd -> bc.new_(
                    ConstructorDesc.of(BigDecimal.class, String.class),
                    Const.of(bd.toString()));

            case LocalDate ld -> bc.invokeStatic(
                    md(LocalDate.class, METHOD_OF, LocalDate.class, int.class, int.class, int.class),
                    Const.of(ld.getYear()), Const.of(ld.getMonthValue()), Const.of(ld.getDayOfMonth()));

            case LocalDateTime ldt -> bc.invokeStatic(
                    md(LocalDateTime.class, METHOD_OF, LocalDateTime.class,
                            int.class, int.class, int.class, int.class, int.class),
                    Const.of(ldt.getYear()), Const.of(ldt.getMonthValue()), Const.of(ldt.getDayOfMonth()),
                    Const.of(ldt.getHour()), Const.of(ldt.getMinute()));

            case LocalTime lt -> bc.invokeStatic(
                    md(LocalTime.class, METHOD_OF, LocalTime.class, int.class, int.class),
                    Const.of(lt.getHour()), Const.of(lt.getMinute()));

            default -> Const.ofNull(Object.class);
        };
    }

    /** Builds cb.construct() for DTO projections from constructor arguments. */
    public static Expr buildConstructorExpression(
            BlockCreator bc,
            Expr cb,
            Expr resultClassHandle,
            List<LambdaExpression> arguments,
            Function<LambdaExpression, Expr> expressionGenerator) {

        int argCount = arguments.size();
        // Use LocalVar for values used across multiple operations (Gizmo2 requirement)
        // Store cb and resultClassHandle in LocalVars since they're passed in from another context
        LocalVar cbLocal = bc.localVar("cbLocal", cb);
        LocalVar resultClassLocal = bc.localVar("resultClassLocal", resultClassHandle);
        LocalVar selectionsArray = bc.localVar("selectionsArray", bc.newEmptyArray(Selection.class, argCount));

        for (int i = 0; i < argCount; i++) {
            // Store each generated expression in a LocalVar before array assignment
            LocalVar argExpression = bc.localVar("arg" + i, expressionGenerator.apply(arguments.get(i)));
            bc.set(selectionsArray.elem(i), argExpression);
        }

        return bc.invokeInterface(CB_CONSTRUCT, cbLocal, resultClassLocal, selectionsArray);
    }

    /** Builds ORDER BY clause with "last call wins" semantics (reverse order). */
    public static void buildOrderByClause(
            BlockCreator bc,
            Expr query,
            Expr cb,
            List<?> sortExpressions,
            Function<SortExpression, Expr> sortKeyGenerator) {

        if (sortExpressions == null || sortExpressions.isEmpty()) {
            return; // No sorting
        }

        // Create array to hold Order objects
        // Use LocalVar for values used across multiple operations (Gizmo2 requirement)
        LocalVar ordersArray = bc.localVar("ordersArray", bc.newEmptyArray(Order.class, sortExpressions.size()));

        // Reverse order for "last call wins" semantics (sortedBy(a).sortedBy(b) → ORDER BY b, a)
        for (int i = 0; i < sortExpressions.size(); i++) {
            int reverseIndex = sortExpressions.size() - 1 - i;
            Object sortExprObj = sortExpressions.get(reverseIndex);

            if (sortExprObj instanceof SortExpression sortExpr) {
                // Generate JPA Expression for the sort key using provided generator
                Expr sortKeyExpr = sortKeyGenerator.apply(sortExpr);

                // Create Order object (ascending or descending)
                Expr order;
                if (sortExpr.direction() == SortDirection.DESCENDING) {
                    order = bc.invokeInterface(CB_DESC, cb, sortKeyExpr);
                } else {
                    order = bc.invokeInterface(CB_ASC, cb, sortKeyExpr);
                }

                // Add to orders array at position i (forward order)
                bc.set(ordersArray.elem(i), order);
            }
        }

        // Apply orderBy to query
        bc.invokeInterface(CQ_ORDER_BY, query, ordersArray);
    }
}
