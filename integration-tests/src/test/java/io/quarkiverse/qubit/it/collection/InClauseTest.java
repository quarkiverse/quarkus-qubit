package io.quarkiverse.qubit.it.collection;

import io.quarkiverse.qubit.it.testutil.PersonQueryOperations;
import io.quarkiverse.qubit.it.testutil.StaticPersonQueryOperations;
import io.quarkus.test.junit.QuarkusTest;

/**
 * Tests for IN clause support using static entity methods.
 */
@QuarkusTest
class InClauseTest extends AbstractInClauseTest {

    @Override
    protected PersonQueryOperations personOps() {
        return StaticPersonQueryOperations.INSTANCE;
    }
}
