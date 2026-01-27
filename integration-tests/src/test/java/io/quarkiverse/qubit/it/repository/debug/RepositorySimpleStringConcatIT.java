package io.quarkiverse.qubit.it.repository.debug;

import io.quarkiverse.qubit.it.PersonRepository;
import io.quarkiverse.qubit.it.debug.AbstractSimpleStringConcatTest;
import io.quarkiverse.qubit.it.testutil.PersonQueryOperations;
import io.quarkiverse.qubit.it.testutil.RepositoryPersonQueryOperations;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

/**
 * Repository pattern debug tests for string concatenation edge cases.
 */
@QuarkusTest
class RepositorySimpleStringConcatIT extends AbstractSimpleStringConcatTest {

    @Inject
    PersonRepository personRepository;

    @Override
    protected PersonQueryOperations personOps() {
        return new RepositoryPersonQueryOperations(personRepository);
    }
}
