package io.quarkiverse.qubit.it.testutil;

import io.quarkiverse.qubit.it.Phone;
import io.quarkiverse.qubit.it.PhoneRepository;
import io.quarkiverse.qubit.runtime.QuerySpec;
import io.quarkiverse.qubit.runtime.QubitStream;

/**
 * Implementation of {@link PhoneQueryOperations} using repository instance methods.
 */
public class RepositoryPhoneQueryOperations implements PhoneQueryOperations {

    private final PhoneRepository repository;

    public RepositoryPhoneQueryOperations(PhoneRepository repository) {
        this.repository = repository;
    }

    @Override
    public QubitStream<Phone> where(QuerySpec<Phone, Boolean> spec) {
        return repository.where(spec);
    }
}
