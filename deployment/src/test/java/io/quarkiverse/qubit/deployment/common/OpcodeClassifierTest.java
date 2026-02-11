package io.quarkiverse.qubit.deployment.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.objectweb.asm.Opcodes.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Unit tests for {@link OpcodeClassifier}.
 *
 * <p>
 * Tests the opcode classification methods for arithmetic, logical, comparison,
 * branch, invoke, conversion, and constant instructions.
 */
class OpcodeClassifierTest {

    // ==================== Arithmetic Opcode Tests ====================

    @Nested
    @DisplayName("isArithmeticOpcode")
    class IsArithmeticOpcodeTests {

        @ParameterizedTest
        @ValueSource(ints = { IADD, LADD, FADD, DADD })
        void addOpcodes_areArithmetic(int opcode) {
            assertThat(OpcodeClassifier.isArithmeticOpcode(opcode))
                    .as("ADD opcode %d should be arithmetic", opcode)
                    .isTrue();
        }

        @ParameterizedTest
        @ValueSource(ints = { ISUB, LSUB, FSUB, DSUB })
        void subOpcodes_areArithmetic(int opcode) {
            assertThat(OpcodeClassifier.isArithmeticOpcode(opcode))
                    .as("SUB opcode %d should be arithmetic", opcode)
                    .isTrue();
        }

        @ParameterizedTest
        @ValueSource(ints = { IMUL, LMUL, FMUL, DMUL })
        void mulOpcodes_areArithmetic(int opcode) {
            assertThat(OpcodeClassifier.isArithmeticOpcode(opcode))
                    .as("MUL opcode %d should be arithmetic", opcode)
                    .isTrue();
        }

        @ParameterizedTest
        @ValueSource(ints = { IDIV, LDIV, FDIV, DDIV })
        void divOpcodes_areArithmetic(int opcode) {
            assertThat(OpcodeClassifier.isArithmeticOpcode(opcode))
                    .as("DIV opcode %d should be arithmetic", opcode)
                    .isTrue();
        }

        @ParameterizedTest
        @ValueSource(ints = { IREM, LREM, FREM, DREM })
        void remOpcodes_areArithmetic(int opcode) {
            assertThat(OpcodeClassifier.isArithmeticOpcode(opcode))
                    .as("REM opcode %d should be arithmetic", opcode)
                    .isTrue();
        }

        @Test
        void iand_isNotArithmetic() {
            assertThat(OpcodeClassifier.isArithmeticOpcode(IAND))
                    .as("IAND should NOT be arithmetic (it's logical)")
                    .isFalse();
        }

        @Test
        void ior_isNotArithmetic() {
            assertThat(OpcodeClassifier.isArithmeticOpcode(IOR))
                    .as("IOR should NOT be arithmetic (it's logical)")
                    .isFalse();
        }

        @Test
        void belowRange_isNotArithmetic() {
            assertThat(OpcodeClassifier.isArithmeticOpcode(IADD - 1))
                    .as("Opcode below IADD should not be arithmetic")
                    .isFalse();
        }

        @Test
        void aboveRange_isNotArithmetic() {
            assertThat(OpcodeClassifier.isArithmeticOpcode(DREM + 1))
                    .as("Opcode above DREM should not be arithmetic")
                    .isFalse();
        }

        @Test
        void nop_isNotArithmetic() {
            assertThat(OpcodeClassifier.isArithmeticOpcode(NOP))
                    .as("NOP should not be arithmetic")
                    .isFalse();
        }
    }

    // ==================== Logical Opcode Tests ====================

    @Nested
    @DisplayName("isLogicalOpcode")
    class IsLogicalOpcodeTests {

        @Test
        void iand_isLogical() {
            assertThat(OpcodeClassifier.isLogicalOpcode(IAND))
                    .as("IAND should be logical")
                    .isTrue();
        }

        @Test
        void ior_isLogical() {
            assertThat(OpcodeClassifier.isLogicalOpcode(IOR))
                    .as("IOR should be logical")
                    .isTrue();
        }

        @Test
        void ixor_isLogical() {
            assertThat(OpcodeClassifier.isLogicalOpcode(IXOR))
                    .as("IXOR should be logical")
                    .isTrue();
        }

        @Test
        void iadd_isNotLogical() {
            assertThat(OpcodeClassifier.isLogicalOpcode(IADD))
                    .as("IADD should NOT be logical")
                    .isFalse();
        }

        @Test
        void isub_isNotLogical() {
            assertThat(OpcodeClassifier.isLogicalOpcode(ISUB))
                    .as("ISUB should NOT be logical")
                    .isFalse();
        }

        @Test
        void nop_isNotLogical() {
            assertThat(OpcodeClassifier.isLogicalOpcode(NOP))
                    .as("NOP should NOT be logical")
                    .isFalse();
        }
    }

    // ==================== Arithmetic or Logical Opcode Tests ====================

    @Nested
    @DisplayName("isArithmeticOrLogicalOpcode")
    class IsArithmeticOrLogicalOpcodeTests {

        @Test
        void iadd_isArithmeticOrLogical() {
            assertThat(OpcodeClassifier.isArithmeticOrLogicalOpcode(IADD))
                    .as("IADD should be arithmetic or logical")
                    .isTrue();
        }

        @Test
        void iand_isArithmeticOrLogical() {
            assertThat(OpcodeClassifier.isArithmeticOrLogicalOpcode(IAND))
                    .as("IAND should be arithmetic or logical")
                    .isTrue();
        }

        @Test
        void ior_isArithmeticOrLogical() {
            assertThat(OpcodeClassifier.isArithmeticOrLogicalOpcode(IOR))
                    .as("IOR should be arithmetic or logical")
                    .isTrue();
        }

        @Test
        void ixor_isArithmeticOrLogical() {
            assertThat(OpcodeClassifier.isArithmeticOrLogicalOpcode(IXOR))
                    .as("IXOR should be arithmetic or logical")
                    .isTrue();
        }

        @Test
        void nop_isNotArithmeticOrLogical() {
            assertThat(OpcodeClassifier.isArithmeticOrLogicalOpcode(NOP))
                    .as("NOP should NOT be arithmetic or logical")
                    .isFalse();
        }

        @Test
        void ifeq_isNotArithmeticOrLogical() {
            assertThat(OpcodeClassifier.isArithmeticOrLogicalOpcode(IFEQ))
                    .as("IFEQ should NOT be arithmetic or logical")
                    .isFalse();
        }
    }

    // ==================== Comparison Opcode Tests ====================

    @Nested
    @DisplayName("isComparisonOpcode")
    class IsComparisonOpcodeTests {

        @Test
        void dcmpl_isComparison() {
            assertThat(OpcodeClassifier.isComparisonOpcode(DCMPL))
                    .as("DCMPL should be comparison")
                    .isTrue();
        }

        @Test
        void dcmpg_isComparison() {
            assertThat(OpcodeClassifier.isComparisonOpcode(DCMPG))
                    .as("DCMPG should be comparison")
                    .isTrue();
        }

        @Test
        void fcmpl_isComparison() {
            assertThat(OpcodeClassifier.isComparisonOpcode(FCMPL))
                    .as("FCMPL should be comparison")
                    .isTrue();
        }

        @Test
        void fcmpg_isComparison() {
            assertThat(OpcodeClassifier.isComparisonOpcode(FCMPG))
                    .as("FCMPG should be comparison")
                    .isTrue();
        }

        @Test
        void lcmp_isComparison() {
            assertThat(OpcodeClassifier.isComparisonOpcode(LCMP))
                    .as("LCMP should be comparison")
                    .isTrue();
        }

        @Test
        void iadd_isNotComparison() {
            assertThat(OpcodeClassifier.isComparisonOpcode(IADD))
                    .as("IADD should NOT be comparison")
                    .isFalse();
        }

        @Test
        void ifeq_isNotComparison() {
            assertThat(OpcodeClassifier.isComparisonOpcode(IFEQ))
                    .as("IFEQ should NOT be comparison")
                    .isFalse();
        }
    }

    // ==================== Branch Opcode Tests ====================

    @Nested
    @DisplayName("isBranchOpcode")
    class IsBranchOpcodeTests {

        @ParameterizedTest
        @ValueSource(ints = { IFEQ, IFNE, IFLT, IFGE, IFGT, IFLE })
        void ifOpcodes_areBranch(int opcode) {
            assertThat(OpcodeClassifier.isBranchOpcode(opcode))
                    .as("IF opcode %d should be branch", opcode)
                    .isTrue();
        }

        @ParameterizedTest
        @ValueSource(ints = { IF_ICMPEQ, IF_ICMPNE, IF_ICMPLT, IF_ICMPGE, IF_ICMPGT, IF_ICMPLE })
        void ifIcmpOpcodes_areBranch(int opcode) {
            assertThat(OpcodeClassifier.isBranchOpcode(opcode))
                    .as("IF_ICMP opcode %d should be branch", opcode)
                    .isTrue();
        }

        @Test
        void ifnull_isBranch() {
            assertThat(OpcodeClassifier.isBranchOpcode(IFNULL))
                    .as("IFNULL should be branch")
                    .isTrue();
        }

        @Test
        void ifnonnull_isBranch() {
            assertThat(OpcodeClassifier.isBranchOpcode(IFNONNULL))
                    .as("IFNONNULL should be branch")
                    .isTrue();
        }

        @Test
        void goto_isNotBranch() {
            assertThat(OpcodeClassifier.isBranchOpcode(GOTO))
                    .as("GOTO should NOT be branch (unconditional)")
                    .isFalse();
        }

        @Test
        void belowIfRange_isNotBranch() {
            assertThat(OpcodeClassifier.isBranchOpcode(IFEQ - 1))
                    .as("Opcode below IFEQ should not be branch")
                    .isFalse();
        }

        @Test
        void aboveIfIcmpRange_isNotBranch() {
            // IF_ICMPLE + 1 is IF_ACMPEQ which is not in our branch range
            assertThat(OpcodeClassifier.isBranchOpcode(IF_ICMPLE + 1))
                    .as("Opcode above IF_ICMPLE (not IFNULL/IFNONNULL) should not be branch")
                    .isFalse();
        }

        @Test
        void nop_isNotBranch() {
            assertThat(OpcodeClassifier.isBranchOpcode(NOP))
                    .as("NOP should NOT be branch")
                    .isFalse();
        }
    }

    // ==================== Invoke Opcode Tests ====================

    @Nested
    @DisplayName("isInvokeOpcode")
    class IsInvokeOpcodeTests {

        @Test
        void invokevirtual_isInvoke() {
            assertThat(OpcodeClassifier.isInvokeOpcode(INVOKEVIRTUAL))
                    .as("INVOKEVIRTUAL should be invoke")
                    .isTrue();
        }

        @Test
        void invokestatic_isInvoke() {
            assertThat(OpcodeClassifier.isInvokeOpcode(INVOKESTATIC))
                    .as("INVOKESTATIC should be invoke")
                    .isTrue();
        }

        @Test
        void invokespecial_isInvoke() {
            assertThat(OpcodeClassifier.isInvokeOpcode(INVOKESPECIAL))
                    .as("INVOKESPECIAL should be invoke")
                    .isTrue();
        }

        @Test
        void invokeinterface_isInvoke() {
            assertThat(OpcodeClassifier.isInvokeOpcode(INVOKEINTERFACE))
                    .as("INVOKEINTERFACE should be invoke")
                    .isTrue();
        }

        @Test
        void invokedynamic_isNotInvoke() {
            // INVOKEDYNAMIC is handled separately
            assertThat(OpcodeClassifier.isInvokeOpcode(INVOKEDYNAMIC))
                    .as("INVOKEDYNAMIC should NOT be in regular invoke set")
                    .isFalse();
        }

        @Test
        void iadd_isNotInvoke() {
            assertThat(OpcodeClassifier.isInvokeOpcode(IADD))
                    .as("IADD should NOT be invoke")
                    .isFalse();
        }

        @Test
        void nop_isNotInvoke() {
            assertThat(OpcodeClassifier.isInvokeOpcode(NOP))
                    .as("NOP should NOT be invoke")
                    .isFalse();
        }
    }

    // ==================== Type Conversion Opcode Tests ====================

    @Nested
    @DisplayName("isTypeConversionOpcode")
    class IsTypeConversionOpcodeTests {

        @ParameterizedTest
        @ValueSource(ints = { I2L, I2F, I2D })
        void intConversions_areTypeConversion(int opcode) {
            assertThat(OpcodeClassifier.isTypeConversionOpcode(opcode))
                    .as("Int conversion opcode %d should be type conversion", opcode)
                    .isTrue();
        }

        @ParameterizedTest
        @ValueSource(ints = { L2I, L2F, L2D })
        void longConversions_areTypeConversion(int opcode) {
            assertThat(OpcodeClassifier.isTypeConversionOpcode(opcode))
                    .as("Long conversion opcode %d should be type conversion", opcode)
                    .isTrue();
        }

        @ParameterizedTest
        @ValueSource(ints = { F2I, F2L, F2D })
        void floatConversions_areTypeConversion(int opcode) {
            assertThat(OpcodeClassifier.isTypeConversionOpcode(opcode))
                    .as("Float conversion opcode %d should be type conversion", opcode)
                    .isTrue();
        }

        @ParameterizedTest
        @ValueSource(ints = { D2I, D2L, D2F })
        void doubleConversions_areTypeConversion(int opcode) {
            assertThat(OpcodeClassifier.isTypeConversionOpcode(opcode))
                    .as("Double conversion opcode %d should be type conversion", opcode)
                    .isTrue();
        }

        @Test
        void iadd_isNotTypeConversion() {
            assertThat(OpcodeClassifier.isTypeConversionOpcode(IADD))
                    .as("IADD should NOT be type conversion")
                    .isFalse();
        }
    }

    // ==================== Constant Opcode Tests ====================

    @Nested
    @DisplayName("isConstantOpcode")
    class IsConstantOpcodeTests {

        @Test
        void bipush_isConstant() {
            assertThat(OpcodeClassifier.isConstantOpcode(BIPUSH))
                    .as("BIPUSH should be constant")
                    .isTrue();
        }

        @Test
        void sipush_isConstant() {
            assertThat(OpcodeClassifier.isConstantOpcode(SIPUSH))
                    .as("SIPUSH should be constant")
                    .isTrue();
        }

        @Test
        void ldc_isConstant() {
            assertThat(OpcodeClassifier.isConstantOpcode(LDC))
                    .as("LDC should be constant")
                    .isTrue();
        }

        @Test
        void aconstNull_isConstant() {
            assertThat(OpcodeClassifier.isConstantOpcode(ACONST_NULL))
                    .as("ACONST_NULL should be constant")
                    .isTrue();
        }

        @ParameterizedTest
        @ValueSource(ints = { ICONST_M1, ICONST_0, ICONST_1, ICONST_2, ICONST_3, ICONST_4, ICONST_5 })
        void iconstOpcodes_areConstant(int opcode) {
            assertThat(OpcodeClassifier.isConstantOpcode(opcode))
                    .as("ICONST opcode %d should be constant", opcode)
                    .isTrue();
        }

        @ParameterizedTest
        @ValueSource(ints = { FCONST_0, FCONST_1, FCONST_2 })
        void fconstOpcodes_areConstant(int opcode) {
            assertThat(OpcodeClassifier.isConstantOpcode(opcode))
                    .as("FCONST opcode %d should be constant", opcode)
                    .isTrue();
        }

        @ParameterizedTest
        @ValueSource(ints = { LCONST_0, LCONST_1 })
        void lconstOpcodes_areConstant(int opcode) {
            assertThat(OpcodeClassifier.isConstantOpcode(opcode))
                    .as("LCONST opcode %d should be constant", opcode)
                    .isTrue();
        }

        @ParameterizedTest
        @ValueSource(ints = { DCONST_0, DCONST_1 })
        void dconstOpcodes_areConstant(int opcode) {
            assertThat(OpcodeClassifier.isConstantOpcode(opcode))
                    .as("DCONST opcode %d should be constant", opcode)
                    .isTrue();
        }

        @Test
        void iadd_isNotConstant() {
            assertThat(OpcodeClassifier.isConstantOpcode(IADD))
                    .as("IADD should NOT be constant")
                    .isFalse();
        }
    }

    // ==================== Int Constant Opcode Tests ====================

    @Nested
    @DisplayName("isIntConstantOpcode")
    class IsIntConstantOpcodeTests {

        @ParameterizedTest
        @ValueSource(ints = { ICONST_M1, ICONST_0, ICONST_1, ICONST_2, ICONST_3, ICONST_4, ICONST_5 })
        void validIntConstants_areIntConstant(int opcode) {
            assertThat(OpcodeClassifier.isIntConstantOpcode(opcode))
                    .as("ICONST opcode %d should be int constant", opcode)
                    .isTrue();
        }

        @Test
        void iconstM1_isInRange() {
            // ICONST_M1 represents the constant -1 and should be classified as an int constant
            assertThat(OpcodeClassifier.isIntConstantOpcode(ICONST_M1))
                    .as("ICONST_M1 should be int constant")
                    .isTrue();
        }

        @Test
        void belowRange_isNotIntConstant() {
            assertThat(OpcodeClassifier.isIntConstantOpcode(ICONST_M1 - 1))
                    .as("Opcode below ICONST_M1 should not be int constant")
                    .isFalse();
        }

        @Test
        void aboveRange_isNotIntConstant() {
            assertThat(OpcodeClassifier.isIntConstantOpcode(ICONST_5 + 1))
                    .as("Opcode above ICONST_5 should not be int constant")
                    .isFalse();
        }
    }

    // ==================== Float Constant Opcode Tests ====================

    @Nested
    @DisplayName("isFloatConstantOpcode")
    class IsFloatConstantOpcodeTests {

        @ParameterizedTest
        @ValueSource(ints = { FCONST_0, FCONST_1, FCONST_2 })
        void validFloatConstants_areFloatConstant(int opcode) {
            assertThat(OpcodeClassifier.isFloatConstantOpcode(opcode))
                    .as("FCONST opcode %d should be float constant", opcode)
                    .isTrue();
        }

        @Test
        void belowRange_isNotFloatConstant() {
            assertThat(OpcodeClassifier.isFloatConstantOpcode(FCONST_0 - 1))
                    .as("Opcode below FCONST_0 should not be float constant")
                    .isFalse();
        }

        @Test
        void aboveRange_isNotFloatConstant() {
            assertThat(OpcodeClassifier.isFloatConstantOpcode(FCONST_2 + 1))
                    .as("Opcode above FCONST_2 should not be float constant")
                    .isFalse();
        }
    }

    // ==================== Long Constant Opcode Tests ====================

    @Nested
    @DisplayName("isLongConstantOpcode")
    class IsLongConstantOpcodeTests {

        @ParameterizedTest
        @ValueSource(ints = { LCONST_0, LCONST_1 })
        void validLongConstants_areLongConstant(int opcode) {
            assertThat(OpcodeClassifier.isLongConstantOpcode(opcode))
                    .as("LCONST opcode %d should be long constant", opcode)
                    .isTrue();
        }

        @Test
        void belowRange_isNotLongConstant() {
            assertThat(OpcodeClassifier.isLongConstantOpcode(LCONST_0 - 1))
                    .as("Opcode below LCONST_0 should not be long constant")
                    .isFalse();
        }

        @Test
        void aboveRange_isNotLongConstant() {
            assertThat(OpcodeClassifier.isLongConstantOpcode(LCONST_1 + 1))
                    .as("Opcode above LCONST_1 should not be long constant")
                    .isFalse();
        }
    }

    // ==================== Double Constant Opcode Tests ====================

    @Nested
    @DisplayName("isDoubleConstantOpcode")
    class IsDoubleConstantOpcodeTests {

        @ParameterizedTest
        @ValueSource(ints = { DCONST_0, DCONST_1 })
        void validDoubleConstants_areDoubleConstant(int opcode) {
            assertThat(OpcodeClassifier.isDoubleConstantOpcode(opcode))
                    .as("DCONST opcode %d should be double constant", opcode)
                    .isTrue();
        }

        @Test
        void belowRange_isNotDoubleConstant() {
            assertThat(OpcodeClassifier.isDoubleConstantOpcode(DCONST_0 - 1))
                    .as("Opcode below DCONST_0 should not be double constant")
                    .isFalse();
        }

        @Test
        void aboveRange_isNotDoubleConstant() {
            assertThat(OpcodeClassifier.isDoubleConstantOpcode(DCONST_1 + 1))
                    .as("Opcode above DCONST_1 should not be double constant")
                    .isFalse();
        }
    }
}
