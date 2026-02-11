package io.quarkiverse.qubit.it.datatypes;

import io.quarkiverse.qubit.it.Person;
import io.quarkiverse.qubit.it.Product;
import io.quarkiverse.qubit.it.testdata.TestDataFactory;
import io.quarkiverse.qubit.it.testutil.PersonQueryOperations;
import io.quarkiverse.qubit.it.testutil.ProductQueryOperations;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Abstract base class for String operation tests.
 *
 * <p>
 * Contains all test methods that can be run with either static entity methods
 * or repository instance methods. Subclasses provide the appropriate query operations.
 *
 * <p>
 * This eliminates duplication between StringOperationsTest and RepositoryStringOperationsTest.
 */
public abstract class AbstractStringOperationsTest {

    /**
     * Returns the Person query operations to use for tests.
     */
    protected abstract PersonQueryOperations personOps();

    /**
     * Returns the Product query operations to use for tests.
     */
    protected abstract ProductQueryOperations productOps();

    @BeforeEach
    @Transactional
    protected void setupTestData() {
        TestDataFactory.clearAllData();
        TestDataFactory.createPersonsForStringTests();
        TestDataFactory.createStandardProducts();
    }

    @Test
    void stringNotEquals() {
        var results = personOps().where((Person p) -> !p.firstName.equals("John")).toList();

        assertThat(results)
                .hasSizeGreaterThan(0)
                .noneMatch(p -> p.getFirstName().equals("John"));
    }

    @Test
    void stringStartsWith() {
        var results = personOps().where((Person p) -> p.firstName.startsWith("J")).toList();

        assertThat(results)
                .hasSizeGreaterThan(0)
                .allMatch(p -> p.getFirstName().startsWith("J"));
    }

    @Test
    void stringEndsWith() {
        var results = personOps().where((Person p) -> p.email.endsWith("@example.com")).toList();

        assertThat(results)
                .hasSizeGreaterThan(0)
                .allMatch(p -> p.getEmail().endsWith("@example.com"));
    }

    @Test
    void stringContains() {
        var results = personOps().where((Person p) -> p.email.contains("john")).toList();

        assertThat(results)
                .hasSizeGreaterThan(0)
                .allMatch(p -> p.getEmail().contains("john"));
    }

    @Test
    void stringLength() {
        var results = personOps().where((Person p) -> p.firstName.length() > 4).toList();

        assertThat(results)
                .hasSizeGreaterThan(0)
                .allMatch(p -> p.getFirstName().length() > 4);
    }

    @Test
    void stringToLowerCase() {
        var results = personOps().where((Person p) -> p.firstName.toLowerCase().equals("john")).toList();

        assertThat(results)
                .hasSizeGreaterThan(0)
                .allMatch(p -> p.getFirstName().toLowerCase().equals("john"));
    }

    @Test
    void stringToUpperCase() {
        var results = personOps().where((Person p) -> p.firstName.toUpperCase().equals("JANE")).toList();

        assertThat(results)
                .hasSizeGreaterThan(0)
                .allMatch(p -> p.getFirstName().toUpperCase().equals("JANE"));
    }

    @Test
    void stringTrim() {
        var results = personOps().where((Person p) -> p.email.trim().equals("david.miller@example.com")).toList();

        assertThat(results)
                .hasSize(1)
                .allMatch(p -> p.getEmail().trim().equals("david.miller@example.com"));
    }

    @Test
    void stringIsEmpty() {
        var results = personOps().where((Person p) -> p.email.isEmpty()).toList();

        assertThat(results)
                .hasSize(1)
                .allMatch(p -> p.getEmail().isEmpty());
    }

    @Test
    void stringSubstring() {
        var results = personOps().where((Person p) -> p.firstName.substring(0, 4).equals("John")).toList();

        assertThat(results)
                .hasSize(1)
                .allMatch(p -> p.getFirstName().substring(0, 4).equals("John"));
    }

    @Test
    void stringMethodChaining() {
        var results = personOps().where((Person p) -> p.email.toLowerCase().contains("example")).toList();

        assertThat(results)
                .hasSizeGreaterThan(0)
                .allMatch(p -> p.getEmail().toLowerCase().contains("example"));
    }

    @Test
    void stringComplexConditions() {
        var results = personOps().where((Person p) -> p.email != null && p.email.contains("@") && p.email.endsWith(".com"))
                .toList();

        assertThat(results)
                .hasSizeGreaterThan(0)
                .allMatch(p -> p.getEmail() != null &&
                        p.getEmail().contains("@") &&
                        p.getEmail().endsWith(".com"));
    }

    @Test
    void productDescriptionIsEmpty() {
        var results = productOps().where((Product p) -> p.description.isEmpty()).toList();

        assertThat(results).isEmpty();
    }

    @Test
    void productDescriptionLength() {
        var results = productOps().where((Product p) -> p.description.length() > 10).toList();

        assertThat(results)
                .hasSizeGreaterThan(0)
                .allMatch(p -> p.getDescription().length() > 10);
    }
}
