package io.quarkiverse.qubit.it.repository.datatypes;

import io.quarkiverse.qubit.it.PersonRepository;
import io.quarkiverse.qubit.it.datatypes.AbstractTemporalTypesTest;
import io.quarkiverse.qubit.it.testutil.PersonQueryOperations;
import io.quarkiverse.qubit.it.testutil.RepositoryPersonQueryOperations;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

/**
 * Tests for temporal types (LocalDate, LocalDateTime, LocalTime) using repository instance methods.
 */
@QuarkusTest
class RepositoryTemporalTypesIT extends AbstractTemporalTypesTest {

    @Inject
    PersonRepository personRepository;

    @Override
    protected PersonQueryOperations personOps() {
        return new RepositoryPersonQueryOperations(personRepository);
    }
}
