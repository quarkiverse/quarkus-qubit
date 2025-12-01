package io.quarkiverse.qubit.it.fluent;

import io.quarkiverse.qubit.it.Person;
import io.quarkiverse.qubit.it.testdata.TestDataFactory;
import io.quarkiverse.qubit.runtime.QubitStream;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for Phase 1 fluent API.
 * <p>
 * NOTE: Most tests are disabled in Phase 1 as they require full implementation.
 * These tests verify that the API compiles and stubs are correctly generated.
 */
@QuarkusTest
class BasicQueryTest {

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
    void fluent_where_returnsQubitStream() {
        // Verify that where() returns QubitStream
        QubitStream<Person> stream = Person.where((Person p) -> p.age >= 18);

        assertThat(stream)
                .isNotNull()
                .isInstanceOf(QubitStream.class);
    }

    @Test
    void fluent_select_returnsQubitStream() {
        // Verify that select() returns QubitStream
        QubitStream<String> stream = Person.select((Person p) -> p.firstName);

        assertThat(stream)
                .isNotNull()
                .isInstanceOf(QubitStream.class);
    }

    @Test
    void fluent_sortedBy_returnsQubitStream() {
        // Verify that sortedBy() returns QubitStream
        QubitStream<Person> stream = Person.sortedBy((Person p) -> Integer.valueOf(p.age));

        assertThat(stream)
                .isNotNull()
                .isInstanceOf(QubitStream.class);
    }

    @Test
    void fluent_sortedDescendingBy_returnsQubitStream() {
        // Verify that sortedDescendingBy() returns QubitStream
        QubitStream<Person> stream = Person.sortedDescendingBy((Person p) -> Integer.valueOf(p.age));

        assertThat(stream)
                .isNotNull()
                .isInstanceOf(QubitStream.class);
    }

    @Test
    void fluent_methodChaining_compiles() {
        // Verify that method chaining compiles correctly
        QubitStream<Person> stream = Person.where((Person p) -> p.age > 25)
                .where((Person p) -> p.active);

        assertThat(stream).isNotNull();
    }

    // =============================================================================================
    // PHASE 1 EXECUTION TESTS - Basic where().toList() and where().count()
    // =============================================================================================

    @Test
    @Transactional
    void where_filtersByPredicate() {
        List<Person> adults = Person.where((Person p) -> p.age >= 18).toList();

        assertThat(adults)
                .isNotEmpty()
                .allMatch(p -> p.age >= 18);
    }

    @Test
    @Transactional
    void multipleWhere_combinesWithAnd() {
        List<Person> results = Person.where((Person p) -> p.age > 25)
                .where((Person p) -> p.active)
                .toList();

        assertThat(results)
                .isNotEmpty()
                .allMatch(p -> p.age > 25 && p.active);
    }

    @Test
    @Transactional
    void whereCount_filtersAndCounts() {
        long count = Person.where((Person p) -> p.active).count();

        assertThat(count).isGreaterThan(0);
    }

    // =============================================================================================
    // DISABLED TESTS - Will be enabled in future phases
    // =============================================================================================

    @Test
    @Transactional
    void distinct_removeDuplicates() {
        List<String> unique = Person.select((Person p) -> p.lastName)
                .distinct()
                .toList();

        assertThat(unique)
                .isNotEmpty()
                .doesNotHaveDuplicates();
    }
}
