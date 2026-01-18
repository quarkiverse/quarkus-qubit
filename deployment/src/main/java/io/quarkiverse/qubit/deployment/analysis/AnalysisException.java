package io.quarkiverse.qubit.deployment.analysis;

/**
 * Exception for bytecode analysis failures with rich context for debugging.
 * Distinguishes between "not found" and "analysis failed" scenarios.
 */
public class AnalysisException extends RuntimeException {

    private final String className;
    private final String methodName;
    private final String callSiteId;

    /** Creates an analysis exception with basic message. */
    public AnalysisException(String message) {
        super(message);
        this.className = null;
        this.methodName = null;
        this.callSiteId = null;
    }

    /** Creates an analysis exception with message and cause. */
    public AnalysisException(String message, Throwable cause) {
        super(message, cause);
        this.className = null;
        this.methodName = null;
        this.callSiteId = null;
    }

    /** Creates an analysis exception with full context (class, method, callSite). */
    public AnalysisException(String message, String className, String methodName, String callSiteId) {
        super(formatMessage(message, className, methodName, callSiteId));
        this.className = className;
        this.methodName = methodName;
        this.callSiteId = callSiteId;
    }

    /** Creates an analysis exception with full context and cause. */
    public AnalysisException(String message, String className, String methodName, String callSiteId, Throwable cause) {
        super(formatMessage(message, className, methodName, callSiteId), cause);
        this.className = className;
        this.methodName = methodName;
        this.callSiteId = callSiteId;
    }

    /** Returns the class name where analysis failed, or null if not set. */
    public String getClassName() {
        return className;
    }

    /** Returns the method name where analysis failed, or null if not set. */
    public String getMethodName() {
        return methodName;
    }

    /** Returns the call site ID where analysis failed, or null if not set. */
    public String getCallSiteId() {
        return callSiteId;
    }

    private static String formatMessage(String message, String className, String methodName, String callSiteId) {
        StringBuilder sb = new StringBuilder(message);
        sb.append(" [");
        if (className != null) {
            sb.append("class=").append(className);
        }
        if (methodName != null) {
            if (className != null) sb.append(", ");
            sb.append("method=").append(methodName);
        }
        if (callSiteId != null) {
            if (className != null || methodName != null) sb.append(", ");
            sb.append("callSite=").append(callSiteId);
        }
        sb.append("]");
        return sb.toString();
    }

    // ========== Factory Methods ==========

    /** Creates exception for bytecode loading failure. */
    public static AnalysisException bytecodeNotFound(String className) {
        return new AnalysisException(
                "Could not load bytecode for class: " + className +
                ". Ensure the class is compiled and in the application classpath.");
    }

    /** Creates exception for lambda method not found. */
    public static AnalysisException lambdaMethodNotFound(String className, String methodName, String descriptor) {
        return new AnalysisException(
                String.format("Lambda method %s%s not found in class %s", methodName, descriptor, className),
                className, methodName, null);
    }

    /** Creates exception for bytecode scanning failure. */
    public static AnalysisException scanningFailed(String className, Throwable cause) {
        return new AnalysisException(
                "Failed to scan class bytecode: " + className,
                className, null, null, cause);
    }

    /** Creates exception for invalid lambda handle. */
    public static AnalysisException invalidLambdaHandle(String className, String reason) {
        return new AnalysisException(
                "Invalid lambda handle: " + reason,
                className, null, null);
    }

    /** Creates exception for expression analysis failure. */
    public static AnalysisException expressionAnalysisFailed(String callSiteId, String reason) {
        return new AnalysisException(
                "Failed to analyze lambda expression: " + reason,
                null, null, callSiteId);
    }
}
