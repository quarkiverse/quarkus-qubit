package io.quarkus.qusaq.deployment.generation;

import static io.quarkus.qusaq.runtime.QusaqConstants.CB_COUNT;
import static io.quarkus.qusaq.runtime.QusaqConstants.CONSTRUCTOR;
import static io.quarkus.qusaq.runtime.QusaqConstants.CQ_FROM;
import static io.quarkus.qusaq.runtime.QusaqConstants.CQ_SELECT;
import static io.quarkus.qusaq.runtime.QusaqConstants.CQ_WHERE;
import static io.quarkus.qusaq.runtime.QusaqConstants.EM_CREATE_QUERY;
import static io.quarkus.qusaq.runtime.QusaqConstants.EM_GET_CRITERIA_BUILDER;
import static io.quarkus.qusaq.runtime.QusaqConstants.QE_EXECUTE;
import static io.quarkus.qusaq.runtime.QusaqConstants.QUERY_EXECUTOR_CLASS_NAME;
import static io.quarkus.qusaq.runtime.QusaqConstants.TQ_GET_RESULT_LIST;
import static io.quarkus.qusaq.runtime.QusaqConstants.TQ_GET_SINGLE_RESULT;

import java.util.List;

import io.quarkus.gizmo.ClassCreator;
import io.quarkus.gizmo.ClassOutput;
import io.quarkus.gizmo.MethodCreator;
import io.quarkus.gizmo.MethodDescriptor;
import io.quarkus.gizmo.ResultHandle;
import io.quarkus.qusaq.deployment.LambdaExpression;
import io.quarkus.qusaq.deployment.analysis.CallSiteProcessor;
import io.quarkus.qusaq.runtime.SortDirection;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
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
            jakarta.persistence.criteria.Order.class, jakarta.persistence.criteria.Expression.class);
    private static final MethodDescriptor CB_DESC = md(CriteriaBuilder.class, "desc",
            jakarta.persistence.criteria.Order.class, jakarta.persistence.criteria.Expression.class);
    private static final MethodDescriptor CQ_ORDER_BY = md(CriteriaQuery.class, "orderBy",
            CriteriaQuery.class, jakarta.persistence.criteria.Order[].class);

    // Method descriptors for pagination
    private static final MethodDescriptor TQ_SET_FIRST_RESULT = md(TypedQuery.class, "setFirstResult",
            TypedQuery.class, int.class);
    private static final MethodDescriptor TQ_SET_MAX_RESULTS = md(TypedQuery.class, "setMaxResults",
            TypedQuery.class, int.class);

    // Method descriptor for DISTINCT
    private static final MethodDescriptor CQ_DISTINCT = md(CriteriaQuery.class, "distinct",
            CriteriaQuery.class, boolean.class);

    // Phase 5: Method descriptors for aggregation functions
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
     * Phase 2.2: Accepts both predicate and projection expressions for combined queries.
     * Phase 3: Accepts sort expressions for ORDER BY generation.
     * Phase 5: Accepts aggregation expression and type for aggregation queries.
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

        class ByteArrayClassOutput implements ClassOutput {
            private byte[] data;

            @Override
            public void write(String name, byte[] data) {
                this.data = data;
            }

            public byte[] getData() {
                return data;
            }
        }

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

            // Phase 4: Updated execute method signature to include offset, limit, and distinct parameters
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
                    // Phase 5: Aggregation queries (min, max, avg, sum*)
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
     * Generates LIST query returning em.createQuery(query).getResultList().
     * Phase 2.2: Handles combined where() + select() queries.
     * Phase 3: Handles ORDER BY for sorting.
     * Phase 4: Handles pagination via offset/limit parameters.
     */
    private ResultHandle generateListQueryBody(
            QueryGenContext ctx,
            LambdaExpression predicateExpression,
            LambdaExpression projectionExpression) {

        // Phase 2.2/2.3: Combined WHERE + SELECT query
        if (predicateExpression != null && projectionExpression != null) {
            return generateCombinedWhereSelectQuery(ctx, predicateExpression, projectionExpression);
        }

        // Phase 2.1/2.3: Projection query (select().toList())
        if (projectionExpression != null) {
            return generateProjectionQuery(ctx, projectionExpression);
        }

        // Phase 1: Predicate query (where().toList())
        if (predicateExpression != null) {
            ResultHandle cb = ctx.method().invokeInterfaceMethod(md(EntityManager.class, EM_GET_CRITERIA_BUILDER, CriteriaBuilder.class), ctx.em());
            ResultHandle query = ctx.method().invokeInterfaceMethod(md(CriteriaBuilder.class, EM_CREATE_QUERY, CriteriaQuery.class, Class.class), cb, ctx.entityClass());
            ResultHandle root = ctx.method().invokeInterfaceMethod(md(CriteriaQuery.class, CQ_FROM, Root.class, Class.class), query, ctx.entityClass());
            ResultHandle predicate = expressionGenerator.generatePredicate(ctx.method(), predicateExpression, cb, root, ctx.capturedValues());
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
        ResultHandle predicate = expressionGenerator.generatePredicate(method, expression, cb, root, capturedValues);
        applyWherePredicate(method, query, predicate);
        ResultHandle typedQuery = method.invokeInterfaceMethod(md(EntityManager.class, EM_CREATE_QUERY, TypedQuery.class, CriteriaQuery.class), em, query);
        return method.invokeInterfaceMethod(md(TypedQuery.class, TQ_GET_SINGLE_RESULT, Object.class), typedQuery);
    }

    /**
     * Generates aggregation query body (Phase 5).
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

        // Apply WHERE predicate if present
        if (predicateExpression != null) {
            ResultHandle predicate = expressionGenerator.generatePredicate(
                    method, predicateExpression, cb, root, capturedValues);
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
     * Phase 5: Maps aggregation type names to Java classes.
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
     * Phase 5: Generates cb.min(), cb.max(), cb.avg(), cb.sum() calls.
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
     * Generates simple field projection query (Phase 2.1).
     * Phase 3: Enhanced to support ORDER BY sorting.
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

        // Phase 3: Apply ORDER BY if sorting expressions present
        applyOrderBy(ctx.method(), query, root, cb, ctx.sortExpressions(), ctx.capturedValues(), path);

        // Phase 4: Apply DISTINCT if requested
        applyDistinct(ctx.method(), query, ctx.distinct());

        // Create TypedQuery
        ResultHandle typedQuery = ctx.method().invokeInterfaceMethod(
                md(EntityManager.class, EM_CREATE_QUERY, TypedQuery.class, CriteriaQuery.class),
                ctx.em(), query);

        // Phase 4: Apply pagination
        applyPagination(ctx.method(), typedQuery, ctx.offset(), ctx.limit());

        // Return getResultList()
        return ctx.method().invokeInterfaceMethod(
                md(TypedQuery.class, TQ_GET_RESULT_LIST, List.class),
                typedQuery);
    }

    /**
     * Generates projection query supporting both field access and expressions (Phase 2.1/2.3).
     * Phase 3: Enhanced to support ORDER BY sorting.
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

        // Phase 2.1: Simple field access projection - use type-safe query
        if (projectionExpression instanceof LambdaExpression.FieldAccess fieldAccess) {
            return generateSimpleFieldProjectionQuery(ctx, fieldAccess);
        }

        // Phase 2.3: Expression projection - use Object query
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

        // Phase 3: Apply ORDER BY if sorting expressions present
        applyOrderBy(ctx.method(), query, root, cb, ctx.sortExpressions(), ctx.capturedValues(), projectionExpr);

        // Phase 4: Apply DISTINCT if requested
        applyDistinct(ctx.method(), query, ctx.distinct());

        // Create TypedQuery
        ResultHandle typedQuery = ctx.method().invokeInterfaceMethod(
                md(EntityManager.class, EM_CREATE_QUERY, TypedQuery.class, CriteriaQuery.class),
                ctx.em(), query);

        // Phase 4: Apply pagination
        applyPagination(ctx.method(), typedQuery, ctx.offset(), ctx.limit());

        // Return getResultList()
        return ctx.method().invokeInterfaceMethod(
                md(TypedQuery.class, TQ_GET_RESULT_LIST, List.class),
                typedQuery);
    }

    /**
     * Generates combined WHERE + SELECT query (Phase 2.2/2.3).
     * Phase 3: Enhanced to support ORDER BY sorting.
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
            // Phase 2.2: Field access projection - use field type for type safety
            resultTypeClass = ctx.method().loadClass(fieldAccess.fieldType());
        } else {
            // Phase 2.3: Expression projection - use Object.class
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

        // Generate WHERE predicate
        ResultHandle predicate = expressionGenerator.generatePredicate(
                ctx.method(), predicateExpression, cb, root, ctx.capturedValues());
        applyWherePredicate(ctx.method(), query, predicate);

        // Generate SELECT projection expression
        ResultHandle projectionExpr;
        if (projectionExpression instanceof LambdaExpression.FieldAccess fieldAccess) {
            // Phase 2.2: Simple field access - root.get("fieldName")
            ResultHandle fieldName = ctx.method().load(fieldAccess.fieldName());
            projectionExpr = ctx.method().invokeInterfaceMethod(
                    md(Path.class, "get", Path.class, String.class),
                    root, fieldName);
        } else {
            // Phase 2.3: Expression projection - use expression generator
            projectionExpr = expressionGenerator.generateExpressionAsJpaExpression(
                    ctx.method(), projectionExpression, cb, root, ctx.capturedValues());
        }

        // query.select(projectionExpr)
        ctx.method().invokeInterfaceMethod(
                md(CriteriaQuery.class, CQ_SELECT, CriteriaQuery.class, Selection.class),
                query, projectionExpr);

        // Phase 3: Apply ORDER BY if sorting expressions present
        applyOrderBy(ctx.method(), query, root, cb, ctx.sortExpressions(), ctx.capturedValues(), projectionExpr);

        // Phase 4: Apply DISTINCT if requested
        applyDistinct(ctx.method(), query, ctx.distinct());

        // Create TypedQuery
        ResultHandle typedQuery = ctx.method().invokeInterfaceMethod(
                md(EntityManager.class, EM_CREATE_QUERY, TypedQuery.class, CriteriaQuery.class),
                ctx.em(), query);

        // Phase 4: Apply pagination
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
     * Phase 3: Generates JPA ordering from sort expressions.
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
     * Phase 3 Enhancement: For SELECT+SORT queries with identity sort functions like (String s) -> s,
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
        ResultHandle ordersArray = method.newArray(jakarta.persistence.criteria.Order.class, sortExpressions.size());

        // Generate Order objects in REVERSE order for "last call wins" semantics
        // Example: .sortedBy(firstName).sortedBy(lastName) should order by lastName first
        for (int i = 0; i < sortExpressions.size(); i++) {
            // Read from end of list (reverse order)
            int reverseIndex = sortExpressions.size() - 1 - i;
            Object sortExprObj = sortExpressions.get(reverseIndex);

            // This is a build-time object, so we can safely cast it
            if (sortExprObj instanceof CallSiteProcessor.SortExpression sortExpr) {
                // Generate JPA Expression for the sort key extractor
                ResultHandle sortKeyExpr = expressionGenerator.generateExpressionAsJpaExpression(
                        method, sortExpr.keyExtractor(), cb, root, capturedValues);

                // Phase 3: If sort key is null (identity function like s -> s after projection),
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
     * Applies DISTINCT clause to CriteriaQuery.
     * Phase 4: Implementation of distinct() support.
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
            io.quarkus.gizmo.BranchResult distinctBranch = method.ifNotNull(distinct);
            try (io.quarkus.gizmo.BytecodeCreator distinctNotNull = distinctBranch.trueBranch()) {
                // Unbox Boolean to boolean
                ResultHandle distinctValue = distinctNotNull.invokeVirtualMethod(
                        MethodDescriptor.ofMethod(Boolean.class, "booleanValue", boolean.class),
                        distinct);

                // Only call query.distinct(true) if the value is true
                io.quarkus.gizmo.BranchResult trueBranch = distinctNotNull.ifTrue(distinctValue);
                try (io.quarkus.gizmo.BytecodeCreator applyDistinct = trueBranch.trueBranch()) {
                    applyDistinct.invokeInterfaceMethod(CQ_DISTINCT, query,
                            applyDistinct.load(true));
                }
            }
        }
    }

    /**
     * Applies pagination (OFFSET and LIMIT) to TypedQuery.
     * Phase 4: Implementation of skip() and limit() support.
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
            io.quarkus.gizmo.BranchResult offsetBranch = method.ifNotNull(offset);
            try (io.quarkus.gizmo.BytecodeCreator offsetTrue = offsetBranch.trueBranch()) {
                // Unbox Integer to int
                ResultHandle offsetValue = offsetTrue.invokeVirtualMethod(
                        MethodDescriptor.ofMethod(Integer.class, "intValue", int.class),
                        offset);
                offsetTrue.invokeInterfaceMethod(TQ_SET_FIRST_RESULT, typedQuery, offsetValue);
            }
        }

        // Apply limit if present: if (limit != null) query.setMaxResults(limit);
        if (limit != null) {
            io.quarkus.gizmo.BranchResult limitBranch = method.ifNotNull(limit);
            try (io.quarkus.gizmo.BytecodeCreator limitTrue = limitBranch.trueBranch()) {
                // Unbox Integer to int
                ResultHandle limitValue = limitTrue.invokeVirtualMethod(
                        MethodDescriptor.ofMethod(Integer.class, "intValue", int.class),
                        limit);
                limitTrue.invokeInterfaceMethod(TQ_SET_MAX_RESULTS, typedQuery, limitValue);
            }
        }
    }
}
