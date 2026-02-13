package io.quarkiverse.qubit.it.collection;

import io.quarkiverse.qubit.it.Person;
import io.quarkiverse.qubit.it.testdata.TestDataFactory;
import io.quarkiverse.qubit.it.testutil.PersonQueryOperations;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Abstract base class for IN clause support tests.
 * Tests the ability to use collection.contains(field) pattern in where clauses,
 * which translates to SQL IN clause.
 */
public abstract class AbstractInClauseTest {

    protected abstract PersonQueryOperations personOps();

    @BeforeEach
    @Transactional
    protected void setupTestData() {
        TestDataFactory.clearAllData();
        TestDataFactory.createStandardPersons();
    }

    @Test
    void inClause_withStringList() {
        List<String> targetNames = List.of("John", "Jane", "Alice");

        List<Person> results = personOps().where((Person p) -> targetNames.contains(p.firstName)).toList();

        assertThat(results)
                .hasSize(3)
                .allMatch(p -> targetNames.contains(p.getFirstName()));
    }

    @Test
    void inClause_withStringListSingleMatch() {
        List<String> targetNames = List.of("John", "NotExisting");

        List<Person> results = personOps().where((Person p) -> targetNames.contains(p.firstName)).toList();

        assertThat(results)
                .hasSize(1)
                .allMatch(p -> p.getFirstName().equals("John"));
    }

    @Test
    void inClause_withEmptyList() {
        List<String> emptyList = List.of();

        List<Person> results = personOps().where((Person p) -> emptyList.contains(p.firstName)).toList();

        assertThat(results).isEmpty();
    }

    @Test
    void inClause_withLastName() {
        List<String> lastNames = List.of("Doe", "Smith", "Brown");

        List<Person> results = personOps().where((Person p) -> lastNames.contains(p.lastName)).toList();

        assertThat(results)
                .hasSize(3)
                .allMatch(p -> lastNames.contains(p.getLastName()));
    }

    @Test
    void inClause_withIntegerList() {
        List<Integer> targetAges = List.of(30, 25, 35);

        List<Person> results = personOps().where((Person p) -> targetAges.contains(p.age)).toList();

        assertThat(results)
                .hasSize(3)
                .allMatch(p -> targetAges.contains(p.getAge()));
    }

    @Test
    void inClause_withIntegerListNoMatch() {
        List<Integer> targetAges = List.of(100, 200, 300);

        List<Person> results = personOps().where((Person p) -> targetAges.contains(p.age)).toList();

        assertThat(results).isEmpty();
    }

    @Test
    void inClause_withStringSet() {
        Set<String> targetNames = Set.of("John", "Jane");

        List<Person> results = personOps().where((Person p) -> targetNames.contains(p.firstName)).toList();

        assertThat(results)
                .hasSize(2)
                .allMatch(p -> targetNames.contains(p.getFirstName()));
    }

    @Test
    void inClause_withIntegerSet() {
        Set<Integer> targetAges = Set.of(30, 45);

        List<Person> results = personOps().where((Person p) -> targetAges.contains(p.age)).toList();

        assertThat(results)
                .hasSize(2)
                .allMatch(p -> targetAges.contains(p.getAge()));
    }

    @Test
    void inClause_combinedWithAnd() {
        List<String> targetNames = List.of("John", "Jane", "Bob");

        List<Person> results = personOps().where((Person p) -> targetNames.contains(p.firstName) && p.active).toList();

        assertThat(results)
                .hasSize(2)
                .allMatch(p -> targetNames.contains(p.getFirstName()) && p.isActive());
    }

    @Test
    void inClause_combinedWithOr() {
        List<String> targetNames = List.of("John", "Jane");

        List<Person> results = personOps().where((Person p) -> targetNames.contains(p.firstName) || p.age > 40).toList();

        assertThat(results)
                .hasSize(3)
                .allMatch(p -> targetNames.contains(p.getFirstName()) || p.getAge() > 40);
    }

    @Test
    void inClause_combinedWithComparison() {
        List<String> targetNames = List.of("John", "Jane", "Alice", "Bob");

        List<Person> results = personOps().where((Person p) -> targetNames.contains(p.firstName) && p.age >= 30).toList();

        assertThat(results)
                .hasSize(3)
                .allMatch(p -> targetNames.contains(p.getFirstName()) && p.getAge() >= 30);
    }

    @Test
    void inClause_withSorting() {
        List<String> targetNames = List.of("John", "Jane", "Alice");

        List<Person> results = personOps()
                .where((Person p) -> targetNames.contains(p.firstName))
                .sortedBy((Person p) -> p.firstName)
                .toList();

        assertThat(results)
                .hasSize(3)
                .extracting(Person::getFirstName)
                .containsExactly("Alice", "Jane", "John");
    }

    @Test
    void inClause_withDescendingSorting() {
        List<String> targetNames = List.of("John", "Jane", "Alice");

        List<Person> results = personOps()
                .where((Person p) -> targetNames.contains(p.firstName))
                .sortedDescendingBy((Person p) -> p.age)
                .toList();

        assertThat(results)
                .hasSize(3)
                .extracting(Person::getFirstName)
                .containsExactly("Alice", "John", "Jane");
    }

    @Test
    void inClause_withLimit() {
        List<String> targetNames = List.of("John", "Jane", "Alice", "Bob");

        List<Person> results = personOps()
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
    void inClause_withSkipAndLimit() {
        List<String> targetNames = List.of("John", "Jane", "Alice", "Bob");

        List<Person> results = personOps()
                .where((Person p) -> targetNames.contains(p.firstName))
                .sortedBy((Person p) -> p.firstName)
                .skip(1)
                .limit(2)
                .toList();

        assertThat(results)
                .hasSize(2)
                .extracting(Person::getFirstName)
                .containsExactly("Bob", "Jane");
    }

    @Test
    void inClause_withCount() {
        List<String> targetNames = List.of("John", "Jane", "Alice");

        long count = personOps().where((Person p) -> targetNames.contains(p.firstName)).count();

        assertThat(count).isEqualTo(3);
    }

    @Test
    void inClause_withExists() {
        List<String> targetNames = List.of("John", "NotExisting");

        boolean exists = personOps().where((Person p) -> targetNames.contains(p.firstName)).exists();

        assertThat(exists).isTrue();
    }

    @Test
    void inClause_withExistsNoMatch() {
        List<String> targetNames = List.of("NotExisting", "AlsoNotExisting");

        boolean exists = personOps().where((Person p) -> targetNames.contains(p.firstName)).exists();

        assertThat(exists).isFalse();
    }

    @Test
    void inClause_withDistinct() {
        List<String> targetNames = List.of("John", "Jane", "Alice");

        List<Person> results = personOps()
                .where((Person p) -> targetNames.contains(p.firstName))
                .distinct()
                .toList();

        assertThat(results)
                .hasSize(3)
                .allMatch(p -> targetNames.contains(p.getFirstName()));
    }

    @Test
    void inClause_withProjection() {
        List<String> targetNames = List.of("John", "Jane", "Alice");

        List<String> emails = personOps()
                .where((Person p) -> targetNames.contains(p.firstName))
                .select((Person p) -> p.email)
                .toList();

        assertThat(emails)
                .hasSize(3)
                .allMatch(email -> email.contains("@example.com"));
    }

    @Test
    void inClause_withAgeProjection() {
        List<String> targetNames = List.of("John", "Jane", "Alice");

        List<Integer> ages = personOps()
                .where((Person p) -> targetNames.contains(p.firstName))
                .sortedBy((Person p) -> p.firstName)
                .select((Person p) -> p.age)
                .toList();

        assertThat(ages).containsExactly(35, 25, 30);
    }

    @Test
    void multipleInClauses() {
        List<String> firstNames = List.of("John", "Jane", "Alice");
        List<String> lastNames = List.of("Doe", "Williams");

        List<Person> results = personOps()
                .where((Person p) -> firstNames.contains(p.firstName) && lastNames.contains(p.lastName)).toList();

        assertThat(results)
                .hasSize(2)
                .allMatch(p -> firstNames.contains(p.getFirstName()) &&
                        lastNames.contains(p.getLastName()));
    }

    @Test
    void inClause_withEmailField() {
        List<String> targetEmails = List.of("john.doe@example.com", "jane.smith@example.com");

        List<Person> results = personOps().where((Person p) -> targetEmails.contains(p.email)).toList();

        assertThat(results)
                .hasSize(2)
                .allMatch(p -> targetEmails.contains(p.getEmail()));
    }
}
