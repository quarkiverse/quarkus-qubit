package io.quarkiverse.qubit.deployment.generation;

import static io.quarkiverse.qubit.deployment.common.ExceptionMessages.unknownAggregationType;
import static io.quarkiverse.qubit.deployment.generation.MethodDescriptors.CB_AVG;
import static io.quarkiverse.qubit.deployment.generation.MethodDescriptors.CB_MAX;
import static io.quarkiverse.qubit.deployment.generation.MethodDescriptors.CB_MIN;
import static io.quarkiverse.qubit.deployment.generation.MethodDescriptors.CB_SUM_AS_DOUBLE;
import static io.quarkiverse.qubit.deployment.generation.MethodDescriptors.CB_SUM_AS_LONG;
import static io.quarkiverse.qubit.deployment.generation.MethodDescriptors.CQ_SELECT;
import static io.quarkiverse.qubit.deployment.generation.MethodDescriptors.EM_CREATE_QUERY;
import static io.quarkiverse.qubit.deployment.generation.MethodDescriptors.TQ_GET_SINGLE_RESULT;
import static io.quarkiverse.qubit.runtime.internal.QubitConstants.AGG_TYPE_AVG;
import static io.quarkiverse.qubit.runtime.internal.QubitConstants.AGG_TYPE_MAX;
import static io.quarkiverse.qubit.runtime.internal.QubitConstants.AGG_TYPE_MIN;
import static io.quarkiverse.qubit.runtime.internal.QubitConstants.AGG_TYPE_SUM_DOUBLE;
import static io.quarkiverse.qubit.runtime.internal.QubitConstants.AGG_TYPE_SUM_INTEGER;
import static io.quarkiverse.qubit.runtime.internal.QubitConstants.AGG_TYPE_SUM_LONG;

import io.quarkiverse.qubit.deployment.ast.LambdaExpression;
import io.quarkiverse.qubit.deployment.generation.QueryExecutorClassGenerator.QuerySetup;
import io.quarkiverse.qubit.deployment.generation.join.StandardClauseApplier;
import io.quarkus.gizmo2.Const;
import io.quarkus.gizmo2.Expr;
import io.quarkus.gizmo2.creator.BlockCreator;

/**
 * Generates aggregation query bytecode (MIN, MAX, AVG, SUM) for JPA Criteria API queries.
 *
 * <p>
 * Handles aggregation queries with optional WHERE predicates. Extracted from
 * {@link QueryExecutorClassGenerator} to separate aggregation-specific logic.
 */
final class AggregationQueryGenerator {

    private final CriteriaExpressionGenerator expressionGenerator;
    private final StandardClauseApplier clauseApplier;
    private final QueryExecutorClassGenerator parent;

    AggregationQueryGenerator(
            CriteriaExpressionGenerator expressionGenerator,
            StandardClauseApplier clauseApplier,
            QueryExecutorClassGenerator parent) {
        this.expressionGenerator = expressionGenerator;
        this.clauseApplier = clauseApplier;
        this.parent = parent;
    }

    /** Generates aggregation query (MIN, MAX, AVG, SUM) with optional WHERE. */
    Expr generateAggregationQueryBody(
            BlockCreator bc,
            Expr em,
            Expr entityClass,
            LambdaExpression predicateExpression,
            LambdaExpression aggregationExpression,
            String aggregationType,
            Expr capturedValues) {

        // Determine result type based on aggregation type
        Class<?> resultType = getAggregationResultType(aggregationType);
        Expr resultClass = Const.of(resultType);

        // Setup query with the aggregation result type
        QuerySetup setup = parent.setupQuery(bc, em, entityClass, resultClass);
        Expr cb = setup.cb();
        Expr query = setup.query();
        Expr root = setup.root();

        // Generate expression for aggregation mapper (e.g., root.get("salary"))
        Expr mapperExpr = expressionGenerator.generateExpression(
                bc, aggregationExpression, cb, root, capturedValues);

        // Apply aggregation function
        Expr aggExpr = applyAggregationFunction(bc, cb, mapperExpr, aggregationType);

        // SELECT aggregation result
        bc.invokeInterface(CQ_SELECT, query, aggExpr);

        // Apply WHERE predicate if present (with subquery support)
        if (predicateExpression != null) {
            Expr predicate = expressionGenerator.generatePredicateWithSubqueries(
                    bc, predicateExpression, cb, query, root, capturedValues);
            clauseApplier.applyWherePredicate(bc, query, predicate);
        }

        // Create and execute query
        Expr typedQuery = bc.invokeInterface(EM_CREATE_QUERY, em, query);

        return bc.invokeInterface(TQ_GET_SINGLE_RESULT, typedQuery);
    }

    /** Maps aggregation type to Java result type (AVG->Double, SUM->Long, etc). */
    private Class<?> getAggregationResultType(String aggregationType) {
        return switch (aggregationType) {
            case AGG_TYPE_AVG -> Double.class; // AVG always returns Double
            case AGG_TYPE_SUM_INTEGER -> Long.class; // SUM of integers returns Long
            case AGG_TYPE_SUM_LONG -> Long.class; // SUM of longs returns Long
            case AGG_TYPE_SUM_DOUBLE -> Double.class; // SUM of doubles returns Double
            case AGG_TYPE_MIN, AGG_TYPE_MAX -> Object.class; // MIN/MAX return same type as field (use Object for now)
            default -> throw new IllegalArgumentException(unknownAggregationType(aggregationType));
        };
    }

    /** Generates cb.min(), cb.max(), cb.avg(), or cb.sum() call. */
    private Expr applyAggregationFunction(
            BlockCreator bc,
            Expr cb,
            Expr expression,
            String aggregationType) {

        return switch (aggregationType) {
            case AGG_TYPE_MIN -> bc.invokeInterface(CB_MIN, cb, expression);
            case AGG_TYPE_MAX -> bc.invokeInterface(CB_MAX, cb, expression);
            case AGG_TYPE_AVG -> bc.invokeInterface(CB_AVG, cb, expression);
            case AGG_TYPE_SUM_INTEGER -> bc.invokeInterface(CB_SUM_AS_LONG, cb, expression); // Use sumAsLong for Integer fields
            case AGG_TYPE_SUM_LONG -> bc.invokeInterface(CB_SUM_AS_LONG, cb, expression);
            case AGG_TYPE_SUM_DOUBLE -> bc.invokeInterface(CB_SUM_AS_DOUBLE, cb, expression);
            default -> throw new IllegalArgumentException(unknownAggregationType(aggregationType));
        };
    }
}
