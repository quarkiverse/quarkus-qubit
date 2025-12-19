package io.quarkiverse.qubit.it.repository.fluent;

import io.quarkiverse.qubit.it.PersonRepository;
import io.quarkiverse.qubit.it.ProductRepository;
import io.quarkiverse.qubit.it.fluent.AbstractSortingTest;
import io.quarkiverse.qubit.it.testutil.PersonQueryOperations;
import io.quarkiverse.qubit.it.testutil.ProductQueryOperations;
import io.quarkiverse.qubit.it.testutil.RepositoryPersonQueryOperations;
import io.quarkiverse.qubit.it.testutil.RepositoryProductQueryOperations;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

/**
 * Tests for sorting functionality using repository instance methods.
 */
@QuarkusTest
@Transactional
class RepositorySortingTest extends AbstractSortingTest {

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
