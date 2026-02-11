package io.quarkiverse.qubit.it.group;

import io.quarkiverse.qubit.it.Person;
import io.quarkiverse.qubit.it.dto.DepartmentStatsDTO;
import io.quarkiverse.qubit.it.testdata.TestDataFactory;
import io.quarkiverse.qubit.Group;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for GROUP BY queries.
 * <p>
 * Tests grouping operations using the fluent API.
 * The test data creates 5 persons in 3 departments:
 * <ul>
 * <li>Engineering: John (salary 75000), Alice (salary 90000) -> 2 persons, avg 82500</li>
 * <li>Sales: Jane (salary 65000), Charlie (salary 55000) -> 2 persons, avg 60000</li>
 * <li>Human Resources: Bob (salary 85000) -> 1 person, avg 85000</li>
 * </ul>
 * <p>
 * Iteration 7: Grouping / GROUP BY implementation.
 */
@QuarkusTest
class GroupQueryIT {

    @BeforeEach
    @Transactional
    void setupTestData() {
        TestDataFactory.clearAllData();
        TestDataFactory.createPersonsWithPhones();
    }

    // ========== BASIC GROUP BY QUERIES ==========

    @Test
    void groupByDepartmentNameReturnsAllDepartments() {
        // Group by department.name, returns distinct department names
        List<String> departmentNames = Person.groupBy((Person p) -> p.department.name)
                .toList();

        // Should return 3 distinct departments
        assertThat(departmentNames)
                .hasSize(3)
                .containsExactlyInAnyOrder("Engineering", "Sales", "Human Resources");
    }

    @Test
    void groupByDepartmentNameWithSelectKey() {
        // Group by department.name, select only the key
        List<String> departmentNames = Person.groupBy((Person p) -> p.department.name)
                .selectKey()
                .toList();

        assertThat(departmentNames)
                .hasSize(3)
                .containsExactlyInAnyOrder("Engineering", "Sales", "Human Resources");
    }

    // ========== GROUP BY WITH COUNT ==========

    @Test
    void groupByWithCountReturnsCorrectCounts() {
        // Group by department.name and count persons in each
        List<DepartmentStatsDTO> stats = Person.groupBy((Person p) -> p.department.name)
                .select((Group<Person, String> g) -> new DepartmentStatsDTO(g.key(), g.count()))
                .toList();

        assertThat(stats).hasSize(3);

        // Verify counts
        assertThat(stats).extracting(DepartmentStatsDTO::getDepartmentName)
                .containsExactlyInAnyOrder("Engineering", "Sales", "Human Resources");

        // Engineering: John, Alice = 2
        assertThat(stats.stream().filter(s -> s.getDepartmentName().equals("Engineering")).findFirst().orElseThrow()
                .getEmployeeCount())
                .isEqualTo(2);

        // Sales: Jane, Charlie = 2
        assertThat(
                stats.stream().filter(s -> s.getDepartmentName().equals("Sales")).findFirst().orElseThrow().getEmployeeCount())
                .isEqualTo(2);

        // HR: Bob = 1
        assertThat(stats.stream().filter(s -> s.getDepartmentName().equals("Human Resources")).findFirst().orElseThrow()
                .getEmployeeCount())
                .isEqualTo(1);
    }

    // ========== GROUP BY WITH AGGREGATIONS ==========

    @Test
    void groupByWithAvgReturnsCorrectAverages() {
        // Group by department.name and compute average salary
        List<DepartmentStatsDTO> stats = Person.groupBy((Person p) -> p.department.name)
                .select((Group<Person, String> g) -> new DepartmentStatsDTO(
                        g.key(),
                        g.count(),
                        g.avg((Person p) -> p.salary)))
                .toList();

        assertThat(stats).hasSize(3);

        // Engineering: (75000 + 90000) / 2 = 82500
        DepartmentStatsDTO engineering = stats.stream()
                .filter(s -> s.getDepartmentName().equals("Engineering")).findFirst().orElseThrow();
        assertThat(engineering.getAverageSalary()).isEqualTo(82500.0);

        // Sales: (65000 + 55000) / 2 = 60000
        DepartmentStatsDTO sales = stats.stream()
                .filter(s -> s.getDepartmentName().equals("Sales")).findFirst().orElseThrow();
        assertThat(sales.getAverageSalary()).isEqualTo(60000.0);

        // HR: 85000
        DepartmentStatsDTO hr = stats.stream()
                .filter(s -> s.getDepartmentName().equals("Human Resources")).findFirst().orElseThrow();
        assertThat(hr.getAverageSalary()).isEqualTo(85000.0);
    }

    @Test
    void groupByWithMinReturnsCorrectMinimums() {
        // Group by department.name and find min salary
        List<Object[]> stats = Person.groupBy((Person p) -> p.department.name)
                .select((Group<Person, String> g) -> new Object[] {
                        g.key(),
                        g.min((Person p) -> p.salary) })
                .toList();

        assertThat(stats).hasSize(3);

        // Find Engineering entry
        Object[] engineering = stats.stream()
                .filter(arr -> "Engineering".equals(arr[0])).findFirst().orElseThrow();
        assertThat(engineering[1]).isEqualTo(75000.0); // John has min salary

        // Find Sales entry
        Object[] sales = stats.stream()
                .filter(arr -> "Sales".equals(arr[0])).findFirst().orElseThrow();
        assertThat(sales[1]).isEqualTo(55000.0); // Charlie has min salary

        // Find HR entry
        Object[] hr = stats.stream()
                .filter(arr -> "Human Resources".equals(arr[0])).findFirst().orElseThrow();
        assertThat(hr[1]).isEqualTo(85000.0); // Bob only one
    }

    @Test
    void groupByWithMaxReturnsCorrectMaximums() {
        // Group by department.name and find max salary
        List<Object[]> stats = Person.groupBy((Person p) -> p.department.name)
                .select((Group<Person, String> g) -> new Object[] {
                        g.key(),
                        g.max((Person p) -> p.salary) })
                .toList();

        assertThat(stats).hasSize(3);

        // Find Engineering entry
        Object[] engineering = stats.stream()
                .filter(arr -> "Engineering".equals(arr[0])).findFirst().orElseThrow();
        assertThat(engineering[1]).isEqualTo(90000.0); // Alice has max salary

        // Find Sales entry
        Object[] sales = stats.stream()
                .filter(arr -> "Sales".equals(arr[0])).findFirst().orElseThrow();
        assertThat(sales[1]).isEqualTo(65000.0); // Jane has max salary
    }

    // ========== GROUP BY WITH HAVING ==========

    @Test
    void groupByWithHavingCountFiltersCorrectly() {
        // Only departments with more than 1 person
        List<String> largeDepts = Person.groupBy((Person p) -> p.department.name)
                .having((Group<Person, String> g) -> g.count() > 1)
                .toList();

        // Engineering and Sales have 2 persons each, HR has 1
        assertThat(largeDepts)
                .hasSize(2)
                .containsExactlyInAnyOrder("Engineering", "Sales");
    }

    @Test
    void groupByWithHavingAvgFiltersCorrectly() {
        // Only departments with average salary > 70000
        List<String> highPayingDepts = Person.groupBy((Person p) -> p.department.name)
                .having((Group<Person, String> g) -> g.avg((Person p) -> p.salary) > 70000.0)
                .toList();

        // Engineering: 82500, HR: 85000 (Sales: 60000 excluded)
        assertThat(highPayingDepts)
                .hasSize(2)
                .containsExactlyInAnyOrder("Engineering", "Human Resources");
    }

    // ========== GROUP BY WITH SORTING ==========

    @Test
    void groupByWithSortByKeyAscending() {
        // Sort by department name alphabetically
        List<String> sortedDepts = Person.groupBy((Person p) -> p.department.name)
                .sortedBy((Group<Person, String> g) -> g.key())
                .toList();

        // Engineering, Human Resources, Sales (alphabetical)
        assertThat(sortedDepts)
                .hasSize(3)
                .containsExactly("Engineering", "Human Resources", "Sales");
    }

    @Test
    void groupByWithSortByKeyDescending() {
        // Sort by department name descending
        List<String> sortedDepts = Person.groupBy((Person p) -> p.department.name)
                .sortedDescendingBy((Group<Person, String> g) -> g.key())
                .toList();

        // Sales, Human Resources, Engineering (reverse alphabetical)
        assertThat(sortedDepts)
                .hasSize(3)
                .containsExactly("Sales", "Human Resources", "Engineering");
    }

    @Test
    void groupByWithSortByCountDescending() {
        // Sort by employee count descending
        List<DepartmentStatsDTO> stats = Person.groupBy((Person p) -> p.department.name)
                .sortedDescendingBy((Group<Person, String> g) -> g.count())
                .select((Group<Person, String> g) -> new DepartmentStatsDTO(g.key(), g.count()))
                .toList();

        // Engineering (2), Sales (2), HR (1) - first two have same count
        assertThat(stats).hasSize(3);
        assertThat(stats.getFirst().getEmployeeCount()).isGreaterThanOrEqualTo(2);
        assertThat(stats.getLast().getEmployeeCount()).isEqualTo(1);
        assertThat(stats.getLast().getDepartmentName()).isEqualTo("Human Resources");
    }

    // ========== GROUP BY WITH PAGINATION ==========

    @Test
    void groupByWithLimit() {
        // Only return first 2 departments
        List<String> limitedDepts = Person.groupBy((Person p) -> p.department.name)
                .sortedBy((Group<Person, String> g) -> g.key())
                .limit(2)
                .toList();

        assertThat(limitedDepts)
                .hasSize(2)
                .containsExactly("Engineering", "Human Resources");
    }

    @Test
    void groupByWithSkipAndLimit() {
        // Skip first department, take next 2
        List<String> pagedDepts = Person.groupBy((Person p) -> p.department.name)
                .sortedBy((Group<Person, String> g) -> g.key())
                .skip(1)
                .limit(2)
                .toList();

        assertThat(pagedDepts)
                .hasSize(2)
                .containsExactly("Human Resources", "Sales");
    }

    // ========== GROUP BY COUNT (TERMINAL) ==========

    @Test
    void groupByCountReturnsNumberOfGroups() {
        // Count how many distinct departments
        long groupCount = Person.groupBy((Person p) -> p.department.name)
                .count();

        assertThat(groupCount).isEqualTo(3);
    }

    @Test
    void groupByWithHavingCountReturnsFilteredGroupCount() {
        // Count departments with more than 1 person
        long largeGroupCount = Person.groupBy((Person p) -> p.department.name)
                .having((Group<Person, String> g) -> g.count() > 1)
                .count();

        assertThat(largeGroupCount).isEqualTo(2);
    }

    // ========== GROUP BY WITH PRE-FILTER ==========

    @Test
    void whereBeforeGroupByFiltersBeforeGrouping() {
        // Only group active persons
        // Active: John, Jane, Alice, Charlie (Bob is inactive)
        List<DepartmentStatsDTO> stats = Person.where((Person p) -> p.active)
                .groupBy((Person p) -> p.department.name)
                .select((Group<Person, String> g) -> new DepartmentStatsDTO(g.key(), g.count()))
                .toList();

        // Engineering: John, Alice = 2
        // Sales: Jane, Charlie = 2
        // HR: Bob (inactive) = 0 -> should not appear
        assertThat(stats).hasSize(2);
        assertThat(stats).extracting(DepartmentStatsDTO::getDepartmentName)
                .containsExactlyInAnyOrder("Engineering", "Sales");
    }

    // ========== GROUP BY WITH CAPTURED VARIABLES ==========

    @Test
    void groupByWithCapturedVariableInHaving() {
        long minCount = 2;

        List<String> depts = Person.groupBy((Person p) -> p.department.name)
                .having((Group<Person, String> g) -> g.count() >= minCount)
                .toList();

        assertThat(depts)
                .hasSize(2)
                .containsExactlyInAnyOrder("Engineering", "Sales");
    }
}
