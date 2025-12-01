package io.quarkiverse.qubit.deployment.common;

import io.quarkiverse.qubit.deployment.ast.LambdaExpression;
import org.jboss.logging.Logger;
import org.objectweb.asm.Type;

/**
 * Utility class for class loading operations during bytecode analysis.
 *
 * <p>This class provides a centralized location for class loading logic
 * that needs to work at build-time when classes may not be available
 * on the classpath.
 *
 * <p>Extracted from SubqueryAnalyzer (ARCH-008 continuation) to provide
 * a reusable utility for class loading with fallback strategies.
 *
 * @see EntityClassInfo
 */
public final class ClassLoaderHelper {

    private static final Logger log = Logger.getLogger(ClassLoaderHelper.class);

    private ClassLoaderHelper() {
        // Utility class
    }

    /**
     * Attempts to load a class using multiple classloaders.
     *
     * <p>Strategy:
     * <ol>
     *   <li>Try the context class loader first</li>
     *   <li>Fall back to the current class's class loader</li>
     *   <li>Return null if the class cannot be loaded</li>
     * </ol>
     *
     * @param className the fully qualified class name
     * @return the loaded Class, or null if not loadable
     */
    public static Class<?> tryLoadClass(String className) {
        if (className == null || className.isEmpty()) {
            return null;
        }

        try {
            // Try context class loader first
            return Class.forName(className, false, Thread.currentThread().getContextClassLoader());
        } catch (ClassNotFoundException e1) {
            try {
                // Fallback to this class's class loader
                return Class.forName(className);
            } catch (ClassNotFoundException e2) {
                // Class not loadable at build-time
                log.debugf("Class not loadable at build-time: %s", className);
                return null;
            }
        }
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
                    log.debugf("Entity class not loadable at analysis time: %s (will resolve at code generation)", className);
                    return new EntityClassInfo(Object.class, className);
                }
            } else if (value instanceof Class<?> clazz) {
                return new EntityClassInfo(clazz, null);
            }
        }
        log.warnf("Expected Class constant for entity class, got: %s", expr);
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
