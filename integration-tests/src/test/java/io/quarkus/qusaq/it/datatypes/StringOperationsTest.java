package io.quarkus.qusaq.it.datatypes;

import io.quarkus.qusaq.it.Person;
import io.quarkus.qusaq.it.testdata.TestDataFactory;
import io.quarkus.qusaq.it.Product;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;


import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for String operations in queries.
 */
@QuarkusTest
class StringOperationsTest {

    @BeforeEach
    @Transactional
    void setupTestData() {
        TestDataFactory.clearAllData();
        TestDataFactory.createPersonsForStringTests();
        TestDataFactory.createStandardProducts();
    }

    @Test
    void stringNotEquals() {
        var results = Person.findWhere((Person p) -> !p.firstName.equals("John"));

        assertThat(results)
                .hasSizeGreaterThan(0)
                .noneMatch(p -> p.getFirstName().equals("John"));
    }

    @Test
    void stringStartsWith() {
        var results = Person.findWhere((Person p) -> p.firstName.startsWith("J"));

        assertThat(results)
                .hasSizeGreaterThan(0)
                .allMatch(p -> p.getFirstName().startsWith("J"));
    }

    @Test
    void stringEndsWith() {
        var results = Person.findWhere((Person p) -> p.email.endsWith("@example.com"));

        assertThat(results)
                .hasSizeGreaterThan(0)
                .allMatch(p -> p.getEmail().endsWith("@example.com"));
    }

    @Test
    void stringContains() {
        var results = Person.findWhere((Person p) -> p.email.contains("john"));

        assertThat(results)
                .hasSizeGreaterThan(0)
                .allMatch(p -> p.getEmail().contains("john"));
    }

    @Test
    void stringLength() {
        var results = Person.findWhere((Person p) -> p.firstName.length() > 4);

        assertThat(results)
                .hasSizeGreaterThan(0)
                .allMatch(p -> p.getFirstName().length() > 4);
    }

    @Test
    void stringToLowerCase() {
        var results = Person.findWhere((Person p) -> p.firstName.toLowerCase().equals("john"));

        assertThat(results)
                .hasSizeGreaterThan(0)
                .allMatch(p -> p.getFirstName().toLowerCase().equals("john"));
    }

    @Test
    void stringToUpperCase() {
        var results = Person.findWhere((Person p) -> p.firstName.toUpperCase().equals("JANE"));

        assertThat(results)
                .hasSizeGreaterThan(0)
                .allMatch(p -> p.getFirstName().toUpperCase().equals("JANE"));
    }

    @Test
    void stringTrim() {
        var results = Person.findWhere((Person p) -> p.email.trim().equals("david.miller@example.com"));

        assertThat(results)
                .hasSize(1)
                .allMatch(p -> p.getEmail().trim().equals("david.miller@example.com"));
    }

    @Test
    void stringIsEmpty() {
        var results = Person.findWhere((Person p) -> p.email.isEmpty());

        assertThat(results)
                .hasSize(1)
                .allMatch(p -> p.getEmail().isEmpty());
    }

    @Test
    void stringSubstring() {
        var results = Person.findWhere((Person p) -> p.firstName.substring(0, 4).equals("John"));

        assertThat(results)
                .hasSize(1)
                .allMatch(p -> p.getFirstName().substring(0, 4).equals("John"));
    }

    @Test
    void stringMethodChaining() {
        var results = Person.findWhere((Person p) ->
                p.email.toLowerCase().contains("example")
        );

        assertThat(results)
                .hasSizeGreaterThan(0)
                .allMatch(p -> p.getEmail().toLowerCase().contains("example"));
    }

    @Test
    void stringComplexConditions() {
        var results = Person.findWhere((Person p) ->
                p.email != null && p.email.contains("@") && p.email.endsWith(".com")
        );

        assertThat(results)
                .hasSizeGreaterThan(0)
                .allMatch(p -> p.getEmail() != null &&
                              p.getEmail().contains("@") &&
                              p.getEmail().endsWith(".com"));
    }

    @Test
    void productDescriptionIsEmpty() {
        var results = Product.findWhere((Product p) -> p.description.isEmpty());

        assertThat(results).isEmpty();
    }

    @Test
    void productDescriptionLength() {
        var results = Product.findWhere((Product p) -> p.description.length() > 10);

        assertThat(results)
                .hasSizeGreaterThan(0)
                .allMatch(p -> p.getDescription().length() > 10);
    }
}
