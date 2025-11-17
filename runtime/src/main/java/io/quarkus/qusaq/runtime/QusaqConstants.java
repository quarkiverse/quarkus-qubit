package io.quarkus.qusaq.runtime;

import java.util.Set;

/**
 * Shared constants for the Qusaq extension.
 */
public final class QusaqConstants {

    private QusaqConstants() {
    }

    // Query method names
    public static final String METHOD_FIND_WHERE = "findWhere";
    public static final String METHOD_COUNT_WHERE = "countWhere";
    public static final String METHOD_EXISTS = "exists";
    public static final String METHOD_IS_AFTER = "isAfter";
    public static final String METHOD_IS_BEFORE = "isBefore";
    public static final String METHOD_IS_EQUAL = "isEqual";

    public static final Set<String> QUERY_METHOD_NAMES = Set.of(
        METHOD_FIND_WHERE, METHOD_COUNT_WHERE, METHOD_EXISTS
    );

    public static final Set<String> TEMPORAL_COMPARISON_METHOD_NAMES = Set.of(
        METHOD_IS_AFTER, METHOD_IS_BEFORE, METHOD_IS_EQUAL
    );

    public static final Set<String> COUNT_QUERY_METHOD_NAMES = Set.of(
        METHOD_COUNT_WHERE, METHOD_EXISTS
    );

    public static final String QUSAQ_ENTITY_CLASS_NAME = "io.quarkus.qusaq.runtime.QusaqEntity";
    public static final String QUSAQ_REPOSITORY_CLASS_NAME = "io.quarkus.qusaq.runtime.QusaqRepository";
    public static final String QUERY_EXECUTOR_CLASS_NAME = "io.quarkus.qusaq.runtime.QueryExecutor";

    // JVM internal names (slash-separated format for ASM bytecode generation)
    public static final String QUSAQ_OPERATIONS_INTERNAL_NAME = "io/quarkus/qusaq/runtime/QusaqOperations";
    public static final String QUSAQ_ENTITY_INTERNAL_NAME = "io/quarkus/qusaq/runtime/QusaqEntity";
    public static final String QUSAQ_REPOSITORY_INTERNAL_NAME = "io/quarkus/qusaq/runtime/QusaqRepository";
    public static final String QUERY_SPEC_DESCRIPTOR = "Lio/quarkus/qusaq/runtime/QuerySpec;";

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

    // CriteriaBuilder method names (for JPA Criteria API bytecode generation)
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
}
