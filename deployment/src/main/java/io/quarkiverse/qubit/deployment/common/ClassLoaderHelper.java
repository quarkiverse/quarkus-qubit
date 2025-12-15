package io.quarkiverse.qubit.deployment.common;

import io.quarkiverse.qubit.deployment.ast.LambdaExpression;
import io.quarkus.logging.Log;
import org.objectweb.asm.Type;

/**
 * Utility class for class loading operations during bytecode analysis.
 *
 * <p>This class provides a centralized location for class loading logic
 * that needs to work at build-time when classes may not be available
 * on the classpath.
 *
 * @see EntityClassInfo
 */
public final class ClassLoaderHelper {

    private ClassLoaderHelper() {
        // Utility class
    }

    /**
     * Attempts to load a class using multiple classloaders.
     *
     * <p>Strategy:
     * <ol>
     *   <li>Try the context class loader first</li>
     *   <li>Fall back to this class's class loader</li>
     *   <li>Return null if the class cannot be loaded</li>
     * </ol>
     *
     * <p>Uses {@code initialize=false} to avoid running static initializers,
     * which is appropriate for build-time analysis.
     *
     * @param className the fully qualified class name
     * @return the loaded Class, or null if not loadable
     */
    public static Class<?> tryLoadClass(String className) {
        if (className == null || className.isEmpty()) {
            return null;
        }

        // Try classloaders in preference order
        ClassLoader[] loaders = {
            Thread.currentThread().getContextClassLoader(),
            ClassLoaderHelper.class.getClassLoader()
        };

        for (ClassLoader loader : loaders) {
            if (loader == null) {
                continue;  // Context classloader may be null in some environments
            }
            try {
                return Class.forName(className, false, loader);
            } catch (ClassNotFoundException ignored) {
                // Try next classloader
            }
        }

        Log.debugf("Class not loadable at build-time: %s", className);
        return null;
    }

    /**
     * Extracts entity class information from a constant expression.
     *
     * <p>Strategy: Extract className early when Type is available, then attempt
     * class loading. If loading fails, preserve className for runtime resolution.
     *
     * @param expr the expression to extract entity class info from
     * @return entity class info, never null
     */
    public static EntityClassInfo extractEntityClassInfo(LambdaExpression expr) {
        if (expr instanceof LambdaExpression.Constant constant) {
            Object value = constant.value();
            if (value instanceof Type asmType) {
                // Extract className FIRST (before attempting class loading)
                String className = asmType.getClassName();

                // Attempt to load the class
                Class<?> loadedClass = tryLoadClass(className);

                if (loadedClass != null) {
                    // Successfully loaded - className not needed
                    return new EntityClassInfo(loadedClass, null);
                } else {
                    // Failed to load - preserve className for code generation
                    Log.debugf("Entity class not loadable at analysis time: %s (will resolve at code generation)", className);
                    return new EntityClassInfo(Object.class, className);
                }
            } else if (value instanceof Class<?> clazz) {
                return new EntityClassInfo(clazz, null);
            }
        }
        Log.warnf("Expected Class constant for entity class, got: %s", expr);
        return new EntityClassInfo(Object.class, null);
    }

    /**
     * Checks if a class is loadable at build-time.
     *
     * @param className the fully qualified class name
     * @return true if the class can be loaded
     */
    public static boolean isClassLoadable(String className) {
        return tryLoadClass(className) != null;
    }
}
