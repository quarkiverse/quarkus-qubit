package io.quarkiverse.qubit.deployment.common;

/** Centralized exception message constants for consistent error reporting. */
public final class ExceptionMessages {

    private ExceptionMessages() {
    }

    public static final String LAMBDA_HASH_REQUIRED = "lambdaHash is required";

    public static final String QUERY_ID_REQUIRED = "queryId is required";

    // --- AST Node Validation (LambdaExpression records) ---
    public static final String FIELD_NAME_NULL = "Field name cannot be null";
    public static final String FIELD_TYPE_NULL = "Field type cannot be null";
    public static final String PARAMETER_NAME_NULL = "Parameter name cannot be null";
    public static final String PARAMETER_TYPE_NULL = "Parameter type cannot be null";
    public static final String RESULT_TYPE_NULL = "Result type cannot be null";
    public static final String ENTITY_POSITION_NULL = "Entity position cannot be null";
    public static final String ENTITY_CLASS_NULL = "Entity class cannot be null";
    public static final String AGGREGATION_TYPE_NULL = "Aggregation type cannot be null";

    // --- Analysis Outcome Validation ---
    public static final String CALL_SITE_ID_NULL = "Call site ID cannot be null";
    public static final String LAMBDA_HASH_NULL = "Lambda hash cannot be null";

    public static final String COUNT_SHOULD_BE_HANDLED_ABOVE = "COUNT should be handled above";

    public static String unexpectedAggregationType(Object aggType) {
        return "Unexpected aggregation type: " + aggType;
    }

    public static String unknownAggregationType(String aggregationType) {
        return "Unknown aggregation type: " + aggregationType;
    }

    /** Common suffix for warnings about unhandled cases in switch expressions. */
    public static final String MISSING_CASE_HANDLER_HINT = "This may indicate a missing case handler.";
}
