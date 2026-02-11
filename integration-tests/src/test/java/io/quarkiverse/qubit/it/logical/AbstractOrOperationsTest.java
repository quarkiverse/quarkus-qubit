package io.quarkiverse.qubit.it.logical;

import io.quarkiverse.qubit.it.Person;
import io.quarkiverse.qubit.it.Product;
import io.quarkiverse.qubit.it.testdata.TestDataFactory;
import io.quarkiverse.qubit.it.testutil.PersonQueryOperations;
import io.quarkiverse.qubit.it.testutil.ProductQueryOperations;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Abstract base class for OR logical operation tests.
 *
 * <p>
 * Contains all test methods that can be run with either static entity methods
 * or repository instance methods.
 */
public abstract class AbstractOrOperationsTest {

    protected abstract PersonQueryOperations personOps();

    protected abstract ProductQueryOperations productOps();

    @BeforeEach
    @Transactional
    protected void setupTestData() {
        TestDataFactory.clearAllData();
        TestDataFactory.createStandardPersonsAndProducts();
    }

    @Test
    void simpleOr() {
        var results = personOps().where((Person p) -> p.age < 26 || p.age > 40).toList();

        assertThat(results)
                .hasSizeGreaterThan(0)
                .allMatch(p -> p.getAge() < 26 || p.getAge() > 40);
    }

    @Test
    void orWithStringOperations() {
        var results = personOps().where((Person p) -> p.firstName.startsWith("A") || p.age > 40).toList();

        assertThat(results)
                .hasSizeGreaterThan(0)
                .allMatch(p -> p.getFirstName().startsWith("A") || p.getAge() > 40);
    }

    @Test
    void threeWayOr() {
        var results = personOps().where((Person p) -> p.age < 26 || p.age > 44 || p.firstName.equals("John")).toList();

        assertThat(results)
                .hasSizeGreaterThan(0)
                .allMatch(p -> p.getAge() < 26 || p.getAge() > 44 ||
                        p.getFirstName().equals("John"));
    }

    @Test
    void fourWayOr() {
        var results = personOps().where((Person p) -> p.age < 27 || p.age > 43 || p.firstName.equals("Alice") ||
                p.email.contains("@example.com")).toList();

        assertThat(results)
                .hasSizeGreaterThan(0)
                .allMatch(p -> p.getAge() < 27 || p.getAge() > 43 ||
                        p.getFirstName().equals("Alice") ||
                        p.getEmail().contains("@example.com"));
    }

    @Test
    void orWithStringMethods() {
        var results = personOps().where((Person p) -> p.firstName.startsWith("A") || p.lastName.endsWith("son")).toList();

        assertThat(results)
                .hasSizeGreaterThan(0)
                .allMatch(p -> p.getFirstName().startsWith("A") ||
                        p.getLastName().endsWith("son"));
    }

    @Test
    void orWithNullChecks() {
        var results = personOps().where((Person p) -> p.birthDate.isBefore(LocalDate.of(1980, 1, 1)) || p.salary > 85000.0)
                .toList();

        assertThat(results)
                .hasSizeGreaterThan(0)
                .allMatch(p -> p.getBirthDate().isBefore(LocalDate.of(1980, 1, 1)) ||
                        p.getSalary() > 85000.0);
    }

    @Test
    void orWithMethodChaining() {
        var results = personOps().where((Person p) -> p.email.toLowerCase().contains("example") ||
                p.firstName.toUpperCase().startsWith("B")).toList();

        assertThat(results)
                .hasSizeGreaterThan(0)
                .allMatch(p -> p.getEmail().toLowerCase().contains("example") ||
                        p.getFirstName().toUpperCase().startsWith("B"));
    }

    @Test
    void orWithMixedTypes() {
        var results = personOps().where((Person p) -> p.age > 40 || p.email.contains("@company") || !p.active).toList();

        assertThat(results)
                .hasSizeGreaterThan(0)
                .allMatch(p -> p.getAge() > 40 ||
                        p.getEmail().contains("@company") ||
                        !p.isActive());
    }

    @Test
    void multipleOrWithAnd() {
        var results = personOps().where((Person p) -> (p.firstName.startsWith("J") || p.firstName.startsWith("A") ||
                p.firstName.startsWith("C")) && p.salary > 50000.0).toList();

        assertThat(results)
                .hasSizeGreaterThan(0)
                .allMatch(p -> (p.getFirstName().startsWith("J") ||
                        p.getFirstName().startsWith("A") ||
                        p.getFirstName().startsWith("C")) &&
                        p.getSalary() > 50000.0);
    }

    @Test
    void productOrOperation() {
        var results = productOps().where((Product p) -> p.stockQuantity > 75 || p.rating > 4.6).toList();

        assertThat(results)
                .hasSizeGreaterThan(0)
                .allMatch(p -> p.getStockQuantity() > 75 || p.getRating() > 4.6);
    }
}
