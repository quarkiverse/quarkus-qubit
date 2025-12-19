package io.quarkiverse.qubit.it.repository.join;

import io.quarkiverse.qubit.it.PersonRepository;
import io.quarkiverse.qubit.it.join.AbstractJoinQueryTest;
import io.quarkiverse.qubit.it.testutil.PersonQueryOperations;
import io.quarkiverse.qubit.it.testutil.RepositoryPersonQueryOperations;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

/**
 * Repository pattern tests for join queries.
 */
@QuarkusTest
class RepositoryJoinQueryTest extends AbstractJoinQueryTest {

    @Inject
    PersonRepository personRepository;

    @Override
    protected PersonQueryOperations personOps() {
        return new RepositoryPersonQueryOperations(personRepository);
    }
}
