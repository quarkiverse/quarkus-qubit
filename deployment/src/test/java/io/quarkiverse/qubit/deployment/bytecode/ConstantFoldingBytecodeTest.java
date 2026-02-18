package io.quarkiverse.qubit.deployment.bytecode;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import io.quarkiverse.qubit.deployment.ast.LambdaExpression;
import io.quarkiverse.qubit.deployment.testutil.TestUtils;

/**
 * Bytecode analysis tests for external method constant folding.
 * Verifies that static method calls with constant or captured variable arguments
 * are correctly folded during bytecode analysis.
 */
class ConstantFoldingBytecodeTest extends PrecompiledLambdaAnalyzer {

    @Nested
    @DisplayName("All-constant args: build-time evaluation")
    class AllConstantTests {

        @Test
        @DisplayName("TestUtils.toUpper(\"hello\") folds to Constant(\"HELLO\")")
        void foldedAllConstant_foldsToConstant() {
            LambdaExpression result = analyzeLambda("foldedAllConstant");

            // Should be BinaryOp(EQ, FieldAccess("firstName"), Constant("HELLO"))
            assertThat(result).isInstanceOf(LambdaExpression.BinaryOp.class);
            var binOp = (LambdaExpression.BinaryOp) result;
            assertThat(binOp.operator()).isEqualTo(LambdaExpression.BinaryOp.Operator.EQ);
            assertThat(binOp.left()).isInstanceOf(LambdaExpression.FieldAccess.class);

            // The right side should be a Constant with the folded value "HELLO"
            assertThat(binOp.right()).isInstanceOf(LambdaExpression.Constant.class);
            var constant = (LambdaExpression.Constant) binOp.right();
            assertThat(constant.value()).isEqualTo("HELLO");
        }

        @Test
        @DisplayName("TestUtils.withPrefix(\"Mr.\", \"Smith\") folds to Constant(\"Mr.Smith\")")
        void foldedTwoArgConstant_foldsToConstant() {
            LambdaExpression result = analyzeLambda("foldedTwoArgConstant");

            assertThat(result).isInstanceOf(LambdaExpression.BinaryOp.class);
            var binOp = (LambdaExpression.BinaryOp) result;

            assertThat(binOp.right()).isInstanceOf(LambdaExpression.Constant.class);
            var constant = (LambdaExpression.Constant) binOp.right();
            assertThat(constant.value()).isEqualTo("Mr.Smith");
        }

        @Test
        @DisplayName("TestUtils.doubleValue(15) folds to Constant(30)")
        void foldedIntConstant_foldsToConstant() {
            LambdaExpression result = analyzeLambda("foldedIntConstant");

            // Should be BinaryOp(GT, FieldAccess("age"), Constant(30))
            assertThat(result).isInstanceOf(LambdaExpression.BinaryOp.class);
            var binOp = (LambdaExpression.BinaryOp) result;
            assertThat(binOp.operator()).isEqualTo(LambdaExpression.BinaryOp.Operator.GT);

            assertThat(binOp.right()).isInstanceOf(LambdaExpression.Constant.class);
            var constant = (LambdaExpression.Constant) binOp.right();
            assertThat(constant.value()).isEqualTo(30);
        }
    }

    @Nested
    @DisplayName("Captured variable args: FoldedMethodCall")
    class CapturedVariableTests {

        @Test
        @DisplayName("TestUtils.toUpper(searchTerm) creates FoldedMethodCall")
        void foldedCapturedVariable_createsFoldedMethodCall() {
            LambdaExpression result = analyzeLambda("foldedCapturedVariable");

            // Should be BinaryOp(EQ, FieldAccess("firstName"), FoldedMethodCall(...))
            assertThat(result).isInstanceOf(LambdaExpression.BinaryOp.class);
            var binOp = (LambdaExpression.BinaryOp) result;

            assertThat(binOp.right()).isInstanceOf(LambdaExpression.FoldedMethodCall.class);
            var folded = (LambdaExpression.FoldedMethodCall) binOp.right();
            assertThat(folded.ownerClass()).isEqualTo(TestUtils.class);
            assertThat(folded.methodName()).isEqualTo("toUpper");
            assertThat(folded.returnType()).isEqualTo(String.class);
            assertThat(folded.arguments()).hasSize(1);
            assertThat(folded.arguments().getFirst()).isInstanceOf(LambdaExpression.CapturedVariable.class);
        }

        @Test
        @DisplayName("TestUtils.withPrefix(prefix, name) creates FoldedMethodCall with 2 args")
        void foldedTwoArgCaptured_createsFoldedMethodCall() {
            LambdaExpression result = analyzeLambda("foldedTwoArgCaptured");

            assertThat(result).isInstanceOf(LambdaExpression.BinaryOp.class);
            var binOp = (LambdaExpression.BinaryOp) result;

            assertThat(binOp.right()).isInstanceOf(LambdaExpression.FoldedMethodCall.class);
            var folded = (LambdaExpression.FoldedMethodCall) binOp.right();
            assertThat(folded.ownerClass()).isEqualTo(TestUtils.class);
            assertThat(folded.methodName()).isEqualTo("withPrefix");
            assertThat(folded.returnType()).isEqualTo(String.class);
            assertThat(folded.arguments()).hasSize(2);
            assertThat(folded.arguments().get(0)).isInstanceOf(LambdaExpression.CapturedVariable.class);
            assertThat(folded.arguments().get(1)).isInstanceOf(LambdaExpression.CapturedVariable.class);
        }

        @Test
        @DisplayName("TestUtils.doubleValue(minAge) creates FoldedMethodCall")
        void foldedIntCaptured_createsFoldedMethodCall() {
            LambdaExpression result = analyzeLambda("foldedIntCaptured");

            assertThat(result).isInstanceOf(LambdaExpression.BinaryOp.class);
            var binOp = (LambdaExpression.BinaryOp) result;

            assertThat(binOp.right()).isInstanceOf(LambdaExpression.FoldedMethodCall.class);
            var folded = (LambdaExpression.FoldedMethodCall) binOp.right();
            assertThat(folded.ownerClass()).isEqualTo(TestUtils.class);
            assertThat(folded.methodName()).isEqualTo("doubleValue");
            assertThat(folded.returnType()).isEqualTo(int.class);
            assertThat(folded.arguments()).hasSize(1);
            assertThat(folded.arguments().getFirst()).isInstanceOf(LambdaExpression.CapturedVariable.class);
        }
    }
}
