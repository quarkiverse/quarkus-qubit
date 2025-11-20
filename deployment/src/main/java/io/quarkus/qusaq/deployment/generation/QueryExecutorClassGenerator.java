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

    /**
     * Generates query executor class bytecode from lambda expressions.
     * Phase 2.2: Accepts both predicate and projection expressions for combined queries.
     */
    public byte[] generateQueryExecutorClass(
            LambdaExpression predicateExpression,
            LambdaExpression projectionExpression,
            String className,
            boolean isCountQuery) {

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

            try (MethodCreator execute = classCreator.getMethodCreator(
                    QE_EXECUTE, Object.class, EntityManager.class, Class.class, Object[].class)) {

                ResultHandle em = execute.getMethodParam(0);
                ResultHandle entityClassParam = execute.getMethodParam(1);
                ResultHandle capturedValues = execute.getMethodParam(2);

                ResultHandle result;
                if (isCountQuery) {
                    result = generateCountQueryBody(execute, em, entityClassParam, predicateExpression, capturedValues);
                } else {
                    result = generateListQueryBody(execute, em, entityClassParam,
                            predicateExpression, projectionExpression, capturedValues);
                }

                execute.returnValue(result);
            }
        }

        return classOutput.getData();
    }

    /**
     * Generates LIST query returning em.createQuery(query).getResultList().
     * Phase 2.2: Handles combined where() + select() queries.
     */
    private ResultHandle generateListQueryBody(
            MethodCreator method,
            ResultHandle em,
            ResultHandle entityClass,
            LambdaExpression predicateExpression,
            LambdaExpression projectionExpression,
            ResultHandle capturedValues) {

        // Phase 2.2/2.3: Combined WHERE + SELECT query
        if (predicateExpression != null && projectionExpression != null) {
            return generateCombinedWhereSelectQuery(method, em, entityClass,
                    predicateExpression, projectionExpression, capturedValues);
        }

        // Phase 2.1/2.3: Projection query (select().toList())
        if (projectionExpression != null) {
            return generateProjectionQuery(method, em, entityClass, projectionExpression, capturedValues);
        }

        // Phase 1: Predicate query (where().toList())
        if (predicateExpression != null) {
            ResultHandle cb = method.invokeInterfaceMethod(md(EntityManager.class, EM_GET_CRITERIA_BUILDER, CriteriaBuilder.class), em);
            ResultHandle query = method.invokeInterfaceMethod(md(CriteriaBuilder.class, EM_CREATE_QUERY, CriteriaQuery.class, Class.class), cb, entityClass);
            ResultHandle root = method.invokeInterfaceMethod(md(CriteriaQuery.class, CQ_FROM, Root.class, Class.class), query, entityClass);
            ResultHandle predicate = expressionGenerator.generatePredicate(method, predicateExpression, cb, root, capturedValues);
            applyWherePredicate(method, query, predicate);
            ResultHandle typedQuery = method.invokeInterfaceMethod(md(EntityManager.class, EM_CREATE_QUERY, TypedQuery.class, CriteriaQuery.class), em, query);
            return method.invokeInterfaceMethod(md(TypedQuery.class, TQ_GET_RESULT_LIST, List.class), typedQuery);
        }

        // No predicate or projection - return all entities
        ResultHandle cb = method.invokeInterfaceMethod(md(EntityManager.class, EM_GET_CRITERIA_BUILDER, CriteriaBuilder.class), em);
        ResultHandle query = method.invokeInterfaceMethod(md(CriteriaBuilder.class, EM_CREATE_QUERY, CriteriaQuery.class, Class.class), cb, entityClass);
        method.invokeInterfaceMethod(md(CriteriaQuery.class, CQ_FROM, Root.class, Class.class), query, entityClass);
        ResultHandle typedQuery = method.invokeInterfaceMethod(md(EntityManager.class, EM_CREATE_QUERY, TypedQuery.class, CriteriaQuery.class), em, query);
        return method.invokeInterfaceMethod(md(TypedQuery.class, TQ_GET_RESULT_LIST, List.class), typedQuery);
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
     * Generates simple field projection query (Phase 2.1).
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
            MethodCreator method,
            ResultHandle em,
            ResultHandle entityClass,
            LambdaExpression.FieldAccess fieldAccess) {

        // Get CriteriaBuilder
        ResultHandle cb = method.invokeInterfaceMethod(
                md(EntityManager.class, EM_GET_CRITERIA_BUILDER, CriteriaBuilder.class), em);

        // Load field type class
        ResultHandle fieldTypeClass = method.loadClass(fieldAccess.fieldType());

        // Create CriteriaQuery<FieldType>
        ResultHandle query = method.invokeInterfaceMethod(
                md(CriteriaBuilder.class, EM_CREATE_QUERY, CriteriaQuery.class, Class.class),
                cb, fieldTypeClass);

        // Create Root<Entity>
        ResultHandle root = method.invokeInterfaceMethod(
                md(CriteriaQuery.class, CQ_FROM, Root.class, Class.class),
                query, entityClass);

        // Generate root.get("fieldName")
        ResultHandle fieldName = method.load(fieldAccess.fieldName());
        ResultHandle path = method.invokeInterfaceMethod(
                md(Path.class, "get", Path.class, String.class),
                root, fieldName);

        // query.select(path)
        method.invokeInterfaceMethod(
                md(CriteriaQuery.class, CQ_SELECT, CriteriaQuery.class, Selection.class),
                query, path);

        // Create TypedQuery
        ResultHandle typedQuery = method.invokeInterfaceMethod(
                md(EntityManager.class, EM_CREATE_QUERY, TypedQuery.class, CriteriaQuery.class),
                em, query);

        // Return getResultList()
        return method.invokeInterfaceMethod(
                md(TypedQuery.class, TQ_GET_RESULT_LIST, List.class),
                typedQuery);
    }

    /**
     * Generates projection query supporting both field access and expressions (Phase 2.1/2.3).
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
            MethodCreator method,
            ResultHandle em,
            ResultHandle entityClass,
            LambdaExpression projectionExpression,
            ResultHandle capturedValues) {

        // Phase 2.1: Simple field access projection - use type-safe query
        if (projectionExpression instanceof LambdaExpression.FieldAccess fieldAccess) {
            return generateSimpleFieldProjectionQuery(method, em, entityClass, fieldAccess);
        }

        // Phase 2.3: Expression projection - use Object query
        // Get CriteriaBuilder
        ResultHandle cb = method.invokeInterfaceMethod(
                md(EntityManager.class, EM_GET_CRITERIA_BUILDER, CriteriaBuilder.class), em);

        // Use Object.class for expression projections
        ResultHandle objectClass = method.loadClass(Object.class);

        // Create CriteriaQuery<Object>
        ResultHandle query = method.invokeInterfaceMethod(
                md(CriteriaBuilder.class, EM_CREATE_QUERY, CriteriaQuery.class, Class.class),
                cb, objectClass);

        // Create Root<Entity>
        ResultHandle root = method.invokeInterfaceMethod(
                md(CriteriaQuery.class, CQ_FROM, Root.class, Class.class),
                query, entityClass);

        // Generate the projection expression as JPA Expression
        ResultHandle projectionExpr = expressionGenerator.generateExpressionAsJpaExpression(
                method, projectionExpression, cb, root, capturedValues);

        // query.select(projectionExpr)
        method.invokeInterfaceMethod(
                md(CriteriaQuery.class, CQ_SELECT, CriteriaQuery.class, Selection.class),
                query, projectionExpr);

        // Create TypedQuery
        ResultHandle typedQuery = method.invokeInterfaceMethod(
                md(EntityManager.class, EM_CREATE_QUERY, TypedQuery.class, CriteriaQuery.class),
                em, query);

        // Return getResultList()
        return method.invokeInterfaceMethod(
                md(TypedQuery.class, TQ_GET_RESULT_LIST, List.class),
                typedQuery);
    }

    /**
     * Generates combined WHERE + SELECT query (Phase 2.2/2.3).
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
            MethodCreator method,
            ResultHandle em,
            ResultHandle entityClass,
            LambdaExpression predicateExpression,
            LambdaExpression projectionExpression,
            ResultHandle capturedValues) {

        // Get CriteriaBuilder
        ResultHandle cb = method.invokeInterfaceMethod(
                md(EntityManager.class, EM_GET_CRITERIA_BUILDER, CriteriaBuilder.class), em);

        // Determine result type based on projection expression
        ResultHandle resultTypeClass;
        if (projectionExpression instanceof LambdaExpression.FieldAccess fieldAccess) {
            // Phase 2.2: Field access projection - use field type for type safety
            resultTypeClass = method.loadClass(fieldAccess.fieldType());
        } else {
            // Phase 2.3: Expression projection - use Object.class
            resultTypeClass = method.loadClass(Object.class);
        }

        // Create CriteriaQuery<ResultType>
        ResultHandle query = method.invokeInterfaceMethod(
                md(CriteriaBuilder.class, EM_CREATE_QUERY, CriteriaQuery.class, Class.class),
                cb, resultTypeClass);

        // Create Root<Entity>
        ResultHandle root = method.invokeInterfaceMethod(
                md(CriteriaQuery.class, CQ_FROM, Root.class, Class.class),
                query, entityClass);

        // Generate WHERE predicate
        ResultHandle predicate = expressionGenerator.generatePredicate(
                method, predicateExpression, cb, root, capturedValues);
        applyWherePredicate(method, query, predicate);

        // Generate SELECT projection expression
        ResultHandle projectionExpr;
        if (projectionExpression instanceof LambdaExpression.FieldAccess fieldAccess) {
            // Phase 2.2: Simple field access - root.get("fieldName")
            ResultHandle fieldName = method.load(fieldAccess.fieldName());
            projectionExpr = method.invokeInterfaceMethod(
                    md(Path.class, "get", Path.class, String.class),
                    root, fieldName);
        } else {
            // Phase 2.3: Expression projection - use expression generator
            projectionExpr = expressionGenerator.generateExpressionAsJpaExpression(
                    method, projectionExpression, cb, root, capturedValues);
        }

        // query.select(projectionExpr)
        method.invokeInterfaceMethod(
                md(CriteriaQuery.class, CQ_SELECT, CriteriaQuery.class, Selection.class),
                query, projectionExpr);

        // Create TypedQuery
        ResultHandle typedQuery = method.invokeInterfaceMethod(
                md(EntityManager.class, EM_CREATE_QUERY, TypedQuery.class, CriteriaQuery.class),
                em, query);

        // Return getResultList()
        return method.invokeInterfaceMethod(
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
}
