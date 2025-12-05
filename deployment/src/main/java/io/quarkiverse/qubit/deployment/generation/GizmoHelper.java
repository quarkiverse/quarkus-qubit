package io.quarkiverse.qubit.deployment.generation;

import static io.quarkiverse.qubit.deployment.generation.MethodDescriptors.*;

import io.quarkiverse.qubit.deployment.analysis.InvokeDynamicScanner;
import io.quarkus.gizmo.FieldDescriptor;
import io.quarkus.gizmo.MethodCreator;
import io.quarkus.gizmo.ResultHandle;
import jakarta.persistence.criteria.JoinType;

/**
 * Utility class for common Gizmo bytecode generation patterns.
 *
 * <p>This class provides reusable methods for generating bytecode using
 * the Gizmo library, avoiding duplication across expression builders.
 *
 * <p>Extracted from SubqueryExpressionBuilder (ARCH-008 continuation).
 */
public final class GizmoHelper {

    private GizmoHelper() {
        // Utility class
    }

    /**
     * Loads an entity class for JPA FROM clause.
     *
     * <p>Handles both direct class references and placeholder class names
     * for classes that couldn't be loaded at build-time.
     *
     * <p>For direct class references, generates:
     * <pre>
     * Person.class
     * </pre>
     *
     * <p>For placeholder class names, generates:
     * <pre>
     * Class.forName("io.quarkiverse.qubit.it.Person")
     * </pre>
     *
     * @param method the method creator for bytecode generation
     * @param entityClass the entity class (may be Object.class for placeholders)
     * @param entityClassName optional entity class name (for placeholders)
     * @return ResultHandle for the entity class
     */
    public static ResultHandle loadEntityClass(MethodCreator method, Class<?> entityClass, String entityClassName) {
        // Placeholder case: Load class by name at runtime
        if (entityClassName != null) {
            ResultHandle classNameHandle = method.load(entityClassName);
            return method.invokeStaticMethod(CLASS_FOR_NAME, classNameHandle);
        }
        // Normal case: Direct class reference
        return method.loadClass(entityClass);
    }

    /**
     * Loads JPA JoinType enum value based on Qubit JoinType.
     *
     * <p>Extracted from QueryExecutorClassGenerator (ARCH-008 continuation).
     *
     * @param method the method creator
     * @param joinType the Qubit join type (INNER or LEFT)
     * @return ResultHandle to the JPA JoinType enum value
     */
    public static ResultHandle loadJpaJoinType(MethodCreator method, InvokeDynamicScanner.JoinType joinType) {
        String jpaJoinTypeName = (joinType == InvokeDynamicScanner.JoinType.LEFT) ? "LEFT" : "INNER";
        return method.readStaticField(
            FieldDescriptor.of(JoinType.class, jpaJoinTypeName, JoinType.class)
        );
    }

    /**
     * Creates a single-element array of a given type.
     *
     * <p>Useful for JPA methods that take varargs like {@code where(Predicate...)}.
     *
     * @param method the method creator
     * @param elementType the array element type
     * @param element the element to put in the array
     * @return ResultHandle for the array
     */
    public static ResultHandle createSingleElementArray(MethodCreator method, Class<?> elementType, ResultHandle element) {
        ResultHandle array = method.newArray(elementType, 1);
        method.writeArrayValue(array, 0, element);
        return array;
    }

    /**
     * Creates a two-element array of a given type.
     *
     * <p>Useful for JPA methods like {@code cb.and(Predicate, Predicate)}.
     *
     * @param method the method creator
     * @param elementType the array element type
     * @param first the first element
     * @param second the second element
     * @return ResultHandle for the array
     */
    public static ResultHandle createTwoElementArray(MethodCreator method, Class<?> elementType,
                                                       ResultHandle first, ResultHandle second) {
        ResultHandle array = method.newArray(elementType, 2);
        method.writeArrayValue(array, 0, first);
        method.writeArrayValue(array, 1, second);
        return array;
    }

    /**
     * Unboxes an Integer ResultHandle to int.
     *
     * @param method the method creator
     * @param boxedValue the boxed Integer value
     * @return ResultHandle for the unboxed int value
     */
    public static ResultHandle unboxInteger(MethodCreator method, ResultHandle boxedValue) {
        return method.invokeVirtualMethod(INTEGER_INT_VALUE, boxedValue);
    }

    /**
     * Unboxes a Boolean ResultHandle to boolean.
     *
     * @param method the method creator
     * @param boxedValue the boxed Boolean value
     * @return ResultHandle for the unboxed boolean value
     */
    public static ResultHandle unboxBoolean(MethodCreator method, ResultHandle boxedValue) {
        return method.invokeVirtualMethod(BOOLEAN_BOOLEAN_VALUE, boxedValue);
    }

    /**
     * Unboxes a Long ResultHandle to long.
     *
     * @param method the method creator
     * @param boxedValue the boxed Long value
     * @return ResultHandle for the unboxed long value
     */
    public static ResultHandle unboxLong(MethodCreator method, ResultHandle boxedValue) {
        return method.invokeVirtualMethod(LONG_LONG_VALUE, boxedValue);
    }
}
