package io.quarkiverse.qubit.deployment.common;

import static org.objectweb.asm.Opcodes.*;

/**
 * Classifies bytecode opcodes into categories: arithmetic, logical, comparison, branch, invoke, conversion, constant.
 */
public final class OpcodeClassifier {

    private OpcodeClassifier() {
    }

    // ========== Arithmetic & Logical Operations ==========

    /** Checks if opcode is arithmetic (ADD, SUB, MUL, DIV, REM). Range IADD-DREM (96-115). */
    public static boolean isArithmeticOpcode(int opcode) {
        return opcode >= IADD && opcode <= DREM;
    }

    /** Checks if opcode is logical (IAND, IOR, IXOR). */
    public static boolean isLogicalOpcode(int opcode) {
        return opcode == IAND || opcode == IOR || opcode == IXOR;
    }

    /** Checks if opcode is arithmetic or logical. */
    public static boolean isArithmeticOrLogicalOpcode(int opcode) {
        return isArithmeticOpcode(opcode) || isLogicalOpcode(opcode);
    }

    /** Checks if opcode is comparison (DCMPL, DCMPG, FCMPL, FCMPG, LCMP). */
    public static boolean isComparisonOpcode(int opcode) {
        return opcode == DCMPL || opcode == DCMPG ||
                opcode == FCMPL || opcode == FCMPG ||
                opcode == LCMP;
    }

    // ========== Branch Instructions ==========

    /** Checks if opcode is conditional branch (IFEQ-IF_ICMPLE, IFNULL, IFNONNULL). */
    public static boolean isBranchOpcode(int opcode) {
        return (opcode >= IFEQ && opcode <= IF_ICMPLE) ||
                opcode == IFNULL || opcode == IFNONNULL;
    }

    // ========== Method Invocations ==========

    /** Checks if opcode is method invocation (INVOKE*). */
    public static boolean isInvokeOpcode(int opcode) {
        return opcode == INVOKEVIRTUAL || opcode == INVOKESTATIC ||
                opcode == INVOKESPECIAL || opcode == INVOKEINTERFACE;
    }

    // ========== Type Conversion Instructions ==========

    /** Checks if opcode is primitive type conversion (I2L, L2D, F2I, etc). */
    public static boolean isTypeConversionOpcode(int opcode) {
        return opcode == I2L || opcode == I2F || opcode == I2D ||
                opcode == L2I || opcode == L2F || opcode == L2D ||
                opcode == F2I || opcode == F2L || opcode == F2D ||
                opcode == D2I || opcode == D2L || opcode == D2F;
    }

    // ========== Constant Instructions ==========

    /** Checks if opcode is constant load (BIPUSH, SIPUSH, LDC, *CONST_*). */
    public static boolean isConstantOpcode(int opcode) {
        return opcode == BIPUSH || opcode == SIPUSH || opcode == LDC ||
                opcode == ACONST_NULL ||
                isIntConstantOpcode(opcode) ||
                isFloatConstantOpcode(opcode) ||
                isLongConstantOpcode(opcode) ||
                isDoubleConstantOpcode(opcode);
    }

    /** Checks if opcode is ICONST_M1 to ICONST_5. */
    public static boolean isIntConstantOpcode(int opcode) {
        return opcode >= ICONST_M1 && opcode <= ICONST_5;
    }

    /** Checks if opcode is FCONST_0 to FCONST_2. */
    public static boolean isFloatConstantOpcode(int opcode) {
        return opcode >= FCONST_0 && opcode <= FCONST_2;
    }

    /** Checks if opcode is LCONST_0 to LCONST_1. */
    public static boolean isLongConstantOpcode(int opcode) {
        return opcode >= LCONST_0 && opcode <= LCONST_1;
    }

    /** Checks if opcode is DCONST_0 to DCONST_1. */
    public static boolean isDoubleConstantOpcode(int opcode) {
        return opcode >= DCONST_0 && opcode <= DCONST_1;
    }
}
