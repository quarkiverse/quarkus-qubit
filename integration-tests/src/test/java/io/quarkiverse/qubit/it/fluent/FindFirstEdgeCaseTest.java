package io.quarkiverse.qubit.it.fluent;

import io.quarkiverse.qubit.it.testutil.PersonQueryOperations;
import io.quarkiverse.qubit.it.testutil.StaticPersonQueryOperations;
import io.quarkus.test.junit.QuarkusTest;

/**
 * Edge case tests verifying findFirst() return logic correctness.
 * Uses static entity methods.
 */
@QuarkusTest
class FindFirstEdgeCaseTest extends AbstractFindFirstEdgeCaseTest {

    @Override
    protected PersonQueryOperations personOps() {
        return StaticPersonQueryOperations.INSTANCE;
    }
}
