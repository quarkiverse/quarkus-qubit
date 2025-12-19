package io.quarkiverse.qubit.it.arithmetic;

import io.quarkiverse.qubit.it.testutil.PersonQueryOperations;
import io.quarkiverse.qubit.it.testutil.StaticPersonQueryOperations;
import io.quarkus.test.junit.QuarkusTest;

/**
 * Tests for arithmetic operations using static entity methods.
 */
@QuarkusTest
class ArithmeticOperationsTest extends AbstractArithmeticOperationsTest {

    @Override
    protected PersonQueryOperations personOps() {
        return StaticPersonQueryOperations.INSTANCE;
    }
}
