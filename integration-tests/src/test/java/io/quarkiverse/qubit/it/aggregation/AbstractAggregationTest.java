package io.quarkiverse.qubit.it.aggregation;

import io.quarkiverse.qubit.it.Person;
import io.quarkiverse.qubit.it.testdata.TestDataFactory;
import io.quarkiverse.qubit.it.testutil.PersonQueryOperations;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Abstract base class for aggregation tests.
 * <p>
 * Tests all aggregation methods: min, max, avg, sumInteger, sumLong, sumDouble.
 * Standard persons: John (30, active, 75000), Jane (25, active, 65000),
 * Bob (45, inactive, 85000), Alice (35, active, 90000), Charlie (28, active, 55000)
 */
public abstract class AbstractAggregationTest {

    protected abstract PersonQueryOperations personOps();

    @BeforeEach
    @Transactional
    protected void setupTestData() {
        TestDataFactory.clearAllData();
        TestDataFactory.createStandardPersons();
    }

    @Test
    void minAge() {
        Integer minAge = personOps().min((Person p) -> p.age).getSingleResult();

        assertThat(minAge).isEqualTo(25); // Jane
    }

    @Test
    void maxAge() {
        Integer maxAge = personOps().max((Person p) -> p.age).getSingleResult();

        assertThat(maxAge).isEqualTo(45); // Bob
    }

    @Test
    void avgAge() {
        Double avgAge = personOps().avg((Person p) -> p.age).getSingleResult();

        assertThat(avgAge).isEqualTo(32.6);
    }

    @Test
    void sumIntegerAge() {
        Long sumAge = personOps().sumInteger((Person p) -> p.age).getSingleResult();

        assertThat(sumAge).isEqualTo(163L);
    }

    @Test
    void sumLongEmployeeId() {
        Long sumEmployeeId = personOps().sumLong((Person p) -> p.employeeId).getSingleResult();

        assertThat(sumEmployeeId).isEqualTo(5000015L);
    }

    @Test
    void sumDoubleSalary() {
        Double sumSalary = personOps().sumDouble((Person p) -> p.salary).getSingleResult();

        assertThat(sumSalary).isEqualTo(370000.0);
    }

    @Test
    void minAgeWithPredicate() {
        Integer minAge = personOps().where((Person p) -> p.active).min((Person p) -> p.age).getSingleResult();

        // Active persons: John (30), Jane (25), Alice (35), Charlie (28)
        assertThat(minAge).isEqualTo(25); // Jane
    }

    @Test
    void maxAgeWithPredicate() {
        Integer maxAge = personOps().where((Person p) -> p.active).max((Person p) -> p.age).getSingleResult();

        // Active persons: John (30), Jane (25), Alice (35), Charlie (28)
        assertThat(maxAge).isEqualTo(35); // Alice
    }

    @Test
    void avgAgeWithPredicate() {
        Double avgAge = personOps().where((Person p) -> p.active).avg((Person p) -> p.age).getSingleResult();

        // Active persons ages: 30, 25, 35, 28
        assertThat(avgAge).isEqualTo(29.5);
    }

    @Test
    void sumIntegerAgeWithPredicate() {
        Long sumAge = personOps().where((Person p) -> p.active).sumInteger((Person p) -> p.age).getSingleResult();

        // Active persons ages: 30, 25, 35, 28
        assertThat(sumAge).isEqualTo(118L);
    }

    @Test
    void sumLongEmployeeIdWithPredicate() {
        Long sumEmployeeId = personOps().where((Person p) -> p.active).sumLong((Person p) -> p.employeeId).getSingleResult();

        // Active persons: 1000001, 1000002, 1000004, 1000005
        assertThat(sumEmployeeId).isEqualTo(4000012L);
    }

    @Test
    void sumDoubleSalaryWithPredicate() {
        Double sumSalary = personOps().where((Person p) -> p.active).sumDouble((Person p) -> p.salary).getSingleResult();

        // Active persons salaries: 75000, 65000, 90000, 55000
        assertThat(sumSalary).isEqualTo(285000.0);
    }

    @Test
    void minAgeWithMultiplePredicates() {
        Integer minAge = personOps().where((Person p) -> p.active).where((Person p) -> p.age > 25).min((Person p) -> p.age)
                .getSingleResult();

        // Active + age > 25: John (30), Alice (35), Charlie (28)
        assertThat(minAge).isEqualTo(28); // Charlie
    }

    @Test
    void maxAgeWithMultiplePredicates() {
        Integer maxAge = personOps().where((Person p) -> p.active).where((Person p) -> p.salary > 60000.0)
                .max((Person p) -> p.age).getSingleResult();

        // Active + salary > 60K: John (30, 75K), Alice (35, 90K), Jane (25, 65K)
        assertThat(maxAge).isEqualTo(35); // Alice
    }

    @Test
    void avgAgeWithMultiplePredicates() {
        Double avgAge = personOps().where((Person p) -> p.active).where((Person p) -> p.age >= 30).avg((Person p) -> p.age)
                .getSingleResult();

        // Active + age >= 30: John (30), Alice (35)
        assertThat(avgAge).isEqualTo(32.5);
    }

    @Test
    void sumIntegerAgeWithMultiplePredicates() {
        Long sumAge = personOps().where((Person p) -> p.active).where((Person p) -> p.age < 35).sumInteger((Person p) -> p.age)
                .getSingleResult();

        // Active + age < 35: John (30), Jane (25), Charlie (28)
        assertThat(sumAge).isEqualTo(83L);
    }

    @Test
    void sumLongEmployeeIdWithMultiplePredicates() {
        Long sumEmployeeId = personOps().where((Person p) -> p.active).where((Person p) -> p.employeeId > 1000002L)
                .sumLong((Person p) -> p.employeeId).getSingleResult();

        // Active + employeeId > 1000002: Alice (1000004), Charlie (1000005)
        assertThat(sumEmployeeId).isEqualTo(2000009L);
    }

    @Test
    void sumDoubleSalaryWithMultiplePredicates() {
        Double sumSalary = personOps().where((Person p) -> p.active).where((Person p) -> p.salary >= 65000.0)
                .sumDouble((Person p) -> p.salary).getSingleResult();

        // Active + salary >= 65K: John (75K), Jane (65K), Alice (90K)
        assertThat(sumSalary).isEqualTo(230000.0);
    }

    @Test
    void minMaxAvg_floatField_height() {
        Float minHeight = personOps().min((Person p) -> p.height).getSingleResult();
        Float maxHeight = personOps().max((Person p) -> p.height).getSingleResult();
        Double avgHeight = personOps().avg((Person p) -> p.height).getSingleResult();

        // Heights: 1.75 (John), 1.68 (Jane), 1.82 (Bob), 1.65 (Alice), 1.78 (Charlie)
        assertThat(minHeight).isEqualTo(1.65f); // Alice
        assertThat(maxHeight).isEqualTo(1.82f); // Bob
        assertThat(avgHeight).isCloseTo(1.736, within(0.001));
    }
}
