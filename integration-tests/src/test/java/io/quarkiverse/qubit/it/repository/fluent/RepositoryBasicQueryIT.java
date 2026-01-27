package io.quarkiverse.qubit.it.repository.fluent;

import io.quarkiverse.qubit.it.PersonRepository;
import io.quarkiverse.qubit.it.fluent.AbstractBasicQueryTest;
import io.quarkiverse.qubit.it.testutil.PersonQueryOperations;
import io.quarkiverse.qubit.it.testutil.RepositoryPersonQueryOperations;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

/**
 * Tests for basic fluent API queries using repository instance methods.
 */
@QuarkusTest
class RepositoryBasicQueryIT extends AbstractBasicQueryTest {

    @Inject
    PersonRepository repository;

    @Override
    protected PersonQueryOperations personOps() {
        return new RepositoryPersonQueryOperations(repository);
    }
}
