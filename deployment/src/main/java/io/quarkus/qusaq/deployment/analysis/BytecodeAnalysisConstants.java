package io.quarkus.qusaq.deployment.analysis;

/**
 * Constants used in bytecode analysis.
 * Documents magic numbers and their rationale.
 */
public final class BytecodeAnalysisConstants {

    private BytecodeAnalysisConstants() {
        // Utility class
    }

    /**
     * Lookahead window size for detecting boolean result markers in bytecode.
     *
     * <p>Rationale: The Java compiler (javac 11-21) typically emits boolean result markers
     * (ICONST_1 for true, ICONST_0 for false) within 5-10 instructions of a comparison operation.
     * A window size of 10 provides a safety margin while keeping the analysis fast.
     *
     * <p>This constant is used to avoid incorrectly interpreting comparison results as
     * integer arithmetic when they are actually boolean expressions.
     *
     * <p>Validated against javac versions 11 through 21.
     */
    public static final int LOOKAHEAD_WINDOW_SIZE = 10;

    /**
     * Maximum depth for tracing label targets in control flow analysis.
     *
     * <p>Rationale: Prevents infinite loops when analyzing malformed or pathological bytecode.
     * No valid lambda expression should have more than 20 levels of nested jumps.
     *
     * <p>This limit protects against:
     * <ul>
     *   <li>Circular jump references (malformed bytecode)</li>
     *   <li>Excessively deep nesting (likely not a real lambda)</li>
     *   <li>Analysis performance degradation</li>
     * </ul>
     */
    public static final int LABEL_TRACE_DEPTH_LIMIT = 20;

    /**
     * Field name prefix for captured variables in lambda instances.
     *
     * <p>Based on javac implementation (JDK 11-21). The Java compiler generates
     * synthetic fields with names following the pattern: {@code arg$1}, {@code arg$2}, {@code arg$3}, etc.
     *
     * <p>Note: This naming convention is implementation-specific to Oracle/OpenJDK javac.
     * Other compilers (Eclipse JDT, GraalVM) may use different naming schemes:
     * <ul>
     *   <li>javac: {@code arg$1}, {@code arg$2}, ...</li>
     *   <li>Eclipse: {@code val$1}, {@code val$2}, ...</li>
     *   <li>GraalVM: {@code arg0}, {@code arg1}, ... (zero-indexed)</li>
     * </ul>
     *
     * @see io.quarkus.qusaq.runtime.CapturedVariableExtractor
     */
    public static final String CAPTURED_VAR_PREFIX = "arg$";

    /**
     * Alternative field name prefix used by Eclipse JDT compiler.
     */
    public static final String ECLIPSE_CAPTURED_VAR_PREFIX = "val$";
}
