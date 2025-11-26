package io.quarkus.qusaq.runtime;

import io.quarkus.arc.Arc;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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

    // =============================================================================================
    // STATE FIELDS
    // =============================================================================================

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

    // =============================================================================================
    // CONSTRUCTORS
    // =============================================================================================

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
        this.predicates = predicates;
        this.havingConditions = havingConditions;
        this.selector = selector;
        this.sortOrders = sortOrders;
        this.offset = offset;
        this.limit = limit;
    }

    // =============================================================================================
    // HAVING CLAUSE
    // =============================================================================================

    @Override
    public GroupStream<T, K> having(GroupQuerySpec<T, K, Boolean> condition) {
        List<GroupQuerySpec<T, K, Boolean>> newConditions = new ArrayList<>(this.havingConditions);
        newConditions.add(condition);
        return withHavingConditions(newConditions);
    }

    // =============================================================================================
    // PROJECTION
    // =============================================================================================

    @Override
    @SuppressWarnings("unchecked")
    public <R> QusaqStream<R> select(GroupQuerySpec<T, K, R> mapper) {
        // Create a new stream that represents the projected result
        // The actual type inference and query generation is done at build time
        String callSiteId = getCallSiteId();
        Object[] capturedValues = extractCapturedVariables(callSiteId);

        QueryExecutorRegistry registry = Arc.container().instance(QueryExecutorRegistry.class).get();
        List<R> results = registry.executeGroupQuery(callSiteId, entityClass, capturedValues, offset, limit);
        return new ListQusaqStream<>(results);
    }

    @Override
    @SuppressWarnings("unchecked")
    public QusaqStream<K> selectKey() {
        String callSiteId = getCallSiteId();
        Object[] capturedValues = extractCapturedVariables(callSiteId);

        QueryExecutorRegistry registry = Arc.container().instance(QueryExecutorRegistry.class).get();
        List<K> results = registry.executeGroupKeyQuery(callSiteId, entityClass, capturedValues, offset, limit);
        return new ListQusaqStream<>(results);
    }

    // =============================================================================================
    // SORTING
    // =============================================================================================

    @Override
    public <C extends Comparable<C>> GroupStream<T, K> sortedBy(GroupQuerySpec<T, K, C> keyExtractor) {
        List<GroupSortOrder<T, K>> newSortOrders = new ArrayList<>(this.sortOrders);
        newSortOrders.add(0, new GroupSortOrder<>(keyExtractor, SortDirection.ASCENDING));
        return withSortOrders(newSortOrders);
    }

    @Override
    public <C extends Comparable<C>> GroupStream<T, K> sortedDescendingBy(GroupQuerySpec<T, K, C> keyExtractor) {
        List<GroupSortOrder<T, K>> newSortOrders = new ArrayList<>(this.sortOrders);
        newSortOrders.add(0, new GroupSortOrder<>(keyExtractor, SortDirection.DESCENDING));
        return withSortOrders(newSortOrders);
    }

    // =============================================================================================
    // PAGINATION
    // =============================================================================================

    @Override
    public GroupStream<T, K> skip(int n) {
        if (n < 0) {
            throw new IllegalArgumentException("skip count must be >= 0, got: " + n);
        }
        return withOffset(n);
    }

    @Override
    public GroupStream<T, K> limit(int n) {
        if (n < 0) {
            throw new IllegalArgumentException("limit count must be >= 0, got: " + n);
        }
        return withLimit(n);
    }

    // =============================================================================================
    // TERMINAL OPERATIONS
    // =============================================================================================

    @Override
    @SuppressWarnings("unchecked")
    public List<K> toList() {
        String callSiteId = getCallSiteId();
        Object[] capturedValues = extractCapturedVariables(callSiteId);

        QueryExecutorRegistry registry = Arc.container().instance(QueryExecutorRegistry.class).get();
        return registry.executeGroupKeyQuery(callSiteId, entityClass, capturedValues, offset, limit);
    }

    @Override
    public long count() {
        String callSiteId = getCallSiteId();
        Object[] capturedValues = extractCapturedVariables(callSiteId);

        QueryExecutorRegistry registry = Arc.container().instance(QueryExecutorRegistry.class).get();
        return registry.executeGroupCountQuery(callSiteId, entityClass, capturedValues);
    }

    // =============================================================================================
    // INTERNAL HELPER METHODS - Stream Derivation
    // =============================================================================================

    private GroupStreamImpl<T, K> withHavingConditions(List<GroupQuerySpec<T, K, Boolean>> havingConditions) {
        return new GroupStreamImpl<>(entityClass, keyExtractor, predicates, havingConditions, selector, sortOrders, offset, limit);
    }

    private GroupStreamImpl<T, K> withSortOrders(List<GroupSortOrder<T, K>> sortOrders) {
        return new GroupStreamImpl<>(entityClass, keyExtractor, predicates, havingConditions, selector, sortOrders, offset, limit);
    }

    private GroupStreamImpl<T, K> withOffset(Integer offset) {
        return new GroupStreamImpl<>(entityClass, keyExtractor, predicates, havingConditions, selector, sortOrders, offset, limit);
    }

    private GroupStreamImpl<T, K> withLimit(Integer limit) {
        return new GroupStreamImpl<>(entityClass, keyExtractor, predicates, havingConditions, selector, sortOrders, offset, limit);
    }

    // =============================================================================================
    // INTERNAL HELPER METHODS - Call Site Resolution
    // =============================================================================================

    private String getCallSiteId() {
        StackWalker walker = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);
        return walker.walk(frames -> frames
                .skip(1)
                .filter(frame -> !frame.getClassName().startsWith("io.quarkus.qusaq.runtime."))
                .filter(frame -> !QusaqConstants.FLUENT_INTERMEDIATE_METHODS.contains(frame.getMethodName()))
                .filter(frame -> !QusaqConstants.FLUENT_TERMINAL_METHODS.contains(frame.getMethodName()))
                .findFirst()
                .map(frame -> frame.getClassName() + ":" +
                             frame.getMethodName() + ":" +
                             frame.getLineNumber())
                .orElseThrow(() -> new IllegalStateException("Could not determine call site")));
    }

    private Object[] extractCapturedVariables(String callSiteId) {
        int capturedCount = QueryExecutorRegistry.getCapturedVariableCount(callSiteId);

        if (capturedCount == 0) {
            return new Object[0];
        }

        List<Object> allCapturedValues = new ArrayList<>();
        int remainingCount = capturedCount;

        // Extract from predicates (WHERE clauses before grouping)
        for (QuerySpec<T, Boolean> predicate : predicates) {
            if (remainingCount == 0) {
                break;
            }

            int predicateCapturedCount = countCapturedFields(predicate);

            if (predicateCapturedCount > 0) {
                Object[] predicateValues = CapturedVariableExtractor.extract(predicate, predicateCapturedCount);
                Collections.addAll(allCapturedValues, predicateValues);
                remainingCount -= predicateCapturedCount;
            }
        }

        // Extract from keyExtractor (groupBy key)
        if (remainingCount > 0 && keyExtractor != null) {
            int keyExtractorCapturedCount = countCapturedFields(keyExtractor);
            if (keyExtractorCapturedCount > 0) {
                Object[] keyValues = CapturedVariableExtractor.extract(keyExtractor, keyExtractorCapturedCount);
                Collections.addAll(allCapturedValues, keyValues);
                remainingCount -= keyExtractorCapturedCount;
            }
        }

        // Extract from havingConditions (HAVING clauses)
        for (GroupQuerySpec<T, K, Boolean> havingCondition : havingConditions) {
            if (remainingCount == 0) {
                break;
            }

            int havingCapturedCount = countCapturedFields(havingCondition);

            if (havingCapturedCount > 0) {
                Object[] havingValues = CapturedVariableExtractor.extract(havingCondition, havingCapturedCount);
                Collections.addAll(allCapturedValues, havingValues);
                remainingCount -= havingCapturedCount;
            }
        }

        // Extract from selector (select projection)
        if (remainingCount > 0 && selector != null) {
            int selectorCapturedCount = countCapturedFields(selector);
            if (selectorCapturedCount > 0) {
                Object[] selectorValues = CapturedVariableExtractor.extract(selector, selectorCapturedCount);
                Collections.addAll(allCapturedValues, selectorValues);
                remainingCount -= selectorCapturedCount;
            }
        }

        return allCapturedValues.toArray(new Object[0]);
    }

    private int countCapturedFields(Object lambdaInstance) {
        if (lambdaInstance == null) {
            return 0;
        }

        Class<?> lambdaClass = lambdaInstance.getClass();
        Field[] allFields = lambdaClass.getDeclaredFields();

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
     * Represents a sort order specification for groups.
     */
    private record GroupSortOrder<T, K>(GroupQuerySpec<T, K, ?> keyExtractor, SortDirection direction) {
    }

    /**
     * Simple QusaqStream implementation that wraps a list of results.
     * Used for returning results from group projections.
     */
    private static class ListQusaqStream<T> implements QusaqStream<T> {
        private final List<T> results;

        ListQusaqStream(List<T> results) {
            this.results = results;
        }

        @Override
        public QusaqStream<T> where(QuerySpec<T, Boolean> predicate) {
            throw new UnsupportedOperationException("Cannot filter after group projection");
        }

        @Override
        public <R> QusaqStream<R> select(QuerySpec<T, R> mapper) {
            throw new UnsupportedOperationException("Cannot project after group projection");
        }

        @Override
        public <K extends Comparable<K>> QusaqStream<T> sortedBy(QuerySpec<T, K> keyExtractor) {
            throw new UnsupportedOperationException("Cannot sort after group projection");
        }

        @Override
        public <K extends Comparable<K>> QusaqStream<T> sortedDescendingBy(QuerySpec<T, K> keyExtractor) {
            throw new UnsupportedOperationException("Cannot sort after group projection");
        }

        @Override
        public QusaqStream<T> skip(int n) {
            return new ListQusaqStream<>(results.subList(Math.min(n, results.size()), results.size()));
        }

        @Override
        public QusaqStream<T> limit(int n) {
            return new ListQusaqStream<>(results.subList(0, Math.min(n, results.size())));
        }

        @Override
        public QusaqStream<T> distinct() {
            return new ListQusaqStream<>(results.stream().distinct().toList());
        }

        @Override
        public long count() {
            return results.size();
        }

        @Override
        public <K extends Comparable<K>> QusaqStream<K> min(QuerySpec<T, K> mapper) {
            throw new UnsupportedOperationException("Cannot aggregate after group projection");
        }

        @Override
        public <K extends Comparable<K>> QusaqStream<K> max(QuerySpec<T, K> mapper) {
            throw new UnsupportedOperationException("Cannot aggregate after group projection");
        }

        @Override
        public QusaqStream<Long> sumInteger(QuerySpec<T, Integer> mapper) {
            throw new UnsupportedOperationException("Cannot aggregate after group projection");
        }

        @Override
        public QusaqStream<Long> sumLong(QuerySpec<T, Long> mapper) {
            throw new UnsupportedOperationException("Cannot aggregate after group projection");
        }

        @Override
        public QusaqStream<Double> sumDouble(QuerySpec<T, Double> mapper) {
            throw new UnsupportedOperationException("Cannot aggregate after group projection");
        }

        @Override
        public QusaqStream<Double> avg(QuerySpec<T, ? extends Number> mapper) {
            throw new UnsupportedOperationException("Cannot aggregate after group projection");
        }

        @Override
        public List<T> toList() {
            return new ArrayList<>(results);
        }

        @Override
        public T getSingleResult() {
            if (results.isEmpty()) {
                throw new jakarta.persistence.NoResultException("getSingleResult() expected exactly one result but found none");
            }
            if (results.size() > 1) {
                throw new jakarta.persistence.NonUniqueResultException("getSingleResult() expected exactly one result but found " + results.size());
            }
            return results.get(0);
        }

        @Override
        public java.util.Optional<T> findFirst() {
            return results.isEmpty() ? java.util.Optional.empty() : java.util.Optional.of(results.get(0));
        }

        @Override
        public boolean exists() {
            return !results.isEmpty();
        }

        @Override
        public <R> JoinStream<T, R> join(QuerySpec<T, java.util.Collection<R>> relationship) {
            throw new UnsupportedOperationException("Cannot join after group projection");
        }

        @Override
        public <R> JoinStream<T, R> leftJoin(QuerySpec<T, java.util.Collection<R>> relationship) {
            throw new UnsupportedOperationException("Cannot join after group projection");
        }

        @Override
        public <K> GroupStream<T, K> groupBy(QuerySpec<T, K> keyExtractor) {
            throw new UnsupportedOperationException("Cannot group after group projection");
        }
    }
}
