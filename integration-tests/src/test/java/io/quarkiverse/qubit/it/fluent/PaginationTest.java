package io.quarkiverse.qubit.it.fluent;

import io.quarkiverse.qubit.it.testutil.PersonQueryOperations;
import io.quarkiverse.qubit.it.testutil.StaticPersonQueryOperations;
import io.quarkus.test.junit.QuarkusTest;

/**
 * Tests for pagination (skip/limit) operations using static entity methods.
 */
@QuarkusTest
class PaginationTest extends AbstractPaginationTest {

    @Override
    protected PersonQueryOperations personOps() {
        return StaticPersonQueryOperations.INSTANCE;
    }
}
