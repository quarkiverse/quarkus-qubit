package io.quarkiverse.qubit.deployment.common;

/** Centralized exception message constants for consistent error reporting. */
public final class ExceptionMessages {

    private ExceptionMessages() {
    }

    // ========== Required Field Validation ==========

    public static final String LAMBDA_HASH_REQUIRED = "lambdaHash is required";

    public static final String QUERY_ID_REQUIRED = "queryId is required";

    public static final String GENERATED_CLASS_NAME_REQUIRED = "generatedClassName is required";

    public static final String CHARACTERISTICS_REQUIRED = "characteristics is required";

    // ========== Null Check Validation ==========

    // --- AST Node Validation (LambdaExpression records) ---
    public static final String FIELD_NAME_NULL = "Field name cannot be null";
    public static final String FIELD_TYPE_NULL = "Field type cannot be null";
    public static final String PARAMETER_NAME_NULL = "Parameter name cannot be null";
    public static final String PARAMETER_TYPE_NULL = "Parameter type cannot be null";
    public static final String RESULT_TYPE_NULL = "Result type cannot be null";
    public static final String ENTITY_POSITION_NULL = "Entity position cannot be null";
    public static final String ENTITY_CLASS_NULL = "Entity class cannot be null";
    public static final String ENTITY_TYPE_NULL = "Entity type cannot be null";
    public static final String AGGREGATION_TYPE_NULL = "Aggregation type cannot be null";

    // --- Analysis Outcome Validation ---
    public static final String CALL_SITE_ID_NULL = "Call site ID cannot be null";
    public static final String LAMBDA_HASH_NULL = "Lambda hash cannot be null";

    // --- Subquery Validation ---
    public static final String SCALAR_SUBQUERY_NULL = "ScalarSubquery cannot be null";

    public static final String EXISTS_SUBQUERY_NULL = "ExistsSubquery cannot be null";

    public static final String IN_SUBQUERY_NULL = "InSubquery cannot be null";

    public static final String FIELD_PATH_EXPRESSION_NULL = "Field path expression cannot be null";

    public static final String HANDLERS_CANNOT_BE_EMPTY = "handlers cannot be empty";

    public static final String CANNOT_COMBINE_EMPTY_PREDICATE_LIST = "Cannot combine empty predicate list";

    // ========== Control Flow ==========

    public static final String COUNT_SHOULD_BE_HANDLED_ABOVE = "COUNT should be handled above";

    public static final String NO_MORE_PARAMETERS = "No more parameters";

    public static final String CANNOT_GET_VALUE_FROM_NOT_APPLICABLE = "Cannot get value from NotApplicable result";

    // ========== Unexpected Type Messages ==========

    public static String unexpectedAggregationType(Object aggType) {
        return "Unexpected aggregation type: " + aggType;
    }

    public static String unexpectedGroupAggregationType(Object aggType) {
        return "Unexpected group aggregation type: " + aggType;
    }

    public static String unknownAggregationType(String aggregationType) {
        return "Unknown aggregation type: " + aggregationType;
    }

    public static String unexpectedBigDecimalMethod(String methodName) {
        return "Unexpected BigDecimal method: " + methodName;
    }

    public static String unexpectedLabelClassification(Object classification) {
        return "Unexpected label classification: " + classification;
    }

    public static String unsupportedFieldPathExpressionType(String expressionType) {
        return "Unsupported expression type for field path generation: " + expressionType
                + ". Expected FieldAccess or PathExpression.";
    }

    // ========== Operator Validation ==========

    public static String notArithmeticOperator(Object operator) {
        return "Not an arithmetic operator: " + operator;
    }

    public static String notComparisonOperator(Object operator) {
        return "Not a comparison operator: " + operator;
    }

    public static String expectedAndOrOperator(Object operator) {
        return "Expected AND or OR operator, got: " + operator;
    }

    // ========== Value Access Errors ==========

    public static String cannotGetValueFromUnsupported(String reason) {
        return "Cannot get value from Unsupported: " + reason;
    }

    // ========== Warning Suffixes ==========

    /** Common suffix for warnings about unhandled cases in switch expressions. */
    public static final String MISSING_CASE_HANDLER_HINT = "This may indicate a missing case handler.";
}
