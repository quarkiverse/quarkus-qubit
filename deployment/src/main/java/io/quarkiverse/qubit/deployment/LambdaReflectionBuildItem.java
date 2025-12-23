package io.quarkiverse.qubit.deployment;

import io.quarkus.builder.item.MultiBuildItem;

/**
 * Build item that captures lambda call site information for native image reflection.
 * <p>
 * In GraalVM native image, lambda classes are synthetic and their fields
 * are not registered for reflection by default. This build item collects
 * information about lambdas with captured variables so that the appropriate
 * reflection configuration can be generated.
 * <p>
 * The generated reflect-config.json uses the GraalVM lambda syntax:
 * <pre>
 * {
 *   "type": {
 *     "lambda": {
 *       "declaringClass": "com.example.MyClass",
 *       "interfaces": ["io.quarkiverse.qubit.runtime.QuerySpec"]
 *     }
 *   },
 *   "allDeclaredFields": true
 * }
 * </pre>
 */
public final class LambdaReflectionBuildItem extends MultiBuildItem {

    private final String declaringClass;
    private final String methodName;
    private final String interfaceType;
    private final int capturedVarCount;

    /**
     * Creates a lambda reflection build item.
     *
     * @param declaringClass the fully qualified name of the class containing the lambda
     * @param methodName the name of the method containing the lambda
     * @param interfaceType the fully qualified name of the lambda interface (e.g., QuerySpec)
     * @param capturedVarCount the number of captured variables in the lambda
     */
    public LambdaReflectionBuildItem(String declaringClass, String methodName,
                                      String interfaceType, int capturedVarCount) {
        this.declaringClass = declaringClass;
        this.methodName = methodName;
        this.interfaceType = interfaceType;
        this.capturedVarCount = capturedVarCount;
    }

    public String getDeclaringClass() {
        return declaringClass;
    }

    public String getMethodName() {
        return methodName;
    }

    public String getInterfaceType() {
        return interfaceType;
    }

    public int getCapturedVarCount() {
        return capturedVarCount;
    }

    @Override
    public String toString() {
        return String.format("LambdaReflectionBuildItem{%s.%s, interface=%s, capturedVars=%d}",
                declaringClass, methodName, interfaceType, capturedVarCount);
    }
}
