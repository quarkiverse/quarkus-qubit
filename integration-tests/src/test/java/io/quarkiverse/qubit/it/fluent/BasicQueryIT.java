package io.quarkiverse.qubit.it.fluent;

import io.quarkiverse.qubit.it.testutil.PersonQueryOperations;
import io.quarkiverse.qubit.it.testutil.StaticPersonQueryOperations;
import io.quarkus.test.junit.QuarkusTest;

/**
 * Tests for basic fluent API queries using static entity methods.
 */
@QuarkusTest
class BasicQueryIT extends AbstractBasicQueryTest {

    @Override
    protected PersonQueryOperations personOps() {
        return StaticPersonQueryOperations.INSTANCE;
    }
}
