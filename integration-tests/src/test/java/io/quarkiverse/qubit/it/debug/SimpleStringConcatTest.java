package io.quarkiverse.qubit.it.debug;

import io.quarkiverse.qubit.it.testutil.PersonQueryOperations;
import io.quarkiverse.qubit.it.testutil.StaticPersonQueryOperations;
import io.quarkus.test.junit.QuarkusTest;

/**
 * Minimal test to debug string concatenation using static entity methods.
 */
@QuarkusTest
class SimpleStringConcatTest extends AbstractSimpleStringConcatTest {

    @Override
    protected PersonQueryOperations personOps() {
        return StaticPersonQueryOperations.INSTANCE;
    }
}
