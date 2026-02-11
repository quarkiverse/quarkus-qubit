package io.quarkiverse.qubit.deployment.analysis;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Checks class bytecode for CONSTANT_InvokeDynamic (tag 18) AND Qubit API references
 * in the constant pool. Both must be present for a class to potentially contain Qubit queries.
 *
 * <p>
 * Returns true conservatively on any parse error (never false negatives).
 */
public final class InvokeDynamicQuickCheck {

    private static final int TAG_INVOKE_DYNAMIC = 18;
    private static final int TAG_UTF8 = 1;
    private static final int MIN_HEADER_SIZE = 10;
    private static final long MAGIC = 0xCAFEBABEL;
    private static final byte[] QUBIT_MARKER = "quarkiverse/qubit".getBytes(StandardCharsets.UTF_8);

    private InvokeDynamicQuickCheck() {
    }

    /** Returns true if the class might contain Qubit invokedynamic queries; false if definitely not. */
    public static boolean mightContainInvokeDynamic(byte[] classBytes) {
        if (classBytes == null || classBytes.length < MIN_HEADER_SIZE) {
            return false;
        }
        try {
            return scanConstantPool(classBytes);
        } catch (ArrayIndexOutOfBoundsException _) {
            return true;
        }
    }

    /** Scans constant pool for tag 18 AND Qubit API Utf8 references. Both required. */
    private static boolean scanConstantPool(byte[] data) {
        if (readU4(data, 0) != MAGIC) {
            return true;
        }
        int cpCount = readU2(data, 8);
        // flags[0] = hasInvokeDynamic, flags[1] = hasQubitReference
        boolean[] flags = new boolean[2];
        int pos = 10;
        int index = 1;

        while (index < cpCount && pos < data.length) {
            int tag = data[pos] & 0xFF;
            pos = advancePastEntry(data, pos + 1, tag, flags);
            if (pos < 0)
                return true; // parse error — conservative
            if (flags[0] && flags[1])
                return true; // both found — early exit
            index += (tag == 5 || tag == 6) ? 2 : 1;
        }
        // Truncated CP → conservative; complete scan with no match → skip
        return index < cpCount;
    }

    /** Advances past one CP entry, updating flags. Returns new pos or -1 on error. */
    private static int advancePastEntry(byte[] data, int pos, int tag, boolean[] flags) {
        if (tag == TAG_INVOKE_DYNAMIC) {
            flags[0] = true;
            return pos + 4;
        }
        if (tag == TAG_UTF8) {
            return advancePastUtf8(data, pos, flags);
        }
        int size = entryPayloadSize(tag);
        return size >= 0 ? pos + size : -1;
    }

    /** Advances past a Utf8 entry, checking for Qubit marker if not yet found. */
    private static int advancePastUtf8(byte[] data, int pos, boolean[] flags) {
        int len = readU2(data, pos);
        if (!flags[1]) {
            flags[1] = containsMarker(data, pos + 2, len);
        }
        return pos + 2 + len;
    }

    /** Returns payload size for a constant pool entry tag, or -1 for unknown tags. */
    private static int entryPayloadSize(int tag) {
        return switch (tag) {
            case 3, 4 -> 4; // Integer, Float
            case 5, 6 -> 8; // Long, Double
            case 7, 8, 16, 19, 20 -> 2; // Class, String, MethodType, Module, Package
            case 9, 10, 11, 12 -> 4; // Fieldref, Methodref, InterfaceMethodref, NameAndType
            case 15 -> 3; // MethodHandle
            case 17 -> 4; // Dynamic
            default -> -1;
        };
    }

    /** Checks if data[offset..offset+len) contains the QUBIT_MARKER bytes. */
    private static boolean containsMarker(byte[] data, int offset, int len) {
        if (len < QUBIT_MARKER.length || offset + len > data.length) {
            return false;
        }
        int end = offset + len - QUBIT_MARKER.length + 1;
        for (int i = offset; i < end; i++) {
            if (data[i] == QUBIT_MARKER[0] && matchesMarkerAt(data, i)) {
                return true;
            }
        }
        return false;
    }

    /** Returns true if QUBIT_MARKER matches at position i in data. */
    private static boolean matchesMarkerAt(byte[] data, int i) {
        return Arrays.mismatch(data, i, i + QUBIT_MARKER.length, QUBIT_MARKER, 0, QUBIT_MARKER.length) == -1;
    }

    /** Reads unsigned 2-byte big-endian value. */
    private static int readU2(byte[] data, int offset) {
        return ((data[offset] & 0xFF) << 8) | (data[offset + 1] & 0xFF);
    }

    /** Reads unsigned 4-byte big-endian value. */
    private static long readU4(byte[] data, int offset) {
        return ((long) (data[offset] & 0xFF) << 24)
                | ((long) (data[offset + 1] & 0xFF) << 16)
                | ((long) (data[offset + 2] & 0xFF) << 8)
                | (data[offset + 3] & 0xFF);
    }
}
