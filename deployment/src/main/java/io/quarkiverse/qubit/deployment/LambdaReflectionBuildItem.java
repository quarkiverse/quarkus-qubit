package io.quarkiverse.qubit.deployment;

import io.quarkus.builder.item.MultiBuildItem;

/**
 * Build item that captures lambda call site information for native image reflection.
 * <p>
 * In GraalVM native image, lambda classes are synthetic and their {@code writeReplace()}
 * method is not registered for reflection by default. This build item collects
 * information about lambdas so that the appropriate reflection configuration
 * can be generated in {@code reachability-metadata.json}.
 * <p>
 * The generated config uses the GraalVM 25+ lambda syntax:
 *
 * <pre>
 * {
 *   "type": {
 *     "lambda": {
 *       "declaringClass": "com.example.MyClass",
 *       "interfaces": ["io.quarkiverse.qubit.QuerySpec"]
 *     }
 *   },
 *   "methods": [
 *     { "name": "writeReplace", "parameterTypes": [] }
 *   ]
 * }
 * </pre>
 */
public final class LambdaReflectionBuildItem extends MultiBuildItem {

    private final String declaringClass;
    private final String interfaceType;

    /**
     * Creates a lambda reflection build item.
     *
     * @param declaringClass the fully qualified name of the class containing the lambda
     * @param interfaceType the fully qualified name of the lambda interface (e.g., QuerySpec)
     */
    public LambdaReflectionBuildItem(String declaringClass, String interfaceType) {
        this.declaringClass = declaringClass;
        this.interfaceType = interfaceType;
    }

    public String getDeclaringClass() {
        return declaringClass;
    }

    public String getInterfaceType() {
        return interfaceType;
    }

    @Override
    public String toString() {
        return String.format("LambdaReflectionBuildItem{%s, interface=%s}",
                declaringClass, interfaceType);
    }
}
