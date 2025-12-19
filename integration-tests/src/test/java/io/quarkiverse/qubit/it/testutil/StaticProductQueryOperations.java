package io.quarkiverse.qubit.it.testutil;

import io.quarkiverse.qubit.it.Product;
import io.quarkiverse.qubit.runtime.QuerySpec;
import io.quarkiverse.qubit.runtime.QubitStream;

/**
 * Implementation of {@link ProductQueryOperations} using static entity methods.
 */
public class StaticProductQueryOperations implements ProductQueryOperations {

    public static final StaticProductQueryOperations INSTANCE = new StaticProductQueryOperations();

    private StaticProductQueryOperations() {
    }

    @Override
    public String getSourceName() {
        return "Static";
    }

    @Override
    public QubitStream<Product> where(QuerySpec<Product, Boolean> spec) {
        return Product.where(spec);
    }

    @Override
    public <R> QubitStream<R> select(QuerySpec<Product, R> mapper) {
        return Product.select(mapper);
    }

    @Override
    public <K extends Comparable<K>> QubitStream<Product> sortedBy(QuerySpec<Product, K> keyExtractor) {
        return Product.sortedBy(keyExtractor);
    }

    @Override
    public <K extends Comparable<K>> QubitStream<Product> sortedDescendingBy(QuerySpec<Product, K> keyExtractor) {
        return Product.sortedDescendingBy(keyExtractor);
    }
}
