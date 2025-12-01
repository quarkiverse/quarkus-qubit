package io.quarkiverse.qubit.it.repository.query;

import io.quarkiverse.qubit.it.Person;
import io.quarkiverse.qubit.it.PersonRepository;
import io.quarkiverse.qubit.it.testdata.TestDataFactory;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;


import static org.assertj.core.api.Assertions.assertThat;

/**
 * Repository pattern tests for count query operations.
 * Mirrors io.quarkiverse.qubit.it.query.CountQueryTest using repository injection.
 */
@QuarkusTest
class RepositoryCountQueryTest {

    @Inject
    PersonRepository personRepository;

    @BeforeEach
    @Transactional
    void setupTestData() {
        TestDataFactory.clearAllData();
        TestDataFactory.createStandardPersonsAndProducts();
    }

    @Test
    void countSimplePredicate() {
        long count = personRepository.where((Person p) -> p.age > 25).count();

        assertThat(count).isGreaterThan(0);
    }

    @Test
    void countComplexPredicate() {
        long count = personRepository.where((Person p) -> p.age > 25 && p.active).count();

        assertThat(count).isGreaterThan(0);
    }

    @Test
    void countWithNestedExpression() {
        long count = personRepository.where((Person p) ->
                (p.age >= 28 && p.age <= 35) || p.salary > 85000
        ).count();

        assertThat(count).isGreaterThan(0);
    }
}
