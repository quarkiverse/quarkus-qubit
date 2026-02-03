package io.quarkiverse.qubit.deployment.common;

import static org.objectweb.asm.Opcodes.*;

/** Converts bytecode opcodes to human-readable names for logging. */
public final class OpcodeNames {

    private OpcodeNames() {}

    /** Returns opcode name (e.g., "IADD", "IFEQ") or "UNKNOWN(n)" if not recognized. */
    public static String get(int opcode) {
        return switch (opcode) {
            // Arithmetic opcodes
            case IADD -> "IADD";
            case LADD -> "LADD";
            case FADD -> "FADD";
            case DADD -> "DADD";
            case ISUB -> "ISUB";
            case LSUB -> "LSUB";
            case FSUB -> "FSUB";
            case DSUB -> "DSUB";
            case IMUL -> "IMUL";
            case LMUL -> "LMUL";
            case FMUL -> "FMUL";
            case DMUL -> "DMUL";
            case IDIV -> "IDIV";
            case LDIV -> "LDIV";
            case FDIV -> "FDIV";
            case DDIV -> "DDIV";
            case IREM -> "IREM";
            case LREM -> "LREM";
            case FREM -> "FREM";
            case DREM -> "DREM";

            // Logical opcodes
            case IAND -> "IAND";
            case IOR -> "IOR";
            case IXOR -> "IXOR";

            // Comparison opcodes
            case DCMPL -> "DCMPL";
            case DCMPG -> "DCMPG";
            case FCMPL -> "FCMPL";
            case FCMPG -> "FCMPG";
            case LCMP -> "LCMP";

            // Branch opcodes - single operand
            case IFEQ -> "IFEQ";
            case IFNE -> "IFNE";
            case IFLT -> "IFLT";
            case IFGE -> "IFGE";
            case IFGT -> "IFGT";
            case IFLE -> "IFLE";

            // Branch opcodes - two operand integer comparison
            case IF_ICMPEQ -> "IF_ICMPEQ";
            case IF_ICMPNE -> "IF_ICMPNE";
            case IF_ICMPLT -> "IF_ICMPLT";
            case IF_ICMPGE -> "IF_ICMPGE";
            case IF_ICMPGT -> "IF_ICMPGT";
            case IF_ICMPLE -> "IF_ICMPLE";

            // Branch opcodes - two operand reference comparison
            case IF_ACMPEQ -> "IF_ACMPEQ";
            case IF_ACMPNE -> "IF_ACMPNE";

            // Branch opcodes - null checks
            case IFNULL -> "IFNULL";
            case IFNONNULL -> "IFNONNULL";

            default -> "UNKNOWN(" + opcode + ")";
        };
    }
}
