package io.quarkiverse.qubit.runtime;

/**
 * Enumeration of sort directions for query ordering.
 * <p>
 * Used throughout both runtime and build-time processing to represent
 * ascending or descending sort orders in a type-safe manner.
 * <p>
 * Each direction includes a suffix string used for AST serialization in lambda deduplication
 * during build-time processing.
 */
public enum SortDirection {
    /**
     * Ascending order (smallest to largest, A-Z, oldest to newest).
     */
    ASCENDING(":ASC"),

    /**
     * Descending order (largest to smallest, Z-A, newest to oldest).
     */
    DESCENDING(":DESC");

    private final String suffix;

    SortDirection(String suffix) {
        this.suffix = suffix;
    }

    /**
     * Returns the string suffix used for AST serialization.
     * Used by the build-time lambda deduplicator to create unique identifiers for query patterns.
     *
     * @return suffix string (e.g., ":ASC" or ":DESC")
     */
    public String getSuffix() {
        return suffix;
    }
}
