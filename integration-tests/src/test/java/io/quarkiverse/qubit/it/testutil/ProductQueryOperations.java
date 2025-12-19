package io.quarkiverse.qubit.it.testutil;

import io.quarkiverse.qubit.it.Product;
import io.quarkiverse.qubit.runtime.QuerySpec;
import io.quarkiverse.qubit.runtime.QubitStream;

/**
 * Abstraction for Product query operations.
 *
 * @see PersonQueryOperations
 */
public interface ProductQueryOperations {

    /**
     * Returns a descriptive name for this query source.
     */
    String getSourceName();

    /**
     * Creates a filtered query stream.
     */
    QubitStream<Product> where(QuerySpec<Product, Boolean> spec);

    /**
     * Creates a projection query stream.
     */
    <R> QubitStream<R> select(QuerySpec<Product, R> mapper);

    /**
     * Creates a sorted query stream (ascending).
     */
    <K extends Comparable<K>> QubitStream<Product> sortedBy(QuerySpec<Product, K> keyExtractor);

    /**
     * Creates a sorted query stream (descending).
     */
    <K extends Comparable<K>> QubitStream<Product> sortedDescendingBy(QuerySpec<Product, K> keyExtractor);
}
