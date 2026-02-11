package io.quarkiverse.qubit.deployment.common;

import java.util.concurrent.ConcurrentHashMap;

import org.objectweb.asm.Type;

import io.quarkiverse.qubit.deployment.ast.LambdaExpression;
import io.quarkus.logging.Log;

/**
 * Build-time class loading utilities.
 * <p>
 * IMPORTANT: During bytecode analysis (which may run in parallel streams), we avoid
 * Class.forName() because it causes JVM-level class loading deadlocks in JDK 25+
 * when multiple ForkJoinPool workers attempt concurrent class loading through
 * Quarkus's complex classloader hierarchy.
 * <p>
 * Instead, we defer class loading to code generation time using className placeholders.
 * The generated code uses Class.forName() at runtime when the class is actually needed.
 */
public final class ClassLoaderHelper {

    private ClassLoaderHelper() {
    }

    /** Cache for loaded classes to avoid repeated Class.forName() calls. */
    private static final ConcurrentHashMap<String, Class<?>> CLASS_CACHE = new ConcurrentHashMap<>();

    /**
     * Attempts to load class with caching. Thread-safe via ConcurrentHashMap.computeIfAbsent.
     * <p>
     * CAUTION: Avoid calling this during parallel stream processing to prevent deadlocks.
     * Use extractEntityClassInfo() instead, which defers loading to runtime.
     */
    public static Class<?> tryLoadClass(String className) {
        if (className == null || className.isEmpty()) {
            return null;
        }

        // Check cache first (fast path, no locking)
        Class<?> cached = CLASS_CACHE.get(className);
        if (cached != null) {
            return cached;
        }

        // Try to load and cache
        ClassLoader[] loaders = {
                Thread.currentThread().getContextClassLoader(),
                ClassLoaderHelper.class.getClassLoader()
        };

        for (ClassLoader loader : loaders) {
            if (loader == null) {
                continue;
            }
            try {
                Class<?> loaded = Class.forName(className, false, loader);
                CLASS_CACHE.put(className, loaded);
                return loaded;
            } catch (ClassNotFoundException _) {
                // Try next classloader
            }
        }

        Log.debugf("Class not loadable at build-time: %s", className);
        return null;
    }

    /**
     * Extracts entity class info WITHOUT loading the class.
     * <p>
     * This method is safe to call from parallel streams because it never calls Class.forName().
     * It always returns a placeholder that defers class loading to runtime.
     */
    public static EntityClassInfo extractEntityClassInfo(LambdaExpression expr) {
        if (expr instanceof LambdaExpression.Constant(var value, _)) {
            if (value instanceof Type asmType) {
                // Extract className and use placeholder - NO class loading during analysis
                // This avoids JVM-level class loading deadlocks in parallel ForkJoinPool workers
                String className = asmType.getClassName();
                Log.debugf("Entity class deferred to runtime: %s", className);
                return EntityClassInfo.placeholder(className);
            } else if (value instanceof Class<?> clazz) {
                // Already have the class object (rare case)
                return EntityClassInfo.of(clazz);
            }
        }
        Log.debugf("Expected Class constant for entity class, got: %s", expr);
        return EntityClassInfo.of(Object.class);
    }

    /**
     * Extracts the simple class name from a fully qualified class name.
     * E.g., "com.example.Person" → "Person"
     */
    public static String getSimpleName(String fullyQualifiedClassName) {
        int lastDot = fullyQualifiedClassName.lastIndexOf('.');
        return lastDot >= 0 ? fullyQualifiedClassName.substring(lastDot + 1) : fullyQualifiedClassName;
    }

    /**
     * Gets the effective simple name from either a Class or className.
     * Handles placeholder case where clazz is Object.class and className is the actual name.
     */
    public static String getEffectiveSimpleName(Class<?> clazz, String className) {
        return className != null ? getSimpleName(className) : clazz.getSimpleName();
    }

    public static boolean isClassLoadable(String className) {
        return tryLoadClass(className) != null;
    }
}
