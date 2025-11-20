package io.quarkus.qusaq.runtime;

import io.quarkus.arc.Arc;
import jakarta.persistence.NoResultException;
import jakarta.persistence.NonUniqueResultException;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Default implementation of {@link QusaqStream} using JPA Criteria Queries.
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
public class QusaqStreamImpl<T> implements QusaqStream<T> {

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

    // =============================================================================================
    // CONSTRUCTORS
    // =============================================================================================

    /**
     * Creates a new stream for the given entity class with no operations.
     */
    public QusaqStreamImpl(Class<T> entityClass) {
        this(entityClass, new ArrayList<>(), null, entityClass, new ArrayList<>(), null, null, false);
    }

    /**
     * Internal constructor for creating derived streams.
     */
    private QusaqStreamImpl(
            Class<T> entityClass,
            List<QuerySpec<T, Boolean>> predicates,
            QuerySpec<T, ?> selector,
            Class<?> resultType,
            List<SortOrder<T>> sortOrders,
            Integer offset,
            Integer limit,
            boolean distinct) {
        this.entityClass = entityClass;
        this.predicates = predicates;
        this.selector = selector;
        this.resultType = resultType;
        this.sortOrders = sortOrders;
        this.offset = offset;
        this.limit = limit;
        this.distinct = distinct;
    }

    // =============================================================================================
    // FILTERING
    // =============================================================================================

    @Override
    public QusaqStream<T> where(QuerySpec<T, Boolean> predicate) {
        List<QuerySpec<T, Boolean>> newPredicates = new ArrayList<>(this.predicates);
        newPredicates.add(predicate);
        return new QusaqStreamImpl<>(entityClass, newPredicates, selector, resultType,
                sortOrders, offset, limit, distinct);
    }

    // =============================================================================================
    // PROJECTION
    // =============================================================================================

    @Override
    @SuppressWarnings("unchecked")
    public <R> QusaqStream<R> select(QuerySpec<T, R> mapper) {
        // For now, we'll use a simple approach - the actual type inference
        // will be done at build time by the processor
        Class<R> newResultType = (Class<R>) Object.class; // Placeholder

        // Create a new stream with the selector
        // Note: This is a type transformation, so we need to cast carefully
        return (QusaqStream<R>) new QusaqStreamImpl<>(
                entityClass,
                predicates,
                mapper,
                newResultType,
                (List<SortOrder<T>>) (List<?>) sortOrders,
                offset,
                limit,
                distinct
        );
    }

    // =============================================================================================
    // SORTING
    // =============================================================================================

    @Override
    public <K extends Comparable<K>> QusaqStream<T> sortedBy(QuerySpec<T, K> keyExtractor) {
        List<SortOrder<T>> newSortOrders = new ArrayList<>(this.sortOrders);
        // Prepend to list (last call wins - becomes primary sort)
        newSortOrders.add(0, new SortOrder<>(keyExtractor, false));
        return new QusaqStreamImpl<>(entityClass, predicates, selector, resultType,
                newSortOrders, offset, limit, distinct);
    }

    @Override
    public <K extends Comparable<K>> QusaqStream<T> sortedDescendingBy(QuerySpec<T, K> keyExtractor) {
        List<SortOrder<T>> newSortOrders = new ArrayList<>(this.sortOrders);
        // Prepend to list (last call wins - becomes primary sort)
        newSortOrders.add(0, new SortOrder<>(keyExtractor, true));
        return new QusaqStreamImpl<>(entityClass, predicates, selector, resultType,
                newSortOrders, offset, limit, distinct);
    }

    // =============================================================================================
    // PAGINATION
    // =============================================================================================

    @Override
    public QusaqStream<T> skip(int n) {
        if (n < 0) {
            throw new IllegalArgumentException("skip count must be >= 0, got: " + n);
        }
        return new QusaqStreamImpl<>(entityClass, predicates, selector, resultType,
                sortOrders, n, limit, distinct);
    }

    @Override
    public QusaqStream<T> limit(int n) {
        if (n < 0) {
            throw new IllegalArgumentException("limit count must be >= 0, got: " + n);
        }
        return new QusaqStreamImpl<>(entityClass, predicates, selector, resultType,
                sortOrders, offset, n, distinct);
    }

    // =============================================================================================
    // DISTINCT
    // =============================================================================================

    @Override
    public QusaqStream<T> distinct() {
        return new QusaqStreamImpl<>(entityClass, predicates, selector, resultType,
                sortOrders, offset, limit, true);
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
    public <K extends Comparable<K>> K min(QuerySpec<T, K> mapper) {
        throw new UnsupportedOperationException(
                "min() will be implemented in Phase 5. " +
                "This is a placeholder for Phase 1 core functionality.");
    }

    @Override
    public <K extends Comparable<K>> K max(QuerySpec<T, K> mapper) {
        throw new UnsupportedOperationException(
                "max() will be implemented in Phase 5. " +
                "This is a placeholder for Phase 1 core functionality.");
    }

    @Override
    public long sumInteger(QuerySpec<T, Integer> mapper) {
        throw new UnsupportedOperationException(
                "sumInteger() will be implemented in Phase 5. " +
                "This is a placeholder for Phase 1 core functionality.");
    }

    @Override
    public long sumLong(QuerySpec<T, Long> mapper) {
        throw new UnsupportedOperationException(
                "sumLong() will be implemented in Phase 5. " +
                "This is a placeholder for Phase 1 core functionality.");
    }

    @Override
    public double sumDouble(QuerySpec<T, Double> mapper) {
        throw new UnsupportedOperationException(
                "sumDouble() will be implemented in Phase 5. " +
                "This is a placeholder for Phase 1 core functionality.");
    }

    @Override
    public Double avg(QuerySpec<T, ? extends Number> mapper) {
        throw new UnsupportedOperationException(
                "avg() will be implemented in Phase 5. " +
                "This is a placeholder for Phase 1 core functionality.");
    }

    // =============================================================================================
    // TERMINAL OPERATIONS
    // =============================================================================================

    @Override
    public List<T> toList() {
        // Phase 3+: Validate advanced features not yet implemented
        if (!sortOrders.isEmpty()) {
            throw new UnsupportedOperationException(
                    "sortedBy() will be implemented in Phase 3. " +
                    "For Phase 2.2, use where() and select() only.");
        }
        if (distinct) {
            throw new UnsupportedOperationException(
                    "distinct() will be implemented in Phase 4. " +
                    "For Phase 2.2, use where() and select() only.");
        }
        if (offset != null || limit != null) {
            throw new UnsupportedOperationException(
                    "skip()/limit() will be implemented in Phase 4. " +
                    "For Phase 2.2, use where() and select() only.");
        }

        // Delegate to build-time generated executor via registry
        String callSiteId = getCallSiteId();
        Object[] capturedValues = extractCapturedVariables(callSiteId);

        QueryExecutorRegistry registry = Arc.container().instance(QueryExecutorRegistry.class).get();
        return registry.executeListQuery(callSiteId, entityClass, capturedValues);
    }

    @Override
    public T getSingleResult() {
        // Delegate to toList() and validate single result
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
        // Delegate to toList() and return first element wrapped in Optional
        // Note: This uses the build-time generated executor infrastructure
        // TODO Phase 4: When limit() is implemented, internally apply limit(1) for optimization
        List<T> results = toList();
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    @Override
    public boolean exists() {
        // Delegate to count() > 0 (as specified in the implementation tracker)
        return count() > 0;
    }

    // =============================================================================================
    // INTERNAL HELPER METHODS
    // =============================================================================================

    /**
     * Gets the call site ID using stack walking.
     * This is used to look up the pre-generated query executor.
     */
    private String getCallSiteId() {
        StackWalker walker = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);
        return walker.walk(frames -> frames
                .skip(1) // Skip getCallSiteId itself
                .filter(frame -> !frame.getClassName().startsWith("io.quarkus.qusaq.runtime."))
                .filter(frame -> !QusaqConstants.FLUENT_INTERMEDIATE_METHODS.contains(frame.getMethodName()))
                .filter(frame -> !QusaqConstants.FLUENT_TERMINAL_METHODS.contains(frame.getMethodName()))
                .findFirst()
                .map(frame -> frame.getClassName() + ":" +
                             frame.getMethodName() + ":" +
                             frame.getLineNumber())
                .orElseThrow(() -> new IllegalStateException("Could not determine call site")));
    }

    /**
     * Extracts captured variables from all predicates in the pipeline.
     * Phase 2.5+: Supports multiple where() clauses with captured variables.
     * Variables are extracted in predicate order and combined into a single array.
     */
    private Object[] extractCapturedVariables(String callSiteId) {
        int capturedCount = QueryExecutorRegistry.getCapturedVariableCount(callSiteId);

        if (capturedCount == 0) {
            return new Object[0];
        }

        // Phase 2.5+: Extract from ALL predicates, not just the first
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
    // INTERNAL CLASSES
    // =============================================================================================

    /**
     * Represents a sort order specification.
     * Phase 3: Will be used for sorting implementation.
     */
    @SuppressWarnings("unused") // Fields will be used in Phase 3
    private static class SortOrder<T> {
        final QuerySpec<T, ?> keyExtractor;
        final boolean descending;

        SortOrder(QuerySpec<T, ?> keyExtractor, boolean descending) {
            this.keyExtractor = keyExtractor;
            this.descending = descending;
        }
    }
}
