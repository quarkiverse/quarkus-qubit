package io.quarkiverse.qubit.it.repository.datatypes;

import io.quarkiverse.qubit.it.PersonRepository;
import io.quarkiverse.qubit.it.ProductRepository;
import io.quarkiverse.qubit.it.datatypes.AbstractStringOperationsTest;
import io.quarkiverse.qubit.it.testutil.PersonQueryOperations;
import io.quarkiverse.qubit.it.testutil.ProductQueryOperations;
import io.quarkiverse.qubit.it.testutil.RepositoryPersonQueryOperations;
import io.quarkiverse.qubit.it.testutil.RepositoryProductQueryOperations;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

/**
 * Repository pattern tests for String operations in queries.
 *
 * <p>Extends {@link AbstractStringOperationsTest} with repository query operations.
 */
@QuarkusTest
class RepositoryStringOperationsTest extends AbstractStringOperationsTest {

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
