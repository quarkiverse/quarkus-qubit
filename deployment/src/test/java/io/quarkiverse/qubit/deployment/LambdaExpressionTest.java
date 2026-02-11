package io.quarkiverse.qubit.deployment;

import static io.quarkiverse.qubit.deployment.testutil.AstBuilders.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.quarkiverse.qubit.deployment.ast.LambdaExpression;

/**
 * Unit tests for LambdaExpression AST classes.
 *
 * <p>
 * Uses {@link io.quarkiverse.qubit.deployment.testutil.AstBuilders} for fluent AST construction.
 */
class LambdaExpressionTest {

    @Test
    void testBinaryOpCreation() {
        var left = constant(10);
        var right = constant(20);
        var binOp = eq(left, right);

        assertThat(binOp.left()).isEqualTo(left);
        assertThat(binOp.right()).isEqualTo(right);
        assertThat(binOp.operator()).isEqualTo(LambdaExpression.BinaryOp.Operator.EQ);
    }

    @Test
    void testFieldAccess() {
        var fieldAccess = field("name", String.class);

        assertThat(fieldAccess.fieldName()).isEqualTo("name");
        assertThat(fieldAccess.fieldType()).isEqualTo(String.class);
    }

    @Test
    void testFieldAccessNullValidation() {
        assertThatThrownBy(() -> new LambdaExpression.FieldAccess(null, String.class))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("Field name cannot be null");
    }

    @Test
    void testMethodCall() {
        var target = param("entity", Object.class, 0);
        var arg = constant("test", String.class);
        var methodCallExpr = methodCall(target, "equals", List.of(arg), Boolean.class);

        assertThat(methodCallExpr.target()).isEqualTo(target);
        assertThat(methodCallExpr.methodName()).isEqualTo("equals");
        assertThat(methodCallExpr.arguments()).containsExactly(arg);
        assertThat(methodCallExpr.returnType()).isEqualTo(Boolean.class);
    }

    @Test
    void testMethodCallFluentBuilder() {
        var target = param("entity", Object.class, 0);
        var arg = constant("test");
        var methodCallExpr = call("equals")
                .on(target)
                .withArg(arg)
                .returns(Boolean.class)
                .build();

        assertThat(methodCallExpr.target()).isEqualTo(target);
        assertThat(methodCallExpr.methodName()).isEqualTo("equals");
        assertThat(methodCallExpr.arguments()).containsExactly(arg);
        assertThat(methodCallExpr.returnType()).isEqualTo(Boolean.class);
    }

    @Test
    void testConstant() {
        var constantExpr = constant("test", String.class);

        assertThat(constantExpr.value()).isEqualTo("test");
        assertThat(constantExpr.type()).isEqualTo(String.class);
    }

    @Test
    void testConstantInferredType() {
        var constantExpr = constant("test");

        assertThat(constantExpr.value()).isEqualTo("test");
        assertThat(constantExpr.type()).isEqualTo(String.class);
    }

    @Test
    void testParameter() {
        var paramExpr = param("entity", Object.class, 0);

        assertThat(paramExpr.name()).isEqualTo("entity");
        assertThat(paramExpr.type()).isEqualTo(Object.class);
        assertThat(paramExpr.index()).isZero();
    }

    @Test
    void testNullLiteral() {
        var nullLitExpr = nullLit(String.class);

        assertThat(nullLitExpr.expectedType()).isEqualTo(String.class);
    }

    @Test
    void testCast() {
        var expr = constant(10);
        var castExpr = cast(expr, Long.class);

        assertThat(castExpr.expression()).isEqualTo(expr);
        assertThat(castExpr.targetType()).isEqualTo(Long.class);
    }

    @Test
    void testInstanceOf() {
        var expr = param("obj", Object.class, 0);
        var instanceOfExpr = instanceOf(expr, String.class);

        assertThat(instanceOfExpr.expression()).isEqualTo(expr);
        assertThat(instanceOfExpr.targetType()).isEqualTo(String.class);
    }

    @Test
    void testConditional() {
        // Build: 5 > 3 ? "yes" : "no"
        var condition = gt(constant(5), constant(3));
        var trueValue = constant("yes");
        var falseValue = constant("no");

        var conditionalExpr = conditional(condition, trueValue, falseValue);

        assertThat(conditionalExpr.condition()).isEqualTo(condition);
        assertThat(conditionalExpr.trueValue()).isEqualTo(trueValue);
        assertThat(conditionalExpr.falseValue()).isEqualTo(falseValue);
    }

    @Test
    void testUnaryOp() {
        var operand = constant(true);
        var unaryOp = not(operand);

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
        var age = field("age", Integer.class);
        var ageComparison = gt(age, constant(25));

        var active = field("active", Boolean.class);
        var leftSide = and(ageComparison, active);

        var salary = field("salary", Double.class);
        var salaryComparison = gt(salary, constant(50000.0));

        var fullExpression = or(leftSide, salaryComparison);

        assertThat(fullExpression).isNotNull();
        assertThat(fullExpression.operator()).isEqualTo(LambdaExpression.BinaryOp.Operator.OR);
    }

    @Test
    void testArithmeticOperations() {
        // Build: (a + b) * (c - d) / e % f
        var a = field("a", Integer.class);
        var b = field("b", Integer.class);
        var c = field("c", Integer.class);
        var d = field("d", Integer.class);
        var e = field("e", Integer.class);
        var f = field("f", Integer.class);

        var sum = add(a, b);
        var diff = sub(c, d);
        var product = mul(sum, diff);
        var quotient = div(product, e);
        var remainder = mod(quotient, f);

        assertThat(remainder.operator()).isEqualTo(LambdaExpression.BinaryOp.Operator.MOD);
        assertThat(remainder.left()).isInstanceOf(LambdaExpression.BinaryOp.class);
    }
}
