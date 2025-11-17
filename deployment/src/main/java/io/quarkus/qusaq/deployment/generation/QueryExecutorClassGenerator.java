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
     * Generates query executor class bytecode from lambda expression.
     */
    public byte[] generateQueryExecutorClass(
            LambdaExpression lambdaExpression,
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
                    result = generateCountQueryBody(execute, em, entityClassParam, lambdaExpression, capturedValues);
                } else {
                    result = generateListQueryBody(execute, em, entityClassParam, lambdaExpression, capturedValues);
                }

                execute.returnValue(result);
            }
        }

        return classOutput.getData();
    }

    /**
     * Generates LIST query returning em.createQuery(query).getResultList().
     */
    private ResultHandle generateListQueryBody(
            MethodCreator method,
            ResultHandle em,
            ResultHandle entityClass,
            LambdaExpression expression,
            ResultHandle capturedValues) {

        ResultHandle cb = method.invokeInterfaceMethod(md(EntityManager.class, EM_GET_CRITERIA_BUILDER, CriteriaBuilder.class), em);
        ResultHandle query = method.invokeInterfaceMethod(md(CriteriaBuilder.class, EM_CREATE_QUERY, CriteriaQuery.class, Class.class), cb, entityClass);
        ResultHandle root = method.invokeInterfaceMethod(md(CriteriaQuery.class, CQ_FROM, Root.class, Class.class), query, entityClass);
        ResultHandle predicate = expressionGenerator.generatePredicate(method, expression, cb, root, capturedValues);
        applyWherePredicate(method, query, predicate);
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
