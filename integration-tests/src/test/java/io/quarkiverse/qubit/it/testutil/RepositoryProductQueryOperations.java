package io.quarkiverse.qubit.it.testutil;

import io.quarkiverse.qubit.it.Product;
import io.quarkiverse.qubit.it.ProductRepository;
import io.quarkiverse.qubit.QuerySpec;
import io.quarkiverse.qubit.QubitStream;

/**
 * Implementation of {@link ProductQueryOperations} using repository instance methods.
 */
public class RepositoryProductQueryOperations implements ProductQueryOperations {

    private final ProductRepository repository;

    public RepositoryProductQueryOperations(ProductRepository repository) {
        this.repository = repository;
    }

    @Override
    public String getSourceName() {
        return "Repository";
    }

    @Override
    public QubitStream<Product> where(QuerySpec<Product, Boolean> spec) {
        return repository.where(spec);
    }

    @Override
    public <R> QubitStream<R> select(QuerySpec<Product, R> mapper) {
        return repository.select(mapper);
    }

    @Override
    public <K extends Comparable<K>> QubitStream<Product> sortedBy(QuerySpec<Product, K> keyExtractor) {
        return repository.sortedBy(keyExtractor);
    }

    @Override
    public <K extends Comparable<K>> QubitStream<Product> sortedDescendingBy(QuerySpec<Product, K> keyExtractor) {
        return repository.sortedDescendingBy(keyExtractor);
    }
}
