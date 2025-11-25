package io.quarkus.qusaq.it.repository.relationship;

import io.quarkus.qusaq.it.Person;
import io.quarkus.qusaq.it.PersonRepository;
import io.quarkus.qusaq.it.Phone;
import io.quarkus.qusaq.it.PhoneRepository;
import io.quarkus.qusaq.it.Product;
import io.quarkus.qusaq.it.ProductRepository;
import io.quarkus.qusaq.it.Tag;
import io.quarkus.qusaq.it.TagRepository;
import io.quarkus.qusaq.it.testdata.TestDataFactory;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Repository pattern tests for Phone and Tag entities.
 * <p>
 * These tests verify that the new entities work correctly with Qusaq queries
 * through the repository pattern.
 * Note: Relationship navigation (e.g., ph.owner.firstName) is not yet implemented.
 */
@QuarkusTest
class RepositoryRelationshipNavigationTest {

    @Inject
    PersonRepository personRepository;

    @Inject
    PhoneRepository phoneRepository;

    @Inject
    ProductRepository productRepository;

    @Inject
    TagRepository tagRepository;

    @BeforeEach
    @Transactional
    void setupTestData() {
        TestDataFactory.clearAllData();
        TestDataFactory.createAllDataWithRelationships();
    }

    // ========== PHONE ENTITY TESTS ==========

    @Test
    void phonesByType() {
        var results = phoneRepository.where((Phone ph) -> ph.type.equals("mobile")).toList();

        assertThat(results)
                .hasSize(5) // One mobile phone per person
                .allMatch(ph -> ph.getType().equals("mobile"));
    }

    @Test
    void phonesByNumber() {
        var results = phoneRepository.where((Phone ph) -> ph.number.equals("555-0101")).toList();

        assertThat(results)
                .hasSize(1)
                .extracting(Phone::getNumber)
                .containsExactly("555-0101");
    }

    @Test
    void phonesByPrimaryStatus() {
        var results = phoneRepository.where((Phone ph) -> ph.isPrimaryPhone).toList();

        assertThat(results)
                .hasSize(5) // Each person has one primary phone
                .allMatch(Phone::isPrimaryPhone);
    }

    @Test
    void workPhones() {
        var results = phoneRepository.where((Phone ph) -> ph.type.equals("work")).toList();

        assertThat(results)
                .hasSizeGreaterThan(0)
                .allMatch(ph -> ph.getType().equals("work"));
    }

    @Test
    void homePhones() {
        var results = phoneRepository.where((Phone ph) -> ph.type.equals("home")).toList();

        assertThat(results)
                .hasSizeGreaterThan(0)
                .allMatch(ph -> ph.getType().equals("home"));
    }

    @Test
    void phoneCountByType() {
        long mobileCount = phoneRepository.where((Phone ph) -> ph.type.equals("mobile")).count();
        long workCount = phoneRepository.where((Phone ph) -> ph.type.equals("work")).count();

        assertThat(mobileCount).isEqualTo(5);
        assertThat(workCount).isGreaterThan(0);
    }

    @Test
    void phoneExists() {
        boolean exists = phoneRepository.where((Phone ph) -> ph.number.startsWith("555-")).exists();

        assertThat(exists).isTrue();
    }

    @Test
    void phoneNumberStartsWith() {
        var results = phoneRepository.where((Phone ph) -> ph.number.startsWith("555-01")).toList();

        assertThat(results)
                .hasSizeGreaterThan(0)
                .allMatch(ph -> ph.getNumber().startsWith("555-01"));
    }

    // ========== TAG ENTITY TESTS ==========

    @Test
    void tagsByName() {
        var results = tagRepository.where((Tag t) -> t.name.equals("electronics")).toList();

        assertThat(results)
                .hasSize(1)
                .extracting(Tag::getName)
                .containsExactly("electronics");
    }

    @Test
    void tagsByColor() {
        var results = tagRepository.where((Tag t) -> t.color.equals("blue")).toList();

        assertThat(results)
                .hasSize(1)
                .extracting(Tag::getColor)
                .containsExactly("blue");
    }

    @Test
    void tagsStartingWith() {
        var results = tagRepository.where((Tag t) -> t.name.startsWith("b")).toList();

        // "bestseller" starts with 'b'
        assertThat(results)
                .hasSizeGreaterThan(0)
                .allMatch(t -> t.getName().startsWith("b"));
    }

    @Test
    void tagCount() {
        long count = tagRepository.where((Tag t) -> t.name.contains("-")).count();

        // Tags with '-': new-arrival, eco-friendly
        assertThat(count).isEqualTo(2);
    }

    @Test
    void tagExists() {
        boolean exists = tagRepository.where((Tag t) -> t.name.equals("premium")).exists();

        assertThat(exists).isTrue();
    }

    @Test
    void tagNotExists() {
        boolean exists = tagRepository.where((Tag t) -> t.name.equals("nonexistent")).exists();

        assertThat(exists).isFalse();
    }

    // ========== COMBINED ENTITY QUERIES ==========

    @Test
    void queryAllEntityTypes() {
        // Verify all entity types work together via repositories

        // Person queries
        var activePersons = personRepository.where((Person p) -> p.active).toList();
        assertThat(activePersons).hasSizeGreaterThan(0);

        // Product queries
        var electronics = productRepository.where((Product p) -> p.category.equals("Electronics")).toList();
        assertThat(electronics).hasSize(3);

        // Phone queries
        var mobilePhones = phoneRepository.where((Phone ph) -> ph.type.equals("mobile")).toList();
        assertThat(mobilePhones).hasSize(5);

        // Tag queries
        var electronicsTags = tagRepository.where((Tag t) -> t.name.equals("electronics")).toList();
        assertThat(electronicsTags).hasSize(1);
    }

    @Test
    void mixedEntityQueries() {
        // Query different entities with various predicates

        // Person by age
        var adults = personRepository.where((Person p) -> p.age >= 30).toList();
        assertThat(adults).hasSizeGreaterThan(0);

        // Product by price
        var premiumProducts = productRepository.where((Product p) ->
                p.price.compareTo(new BigDecimal("500")) > 0 && p.available
        ).toList();
        assertThat(premiumProducts).hasSizeGreaterThan(0);

        // Phone by type
        var primaryPhones = phoneRepository.where((Phone ph) -> ph.isPrimaryPhone).toList();
        assertThat(primaryPhones).hasSize(5);

        // Tag by color
        var coloredTags = tagRepository.where((Tag t) -> !t.color.equals("")).toList();
        assertThat(coloredTags).hasSizeGreaterThan(0);
    }
}
