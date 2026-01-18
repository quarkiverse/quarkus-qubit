package io.quarkiverse.qubit.runtime;

import org.jboss.logging.Logger;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Extracts captured variable values from lambda instances via reflection.
 * Uses multiple field naming strategies to support different Java compilers:
 * <ul>
 *   <li><b>javac:</b> arg$1, arg$2, ... (Oracle/OpenJDK)</li>
 *   <li><b>Eclipse JDT:</b> val$1, val$2, ...</li>
 *   <li><b>GraalVM:</b> arg0, arg1, ...</li>
 * </ul>
 *
 * <p><strong>Important:</strong> Index-based field extraction is intentionally NOT supported
 * because {@link Class#getDeclaredFields()} does not guarantee field ordering. Using it would
 * cause silent data corruption when captured variables are extracted in the wrong order.
 *
 * <p><strong>Native Image Requirement:</strong> GraalVM/Mandrel 25 or later is required for native builds.
 * This version introduced lambda reflection support, allowing captured variables to be accessed via reflection.
 * Earlier versions do not support reflection on lambda-proxy class fields.
 *
 * @see <a href="https://www.graalvm.org/jdk21/reference-manual/native-image/dynamic-features/Reflection/">GraalVM Reflection Documentation</a>
 */
public final class CapturedVariableExtractor {

    private static final Logger LOG = Logger.getLogger(CapturedVariableExtractor.class);
    private static final Object[] EMPTY_ARRAY = new Object[0];
    private static final Map<String, Field[]> FIELD_CACHE = new ConcurrentHashMap<>();
    private static final boolean IS_NATIVE_IMAGE = isNativeImage();

    /** Field naming strategies in priority order (javac first as most common). */
    private static final List<FieldNamingStrategy> STRATEGIES = List.of(
            new FieldNamingStrategy.JavacStrategy(),
            new FieldNamingStrategy.EclipseStrategy(),
            new FieldNamingStrategy.GraalVMStrategy(),
            new FieldNamingStrategy.IndexBasedStrategy()
    );

    private CapturedVariableExtractor() {
    }

    private static boolean isNativeImage() {
        String vmName = System.getProperty("java.vm.name", "");
        return vmName.contains("Substrate") || vmName.contains("GraalVM");
    }

    /** Extracts captured variable values from lambda instance. */
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

            // Defense-in-depth: validate array is properly sized and has no null entries
            if (fields.length != count) {
                throw new CapturedVariableExtractionException(
                        String.format("Field array length mismatch in %s: expected %d fields but got %d",
                                lambdaClass.getName(), count, fields.length));
            }

            Object[] values = new Object[count];
            for (int i = 0; i < count; i++) {
                if (fields[i] == null) {
                    throw new CapturedVariableExtractionException(
                            String.format("Field at index %d in %s is null (naming strategy mismatch)",
                                    i, lambdaClass.getName()));
                }
                values[i] = fields[i].get(lambdaInstance);
            }

            LOG.tracef("Extracted %d captured variables from %s", count, lambdaClass.getName());
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
                String availableFields = listAvailableFields(lambdaClass);
                if (IS_NATIVE_IMAGE && "none".equals(availableFields)) {
                    throw new NoSuchFieldException(String.format(
                            "Could not find captured variable at index %d in lambda class %s. " +
                            "NATIVE IMAGE ERROR: Lambda reflection metadata may not be properly configured. " +
                            "Ensure you are using GraalVM/Mandrel 25+ and that the reachability-metadata.json " +
                            "was generated during the native build. Check that the quarkus-qubit extension is " +
                            "properly included in your build. " +
                            "See: https://www.graalvm.org/latest/reference-manual/native-image/metadata/",
                            i, lambdaClass.getName()));
                }
                throw new NoSuchFieldException(String.format(
                        "Could not find captured variable at index %d in lambda class %s. " +
                        "Your Java compiler uses an unsupported field naming convention. " +
                        "Supported compilers: Oracle/OpenJDK javac (arg$N), Eclipse JDT (val$N), GraalVM (argN). " +
                        "Available fields in lambda: [%s]. " +
                        "To fix: (1) Use a supported compiler, or (2) File an issue to add support for your compiler's naming convention.",
                        i, lambdaClass.getName(), availableFields));
            }
            fields[i] = field;
        }

        // Use putIfAbsent for atomic check-and-put to prevent duplicate computation in races
        Field[] existing = FIELD_CACHE.putIfAbsent(cacheKey, fields);
        if (existing != null) {
            // Another thread already cached this, use their version for consistency
            return existing;
        }
        LOG.debugf("Cached field lookups for %s (%d fields)", lambdaClass.getName(), count);
        return fields;
    }

    /** Tries each naming strategy in order, returns field or null. */
    private static Field findFieldUsingStrategies(Class<?> lambdaClass, int index) {
        for (FieldNamingStrategy strategy : STRATEGIES) {
            Optional<Field> field = strategy.findCapturedField(lambdaClass, index);
            if (field.isPresent()) {
                LOG.tracef("Resolved captured variable field at index %d in %s using strategy: %s (field name: %s)",
                        index, lambdaClass.getName(), strategy.getStrategyName(), field.get().getName());
                return field.get();
            }
        }

        LOG.debugf("No strategy found field at index %d in lambda class %s", index, lambdaClass.getName());
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
        LOG.debug("Cleared captured variable field cache");
    }

    /**
     * Returns size of field cache.
     */
    public static int getCacheSize() {
        return FIELD_CACHE.size();
    }
}
