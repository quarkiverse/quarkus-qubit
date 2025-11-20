package io.quarkus.qusaq.it.query;

import io.quarkus.qusaq.it.Person;
import io.quarkus.qusaq.it.testdata.TestDataFactory;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;


import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for count query operations.
 */
@QuarkusTest
class CountQueryTest {

    @BeforeEach
    @Transactional
    void setupTestData() {
        TestDataFactory.clearAllData();
        TestDataFactory.createStandardPersons();
    }

    @Test
    void countSimplePredicate() {
        long count = Person.where((Person p) -> p.age > 25).count();

        assertThat(count).isGreaterThan(0);
    }

    @Test
    void countComplexPredicate() {
        long count = Person.where((Person p) -> p.age > 25 && p.active).count();

        assertThat(count).isGreaterThan(0);
    }

    @Test
    void countWithNestedExpression() {
        long count = Person.where((Person p) ->
                (p.age >= 28 && p.age <= 35) || p.salary > 85000
        ).count();

        assertThat(count).isGreaterThan(0);
    }
}
