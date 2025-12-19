package io.quarkiverse.qubit.it.datatypes;

import io.quarkiverse.qubit.it.testutil.ProductQueryOperations;
import io.quarkiverse.qubit.it.testutil.StaticProductQueryOperations;
import io.quarkus.test.junit.QuarkusTest;

/**
 * Tests for BigDecimal operations using static entity methods.
 */
@QuarkusTest
class BigDecimalTest extends AbstractBigDecimalTest {

    @Override
    protected ProductQueryOperations productOps() {
        return StaticProductQueryOperations.INSTANCE;
    }
}
