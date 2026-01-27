package io.quarkiverse.qubit.it.repository.basic;

import io.quarkiverse.qubit.it.PersonRepository;
import io.quarkiverse.qubit.it.ProductRepository;
import io.quarkiverse.qubit.it.basic.AbstractComparisonTest;
import io.quarkiverse.qubit.it.testutil.PersonQueryOperations;
import io.quarkiverse.qubit.it.testutil.ProductQueryOperations;
import io.quarkiverse.qubit.it.testutil.RepositoryPersonQueryOperations;
import io.quarkiverse.qubit.it.testutil.RepositoryProductQueryOperations;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

/**
 * Repository pattern tests for comparison operations (>, <, >=, <=, !=) on various field types.
 */
@QuarkusTest
class RepositoryComparisonIT extends AbstractComparisonTest {

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
