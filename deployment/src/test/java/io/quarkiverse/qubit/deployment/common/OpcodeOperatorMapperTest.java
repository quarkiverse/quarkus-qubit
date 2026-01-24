package io.quarkiverse.qubit.deployment.common;

import io.quarkiverse.qubit.deployment.analysis.ControlFlowAnalyzer.LabelClassification;
import io.quarkiverse.qubit.deployment.ast.LambdaExpression.BinaryOp.Operator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.Opcodes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link OpcodeOperatorMapper}.
 */
class OpcodeOperatorMapperTest {

    // ========================================================================
    // mapSingleOperandOp Tests
    // ========================================================================

    @Nested
    class MapSingleOperandOpTests {

        @Test
        void mapSingleOperandOp_IFLE_notInverted_returnsLE() {
            Operator result = OpcodeOperatorMapper.mapSingleOperandOp(Opcodes.IFLE, false);
            assertThat(result).isEqualTo(Operator.LE);
        }

        @Test
        void mapSingleOperandOp_IFLE_inverted_returnsGT() {
            Operator result = OpcodeOperatorMapper.mapSingleOperandOp(Opcodes.IFLE, true);
            assertThat(result).isEqualTo(Operator.GT);
        }

        @Test
        void mapSingleOperandOp_IFLT_notInverted_returnsLT() {
            Operator result = OpcodeOperatorMapper.mapSingleOperandOp(Opcodes.IFLT, false);
            assertThat(result).isEqualTo(Operator.LT);
        }

        @Test
        void mapSingleOperandOp_IFLT_inverted_returnsGE() {
            Operator result = OpcodeOperatorMapper.mapSingleOperandOp(Opcodes.IFLT, true);
            assertThat(result).isEqualTo(Operator.GE);
        }

        @Test
        void mapSingleOperandOp_IFGE_notInverted_returnsGE() {
            Operator result = OpcodeOperatorMapper.mapSingleOperandOp(Opcodes.IFGE, false);
            assertThat(result).isEqualTo(Operator.GE);
        }

        @Test
        void mapSingleOperandOp_IFGE_inverted_returnsLT() {
            Operator result = OpcodeOperatorMapper.mapSingleOperandOp(Opcodes.IFGE, true);
            assertThat(result).isEqualTo(Operator.LT);
        }

        @Test
        void mapSingleOperandOp_IFGT_notInverted_returnsGT() {
            Operator result = OpcodeOperatorMapper.mapSingleOperandOp(Opcodes.IFGT, false);
            assertThat(result).isEqualTo(Operator.GT);
        }

        @Test
        void mapSingleOperandOp_IFGT_inverted_returnsLE() {
            Operator result = OpcodeOperatorMapper.mapSingleOperandOp(Opcodes.IFGT, true);
            assertThat(result).isEqualTo(Operator.LE);
        }

        @Test
        void mapSingleOperandOp_invalidOpcode_throwsException() {
            assertThatThrownBy(() -> OpcodeOperatorMapper.mapSingleOperandOp(Opcodes.NOP, false))
                    .isInstanceOf(BytecodeAnalysisException.class)
                    .hasMessageContaining("single-operand comparison");
        }
    }

    // ========================================================================
    // mapTwoOperandOp Tests
    // ========================================================================

    @Nested
    class MapTwoOperandOpTests {

        @Test
        void mapTwoOperandOp_IF_ICMPGT_notInverted_returnsGT() {
            Operator result = OpcodeOperatorMapper.mapTwoOperandOp(Opcodes.IF_ICMPGT, false);
            assertThat(result).isEqualTo(Operator.GT);
        }

        @Test
        void mapTwoOperandOp_IF_ICMPGT_inverted_returnsLE() {
            Operator result = OpcodeOperatorMapper.mapTwoOperandOp(Opcodes.IF_ICMPGT, true);
            assertThat(result).isEqualTo(Operator.LE);
        }

        @Test
        void mapTwoOperandOp_IF_ICMPGE_notInverted_returnsGE() {
            Operator result = OpcodeOperatorMapper.mapTwoOperandOp(Opcodes.IF_ICMPGE, false);
            assertThat(result).isEqualTo(Operator.GE);
        }

        @Test
        void mapTwoOperandOp_IF_ICMPGE_inverted_returnsLT() {
            Operator result = OpcodeOperatorMapper.mapTwoOperandOp(Opcodes.IF_ICMPGE, true);
            assertThat(result).isEqualTo(Operator.LT);
        }

        @Test
        void mapTwoOperandOp_IF_ICMPLT_notInverted_returnsLT() {
            Operator result = OpcodeOperatorMapper.mapTwoOperandOp(Opcodes.IF_ICMPLT, false);
            assertThat(result).isEqualTo(Operator.LT);
        }

        @Test
        void mapTwoOperandOp_IF_ICMPLT_inverted_returnsGE() {
            Operator result = OpcodeOperatorMapper.mapTwoOperandOp(Opcodes.IF_ICMPLT, true);
            assertThat(result).isEqualTo(Operator.GE);
        }

        @Test
        void mapTwoOperandOp_IF_ICMPLE_notInverted_returnsLE() {
            Operator result = OpcodeOperatorMapper.mapTwoOperandOp(Opcodes.IF_ICMPLE, false);
            assertThat(result).isEqualTo(Operator.LE);
        }

        @Test
        void mapTwoOperandOp_IF_ICMPLE_inverted_returnsGT() {
            Operator result = OpcodeOperatorMapper.mapTwoOperandOp(Opcodes.IF_ICMPLE, true);
            assertThat(result).isEqualTo(Operator.GT);
        }

        @Test
        void mapTwoOperandOp_IF_ICMPEQ_notInverted_returnsEQ() {
            Operator result = OpcodeOperatorMapper.mapTwoOperandOp(Opcodes.IF_ICMPEQ, false);
            assertThat(result).isEqualTo(Operator.EQ);
        }

        @Test
        void mapTwoOperandOp_IF_ICMPEQ_inverted_returnsNE() {
            Operator result = OpcodeOperatorMapper.mapTwoOperandOp(Opcodes.IF_ICMPEQ, true);
            assertThat(result).isEqualTo(Operator.NE);
        }

        @Test
        void mapTwoOperandOp_IF_ICMPNE_notInverted_returnsNE() {
            Operator result = OpcodeOperatorMapper.mapTwoOperandOp(Opcodes.IF_ICMPNE, false);
            assertThat(result).isEqualTo(Operator.NE);
        }

        @Test
        void mapTwoOperandOp_IF_ICMPNE_inverted_returnsEQ() {
            Operator result = OpcodeOperatorMapper.mapTwoOperandOp(Opcodes.IF_ICMPNE, true);
            assertThat(result).isEqualTo(Operator.EQ);
        }

        @Test
        void mapTwoOperandOp_IF_ACMPEQ_notInverted_returnsEQ() {
            Operator result = OpcodeOperatorMapper.mapTwoOperandOp(Opcodes.IF_ACMPEQ, false);
            assertThat(result).isEqualTo(Operator.EQ);
        }

        @Test
        void mapTwoOperandOp_IF_ACMPEQ_inverted_returnsNE() {
            Operator result = OpcodeOperatorMapper.mapTwoOperandOp(Opcodes.IF_ACMPEQ, true);
            assertThat(result).isEqualTo(Operator.NE);
        }

        @Test
        void mapTwoOperandOp_IF_ACMPNE_notInverted_returnsNE() {
            Operator result = OpcodeOperatorMapper.mapTwoOperandOp(Opcodes.IF_ACMPNE, false);
            assertThat(result).isEqualTo(Operator.NE);
        }

        @Test
        void mapTwoOperandOp_IF_ACMPNE_inverted_returnsEQ() {
            Operator result = OpcodeOperatorMapper.mapTwoOperandOp(Opcodes.IF_ACMPNE, true);
            assertThat(result).isEqualTo(Operator.EQ);
        }

        @Test
        void mapTwoOperandOp_unknownOpcode_returnsEQ() {
            // Unknown opcode defaults to EQ
            Operator result = OpcodeOperatorMapper.mapTwoOperandOp(Opcodes.NOP, false);
            assertThat(result).isEqualTo(Operator.EQ);
        }
    }

    // ========================================================================
    // isSuccessJumpSingleOperand Tests
    // ========================================================================

    @Nested
    class IsSuccessJumpSingleOperandTests {

        @Test
        void isSuccessJumpSingleOperand_IFLT_returnsTrue() {
            assertThat(OpcodeOperatorMapper.isSuccessJumpSingleOperand(Opcodes.IFLT)).isTrue();
        }

        @Test
        void isSuccessJumpSingleOperand_IFGT_returnsTrue() {
            assertThat(OpcodeOperatorMapper.isSuccessJumpSingleOperand(Opcodes.IFGT)).isTrue();
        }

        @Test
        void isSuccessJumpSingleOperand_IFLE_returnsFalse() {
            assertThat(OpcodeOperatorMapper.isSuccessJumpSingleOperand(Opcodes.IFLE)).isFalse();
        }

        @Test
        void isSuccessJumpSingleOperand_IFGE_returnsFalse() {
            assertThat(OpcodeOperatorMapper.isSuccessJumpSingleOperand(Opcodes.IFGE)).isFalse();
        }

        @Test
        void isSuccessJumpSingleOperand_unknownOpcode_returnsFalse() {
            assertThat(OpcodeOperatorMapper.isSuccessJumpSingleOperand(Opcodes.NOP)).isFalse();
        }
    }

    // ========================================================================
    // isSuccessJumpTwoOperand Tests
    // ========================================================================

    @Nested
    class IsSuccessJumpTwoOperandTests {

        @Test
        void isSuccessJumpTwoOperand_IF_ICMPLT_returnsTrue() {
            assertThat(OpcodeOperatorMapper.isSuccessJumpTwoOperand(Opcodes.IF_ICMPLT)).isTrue();
        }

        @Test
        void isSuccessJumpTwoOperand_IF_ICMPGT_returnsTrue() {
            assertThat(OpcodeOperatorMapper.isSuccessJumpTwoOperand(Opcodes.IF_ICMPGT)).isTrue();
        }

        @Test
        void isSuccessJumpTwoOperand_IF_ICMPEQ_returnsTrue() {
            assertThat(OpcodeOperatorMapper.isSuccessJumpTwoOperand(Opcodes.IF_ICMPEQ)).isTrue();
        }

        @Test
        void isSuccessJumpTwoOperand_IF_ICMPNE_returnsTrue() {
            assertThat(OpcodeOperatorMapper.isSuccessJumpTwoOperand(Opcodes.IF_ICMPNE)).isTrue();
        }

        @Test
        void isSuccessJumpTwoOperand_IF_ACMPEQ_returnsTrue() {
            assertThat(OpcodeOperatorMapper.isSuccessJumpTwoOperand(Opcodes.IF_ACMPEQ)).isTrue();
        }

        @Test
        void isSuccessJumpTwoOperand_IF_ACMPNE_returnsTrue() {
            assertThat(OpcodeOperatorMapper.isSuccessJumpTwoOperand(Opcodes.IF_ACMPNE)).isTrue();
        }

        @Test
        void isSuccessJumpTwoOperand_IF_ICMPLE_returnsFalse() {
            assertThat(OpcodeOperatorMapper.isSuccessJumpTwoOperand(Opcodes.IF_ICMPLE)).isFalse();
        }

        @Test
        void isSuccessJumpTwoOperand_IF_ICMPGE_returnsFalse() {
            assertThat(OpcodeOperatorMapper.isSuccessJumpTwoOperand(Opcodes.IF_ICMPGE)).isFalse();
        }

        @Test
        void isSuccessJumpTwoOperand_unknownOpcode_returnsFalse() {
            assertThat(OpcodeOperatorMapper.isSuccessJumpTwoOperand(Opcodes.NOP)).isFalse();
        }
    }

    // ========================================================================
    // determineSingleOperandOperator Tests
    // ========================================================================

    @Nested
    class DetermineSingleOperandOperatorTests {

        @Test
        void determineSingleOperandOperator_intermediate_successJump_inverts() {
            // IFLT is a success jump, so with INTERMEDIATE it should be inverted
            Operator result = OpcodeOperatorMapper.determineSingleOperandOperator(
                    LabelClassification.INTERMEDIATE, null, Opcodes.IFLT);
            // IFLT success jump = true, so invert = !true = false
            // mapSingleOperandOp(IFLT, false) = LT
            assertThat(result).isEqualTo(Operator.LT);
        }

        @Test
        void determineSingleOperandOperator_intermediate_nonSuccessJump_doesNotInvert() {
            // IFLE is NOT a success jump, so with INTERMEDIATE it should NOT be inverted
            Operator result = OpcodeOperatorMapper.determineSingleOperandOperator(
                    LabelClassification.INTERMEDIATE, null, Opcodes.IFLE);
            // IFLE success jump = false, so invert = !false = true
            // mapSingleOperandOp(IFLE, true) = GT
            assertThat(result).isEqualTo(Operator.GT);
        }

        @Test
        void determineSingleOperandOperator_trueSink_jumpTargetTrue_noInversion() {
            Operator result = OpcodeOperatorMapper.determineSingleOperandOperator(
                    LabelClassification.TRUE_SINK, true, Opcodes.IFGT);
            // jumpTarget is true, FALSE.equals(true) = false, so invert = false
            // mapSingleOperandOp(IFGT, false) = GT
            assertThat(result).isEqualTo(Operator.GT);
        }

        @Test
        void determineSingleOperandOperator_falseSink_jumpTargetFalse_inverts() {
            Operator result = OpcodeOperatorMapper.determineSingleOperandOperator(
                    LabelClassification.FALSE_SINK, false, Opcodes.IFGT);
            // jumpTarget is false, FALSE.equals(false) = true, so invert = true
            // mapSingleOperandOp(IFGT, true) = LE
            assertThat(result).isEqualTo(Operator.LE);
        }

        @Test
        void determineSingleOperandOperator_trueSink_jumpTargetNull_noInversion() {
            Operator result = OpcodeOperatorMapper.determineSingleOperandOperator(
                    LabelClassification.TRUE_SINK, null, Opcodes.IFGE);
            // jumpTarget is null, FALSE.equals(null) = false, so invert = false
            // mapSingleOperandOp(IFGE, false) = GE
            assertThat(result).isEqualTo(Operator.GE);
        }
    }

    // ========================================================================
    // determineTwoOperandOperator Tests
    // ========================================================================

    @Nested
    class DetermineTwoOperandOperatorTests {

        @Test
        void determineTwoOperandOperator_intermediate_successJump_inverts() {
            // IF_ICMPGT is a success jump
            Operator result = OpcodeOperatorMapper.determineTwoOperandOperator(
                    LabelClassification.INTERMEDIATE, null, Opcodes.IF_ICMPGT);
            // IF_ICMPGT success jump = true, so invert = !true = false
            // mapTwoOperandOp(IF_ICMPGT, false) = GT
            assertThat(result).isEqualTo(Operator.GT);
        }

        @Test
        void determineTwoOperandOperator_intermediate_nonSuccessJump_doesNotInvert() {
            // IF_ICMPLE is NOT a success jump
            Operator result = OpcodeOperatorMapper.determineTwoOperandOperator(
                    LabelClassification.INTERMEDIATE, null, Opcodes.IF_ICMPLE);
            // IF_ICMPLE success jump = false, so invert = !false = true
            // mapTwoOperandOp(IF_ICMPLE, true) = GT
            assertThat(result).isEqualTo(Operator.GT);
        }

        @Test
        void determineTwoOperandOperator_trueSink_jumpTargetTrue_noInversion() {
            Operator result = OpcodeOperatorMapper.determineTwoOperandOperator(
                    LabelClassification.TRUE_SINK, true, Opcodes.IF_ICMPEQ);
            // jumpTarget is true, FALSE.equals(true) = false, so invert = false
            // mapTwoOperandOp(IF_ICMPEQ, false) = EQ
            assertThat(result).isEqualTo(Operator.EQ);
        }

        @Test
        void determineTwoOperandOperator_falseSink_jumpTargetFalse_inverts() {
            Operator result = OpcodeOperatorMapper.determineTwoOperandOperator(
                    LabelClassification.FALSE_SINK, false, Opcodes.IF_ICMPEQ);
            // jumpTarget is false, FALSE.equals(false) = true, so invert = true
            // mapTwoOperandOp(IF_ICMPEQ, true) = NE
            assertThat(result).isEqualTo(Operator.NE);
        }

        @Test
        void determineTwoOperandOperator_trueSink_jumpTargetNull_noInversion() {
            Operator result = OpcodeOperatorMapper.determineTwoOperandOperator(
                    LabelClassification.TRUE_SINK, null, Opcodes.IF_ACMPNE);
            // jumpTarget is null, FALSE.equals(null) = false, so invert = false
            // mapTwoOperandOp(IF_ACMPNE, false) = NE
            assertThat(result).isEqualTo(Operator.NE);
        }

        @Test
        void determineTwoOperandOperator_intermediate_equalityOpcodes() {
            // Test that equality opcodes work correctly in intermediate context
            Operator eq = OpcodeOperatorMapper.determineTwoOperandOperator(
                    LabelClassification.INTERMEDIATE, null, Opcodes.IF_ICMPEQ);
            Operator ne = OpcodeOperatorMapper.determineTwoOperandOperator(
                    LabelClassification.INTERMEDIATE, null, Opcodes.IF_ICMPNE);

            // Both are success jumps, so no inversion
            assertThat(eq).isEqualTo(Operator.EQ);
            assertThat(ne).isEqualTo(Operator.NE);
        }
    }
}
