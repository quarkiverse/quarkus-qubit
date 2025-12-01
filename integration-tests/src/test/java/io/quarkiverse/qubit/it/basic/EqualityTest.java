package io.quarkiverse.qubit.it.basic;

import io.quarkiverse.qubit.it.Person;
import io.quarkiverse.qubit.it.Product;
import io.quarkiverse.qubit.it.testdata.TestDataFactory;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for equality operations on various field types.
 */
@QuarkusTest
class EqualityTest {

    @BeforeEach
    @Transactional
    void setupTestData() {
        TestDataFactory.clearAllData();
        TestDataFactory.createMinimalPersons();
        TestDataFactory.createStandardProducts();
    }

    @Test
    void stringEqualityByFieldAccess() {
        var results = Person.where((Person p) -> p.firstName.equals("John")).toList();

        assertThat(results)
                .hasSize(1)
                .extracting(Person::getFirstName)
                .containsExactly("John");
    }

    @Test
    void stringEqualityByGetter() {
        var results = Person.where((Person p) -> p.getLastName().equals("Smith")).toList();

        assertThat(results)
                .hasSize(1)
                .extracting(Person::getLastName)
                .containsExactly("Smith");
    }

    @Test
    void integerEquality() {
        var results = Person.where((Person p) -> p.age == 30).toList();

        assertThat(results)
                .hasSize(1)
                .extracting(Person::getAge)
                .containsExactly(30);
    }

    @Test
    void booleanEqualityImplicit() {
        var results = Person.where((Person p) -> p.active).toList();

        assertThat(results)
                .hasSizeGreaterThanOrEqualTo(2)
                .allMatch(Person::isActive);
    }

    @Test
    void booleanEqualityTrue() {
        var results = Person.where((Person p) -> p.active == true).toList();

        assertThat(results)
                .hasSizeGreaterThan(0)
                .allMatch(Person::isActive);
    }

    @Test
    void booleanEqualityFalse() {
        var results = Person.where((Person p) -> p.active == false).toList();

        assertThat(results)
                .hasSizeGreaterThan(0)
                .noneMatch(Person::isActive);
    }

    @Test
    void longEquality() {
        var results = Person.where((Person p) -> p.employeeId == 1000001L).toList();

        assertThat(results)
                .hasSize(1)
                .extracting(Person::getEmployeeId)
                .containsExactly(1000001L);
    }

    @Test
    void floatEquality() {
        var results = Person.where((Person p) -> p.height == 1.75f).toList();

        assertThat(results)
                .hasSize(1)
                .extracting(Person::getHeight)
                .containsExactly(1.75f);
    }

    @Test
    void doubleEquality() {
        var results = Person.where((Person p) -> p.salary == 75000.0).toList();

        assertThat(results)
                .hasSize(1)
                .allMatch(p -> p.getSalary() == 75000.0);
    }

    @Test
    void localDateTimeEquality() {
        var results = Person.where((Person p) -> p.createdAt.isEqual(LocalDateTime.of(2024, 1, 15, 9, 30))).toList();

        assertThat(results)
                .hasSize(1)
                .extracting(Person::getCreatedAt)
                .containsExactly(LocalDateTime.of(2024, 1, 15, 9, 30));
    }

    @Test
    void localTimeEquality() {
        var results = Person.where((Person p) -> p.startTime.equals(LocalTime.of(9, 0))).toList();

        assertThat(results)
                .hasSize(1)
                .extracting(Person::getStartTime)
                .containsExactly(LocalTime.of(9, 0));
    }

    @Test
    void localDateEquality() {
        var results = Person.where((Person p) -> p.birthDate.isEqual(LocalDate.of(1993, 5, 15))).toList();

        assertThat(results)
                .hasSizeGreaterThan(0)
                .allMatch(p -> p.getBirthDate().isEqual(LocalDate.of(1993, 5, 15)));
    }

    @Test
    void bigDecimalEquality() {
        var results = Product.where((Product p) -> p.price.compareTo(new BigDecimal("899.99")) == 0).toList();

        assertThat(results)
                .hasSize(1)
                .allMatch(p -> p.getPrice().compareTo(new BigDecimal("899.99")) == 0);
    }
}
