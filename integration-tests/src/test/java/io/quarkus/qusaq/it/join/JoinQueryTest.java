package io.quarkus.qusaq.it.join;

import io.quarkus.qusaq.it.Person;
import io.quarkus.qusaq.it.Phone;
import io.quarkus.qusaq.it.testdata.TestDataFactory;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for join queries.
 * <p>
 * Tests join operations between Person and Phone entities using the fluent API.
 * The test data creates 5 persons with phones:
 * <ul>
 *   <li>John: 2 phones (mobile, work)</li>
 *   <li>Jane: 1 phone (mobile)</li>
 *   <li>Bob: 3 phones (mobile, home, work)</li>
 *   <li>Alice: 2 phones (mobile, work)</li>
 *   <li>Charlie: 1 phone (mobile)</li>
 * </ul>
 * <p>
 * Iteration 6: Join Queries implementation.
 */
@QuarkusTest
class JoinQueryTest {

    @BeforeEach
    @Transactional
    void setupTestData() {
        TestDataFactory.clearAllData();
        TestDataFactory.createPersonsWithPhones();
    }

    // ========== BASIC JOIN QUERIES ==========

    @Test
    void basicJoinReturnsAllPersonsWithPhones() {
        // All 5 persons have phones, but inner join returns one row per matching phone
        // So we expect duplicates - use distinct to get unique persons
        var results = Person.join((Person p) -> p.phones)
                .distinct()
                .toList();

        // All 5 persons have phones
        assertThat(results).hasSize(5);
    }

    @Test
    void joinWithWhereOnJoinedEntity() {
        // Find persons who have a mobile phone
        var results = Person.join((Person p) -> p.phones)
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
    void joinWithWhereOnSpecificPhoneType() {
        // Find persons who have a work phone
        var results = Person.join((Person p) -> p.phones)
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
    void joinWithWhereOnHomePhone() {
        // Find persons who have a home phone
        var results = Person.join((Person p) -> p.phones)
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
    void joinWithCapturedVariable() {
        String phoneType = "mobile";

        var results = Person.join((Person p) -> p.phones)
                .where((Person p, Phone ph) -> ph.type.equals(phoneType))
                .distinct()
                .toList();

        assertThat(results).hasSize(5);
    }

    @Test
    void joinWithCapturedPhoneNumber() {
        String phoneNumber = "555-0101";

        var results = Person.join((Person p) -> p.phones)
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
    void joinWithWhereOnSourceEntity() {
        // Find active persons who have mobile phones
        var results = Person.join((Person p) -> p.phones)
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
    void joinWithWhereOnBothEntities() {
        // Find persons over 30 who have work phones
        var results = Person.join((Person p) -> p.phones)
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
    void joinWithMultipleWhereClausesOnJoinedEntity() {
        // Find persons with mobile phones that start with 555-01
        var results = Person.join((Person p) -> p.phones)
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
    void joinCount() {
        // Count person-phone pairs for mobile phones
        long count = Person.join((Person p) -> p.phones)
                .where((Person p, Phone ph) -> ph.type.equals("mobile"))
                .count();

        // 5 persons each have 1 mobile phone = 5 pairs
        assertThat(count).isEqualTo(5);
    }

    @Test
    void joinExists() {
        // Check if any person has a work phone
        boolean exists = Person.join((Person p) -> p.phones)
                .where((Person p, Phone ph) -> ph.type.equals("work"))
                .exists();

        assertThat(exists).isTrue();
    }

    @Test
    void joinNotExists() {
        // Check if any person has a fax (none do)
        boolean exists = Person.join((Person p) -> p.phones)
                .where((Person p, Phone ph) -> ph.type.equals("fax"))
                .exists();

        assertThat(exists).isFalse();
    }

    // ========== JOIN WITH PAGINATION ==========

    @Test
    void joinWithLimit() {
        var results = Person.join((Person p) -> p.phones)
                .where((Person p, Phone ph) -> ph.type.equals("mobile"))
                .limit(3)
                .toList();

        assertThat(results).hasSize(3);
    }

    @Test
    void joinWithSkipAndLimit() {
        var results = Person.join((Person p) -> p.phones)
                .where((Person p, Phone ph) -> ph.type.equals("mobile"))
                .skip(2)
                .limit(2)
                .toList();

        assertThat(results).hasSize(2);
    }

    // ========== JOIN WITH DISTINCT ==========

    @Test
    void joinDistinctWithEntities() {
        // In JPA/Hibernate, when selecting entities (not scalars), the persistence
        // context naturally deduplicates entity instances. The SQL may have duplicates,
        // but getResultList() returns unique managed entities.
        //
        // To verify actual row counts, use count() which shows SQL-level counts.
        var results = Person.join((Person p) -> p.phones)
                .toList();

        var resultsWithDistinct = Person.join((Person p) -> p.phones)
                .distinct()
                .toList();

        // Both return 5 unique Person entities (Hibernate deduplicates in entity mode)
        assertThat(results).hasSize(5);
        assertThat(resultsWithDistinct).hasSize(5);

        // COUNT shows actual SQL row count (9 person-phone pairs)
        long count = Person.join((Person p) -> p.phones).count();
        assertThat(count).isEqualTo(9);
    }

    // ========== JOIN WITH STRING METHODS ==========

    @Test
    void joinWithStartsWith() {
        // Find persons whose phone numbers start with "555-03"
        var results = Person.join((Person p) -> p.phones)
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
    void joinWithContains() {
        // Find persons whose phone numbers contain "02"
        var results = Person.join((Person p) -> p.phones)
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
    void joinWithPrimaryPhone() {
        // Find persons who have a primary phone
        var results = Person.join((Person p) -> p.phones)
                .where((Person p, Phone ph) -> ph.isPrimaryPhone)
                .distinct()
                .toList();

        // All 5 persons have primary phones (their mobile phones)
        assertThat(results).hasSize(5);
    }

    @Test
    void joinWithNonPrimaryPhone() {
        // Find persons who have non-primary phones
        var results = Person.join((Person p) -> p.phones)
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
    void selectJoinedReturnsPhones() {
        // Use selectJoined() to return Phone entities instead of Person entities
        var phones = Person.join((Person p) -> p.phones)
                .selectJoined()
                .toList();

        // Should return 9 phones (all phone-person pairs)
        assertThat(phones).hasSize(9);
        assertThat(phones).allMatch(obj -> obj instanceof Phone);
    }

    @Test
    void selectJoinedWithWhere() {
        // Find all mobile phones using selectJoined()
        var phones = Person.join((Person p) -> p.phones)
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
    void selectJoinedWithWhereOnWorkPhones() {
        // Find work phones using selectJoined()
        var phones = Person.join((Person p) -> p.phones)
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
    void selectJoinedWithLimit() {
        // Use selectJoined() with limit
        var phones = Person.join((Person p) -> p.phones)
                .selectJoined()
                .limit(3)
                .toList();

        assertThat(phones).hasSize(3);
    }

    @Test
    void selectJoinedWithDistinct() {
        // selectJoined() with distinct (should still work though phones are unique)
        var phones = Person.join((Person p) -> p.phones)
                .distinct()
                .selectJoined()
                .toList();

        // All 9 phones are unique
        assertThat(phones).hasSize(9);
    }
}
