package io.quarkiverse.qubit.runtime;

import io.quarkus.logging.Log;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Extracts captured variable values from lambda instances via reflection.
 * Uses multiple field naming strategies to support different Java compilers:
 * - javac: arg$1, arg$2, ... (Oracle/OpenJDK)
 * - Eclipse JDT: val$1, val$2, ...
 * - GraalVM: arg0, arg1, ...
 * - Index-based fallback: iterates all fields
 */
public final class CapturedVariableExtractor {

    private static final Object[] EMPTY_ARRAY = new Object[0];
    private static final Map<String, Field[]> FIELD_CACHE = new ConcurrentHashMap<>();

    /**
     * Field naming strategies in order of priority.
     * Javac is first as it's the most common compiler.
     */
    private static final List<FieldNamingStrategy> STRATEGIES = List.of(
            new FieldNamingStrategy.JavacStrategy(),
            new FieldNamingStrategy.EclipseStrategy(),
            new FieldNamingStrategy.GraalVMStrategy(),
            new FieldNamingStrategy.IndexBasedStrategy()
    );

    private CapturedVariableExtractor() {
    }

    /**
     * Extracts captured variable values from lambda instance.
     */
    public static Object[] extract(Object lambdaInstance, int count) {
        if (lambdaInstance == null) {
            throw new IllegalArgumentException("Lambda instance cannot be null");
        }

        if (count == 0) {
            return EMPTY_ARRAY;
        }

        try {
            Class<?> lambdaClass = lambdaInstance.getClass();
            Field[] fields = getFields(lambdaClass, count);

            Object[] values = new Object[count];
            for (int i = 0; i < count; i++) {
                values[i] = fields[i].get(lambdaInstance);
            }

            Log.tracef("Extracted %d captured variables from %s", count, lambdaClass.getName());
            return values;

        } catch (IllegalAccessException | NoSuchFieldException e) {
            throw new CapturedVariableExtractionException("Failed to extract captured variables from " +
                                      lambdaInstance.getClass().getName(), e);
        }
    }

    private static Field[] getFields(Class<?> lambdaClass, int count) throws NoSuchFieldException {
        String cacheKey = lambdaClass.getName() + ":" + count;
        Field[] cached = FIELD_CACHE.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        Field[] fields = new Field[count];
        for (int i = 0; i < count; i++) {
            Field field = findFieldUsingStrategies(lambdaClass, i);
            if (field == null) {
                throw new NoSuchFieldException(String.format(
                        "Could not find captured variable at index %d in lambda class %s using any known strategy. " +
                        "Available fields: %s",
                        i, lambdaClass.getName(), listAvailableFields(lambdaClass)));
            }
            fields[i] = field;
        }

        FIELD_CACHE.put(cacheKey, fields);
        Log.debugf("Cached field lookups for %s (%d fields)", lambdaClass.getName(), count);
        return fields;
    }

    /**
     * Attempts to find the captured variable field at the specified index
     * using all available strategies in order.
     *
     * @param lambdaClass the lambda class to search
     * @param index the zero-based index of the captured variable
     * @return the field if found, null otherwise
     */
    private static Field findFieldUsingStrategies(Class<?> lambdaClass, int index) {
        for (FieldNamingStrategy strategy : STRATEGIES) {
            Optional<Field> field = strategy.findCapturedField(lambdaClass, index);
            if (field.isPresent()) {
                Log.tracef("Resolved captured variable field at index %d in %s using strategy: %s (field name: %s)",
                        index, lambdaClass.getName(), strategy.getStrategyName(), field.get().getName());
                return field.get();
            }
        }

        Log.debugf("No strategy found field at index %d in lambda class %s", index, lambdaClass.getName());
        return null;
    }

    private static String listAvailableFields(Class<?> lambdaClass) {
        Field[] allFields = lambdaClass.getDeclaredFields();
        if (allFields.length == 0) {
            return "none";
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < allFields.length; i++) {
            if (i > 0) sb.append(", ");
            sb.append(allFields[i].getName());
            sb.append(" (").append(allFields[i].getType().getSimpleName()).append(")");
        }
        return sb.toString();
    }

    /**
     * Clears captured variable field cache.
     */
    public static void clearCache() {
        FIELD_CACHE.clear();
        Log.debug("Cleared captured variable field cache");
    }

    /**
     * Returns size of field cache.
     */
    public static int getCacheSize() {
        return FIELD_CACHE.size();
    }
}
