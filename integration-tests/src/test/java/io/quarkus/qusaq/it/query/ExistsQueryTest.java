package io.quarkus.qusaq.it.query;

import io.quarkus.qusaq.it.Person;
import io.quarkus.qusaq.it.testdata.TestDataFactory;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;


import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for exists query operations.
 */
@QuarkusTest
class ExistsQueryTest {

    @BeforeEach
    @Transactional
    void setupTestData() {
        TestDataFactory.clearAllData();
        TestDataFactory.createStandardPersons();
    }

    @Test
    void existsTrue() {
        boolean exists = Person.exists((Person p) -> p.firstName.equals("John"));

        assertThat(exists).isTrue();
    }

    @Test
    void existsFalse() {
        boolean exists = Person.exists((Person p) -> p.firstName.equals("NonExistent"));

        assertThat(exists).isFalse();
    }

    @Test
    void existsWithAnd() {
        boolean exists = Person.exists((Person p) ->
                p.firstName.equals("Bob") && !p.active
        );

        assertThat(exists).isTrue();
    }

    @Test
    void existsWithComplexExpression() {
        boolean exists = Person.exists((Person p) ->
                p.active && p.salary > 85000.0 && p.height != null &&
                p.height > 1.60f && p.email.contains("@example.com")
        );

        assertThat(exists).isTrue();
    }
}
