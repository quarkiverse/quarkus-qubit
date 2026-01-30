package io.quarkiverse.qubit.deployment.common;

/** Bytecode analysis constants: DESC_* method descriptors and lookahead limits. */
public final class BytecodeAnalysisConstants {

    private BytecodeAnalysisConstants() {
    }

    // ========== JVM Method Descriptors ==========

    public static final String DESC_BOOLEAN_VALUE_OF = "(Z)Ljava/lang/Boolean;";          // Boolean.valueOf(boolean)
    public static final String DESC_OBJECT_TO_BOOLEAN = "(Ljava/lang/Object;)Z";          // equals(), contains()
    public static final String DESC_BIG_DECIMAL_ARITHMETIC = "(Ljava/math/BigDecimal;)Ljava/math/BigDecimal;";
    public static final String DESC_STRING_TO_BOOLEAN = "(Ljava/lang/String;)Z";          // startsWith, endsWith
    public static final String DESC_CHAR_SEQUENCE_TO_BOOLEAN = "(Ljava/lang/CharSequence;)Z"; // String.contains
    public static final String DESC_NO_ARG_TO_INT = "()I";                                // String.length()
    public static final String DESC_NO_ARG_TO_BOOLEAN = "()Z";                            // String.isEmpty()
    public static final String DESC_NO_ARG_TO_STRING = "()Ljava/lang/String;";            // toLowerCase, trim
    public static final String DESC_INT_TO_STRING = "(I)Ljava/lang/String;";              // substring(int)
    public static final String DESC_TWO_INTS_TO_STRING = "(II)Ljava/lang/String;";        // substring(int, int)
    public static final String DESC_CLASS_CONSTRUCTOR = "(Ljava/lang/Class;)V";           // QubitStreamImpl(Class<E>)

    // ========== Lookahead Limits ==========

    /** Boolean markers (ICONST_0/1) - javac 11-21 emits within 5-10 instructions. */
    public static final int LOOKAHEAD_WINDOW_SIZE = 10;
    public static final int LABEL_CLASSIFICATION_LOOKAHEAD_LIMIT = LOOKAHEAD_WINDOW_SIZE;
    public static final int CONDITIONAL_JUMP_LOOKAHEAD_LIMIT = 5;  // GOTO after conditional jumps
    public static final int LABEL_TRACE_DEPTH_LIMIT = 20;          // Prevents infinite loops

}
