package io.quarkiverse.qubit.it.testutil;

import io.quarkiverse.qubit.it.Person;
import io.quarkiverse.qubit.JoinStream;
import io.quarkiverse.qubit.QuerySpec;
import io.quarkiverse.qubit.QubitStream;
import io.quarkiverse.qubit.ScalarResult;

import java.util.Collection;

/**
 * Abstraction for Person query operations that allows the same test logic
 * to be run against both static entity methods and repository instance methods.
 *
 * <p>
 * This interface eliminates duplication between:
 * <ul>
 * <li>Tests using {@code Person.where()}, {@code Person.select()}, etc.</li>
 * <li>Tests using {@code personRepository.where()}, {@code personRepository.select()}, etc.</li>
 * </ul>
 *
 * <p>
 * Use {@link StaticPersonQueryOperations} for static entity methods and
 * inject/create a repository-based implementation for repository pattern tests.
 */
public interface PersonQueryOperations {

    /**
     * Returns a descriptive name for this query source (e.g., "Static" or "Repository").
     */
    String getSourceName();

    /**
     * Creates a filtered query stream.
     */
    QubitStream<Person> where(QuerySpec<Person, Boolean> spec);

    /**
     * Creates a projection query stream.
     */
    <R> QubitStream<R> select(QuerySpec<Person, R> mapper);

    /**
     * Creates a sorted query stream (ascending).
     */
    <K extends Comparable<K>> QubitStream<Person> sortedBy(QuerySpec<Person, K> keyExtractor);

    /**
     * Creates a sorted query stream (descending).
     */
    <K extends Comparable<K>> QubitStream<Person> sortedDescendingBy(QuerySpec<Person, K> keyExtractor);

    /**
     * Creates a MIN aggregation query.
     */
    <K extends Comparable<K>> ScalarResult<K> min(QuerySpec<Person, K> mapper);

    /**
     * Creates a MAX aggregation query.
     */
    <K extends Comparable<K>> ScalarResult<K> max(QuerySpec<Person, K> mapper);

    /**
     * Creates an AVG aggregation query.
     */
    ScalarResult<Double> avg(QuerySpec<Person, ? extends Number> mapper);

    /**
     * Creates a SUM aggregation query for Integer fields.
     */
    ScalarResult<Long> sumInteger(QuerySpec<Person, Integer> mapper);

    /**
     * Creates a SUM aggregation query for Long fields.
     */
    ScalarResult<Long> sumLong(QuerySpec<Person, Long> mapper);

    /**
     * Creates a SUM aggregation query for Double fields.
     */
    ScalarResult<Double> sumDouble(QuerySpec<Person, Double> mapper);

    /**
     * Creates a join query stream.
     */
    <R> JoinStream<Person, R> join(QuerySpec<Person, Collection<R>> relationship);

    /**
     * Creates a left join query stream.
     */
    <R> JoinStream<Person, R> leftJoin(QuerySpec<Person, Collection<R>> relationship);
}
