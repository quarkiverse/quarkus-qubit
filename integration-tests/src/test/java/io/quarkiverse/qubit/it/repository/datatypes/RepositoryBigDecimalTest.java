package io.quarkiverse.qubit.it.repository.datatypes;

import io.quarkiverse.qubit.it.ProductRepository;
import io.quarkiverse.qubit.it.datatypes.AbstractBigDecimalTest;
import io.quarkiverse.qubit.it.testutil.ProductQueryOperations;
import io.quarkiverse.qubit.it.testutil.RepositoryProductQueryOperations;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

/**
 * Tests for BigDecimal operations using repository instance methods.
 */
@QuarkusTest
class RepositoryBigDecimalTest extends AbstractBigDecimalTest {

    @Inject
    ProductRepository productRepository;

    @Override
    protected ProductQueryOperations productOps() {
        return new RepositoryProductQueryOperations(productRepository);
    }
}
