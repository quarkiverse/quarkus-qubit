package io.quarkiverse.qubit.deployment.analysis;

import static io.quarkiverse.qubit.deployment.common.ExceptionMessages.LAMBDA_HASH_REQUIRED;
import static io.quarkiverse.qubit.deployment.common.ExceptionMessages.QUERY_ID_REQUIRED;
import static io.quarkiverse.qubit.runtime.internal.QubitConstants.HASH_CHARS_FOR_CLASS_NAME;

/**
 * Parameter Object consolidating query executor generation metadata.
 * Reduces method parameter counts from 17+ to 3-4 parameters.
 */
public record QueryExecutionPlan(
        String lambdaHash,
        String queryId,
        String entityClassName,
        String terminalMethodName,
        boolean hasDistinct,
        Integer skipValue,
        Integer limitValue,
        int capturedVarCount) {

    public static Builder builder() {
        return new Builder();
    }

    /** Generates fully qualified class name using hash prefix for collision-free naming. */
    public String generateClassName(String targetPackage, String classNamePrefix) {
        return targetPackage + "." + classNamePrefix + lambdaHash.substring(0, HASH_CHARS_FOR_CLASS_NAME);
    }

    /** Fluent builder for QueryExecutionPlan. */
    public static final class Builder {
        private String lambdaHash;
        private String queryId;
        private String entityClassName;
        private String terminalMethodName;
        private boolean hasDistinct;
        private Integer skipValue;
        private Integer limitValue;
        private int capturedVarCount;

        private Builder() {
        }

        public Builder lambdaHash(String lambdaHash) {
            this.lambdaHash = lambdaHash;
            return this;
        }

        public Builder queryId(String queryId) {
            this.queryId = queryId;
            return this;
        }

        public Builder entityClassName(String entityClassName) {
            this.entityClassName = entityClassName;
            return this;
        }

        public Builder terminalMethodName(String terminalMethodName) {
            this.terminalMethodName = terminalMethodName;
            return this;
        }

        public Builder hasDistinct(boolean hasDistinct) {
            this.hasDistinct = hasDistinct;
            return this;
        }

        public Builder skipValue(Integer skipValue) {
            this.skipValue = skipValue;
            return this;
        }

        public Builder limitValue(Integer limitValue) {
            this.limitValue = limitValue;
            return this;
        }

        public Builder capturedVarCount(int capturedVarCount) {
            this.capturedVarCount = capturedVarCount;
            return this;
        }

        public QueryExecutionPlan build() {
            if (lambdaHash == null || lambdaHash.isEmpty()) {
                throw new IllegalStateException(LAMBDA_HASH_REQUIRED);
            }
            if (queryId == null || queryId.isEmpty()) {
                throw new IllegalStateException(QUERY_ID_REQUIRED);
            }
            return new QueryExecutionPlan(
                    lambdaHash, queryId, entityClassName, terminalMethodName,
                    hasDistinct, skipValue, limitValue, capturedVarCount);
        }
    }
}
