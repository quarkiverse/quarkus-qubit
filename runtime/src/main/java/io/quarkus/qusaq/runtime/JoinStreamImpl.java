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

    // =============================================================================================
    // STATE FIELDS
    // =============================================================================================

    /**
     * The source entity class being queried.
     */
    private final Class<T> sourceEntityClass;

    /**
     * The joined entity class.
     */
    private final Class<R> joinedEntityClass;

    /**
     * The relationship accessor lambda for the join.
     */
    private final QuerySpec<T, ?> relationshipAccessor;

    /**
     * Join type (INNER or LEFT).
     */
    private final JoinType joinType;

    /**
     * ON clause conditions.
     */
    private final List<BiQuerySpec<T, R, Boolean>> onConditions;

    /**
     * WHERE predicates using bi-entity lambdas.
     */
    private final List<BiQuerySpec<T, R, Boolean>> biPredicates;

    /**
     * WHERE predicates using single-entity lambdas (source only).
     */
    private final List<QuerySpec<T, Boolean>> sourcePredicates;

    /**
     * Sort orders.
     */
    private final List<BiSortOrder<T, R>> sortOrders;

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
     * Creates a new JoinStream for the given source entity and relationship.
     */
    public JoinStreamImpl(
            Class<T> sourceEntityClass,
            Class<R> joinedEntityClass,
            QuerySpec<T, ?> relationshipAccessor,
            JoinType joinType) {
        this(sourceEntityClass, joinedEntityClass, relationshipAccessor, joinType,
             new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>(),
             null, null, false);
    }

    /**
     * Internal constructor for creating derived streams.
     */
    private JoinStreamImpl(
            Class<T> sourceEntityClass,
            Class<R> joinedEntityClass,
            QuerySpec<T, ?> relationshipAccessor,
            JoinType joinType,
            List<BiQuerySpec<T, R, Boolean>> onConditions,
            List<BiQuerySpec<T, R, Boolean>> biPredicates,
            List<QuerySpec<T, Boolean>> sourcePredicates,
            List<BiSortOrder<T, R>> sortOrders,
            Integer offset,
            Integer limit,
            boolean distinct) {
        this.sourceEntityClass = sourceEntityClass;
        this.joinedEntityClass = joinedEntityClass;
        this.relationshipAccessor = relationshipAccessor;
        this.joinType = joinType;
        this.onConditions = onConditions;
        this.biPredicates = biPredicates;
        this.sourcePredicates = sourcePredicates;
        this.sortOrders = sortOrders;
        this.offset = offset;
        this.limit = limit;
        this.distinct = distinct;
    }

    // =============================================================================================
    // JOIN CONDITIONS
    // =============================================================================================

    @Override
    public JoinStream<T, R> on(BiQuerySpec<T, R, Boolean> condition) {
        List<BiQuerySpec<T, R, Boolean>> newOnConditions = new ArrayList<>(this.onConditions);
        newOnConditions.add(condition);
        return new JoinStreamImpl<>(sourceEntityClass, joinedEntityClass, relationshipAccessor, joinType,
                newOnConditions, biPredicates, sourcePredicates, sortOrders, offset, limit, distinct);
    }

    // =============================================================================================
    // FILTERING
    // =============================================================================================

    @Override
    public JoinStream<T, R> where(BiQuerySpec<T, R, Boolean> predicate) {
        List<BiQuerySpec<T, R, Boolean>> newBiPredicates = new ArrayList<>(this.biPredicates);
        newBiPredicates.add(predicate);
        return new JoinStreamImpl<>(sourceEntityClass, joinedEntityClass, relationshipAccessor, joinType,
                onConditions, newBiPredicates, sourcePredicates, sortOrders, offset, limit, distinct);
    }

    @Override
    public JoinStream<T, R> where(QuerySpec<T, Boolean> predicate) {
        List<QuerySpec<T, Boolean>> newSourcePredicates = new ArrayList<>(this.sourcePredicates);
        newSourcePredicates.add(predicate);
        return new JoinStreamImpl<>(sourceEntityClass, joinedEntityClass, relationshipAccessor, joinType,
                onConditions, biPredicates, newSourcePredicates, sortOrders, offset, limit, distinct);
    }

    // =============================================================================================
    // PROJECTION
    // =============================================================================================

    @Override
    public <S> QusaqStream<S> select(BiQuerySpec<T, R, S> mapper) {
        // Iteration 6.6: Execute join projection query and return wrapped results
        String callSiteId = getCallSiteId();
        Object[] capturedValues = extractCapturedVariables();

        QueryExecutorRegistry registry = Arc.container().instance(QueryExecutorRegistry.class).get();
        List<S> results = registry.executeJoinProjectionQuery(callSiteId, sourceEntityClass, capturedValues, offset, limit, distinct);
        return new ListProjectionQusaqStream<>(results);
    }

    @Override
    public QusaqStream<T> selectSource() {
        // Return source entities only - this is handled by toList()
        return new QusaqStreamImpl<>(sourceEntityClass);
    }

    @Override
    public QusaqStream<R> selectJoined() {
        // Execute query selecting joined entities and return wrapped results
        String callSiteId = getCallSiteId();
        Object[] capturedValues = extractCapturedVariables();

        QueryExecutorRegistry registry = Arc.container().instance(QueryExecutorRegistry.class).get();
        List<R> results = registry.executeJoinSelectJoinedQuery(callSiteId, sourceEntityClass, capturedValues, offset, limit, distinct);
        return new ListJoinedQusaqStream<>(results);
    }

    // =============================================================================================
    // SORTING
    // =============================================================================================

    @Override
    public <K extends Comparable<K>> JoinStream<T, R> sortedBy(BiQuerySpec<T, R, K> keyExtractor) {
        List<BiSortOrder<T, R>> newSortOrders = new ArrayList<>(this.sortOrders);
        newSortOrders.add(0, new BiSortOrder<>(keyExtractor, SortDirection.ASCENDING));
        return new JoinStreamImpl<>(sourceEntityClass, joinedEntityClass, relationshipAccessor, joinType,
                onConditions, biPredicates, sourcePredicates, newSortOrders, offset, limit, distinct);
    }

    @Override
    public <K extends Comparable<K>> JoinStream<T, R> sortedDescendingBy(BiQuerySpec<T, R, K> keyExtractor) {
        List<BiSortOrder<T, R>> newSortOrders = new ArrayList<>(this.sortOrders);
        newSortOrders.add(0, new BiSortOrder<>(keyExtractor, SortDirection.DESCENDING));
        return new JoinStreamImpl<>(sourceEntityClass, joinedEntityClass, relationshipAccessor, joinType,
                onConditions, biPredicates, sourcePredicates, newSortOrders, offset, limit, distinct);
    }

    // =============================================================================================
    // PAGINATION
    // =============================================================================================

    @Override
    public JoinStream<T, R> skip(int n) {
        if (n < 0) {
            throw new IllegalArgumentException("skip count must be >= 0, got: " + n);
        }
        return new JoinStreamImpl<>(sourceEntityClass, joinedEntityClass, relationshipAccessor, joinType,
                onConditions, biPredicates, sourcePredicates, sortOrders, n, limit, distinct);
    }

    @Override
    public JoinStream<T, R> limit(int n) {
        if (n < 0) {
            throw new IllegalArgumentException("limit count must be >= 0, got: " + n);
        }
        return new JoinStreamImpl<>(sourceEntityClass, joinedEntityClass, relationshipAccessor, joinType,
                onConditions, biPredicates, sourcePredicates, sortOrders, offset, n, distinct);
    }

    // =============================================================================================
    // DISTINCT
    // =============================================================================================

    @Override
    public JoinStream<T, R> distinct() {
        return new JoinStreamImpl<>(sourceEntityClass, joinedEntityClass, relationshipAccessor, joinType,
                onConditions, biPredicates, sourcePredicates, sortOrders, offset, limit, true);
    }

    // =============================================================================================
    // TERMINAL OPERATIONS
    // =============================================================================================

    @Override
    public List<T> toList() {
        // Delegate to build-time generated executor via registry
        String callSiteId = getCallSiteId();
        Object[] capturedValues = extractCapturedVariables();

        QueryExecutorRegistry registry = Arc.container().instance(QueryExecutorRegistry.class).get();

        // Execute as join query - registry will need to handle this specially
        return registry.executeJoinListQuery(callSiteId, sourceEntityClass, capturedValues, offset, limit, distinct);
    }

    @Override
    public T getSingleResult() {
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
        JoinStream<T, R> stream = (this.limit == null || this.limit > 1) ? this.limit(1) : this;
        List<T> results = stream.toList();
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    @Override
    public long count() {
        String callSiteId = getCallSiteId();
        Object[] capturedValues = extractCapturedVariables();

        QueryExecutorRegistry registry = Arc.container().instance(QueryExecutorRegistry.class).get();
        return registry.executeJoinCountQuery(callSiteId, sourceEntityClass, capturedValues);
    }

    @Override
    public boolean exists() {
        return count() > 0;
    }

    // =============================================================================================
    // INTERNAL HELPER METHODS
    // =============================================================================================

    /**
     * Gets the call site ID using stack walking.
     */
    private String getCallSiteId() {
        StackWalker walker = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);
        return walker.walk(frames -> frames
                .skip(1)
                .filter(frame -> !frame.getClassName().startsWith("io.quarkus.qusaq.runtime."))
                .filter(frame -> !QusaqConstants.FLUENT_INTERMEDIATE_METHODS.contains(frame.getMethodName()))
                .filter(frame -> !QusaqConstants.FLUENT_TERMINAL_METHODS.contains(frame.getMethodName()))
                .filter(frame -> !QusaqConstants.JOIN_METHODS.contains(frame.getMethodName()))
                .findFirst()
                .map(frame -> frame.getClassName() + ":" +
                             frame.getMethodName() + ":" +
                             frame.getLineNumber())
                .orElseThrow(() -> new IllegalStateException("Could not determine call site")));
    }

    /**
     * Extracts captured variables from all lambdas in the join stream.
     * Supports bi-entity predicates, source predicates, and ON conditions.
     * Variables are extracted in the order they appear and combined into a single array.
     */
    private Object[] extractCapturedVariables() {
        String callSiteId = getCallSiteId();
        int capturedCount = QueryExecutorRegistry.getCapturedVariableCount(callSiteId);

        if (capturedCount == 0) {
            return new Object[0];
        }

        // Extract from all lambda sources: bi-predicates, source predicates, ON conditions
        List<Object> allCapturedValues = new ArrayList<>();
        int remainingCount = capturedCount;

        // Extract from bi-entity predicates (most common for join queries)
        for (BiQuerySpec<T, R, Boolean> biPredicate : biPredicates) {
            if (remainingCount == 0) break;

            int predicateCapturedCount = countCapturedFields(biPredicate);
            if (predicateCapturedCount > 0) {
                Object[] predicateValues = CapturedVariableExtractor.extract(biPredicate, predicateCapturedCount);
                Collections.addAll(allCapturedValues, predicateValues);
                remainingCount -= predicateCapturedCount;
            }
        }

        // Extract from source predicates (single-entity predicates on source)
        for (QuerySpec<T, Boolean> sourcePredicate : sourcePredicates) {
            if (remainingCount == 0) break;

            int predicateCapturedCount = countCapturedFields(sourcePredicate);
            if (predicateCapturedCount > 0) {
                Object[] predicateValues = CapturedVariableExtractor.extract(sourcePredicate, predicateCapturedCount);
                Collections.addAll(allCapturedValues, predicateValues);
                remainingCount -= predicateCapturedCount;
            }
        }

        // Extract from ON conditions
        for (BiQuerySpec<T, R, Boolean> onCondition : onConditions) {
            if (remainingCount == 0) break;

            int conditionCapturedCount = countCapturedFields(onCondition);
            if (conditionCapturedCount > 0) {
                Object[] conditionValues = CapturedVariableExtractor.extract(onCondition, conditionCapturedCount);
                Collections.addAll(allCapturedValues, conditionValues);
                remainingCount -= conditionCapturedCount;
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
     * @param lambdaInstance the lambda instance
     * @return number of captured variable fields
     */
    private int countCapturedFields(Object lambdaInstance) {
        if (lambdaInstance == null) {
            return 0;
        }

        Class<?> lambdaClass = lambdaInstance.getClass();
        Field[] allFields = lambdaClass.getDeclaredFields();

        // Count non-static instance fields - these are the captured variables
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
     * Represents a bi-entity sort order specification.
     */
    private record BiSortOrder<T, R>(BiQuerySpec<T, R, ?> keyExtractor, SortDirection direction) {
    }

    /**
     * Simple QusaqStream implementation wrapping results from select() with BiQuerySpec.
     * Used for returning projected results after select((source, joined) -> projection) operation.
     * Iteration 6.6: Join Projections
     */
    private static class ListProjectionQusaqStream<T> implements QusaqStream<T> {
        private final List<T> results;

        ListProjectionQusaqStream(List<T> results) {
            this.results = results;
        }

        @Override
        public QusaqStream<T> where(QuerySpec<T, Boolean> predicate) {
            throw new UnsupportedOperationException("Cannot filter after join projection");
        }

        @Override
        public <R> QusaqStream<R> select(QuerySpec<T, R> mapper) {
            throw new UnsupportedOperationException("Cannot project after join projection");
        }

        @Override
        public <K extends Comparable<K>> QusaqStream<T> sortedBy(QuerySpec<T, K> keyExtractor) {
            throw new UnsupportedOperationException("Cannot sort after join projection");
        }

        @Override
        public <K extends Comparable<K>> QusaqStream<T> sortedDescendingBy(QuerySpec<T, K> keyExtractor) {
            throw new UnsupportedOperationException("Cannot sort after join projection");
        }

        @Override
        public QusaqStream<T> skip(int n) {
            return new ListProjectionQusaqStream<>(results.subList(Math.min(n, results.size()), results.size()));
        }

        @Override
        public QusaqStream<T> limit(int n) {
            return new ListProjectionQusaqStream<>(results.subList(0, Math.min(n, results.size())));
        }

        @Override
        public QusaqStream<T> distinct() {
            return new ListProjectionQusaqStream<>(results.stream().distinct().toList());
        }

        @Override
        public long count() {
            return results.size();
        }

        @Override
        public <K extends Comparable<K>> QusaqStream<K> min(QuerySpec<T, K> mapper) {
            throw new UnsupportedOperationException("Cannot aggregate after join projection");
        }

        @Override
        public <K extends Comparable<K>> QusaqStream<K> max(QuerySpec<T, K> mapper) {
            throw new UnsupportedOperationException("Cannot aggregate after join projection");
        }

        @Override
        public QusaqStream<Long> sumInteger(QuerySpec<T, Integer> mapper) {
            throw new UnsupportedOperationException("Cannot aggregate after join projection");
        }

        @Override
        public QusaqStream<Long> sumLong(QuerySpec<T, Long> mapper) {
            throw new UnsupportedOperationException("Cannot aggregate after join projection");
        }

        @Override
        public QusaqStream<Double> sumDouble(QuerySpec<T, Double> mapper) {
            throw new UnsupportedOperationException("Cannot aggregate after join projection");
        }

        @Override
        public QusaqStream<Double> avg(QuerySpec<T, ? extends Number> mapper) {
            throw new UnsupportedOperationException("Cannot aggregate after join projection");
        }

        @Override
        public List<T> toList() {
            return new ArrayList<>(results);
        }

        @Override
        public T getSingleResult() {
            if (results.isEmpty()) {
                throw new NoResultException("getSingleResult() expected exactly one result but found none");
            }
            if (results.size() > 1) {
                throw new NonUniqueResultException("getSingleResult() expected exactly one result but found " + results.size());
            }
            return results.get(0);
        }

        @Override
        public Optional<T> findFirst() {
            return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
        }

        @Override
        public boolean exists() {
            return !results.isEmpty();
        }

        @Override
        public <R> JoinStream<T, R> join(QuerySpec<T, java.util.Collection<R>> relationship) {
            throw new UnsupportedOperationException("Cannot join after join projection");
        }

        @Override
        public <R> JoinStream<T, R> leftJoin(QuerySpec<T, java.util.Collection<R>> relationship) {
            throw new UnsupportedOperationException("Cannot join after join projection");
        }

        @Override
        public <K> GroupStream<T, K> groupBy(QuerySpec<T, K> keyExtractor) {
            throw new UnsupportedOperationException("Cannot group after join projection");
        }
    }

    /**
     * Simple QusaqStream implementation wrapping results from selectJoined().
     * Used for returning joined entity results after selectJoined() projection.
     */
    private static class ListJoinedQusaqStream<T> implements QusaqStream<T> {
        private final List<T> results;

        ListJoinedQusaqStream(List<T> results) {
            this.results = results;
        }

        @Override
        public QusaqStream<T> where(QuerySpec<T, Boolean> predicate) {
            throw new UnsupportedOperationException("Cannot filter after selectJoined projection");
        }

        @Override
        public <R> QusaqStream<R> select(QuerySpec<T, R> mapper) {
            throw new UnsupportedOperationException("Cannot project after selectJoined projection");
        }

        @Override
        public <K extends Comparable<K>> QusaqStream<T> sortedBy(QuerySpec<T, K> keyExtractor) {
            throw new UnsupportedOperationException("Cannot sort after selectJoined projection");
        }

        @Override
        public <K extends Comparable<K>> QusaqStream<T> sortedDescendingBy(QuerySpec<T, K> keyExtractor) {
            throw new UnsupportedOperationException("Cannot sort after selectJoined projection");
        }

        @Override
        public QusaqStream<T> skip(int n) {
            return new ListJoinedQusaqStream<>(results.subList(Math.min(n, results.size()), results.size()));
        }

        @Override
        public QusaqStream<T> limit(int n) {
            return new ListJoinedQusaqStream<>(results.subList(0, Math.min(n, results.size())));
        }

        @Override
        public QusaqStream<T> distinct() {
            return new ListJoinedQusaqStream<>(results.stream().distinct().toList());
        }

        @Override
        public long count() {
            return results.size();
        }

        @Override
        public <K extends Comparable<K>> QusaqStream<K> min(QuerySpec<T, K> mapper) {
            throw new UnsupportedOperationException("Cannot aggregate after selectJoined projection");
        }

        @Override
        public <K extends Comparable<K>> QusaqStream<K> max(QuerySpec<T, K> mapper) {
            throw new UnsupportedOperationException("Cannot aggregate after selectJoined projection");
        }

        @Override
        public QusaqStream<Long> sumInteger(QuerySpec<T, Integer> mapper) {
            throw new UnsupportedOperationException("Cannot aggregate after selectJoined projection");
        }

        @Override
        public QusaqStream<Long> sumLong(QuerySpec<T, Long> mapper) {
            throw new UnsupportedOperationException("Cannot aggregate after selectJoined projection");
        }

        @Override
        public QusaqStream<Double> sumDouble(QuerySpec<T, Double> mapper) {
            throw new UnsupportedOperationException("Cannot aggregate after selectJoined projection");
        }

        @Override
        public QusaqStream<Double> avg(QuerySpec<T, ? extends Number> mapper) {
            throw new UnsupportedOperationException("Cannot aggregate after selectJoined projection");
        }

        @Override
        public List<T> toList() {
            return new ArrayList<>(results);
        }

        @Override
        public T getSingleResult() {
            if (results.isEmpty()) {
                throw new NoResultException("getSingleResult() expected exactly one result but found none");
            }
            if (results.size() > 1) {
                throw new NonUniqueResultException("getSingleResult() expected exactly one result but found " + results.size());
            }
            return results.get(0);
        }

        @Override
        public Optional<T> findFirst() {
            return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
        }

        @Override
        public boolean exists() {
            return !results.isEmpty();
        }

        @Override
        public <R> JoinStream<T, R> join(QuerySpec<T, java.util.Collection<R>> relationship) {
            throw new UnsupportedOperationException("Cannot join after selectJoined projection");
        }

        @Override
        public <R> JoinStream<T, R> leftJoin(QuerySpec<T, java.util.Collection<R>> relationship) {
            throw new UnsupportedOperationException("Cannot join after selectJoined projection");
        }

        @Override
        public <K> GroupStream<T, K> groupBy(QuerySpec<T, K> keyExtractor) {
            throw new UnsupportedOperationException("Cannot group after selectJoined projection");
        }
    }

    // =============================================================================================
    // GETTERS FOR BUILD-TIME ANALYSIS
    // =============================================================================================

    public Class<T> getSourceEntityClass() {
        return sourceEntityClass;
    }

    public Class<R> getJoinedEntityClass() {
        return joinedEntityClass;
    }

    public QuerySpec<T, ?> getRelationshipAccessor() {
        return relationshipAccessor;
    }

    public JoinType getJoinType() {
        return joinType;
    }

    public List<BiQuerySpec<T, R, Boolean>> getOnConditions() {
        return onConditions;
    }

    public List<BiQuerySpec<T, R, Boolean>> getBiPredicates() {
        return biPredicates;
    }

    public List<QuerySpec<T, Boolean>> getSourcePredicates() {
        return sourcePredicates;
    }
}
