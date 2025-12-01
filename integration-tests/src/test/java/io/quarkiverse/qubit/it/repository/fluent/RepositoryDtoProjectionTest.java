package io.quarkiverse.qubit.it.repository.fluent;

import io.quarkiverse.qubit.it.Person;
import io.quarkiverse.qubit.it.PersonRepository;
import io.quarkiverse.qubit.it.Product;
import io.quarkiverse.qubit.it.ProductRepository;
import io.quarkiverse.qubit.it.dto.PersonBasicDTO;
import io.quarkiverse.qubit.it.dto.PersonNameDTO;
import io.quarkiverse.qubit.it.dto.PersonSummaryDTO;
import io.quarkiverse.qubit.it.dto.ProductInfoDTO;
import io.quarkiverse.qubit.it.testdata.TestDataFactory;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Repository pattern tests for DTO constructor-based projections (Phase 2.4).
 * Mirrors io.quarkiverse.qubit.it.fluent.DtoProjectionTest using repository injection.
 *
 * <p>Validates constructor-based projections using {@code new DTOClass(field1, field2, ...)} syntax
 * which translates to JPA's {@code cb.construct(DTOClass.class, selection1, selection2, ...)}.
 *
 * <p>Examples:
 * <ul>
 * <li>Simple DTO: {@code repository.select(p -> new PersonNameDTO(p.firstName, p.lastName)).toList()}</li>
 * <li>Multi-type DTO: {@code repository.select(p -> new PersonSummaryDTO(p.firstName, p.age, p.salary)).toList()}</li>
 * <li>Combined: {@code repository.where(p -> p.active).select(p -> new PersonNameDTO(p.firstName, p.lastName)).toList()}</li>
 * </ul>
 */
@QuarkusTest
class RepositoryDtoProjectionTest {

    @Inject
    PersonRepository personRepository;

    @Inject
    ProductRepository productRepository;

    @BeforeEach
    @Transactional
    void setupTestData() {
        TestDataFactory.clearAllData();
        TestDataFactory.createStandardPersonsAndProducts();
    }

    // ========== Basic DTO Projections ==========

    @Test
    void selectDTO_twoFields() {
        var names = personRepository.select((Person p) -> new PersonNameDTO(p.firstName, p.lastName)).toList();

        assertThat(names)
                .hasSize(5)
                .containsExactlyInAnyOrder(
                        new PersonNameDTO("John", "Doe"),
                        new PersonNameDTO("Jane", "Smith"),
                        new PersonNameDTO("Bob", "Johnson"),
                        new PersonNameDTO("Alice", "Williams"),
                        new PersonNameDTO("Charlie", "Brown"));
    }

    @Test
    void selectDTO_threeStringFields() {
        var basics = personRepository.select((Person p) -> new PersonBasicDTO(p.firstName, p.lastName, p.email)).toList();

        assertThat(basics)
                .hasSize(5)
                .containsExactlyInAnyOrder(
                        new PersonBasicDTO("John", "Doe", "john.doe@example.com"),
                        new PersonBasicDTO("Jane", "Smith", "jane.smith@example.com"),
                        new PersonBasicDTO("Bob", "Johnson", "bob.johnson@example.com"),
                        new PersonBasicDTO("Alice", "Williams", "alice.williams@example.com"),
                        new PersonBasicDTO("Charlie", "Brown", "charlie.brown@example.com"));
    }

    @Test
    void selectDTO_multipleTypes() {
        var summaries = personRepository.select((Person p) -> new PersonSummaryDTO(p.firstName, p.age, p.salary)).toList();

        // Standard test data: John(30, $75K), Jane(25, $65K), Bob(45, $85K), Alice(35, $90K), Charlie(28, $55K)
        assertThat(summaries)
                .hasSize(5)
                .containsExactlyInAnyOrder(
                        new PersonSummaryDTO("John", 30, 75000.0),
                        new PersonSummaryDTO("Jane", 25, 65000.0),
                        new PersonSummaryDTO("Bob", 45, 85000.0),
                        new PersonSummaryDTO("Alice", 35, 90000.0),
                        new PersonSummaryDTO("Charlie", 28, 55000.0));
    }

    // ========== Product DTO Projections ==========

    @Test
    void selectDTO_productWithBigDecimal() {
        var products = productRepository.select((Product p) -> new ProductInfoDTO(p.name, p.price, p.category)).toList();

        assertThat(products)
                .hasSize(5)
                .containsExactlyInAnyOrder(
                        new ProductInfoDTO("Laptop", new BigDecimal("1299.99"), "Electronics"),
                        new ProductInfoDTO("Smartphone", new BigDecimal("899.99"), "Electronics"),
                        new ProductInfoDTO("Desk Chair", new BigDecimal("299.99"), "Furniture"),
                        new ProductInfoDTO("Coffee Maker", new BigDecimal("89.99"), "Appliances"),
                        new ProductInfoDTO("Monitor", new BigDecimal("399.99"), "Electronics"));
    }

    // ========== Combined WHERE + DTO Projection ==========

    @Test
    void whereActive_selectDTO() {
        var activeNames = personRepository.where((Person p) -> p.active)
                                .select((Person p) -> new PersonNameDTO(p.firstName, p.lastName))
                                .toList();

        // Active persons: John, Jane, Alice, Charlie (Bob is inactive)
        assertThat(activeNames)
                .hasSize(4)
                .containsExactlyInAnyOrder(
                        new PersonNameDTO("John", "Doe"),
                        new PersonNameDTO("Jane", "Smith"),
                        new PersonNameDTO("Alice", "Williams"),
                        new PersonNameDTO("Charlie", "Brown"));
    }

    @Test
    void whereAgeLessThan30_selectDTO() {
        var youngPeople = personRepository.where((Person p) -> p.age < 30)
                                .select((Person p) -> new PersonSummaryDTO(p.firstName, p.age, p.salary))
                                .toList();

        // age < 30 → Jane(25, $65K), Charlie(28, $55K)
        assertThat(youngPeople)
                .hasSize(2)
                .containsExactlyInAnyOrder(
                        new PersonSummaryDTO("Jane", 25, 65000.0),
                        new PersonSummaryDTO("Charlie", 28, 55000.0));
    }

    @Test
    void whereSalaryGreaterThan70K_selectDTO() {
        var highEarners = personRepository.where((Person p) -> p.salary > 70000.0)
                                .select((Person p) -> new PersonBasicDTO(p.firstName, p.lastName, p.email))
                                .toList();

        // salary > $70K → John($75K), Bob($85K), Alice($90K)
        assertThat(highEarners)
                .hasSize(3)
                .containsExactlyInAnyOrder(
                        new PersonBasicDTO("John", "Doe", "john.doe@example.com"),
                        new PersonBasicDTO("Bob", "Johnson", "bob.johnson@example.com"),
                        new PersonBasicDTO("Alice", "Williams", "alice.williams@example.com"));
    }

    @Test
    void productWhereAvailable_selectDTO() {
        var availableProducts = productRepository.where((Product p) -> p.available)
                                       .select((Product p) -> new ProductInfoDTO(p.name, p.price, p.category))
                                       .toList();

        // Available products: Laptop, Smartphone, Desk Chair, Monitor (Coffee Maker is not available)
        assertThat(availableProducts)
                .hasSize(4)
                .containsExactlyInAnyOrder(
                        new ProductInfoDTO("Laptop", new BigDecimal("1299.99"), "Electronics"),
                        new ProductInfoDTO("Smartphone", new BigDecimal("899.99"), "Electronics"),
                        new ProductInfoDTO("Desk Chair", new BigDecimal("299.99"), "Furniture"),
                        new ProductInfoDTO("Monitor", new BigDecimal("399.99"), "Electronics"));
    }

    // ========== Complex Filtering + DTO Projection ==========

    @Test
    void whereActiveAndSalaryGreaterThan60K_selectDTO() {
        var activeHighEarners = personRepository.where((Person p) -> p.active && p.salary > 60000.0)
                                      .select((Person p) -> new PersonSummaryDTO(p.firstName, p.age, p.salary))
                                      .toList();

        // active && salary > $60K → John($75K), Jane($65K), Alice($90K)
        assertThat(activeHighEarners)
                .hasSize(3)
                .containsExactlyInAnyOrder(
                        new PersonSummaryDTO("John", 30, 75000.0),
                        new PersonSummaryDTO("Jane", 25, 65000.0),
                        new PersonSummaryDTO("Alice", 35, 90000.0));
    }

    @Test
    void whereInactive_selectDTO() {
        var inactivePeople = personRepository.where((Person p) -> !p.active)
                                   .select((Person p) -> new PersonNameDTO(p.firstName, p.lastName))
                                   .toList();

        // inactive → Bob
        assertThat(inactivePeople)
                .hasSize(1)
                .containsExactly(new PersonNameDTO("Bob", "Johnson"));
    }

    @Test
    void productWherePriceGreaterThan300_selectDTO() {
        var expensiveProducts = productRepository.where((Product p) -> p.price.compareTo(new BigDecimal("300.00")) > 0)
                                       .select((Product p) -> new ProductInfoDTO(p.name, p.price, p.category))
                                       .toList();

        // price > $300 → Laptop($1299.99), Smartphone($899.99), Monitor($399.99)
        assertThat(expensiveProducts)
                .hasSize(3)
                .containsExactlyInAnyOrder(
                        new ProductInfoDTO("Laptop", new BigDecimal("1299.99"), "Electronics"),
                        new ProductInfoDTO("Smartphone", new BigDecimal("899.99"), "Electronics"),
                        new ProductInfoDTO("Monitor", new BigDecimal("399.99"), "Electronics"));
    }

    // ========== Edge Cases ==========

    @Test
    void whereNoMatches_selectDTO_returnsEmptyList() {
        var results = personRepository.where((Person p) -> p.age > 100)
                            .select((Person p) -> new PersonNameDTO(p.firstName, p.lastName))
                            .toList();

        assertThat(results).isEmpty();
    }

    @Test
    void selectDTO_resultCountMatchesEntityCount() {
        long entityCount = personRepository.where((Person p) -> p.age > 0).count();
        var dtos = personRepository.select((Person p) -> new PersonNameDTO(p.firstName, p.lastName)).toList();

        assertThat(dtos).hasSize((int) entityCount);
    }

    @Test
    void selectDTO_distinctPersons() {
        // All persons should have unique names in our test data
        var names = personRepository.select((Person p) -> new PersonNameDTO(p.firstName, p.lastName)).toList();

        assertThat(names)
                .hasSize(5)
                .doesNotHaveDuplicates();
    }

    @Test
    void productSelectDTO_distinctCategories() {
        var products = productRepository.select((Product p) -> new ProductInfoDTO(p.name, p.price, p.category)).toList();

        // Test data has 3 Electronics, 1 Furniture, 1 Appliances
        assertThat(products)
                .hasSize(5)
                .extracting(ProductInfoDTO::getCategory)
                .containsExactlyInAnyOrder("Electronics", "Electronics", "Electronics", "Furniture", "Appliances");
    }
}
