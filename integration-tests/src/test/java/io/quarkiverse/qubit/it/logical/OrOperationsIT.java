package io.quarkiverse.qubit.it.logical;

import io.quarkiverse.qubit.it.testutil.PersonQueryOperations;
import io.quarkiverse.qubit.it.testutil.ProductQueryOperations;
import io.quarkiverse.qubit.it.testutil.StaticPersonQueryOperations;
import io.quarkiverse.qubit.it.testutil.StaticProductQueryOperations;
import io.quarkus.test.junit.QuarkusTest;

/**
 * Tests for OR logical operations using static entity methods.
 */
@QuarkusTest
class OrOperationsIT extends AbstractOrOperationsTest {

    @Override
    protected PersonQueryOperations personOps() {
        return StaticPersonQueryOperations.INSTANCE;
    }

    @Override
    protected ProductQueryOperations productOps() {
        return StaticProductQueryOperations.INSTANCE;
    }
}
