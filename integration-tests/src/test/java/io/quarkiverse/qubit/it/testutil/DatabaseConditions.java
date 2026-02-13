package io.quarkiverse.qubit.it.testutil;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * Custom JUnit 5 conditions for database-specific test execution.
 * Use these annotations to conditionally run tests based on the current database.
 *
 * <p>
 * Example usage:
 * </p>
 *
 * <pre>
 * {@code
 * &#64;Test
 * &#64;PostgreSQLOnly(reason = "Tests PostgreSQL-specific JSONB functionality")
 * void testPostgreSQLJsonOperations() {
 *     // PostgreSQL-specific test
 * }
 *
 * &#64;Test
 * @NotH2(reason = "H2 doesn't support this SQL syntax")
 * void testComplexSQLFeature() {
 *     // Test that doesn't work on H2
 * }
 * }
 * </pre>
 */
public class DatabaseConditions {

    // ANNOTATIONS

    /**
     * Annotation to run a test only on PostgreSQL.
     */
    @Target({ ElementType.TYPE, ElementType.METHOD })
    @Retention(RetentionPolicy.RUNTIME)
    @ExtendWith(PostgreSQLOnlyCondition.class)
    public @interface PostgreSQLOnly {
        String reason() default "This test only runs on PostgreSQL";
    }

    /**
     * Annotation to run a test only on MySQL.
     */
    @Target({ ElementType.TYPE, ElementType.METHOD })
    @Retention(RetentionPolicy.RUNTIME)
    @ExtendWith(MySQLOnlyCondition.class)
    public @interface MySQLOnly {
        String reason() default "This test only runs on MySQL";
    }

    /**
     * Annotation to run a test only on MySQL or MariaDB (MySQL family).
     */
    @Target({ ElementType.TYPE, ElementType.METHOD })
    @Retention(RetentionPolicy.RUNTIME)
    @ExtendWith(MySQLFamilyOnlyCondition.class)
    public @interface MySQLFamilyOnly {
        String reason() default "This test only runs on MySQL/MariaDB";
    }

    /**
     * Annotation to run a test only on Oracle.
     */
    @Target({ ElementType.TYPE, ElementType.METHOD })
    @Retention(RetentionPolicy.RUNTIME)
    @ExtendWith(OracleOnlyCondition.class)
    public @interface OracleOnly {
        String reason() default "This test only runs on Oracle";
    }

    /**
     * Annotation to skip a test on H2 (H2 has limitations).
     */
    @Target({ ElementType.TYPE, ElementType.METHOD })
    @Retention(RetentionPolicy.RUNTIME)
    @ExtendWith(NotH2Condition.class)
    public @interface NotH2 {
        String reason() default "This test doesn't run on H2";
    }

    /**
     * Annotation to run a test only on databases that support native compilation.
     */
    @Target({ ElementType.TYPE, ElementType.METHOD })
    @Retention(RetentionPolicy.RUNTIME)
    @ExtendWith(SupportsNativeCondition.class)
    public @interface RequiresNativeSupport {
        String reason() default "This test requires native compilation support";
    }

    // CONDITIONS

    /**
     * Condition that enables tests only on PostgreSQL.
     */
    public static class PostgreSQLOnlyCondition implements ExecutionCondition {
        @Override
        public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
            DatabaseKind current = DatabaseKind.current();
            if (current.isPostgreSQL()) {
                return ConditionEvaluationResult.enabled("Running on PostgreSQL");
            }
            return ConditionEvaluationResult.disabled(
                    "Skipped: current database is " + current + ", not PostgreSQL");
        }
    }

    /**
     * Condition that enables tests only on MySQL.
     */
    public static class MySQLOnlyCondition implements ExecutionCondition {
        @Override
        public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
            DatabaseKind current = DatabaseKind.current();
            if (current.isMySQL()) {
                return ConditionEvaluationResult.enabled("Running on MySQL");
            }
            return ConditionEvaluationResult.disabled(
                    "Skipped: current database is " + current + ", not MySQL");
        }
    }

    /**
     * Condition that enables tests only on MySQL or MariaDB.
     */
    public static class MySQLFamilyOnlyCondition implements ExecutionCondition {
        @Override
        public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
            DatabaseKind current = DatabaseKind.current();
            if (current.isMySQLFamily()) {
                return ConditionEvaluationResult.enabled("Running on MySQL/MariaDB family");
            }
            return ConditionEvaluationResult.disabled(
                    "Skipped: current database is " + current + ", not MySQL/MariaDB");
        }
    }

    /**
     * Condition that enables tests only on Oracle.
     */
    public static class OracleOnlyCondition implements ExecutionCondition {
        @Override
        public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
            DatabaseKind current = DatabaseKind.current();
            if (current.isOracle()) {
                return ConditionEvaluationResult.enabled("Running on Oracle");
            }
            return ConditionEvaluationResult.disabled(
                    "Skipped: current database is " + current + ", not Oracle");
        }
    }

    /**
     * Condition that disables tests on H2.
     */
    public static class NotH2Condition implements ExecutionCondition {
        @Override
        public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
            DatabaseKind current = DatabaseKind.current();
            if (!current.isH2()) {
                return ConditionEvaluationResult.enabled("Not running on H2");
            }
            return ConditionEvaluationResult.disabled(
                    "Skipped: current database is H2, which is not supported for this test");
        }
    }

    /**
     * Condition that enables tests only on databases that support native compilation.
     */
    public static class SupportsNativeCondition implements ExecutionCondition {
        @Override
        public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
            DatabaseKind current = DatabaseKind.current();
            if (current.supportsNative()) {
                return ConditionEvaluationResult.enabled("Database " + current + " supports native compilation");
            }
            return ConditionEvaluationResult.disabled(
                    "Skipped: database " + current + " does not support native compilation");
        }
    }
}
