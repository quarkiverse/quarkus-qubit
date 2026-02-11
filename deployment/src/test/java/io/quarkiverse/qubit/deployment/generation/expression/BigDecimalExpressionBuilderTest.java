package io.quarkiverse.qubit.deployment.generation.expression;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import io.quarkiverse.qubit.deployment.ast.LambdaExpression;
import io.quarkiverse.qubit.deployment.ast.LambdaExpression.BinaryOp.Operator;

/**
 * Unit tests for {@link BigDecimalExpressionBuilder}.
 */
@DisplayName("BigDecimalExpressionBuilder")
class BigDecimalExpressionBuilderTest {

    private BigDecimalExpressionBuilder builder;

    @BeforeEach
    void setUp() {
        builder = BigDecimalExpressionBuilder.INSTANCE;
    }

    @Nested
    @DisplayName("mapMethodToOperator()")
    class MapMethodToOperatorTests {

        @ParameterizedTest(name = "{0} should map to {1}")
        @CsvSource({
                "add, ADD",
                "subtract, SUB",
                "multiply, MUL",
                "divide, DIV"
        })
        void shouldMapBigDecimalMethodsToOperators(String methodName, String operatorName) {
            Operator result = BigDecimalExpressionBuilder.mapMethodToOperator(methodName);

            assertThat(result)
                    .as("mapMethodToOperator(\"%s\") should return %s", methodName, operatorName)
                    .isNotNull()
                    .isEqualTo(Operator.valueOf(operatorName));
        }

        @Test
        @DisplayName("add maps to ADD")
        void addMapsToADD() {
            assertThat(BigDecimalExpressionBuilder.mapMethodToOperator("add")).isEqualTo(Operator.ADD);
        }

        @Test
        @DisplayName("subtract maps to SUB")
        void subtractMapsToSUB() {
            assertThat(BigDecimalExpressionBuilder.mapMethodToOperator("subtract")).isEqualTo(Operator.SUB);
        }

        @Test
        @DisplayName("multiply maps to MUL")
        void multiplyMapsToMUL() {
            assertThat(BigDecimalExpressionBuilder.mapMethodToOperator("multiply")).isEqualTo(Operator.MUL);
        }

        @Test
        @DisplayName("divide maps to DIV")
        void divideMapsToDiv() {
            assertThat(BigDecimalExpressionBuilder.mapMethodToOperator("divide")).isEqualTo(Operator.DIV);
        }

        @ParameterizedTest(name = "{0} should return null")
        @ValueSource(strings = { "abs", "compareTo", "toString", "intValue", "pow", "remainder", "negate" })
        void shouldReturnNullForUnknownMethods(String methodName) {
            Operator result = BigDecimalExpressionBuilder.mapMethodToOperator(methodName);

            assertThat(result)
                    .as("mapMethodToOperator(\"%s\") should return null", methodName)
                    .isNull();
        }

        @Test
        @DisplayName("empty string returns null")
        void emptyStringReturnsNull() {
            assertThat(BigDecimalExpressionBuilder.mapMethodToOperator("")).isNull();
        }
    }

    @Nested
    @DisplayName("isBigDecimalArithmetic()")
    class IsBigDecimalArithmeticTests {

        @ParameterizedTest(name = "{0} should return true")
        @ValueSource(strings = { "add", "subtract", "multiply", "divide" })
        void shouldReturnTrueForBigDecimalArithmeticMethods(String methodName) {
            LambdaExpression.MethodCall methodCall = createMethodCall(methodName);

            boolean result = builder.isBigDecimalArithmetic(methodCall);

            assertThat(result)
                    .as("isBigDecimalArithmetic(\"%s\") should return true", methodName)
                    .isTrue();
        }

        @ParameterizedTest(name = "{0} should return false")
        @ValueSource(strings = { "abs", "compareTo", "toString", "intValue", "pow", "remainder", "negate", "" })
        void shouldReturnFalseForNonArithmeticMethods(String methodName) {
            LambdaExpression.MethodCall methodCall = createMethodCall(methodName);

            boolean result = builder.isBigDecimalArithmetic(methodCall);

            assertThat(result)
                    .as("isBigDecimalArithmetic(\"%s\") should return false", methodName)
                    .isFalse();
        }

        @Test
        @DisplayName("should detect add method")
        void shouldDetectAddMethod() {
            LambdaExpression.MethodCall methodCall = createMethodCall("add");

            assertThat(builder.isBigDecimalArithmetic(methodCall)).isTrue();
        }

        @Test
        @DisplayName("should detect subtract method")
        void shouldDetectSubtractMethod() {
            LambdaExpression.MethodCall methodCall = createMethodCall("subtract");

            assertThat(builder.isBigDecimalArithmetic(methodCall)).isTrue();
        }

        @Test
        @DisplayName("should detect multiply method")
        void shouldDetectMultiplyMethod() {
            LambdaExpression.MethodCall methodCall = createMethodCall("multiply");

            assertThat(builder.isBigDecimalArithmetic(methodCall)).isTrue();
        }

        @Test
        @DisplayName("should detect divide method")
        void shouldDetectDivideMethod() {
            LambdaExpression.MethodCall methodCall = createMethodCall("divide");

            assertThat(builder.isBigDecimalArithmetic(methodCall)).isTrue();
        }
    }

    /**
     * Helper method to create a mock MethodCall with the given method name.
     */
    private LambdaExpression.MethodCall createMethodCall(String methodName) {
        return new LambdaExpression.MethodCall(
                null, // target (not used)
                methodName, // methodName
                List.of(), // arguments (not used)
                java.math.BigDecimal.class // returnType
        );
    }
}
