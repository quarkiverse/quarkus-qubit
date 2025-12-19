package io.quarkiverse.qubit.it.basic;

import io.quarkiverse.qubit.it.testutil.PersonQueryOperations;
import io.quarkiverse.qubit.it.testutil.ProductQueryOperations;
import io.quarkiverse.qubit.it.testutil.StaticPersonQueryOperations;
import io.quarkiverse.qubit.it.testutil.StaticProductQueryOperations;
import io.quarkus.test.junit.QuarkusTest;

/**
 * Tests for comparison operations (>, <, >=, <=, !=) on various field types
 * using static entity methods.
 */
@QuarkusTest
class ComparisonTest extends AbstractComparisonTest {

    @Override
    protected PersonQueryOperations personOps() {
        return StaticPersonQueryOperations.INSTANCE;
    }

    @Override
    protected ProductQueryOperations productOps() {
        return StaticProductQueryOperations.INSTANCE;
    }
}
