package io.quarkiverse.qubit.it.testutil;

import io.quarkiverse.qubit.it.Person;
import io.quarkiverse.qubit.JoinStream;
import io.quarkiverse.qubit.QuerySpec;
import io.quarkiverse.qubit.QubitStream;

import java.util.Collection;

/**
 * Implementation of {@link PersonQueryOperations} using static entity methods.
 *
 * <p>Delegates all operations to {@code Person.where()}, {@code Person.select()}, etc.
 */
public class StaticPersonQueryOperations implements PersonQueryOperations {

    public static final StaticPersonQueryOperations INSTANCE = new StaticPersonQueryOperations();

    private StaticPersonQueryOperations() {
    }

    @Override
    public String getSourceName() {
        return "Static";
    }

    @Override
    public QubitStream<Person> where(QuerySpec<Person, Boolean> spec) {
        return Person.where(spec);
    }

    @Override
    public <R> QubitStream<R> select(QuerySpec<Person, R> mapper) {
        return Person.select(mapper);
    }

    @Override
    public <K extends Comparable<K>> QubitStream<Person> sortedBy(QuerySpec<Person, K> keyExtractor) {
        return Person.sortedBy(keyExtractor);
    }

    @Override
    public <K extends Comparable<K>> QubitStream<Person> sortedDescendingBy(QuerySpec<Person, K> keyExtractor) {
        return Person.sortedDescendingBy(keyExtractor);
    }

    @Override
    public <K extends Comparable<K>> QubitStream<K> min(QuerySpec<Person, K> mapper) {
        return Person.min(mapper);
    }

    @Override
    public <K extends Comparable<K>> QubitStream<K> max(QuerySpec<Person, K> mapper) {
        return Person.max(mapper);
    }

    @Override
    public QubitStream<Double> avg(QuerySpec<Person, ? extends Number> mapper) {
        return Person.avg(mapper);
    }

    @Override
    public QubitStream<Long> sumInteger(QuerySpec<Person, Integer> mapper) {
        return Person.sumInteger(mapper);
    }

    @Override
    public QubitStream<Long> sumLong(QuerySpec<Person, Long> mapper) {
        return Person.sumLong(mapper);
    }

    @Override
    public QubitStream<Double> sumDouble(QuerySpec<Person, Double> mapper) {
        return Person.sumDouble(mapper);
    }

    @Override
    public <R> JoinStream<Person, R> join(QuerySpec<Person, Collection<R>> relationship) {
        return Person.join(relationship);
    }

    @Override
    public <R> JoinStream<Person, R> leftJoin(QuerySpec<Person, Collection<R>> relationship) {
        return Person.leftJoin(relationship);
    }
}
