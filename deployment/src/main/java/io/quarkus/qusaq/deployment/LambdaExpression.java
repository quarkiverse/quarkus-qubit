package io.quarkus.qusaq.deployment;

import java.util.List;
import java.util.Objects;

/**
 * Parsed lambda expression AST.
 */
public sealed interface LambdaExpression {

    /**
     * Binary operation (comparison, logical, or arithmetic).
     */
    record BinaryOp(LambdaExpression left, Operator operator, LambdaExpression right) implements LambdaExpression {
        /**
         * Binary operation types.
         */
        public enum Operator {
            EQ("=="), NE("!="), LT("<"), LE("<="), GT(">"), GE(">="),
            AND("&&"), OR("||"),
            ADD("+"), SUB("-"), MUL("*"), DIV("/"), MOD("%");

            private final String symbol;

            Operator(String symbol) {
                this.symbol = symbol;
            }

            /**
             * Returns the operator symbol.
             */
            public String symbol() {
                return symbol;
            }
        }
    }

    /**
     * Unary operation (NOT).
     */
    record UnaryOp(Operator operator, LambdaExpression operand) implements LambdaExpression {
        /**
         * Unary operation types.
         */
        public enum Operator {
            NOT("!");

            private final String symbol;

            Operator(String symbol) {
                this.symbol = symbol;
            }

            /**
             * Returns the operator symbol.
             */
            public String symbol() {
                return symbol;
            }
        }
    }

    /**
     * Field access expression.
     */
    record FieldAccess(String fieldName, Class<?> fieldType) implements LambdaExpression {
        public FieldAccess {
            Objects.requireNonNull(fieldName, "Field name cannot be null");
            Objects.requireNonNull(fieldType, "Field type cannot be null");
        }
    }

    /**
     * Method invocation expression.
     */
    record MethodCall(
            LambdaExpression target,
            String methodName,
            List<LambdaExpression> arguments,
            Class<?> returnType) implements LambdaExpression {

        public MethodCall {
            Objects.requireNonNull(methodName, "Method name cannot be null");
            arguments = List.copyOf(arguments);
            Objects.requireNonNull(returnType, "Return type cannot be null");
        }
    }

    /**
     * Constant literal value.
     */
    record Constant(Object value, Class<?> type) implements LambdaExpression {
        public Constant {
            Objects.requireNonNull(type, "Type cannot be null");
        }
    }

    /**
     * Lambda parameter reference.
     */
    record Parameter(String name, Class<?> type, int index) implements LambdaExpression {
        public Parameter {
            Objects.requireNonNull(name, "Parameter name cannot be null");
            Objects.requireNonNull(type, "Parameter type cannot be null");
        }
    }

    /**
     * Captured variable from enclosing scope.
     */
    record CapturedVariable(int index, Class<?> type) implements LambdaExpression {
        public CapturedVariable {
            Objects.requireNonNull(type, "Type cannot be null");
        }
    }

    /**
     * Null literal constant.
     */
    record NullLiteral(Class<?> expectedType) implements LambdaExpression {
    }

    /**
     * Type cast expression.
     */
    record Cast(LambdaExpression expression, Class<?> targetType) implements LambdaExpression {
        public Cast {
            Objects.requireNonNull(expression, "Expression cannot be null");
            Objects.requireNonNull(targetType, "Target type cannot be null");
        }
    }

    /**
     * Instance type check expression.
     */
    record InstanceOf(LambdaExpression expression, Class<?> targetType) implements LambdaExpression {
        public InstanceOf {
            Objects.requireNonNull(expression, "Expression cannot be null");
            Objects.requireNonNull(targetType, "Target type cannot be null");
        }
    }

    /**
     * Ternary conditional expression.
     */
    record Conditional(
            LambdaExpression condition,
            LambdaExpression trueValue,
            LambdaExpression falseValue) implements LambdaExpression {

        public Conditional {
            Objects.requireNonNull(condition, "Condition cannot be null");
            Objects.requireNonNull(trueValue, "True value cannot be null");
            Objects.requireNonNull(falseValue, "False value cannot be null");
        }
    }
}
