package io.quarkus.qusaq.it.repository.fluent;

import io.quarkus.qusaq.it.Person;
import io.quarkus.qusaq.it.PersonRepository;
import io.quarkus.qusaq.it.testdata.TestDataFactory;
import io.quarkus.qusaq.runtime.QusaqStream;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Repository pattern tests for fluent API.
 * Mirrors io.quarkus.qusaq.it.fluent.BasicQueryTest using repository injection.
 */
@QuarkusTest
class RepositoryBasicQueryTest {

    @Inject
    PersonRepository repository;

    @BeforeEach
    @Transactional
    void setupTestData() {
        TestDataFactory.clearAllData();
        TestDataFactory.createStandardPersons();
    }

    // =============================================================================================
    // COMPILATION TESTS - Verify fluent API compiles
    // =============================================================================================

    @Test
    void fluent_where_returnsQusaqStream() {
        // Verify that where() returns QusaqStream
        QusaqStream<Person> stream = repository.where((Person p) -> p.age >= 18);

        assertThat(stream)
                .isNotNull()
                .isInstanceOf(QusaqStream.class);
    }

    @Test
    void fluent_select_returnsQusaqStream() {
        // Verify that select() returns QusaqStream
        QusaqStream<String> stream = repository.select((Person p) -> p.firstName);

        assertThat(stream)
                .isNotNull()
                .isInstanceOf(QusaqStream.class);
    }

    @Test
    void fluent_sortedBy_returnsQusaqStream() {
        // Verify that sortedBy() returns QusaqStream
        QusaqStream<Person> stream = repository.sortedBy((Person p) -> Integer.valueOf(p.age));

        assertThat(stream)
                .isNotNull()
                .isInstanceOf(QusaqStream.class);
    }

    @Test
    void fluent_sortedDescendingBy_returnsQusaqStream() {
        // Verify that sortedDescendingBy() returns QusaqStream
        QusaqStream<Person> stream = repository.sortedDescendingBy((Person p) -> Integer.valueOf(p.age));

        assertThat(stream)
                .isNotNull()
                .isInstanceOf(QusaqStream.class);
    }

    @Test
    void fluent_methodChaining_compiles() {
        // Verify that method chaining compiles correctly
        QusaqStream<Person> stream = repository.where((Person p) -> p.age > 25)
                .where((Person p) -> p.active);

        assertThat(stream).isNotNull();
    }

    // =============================================================================================
    // EXECUTION TESTS - Basic where().toList() and where().count()
    // =============================================================================================

    @Test
    @Transactional
    void where_filtersByPredicate() {
        List<Person> adults = repository.where((Person p) -> p.age >= 18).toList();

        assertThat(adults)
                .isNotEmpty()
                .allMatch(p -> p.age >= 18);
    }

    @Test
    @Transactional
    void multipleWhere_combinesWithAnd() {
        List<Person> results = repository.where((Person p) -> p.age > 25)
                .where((Person p) -> p.active)
                .toList();

        assertThat(results)
                .isNotEmpty()
                .allMatch(p -> p.age > 25 && p.active);
    }

    @Test
    @Transactional
    void whereCount_filtersAndCounts() {
        long count = repository.where((Person p) -> p.active).count();

        assertThat(count).isGreaterThan(0);
    }

    @Test
    @Transactional
    void distinct_removeDuplicates() {
        List<String> unique = repository.select((Person p) -> p.lastName)
                .distinct()
                .toList();

        assertThat(unique)
                .isNotEmpty()
                .doesNotHaveDuplicates();
    }
}
