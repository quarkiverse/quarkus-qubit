package io.quarkiverse.qubit.runtime.internal;

import static io.quarkiverse.qubit.runtime.internal.LambdaReflectionUtils.extractFromLambdas;
import static io.quarkiverse.qubit.runtime.internal.LambdaReflectionUtils.getCallSiteId;
import static io.quarkiverse.qubit.runtime.internal.LambdaReflectionUtils.getQueryExecutorRegistry;
import static io.quarkiverse.qubit.runtime.internal.LambdaReflectionUtils.requireNonNullLambda;
import static io.quarkiverse.qubit.runtime.internal.LambdaReflectionUtils.requireSingleResult;
import static io.quarkiverse.qubit.runtime.internal.LambdaReflectionUtils.validateLimitCount;
import static io.quarkiverse.qubit.runtime.internal.LambdaReflectionUtils.validateSkipCount;

import io.quarkiverse.qubit.BiQuerySpec;
import io.quarkiverse.qubit.JoinStream;
import io.quarkiverse.qubit.JoinType;
import io.quarkiverse.qubit.QuerySpec;
import io.quarkiverse.qubit.QubitStream;
import io.quarkiverse.qubit.SortDirection;

import java.util.ArrayList;
import java.util.Collection;
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

    // ========== State Fields ==========

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
     * Returns a collection-valued relationship (e.g., {@code p -> p.phones}).
     */
    private final QuerySpec<T, Collection<R>> relationshipAccessor;

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

    // ========== Constructors ==========

    /**
     * Creates a new JoinStream for the given source entity and relationship.
     */
    public JoinStreamImpl(
            Class<T> sourceEntityClass,
            Class<R> joinedEntityClass,
            QuerySpec<T, Collection<R>> relationshipAccessor,
            JoinType joinType) {
        this(sourceEntityClass, joinedEntityClass, relationshipAccessor, joinType,
             new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>(),
             null, null, false);
    }

    /**
     * Internal constructor for creating derived streams.
     * <p>
     * <b>Issue #19 Fix (Thread Safety):</b> Defensive copies are made of mutable
     * List parameters to prevent unsafe publication. While the current derivation
     * methods always create new lists, this defensive copying ensures the class
     * remains safe even if future code changes introduce shared references.
     */
    private JoinStreamImpl(
            Class<T> sourceEntityClass,
            Class<R> joinedEntityClass,
            QuerySpec<T, Collection<R>> relationshipAccessor,
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
        this.onConditions = List.copyOf(onConditions);
        this.biPredicates = List.copyOf(biPredicates);
        this.sourcePredicates = List.copyOf(sourcePredicates);
        this.sortOrders = List.copyOf(sortOrders);
        this.offset = offset;
        this.limit = limit;
        this.distinct = distinct;
    }

    // ========== Join Conditions ==========

    @Override
    public JoinStream<T, R> on(BiQuerySpec<T, R, Boolean> condition) {
        requireNonNullLambda(condition, "Condition", "on");
        List<BiQuerySpec<T, R, Boolean>> newOnConditions = new ArrayList<>(this.onConditions);
        newOnConditions.add(condition);
        return new JoinStreamImpl<>(sourceEntityClass, joinedEntityClass, relationshipAccessor, joinType,
                newOnConditions, biPredicates, sourcePredicates, sortOrders, offset, limit, distinct);
    }

    // ========== Filtering ==========

    @Override
    public JoinStream<T, R> where(BiQuerySpec<T, R, Boolean> predicate) {
        requireNonNullLambda(predicate, "Predicate", "where");
        List<BiQuerySpec<T, R, Boolean>> newBiPredicates = new ArrayList<>(this.biPredicates);
        newBiPredicates.add(predicate);
        return new JoinStreamImpl<>(sourceEntityClass, joinedEntityClass, relationshipAccessor, joinType,
                onConditions, newBiPredicates, sourcePredicates, sortOrders, offset, limit, distinct);
    }

    @Override
    public JoinStream<T, R> where(QuerySpec<T, Boolean> predicate) {
        requireNonNullLambda(predicate, "Predicate", "where");
        List<QuerySpec<T, Boolean>> newSourcePredicates = new ArrayList<>(this.sourcePredicates);
        newSourcePredicates.add(predicate);
        return new JoinStreamImpl<>(sourceEntityClass, joinedEntityClass, relationshipAccessor, joinType,
                onConditions, biPredicates, newSourcePredicates, sortOrders, offset, limit, distinct);
    }

    // ========== Projection ==========

    @Override
    public <S> QubitStream<S> select(BiQuerySpec<T, R, S> mapper) {
        requireNonNullLambda(mapper, "Mapper", "select");
        // Execute join projection query and return wrapped results
        String callSiteId = getCallSiteId(QubitConstants.JOIN_METHODS, getPrimaryLambda());
        Object[] capturedValues = extractCapturedVariables();

        QueryExecutorRegistry registry = getQueryExecutorRegistry();
        List<S> results = registry.executeJoinProjectionQuery(callSiteId, sourceEntityClass, capturedValues, offset, limit, distinct);
        return new ImmutableResultStream<>(results, "join projection");
    }

    @Override
    public QubitStream<T> selectSource() {
        // Execute join query and return source entities wrapped in ImmutableResultStream
        // Issue #22 Fix: Previously this created an empty QubitStreamImpl, losing all
        // join context (ON conditions, WHERE predicates, sort orders, pagination).
        // Now correctly delegates to toList() which executes the join query via registry.
        List<T> results = toList();
        return new ImmutableResultStream<>(results, "selectSource projection");
    }

    @Override
    public QubitStream<R> selectJoined() {
        // Execute query selecting joined entities and return wrapped results
        String callSiteId = getCallSiteId(QubitConstants.JOIN_METHODS, getPrimaryLambda());
        Object[] capturedValues = extractCapturedVariables();

        QueryExecutorRegistry registry = getQueryExecutorRegistry();
        List<R> results = registry.executeJoinSelectJoinedQuery(callSiteId, sourceEntityClass, capturedValues, offset, limit, distinct);
        return new ImmutableResultStream<>(results, "selectJoined projection");
    }

    // ========== Sorting ==========

    @Override
    public <K extends Comparable<K>> JoinStream<T, R> sortedBy(BiQuerySpec<T, R, K> keyExtractor) {
        requireNonNullLambda(keyExtractor, "Key extractor", "sortedBy");
        List<BiSortOrder<T, R>> newSortOrders = new ArrayList<>(this.sortOrders);
        newSortOrders.add(0, new BiSortOrder<>(keyExtractor, SortDirection.ASCENDING));
        return new JoinStreamImpl<>(sourceEntityClass, joinedEntityClass, relationshipAccessor, joinType,
                onConditions, biPredicates, sourcePredicates, newSortOrders, offset, limit, distinct);
    }

    @Override
    public <K extends Comparable<K>> JoinStream<T, R> sortedDescendingBy(BiQuerySpec<T, R, K> keyExtractor) {
        requireNonNullLambda(keyExtractor, "Key extractor", "sortedDescendingBy");
        List<BiSortOrder<T, R>> newSortOrders = new ArrayList<>(this.sortOrders);
        newSortOrders.add(0, new BiSortOrder<>(keyExtractor, SortDirection.DESCENDING));
        return new JoinStreamImpl<>(sourceEntityClass, joinedEntityClass, relationshipAccessor, joinType,
                onConditions, biPredicates, sourcePredicates, newSortOrders, offset, limit, distinct);
    }

    // ========== Pagination ==========

    @Override
    public JoinStream<T, R> skip(int n) {
        return new JoinStreamImpl<>(sourceEntityClass, joinedEntityClass, relationshipAccessor, joinType,
                onConditions, biPredicates, sourcePredicates, sortOrders, validateSkipCount(n), limit, distinct);
    }

    @Override
    public JoinStream<T, R> limit(int n) {
        return new JoinStreamImpl<>(sourceEntityClass, joinedEntityClass, relationshipAccessor, joinType,
                onConditions, biPredicates, sourcePredicates, sortOrders, offset, validateLimitCount(n), distinct);
    }

    // ========== Distinct ==========

    @Override
    public JoinStream<T, R> distinct() {
        return new JoinStreamImpl<>(sourceEntityClass, joinedEntityClass, relationshipAccessor, joinType,
                onConditions, biPredicates, sourcePredicates, sortOrders, offset, limit, true);
    }

    // ========== Terminal Operations ==========

    @Override
    public List<T> toList() {
        // Delegate to build-time generated executor via registry
        String callSiteId = getCallSiteId(QubitConstants.JOIN_METHODS, getPrimaryLambda());
        Object[] capturedValues = extractCapturedVariables();

        QueryExecutorRegistry registry = getQueryExecutorRegistry();

        // Execute as join query - registry will need to handle this specially
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
        return count() > 0;
    }

    // ========== Internal Helper Methods ==========

    /**
     * Returns the primary lambda for call site ID uniqueness.
     * <p>
     * Priority order (matching build-time InvokeDynamicScanner.getPrimaryLambdaMethodName):
     * <ol>
     *   <li>First source predicate (single-entity WHERE on source)</li>
     *   <li>First bi-entity predicate (bi-entity WHERE on both)</li>
     *   <li>Relationship accessor (join relationship)</li>
     *   <li>First ON condition</li>
     * </ol>
     *
     * @return the primary lambda, or null if no lambdas are present
     */
    private Object getPrimaryLambda() {
        // Source predicate first (matches build-time predicateLambdas priority)
        if (!sourcePredicates.isEmpty()) {
            return sourcePredicates.getFirst();
        }
        // Bi-entity predicate (matches build-time biEntityPredicateLambdas)
        if (!biPredicates.isEmpty()) {
            return biPredicates.getFirst();
        }
        // Relationship accessor (always present for joins)
        if (relationshipAccessor != null) {
            return relationshipAccessor;
        }
        // ON condition
        if (!onConditions.isEmpty()) {
            return onConditions.getFirst();
        }
        return null;
    }

    /**
     * Extracts captured variables from all lambdas in the join stream.
     * Supports bi-entity predicates, source predicates, and ON conditions.
     * Variables are extracted in the order they appear and combined into a single array.
     */
    private Object[] extractCapturedVariables() {
        String callSiteId = getCallSiteId(QubitConstants.JOIN_METHODS, getPrimaryLambda());
        int capturedCount = QueryExecutorRegistry.getCapturedVariableCount(callSiteId);

        if (capturedCount == 0) {
            return new Object[0];
        }

        // Extract from all lambda sources: bi-predicates, source predicates, ON conditions
        List<Object> allCapturedValues = new ArrayList<>();
        int remainingCount = capturedCount;

        // Extract from all lambda sources using helper method
        remainingCount = extractFromLambdas(biPredicates, "bi-predicate", callSiteId,
                allCapturedValues, remainingCount);
        remainingCount = extractFromLambdas(sourcePredicates, "source-predicate", callSiteId,
                allCapturedValues, remainingCount);
        remainingCount = extractFromLambdas(onConditions, "ON-condition", callSiteId,
                allCapturedValues, remainingCount);

        if (remainingCount != 0) {
            throw new IllegalStateException(
                    String.format("Captured variable count mismatch at %s: expected %d, found %d",
                            callSiteId, capturedCount, capturedCount - remainingCount));
        }

        return allCapturedValues.toArray(new Object[0]);
    }

    // ========== Internal Classes ==========

    /**
     * Represents a bi-entity sort order specification.
     */
    private record BiSortOrder<T, R>(BiQuerySpec<T, R, ?> keyExtractor, SortDirection direction) {
    }

    // ========== Getters for Build-Time Analysis ==========

    public Class<T> getSourceEntityClass() {
        return sourceEntityClass;
    }

    public Class<R> getJoinedEntityClass() {
        return joinedEntityClass;
    }

    public QuerySpec<T, Collection<R>> getRelationshipAccessor() {
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
