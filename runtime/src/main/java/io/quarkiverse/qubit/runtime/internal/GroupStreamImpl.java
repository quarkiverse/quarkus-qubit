package io.quarkiverse.qubit.runtime.internal;

import static io.quarkiverse.qubit.runtime.internal.LambdaReflectionUtils.extractFromLambdas;
import static io.quarkiverse.qubit.runtime.internal.LambdaReflectionUtils.extractFromSingleLambda;
import static io.quarkiverse.qubit.runtime.internal.LambdaReflectionUtils.getCallSiteId;
import static io.quarkiverse.qubit.runtime.internal.LambdaReflectionUtils.getQueryExecutorRegistry;
import static io.quarkiverse.qubit.runtime.internal.LambdaReflectionUtils.requireNonNullLambda;
import static io.quarkiverse.qubit.runtime.internal.LambdaReflectionUtils.validateLimitCount;
import static io.quarkiverse.qubit.runtime.internal.LambdaReflectionUtils.validateSkipCount;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import io.quarkiverse.qubit.GroupQuerySpec;
import io.quarkiverse.qubit.GroupStream;
import io.quarkiverse.qubit.QubitStream;
import io.quarkiverse.qubit.QuerySpec;
import io.quarkiverse.qubit.SortDirection;

/**
 * Default implementation of {@link GroupStream} using JPA Criteria Queries.
 * <p>
 * This class implements an immutable stream pattern where each operation
 * returns a new instance with the accumulated operation state. Terminal
 * operations execute the accumulated pipeline as a JPA Criteria Query.
 *
 * @param <T> the entity type being grouped
 * @param <K> the type of the grouping key
 */
public class GroupStreamImpl<T, K> implements GroupStream<T, K> {

    private final Class<T> entityClass;
    private final QuerySpec<T, K> keyExtractor;
    private final List<QuerySpec<T, Boolean>> predicates;
    private final List<GroupQuerySpec<T, K, Boolean>> havingConditions;
    private final List<GroupSortOrder<T, K>> sortOrders;
    private final @Nullable Integer offset;
    private final @Nullable Integer limit;

    /**
     * Creates a new group stream with pre-filtered entities.
     */
    public GroupStreamImpl(Class<T> entityClass, QuerySpec<T, K> keyExtractor, List<QuerySpec<T, Boolean>> predicates) {
        this(entityClass, keyExtractor, predicates, List.of(), List.of(), null, null);
    }

    /**
     * Internal constructor for creating derived streams.
     * <p>
     * <b>Issue #19 Fix (Thread Safety):</b> Defensive copies are made of mutable
     * List parameters to prevent unsafe publication.
     */
    private GroupStreamImpl(
            Class<T> entityClass,
            QuerySpec<T, K> keyExtractor,
            List<QuerySpec<T, Boolean>> predicates,
            List<GroupQuerySpec<T, K, Boolean>> havingConditions,
            List<GroupSortOrder<T, K>> sortOrders,
            @Nullable Integer offset,
            @Nullable Integer limit) {
        this.entityClass = entityClass;
        this.keyExtractor = keyExtractor;
        this.predicates = List.copyOf(predicates);
        this.havingConditions = List.copyOf(havingConditions);
        this.sortOrders = List.copyOf(sortOrders);
        this.offset = offset;
        this.limit = limit;
    }

    @Override
    public GroupStream<T, K> having(GroupQuerySpec<T, K, Boolean> condition) {
        requireNonNullLambda(condition, "Condition", "having");
        List<GroupQuerySpec<T, K, Boolean>> newConditions = new ArrayList<>(this.havingConditions);
        newConditions.add(condition);
        return withHavingConditions(newConditions);
    }

    @Override
    public <R> QubitStream<R> select(GroupQuerySpec<T, K, R> mapper) {
        requireNonNullLambda(mapper, "Mapper", "select");
        String callSiteId = getCallSiteId(Set.of(), getPrimaryLambda());
        Object[] capturedValues = extractCapturedVariables(callSiteId);

        QueryExecutorRegistry registry = getQueryExecutorRegistry();
        List<R> results = registry.executeGroupQuery(callSiteId, entityClass, capturedValues, offset, limit);
        return new ImmutableResultStream<>(results, "group projection");
    }

    @Override
    public QubitStream<K> selectKey() {
        String callSiteId = getCallSiteId(Set.of(), getPrimaryLambda());
        Object[] capturedValues = extractCapturedVariables(callSiteId);

        QueryExecutorRegistry registry = getQueryExecutorRegistry();
        List<K> results = registry.executeGroupKeyQuery(callSiteId, entityClass, capturedValues, offset, limit);
        return new ImmutableResultStream<>(results, "group projection");
    }

    @Override
    public <C extends Comparable<C>> GroupStream<T, K> sortedBy(GroupQuerySpec<T, K, C> keyExtractor) {
        requireNonNullLambda(keyExtractor, "Key extractor", "sortedBy");
        List<GroupSortOrder<T, K>> newSortOrders = new ArrayList<>(this.sortOrders);
        newSortOrders.add(0, new GroupSortOrder<>(keyExtractor, SortDirection.ASCENDING));
        return withSortOrders(newSortOrders);
    }

    @Override
    public <C extends Comparable<C>> GroupStream<T, K> sortedDescendingBy(GroupQuerySpec<T, K, C> keyExtractor) {
        requireNonNullLambda(keyExtractor, "Key extractor", "sortedDescendingBy");
        List<GroupSortOrder<T, K>> newSortOrders = new ArrayList<>(this.sortOrders);
        newSortOrders.add(0, new GroupSortOrder<>(keyExtractor, SortDirection.DESCENDING));
        return withSortOrders(newSortOrders);
    }

    @Override
    public <C extends Comparable<C>> GroupStream<T, K> thenSortedBy(GroupQuerySpec<T, K, C> keyExtractor) {
        requireNonNullLambda(keyExtractor, "Key extractor", "thenSortedBy");
        List<GroupSortOrder<T, K>> newSortOrders = new ArrayList<>(this.sortOrders);
        newSortOrders.add(new GroupSortOrder<>(keyExtractor, SortDirection.ASCENDING));
        return withSortOrders(newSortOrders);
    }

    @Override
    public <C extends Comparable<C>> GroupStream<T, K> thenSortedDescendingBy(GroupQuerySpec<T, K, C> keyExtractor) {
        requireNonNullLambda(keyExtractor, "Key extractor", "thenSortedDescendingBy");
        List<GroupSortOrder<T, K>> newSortOrders = new ArrayList<>(this.sortOrders);
        newSortOrders.add(new GroupSortOrder<>(keyExtractor, SortDirection.DESCENDING));
        return withSortOrders(newSortOrders);
    }

    @Override
    public GroupStream<T, K> skip(int n) {
        return withOffset(validateSkipCount(n));
    }

    @Override
    public GroupStream<T, K> limit(int n) {
        return withLimit(validateLimitCount(n));
    }

    @Override
    public List<K> toList() {
        String callSiteId = getCallSiteId(Set.of(), getPrimaryLambda());
        Object[] capturedValues = extractCapturedVariables(callSiteId);

        QueryExecutorRegistry registry = getQueryExecutorRegistry();
        return registry.executeGroupKeyQuery(callSiteId, entityClass, capturedValues, offset, limit);
    }

    @Override
    public long count() {
        String callSiteId = getCallSiteId(Set.of(), getPrimaryLambda());
        Object[] capturedValues = extractCapturedVariables(callSiteId);

        QueryExecutorRegistry registry = getQueryExecutorRegistry();
        return registry.executeGroupCountQuery(callSiteId, entityClass, capturedValues);
    }

    private GroupStreamImpl<T, K> withHavingConditions(List<GroupQuerySpec<T, K, Boolean>> havingConditions) {
        return new GroupStreamImpl<>(entityClass, keyExtractor, predicates, havingConditions, sortOrders, offset, limit);
    }

    private GroupStreamImpl<T, K> withSortOrders(List<GroupSortOrder<T, K>> sortOrders) {
        return new GroupStreamImpl<>(entityClass, keyExtractor, predicates, havingConditions, sortOrders, offset, limit);
    }

    private GroupStreamImpl<T, K> withOffset(Integer offset) {
        return new GroupStreamImpl<>(entityClass, keyExtractor, predicates, havingConditions, sortOrders, offset, limit);
    }

    private GroupStreamImpl<T, K> withLimit(Integer limit) {
        return new GroupStreamImpl<>(entityClass, keyExtractor, predicates, havingConditions, sortOrders, offset, limit);
    }

    private Object getPrimaryLambda() {
        if (!predicates.isEmpty()) {
            return predicates.getFirst();
        }
        return keyExtractor;
    }

    private Object[] extractCapturedVariables(String callSiteId) {
        int capturedCount = QueryExecutorRegistry.getCapturedVariableCount(callSiteId);

        if (capturedCount == 0) {
            return LambdaReflectionUtils.EMPTY_OBJECT_ARRAY;
        }

        List<Object> allCapturedValues = new ArrayList<>();

        extractFromLambdas(predicates, allCapturedValues);
        extractFromSingleLambda(keyExtractor, allCapturedValues);
        extractFromLambdas(havingConditions, allCapturedValues);

        for (GroupSortOrder<T, K> sortOrder : sortOrders) {
            extractFromSingleLambda(sortOrder.keyExtractor(), allCapturedValues);
        }

        if (allCapturedValues.size() != capturedCount) {
            throw new IllegalStateException(
                    String.format("Captured variable count mismatch at %s: expected %d, found %d",
                            callSiteId, capturedCount, allCapturedValues.size()));
        }

        return allCapturedValues.toArray(LambdaReflectionUtils.EMPTY_OBJECT_ARRAY);
    }

    private record GroupSortOrder<T, K>(GroupQuerySpec<T, K, ?> keyExtractor, SortDirection direction) {
    }
}
