package io.quarkiverse.qubit.it.captured;

import io.quarkiverse.qubit.it.testutil.PersonQueryOperations;
import io.quarkiverse.qubit.it.testutil.ProductQueryOperations;
import io.quarkiverse.qubit.it.testutil.StaticPersonQueryOperations;
import io.quarkiverse.qubit.it.testutil.StaticProductQueryOperations;
import io.quarkus.test.junit.QuarkusTest;

/**
 * Tests for captured variables in lambda expressions using static entity methods.
 */
@QuarkusTest
class CapturedVariablesTest extends AbstractCapturedVariablesTest {

    @Override
    protected PersonQueryOperations personOps() {
        return StaticPersonQueryOperations.INSTANCE;
    }

    @Override
    protected ProductQueryOperations productOps() {
        return StaticProductQueryOperations.INSTANCE;
    }
}
