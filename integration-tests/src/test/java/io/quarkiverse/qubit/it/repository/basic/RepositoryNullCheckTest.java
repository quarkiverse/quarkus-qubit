package io.quarkiverse.qubit.it.repository.basic;

import io.quarkiverse.qubit.it.PersonRepository;
import io.quarkiverse.qubit.it.basic.AbstractNullCheckTest;
import io.quarkiverse.qubit.it.testutil.PersonQueryOperations;
import io.quarkiverse.qubit.it.testutil.RepositoryPersonQueryOperations;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

/**
 * Repository pattern tests for null safety checks on various field types.
 */
@QuarkusTest
class RepositoryNullCheckTest extends AbstractNullCheckTest {

    @Inject
    PersonRepository repository;

    @Override
    protected PersonQueryOperations personOps() {
        return new RepositoryPersonQueryOperations(repository);
    }
}
