package io.quarkiverse.qubit.deployment.generation;

import static io.quarkiverse.qubit.runtime.QubitConstants.CB_COUNT;
import static io.quarkiverse.qubit.runtime.QubitConstants.CONSTRUCTOR;
import static io.quarkiverse.qubit.runtime.QubitConstants.CQ_FROM;
import static io.quarkiverse.qubit.runtime.QubitConstants.CQ_SELECT;
import static io.quarkiverse.qubit.runtime.QubitConstants.CQ_WHERE;
import static io.quarkiverse.qubit.runtime.QubitConstants.EM_CREATE_QUERY;
import static io.quarkiverse.qubit.runtime.QubitConstants.EM_GET_CRITERIA_BUILDER;
import static io.quarkiverse.qubit.runtime.QubitConstants.QE_EXECUTE;
import static io.quarkiverse.qubit.runtime.QubitConstants.QUERY_EXECUTOR_CLASS_NAME;
import static io.quarkiverse.qubit.runtime.QubitConstants.TQ_GET_RESULT_LIST;
import static io.quarkiverse.qubit.runtime.QubitConstants.TQ_GET_SINGLE_RESULT;
import static io.quarkiverse.qubit.deployment.generation.MethodDescriptors.BOOLEAN_BOOLEAN_VALUE;
import static io.quarkiverse.qubit.deployment.generation.MethodDescriptors.INTEGER_INT_VALUE;

import java.util.List;

import io.quarkus.gizmo.BranchResult;
import io.quarkus.gizmo.BytecodeCreator;
import io.quarkus.gizmo.ClassCreator;
import io.quarkus.gizmo.MethodCreator;
import io.quarkus.gizmo.MethodDescriptor;
import io.quarkus.gizmo.ResultHandle;
import io.quarkiverse.qubit.deployment.analysis.InvokeDynamicScanner;
import io.quarkiverse.qubit.deployment.analysis.LambdaAnalysisResult.SortExpression;
import io.quarkiverse.qubit.deployment.ast.LambdaExpression;
import io.quarkiverse.qubit.deployment.util.ByteArrayClassOutput;
import io.quarkiverse.qubit.runtime.SortDirection;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.From;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Selection;

/**
 * Generates query executor classes that execute JPA Criteria API queries from lambda expressions.
 */
public class QueryExecutorClassGenerator {

    private final CriteriaExpressionGenerator expressionGenerator = new CriteriaExpressionGenerator();

    /**
     * Creates MethodDescriptor for method.
     */
private static MethodDescriptor md(Class<?> clazz, String methodName, Class<?> returnType, Class<?>... params) {
        return MethodDescriptor.ofMethod(clazz, methodName, returnType, params);
    }

    // Method descriptors for ORDER BY generation
    private static final MethodDescriptor CB_ASC = md(CriteriaBuilder.class, "asc",
            Order.class, Expression.class);
    private static final MethodDescriptor CB_DESC = md(CriteriaBuilder.class, "desc",
            Order.class, Expression.class);
    private static final MethodDescriptor CQ_ORDER_BY = md(CriteriaQuery.class, "orderBy",
            CriteriaQuery.class, Order[].class);

    // Method descriptors for pagination
    private static final MethodDescriptor TQ_SET_FIRST_RESULT = md(TypedQuery.class, "setFirstResult",
            TypedQuery.class, int.class);
    private static final MethodDescriptor TQ_SET_MAX_RESULTS = md(TypedQuery.class, "setMaxResults",
            TypedQuery.class, int.class);

    // Method descriptor for DISTINCT
    private static final MethodDescriptor CQ_DISTINCT = md(CriteriaQuery.class, "distinct",
            CriteriaQuery.class, boolean.class);

    // Method descriptors for aggregation functions
    private static final MethodDescriptor CB_MIN = md(CriteriaBuilder.class, "min",
            Expression.class, Expression.class);
    private static final MethodDescriptor CB_MAX = md(CriteriaBuilder.class, "max",
            Expression.class, Expression.class);
    private static final MethodDescriptor CB_AVG = md(CriteriaBuilder.class, "avg",
            Expression.class, Expression.class);
    private static final MethodDescriptor CB_SUM_AS_LONG = md(CriteriaBuilder.class, "sumAsLong",
            Expression.class, Expression.class);
    private static final MethodDescriptor CB_SUM_AS_DOUBLE = md(CriteriaBuilder.class, "sumAsDouble",
            Expression.class, Expression.class);

    // Method descriptors for JOIN operations
    private static final MethodDescriptor FROM_JOIN = md(From.class, "join",
            Join.class, String.class, JoinType.class);

    // Method descriptors for GROUP BY operations
    private static final MethodDescriptor CQ_GROUP_BY = md(CriteriaQuery.class, "groupBy",
            CriteriaQuery.class, Expression[].class);
    private static final MethodDescriptor CQ_HAVING = md(CriteriaQuery.class, "having",
            CriteriaQuery.class, Predicate[].class);
    private static final MethodDescriptor CB_COUNT_DISTINCT = md(CriteriaBuilder.class, "countDistinct",
            Expression.class, Expression.class);

    /**
     * Common context for query generation methods.
     * Encapsulates parameters shared across all query generation methods.
     *
     * @param method Gizmo bytecode generator context
     * @param em EntityManager handle
     * @param entityClass Entity class being queried
     * @param sortExpressions ORDER BY expressions (null if none)
     * @param capturedValues Lambda captured variables
     * @param offset OFFSET value for pagination (null if none)
     * @param limit LIMIT value for pagination (null if none)
     * @param distinct DISTINCT flag (null or false for no distinct)
     */
    private record QueryGenContext(
        MethodCreator method,
        ResultHandle em,
        ResultHandle entityClass,
        List<?> sortExpressions,
        ResultHandle capturedValues,
        ResultHandle offset,
        ResultHandle limit,
        ResultHandle distinct
    ) {}

    /**
     * Generates query executor class bytecode from lambda expressions.
     * <p>
     * Accepts predicate, projection, sort, and aggregation expressions to generate
     * combined queries with WHERE, SELECT, ORDER BY, and aggregation operations.
     */
    public byte[] generateQueryExecutorClass(
            LambdaExpression predicateExpression,
            LambdaExpression projectionExpression,
            List<?> sortExpressions,
            LambdaExpression aggregationExpression,
            String aggregationType,
            String className,
            boolean isCountQuery,
            boolean isAggregationQuery) {

        ByteArrayClassOutput classOutput = new ByteArrayClassOutput();

        try (ClassCreator classCreator = ClassCreator.builder()
                .classOutput(classOutput)
                .className(className)
                .interfaces(QUERY_EXECUTOR_CLASS_NAME)
                .build()) {

            try (MethodCreator constructor = classCreator.getMethodCreator(CONSTRUCTOR, void.class)) {
                constructor.invokeSpecialMethod(
                        MethodDescriptor.ofConstructor(Object.class),
                        constructor.getThis());
                constructor.returnValue(null);
            }

            // Execute method signature includes offset, limit, and distinct parameters
            try (MethodCreator execute = classCreator.getMethodCreator(
                    QE_EXECUTE, Object.class, EntityManager.class, Class.class, Object[].class,
                    Integer.class, Integer.class, Boolean.class)) {

                ResultHandle em = execute.getMethodParam(0);
                ResultHandle entityClassParam = execute.getMethodParam(1);
                ResultHandle capturedValues = execute.getMethodParam(2);
                ResultHandle offset = execute.getMethodParam(3);
                ResultHandle limit = execute.getMethodParam(4);
                ResultHandle distinct = execute.getMethodParam(5);

                ResultHandle result;
                if (isAggregationQuery) {
                    // Aggregation queries (min, max, avg, sum*)
                    result = generateAggregationQueryBody(execute, em, entityClassParam, predicateExpression,
                            aggregationExpression, aggregationType, capturedValues);
                } else if (isCountQuery) {
                    // Count queries ignore pagination and distinct parameters
                    result = generateCountQueryBody(execute, em, entityClassParam, predicateExpression, capturedValues);
                } else {
                    QueryGenContext ctx = new QueryGenContext(execute, em, entityClassParam,
                            sortExpressions, capturedValues, offset, limit, distinct);
                    result = generateListQueryBody(ctx, predicateExpression, projectionExpression);
                }

                execute.returnValue(result);
            }
        }

        return classOutput.getData();
    }

    /**
     * Generates query executor class bytecode for JOIN queries.
     * <p>
     * Creates a query executor that performs a JPA join between two related entities
     * and applies bi-entity predicates that reference both the source and joined entity.
     *
     * @param joinRelationshipExpression Lambda for the join relationship (e.g., p -> p.phones)
     * @param biEntityPredicateExpression Lambda for bi-entity predicate (e.g., (p, ph) -> ph.type.equals("mobile"))
     * @param biEntityProjectionExpression BiQuerySpec SELECT projection (e.g., (p, ph) -> new DTO(...))
     * @param sortExpressions List of sort expressions for ORDER BY (null or empty for no sorting)
     * @param joinType The type of join (INNER or LEFT)
     * @param className The generated class name
     * @param isCountQuery True if this is a count query (JoinStream.count())
     * @param isSelectJoined True if selectJoined() was called (returns joined entities)
     * @param isJoinProjection True if select() with BiQuerySpec was called
     * @return The bytecode for the generated QueryExecutor class
     */
    public byte[] generateJoinQueryExecutorClass(
            LambdaExpression joinRelationshipExpression,
            LambdaExpression biEntityPredicateExpression,
            LambdaExpression biEntityProjectionExpression,
            List<SortExpression> sortExpressions,
            InvokeDynamicScanner.JoinType joinType,
            String className,
            boolean isCountQuery,
            boolean isSelectJoined,
            boolean isJoinProjection) {

        ByteArrayClassOutput classOutput = new ByteArrayClassOutput();

        try (ClassCreator classCreator = ClassCreator.builder()
                .classOutput(classOutput)
                .className(className)
                .interfaces(QUERY_EXECUTOR_CLASS_NAME)
                .build()) {

            try (MethodCreator constructor = classCreator.getMethodCreator(CONSTRUCTOR, void.class)) {
                constructor.invokeSpecialMethod(
                        MethodDescriptor.ofConstructor(Object.class),
                        constructor.getThis());
                constructor.returnValue(null);
            }

            // Same execute method signature as regular queries
            try (MethodCreator execute = classCreator.getMethodCreator(
                    QE_EXECUTE, Object.class, EntityManager.class, Class.class, Object[].class,
                    Integer.class, Integer.class, Boolean.class)) {

                ResultHandle em = execute.getMethodParam(0);
                ResultHandle entityClassParam = execute.getMethodParam(1);
                ResultHandle capturedValues = execute.getMethodParam(2);
                ResultHandle offset = execute.getMethodParam(3);
                ResultHandle limit = execute.getMethodParam(4);
                ResultHandle distinct = execute.getMethodParam(5);

                ResultHandle result;
                if (isCountQuery) {
                    result = generateJoinCountQueryBody(
                            execute, em, entityClassParam,
                            joinRelationshipExpression, biEntityPredicateExpression,
                            joinType, capturedValues);
                } else if (isJoinProjection) {
                    // select() with BiQuerySpec returns projected results
                    result = generateJoinProjectionQueryBody(
                            execute, em, entityClassParam,
                            joinRelationshipExpression, biEntityPredicateExpression,
                            biEntityProjectionExpression,
                            joinType, sortExpressions, capturedValues, offset, limit, distinct);
                } else if (isSelectJoined) {
                    // selectJoined() returns joined entities instead of source entities
                    result = generateJoinSelectJoinedQueryBody(
                            execute, em, entityClassParam,
                            joinRelationshipExpression, biEntityPredicateExpression,
                            joinType, sortExpressions, capturedValues, offset, limit, distinct);
                } else {
                    result = generateJoinQueryBody(
                            execute, em, entityClassParam,
                            joinRelationshipExpression, biEntityPredicateExpression,
                            joinType, sortExpressions, capturedValues, offset, limit, distinct);
                }

                execute.returnValue(result);
            }
        }

        return classOutput.getData();
    }

    /**
     * Generates query executor class bytecode for GROUP BY queries.
     * <p>
     * Creates a query executor that performs JPA GROUP BY operations with optional
     * HAVING clause, aggregations in SELECT, and sorting.
     * <p>
     * Example: {@code Person.groupBy(p -> p.department).select(g -> new DeptStats(g.key(), g.count())).toList()}
     * <p>
     * Generates:
     * <pre>
     * CriteriaBuilder cb = em.getCriteriaBuilder();
     * CriteriaQuery&lt;Object&gt; query = cb.createQuery(Object.class);
     * Root&lt;Person&gt; root = query.from(Person.class);
     * Expression&lt;String&gt; groupKey = root.get("department");
     * query.groupBy(groupKey);
     * query.multiselect(groupKey, cb.count(root));
     * return em.createQuery(query).getResultList();
     * </pre>
     *
     * @param predicateExpression Pre-grouping WHERE clause (null if no filtering)
     * @param groupByKeyExpression groupBy() key extractor lambda (e.g., p -> p.department)
     * @param havingExpression having() predicate (null if no having)
     * @param groupSelectExpression select() projection in group context (null if no select)
     * @param groupSortExpressions sortedBy() in group context (null or empty if no sorting)
     * @param className The generated class name
     * @param isCountQuery True if this is a count query (counting groups)
     * @return The bytecode for the generated QueryExecutor class
     */
    public byte[] generateGroupQueryExecutorClass(
            LambdaExpression predicateExpression,
            LambdaExpression groupByKeyExpression,
            LambdaExpression havingExpression,
            LambdaExpression groupSelectExpression,
            List<SortExpression> groupSortExpressions,
            String className,
            boolean isCountQuery) {

        ByteArrayClassOutput classOutput = new ByteArrayClassOutput();

        try (ClassCreator classCreator = ClassCreator.builder()
                .classOutput(classOutput)
                .className(className)
                .interfaces(QUERY_EXECUTOR_CLASS_NAME)
                .build()) {

            try (MethodCreator constructor = classCreator.getMethodCreator(CONSTRUCTOR, void.class)) {
                constructor.invokeSpecialMethod(
                        MethodDescriptor.ofConstructor(Object.class),
                        constructor.getThis());
                constructor.returnValue(null);
            }

            // Same execute method signature as regular queries
            try (MethodCreator execute = classCreator.getMethodCreator(
                    QE_EXECUTE, Object.class, EntityManager.class, Class.class, Object[].class,
                    Integer.class, Integer.class, Boolean.class)) {

                ResultHandle em = execute.getMethodParam(0);
                ResultHandle entityClassParam = execute.getMethodParam(1);
                ResultHandle capturedValues = execute.getMethodParam(2);
                ResultHandle offset = execute.getMethodParam(3);
                ResultHandle limit = execute.getMethodParam(4);
                ResultHandle distinct = execute.getMethodParam(5);

                ResultHandle result;
                if (isCountQuery) {
                    result = generateGroupCountQueryBody(
                            execute, em, entityClassParam,
                            predicateExpression, groupByKeyExpression,
                            havingExpression, capturedValues);
                } else {
                    result = generateGroupQueryBody(
                            execute, em, entityClassParam,
                            predicateExpression, groupByKeyExpression,
                            havingExpression, groupSelectExpression,
                            groupSortExpressions, capturedValues, offset, limit, distinct);
                }

                execute.returnValue(result);
            }
        }

        return classOutput.getData();
    }

    /**
     * Generates GROUP BY query body.
     * <p>
     * Creates a JPA Criteria API group by query with optional WHERE, HAVING,
     * SELECT (with aggregations), and ORDER BY clauses.
     */
    private ResultHandle generateGroupQueryBody(
            MethodCreator method,
            ResultHandle em,
            ResultHandle entityClass,
            LambdaExpression predicateExpression,
            LambdaExpression groupByKeyExpression,
            LambdaExpression havingExpression,
            LambdaExpression groupSelectExpression,
            List<SortExpression> groupSortExpressions,
            ResultHandle capturedValues,
            ResultHandle offset,
            ResultHandle limit,
            ResultHandle distinct) {

        // Get CriteriaBuilder
        ResultHandle cb = method.invokeInterfaceMethod(
                md(EntityManager.class, EM_GET_CRITERIA_BUILDER, CriteriaBuilder.class), em);

        // Use Object.class for group queries (multi-select or tuple results)
        ResultHandle objectClass = method.loadClass(Object.class);

        // Create CriteriaQuery<Object>
        ResultHandle query = method.invokeInterfaceMethod(
                md(CriteriaBuilder.class, EM_CREATE_QUERY, CriteriaQuery.class, Class.class),
                cb, objectClass);

        // Create Root<Entity>
        ResultHandle root = method.invokeInterfaceMethod(
                md(CriteriaQuery.class, CQ_FROM, Root.class, Class.class), query, entityClass);

        // Apply pre-grouping WHERE predicate if present (with subquery support)
        if (predicateExpression != null) {
            ResultHandle predicate = expressionGenerator.generatePredicateWithSubqueries(
                    method, predicateExpression, cb, query, root, capturedValues);
            applyWherePredicate(method, query, predicate);
        }

        // Generate GROUP BY key expression
        ResultHandle groupKeyExpr = expressionGenerator.generateExpression(
                method, groupByKeyExpression, cb, root, capturedValues);

        // Apply GROUP BY
        ResultHandle groupByArray = method.newArray(Expression.class, 1);
        method.writeArrayValue(groupByArray, 0, groupKeyExpr);
        method.invokeInterfaceMethod(CQ_GROUP_BY, query, groupByArray);

        // Apply HAVING predicate if present
        if (havingExpression != null) {
            ResultHandle havingPredicate = expressionGenerator.generateGroupPredicate(
                    method, havingExpression, cb, root, groupKeyExpr, capturedValues);
            applyHavingPredicate(method, query, havingPredicate);
        }

        // Apply SELECT projection if present
        if (groupSelectExpression != null) {
            ResultHandle selection = expressionGenerator.generateGroupSelectExpression(
                    method, groupSelectExpression, cb, root, groupKeyExpr, capturedValues);
            method.invokeInterfaceMethod(
                    md(CriteriaQuery.class, CQ_SELECT, CriteriaQuery.class, Selection.class),
                    query, selection);
        } else {
            // Default: SELECT the grouping key
            method.invokeInterfaceMethod(
                    md(CriteriaQuery.class, CQ_SELECT, CriteriaQuery.class, Selection.class),
                    query, groupKeyExpr);
        }

        // Apply ORDER BY for group queries
        applyGroupOrderBy(method, query, root, groupKeyExpr, cb, groupSortExpressions, capturedValues);

        // Apply DISTINCT if requested
        applyDistinct(method, query, distinct);

        // Create TypedQuery
        ResultHandle typedQuery = method.invokeInterfaceMethod(
                md(EntityManager.class, EM_CREATE_QUERY, TypedQuery.class, CriteriaQuery.class),
                em, query);

        // Apply pagination
        applyPagination(method, typedQuery, offset, limit);

        // Return getResultList()
        return method.invokeInterfaceMethod(
                md(TypedQuery.class, TQ_GET_RESULT_LIST, List.class), typedQuery);
    }

    /**
     * Generates GROUP BY COUNT query body.
     * <p>
     * Counts the number of groups (not the total entities).
     * Uses a subquery or COUNT(DISTINCT groupKey) pattern.
     */
    private ResultHandle generateGroupCountQueryBody(
            MethodCreator method,
            ResultHandle em,
            ResultHandle entityClass,
            LambdaExpression predicateExpression,
            LambdaExpression groupByKeyExpression,
            LambdaExpression havingExpression,
            ResultHandle capturedValues) {

        // Get CriteriaBuilder
        ResultHandle cb = method.invokeInterfaceMethod(
                md(EntityManager.class, EM_GET_CRITERIA_BUILDER, CriteriaBuilder.class), em);

        // For counting groups, we use different strategies based on whether HAVING is present:
        // - Without HAVING: SELECT COUNT(DISTINCT groupKey) - simpler, returns Long
        // - With HAVING: Need to return group keys and count them at runtime
        if (havingExpression != null) {
            // With HAVING: Create query for Object (group key type may vary)
            ResultHandle objectClass = method.loadClass(Object.class);
            ResultHandle query = method.invokeInterfaceMethod(
                    md(CriteriaBuilder.class, EM_CREATE_QUERY, CriteriaQuery.class, Class.class),
                    cb, objectClass);

            // Create Root<Entity>
            ResultHandle root = method.invokeInterfaceMethod(
                    md(CriteriaQuery.class, CQ_FROM, Root.class, Class.class), query, entityClass);

            // Apply pre-grouping WHERE predicate if present (with subquery support)
            if (predicateExpression != null) {
                ResultHandle predicate = expressionGenerator.generatePredicateWithSubqueries(
                        method, predicateExpression, cb, query, root, capturedValues);
                applyWherePredicate(method, query, predicate);
            }

            // Generate GROUP BY key expression
            ResultHandle groupKeyExpr = expressionGenerator.generateExpression(
                    method, groupByKeyExpression, cb, root, capturedValues);

            // Apply GROUP BY
            ResultHandle groupByArray = method.newArray(Expression.class, 1);
            method.writeArrayValue(groupByArray, 0, groupKeyExpr);
            method.invokeInterfaceMethod(CQ_GROUP_BY, query, groupByArray);

            // Apply HAVING predicate
            ResultHandle havingPredicate = expressionGenerator.generateGroupPredicate(
                    method, havingExpression, cb, root, groupKeyExpr, capturedValues);
            applyHavingPredicate(method, query, havingPredicate);

            // SELECT groupKey (we'll count results at runtime)
            method.invokeInterfaceMethod(
                    md(CriteriaQuery.class, CQ_SELECT, CriteriaQuery.class, Selection.class),
                    query, groupKeyExpr);

            // Create TypedQuery
            ResultHandle typedQuery = method.invokeInterfaceMethod(
                    md(EntityManager.class, EM_CREATE_QUERY, TypedQuery.class, CriteriaQuery.class),
                    em, query);

            // Get result list and return its size as Long
            ResultHandle resultList = method.invokeInterfaceMethod(
                    md(TypedQuery.class, TQ_GET_RESULT_LIST, List.class), typedQuery);
            ResultHandle size = method.invokeInterfaceMethod(
                    md(List.class, "size", int.class), resultList);
            // Convert int to long using explicit widening cast
            ResultHandle sizeLong = method.invokeVirtualMethod(
                    md(Integer.class, "longValue", long.class),
                    method.invokeStaticMethod(md(Integer.class, "valueOf", Integer.class, int.class), size));
            return method.invokeStaticMethod(
                    md(Long.class, "valueOf", Long.class, long.class), sizeLong);
        } else {
            // Without HAVING: Create query for Long (count result)
            ResultHandle longClass = method.loadClass(Long.class);
            ResultHandle query = method.invokeInterfaceMethod(
                    md(CriteriaBuilder.class, EM_CREATE_QUERY, CriteriaQuery.class, Class.class),
                    cb, longClass);

            // Create Root<Entity>
            ResultHandle root = method.invokeInterfaceMethod(
                    md(CriteriaQuery.class, CQ_FROM, Root.class, Class.class), query, entityClass);

            // Apply pre-grouping WHERE predicate if present (with subquery support)
            if (predicateExpression != null) {
                ResultHandle predicate = expressionGenerator.generatePredicateWithSubqueries(
                        method, predicateExpression, cb, query, root, capturedValues);
                applyWherePredicate(method, query, predicate);
            }

            // Generate GROUP BY key expression
            ResultHandle groupKeyExpr = expressionGenerator.generateExpression(
                    method, groupByKeyExpression, cb, root, capturedValues);

            // Simple COUNT(DISTINCT groupKey), no GROUP BY needed
            ResultHandle countExpr = method.invokeInterfaceMethod(CB_COUNT_DISTINCT, cb, groupKeyExpr);
            method.invokeInterfaceMethod(
                    md(CriteriaQuery.class, CQ_SELECT, CriteriaQuery.class, Selection.class),
                    query, countExpr);

            // Create TypedQuery
            ResultHandle typedQuery = method.invokeInterfaceMethod(
                    md(EntityManager.class, EM_CREATE_QUERY, TypedQuery.class, CriteriaQuery.class),
                    em, query);

            // Return getSingleResult()
            return method.invokeInterfaceMethod(
                    md(TypedQuery.class, TQ_GET_SINGLE_RESULT, Object.class), typedQuery);
        }
    }

    /**
     * Applies HAVING clause predicate to CriteriaQuery.
     */
    private void applyHavingPredicate(MethodCreator method, ResultHandle query, ResultHandle predicate) {
        if (predicate != null) {
            ResultHandle predicateArray = method.newArray(Predicate.class, 1);
            method.writeArrayValue(predicateArray, 0, predicate);
            method.invokeInterfaceMethod(CQ_HAVING, query, predicateArray);
        }
    }

    /**
     * Applies ORDER BY clause for GROUP BY queries.
     */
    private void applyGroupOrderBy(
            MethodCreator method,
            ResultHandle query,
            ResultHandle root,
            ResultHandle groupKeyExpr,
            ResultHandle cb,
            List<?> sortExpressions,
            ResultHandle capturedValues) {

        if (sortExpressions == null || sortExpressions.isEmpty()) {
            return; // No sorting
        }

        // Create array to hold Order objects
        ResultHandle ordersArray = method.newArray(Order.class, sortExpressions.size());

        // Generate Order objects in REVERSE order for "last call wins" semantics
        for (int i = 0; i < sortExpressions.size(); i++) {
            int reverseIndex = sortExpressions.size() - 1 - i;
            Object sortExprObj = sortExpressions.get(reverseIndex);

            if (sortExprObj instanceof SortExpression sortExpr) {
                // Generate JPA Expression for the group sort key extractor
                ResultHandle sortKeyExpr = expressionGenerator.generateGroupSortExpression(
                        method, sortExpr.keyExtractor(), cb, root, groupKeyExpr, capturedValues);

                // Create Order object (ascending or descending)
                ResultHandle order;
                if (sortExpr.direction() == SortDirection.DESCENDING) {
                    order = method.invokeInterfaceMethod(CB_DESC, cb, sortKeyExpr);
                } else {
                    order = method.invokeInterfaceMethod(CB_ASC, cb, sortKeyExpr);
                }

                // Add to orders array at position i (forward order)
                method.writeArrayValue(ordersArray, i, order);
            }
        }

        // Apply orderBy to query
        method.invokeInterfaceMethod(CQ_ORDER_BY, query, ordersArray);
    }

    /**
     * Generates LIST query returning em.createQuery(query).getResultList().
     * <p>
     * Handles combined where() + select() queries, ORDER BY sorting, and pagination.
     */
    private ResultHandle generateListQueryBody(
            QueryGenContext ctx,
            LambdaExpression predicateExpression,
            LambdaExpression projectionExpression) {

        // Combined WHERE + SELECT query
        if (predicateExpression != null && projectionExpression != null) {
            return generateCombinedWhereSelectQuery(ctx, predicateExpression, projectionExpression);
        }

        // Projection query (select().toList())
        if (projectionExpression != null) {
            return generateProjectionQuery(ctx, projectionExpression);
        }

        // Predicate query (where().toList()) - with subquery support
        if (predicateExpression != null) {
            ResultHandle cb = ctx.method().invokeInterfaceMethod(md(EntityManager.class, EM_GET_CRITERIA_BUILDER, CriteriaBuilder.class), ctx.em());
            ResultHandle query = ctx.method().invokeInterfaceMethod(md(CriteriaBuilder.class, EM_CREATE_QUERY, CriteriaQuery.class, Class.class), cb, ctx.entityClass());
            ResultHandle root = ctx.method().invokeInterfaceMethod(md(CriteriaQuery.class, CQ_FROM, Root.class, Class.class), query, ctx.entityClass());
            ResultHandle predicate = expressionGenerator.generatePredicateWithSubqueries(ctx.method(), predicateExpression, cb, query, root, ctx.capturedValues());
            applyWherePredicate(ctx.method(), query, predicate);
            applyOrderBy(ctx.method(), query, root, cb, ctx.sortExpressions(), ctx.capturedValues(), null);
            applyDistinct(ctx.method(), query, ctx.distinct());
            ResultHandle typedQuery = ctx.method().invokeInterfaceMethod(md(EntityManager.class, EM_CREATE_QUERY, TypedQuery.class, CriteriaQuery.class), ctx.em(), query);
            applyPagination(ctx.method(), typedQuery, ctx.offset(), ctx.limit());
            return ctx.method().invokeInterfaceMethod(md(TypedQuery.class, TQ_GET_RESULT_LIST, List.class), typedQuery);
        }

        // No predicate or projection - return all entities
        ResultHandle cb = ctx.method().invokeInterfaceMethod(md(EntityManager.class, EM_GET_CRITERIA_BUILDER, CriteriaBuilder.class), ctx.em());
        ResultHandle query = ctx.method().invokeInterfaceMethod(md(CriteriaBuilder.class, EM_CREATE_QUERY, CriteriaQuery.class, Class.class), cb, ctx.entityClass());
        ResultHandle root = ctx.method().invokeInterfaceMethod(md(CriteriaQuery.class, CQ_FROM, Root.class, Class.class), query, ctx.entityClass());
        applyOrderBy(ctx.method(), query, root, cb, ctx.sortExpressions(), ctx.capturedValues(), null);
        applyDistinct(ctx.method(), query, ctx.distinct());
        ResultHandle typedQuery = ctx.method().invokeInterfaceMethod(md(EntityManager.class, EM_CREATE_QUERY, TypedQuery.class, CriteriaQuery.class), ctx.em(), query);
        applyPagination(ctx.method(), typedQuery, ctx.offset(), ctx.limit());
        return ctx.method().invokeInterfaceMethod(md(TypedQuery.class, TQ_GET_RESULT_LIST, List.class), typedQuery);
    }

    /**
     * Generates COUNT query returning em.createQuery(query).getSingleResult().
     * Includes subquery support.
     */
    private ResultHandle generateCountQueryBody(
            MethodCreator method,
            ResultHandle em,
            ResultHandle entityClass,
            LambdaExpression expression,
            ResultHandle capturedValues) {

        ResultHandle cb = method.invokeInterfaceMethod(md(EntityManager.class, EM_GET_CRITERIA_BUILDER, CriteriaBuilder.class), em);
        ResultHandle longClass = method.loadClass(Long.class);
        ResultHandle query = method.invokeInterfaceMethod(md(CriteriaBuilder.class, EM_CREATE_QUERY, CriteriaQuery.class, Class.class), cb, longClass);
        ResultHandle root = method.invokeInterfaceMethod(md(CriteriaQuery.class, CQ_FROM, Root.class, Class.class), query, entityClass);
        ResultHandle countExpr = method.invokeInterfaceMethod(md(CriteriaBuilder.class, CB_COUNT, Expression.class, Expression.class), cb, root);
        method.invokeInterfaceMethod(md(CriteriaQuery.class, CQ_SELECT, CriteriaQuery.class, Selection.class), query, countExpr);
        ResultHandle predicate = expressionGenerator.generatePredicateWithSubqueries(method, expression, cb, query, root, capturedValues);
        applyWherePredicate(method, query, predicate);
        ResultHandle typedQuery = method.invokeInterfaceMethod(md(EntityManager.class, EM_CREATE_QUERY, TypedQuery.class, CriteriaQuery.class), em, query);
        return method.invokeInterfaceMethod(md(TypedQuery.class, TQ_GET_SINGLE_RESULT, Object.class), typedQuery);
    }

    /**
     * Generates aggregation query body.
     * Supports MIN, MAX, AVG, SUM_INTEGER, SUM_LONG, SUM_DOUBLE aggregation operations.
     * <p>
     * Example: Person.where(p -> p.age > 25).min(p -> p.salary)
     * <p>
     * Generates:
     * <pre>
     * CriteriaBuilder cb = em.getCriteriaBuilder();
     * CriteriaQuery<Double> query = cb.createQuery(Double.class);
     * Root<Person> root = query.from(Person.class);
     * Expression<Double> salaryExpr = root.get("salary");
     * query.select(cb.min(salaryExpr));
     * Predicate where = cb.greaterThan(root.get("age"), 25);
     * query.where(where);
     * return em.createQuery(query).getSingleResult();
     * </pre>
     *
     * @param method Bytecode generator context
     * @param em EntityManager handle
     * @param entityClass Entity class being queried
     * @param predicateExpression WHERE clause lambda (null if no filtering)
     * @param aggregationExpression Aggregation mapper lambda (e.g., p -> p.salary)
     * @param aggregationType Aggregation type: MIN, MAX, AVG, SUM_INTEGER, SUM_LONG, SUM_DOUBLE
     * @param capturedValues Captured variables from lambdas
     * @return ResultHandle to aggregation query result
     */
    private ResultHandle generateAggregationQueryBody(
            MethodCreator method,
            ResultHandle em,
            ResultHandle entityClass,
            LambdaExpression predicateExpression,
            LambdaExpression aggregationExpression,
            String aggregationType,
            ResultHandle capturedValues) {

        // Get CriteriaBuilder
        ResultHandle cb = method.invokeInterfaceMethod(
                md(EntityManager.class, EM_GET_CRITERIA_BUILDER, CriteriaBuilder.class), em);

        // Determine result type based on aggregation type
        Class<?> resultType = getAggregationResultType(aggregationType);
        ResultHandle resultClass = method.loadClass(resultType);

        // Create CriteriaQuery<ResultType>
        ResultHandle query = method.invokeInterfaceMethod(
                md(CriteriaBuilder.class, EM_CREATE_QUERY, CriteriaQuery.class, Class.class),
                cb, resultClass);

        // Create Root<Entity>
        ResultHandle root = method.invokeInterfaceMethod(
                md(CriteriaQuery.class, CQ_FROM, Root.class, Class.class), query, entityClass);

        // Generate expression for aggregation mapper (e.g., root.get("salary"))
        ResultHandle mapperExpr = expressionGenerator.generateExpression(
                method, aggregationExpression, cb, root, capturedValues);

        // Apply aggregation function
        ResultHandle aggExpr = applyAggregationFunction(method, cb, mapperExpr, aggregationType);

        // SELECT aggregation result
        method.invokeInterfaceMethod(
                md(CriteriaQuery.class, CQ_SELECT, CriteriaQuery.class, Selection.class),
                query, aggExpr);

        // Apply WHERE predicate if present (with subquery support)
        if (predicateExpression != null) {
            ResultHandle predicate = expressionGenerator.generatePredicateWithSubqueries(
                    method, predicateExpression, cb, query, root, capturedValues);
            applyWherePredicate(method, query, predicate);
        }

        // Create and execute query
        ResultHandle typedQuery = method.invokeInterfaceMethod(
                md(EntityManager.class, EM_CREATE_QUERY, TypedQuery.class, CriteriaQuery.class),
                em, query);

        return method.invokeInterfaceMethod(
                md(TypedQuery.class, TQ_GET_SINGLE_RESULT, Object.class), typedQuery);
    }

    /**
     * Determines the Java result type for an aggregation query.
     * Maps aggregation type names to Java classes.
     */
    private Class<?> getAggregationResultType(String aggregationType) {
        return switch (aggregationType) {
            case "AVG" -> Double.class;           // AVG always returns Double
            case "SUM_INTEGER" -> Long.class;     // SUM of integers returns Long
            case "SUM_LONG" -> Long.class;        // SUM of longs returns Long
            case "SUM_DOUBLE" -> Double.class;    // SUM of doubles returns Double
            case "MIN", "MAX" -> Object.class;    // MIN/MAX return same type as field (use Object for now)
            default -> throw new IllegalArgumentException("Unknown aggregation type: " + aggregationType);
        };
    }

    /**
     * Applies the appropriate CriteriaBuilder aggregation function.
     * Generates cb.min(), cb.max(), cb.avg(), cb.sum() calls.
     */
    private ResultHandle applyAggregationFunction(
            MethodCreator method,
            ResultHandle cb,
            ResultHandle expression,
            String aggregationType) {

        return switch (aggregationType) {
            case "MIN" -> method.invokeInterfaceMethod(CB_MIN, cb, expression);
            case "MAX" -> method.invokeInterfaceMethod(CB_MAX, cb, expression);
            case "AVG" -> method.invokeInterfaceMethod(CB_AVG, cb, expression);
            case "SUM_INTEGER" -> method.invokeInterfaceMethod(CB_SUM_AS_LONG, cb, expression);  // Use sumAsLong for Integer fields
            case "SUM_LONG" -> method.invokeInterfaceMethod(CB_SUM_AS_LONG, cb, expression);
            case "SUM_DOUBLE" -> method.invokeInterfaceMethod(CB_SUM_AS_DOUBLE, cb, expression);
            default -> throw new IllegalArgumentException("Unknown aggregation type: " + aggregationType);
        };
    }

    /**
     * Generates JOIN query bytecode.
     * <p>
     * Creates a JPA Criteria API join query that navigates relationships and applies
     * bi-entity predicates to filter results based on both the source and joined entities.
     * <p>
     * Example: {@code Person.join(p -> p.phones).where((p, ph) -> ph.type.equals("mobile")).toList()}
     * <p>
     * Generates:
     * <pre>
     * CriteriaBuilder cb = em.getCriteriaBuilder();
     * CriteriaQuery&lt;Person&gt; query = cb.createQuery(Person.class);
     * Root&lt;Person&gt; root = query.from(Person.class);
     * Join&lt;Person, Phone&gt; join = root.join("phones", JoinType.INNER);
     * Predicate where = cb.equal(join.get("type"), "mobile");
     * query.where(where);
     * return em.createQuery(query).getResultList();
     * </pre>
     *
     * @param method Bytecode generator context
     * @param em EntityManager handle
     * @param entityClass Entity class being queried (source entity)
     * @param joinRelationshipExpression Lambda expression for join relationship (e.g., p -> p.phones)
     * @param biEntityPredicateExpression Lambda expression for bi-entity predicate (e.g., (p, ph) -> ph.type.equals("mobile"))
     * @param joinType Join type (INNER or LEFT)
     * @param capturedValues Captured variables from lambdas
     * @param offset OFFSET value for pagination (null if none)
     * @param limit LIMIT value for pagination (null if none)
     * @param distinct DISTINCT flag (null or false for no distinct)
     * @param sortExpressions List of sort expressions for ORDER BY (null or empty for no sorting)
     * @return ResultHandle to join query result
     */
    private ResultHandle generateJoinQueryBody(
            MethodCreator method,
            ResultHandle em,
            ResultHandle entityClass,
            LambdaExpression joinRelationshipExpression,
            LambdaExpression biEntityPredicateExpression,
            InvokeDynamicScanner.JoinType joinType,
            List<SortExpression> sortExpressions,
            ResultHandle capturedValues,
            ResultHandle offset,
            ResultHandle limit,
            ResultHandle distinct) {

        // Get CriteriaBuilder
        ResultHandle cb = method.invokeInterfaceMethod(
                md(EntityManager.class, EM_GET_CRITERIA_BUILDER, CriteriaBuilder.class), em);

        // Create CriteriaQuery<EntityClass>
        ResultHandle query = method.invokeInterfaceMethod(
                md(CriteriaBuilder.class, EM_CREATE_QUERY, CriteriaQuery.class, Class.class),
                cb, entityClass);

        // Create Root<Entity>
        ResultHandle root = method.invokeInterfaceMethod(
                md(CriteriaQuery.class, CQ_FROM, Root.class, Class.class), query, entityClass);

        // Extract relationship field name from join relationship expression
        // The expression should be a FieldAccess like "phones" from p -> p.phones
        String relationshipFieldName = joinRelationshipExpression.getFieldNameOrThrow();

        // Create Join by calling root.join(relationshipFieldName, JoinType)
        ResultHandle relationshipName = method.load(relationshipFieldName);
        ResultHandle jpaJoinType = GizmoHelper.loadJpaJoinType(method, joinType);
        ResultHandle joinHandle = method.invokeInterfaceMethod(FROM_JOIN, root, relationshipName, jpaJoinType);

        // Apply bi-entity predicate if present
        // BR-010: Use subquery-aware method to handle EXISTS/IN/scalar subqueries in join predicates
        if (biEntityPredicateExpression != null) {
            ResultHandle predicate = expressionGenerator.generateBiEntityPredicateWithSubqueries(
                    method, biEntityPredicateExpression, cb, query, root, joinHandle, capturedValues);
            applyWherePredicate(method, query, predicate);
        }

        // Apply ORDER BY for join query sorting
        applyBiEntityOrderBy(method, query, root, joinHandle, cb, sortExpressions, capturedValues);

        // Apply DISTINCT if requested
        applyDistinct(method, query, distinct);

        // Create TypedQuery
        ResultHandle typedQuery = method.invokeInterfaceMethod(
                md(EntityManager.class, EM_CREATE_QUERY, TypedQuery.class, CriteriaQuery.class),
                em, query);

        // Apply pagination
        applyPagination(method, typedQuery, offset, limit);

        // Return getResultList()
        return method.invokeInterfaceMethod(
                md(TypedQuery.class, TQ_GET_RESULT_LIST, List.class), typedQuery);
    }

    /**
     * Generates JOIN COUNT query bytecode.
     * <p>
     * Creates a JPA Criteria API count query that navigates relationships and applies
     * bi-entity predicates to count matching results.
     * <p>
     * Example: {@code Person.join(p -> p.phones).where((p, ph) -> ph.type.equals("mobile")).count()}
     * <p>
     * Generates:
     * <pre>
     * CriteriaBuilder cb = em.getCriteriaBuilder();
     * CriteriaQuery&lt;Long&gt; query = cb.createQuery(Long.class);
     * Root&lt;Person&gt; root = query.from(Person.class);
     * Join&lt;Person, Phone&gt; join = root.join("phones", JoinType.INNER);
     * query.select(cb.count(root));
     * Predicate where = cb.equal(join.get("type"), "mobile");
     * query.where(where);
     * return em.createQuery(query).getSingleResult();
     * </pre>
     *
     * @param method Bytecode generator context
     * @param em EntityManager handle
     * @param entityClass Entity class being queried (source entity)
     * @param joinRelationshipExpression Lambda expression for join relationship (e.g., p -> p.phones)
     * @param biEntityPredicateExpression Lambda expression for bi-entity predicate (e.g., (p, ph) -> ph.type.equals("mobile"))
     * @param joinType Join type (INNER or LEFT)
     * @param capturedValues Captured variables from lambdas
     * @return ResultHandle to join count query result (Long)
     */
    private ResultHandle generateJoinCountQueryBody(
            MethodCreator method,
            ResultHandle em,
            ResultHandle entityClass,
            LambdaExpression joinRelationshipExpression,
            LambdaExpression biEntityPredicateExpression,
            InvokeDynamicScanner.JoinType joinType,
            ResultHandle capturedValues) {

        // Get CriteriaBuilder
        ResultHandle cb = method.invokeInterfaceMethod(
                md(EntityManager.class, EM_GET_CRITERIA_BUILDER, CriteriaBuilder.class), em);

        // Create CriteriaQuery<Long> for count
        ResultHandle longClass = method.loadClass(Long.class);
        ResultHandle query = method.invokeInterfaceMethod(
                md(CriteriaBuilder.class, EM_CREATE_QUERY, CriteriaQuery.class, Class.class),
                cb, longClass);

        // Create Root<Entity>
        ResultHandle root = method.invokeInterfaceMethod(
                md(CriteriaQuery.class, CQ_FROM, Root.class, Class.class), query, entityClass);

        // Extract relationship field name from join relationship expression
        String relationshipFieldName = joinRelationshipExpression.getFieldNameOrThrow();

        // Create Join by calling root.join(relationshipFieldName, JoinType)
        ResultHandle relationshipName = method.load(relationshipFieldName);
        ResultHandle jpaJoinType = GizmoHelper.loadJpaJoinType(method, joinType);
        ResultHandle joinHandle = method.invokeInterfaceMethod(FROM_JOIN, root, relationshipName, jpaJoinType);

        // SELECT COUNT(root)
        ResultHandle countExpr = method.invokeInterfaceMethod(
                md(CriteriaBuilder.class, CB_COUNT, Expression.class, Expression.class), cb, root);
        method.invokeInterfaceMethod(
                md(CriteriaQuery.class, CQ_SELECT, CriteriaQuery.class, Selection.class), query, countExpr);

        // Apply bi-entity predicate if present
        // BR-010: Use subquery-aware method to handle EXISTS/IN/scalar subqueries in join predicates
        if (biEntityPredicateExpression != null) {
            ResultHandle predicate = expressionGenerator.generateBiEntityPredicateWithSubqueries(
                    method, biEntityPredicateExpression, cb, query, root, joinHandle, capturedValues);
            applyWherePredicate(method, query, predicate);
        }

        // Create TypedQuery
        ResultHandle typedQuery = method.invokeInterfaceMethod(
                md(EntityManager.class, EM_CREATE_QUERY, TypedQuery.class, CriteriaQuery.class),
                em, query);

        // Return getSingleResult() for count
        return method.invokeInterfaceMethod(
                md(TypedQuery.class, TQ_GET_SINGLE_RESULT, Object.class), typedQuery);
    }

    /**
     * Generates JOIN SELECT JOINED query bytecode.
     * <p>
     * Creates a JPA Criteria API query that navigates relationships and returns
     * the joined entities instead of the source entities.
     * <p>
     * Example: {@code Person.join(p -> p.phones).where((p, ph) -> ph.type.equals("mobile")).selectJoined().toList()}
     * <p>
     * Generates:
     * <pre>
     * CriteriaBuilder cb = em.getCriteriaBuilder();
     * CriteriaQuery&lt;Object&gt; query = cb.createQuery(Object.class);
     * Root&lt;Person&gt; root = query.from(Person.class);
     * Join&lt;Person, Phone&gt; join = root.join("phones", JoinType.INNER);
     * query.select(join);  // SELECT joined entity instead of root
     * Predicate where = cb.equal(join.get("type"), "mobile");
     * query.where(where);
     * return em.createQuery(query).getResultList();
     * </pre>
     *
     * @param method Bytecode generator context
     * @param em EntityManager handle
     * @param entityClass Entity class being queried (source entity)
     * @param joinRelationshipExpression Lambda expression for join relationship (e.g., p -> p.phones)
     * @param biEntityPredicateExpression Lambda expression for bi-entity predicate (e.g., (p, ph) -> ph.type.equals("mobile"))
     * @param joinType Join type (INNER or LEFT)
     * @param sortExpressions List of bi-entity sort expressions for ORDER BY
     * @param capturedValues Captured variables from lambdas
     * @param offset Pagination offset (null for no offset)
     * @param limit Pagination limit (null for no limit)
     * @param distinct Whether to apply DISTINCT
     * @return ResultHandle to join select joined query result (List of joined entities)
     */
    private ResultHandle generateJoinSelectJoinedQueryBody(
            MethodCreator method,
            ResultHandle em,
            ResultHandle entityClass,
            LambdaExpression joinRelationshipExpression,
            LambdaExpression biEntityPredicateExpression,
            InvokeDynamicScanner.JoinType joinType,
            List<SortExpression> sortExpressions,
            ResultHandle capturedValues,
            ResultHandle offset,
            ResultHandle limit,
            ResultHandle distinct) {

        // Get CriteriaBuilder
        ResultHandle cb = method.invokeInterfaceMethod(
                md(EntityManager.class, EM_GET_CRITERIA_BUILDER, CriteriaBuilder.class), em);

        // Create CriteriaQuery<Object> (joined entity type not known at build time)
        ResultHandle objectClass = method.loadClass(Object.class);
        ResultHandle query = method.invokeInterfaceMethod(
                md(CriteriaBuilder.class, EM_CREATE_QUERY, CriteriaQuery.class, Class.class),
                cb, objectClass);

        // Create Root<Entity> from source entity
        ResultHandle root = method.invokeInterfaceMethod(
                md(CriteriaQuery.class, CQ_FROM, Root.class, Class.class), query, entityClass);

        // Extract relationship field name from join relationship expression
        String relationshipFieldName = joinRelationshipExpression.getFieldNameOrThrow();

        // Create Join by calling root.join(relationshipFieldName, JoinType)
        ResultHandle relationshipName = method.load(relationshipFieldName);
        ResultHandle jpaJoinType = GizmoHelper.loadJpaJoinType(method, joinType);
        ResultHandle joinHandle = method.invokeInterfaceMethod(FROM_JOIN, root, relationshipName, jpaJoinType);

        // SELECT the joined entity (joinHandle) instead of root
        method.invokeInterfaceMethod(
                md(CriteriaQuery.class, CQ_SELECT, CriteriaQuery.class, Selection.class), query, joinHandle);

        // Apply bi-entity predicate if present
        // BR-010: Use subquery-aware method to handle EXISTS/IN/scalar subqueries in join predicates
        if (biEntityPredicateExpression != null) {
            ResultHandle predicate = expressionGenerator.generateBiEntityPredicateWithSubqueries(
                    method, biEntityPredicateExpression, cb, query, root, joinHandle, capturedValues);
            applyWherePredicate(method, query, predicate);
        }

        // Apply ORDER BY for join query sorting
        applyBiEntityOrderBy(method, query, root, joinHandle, cb, sortExpressions, capturedValues);

        // Apply DISTINCT if requested
        applyDistinct(method, query, distinct);

        // Create TypedQuery
        ResultHandle typedQuery = method.invokeInterfaceMethod(
                md(EntityManager.class, EM_CREATE_QUERY, TypedQuery.class, CriteriaQuery.class),
                em, query);

        // Apply pagination
        applyPagination(method, typedQuery, offset, limit);

        // Return getResultList() containing joined entities
        return method.invokeInterfaceMethod(
                md(TypedQuery.class, TQ_GET_RESULT_LIST, List.class), typedQuery);
    }

    /**
     * Generates query body for join query with bi-entity projection.
     * <p>
     * Example: {@code Person.join(p -> p.phones).select((p, ph) -> new PersonPhoneDTO(p.firstName, ph.number)).toList()}
     * <p>
     * Generates:
     * <pre>
     * CriteriaBuilder cb = em.getCriteriaBuilder();
     * CriteriaQuery&lt;Object&gt; query = cb.createQuery(Object.class);
     * Root&lt;Person&gt; root = query.from(Person.class);
     * Join&lt;Person, Phone&gt; join = root.join("phones", JoinType.INNER);
     * query.select(cb.construct(PersonPhoneDTO.class, root.get("firstName"), join.get("number")));
     * Predicate where = cb.equal(join.get("type"), "mobile");
     * query.where(where);
     * return em.createQuery(query).getResultList();
     * </pre>
     *
     * @param method Bytecode generator context
     * @param em EntityManager handle
     * @param entityClass Entity class being queried (source entity)
     * @param joinRelationshipExpression Lambda expression for join relationship (e.g., p -> p.phones)
     * @param biEntityPredicateExpression Lambda expression for bi-entity predicate (e.g., (p, ph) -> ph.type.equals("mobile"))
     * @param biEntityProjectionExpression Lambda expression for bi-entity projection (e.g., (p, ph) -> new DTO(...))
     * @param joinType Join type (INNER or LEFT)
     * @param sortExpressions List of bi-entity sort expressions for ORDER BY
     * @param capturedValues Captured variables from lambdas
     * @param offset Pagination offset (null for no offset)
     * @param limit Pagination limit (null for no limit)
     * @param distinct Whether to apply DISTINCT
     * @return ResultHandle to join projection query result (List of projected objects)
     */
    private ResultHandle generateJoinProjectionQueryBody(
            MethodCreator method,
            ResultHandle em,
            ResultHandle entityClass,
            LambdaExpression joinRelationshipExpression,
            LambdaExpression biEntityPredicateExpression,
            LambdaExpression biEntityProjectionExpression,
            InvokeDynamicScanner.JoinType joinType,
            List<SortExpression> sortExpressions,
            ResultHandle capturedValues,
            ResultHandle offset,
            ResultHandle limit,
            ResultHandle distinct) {

        // Get CriteriaBuilder
        ResultHandle cb = method.invokeInterfaceMethod(
                md(EntityManager.class, EM_GET_CRITERIA_BUILDER, CriteriaBuilder.class), em);

        // Create CriteriaQuery<Object> (projection type not known at build time)
        ResultHandle objectClass = method.loadClass(Object.class);
        ResultHandle query = method.invokeInterfaceMethod(
                md(CriteriaBuilder.class, EM_CREATE_QUERY, CriteriaQuery.class, Class.class),
                cb, objectClass);

        // Create Root<Entity> from source entity
        ResultHandle root = method.invokeInterfaceMethod(
                md(CriteriaQuery.class, CQ_FROM, Root.class, Class.class), query, entityClass);

        // Extract relationship field name from join relationship expression
        String relationshipFieldName = joinRelationshipExpression.getFieldNameOrThrow();

        // Create Join by calling root.join(relationshipFieldName, JoinType)
        ResultHandle relationshipName = method.load(relationshipFieldName);
        ResultHandle jpaJoinType = GizmoHelper.loadJpaJoinType(method, joinType);
        ResultHandle joinHandle = method.invokeInterfaceMethod(FROM_JOIN, root, relationshipName, jpaJoinType);

        // Generate and apply bi-entity projection
        ResultHandle projection = expressionGenerator.generateBiEntityProjection(
                method, biEntityProjectionExpression, cb, root, joinHandle, capturedValues);
        method.invokeInterfaceMethod(
                md(CriteriaQuery.class, CQ_SELECT, CriteriaQuery.class, Selection.class), query, projection);

        // Apply bi-entity predicate if present
        // BR-010: Use subquery-aware method to handle EXISTS/IN/scalar subqueries in join predicates
        if (biEntityPredicateExpression != null) {
            ResultHandle predicate = expressionGenerator.generateBiEntityPredicateWithSubqueries(
                    method, biEntityPredicateExpression, cb, query, root, joinHandle, capturedValues);
            applyWherePredicate(method, query, predicate);
        }

        // Apply ORDER BY for join query sorting
        applyBiEntityOrderBy(method, query, root, joinHandle, cb, sortExpressions, capturedValues);

        // Apply DISTINCT if requested
        applyDistinct(method, query, distinct);

        // Create TypedQuery
        ResultHandle typedQuery = method.invokeInterfaceMethod(
                md(EntityManager.class, EM_CREATE_QUERY, TypedQuery.class, CriteriaQuery.class),
                em, query);

        // Apply pagination
        applyPagination(method, typedQuery, offset, limit);

        // Return getResultList() containing projected objects
        return method.invokeInterfaceMethod(
                md(TypedQuery.class, TQ_GET_RESULT_LIST, List.class), typedQuery);
    }

    /**
     * Generates simple field projection query.
     * <p>
     * Example: Person.select(p -> p.firstName).toList()
     * <p>
     * Generates:
     * <pre>
     * CriteriaBuilder cb = em.getCriteriaBuilder();
     * CriteriaQuery<String> query = cb.createQuery(String.class);
     * Root<Person> root = query.from(Person.class);
     * query.select(root.get("firstName"));
     * return em.createQuery(query).getResultList();
     * </pre>
     */
    private ResultHandle generateSimpleFieldProjectionQuery(
            QueryGenContext ctx,
            LambdaExpression.FieldAccess fieldAccess) {

        // Get CriteriaBuilder
        ResultHandle cb = ctx.method().invokeInterfaceMethod(
                md(EntityManager.class, EM_GET_CRITERIA_BUILDER, CriteriaBuilder.class), ctx.em());

        // Load field type class
        ResultHandle fieldTypeClass = ctx.method().loadClass(fieldAccess.fieldType());

        // Create CriteriaQuery<FieldType>
        ResultHandle query = ctx.method().invokeInterfaceMethod(
                md(CriteriaBuilder.class, EM_CREATE_QUERY, CriteriaQuery.class, Class.class),
                cb, fieldTypeClass);

        // Create Root<Entity>
        ResultHandle root = ctx.method().invokeInterfaceMethod(
                md(CriteriaQuery.class, CQ_FROM, Root.class, Class.class),
                query, ctx.entityClass());

        // Generate root.get("fieldName")
        ResultHandle fieldName = ctx.method().load(fieldAccess.fieldName());
        ResultHandle path = ctx.method().invokeInterfaceMethod(
                md(Path.class, "get", Path.class, String.class),
                root, fieldName);

        // query.select(path)
        ctx.method().invokeInterfaceMethod(
                md(CriteriaQuery.class, CQ_SELECT, CriteriaQuery.class, Selection.class),
                query, path);

        // Apply ORDER BY if sorting expressions present
        applyOrderBy(ctx.method(), query, root, cb, ctx.sortExpressions(), ctx.capturedValues(), path);

        // Apply DISTINCT if requested
        applyDistinct(ctx.method(), query, ctx.distinct());

        // Create TypedQuery
        ResultHandle typedQuery = ctx.method().invokeInterfaceMethod(
                md(EntityManager.class, EM_CREATE_QUERY, TypedQuery.class, CriteriaQuery.class),
                ctx.em(), query);

        // Apply pagination
        applyPagination(ctx.method(), typedQuery, ctx.offset(), ctx.limit());

        // Return getResultList()
        return ctx.method().invokeInterfaceMethod(
                md(TypedQuery.class, TQ_GET_RESULT_LIST, List.class),
                typedQuery);
    }

    /**
     * Generates projection query supporting both field access and expressions.
     * <p>
     * Examples:
     * <ul>
     * <li>Field access: {@code Person.select(p -> p.firstName).toList()}</li>
     * <li>Expression: {@code Person.select(p -> p.salary * 1.1).toList()}</li>
     * <li>String concat: {@code Person.select(p -> p.firstName + " " + p.lastName).toList()}</li>
     * </ul>
     * <p>
     * For field access projections, creates type-safe CriteriaQuery&lt;FieldType&gt;.
     * For expression projections, creates CriteriaQuery&lt;Object&gt;.
     */
    private ResultHandle generateProjectionQuery(
            QueryGenContext ctx,
            LambdaExpression projectionExpression) {

        // Simple field access projection - use type-safe query
        if (projectionExpression instanceof LambdaExpression.FieldAccess fieldAccess) {
            return generateSimpleFieldProjectionQuery(ctx, fieldAccess);
        }

        // Expression projection - use Object query
        // Get CriteriaBuilder
        ResultHandle cb = ctx.method().invokeInterfaceMethod(
                md(EntityManager.class, EM_GET_CRITERIA_BUILDER, CriteriaBuilder.class), ctx.em());

        // Use Object.class for expression projections
        ResultHandle objectClass = ctx.method().loadClass(Object.class);

        // Create CriteriaQuery<Object>
        ResultHandle query = ctx.method().invokeInterfaceMethod(
                md(CriteriaBuilder.class, EM_CREATE_QUERY, CriteriaQuery.class, Class.class),
                cb, objectClass);

        // Create Root<Entity>
        ResultHandle root = ctx.method().invokeInterfaceMethod(
                md(CriteriaQuery.class, CQ_FROM, Root.class, Class.class),
                query, ctx.entityClass());

        // Generate the projection expression as JPA Expression
        ResultHandle projectionExpr = expressionGenerator.generateExpressionAsJpaExpression(
                ctx.method(), projectionExpression, cb, root, ctx.capturedValues());

        // query.select(projectionExpr)
        ctx.method().invokeInterfaceMethod(
                md(CriteriaQuery.class, CQ_SELECT, CriteriaQuery.class, Selection.class),
                query, projectionExpr);

        // Apply ORDER BY if sorting expressions present
        applyOrderBy(ctx.method(), query, root, cb, ctx.sortExpressions(), ctx.capturedValues(), projectionExpr);

        // Apply DISTINCT if requested
        applyDistinct(ctx.method(), query, ctx.distinct());

        // Create TypedQuery
        ResultHandle typedQuery = ctx.method().invokeInterfaceMethod(
                md(EntityManager.class, EM_CREATE_QUERY, TypedQuery.class, CriteriaQuery.class),
                ctx.em(), query);

        // Apply pagination
        applyPagination(ctx.method(), typedQuery, ctx.offset(), ctx.limit());

        // Return getResultList()
        return ctx.method().invokeInterfaceMethod(
                md(TypedQuery.class, TQ_GET_RESULT_LIST, List.class),
                typedQuery);
    }

    /**
     * Generates combined WHERE + SELECT query.
     * <p>
     * Examples:
     * <ul>
     * <li>Field projection: {@code Person.where(p -> p.age > 25).select(p -> p.firstName).toList()}</li>
     * <li>Expression projection: {@code Person.where(p -> p.active).select(p -> p.salary * 1.1).toList()}</li>
     * </ul>
     * <p>
     * For field access projections, creates type-safe CriteriaQuery&lt;FieldType&gt;.
     * For expression projections, creates CriteriaQuery&lt;Object&gt;.
     */
    private ResultHandle generateCombinedWhereSelectQuery(
            QueryGenContext ctx,
            LambdaExpression predicateExpression,
            LambdaExpression projectionExpression) {

        // Get CriteriaBuilder
        ResultHandle cb = ctx.method().invokeInterfaceMethod(
                md(EntityManager.class, EM_GET_CRITERIA_BUILDER, CriteriaBuilder.class), ctx.em());

        // Determine result type based on projection expression
        ResultHandle resultTypeClass;
        if (projectionExpression instanceof LambdaExpression.FieldAccess fieldAccess) {
            // Field access projection - use field type for type safety
            resultTypeClass = ctx.method().loadClass(fieldAccess.fieldType());
        } else {
            // Expression projection - use Object.class
            resultTypeClass = ctx.method().loadClass(Object.class);
        }

        // Create CriteriaQuery<ResultType>
        ResultHandle query = ctx.method().invokeInterfaceMethod(
                md(CriteriaBuilder.class, EM_CREATE_QUERY, CriteriaQuery.class, Class.class),
                cb, resultTypeClass);

        // Create Root<Entity>
        ResultHandle root = ctx.method().invokeInterfaceMethod(
                md(CriteriaQuery.class, CQ_FROM, Root.class, Class.class),
                query, ctx.entityClass());

        // Generate WHERE predicate (with subquery support)
        ResultHandle predicate = expressionGenerator.generatePredicateWithSubqueries(
                ctx.method(), predicateExpression, cb, query, root, ctx.capturedValues());
        applyWherePredicate(ctx.method(), query, predicate);

        // Generate SELECT projection expression
        ResultHandle projectionExpr;
        if (projectionExpression instanceof LambdaExpression.FieldAccess fieldAccess) {
            // Simple field access - root.get("fieldName")
            ResultHandle fieldName = ctx.method().load(fieldAccess.fieldName());
            projectionExpr = ctx.method().invokeInterfaceMethod(
                    md(Path.class, "get", Path.class, String.class),
                    root, fieldName);
        } else {
            // Expression projection - use expression generator
            ResultHandle expr = expressionGenerator.generateExpressionAsJpaExpression(
                    ctx.method(), projectionExpression, cb, root, ctx.capturedValues());
            projectionExpr = expr;
        }

        // query.select(projectionExpr)
        ctx.method().invokeInterfaceMethod(
                md(CriteriaQuery.class, CQ_SELECT, CriteriaQuery.class, Selection.class),
                query, projectionExpr);

        // Apply ORDER BY if sorting expressions present
        applyOrderBy(ctx.method(), query, root, cb, ctx.sortExpressions(), ctx.capturedValues(), projectionExpr);

        // Apply DISTINCT if requested
        applyDistinct(ctx.method(), query, ctx.distinct());

        // Create TypedQuery
        ResultHandle typedQuery = ctx.method().invokeInterfaceMethod(
                md(EntityManager.class, EM_CREATE_QUERY, TypedQuery.class, CriteriaQuery.class),
                ctx.em(), query);

        // Apply pagination
        applyPagination(ctx.method(), typedQuery, ctx.offset(), ctx.limit());

        // Return getResultList()
        return ctx.method().invokeInterfaceMethod(
                md(TypedQuery.class, TQ_GET_RESULT_LIST, List.class),
                typedQuery);
    }

    /**
     * Applies where clause predicate to CriteriaQuery.
     */
    private void applyWherePredicate(MethodCreator method, ResultHandle query, ResultHandle predicate) {
        if (predicate != null) {
            ResultHandle predicateArray = method.newArray(Predicate.class, 1);
            method.writeArrayValue(predicateArray, 0, predicate);
            method.invokeInterfaceMethod(md(CriteriaQuery.class, CQ_WHERE, CriteriaQuery.class, Predicate[].class), query, predicateArray);
        }
    }

    /**
     * Applies ORDER BY clause to CriteriaQuery.
     * Generates JPA ordering from sort expressions.
     * <p>
     * For each sort expression, generates:
     * - Expression for the sort key (e.g., root.get("age"))
     * - Order object (cb.asc() or cb.desc())
     * Then applies all orders via query.orderBy(orders...)
     * <p>
     * IMPORTANT: sortExpressions are in bytecode order (first-to-last as written in source).
     * For "last call wins" semantics, we process in REVERSE order so the last call becomes
     * the primary sort (first in JPA's ORDER BY array).
     * <p>
     * For SELECT+SORT queries with identity sort functions like (String s) -> s,
     * the sort key expression will be null (Parameter cannot be converted to JPA expression).
     * In this case, we use the projectionExpression parameter as the ORDER BY key.
     *
     * @param projectionExpression the JPA expression for the SELECT clause (used for identity sorts), or null
     */
    private void applyOrderBy(
            MethodCreator method,
            ResultHandle query,
            ResultHandle root,
            ResultHandle cb,
            List<?> sortExpressions,
            ResultHandle capturedValues,
            ResultHandle projectionExpression) {

        if (sortExpressions == null || sortExpressions.isEmpty()) {
            return; // No sorting
        }

        // Create array to hold Order objects
        ResultHandle ordersArray = method.newArray(Order.class, sortExpressions.size());

        // Generate Order objects in REVERSE order for "last call wins" semantics
        // Example: .sortedBy(firstName).sortedBy(lastName) should order by lastName first
        for (int i = 0; i < sortExpressions.size(); i++) {
            // Read from end of list (reverse order)
            int reverseIndex = sortExpressions.size() - 1 - i;
            Object sortExprObj = sortExpressions.get(reverseIndex);

            // This is a build-time object, so we can safely cast it
            if (sortExprObj instanceof SortExpression sortExpr) {
                // Generate JPA Expression for the sort key extractor
                ResultHandle sortKeyExpr = expressionGenerator.generateExpressionAsJpaExpression(
                        method, sortExpr.keyExtractor(), cb, root, capturedValues);

                // If sort key is null (identity function like s -> s after projection),
                // use the projection expression instead
                if (sortKeyExpr == null && projectionExpression != null) {
                    sortKeyExpr = projectionExpression;
                }

                // Create Order object (ascending or descending)
                ResultHandle order;
                if (sortExpr.direction() == SortDirection.DESCENDING) {
                    order = method.invokeInterfaceMethod(CB_DESC, cb, sortKeyExpr);
                } else {
                    order = method.invokeInterfaceMethod(CB_ASC, cb, sortKeyExpr);
                }

                // Add to orders array at position i (forward order)
                method.writeArrayValue(ordersArray, i, order);
            }
        }

        // Apply orderBy to query
        method.invokeInterfaceMethod(CQ_ORDER_BY, query, ordersArray);
    }

    /**
     * Applies ORDER BY clause for join queries using bi-entity sort expressions.
     * <p>
     * Uses the "last call wins" semantics where the most recent sortedBy() call
     * becomes the primary sort key.
     *
     * @param method the method creator
     * @param query the CriteriaQuery to apply order by to
     * @param root the Root for source entity (position 0)
     * @param join the Join for joined entity (position 1)
     * @param cb the CriteriaBuilder
     * @param sortExpressions list of sort expressions with bi-entity key extractors
     * @param capturedValues captured variables from lambdas
     */
    private void applyBiEntityOrderBy(
            MethodCreator method,
            ResultHandle query,
            ResultHandle root,
            ResultHandle join,
            ResultHandle cb,
            List<?> sortExpressions,
            ResultHandle capturedValues) {

        if (sortExpressions == null || sortExpressions.isEmpty()) {
            return; // No sorting
        }

        // Create array to hold Order objects
        ResultHandle ordersArray = method.newArray(Order.class, sortExpressions.size());

        // Generate Order objects in REVERSE order for "last call wins" semantics
        // Example: .sortedBy(sourceField).sortedBy(joinedField) should order by joinedField first
        for (int i = 0; i < sortExpressions.size(); i++) {
            // Read from end of list (reverse order)
            int reverseIndex = sortExpressions.size() - 1 - i;
            Object sortExprObj = sortExpressions.get(reverseIndex);

            if (sortExprObj instanceof SortExpression sortExpr) {
                // Generate JPA Expression for the bi-entity sort key extractor
                ResultHandle sortKeyExpr = expressionGenerator.generateBiEntityExpressionAsJpaExpression(
                        method, sortExpr.keyExtractor(), cb, root, join, capturedValues);

                // Create Order object (ascending or descending)
                ResultHandle order;
                if (sortExpr.direction() == SortDirection.DESCENDING) {
                    order = method.invokeInterfaceMethod(CB_DESC, cb, sortKeyExpr);
                } else {
                    order = method.invokeInterfaceMethod(CB_ASC, cb, sortKeyExpr);
                }

                // Add to orders array at position i (forward order)
                method.writeArrayValue(ordersArray, i, order);
            }
        }

        // Apply orderBy to query
        method.invokeInterfaceMethod(CQ_ORDER_BY, query, ordersArray);
    }

    /**
     * Applies DISTINCT clause to CriteriaQuery.
     * <p>
     * Generates code equivalent to:
     * <pre>
     * if (distinct != null && distinct) query.distinct(true);
     * </pre>
     *
     * @param method the method creator
     * @param query the CriteriaQuery to apply distinct to
     * @param distinct the distinct parameter (Boolean, may be null)
     */
    private void applyDistinct(
            MethodCreator method,
            ResultHandle query,
            ResultHandle distinct) {

        // Apply distinct if present and true: if (distinct != null && distinct) query.distinct(true);
        if (distinct != null) {
            BranchResult distinctBranch = method.ifNotNull(distinct);
            try (BytecodeCreator distinctNotNull = distinctBranch.trueBranch()) {
                // Unbox Boolean to boolean
                ResultHandle distinctValue = distinctNotNull.invokeVirtualMethod(BOOLEAN_BOOLEAN_VALUE, distinct);

                // Only call query.distinct(true) if the value is true
                BranchResult trueBranch = distinctNotNull.ifTrue(distinctValue);
                try (BytecodeCreator applyDistinct = trueBranch.trueBranch()) {
                    applyDistinct.invokeInterfaceMethod(CQ_DISTINCT, query,
                            applyDistinct.load(true));
                }
            }
        }
    }

    /**
     * Applies pagination (OFFSET and LIMIT) to TypedQuery.
     * <p>
     * Generates code equivalent to:
     * <pre>
     * if (offset != null) query.setFirstResult(offset);
     * if (limit != null) query.setMaxResults(limit);
     * </pre>
     *
     * @param method the method creator
     * @param typedQuery the TypedQuery to apply pagination to
     * @param offset the offset parameter (Integer, may be null)
     * @param limit the limit parameter (Integer, may be null)
     */
    private void applyPagination(
            MethodCreator method,
            ResultHandle typedQuery,
            ResultHandle offset,
            ResultHandle limit) {

        // Apply offset if present: if (offset != null) query.setFirstResult(offset);
        if (offset != null) {
            BranchResult offsetBranch = method.ifNotNull(offset);
            try (BytecodeCreator offsetTrue = offsetBranch.trueBranch()) {
                // Unbox Integer to int
                ResultHandle offsetValue = offsetTrue.invokeVirtualMethod(INTEGER_INT_VALUE, offset);
                offsetTrue.invokeInterfaceMethod(TQ_SET_FIRST_RESULT, typedQuery, offsetValue);
            }
        }

        // Apply limit if present: if (limit != null) query.setMaxResults(limit);
        if (limit != null) {
            BranchResult limitBranch = method.ifNotNull(limit);
            try (BytecodeCreator limitTrue = limitBranch.trueBranch()) {
                // Unbox Integer to int
                ResultHandle limitValue = limitTrue.invokeVirtualMethod(INTEGER_INT_VALUE, limit);
                limitTrue.invokeInterfaceMethod(TQ_SET_MAX_RESULTS, typedQuery, limitValue);
            }
        }
    }
}
