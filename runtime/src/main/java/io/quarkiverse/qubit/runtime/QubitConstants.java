package io.quarkiverse.qubit.runtime;

import java.util.Set;

/**
 * Shared constants for the Qubit extension.
 *
 * <p>Naming Conventions:
 * <ul>
 *   <li><b>CB_*</b> - {@link jakarta.persistence.criteria.CriteriaBuilder} method names</li>
 *   <li><b>METHOD_*</b> - Java/JPA method names used in lambda expression analysis</li>
 *   <li><b>SQL_*</b> - SQL function names for database operations</li>
 *   <li><b>PREFIX_*</b> - Method name prefixes (e.g., "get", "is") for accessor detection</li>
 * </ul>
 */
public final class QubitConstants {

    private QubitConstants() {
    }

    // Fluent API - Entry point methods (static methods on QubitEntity)
    public static final String METHOD_WHERE = "where";
    public static final String METHOD_SELECT = "select";
    public static final String METHOD_SORTED_BY = "sortedBy";
    public static final String METHOD_SORTED_DESCENDING_BY = "sortedDescendingBy";

    // Fluent API - Intermediate operations (instance methods on QubitStream)
    public static final String METHOD_SKIP = "skip";
    public static final String METHOD_LIMIT = "limit";
    public static final String METHOD_DISTINCT = "distinct";

    // Fluent API - Aggregation operations (return QubitStream, can be entry points or intermediate)
    public static final String METHOD_MIN = "min";
    public static final String METHOD_MAX = "max";
    public static final String METHOD_AVG = "avg";
    public static final String METHOD_SUM_INTEGER = "sumInteger";
    public static final String METHOD_SUM_LONG = "sumLong";
    public static final String METHOD_SUM_DOUBLE = "sumDouble";

    // Fluent API - Terminal operations (instance methods on QubitStream)
    public static final String METHOD_TO_LIST = "toList";
    public static final String METHOD_COUNT = "count";
    public static final String METHOD_GET_SINGLE_RESULT = "getSingleResult";
    public static final String METHOD_FIND_FIRST = "findFirst";
    public static final String METHOD_EXISTS = "exists";

    // All fluent API entry points (static methods)
    public static final Set<String> FLUENT_ENTRY_POINT_METHODS = Set.of(
        METHOD_WHERE, METHOD_SELECT, METHOD_SORTED_BY, METHOD_SORTED_DESCENDING_BY,
        METHOD_MIN, METHOD_MAX, METHOD_AVG,
        METHOD_SUM_INTEGER, METHOD_SUM_LONG, METHOD_SUM_DOUBLE
    );

    // All fluent API intermediate operations
    public static final Set<String> FLUENT_INTERMEDIATE_METHODS = Set.of(
        METHOD_WHERE, METHOD_SELECT, METHOD_SORTED_BY, METHOD_SORTED_DESCENDING_BY,
        METHOD_SKIP, METHOD_LIMIT, METHOD_DISTINCT,
        METHOD_MIN, METHOD_MAX, METHOD_AVG,
        METHOD_SUM_INTEGER, METHOD_SUM_LONG, METHOD_SUM_DOUBLE
    );

    // All fluent API terminal operations
    public static final Set<String> FLUENT_TERMINAL_METHODS = Set.of(
        METHOD_TO_LIST, METHOD_COUNT, METHOD_EXISTS,
        METHOD_GET_SINGLE_RESULT, METHOD_FIND_FIRST
    );

    // Join method names
    public static final String METHOD_JOIN = "join";
    public static final String METHOD_LEFT_JOIN = "leftJoin";
    public static final String METHOD_ON = "on";
    public static final String METHOD_SELECT_SOURCE = "selectSource";
    public static final String METHOD_SELECT_JOINED = "selectJoined";

    // All join methods (for stack walking filter)
    public static final Set<String> JOIN_METHODS = Set.of(
        METHOD_JOIN, METHOD_LEFT_JOIN, METHOD_ON,
        METHOD_SELECT_SOURCE, METHOD_SELECT_JOINED
    );

    // Group method names
    public static final String METHOD_GROUP_BY = "groupBy";
    public static final String METHOD_HAVING = "having";
    public static final String METHOD_SELECT_KEY = "selectKey";

    // Group.xxx() method names for aggregate functions
    public static final String METHOD_KEY = "key";
    public static final String METHOD_COUNT_DISTINCT = "countDistinct";
    // Note: METHOD_AVG, METHOD_MIN, METHOD_MAX, METHOD_SUM_* already defined in aggregation section above

    // All group methods (for stack walking filter)
    public static final Set<String> GROUP_METHODS = Set.of(
        METHOD_GROUP_BY, METHOD_HAVING, METHOD_SELECT_KEY
    );

    // Group interface internal name
    public static final String GROUP_INTERNAL_NAME = "io/quarkiverse/qubit/runtime/Group";

    // Subquery method names
    public static final String SUBQUERY_AVG = "avg";
    public static final String SUBQUERY_SUM = "sum";
    public static final String SUBQUERY_MIN = "min";
    public static final String SUBQUERY_MAX = "max";
    public static final String SUBQUERY_COUNT = "count";
    public static final String SUBQUERY_EXISTS = "exists";
    public static final String SUBQUERY_NOT_EXISTS = "notExists";
    public static final String SUBQUERY_IN = "in";
    public static final String SUBQUERY_NOT_IN = "notIn";

    // All subquery methods (for stack walking filter)
    public static final Set<String> SUBQUERY_METHODS = Set.of(
        SUBQUERY_AVG, SUBQUERY_SUM, SUBQUERY_MIN, SUBQUERY_MAX, SUBQUERY_COUNT,
        SUBQUERY_EXISTS, SUBQUERY_NOT_EXISTS, SUBQUERY_IN, SUBQUERY_NOT_IN
    );

    // Subqueries utility class internal name
    public static final String SUBQUERIES_INTERNAL_NAME = "io/quarkiverse/qubit/runtime/Subqueries";

    // SubqueryBuilder class internal name and descriptor
    public static final String SUBQUERY_BUILDER_INTERNAL_NAME = "io/quarkiverse/qubit/runtime/SubqueryBuilder";
    public static final String SUBQUERY_BUILDER_DESCRIPTOR = "Lio/quarkiverse/qubit/runtime/SubqueryBuilder;";

    // Subquery factory method name
    public static final String METHOD_SUBQUERY = "subquery";

    // Temporal comparison methods (used in lambda expressions for date/time comparisons)
    public static final String METHOD_IS_AFTER = "isAfter";
    public static final String METHOD_IS_BEFORE = "isBefore";
    public static final String METHOD_IS_EQUAL = "isEqual";

    public static final Set<String> TEMPORAL_COMPARISON_METHOD_NAMES = Set.of(
        METHOD_IS_AFTER, METHOD_IS_BEFORE, METHOD_IS_EQUAL
    );

    public static final String QUBIT_ENTITY_CLASS_NAME = "io.quarkiverse.qubit.runtime.QubitEntity";
    public static final String QUBIT_REPOSITORY_CLASS_NAME = "io.quarkiverse.qubit.runtime.QubitRepository";
    public static final String QUBIT_STREAM_CLASS_NAME = "io.quarkiverse.qubit.runtime.QubitStream";
    public static final String QUERY_EXECUTOR_CLASS_NAME = "io.quarkiverse.qubit.runtime.QueryExecutor";

    // JVM internal names (slash-separated format for ASM bytecode generation)
    public static final String QUBIT_ENTITY_INTERNAL_NAME = "io/quarkiverse/qubit/runtime/QubitEntity";
    public static final String QUBIT_REPOSITORY_INTERNAL_NAME = "io/quarkiverse/qubit/runtime/QubitRepository";
    public static final String QUBIT_STREAM_INTERNAL_NAME = "io/quarkiverse/qubit/runtime/QubitStream";
    public static final String JOIN_STREAM_INTERNAL_NAME = "io/quarkiverse/qubit/runtime/JoinStream";
    public static final String GROUP_STREAM_INTERNAL_NAME = "io/quarkiverse/qubit/runtime/GroupStream";
    public static final String QUERY_SPEC_DESCRIPTOR = "Lio/quarkiverse/qubit/runtime/QuerySpec;";
    public static final String BI_QUERY_SPEC_DESCRIPTOR = "Lio/quarkiverse/qubit/runtime/BiQuerySpec;";
    public static final String GROUP_QUERY_SPEC_DESCRIPTOR = "Lio/quarkiverse/qubit/runtime/GroupQuerySpec;";

    // Standard Java method names for lambda expression analysis
    public static final String METHOD_DOUBLE_VALUE = "doubleValue";
    public static final String METHOD_INT_VALUE = "intValue";
    public static final String METHOD_LONG_VALUE = "longValue";
    public static final String METHOD_FLOAT_VALUE = "floatValue";
    public static final String METHOD_BOOLEAN_VALUE = "booleanValue";
    public static final String METHOD_BYTE_VALUE = "byteValue";
    public static final String METHOD_SHORT_VALUE = "shortValue";
    public static final String METHOD_CHAR_VALUE = "charValue";
    public static final String METHOD_ADD = "add";
    public static final String METHOD_SUBTRACT = "subtract";
    public static final String METHOD_MULTIPLY = "multiply";
    public static final String METHOD_DIVIDE = "divide";
    public static final String METHOD_STARTS_WITH = "startsWith";
    public static final String METHOD_ENDS_WITH = "endsWith";
    public static final String METHOD_CONTAINS = "contains";

    /**
     * String methods that generate LIKE patterns (startsWith, endsWith, contains).
     */
    public static final Set<String> STRING_PATTERN_METHOD_NAMES = Set.of(
        METHOD_STARTS_WITH, METHOD_ENDS_WITH, METHOD_CONTAINS
    );

    public static final String METHOD_TO_LOWER_CASE = "toLowerCase";
    public static final String METHOD_TO_UPPER_CASE = "toUpperCase";
    public static final String METHOD_TRIM = "trim";
    public static final String METHOD_EQUALS = "equals";
    public static final String METHOD_LENGTH = "length";
    public static final String METHOD_IS_EMPTY = "isEmpty";
    public static final String METHOD_SUBSTRING = "substring";
    public static final String METHOD_GET_YEAR = "getYear";
    public static final String METHOD_GET_MONTH_VALUE = "getMonthValue";
    public static final String METHOD_GET_DAY_OF_MONTH = "getDayOfMonth";
    public static final String METHOD_GET_HOUR = "getHour";
    public static final String METHOD_GET_MINUTE = "getMinute";
    public static final String METHOD_GET_SECOND = "getSecond";
    public static final String METHOD_COMPARE_TO = "compareTo";
    public static final String METHOD_VALUE_OF = "valueOf";
    public static final String METHOD_OF = "of";

    /**
     * {@link jakarta.persistence.criteria.CriteriaBuilder} method names for JPA Criteria API bytecode generation.
     *
     * <p>These constants map to {@code CriteriaBuilder} methods used to construct type-safe JPA queries.
     * The "CB_" prefix stands for "CriteriaBuilder" and helps identify these constants at a glance.
     *
     * <p><b>Boolean Operations:</b>
     * <ul>
     *   <li>{@link #CB_IS_TRUE} - Tests if expression is true</li>
     *   <li>{@link #CB_IS_FALSE} - Tests if expression is false</li>
     *   <li>{@link #CB_IS_NULL} - Tests if expression is null</li>
     *   <li>{@link #CB_IS_NOT_NULL} - Tests if expression is not null</li>
     *   <li>{@link #CB_NOT} - Logical negation</li>
     * </ul>
     *
     * <p><b>Comparison Operations:</b>
     * <ul>
     *   <li>{@link #CB_EQUAL} - Equality comparison</li>
     *   <li>{@link #CB_NOT_EQUAL} - Inequality comparison</li>
     *   <li>{@link #CB_GREATER_THAN} - Greater than comparison</li>
     *   <li>{@link #CB_GREATER_THAN_OR_EQUAL_TO} - Greater than or equal comparison</li>
     *   <li>{@link #CB_LESS_THAN} - Less than comparison</li>
     *   <li>{@link #CB_LESS_THAN_OR_EQUAL_TO} - Less than or equal comparison</li>
     * </ul>
     *
     * <p><b>Arithmetic Operations:</b>
     * <ul>
     *   <li>{@link #CB_SUM} - Addition</li>
     *   <li>{@link #CB_DIFF} - Subtraction (difference)</li>
     *   <li>{@link #CB_PROD} - Multiplication (product)</li>
     *   <li>{@link #CB_QUOT} - Division (quotient)</li>
     *   <li>{@link #CB_MOD} - Modulo</li>
     * </ul>
     *
     * <p><b>Logical Operations:</b>
     * <ul>
     *   <li>{@link #CB_AND} - Logical AND</li>
     *   <li>{@link #CB_OR} - Logical OR</li>
     * </ul>
     *
     * <p><b>String Operations:</b>
     * <ul>
     *   <li>{@link #CB_LOWER} - Convert to lowercase</li>
     *   <li>{@link #CB_UPPER} - Convert to uppercase</li>
     *   <li>{@link #CB_TRIM} - Trim whitespace</li>
     *   <li>{@link #CB_LIKE} - SQL LIKE pattern matching</li>
     *   <li>{@link #CB_SUBSTRING} - Extract substring</li>
     *   <li>{@link #CB_LENGTH} - String length</li>
     * </ul>
     *
     * <p><b>Utility Operations:</b>
     * <ul>
     *   <li>{@link #CB_FUNCTION} - Invoke database function</li>
     *   <li>{@link #CB_LITERAL} - Wrap value as literal expression</li>
     * </ul>
     *
     * @see jakarta.persistence.criteria.CriteriaBuilder
     */
    public static final String CB_IS_TRUE = "isTrue";
    public static final String CB_IS_FALSE = "isFalse";
    public static final String CB_IS_NULL = "isNull";
    public static final String CB_IS_NOT_NULL = "isNotNull";
    public static final String CB_EQUAL = "equal";
    public static final String CB_NOT_EQUAL = "notEqual";
    public static final String CB_NOT = "not";
    public static final String CB_SUM = "sum";
    public static final String CB_DIFF = "diff";
    public static final String CB_PROD = "prod";
    public static final String CB_QUOT = "quot";
    public static final String CB_MOD = "mod";
    public static final String CB_GREATER_THAN = "greaterThan";
    public static final String CB_GREATER_THAN_OR_EQUAL_TO = "greaterThanOrEqualTo";
    public static final String CB_LESS_THAN = "lessThan";
    public static final String CB_LESS_THAN_OR_EQUAL_TO = "lessThanOrEqualTo";
    public static final String CB_AND = "and";
    public static final String CB_OR = "or";
    public static final String CB_FUNCTION = "function";
    public static final String CB_LOWER = "lower";
    public static final String CB_UPPER = "upper";
    public static final String CB_TRIM = "trim";
    public static final String CB_LIKE = "like";
    public static final String CB_SUBSTRING = "substring";
    public static final String CB_LENGTH = "length";
    public static final String CB_LITERAL = "literal";

    // CriteriaBuilder IN and MEMBER OF operations
    public static final String CB_IN = "in";
    public static final String CB_IS_MEMBER = "isMember";
    public static final String CB_IS_NOT_MEMBER = "isNotMember";

    // CriteriaBuilder aggregation operations
    public static final String CB_AVG = "avg";
    public static final String CB_COUNT_DISTINCT = "countDistinct";
    public static final String CB_MIN = "min";
    public static final String CB_MAX = "max";
    public static final String CB_LEAST = "least";
    public static final String CB_GREATEST = "greatest";

    // CriteriaQuery GROUP BY operations
    public static final String CQ_GROUP_BY = "groupBy";
    public static final String CQ_HAVING = "having";

    // CriteriaBuilder subquery operations
    public static final String CB_EXISTS = "exists";
    public static final String CB_NOT_EXISTS = "not"; // cb.not(cb.exists(subquery))
    public static final String CQ_SUBQUERY = "subquery"; // criteriaQuery.subquery()

    // Path method names (for JPA Path API)
    public static final String PATH_GET = "get";

    // String class method names
    public static final String STRING_CONCAT = "concat";

    // SQL function names (used with CriteriaBuilder.function)
    public static final String SQL_YEAR = "YEAR";
    public static final String SQL_MONTH = "MONTH";
    public static final String SQL_DAY = "DAY";
    public static final String SQL_HOUR = "HOUR";
    public static final String SQL_MINUTE = "MINUTE";
    public static final String SQL_SECOND = "SECOND";

    // Method name prefixes (for getter/accessor detection)
    public static final String PREFIX_GET = "get";
    public static final String PREFIX_IS = "is";

    // JPA/EntityManager method names (for bytecode generation)
    public static final String EM_GET_CRITERIA_BUILDER = "getCriteriaBuilder";
    public static final String EM_CREATE_QUERY = "createQuery";
    public static final String CQ_FROM = "from";
    public static final String CQ_SELECT = "select";
    public static final String CQ_WHERE = "where";
    public static final String CB_COUNT = "count";
    public static final String TQ_GET_RESULT_LIST = "getResultList";
    public static final String TQ_GET_SINGLE_RESULT = "getSingleResult";

    // QueryExecutor method names
    public static final String QE_EXECUTE = "execute";

    // Constructor method name
    public static final String CONSTRUCTOR = "<init>";

    // Captured variable field name patterns (compiler-specific)
    /**
     * Field name prefix for captured variables in lambda instances (javac/OpenJDK).
     * The Java compiler generates synthetic fields with names: arg$1, arg$2, arg$3, etc.
     */
    public static final String CAPTURED_VAR_PREFIX_JAVAC = "arg$";

    /**
     * Field name prefix for captured variables in lambda instances (Eclipse JDT).
     * Eclipse compiler uses: val$1, val$2, val$3, etc.
     */
    public static final String CAPTURED_VAR_PREFIX_ECLIPSE = "val$";

    // ========================================================================
    // JVM Internal Class Names (CS-001: Extracted magic strings)
    // ========================================================================

    /**
     * JVM internal class names for bytecode analysis.
     *
     * <p>These constants use the JVM internal format (slash-separated) as required
     * by ASM for bytecode analysis. They are used to identify class types when
     * processing method invocations.
     *
     * <p><b>Naming Convention:</b>
     * <ul>
     *   <li><b>JVM_*</b> - JVM internal name format (slash-separated)</li>
     *   <li><b>JVM_PREFIX_*</b> - Prefix for pattern matching (e.g., startsWith checks)</li>
     *   <li><b>COLLECTION_INTERFACE_OWNERS</b> - Set of collection types for contains() detection</li>
     * </ul>
     */

    // Java standard library class internal names
    public static final String JVM_JAVA_LANG_STRING = "java/lang/String";
    public static final String JVM_JAVA_LANG_BOOLEAN = "java/lang/Boolean";
    public static final String JVM_JAVA_MATH_BIG_DECIMAL = "java/math/BigDecimal";

    // Temporal class internal names
    public static final String JVM_JAVA_TIME_LOCAL_DATE = "java/time/LocalDate";
    public static final String JVM_JAVA_TIME_LOCAL_DATE_TIME = "java/time/LocalDateTime";
    public static final String JVM_JAVA_TIME_LOCAL_TIME = "java/time/LocalTime";

    /**
     * Prefix for temporal class internal names (used for startsWith checks).
     */
    public static final String JVM_PREFIX_JAVA_TIME_LOCAL = "java/time/Local";

    // Collection interface internal names
    public static final String JVM_JAVA_UTIL_COLLECTION = "java/util/Collection";
    public static final String JVM_JAVA_UTIL_LIST = "java/util/List";
    public static final String JVM_JAVA_UTIL_SET = "java/util/Set";
    public static final String JVM_JAVA_UTIL_ABSTRACT_COLLECTION = "java/util/AbstractCollection";
    public static final String JVM_JAVA_UTIL_ABSTRACT_LIST = "java/util/AbstractList";
    public static final String JVM_JAVA_UTIL_ABSTRACT_SET = "java/util/AbstractSet";
    public static final String JVM_JAVA_UTIL_ARRAY_LIST = "java/util/ArrayList";
    public static final String JVM_JAVA_UTIL_LINKED_LIST = "java/util/LinkedList";
    public static final String JVM_JAVA_UTIL_HASH_SET = "java/util/HashSet";
    public static final String JVM_JAVA_UTIL_TREE_SET = "java/util/TreeSet";
    public static final String JVM_JAVA_UTIL_LINKED_HASH_SET = "java/util/LinkedHashSet";

    /**
     * Collection interface types that support contains() for IN/MEMBER OF detection.
     *
     * <p>This set includes all standard Java collection interfaces and their common
     * implementations that support the {@code contains(Object)} method. Used in
     * bytecode analysis to detect IN clause and MEMBER OF patterns.
     */
    public static final Set<String> COLLECTION_INTERFACE_OWNERS = Set.of(
            JVM_JAVA_UTIL_COLLECTION,
            JVM_JAVA_UTIL_LIST,
            JVM_JAVA_UTIL_SET,
            JVM_JAVA_UTIL_ABSTRACT_COLLECTION,
            JVM_JAVA_UTIL_ABSTRACT_LIST,
            JVM_JAVA_UTIL_ABSTRACT_SET,
            JVM_JAVA_UTIL_ARRAY_LIST,
            JVM_JAVA_UTIL_LINKED_LIST,
            JVM_JAVA_UTIL_HASH_SET,
            JVM_JAVA_UTIL_TREE_SET,
            JVM_JAVA_UTIL_LINKED_HASH_SET
    );

    // ========================================================================
    // JVM Bootstrap Method Factory Class Names (MAINT-004: Constants Consolidation)
    // ========================================================================

    /**
     * StringConcatFactory bootstrap method owner for Java 9+ string concatenation.
     * <p>
     * Java 9+ compiles string concatenation using invokedynamic with StringConcatFactory
     * as the bootstrap method, not traditional StringBuilder bytecode.
     */
    public static final String JVM_JAVA_LANG_INVOKE_STRING_CONCAT_FACTORY =
            "java/lang/invoke/StringConcatFactory";

    /**
     * LambdaMetafactory bootstrap method owner for lambda expressions.
     * <p>
     * Used to detect nested lambda creation for group aggregations and subqueries
     * (e.g., g.avg(p -> p.salary) or subquery(Person.class).avg(q -> q.salary)).
     */
    public static final String JVM_JAVA_LANG_INVOKE_LAMBDA_METAFACTORY =
            "java/lang/invoke/LambdaMetafactory";

    // ========================================================================
    // Qubit Runtime Implementation Class Names (MAINT-004: Constants Consolidation)
    // ========================================================================

    /**
     * QubitStreamImpl internal name for bytecode generation.
     * <p>
     * Used when generating fluent API entry point methods that return QubitStreamImpl.
     */
    public static final String QUBIT_STREAM_IMPL_INTERNAL_NAME =
            "io/quarkiverse/qubit/runtime/QubitStreamImpl";
}
