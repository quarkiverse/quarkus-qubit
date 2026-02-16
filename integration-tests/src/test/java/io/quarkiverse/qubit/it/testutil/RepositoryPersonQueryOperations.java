package io.quarkiverse.qubit.it.testutil;

import io.quarkiverse.qubit.it.Person;
import io.quarkiverse.qubit.it.PersonRepository;
import io.quarkiverse.qubit.JoinStream;
import io.quarkiverse.qubit.QuerySpec;
import io.quarkiverse.qubit.QubitStream;
import io.quarkiverse.qubit.ScalarResult;

import java.util.Collection;

/**
 * Implementation of {@link PersonQueryOperations} using repository instance methods.
 *
 * <p>
 * Delegates all operations to the injected {@link PersonRepository}.
 */
public class RepositoryPersonQueryOperations implements PersonQueryOperations {

    private final PersonRepository repository;

    public RepositoryPersonQueryOperations(PersonRepository repository) {
        this.repository = repository;
    }

    @Override
    public String getSourceName() {
        return "Repository";
    }

    @Override
    public QubitStream<Person> where(QuerySpec<Person, Boolean> spec) {
        return repository.where(spec);
    }

    @Override
    public <R> QubitStream<R> select(QuerySpec<Person, R> mapper) {
        return repository.select(mapper);
    }

    @Override
    public <K extends Comparable<K>> QubitStream<Person> sortedBy(QuerySpec<Person, K> keyExtractor) {
        return repository.sortedBy(keyExtractor);
    }

    @Override
    public <K extends Comparable<K>> QubitStream<Person> sortedDescendingBy(QuerySpec<Person, K> keyExtractor) {
        return repository.sortedDescendingBy(keyExtractor);
    }

    @Override
    public <K extends Comparable<K>> ScalarResult<K> min(QuerySpec<Person, K> mapper) {
        return repository.min(mapper);
    }

    @Override
    public <K extends Comparable<K>> ScalarResult<K> max(QuerySpec<Person, K> mapper) {
        return repository.max(mapper);
    }

    @Override
    public ScalarResult<Double> avg(QuerySpec<Person, ? extends Number> mapper) {
        return repository.avg(mapper);
    }

    @Override
    public ScalarResult<Long> sumInteger(QuerySpec<Person, Integer> mapper) {
        return repository.sumInteger(mapper);
    }

    @Override
    public ScalarResult<Long> sumLong(QuerySpec<Person, Long> mapper) {
        return repository.sumLong(mapper);
    }

    @Override
    public ScalarResult<Double> sumDouble(QuerySpec<Person, Double> mapper) {
        return repository.sumDouble(mapper);
    }

    @Override
    public <R> JoinStream<Person, R> join(QuerySpec<Person, Collection<R>> relationship) {
        return repository.join(relationship);
    }

    @Override
    public <R> JoinStream<Person, R> leftJoin(QuerySpec<Person, Collection<R>> relationship) {
        return repository.leftJoin(relationship);
    }
}
