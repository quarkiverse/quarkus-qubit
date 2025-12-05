package io.quarkiverse.qubit.deployment.testutil;

import io.quarkiverse.qubit.deployment.ast.LambdaExpression;

import java.util.ArrayList;
import java.util.List;

/**
 * Test utility class providing fluent factory methods for building AST nodes.
 *
 * <p>This builder simplifies test setup code by reducing the verbosity of constructing
 * {@link LambdaExpression} AST nodes. Instead of:
 * <pre>{@code
 * var left = new LambdaExpression.Constant(10, Integer.class);
 * var right = new LambdaExpression.Constant(20, Integer.class);
 * var binOp = new LambdaExpression.BinaryOp(left, Operator.EQ, right);
 * }</pre>
 *
 * <p>You can write:
 * <pre>{@code
 * var binOp = eq(constant(10), constant(20));
 * }</pre>
 *
 * <p><strong>Usage Pattern:</strong> Static import all methods for cleaner test code:
 * <pre>{@code
 * import static io.quarkiverse.qubit.deployment.testutil.AstBuilders.*;
 * }</pre>
 *
 * <p><strong>Design Note:</strong> This utility leverages the existing factory methods on
 * {@link LambdaExpression.BinaryOp} and {@link LambdaExpression.UnaryOp} that were added
 * as part of ARCH-009. The builder provides consistent, discoverable static methods for
 * all AST node types.
 *
 * @see LambdaExpression
 */
public final class AstBuilders {

    private AstBuilders() {
        // Utility class - no instantiation
    }

    // ======================================================================
    // LEAF EXPRESSIONS - Constants, Fields, Parameters, Captured Variables
    // ======================================================================

    /**
     * Creates a constant expression with inferred type.
     *
     * @param value the constant value (type is inferred from the value's class)
     * @return a new Constant expression
     */
    public static LambdaExpression.Constant constant(Object value) {
        return new LambdaExpression.Constant(value, value != null ? value.getClass() : Object.class);
    }

    /**
     * Creates a constant expression with explicit type.
     *
     * @param value the constant value
     * @param type the explicit type for the constant
     * @return a new Constant expression
     */
    public static LambdaExpression.Constant constant(Object value, Class<?> type) {
        return new LambdaExpression.Constant(value, type);
    }

    /**
     * Creates a field access expression.
     *
     * @param fieldName the field name
     * @param fieldType the field type
     * @return a new FieldAccess expression
     */
    public static LambdaExpression.FieldAccess field(String fieldName, Class<?> fieldType) {
        return new LambdaExpression.FieldAccess(fieldName, fieldType);
    }

    /**
     * Creates a parameter reference expression.
     *
     * @param name the parameter name
     * @param type the parameter type
     * @param index the parameter index in the method signature
     * @return a new Parameter expression
     */
    public static LambdaExpression.Parameter param(String name, Class<?> type, int index) {
        return new LambdaExpression.Parameter(name, type, index);
    }

    /**
     * Creates a captured variable reference expression.
     *
     * @param index the index of the captured variable in the captures array
     * @param type the type of the captured variable
     * @return a new CapturedVariable expression
     */
    public static LambdaExpression.CapturedVariable captured(int index, Class<?> type) {
        return new LambdaExpression.CapturedVariable(index, type);
    }

    /**
     * Creates a null literal expression.
     *
     * @param expectedType the expected type of the null value
     * @return a new NullLiteral expression
     */
    public static LambdaExpression.NullLiteral nullLit(Class<?> expectedType) {
        return new LambdaExpression.NullLiteral(expectedType);
    }

    // ======================================================================
    // BINARY OPERATIONS - Comparison, Logical, and Arithmetic
    // ======================================================================

    /**
     * Creates an equality comparison: left == right
     */
    public static LambdaExpression.BinaryOp eq(LambdaExpression left, LambdaExpression right) {
        return LambdaExpression.BinaryOp.eq(left, right);
    }

    /**
     * Creates an inequality comparison: left != right
     */
    public static LambdaExpression.BinaryOp ne(LambdaExpression left, LambdaExpression right) {
        return LambdaExpression.BinaryOp.ne(left, right);
    }

    /**
     * Creates a less-than comparison: left < right
     */
    public static LambdaExpression.BinaryOp lt(LambdaExpression left, LambdaExpression right) {
        return LambdaExpression.BinaryOp.lt(left, right);
    }

    /**
     * Creates a less-than-or-equal comparison: left <= right
     */
    public static LambdaExpression.BinaryOp le(LambdaExpression left, LambdaExpression right) {
        return LambdaExpression.BinaryOp.le(left, right);
    }

    /**
     * Creates a greater-than comparison: left > right
     */
    public static LambdaExpression.BinaryOp gt(LambdaExpression left, LambdaExpression right) {
        return LambdaExpression.BinaryOp.gt(left, right);
    }

    /**
     * Creates a greater-than-or-equal comparison: left >= right
     */
    public static LambdaExpression.BinaryOp ge(LambdaExpression left, LambdaExpression right) {
        return LambdaExpression.BinaryOp.ge(left, right);
    }

    /**
     * Creates a logical AND: left && right
     */
    public static LambdaExpression.BinaryOp and(LambdaExpression left, LambdaExpression right) {
        return LambdaExpression.BinaryOp.and(left, right);
    }

    /**
     * Creates a logical OR: left || right
     */
    public static LambdaExpression.BinaryOp or(LambdaExpression left, LambdaExpression right) {
        return LambdaExpression.BinaryOp.or(left, right);
    }

    /**
     * Creates an addition: left + right
     */
    public static LambdaExpression.BinaryOp add(LambdaExpression left, LambdaExpression right) {
        return LambdaExpression.BinaryOp.add(left, right);
    }

    /**
     * Creates a subtraction: left - right
     */
    public static LambdaExpression.BinaryOp sub(LambdaExpression left, LambdaExpression right) {
        return LambdaExpression.BinaryOp.sub(left, right);
    }

    /**
     * Creates a multiplication: left * right
     */
    public static LambdaExpression.BinaryOp mul(LambdaExpression left, LambdaExpression right) {
        return LambdaExpression.BinaryOp.mul(left, right);
    }

    /**
     * Creates a division: left / right
     */
    public static LambdaExpression.BinaryOp div(LambdaExpression left, LambdaExpression right) {
        return LambdaExpression.BinaryOp.div(left, right);
    }

    /**
     * Creates a modulo: left % right
     */
    public static LambdaExpression.BinaryOp mod(LambdaExpression left, LambdaExpression right) {
        return LambdaExpression.BinaryOp.mod(left, right);
    }

    // ======================================================================
    // UNARY OPERATIONS
    // ======================================================================

    /**
     * Creates a logical NOT: !operand
     */
    public static LambdaExpression.UnaryOp not(LambdaExpression operand) {
        return LambdaExpression.UnaryOp.not(operand);
    }

    // ======================================================================
    // TYPE OPERATIONS - Cast, InstanceOf
    // ======================================================================

    /**
     * Creates a type cast expression.
     *
     * @param expression the expression to cast
     * @param targetType the target type to cast to
     * @return a new Cast expression
     */
    public static LambdaExpression.Cast cast(LambdaExpression expression, Class<?> targetType) {
        return new LambdaExpression.Cast(expression, targetType);
    }

    /**
     * Creates an instanceof check expression.
     *
     * @param expression the expression to check
     * @param targetType the type to check against
     * @return a new InstanceOf expression
     */
    public static LambdaExpression.InstanceOf instanceOf(LambdaExpression expression, Class<?> targetType) {
        return new LambdaExpression.InstanceOf(expression, targetType);
    }

    // ======================================================================
    // CONDITIONAL EXPRESSION
    // ======================================================================

    /**
     * Creates a ternary conditional expression: condition ? trueValue : falseValue
     *
     * @param condition the condition expression
     * @param trueValue the value if condition is true
     * @param falseValue the value if condition is false
     * @return a new Conditional expression
     */
    public static LambdaExpression.Conditional conditional(
            LambdaExpression condition,
            LambdaExpression trueValue,
            LambdaExpression falseValue) {
        return new LambdaExpression.Conditional(condition, trueValue, falseValue);
    }

    // ======================================================================
    // METHOD CALL - Fluent Builder
    // ======================================================================

    /**
     * Creates a method call with explicit components.
     *
     * @param target the target object for the method call
     * @param methodName the method name
     * @param arguments the method arguments
     * @param returnType the return type of the method
     * @return a new MethodCall expression
     */
    public static LambdaExpression.MethodCall methodCall(
            LambdaExpression target,
            String methodName,
            List<LambdaExpression> arguments,
            Class<?> returnType) {
        return new LambdaExpression.MethodCall(target, methodName, arguments, returnType);
    }

    /**
     * Creates a no-arg method call.
     *
     * @param target the target object for the method call
     * @param methodName the method name
     * @param returnType the return type of the method
     * @return a new MethodCall expression
     */
    public static LambdaExpression.MethodCall methodCall(
            LambdaExpression target,
            String methodName,
            Class<?> returnType) {
        return new LambdaExpression.MethodCall(target, methodName, List.of(), returnType);
    }

    /**
     * Starts building a method call with fluent syntax.
     *
     * <p>Example usage:
     * <pre>{@code
     * var call = call("getValue").on(captured(0, Object.class)).returns(Object.class).build();
     * }</pre>
     *
     * @param methodName the method name
     * @return a MethodCallBuilder for fluent configuration
     */
    public static MethodCallBuilder call(String methodName) {
        return new MethodCallBuilder(methodName);
    }

    /**
     * Fluent builder for constructing MethodCall expressions.
     */
    public static class MethodCallBuilder {
        private final String methodName;
        private LambdaExpression target;
        private final List<LambdaExpression> arguments = new ArrayList<>();
        private Class<?> returnType = Object.class;

        public MethodCallBuilder(String methodName) {
            this.methodName = methodName;
        }

        /**
         * Sets the target object for the method call.
         */
        public MethodCallBuilder on(LambdaExpression target) {
            this.target = target;
            return this;
        }

        /**
         * Adds arguments to the method call.
         */
        public MethodCallBuilder withArgs(LambdaExpression... args) {
            this.arguments.clear();
            this.arguments.addAll(List.of(args));
            return this;
        }

        /**
         * Adds a single argument to the method call.
         */
        public MethodCallBuilder withArg(LambdaExpression arg) {
            this.arguments.add(arg);
            return this;
        }

        /**
         * Sets the return type of the method.
         */
        public MethodCallBuilder returns(Class<?> returnType) {
            this.returnType = returnType;
            return this;
        }

        /**
         * Builds the MethodCall expression.
         */
        public LambdaExpression.MethodCall build() {
            return new LambdaExpression.MethodCall(target, methodName, List.copyOf(arguments), returnType);
        }
    }
}
