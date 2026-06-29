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
import java.util.Set;

import org.jspecify.annotations.Nullable;

import io.quarkiverse.qubit.GroupStream;
import io.quarkiverse.qubit.JoinStream;
import io.quarkiverse.qubit.JoinType;
import io.quarkiverse.qubit.QubitStream;
import io.quarkiverse.qubit.QuerySpec;
import io.quarkiverse.qubit.ScalarResult;
import io.quarkiverse.qubit.SortDirection;
import jakarta.persistence.criteria.Nulls;

/**
 * Default implementation of {@link QubitStream} using JPA Criteria Queries.
 * <p>
 * This class implements an immutable stream pattern where each operation
 * returns a new instance with the accumulated operation state. Terminal
 * operations execute the accumulated pipeline as a JPA Criteria Query.
 *
 * @param <T> the type of elements in this stream
 */
public class QubitStreamImpl<T> implements QubitStream<T> {

    private static final String PARAM_MAPPER = "Mapper";
    private static final String PARAM_KEY_EXTRACTOR = "Key extractor";

    private final Class<T> entityClass;
    private final List<QuerySpec<T, Boolean>> predicates;
    private final @Nullable QuerySpec<T, ?> selector;
    private final List<SortOrder<T>> sortOrders;
    private final @Nullable Integer offset;
    private final @Nullable Integer limit;
    private final boolean distinct;
    private final @Nullable AggregationType aggregationType;
    private final @Nullable QuerySpec<T, ?> aggregationMapper;

    /**
     * Creates a new stream for the given entity class with no operations.
     */
    public QubitStreamImpl(Class<T> entityClass) {
        this(entityClass, new ArrayList<>(), null, new ArrayList<>(), null, null, false, null, null);
    }

    /**
     * Internal constructor for creating derived streams.
     * <p>
     * <b>Issue #19 Fix (Thread Safety):</b> Defensive copies are made of mutable
     * List parameters to prevent unsafe publication.
     */
    private QubitStreamImpl(
            Class<T> entityClass,
            List<QuerySpec<T, Boolean>> predicates,
            @Nullable QuerySpec<T, ?> selector,
            List<SortOrder<T>> sortOrders,
            @Nullable Integer offset,
            @Nullable Integer limit,
            boolean distinct,
            @Nullable AggregationType aggregationType,
            @Nullable QuerySpec<T, ?> aggregationMapper) {
        this.entityClass = entityClass;
        this.predicates = List.copyOf(predicates);
        this.selector = selector;
        this.sortOrders = List.copyOf(sortOrders);
        this.offset = offset;
        this.limit = limit;
        this.distinct = distinct;
        this.aggregationType = aggregationType;
        this.aggregationMapper = aggregationMapper;
    }

    @Override
    public QubitStream<T> where(QuerySpec<T, Boolean> predicate) {
        requireNonNullLambda(predicate, "Predicate", "where");
        List<QuerySpec<T, Boolean>> newPredicates = new ArrayList<>(this.predicates);
        newPredicates.add(predicate);
        return withPredicates(newPredicates);
    }

    @Override
    public <R> QubitStream<R> select(QuerySpec<T, R> mapper) {
        requireNonNullLambda(mapper, PARAM_MAPPER, "select");
        return withSelector(mapper);
    }

    @Override
    public <K extends Comparable<K>> QubitStream<T> sortedBy(QuerySpec<T, K> keyExtractor) {
        requireNonNullLambda(keyExtractor, PARAM_KEY_EXTRACTOR, "sortedBy");
        List<SortOrder<T>> newSortOrders = new ArrayList<>(this.sortOrders);
        newSortOrders.add(0, new SortOrder<>(keyExtractor, SortDirection.ASCENDING));
        return withSortOrders(newSortOrders);
    }

    @Override
    public <K extends Comparable<K>> QubitStream<T> sortedDescendingBy(QuerySpec<T, K> keyExtractor) {
        requireNonNullLambda(keyExtractor, PARAM_KEY_EXTRACTOR, "sortedDescendingBy");
        List<SortOrder<T>> newSortOrders = new ArrayList<>(this.sortOrders);
        newSortOrders.add(0, new SortOrder<>(keyExtractor, SortDirection.DESCENDING));
        return withSortOrders(newSortOrders);
    }

    @Override
    public <K extends Comparable<K>> QubitStream<T> thenSortedBy(QuerySpec<T, K> keyExtractor) {
        requireNonNullLambda(keyExtractor, PARAM_KEY_EXTRACTOR, "thenSortedBy");
        List<SortOrder<T>> newSortOrders = new ArrayList<>(this.sortOrders);
        newSortOrders.add(new SortOrder<>(keyExtractor, SortDirection.ASCENDING));
        return withSortOrders(newSortOrders);
    }

    @Override
    public <K extends Comparable<K>> QubitStream<T> thenSortedDescendingBy(QuerySpec<T, K> keyExtractor) {
        requireNonNullLambda(keyExtractor, PARAM_KEY_EXTRACTOR, "thenSortedDescendingBy");
        List<SortOrder<T>> newSortOrders = new ArrayList<>(this.sortOrders);
        newSortOrders.add(new SortOrder<>(keyExtractor, SortDirection.DESCENDING));
        return withSortOrders(newSortOrders);
    }

    @Override
    public <K extends Comparable<K>> QubitStream<T> sortedBy(QuerySpec<T, K> keyExtractor,
            Nulls nullPrecedence) {
        return sortedBy(keyExtractor);
    }

    @Override
    public <K extends Comparable<K>> QubitStream<T> sortedDescendingBy(QuerySpec<T, K> keyExtractor,
            Nulls nullPrecedence) {
        return sortedDescendingBy(keyExtractor);
    }

    @Override
    public <K extends Comparable<K>> QubitStream<T> thenSortedBy(QuerySpec<T, K> keyExtractor,
            Nulls nullPrecedence) {
        return thenSortedBy(keyExtractor);
    }

    @Override
    public <K extends Comparable<K>> QubitStream<T> thenSortedDescendingBy(QuerySpec<T, K> keyExtractor,
            Nulls nullPrecedence) {
        return thenSortedDescendingBy(keyExtractor);
    }

    @Override
    public QubitStream<T> skip(int n) {
        return withOffset(validateSkipCount(n));
    }

    @Override
    public QubitStream<T> limit(int n) {
        return withLimit(validateLimitCount(n));
    }

    @Override
    public QubitStream<T> distinct() {
        return withDistinct(true);
    }

    @Override
    public long count() {
        String callSiteId = getCallSiteId(Set.of(), getPrimaryLambda());
        Object[] capturedValues = extractCapturedVariables(callSiteId);

        QueryExecutorRegistry registry = getQueryExecutorRegistry();
        return registry.executeCountQuery(callSiteId, entityClass, capturedValues);
    }

    @Override
    public <K extends Comparable<K>> ScalarResult<K> min(QuerySpec<T, K> mapper) {
        requireNonNullLambda(mapper, PARAM_MAPPER, "min");
        return new ScalarResultImpl<>(withAggregation(AggregationType.MIN, mapper));
    }

    @Override
    public <K extends Comparable<K>> ScalarResult<K> max(QuerySpec<T, K> mapper) {
        requireNonNullLambda(mapper, PARAM_MAPPER, "max");
        return new ScalarResultImpl<>(withAggregation(AggregationType.MAX, mapper));
    }

    @Override
    public ScalarResult<Long> sumInteger(QuerySpec<T, Integer> mapper) {
        requireNonNullLambda(mapper, PARAM_MAPPER, "sumInteger");
        return new ScalarResultImpl<>(withAggregation(AggregationType.SUM_INTEGER, mapper));
    }

    @Override
    public ScalarResult<Long> sumLong(QuerySpec<T, Long> mapper) {
        requireNonNullLambda(mapper, PARAM_MAPPER, "sumLong");
        return new ScalarResultImpl<>(withAggregation(AggregationType.SUM_LONG, mapper));
    }

    @Override
    public ScalarResult<Double> sumDouble(QuerySpec<T, Double> mapper) {
        requireNonNullLambda(mapper, PARAM_MAPPER, "sumDouble");
        return new ScalarResultImpl<>(withAggregation(AggregationType.SUM_DOUBLE, mapper));
    }

    @Override
    public ScalarResult<Double> avg(QuerySpec<T, ? extends Number> mapper) {
        requireNonNullLambda(mapper, PARAM_MAPPER, "avg");
        return new ScalarResultImpl<>(withAggregation(AggregationType.AVG, mapper));
    }

    @Override
    public List<T> toList() {
        String callSiteId = getCallSiteId(Set.of(), getPrimaryLambda());
        Object[] capturedValues = extractCapturedVariables(callSiteId);

        QueryExecutorRegistry registry = getQueryExecutorRegistry();
        return registry.executeListQuery(callSiteId, entityClass, capturedValues, offset, limit, distinct);
    }

    @Override
    @SuppressWarnings("unchecked")
    public T getSingleResult() {
        if (aggregationType != null) {
            String callSiteId = getCallSiteId(Set.of(), getPrimaryLambda());
            Object[] capturedValues = extractCapturedVariables(callSiteId);

            QueryExecutorRegistry registry = getQueryExecutorRegistry();
            return (T) registry.executeAggregationQuery(callSiteId, entityClass, capturedValues);
        }

        return requireSingleResult(toList());
    }

    @Override
    public Optional<T> findFirst() {
        QubitStream<T> stream = (this.limit == null || this.limit > 1) ? this.limit(1) : this;
        List<T> results = stream.toList();
        return results.isEmpty() ? Optional.empty() : Optional.of(results.getFirst());
    }

    @Override
    public boolean exists() {
        return findFirst().isPresent();
    }

    // Stream derivation methods

    private QubitStreamImpl<T> withPredicates(List<QuerySpec<T, Boolean>> predicates) {
        return new QubitStreamImpl<>(entityClass, predicates, selector,
                sortOrders, offset, limit, distinct, aggregationType, aggregationMapper);
    }

    @SuppressWarnings("unchecked")
    private <R> QubitStream<R> withSelector(QuerySpec<T, R> selector) {
        return (QubitStream<R>) new QubitStreamImpl<>(
                entityClass, predicates, selector,
                (List<SortOrder<T>>) (List<?>) sortOrders,
                offset, limit, distinct, aggregationType, aggregationMapper);
    }

    private QubitStreamImpl<T> withSortOrders(List<SortOrder<T>> sortOrders) {
        return new QubitStreamImpl<>(entityClass, predicates, selector,
                sortOrders, offset, limit, distinct, aggregationType, aggregationMapper);
    }

    private QubitStreamImpl<T> withOffset(Integer offset) {
        return new QubitStreamImpl<>(entityClass, predicates, selector,
                sortOrders, offset, limit, distinct, aggregationType, aggregationMapper);
    }

    private QubitStreamImpl<T> withLimit(Integer limit) {
        return new QubitStreamImpl<>(entityClass, predicates, selector,
                sortOrders, offset, limit, distinct, aggregationType, aggregationMapper);
    }

    private QubitStreamImpl<T> withDistinct(boolean distinct) {
        return new QubitStreamImpl<>(entityClass, predicates, selector,
                sortOrders, offset, limit, distinct, aggregationType, aggregationMapper);
    }

    @SuppressWarnings("unchecked")
    private <R> QubitStream<R> withAggregation(AggregationType type, QuerySpec<T, ?> mapper) {
        return (QubitStream<R>) new QubitStreamImpl<>(
                entityClass, predicates, selector,
                sortOrders, offset, limit, distinct, type, mapper);
    }

    private Object getPrimaryLambda() {
        if (!predicates.isEmpty()) {
            return predicates.getFirst();
        }
        if (selector != null) {
            return selector;
        }
        if (aggregationMapper != null) {
            return aggregationMapper;
        }
        if (!sortOrders.isEmpty()) {
            return sortOrders.getLast().keyExtractor();
        }
        throw new IllegalStateException(
                "No lambda found in query pipeline. A Qubit query must have at least one " +
                        "lambda expression (where, select, sortedBy, min, max, avg, or sum*).");
    }

    private Object[] extractCapturedVariables(String callSiteId) {
        int capturedCount = QueryExecutorRegistry.getCapturedVariableCount(callSiteId);

        if (capturedCount == 0) {
            return new Object[0];
        }

        List<Object> allCapturedValues = new ArrayList<>();
        extractFromLambdas(predicates, allCapturedValues);
        if (selector != null) {
            extractFromSingleLambda(selector, allCapturedValues);
        }
        if (aggregationMapper != null) {
            extractFromSingleLambda(aggregationMapper, allCapturedValues);
        }
        for (SortOrder<T> sortOrder : sortOrders) {
            extractFromSingleLambda(sortOrder.keyExtractor(), allCapturedValues);
        }

        if (allCapturedValues.size() != capturedCount) {
            throw new IllegalStateException(
                    String.format("Captured variable count mismatch at %s: expected %d, found %d",
                            callSiteId, capturedCount, allCapturedValues.size()));
        }

        return allCapturedValues.toArray(new Object[0]);
    }

    @Override
    public <R> JoinStream<T, R> join(QuerySpec<T, Collection<R>> relationship) {
        requireNonNullLambda(relationship, "Relationship", "join");
        return new JoinStreamImpl<>(entityClass, relationship, JoinType.INNER);
    }

    @Override
    public <R> JoinStream<T, R> leftJoin(QuerySpec<T, Collection<R>> relationship) {
        requireNonNullLambda(relationship, "Relationship", "leftJoin");
        return new JoinStreamImpl<>(entityClass, relationship, JoinType.LEFT);
    }

    @Override
    public <K> GroupStream<T, K> groupBy(QuerySpec<T, K> keyExtractor) {
        requireNonNullLambda(keyExtractor, PARAM_KEY_EXTRACTOR, "groupBy");
        return new GroupStreamImpl<>(entityClass, keyExtractor, new ArrayList<>(predicates));
    }

    private record SortOrder<T>(QuerySpec<T, ?> keyExtractor, SortDirection direction) {
    }

    private enum AggregationType {
        MIN,
        MAX,
        SUM_INTEGER,
        SUM_LONG,
        SUM_DOUBLE,
        AVG
    }
}
