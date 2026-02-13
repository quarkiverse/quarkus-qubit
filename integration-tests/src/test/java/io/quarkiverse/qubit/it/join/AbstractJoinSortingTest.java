package io.quarkiverse.qubit.it.join;

import io.quarkiverse.qubit.it.Person;
import io.quarkiverse.qubit.it.Phone;
import io.quarkiverse.qubit.it.testdata.TestDataFactory;
import io.quarkiverse.qubit.it.testutil.PersonQueryOperations;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Abstract base class for join query sorting tests.
 * <p>
 * Tests sortedBy() and sortedDescendingBy() operations on JoinStream with bi-entity lambdas.
 * The test data creates 5 persons with phones:
 * <ul>
 * <li>John (30): 2 phones (mobile, work)</li>
 * <li>Jane (25): 1 phone (mobile)</li>
 * <li>Bob (45): 3 phones (mobile, home, work)</li>
 * <li>Alice (35): 2 phones (mobile, work)</li>
 * <li>Charlie (28): 1 phone (mobile)</li>
 * </ul>
 */
public abstract class AbstractJoinSortingTest {

    protected abstract PersonQueryOperations personOps();

    @BeforeEach
    @Transactional
    protected void setupTestData() {
        TestDataFactory.clearAllData();
        TestDataFactory.createPersonsWithPhones();
    }

    @Test
    void sortBySourceEntityFieldAscending() {
        // Sort persons with mobile phones by age ascending
        var results = personOps().join((Person p) -> p.phones)
                .where((Person p, Phone ph) -> ph.type.equals("mobile"))
                .sortedBy((Person p, Phone ph) -> p.age)
                .distinct()
                .toList();

        // Jane(25), Charlie(28), John(30), Alice(35), Bob(45)
        assertThat(results)
                .hasSize(5)
                .extracting(p -> p.firstName)
                .containsExactly("Jane", "Charlie", "John", "Alice", "Bob");
    }

    @Test
    void sortBySourceEntityFieldDescending() {
        // Sort persons with mobile phones by age descending
        var results = personOps().join((Person p) -> p.phones)
                .where((Person p, Phone ph) -> ph.type.equals("mobile"))
                .sortedDescendingBy((Person p, Phone ph) -> p.age)
                .distinct()
                .toList();

        // Bob(45), Alice(35), John(30), Charlie(28), Jane(25)
        assertThat(results)
                .hasSize(5)
                .extracting(p -> p.firstName)
                .containsExactly("Bob", "Alice", "John", "Charlie", "Jane");
    }

    @Test
    void sortBySourceEntityNameAscending() {
        // Sort persons with mobile phones by first name ascending
        var results = personOps().join((Person p) -> p.phones)
                .where((Person p, Phone ph) -> ph.type.equals("mobile"))
                .sortedBy((Person p, Phone ph) -> p.firstName)
                .distinct()
                .toList();

        // Alice, Bob, Charlie, Jane, John (alphabetical order)
        assertThat(results)
                .hasSize(5)
                .extracting(p -> p.firstName)
                .containsExactly("Alice", "Bob", "Charlie", "Jane", "John");
    }

    @Test
    void sortByJoinedEntityFieldAscending() {
        // Sort by phone number ascending for persons with work phones
        var results = personOps().join((Person p) -> p.phones)
                .where((Person p, Phone ph) -> ph.type.equals("work"))
                .sortedBy((Person p, Phone ph) -> ph.number)
                .toList();

        // Work phone numbers: John(555-0102), Bob(555-0303), Alice(555-0402)
        assertThat(results)
                .hasSize(3)
                .extracting(p -> p.firstName)
                .containsExactly("John", "Bob", "Alice");
    }

    @Test
    void sortByJoinedEntityFieldDescending() {
        // Sort by phone number descending for persons with work phones
        var results = personOps().join((Person p) -> p.phones)
                .where((Person p, Phone ph) -> ph.type.equals("work"))
                .sortedDescendingBy((Person p, Phone ph) -> ph.number)
                .toList();

        // Work phone numbers reversed: Alice(555-0402), Bob(555-0303), John(555-0102)
        assertThat(results)
                .hasSize(3)
                .extracting(p -> p.firstName)
                .containsExactly("Alice", "Bob", "John");
    }

    @Test
    void sortByJoinedEntityType() {
        // Sort all phone joins by type
        // Note: Hibernate deduplicates entity instances, so we get 5 unique persons
        // but the ordering is based on the first matching phone type per person
        var results = personOps().join((Person p) -> p.phones)
                .sortedBy((Person p, Phone ph) -> ph.type)
                .toList();

        // Hibernate returns 5 unique Person entities (deduplicated)
        assertThat(results).hasSize(5);

        // First should be Bob (only person with "home" phone - "home" < "mobile" < "work")
        assertThat(results.getFirst().firstName).isEqualTo("Bob");
    }

    @Test
    void sortByMultipleCriteria() {
        // Sort by type then by phone number (last call wins - type is primary)
        // Note: Hibernate deduplicates entity instances, so we get 5 unique persons
        var results = personOps().join((Person p) -> p.phones)
                .sortedBy((Person p, Phone ph) -> ph.number)
                .sortedBy((Person p, Phone ph) -> ph.type)
                .toList();

        // Hibernate returns 5 unique Person entities (deduplicated)
        assertThat(results).hasSize(5);

        // First result should be from Bob (only person with home phone - "home" < "mobile" < "work")
        assertThat(results.getFirst().firstName).isEqualTo("Bob");
    }

    @Test
    void sortBySourceThenJoinedEntity() {
        // Sort by source entity age, then by phone type (last wins - age is primary)
        // Note: Hibernate deduplicates entity instances, so we get 5 unique persons
        var results = personOps().join((Person p) -> p.phones)
                .sortedBy((Person p, Phone ph) -> ph.type)
                .sortedBy((Person p, Phone ph) -> p.age)
                .toList();

        // Should be ordered by age first (youngest to oldest)
        // Jane(25) < Charlie(28) < John(30) < Alice(35) < Bob(45)
        // Hibernate returns 5 unique Person entities (deduplicated)
        assertThat(results).hasSize(5);

        // First results should be from Jane (age 25)
        assertThat(results.getFirst().firstName).isEqualTo("Jane");
    }

    @Test
    void sortWithLimit() {
        // Get top 3 youngest persons with mobile phones
        var results = personOps().join((Person p) -> p.phones)
                .where((Person p, Phone ph) -> ph.type.equals("mobile"))
                .sortedBy((Person p, Phone ph) -> p.age)
                .limit(3)
                .toList();

        assertThat(results)
                .hasSize(3)
                .extracting(p -> p.firstName)
                .containsExactly("Jane", "Charlie", "John");
    }

    @Test
    void sortWithSkipAndLimit() {
        // Get 2 persons after skipping the youngest 2
        var results = personOps().join((Person p) -> p.phones)
                .where((Person p, Phone ph) -> ph.type.equals("mobile"))
                .sortedBy((Person p, Phone ph) -> p.age)
                .skip(2)
                .limit(2)
                .toList();

        // Skip Jane(25), Charlie(28), get John(30), Alice(35)
        assertThat(results)
                .hasSize(2)
                .extracting(p -> p.firstName)
                .containsExactly("John", "Alice");
    }

    @Test
    void sortDescendingWithLimit() {
        // Get top 3 oldest persons with mobile phones
        var results = personOps().join((Person p) -> p.phones)
                .where((Person p, Phone ph) -> ph.type.equals("mobile"))
                .sortedDescendingBy((Person p, Phone ph) -> p.age)
                .limit(3)
                .toList();

        assertThat(results)
                .hasSize(3)
                .extracting(p -> p.firstName)
                .containsExactly("Bob", "Alice", "John");
    }

    @Test
    void sortWithDistinct() {
        // Sort distinct persons by age ascending
        var results = personOps().join((Person p) -> p.phones)
                .sortedBy((Person p, Phone ph) -> p.age)
                .distinct()
                .toList();

        assertThat(results)
                .hasSize(5)
                .extracting(p -> p.firstName)
                .containsExactly("Jane", "Charlie", "John", "Alice", "Bob");
    }

    @Test
    void sortDescendingWithDistinct() {
        // Sort distinct persons by age descending
        var results = personOps().join((Person p) -> p.phones)
                .sortedDescendingBy((Person p, Phone ph) -> p.age)
                .distinct()
                .toList();

        assertThat(results)
                .hasSize(5)
                .extracting(p -> p.firstName)
                .containsExactly("Bob", "Alice", "John", "Charlie", "Jane");
    }

    @Test
    void sortWithComplexWhere() {
        // Find active persons over 27 with mobile phones, sorted by age
        var results = personOps().join((Person p) -> p.phones)
                .where((Person p, Phone ph) -> ph.type.equals("mobile"))
                .where((Person p, Phone ph) -> p.active && p.age > 27)
                .sortedBy((Person p, Phone ph) -> p.age)
                .distinct()
                .toList();

        // Active persons over 27 with mobile: Charlie(28), John(30), Alice(35)
        // Bob(45) is inactive
        assertThat(results)
                .hasSize(3)
                .extracting(p -> p.firstName)
                .containsExactly("Charlie", "John", "Alice");
    }

    @Test
    void sortWithWhereOnBothEntities() {
        // Persons with work phones under 40, sorted by age descending
        var results = personOps().join((Person p) -> p.phones)
                .where((Person p, Phone ph) -> ph.type.equals("work") && p.age < 40)
                .sortedDescendingBy((Person p, Phone ph) -> p.age)
                .distinct()
                .toList();

        // Work phones: John(30), Alice(35), Bob(45) - exclude Bob (45 >= 40)
        assertThat(results)
                .hasSize(2)
                .extracting(p -> p.firstName)
                .containsExactly("Alice", "John");
    }

    @Test
    void leftJoinSortBySourceEntity() {
        // Left join sorted by source entity age
        var results = personOps().leftJoin((Person p) -> p.phones)
                .sortedBy((Person p, Phone ph) -> p.age)
                .distinct()
                .toList();

        assertThat(results)
                .hasSize(5)
                .extracting(p -> p.firstName)
                .containsExactly("Jane", "Charlie", "John", "Alice", "Bob");
    }

    @Test
    void leftJoinSortBySourceEntityDescending() {
        // Left join sorted by source entity age descending
        var results = personOps().leftJoin((Person p) -> p.phones)
                .sortedDescendingBy((Person p, Phone ph) -> p.age)
                .distinct()
                .toList();

        assertThat(results)
                .hasSize(5)
                .extracting(p -> p.firstName)
                .containsExactly("Bob", "Alice", "John", "Charlie", "Jane");
    }

    @Test
    void findFirstWithSort() {
        // Find the youngest person with a mobile phone
        var result = personOps().join((Person p) -> p.phones)
                .where((Person p, Phone ph) -> ph.type.equals("mobile"))
                .sortedBy((Person p, Phone ph) -> p.age)
                .findFirst();

        assertThat(result)
                .isPresent()
                .hasValueSatisfying(p -> assertThat(p.firstName).isEqualTo("Jane"));
    }

    @Test
    void findFirstWithSortDescending() {
        // Find the oldest person with a mobile phone
        var result = personOps().join((Person p) -> p.phones)
                .where((Person p, Phone ph) -> ph.type.equals("mobile"))
                .sortedDescendingBy((Person p, Phone ph) -> p.age)
                .findFirst();

        assertThat(result)
                .isPresent()
                .hasValueSatisfying(p -> assertThat(p.firstName).isEqualTo("Bob"));
    }

    @Test
    void getSingleResultWithSortAndLimit() {
        // Get the single youngest person with work phone
        var result = personOps().join((Person p) -> p.phones)
                .where((Person p, Phone ph) -> ph.type.equals("work"))
                .sortedBy((Person p, Phone ph) -> p.age)
                .limit(1)
                .getSingleResult();

        assertThat(result.firstName).isEqualTo("John");
    }
}
