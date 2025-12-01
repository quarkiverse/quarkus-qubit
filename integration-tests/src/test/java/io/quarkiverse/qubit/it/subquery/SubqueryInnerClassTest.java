package io.quarkiverse.qubit.it.subquery;

import io.quarkiverse.qubit.runtime.QubitEntity;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.persistence.Entity;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static io.quarkiverse.qubit.runtime.Subqueries.subquery;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test case for the createPlaceholderClass() fix.
 * <p>
 * This test uses an inner entity class that may not be loadable via Class.forName()
 * during Quarkus build-time bytecode analysis. The fix preserves the className
 * in the AST and uses runtime class loading (Class.forName) during code generation.
 */
@QuarkusTest
@DisplayName("Subquery with Inner Entity Class (Placeholder Fix Test)")
class SubqueryInnerClassTest {

    /**
     * Inner entity class for testing.
     * <p>
     * This class may not be loadable during bytecode analysis via Class.forName()
     * in certain build configurations, triggering the placeholder code path.
     * <p>
     * Full class name: io.quarkiverse.qubit.it.subquery.SubqueryInnerClassTest$TestPerson
     */
    @Entity
    public static class TestPerson extends QubitEntity {
        public String name;
        public Double salary;
        public Integer age;
        public Boolean active;

        public TestPerson() {
        }

        public TestPerson(String name, Double salary, Integer age, Boolean active) {
            this.name = name;
            this.salary = salary;
            this.age = age;
            this.active = active;
        }
    }

    @BeforeEach
    @Transactional
    void setupTestData() {
        TestPerson.deleteAll();

        TestPerson.persist(new TestPerson("Alice", 90000.0, 35, true));
        TestPerson.persist(new TestPerson("Bob", 75000.0, 28, true));
        TestPerson.persist(new TestPerson("Charlie", 60000.0, 42, false));
        TestPerson.persist(new TestPerson("Diana", 85000.0, 31, true));
        TestPerson.persist(new TestPerson("Eve", 55000.0, 26, false));
    }

    @Test
    @DisplayName("AVG subquery with inner entity class")
    @Transactional
    void avgSubqueryWithInnerEntityClass() {
        // Average salary: (90000 + 75000 + 60000 + 85000 + 55000) / 5 = 73000
        // Above average: Alice (90000), Bob (75000), Diana (85000)

        List<TestPerson> aboveAvg = TestPerson.where(
            (TestPerson p) -> p.salary > subquery(TestPerson.class).avg(q -> q.salary)
        ).toList();

        assertThat(aboveAvg)
            .as("Should find people earning above average salary")
            .hasSize(3)
            .extracting(p -> p.name)
            .containsExactlyInAnyOrder("Alice", "Bob", "Diana");
    }

    @Test
    @DisplayName("SUM subquery with inner entity class")
    @Transactional
    void sumSubqueryWithInnerEntityClass() {
        // Sum of all salaries: 365000
        // All individual salaries are less than total sum

        List<TestPerson> belowTotal = TestPerson.where(
            (TestPerson p) -> p.salary < subquery(TestPerson.class).sum(q -> q.salary)
        ).toList();

        assertThat(belowTotal).hasSize(5);
    }

    @Test
    @DisplayName("Filtered AVG subquery with inner entity class")
    @Transactional
    void filteredAvgSubqueryWithInnerEntityClass() {
        // Active employees: Alice (90000), Bob (75000), Diana (85000)
        // Active average: (90000 + 75000 + 85000) / 3 = 83333.33
        // Active above active average: Alice (90000), Diana (85000)

        List<TestPerson> activeAboveActiveAvg = TestPerson.where(
            (TestPerson p) -> p.active && p.salary > subquery(TestPerson.class)
                .where(q -> q.active)
                .avg(q -> q.salary)
        ).toList();

        assertThat(activeAboveActiveAvg)
            .hasSize(2)
            .extracting(p -> p.name)
            .containsExactlyInAnyOrder("Alice", "Diana");
    }

    @Test
    @DisplayName("COUNT subquery with inner entity class")
    @Transactional
    void countSubqueryWithInnerEntityClass() {
        // Total count: 5
        // Ages greater than count: Alice (35), Charlie (42), Diana (31), Bob (28), Eve (26)

        List<TestPerson> olderThanCount = TestPerson.where(
            (TestPerson p) -> (long) p.age > subquery(TestPerson.class).count()
        ).toList();

        assertThat(olderThanCount).hasSize(5);
    }

    @Test
    @DisplayName("MAX subquery with inner entity class")
    @Transactional
    void maxSubqueryWithInnerEntityClass() {
        // Max salary: 90000 (Alice)

        List<TestPerson> maxSalaryPerson = TestPerson.where(
            (TestPerson p) -> p.salary.equals(subquery(TestPerson.class).max(q -> q.salary))
        ).toList();

        assertThat(maxSalaryPerson)
            .hasSize(1)
            .extracting(p -> p.name)
            .containsExactly("Alice");
    }

    @Test
    @DisplayName("MIN subquery with inner entity class")
    @Transactional
    void minSubqueryWithInnerEntityClass() {
        // Min salary: 55000 (Eve)

        List<TestPerson> minSalaryPerson = TestPerson.where(
            (TestPerson p) -> p.salary.equals(subquery(TestPerson.class).min(q -> q.salary))
        ).toList();

        assertThat(minSalaryPerson)
            .hasSize(1)
            .extracting(p -> p.name)
            .containsExactly("Eve");
    }
}
