package io.quarkiverse.qubit.it.repository.arithmetic;

import io.quarkiverse.qubit.it.PersonRepository;
import io.quarkiverse.qubit.it.arithmetic.AbstractArithmeticOperationsTest;
import io.quarkiverse.qubit.it.testutil.PersonQueryOperations;
import io.quarkiverse.qubit.it.testutil.RepositoryPersonQueryOperations;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

/**
 * Tests for arithmetic operations using repository instance methods.
 */
@QuarkusTest
class RepositoryArithmeticOperationsIT extends AbstractArithmeticOperationsTest {

    @Inject
    PersonRepository personRepository;

    @Override
    protected PersonQueryOperations personOps() {
        return new RepositoryPersonQueryOperations(personRepository);
    }
}
