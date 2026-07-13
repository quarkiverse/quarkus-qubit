package io.quarkiverse.qubit.runtime.internal;

import static java.util.stream.Collectors.joining;

import java.io.Serializable;
import java.lang.invoke.SerializedLambda;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
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

    static final Object[] EMPTY_OBJECT_ARRAY = {};

    private LambdaReflectionUtils() {
    }

    /** Validates lambda is not null, returns it for inline usage. */
    public static <T> T requireNonNullLambda(T lambda, String paramName, String methodName) {
        if (lambda == null) {
            throw new IllegalArgumentException(
                    paramName + " cannot be null. Use " + methodName + "() with a non-null lambda expression.");
        }
        return lambda;
    }

    /** Max stack frames to scan for call site (safety limit). */
    private static final int MAX_STACK_FRAMES = 50;

    private static final StackWalker WALKER = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);

    /** Returns call site ID via stack walking. Format: "className:methodName:lineNumber". */
    public static String getCallSiteId(Set<String> additionalFilterMethods) {
        return WALKER.walk(frames -> frames
                .skip(1).limit(MAX_STACK_FRAMES)
                .filter(f -> isValidCallSiteFrame(f, additionalFilterMethods))
                .findFirst()
                .map(f -> f.getClassName() + ":" + f.getMethodName() + ":" + f.getLineNumber())
                .orElseThrow(() -> new IllegalStateException("Could not determine Qubit call site.")));
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
            return EMPTY_OBJECT_ARRAY;
        }
        SerializedLambda sl = getSerializedLambda(lambdaInstance);
        int count = sl.getCapturedArgCount();
        if (count == 0) {
            return EMPTY_OBJECT_ARRAY;
        }
        Object[] args = new Object[count];
        for (int i = 0; i < count; i++) {
            args[i] = sl.getCapturedArg(i);
        }
        return args;
    }

    // ponytail: ClassValue stores on the Class object itself — no ConcurrentHashMap, zero GC pressure
    private static final ClassValue<Method> WRITE_REPLACE_CACHE = new ClassValue<>() {
        @Override
        protected Method computeValue(Class<?> type) {
            try {
                Method m = type.getDeclaredMethod("writeReplace");
                m.setAccessible(true);
                return m;
            } catch (NoSuchMethodException e) {
                throw new IllegalStateException(String.format(
                        "Lambda class %s does not have writeReplace() method. " +
                                "In native image mode, ensure reachability-metadata.json registers this method. " +
                                "Lambda interface type: %s",
                        type.getName(), getLambdaInterfaceType(type)), e);
            }
        }
    };

    /**
     * Gets {@link SerializedLambda} from a lambda instance via cached {@code writeReplace()}.
     * The Method lookup is cached per lambda class via ClassValue.
     */
    private static SerializedLambda getSerializedLambda(Object lambdaInstance) {
        if (!(lambdaInstance instanceof Serializable)) {
            throw new IllegalStateException(String.format(
                    "Lambda class %s does not implement Serializable. " +
                            "All QuerySpec functional interfaces must extend Serializable.",
                    lambdaInstance.getClass().getName()));
        }

        try {
            Method writeReplace = WRITE_REPLACE_CACHE.get(lambdaInstance.getClass());
            Object serializedForm = writeReplace.invoke(lambdaInstance);
            if (serializedForm instanceof SerializedLambda sl) {
                return sl;
            }
            throw new IllegalStateException(String.format(
                    "Lambda class %s.writeReplace() returned %s instead of SerializedLambda.",
                    lambdaInstance.getClass().getName(),
                    serializedForm != null ? serializedForm.getClass().getName() : "null"));
        } catch (IllegalStateException e) {
            throw e;
        } catch (InvocationTargetException | IllegalAccessException e) {
            throw new IllegalStateException(
                    "Failed to extract SerializedLambda from " + lambdaInstance.getClass().getName(), e);
        }
    }

    /** Returns functional interface type(s) for diagnostic messages. */
    private static String getLambdaInterfaceType(Class<?> lambdaClass) {
        Class<?>[] interfaces = lambdaClass.getInterfaces();
        if (interfaces.length == 0) {
            return "unknown";
        }
        return Arrays.stream(interfaces).map(Class::getName).collect(joining(", "));
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
