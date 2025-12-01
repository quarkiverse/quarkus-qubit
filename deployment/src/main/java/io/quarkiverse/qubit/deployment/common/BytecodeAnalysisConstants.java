package io.quarkiverse.qubit.deployment.common;

/**
 * Bytecode analysis configuration constants.
 */
public final class BytecodeAnalysisConstants {

    private BytecodeAnalysisConstants() {
    }

    /**
     * Lookahead window for detecting boolean markers (ICONST_0/1).
     * Javac 11-21 emits these within 5-10 instructions of comparisons.
     */
    public static final int LOOKAHEAD_WINDOW_SIZE = 10;

    /**
     * Lookahead for label classification (same as LOOKAHEAD_WINDOW_SIZE for consistency).
     */
    public static final int LABEL_CLASSIFICATION_LOOKAHEAD_LIMIT = LOOKAHEAD_WINDOW_SIZE;

    /**
     * Lookahead for GOTO after conditional jumps (&&, || patterns).
     */
    public static final int CONDITIONAL_JUMP_LOOKAHEAD_LIMIT = 5;

    /**
     * Max depth for label tracing (prevents infinite loops on malformed bytecode).
     */
    public static final int LABEL_TRACE_DEPTH_LIMIT = 20;

}
