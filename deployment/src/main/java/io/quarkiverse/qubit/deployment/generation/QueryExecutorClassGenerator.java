package io.quarkiverse.qubit.deployment.generation;

import static io.quarkiverse.qubit.deployment.common.ExceptionMessages.unknownAggregationType;
import static io.quarkiverse.qubit.runtime.internal.QubitConstants.AGG_TYPE_AVG;
import static io.quarkiverse.qubit.runtime.internal.QubitConstants.AGG_TYPE_MAX;
import static io.quarkiverse.qubit.runtime.internal.QubitConstants.AGG_TYPE_MIN;
import static io.quarkiverse.qubit.runtime.internal.QubitConstants.AGG_TYPE_SUM_DOUBLE;
import static io.quarkiverse.qubit.runtime.internal.QubitConstants.AGG_TYPE_SUM_INTEGER;
import static io.quarkiverse.qubit.runtime.internal.QubitConstants.AGG_TYPE_SUM_LONG;
import static io.quarkiverse.qubit.runtime.internal.QubitConstants.CONSTRUCTOR;
import static io.quarkiverse.qubit.runtime.internal.QubitConstants.QE_EXECUTE;
import static io.quarkiverse.qubit.runtime.internal.QubitConstants.QUERY_EXECUTOR_CLASS_NAME;
import static io.quarkiverse.qubit.deployment.generation.MethodDescriptors.CB_AVG;
import static io.quarkiverse.qubit.deployment.generation.MethodDescriptors.CB_COUNT;
import static io.quarkiverse.qubit.deployment.generation.MethodDescriptors.CB_COUNT_DISTINCT;
import static io.quarkiverse.qubit.deployment.generation.MethodDescriptors.CB_CREATE_QUERY;
import static io.quarkiverse.qubit.deployment.generation.MethodDescriptors.CB_MAX;
import static io.quarkiverse.qubit.deployment.generation.MethodDescriptors.CB_MIN;
import static io.quarkiverse.qubit.deployment.generation.MethodDescriptors.CB_SUM_AS_DOUBLE;
import static io.quarkiverse.qubit.deployment.generation.MethodDescriptors.CB_SUM_AS_LONG;
import static io.quarkiverse.qubit.deployment.generation.MethodDescriptors.CQ_FROM;
import static io.quarkiverse.qubit.deployment.generation.MethodDescriptors.CQ_GROUP_BY;
import static io.quarkiverse.qubit.deployment.generation.MethodDescriptors.CQ_HAVING;
import static io.quarkiverse.qubit.deployment.generation.MethodDescriptors.CQ_SELECT;
import static io.quarkiverse.qubit.deployment.generation.MethodDescriptors.EM_CREATE_QUERY;
import static io.quarkiverse.qubit.deployment.generation.MethodDescriptors.EM_GET_CRITERIA_BUILDER;
import static io.quarkiverse.qubit.deployment.generation.MethodDescriptors.FROM_JOIN;
import static io.quarkiverse.qubit.deployment.generation.MethodDescriptors.INTEGER_LONG_VALUE;
import static io.quarkiverse.qubit.deployment.generation.MethodDescriptors.LIST_SIZE;
import static io.quarkiverse.qubit.deployment.generation.MethodDescriptors.LONG_VALUE_OF;
import static io.quarkiverse.qubit.deployment.generation.MethodDescriptors.INTEGER_VALUE_OF;
import static io.quarkiverse.qubit.deployment.generation.MethodDescriptors.PATH_GET;
import static io.quarkiverse.qubit.deployment.generation.MethodDescriptors.TQ_GET_RESULT_LIST;
import static io.quarkiverse.qubit.deployment.generation.MethodDescriptors.TQ_GET_SINGLE_RESULT;

import java.util.List;

import io.quarkus.gizmo.ClassCreator;
import io.quarkus.gizmo.MethodCreator;
import io.quarkus.gizmo.MethodDescriptor;
import io.quarkus.gizmo.ResultHandle;
import io.quarkiverse.qubit.deployment.analysis.InvokeDynamicScanner;
import io.quarkiverse.qubit.deployment.analysis.LambdaAnalysisResult.SortExpression;
import io.quarkiverse.qubit.deployment.ast.LambdaExpression;
import io.quarkiverse.qubit.deployment.generation.join.JoinQueryBuilder;
import io.quarkiverse.qubit.deployment.generation.join.JoinQueryContext;
import io.quarkiverse.qubit.deployment.generation.join.JoinSelectionStrategy;
import io.quarkiverse.qubit.deployment.generation.join.StandardClauseApplier;
import io.quarkiverse.qubit.deployment.util.ByteArrayClassOutput;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;

/**
 * Generates query executor classes that execute JPA Criteria API queries from lambda expressions.
 */
public class QueryExecutorClassGenerator {

    private final CriteriaExpressionGenerator expressionGenerator = new CriteriaExpressionGenerator();

    /** Join query builder using Template Method pattern. */
    private final JoinQueryBuilder joinQueryBuilder;

    private final StandardClauseApplier clauseApplier = new StandardClauseApplier(expressionGenerator);

    public QueryExecutorClassGenerator() {
        this.joinQueryBuilder = new JoinQueryBuilder(expressionGenerator, clauseApplier);
    }

    /** Context for query generation methods with shared parameters. */
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

    /** JPA Criteria API query setup: CriteriaBuilder, CriteriaQuery, and Root. */
    private record QuerySetup(
        ResultHandle cb,
        ResultHandle query,
        ResultHandle root
    ) {}

    /** Context for GROUP BY query generation with WHERE, GROUP BY, and HAVING. */
    private record GroupQueryContext(
        MethodCreator method,
        ResultHandle em,
        ResultHandle entityClass,
        LambdaExpression predicateExpression,
        LambdaExpression groupByKeyExpression,
        LambdaExpression havingExpression,
        ResultHandle capturedValues
    ) {}

    /** Extended GROUP BY context adding select, sort, pagination, and distinct. */
    private record GroupListContext(
        GroupQueryContext base,
        LambdaExpression groupSelectExpression,
        List<SortExpression> groupSortExpressions,
        ResultHandle offset,
        ResultHandle limit,
        ResultHandle distinct
    ) {
        // Delegate accessors for convenience
        MethodCreator method() { return base.method(); }
        ResultHandle em() { return base.em(); }
        ResultHandle entityClass() { return base.entityClass(); }
        LambdaExpression havingExpression() { return base.havingExpression(); }
        ResultHandle capturedValues() { return base.capturedValues(); }
    }

    /** Sets up CriteriaBuilder, CriteriaQuery, and Root in a single call. */
    private QuerySetup setupQuery(
            MethodCreator method,
            ResultHandle em,
            ResultHandle entityClass,
            ResultHandle resultClass) {
        ResultHandle cb = method.invokeInterfaceMethod(EM_GET_CRITERIA_BUILDER, em);
        ResultHandle query = method.invokeInterfaceMethod(CB_CREATE_QUERY, cb, resultClass);
        ResultHandle root = method.invokeInterfaceMethod(CQ_FROM, query, entityClass);
        return new QuerySetup(cb, query, root);
    }

    /** Sets up query with Object.class result type (projections, groups). */
    private QuerySetup setupQueryForObject(MethodCreator method, ResultHandle em, ResultHandle entityClass) {
        return setupQuery(method, em, entityClass, method.loadClass(Object.class));
    }

    /** Sets up query with Long.class result type (count queries). */
    private QuerySetup setupQueryForLong(MethodCreator method, ResultHandle em, ResultHandle entityClass) {
        return setupQuery(method, em, entityClass, method.loadClass(Long.class));
    }

    /** Generates query executor class with WHERE, SELECT, ORDER BY, and aggregation. */
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

    /** Generates query executor for JOIN queries with bi-entity predicates and projections. */
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
                } else {
                    // Use Template Method pattern with Strategy for the three join query variants
                    JoinQueryContext ctx = new JoinQueryContext(
                            execute, em, entityClassParam,
                            joinRelationshipExpression, biEntityPredicateExpression,
                            joinType, sortExpressions, capturedValues, offset, limit, distinct);

                    // Determine selection strategy based on query type
                    JoinSelectionStrategy strategy;
                    if (isJoinProjection) {
                        // select() with BiQuerySpec returns projected results
                        ResultHandle objectClass = execute.loadClass(Object.class);
                        strategy = new JoinSelectionStrategy.SelectProjection(objectClass, biEntityProjectionExpression);
                    } else if (isSelectJoined) {
                        // selectJoined() returns joined entities instead of source entities
                        ResultHandle objectClass = execute.loadClass(Object.class);
                        strategy = new JoinSelectionStrategy.SelectJoined(objectClass);
                    } else {
                        // Default: return root entities
                        strategy = new JoinSelectionStrategy.SelectRoot(entityClassParam);
                    }

                    result = joinQueryBuilder.build(ctx, strategy);
                }

                execute.returnValue(result);
            }
        }

        return classOutput.getData();
    }

    /** Generates query executor for GROUP BY with optional HAVING, aggregations, and sorting. */
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

                // Create base context with common parameters
                GroupQueryContext baseCtx = new GroupQueryContext(
                        execute, em, entityClassParam,
                        predicateExpression, groupByKeyExpression,
                        havingExpression, capturedValues);

                ResultHandle result;
                if (isCountQuery) {
                    result = generateGroupCountQueryBody(baseCtx);
                } else {
                    GroupListContext listCtx = new GroupListContext(
                            baseCtx, groupSelectExpression, groupSortExpressions,
                            offset, limit, distinct);
                    result = generateGroupQueryBody(listCtx);
                }

                execute.returnValue(result);
            }
        }

        return classOutput.getData();
    }

    /** Generates GROUP BY query with optional WHERE, HAVING, SELECT, and ORDER BY. */
    private ResultHandle generateGroupQueryBody(GroupListContext ctx) {
        MethodCreator method = ctx.method();

        // Setup query and apply common GROUP BY logic
        QuerySetup setup = setupQueryForObject(method, ctx.em(), ctx.entityClass());
        ResultHandle cb = setup.cb();
        ResultHandle query = setup.query();
        ResultHandle root = setup.root();

        // Apply common WHERE and GROUP BY setup
        ResultHandle groupKeyExpr = applyGroupQuerySetup(ctx.base(), setup);

        // Apply HAVING predicate if present
        if (ctx.havingExpression() != null) {
            ResultHandle havingPredicate = expressionGenerator.generateGroupPredicate(
                    method, ctx.havingExpression(), cb, root, groupKeyExpr, ctx.capturedValues());
            applyHavingPredicate(method, query, havingPredicate);
        }

        // Apply SELECT projection if present
        if (ctx.groupSelectExpression() != null) {
            ResultHandle selection = expressionGenerator.generateGroupSelectExpression(
                    method, ctx.groupSelectExpression(), cb, root, groupKeyExpr, ctx.capturedValues());
            method.invokeInterfaceMethod(CQ_SELECT, query, selection);
        } else {
            // Default: SELECT the grouping key
            method.invokeInterfaceMethod(CQ_SELECT, query, groupKeyExpr);
        }

        // Apply ORDER BY for group queries
        applyGroupOrderBy(method, query, root, groupKeyExpr, cb, ctx.groupSortExpressions(), ctx.capturedValues());

        // Apply DISTINCT if requested
        clauseApplier.applyDistinct(method, query, ctx.distinct());

        // Create TypedQuery
        ResultHandle typedQuery = method.invokeInterfaceMethod(EM_CREATE_QUERY, ctx.em(), query);

        // Apply pagination
        clauseApplier.applyPagination(method, typedQuery, ctx.offset(), ctx.limit());

        // Return getResultList()
        return method.invokeInterfaceMethod(TQ_GET_RESULT_LIST, typedQuery);
    }

    /** Applies WHERE predicate and generates GROUP BY key expression. */
    private ResultHandle applyWhereAndGenerateGroupKey(GroupQueryContext ctx, QuerySetup setup) {
        MethodCreator method = ctx.method();

        // Apply pre-grouping WHERE predicate if present (with subquery support)
        if (ctx.predicateExpression() != null) {
            ResultHandle predicate = expressionGenerator.generatePredicateWithSubqueries(
                    method, ctx.predicateExpression(), setup.cb(), setup.query(), setup.root(), ctx.capturedValues());
            clauseApplier.applyWherePredicate(method, setup.query(), predicate);
        }

        // Generate and return GROUP BY key expression
        return expressionGenerator.generateExpression(
                method, ctx.groupByKeyExpression(), setup.cb(), setup.root(), ctx.capturedValues());
    }

    /** Applies WHERE, GROUP BY key, and GROUP BY clause setup. */
    private ResultHandle applyGroupQuerySetup(GroupQueryContext ctx, QuerySetup setup) {
        // Apply WHERE and generate group key
        ResultHandle groupKeyExpr = applyWhereAndGenerateGroupKey(ctx, setup);

        // Apply GROUP BY
        ResultHandle groupByArray = ctx.method().newArray(Expression.class, 1);
        ctx.method().writeArrayValue(groupByArray, 0, groupKeyExpr);
        ctx.method().invokeInterfaceMethod(CQ_GROUP_BY, setup.query(), groupByArray);

        return groupKeyExpr;
    }

    /** Generates GROUP BY COUNT using COUNT(DISTINCT) or runtime result counting for HAVING. */
    private ResultHandle generateGroupCountQueryBody(GroupQueryContext ctx) {
        if (ctx.havingExpression() != null) {
            return generateGroupCountWithHaving(ctx);
        } else {
            return generateGroupCountWithoutHaving(ctx);
        }
    }

    /** Generates GROUP COUNT with HAVING (counts results at runtime). */
    private ResultHandle generateGroupCountWithHaving(GroupQueryContext ctx) {
        MethodCreator method = ctx.method();

        // With HAVING: Create query for Object (group key type may vary)
        QuerySetup setup = setupQueryForObject(method, ctx.em(), ctx.entityClass());

        // Apply WHERE + GROUP BY setup
        ResultHandle groupKeyExpr = applyGroupQuerySetup(ctx, setup);

        // Apply HAVING predicate
        ResultHandle havingPredicate = expressionGenerator.generateGroupPredicate(
                method, ctx.havingExpression(), setup.cb(), setup.root(), groupKeyExpr, ctx.capturedValues());
        applyHavingPredicate(method, setup.query(), havingPredicate);

        // SELECT groupKey (we'll count results at runtime)
        method.invokeInterfaceMethod(CQ_SELECT, setup.query(), groupKeyExpr);

        // Create TypedQuery and get result list
        ResultHandle typedQuery = method.invokeInterfaceMethod(EM_CREATE_QUERY, ctx.em(), setup.query());
        ResultHandle resultList = method.invokeInterfaceMethod(TQ_GET_RESULT_LIST, typedQuery);

        // Return result list size as Long
        ResultHandle size = method.invokeInterfaceMethod(LIST_SIZE, resultList);
        ResultHandle sizeLong = method.invokeVirtualMethod(
                INTEGER_LONG_VALUE,
                method.invokeStaticMethod(INTEGER_VALUE_OF, size));
        return method.invokeStaticMethod(LONG_VALUE_OF, sizeLong);
    }

    /** Generates GROUP COUNT without HAVING using COUNT(DISTINCT groupKey). */
    private ResultHandle generateGroupCountWithoutHaving(GroupQueryContext ctx) {
        MethodCreator method = ctx.method();

        // Without HAVING: Create query for Long (count result)
        QuerySetup setup = setupQueryForLong(method, ctx.em(), ctx.entityClass());

        // Apply WHERE and generate group key (no GROUP BY needed for COUNT DISTINCT)
        ResultHandle groupKeyExpr = applyWhereAndGenerateGroupKey(ctx, setup);

        // Simple COUNT(DISTINCT groupKey)
        ResultHandle countExpr = method.invokeInterfaceMethod(CB_COUNT_DISTINCT, setup.cb(), groupKeyExpr);
        method.invokeInterfaceMethod(CQ_SELECT, setup.query(), countExpr);

        // Create TypedQuery and return getSingleResult()
        ResultHandle typedQuery = method.invokeInterfaceMethod(EM_CREATE_QUERY, ctx.em(), setup.query());
        return method.invokeInterfaceMethod(TQ_GET_SINGLE_RESULT, typedQuery);
    }

    /**
     * Applies HAVING clause predicate to CriteriaQuery.
     */
    private void applyHavingPredicate(MethodCreator method, ResultHandle query, ResultHandle predicate) {
        if (predicate != null) {
            ResultHandle predicateArray = GizmoHelper.createElementArray(method, Predicate.class, predicate);
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

        GizmoHelper.buildOrderByClause(method, query, cb, sortExpressions,
                sortExpr -> expressionGenerator.generateGroupSortExpression(
                        method, sortExpr.keyExtractor(), cb, root, groupKeyExpr, capturedValues));
    }

    /** Generates LIST query with WHERE, SELECT, ORDER BY, and pagination. */
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

        // Both predicate and no-predicate paths share the same query execution flow
        QuerySetup setup = setupQuery(ctx.method(), ctx.em(), ctx.entityClass(), ctx.entityClass());

        // Apply WHERE predicate if present (with subquery support)
        if (predicateExpression != null) {
            ResultHandle predicate = expressionGenerator.generatePredicateWithSubqueries(
                    ctx.method(), predicateExpression, setup.cb(), setup.query(), setup.root(), ctx.capturedValues());
            clauseApplier.applyWherePredicate(ctx.method(), setup.query(), predicate);
        }

        // Execute common list query flow: ORDER BY, DISTINCT, pagination, getResultList()
        return executeListQuery(ctx, setup);
    }

    /** Executes list query: ORDER BY, DISTINCT, pagination, getResultList(). */
    private ResultHandle executeListQuery(QueryGenContext ctx, QuerySetup setup) {
        applyOrderBy(ctx.method(), setup.query(), setup.root(), setup.cb(),
                ctx.sortExpressions(), ctx.capturedValues(), null);
        clauseApplier.applyDistinct(ctx.method(), setup.query(), ctx.distinct());
        ResultHandle typedQuery = ctx.method().invokeInterfaceMethod(EM_CREATE_QUERY, ctx.em(), setup.query());
        clauseApplier.applyPagination(ctx.method(), typedQuery, ctx.offset(), ctx.limit());
        return ctx.method().invokeInterfaceMethod(TQ_GET_RESULT_LIST, typedQuery);
    }

    /** Generates COUNT query with subquery support. */
    private ResultHandle generateCountQueryBody(
            MethodCreator method,
            ResultHandle em,
            ResultHandle entityClass,
            LambdaExpression expression,
            ResultHandle capturedValues) {

        QuerySetup setup = setupQueryForLong(method, em, entityClass);
        ResultHandle cb = setup.cb();
        ResultHandle query = setup.query();
        ResultHandle root = setup.root();
        ResultHandle countExpr = method.invokeInterfaceMethod(CB_COUNT, cb, root);
        method.invokeInterfaceMethod(CQ_SELECT, query, countExpr);
        ResultHandle predicate = expressionGenerator.generatePredicateWithSubqueries(method, expression, cb, query, root, capturedValues);
        clauseApplier.applyWherePredicate(method, query, predicate);
        ResultHandle typedQuery = method.invokeInterfaceMethod(EM_CREATE_QUERY, em, query);
        return method.invokeInterfaceMethod(TQ_GET_SINGLE_RESULT, typedQuery);
    }

    /** Generates aggregation query (MIN, MAX, AVG, SUM) with optional WHERE. */
    private ResultHandle generateAggregationQueryBody(
            MethodCreator method,
            ResultHandle em,
            ResultHandle entityClass,
            LambdaExpression predicateExpression,
            LambdaExpression aggregationExpression,
            String aggregationType,
            ResultHandle capturedValues) {

        // Determine result type based on aggregation type
        Class<?> resultType = getAggregationResultType(aggregationType);
        ResultHandle resultClass = method.loadClass(resultType);

        // Setup query with the aggregation result type
        QuerySetup setup = setupQuery(method, em, entityClass, resultClass);
        ResultHandle cb = setup.cb();
        ResultHandle query = setup.query();
        ResultHandle root = setup.root();

        // Generate expression for aggregation mapper (e.g., root.get("salary"))
        ResultHandle mapperExpr = expressionGenerator.generateExpression(
                method, aggregationExpression, cb, root, capturedValues);

        // Apply aggregation function
        ResultHandle aggExpr = applyAggregationFunction(method, cb, mapperExpr, aggregationType);

        // SELECT aggregation result
        method.invokeInterfaceMethod(
                CQ_SELECT,
                query, aggExpr);

        // Apply WHERE predicate if present (with subquery support)
        if (predicateExpression != null) {
            ResultHandle predicate = expressionGenerator.generatePredicateWithSubqueries(
                    method, predicateExpression, cb, query, root, capturedValues);
            clauseApplier.applyWherePredicate(method, query, predicate);
        }

        // Create and execute query
        ResultHandle typedQuery = method.invokeInterfaceMethod(
                EM_CREATE_QUERY,
                em, query);

        return method.invokeInterfaceMethod(
                TQ_GET_SINGLE_RESULT, typedQuery);
    }

    /** Maps aggregation type to Java result type (AVG->Double, SUM->Long, etc). */
    private Class<?> getAggregationResultType(String aggregationType) {
        return switch (aggregationType) {
            case AGG_TYPE_AVG -> Double.class;           // AVG always returns Double
            case AGG_TYPE_SUM_INTEGER -> Long.class;     // SUM of integers returns Long
            case AGG_TYPE_SUM_LONG -> Long.class;        // SUM of longs returns Long
            case AGG_TYPE_SUM_DOUBLE -> Double.class;    // SUM of doubles returns Double
            case AGG_TYPE_MIN, AGG_TYPE_MAX -> Object.class;    // MIN/MAX return same type as field (use Object for now)
            default -> throw new IllegalArgumentException(unknownAggregationType(aggregationType));
        };
    }

    /** Generates cb.min(), cb.max(), cb.avg(), or cb.sum() call. */
    private ResultHandle applyAggregationFunction(
            MethodCreator method,
            ResultHandle cb,
            ResultHandle expression,
            String aggregationType) {

        return switch (aggregationType) {
            case AGG_TYPE_MIN -> method.invokeInterfaceMethod(CB_MIN, cb, expression);
            case AGG_TYPE_MAX -> method.invokeInterfaceMethod(CB_MAX, cb, expression);
            case AGG_TYPE_AVG -> method.invokeInterfaceMethod(CB_AVG, cb, expression);
            case AGG_TYPE_SUM_INTEGER -> method.invokeInterfaceMethod(CB_SUM_AS_LONG, cb, expression);  // Use sumAsLong for Integer fields
            case AGG_TYPE_SUM_LONG -> method.invokeInterfaceMethod(CB_SUM_AS_LONG, cb, expression);
            case AGG_TYPE_SUM_DOUBLE -> method.invokeInterfaceMethod(CB_SUM_AS_DOUBLE, cb, expression);
            default -> throw new IllegalArgumentException(unknownAggregationType(aggregationType));
        };
    }

    /** Generates JOIN COUNT query with relationship navigation and bi-entity predicates. */
    private ResultHandle generateJoinCountQueryBody(
            MethodCreator method,
            ResultHandle em,
            ResultHandle entityClass,
            LambdaExpression joinRelationshipExpression,
            LambdaExpression biEntityPredicateExpression,
            InvokeDynamicScanner.JoinType joinType,
            ResultHandle capturedValues) {

        // Setup query for Long (count result)
        QuerySetup setup = setupQueryForLong(method, em, entityClass);
        ResultHandle cb = setup.cb();
        ResultHandle query = setup.query();
        ResultHandle root = setup.root();

        // Extract relationship field name from join relationship expression
        String relationshipFieldName = joinRelationshipExpression.getFieldNameOrThrow();

        // Create Join by calling root.join(relationshipFieldName, JoinType)
        ResultHandle relationshipName = method.load(relationshipFieldName);
        ResultHandle jpaJoinType = GizmoHelper.loadJpaJoinType(method, joinType);
        ResultHandle joinHandle = method.invokeInterfaceMethod(FROM_JOIN, root, relationshipName, jpaJoinType);

        // SELECT COUNT(root)
        ResultHandle countExpr = method.invokeInterfaceMethod(
                CB_COUNT, cb, root);
        method.invokeInterfaceMethod(
                CQ_SELECT, query, countExpr);

        // Apply bi-entity predicate if present
        if (biEntityPredicateExpression != null) {
            ResultHandle predicate = expressionGenerator.generateBiEntityPredicateWithSubqueries(
                    method, biEntityPredicateExpression, cb, query, root, joinHandle, capturedValues);
            clauseApplier.applyWherePredicate(method, query, predicate);
        }

        // Create TypedQuery
        ResultHandle typedQuery = method.invokeInterfaceMethod(
                EM_CREATE_QUERY,
                em, query);

        // Return getSingleResult() for count
        return method.invokeInterfaceMethod(
                TQ_GET_SINGLE_RESULT, typedQuery);
    }

    /** Generates simple field projection: select(p -> p.field).toList(). */
    private ResultHandle generateSimpleFieldProjectionQuery(
            QueryGenContext ctx,
            LambdaExpression.FieldAccess fieldAccess) {

        // Load field type class and setup query
        ResultHandle fieldTypeClass = ctx.method().loadClass(fieldAccess.fieldType());
        QuerySetup setup = setupQuery(ctx.method(), ctx.em(), ctx.entityClass(), fieldTypeClass);
        ResultHandle cb = setup.cb();
        ResultHandle query = setup.query();
        ResultHandle root = setup.root();

        // Generate root.get("fieldName")
        ResultHandle fieldName = ctx.method().load(fieldAccess.fieldName());
        ResultHandle path = ctx.method().invokeInterfaceMethod(
                PATH_GET,
                root, fieldName);

        // query.select(path)
        ctx.method().invokeInterfaceMethod(
                CQ_SELECT,
                query, path);

        // Apply ORDER BY if sorting expressions present
        applyOrderBy(ctx.method(), query, root, cb, ctx.sortExpressions(), ctx.capturedValues(), path);

        // Apply DISTINCT if requested
        clauseApplier.applyDistinct(ctx.method(), query, ctx.distinct());

        // Create TypedQuery
        ResultHandle typedQuery = ctx.method().invokeInterfaceMethod(
                EM_CREATE_QUERY,
                ctx.em(), query);

        // Apply pagination
        clauseApplier.applyPagination(ctx.method(), typedQuery, ctx.offset(), ctx.limit());

        // Return getResultList()
        return ctx.method().invokeInterfaceMethod(
                TQ_GET_RESULT_LIST,
                typedQuery);
    }

    /** Generates projection query for field access or expressions. */
    private ResultHandle generateProjectionQuery(
            QueryGenContext ctx,
            LambdaExpression projectionExpression) {

        // Simple field access projection - use type-safe query
        if (projectionExpression instanceof LambdaExpression.FieldAccess fieldAccess) {
            return generateSimpleFieldProjectionQuery(ctx, fieldAccess);
        }

        // Expression projection - use Object query
        QuerySetup setup = setupQueryForObject(ctx.method(), ctx.em(), ctx.entityClass());
        ResultHandle cb = setup.cb();
        ResultHandle query = setup.query();
        ResultHandle root = setup.root();

        // Generate the projection expression as JPA Expression
        ResultHandle projectionExpr = expressionGenerator.generateExpressionAsJpaExpression(
                ctx.method(), projectionExpression, cb, root, ctx.capturedValues());

        // query.select(projectionExpr)
        ctx.method().invokeInterfaceMethod(
                CQ_SELECT,
                query, projectionExpr);

        // Apply ORDER BY if sorting expressions present
        applyOrderBy(ctx.method(), query, root, cb, ctx.sortExpressions(), ctx.capturedValues(), projectionExpr);

        // Apply DISTINCT if requested
        clauseApplier.applyDistinct(ctx.method(), query, ctx.distinct());

        // Create TypedQuery
        ResultHandle typedQuery = ctx.method().invokeInterfaceMethod(
                EM_CREATE_QUERY,
                ctx.em(), query);

        // Apply pagination
        clauseApplier.applyPagination(ctx.method(), typedQuery, ctx.offset(), ctx.limit());

        // Return getResultList()
        return ctx.method().invokeInterfaceMethod(
                TQ_GET_RESULT_LIST,
                typedQuery);
    }

    /** Generates combined WHERE + SELECT query. */
    private ResultHandle generateCombinedWhereSelectQuery(
            QueryGenContext ctx,
            LambdaExpression predicateExpression,
            LambdaExpression projectionExpression) {

        // Determine result type based on projection expression
        ResultHandle resultTypeClass;
        if (projectionExpression instanceof LambdaExpression.FieldAccess(var fieldName, var fieldType)) {
            // Field access projection - use field type for type safety
            resultTypeClass = ctx.method().loadClass(fieldType);
        } else {
            // Expression projection - use Object.class
            resultTypeClass = ctx.method().loadClass(Object.class);
        }

        // Setup query with the determined result type
        QuerySetup setup = setupQuery(ctx.method(), ctx.em(), ctx.entityClass(), resultTypeClass);
        ResultHandle cb = setup.cb();
        ResultHandle query = setup.query();
        ResultHandle root = setup.root();

        // Generate WHERE predicate (with subquery support)
        ResultHandle predicate = expressionGenerator.generatePredicateWithSubqueries(
                ctx.method(), predicateExpression, cb, query, root, ctx.capturedValues());
        clauseApplier.applyWherePredicate(ctx.method(), query, predicate);

        // Generate SELECT projection expression
        ResultHandle projectionExpr;
        if (projectionExpression instanceof LambdaExpression.FieldAccess(var projFieldName, var projFieldType)) {
            // Simple field access - root.get("fieldName")
            ResultHandle fieldNameHandle = ctx.method().load(projFieldName);
            projectionExpr = ctx.method().invokeInterfaceMethod(
                    PATH_GET,
                    root, fieldNameHandle);
        } else {
            // Expression projection - use expression generator
            ResultHandle expr = expressionGenerator.generateExpressionAsJpaExpression(
                    ctx.method(), projectionExpression, cb, root, ctx.capturedValues());
            projectionExpr = expr;
        }

        // query.select(projectionExpr)
        ctx.method().invokeInterfaceMethod(
                CQ_SELECT,
                query, projectionExpr);

        // Apply ORDER BY if sorting expressions present
        applyOrderBy(ctx.method(), query, root, cb, ctx.sortExpressions(), ctx.capturedValues(), projectionExpr);

        // Apply DISTINCT if requested
        clauseApplier.applyDistinct(ctx.method(), query, ctx.distinct());

        // Create TypedQuery
        ResultHandle typedQuery = ctx.method().invokeInterfaceMethod(
                EM_CREATE_QUERY,
                ctx.em(), query);

        // Apply pagination
        clauseApplier.applyPagination(ctx.method(), typedQuery, ctx.offset(), ctx.limit());

        // Return getResultList()
        return ctx.method().invokeInterfaceMethod(
                TQ_GET_RESULT_LIST,
                typedQuery);
    }

    /** Applies ORDER BY, using projectionExpression as fallback for identity sorts. */
    private void applyOrderBy(
            MethodCreator method,
            ResultHandle query,
            ResultHandle root,
            ResultHandle cb,
            List<?> sortExpressions,
            ResultHandle capturedValues,
            ResultHandle projectionExpression) {

        GizmoHelper.buildOrderByClause(method, query, cb, sortExpressions, sortExpr -> {
            // Generate JPA Expression for the sort key extractor
            ResultHandle sortKeyExpr = expressionGenerator.generateExpressionAsJpaExpression(
                    method, sortExpr.keyExtractor(), cb, root, capturedValues);

            // If sort key is null (identity function like s -> s after projection),
            // use the projection expression instead
            if (sortKeyExpr == null && projectionExpression != null) {
                return projectionExpression;
            }
            return sortKeyExpr;
        });
    }
}
