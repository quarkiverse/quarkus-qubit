package io.quarkiverse.qubit.runtime.internal;

import java.util.Set;

/** Shared constants: CB_* (CriteriaBuilder), METHOD_* (lambda analysis), SQL_*, PREFIX_* (accessors). */
public final class QubitConstants {

    private QubitConstants() {
    }

    // Fluent API - Entry point methods (static methods on QubitEntity)
    public static final String METHOD_WHERE = "where";
    public static final String METHOD_SELECT = "select";
    public static final String METHOD_SORTED_BY = "sortedBy";
    public static final String METHOD_SORTED_DESCENDING_BY = "sortedDescendingBy";
    public static final String METHOD_THEN_SORTED_BY = "thenSortedBy";
    public static final String METHOD_THEN_SORTED_DESCENDING_BY = "thenSortedDescendingBy";

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

    public static final Set<String> AGGREGATION_METHOD_NAMES = Set.of(
            METHOD_MIN, METHOD_MAX, METHOD_AVG,
            METHOD_SUM_INTEGER, METHOD_SUM_LONG, METHOD_SUM_DOUBLE);

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
            METHOD_SUM_INTEGER, METHOD_SUM_LONG, METHOD_SUM_DOUBLE);

    // All fluent API intermediate operations
    public static final Set<String> FLUENT_INTERMEDIATE_METHODS = Set.of(
            METHOD_WHERE, METHOD_SELECT, METHOD_SORTED_BY, METHOD_SORTED_DESCENDING_BY,
            METHOD_THEN_SORTED_BY, METHOD_THEN_SORTED_DESCENDING_BY,
            METHOD_SKIP, METHOD_LIMIT, METHOD_DISTINCT,
            METHOD_MIN, METHOD_MAX, METHOD_AVG,
            METHOD_SUM_INTEGER, METHOD_SUM_LONG, METHOD_SUM_DOUBLE);

    // All fluent API terminal operations
    public static final Set<String> FLUENT_TERMINAL_METHODS = Set.of(
            METHOD_TO_LIST, METHOD_COUNT, METHOD_EXISTS,
            METHOD_GET_SINGLE_RESULT, METHOD_FIND_FIRST);

    // Join method names
    public static final String METHOD_JOIN = "join";
    public static final String METHOD_LEFT_JOIN = "leftJoin";
    public static final String METHOD_ON = "on";
    public static final String METHOD_SELECT_SOURCE = "selectSource";
    public static final String METHOD_SELECT_JOINED = "selectJoined";

    public static final Set<String> JOIN_ENTRY_METHODS = Set.of(
            METHOD_JOIN, METHOD_LEFT_JOIN);

    // All join methods (for stack walking filter)
    public static final Set<String> JOIN_METHODS = Set.of(
            METHOD_JOIN, METHOD_LEFT_JOIN, METHOD_ON,
            METHOD_SELECT_SOURCE, METHOD_SELECT_JOINED);

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
            METHOD_GROUP_BY, METHOD_HAVING, METHOD_SELECT_KEY);

    // Group interface internal name
    public static final String GROUP_INTERNAL_NAME = "io/quarkiverse/qubit/Group";

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

    // Subqueries utility class internal name
    public static final String SUBQUERIES_INTERNAL_NAME = "io/quarkiverse/qubit/Subqueries";

    // SubqueryBuilder class internal name and descriptor
    public static final String SUBQUERY_BUILDER_INTERNAL_NAME = "io/quarkiverse/qubit/SubqueryBuilder";
    public static final String SUBQUERY_BUILDER_DESCRIPTOR = "Lio/quarkiverse/qubit/SubqueryBuilder;";

    // Subquery factory method name
    public static final String METHOD_SUBQUERY = "subquery";

    // Temporal comparison methods (used in lambda expressions for date/time comparisons)
    public static final String METHOD_IS_AFTER = "isAfter";
    public static final String METHOD_IS_BEFORE = "isBefore";
    public static final String METHOD_IS_EQUAL = "isEqual";

    public static final Set<String> TEMPORAL_COMPARISON_METHOD_NAMES = Set.of(
            METHOD_IS_AFTER, METHOD_IS_BEFORE, METHOD_IS_EQUAL);

    public static final String QUBIT_ENTITY_CLASS_NAME = "io.quarkiverse.qubit.QubitEntity";
    public static final String QUBIT_REPOSITORY_CLASS_NAME = "io.quarkiverse.qubit.QubitRepository";

    // JVM internal names (slash-separated format for ASM bytecode generation)
    public static final String QUBIT_ENTITY_INTERNAL_NAME = "io/quarkiverse/qubit/QubitEntity";
    public static final String QUBIT_REPOSITORY_INTERNAL_NAME = "io/quarkiverse/qubit/QubitRepository";
    public static final String QUBIT_STREAM_INTERNAL_NAME = "io/quarkiverse/qubit/QubitStream";
    public static final String SCALAR_RESULT_INTERNAL_NAME = "io/quarkiverse/qubit/ScalarResult";
    public static final String JOIN_STREAM_INTERNAL_NAME = "io/quarkiverse/qubit/JoinStream";
    public static final String GROUP_STREAM_INTERNAL_NAME = "io/quarkiverse/qubit/GroupStream";
    public static final String QUERY_SPEC_INTERNAL_NAME = "io/quarkiverse/qubit/QuerySpec";
    public static final String QUERY_SPEC_DESCRIPTOR = "Lio/quarkiverse/qubit/QuerySpec;";
    public static final String BI_QUERY_SPEC_DESCRIPTOR = "Lio/quarkiverse/qubit/BiQuerySpec;";
    public static final String GROUP_QUERY_SPEC_DESCRIPTOR = "Lio/quarkiverse/qubit/GroupQuerySpec;";

    // Fluent API method descriptors (QuerySpec -> Stream / ScalarResult)
    public static final String DESC_QUERY_SPEC_TO_STREAM = "(Lio/quarkiverse/qubit/QuerySpec;)Lio/quarkiverse/qubit/QubitStream;";
    public static final String DESC_QUERY_SPEC_TO_SCALAR_RESULT = "(Lio/quarkiverse/qubit/QuerySpec;)Lio/quarkiverse/qubit/ScalarResult;";
    public static final String DESC_QUERY_SPEC_TO_JOIN_STREAM = "(Lio/quarkiverse/qubit/QuerySpec;)Lio/quarkiverse/qubit/JoinStream;";
    public static final String DESC_QUERY_SPEC_TO_GROUP_STREAM = "(Lio/quarkiverse/qubit/QuerySpec;)Lio/quarkiverse/qubit/GroupStream;";

    // Standard Java method names for lambda expression analysis
    public static final String METHOD_ADD = "add";
    public static final String METHOD_SUBTRACT = "subtract";
    public static final String METHOD_MULTIPLY = "multiply";
    public static final String METHOD_DIVIDE = "divide";

    public static final Set<String> BIG_DECIMAL_ARITHMETIC_METHODS = Set.of(
            METHOD_ADD, METHOD_SUBTRACT, METHOD_MULTIPLY, METHOD_DIVIDE);

    // ─── Math Class Methods ──────────────────────────────────────────────────

    /** JVM internal name for java.lang.Math. */
    public static final String JVM_JAVA_LANG_MATH = "java/lang/Math";

    /** JVM internal name for java.lang.Integer. */
    public static final String JVM_JAVA_LANG_INTEGER = "java/lang/Integer";

    /** JVM internal name for java.lang.Long. */
    public static final String JVM_JAVA_LANG_LONG = "java/lang/Long";

    // Math method names
    public static final String METHOD_ABS = "abs";
    public static final String METHOD_SQRT = "sqrt";
    public static final String METHOD_CEIL = "ceil";
    public static final String METHOD_FLOOR = "floor";
    public static final String METHOD_EXP = "exp";
    public static final String METHOD_LOG = "log";
    public static final String METHOD_POW = "pow";
    public static final String METHOD_ROUND = "round";
    public static final String METHOD_SIGNUM = "signum";

    /** Math unary methods that map to JPA CriteriaBuilder unary functions. */
    public static final Set<String> MATH_UNARY_METHODS = Set.of(
            METHOD_ABS, METHOD_SQRT, METHOD_CEIL, METHOD_FLOOR,
            METHOD_EXP, METHOD_LOG, METHOD_SIGNUM);

    /** Math binary methods that map to JPA CriteriaBuilder binary functions. */
    public static final Set<String> MATH_BINARY_METHODS = Set.of(METHOD_POW);

    /** Owners that have signum() static method. */
    public static final Set<String> SIGNUM_OWNERS = Set.of(
            JVM_JAVA_LANG_MATH, JVM_JAVA_LANG_INTEGER, JVM_JAVA_LANG_LONG);

    /** JVM internal name for QubitMath marker class. */
    public static final String JVM_QUBIT_MATH = "io/quarkiverse/qubit/QubitMath";

    /** JVM internal name for Qubit marker class. */
    public static final String JVM_QUBIT = "io/quarkiverse/qubit/Qubit";

    // Qubit marker method names
    public static final String METHOD_LIKE = "like";
    public static final String METHOD_NOT_LIKE = "notLike";

    public static final String METHOD_STARTS_WITH = "startsWith";
    public static final String METHOD_ENDS_WITH = "endsWith";
    public static final String METHOD_CONTAINS = "contains";

    public static final Set<String> STRING_PATTERN_METHOD_NAMES = Set.of(
            METHOD_STARTS_WITH, METHOD_ENDS_WITH, METHOD_CONTAINS);

    public static final String METHOD_TO_LOWER_CASE = "toLowerCase";
    public static final String METHOD_TO_UPPER_CASE = "toUpperCase";
    public static final String METHOD_TRIM = "trim";
    public static final String METHOD_EQUALS = "equals";
    public static final String METHOD_EQUALS_IGNORE_CASE = "equalsIgnoreCase";
    public static final String METHOD_LENGTH = "length";
    public static final String METHOD_IS_EMPTY = "isEmpty";
    public static final String METHOD_IS_BLANK = "isBlank";
    public static final String METHOD_SUBSTRING = "substring";
    public static final String METHOD_INDEX_OF = "indexOf";

    public static final Set<String> STRING_TRANSFORMATION_METHODS = Set.of(
            METHOD_TO_LOWER_CASE, METHOD_TO_UPPER_CASE, METHOD_TRIM);

    public static final Set<String> STRING_UTILITY_METHODS = Set.of(
            METHOD_EQUALS, METHOD_LENGTH, METHOD_IS_EMPTY, METHOD_IS_BLANK);
    public static final String METHOD_GET_YEAR = "getYear";
    public static final String METHOD_GET_MONTH_VALUE = "getMonthValue";
    public static final String METHOD_GET_DAY_OF_MONTH = "getDayOfMonth";
    public static final String METHOD_GET_HOUR = "getHour";
    public static final String METHOD_GET_MINUTE = "getMinute";
    public static final String METHOD_GET_SECOND = "getSecond";

    public static final Set<String> LOCAL_DATE_ACCESSOR_METHODS = Set.of(
            METHOD_GET_YEAR, METHOD_GET_MONTH_VALUE, METHOD_GET_DAY_OF_MONTH);

    public static final Set<String> LOCAL_DATE_TIME_ACCESSOR_METHODS = Set.of(
            METHOD_GET_YEAR, METHOD_GET_MONTH_VALUE, METHOD_GET_DAY_OF_MONTH,
            METHOD_GET_HOUR, METHOD_GET_MINUTE, METHOD_GET_SECOND);

    public static final Set<String> LOCAL_TIME_ACCESSOR_METHODS = Set.of(
            METHOD_GET_HOUR, METHOD_GET_MINUTE, METHOD_GET_SECOND);

    public static final String METHOD_COMPARE_TO = "compareTo";
    public static final String METHOD_VALUE_OF = "valueOf";
    public static final String METHOD_OF = "of";

    // CriteriaBuilder method names
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

    // CriteriaQuery GROUP BY operations
    public static final String CQ_GROUP_BY = "groupBy";
    public static final String CQ_HAVING = "having";

    // CriteriaBuilder subquery operations
    public static final String CB_EXISTS = "exists";

    // Path method names (for JPA Path API)
    public static final String PATH_GET = "get";

    // String class method names
    public static final String STRING_CONCAT = "concat";

    public static final String SQL_LIKE_WILDCARD = "%";

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

    // JVM Internal Class Names (slash-separated for ASM bytecode analysis)
    public static final String JVM_JAVA_LANG_OBJECT = "java/lang/Object";
    public static final String JVM_JAVA_LANG_STRING = "java/lang/String";
    public static final String JVM_JAVA_LANG_BOOLEAN = "java/lang/Boolean";
    public static final String JVM_JAVA_MATH_BIG_DECIMAL = "java/math/BigDecimal";

    // Temporal class internal names
    public static final String JVM_JAVA_TIME_LOCAL_DATE = "java/time/LocalDate";
    public static final String JVM_JAVA_TIME_LOCAL_DATE_TIME = "java/time/LocalDateTime";
    public static final String JVM_JAVA_TIME_LOCAL_TIME = "java/time/LocalTime";

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

    /** Collection types that support contains() for IN/MEMBER OF detection. */
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
            JVM_JAVA_UTIL_LINKED_HASH_SET);

    // Aggregation type identifiers for build-time to runtime communication
    public static final String AGG_TYPE_MIN = "MIN";
    public static final String AGG_TYPE_MAX = "MAX";
    public static final String AGG_TYPE_AVG = "AVG";
    public static final String AGG_TYPE_SUM_INTEGER = "SUM_INTEGER";
    public static final String AGG_TYPE_SUM_LONG = "SUM_LONG";
    public static final String AGG_TYPE_SUM_DOUBLE = "SUM_DOUBLE";

    // Query type identifiers for hash computation and deduplication
    public static final String QUERY_TYPE_LIST = "LIST";
    public static final String QUERY_TYPE_COUNT = "COUNT";
    public static final String QUERY_TYPE_PROJECTION = "PROJECTION";
    public static final String QUERY_TYPE_COMBINED = "COMBINED";

    // JVM Bootstrap Method Factory Class Names
    public static final String JVM_JAVA_LANG_INVOKE_STRING_CONCAT_FACTORY = "java/lang/invoke/StringConcatFactory";
    public static final String JVM_JAVA_LANG_INVOKE_LAMBDA_METAFACTORY = "java/lang/invoke/LambdaMetafactory";

    // Qubit Runtime Implementation Class Names
    public static final String QUBIT_STREAM_IMPL_INTERNAL_NAME = "io/quarkiverse/qubit/runtime/internal/QubitStreamImpl";
    public static final String JOIN_STREAM_IMPL_INTERNAL_NAME = "io/quarkiverse/qubit/runtime/internal/JoinStreamImpl";
    public static final String JOIN_TYPE_INTERNAL_NAME = "io/quarkiverse/qubit/JoinType";
    public static final String JOIN_TYPE_DESCRIPTOR = "Lio/quarkiverse/qubit/JoinType;";

    // Lambda Hash Constants
    public static final int HASH_CHARS_FOR_CLASS_NAME = 16; // 64 bits entropy for class names
    public static final int HASH_CHARS_FOR_LOG = 8; // Compact identifier for logs
    public static final char QUERY_ID_SEPARATOR = '#'; // Class#method$lambda$1
    public static final char LAMBDA_SUFFIX_MARKER = '$';
}
