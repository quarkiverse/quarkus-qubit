package io.quarkiverse.qubit.runtime.internal;

import static java.util.stream.Collectors.joining;

import java.io.Serializable;
import java.lang.invoke.SerializedLambda;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.persistence.NoResultException;
import jakarta.persistence.NonUniqueResultException;

import org.jspecify.annotations.Nullable;

import io.quarkus.arc.Arc;
import io.quarkus.arc.ArcContainer;
import io.quarkus.arc.InstanceHandle;

/**
 * Utility class for lambda reflection operations.
 * <p>
 * Consolidates common reflection utilities used across stream implementations
 * for extracting captured variables and determining call sites.
 * <p>
 * <strong>Design rationale:</strong>
 * Extracted from QubitStreamImpl, JoinStreamImpl, and GroupStreamImpl to
 * eliminate 100% code duplication of these utility methods.
 */
public final class LambdaReflectionUtils {

    private LambdaReflectionUtils() {
        // Utility class - no instantiation
    }

    /** Validates lambda is not null, returns it for inline usage. */
    public static <T> T requireNonNullLambda(T lambda, String paramName, String methodName) {
        if (lambda == null) {
            throw new IllegalArgumentException(
                    paramName + " cannot be null. Use " + methodName + "() with a non-null lambda expression.");
        }
        return lambda;
    }

    /** Counts non-static instance fields (captured variables) in a lambda. */
    public static int countCapturedFields(@Nullable Object lambdaInstance) {
        if (lambdaInstance == null) {
            return 0;
        }

        Class<?> lambdaClass = lambdaInstance.getClass();
        Field[] allFields = lambdaClass.getDeclaredFields();

        int count = 0;
        for (Field field : allFields) {
            if (!Modifier.isStatic(field.getModifiers())) {
                count++;
            }
        }

        return count;
    }

    /** Max stack frames to scan for call site (safety limit). */
    private static final int MAX_STACK_FRAMES = 50;

    /** Returns call site ID via stack walking. Format: "className:methodName:lineNumber". */
    public static String getCallSiteId(Set<String> additionalFilterMethods) {
        StackWalker walker = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);
        return walker.walk(frames -> {
            // Collect frames up to limit for both finding and error reporting
            List<StackWalker.StackFrame> frameList = frames
                    .skip(1) // Skip getCallSiteId itself
                    .limit(MAX_STACK_FRAMES)
                    .toList();

            // Find first frame that passes all filters
            for (StackWalker.StackFrame frame : frameList) {
                if (isValidCallSiteFrame(frame, additionalFilterMethods)) {
                    return frame.getClassName() + ":" + frame.getMethodName() + ":" + frame.getLineNumber();
                }
            }

            // Build error message with scanned frames for debugging
            String scannedFrames = frameList.stream()
                    .limit(10) // Show first 10 frames in error
                    .map(f -> f.getClassName() + "." + f.getMethodName() + ":" + f.getLineNumber())
                    .collect(joining("\n  - ", "\n  - ", ""));

            throw new IllegalStateException(
                    "Could not determine call site. Scanned " + frameList.size() +
                            " frames (limit: " + MAX_STACK_FRAMES + ")." +
                            " This may indicate the query was called from a generated class or via reflection." +
                            " First frames examined:" + scannedFrames);
        });
    }

    /** Returns true if frame is valid call site (not runtime class, fluent method, or synthetic). */
    private static boolean isValidCallSiteFrame(StackWalker.StackFrame frame, Set<String> additionalFilterMethods) {
        String className = frame.getClassName();
        String methodName = frame.getMethodName();

        // Skip Qubit runtime classes
        if (className.startsWith("io.quarkiverse.qubit.runtime.")) {
            return false;
        }

        // Skip fluent API methods
        if (QubitConstants.FLUENT_INTERMEDIATE_METHODS.contains(methodName) ||
                QubitConstants.FLUENT_TERMINAL_METHODS.contains(methodName) ||
                additionalFilterMethods.contains(methodName)) {
            return false;
        }

        // Skip synthetic or generated classes (lambda proxies, etc.)
        return !className.contains("$$") && !className.contains("$Lambda");
    }

    /** Returns call site ID with default filters. */
    public static String getCallSiteId() {
        return getCallSiteId(Set.of());
    }

    /** Returns call site ID with lambda discriminator for same-line queries. */
    public static String getCallSiteId(Set<String> additionalFilterMethods, Object primaryLambda) {
        String baseCallSiteId = getCallSiteId(additionalFilterMethods);
        String lambdaMethodName = extractLambdaMethodName(primaryLambda);
        return baseCallSiteId + ":" + lambdaMethodName;
    }

    /**
     * Unified lambda metadata extraction via {@link SerializedLambda}.
     * Combines method name and captured argument extraction into a single
     * {@code writeReplace()} call, replacing the separate
     * {@code CapturedVariableExtractor} field-reflection path.
     *
     * @param implMethodName the lambda's implementation method name (e.g., "lambda$where$0")
     * @param capturedArgs the values captured by the lambda closure
     */
    public record LambdaInfo(String implMethodName, Object[] capturedArgs) {

        /** Extracts both method name and captured args from a lambda instance. */
        public static LambdaInfo extract(@Nullable Object lambdaInstance) {
            if (lambdaInstance == null) {
                return new LambdaInfo("null", new Object[0]);
            }
            SerializedLambda sl = getSerializedLambda(lambdaInstance);
            int count = sl.getCapturedArgCount();
            Object[] args = new Object[count];
            for (int i = 0; i < count; i++) {
                args[i] = sl.getCapturedArg(i);
            }
            return new LambdaInfo(sl.getImplMethodName(), args);
        }
    }

    /**
     * Extracts lambda implementation method name (e.g., "lambda$where$0") via SerializedLambda.
     * Fails fast on extraction failure - silent fallback would cause build/runtime ID mismatch.
     */
    public static String extractLambdaMethodName(@Nullable Object lambdaInstance) {
        if (lambdaInstance == null) {
            return "null";
        }
        return getSerializedLambda(lambdaInstance).getImplMethodName();
    }

    /**
     * Extracts captured argument values from a lambda instance via
     * {@link SerializedLambda#getCapturedArg(int)}.
     * <p>
     * This replaces the compiler-specific field reflection approach
     * ({@code arg$N} for javac, {@code val$N} for Eclipse, {@code argN} for GraalVM)
     * with the stable {@code SerializedLambda} public API available since Java 8.
     *
     * @param lambdaInstance the lambda to extract captured args from
     * @return the captured argument values, or empty array if none
     */
    public static Object[] extractCapturedArgs(@Nullable Object lambdaInstance) {
        if (lambdaInstance == null) {
            return new Object[0];
        }
        SerializedLambda sl = getSerializedLambda(lambdaInstance);
        int count = sl.getCapturedArgCount();
        if (count == 0) {
            return new Object[0];
        }
        Object[] args = new Object[count];
        for (int i = 0; i < count; i++) {
            args[i] = sl.getCapturedArg(i);
        }
        return args;
    }

    /**
     * Gets {@link SerializedLambda} from a lambda instance via {@code writeReplace()}.
     * Fails fast with actionable diagnostics on all failure modes.
     */
    private static SerializedLambda getSerializedLambda(Object lambdaInstance) {
        Class<?> lambdaClass = lambdaInstance.getClass();

        if (!(lambdaInstance instanceof Serializable)) {
            throw new IllegalStateException(String.format(
                    "Lambda class %s does not implement Serializable. " +
                            "This is a Qubit configuration error - all QuerySpec, BiQuerySpec, and GroupQuerySpec " +
                            "functional interfaces must extend Serializable to enable lambda method name extraction. " +
                            "This is required for unique call site ID generation.",
                    lambdaClass.getName()));
        }

        try {
            Method writeReplace = lambdaClass.getDeclaredMethod("writeReplace");
            writeReplace.setAccessible(true);
            Object serializedForm = writeReplace.invoke(lambdaInstance);
            if (serializedForm instanceof SerializedLambda sl) {
                return sl;
            }

            throw new IllegalStateException(String.format(
                    "Lambda class %s.writeReplace() returned %s instead of SerializedLambda. " +
                            "This is unexpected - the lambda may be using a non-standard implementation.",
                    lambdaClass.getName(),
                    serializedForm != null ? serializedForm.getClass().getName() : "null"));

        } catch (NoSuchMethodException e) {
            throw new IllegalStateException(String.format(
                    "Lambda class %s does not have writeReplace() method. " +
                            "In native image mode, ensure reachability-metadata.json registers this method. " +
                            "Check that QubitNativeImageProcessor is generating proper reflection configuration. " +
                            "Lambda interface type: %s",
                    lambdaClass.getName(),
                    getLambdaInterfaceType(lambdaClass)), e);

        } catch (IllegalStateException e) {
            throw e;

        } catch (Exception e) {
            throw new IllegalStateException(String.format(
                    "Failed to extract SerializedLambda from %s. " +
                            "Cause: %s: %s. " +
                            "This may indicate a security manager restriction or native image reflection issue.",
                    lambdaClass.getName(),
                    e.getClass().getSimpleName(),
                    e.getMessage()), e);
        }
    }

    /** Returns functional interface type(s) for diagnostic messages. */
    private static String getLambdaInterfaceType(Class<?> lambdaClass) {
        Class<?>[] interfaces = lambdaClass.getInterfaces();
        if (interfaces.length > 0) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < interfaces.length; i++) {
                if (i > 0)
                    sb.append(", ");
                sb.append(interfaces[i].getName());
            }
            return sb.toString();
        }
        return "unknown";
    }

    /** Cached registry (Issue #17 fix: prevents InstanceHandle leaks). */
    private static final AtomicReference<QueryExecutorRegistry> CACHED_REGISTRY = new AtomicReference<>();

    private static final Object REGISTRY_LOCK = new Object();

    /**
     * Returns QueryExecutorRegistry via CDI, cached for efficiency.
     * Uses double-checked locking and properly closes InstanceHandle.
     */
    public static QueryExecutorRegistry getQueryExecutorRegistry() {
        // Fast path: return cached instance if available
        QueryExecutorRegistry registry = CACHED_REGISTRY.get();
        if (registry != null) {
            return registry;
        }

        // Slow path: synchronized initialization
        synchronized (REGISTRY_LOCK) {
            // Double-check after acquiring lock
            registry = CACHED_REGISTRY.get();
            if (registry != null) {
                return registry;
            }

            ArcContainer container = Arc.container();
            if (container == null) {
                throw new IllegalStateException(
                        "Arc CDI container not initialized. " +
                                "Ensure this code runs within a Quarkus application context. " +
                                "This error typically occurs when running outside the Quarkus lifecycle " +
                                "(e.g., in unit tests without @QuarkusTest, or in background threads started before CDI initialization).");
            }

            // Use try-with-resources to ensure InstanceHandle is closed
            // This prevents the memory leak identified in Issue #17
            try (InstanceHandle<QueryExecutorRegistry> handle = container.instance(QueryExecutorRegistry.class)) {
                if (!handle.isAvailable()) {
                    throw new IllegalStateException(
                            "QueryExecutorRegistry not available in CDI context. " +
                                    "This typically indicates: " +
                                    "(1) Build-time query executor generation failed - check build logs for QubitProcessor errors, "
                                    +
                                    "(2) The registry bean was not properly initialized, or " +
                                    "(3) The application is running in a non-CDI context.");
                }

                // Cache and return the instance
                // Safe to cache because QueryExecutorRegistry is @ApplicationScoped (singleton)
                // Closing the handle doesn't destroy the bean, just releases the handle reference
                registry = handle.get();
                CACHED_REGISTRY.set(registry);
                return registry;
            }
        }
    }

    /** Clears cached registry (for dev mode hot reload). */
    public static void clearCachedRegistry() {
        CACHED_REGISTRY.set(null);
    }

    /** Extracts captured variables from all lambdas in the list via SerializedLambda. */
    public static void extractFromLambdas(List<?> lambdas, List<Object> destination) {
        for (Object lambda : lambdas) {
            Collections.addAll(destination, extractCapturedArgs(lambda));
        }
    }

    /** Extracts captured variables from a single lambda via SerializedLambda. */
    public static void extractFromSingleLambda(Object lambda, List<Object> destination) {
        Collections.addAll(destination, extractCapturedArgs(lambda));
    }

    /** Validates skip count is non-negative, returns it. */
    public static int validateSkipCount(int n) {
        if (n < 0) {
            throw new IllegalArgumentException("skip count must be >= 0, got: " + n);
        }
        return n;
    }

    /** Validates limit count is non-negative, returns it. */
    public static int validateLimitCount(int n) {
        if (n < 0) {
            throw new IllegalArgumentException("limit count must be >= 0, got: " + n);
        }
        return n;
    }

    /** Returns single result or throws NoResultException/NonUniqueResultException. */
    public static <T> T requireSingleResult(List<T> results) {
        if (results.isEmpty()) {
            throw new NoResultException(
                    "getSingleResult() expected exactly one result but found none");
        }
        if (results.size() > 1) {
            throw new NonUniqueResultException(
                    "getSingleResult() expected exactly one result but found " + results.size());
        }
        return results.getFirst();
    }
}
