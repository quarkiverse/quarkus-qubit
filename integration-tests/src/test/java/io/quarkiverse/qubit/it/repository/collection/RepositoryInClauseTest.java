package io.quarkiverse.qubit.it.repository.collection;

import io.quarkiverse.qubit.it.Person;
import io.quarkiverse.qubit.it.PersonRepository;
import io.quarkiverse.qubit.it.testdata.TestDataFactory;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Repository pattern tests for IN clause support (Iteration 5: Collections).
 * Mirrors io.quarkiverse.qubit.it.collection.InClauseTest using repository injection.
 * <p>
 * Tests the ability to use collection.contains(field) pattern in where clauses,
 * which translates to SQL IN clause.
 * <p>
 * Test data setup (5 persons):
 * <ul>
 *   <li>John Doe: age 30, active=true</li>
 *   <li>Jane Smith: age 25, active=true</li>
 *   <li>Bob Johnson: age 45, active=false</li>
 *   <li>Alice Williams: age 35, active=true</li>
 *   <li>Charlie Brown: age 28, active=true</li>
 * </ul>
 */
@QuarkusTest
class RepositoryInClauseTest {

    @Inject
    PersonRepository personRepository;

    @BeforeEach
    @Transactional
    void setupTestData() {
        TestDataFactory.clearAllData();
        TestDataFactory.createStandardPersons();
    }

    // ========== IN with String List ==========

    @Test
    void repositoryInClause_withStringList() {
        // Find persons whose firstName is in the list
        List<String> targetNames = List.of("John", "Jane", "Alice");

        List<Person> results = personRepository.where((Person p) ->
            targetNames.contains(p.firstName)
        ).toList();

        assertThat(results)
                .hasSize(3)
                .allMatch(p -> targetNames.contains(p.getFirstName()));
    }

    @Test
    void repositoryInClause_withStringListSingleMatch() {
        // Find persons whose firstName is in a list with single match
        List<String> targetNames = List.of("John", "NotExisting");

        List<Person> results = personRepository.where((Person p) ->
            targetNames.contains(p.firstName)
        ).toList();

        assertThat(results)
                .hasSize(1)
                .allMatch(p -> p.getFirstName().equals("John"));
    }

    @Test
    void repositoryInClause_withEmptyList() {
        // Find persons whose firstName is in an empty list (should return none)
        List<String> emptyList = List.of();

        List<Person> results = personRepository.where((Person p) ->
            emptyList.contains(p.firstName)
        ).toList();

        assertThat(results).isEmpty();
    }

    @Test
    void repositoryInClause_withLastName() {
        // Find persons whose lastName is in the list
        List<String> lastNames = List.of("Doe", "Smith", "Brown");

        List<Person> results = personRepository.where((Person p) ->
            lastNames.contains(p.lastName)
        ).toList();

        assertThat(results)
                .hasSize(3)
                .allMatch(p -> lastNames.contains(p.getLastName()));
    }

    // ========== IN with Integer List ==========

    @Test
    void repositoryInClause_withIntegerList() {
        // Find persons whose age is in the list
        List<Integer> targetAges = List.of(30, 25, 35);

        List<Person> results = personRepository.where((Person p) ->
            targetAges.contains(p.age)
        ).toList();

        assertThat(results)
                .hasSize(3) // John (30), Jane (25), Alice (35)
                .allMatch(p -> targetAges.contains(p.getAge()));
    }

    @Test
    void repositoryInClause_withIntegerListNoMatch() {
        // Find persons with ages not in test data
        List<Integer> targetAges = List.of(100, 200, 300);

        List<Person> results = personRepository.where((Person p) ->
            targetAges.contains(p.age)
        ).toList();

        assertThat(results).isEmpty();
    }

    // ========== IN with Set ==========

    @Test
    void repositoryInClause_withStringSet() {
        // Using Set instead of List
        Set<String> targetNames = Set.of("John", "Jane");

        List<Person> results = personRepository.where((Person p) ->
            targetNames.contains(p.firstName)
        ).toList();

        assertThat(results)
                .hasSize(2)
                .allMatch(p -> targetNames.contains(p.getFirstName()));
    }

    @Test
    void repositoryInClause_withIntegerSet() {
        // Using Set with integers
        Set<Integer> targetAges = Set.of(30, 45);

        List<Person> results = personRepository.where((Person p) ->
            targetAges.contains(p.age)
        ).toList();

        assertThat(results)
                .hasSize(2) // John (30), Bob (45)
                .allMatch(p -> targetAges.contains(p.getAge()));
    }

    // ========== IN combined with other predicates ==========

    @Test
    void repositoryInClause_combinedWithAnd() {
        // Find persons whose firstName is in list AND active is true
        List<String> targetNames = List.of("John", "Jane", "Bob");

        List<Person> results = personRepository.where((Person p) ->
            targetNames.contains(p.firstName) && p.active
        ).toList();

        assertThat(results)
                .hasSize(2) // John and Jane (Bob is inactive)
                .allMatch(p -> targetNames.contains(p.getFirstName()) && p.isActive());
    }

    @Test
    void repositoryInClause_combinedWithOr() {
        // Find persons whose firstName is in list OR age > 40
        List<String> targetNames = List.of("John", "Jane");

        List<Person> results = personRepository.where((Person p) ->
            targetNames.contains(p.firstName) || p.age > 40
        ).toList();

        assertThat(results)
                .hasSize(3) // John, Jane, Bob (age 45)
                .allMatch(p -> targetNames.contains(p.getFirstName()) || p.getAge() > 40);
    }

    @Test
    void repositoryInClause_combinedWithComparison() {
        // Find persons whose firstName is in list AND age >= 30
        List<String> targetNames = List.of("John", "Jane", "Alice", "Bob");

        List<Person> results = personRepository.where((Person p) ->
            targetNames.contains(p.firstName) && p.age >= 30
        ).toList();

        assertThat(results)
                .hasSize(3) // John (30), Alice (35), Bob (45) - Jane is 25
                .allMatch(p -> targetNames.contains(p.getFirstName()) && p.getAge() >= 30);
    }

    // ========== IN with sorting ==========

    @Test
    void repositoryInClause_withSorting() {
        List<String> targetNames = List.of("John", "Jane", "Alice");

        List<Person> results = personRepository
                .where((Person p) -> targetNames.contains(p.firstName))
                .sortedBy((Person p) -> p.firstName)
                .toList();

        assertThat(results)
                .hasSize(3)
                .extracting(Person::getFirstName)
                .containsExactly("Alice", "Jane", "John");
    }

    @Test
    void repositoryInClause_withDescendingSorting() {
        List<String> targetNames = List.of("John", "Jane", "Alice");

        List<Person> results = personRepository
                .where((Person p) -> targetNames.contains(p.firstName))
                .sortedDescendingBy((Person p) -> p.age)
                .toList();

        // Alice (35), John (30), Jane (25)
        assertThat(results)
                .hasSize(3)
                .extracting(Person::getFirstName)
                .containsExactly("Alice", "John", "Jane");
    }

    // ========== IN with pagination ==========

    @Test
    void repositoryInClause_withLimit() {
        List<String> targetNames = List.of("John", "Jane", "Alice", "Bob");

        List<Person> results = personRepository
                .where((Person p) -> targetNames.contains(p.firstName))
                .sortedBy((Person p) -> p.firstName)
                .limit(2)
                .toList();

        assertThat(results)
                .hasSize(2)
                .extracting(Person::getFirstName)
                .containsExactly("Alice", "Bob");
    }

    @Test
    void repositoryInClause_withSkipAndLimit() {
        List<String> targetNames = List.of("John", "Jane", "Alice", "Bob");

        List<Person> results = personRepository
                .where((Person p) -> targetNames.contains(p.firstName))
                .sortedBy((Person p) -> p.firstName)
                .skip(1)
                .limit(2)
                .toList();

        // Skip "Alice", take "Bob" and "Jane"
        assertThat(results)
                .hasSize(2)
                .extracting(Person::getFirstName)
                .containsExactly("Bob", "Jane");
    }

    // ========== IN with count/exists ==========

    @Test
    void repositoryInClause_withCount() {
        List<String> targetNames = List.of("John", "Jane", "Alice");

        long count = personRepository.where((Person p) ->
            targetNames.contains(p.firstName)
        ).count();

        assertThat(count).isEqualTo(3);
    }

    @Test
    void repositoryInClause_withExists() {
        List<String> targetNames = List.of("John", "NotExisting");

        boolean exists = personRepository.where((Person p) ->
            targetNames.contains(p.firstName)
        ).exists();

        assertThat(exists).isTrue();
    }

    @Test
    void repositoryInClause_withExistsNoMatch() {
        List<String> targetNames = List.of("NotExisting", "AlsoNotExisting");

        boolean exists = personRepository.where((Person p) ->
            targetNames.contains(p.firstName)
        ).exists();

        assertThat(exists).isFalse();
    }

    // ========== IN with distinct ==========

    @Test
    void repositoryInClause_withDistinct() {
        List<String> targetNames = List.of("John", "Jane", "Alice");

        List<Person> results = personRepository
                .where((Person p) -> targetNames.contains(p.firstName))
                .distinct()
                .toList();

        assertThat(results)
                .hasSize(3)
                .allMatch(p -> targetNames.contains(p.getFirstName()));
    }

    // ========== IN with projection ==========

    @Test
    void repositoryInClause_withProjection() {
        List<String> targetNames = List.of("John", "Jane", "Alice");

        List<String> emails = personRepository
                .where((Person p) -> targetNames.contains(p.firstName))
                .select((Person p) -> p.email)
                .toList();

        assertThat(emails)
                .hasSize(3)
                .allMatch(email -> email.contains("@example.com"));
    }

    @Test
    void repositoryInClause_withAgeProjection() {
        List<String> targetNames = List.of("John", "Jane", "Alice");

        List<Integer> ages = personRepository
                .where((Person p) -> targetNames.contains(p.firstName))
                .sortedBy((Person p) -> p.firstName)
                .select((Person p) -> p.age)
                .toList();

        // Alice (35), Jane (25), John (30)
        assertThat(ages).containsExactly(35, 25, 30);
    }

    // ========== Multiple IN clauses ==========

    @Test
    void repositoryMultipleInClauses() {
        // Combine two IN clauses
        List<String> firstNames = List.of("John", "Jane", "Alice");
        List<String> lastNames = List.of("Doe", "Williams");

        List<Person> results = personRepository.where((Person p) ->
            firstNames.contains(p.firstName) && lastNames.contains(p.lastName)
        ).toList();

        // John Doe, Alice Williams match both
        assertThat(results)
                .hasSize(2)
                .allMatch(p -> firstNames.contains(p.getFirstName()) &&
                               lastNames.contains(p.getLastName()));
    }

    @Test
    void repositoryInClause_withEmailField() {
        // Find persons whose email is in the list
        List<String> targetEmails = List.of("john.doe@example.com", "jane.smith@example.com");

        List<Person> results = personRepository.where((Person p) ->
            targetEmails.contains(p.email)
        ).toList();

        assertThat(results)
                .hasSize(2)
                .allMatch(p -> targetEmails.contains(p.getEmail()));
    }
}
