package io.quarkiverse.qubit.deployment.bytecode;

import io.quarkiverse.qubit.deployment.ast.LambdaExpression;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Bytecode analysis tests for lambda expressions with captured variables.
 *
 * <p>Captured variables are variables from the enclosing scope that are used
 * inside a lambda expression. For example:
 * <pre>{@code
 * String prefix = "J";
 * List<Person> results = Person.findWhere(p -> p.name.startsWith(prefix));
 * }</pre>
 *
 * <p>In this case, {@code prefix} is a captured variable. At the bytecode level,
 * captured variables are passed as additional parameters to the lambda's synthetic method.
 *
 * <p>These tests verify that:
 * <ul>
 *   <li>Captured variables are correctly detected and represented in the AST</li>
 *   <li>The entity parameter is correctly distinguished from captured variables</li>
 *   <li>Multiple captured variables are handled properly</li>
 * </ul>
 */
class CapturedVariablesBytecodeTest extends PrecompiledLambdaAnalyzer {

    @Test
    void capturedStringVariable() {
        LambdaExpression expr = analyzeLambda("capturedStringVariable");

        // p.firstName.equals(searchName)
        // The analyzer transforms .equals() to ==
        assertBinaryOp(expr, LambdaExpression.BinaryOp.Operator.EQ);
        LambdaExpression.BinaryOp eqOp = (LambdaExpression.BinaryOp) expr;

        // Left: firstName field
        assertFieldAccess(eqOp.left(), "firstName");

        // Right: captured variable "searchName"
        assertCapturedVariable(eqOp.right(), 0);  // Index 0 because it's the first parameter
    }

    @Test
    void capturedIntVariable() {
        LambdaExpression expr = analyzeLambda("capturedIntVariable");

        // p.age > minAge
        assertBinaryOp(expr, LambdaExpression.BinaryOp.Operator.GT);
        LambdaExpression.BinaryOp gtOp = (LambdaExpression.BinaryOp) expr;

        // Left: age field
        assertFieldAccess(gtOp.left(), "age");

        // Right: captured variable "minAge"
        assertCapturedVariable(gtOp.right(), 0);
    }

    @Test
    void capturedDoubleVariable() {
        LambdaExpression expr = analyzeLambda("capturedDoubleVariable");

        // p.salary >= minSalary
        assertBinaryOp(expr, LambdaExpression.BinaryOp.Operator.GE);
        LambdaExpression.BinaryOp geOp = (LambdaExpression.BinaryOp) expr;

        // Left: salary field
        assertFieldAccess(geOp.left(), "salary");

        // Right: captured variable "minSalary"
        assertCapturedVariable(geOp.right(), 0);
    }

    @Test
    void capturedStringStartsWith() {
        LambdaExpression expr = analyzeLambda("capturedStringStartsWith");

        // p.firstName.startsWith(prefix)
        assertMethodCall(expr, "startsWith");
        LambdaExpression.MethodCall methodCall = (LambdaExpression.MethodCall) expr;

        // Target: firstName field
        assertFieldAccess(methodCall.target(), "firstName");

        // Argument: captured variable "prefix"
        assertThat(methodCall.arguments()).hasSize(1);
        assertCapturedVariable(methodCall.arguments().get(0), 0);
    }

    @Test
    void multipleCapturedVariables() {
        LambdaExpression expr = analyzeLambda("multipleCapturedVariables");

        // p.firstName.equals(searchName) && p.age > minAge
        // The bytecode analyzer wraps .equals() result in EQ(_, true)
        assertBinaryOp(expr, LambdaExpression.BinaryOp.Operator.AND);
        LambdaExpression.BinaryOp andOp = (LambdaExpression.BinaryOp) expr;

        // Left: EQ(firstName.equals(searchName), true)
        assertBinaryOp(andOp.left(), LambdaExpression.BinaryOp.Operator.EQ);
        LambdaExpression.BinaryOp outerEq = (LambdaExpression.BinaryOp) andOp.left();
        assertConstant(outerEq.right(), true);  // Wrapped with == true

        // Inner EQ: firstName.equals(searchName)
        assertBinaryOp(outerEq.left(), LambdaExpression.BinaryOp.Operator.EQ);
        LambdaExpression.BinaryOp innerEq = (LambdaExpression.BinaryOp) outerEq.left();
        assertFieldAccess(innerEq.left(), "firstName");
        assertCapturedVariable(innerEq.right(), 0);  // searchName is first captured variable

        // Right: age > minAge
        assertBinaryOp(andOp.right(), LambdaExpression.BinaryOp.Operator.GT);
        LambdaExpression.BinaryOp gtOp = (LambdaExpression.BinaryOp) andOp.right();
        assertFieldAccess(gtOp.left(), "age");
        assertCapturedVariable(gtOp.right(), 1);  // minAge is second captured variable
    }

    @Test
    void capturedVariableInComplexExpression() {
        LambdaExpression expr = analyzeLambda("capturedVariableInComplexExpression");

        // (p.age > threshold && p.active) || p.salary > 80000
        assertBinaryOp(expr, LambdaExpression.BinaryOp.Operator.OR);
        LambdaExpression.BinaryOp orOp = (LambdaExpression.BinaryOp) expr;

        // Left: (age > threshold && active)
        assertBinaryOp(orOp.left(), LambdaExpression.BinaryOp.Operator.AND);
        LambdaExpression.BinaryOp andOp = (LambdaExpression.BinaryOp) orOp.left();

        // Left of AND: age > threshold
        assertBinaryOp(andOp.left(), LambdaExpression.BinaryOp.Operator.GT);
        LambdaExpression.BinaryOp gtOp = (LambdaExpression.BinaryOp) andOp.left();
        assertFieldAccess(gtOp.left(), "age");
        assertCapturedVariable(gtOp.right(), 0);  // threshold is captured variable

        // Right of AND: active
        LambdaExpression.BinaryOp activeCheck = (LambdaExpression.BinaryOp) andOp.right();
        assertThat(activeCheck.operator()).isEqualTo(LambdaExpression.BinaryOp.Operator.EQ);
        assertFieldAccess(activeCheck.left(), "active");

        // Right: salary > 80000
        assertBinaryOp(orOp.right(), LambdaExpression.BinaryOp.Operator.GT);
        LambdaExpression.BinaryOp salaryGtOp = (LambdaExpression.BinaryOp) orOp.right();
        assertFieldAccess(salaryGtOp.left(), "salary");
        assertConstant(salaryGtOp.right(), 80000.0);
    }
}
