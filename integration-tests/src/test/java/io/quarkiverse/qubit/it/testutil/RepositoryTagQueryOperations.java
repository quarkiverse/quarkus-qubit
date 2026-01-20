package io.quarkiverse.qubit.it.testutil;

import io.quarkiverse.qubit.it.Tag;
import io.quarkiverse.qubit.it.TagRepository;
import io.quarkiverse.qubit.QuerySpec;
import io.quarkiverse.qubit.QubitStream;

/**
 * Implementation of {@link TagQueryOperations} using repository instance methods.
 */
public class RepositoryTagQueryOperations implements TagQueryOperations {

    private final TagRepository repository;

    public RepositoryTagQueryOperations(TagRepository repository) {
        this.repository = repository;
    }

    @Override
    public QubitStream<Tag> where(QuerySpec<Tag, Boolean> spec) {
        return repository.where(spec);
    }
}
