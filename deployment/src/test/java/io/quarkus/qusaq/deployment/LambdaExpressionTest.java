package io.quarkus.qusaq.deployment;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for LambdaExpression AST classes.
 */
class LambdaExpressionTest {

    @Test
    void testBinaryOpCreation() {
        var left = new LambdaExpression.Constant(10, Integer.class);
        var right = new LambdaExpression.Constant(20, Integer.class);
        var binOp = new LambdaExpression.BinaryOp(
                left,
                LambdaExpression.BinaryOp.Operator.EQ,
                right);

        assertThat(binOp.left()).isEqualTo(left);
        assertThat(binOp.right()).isEqualTo(right);
        assertThat(binOp.operator()).isEqualTo(LambdaExpression.BinaryOp.Operator.EQ);
    }

    @Test
    void testFieldAccess() {
        var fieldAccess = new LambdaExpression.FieldAccess("name", String.class);

        assertThat(fieldAccess.fieldName()).isEqualTo("name");
        assertThat(fieldAccess.fieldType()).isEqualTo(String.class);
    }

    @Test
    void testFieldAccessNullValidation() {
        assertThatThrownBy(() ->
                new LambdaExpression.FieldAccess(null, String.class))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("Field name cannot be null");
    }

    @Test
    void testMethodCall() {
        var target = new LambdaExpression.Parameter("entity", Object.class, 0);
        var arg = new LambdaExpression.Constant("test", String.class);
        var methodCall = new LambdaExpression.MethodCall(
                target,
                "equals",
                List.of(arg),
                Boolean.class);

        assertThat(methodCall.target()).isEqualTo(target);
        assertThat(methodCall.methodName()).isEqualTo("equals");
        assertThat(methodCall.arguments()).containsExactly(arg);
        assertThat(methodCall.returnType()).isEqualTo(Boolean.class);
    }

    @Test
    void testConstant() {
        var constant = new LambdaExpression.Constant("test", String.class);

        assertThat(constant.value()).isEqualTo("test");
        assertThat(constant.type()).isEqualTo(String.class);
    }

    @Test
    void testParameter() {
        var param = new LambdaExpression.Parameter("entity", Object.class, 0);

        assertThat(param.name()).isEqualTo("entity");
        assertThat(param.type()).isEqualTo(Object.class);
        assertThat(param.index()).isZero();
    }

    @Test
    void testNullLiteral() {
        var nullLit = new LambdaExpression.NullLiteral(String.class);

        assertThat(nullLit.expectedType()).isEqualTo(String.class);
    }

    @Test
    void testCast() {
        var expr = new LambdaExpression.Constant(10, Integer.class);
        var cast = new LambdaExpression.Cast(expr, Long.class);

        assertThat(cast.expression()).isEqualTo(expr);
        assertThat(cast.targetType()).isEqualTo(Long.class);
    }

    @Test
    void testInstanceOf() {
        var expr = new LambdaExpression.Parameter("obj", Object.class, 0);
        var instanceOf = new LambdaExpression.InstanceOf(expr, String.class);

        assertThat(instanceOf.expression()).isEqualTo(expr);
        assertThat(instanceOf.targetType()).isEqualTo(String.class);
    }

    @Test
    void testConditional() {
        var condition = new LambdaExpression.BinaryOp(
                new LambdaExpression.Constant(5, Integer.class),
                LambdaExpression.BinaryOp.Operator.GT,
                new LambdaExpression.Constant(3, Integer.class));

        var trueValue = new LambdaExpression.Constant("yes", String.class);
        var falseValue = new LambdaExpression.Constant("no", String.class);

        var conditional = new LambdaExpression.Conditional(condition, trueValue, falseValue);

        assertThat(conditional.condition()).isEqualTo(condition);
        assertThat(conditional.trueValue()).isEqualTo(trueValue);
        assertThat(conditional.falseValue()).isEqualTo(falseValue);
    }

    @Test
    void testUnaryOp() {
        var operand = new LambdaExpression.Constant(true, Boolean.class);
        var unaryOp = new LambdaExpression.UnaryOp(
                LambdaExpression.UnaryOp.Operator.NOT,
                operand);

        assertThat(unaryOp.operator()).isEqualTo(LambdaExpression.UnaryOp.Operator.NOT);
        assertThat(unaryOp.operand()).isEqualTo(operand);
    }

    @Test
    void testOperatorSymbols() {
        assertThat(LambdaExpression.BinaryOp.Operator.EQ.symbol()).isEqualTo("==");
        assertThat(LambdaExpression.BinaryOp.Operator.NE.symbol()).isEqualTo("!=");
        assertThat(LambdaExpression.BinaryOp.Operator.LT.symbol()).isEqualTo("<");
        assertThat(LambdaExpression.BinaryOp.Operator.LE.symbol()).isEqualTo("<=");
        assertThat(LambdaExpression.BinaryOp.Operator.GT.symbol()).isEqualTo(">");
        assertThat(LambdaExpression.BinaryOp.Operator.GE.symbol()).isEqualTo(">=");
        assertThat(LambdaExpression.BinaryOp.Operator.AND.symbol()).isEqualTo("&&");
        assertThat(LambdaExpression.BinaryOp.Operator.OR.symbol()).isEqualTo("||");
        assertThat(LambdaExpression.UnaryOp.Operator.NOT.symbol()).isEqualTo("!");
    }

    @Test
    void testComplexExpressionTree() {
        // Build: (age > 25 && active) || salary > 50000
        var age = new LambdaExpression.FieldAccess("age", Integer.class);
        var ageComparison = new LambdaExpression.BinaryOp(
                age,
                LambdaExpression.BinaryOp.Operator.GT,
                new LambdaExpression.Constant(25, Integer.class));

        var active = new LambdaExpression.FieldAccess("active", Boolean.class);

        var leftSide = new LambdaExpression.BinaryOp(
                ageComparison,
                LambdaExpression.BinaryOp.Operator.AND,
                active);

        var salary = new LambdaExpression.FieldAccess("salary", Double.class);
        var salaryComparison = new LambdaExpression.BinaryOp(
                salary,
                LambdaExpression.BinaryOp.Operator.GT,
                new LambdaExpression.Constant(50000.0, Double.class));

        var fullExpression = new LambdaExpression.BinaryOp(
                leftSide,
                LambdaExpression.BinaryOp.Operator.OR,
                salaryComparison);

        assertThat(fullExpression).isNotNull();
        assertThat(fullExpression.operator()).isEqualTo(LambdaExpression.BinaryOp.Operator.OR);
    }
}
