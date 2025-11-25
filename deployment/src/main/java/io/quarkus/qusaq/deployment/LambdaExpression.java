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

        /**
         * Common constant for boolean true value.
         */
        public static final Constant TRUE = new Constant(true, boolean.class);

        /**
         * Common constant for boolean false value.
         */
        public static final Constant FALSE = new Constant(false, boolean.class);

        /**
         * Common constant for integer zero value.
         */
        public static final Constant ZERO_INT = new Constant(0, int.class);

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

    /**
     * Constructor invocation expression for DTO projections.
     * <p>
     * Represents {@code new ClassName(arg1, arg2, ...)} in lambda expressions.
     * Used for JPA constructor expressions: {@code cb.construct(ClassName.class, arg1, arg2, ...)}.
     * <p>
     * Example:
     * <pre>
     * Person.select(p -> new PersonDTO(p.firstName, p.age)).toList()
     * → ConstructorCall("PersonDTO", [FieldAccess("firstName"), FieldAccess("age")])
     * → cb.construct(PersonDTO.class, root.get("firstName"), root.get("age"))
     * </pre>
     *
     * @param className Fully qualified class name (e.g., "com/example/PersonDTO")
     * @param arguments Constructor arguments (field accesses, constants, expressions)
     * @param resultType The class being instantiated
     */
    record ConstructorCall(
            String className,
            List<LambdaExpression> arguments,
            Class<?> resultType) implements LambdaExpression {

        public ConstructorCall {
            Objects.requireNonNull(className, "Class name cannot be null");
            arguments = List.copyOf(arguments);
            Objects.requireNonNull(resultType, "Result type cannot be null");
        }
    }

    // =============================================================================================
    // RELATIONSHIP NAVIGATION (Iteration 4)
    // =============================================================================================

    /**
     * Relationship type for path segments.
     * <p>
     * Used to determine whether a path segment requires a JPA join or simple field access.
     */
    enum RelationType {
        /** Regular field access (no join required). */
        FIELD,
        /** @ManyToOne relationship (requires implicit join). */
        MANY_TO_ONE,
        /** @OneToOne relationship (requires implicit join). */
        ONE_TO_ONE,
        /** @OneToMany relationship (requires implicit join for collection access). */
        ONE_TO_MANY,
        /** @ManyToMany relationship (requires implicit join for collection access). */
        MANY_TO_MANY
    }

    /**
     * A segment in a path expression representing a single navigation step.
     * <p>
     * Each segment captures the field name, its type, and whether it's a relationship
     * that requires a JPA join.
     *
     * @param fieldName The field name for this navigation step
     * @param fieldType The type of this field
     * @param relationType Whether this is a relationship requiring a join
     */
    record PathSegment(
            String fieldName,
            Class<?> fieldType,
            RelationType relationType) {

        public PathSegment {
            Objects.requireNonNull(fieldName, "Field name cannot be null");
            Objects.requireNonNull(fieldType, "Field type cannot be null");
            Objects.requireNonNull(relationType, "Relation type cannot be null");
        }

        /**
         * Returns true if this segment requires a JPA join.
         */
        public boolean requiresJoin() {
            return relationType != RelationType.FIELD;
        }
    }

    /**
     * Path expression for relationship navigation.
     * <p>
     * Represents chained field access like {@code p.owner.firstName} or
     * {@code phone.owner.department.name}.
     * <p>
     * Example:
     * <pre>
     * Phone.where(p -> p.owner.firstName.equals("John"))
     * → PathExpression([
     *     PathSegment("owner", Person.class, MANY_TO_ONE),
     *     PathSegment("firstName", String.class, FIELD)
     *   ])
     *
     * Generated JPA:
     *   Join&lt;Phone, Person&gt; ownerJoin = root.join("owner");
     *   Path&lt;String&gt; firstName = ownerJoin.get("firstName");
     *   Predicate pred = cb.equal(firstName, "John");
     * </pre>
     *
     * @param segments The list of path segments from root to final field
     * @param resultType The type of the final expression result
     */
    record PathExpression(
            List<PathSegment> segments,
            Class<?> resultType) implements LambdaExpression {

        public PathExpression {
            Objects.requireNonNull(segments, "Segments cannot be null");
            if (segments.isEmpty()) {
                throw new IllegalArgumentException("Path expression must have at least one segment");
            }
            segments = List.copyOf(segments);
            Objects.requireNonNull(resultType, "Result type cannot be null");
        }

        /**
         * Returns true if this path requires any JPA joins.
         */
        public boolean requiresJoins() {
            return segments.stream().anyMatch(PathSegment::requiresJoin);
        }

        /**
         * Returns the number of segments in this path.
         */
        public int depth() {
            return segments.size();
        }

        /**
         * Returns the final segment (the actual field being accessed).
         */
        public PathSegment finalSegment() {
            return segments.get(segments.size() - 1);
        }
    }
}
