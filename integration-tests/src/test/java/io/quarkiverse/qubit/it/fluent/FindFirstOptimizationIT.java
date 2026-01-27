package io.quarkiverse.qubit.it.fluent;

import io.quarkiverse.qubit.it.testutil.PersonQueryOperations;
import io.quarkiverse.qubit.it.testutil.StaticPersonQueryOperations;
import io.quarkus.test.junit.QuarkusTest;

/**
 * Verification tests for findFirst() SQL LIMIT optimization.
 * Uses static entity methods.
 */
@QuarkusTest
class FindFirstOptimizationIT extends AbstractFindFirstOptimizationTest {

    @Override
    protected PersonQueryOperations personOps() {
        return StaticPersonQueryOperations.INSTANCE;
    }
}
