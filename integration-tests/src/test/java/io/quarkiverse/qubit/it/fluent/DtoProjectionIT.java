package io.quarkiverse.qubit.it.fluent;

import io.quarkiverse.qubit.it.Person;
import io.quarkiverse.qubit.it.Product;
import io.quarkiverse.qubit.it.dto.PersonBasicDTO;
import io.quarkiverse.qubit.it.dto.PersonNameDTO;
import io.quarkiverse.qubit.it.dto.PersonSummaryDTO;
import io.quarkiverse.qubit.it.dto.ProductInfoDTO;
import io.quarkiverse.qubit.it.testutil.PersonQueryOperations;
import io.quarkiverse.qubit.it.testutil.ProductQueryOperations;
import io.quarkiverse.qubit.it.testutil.StaticPersonQueryOperations;
import io.quarkiverse.qubit.it.testutil.StaticProductQueryOperations;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for DTO constructor-based projections using static entity methods.
 */
@QuarkusTest
class DtoProjectionIT extends AbstractDtoProjectionTest {

    @Override
    protected PersonQueryOperations personOps() {
        return StaticPersonQueryOperations.INSTANCE;
    }

    @Override
    protected ProductQueryOperations productOps() {
        return StaticProductQueryOperations.INSTANCE;
    }

    // These tests use static entity methods directly to cover DTO getters

    @Test
    void personNameDTO_gettersReturnCorrectValues() {
        var names = Person.where((Person p) -> p.firstName.equals("John"))
                .select((Person p) -> new PersonNameDTO(p.firstName, p.lastName))
                .findFirst();

        assertThat(names).isPresent();
        var dto = names.get();
        // Explicitly call getters to cover mutations
        assertThat(dto.getFirstName()).isEqualTo("John");
        assertThat(dto.getLastName()).isEqualTo("Doe");
    }

    @Test
    void personBasicDTO_gettersReturnCorrectValues() {
        var basics = Person.where((Person p) -> p.firstName.equals("Jane"))
                .select((Person p) -> new PersonBasicDTO(p.firstName, p.lastName, p.email))
                .findFirst();

        assertThat(basics).isPresent();
        var dto = basics.get();
        // Explicitly call getters to cover mutations
        assertThat(dto.getFirstName()).isEqualTo("Jane");
        assertThat(dto.getLastName()).isEqualTo("Smith");
        assertThat(dto.getEmail()).isEqualTo("jane.smith@example.com");
    }

    @Test
    void personSummaryDTO_gettersReturnCorrectValues() {
        var summaries = Person.where((Person p) -> p.firstName.equals("Bob"))
                .select((Person p) -> new PersonSummaryDTO(p.firstName, p.age, p.salary))
                .findFirst();

        assertThat(summaries).isPresent();
        var dto = summaries.get();
        // Explicitly call getters to cover mutations
        assertThat(dto.getFirstName()).isEqualTo("Bob");
        assertThat(dto.getAge()).isEqualTo(45);
        assertThat(dto.getSalary()).isEqualTo(85000.0);
    }

    @Test
    void productInfoDTO_gettersReturnCorrectValues() {
        var products = Product.where((Product p) -> p.name.equals("Laptop"))
                .select((Product p) -> new ProductInfoDTO(p.name, p.price, p.category))
                .findFirst();

        assertThat(products).isPresent();
        var dto = products.get();
        // Explicitly call getters to cover mutations
        assertThat(dto.getName()).isEqualTo("Laptop");
        assertThat(dto.getPrice()).isEqualTo(new BigDecimal("1299.99"));
        assertThat(dto.getCategory()).isEqualTo("Electronics");
    }
}
