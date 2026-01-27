package io.quarkiverse.qubit.it.fluent;

import io.quarkiverse.qubit.it.testutil.PersonQueryOperations;
import io.quarkiverse.qubit.it.testutil.StaticPersonQueryOperations;
import io.quarkus.test.junit.QuarkusTest;

/**
 * Integration tests for getSingleResult() and findFirst() terminal operations.
 * Uses static entity methods.
 */
@QuarkusTest
class SingleResultIT extends AbstractSingleResultTest {

    @Override
    protected PersonQueryOperations personOps() {
        return StaticPersonQueryOperations.INSTANCE;
    }
}
