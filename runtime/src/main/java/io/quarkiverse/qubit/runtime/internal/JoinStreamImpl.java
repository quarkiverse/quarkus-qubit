package io.quarkiverse.qubit.runtime.internal;

import static io.quarkiverse.qubit.runtime.internal.LambdaReflectionUtils.extractFromLambdas;
import static io.quarkiverse.qubit.runtime.internal.LambdaReflectionUtils.extractFromSingleLambda;
import static io.quarkiverse.qubit.runtime.internal.LambdaReflectionUtils.getCallSiteId;
import static io.quarkiverse.qubit.runtime.internal.LambdaReflectionUtils.getQueryExecutorRegistry;
import static io.quarkiverse.qubit.runtime.internal.LambdaReflectionUtils.requireNonNullLambda;
import static io.quarkiverse.qubit.runtime.internal.LambdaReflectionUtils.requireSingleResult;
import static io.quarkiverse.qubit.runtime.internal.LambdaReflectionUtils.validateLimitCount;
import static io.quarkiverse.qubit.runtime.internal.LambdaReflectionUtils.validateSkipCount;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import io.quarkiverse.qubit.BiQuerySpec;
import io.quarkiverse.qubit.JoinStream;
import io.quarkiverse.qubit.JoinType;
import io.quarkiverse.qubit.QubitStream;
import io.quarkiverse.qubit.QuerySpec;
import io.quarkiverse.qubit.SortDirection;

/**
 * Default implementation of {@link JoinStream} for join query operations.
 * <p>
 * This class implements an immutable stream pattern where each operation
 * returns a new instance with the accumulated operation state. Terminal
 * operations execute the accumulated pipeline as a JPA Criteria Query.
 *
 * @param <T> the source entity type (left side of join)
 * @param <R> the joined entity type (right side of join)
 */
public class JoinStreamImpl<T, R> implements JoinStream<T, R> {

    private final Class<T> sourceEntityClass;
    private final QuerySpec<T, Collection<R>> relationshipAccessor;
    private final JoinType joinType;
    private final List<BiQuerySpec<T, R, Boolean>> onConditions;
    private final List<BiQuerySpec<T, R, Boolean>> biPredicates;
    private final List<QuerySpec<T, Boolean>> sourcePredicates;
    private final List<BiSortOrder<T, R>> sortOrders;
    private final @Nullable Integer offset;
    private final @Nullable Integer limit;
    private final boolean distinct;

    /**
     * Creates a new JoinStream for the given source entity and relationship.
     */
    public JoinStreamImpl(
            Class<T> sourceEntityClass,
            QuerySpec<T, Collection<R>> relationshipAccessor,
            JoinType joinType) {
        this(sourceEntityClass, relationshipAccessor, joinType,
                new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>(),
                null, null, false);
    }

    /**
     * Internal constructor for creating derived streams.
     * <p>
     * <b>Issue #19 Fix (Thread Safety):</b> Defensive copies are made of mutable
     * List parameters to prevent unsafe publication.
     */
    private JoinStreamImpl(
            Class<T> sourceEntityClass,
            QuerySpec<T, Collection<R>> relationshipAccessor,
            JoinType joinType,
            List<BiQuerySpec<T, R, Boolean>> onConditions,
            List<BiQuerySpec<T, R, Boolean>> biPredicates,
            List<QuerySpec<T, Boolean>> sourcePredicates,
            List<BiSortOrder<T, R>> sortOrders,
            @Nullable Integer offset,
            @Nullable Integer limit,
            boolean distinct) {
        this.sourceEntityClass = sourceEntityClass;
        this.relationshipAccessor = relationshipAccessor;
        this.joinType = joinType;
        this.onConditions = List.copyOf(onConditions);
        this.biPredicates = List.copyOf(biPredicates);
        this.sourcePredicates = List.copyOf(sourcePredicates);
        this.sortOrders = List.copyOf(sortOrders);
        this.offset = offset;
        this.limit = limit;
        this.distinct = distinct;
    }

    @Override
    public JoinStream<T, R> on(BiQuerySpec<T, R, Boolean> condition) {
        requireNonNullLambda(condition, "Condition", "on");
        List<BiQuerySpec<T, R, Boolean>> newOnConditions = new ArrayList<>(this.onConditions);
        newOnConditions.add(condition);
        return new JoinStreamImpl<>(sourceEntityClass, relationshipAccessor, joinType,
                newOnConditions, biPredicates, sourcePredicates, sortOrders, offset, limit, distinct);
    }

    @Override
    public JoinStream<T, R> where(BiQuerySpec<T, R, Boolean> predicate) {
        requireNonNullLambda(predicate, "Predicate", "where");
        List<BiQuerySpec<T, R, Boolean>> newBiPredicates = new ArrayList<>(this.biPredicates);
        newBiPredicates.add(predicate);
        return new JoinStreamImpl<>(sourceEntityClass, relationshipAccessor, joinType,
                onConditions, newBiPredicates, sourcePredicates, sortOrders, offset, limit, distinct);
    }

    @Override
    public JoinStream<T, R> where(QuerySpec<T, Boolean> predicate) {
        requireNonNullLambda(predicate, "Predicate", "where");
        List<QuerySpec<T, Boolean>> newSourcePredicates = new ArrayList<>(this.sourcePredicates);
        newSourcePredicates.add(predicate);
        return new JoinStreamImpl<>(sourceEntityClass, relationshipAccessor, joinType,
                onConditions, biPredicates, newSourcePredicates, sortOrders, offset, limit, distinct);
    }

    @Override
    public <S> QubitStream<S> select(BiQuerySpec<T, R, S> mapper) {
        requireNonNullLambda(mapper, "Mapper", "select");
        String callSiteId = getCallSiteId(QubitConstants.JOIN_METHODS, getPrimaryLambda());
        Object[] capturedValues = extractCapturedVariables();

        QueryExecutorRegistry registry = getQueryExecutorRegistry();
        List<S> results = registry.executeJoinProjectionQuery(callSiteId, sourceEntityClass, capturedValues, offset, limit,
                distinct);
        return new ImmutableResultStream<>(results, "join projection");
    }

    @Override
    public QubitStream<T> selectSource() {
        List<T> results = toList();
        return new ImmutableResultStream<>(results, "selectSource projection");
    }

    @Override
    public QubitStream<R> selectJoined() {
        String callSiteId = getCallSiteId(QubitConstants.JOIN_METHODS, getPrimaryLambda());
        Object[] capturedValues = extractCapturedVariables();

        QueryExecutorRegistry registry = getQueryExecutorRegistry();
        List<R> results = registry.executeJoinSelectJoinedQuery(callSiteId, sourceEntityClass, capturedValues, offset, limit,
                distinct);
        return new ImmutableResultStream<>(results, "selectJoined projection");
    }

    @Override
    public <K extends Comparable<K>> JoinStream<T, R> sortedBy(BiQuerySpec<T, R, K> keyExtractor) {
        requireNonNullLambda(keyExtractor, "Key extractor", "sortedBy");
        List<BiSortOrder<T, R>> newSortOrders = new ArrayList<>(this.sortOrders);
        newSortOrders.add(0, new BiSortOrder<>(keyExtractor, SortDirection.ASCENDING));
        return new JoinStreamImpl<>(sourceEntityClass, relationshipAccessor, joinType,
                onConditions, biPredicates, sourcePredicates, newSortOrders, offset, limit, distinct);
    }

    @Override
    public <K extends Comparable<K>> JoinStream<T, R> sortedDescendingBy(BiQuerySpec<T, R, K> keyExtractor) {
        requireNonNullLambda(keyExtractor, "Key extractor", "sortedDescendingBy");
        List<BiSortOrder<T, R>> newSortOrders = new ArrayList<>(this.sortOrders);
        newSortOrders.add(0, new BiSortOrder<>(keyExtractor, SortDirection.DESCENDING));
        return new JoinStreamImpl<>(sourceEntityClass, relationshipAccessor, joinType,
                onConditions, biPredicates, sourcePredicates, newSortOrders, offset, limit, distinct);
    }

    @Override
    public <K extends Comparable<K>> JoinStream<T, R> thenSortedBy(BiQuerySpec<T, R, K> keyExtractor) {
        requireNonNullLambda(keyExtractor, "Key extractor", "thenSortedBy");
        List<BiSortOrder<T, R>> newSortOrders = new ArrayList<>(this.sortOrders);
        newSortOrders.add(new BiSortOrder<>(keyExtractor, SortDirection.ASCENDING));
        return new JoinStreamImpl<>(sourceEntityClass, relationshipAccessor, joinType,
                onConditions, biPredicates, sourcePredicates, newSortOrders, offset, limit, distinct);
    }

    @Override
    public <K extends Comparable<K>> JoinStream<T, R> thenSortedDescendingBy(BiQuerySpec<T, R, K> keyExtractor) {
        requireNonNullLambda(keyExtractor, "Key extractor", "thenSortedDescendingBy");
        List<BiSortOrder<T, R>> newSortOrders = new ArrayList<>(this.sortOrders);
        newSortOrders.add(new BiSortOrder<>(keyExtractor, SortDirection.DESCENDING));
        return new JoinStreamImpl<>(sourceEntityClass, relationshipAccessor, joinType,
                onConditions, biPredicates, sourcePredicates, newSortOrders, offset, limit, distinct);
    }

    @Override
    public JoinStream<T, R> skip(int n) {
        return new JoinStreamImpl<>(sourceEntityClass, relationshipAccessor, joinType,
                onConditions, biPredicates, sourcePredicates, sortOrders, validateSkipCount(n), limit, distinct);
    }

    @Override
    public JoinStream<T, R> limit(int n) {
        return new JoinStreamImpl<>(sourceEntityClass, relationshipAccessor, joinType,
                onConditions, biPredicates, sourcePredicates, sortOrders, offset, validateLimitCount(n), distinct);
    }

    @Override
    public JoinStream<T, R> distinct() {
        return new JoinStreamImpl<>(sourceEntityClass, relationshipAccessor, joinType,
                onConditions, biPredicates, sourcePredicates, sortOrders, offset, limit, true);
    }

    @Override
    public List<T> toList() {
        String callSiteId = getCallSiteId(QubitConstants.JOIN_METHODS, getPrimaryLambda());
        Object[] capturedValues = extractCapturedVariables();

        QueryExecutorRegistry registry = getQueryExecutorRegistry();
        return registry.executeJoinListQuery(callSiteId, sourceEntityClass, capturedValues, offset, limit, distinct);
    }

    @Override
    public T getSingleResult() {
        return requireSingleResult(toList());
    }

    @Override
    public Optional<T> findFirst() {
        JoinStream<T, R> stream = (this.limit == null || this.limit > 1) ? this.limit(1) : this;
        List<T> results = stream.toList();
        return results.isEmpty() ? Optional.empty() : Optional.of(results.getFirst());
    }

    @Override
    public long count() {
        String callSiteId = getCallSiteId(QubitConstants.JOIN_METHODS, getPrimaryLambda());
        Object[] capturedValues = extractCapturedVariables();

        QueryExecutorRegistry registry = getQueryExecutorRegistry();
        return registry.executeJoinCountQuery(callSiteId, sourceEntityClass, capturedValues);
    }

    @Override
    public boolean exists() {
        return findFirst().isPresent();
    }

    private Object getPrimaryLambda() {
        if (!sourcePredicates.isEmpty()) {
            return sourcePredicates.getFirst();
        }
        if (!biPredicates.isEmpty()) {
            return biPredicates.getFirst();
        }
        return relationshipAccessor;
    }

    private Object[] extractCapturedVariables() {
        String callSiteId = getCallSiteId(QubitConstants.JOIN_METHODS, getPrimaryLambda());
        int capturedCount = QueryExecutorRegistry.getCapturedVariableCount(callSiteId);

        if (capturedCount == 0) {
            return new Object[0];
        }

        List<Object> allCapturedValues = new ArrayList<>();
        extractFromLambdas(biPredicates, allCapturedValues);
        extractFromLambdas(sourcePredicates, allCapturedValues);
        extractFromLambdas(onConditions, allCapturedValues);
        for (BiSortOrder<T, R> sortOrder : sortOrders) {
            extractFromSingleLambda(sortOrder.keyExtractor(), allCapturedValues);
        }

        if (allCapturedValues.size() != capturedCount) {
            throw new IllegalStateException(
                    String.format("Captured variable count mismatch at %s: expected %d, found %d",
                            callSiteId, capturedCount, allCapturedValues.size()));
        }

        return allCapturedValues.toArray(new Object[0]);
    }

    private record BiSortOrder<T, R>(BiQuerySpec<T, R, ?> keyExtractor, SortDirection direction) {
    }

}
