package io.quarkus.qusaq.it.repository.aggregation;

import io.quarkus.qusaq.it.Person;
import io.quarkus.qusaq.it.PersonRepository;
import io.quarkus.qusaq.it.testdata.TestDataFactory;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Comprehensive test suite for repository pattern aggregation operations.
 * Tests all aggregation methods: min, max, avg, sumInteger, sumLong, sumDouble.
 * Mirrors io.quarkus.qusaq.it.aggregation.AggregationTest using repository injection.
 *
 * Test coverage (25 tests):
 * - Direct aggregations (6 tests)
 * - Aggregations with single predicate (6 tests)
 * - Aggregations with multiple predicates (6 tests)
 * - Null handling (3 tests)
 * - Empty result sets (3 tests)
 * - Type-specific aggregations (1 test)
 */
@QuarkusTest
class RepositoryAggregationTest {

    @Inject
    PersonRepository repository;

    @BeforeEach
    @Transactional
    void setupTestData() {
        TestDataFactory.clearAllData();
        TestDataFactory.createStandardPersons();
        // Standard persons: John (30, active, 75000), Jane (25, active, 65000),
        // Bob (45, inactive, 85000), Alice (35, active, 90000), Charlie (28, active, 55000)
    }

    // ========== DIRECT AGGREGATIONS (6 tests) ==========

    @Test
    void minAge() {
        Integer minAge = repository.min(p -> p.age).getSingleResult();

        assertThat(minAge).isEqualTo(25); // Jane
    }

    @Test
    void maxAge() {
        Integer maxAge = repository.max(p -> p.age).getSingleResult();

        assertThat(maxAge).isEqualTo(45); // Bob
    }

    @Test
    void avgAge() {
        Double avgAge = repository.avg(p -> p.age).getSingleResult();

        // Average: (30 + 25 + 45 + 35 + 28) / 5 = 163 / 5 = 32.6
        assertThat(avgAge).isEqualTo(32.6);
    }

    @Test
    void sumIntegerAge() {
        Long sumAge = repository.sumInteger(p -> p.age).getSingleResult();

        // Sum: 30 + 25 + 45 + 35 + 28 = 163
        assertThat(sumAge).isEqualTo(163L);
    }

    @Test
    void sumLongEmployeeId() {
        Long sumEmployeeId = repository.sumLong(p -> p.employeeId).getSingleResult();

        // Sum: 1000001 + 1000002 + 1000003 + 1000004 + 1000005 = 5000015
        assertThat(sumEmployeeId).isEqualTo(5000015L);
    }

    @Test
    void sumDoubleSalary() {
        Double sumSalary = repository.sumDouble(p -> p.salary).getSingleResult();

        // Sum: 75000 + 65000 + 85000 + 90000 + 55000 = 370000
        assertThat(sumSalary).isEqualTo(370000.0);
    }

    // ========== AGGREGATIONS WITH SINGLE PREDICATE (6 tests) ==========

    @Test
    void minAgeWithPredicate() {
        Integer minAge = repository.where(p -> p.active).min(p -> p.age).getSingleResult();

        // Active persons: John (30), Jane (25), Alice (35), Charlie (28)
        assertThat(minAge).isEqualTo(25); // Jane
    }

    @Test
    void maxAgeWithPredicate() {
        Integer maxAge = repository.where(p -> p.active).max(p -> p.age).getSingleResult();

        // Active persons: John (30), Jane (25), Alice (35), Charlie (28)
        assertThat(maxAge).isEqualTo(35); // Alice
    }

    @Test
    void avgAgeWithPredicate() {
        Double avgAge = repository.where(p -> p.active).avg(p -> p.age).getSingleResult();

        // Active persons ages: 30, 25, 35, 28
        // Average: (30 + 25 + 35 + 28) / 4 = 118 / 4 = 29.5
        assertThat(avgAge).isEqualTo(29.5);
    }

    @Test
    void sumIntegerAgeWithPredicate() {
        Long sumAge = repository.where(p -> p.active).sumInteger(p -> p.age).getSingleResult();

        // Active persons ages: 30, 25, 35, 28
        // Sum: 30 + 25 + 35 + 28 = 118
        assertThat(sumAge).isEqualTo(118L);
    }

    @Test
    void sumLongEmployeeIdWithPredicate() {
        Long sumEmployeeId = repository.where(p -> p.active).sumLong(p -> p.employeeId).getSingleResult();

        // Active persons: 1000001, 1000002, 1000004, 1000005
        // Sum: 1000001 + 1000002 + 1000004 + 1000005 = 4000012
        assertThat(sumEmployeeId).isEqualTo(4000012L);
    }

    @Test
    void sumDoubleSalaryWithPredicate() {
        Double sumSalary = repository.where(p -> p.active).sumDouble(p -> p.salary).getSingleResult();

        // Active persons salaries: 75000, 65000, 90000, 55000
        // Sum: 75000 + 65000 + 90000 + 55000 = 285000
        assertThat(sumSalary).isEqualTo(285000.0);
    }

    // ========== AGGREGATIONS WITH MULTIPLE PREDICATES (6 tests) ==========

    @Test
    void minAgeWithMultiplePredicates() {
        Integer minAge = repository.where(p -> p.active).where(p -> p.age > 25).min(p -> p.age).getSingleResult();

        // Active + age > 25: John (30), Alice (35), Charlie (28)
        assertThat(minAge).isEqualTo(28); // Charlie
    }

    @Test
    void maxAgeWithMultiplePredicates() {
        Integer maxAge = repository.where(p -> p.active).where(p -> p.salary > 60000.0).max(p -> p.age).getSingleResult();

        // Active + salary > 60K: John (30, 75K), Alice (35, 90K), Jane (25, 65K)
        assertThat(maxAge).isEqualTo(35); // Alice
    }

    @Test
    void avgAgeWithMultiplePredicates() {
        Double avgAge = repository.where(p -> p.active).where(p -> p.age >= 30).avg(p -> p.age).getSingleResult();

        // Active + age >= 30: John (30), Alice (35)
        // Average: (30 + 35) / 2 = 32.5
        assertThat(avgAge).isEqualTo(32.5);
    }

    @Test
    void sumIntegerAgeWithMultiplePredicates() {
        Long sumAge = repository.where(p -> p.active).where(p -> p.age < 35).sumInteger(p -> p.age).getSingleResult();

        // Active + age < 35: John (30), Jane (25), Charlie (28)
        // Sum: 30 + 25 + 28 = 83
        assertThat(sumAge).isEqualTo(83L);
    }

    @Test
    void sumLongEmployeeIdWithMultiplePredicates() {
        Long sumEmployeeId = repository.where(p -> p.active).where(p -> p.employeeId > 1000002L).sumLong(p -> p.employeeId).getSingleResult();

        // Active + employeeId > 1000002: Alice (1000004), Charlie (1000005)
        // Sum: 1000004 + 1000005 = 2000009
        assertThat(sumEmployeeId).isEqualTo(2000009L);
    }

    @Test
    void sumDoubleSalaryWithMultiplePredicates() {
        Double sumSalary = repository.where(p -> p.active).where(p -> p.salary >= 65000.0).sumDouble(p -> p.salary).getSingleResult();

        // Active + salary >= 65K: John (75K), Jane (65K), Alice (90K)
        // Sum: 75000 + 65000 + 90000 = 230000
        assertThat(sumSalary).isEqualTo(230000.0);
    }

    // ========== NULL HANDLING (3 tests) ==========

    @Test
    @Transactional
    void minHeight_withNullValues_skipsNulls() {
        // Create a person with null height
        Person personWithNullHeight = new Person();
        personWithNullHeight.firstName = "NullHeight";
        personWithNullHeight.lastName = "Person";
        personWithNullHeight.age = 40;
        personWithNullHeight.height = null;
        personWithNullHeight.persist();

        Float minHeight = repository.min(p -> p.height).getSingleResult();

        // Should skip null and return 1.65 (Alice)
        assertThat(minHeight).isEqualTo(1.65f);
    }

    @Test
    @Transactional
    void avgSalary_withNullValues_skipsNulls() {
        // Create a person with null salary
        Person personWithNullSalary = new Person();
        personWithNullSalary.firstName = "NullSalary";
        personWithNullSalary.lastName = "Person";
        personWithNullSalary.age = 40;
        personWithNullSalary.salary = null;
        personWithNullSalary.persist();

        Double avgSalary = repository.avg(p -> p.salary).getSingleResult();

        // Should skip null and compute average of 5 standard persons
        // Average: 370000 / 5 = 74000
        assertThat(avgSalary).isEqualTo(74000.0);
    }

    @Test
    @Transactional
    void sumLongEmployeeId_withNullValues_skipsNulls() {
        // Create a person with null employeeId
        Person personWithNullEmployeeId = new Person();
        personWithNullEmployeeId.firstName = "NullEmpId";
        personWithNullEmployeeId.lastName = "Person";
        personWithNullEmployeeId.age = 40;
        personWithNullEmployeeId.employeeId = null;
        personWithNullEmployeeId.persist();

        Long sumEmployeeId = repository.sumLong(p -> p.employeeId).getSingleResult();

        // Should skip null and sum 5 standard persons: 5000015
        assertThat(sumEmployeeId).isEqualTo(5000015L);
    }

    // ========== EMPTY RESULT SETS (3 tests) ==========

    @Test
    @Transactional
    void minAge_emptyResultSet_returnsNull() {
        Person.deleteAll();

        Integer minAge = repository.min(p -> p.age).getSingleResult();

        assertThat(minAge).isNull();
    }

    @Test
    @Transactional
    void avgSalary_emptyResultSet_returnsNull() {
        Person.deleteAll();

        Double avgSalary = repository.avg(p -> p.salary).getSingleResult();

        assertThat(avgSalary).isNull();
    }

    @Test
    @Transactional
    void sumDoubleSalary_emptyResultSet_returnsNull() {
        Person.deleteAll();

        Double sumSalary = repository.sumDouble(p -> p.salary).getSingleResult();

        assertThat(sumSalary).isNull();
    }

    // ========== TYPE-SPECIFIC AGGREGATIONS (1 test) ==========

    @Test
    void minMaxAvg_floatField_height() {
        Float minHeight = repository.min(p -> p.height).getSingleResult();
        Float maxHeight = repository.max(p -> p.height).getSingleResult();
        Double avgHeight = repository.avg(p -> p.height).getSingleResult();

        // Heights: 1.75 (John), 1.68 (Jane), 1.82 (Bob), 1.65 (Alice), 1.78 (Charlie)
        assertThat(minHeight).isEqualTo(1.65f);  // Alice
        assertThat(maxHeight).isEqualTo(1.82f);  // Bob
        // Average: (1.75 + 1.68 + 1.82 + 1.65 + 1.78) / 5 = 8.68 / 5 = 1.736
        assertThat(avgHeight).isCloseTo(1.736, within(0.001));
    }
}
