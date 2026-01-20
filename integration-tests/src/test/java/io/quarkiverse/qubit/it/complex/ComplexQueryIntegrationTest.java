package io.quarkiverse.qubit.it.complex;

import io.quarkiverse.qubit.it.Department;
import io.quarkiverse.qubit.it.Person;
import io.quarkiverse.qubit.it.Phone;
import io.quarkiverse.qubit.it.dto.DepartmentStatsDTO;
import io.quarkiverse.qubit.it.testdata.TestDataFactory;
import io.quarkiverse.qubit.Group;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static io.quarkiverse.qubit.Subqueries.subquery;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Comprehensive integration tests for complex query scenarios.
 * <p>
 * This test class covers end-to-end tests for:
 * <ul>
 *   <li>Complex nested subqueries (EXISTS, NOT EXISTS, IN, NOT IN)</li>
 *   <li>Multi-level relationship navigation</li>
 *   <li>Combined group/having/select operations</li>
 *   <li>Multiple subqueries in same predicate</li>
 *   <li>Full query pipeline: pre-filter + group + having + sort + pagination</li>
 * </ul>
 * <p>
 * Test data (via createPersonsWithPhones):
 * <ul>
 *   <li>Engineering (budget 500000): John (salary 75000, 2 phones), Alice (salary 90000, 2 phones)</li>
 *   <li>Sales (budget 300000): Jane (salary 65000, 1 phone), Charlie (salary 55000, 1 phone)</li>
 *   <li>Human Resources (budget 150000): Bob (salary 85000, inactive, 3 phones)</li>
 * </ul>
 */
@QuarkusTest
class ComplexQueryIntegrationTest {

    @BeforeEach
    @Transactional
    void setupTestData() {
        TestDataFactory.clearAllData();
        TestDataFactory.createPersonsWithPhones();
    }

    // ==========================================================================
    // NESTED SUBQUERIES: EXISTS / NOT EXISTS
    // ==========================================================================

    @Nested
    @DisplayName("EXISTS Subqueries")
    class ExistsSubqueryTests {

        @Test
        @DisplayName("Find persons who have at least one work phone (EXISTS)")
        void findPersonsWithWorkPhone() {
            // John, Bob, and Alice have work phones
            List<Person> personsWithWorkPhone = Person.where(
                    (Person p) -> subquery(Phone.class).exists(
                            ph -> ph.owner.id.equals(p.id) && ph.type.equals("work")
                    )
            ).toList();

            assertThat(personsWithWorkPhone)
                    .hasSize(3)
                    .extracting(Person::getFirstName)
                    .containsExactlyInAnyOrder("John", "Bob", "Alice");
        }

        @Test
        @DisplayName("Find persons who have at least one home phone (EXISTS)")
        void findPersonsWithHomePhone() {
            // Only Bob has a home phone
            List<Person> personsWithHomePhone = Person.where(
                    (Person p) -> subquery(Phone.class).exists(
                            ph -> ph.owner.id.equals(p.id) && ph.type.equals("home")
                    )
            ).toList();

            assertThat(personsWithHomePhone)
                    .hasSize(1)
                    .extracting(Person::getFirstName)
                    .containsExactly("Bob");
        }

        @Test
        @DisplayName("Find active persons who have phones (EXISTS with outer predicate)")
        void findActivePersonsWithPhones() {
            // Active persons: John, Jane, Alice, Charlie - all have phones
            // Bob is inactive
            List<Person> activeWithPhones = Person.where(
                    (Person p) -> p.active && subquery(Phone.class).exists(
                            ph -> ph.owner.id.equals(p.id)
                    )
            ).toList();

            assertThat(activeWithPhones)
                    .hasSize(4)
                    .extracting(Person::getFirstName)
                    .containsExactlyInAnyOrder("John", "Jane", "Alice", "Charlie");
        }
    }

    @Nested
    @DisplayName("NOT EXISTS Subqueries")
    class NotExistsSubqueryTests {

        @Test
        @DisplayName("Find persons who do NOT have a work phone (NOT EXISTS)")
        void findPersonsWithoutWorkPhone() {
            // Jane and Charlie don't have work phones
            List<Person> personsWithoutWorkPhone = Person.where(
                    (Person p) -> subquery(Phone.class).notExists(
                            ph -> ph.owner.id.equals(p.id) && ph.type.equals("work")
                    )
            ).toList();

            assertThat(personsWithoutWorkPhone)
                    .hasSize(2)
                    .extracting(Person::getFirstName)
                    .containsExactlyInAnyOrder("Jane", "Charlie");
        }

        @Test
        @DisplayName("Find persons who do NOT have a home phone (NOT EXISTS)")
        void findPersonsWithoutHomePhone() {
            // Everyone except Bob doesn't have a home phone
            List<Person> personsWithoutHomePhone = Person.where(
                    (Person p) -> subquery(Phone.class).notExists(
                            ph -> ph.owner.id.equals(p.id) && ph.type.equals("home")
                    )
            ).toList();

            assertThat(personsWithoutHomePhone)
                    .hasSize(4)
                    .extracting(Person::getFirstName)
                    .containsExactlyInAnyOrder("John", "Jane", "Alice", "Charlie");
        }
    }

    // ==========================================================================
    // NESTED SUBQUERIES: IN / NOT IN
    // ==========================================================================

    @Nested
    @DisplayName("IN Subqueries")
    class InSubqueryTests {

        @Test
        @DisplayName("Find persons in departments with budget > 200000 (IN subquery)")
        void findPersonsInHighBudgetDepartments() {
            // Engineering (500000) and Sales (300000) have budget > 200000
            // Persons: John, Alice (Engineering), Jane, Charlie (Sales)
            List<Person> personsInRichDepts = Person.where(
                    (Person p) -> subquery(Department.class).in(
                            p.department.id,
                            d -> d.id,
                            d -> d.budget > 200000
                    )
            ).toList();

            assertThat(personsInRichDepts)
                    .hasSize(4)
                    .extracting(Person::getFirstName)
                    .containsExactlyInAnyOrder("John", "Alice", "Jane", "Charlie");
        }

        @Test
        @DisplayName("Find persons in departments with budget > 400000 (IN subquery)")
        void findPersonsInVeryHighBudgetDepartments() {
            // Only Engineering (500000) has budget > 400000
            // Persons: John, Alice
            List<Person> personsInRichDepts = Person.where(
                    (Person p) -> subquery(Department.class).in(
                            p.department.id,
                            d -> d.id,
                            d -> d.budget > 400000
                    )
            ).toList();

            assertThat(personsInRichDepts)
                    .hasSize(2)
                    .extracting(Person::getFirstName)
                    .containsExactlyInAnyOrder("John", "Alice");
        }
    }

    @Nested
    @DisplayName("NOT IN Subqueries")
    class NotInSubqueryTests {

        @Test
        @DisplayName("Find persons NOT in departments with budget > 200000 (NOT IN subquery)")
        void findPersonsNotInHighBudgetDepartments() {
            // Only HR (150000) has budget <= 200000
            // Bob is in HR
            List<Person> personsNotInRichDepts = Person.where(
                    (Person p) -> subquery(Department.class).notIn(
                            p.department.id,
                            d -> d.id,
                            d -> d.budget > 200000
                    )
            ).toList();

            assertThat(personsNotInRichDepts)
                    .hasSize(1)
                    .extracting(Person::getFirstName)
                    .containsExactly("Bob");
        }
    }

    // ==========================================================================
    // MULTIPLE SUBQUERIES IN SAME PREDICATE
    // ==========================================================================

    @Nested
    @DisplayName("Multiple Subqueries Combined")
    class MultipleSubqueryTests {

        @Test
        @DisplayName("Find persons earning above average AND below maximum")
        void findPersonsBetweenAverageAndMaximum() {
            // Average salary: 74000
            // Max salary: 90000 (Alice)
            // Above 74000 but below 90000: John (75000), Bob (85000)
            List<Person> betweenAvgAndMax = Person.where(
                    (Person p) -> p.salary > subquery(Person.class).avg(q -> q.salary)
                            && p.salary < subquery(Person.class).max(q -> q.salary)
            ).toList();

            assertThat(betweenAvgAndMax)
                    .hasSize(2)
                    .extracting(Person::getFirstName)
                    .containsExactlyInAnyOrder("John", "Bob");
        }

        @Test
        @DisplayName("Find persons earning above minimum AND have work phone")
        void findPersonsAboveMinWithWorkPhone() {
            // Min salary: 55000 (Charlie)
            // Above min AND have work phone: John, Bob, Alice (all above 55000 and have work)
            List<Person> aboveMinWithWorkPhone = Person.where(
                    (Person p) -> p.salary > subquery(Person.class).min(q -> q.salary)
                            && subquery(Phone.class).exists(
                            ph -> ph.owner.id.equals(p.id) && ph.type.equals("work")
                    )
            ).toList();

            assertThat(aboveMinWithWorkPhone)
                    .hasSize(3)
                    .extracting(Person::getFirstName)
                    .containsExactlyInAnyOrder("John", "Bob", "Alice");
        }

        @Test
        @DisplayName("Find active persons earning above department average")
        void findActiveAboveDepartmentAverage() {
            // Active persons earning above overall active average (71250):
            // John (75000), Alice (90000)
            // Bob (85000) is inactive
            List<Person> activeAboveActiveAvg = Person.where(
                    (Person p) -> p.active
                            && p.salary > subquery(Person.class)
                            .where(q -> q.active)
                            .avg(q -> q.salary)
            ).toList();

            assertThat(activeAboveActiveAvg)
                    .hasSize(2)
                    .extracting(Person::getFirstName)
                    .containsExactlyInAnyOrder("John", "Alice");
        }
    }

    // ==========================================================================
    // MULTI-LEVEL RELATIONSHIP NAVIGATION
    // ==========================================================================

    @Nested
    @DisplayName("Multi-level Navigation")
    class MultiLevelNavigationTests {

        @Test
        @DisplayName("Find phones owned by persons in Engineering department (3-level navigation)")
        void findPhonesInEngineeringDepartment() {
            // Phone -> owner (Person) -> department (Department) -> name
            List<Phone> engineeringPhones = Phone.where(
                    (Phone ph) -> ph.owner.department.name.equals("Engineering")
            ).toList();

            // John has 2 phones, Alice has 2 phones = 4 phones total in Engineering
            assertThat(engineeringPhones).hasSize(4);
        }

        @Test
        @DisplayName("Find work phones owned by persons in high-budget departments")
        void findWorkPhonesInHighBudgetDepts() {
            // Work phones where owner's department budget > 200000
            // Engineering (500000): John's work phone, Alice's work phone
            // Sales (300000): no work phones for Jane or Charlie
            List<Phone> workPhonesHighBudget = Phone.where(
                    (Phone ph) -> ph.type.equals("work")
                            && ph.owner.department.budget > 200000
            ).toList();

            // John's work phone + Alice's work phone + Bob's work phone (HR budget is 150000, so excluded)
            // Wait, HR budget is 150000 which is < 200000, so Bob's work phone is excluded
            // Engineering (500000): John's work, Alice's work = 2
            // Sales (300000): no work phones
            assertThat(workPhonesHighBudget).hasSize(2);
        }

        @Test
        @DisplayName("Find phones of active persons older than 30")
        void findPhonesOfActiveOlderPersons() {
            // Active persons older than 30: Alice (35)
            // John is 30 (not > 30), Bob is 45 but inactive
            List<Phone> phones = Phone.where(
                    (Phone ph) -> ph.owner.active && ph.owner.age > 30
            ).toList();

            // Alice has 2 phones
            assertThat(phones).hasSize(2);
        }
    }

    // ==========================================================================
    // COMBINED GROUP / HAVING / SELECT
    // ==========================================================================

    @Nested
    @DisplayName("Complex Grouping Operations")
    class ComplexGroupingTests {

        @Test
        @DisplayName("Group with multiple having conditions (count AND avg)")
        void groupWithMultipleHavingConditions() {
            // Departments with count > 1 AND average salary > 60000
            // Engineering: 2 persons, avg 82500 -> YES
            // Sales: 2 persons, avg 60000 -> NO (avg not > 60000)
            // HR: 1 person -> NO (count not > 1)
            List<String> depts = Person.groupBy((Person p) -> p.department.name)
                    .having((Group<Person, String> g) -> g.count() > 1)
                    .having((Group<Person, String> g) -> g.avg((Person p) -> p.salary) > 60000.0)
                    .toList();

            assertThat(depts)
                    .hasSize(1)
                    .containsExactly("Engineering");
        }

        @Test
        @DisplayName("Group with having using min aggregation")
        void groupWithHavingMin() {
            // Departments where minimum salary is >= 55000
            // Engineering: min 75000 (John) -> YES
            // Sales: min 55000 (Charlie) -> YES (>= 55000)
            // HR: min 85000 (Bob only) -> YES
            List<String> depts = Person.groupBy((Person p) -> p.department.name)
                    .having((Group<Person, String> g) -> g.min((Person p) -> p.salary) >= 55000.0)
                    .toList();

            assertThat(depts)
                    .hasSize(3)
                    .containsExactlyInAnyOrder("Engineering", "Sales", "Human Resources");
        }

        @Test
        @DisplayName("Group with having using max aggregation")
        void groupWithHavingMax() {
            // Departments where maximum salary is > 80000
            // Engineering: max 90000 (Alice) -> YES
            // Sales: max 65000 (Jane) -> NO
            // HR: max 85000 (Bob) -> YES
            List<String> depts = Person.groupBy((Person p) -> p.department.name)
                    .having((Group<Person, String> g) -> g.max((Person p) -> p.salary) > 80000.0)
                    .toList();

            assertThat(depts)
                    .hasSize(2)
                    .containsExactlyInAnyOrder("Engineering", "Human Resources");
        }

        @Test
        @DisplayName("Complex select with multiple aggregations")
        void complexSelectWithMultipleAggregations() {
            // Select department name, count, avg, min, and max salary
            List<Object[]> stats = Person.groupBy((Person p) -> p.department.name)
                    .select((Group<Person, String> g) -> new Object[]{
                            g.key(),
                            g.count(),
                            g.avg((Person p) -> p.salary),
                            g.min((Person p) -> p.salary),
                            g.max((Person p) -> p.salary)
                    })
                    .toList();

            assertThat(stats).hasSize(3);

            // Verify Engineering stats
            Object[] engineering = stats.stream()
                    .filter(arr -> "Engineering".equals(arr[0]))
                    .findFirst().orElseThrow();
            assertThat(engineering[1]).isEqualTo(2L); // count
            assertThat(engineering[2]).isEqualTo(82500.0); // avg
            assertThat(engineering[3]).isEqualTo(75000.0); // min (John)
            assertThat(engineering[4]).isEqualTo(90000.0); // max (Alice)
        }

        @Test
        @DisplayName("Full pipeline: where + groupBy + having + sortedBy + limit")
        void fullQueryPipeline() {
            // Filter only active persons, group by department,
            // filter groups with count > 0, sort by avg salary desc, limit to top 2
            List<DepartmentStatsDTO> topDepts = Person
                    .where((Person p) -> p.active)
                    .groupBy((Person p) -> p.department.name)
                    .having((Group<Person, String> g) -> g.count() > 0)
                    .sortedDescendingBy((Group<Person, String> g) -> g.avg((Person p) -> p.salary))
                    .select((Group<Person, String> g) -> new DepartmentStatsDTO(
                            g.key(),
                            g.count(),
                            g.avg((Person p) -> p.salary)
                    ))
                    .limit(2)
                    .toList();

            // Active persons:
            // Engineering: John (75000), Alice (90000) -> avg 82500
            // Sales: Jane (65000), Charlie (55000) -> avg 60000
            // HR: Bob (inactive) -> excluded
            // Sorted by avg desc: Engineering (82500), Sales (60000)
            assertThat(topDepts)
                    .hasSize(2)
                    .extracting(DepartmentStatsDTO::getDepartmentName)
                    .containsExactly("Engineering", "Sales");

            assertThat(topDepts.get(0).getAverageSalary()).isEqualTo(82500.0);
            assertThat(topDepts.get(1).getAverageSalary()).isEqualTo(60000.0);
        }

        @Test
        @DisplayName("Group with pre-filter and pagination")
        void groupWithPreFilterAndPagination() {
            // Active persons grouped by department, sorted alphabetically, skip 1, limit 1
            List<String> pagedDepts = Person
                    .where((Person p) -> p.active)
                    .groupBy((Person p) -> p.department.name)
                    .sortedBy((Group<Person, String> g) -> g.key())
                    .skip(1)
                    .limit(1)
                    .toList();

            // Active departments sorted: Engineering, Sales (HR excluded - Bob inactive)
            // Skip 1 (Engineering), take 1 (Sales)
            assertThat(pagedDepts)
                    .hasSize(1)
                    .containsExactly("Sales");
        }
    }

    // ==========================================================================
    // JOIN WITH SUBQUERY COMBINATIONS
    // ==========================================================================

    @Nested
    @DisplayName("Join Combined with Subquery")
    class JoinWithSubqueryTests {

        @Test
        @DisplayName("Join with subquery filter on owner salary")
        void joinWithSubqueryOnOwner() {
            // Find phones of persons earning above average
            // Average: 74000
            // Above average: John (75000), Bob (85000), Alice (90000)
            var phones = Person.join((Person p) -> p.phones)
                    .where((Person p, Phone ph) ->
                            p.salary > subquery(Person.class).avg(q -> q.salary))
                    .selectJoined()
                    .toList();

            // John: 2 phones, Bob: 3 phones, Alice: 2 phones = 7 phones
            assertThat(phones).hasSize(7);
        }

        @Test
        @DisplayName("Join with EXISTS subquery")
        void joinWithExistsSubquery() {
            // Find mobile phones of persons who also have work phones
            var mobileOfPersonsWithWork = Person.join((Person p) -> p.phones)
                    .where((Person p, Phone ph) ->
                            ph.type.equals("mobile") &&
                                    subquery(Phone.class).exists(
                                            ph2 -> ph2.owner.id.equals(p.id) && ph2.type.equals("work")
                                    ))
                    .selectJoined()
                    .toList();

            // John, Bob, Alice have both mobile and work phones = 3 mobile phones
            assertThat(mobileOfPersonsWithWork).hasSize(3);
        }
    }

    // ==========================================================================
    // EDGE CASES AND BOUNDARY CONDITIONS
    // ==========================================================================

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("Subquery with empty result set")
        void subqueryWithEmptyResult() {
            // Find persons earning more than the average of inactive persons earning > 100000
            // There are no inactive persons earning > 100000, so subquery returns null/empty
            // This tests how the system handles empty subquery results
            List<Person> result = Person.where(
                    (Person p) -> p.salary > subquery(Person.class)
                            .where(q -> !q.active && q.salary > 100000)
                            .avg(q -> q.salary)
            ).toList();

            // When subquery returns null (no matching rows), comparison returns false for all
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("Group with zero results after having filter")
        void groupWithZeroResultsAfterHaving() {
            // Filter departments with count > 10 (none exist)
            List<String> depts = Person.groupBy((Person p) -> p.department.name)
                    .having((Group<Person, String> g) -> g.count() > 10)
                    .toList();

            assertThat(depts).isEmpty();
        }

        @Test
        @DisplayName("Chained where clauses with subqueries")
        void chainedWhereWithSubqueries() {
            // Multiple where() calls combined
            List<Person> result = Person.where(
                            (Person p) -> p.salary > subquery(Person.class).min(q -> q.salary)
                    ).where(
                            (Person p) -> p.salary < subquery(Person.class).max(q -> q.salary)
                    ).where(
                            (Person p) -> p.active
                    )
                    .toList();

            // Active persons between min (55000) and max (90000): John (75000), Jane (65000)
            // Alice is AT max so excluded
            assertThat(result)
                    .extracting(Person::getFirstName)
                    .containsExactlyInAnyOrder("John", "Jane");
        }
    }
}
