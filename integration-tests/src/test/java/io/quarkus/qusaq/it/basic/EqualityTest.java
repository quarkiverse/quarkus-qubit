package io.quarkus.qusaq.it.basic;

import io.quarkus.qusaq.it.Person;
import io.quarkus.qusaq.it.Product;
import io.quarkus.qusaq.it.testdata.TestDataFactory;
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
        var results = Person.findWhere((Person p) -> p.firstName.equals("John"));

        assertThat(results)
                .hasSize(1)
                .extracting(Person::getFirstName)
                .containsExactly("John");
    }

    @Test
    void stringEqualityByGetter() {
        var results = Person.findWhere((Person p) -> p.getLastName().equals("Smith"));

        assertThat(results)
                .hasSize(1)
                .extracting(Person::getLastName)
                .containsExactly("Smith");
    }

    @Test
    void integerEquality() {
        var results = Person.findWhere((Person p) -> p.age == 30);

        assertThat(results)
                .hasSize(1)
                .extracting(Person::getAge)
                .containsExactly(30);
    }

    @Test
    void booleanEqualityImplicit() {
        var results = Person.findWhere((Person p) -> p.active);

        assertThat(results)
                .hasSizeGreaterThanOrEqualTo(2)
                .allMatch(Person::isActive);
    }

    @Test
    void booleanEqualityTrue() {
        var results = Person.findWhere((Person p) -> p.active == true);

        assertThat(results)
                .hasSizeGreaterThan(0)
                .allMatch(Person::isActive);
    }

    @Test
    void booleanEqualityFalse() {
        var results = Person.findWhere((Person p) -> p.active == false);

        assertThat(results)
                .hasSizeGreaterThan(0)
                .noneMatch(Person::isActive);
    }

    @Test
    void longEquality() {
        var results = Person.findWhere((Person p) -> p.employeeId == 1000001L);

        assertThat(results)
                .hasSize(1)
                .extracting(Person::getEmployeeId)
                .containsExactly(1000001L);
    }

    @Test
    void floatEquality() {
        var results = Person.findWhere((Person p) -> p.height == 1.75f);

        assertThat(results)
                .hasSize(1)
                .extracting(Person::getHeight)
                .containsExactly(1.75f);
    }

    @Test
    void doubleEquality() {
        var results = Person.findWhere((Person p) -> p.salary == 75000.0);

        assertThat(results)
                .hasSize(1)
                .allMatch(p -> p.getSalary() == 75000.0);
    }

    @Test
    void localDateTimeEquality() {
        var results = Person.findWhere((Person p) -> p.createdAt.isEqual(LocalDateTime.of(2024, 1, 15, 9, 30)));

        assertThat(results)
                .hasSize(1)
                .extracting(Person::getCreatedAt)
                .containsExactly(LocalDateTime.of(2024, 1, 15, 9, 30));
    }

    @Test
    void localTimeEquality() {
        var results = Person.findWhere((Person p) -> p.startTime.equals(LocalTime.of(9, 0)));

        assertThat(results)
                .hasSize(1)
                .extracting(Person::getStartTime)
                .containsExactly(LocalTime.of(9, 0));
    }

    @Test
    void localDateEquality() {
        var results = Person.findWhere((Person p) -> p.birthDate.isEqual(LocalDate.of(1993, 5, 15)));

        assertThat(results)
                .hasSizeGreaterThan(0)
                .allMatch(p -> p.getBirthDate().isEqual(LocalDate.of(1993, 5, 15)));
    }

    @Test
    void bigDecimalEquality() {
        var results = Product.findWhere((Product p) -> p.price.compareTo(new BigDecimal("899.99")) == 0);

        assertThat(results)
                .hasSize(1)
                .allMatch(p -> p.getPrice().compareTo(new BigDecimal("899.99")) == 0);
    }
}
