package io.quarkiverse.qubit.it.util;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.InaccessibleObjectException;
import java.lang.reflect.Modifier;
import java.util.IdentityHashMap;
import java.util.Set;

/**
 * Reflection-based deep size estimator for heap usage measurement.
 * Provides rough estimates (±20% accuracy) for baseline comparisons.
 */
public final class MemoryEstimator {

    // Object header size (compressed oops on 64-bit JVM)
    private static final int OBJECT_HEADER_SIZE = 12;
    // Reference size (compressed oops)
    private static final int REFERENCE_SIZE = 4;
    // Array header size
    private static final int ARRAY_HEADER_SIZE = 16;

    private MemoryEstimator() {
        // Utility class
    }

    /**
     * Estimates the deep size of an object graph in bytes.
     * Traverses all reachable objects via reflection.
     */
    public static long estimateDeepSize(Object obj) {
        if (obj == null) {
            return 0;
        }
        Set<Object> visited = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
        return estimateSize(obj, visited);
    }

    private static long estimateSize(Object obj, Set<Object> visited) {
        if (obj == null || visited.contains(obj)) {
            return 0;
        }

        Class<?> clazz = obj.getClass();

        // Skip Class objects to avoid infinite recursion
        if (clazz == Class.class) {
            return 0;
        }

        visited.add(obj);

        if (clazz.isArray()) {
            return estimateArraySize(obj, visited);
        }

        return estimateObjectSize(obj, clazz, visited);
    }

    private static long estimateArraySize(Object array, Set<Object> visited) {
        Class<?> componentType = array.getClass().getComponentType();
        int length = Array.getLength(array);

        long size = ARRAY_HEADER_SIZE;

        if (componentType.isPrimitive()) {
            size += length * getPrimitiveSize(componentType);
        } else {
            size += length * REFERENCE_SIZE;
            for (int i = 0; i < length; i++) {
                size += estimateSize(Array.get(array, i), visited);
            }
        }

        return alignTo8(size);
    }

    private static long estimateObjectSize(Object obj, Class<?> clazz, Set<Object> visited) {
        long size = OBJECT_HEADER_SIZE;

        // Walk up the class hierarchy
        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            for (Field field : current.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())) {
                    continue;
                }

                Class<?> fieldType = field.getType();

                if (fieldType.isPrimitive()) {
                    size += getPrimitiveSize(fieldType);
                } else {
                    size += REFERENCE_SIZE;
                    try {
                        field.setAccessible(true);
                        Object fieldValue = field.get(obj);
                        size += estimateSize(fieldValue, visited);
                    } catch (IllegalAccessException | InaccessibleObjectException e) {
                        // Skip inaccessible fields
                    }
                }
            }
            current = current.getSuperclass();
        }

        return alignTo8(size);
    }

    private static int getPrimitiveSize(Class<?> type) {
        if (type == boolean.class || type == byte.class)
            return 1;
        if (type == char.class || type == short.class)
            return 2;
        if (type == int.class || type == float.class)
            return 4;
        if (type == long.class || type == double.class)
            return 8;
        return 4; // Default
    }

    private static long alignTo8(long size) {
        return (size + 7) & ~7;
    }
}
