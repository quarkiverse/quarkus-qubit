package io.quarkiverse.qubit.it.fluent;

import io.quarkiverse.qubit.it.testutil.PersonQueryOperations;
import io.quarkiverse.qubit.it.testutil.StaticPersonQueryOperations;
import io.quarkus.test.junit.QuarkusTest;

/**
 * Tests for complex operation combinations using static entity methods.
 */
@QuarkusTest
class ComplexCombinationsIT extends AbstractComplexCombinationsTest {

    @Override
    protected PersonQueryOperations personOps() {
        return StaticPersonQueryOperations.INSTANCE;
    }
}
