package io.quarkiverse.qubit.it.repository.relationship;

import io.quarkiverse.qubit.it.Person;
import io.quarkiverse.qubit.it.PersonRepository;
import io.quarkiverse.qubit.it.Phone;
import io.quarkiverse.qubit.it.PhoneRepository;
import io.quarkiverse.qubit.it.Product;
import io.quarkiverse.qubit.it.ProductRepository;
import io.quarkiverse.qubit.it.Tag;
import io.quarkiverse.qubit.it.TagRepository;
import io.quarkiverse.qubit.it.relationship.AbstractRelationshipNavigationTest;
import io.quarkiverse.qubit.it.testutil.PhoneQueryOperations;
import io.quarkiverse.qubit.it.testutil.RepositoryPhoneQueryOperations;
import io.quarkiverse.qubit.it.testutil.RepositoryTagQueryOperations;
import io.quarkiverse.qubit.it.testutil.TagQueryOperations;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Repository pattern tests for Phone and Tag entities.
 */
@QuarkusTest
class RepositoryRelationshipNavigationIT extends AbstractRelationshipNavigationTest {

    @Inject
    PersonRepository personRepository;

    @Inject
    PhoneRepository phoneRepository;

    @Inject
    ProductRepository productRepository;

    @Inject
    TagRepository tagRepository;

    @Override
    protected PhoneQueryOperations phoneOps() {
        return new RepositoryPhoneQueryOperations(phoneRepository);
    }

    @Override
    protected TagQueryOperations tagOps() {
        return new RepositoryTagQueryOperations(tagRepository);
    }

    // ========== COMBINED ENTITY QUERIES ==========

    @Test
    void queryAllEntityTypes() {
        var activePersons = personRepository.where((Person p) -> p.active).toList();
        assertThat(activePersons).hasSizeGreaterThan(0);

        var electronics = productRepository.where((Product p) -> p.category.equals("Electronics")).toList();
        assertThat(electronics).hasSize(3);

        var mobilePhones = phoneRepository.where((Phone ph) -> ph.type.equals("mobile")).toList();
        assertThat(mobilePhones).hasSize(5);

        var electronicsTags = tagRepository.where((Tag t) -> t.name.equals("electronics")).toList();
        assertThat(electronicsTags).hasSize(1);
    }

    @Test
    void mixedEntityQueries() {
        var adults = personRepository.where((Person p) -> p.age >= 30).toList();
        assertThat(adults).hasSizeGreaterThan(0);

        var premiumProducts = productRepository.where((Product p) ->
                p.price.compareTo(new BigDecimal("500")) > 0 && p.available
        ).toList();
        assertThat(premiumProducts).hasSizeGreaterThan(0);

        var primaryPhones = phoneRepository.where((Phone ph) -> ph.isPrimaryPhone).toList();
        assertThat(primaryPhones).hasSize(5);

        var coloredTags = tagRepository.where((Tag t) -> !t.color.equals("")).toList();
        assertThat(coloredTags).hasSizeGreaterThan(0);
    }
}
