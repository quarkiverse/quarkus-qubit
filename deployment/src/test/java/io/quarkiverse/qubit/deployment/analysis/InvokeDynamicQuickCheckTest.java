package io.quarkiverse.qubit.deployment.analysis;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

/** Tests for constant pool-based invokedynamic + Qubit API reference detection. */
class InvokeDynamicQuickCheckTest {

    private static final String CLASS_WITH_QUBIT_LAMBDAS =
            "io/quarkiverse/qubit/deployment/testutil/FluentApiTestSources.class";
    private static final String CLASS_WITHOUT_LAMBDAS =
            "io/quarkiverse/qubit/deployment/analysis/InvokeDynamicQuickCheck.class";

    private static byte[] classWithQubitLambdas;
    private static byte[] classWithoutLambdas;

    @BeforeAll
    static void loadTestClasses() throws Exception {
        classWithQubitLambdas = loadClassBytes(CLASS_WITH_QUBIT_LAMBDAS);
        classWithoutLambdas = loadClassBytes(CLASS_WITHOUT_LAMBDAS);
    }

    private static byte[] loadClassBytes(String resourcePath) throws Exception {
        try (var is = InvokeDynamicQuickCheckTest.class.getClassLoader().getResourceAsStream(resourcePath)) {
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

            var rawScanFinds0xBA = false;
            for (var b : classWithoutLambdas) {
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
            var garbage = new byte[100];
            garbage[0] = (byte) 0xDE;
            assertThat(InvokeDynamicQuickCheck.mightContainInvokeDynamic(garbage))
                    .as("Invalid class file should return true (conservative)")
                    .isTrue();
        }

        @Test
        void returnsTrueForTruncatedConstantPool() {
            var truncated = new byte[] {
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
            var classBytes = buildSyntheticClass(true, true);
            assertThat(InvokeDynamicQuickCheck.mightContainInvokeDynamic(classBytes))
                    .as("Class with both tag 18 and Qubit Utf8 reference should return true")
                    .isTrue();
        }

        @Test
        void returnsFalseWhenTag18ButNoQubitMarker() throws Exception {
            var classBytes = buildSyntheticClass(true, false);
            assertThat(InvokeDynamicQuickCheck.mightContainInvokeDynamic(classBytes))
                    .as("Class with tag 18 but no Qubit reference should return false")
                    .isFalse();
        }

        @Test
        void returnsFalseWhenQubitMarkerButNoTag18() throws Exception {
            var classBytes = buildSyntheticClass(false, true);
            assertThat(InvokeDynamicQuickCheck.mightContainInvokeDynamic(classBytes))
                    .as("Class with Qubit reference but no tag 18 should return false")
                    .isFalse();
        }

        @Test
        void returnsFalseWhenNeitherPresent() throws Exception {
            var classBytes = buildSyntheticClass(false, false);
            assertThat(InvokeDynamicQuickCheck.mightContainInvokeDynamic(classBytes))
                    .as("Class with neither tag 18 nor Qubit reference should return false")
                    .isFalse();
        }

        /**
         * Builds minimal class file with controllable tag 18 and Qubit Utf8 marker.
         * CP: [Utf8 className, Class->#1, optional Utf8 qubitMarker, optional InvokeDynamic]
         */
        private byte[] buildSyntheticClass(boolean includeTag18, boolean includeQubitRef) throws Exception {
            var baos = new ByteArrayOutputStream();
            var dos = new DataOutputStream(baos);

            dos.writeInt(0xCAFEBABE);
            dos.writeShort(0);
            dos.writeShort(65);

            var cpCount = 3;
            if (includeQubitRef) cpCount++;
            if (includeTag18) cpCount++;
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
                var marker = "io/quarkiverse/qubit/QuerySpec";
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
}
