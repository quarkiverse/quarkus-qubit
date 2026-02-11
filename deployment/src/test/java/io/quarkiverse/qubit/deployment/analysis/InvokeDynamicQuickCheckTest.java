package io.quarkiverse.qubit.deployment.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.InputStream;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Tests for constant pool-based invokedynamic + Qubit API reference detection. */
class InvokeDynamicQuickCheckTest {

    private static final String CLASS_WITH_QUBIT_LAMBDAS = "io/quarkiverse/qubit/deployment/testutil/FluentApiTestSources.class";
    private static final String CLASS_WITHOUT_LAMBDAS = "io/quarkiverse/qubit/deployment/analysis/InvokeDynamicQuickCheck.class";

    private static byte[] classWithQubitLambdas;
    private static byte[] classWithoutLambdas;

    @BeforeAll
    static void loadTestClasses() throws Exception {
        classWithQubitLambdas = loadClassBytes(CLASS_WITH_QUBIT_LAMBDAS);
        classWithoutLambdas = loadClassBytes(CLASS_WITHOUT_LAMBDAS);
    }

    private static byte[] loadClassBytes(String resourcePath) throws Exception {
        try (InputStream is = InvokeDynamicQuickCheckTest.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new RuntimeException("Cannot find class file: " + resourcePath);
            }
            return is.readAllBytes();
        }
    }

    @Nested
    class RealClassFiles {

        @Test
        void returnsTrueForClassWithQubitLambdas() {
            assertThat(InvokeDynamicQuickCheck.mightContainInvokeDynamic(classWithQubitLambdas))
                    .as("Class with Qubit lambdas must have both tag 18 and Qubit Utf8 refs")
                    .isTrue();
        }

        @Test
        void noFalseNegativesForQubitClasses() {
            assertThat(InvokeDynamicQuickCheck.mightContainInvokeDynamic(classWithQubitLambdas))
                    .as("Quick check must never produce false negatives for Qubit lambda classes")
                    .isTrue();
        }

        @Test
        void returnsFalseForClassWithoutLambdas() {
            assertThat(InvokeDynamicQuickCheck.mightContainInvokeDynamic(classWithoutLambdas))
                    .as("Utility class without lambdas should return false")
                    .isFalse();
        }

        @Test
        void oldApproachWouldFalsePositive() {
            assertThat(InvokeDynamicQuickCheck.mightContainInvokeDynamic(classWithoutLambdas))
                    .isFalse();

            boolean rawScanFinds0xBA = false;
            for (byte b : classWithoutLambdas) {
                if ((b & 0xFF) == 0xBA) {
                    rawScanFinds0xBA = true;
                    break;
                }
            }
            assertThat(rawScanFinds0xBA)
                    .as("Old 0xBA scan would false-positive on this class")
                    .isTrue();
        }
    }

    @Nested
    class BoundaryConditions {

        @Test
        void returnsFalseForNullInput() {
            assertThat(InvokeDynamicQuickCheck.mightContainInvokeDynamic(null)).isFalse();
        }

        @Test
        void returnsFalseForEmptyInput() {
            assertThat(InvokeDynamicQuickCheck.mightContainInvokeDynamic(new byte[0])).isFalse();
        }

        @Test
        void returnsFalseForTooSmallInput() {
            assertThat(InvokeDynamicQuickCheck.mightContainInvokeDynamic(new byte[9])).isFalse();
        }

        @Test
        void returnsTrueForInvalidMagicNumber() {
            byte[] garbage = new byte[100];
            garbage[0] = (byte) 0xDE;
            assertThat(InvokeDynamicQuickCheck.mightContainInvokeDynamic(garbage))
                    .as("Invalid class file should return true (conservative)")
                    .isTrue();
        }

        @Test
        void returnsTrueForTruncatedConstantPool() {
            byte[] truncated = new byte[] {
                    (byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE,
                    0x00, 0x00, 0x00, 0x41,
                    0x00, 0x05
            };
            assertThat(InvokeDynamicQuickCheck.mightContainInvokeDynamic(truncated))
                    .as("Truncated constant pool should return true (conservative)")
                    .isTrue();
        }
    }

    @Nested
    class SyntheticClassFiles {

        @Test
        void returnsTrueWhenBothTag18AndQubitMarkerPresent() throws Exception {
            byte[] classBytes = buildSyntheticClass(true, true);
            assertThat(InvokeDynamicQuickCheck.mightContainInvokeDynamic(classBytes))
                    .as("Class with both tag 18 and Qubit Utf8 reference should return true")
                    .isTrue();
        }

        @Test
        void returnsFalseWhenTag18ButNoQubitMarker() throws Exception {
            byte[] classBytes = buildSyntheticClass(true, false);
            assertThat(InvokeDynamicQuickCheck.mightContainInvokeDynamic(classBytes))
                    .as("Class with tag 18 but no Qubit reference should return false")
                    .isFalse();
        }

        @Test
        void returnsFalseWhenQubitMarkerButNoTag18() throws Exception {
            byte[] classBytes = buildSyntheticClass(false, true);
            assertThat(InvokeDynamicQuickCheck.mightContainInvokeDynamic(classBytes))
                    .as("Class with Qubit reference but no tag 18 should return false")
                    .isFalse();
        }

        @Test
        void returnsFalseWhenNeitherPresent() throws Exception {
            byte[] classBytes = buildSyntheticClass(false, false);
            assertThat(InvokeDynamicQuickCheck.mightContainInvokeDynamic(classBytes))
                    .as("Class with neither tag 18 nor Qubit reference should return false")
                    .isFalse();
        }

        /**
         * Builds minimal class file with controllable tag 18 and Qubit Utf8 marker.
         * CP: [Utf8 className, Class->#1, optional Utf8 qubitMarker, optional InvokeDynamic]
         */
        private byte[] buildSyntheticClass(boolean includeTag18, boolean includeQubitRef) throws Exception {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(baos);

            dos.writeInt(0xCAFEBABE);
            dos.writeShort(0);
            dos.writeShort(65);

            int cpCount = 3;
            if (includeQubitRef)
                cpCount++;
            if (includeTag18)
                cpCount++;
            dos.writeShort(cpCount);

            // #1: CONSTANT_Utf8 "Test"
            dos.writeByte(1);
            dos.writeShort(4);
            dos.writeBytes("Test");

            // #2: CONSTANT_Class -> #1
            dos.writeByte(7);
            dos.writeShort(1);

            if (includeQubitRef) {
                // Utf8 with Qubit API reference
                String marker = "io/quarkiverse/qubit/QuerySpec";
                dos.writeByte(1);
                dos.writeShort(marker.length());
                dos.writeBytes(marker);
            }

            if (includeTag18) {
                // CONSTANT_InvokeDynamic
                dos.writeByte(18);
                dos.writeShort(0);
                dos.writeShort(1);
            }

            // Minimal class structure
            dos.writeShort(0x0021);
            dos.writeShort(2);
            dos.writeShort(0);
            dos.writeShort(0);
            dos.writeShort(0);
            dos.writeShort(0);
            dos.writeShort(0);

            dos.flush();
            return baos.toByteArray();
        }
    }

    @Nested
    class ConstantPoolParsingAccuracy {

        @Test
        void correctlyParsesUtf8LengthAndSkipsForward() throws Exception {
            // Kills mutation: advancePastUtf8 line 74 "addition → subtraction"
            // If pos += 2 + len becomes pos += 2 - len, the parser goes backward
            // and would loop forever or produce wrong result.
            // This test uses a Utf8 entry followed by tag 18 — the parser must
            // advance PAST the Utf8 to find the tag 18.
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(baos);
            dos.writeInt(0xCAFEBABE);
            dos.writeShort(0);
            dos.writeShort(65);
            dos.writeShort(4); // 3 entries + 1

            // #1: Utf8 with Qubit marker (30 bytes — must advance correctly)
            String marker = "io/quarkiverse/qubit/QuerySpec";
            dos.writeByte(1);
            dos.writeShort(marker.length());
            dos.writeBytes(marker);

            // #2: Utf8 "X" (padding — ensures parser advanced past #1)
            dos.writeByte(1);
            dos.writeShort(1);
            dos.writeBytes("X");

            // #3: CONSTANT_InvokeDynamic (tag 18 — must be reached)
            dos.writeByte(18);
            dos.writeShort(0);
            dos.writeShort(1);

            dos.writeShort(0x0021);
            dos.writeShort(1);
            dos.writeShort(0);
            dos.writeShort(0);
            dos.writeShort(0);
            dos.writeShort(0);
            dos.writeShort(0);
            dos.flush();

            assertThat(InvokeDynamicQuickCheck.mightContainInvokeDynamic(baos.toByteArray()))
                    .as("Parser must advance past Utf8 entries to find tag 18")
                    .isTrue();
        }

        @Test
        void detectsMarkerWithExactLength() throws Exception {
            // Kills mutation: containsMarker line 94 "< → <=" boundary
            // If len < MARKER.length becomes len <= MARKER.length,
            // an exact-length match would be rejected.
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(baos);
            dos.writeInt(0xCAFEBABE);
            dos.writeShort(0);
            dos.writeShort(65);
            dos.writeShort(3);

            // #1: Utf8 with EXACT marker string "quarkiverse/qubit" (17 bytes)
            String exactMarker = "quarkiverse/qubit";
            dos.writeByte(1);
            dos.writeShort(exactMarker.length());
            dos.writeBytes(exactMarker);

            // #2: CONSTANT_InvokeDynamic
            dos.writeByte(18);
            dos.writeShort(0);
            dos.writeShort(1);

            dos.writeShort(0x0021);
            dos.writeShort(1);
            dos.writeShort(0);
            dos.writeShort(0);
            dos.writeShort(0);
            dos.writeShort(0);
            dos.writeShort(0);
            dos.flush();

            assertThat(InvokeDynamicQuickCheck.mightContainInvokeDynamic(baos.toByteArray()))
                    .as("Exact-length marker must be detected")
                    .isTrue();
        }

        @Test
        void returnsFalseWhenTag18ExistsButNoMarkerInAnyUtf8() throws Exception {
            // Validates that the marker check actually filters — not just tag 18
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(baos);
            dos.writeInt(0xCAFEBABE);
            dos.writeShort(0);
            dos.writeShort(65);
            dos.writeShort(4);

            // #1: Utf8 "java/lang/Object" (no qubit marker)
            String noMarker = "java/lang/Object";
            dos.writeByte(1);
            dos.writeShort(noMarker.length());
            dos.writeBytes(noMarker);

            // #2: Utf8 "toString" (no qubit marker)
            dos.writeByte(1);
            dos.writeShort(8);
            dos.writeBytes("toString");

            // #3: CONSTANT_InvokeDynamic
            dos.writeByte(18);
            dos.writeShort(0);
            dos.writeShort(1);

            dos.writeShort(0x0021);
            dos.writeShort(1);
            dos.writeShort(0);
            dos.writeShort(0);
            dos.writeShort(0);
            dos.writeShort(0);
            dos.writeShort(0);
            dos.flush();

            assertThat(InvokeDynamicQuickCheck.mightContainInvokeDynamic(baos.toByteArray()))
                    .as("Tag 18 without qubit marker in any Utf8 should return false")
                    .isFalse();
        }
    }
}
