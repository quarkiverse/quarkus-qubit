package io.quarkiverse.qubit.it.repository.logical;

import io.quarkiverse.qubit.it.PersonRepository;
import io.quarkiverse.qubit.it.ProductRepository;
import io.quarkiverse.qubit.it.logical.AbstractAndOperationsTest;
import io.quarkiverse.qubit.it.testutil.PersonQueryOperations;
import io.quarkiverse.qubit.it.testutil.ProductQueryOperations;
import io.quarkiverse.qubit.it.testutil.RepositoryPersonQueryOperations;
import io.quarkiverse.qubit.it.testutil.RepositoryProductQueryOperations;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

/**
 * Repository pattern tests for AND logical operations.
 */
@QuarkusTest
class RepositoryAndOperationsTest extends AbstractAndOperationsTest {

    @Inject
    PersonRepository personRepository;

    @Inject
    ProductRepository productRepository;

    @Override
    protected PersonQueryOperations personOps() {
        return new RepositoryPersonQueryOperations(personRepository);
    }

    @Override
    protected ProductQueryOperations productOps() {
        return new RepositoryProductQueryOperations(productRepository);
    }
}
