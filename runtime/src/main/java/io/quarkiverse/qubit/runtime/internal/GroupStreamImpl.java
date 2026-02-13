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

    /**
     * The entity class being queried.
     */
    private final Class<T> entityClass;

    /**
     * The grouping key extractor.
     */
    private final QuerySpec<T, K> keyExtractor;

    /**
     * Accumulated WHERE predicates applied before grouping (combined with AND).
     */
    private final List<QuerySpec<T, Boolean>> predicates;

    /**
     * Accumulated HAVING conditions (combined with AND).
     */
    private final List<GroupQuerySpec<T, K, Boolean>> havingConditions;

    /**
     * Projection selector (null if no projection).
     */
    private final GroupQuerySpec<T, K, ?> selector;

    /**
     * Sort orders for groups.
     */
    private final List<GroupSortOrder<T, K>> sortOrders;

    /**
     * OFFSET value (null if not set).
     */
    private final Integer offset;

    /**
     * LIMIT value (null if not set).
     */
    private final Integer limit;

    /**
     * Creates a new group stream for the given entity class and key extractor.
     */
    public GroupStreamImpl(Class<T> entityClass, QuerySpec<T, K> keyExtractor) {
        this(entityClass, keyExtractor, new ArrayList<>(), new ArrayList<>(), null, new ArrayList<>(), null, null);
    }

    /**
     * Creates a new group stream with pre-filtered entities.
     */
    public GroupStreamImpl(Class<T> entityClass, QuerySpec<T, K> keyExtractor, List<QuerySpec<T, Boolean>> predicates) {
        this(entityClass, keyExtractor, predicates, new ArrayList<>(), null, new ArrayList<>(), null, null);
    }

    /**
     * Internal constructor for creating derived streams.
     * <p>
     * <b>Issue #19 Fix (Thread Safety):</b> Defensive copies are made of mutable
     * List parameters to prevent unsafe publication. While the current derivation
     * methods always create new lists, this defensive copying ensures the class
     * remains safe even if future code changes introduce shared references.
     */
    private GroupStreamImpl(
            Class<T> entityClass,
            QuerySpec<T, K> keyExtractor,
            List<QuerySpec<T, Boolean>> predicates,
            List<GroupQuerySpec<T, K, Boolean>> havingConditions,
            GroupQuerySpec<T, K, ?> selector,
            List<GroupSortOrder<T, K>> sortOrders,
            Integer offset,
            Integer limit) {
        this.entityClass = entityClass;
        this.keyExtractor = keyExtractor;
        this.predicates = List.copyOf(predicates);
        this.havingConditions = List.copyOf(havingConditions);
        this.selector = selector;
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
        // Create a new stream that represents the projected result
        // The actual type inference and query generation is done at build time
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
        return new GroupStreamImpl<>(entityClass, keyExtractor, predicates, havingConditions, selector, sortOrders, offset,
                limit);
    }

    private GroupStreamImpl<T, K> withSortOrders(List<GroupSortOrder<T, K>> sortOrders) {
        return new GroupStreamImpl<>(entityClass, keyExtractor, predicates, havingConditions, selector, sortOrders, offset,
                limit);
    }

    private GroupStreamImpl<T, K> withOffset(Integer offset) {
        return new GroupStreamImpl<>(entityClass, keyExtractor, predicates, havingConditions, selector, sortOrders, offset,
                limit);
    }

    private GroupStreamImpl<T, K> withLimit(Integer limit) {
        return new GroupStreamImpl<>(entityClass, keyExtractor, predicates, havingConditions, selector, sortOrders, offset,
                limit);
    }

    /**
     * Returns the primary lambda for call site ID uniqueness.
     * <p>
     * Priority order (matching build-time InvokeDynamicScanner.getPrimaryLambdaMethodName):
     * <ol>
     * <li>First predicate (WHERE clause before grouping)</li>
     * <li>groupBy key extractor</li>
     * <li>First having condition</li>
     * <li>Selector (select projection)</li>
     * </ol>
     *
     * @return the primary lambda, or null if no lambdas are present
     */
    private Object getPrimaryLambda() {
        // First predicate takes priority
        if (!predicates.isEmpty()) {
            return predicates.getFirst();
        }
        // groupBy key extractor (always present for group queries)
        if (keyExtractor != null) {
            return keyExtractor;
        }
        // Having condition
        if (!havingConditions.isEmpty()) {
            return havingConditions.getFirst();
        }
        // Selector for projection
        if (selector != null) {
            return selector;
        }
        return null;
    }

    private Object[] extractCapturedVariables(String callSiteId) {
        int capturedCount = QueryExecutorRegistry.getCapturedVariableCount(callSiteId);

        if (capturedCount == 0) {
            return new Object[0];
        }

        List<Object> allCapturedValues = new ArrayList<>();
        int remainingCount = capturedCount;

        // Extract from predicates (WHERE clauses before grouping)
        remainingCount = extractFromLambdas(predicates, "predicate", callSiteId,
                allCapturedValues, remainingCount);

        // Extract from keyExtractor (groupBy key)
        if (remainingCount > 0 && keyExtractor != null) {
            remainingCount = extractFromSingleLambda(keyExtractor, "keyExtractor", callSiteId,
                    allCapturedValues, remainingCount);
        }

        // Extract from havingConditions (HAVING clauses)
        remainingCount = extractFromLambdas(havingConditions, "havingCondition", callSiteId,
                allCapturedValues, remainingCount);

        // Extract from selector (select projection)
        if (remainingCount > 0 && selector != null) {
            extractFromSingleLambda(selector, "selector", callSiteId,
                    allCapturedValues, remainingCount);
        }

        return allCapturedValues.toArray(new Object[0]);
    }

    /**
     * Represents a sort order specification for groups.
     */
    private record GroupSortOrder<T, K>(GroupQuerySpec<T, K, ?> keyExtractor, SortDirection direction) {
    }
}
