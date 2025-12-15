package io.quarkiverse.qubit.runtime;

import io.quarkus.arc.Arc;
import jakarta.persistence.NoResultException;
import jakarta.persistence.NonUniqueResultException;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Default implementation of {@link QubitStream} using JPA Criteria Queries.
 * <p>
 * This class implements an immutable stream pattern where each operation
 * returns a new instance with the accumulated operation state. Terminal
 * operations execute the accumulated pipeline as a JPA Criteria Query.
 * <p>
 * <strong>Design principles:</strong>
 * <ul>
 *   <li><strong>Immutability</strong>: Each intermediate operation returns a new instance</li>
 *   <li><strong>Build-time optimization</strong>: In production, this class is replaced with
 *       build-time generated executors for zero runtime overhead</li>
 *   <li><strong>Type safety</strong>: Generic parameters track type transformations through pipeline</li>
 * </ul>
 *
 * @param <T> the type of elements in this stream
 */
public class QubitStreamImpl<T> implements QubitStream<T> {

    // =============================================================================================
    // STATE FIELDS
    // =============================================================================================

    /**
     * The entity class being queried.
     */
    private final Class<T> entityClass;

    /**
     * Accumulated WHERE predicates (combined with AND).
     */
    private final List<QuerySpec<T, Boolean>> predicates;

    /**
     * Projection selector (null if no projection).
     */
    private final QuerySpec<T, ?> selector;

    /**
     * Result type after projection (same as T if no projection).
     */
    private final Class<?> resultType;

    /**
     * Sort orders (last added has priority).
     */
    private final List<SortOrder<T>> sortOrders;

    /**
     * OFFSET value (null if not set).
     */
    private final Integer offset;

    /**
     * LIMIT value (null if not set).
     */
    private final Integer limit;

    /**
     * DISTINCT flag.
     */
    private final boolean distinct;

    /**
     * Aggregation type (null if not an aggregation query).
     */
    private final AggregationType aggregationType;

    /**
     * Aggregation mapper (null if not an aggregation query).
     */
    private final QuerySpec<T, ?> aggregationMapper;

    // =============================================================================================
    // CONSTRUCTORS
    // =============================================================================================

    /**
     * Creates a new stream for the given entity class with no operations.
     */
    public QubitStreamImpl(Class<T> entityClass) {
        this(entityClass, new ArrayList<>(), null, entityClass, new ArrayList<>(), null, null, false, null, null);
    }

    /**
     * Internal constructor for creating derived streams.
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
        this.predicates = predicates;
        this.selector = selector;
        this.resultType = resultType;
        this.sortOrders = sortOrders;
        this.offset = offset;
        this.limit = limit;
        this.distinct = distinct;
        this.aggregationType = aggregationType;
        this.aggregationMapper = aggregationMapper;
    }

    // =============================================================================================
    // FILTERING
    // =============================================================================================

    @Override
    public QubitStream<T> where(QuerySpec<T, Boolean> predicate) {
        List<QuerySpec<T, Boolean>> newPredicates = new ArrayList<>(this.predicates);
        newPredicates.add(predicate);
        return withPredicates(newPredicates);
    }

    // =============================================================================================
    // PROJECTION
    // =============================================================================================

    @Override
    @SuppressWarnings("unchecked")
    public <R> QubitStream<R> select(QuerySpec<T, R> mapper) {
        // For now, we'll use a simple approach - the actual type inference
        // will be done at build time by the processor
        Class<R> newResultType = (Class<R>) Object.class; // Placeholder

        // Create a new stream with the selector
        // Note: This is a type transformation, so we need to cast carefully
        return withSelector(mapper, newResultType);
    }

    // =============================================================================================
    // SORTING
    // =============================================================================================

    @Override
    public <K extends Comparable<K>> QubitStream<T> sortedBy(QuerySpec<T, K> keyExtractor) {
        List<SortOrder<T>> newSortOrders = new ArrayList<>(this.sortOrders);
        // Prepend to list (last call wins - becomes primary sort)
        newSortOrders.add(0, new SortOrder<>(keyExtractor, SortDirection.ASCENDING));
        return withSortOrders(newSortOrders);
    }

    @Override
    public <K extends Comparable<K>> QubitStream<T> sortedDescendingBy(QuerySpec<T, K> keyExtractor) {
        List<SortOrder<T>> newSortOrders = new ArrayList<>(this.sortOrders);
        // Prepend to list (last call wins - becomes primary sort)
        newSortOrders.add(0, new SortOrder<>(keyExtractor, SortDirection.DESCENDING));
        return withSortOrders(newSortOrders);
    }

    // =============================================================================================
    // PAGINATION
    // =============================================================================================

    @Override
    public QubitStream<T> skip(int n) {
        if (n < 0) {
            throw new IllegalArgumentException("skip count must be >= 0, got: " + n);
        }
        return withOffset(n);
    }

    @Override
    public QubitStream<T> limit(int n) {
        if (n < 0) {
            throw new IllegalArgumentException("limit count must be >= 0, got: " + n);
        }
        return withLimit(n);
    }

    // =============================================================================================
    // DISTINCT
    // =============================================================================================

    @Override
    public QubitStream<T> distinct() {
        return withDistinct(true);
    }

    // =============================================================================================
    // AGGREGATION OPERATIONS (Terminal)
    // =============================================================================================

    @Override
    public long count() {
        // Delegate to build-time generated executor via registry
        String callSiteId = getCallSiteId();
        Object[] capturedValues = extractCapturedVariables(callSiteId);

        QueryExecutorRegistry registry = Arc.container().instance(QueryExecutorRegistry.class).get();
        return registry.executeCountQuery(callSiteId, entityClass, capturedValues);
    }

    @Override
    public <K extends Comparable<K>> QubitStream<K> min(QuerySpec<T, K> mapper) {
        // Store aggregation state, execution happens in getSingleResult()
        return withAggregation(AggregationType.MIN, mapper);
    }

    @Override
    public <K extends Comparable<K>> QubitStream<K> max(QuerySpec<T, K> mapper) {
        // Store aggregation state, execution happens in getSingleResult()
        return withAggregation(AggregationType.MAX, mapper);
    }

    @Override
    public QubitStream<Long> sumInteger(QuerySpec<T, Integer> mapper) {
        // Store aggregation state, execution happens in getSingleResult()
        return withAggregation(AggregationType.SUM_INTEGER, mapper);
    }

    @Override
    public QubitStream<Long> sumLong(QuerySpec<T, Long> mapper) {
        // Store aggregation state, execution happens in getSingleResult()
        return withAggregation(AggregationType.SUM_LONG, mapper);
    }

    @Override
    public QubitStream<Double> sumDouble(QuerySpec<T, Double> mapper) {
        // Store aggregation state, execution happens in getSingleResult()
        return withAggregation(AggregationType.SUM_DOUBLE, mapper);
    }

    @Override
    public QubitStream<Double> avg(QuerySpec<T, ? extends Number> mapper) {
        // Store aggregation state, execution happens in getSingleResult()
        return withAggregation(AggregationType.AVG, mapper);
    }

    // =============================================================================================
    // TERMINAL OPERATIONS
    // =============================================================================================

    @Override
    public List<T> toList() {
        // Delegate to build-time generated executor via registry
        String callSiteId = getCallSiteId();
        Object[] capturedValues = extractCapturedVariables(callSiteId);

        QueryExecutorRegistry registry = Arc.container().instance(QueryExecutorRegistry.class).get();

        // Pass pagination and distinct parameters to registry for runtime application
        return registry.executeListQuery(callSiteId, entityClass, capturedValues, offset, limit, distinct);
    }

    @Override
    @SuppressWarnings("unchecked")
    public T getSingleResult() {
        // If this is an aggregation query, execute it directly
        if (aggregationType != null) {
            String callSiteId = getCallSiteId();
            Object[] capturedValues = extractCapturedVariables(callSiteId);

            QueryExecutorRegistry registry = Arc.container().instance(QueryExecutorRegistry.class).get();
            return (T) registry.executeAggregationQuery(callSiteId, entityClass, capturedValues);
        }

        // Otherwise, delegate to toList() and validate single result
        // Note: This uses the build-time generated executor infrastructure
        List<T> results = toList();

        if (results.isEmpty()) {
            throw new NoResultException(
                    "getSingleResult() expected exactly one result but found none");
        }

        if (results.size() > 1) {
            throw new NonUniqueResultException(
                    "getSingleResult() expected exactly one result but found " + results.size());
        }

        return results.get(0);
    }

    @Override
    public Optional<T> findFirst() {
        // Optimization: Apply limit(1) at SQL level if not already limited to ≤1 results
        // This prevents fetching all rows when we only need the first one
        QubitStream<T> stream = (this.limit == null || this.limit > 1) ? this.limit(1) : this;
        List<T> results = stream.toList();
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    @Override
    public boolean exists() {
        // Delegate to count() > 0 (as specified in the implementation tracker)
        return count() > 0;
    }

    // =============================================================================================
    // INTERNAL HELPER METHODS - Stream Derivation
    // =============================================================================================

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
                aggregationMapper
        );
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
                mapper
        );
    }

    // =============================================================================================
    // INTERNAL HELPER METHODS - Call Site Resolution
    // =============================================================================================

    /**
     * Gets the call site ID using stack walking.
     * This is used to look up the pre-generated query executor.
     */
    private String getCallSiteId() {
        StackWalker walker = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);
        return walker.walk(frames -> frames
                .skip(1) // Skip getCallSiteId itself
                .filter(frame -> !frame.getClassName().startsWith("io.quarkiverse.qubit.runtime."))
                .filter(frame -> !QubitConstants.FLUENT_INTERMEDIATE_METHODS.contains(frame.getMethodName()))
                .filter(frame -> !QubitConstants.FLUENT_TERMINAL_METHODS.contains(frame.getMethodName()))
                .findFirst()
                .map(frame -> frame.getClassName() + ":" +
                             frame.getMethodName() + ":" +
                             frame.getLineNumber())
                .orElseThrow(() -> new IllegalStateException("Could not determine call site")));
    }

    /**
     * Extracts captured variables from all predicates in the pipeline.
     * Supports multiple where() clauses with captured variables.
     */
    private Object[] extractCapturedVariables(String callSiteId) {
        int capturedCount = QueryExecutorRegistry.getCapturedVariableCount(callSiteId);

        if (capturedCount == 0) {
            return new Object[0];
        }

        // Extract from ALL predicates, not just the first
        if (predicates.isEmpty()) {
            return new Object[0];
        }

        // Single predicate optimization (most common case)
        if (predicates.size() == 1) {
            return CapturedVariableExtractor.extract(predicates.get(0), capturedCount);
        }

        // Multiple predicates: extract from each and combine
        // Build-time renumbering in CallSiteProcessor ensures CapturedVariable indices are sequential
        List<Object> allCapturedValues = new ArrayList<>();
        int remainingCount = capturedCount;

        for (QuerySpec<T, Boolean> predicate : predicates) {
            if (remainingCount == 0) {
                break; // All captured variables extracted
            }

            // Count captured fields in this predicate
            int predicateCapturedCount = countCapturedFields(predicate);

            if (predicateCapturedCount > 0) {
                Object[] predicateValues = CapturedVariableExtractor.extract(predicate, predicateCapturedCount);
                Collections.addAll(allCapturedValues, predicateValues);
                remainingCount -= predicateCapturedCount;
            }
        }

        if (remainingCount != 0) {
            throw new IllegalStateException(
                    String.format("Captured variable count mismatch at %s: expected %d, found %d",
                            callSiteId, capturedCount, capturedCount - remainingCount));
        }

        return allCapturedValues.toArray(new Object[0]);
    }

    /**
     * Counts the number of captured variable fields in a lambda instance.
     * Lambda instances store captured variables as non-static instance fields.
     *
     * @param lambdaInstance the lambda instance (QuerySpec)
     * @return number of captured variable fields
     */
    private int countCapturedFields(Object lambdaInstance) {
        if (lambdaInstance == null) {
            return 0;
        }

        Class<?> lambdaClass = lambdaInstance.getClass();
        Field[] allFields = lambdaClass.getDeclaredFields();

        // Count non-static instance fields - these are the captured variables
        // Lambda instances only have fields for captured variables (no other instance fields)
        int count = 0;
        for (Field field : allFields) {
            if (!Modifier.isStatic(field.getModifiers())) {
                count++;
            }
        }

        return count;
    }

    // =============================================================================================
    // JOIN OPERATIONS
    // =============================================================================================

    @Override
    @SuppressWarnings("unchecked")
    public <R> JoinStream<T, R> join(QuerySpec<T, Collection<R>> relationship) {
        // Infer joined entity class from the relationship
        // For now, use Object.class as placeholder - actual type is resolved at build time
        Class<R> joinedClass = (Class<R>) Object.class;
        return new JoinStreamImpl<>(entityClass, joinedClass, relationship, JoinType.INNER);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <R> JoinStream<T, R> leftJoin(QuerySpec<T, Collection<R>> relationship) {
        // Infer joined entity class from the relationship
        // For now, use Object.class as placeholder - actual type is resolved at build time
        Class<R> joinedClass = (Class<R>) Object.class;
        return new JoinStreamImpl<>(entityClass, joinedClass, relationship, JoinType.LEFT);
    }

    // =============================================================================================
    // GROUPING OPERATIONS
    // =============================================================================================

    @Override
    public <K> GroupStream<T, K> groupBy(QuerySpec<T, K> keyExtractor) {
        // Create a new GroupStream with the accumulated predicates
        return new GroupStreamImpl<>(entityClass, keyExtractor, new ArrayList<>(predicates));
    }

    // =============================================================================================
    // INTERNAL CLASSES
    // =============================================================================================

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
