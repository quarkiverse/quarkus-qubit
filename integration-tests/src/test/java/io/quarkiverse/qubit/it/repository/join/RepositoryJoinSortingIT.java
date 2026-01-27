package io.quarkiverse.qubit.it.repository.join;

import io.quarkiverse.qubit.it.PersonRepository;
import io.quarkiverse.qubit.it.join.AbstractJoinSortingTest;
import io.quarkiverse.qubit.it.testutil.PersonQueryOperations;
import io.quarkiverse.qubit.it.testutil.RepositoryPersonQueryOperations;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

/**
 * Repository pattern tests for join query sorting.
 */
@QuarkusTest
class RepositoryJoinSortingIT extends AbstractJoinSortingTest {

    @Inject
    PersonRepository personRepository;

    @Override
    protected PersonQueryOperations personOps() {
        return new RepositoryPersonQueryOperations(personRepository);
    }
}
