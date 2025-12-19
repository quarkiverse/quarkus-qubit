package io.quarkiverse.qubit.it.join;

import io.quarkiverse.qubit.it.testutil.PersonQueryOperations;
import io.quarkiverse.qubit.it.testutil.StaticPersonQueryOperations;
import io.quarkus.test.junit.QuarkusTest;

/**
 * Tests for join queries using static entity methods.
 */
@QuarkusTest
class JoinQueryTest extends AbstractJoinQueryTest {

    @Override
    protected PersonQueryOperations personOps() {
        return StaticPersonQueryOperations.INSTANCE;
    }
}
