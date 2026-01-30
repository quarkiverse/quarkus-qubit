package io.quarkiverse.qubit.runtime.internal;

import static io.quarkiverse.qubit.runtime.internal.LambdaReflectionUtils.requireSingleResult;
import static io.quarkiverse.qubit.runtime.internal.LambdaReflectionUtils.validateLimitCount;
import static io.quarkiverse.qubit.runtime.internal.LambdaReflectionUtils.validateSkipCount;

import io.quarkiverse.qubit.GroupStream;
import io.quarkiverse.qubit.JoinStream;
import io.quarkiverse.qubit.QuerySpec;
import io.quarkiverse.qubit.QubitStream;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Immutable QubitStream wrapping pre-computed projection results.
 * Consolidates ListQubitStream, ListProjectionQubitStream, ListJoinedQubitStream.
 */
public final class ImmutableResultStream<T> implements QubitStream<T> {

    private static final String OP_AGGREGATE = "aggregate";

    private final List<T> results;
    private final String operationContext;

    /** Wraps results with defensive copy (Issue #23: true immutability). */
    public ImmutableResultStream(List<T> results, String operationContext) {
        this.results = results != null ? List.copyOf(results) : List.of();
        this.operationContext = operationContext;
    }

    /** Wraps results with default "projection" context. */
    public ImmutableResultStream(List<T> results) {
        this(results, "projection");
    }

    // ========== Unsupported Intermediate Operations ==========

    @Override
    public QubitStream<T> where(QuerySpec<T, Boolean> predicate) {
        throw unsupported("filter");
    }

    @Override
    public <R> QubitStream<R> select(QuerySpec<T, R> mapper) {
        throw unsupported("project");
    }

    @Override
    public <K extends Comparable<K>> QubitStream<T> sortedBy(QuerySpec<T, K> keyExtractor) {
        throw unsupported("sort");
    }

    @Override
    public <K extends Comparable<K>> QubitStream<T> sortedDescendingBy(QuerySpec<T, K> keyExtractor) {
        throw unsupported("sort");
    }

    @Override
    public <K extends Comparable<K>> QubitStream<K> min(QuerySpec<T, K> mapper) {
        throw unsupported(OP_AGGREGATE);
    }

    @Override
    public <K extends Comparable<K>> QubitStream<K> max(QuerySpec<T, K> mapper) {
        throw unsupported(OP_AGGREGATE);
    }

    @Override
    public QubitStream<Long> sumInteger(QuerySpec<T, Integer> mapper) {
        throw unsupported(OP_AGGREGATE);
    }

    @Override
    public QubitStream<Long> sumLong(QuerySpec<T, Long> mapper) {
        throw unsupported(OP_AGGREGATE);
    }

    @Override
    public QubitStream<Double> sumDouble(QuerySpec<T, Double> mapper) {
        throw unsupported(OP_AGGREGATE);
    }

    @Override
    public QubitStream<Double> avg(QuerySpec<T, ? extends Number> mapper) {
        throw unsupported(OP_AGGREGATE);
    }

    @Override
    public <R> JoinStream<T, R> join(QuerySpec<T, Collection<R>> relationship) {
        throw unsupported("join");
    }

    @Override
    public <R> JoinStream<T, R> leftJoin(QuerySpec<T, Collection<R>> relationship) {
        throw unsupported("join");
    }

    @Override
    public <K> GroupStream<T, K> groupBy(QuerySpec<T, K> keyExtractor) {
        throw unsupported("group");
    }

    // ========== Supported Operations ==========

    @Override
    public QubitStream<T> skip(int n) {
        // Create independent copy to break reference chain to original list (enables GC)
        // List.subList() returns a view backed by the original, causing memory leaks
        int startIndex = Math.min(validateSkipCount(n), results.size());
        return new ImmutableResultStream<>(
                new ArrayList<>(results.subList(startIndex, results.size())),
                operationContext);
    }

    @Override
    public QubitStream<T> limit(int n) {
        // Create independent copy to break reference chain to original list (enables GC)
        // List.subList() returns a view backed by the original, causing memory leaks
        int endIndex = Math.min(validateLimitCount(n), results.size());
        return new ImmutableResultStream<>(
                new ArrayList<>(results.subList(0, endIndex)),
                operationContext);
    }

    @Override
    public QubitStream<T> distinct() {
        return new ImmutableResultStream<>(
                results.stream().distinct().toList(),
                operationContext);
    }

    // ========== Terminal Operations ==========

    @Override
    public long count() {
        return results.size();
    }

    @Override
    public List<T> toList() {
        return new ArrayList<>(results);
    }

    @Override
    public T getSingleResult() {
        return requireSingleResult(results);
    }

    @Override
    public Optional<T> findFirst() {
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    @Override
    public boolean exists() {
        return !results.isEmpty();
    }

    // ========== Helper Methods ==========

    private UnsupportedOperationException unsupported(String operation) {
        return new UnsupportedOperationException(
                "Cannot " + operation + " after " + operationContext);
    }
}
