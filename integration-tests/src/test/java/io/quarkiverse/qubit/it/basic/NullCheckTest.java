package io.quarkiverse.qubit.it.basic;

import io.quarkiverse.qubit.it.testutil.PersonQueryOperations;
import io.quarkiverse.qubit.it.testutil.StaticPersonQueryOperations;
import io.quarkus.test.junit.QuarkusTest;

/**
 * Tests for null safety checks on various field types using static entity methods.
 */
@QuarkusTest
class NullCheckTest extends AbstractNullCheckTest {

    @Override
    protected PersonQueryOperations personOps() {
        return StaticPersonQueryOperations.INSTANCE;
    }
}
