package io.quarkiverse.qubit.it.testutil;

import java.util.Locale;

/**
 * Utility for detecting the current database kind at test runtime.
 * The db.kind system property is set by Maven profiles.
 */
public enum DatabaseKind {
    POSTGRESQL,
    MYSQL,
    MARIADB,
    ORACLE,
    MSSQL,
    DB2,
    H2,
    UNKNOWN;

    private static DatabaseKind current;

    /**
     * Get the current database kind from system property.
     * The db.kind property is set by Maven profiles and passed via surefire/failsafe.
     */
    public static DatabaseKind current() {
        if (current == null) {
            String dbKind = System.getProperty("db.kind", "postgresql");
            current = fromString(dbKind);
        }
        return current;
    }

    /**
     * Reset the cached current database kind (for testing purposes).
     */
    public static void resetCurrent() {
        current = null;
    }

    /**
     * Parse a database kind from string value.
     */
    public static DatabaseKind fromString(String value) {
        if (value == null) {
            return UNKNOWN;
        }
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "postgresql", "postgres" -> POSTGRESQL;
            case "mysql" -> MYSQL;
            case "mariadb" -> MARIADB;
            case "oracle" -> ORACLE;
            case "mssql", "sqlserver" -> MSSQL;
            case "db2" -> DB2;
            case "h2" -> H2;
            default -> UNKNOWN;
        };
    }

    /**
     * Check if current database is PostgreSQL.
     */
    public boolean isPostgreSQL() {
        return this == POSTGRESQL;
    }

    /**
     * Check if current database is MySQL.
     */
    public boolean isMySQL() {
        return this == MYSQL;
    }

    /**
     * Check if current database is MariaDB.
     */
    public boolean isMariaDB() {
        return this == MARIADB;
    }

    /**
     * Check if current database is MySQL or MariaDB (MySQL family).
     */
    public boolean isMySQLFamily() {
        return this == MYSQL || this == MARIADB;
    }

    /**
     * Check if current database is Oracle.
     */
    public boolean isOracle() {
        return this == ORACLE;
    }

    /**
     * Check if current database is Microsoft SQL Server.
     */
    public boolean isMSSQL() {
        return this == MSSQL;
    }

    /**
     * Check if current database is DB2.
     */
    public boolean isDB2() {
        return this == DB2;
    }

    /**
     * Check if current database is H2.
     */
    public boolean isH2() {
        return this == H2;
    }

    /**
     * Check if current database supports native compilation.
     * H2 doesn't support GraalVM native compilation.
     */
    public boolean supportsNative() {
        return this != H2;
    }

    /**
     * Get the Quarkus configuration profile name for this database.
     */
    public String getQuarkusProfile() {
        return switch (this) {
            case POSTGRESQL -> "postgresql";
            case MYSQL -> "mysql";
            case MARIADB -> "mariadb";
            case ORACLE -> "oracle";
            case MSSQL -> "mssql";
            case DB2 -> "db2";
            case H2 -> "h2";
            case UNKNOWN -> "postgresql"; // Default fallback
        };
    }
}
