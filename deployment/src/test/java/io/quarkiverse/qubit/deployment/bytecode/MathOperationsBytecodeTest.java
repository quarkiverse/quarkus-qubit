package io.quarkiverse.qubit.deployment.bytecode;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import io.quarkiverse.qubit.deployment.ast.LambdaExpression;
import io.quarkiverse.qubit.deployment.ast.LambdaExpression.MathFunction;
import io.quarkiverse.qubit.deployment.ast.LambdaExpression.MathFunction.MathOp;

@DisplayName("Math operations bytecode analysis")
class MathOperationsBytecodeTest extends PrecompiledLambdaAnalyzer {

    @Nested
    @DisplayName("Unary math functions")
    class UnaryMathFunctions {

        static Stream<Arguments> unaryMathMethods() {
            return Stream.of(
                    Arguments.of("mathAbs", MathOp.ABS),
                    Arguments.of("mathSqrt", MathOp.SQRT),
                    Arguments.of("mathCeil", MathOp.CEILING),
                    Arguments.of("mathFloor", MathOp.FLOOR),
                    Arguments.of("mathExp", MathOp.EXP),
                    Arguments.of("mathLog", MathOp.LN),
                    Arguments.of("integerSignum", MathOp.SIGN));
        }

        @ParameterizedTest(name = "{0} -> MathOp.{1}")
        @MethodSource("unaryMathMethods")
        void unaryMathMethod_producesMathFunctionNode(String lambdaMethod, MathOp expectedOp) {
            LambdaExpression expr = analyzeLambda(lambdaMethod);
            // Top level is BinaryOp (comparison like > 5)
            assertThat(expr).isInstanceOf(LambdaExpression.BinaryOp.class);
            LambdaExpression.BinaryOp comparison = (LambdaExpression.BinaryOp) expr;
            // Left side should be MathFunction
            assertThat(comparison.left()).isInstanceOf(MathFunction.class);
            MathFunction mathFunc = (MathFunction) comparison.left();
            assertThat(mathFunc.op()).isEqualTo(expectedOp);
            assertThat(mathFunc.secondOperand()).isNull();
        }
    }

    @Nested
    @DisplayName("Binary math functions")
    class BinaryMathFunctions {

        @Test
        @DisplayName("Math.pow(p.age, 2) produces POWER node")
        void mathPow_producesPowerNode() {
            LambdaExpression expr = analyzeLambda("mathPow");
            LambdaExpression.BinaryOp comparison = (LambdaExpression.BinaryOp) expr;
            assertThat(comparison.left()).isInstanceOf(MathFunction.class);
            MathFunction mathFunc = (MathFunction) comparison.left();
            assertThat(mathFunc.op()).isEqualTo(MathOp.POWER);
            assertThat(mathFunc.secondOperand()).isNotNull();
        }

        @Test
        @DisplayName("Math.round(p.salary) produces ROUND node with 0 decimal places")
        void mathRound_producesRoundNodeWithZero() {
            LambdaExpression expr = analyzeLambda("mathRound");
            LambdaExpression.BinaryOp comparison = (LambdaExpression.BinaryOp) expr;
            assertThat(comparison.left()).isInstanceOf(MathFunction.class);
            MathFunction mathFunc = (MathFunction) comparison.left();
            assertThat(mathFunc.op()).isEqualTo(MathOp.ROUND);
            assertThat(mathFunc.secondOperand()).isInstanceOf(LambdaExpression.Constant.class);
            LambdaExpression.Constant decPlaces = (LambdaExpression.Constant) mathFunc.secondOperand();
            assertThat(decPlaces.value()).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("Unary negation")
    class UnaryNegation {

        @Test
        @DisplayName("-p.age produces NEG node")
        void unaryNegation_producesNegNode() {
            LambdaExpression expr = analyzeLambda("unaryNegation");
            assertThat(expr).isInstanceOf(LambdaExpression.BinaryOp.class);
            LambdaExpression.BinaryOp comparison = (LambdaExpression.BinaryOp) expr;
            // Left side: -p.age -> MathFunction.NEG(FieldAccess("age"))
            assertThat(comparison.left()).isInstanceOf(MathFunction.class);
            MathFunction neg = (MathFunction) comparison.left();
            assertThat(neg.op()).isEqualTo(MathOp.NEG);
        }
    }

    @Nested
    @DisplayName("Math with arithmetic expressions")
    class MathWithArithmetic {

        @Test
        @DisplayName("Math.abs(p.age - target) produces ABS with nested expression")
        void mathAbsWithArithmetic_producesNestedNode() {
            LambdaExpression expr = analyzeLambda("mathAbsArithmetic");
            LambdaExpression.BinaryOp comparison = (LambdaExpression.BinaryOp) expr;
            assertThat(comparison.left()).isInstanceOf(MathFunction.class);
            MathFunction absFunc = (MathFunction) comparison.left();
            assertThat(absFunc.op()).isEqualTo(MathOp.ABS);
            assertThat(absFunc.operand()).isInstanceOf(LambdaExpression.BinaryOp.class);
        }
    }
}
