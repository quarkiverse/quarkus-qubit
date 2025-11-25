package io.quarkus.qusaq.it.repository.arithmetic;

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
 * Repository pattern tests for arithmetic operations on numeric fields.
 * Mirrors io.quarkus.qusaq.it.arithmetic.ArithmeticOperationsTest using repository injection.
 */
@QuarkusTest
class RepositoryArithmeticOperationsTest {

    @Inject
    PersonRepository personRepository;

    @BeforeEach
    @Transactional
    void setupTestData() {
        TestDataFactory.clearAllData();
        TestDataFactory.createAdditionalPersons();
    }

    // Integer arithmetic
    @Test
    void integerAddition() {
        var results = personRepository.where((Person p) -> p.age + 5 > 35).toList();

        assertThat(results)
                .hasSizeGreaterThan(0)
                .allMatch(p -> p.getAge() + 5 > 35);
    }

    @Test
    void integerSubtraction() {
        var results = personRepository.where((Person p) -> p.age - 5 > 20).toList();

        assertThat(results)
                .hasSizeGreaterThan(0)
                .allMatch(p -> p.getAge() - 5 > 20);
    }

    @Test
    void integerMultiplication() {
        var results = personRepository.where((Person p) -> p.age * 2 > 60).toList();

        assertThat(results)
                .hasSizeGreaterThan(0)
                .allMatch(p -> p.getAge() * 2 > 60);
    }

    @Test
    void integerDivision() {
        var results = personRepository.where((Person p) -> p.age / 2 > 15).toList();

        assertThat(results)
                .hasSizeGreaterThan(0)
                .allMatch(p -> p.getAge() / 2 > 15);
    }

    @Test
    void integerModulo() {
        var results = personRepository.where((Person p) -> p.age % 10 == 0).toList();

        assertThat(results)
                .hasSizeGreaterThan(0)
                .allMatch(p -> p.getAge() % 10 == 0);
    }

    // Long arithmetic
    @Test
    void longAddition() {
        var results = personRepository.where((Person p) -> p.employeeId + 10L > 1000010L).toList();

        assertThat(results)
                .hasSizeGreaterThan(0)
                .allMatch(p -> p.getEmployeeId() + 10L > 1000010L);
    }

    @Test
    void longSubtraction() {
        var results = personRepository.where((Person p) -> p.employeeId - 10L < 1000000L).toList();

        assertThat(results)
                .hasSizeGreaterThan(0)
                .allMatch(p -> p.getEmployeeId() - 10L < 1000000L);
    }

    @Test
    void longMultiplication() {
        var results = personRepository.where((Person p) -> p.employeeId * 2L > 2000000L).toList();

        assertThat(results)
                .hasSizeGreaterThan(0)
                .allMatch(p -> p.getEmployeeId() * 2L > 2000000L);
    }

    @Test
    void longDivision() {
        var results = personRepository.where((Person p) -> p.employeeId / 2L < 500002L).toList();

        assertThat(results)
                .hasSizeGreaterThan(0)
                .allMatch(p -> p.getEmployeeId() / 2L < 500002L);
    }

    @Test
    void longModulo() {
        var results = personRepository.where((Person p) -> p.employeeId % 2L == 1L).toList();

        assertThat(results)
                .hasSizeGreaterThan(0)
                .allMatch(p -> p.getEmployeeId() % 2L == 1L);
    }

    // Float arithmetic
    @Test
    void floatAddition() {
        var results = personRepository.where((Person p) -> p.height + 0.10f > 1.85f).toList();

        assertThat(results)
                .hasSizeGreaterThan(0)
                .allMatch(p -> p.getHeight() + 0.10f > 1.85f);
    }

    @Test
    void floatSubtraction() {
        var results = personRepository.where((Person p) -> p.height - 0.05f < 1.70f).toList();

        assertThat(results)
                .hasSizeGreaterThan(0)
                .allMatch(p -> p.getHeight() - 0.05f < 1.70f);
    }

    @Test
    void floatMultiplication() {
        var results = personRepository.where((Person p) -> p.height * 2.0f > 3.5f).toList();

        assertThat(results)
                .hasSizeGreaterThan(0)
                .allMatch(p -> p.getHeight() * 2.0f > 3.5f);
    }

    @Test
    void floatDivision() {
        var results = personRepository.where((Person p) -> p.height / 2.0f < 0.85f).toList();

        assertThat(results)
                .hasSizeGreaterThan(0)
                .allMatch(p -> p.getHeight() / 2.0f < 0.85f);
    }

    // Double arithmetic
    @Test
    void doubleAddition() {
        var results = personRepository.where((Person p) -> p.salary + 5000.0 > 80000.0).toList();

        assertThat(results)
                .hasSizeGreaterThan(0)
                .allMatch(p -> p.getSalary() + 5000.0 > 80000.0);
    }

    @Test
    void doubleSubtraction() {
        var results = personRepository.where((Person p) -> p.salary - 10000.0 < 70000.0).toList();

        assertThat(results)
                .hasSizeGreaterThan(0)
                .allMatch(p -> p.getSalary() - 10000.0 < 70000.0);
    }

    @Test
    void doubleMultiplication() {
        var results = personRepository.where((Person p) -> p.salary * 1.1 > 80000.0).toList();

        assertThat(results)
                .hasSizeGreaterThan(0)
                .allMatch(p -> p.getSalary() * 1.1 > 80000.0);
    }

    @Test
    void doubleDivision() {
        var results = personRepository.where((Person p) -> p.salary / 1000.0 > 75.0).toList();

        assertThat(results)
                .hasSizeGreaterThan(0)
                .allMatch(p -> p.getSalary() / 1000.0 > 75.0);
    }

    // Field-field arithmetic expressions
    @Test
    void longFieldFieldAddition() {
        var results = personRepository.where((Person p) ->
                p.employeeId + p.employeeId > 2000000L
        ).toList();

        assertThat(results)
                .hasSizeGreaterThan(0)
                .allMatch(p -> p.getEmployeeId() + p.getEmployeeId() > 2000000L);
    }

    @Test
    void longFieldFieldSubtraction() {
        var results = personRepository.where((Person p) ->
                p.employeeId - p.employeeId == 0L
        ).toList();

        assertThat(results)
                .hasSizeGreaterThan(0)
                .allMatch(p -> p.getEmployeeId() - p.getEmployeeId() == 0L);
    }

    @Test
    void longFieldFieldMultiplication() {
        var results = personRepository.where((Person p) ->
                p.employeeId * p.employeeId > 1000000000000L
        ).toList();

        assertThat(results)
                .hasSizeGreaterThan(0)
                .allMatch(p -> p.getEmployeeId() * p.getEmployeeId() > 1000000000000L);
    }

    @Test
    void longFieldFieldDivision() {
        var results = personRepository.where((Person p) ->
                p.employeeId / p.employeeId == 1L
        ).toList();

        assertThat(results)
                .hasSizeGreaterThan(0)
                .allMatch(p -> p.getEmployeeId() / p.getEmployeeId() == 1L);
    }

    @Test
    void floatFieldFieldAddition() {
        var results = personRepository.where((Person p) -> p.height + p.height > 3.0f).toList();

        assertThat(results)
                .hasSizeGreaterThan(0)
                .allMatch(p -> p.getHeight() + p.getHeight() > 3.0f);
    }

    @Test
    void floatFieldFieldSubtraction() {
        var results = personRepository.where((Person p) -> p.height - p.height == 0.0f).toList();

        assertThat(results)
                .hasSize(6)
                .allMatch(p -> p.getHeight() - p.getHeight() == 0.0f);
    }

    @Test
    void floatFieldFieldMultiplication() {
        var results = personRepository.where((Person p) -> p.height * p.height > 2.0f).toList();

        assertThat(results)
                .hasSizeGreaterThan(0)
                .allMatch(p -> p.getHeight() * p.getHeight() > 2.0f);
    }

    @Test
    void floatFieldFieldDivision() {
        var results = personRepository.where((Person p) -> p.height / p.height == 1.0f).toList();

        assertThat(results)
                .hasSize(6)
                .allMatch(p -> p.getHeight() / p.getHeight() == 1.0f);
    }

    @Test
    void doubleFieldFieldAddition() {
        var results = personRepository.where((Person p) -> p.salary + p.salary > 170000.0).toList();

        assertThat(results)
                .hasSizeGreaterThan(0)
                .allMatch(p -> p.getSalary() + p.getSalary() > 170000.0);
    }

    @Test
    void doubleFieldFieldSubtraction() {
        var results = personRepository.where((Person p) -> p.salary - p.salary == 0.0).toList();

        assertThat(results)
                .hasSize(6)
                .allMatch(p -> p.getSalary() - p.getSalary() == 0.0);
    }

    @Test
    void doubleFieldFieldMultiplication() {
        var results = personRepository.where((Person p) -> p.salary * p.salary > 8000000000.0).toList();

        assertThat(results)
                .hasSizeGreaterThan(0)
                .allMatch(p -> p.getSalary() * p.getSalary() > 8000000000.0);
    }

    @Test
    void doubleFieldFieldDivision() {
        var results = personRepository.where((Person p) -> p.salary / p.salary == 1.0).toList();

        assertThat(results)
                .hasSize(6)
                .allMatch(p -> p.getSalary() / p.getSalary() == 1.0);
    }

    // Type conversion tests (wrapper types with int constants)
    @Test
    void longWrapperWithIntConstant() {
        var results = personRepository.where((Person p) -> p.employeeId + 5 == 1000006L).toList();

        assertThat(results)
                .hasSize(1)
                .allMatch(p -> p.getEmployeeId() + 5 == 1000006L);
    }

    @Test
    void doubleWrapperWithIntConstant() {
        var results = personRepository.where((Person p) -> p.salary + 1000 == 76000.0).toList();

        assertThat(results)
                .hasSize(1)
                .allMatch(p -> p.getSalary() + 1000 == 76000.0);
    }

    @Test
    void longWrapperSubtractionWithIntConstant() {
        var results = personRepository.where((Person p) -> p.employeeId - 3 == 999999L).toList();

        assertThat(results)
                .hasSize(1)
                .allMatch(p -> p.getEmployeeId() - 3 == 999999L);
    }

    @Test
    void doubleWrapperMultiplicationWithIntConstant() {
        var results = personRepository.where((Person p) -> p.salary * 2 == 180000.0).toList();

        assertThat(results)
                .hasSize(1)
                .allMatch(p -> p.getSalary() * 2 == 180000.0);
    }

    @Test
    void floatWrapperComparisonWithIntConstant() {
        var results = personRepository.where((Person p) -> p.height > 1).toList();

        assertThat(results)
                .hasSize(6)
                .allMatch(p -> p.getHeight() > 1);
    }
}
