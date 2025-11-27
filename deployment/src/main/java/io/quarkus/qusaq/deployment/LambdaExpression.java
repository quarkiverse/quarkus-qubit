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

    /**
     * Array creation expression for multi-value projections.
     * <p>
     * Represents {@code new T[]{element1, element2, ...}} in lambda expressions.
     * Used for GROUP BY projections returning multiple values.
     * <p>
     * Example:
     * <pre>
     * .select(g -> new Object[]{g.key(), g.count()})
     * → ArrayCreation("java/lang/Object", [GroupKeyReference, GroupAggregation], Object[].class)
     * → cb.array(keyExpr, cb.count(root))
     * </pre>
     *
     * @param elementType Internal name of array element type (e.g., "java/lang/Object")
     * @param elements The array elements
     * @param resultType The array type (e.g., Object[].class)
     */
    record ArrayCreation(
            String elementType,
            List<LambdaExpression> elements,
            Class<?> resultType) implements LambdaExpression {

        public ArrayCreation {
            Objects.requireNonNull(elementType, "Element type cannot be null");
            elements = List.copyOf(elements);
            Objects.requireNonNull(resultType, "Result type cannot be null");
        }

        /**
         * Returns true if this is an Object[] array.
         */
        public boolean isObjectArray() {
            return "java/lang/Object".equals(elementType);
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

    // =============================================================================================
    // GROUPING OPERATIONS (Iteration 7)
    // =============================================================================================

    /**
     * Group key reference in a GroupQuerySpec lambda.
     * <p>
     * Represents the {@code g.key()} call in a group context lambda.
     * This captures the grouping key expression from the original groupBy() call.
     * <p>
     * Example:
     * <pre>
     * // Lambda: .select((Group&lt;Person, String&gt; g) -> g.key())
     * // → GroupKeyReference(keyExpression, String.class)
     *
     * // Generated JPA: root.get("department") (the grouping expression)
     * </pre>
     *
     * @param keyExpression The expression used for grouping (from groupBy() lambda), may be null
     *                      when analyzed in isolation (resolved at code generation time)
     * @param resultType The type of the grouping key
     */
    record GroupKeyReference(
            LambdaExpression keyExpression,
            Class<?> resultType) implements LambdaExpression {

        public GroupKeyReference {
            // keyExpression can be null - it gets resolved at code generation time
            // from the groupBy() lambda's key expression
            Objects.requireNonNull(resultType, "Result type cannot be null");
        }
    }

    /**
     * Types of group aggregation functions.
     */
    enum GroupAggregationType {
        /** Count of entities in group - g.count() */
        COUNT,
        /** Count of distinct values - g.countDistinct(field) */
        COUNT_DISTINCT,
        /** Average of numeric field - g.avg(field) */
        AVG,
        /** Sum of integer field - g.sumInteger(field) */
        SUM_INTEGER,
        /** Sum of long field - g.sumLong(field) */
        SUM_LONG,
        /** Sum of double field - g.sumDouble(field) */
        SUM_DOUBLE,
        /** Minimum value - g.min(field) */
        MIN,
        /** Maximum value - g.max(field) */
        MAX
    }

    /**
     * Group aggregation expression in a GroupQuerySpec lambda.
     * <p>
     * Represents aggregate function calls on a Group context, such as
     * {@code g.count()}, {@code g.avg(p -> p.salary)}, etc.
     * <p>
     * Example:
     * <pre>
     * // Lambda: .select((Group&lt;Person, String&gt; g) -> g.count())
     * // → GroupAggregation(COUNT, null, long.class)
     *
     * // Lambda: .select((Group&lt;Person, String&gt; g) -> g.avg(p -> p.salary))
     * // → GroupAggregation(AVG, FieldAccess("salary"), Double.class)
     *
     * // Generated JPA:
     * //   cb.count(root) for COUNT
     * //   cb.avg(root.get("salary")) for AVG
     * </pre>
     *
     * @param aggregationType The type of aggregation (COUNT, AVG, MIN, MAX, etc.)
     * @param fieldExpression The field being aggregated (null for COUNT without field)
     * @param resultType The return type of the aggregation
     */
    record GroupAggregation(
            GroupAggregationType aggregationType,
            LambdaExpression fieldExpression,
            Class<?> resultType) implements LambdaExpression {

        public GroupAggregation {
            Objects.requireNonNull(aggregationType, "Aggregation type cannot be null");
            Objects.requireNonNull(resultType, "Result type cannot be null");
            // fieldExpression can be null for count()
        }

        /**
         * Creates a COUNT aggregation (counts all entities in group).
         */
        public static GroupAggregation count() {
            return new GroupAggregation(GroupAggregationType.COUNT, null, long.class);
        }

        /**
         * Creates a COUNT DISTINCT aggregation.
         */
        public static GroupAggregation countDistinct(LambdaExpression field) {
            return new GroupAggregation(GroupAggregationType.COUNT_DISTINCT, field, long.class);
        }

        /**
         * Creates an AVG aggregation.
         */
        public static GroupAggregation avg(LambdaExpression field) {
            return new GroupAggregation(GroupAggregationType.AVG, field, Double.class);
        }

        /**
         * Creates a SUM aggregation for integers.
         */
        public static GroupAggregation sumInteger(LambdaExpression field) {
            return new GroupAggregation(GroupAggregationType.SUM_INTEGER, field, Long.class);
        }

        /**
         * Creates a SUM aggregation for longs.
         */
        public static GroupAggregation sumLong(LambdaExpression field) {
            return new GroupAggregation(GroupAggregationType.SUM_LONG, field, Long.class);
        }

        /**
         * Creates a SUM aggregation for doubles.
         */
        public static GroupAggregation sumDouble(LambdaExpression field) {
            return new GroupAggregation(GroupAggregationType.SUM_DOUBLE, field, Double.class);
        }

        /**
         * Creates a MIN aggregation.
         */
        public static GroupAggregation min(LambdaExpression field, Class<?> resultType) {
            return new GroupAggregation(GroupAggregationType.MIN, field, resultType);
        }

        /**
         * Creates a MAX aggregation.
         */
        public static GroupAggregation max(LambdaExpression field, Class<?> resultType) {
            return new GroupAggregation(GroupAggregationType.MAX, field, resultType);
        }

        /**
         * Returns true if this aggregation requires a field expression.
         */
        public boolean requiresField() {
            return aggregationType != GroupAggregationType.COUNT;
        }
    }

    /**
     * Group context parameter reference in a GroupQuerySpec lambda.
     * <p>
     * Represents the Group parameter itself in a group lambda.
     * Used as the target for method calls like {@code g.key()}, {@code g.count()}.
     * <p>
     * Example:
     * <pre>
     * // Lambda: (Group&lt;Person, String&gt; g) -> g.count()
     * // The 'g' is represented as:
     * // GroupParameter("g", Group.class, 0, Person.class, String.class)
     * </pre>
     *
     * @param name Parameter name (typically "g" or "group")
     * @param type The Group class type
     * @param index The parameter index in bytecode (typically 0)
     * @param entityType The entity type being grouped (T)
     * @param keyType The grouping key type (K)
     */
    record GroupParameter(
            String name,
            Class<?> type,
            int index,
            Class<?> entityType,
            Class<?> keyType) implements LambdaExpression {

        public GroupParameter {
            Objects.requireNonNull(name, "Parameter name cannot be null");
            Objects.requireNonNull(type, "Parameter type cannot be null");
            Objects.requireNonNull(entityType, "Entity type cannot be null");
            Objects.requireNonNull(keyType, "Key type cannot be null");
        }
    }

    // =============================================================================================
    // SUBQUERIES (Iteration 8)
    // =============================================================================================

    /**
     * Types of scalar aggregation subqueries.
     */
    enum SubqueryAggregationType {
        /** Average aggregation - subquery(...).avg(...) */
        AVG,
        /** Sum aggregation - subquery(...).sum(...) */
        SUM,
        /** Minimum value - subquery(...).min(...) */
        MIN,
        /** Maximum value - subquery(...).max(...) */
        MAX,
        /** Count - subquery(...).count(...) */
        COUNT
    }

    /**
     * Reference to a SubqueryBuilder instance (intermediate AST node).
     * <p>
     * Created by {@code Subqueries.subquery(Class)}, holds entity class and optional predicate
     * from {@code .where()} calls. Converted to ScalarSubquery/ExistsSubquery/InSubquery when
     * aggregation method ({@code .avg()}, {@code .exists()}, etc.) is called.
     *
     * @param entityClass The entity class for the subquery
     * @param entityClassName Optional entity class name (for placeholder classes not loadable at build-time)
     * @param predicate Optional WHERE predicate (null if none)
     */
    record SubqueryBuilderReference(
            Class<?> entityClass,
            String entityClassName,
            LambdaExpression predicate) implements LambdaExpression {

        public SubqueryBuilderReference {
            Objects.requireNonNull(entityClass, "Entity class cannot be null");
            // entityClassName can be null (only set when class not loadable at build-time)
            // predicate can be null (no WHERE clause)
        }

        /**
         * Creates a SubqueryBuilderReference without entityClassName or predicate.
         */
        public SubqueryBuilderReference(Class<?> entityClass) {
            this(entityClass, null, null);
        }

        /**
         * Creates a SubqueryBuilderReference without predicate.
         */
        public SubqueryBuilderReference(Class<?> entityClass, String entityClassName) {
            this(entityClass, entityClassName, null);
        }

        /**
         * Creates a SubqueryBuilderReference without entityClassName (for compatibility).
         */
        public SubqueryBuilderReference(Class<?> entityClass, LambdaExpression predicate) {
            this(entityClass, null, predicate);
        }

        /**
         * Creates a new SubqueryBuilderReference with an added predicate.
         * If this builder already has a predicate, combines them with AND.
         */
        public SubqueryBuilderReference withPredicate(LambdaExpression newPredicate) {
            Objects.requireNonNull(newPredicate, "New predicate cannot be null");

            if (this.predicate == null) {
                return new SubqueryBuilderReference(entityClass, entityClassName, newPredicate);
            }

            // Combine with existing predicate using AND
            LambdaExpression combinedPredicate = new BinaryOp(
                this.predicate,
                BinaryOp.Operator.AND,
                newPredicate
            );
            return new SubqueryBuilderReference(entityClass, entityClassName, combinedPredicate);
        }

        /**
         * Returns true if this builder has a filtering predicate.
         */
        public boolean hasPredicate() {
            return predicate != null;
        }
    }

    /**
     * Scalar aggregation subquery expression.
     * <p>
     * Represents a subquery that returns a single scalar value from an aggregation.
     * Used for comparisons like {@code p.salary > subquery(Person.class).avg(q -> q.salary)}.
     * <p>
     * Example:
     * <pre>
     * // Fluent API: p -> p.salary > subquery(Person.class).avg(q -> q.salary)
     * // → BinaryOp(FieldAccess("salary"), GT, ScalarSubquery(AVG, Person.class, FieldAccess("salary"), null))
     *
     * // Generated JPA:
     * Subquery&lt;Double&gt; avgSalary = cq.subquery(Double.class);
     * Root&lt;Person&gt; subRoot = avgSalary.from(Person.class);
     * avgSalary.select(cb.avg(subRoot.get("salary")));
     * cq.where(cb.gt(root.get("salary"), avgSalary));
     * </pre>
     *
     * @param aggregationType The type of aggregation (AVG, SUM, MIN, MAX, COUNT)
     * @param entityClass The entity class for the subquery
     * @param entityClassName Optional entity class name (for placeholder classes not loadable at build-time)
     * @param fieldExpression The field being aggregated (may be null for COUNT)
     * @param predicate Optional predicate to filter the subquery (WHERE clause)
     * @param resultType The return type of the aggregation
     */
    record ScalarSubquery(
            SubqueryAggregationType aggregationType,
            Class<?> entityClass,
            String entityClassName,
            LambdaExpression fieldExpression,
            LambdaExpression predicate,
            Class<?> resultType) implements LambdaExpression {

        public ScalarSubquery {
            Objects.requireNonNull(aggregationType, "Aggregation type cannot be null");
            Objects.requireNonNull(entityClass, "Entity class cannot be null");
            Objects.requireNonNull(resultType, "Result type cannot be null");
            // entityClassName can be null (only set when class not loadable at build-time)
            // fieldExpression can be null for COUNT
            // predicate can be null (no WHERE clause in subquery)
        }

        /**
         * Creates an AVG subquery.
         */
        public static ScalarSubquery avg(Class<?> entityClass, LambdaExpression field, LambdaExpression predicate) {
            return new ScalarSubquery(SubqueryAggregationType.AVG, entityClass, null, field, predicate, Double.class);
        }

        /**
         * Creates a SUM subquery.
         */
        public static ScalarSubquery sum(Class<?> entityClass, LambdaExpression field, LambdaExpression predicate, Class<?> resultType) {
            return new ScalarSubquery(SubqueryAggregationType.SUM, entityClass, null, field, predicate, resultType);
        }

        /**
         * Creates a MIN subquery.
         */
        public static ScalarSubquery min(Class<?> entityClass, LambdaExpression field, LambdaExpression predicate, Class<?> resultType) {
            return new ScalarSubquery(SubqueryAggregationType.MIN, entityClass, null, field, predicate, resultType);
        }

        /**
         * Creates a MAX subquery.
         */
        public static ScalarSubquery max(Class<?> entityClass, LambdaExpression field, LambdaExpression predicate, Class<?> resultType) {
            return new ScalarSubquery(SubqueryAggregationType.MAX, entityClass, null, field, predicate, resultType);
        }

        /**
         * Creates a COUNT subquery.
         */
        public static ScalarSubquery count(Class<?> entityClass, LambdaExpression predicate) {
            return new ScalarSubquery(SubqueryAggregationType.COUNT, entityClass, null, null, predicate, Long.class);
        }

        /**
         * Returns true if this subquery has a predicate (WHERE clause).
         */
        public boolean hasPredicate() {
            return predicate != null;
        }

        /**
         * Returns true if this is a COUNT subquery (no field required).
         */
        public boolean isCount() {
            return aggregationType == SubqueryAggregationType.COUNT;
        }
    }

    /**
     * EXISTS subquery expression.
     * <p>
     * Represents an EXISTS check on a correlated or non-correlated subquery.
     * Used for predicates like {@code subquery(Phone.class).exists(ph -> ph.owner.id == p.id)}.
     * <p>
     * Example:
     * <pre>
     * // Fluent API: p -> subquery(Phone.class).exists(ph -> ph.owner.id.equals(p.id))
     * // → ExistsSubquery(Phone.class, BinaryOp(PathExpression(...), EQ, CorrelatedVariable(...)), false)
     *
     * // Generated JPA:
     * Subquery&lt;Phone&gt; phoneSub = cq.subquery(Phone.class);
     * Root&lt;Phone&gt; phoneRoot = phoneSub.from(Phone.class);
     * phoneSub.select(phoneRoot);
     * phoneSub.where(cb.equal(phoneRoot.get("owner").get("id"), root.get("id")));
     * cq.where(cb.exists(phoneSub));
     * </pre>
     *
     * @param entityClass The entity class for the subquery
     * @param entityClassName Optional entity class name (for placeholder classes not loadable at build-time)
     * @param predicate The predicate defining the subquery (may contain correlated references)
     * @param negated True for NOT EXISTS, false for EXISTS
     */
    record ExistsSubquery(
            Class<?> entityClass,
            String entityClassName,
            LambdaExpression predicate,
            boolean negated) implements LambdaExpression {

        public ExistsSubquery {
            Objects.requireNonNull(entityClass, "Entity class cannot be null");
            Objects.requireNonNull(predicate, "Predicate cannot be null");
            // entityClassName can be null (only set when class not loadable at build-time)
        }

        /**
         * Creates an EXISTS subquery.
         */
        public static ExistsSubquery exists(Class<?> entityClass, LambdaExpression predicate) {
            return new ExistsSubquery(entityClass, null, predicate, false);
        }

        /**
         * Creates a NOT EXISTS subquery.
         */
        public static ExistsSubquery notExists(Class<?> entityClass, LambdaExpression predicate) {
            return new ExistsSubquery(entityClass, null, predicate, true);
        }
    }

    /**
     * IN subquery expression.
     * <p>
     * Represents an IN check against a subquery result set.
     * Used for predicates like {@code subquery(Department.class).in(p.department.name, d -> d.name, d -> d.budget > 1000000)}.
     * <p>
     * Example:
     * <pre>
     * // Fluent API: p -> subquery(Department.class).in(p.department.name, d -> d.name, d -> d.budget > 1000000)
     * // → InSubquery(PathExpression("department.name"), Department.class, FieldAccess("name"), predicate, false)
     *
     * // Generated JPA:
     * Subquery&lt;String&gt; deptNames = cq.subquery(String.class);
     * Root&lt;Department&gt; deptRoot = deptNames.from(Department.class);
     * deptNames.select(deptRoot.get("name"));
     * deptNames.where(cb.gt(deptRoot.get("budget"), 1000000));
     * cq.where(root.get("department").get("name").in(deptNames));
     * </pre>
     *
     * @param field The field to check (left side of IN)
     * @param entityClass The entity class for the subquery
     * @param entityClassName Optional entity class name (for placeholder classes not loadable at build-time)
     * @param selectExpression The expression to select (right side values)
     * @param predicate Optional predicate to filter the subquery
     * @param negated True for NOT IN, false for IN
     */
    record InSubquery(
            LambdaExpression field,
            Class<?> entityClass,
            String entityClassName,
            LambdaExpression selectExpression,
            LambdaExpression predicate,
            boolean negated) implements LambdaExpression {

        public InSubquery {
            Objects.requireNonNull(field, "Field cannot be null");
            Objects.requireNonNull(entityClass, "Entity class cannot be null");
            Objects.requireNonNull(selectExpression, "Select expression cannot be null");
            // entityClassName can be null (only set when class not loadable at build-time)
            // predicate can be null (no WHERE clause in subquery)
        }

        /**
         * Creates an IN subquery.
         */
        public static InSubquery in(LambdaExpression field, Class<?> entityClass,
                                     LambdaExpression selectExpr, LambdaExpression predicate) {
            return new InSubquery(field, entityClass, null, selectExpr, predicate, false);
        }

        /**
         * Creates a NOT IN subquery.
         */
        public static InSubquery notIn(LambdaExpression field, Class<?> entityClass,
                                        LambdaExpression selectExpr, LambdaExpression predicate) {
            return new InSubquery(field, entityClass, null, selectExpr, predicate, true);
        }

        /**
         * Returns true if this subquery has a predicate (WHERE clause).
         */
        public boolean hasPredicate() {
            return predicate != null;
        }
    }

    /**
     * Correlated variable reference in a subquery.
     * <p>
     * Represents a reference to a variable from the outer query within a correlated subquery.
     * This enables the subquery to access the outer query's root entity.
     * <p>
     * Example:
     * <pre>
     * // Fluent API: p -> subquery(Phone.class).exists(ph -> ph.owner.id.equals(p.id))
     * // The 'p.id' inside the inner lambda is:
     * // CorrelatedVariable(PathExpression("id"), 0, Person.class)
     * //
     * // This tells the code generator to use the outer root instead of the subquery root
     * </pre>
     *
     * @param fieldExpression The field/path being accessed on the outer variable
     * @param outerParameterIndex The parameter index in the outer lambda (typically 0)
     * @param outerEntityType The entity type of the outer variable
     */
    record CorrelatedVariable(
            LambdaExpression fieldExpression,
            int outerParameterIndex,
            Class<?> outerEntityType) implements LambdaExpression {

        public CorrelatedVariable {
            Objects.requireNonNull(fieldExpression, "Field expression cannot be null");
            Objects.requireNonNull(outerEntityType, "Outer entity type cannot be null");
        }
    }
}
