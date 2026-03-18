package io.quarkiverse.qubit.deployment.ast;

import static io.quarkiverse.qubit.deployment.common.ExceptionMessages.AGGREGATION_TYPE_NULL;
import static io.quarkiverse.qubit.deployment.common.ExceptionMessages.ENTITY_CLASS_NULL;
import static io.quarkiverse.qubit.deployment.common.ExceptionMessages.ENTITY_POSITION_NULL;
import static io.quarkiverse.qubit.deployment.common.ExceptionMessages.FIELD_NAME_NULL;
import static io.quarkiverse.qubit.deployment.common.ExceptionMessages.FIELD_TYPE_NULL;
import static io.quarkiverse.qubit.deployment.common.ExceptionMessages.PARAMETER_NAME_NULL;
import static io.quarkiverse.qubit.deployment.common.ExceptionMessages.PARAMETER_TYPE_NULL;
import static io.quarkiverse.qubit.deployment.common.ExceptionMessages.RESULT_TYPE_NULL;
import static io.quarkiverse.qubit.runtime.internal.QubitConstants.JVM_JAVA_LANG_OBJECT;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;

/**
 * Sealed AST for parsed lambda expressions.
 *
 * <p>
 * <b>Why sealed?</b> Exhaustive switch expressions, controlled hierarchy, JVM optimization.
 * <b>Why records?</b> Immutability, structural equality, pattern matching decomposition.
 *
 * <pre>
 * p -> p.age > 18 && p.active
 *       ↓ bytecode analysis
 * BinaryOp(BinaryOp(FieldAccess("age"), GT, Constant(18)), AND, FieldAccess("active"))
 *       ↓ code generation
 * cb.and(cb.gt(root.get("age"), 18), cb.isTrue(root.get("active")))
 * </pre>
 *
 * @see io.quarkiverse.qubit.deployment.analysis.LambdaBytecodeAnalyzer
 */
public sealed interface LambdaExpression {

    /** Extracts field name if applicable (FieldAccess, PathExpression, BiEntity variants). */
    default Optional<String> getFieldName() {
        return Optional.empty();
    }

    /** Extracts field name, throwing if not available. */
    default String getFieldNameOrThrow() {
        return getFieldName()
                .orElseThrow(() -> new IllegalArgumentException("Cannot extract field name from expression: " + this));
    }

    /** Binary operation (comparison, logical, or arithmetic). */
    record BinaryOp(LambdaExpression left, Operator operator, LambdaExpression right) implements LambdaExpression {
        /** Binary operation types with symbols. */
        public enum Operator {
            EQ("=="),
            NE("!="),
            LT("<"),
            LE("<="),
            GT(">"),
            GE(">="),
            AND("&&"),
            OR("||"),
            ADD("+"),
            SUB("-"),
            MUL("*"),
            DIV("/"),
            MOD("%");

            private final String symbol;

            Operator(String symbol) {
                this.symbol = symbol;
            }

            public String symbol() {
                return symbol;
            }
        }

        // Factory methods for all operator types
        public static BinaryOp and(LambdaExpression left, LambdaExpression right) {
            return new BinaryOp(left, Operator.AND, right);
        }

        public static BinaryOp or(LambdaExpression left, LambdaExpression right) {
            return new BinaryOp(left, Operator.OR, right);
        }

        public static BinaryOp eq(LambdaExpression left, LambdaExpression right) {
            return new BinaryOp(left, Operator.EQ, right);
        }

        public static BinaryOp ne(LambdaExpression left, LambdaExpression right) {
            return new BinaryOp(left, Operator.NE, right);
        }

        public static BinaryOp lt(LambdaExpression left, LambdaExpression right) {
            return new BinaryOp(left, Operator.LT, right);
        }

        public static BinaryOp le(LambdaExpression left, LambdaExpression right) {
            return new BinaryOp(left, Operator.LE, right);
        }

        public static BinaryOp gt(LambdaExpression left, LambdaExpression right) {
            return new BinaryOp(left, Operator.GT, right);
        }

        public static BinaryOp ge(LambdaExpression left, LambdaExpression right) {
            return new BinaryOp(left, Operator.GE, right);
        }

        public static BinaryOp add(LambdaExpression left, LambdaExpression right) {
            return new BinaryOp(left, Operator.ADD, right);
        }

        public static BinaryOp sub(LambdaExpression left, LambdaExpression right) {
            return new BinaryOp(left, Operator.SUB, right);
        }

        public static BinaryOp mul(LambdaExpression left, LambdaExpression right) {
            return new BinaryOp(left, Operator.MUL, right);
        }

        public static BinaryOp div(LambdaExpression left, LambdaExpression right) {
            return new BinaryOp(left, Operator.DIV, right);
        }

        public static BinaryOp mod(LambdaExpression left, LambdaExpression right) {
            return new BinaryOp(left, Operator.MOD, right);
        }
    }

    /** Unary operation (NOT). */
    record UnaryOp(Operator operator, LambdaExpression operand) implements LambdaExpression {
        public enum Operator {
            NOT("!");

            private final String symbol;

            Operator(String symbol) {
                this.symbol = symbol;
            }

            public String symbol() {
                return symbol;
            }
        }

        public static UnaryOp not(LambdaExpression operand) {
            return new UnaryOp(Operator.NOT, operand);
        }
    }

    /** Field access expression (e.g., p.name, p.age). */
    record FieldAccess(String fieldName, Class<?> fieldType) implements LambdaExpression {
        public FieldAccess {
            Objects.requireNonNull(fieldName, FIELD_NAME_NULL);
            Objects.requireNonNull(fieldType, FIELD_TYPE_NULL);
        }

        @Override
        public Optional<String> getFieldName() {
            return Optional.of(fieldName);
        }
    }

    /** Method invocation expression. */
    record MethodCall(
            @Nullable LambdaExpression target,
            String methodName,
            List<LambdaExpression> arguments,
            Class<?> returnType) implements LambdaExpression {

        public MethodCall {
            Objects.requireNonNull(methodName, "Method name cannot be null");
            arguments = List.copyOf(arguments);
            Objects.requireNonNull(returnType, "Return type cannot be null");
        }
    }

    /** Constant literal value (non-null). Use {@link NullLiteral} for null values. */
    record Constant(Object value, Class<?> type) implements LambdaExpression {
        // Common constants
        public static final Constant TRUE = new Constant(true, boolean.class);
        public static final Constant FALSE = new Constant(false, boolean.class);
        public static final Constant ZERO_INT = new Constant(0, int.class);

        public Constant {
            Objects.requireNonNull(value, "Value cannot be null — use NullLiteral for null constants");
            Objects.requireNonNull(type, "Type cannot be null");
        }
    }

    /** Lambda parameter reference. */
    record Parameter(String name, Class<?> type, int index) implements LambdaExpression {
        public Parameter {
            Objects.requireNonNull(name, PARAMETER_NAME_NULL);
            Objects.requireNonNull(type, PARAMETER_TYPE_NULL);
        }
    }

    /**
     * Captured variable from enclosing scope.
     * Index = parameter position in lambda descriptor (captured vars precede entity params).
     * Name from LocalVariableTable debug info, or null if unavailable.
     */
    record CapturedVariable(int index, Class<?> type, @Nullable String name) implements LambdaExpression {
        public CapturedVariable {
            // Validate index bounds to prevent ArrayIndexOutOfBoundsException when accessing capturedValues array in generated code
            if (index < 0) {
                throw new IllegalArgumentException(
                        "CapturedVariable index must be non-negative, got: " + index +
                                ". This may indicate a lambda local variable was incorrectly identified as a captured variable.");
            }
            Objects.requireNonNull(type, "Type cannot be null");
        }

        public CapturedVariable(int index, Class<?> type) {
            this(index, type, null);
        }

        /** Returns original name or fallback like "capturedVar0". */
        public String displayName() {
            return name != null ? name : "capturedVar" + index;
        }
    }

    /** Null literal constant. */
    record NullLiteral(Class<?> expectedType) implements LambdaExpression {
    }

    /** Type cast expression. */
    record Cast(LambdaExpression expression, Class<?> targetType) implements LambdaExpression {
        public Cast {
            Objects.requireNonNull(expression, "Expression cannot be null");
            Objects.requireNonNull(targetType, "Target type cannot be null");
        }
    }

    /**
     * SQL CAST expression (JPA 3.2).
     * Generates {@code expression.cast(targetType)} → SQL {@code CAST(expr AS type)}.
     * Distinct from {@link Cast} which represents bytecode CHECKCAST (type narrowing, no SQL effect).
     */
    record SqlCast(LambdaExpression expression, Class<?> targetType) implements LambdaExpression {
        public SqlCast {
            Objects.requireNonNull(expression, "Expression cannot be null");
            Objects.requireNonNull(targetType, "Target type cannot be null");
        }
    }

    /** Instance type check expression. */
    record InstanceOf(LambdaExpression expression, Class<?> targetType) implements LambdaExpression {
        public InstanceOf {
            Objects.requireNonNull(expression, "Expression cannot be null");
            Objects.requireNonNull(targetType, "Target type cannot be null");
        }
    }

    /**
     * Entity downcast for accessing subclass fields in inheritance hierarchies.
     *
     * <p>
     * Maps to JPA: {@code cb.treat(root, targetType).get(fieldName)}.
     * The {@code inner} expression is resolved against the treated root.
     *
     * <p>
     * Created when pattern matching ({@code a instanceof Dog d && d.breed.equals("Lab")})
     * or explicit casting ({@code a instanceof Dog && ((Dog) a).breed.equals("Lab")}) is
     * detected during bytecode analysis.
     *
     * @param treatType the entity subclass to downcast to
     * @param inner the expression to resolve on the treated root (FieldAccess, PathExpression)
     */
    record TreatExpression(
            Class<?> treatType,
            LambdaExpression inner) implements LambdaExpression {

        public TreatExpression {
            Objects.requireNonNull(treatType, "Treat type cannot be null");
            Objects.requireNonNull(inner, "Inner expression cannot be null");
        }
    }

    /** Ternary conditional expression. */
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
     * Constructor invocation for DTO projections (new ClassName(arg1, arg2, ...)).
     * Maps to JPA: cb.construct(ClassName.class, arg1, arg2, ...).
     */
    record ConstructorCall(
            String className,
            List<LambdaExpression> arguments,
            Class<?> resultType) implements LambdaExpression {

        public ConstructorCall {
            Objects.requireNonNull(className, "Class name cannot be null");
            arguments = List.copyOf(arguments);
            Objects.requireNonNull(resultType, RESULT_TYPE_NULL);
        }
    }

    /**
     * Array creation for multi-value projections (new T[]{e1, e2, ...}).
     * Used for GROUP BY projections returning multiple values.
     */
    record ArrayCreation(
            String elementType,
            List<LambdaExpression> elements,
            Class<?> resultType) implements LambdaExpression {

        public ArrayCreation {
            Objects.requireNonNull(elementType, "Element type cannot be null");
            elements = List.copyOf(elements);
            Objects.requireNonNull(resultType, RESULT_TYPE_NULL);
        }

        public boolean isObjectArray() {
            return JVM_JAVA_LANG_OBJECT.equals(elementType);
        }
    }

    /** Relationship type - determines whether path segment requires JPA join. */
    enum RelationType {
        FIELD,
        MANY_TO_ONE,
        ONE_TO_ONE,
        ONE_TO_MANY,
        MANY_TO_MANY
    }

    /** A segment in a path expression representing a single navigation step. */
    record PathSegment(
            String fieldName,
            Class<?> fieldType,
            RelationType relationType) {

        public PathSegment {
            Objects.requireNonNull(fieldName, FIELD_NAME_NULL);
            if (fieldName.isBlank()) {
                throw new IllegalArgumentException("Field name cannot be empty or blank");
            }
            Objects.requireNonNull(fieldType, FIELD_TYPE_NULL);
            Objects.requireNonNull(relationType, "Relation type cannot be null");
        }

        public boolean requiresJoin() {
            return relationType != RelationType.FIELD;
        }
    }

    /**
     * Sealed interface for path-based expressions that navigate through segments.
     * <p>
     * This interface provides a shared {@link #getFieldName()} implementation for
     * both {@link PathExpression} and {@link BiEntityPathExpression}, eliminating
     * code duplication.
     * <p>
     * Both implementations are guaranteed to have non-empty segments (validated by
     * constructors), so direct access to the first segment is always safe.
     */
    sealed interface SegmentBasedPath extends LambdaExpression
            permits PathExpression, BiEntityPathExpression {

        /**
         * Returns the list of path segments.
         * <p>
         * This accessor is automatically implemented by records that have a
         * {@code segments} component.
         *
         * @return the non-empty list of path segments
         */
        List<PathSegment> segments();

        /**
         * Returns the first segment's field name.
         * <p>
         * This is the relationship or field name at the start of the path navigation.
         * For example, in {@code p.owner.firstName}, this returns "owner".
         *
         * @return Optional containing the first segment's field name
         */
        @Override
        default Optional<String> getFieldName() {
            // Constructors of implementing records guarantee segments is non-empty
            return Optional.of(segments().getFirst().fieldName());
        }

        /**
         * Returns the dot-separated path string for all segments.
         * <p>
         * For example, a path with segments ["owner", "firstName"] returns "owner.firstName".
         * <p>
         * This is useful for display purposes in JPQL generation and debugging.
         *
         * @return the dot-joined path string
         */
        default String toPath() {
            return segments().stream()
                    .map(PathSegment::fieldName)
                    .collect(Collectors.joining("."));
        }

        /**
         * Validates and normalizes segments for path expression constructors.
         * <p>
         * This helper method provides consistent validation across all path-based
         * expression types, ensuring:
         * <ul>
         * <li>Segments list is not null</li>
         * <li>Segments list is not empty</li>
         * <li>Returns a defensive immutable copy</li>
         * </ul>
         *
         * @param segments the segments to validate
         * @return an immutable copy of the validated segments
         * @throws NullPointerException if segments is null
         * @throws IllegalArgumentException if segments is empty
         */
        static List<PathSegment> validateSegments(List<PathSegment> segments) {
            Objects.requireNonNull(segments, "Segments cannot be null");
            if (segments.isEmpty()) {
                throw new IllegalArgumentException("Path expression must have at least one segment");
            }
            return List.copyOf(segments);
        }
    }

    /**
     * Path expression for relationship navigation.
     * <p>
     * Represents chained field access like {@code p.owner.firstName} or
     * {@code phone.owner.department.name}.
     * <p>
     * Example:
     *
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
            Class<?> resultType) implements SegmentBasedPath {

        public PathExpression {
            segments = SegmentBasedPath.validateSegments(segments);
            Objects.requireNonNull(resultType, RESULT_TYPE_NULL);
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
            return segments.getLast();
        }

        /**
         * Creates a path expression with a single segment (simple field navigation).
         *
         * @param fieldName the field name
         * @param fieldType the field type (also used as result type)
         * @param relationType the relationship type for this field
         * @return a PathExpression with one segment
         */
        public static PathExpression single(String fieldName, Class<?> fieldType, RelationType relationType) {
            PathSegment segment = new PathSegment(fieldName, fieldType, relationType);
            return new PathExpression(List.of(segment), fieldType);
        }

        /**
         * Creates a simple field path expression (no relationship, just FIELD access).
         *
         * @param fieldName the field name
         * @param fieldType the field type (also used as result type)
         * @return a PathExpression with one FIELD segment
         */
        public static PathExpression field(String fieldName, Class<?> fieldType) {
            return single(fieldName, fieldType, RelationType.FIELD);
        }

        // getFieldName() inherited from SegmentBasedPath
    }

    /**
     * IN expression for collection membership testing.
     * <p>
     * Represents a predicate that checks if a field value is contained within a collection.
     * This translates to SQL {@code WHERE field IN (value1, value2, ...)} or JPA
     * {@code cb.in(root.get("field")).value(v1).value(v2)...}.
     * <p>
     * Example:
     *
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
     *
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

    /**
     * Identifies which entity in a bi-entity lambda (BiQuerySpec).
     */
    enum EntityPosition {
        /** First entity in the join (source/left side). */
        FIRST,
        /** Second entity in the join (joined/right side). */
        SECOND;

        /**
         * Selects the appropriate alias based on entity position.
         * <p>
         * Simplifies the common pattern of choosing between two aliases
         * based on whether this position is FIRST or SECOND.
         *
         * @param firstAlias alias to return for FIRST position
         * @param secondAlias alias to return for SECOND position
         * @return the selected alias
         */
        public String selectAlias(String firstAlias, String secondAlias) {
            return this == FIRST ? firstAlias : secondAlias;
        }
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
     *
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
            Objects.requireNonNull(name, PARAMETER_NAME_NULL);
            Objects.requireNonNull(type, PARAMETER_TYPE_NULL);
            Objects.requireNonNull(position, ENTITY_POSITION_NULL);
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
     *
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
            Objects.requireNonNull(fieldName, FIELD_NAME_NULL);
            Objects.requireNonNull(fieldType, FIELD_TYPE_NULL);
            Objects.requireNonNull(entityPosition, ENTITY_POSITION_NULL);
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

        /**
         * Creates a field access from the first (source/left) entity.
         *
         * @param fieldName the field name
         * @param fieldType the field type
         * @return a BiEntityFieldAccess from the first entity
         */
        public static BiEntityFieldAccess fromFirst(String fieldName, Class<?> fieldType) {
            return new BiEntityFieldAccess(fieldName, fieldType, EntityPosition.FIRST);
        }

        /**
         * Creates a field access from the second (joined/right) entity.
         *
         * @param fieldName the field name
         * @param fieldType the field type
         * @return a BiEntityFieldAccess from the second entity
         */
        public static BiEntityFieldAccess fromSecond(String fieldName, Class<?> fieldType) {
            return new BiEntityFieldAccess(fieldName, fieldType, EntityPosition.SECOND);
        }

        @Override
        public Optional<String> getFieldName() {
            return Optional.of(fieldName);
        }
    }

    /**
     * Path expression from a bi-entity context (join query).
     * <p>
     * Extends PathExpression to track which entity the path starts from in a join.
     * <p>
     * Example:
     *
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
            EntityPosition entityPosition) implements SegmentBasedPath {

        public BiEntityPathExpression {
            segments = SegmentBasedPath.validateSegments(segments);
            Objects.requireNonNull(resultType, RESULT_TYPE_NULL);
            Objects.requireNonNull(entityPosition, ENTITY_POSITION_NULL);
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

        /**
         * Creates a path expression starting from the first (source/left) entity.
         *
         * @param segments the path segments
         * @param resultType the result type
         * @return a BiEntityPathExpression from the first entity
         */
        public static BiEntityPathExpression fromFirst(List<PathSegment> segments, Class<?> resultType) {
            return new BiEntityPathExpression(segments, resultType, EntityPosition.FIRST);
        }

        /**
         * Creates a path expression starting from the second (joined/right) entity.
         *
         * @param segments the path segments
         * @param resultType the result type
         * @return a BiEntityPathExpression from the second entity
         */
        public static BiEntityPathExpression fromSecond(List<PathSegment> segments, Class<?> resultType) {
            return new BiEntityPathExpression(segments, resultType, EntityPosition.SECOND);
        }

        // getFieldName() inherited from SegmentBasedPath
    }

    /**
     * Group key reference in a GroupQuerySpec lambda.
     * <p>
     * Represents the {@code g.key()} call in a group context lambda.
     * This captures the grouping key expression from the original groupBy() call.
     * <p>
     * Example:
     *
     * <pre>
     * // Lambda: .select((Group&lt;Person, String&gt; g) -> g.key())
     * // → GroupKeyReference(keyExpression, String.class)
     *
     * // Generated JPA: root.get("department") (the grouping expression)
     * </pre>
     *
     * @param keyExpression The expression used for grouping (from groupBy() lambda), may be null
     *        when analyzed in isolation (resolved at code generation time)
     * @param resultType The type of the grouping key
     */
    record GroupKeyReference(
            @Nullable LambdaExpression keyExpression,
            Class<?> resultType) implements LambdaExpression {

        public GroupKeyReference {
            // keyExpression can be null - it gets resolved at code generation time
            // from the groupBy() lambda's key expression
            Objects.requireNonNull(resultType, RESULT_TYPE_NULL);
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
     *
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
            @Nullable LambdaExpression fieldExpression,
            Class<?> resultType) implements LambdaExpression {

        public GroupAggregation {
            Objects.requireNonNull(aggregationType, AGGREGATION_TYPE_NULL);
            Objects.requireNonNull(resultType, RESULT_TYPE_NULL);
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
     *
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
            Objects.requireNonNull(name, PARAMETER_NAME_NULL);
            Objects.requireNonNull(type, PARAMETER_TYPE_NULL);
            Objects.requireNonNull(entityType, "Entity type cannot be null");
            Objects.requireNonNull(keyType, "Key type cannot be null");
        }
    }

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
            @Nullable String entityClassName,
            @Nullable LambdaExpression predicate) implements LambdaExpression {

        public SubqueryBuilderReference {
            Objects.requireNonNull(entityClass, ENTITY_CLASS_NULL);
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
                    newPredicate);
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
     *
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
            @Nullable String entityClassName,
            @Nullable LambdaExpression fieldExpression,
            @Nullable LambdaExpression predicate,
            Class<?> resultType) implements LambdaExpression {

        public ScalarSubquery {
            Objects.requireNonNull(aggregationType, AGGREGATION_TYPE_NULL);
            Objects.requireNonNull(entityClass, ENTITY_CLASS_NULL);
            Objects.requireNonNull(resultType, RESULT_TYPE_NULL);
            // entityClassName can be null (only set when class not loadable at build-time)
            // fieldExpression can be null for COUNT
            // predicate can be null (no WHERE clause in subquery)
        }

        /**
         * Creates an AVG subquery.
         */
        public static ScalarSubquery avg(Class<?> entityClass, @Nullable LambdaExpression field,
                @Nullable LambdaExpression predicate) {
            return new ScalarSubquery(SubqueryAggregationType.AVG, entityClass, null, field, predicate, Double.class);
        }

        /**
         * Creates a SUM subquery.
         */
        public static ScalarSubquery sum(Class<?> entityClass, @Nullable LambdaExpression field,
                @Nullable LambdaExpression predicate,
                Class<?> resultType) {
            return new ScalarSubquery(SubqueryAggregationType.SUM, entityClass, null, field, predicate, resultType);
        }

        /**
         * Creates a MIN subquery.
         */
        public static ScalarSubquery min(Class<?> entityClass, @Nullable LambdaExpression field,
                @Nullable LambdaExpression predicate,
                Class<?> resultType) {
            return new ScalarSubquery(SubqueryAggregationType.MIN, entityClass, null, field, predicate, resultType);
        }

        /**
         * Creates a MAX subquery.
         */
        public static ScalarSubquery max(Class<?> entityClass, @Nullable LambdaExpression field,
                @Nullable LambdaExpression predicate,
                Class<?> resultType) {
            return new ScalarSubquery(SubqueryAggregationType.MAX, entityClass, null, field, predicate, resultType);
        }

        /**
         * Creates a COUNT subquery.
         */
        public static ScalarSubquery count(Class<?> entityClass, @Nullable LambdaExpression predicate) {
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
     *
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
            @Nullable String entityClassName,
            LambdaExpression predicate,
            boolean negated) implements LambdaExpression {

        public ExistsSubquery {
            Objects.requireNonNull(entityClass, ENTITY_CLASS_NULL);
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
     *
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
            @Nullable String entityClassName,
            LambdaExpression selectExpression,
            @Nullable LambdaExpression predicate,
            boolean negated) implements LambdaExpression {

        public InSubquery {
            Objects.requireNonNull(field, "Field cannot be null");
            Objects.requireNonNull(entityClass, ENTITY_CLASS_NULL);
            Objects.requireNonNull(selectExpression, "Select expression cannot be null");
            // entityClassName can be null (only set when class not loadable at build-time)
            // predicate can be null (no WHERE clause in subquery)
        }

        /**
         * Creates an IN subquery.
         */
        public static InSubquery in(LambdaExpression field, Class<?> entityClass,
                LambdaExpression selectExpr, @Nullable LambdaExpression predicate) {
            return new InSubquery(field, entityClass, null, selectExpr, predicate, false);
        }

        /**
         * Creates a NOT IN subquery.
         */
        public static InSubquery notIn(LambdaExpression field, Class<?> entityClass,
                LambdaExpression selectExpr, @Nullable LambdaExpression predicate) {
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
     *
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

    // ─── Mathematical Functions ───────────────────────────────────────────────

    /**
     * Static method call evaluated at query execution time (constant folding).
     *
     * <p>
     * When all arguments to a static method are compile-time constants or captured
     * variables, the method is invoked at query execution time and the result is used
     * as a JPA literal parameter. This enables utility methods like
     * {@code StringUtils.upperCase(searchTerm)} in lambda expressions.
     *
     * @param ownerClass the class containing the static method
     * @param methodName the method name
     * @param methodDescriptor the JVM method descriptor (e.g., "(Ljava/lang/String;)Ljava/lang/String;")
     * @param arguments the method arguments (must be Constant or CapturedVariable)
     * @param returnType the method return type
     */
    record FoldedMethodCall(
            Class<?> ownerClass,
            String methodName,
            String methodDescriptor,
            List<LambdaExpression> arguments,
            Class<?> returnType) implements LambdaExpression {

        public FoldedMethodCall {
            Objects.requireNonNull(ownerClass, "Owner class cannot be null");
            Objects.requireNonNull(methodName, "Method name cannot be null");
            Objects.requireNonNull(methodDescriptor, "Method descriptor cannot be null");
            arguments = List.copyOf(arguments);
            Objects.requireNonNull(returnType, "Return type cannot be null");
        }
    }

    /**
     * Mathematical function applied to one or two expressions.
     *
     * <p>
     * Represents standard JPA CriteriaBuilder math functions:
     * <ul>
     * <li>Unary: {@code cb.abs()}, {@code cb.neg()}, {@code cb.sqrt()}, {@code cb.sign()},
     * {@code cb.ceiling()}, {@code cb.floor()}, {@code cb.exp()}, {@code cb.ln()}</li>
     * <li>Binary: {@code cb.power()}, {@code cb.round()}</li>
     * </ul>
     *
     * @param op the math operation
     * @param operand the primary operand expression
     * @param secondOperand the second operand (non-null for binary ops, null for unary)
     */
    record MathFunction(
            MathOp op,
            LambdaExpression operand,
            @Nullable LambdaExpression secondOperand) implements LambdaExpression {

        /** Standard JPA CriteriaBuilder math operations. */
        public enum MathOp {
            /** Absolute value — {@code cb.abs()} (JPA 2.0) */
            ABS,
            /** Arithmetic negation — {@code cb.neg()} (JPA 2.0) */
            NEG,
            /** Square root — {@code cb.sqrt()} (JPA 2.0) */
            SQRT,
            /** Sign of a number (-1, 0, 1) — {@code cb.sign()} (JPA 3.1) */
            SIGN,
            /** Ceiling — {@code cb.ceiling()} (JPA 3.1) */
            CEILING,
            /** Floor — {@code cb.floor()} (JPA 3.1) */
            FLOOR,
            /** Exponential (e^x) — {@code cb.exp()} (JPA 3.1) */
            EXP,
            /** Natural logarithm — {@code cb.ln()} (JPA 3.1) */
            LN,
            /** Exponentiation (x^y) — {@code cb.power()} (JPA 3.1) */
            POWER,
            /** Round to N decimal places — {@code cb.round()} (JPA 3.1) */
            ROUND;

            /** Returns true if this operation requires a second operand. */
            public boolean isBinary() {
                return this == POWER || this == ROUND;
            }
        }

        public MathFunction {
            Objects.requireNonNull(op, "MathOp cannot be null");
            Objects.requireNonNull(operand, "operand cannot be null");
            if (op.isBinary() && secondOperand == null) {
                throw new IllegalArgumentException(op + " requires a second operand");
            }
            if (!op.isBinary() && secondOperand != null) {
                throw new IllegalArgumentException(op + " is unary but received a second operand");
            }
        }

        // Unary factory methods
        public static MathFunction abs(LambdaExpression operand) {
            return new MathFunction(MathOp.ABS, operand, null);
        }

        public static MathFunction neg(LambdaExpression operand) {
            return new MathFunction(MathOp.NEG, operand, null);
        }

        public static MathFunction sqrt(LambdaExpression operand) {
            return new MathFunction(MathOp.SQRT, operand, null);
        }

        public static MathFunction sign(LambdaExpression operand) {
            return new MathFunction(MathOp.SIGN, operand, null);
        }

        public static MathFunction ceiling(LambdaExpression operand) {
            return new MathFunction(MathOp.CEILING, operand, null);
        }

        public static MathFunction floor(LambdaExpression operand) {
            return new MathFunction(MathOp.FLOOR, operand, null);
        }

        public static MathFunction exp(LambdaExpression operand) {
            return new MathFunction(MathOp.EXP, operand, null);
        }

        public static MathFunction ln(LambdaExpression operand) {
            return new MathFunction(MathOp.LN, operand, null);
        }

        // Binary factory methods
        public static MathFunction power(LambdaExpression base, LambdaExpression exponent) {
            return new MathFunction(MathOp.POWER, base, exponent);
        }

        public static MathFunction round(LambdaExpression operand, LambdaExpression decimalPlaces) {
            return new MathFunction(MathOp.ROUND, operand, decimalPlaces);
        }
    }
}
