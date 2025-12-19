package io.quarkiverse.qubit.it.repository.collection;

import io.quarkiverse.qubit.it.PersonRepository;
import io.quarkiverse.qubit.it.collection.AbstractInClauseTest;
import io.quarkiverse.qubit.it.testutil.PersonQueryOperations;
import io.quarkiverse.qubit.it.testutil.RepositoryPersonQueryOperations;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

/**
 * Repository pattern tests for IN clause support.
 */
@QuarkusTest
class RepositoryInClauseTest extends AbstractInClauseTest {

    @Inject
    PersonRepository personRepository;

    @Override
    protected PersonQueryOperations personOps() {
        return new RepositoryPersonQueryOperations(personRepository);
    }
}
