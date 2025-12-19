package io.quarkiverse.qubit.it.datatypes;

import io.quarkiverse.qubit.it.testutil.PersonQueryOperations;
import io.quarkiverse.qubit.it.testutil.StaticPersonQueryOperations;
import io.quarkus.test.junit.QuarkusTest;

/**
 * Tests for temporal types (LocalDate, LocalDateTime, LocalTime) using static entity methods.
 */
@QuarkusTest
class TemporalTypesTest extends AbstractTemporalTypesTest {

    @Override
    protected PersonQueryOperations personOps() {
        return StaticPersonQueryOperations.INSTANCE;
    }
}
