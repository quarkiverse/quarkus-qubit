package io.quarkiverse.qubit.runtime;

import io.quarkus.arc.Arc;
import io.quarkus.arc.ArcContainer;
import io.quarkus.arc.InstanceHandle;
import jakarta.persistence.NoResultException;
import jakarta.persistence.NonUniqueResultException;

import java.io.Serializable;
import java.lang.invoke.SerializedLambda;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static java.util.stream.Collectors.joining;

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

    // ========== Lambda Validation Utilities ==========

    /** Validates lambda is not null, returns it for inline usage. */
    public static <T> T requireNonNullLambda(T lambda, String paramName, String methodName) {
        if (lambda == null) {
            throw new IllegalArgumentException(
                    paramName + " cannot be null. Use " + methodName + "() with a non-null lambda expression.");
        }
        return lambda;
    }

    /** Counts non-static instance fields (captured variables) in a lambda. */
    public static int countCapturedFields(Object lambdaInstance) {
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
     * Extracts lambda implementation method name (e.g., "lambda$where$0") via SerializedLambda.
     * Fails fast on extraction failure - silent fallback would cause build/runtime ID mismatch.
     */
    public static String extractLambdaMethodName(Object lambdaInstance) {
        if (lambdaInstance == null) {
            return "null";
        }

        Class<?> lambdaClass = lambdaInstance.getClass();

        // Validate that the lambda implements Serializable (required for SerializedLambda extraction)
        if (!(lambdaInstance instanceof Serializable)) {
            throw new IllegalStateException(String.format(
                    "Lambda class %s does not implement Serializable. " +
                    "This is a Qubit configuration error - all QuerySpec, BiQuerySpec, and GroupQuerySpec " +
                    "functional interfaces must extend Serializable to enable lambda method name extraction. " +
                    "This is required for unique call site ID generation.",
                    lambdaClass.getName()));
        }

        // Extract using SerializedLambda (the only reliable approach)
        try {
            Method writeReplace = lambdaClass.getDeclaredMethod("writeReplace");
            writeReplace.setAccessible(true);
            Object serializedForm = writeReplace.invoke(lambdaInstance);
            if (serializedForm instanceof SerializedLambda sl) {
                return sl.getImplMethodName();
            }

            // writeReplace returned something other than SerializedLambda
            throw new IllegalStateException(String.format(
                    "Lambda class %s.writeReplace() returned %s instead of SerializedLambda. " +
                    "This is unexpected - the lambda may be using a non-standard implementation.",
                    lambdaClass.getName(),
                    serializedForm != null ? serializedForm.getClass().getName() : "null"));

        } catch (NoSuchMethodException e) {
            // Lambda doesn't have writeReplace method
            throw new IllegalStateException(String.format(
                    "Lambda class %s does not have writeReplace() method. " +
                    "In native image mode, ensure reachability-metadata.json registers this method. " +
                    "Check that QubitNativeImageProcessor is generating proper reflection configuration. " +
                    "Lambda interface type: %s",
                    lambdaClass.getName(),
                    getLambdaInterfaceType(lambdaClass)), e);

        } catch (IllegalStateException e) {
            // Re-throw our own exceptions
            throw e;

        } catch (Exception e) {
            // Any other reflection error
            throw new IllegalStateException(String.format(
                    "Failed to extract lambda method name from %s via SerializedLambda. " +
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
                if (i > 0) sb.append(", ");
                sb.append(interfaces[i].getName());
            }
            return sb.toString();
        }
        return "unknown";
    }

    // ========== CDI Lookup Utilities ==========

    /** Cached registry (Issue #17 fix: prevents InstanceHandle leaks). */
    private static final AtomicReference<QueryExecutorRegistry> CACHED_REGISTRY =
            new AtomicReference<>();

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
                            "(1) Build-time query executor generation failed - check build logs for QubitProcessor errors, " +
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

    // ========== Captured Variable Extraction Utilities ==========

    /** Extracts captured variables from lambda list with per-lambda validation. */
    public static int extractFromLambdas(List<?> lambdas, String lambdaType, String callSiteId,
                                          List<Object> destination, int remainingCount) {
        for (Object lambda : lambdas) {
            if (remainingCount == 0) {
                break;
            }
            remainingCount = extractFromSingleLambda(lambda, lambdaType, callSiteId, destination, remainingCount);
        }
        return remainingCount;
    }

    /** Extracts captured variables from single lambda with validation. */
    public static int extractFromSingleLambda(Object lambda, String lambdaType, String callSiteId,
                                               List<Object> destination, int remainingCount) {
        int capturedCount = countCapturedFields(lambda);
        if (capturedCount > 0) {
            Object[] values = CapturedVariableExtractor.extract(lambda, capturedCount);

            // Per-lambda validation for easier debugging
            if (values.length != capturedCount) {
                throw new IllegalStateException(String.format(
                        "Captured variable extraction mismatch for %s in %s: " +
                        "expected %d values but extractor returned %d.",
                        lambdaType, callSiteId, capturedCount, values.length));
            }

            java.util.Collections.addAll(destination, values);
            remainingCount -= capturedCount;
        }
        return remainingCount;
    }

    // ========== Pagination Validation Utilities ==========

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

    // ========== Single Result Validation Utilities ==========

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
        return results.get(0);
    }
}
