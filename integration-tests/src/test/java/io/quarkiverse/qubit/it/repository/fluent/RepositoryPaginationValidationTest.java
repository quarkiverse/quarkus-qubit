package io.quarkiverse.qubit.it.repository.fluent;

import io.quarkiverse.qubit.it.PersonRepository;
import io.quarkiverse.qubit.it.fluent.AbstractPaginationValidationTest;
import io.quarkiverse.qubit.it.testutil.PersonQueryOperations;
import io.quarkiverse.qubit.it.testutil.RepositoryPersonQueryOperations;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

/**
 * Repository pattern tests for validation and error handling of pagination operations.
 */
@QuarkusTest
class RepositoryPaginationValidationTest extends AbstractPaginationValidationTest {

    @Inject
    PersonRepository personRepository;

    @Override
    protected PersonQueryOperations personOps() {
        return new RepositoryPersonQueryOperations(personRepository);
    }
}
