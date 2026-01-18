package io.quarkiverse.qubit.deployment.common;

import io.quarkiverse.qubit.deployment.ast.LambdaExpression;
import io.quarkus.logging.Log;
import org.objectweb.asm.Type;

/** Build-time class loading (context classloader → this class's classloader → null). */
public final class ClassLoaderHelper {

    private ClassLoaderHelper() {}

    /** Attempts to load class without running static initializers. */
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

    /** Extracts entity class info; preserves className for runtime resolution if loading fails. */
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

    public static boolean isClassLoadable(String className) {
        return tryLoadClass(className) != null;
    }
}
