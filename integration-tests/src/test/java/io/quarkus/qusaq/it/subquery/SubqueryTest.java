package io.quarkus.qusaq.it.subquery;

import io.quarkus.qusaq.it.Person;
import io.quarkus.qusaq.it.testdata.TestDataFactory;
import io.quarkus.qusaq.runtime.Subqueries;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for subquery expressions.
 * <p>
 * Tests scalar aggregation subqueries (AVG, SUM, MIN, MAX, COUNT).
 * <p>
 * Test data (via createPersonsWithPhones):
 * <ul>
 *   <li>Engineering: John (salary 75000), Alice (salary 90000) -> avg 82500</li>
 *   <li>Sales: Jane (salary 65000), Charlie (salary 55000) -> avg 60000</li>
 *   <li>Human Resources: Bob (salary 85000) -> avg 85000</li>
 * </ul>
 * <p>
 * Iteration 8: Subqueries implementation.
 */
@QuarkusTest
class SubqueryTest {

    @BeforeEach
    @Transactional
    void setupTestData() {
        TestDataFactory.clearAllData();
        TestDataFactory.createPersonsWithPhones();
    }

    // ========== SCALAR AGGREGATION SUBQUERIES ==========

    @Nested
    @DisplayName("Scalar AVG Subqueries")
    class AvgSubqueryTests {

        @Test
        @DisplayName("Find persons earning above average salary")
        void findPersonsAboveAverageSalary() {
            // Average salary: (75000 + 65000 + 85000 + 90000 + 55000) / 5 = 74000
            // Above average: John (75000), Bob (85000), Alice (90000)
            List<Person> aboveAverage = Person.where(
                    (Person p) -> p.salary > Subqueries.avg(Person.class, q -> q.salary)
            ).toList();

            assertThat(aboveAverage)
                    .hasSize(3)
                    .extracting(Person::getFirstName)
                    .containsExactlyInAnyOrder("John", "Bob", "Alice");
        }

        @Test
        @DisplayName("Find persons earning below average salary")
        void findPersonsBelowAverageSalary() {
            // Below 74000: Jane (65000), Charlie (55000)
            List<Person> belowAverage = Person.where(
                    (Person p) -> p.salary < Subqueries.avg(Person.class, q -> q.salary)
            ).toList();

            assertThat(belowAverage)
                    .hasSize(2)
                    .extracting(Person::getFirstName)
                    .containsExactlyInAnyOrder("Jane", "Charlie");
        }
    }

    @Nested
    @DisplayName("Scalar MAX Subqueries")
    class MaxSubqueryTests {

        @Test
        @DisplayName("Find the person with maximum salary")
        void findPersonWithMaxSalary() {
            // Alice has max salary (90000)
            List<Person> maxSalaryPerson = Person.where(
                    (Person p) -> p.salary.equals(Subqueries.max(Person.class, q -> q.salary))
            ).toList();

            assertThat(maxSalaryPerson)
                    .hasSize(1)
                    .extracting(Person::getFirstName)
                    .containsExactly("Alice");
        }

        @Test
        @DisplayName("Find persons earning less than maximum")
        void findPersonsBelowMaxSalary() {
            // Everyone except Alice
            List<Person> belowMax = Person.where(
                    (Person p) -> p.salary < Subqueries.max(Person.class, q -> q.salary)
            ).toList();

            assertThat(belowMax)
                    .hasSize(4)
                    .extracting(Person::getFirstName)
                    .containsExactlyInAnyOrder("John", "Jane", "Bob", "Charlie");
        }
    }

    @Nested
    @DisplayName("Scalar MIN Subqueries")
    class MinSubqueryTests {

        @Test
        @DisplayName("Find the person with minimum salary")
        void findPersonWithMinSalary() {
            // Charlie has min salary (55000)
            List<Person> minSalaryPerson = Person.where(
                    (Person p) -> p.salary.equals(Subqueries.min(Person.class, q -> q.salary))
            ).toList();

            assertThat(minSalaryPerson)
                    .hasSize(1)
                    .extracting(Person::getFirstName)
                    .containsExactly("Charlie");
        }

        @Test
        @DisplayName("Find persons earning more than minimum")
        void findPersonsAboveMinSalary() {
            // Everyone except Charlie
            List<Person> aboveMin = Person.where(
                    (Person p) -> p.salary > Subqueries.min(Person.class, q -> q.salary)
            ).toList();

            assertThat(aboveMin)
                    .hasSize(4)
                    .extracting(Person::getFirstName)
                    .containsExactlyInAnyOrder("John", "Jane", "Bob", "Alice");
        }
    }

    @Nested
    @DisplayName("Scalar COUNT Subqueries")
    class CountSubqueryTests {

        @Test
        @DisplayName("Find persons older than total person count")
        void findPersonsOlderThanCount() {
            // Count is 5, ages are: John (30), Jane (25), Bob (45), Alice (35), Charlie (28)
            // Older than 5: all of them
            List<Person> older = Person.where(
                    (Person p) -> (long) p.age > Subqueries.count(Person.class)
            ).toList();

            assertThat(older).hasSize(5);
        }
    }

    @Nested
    @DisplayName("Combined Subqueries")
    class CombinedSubqueryTests {

        @Test
        @DisplayName("Find active persons earning above average")
        void findActivePersonsAboveAverage() {
            // Active and above 74000: John (75000), Alice (90000)
            // Bob is inactive
            List<Person> activeAboveAvg = Person.where(
                    (Person p) -> p.active && p.salary > Subqueries.avg(Person.class, q -> q.salary)
            ).toList();

            assertThat(activeAboveAvg)
                    .hasSize(2)
                    .extracting(Person::getFirstName)
                    .containsExactlyInAnyOrder("John", "Alice");
        }
    }
}
