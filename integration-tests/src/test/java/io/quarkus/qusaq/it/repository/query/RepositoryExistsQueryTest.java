package io.quarkus.qusaq.it.repository.query;

import io.quarkus.qusaq.it.Person;
import io.quarkus.qusaq.it.PersonRepository;
import io.quarkus.qusaq.it.testdata.TestDataFactory;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;


import static org.assertj.core.api.Assertions.assertThat;

/**
 * Repository pattern tests for exists query operations.
 * Mirrors io.quarkus.qusaq.it.query.ExistsQueryTest using repository injection.
 */
@QuarkusTest
class RepositoryExistsQueryTest {

    @Inject
    PersonRepository personRepository;

    @BeforeEach
    @Transactional
    void setupTestData() {
        TestDataFactory.clearAllData();
        TestDataFactory.createStandardPersonsAndProducts();
    }

    @Test
    void existsTrue() {
        boolean exists = personRepository.where((Person p) -> p.firstName.equals("John")).exists();

        assertThat(exists).isTrue();
    }

    @Test
    void existsFalse() {
        boolean exists = personRepository.where((Person p) -> p.firstName.equals("NonExistent")).exists();

        assertThat(exists).isFalse();
    }

    @Test
    void existsWithAnd() {
        boolean exists = personRepository.where((Person p) ->
                p.firstName.equals("Bob") && !p.active
        ).exists();

        assertThat(exists).isTrue();
    }

    @Test
    void existsWithComplexExpression() {
        boolean exists = personRepository.where((Person p) ->
                p.active && p.salary > 85000.0 && p.height != null &&
                p.height > 1.60f && p.email.contains("@example.com")
        ).exists();

        assertThat(exists).isTrue();
    }
}
