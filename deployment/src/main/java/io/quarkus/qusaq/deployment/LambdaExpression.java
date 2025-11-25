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

    // =============================================================================================
    // COLLECTION OPERATIONS (Iteration 5)
    // =============================================================================================

    /**
     * IN expression for collection membership testing.
     * <p>
     * Represents a predicate that checks if a field value is contained within a collection.
     * This translates to SQL {@code WHERE field IN (value1, value2, ...)} or JPA
     * {@code cb.in(root.get("field")).value(v1).value(v2)...}.
     * <p>
     * Example:
     * <pre>
     * List&lt;String&gt; cities = List.of("NYC", "LA", "Chicago");
     * Person.where(p -&gt; cities.contains(p.city))
     * → InExpression(FieldAccess("city"), CapturedVariable(0), false)
     *
     * Generated JPA:
     *   CriteriaBuilder.In&lt;String&gt; in = cb.in(root.get("city"));
     *   in.value("NYC").value("LA").value("Chicago");
     *   // or: root.get("city").in(collectionParameter)
     * </pre>
     *
     * @param field The field to check (e.g., p.city)
     * @param collection The collection to check against (typically a CapturedVariable)
     * @param negated True for NOT IN, false for IN
     */
    record InExpression(
            LambdaExpression field,
            LambdaExpression collection,
            boolean negated) implements LambdaExpression {

        public InExpression {
            Objects.requireNonNull(field, "Field cannot be null");
            Objects.requireNonNull(collection, "Collection cannot be null");
        }

        /**
         * Creates a non-negated IN expression.
         */
        public static InExpression in(LambdaExpression field, LambdaExpression collection) {
            return new InExpression(field, collection, false);
        }

        /**
         * Creates a negated NOT IN expression.
         */
        public static InExpression notIn(LambdaExpression field, LambdaExpression collection) {
            return new InExpression(field, collection, true);
        }
    }

    /**
     * MEMBER OF expression for collection field membership.
     * <p>
     * Represents a predicate that checks if a value is a member of a mapped collection field
     * (e.g., @ElementCollection, @OneToMany). This translates to SQL
     * {@code WHERE value MEMBER OF collectionField} or JPA {@code cb.isMember(value, collection)}.
     * <p>
     * Example:
     * <pre>
     * // Assuming Person has @ElementCollection Set&lt;String&gt; roles
     * Person.where(p -&gt; p.roles.contains("admin"))
     * → MemberOfExpression(Constant("admin"), FieldAccess("roles"), false)
     *
     * Generated JPA:
     *   Predicate pred = cb.isMember("admin", root.get("roles"));
     * </pre>
     *
     * @param value The value to check for membership
     * @param collectionField The collection field to search within
     * @param negated True for NOT MEMBER OF, false for MEMBER OF
     */
    record MemberOfExpression(
            LambdaExpression value,
            LambdaExpression collectionField,
            boolean negated) implements LambdaExpression {

        public MemberOfExpression {
            Objects.requireNonNull(value, "Value cannot be null");
            Objects.requireNonNull(collectionField, "Collection field cannot be null");
        }

        /**
         * Creates a non-negated MEMBER OF expression.
         */
        public static MemberOfExpression memberOf(LambdaExpression value, LambdaExpression collectionField) {
            return new MemberOfExpression(value, collectionField, false);
        }

        /**
         * Creates a negated NOT MEMBER OF expression.
         */
        public static MemberOfExpression notMemberOf(LambdaExpression value, LambdaExpression collectionField) {
            return new MemberOfExpression(value, collectionField, true);
        }
    }

    // =============================================================================================
    // JOIN QUERIES (Iteration 6)
    // =============================================================================================

    /**
     * Identifies which entity in a bi-entity lambda (BiQuerySpec).
     */
    enum EntityPosition {
        /** First entity in the join (source/left side). */
        FIRST,
        /** Second entity in the join (joined/right side). */
        SECOND
    }

    /**
     * Bi-entity parameter reference for join query lambdas.
     * <p>
     * Used in BiQuerySpec lambdas where two entity parameters are available:
     * {@code (Person p, Phone ph) -> ...}
     * <p>
     * Unlike single-entity Parameter, this tracks which entity (FIRST or SECOND)
     * the parameter represents, enabling correct JPA alias generation.
     * <p>
     * Example:
     * <pre>
     * // Lambda: (Person p, Phone ph) -> ph.type.equals("mobile")
     * // Parameter 'ph' is:
     * BiEntityParameter("entity", Phone.class, 1, SECOND)
     *
     * // Generated JPA uses 'phoneJoin' alias instead of 'root'
     * </pre>
     *
     * @param name Parameter name (typically "entity" or "joinedEntity")
     * @param type The entity class type
     * @param index The slot index in bytecode
     * @param position Which entity in the join (FIRST or SECOND)
     */
    record BiEntityParameter(
            String name,
            Class<?> type,
            int index,
            EntityPosition position) implements LambdaExpression {

        public BiEntityParameter {
            Objects.requireNonNull(name, "Parameter name cannot be null");
            Objects.requireNonNull(type, "Parameter type cannot be null");
            Objects.requireNonNull(position, "Entity position cannot be null");
        }

        /**
         * Returns true if this is the first (source/left) entity in the join.
         */
        public boolean isFirstEntity() {
            return position == EntityPosition.FIRST;
        }

        /**
         * Returns true if this is the second (joined/right) entity in the join.
         */
        public boolean isSecondEntity() {
            return position == EntityPosition.SECOND;
        }
    }

    /**
     * Field access from a bi-entity context (join query).
     * <p>
     * Extends FieldAccess to track which entity the field belongs to in a join.
     * This enables correct JPA path generation: {@code root.get("field")} vs
     * {@code joinAlias.get("field")}.
     * <p>
     * Example:
     * <pre>
     * // Lambda: (Person p, Phone ph) -> ph.type.equals("mobile")
     * // Field 'type' from 'ph' is:
     * BiEntityFieldAccess("type", String.class, SECOND)
     *
     * // Generated JPA: phoneJoin.get("type") instead of root.get("type")
     * </pre>
     *
     * @param fieldName The field name
     * @param fieldType The field type
     * @param entityPosition Which entity the field belongs to (FIRST or SECOND)
     */
    record BiEntityFieldAccess(
            String fieldName,
            Class<?> fieldType,
            EntityPosition entityPosition) implements LambdaExpression {

        public BiEntityFieldAccess {
            Objects.requireNonNull(fieldName, "Field name cannot be null");
            Objects.requireNonNull(fieldType, "Field type cannot be null");
            Objects.requireNonNull(entityPosition, "Entity position cannot be null");
        }

        /**
         * Returns true if this field is from the first (source/left) entity.
         */
        public boolean isFromFirstEntity() {
            return entityPosition == EntityPosition.FIRST;
        }

        /**
         * Returns true if this field is from the second (joined/right) entity.
         */
        public boolean isFromSecondEntity() {
            return entityPosition == EntityPosition.SECOND;
        }
    }

    /**
     * Path expression from a bi-entity context (join query).
     * <p>
     * Extends PathExpression to track which entity the path starts from in a join.
     * <p>
     * Example:
     * <pre>
     * // Lambda: (Person p, Phone ph) -> ph.owner.firstName.equals("John")
     * // Path from 'ph' is:
     * BiEntityPathExpression([owner, firstName], String.class, SECOND)
     * </pre>
     *
     * @param segments The list of path segments
     * @param resultType The type of the final expression result
     * @param entityPosition Which entity the path starts from
     */
    record BiEntityPathExpression(
            List<PathSegment> segments,
            Class<?> resultType,
            EntityPosition entityPosition) implements LambdaExpression {

        public BiEntityPathExpression {
            Objects.requireNonNull(segments, "Segments cannot be null");
            if (segments.isEmpty()) {
                throw new IllegalArgumentException("Path expression must have at least one segment");
            }
            segments = List.copyOf(segments);
            Objects.requireNonNull(resultType, "Result type cannot be null");
            Objects.requireNonNull(entityPosition, "Entity position cannot be null");
        }

        /**
         * Returns true if this path starts from the first (source/left) entity.
         */
        public boolean isFromFirstEntity() {
            return entityPosition == EntityPosition.FIRST;
        }

        /**
         * Returns true if this path starts from the second (joined/right) entity.
         */
        public boolean isFromSecondEntity() {
            return entityPosition == EntityPosition.SECOND;
        }
    }
}
