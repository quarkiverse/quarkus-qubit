package io.quarkus.qusaq.it.subquery;

import io.quarkus.qusaq.it.Department;
import io.quarkus.qusaq.it.Person;
import io.quarkus.qusaq.it.testdata.TestDataFactory;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static io.quarkus.qusaq.runtime.Subqueries.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for subquery expressions using the fluent builder API.
 * <p>
 * Tests scalar aggregation subqueries (AVG, SUM, MIN, MAX, COUNT) using
 * the fluent builder API: {@code subquery(Person.class).avg(q -> q.salary)}.
 * <p>
 * Test data (via createPersonsWithPhones):
 * <ul>
 *   <li>Engineering: John (salary 75000), Alice (salary 90000) -> avg 82500</li>
 *   <li>Sales: Jane (salary 65000), Charlie (salary 55000) -> avg 60000</li>
 *   <li>Human Resources: Bob (salary 85000) -> avg 85000</li>
 * </ul>
 * <p>
 * Iteration 8: Subqueries implementation with fluent builder pattern.
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
                    (Person p) -> p.salary > subquery(Person.class).avg(q -> q.salary)
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
                    (Person p) -> p.salary < subquery(Person.class).avg(q -> q.salary)
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
                    (Person p) -> p.salary.equals(subquery(Person.class).max(q -> q.salary))
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
                    (Person p) -> p.salary < subquery(Person.class).max(q -> q.salary)
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
                    (Person p) -> p.salary.equals(subquery(Person.class).min(q -> q.salary))
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
                    (Person p) -> p.salary > subquery(Person.class).min(q -> q.salary)
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
                    (Person p) -> (long) p.age > subquery(Person.class).count()
            ).toList();

            assertThat(older).hasSize(5);
        }
    }

    @Nested
    @DisplayName("Scalar SUM Subqueries")
    class SumSubqueryTests {

        @Test
        @DisplayName("Find departments with budget greater than total salary")
        void findDepartmentsAboveTotalSalary() {
            // Total salaries: 75000 + 65000 + 85000 + 90000 + 55000 = 370000
            // Departments: Engineering (500000), Sales (300000), HR (200000)
            // Above total salary: Engineering (500000)
            List<Department> richDepts = Department.where(
                    (Department d) -> d.budget > subquery(Person.class).sum(p -> p.salary)
            ).toList();

            assertThat(richDepts)
                    .hasSize(1)
                    .extracting(Department::getName)
                    .containsExactly("Engineering");
        }

        @Test
        @DisplayName("Find persons with individual salary less than total")
        void findPersonsWithSalaryBelowTotal() {
            // Sum of all salaries: 75000 + 65000 + 85000 + 90000 + 55000 = 370000
            // Each individual salary is less than total sum
            // All 5 people have salary < 370000
            List<Person> belowTotalSum = Person.where(
                    (Person p) -> p.salary < subquery(Person.class).sum(q -> q.salary)
            ).toList();

            assertThat(belowTotalSum).hasSize(5);
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
                    (Person p) -> p.active && p.salary > subquery(Person.class).avg(q -> q.salary)
            ).toList();

            assertThat(activeAboveAvg)
                    .hasSize(2)
                    .extracting(Person::getFirstName)
                    .containsExactlyInAnyOrder("John", "Alice");
        }
    }

    // ========== FILTERED SUBQUERIES (WITH .WHERE()) ==========

    @Nested
    @DisplayName("Filtered Subqueries")
    class FilteredSubqueryTests {

        @Test
        @DisplayName("Average salary of active employees only")
        void averageSalaryOfActiveEmployees() {
            // Active employees: John (75000), Jane (65000), Alice (90000), Charlie (55000)
            // Bob is inactive (85000)
            // Active average: (75000 + 65000 + 90000 + 55000) / 4 = 71250
            // Active employees above active average: John (75000), Alice (90000)
            List<Person> aboveActiveAvg = Person.where(
                    (Person p) -> p.active && p.salary > subquery(Person.class)
                            .where(q -> q.active)
                            .avg(q -> q.salary)
            ).toList();

            assertThat(aboveActiveAvg)
                    .hasSize(2)
                    .extracting(Person::getFirstName)
                    .containsExactlyInAnyOrder("John", "Alice");
        }

        @Test
        @DisplayName("Maximum salary in specific department")
        void maxSalaryInDepartment() {
            // Engineering: John (75000), Alice (90000) -> max 90000
            // Persons with salary equal to Engineering max: Alice
            List<Person> engineeringMax = Person.where(
                    (Person p) -> p.salary.equals(
                            subquery(Person.class)
                                    .where(q -> q.department.name.equals("Engineering"))
                                    .max(q -> q.salary)
                    )
            ).toList();

            assertThat(engineeringMax)
                    .hasSize(1)
                    .extracting(Person::getFirstName)
                    .containsExactly("Alice");
        }

        @Test
        @DisplayName("Minimum salary among active employees")
        void minSalaryAmongActive() {
            // Active: John (75000), Jane (65000), Alice (90000), Charlie (55000)
            // Min active salary: Charlie (55000)
            List<Person> minActivePersons = Person.where(
                    (Person p) -> p.salary.equals(
                            subquery(Person.class)
                                    .where(q -> q.active)
                                    .min(q -> q.salary)
                    )
            ).toList();

            assertThat(minActivePersons)
                    .hasSize(1)
                    .extracting(Person::getFirstName)
                    .containsExactly("Charlie");
        }

        @Test
        @DisplayName("Sum of active employee salaries")
        void sumOfActiveEmployeeSalaries() {
            // Active employees: John (75000), Jane (65000), Alice (90000), Charlie (55000)
            // Sum of active salaries: 285000
            // Departments above active sum: Engineering (500000), Sales (300000)
            // HR (200000) is below
            List<Department> deptAboveActiveSum = Department.where(
                    (Department d) -> d.budget > subquery(Person.class)
                            .where(p -> p.active)
                            .sum(p -> p.salary)
            ).toList();

            assertThat(deptAboveActiveSum)
                    .hasSize(2)
                    .extracting(Department::getName)
                    .containsExactlyInAnyOrder("Engineering", "Sales");
        }

        @Test
        @DisplayName("Count of employees in specific department")
        void countInDepartment() {
            // Engineering: John, Alice (2 people)
            // Persons whose age is greater than Engineering employee count (2): all 5 people
            // All ages (30, 25, 45, 35, 28) are > 2
            List<Person> olderThanDeptCount = Person.where(
                    (Person p) -> (long) p.age > subquery(Person.class)
                            .where(q -> q.department.name.equals("Engineering"))
                            .count()
            ).toList();

            assertThat(olderThanDeptCount)
                    .hasSize(5)
                    .extracting(Person::getFirstName)
                    .containsExactlyInAnyOrder("John", "Jane", "Bob", "Alice", "Charlie");
        }

        @Test
        @DisplayName("Multiple where() calls are combined with AND")
        void multipleWhereCallsCombined() {
            // Active AND department = "Sales": Jane (65000), Charlie (55000)
            // Average of active Sales: (65000 + 55000) / 2 = 60000
            // Active Sales people above their department average: Jane (65000)
            List<Person> aboveActiveSalesAvg = Person.where(
                    (Person p) -> p.active &&
                            p.department.name.equals("Sales") &&
                            p.salary > subquery(Person.class)
                                    .where(q -> q.active)
                                    .where(q -> q.department.name.equals("Sales"))
                                    .avg(q -> q.salary)
            ).toList();

            assertThat(aboveActiveSalesAvg)
                    .hasSize(1)
                    .extracting(Person::getFirstName)
                    .containsExactly("Jane");
        }

        @Test
        @DisplayName("Filtered subquery vs non-filtered comparison")
        void filteredVsNonFiltered() {
            // All persons average: 74000
            // Active persons average: 71250
            // The difference should show in results

            // Above overall average: John, Bob, Alice (3 people)
            List<Person> aboveOverallAvg = Person.where(
                    (Person p) -> p.salary > subquery(Person.class).avg(q -> q.salary)
            ).toList();

            // Above active average: John, Alice (2 people) - Bob is inactive
            List<Person> aboveActiveAvg = Person.where(
                    (Person p) -> p.active && p.salary > subquery(Person.class)
                            .where(q -> q.active)
                            .avg(q -> q.salary)
            ).toList();

            assertThat(aboveOverallAvg).hasSize(3);
            assertThat(aboveActiveAvg).hasSize(2);
        }
    }
}
