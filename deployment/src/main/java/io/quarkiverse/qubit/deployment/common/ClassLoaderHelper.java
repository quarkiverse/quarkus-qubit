package io.quarkiverse.qubit.deployment.common;

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

    /**
     * Extracts entity class info WITHOUT loading the class.
     * <p>
     * This method is safe to call from parallel streams because it never calls Class.forName().
     * It always returns a placeholder that defers class loading to runtime.
     */
    public static EntityClassInfo extractEntityClassInfo(LambdaExpression expr) {
        if (expr instanceof LambdaExpression.Constant(var value, _)) {
            if (value instanceof Type asmType) {
                String className = asmType.getClassName();
                Log.debugf("Entity class deferred to runtime: %s", className);
                return EntityClassInfo.placeholder(className);
            } else if (value instanceof Class<?> clazz) {
                return EntityClassInfo.of(clazz);
            }
        }
        Log.debugf("Expected Class constant for entity class, got: %s", expr);
        return EntityClassInfo.of(Object.class);
    }
}
