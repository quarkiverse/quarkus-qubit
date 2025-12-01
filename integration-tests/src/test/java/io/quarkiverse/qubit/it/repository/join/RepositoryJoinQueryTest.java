package io.quarkiverse.qubit.it.repository.join;

import io.quarkiverse.qubit.it.Person;
import io.quarkiverse.qubit.it.PersonRepository;
import io.quarkiverse.qubit.it.Phone;
import io.quarkiverse.qubit.it.dto.PersonPhoneDTO;
import io.quarkiverse.qubit.it.testdata.TestDataFactory;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Repository pattern tests for join queries (Iteration 6).
 * Mirrors io.quarkiverse.qubit.it.join.JoinQueryTest using repository injection.
 * <p>
 * Tests join operations between Person and Phone entities using the repository fluent API.
 * The test data creates 5 persons with phones:
 * <ul>
 *   <li>John: 2 phones (mobile, work)</li>
 *   <li>Jane: 1 phone (mobile)</li>
 *   <li>Bob: 3 phones (mobile, home, work)</li>
 *   <li>Alice: 2 phones (mobile, work)</li>
 *   <li>Charlie: 1 phone (mobile)</li>
 * </ul>
 */
@QuarkusTest
class RepositoryJoinQueryTest {

    @Inject
    PersonRepository personRepository;

    @BeforeEach
    @Transactional
    void setupTestData() {
        TestDataFactory.clearAllData();
        TestDataFactory.createPersonsWithPhones();
    }

    // ========== BASIC JOIN QUERIES ==========

    @Test
    void repositoryBasicJoinReturnsAllPersonsWithPhones() {
        // All 5 persons have phones, but inner join returns one row per matching phone
        // So we expect duplicates - use distinct to get unique persons
        var results = personRepository.join((Person p) -> p.phones)
                .distinct()
                .toList();

        // All 5 persons have phones
        assertThat(results).hasSize(5);
    }

    @Test
    void repositoryJoinWithWhereOnJoinedEntity() {
        // Find persons who have a mobile phone
        var results = personRepository.join((Person p) -> p.phones)
                .where((Person p, Phone ph) -> ph.type.equals("mobile"))
                .distinct()
                .toList();

        // All 5 persons have mobile phones
        assertThat(results)
                .hasSize(5)
                .extracting(p -> p.firstName)
                .containsExactlyInAnyOrder("John", "Jane", "Bob", "Alice", "Charlie");
    }

    @Test
    void repositoryJoinWithWhereOnSpecificPhoneType() {
        // Find persons who have a work phone
        var results = personRepository.join((Person p) -> p.phones)
                .where((Person p, Phone ph) -> ph.type.equals("work"))
                .distinct()
                .toList();

        // John, Bob, and Alice have work phones
        assertThat(results)
                .hasSize(3)
                .extracting(p -> p.firstName)
                .containsExactlyInAnyOrder("John", "Bob", "Alice");
    }

    @Test
    void repositoryJoinWithWhereOnHomePhone() {
        // Find persons who have a home phone
        var results = personRepository.join((Person p) -> p.phones)
                .where((Person p, Phone ph) -> ph.type.equals("home"))
                .distinct()
                .toList();

        // Only Bob has a home phone
        assertThat(results)
                .hasSize(1)
                .extracting(p -> p.firstName)
                .containsExactly("Bob");
    }

    // ========== JOIN WITH CAPTURED VARIABLES ==========

    @Test
    void repositoryJoinWithCapturedVariable() {
        String phoneType = "mobile";

        var results = personRepository.join((Person p) -> p.phones)
                .where((Person p, Phone ph) -> ph.type.equals(phoneType))
                .distinct()
                .toList();

        assertThat(results).hasSize(5);
    }

    @Test
    void repositoryJoinWithCapturedPhoneNumber() {
        String phoneNumber = "555-0101";

        var results = personRepository.join((Person p) -> p.phones)
                .where((Person p, Phone ph) -> ph.number.equals(phoneNumber))
                .toList();

        // Only John has this phone number
        assertThat(results)
                .hasSize(1)
                .extracting(p -> p.firstName)
                .containsExactly("John");
    }

    // ========== JOIN WITH PREDICATE ON SOURCE ENTITY ==========

    @Test
    void repositoryJoinWithWhereOnSourceEntity() {
        // Find active persons who have mobile phones
        var results = personRepository.join((Person p) -> p.phones)
                .where((Person p, Phone ph) -> ph.type.equals("mobile"))
                .where((Person p, Phone ph) -> p.active)
                .distinct()
                .toList();

        // Active persons with mobile: John, Jane, Alice, Charlie (Bob is inactive)
        assertThat(results)
                .hasSize(4)
                .extracting(p -> p.firstName)
                .containsExactlyInAnyOrder("John", "Jane", "Alice", "Charlie");
    }

    @Test
    void repositoryJoinWithWhereOnBothEntities() {
        // Find persons over 30 who have work phones
        var results = personRepository.join((Person p) -> p.phones)
                .where((Person p, Phone ph) -> p.age >= 30 && ph.type.equals("work"))
                .distinct()
                .toList();

        // John (30), Bob (45), Alice (35) have work phones and age >= 30
        assertThat(results)
                .hasSize(3)
                .extracting(p -> p.firstName)
                .containsExactlyInAnyOrder("John", "Bob", "Alice");
    }

    // ========== JOIN WITH MULTIPLE PREDICATES ==========

    @Test
    void repositoryJoinWithMultipleWhereClausesOnJoinedEntity() {
        // Find persons with mobile phones that start with 555-01
        var results = personRepository.join((Person p) -> p.phones)
                .where((Person p, Phone ph) -> ph.type.equals("mobile"))
                .where((Person p, Phone ph) -> ph.number.startsWith("555-01"))
                .distinct()
                .toList();

        // John has 555-0101 (mobile)
        assertThat(results)
                .hasSize(1)
                .extracting(p -> p.firstName)
                .containsExactly("John");
    }

    // ========== JOIN COUNT/EXISTS ==========

    @Test
    void repositoryJoinCount() {
        // Count person-phone pairs for mobile phones
        long count = personRepository.join((Person p) -> p.phones)
                .where((Person p, Phone ph) -> ph.type.equals("mobile"))
                .count();

        // 5 persons each have 1 mobile phone = 5 pairs
        assertThat(count).isEqualTo(5);
    }

    @Test
    void repositoryJoinExists() {
        // Check if any person has a work phone
        boolean exists = personRepository.join((Person p) -> p.phones)
                .where((Person p, Phone ph) -> ph.type.equals("work"))
                .exists();

        assertThat(exists).isTrue();
    }

    @Test
    void repositoryJoinNotExists() {
        // Check if any person has a fax (none do)
        boolean exists = personRepository.join((Person p) -> p.phones)
                .where((Person p, Phone ph) -> ph.type.equals("fax"))
                .exists();

        assertThat(exists).isFalse();
    }

    // ========== JOIN WITH PAGINATION ==========

    @Test
    void repositoryJoinWithLimit() {
        var results = personRepository.join((Person p) -> p.phones)
                .where((Person p, Phone ph) -> ph.type.equals("mobile"))
                .limit(3)
                .toList();

        assertThat(results).hasSize(3);
    }

    @Test
    void repositoryJoinWithSkipAndLimit() {
        var results = personRepository.join((Person p) -> p.phones)
                .where((Person p, Phone ph) -> ph.type.equals("mobile"))
                .skip(2)
                .limit(2)
                .toList();

        assertThat(results).hasSize(2);
    }

    // ========== JOIN WITH DISTINCT ==========

    @Test
    void repositoryJoinDistinctWithEntities() {
        // In JPA/Hibernate, when selecting entities (not scalars), the persistence
        // context naturally deduplicates entity instances. The SQL may have duplicates,
        // but getResultList() returns unique managed entities.
        //
        // To verify actual row counts, use count() which shows SQL-level counts.
        var results = personRepository.join((Person p) -> p.phones)
                .toList();

        var resultsWithDistinct = personRepository.join((Person p) -> p.phones)
                .distinct()
                .toList();

        // Both return 5 unique Person entities (Hibernate deduplicates in entity mode)
        assertThat(results).hasSize(5);
        assertThat(resultsWithDistinct).hasSize(5);

        // COUNT shows actual SQL row count (9 person-phone pairs)
        long count = personRepository.join((Person p) -> p.phones).count();
        assertThat(count).isEqualTo(9);
    }

    // ========== JOIN WITH STRING METHODS ==========

    @Test
    void repositoryJoinWithStartsWith() {
        // Find persons whose phone numbers start with "555-03"
        var results = personRepository.join((Person p) -> p.phones)
                .where((Person p, Phone ph) -> ph.number.startsWith("555-03"))
                .distinct()
                .toList();

        // Bob has phones starting with 555-03
        assertThat(results)
                .hasSize(1)
                .extracting(p -> p.firstName)
                .containsExactly("Bob");
    }

    @Test
    void repositoryJoinWithContains() {
        // Find persons whose phone numbers contain "02"
        var results = personRepository.join((Person p) -> p.phones)
                .where((Person p, Phone ph) -> ph.number.contains("02"))
                .distinct()
                .toList();

        // John (555-0102), Jane (555-0201), Bob (555-0302), Alice (555-0402)
        assertThat(results)
                .hasSize(4)
                .extracting(p -> p.firstName)
                .containsExactlyInAnyOrder("John", "Jane", "Bob", "Alice");
    }

    // ========== JOIN WITH PRIMARY PHONE ==========

    @Test
    void repositoryJoinWithPrimaryPhone() {
        // Find persons who have a primary phone
        var results = personRepository.join((Person p) -> p.phones)
                .where((Person p, Phone ph) -> ph.isPrimaryPhone)
                .distinct()
                .toList();

        // All 5 persons have primary phones (their mobile phones)
        assertThat(results).hasSize(5);
    }

    @Test
    void repositoryJoinWithNonPrimaryPhone() {
        // Find persons who have non-primary phones
        var results = personRepository.join((Person p) -> p.phones)
                .where((Person p, Phone ph) -> !ph.isPrimaryPhone)
                .distinct()
                .toList();

        // John (work), Bob (home, work), Alice (work) have non-primary phones
        assertThat(results)
                .hasSize(3)
                .extracting(p -> p.firstName)
                .containsExactlyInAnyOrder("John", "Bob", "Alice");
    }

    // ========== SELECT JOINED (Iteration 6.5) ==========

    @Test
    void repositorySelectJoinedReturnsPhones() {
        // Use selectJoined() to return Phone entities instead of Person entities
        var phones = personRepository.join((Person p) -> p.phones)
                .selectJoined()
                .toList();

        // Should return 9 phones (all phone-person pairs)
        assertThat(phones).hasSize(9);
        assertThat(phones).allMatch(obj -> obj instanceof Phone);
    }

    @Test
    void repositorySelectJoinedWithWhere() {
        // Find all mobile phones using selectJoined()
        var phones = personRepository.join((Person p) -> p.phones)
                .where((Person p, Phone ph) -> ph.type.equals("mobile"))
                .selectJoined()
                .toList();

        // 5 mobile phones (one per person)
        assertThat(phones)
                .hasSize(5)
                .allMatch(obj -> obj instanceof Phone)
                .extracting(obj -> ((Phone) obj).type)
                .containsOnly("mobile");
    }

    @Test
    void repositorySelectJoinedWithWhereOnWorkPhones() {
        // Find work phones using selectJoined()
        var phones = personRepository.join((Person p) -> p.phones)
                .where((Person p, Phone ph) -> ph.type.equals("work"))
                .selectJoined()
                .toList();

        // John, Bob, and Alice have work phones
        assertThat(phones)
                .hasSize(3)
                .allMatch(obj -> obj instanceof Phone)
                .extracting(obj -> ((Phone) obj).type)
                .containsOnly("work");
    }

    @Test
    void repositorySelectJoinedWithLimit() {
        // Use selectJoined() with limit
        var phones = personRepository.join((Person p) -> p.phones)
                .selectJoined()
                .limit(3)
                .toList();

        assertThat(phones).hasSize(3);
    }

    @Test
    void repositorySelectJoinedWithDistinct() {
        // selectJoined() with distinct (should still work though phones are unique)
        var phones = personRepository.join((Person p) -> p.phones)
                .distinct()
                .selectJoined()
                .toList();

        // All 9 phones are unique
        assertThat(phones).hasSize(9);
    }

    // ========== JOIN PROJECTION (Iteration 6.6) ==========

    @Test
    void repositorySelectWithBiQuerySpecReturnsProjectedDTO() {
        // Use select() with BiQuerySpec to project both entities into a DTO
        var results = personRepository.join((Person p) -> p.phones)
                .select((Person p, Phone ph) -> new PersonPhoneDTO(p.firstName, ph.number))
                .toList();

        // Should return 9 person-phone pairs as DTOs
        assertThat(results).hasSize(9);
        assertThat(results).allMatch(obj -> obj instanceof PersonPhoneDTO);
    }

    @Test
    void repositorySelectWithBiQuerySpecAndWhere() {
        // Find mobile phones and project to DTO
        var results = personRepository.join((Person p) -> p.phones)
                .where((Person p, Phone ph) -> ph.type.equals("mobile"))
                .select((Person p, Phone ph) -> new PersonPhoneDTO(p.firstName, ph.number))
                .toList();

        // 5 mobile phones (one per person)
        assertThat(results)
                .hasSize(5)
                .allMatch(obj -> obj instanceof PersonPhoneDTO);
    }

    @Test
    void repositorySelectWithBiQuerySpecAndLimit() {
        // Use select() with limit
        var results = personRepository.join((Person p) -> p.phones)
                .select((Person p, Phone ph) -> new PersonPhoneDTO(p.firstName, ph.number))
                .limit(3)
                .toList();

        assertThat(results).hasSize(3);
    }

    @Test
    void repositorySelectWithBiQuerySpecAndDistinct() {
        // Use select() with distinct
        var results = personRepository.join((Person p) -> p.phones)
                .distinct()
                .select((Person p, Phone ph) -> new PersonPhoneDTO(p.firstName, ph.number))
                .toList();

        // All 9 person-phone pairs are unique
        assertThat(results).hasSize(9);
    }

    @Test
    void repositorySelectWithBiQuerySpecExtractingScalarField() {
        // Use select() to extract a scalar field from joined entity
        var phoneNumbers = personRepository.join((Person p) -> p.phones)
                .where((Person p, Phone ph) -> ph.type.equals("work"))
                .select((Person p, Phone ph) -> ph.number)
                .toList();

        // 3 work phones
        assertThat(phoneNumbers)
                .hasSize(3)
                .allMatch(n -> n instanceof String);
    }

    // ========== LEFT JOIN (Iteration 6) ==========

    @Test
    void repositoryLeftJoinBasic() {
        // LEFT JOIN should include all source entities even without matching joined entities
        // Since all 5 persons have phones, result is same as inner join
        var results = personRepository.leftJoin((Person p) -> p.phones)
                .distinct()
                .toList();

        assertThat(results).hasSize(5);
    }

    @Test
    void repositoryLeftJoinWithWhere() {
        // LEFT JOIN with predicate on joined entity
        var results = personRepository.leftJoin((Person p) -> p.phones)
                .where((Person p, Phone ph) -> ph.type.equals("mobile"))
                .distinct()
                .toList();

        // All 5 persons have mobile phones
        assertThat(results)
                .hasSize(5)
                .extracting(p -> p.firstName)
                .containsExactlyInAnyOrder("John", "Jane", "Bob", "Alice", "Charlie");
    }

    @Test
    void repositoryLeftJoinWithWhereOnWorkPhone() {
        // LEFT JOIN filtering for work phones
        var results = personRepository.leftJoin((Person p) -> p.phones)
                .where((Person p, Phone ph) -> ph.type.equals("work"))
                .distinct()
                .toList();

        // John, Bob, and Alice have work phones
        assertThat(results)
                .hasSize(3)
                .extracting(p -> p.firstName)
                .containsExactlyInAnyOrder("John", "Bob", "Alice");
    }

    @Test
    void repositoryLeftJoinWithCapturedVariable() {
        String phoneType = "home";

        var results = personRepository.leftJoin((Person p) -> p.phones)
                .where((Person p, Phone ph) -> ph.type.equals(phoneType))
                .distinct()
                .toList();

        // Only Bob has a home phone
        assertThat(results)
                .hasSize(1)
                .extracting(p -> p.firstName)
                .containsExactly("Bob");
    }

    @Test
    void repositoryLeftJoinWithLimit() {
        var results = personRepository.leftJoin((Person p) -> p.phones)
                .where((Person p, Phone ph) -> ph.type.equals("mobile"))
                .limit(3)
                .toList();

        assertThat(results).hasSize(3);
    }

    @Test
    void repositoryLeftJoinWithSkipAndLimit() {
        var results = personRepository.leftJoin((Person p) -> p.phones)
                .where((Person p, Phone ph) -> ph.type.equals("mobile"))
                .skip(2)
                .limit(2)
                .toList();

        assertThat(results).hasSize(2);
    }

    @Test
    void repositoryLeftJoinCount() {
        // Count person-phone pairs with LEFT JOIN
        long count = personRepository.leftJoin((Person p) -> p.phones)
                .where((Person p, Phone ph) -> ph.type.equals("work"))
                .count();

        // 3 work phones (John, Bob, Alice)
        assertThat(count).isEqualTo(3);
    }

    @Test
    void repositoryLeftJoinExists() {
        // Check if any person has a home phone using LEFT JOIN
        boolean exists = personRepository.leftJoin((Person p) -> p.phones)
                .where((Person p, Phone ph) -> ph.type.equals("home"))
                .exists();

        assertThat(exists).isTrue();
    }

    @Test
    void repositoryLeftJoinNotExists() {
        // Check if any person has a fax (none do) using LEFT JOIN
        boolean exists = personRepository.leftJoin((Person p) -> p.phones)
                .where((Person p, Phone ph) -> ph.type.equals("fax"))
                .exists();

        assertThat(exists).isFalse();
    }
}
