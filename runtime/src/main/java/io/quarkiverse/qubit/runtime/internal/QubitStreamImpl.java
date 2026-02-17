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

import io.quarkiverse.qubit.GroupStream;
import io.quarkiverse.qubit.JoinStream;
import io.quarkiverse.qubit.JoinType;
import io.quarkiverse.qubit.QubitStream;
import io.quarkiverse.qubit.QuerySpec;
import io.quarkiverse.qubit.ScalarResult;
import io.quarkiverse.qubit.SortDirection;

/**
 * Default implementation of {@link QubitStream} using JPA Criteria Queries.
 * <p>
 * This class implements an immutable stream pattern where each operation
 * returns a new instance with the accumulated operation state. Terminal
 * operations execute the accumulated pipeline as a JPA Criteria Query.
 * <p>
 * <strong>Design principles:</strong>
 * <ul>
 * <li><strong>Immutability</strong>: Each intermediate operation returns a new instance</li>
 * <li><strong>Build-time optimization</strong>: In production, this class is replaced with
 * build-time generated executors for zero runtime overhead</li>
 * <li><strong>Type safety</strong>: Generic parameters track type transformations through pipeline</li>
 * </ul>
 *
 * @param <T> the type of elements in this stream
 */
public class QubitStreamImpl<T> implements QubitStream<T> {

    // CONSTANTS

    private static final String PARAM_MAPPER = "Mapper";
    private static final String PARAM_KEY_EXTRACTOR = "Key extractor";

    // STATE FIELDS

    private final Class<T> entityClass;

    /** WHERE predicates combined with AND. */
    private final List<QuerySpec<T, Boolean>> predicates;

    /** Projection selector (null if no projection). */
    private final QuerySpec<T, ?> selector;

    /** Result type after projection (same as T if no projection). */
    private final Class<?> resultType;

    /** Sort orders (last added has priority). */
    private final List<SortOrder<T>> sortOrders;

    private final Integer offset;
    private final Integer limit;
    private final boolean distinct;

    /** Aggregation type (null if not an aggregation query). */
    private final AggregationType aggregationType;

    /** Aggregation mapper (null if not an aggregation query). */
    private final QuerySpec<T, ?> aggregationMapper;

    // CONSTRUCTORS

    /**
     * Creates a new stream for the given entity class with no operations.
     */
    public QubitStreamImpl(Class<T> entityClass) {
        this(entityClass, new ArrayList<>(), null, entityClass, new ArrayList<>(), null, null, false, null, null);
    }

    /**
     * Internal constructor for creating derived streams.
     * <p>
     * <b>Issue #19 Fix (Thread Safety):</b> Defensive copies are made of mutable
     * List parameters to prevent unsafe publication. While the current derivation
     * methods always create new lists, this defensive copying ensures the class
     * remains safe even if future code changes introduce shared references.
     */
    private QubitStreamImpl(
            Class<T> entityClass,
            List<QuerySpec<T, Boolean>> predicates,
            QuerySpec<T, ?> selector,
            Class<?> resultType,
            List<SortOrder<T>> sortOrders,
            Integer offset,
            Integer limit,
            boolean distinct,
            AggregationType aggregationType,
            QuerySpec<T, ?> aggregationMapper) {
        this.entityClass = entityClass;
        this.predicates = List.copyOf(predicates);
        this.selector = selector;
        this.resultType = resultType;
        this.sortOrders = List.copyOf(sortOrders);
        this.offset = offset;
        this.limit = limit;
        this.distinct = distinct;
        this.aggregationType = aggregationType;
        this.aggregationMapper = aggregationMapper;
    }

    // FILTERING

    @Override
    public QubitStream<T> where(QuerySpec<T, Boolean> predicate) {
        requireNonNullLambda(predicate, "Predicate", "where");
        List<QuerySpec<T, Boolean>> newPredicates = new ArrayList<>(this.predicates);
        newPredicates.add(predicate);
        return withPredicates(newPredicates);
    }

    // PROJECTION

    @Override
    @SuppressWarnings("unchecked")
    public <R> QubitStream<R> select(QuerySpec<T, R> mapper) {
        requireNonNullLambda(mapper, PARAM_MAPPER, "select");
        // For now, we'll use a simple approach - the actual type inference
        // will be done at build time by the processor
        Class<R> newResultType = (Class<R>) Object.class; // Placeholder

        // Create a new stream with the selector
        // Note: This is a type transformation, so we need to cast carefully
        return withSelector(mapper, newResultType);
    }

    // SORTING

    @Override
    public <K extends Comparable<K>> QubitStream<T> sortedBy(QuerySpec<T, K> keyExtractor) {
        requireNonNullLambda(keyExtractor, PARAM_KEY_EXTRACTOR, "sortedBy");
        // Sort orders are stored in execution priority order (index 0 = primary sort).
        // Since sortedBy() uses "last call wins" semantics, we PREPEND to make the
        // last-called sort the primary (index 0). thenSortedBy() APPENDS for secondary.
        List<SortOrder<T>> newSortOrders = new ArrayList<>(this.sortOrders);
        newSortOrders.add(0, new SortOrder<>(keyExtractor, SortDirection.ASCENDING));
        return withSortOrders(newSortOrders);
    }

    @Override
    public <K extends Comparable<K>> QubitStream<T> sortedDescendingBy(QuerySpec<T, K> keyExtractor) {
        requireNonNullLambda(keyExtractor, PARAM_KEY_EXTRACTOR, "sortedDescendingBy");
        List<SortOrder<T>> newSortOrders = new ArrayList<>(this.sortOrders);
        // Prepend to list (last call wins - becomes primary sort)
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

    // PAGINATION

    @Override
    public QubitStream<T> skip(int n) {
        return withOffset(validateSkipCount(n));
    }

    @Override
    public QubitStream<T> limit(int n) {
        return withLimit(validateLimitCount(n));
    }

    // DISTINCT

    @Override
    public QubitStream<T> distinct() {
        return withDistinct(true);
    }

    // AGGREGATION OPERATIONS (Terminal)

    @Override
    public long count() {
        // Delegate to build-time generated executor via registry
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

    // TERMINAL OPERATIONS

    @Override
    public List<T> toList() {
        // Delegate to build-time generated executor via registry
        String callSiteId = getCallSiteId(Set.of(), getPrimaryLambda());
        Object[] capturedValues = extractCapturedVariables(callSiteId);

        QueryExecutorRegistry registry = getQueryExecutorRegistry();

        // Pass pagination and distinct parameters to registry for runtime application
        return registry.executeListQuery(callSiteId, entityClass, capturedValues, offset, limit, distinct);
    }

    @Override
    @SuppressWarnings("unchecked")
    public T getSingleResult() {
        // If this is an aggregation query, execute it directly
        if (aggregationType != null) {
            String callSiteId = getCallSiteId(Set.of(), getPrimaryLambda());
            Object[] capturedValues = extractCapturedVariables(callSiteId);

            QueryExecutorRegistry registry = getQueryExecutorRegistry();
            return (T) registry.executeAggregationQuery(callSiteId, entityClass, capturedValues);
        }

        // Otherwise, delegate to toList() and validate single result
        // Note: This uses the build-time generated executor infrastructure
        return requireSingleResult(toList());
    }

    @Override
    public Optional<T> findFirst() {
        // Optimization: Apply limit(1) at SQL level if not already limited to ≤1 results
        // This prevents fetching all rows when we only need the first one
        QubitStream<T> stream = (this.limit == null || this.limit > 1) ? this.limit(1) : this;
        List<T> results = stream.toList();
        return results.isEmpty() ? Optional.empty() : Optional.of(results.getFirst());
    }

    @Override
    public boolean exists() {
        // Optimization: uses findFirst() which applies LIMIT 1 at the SQL level,
        // avoiding a full COUNT(*) scan of all matching rows.
        return findFirst().isPresent();
    }

    // INTERNAL HELPER METHODS - Stream Derivation

    /**
     * Creates a new stream with modified predicates.
     * All other state is copied from the current stream.
     */
    private QubitStreamImpl<T> withPredicates(List<QuerySpec<T, Boolean>> predicates) {
        return new QubitStreamImpl<>(entityClass, predicates, selector, resultType,
                sortOrders, offset, limit, distinct, aggregationType, aggregationMapper);
    }

    /**
     * Creates a new stream with modified selector and result type.
     * All other state is copied from the current stream.
     */
    @SuppressWarnings("unchecked")
    private <R> QubitStream<R> withSelector(QuerySpec<T, R> selector, Class<R> resultType) {
        return (QubitStream<R>) new QubitStreamImpl<>(
                entityClass,
                predicates,
                selector,
                resultType,
                (List<SortOrder<T>>) (List<?>) sortOrders,
                offset,
                limit,
                distinct,
                aggregationType,
                aggregationMapper);
    }

    /**
     * Creates a new stream with modified sort orders.
     * All other state is copied from the current stream.
     */
    private QubitStreamImpl<T> withSortOrders(List<SortOrder<T>> sortOrders) {
        return new QubitStreamImpl<>(entityClass, predicates, selector, resultType,
                sortOrders, offset, limit, distinct, aggregationType, aggregationMapper);
    }

    /**
     * Creates a new stream with modified offset.
     * All other state is copied from the current stream.
     */
    private QubitStreamImpl<T> withOffset(Integer offset) {
        return new QubitStreamImpl<>(entityClass, predicates, selector, resultType,
                sortOrders, offset, limit, distinct, aggregationType, aggregationMapper);
    }

    /**
     * Creates a new stream with modified limit.
     * All other state is copied from the current stream.
     */
    private QubitStreamImpl<T> withLimit(Integer limit) {
        return new QubitStreamImpl<>(entityClass, predicates, selector, resultType,
                sortOrders, offset, limit, distinct, aggregationType, aggregationMapper);
    }

    /**
     * Creates a new stream with modified distinct flag.
     * All other state is copied from the current stream.
     */
    private QubitStreamImpl<T> withDistinct(boolean distinct) {
        return new QubitStreamImpl<>(entityClass, predicates, selector, resultType,
                sortOrders, offset, limit, distinct, aggregationType, aggregationMapper);
    }

    /**
     * Creates a new stream with aggregation state.
     * All other state is copied from the current stream.
     */
    @SuppressWarnings("unchecked")
    private <R> QubitStream<R> withAggregation(AggregationType type, QuerySpec<T, ?> mapper) {
        return (QubitStream<R>) new QubitStreamImpl<>(
                entityClass,
                predicates,
                selector,
                resultType,
                sortOrders,
                offset,
                limit,
                distinct,
                type,
                mapper);
    }

    // INTERNAL HELPER METHODS - Call Site Resolution

    /**
     * Returns the primary lambda for call site ID uniqueness.
     * <p>
     * Priority order (matching build-time InvokeDynamicScanner.getPrimaryLambdaMethodName):
     * <ol>
     * <li>First predicate (most queries have a where clause)</li>
     * <li>Selector (for projection-only queries)</li>
     * <li>Aggregation mapper (for aggregation queries)</li>
     * <li>First sort key extractor (for sort-only queries)</li>
     * </ol>
     *
     * @return the primary lambda
     * @throws IllegalStateException if no lambdas are present in the pipeline
     */
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
        // sortOrders are prepended (newest at index 0), so pick the last
        // element (first added) to match build-time's sortLambdas.get(0)
        if (!sortOrders.isEmpty()) {
            return sortOrders.getLast().keyExtractor();
        }
        throw new IllegalStateException(
                "No lambda found in query pipeline. A Qubit query must have at least one " +
                        "lambda expression (where, select, sortedBy, min, max, avg, or sum*).");
    }

    /**
     * Extracts captured variables from all lambdas in the pipeline.
     * Uses {@link SerializedLambda#getCapturedArg(int)} via
     * {@link LambdaReflectionUtils#extractCapturedArgs(Object)}.
     * <p>
     * Extraction order must match build-time counting order in SimpleQueryHandler:
     * predicates → selector (projection) → aggregationMapper → sort key extractors.
     */
    private Object[] extractCapturedVariables(String callSiteId) {
        int capturedCount = QueryExecutorRegistry.getCapturedVariableCount(callSiteId);

        if (capturedCount == 0) {
            return new Object[0];
        }

        // Extract from ALL lambda sources in build-time counting order
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

    // JOIN OPERATIONS

    @Override
    @SuppressWarnings("unchecked")
    public <R> JoinStream<T, R> join(QuerySpec<T, Collection<R>> relationship) {
        requireNonNullLambda(relationship, "Relationship", "join");
        // Infer joined entity class from the relationship
        // For now, use Object.class as placeholder - actual type is resolved at build time
        Class<R> joinedClass = (Class<R>) Object.class;
        return new JoinStreamImpl<>(entityClass, joinedClass, relationship, JoinType.INNER);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <R> JoinStream<T, R> leftJoin(QuerySpec<T, Collection<R>> relationship) {
        requireNonNullLambda(relationship, "Relationship", "leftJoin");
        // Infer joined entity class from the relationship
        // For now, use Object.class as placeholder - actual type is resolved at build time
        Class<R> joinedClass = (Class<R>) Object.class;
        return new JoinStreamImpl<>(entityClass, joinedClass, relationship, JoinType.LEFT);
    }

    // GROUPING OPERATIONS

    @Override
    public <K> GroupStream<T, K> groupBy(QuerySpec<T, K> keyExtractor) {
        requireNonNullLambda(keyExtractor, PARAM_KEY_EXTRACTOR, "groupBy");
        // Create a new GroupStream with the accumulated predicates
        return new GroupStreamImpl<>(entityClass, keyExtractor, new ArrayList<>(predicates));
    }

    // INTERNAL CLASSES

    /**
     * Represents a sort order specification combining a key extractor with a direction.
     * Immutable record providing type-safe sort specifications for query ordering.
     *
     * @param keyExtractor Function to extract the sort key from entities
     * @param direction Sort direction (ascending or descending)
     * @param <T> Entity type being sorted
     */
    private record SortOrder<T>(QuerySpec<T, ?> keyExtractor, SortDirection direction) {
    }

    /**
     * Types of aggregation operations supported.
     */
    private enum AggregationType {
        MIN,
        MAX,
        SUM_INTEGER,
        SUM_LONG,
        SUM_DOUBLE,
        AVG
    }
}
