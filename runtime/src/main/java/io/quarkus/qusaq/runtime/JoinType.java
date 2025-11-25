package io.quarkus.qusaq.runtime;

/**
 * Specifies the type of join operation.
 */
public enum JoinType {
    /**
     * Inner join - returns only rows where the join condition is met on both sides.
     */
    INNER,

    /**
     * Left outer join - returns all rows from the left (source) table,
     * with NULL values for non-matching right (joined) side.
     */
    LEFT
}
