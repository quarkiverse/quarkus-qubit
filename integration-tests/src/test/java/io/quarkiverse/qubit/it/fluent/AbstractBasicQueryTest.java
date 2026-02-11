package io.quarkiverse.qubit.it.fluent;

import io.quarkiverse.qubit.it.Person;
import io.quarkiverse.qubit.it.testdata.TestDataFactory;
import io.quarkiverse.qubit.it.testutil.PersonQueryOperations;
import io.quarkiverse.qubit.QubitStream;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Abstract base class for basic fluent API query tests.
 *
 * <p>
 * Contains all test methods that can be run with either static entity methods
 * or repository instance methods.
 */
public abstract class AbstractBasicQueryTest {

    protected abstract PersonQueryOperations personOps();

    @BeforeEach
    @Transactional
    protected void setupTestData() {
        TestDataFactory.clearAllData();
        TestDataFactory.createStandardPersons();
    }

    // =============================================================================================
    // COMPILATION TESTS - Verify fluent API compiles
    // =============================================================================================

    @Test
    void fluent_where_returnsQubitStream() {
        // Verify that where() returns QubitStream
        QubitStream<Person> stream = personOps().where((Person p) -> p.age >= 18);

        assertThat(stream)
                .isNotNull()
                .isInstanceOf(QubitStream.class);
    }

    @Test
    void fluent_select_returnsQubitStream() {
        // Verify that select() returns QubitStream
        QubitStream<String> stream = personOps().select((Person p) -> p.firstName);

        assertThat(stream)
                .isNotNull()
                .isInstanceOf(QubitStream.class);
    }

    @Test
    void fluent_sortedBy_returnsQubitStream() {
        // Verify that sortedBy() returns QubitStream
        QubitStream<Person> stream = personOps().sortedBy((Person p) -> Integer.valueOf(p.age));

        assertThat(stream)
                .isNotNull()
                .isInstanceOf(QubitStream.class);
    }

    @Test
    void fluent_sortedDescendingBy_returnsQubitStream() {
        // Verify that sortedDescendingBy() returns QubitStream
        QubitStream<Person> stream = personOps().sortedDescendingBy((Person p) -> Integer.valueOf(p.age));

        assertThat(stream)
                .isNotNull()
                .isInstanceOf(QubitStream.class);
    }

    @Test
    void fluent_methodChaining_compiles() {
        // Verify that method chaining compiles correctly
        QubitStream<Person> stream = personOps().where((Person p) -> p.age > 25)
                .where((Person p) -> p.active);

        assertThat(stream).isNotNull();
    }

    // =============================================================================================
    // EXECUTION TESTS - Basic where().toList() and where().count()
    // =============================================================================================

    @Test
    @Transactional
    void where_filtersByPredicate() {
        List<Person> adults = personOps().where((Person p) -> p.age >= 18).toList();

        assertThat(adults)
                .isNotEmpty()
                .allMatch(p -> p.age >= 18);
    }

    @Test
    @Transactional
    void multipleWhere_combinesWithAnd() {
        List<Person> results = personOps().where((Person p) -> p.age > 25)
                .where((Person p) -> p.active)
                .toList();

        assertThat(results)
                .isNotEmpty()
                .allMatch(p -> p.age > 25 && p.active);
    }

    @Test
    @Transactional
    void whereCount_filtersAndCounts() {
        long count = personOps().where((Person p) -> p.active).count();

        assertThat(count).isGreaterThan(0);
    }

    @Test
    @Transactional
    void distinct_removeDuplicates() {
        List<String> unique = personOps().select((Person p) -> p.lastName)
                .distinct()
                .toList();

        assertThat(unique)
                .isNotEmpty()
                .doesNotHaveDuplicates();
    }
}
