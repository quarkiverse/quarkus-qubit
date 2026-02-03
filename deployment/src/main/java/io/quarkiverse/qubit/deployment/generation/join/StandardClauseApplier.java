package io.quarkiverse.qubit.deployment.generation.join;

import static io.quarkiverse.qubit.deployment.generation.MethodDescriptors.CQ_DISTINCT;
import static io.quarkiverse.qubit.deployment.generation.MethodDescriptors.CQ_WHERE;
import static io.quarkiverse.qubit.deployment.generation.MethodDescriptors.TQ_SET_FIRST_RESULT;
import static io.quarkiverse.qubit.deployment.generation.MethodDescriptors.TQ_SET_MAX_RESULTS;

import io.quarkiverse.qubit.deployment.generation.CriteriaExpressionGenerator;
import io.quarkiverse.qubit.deployment.generation.GizmoHelper;
import io.quarkus.gizmo2.Expr;
import io.quarkus.gizmo2.creator.BlockCreator;
import jakarta.persistence.criteria.Predicate;

import java.util.List;
import java.util.Objects;

/**
 * Standard implementation generating JPA Criteria API clause bytecode. Thread-safe.
 *
 * <p>Uses Gizmo 2 API with BlockCreator and Expr types.
 */
public final class StandardClauseApplier implements QueryClauseApplier {

    private final CriteriaExpressionGenerator expressionGenerator;

    public StandardClauseApplier(CriteriaExpressionGenerator expressionGenerator) {
        this.expressionGenerator = Objects.requireNonNull(expressionGenerator,
                "expressionGenerator cannot be null");
    }

    @Override
    public void applyWherePredicate(BlockCreator bc, Expr query, Expr predicate) {
        if (predicate != null) {
            Expr predicateArray = GizmoHelper.createElementArray(bc, Predicate.class, predicate);
            bc.invokeInterface(CQ_WHERE, query, predicateArray);
        }
    }

    @Override
    public void applyBiEntityOrderBy(
            BlockCreator bc,
            Expr query,
            Expr root,
            Expr join,
            Expr cb,
            List<?> sortExpressions,
            Expr capturedValues) {

        GizmoHelper.buildOrderByClause(bc, query, cb, sortExpressions,
                sortExpr -> expressionGenerator.generateBiEntityExpressionAsJpaExpression(
                        bc, sortExpr.keyExtractor(), cb, root, join, capturedValues));
    }

    @Override
    public void applyDistinct(
            BlockCreator bc,
            Expr query,
            Expr distinct) {

        // Apply distinct if present: if (distinct != null) query.distinct(distinctValue);
        // JPA's distinct(boolean) handles both true and false cases
        if (distinct != null) {
            bc.ifNotNull(distinct, distinctNotNull -> {
                // Unbox Boolean to boolean and pass to query.distinct()
                Expr distinctValue = GizmoHelper.unboxBoolean(distinctNotNull, distinct);
                distinctNotNull.invokeInterface(CQ_DISTINCT, query, distinctValue);
            });
        }
    }

    @Override
    public void applyPagination(
            BlockCreator bc,
            Expr typedQuery,
            Expr offset,
            Expr limit) {

        // Apply offset if present: if (offset != null) query.setFirstResult(offset);
        if (offset != null) {
            bc.ifNotNull(offset, offsetTrue -> {
                // Unbox Integer to int
                Expr offsetValue = GizmoHelper.unboxInteger(offsetTrue, offset);
                offsetTrue.invokeInterface(TQ_SET_FIRST_RESULT, typedQuery, offsetValue);
            });
        }

        // Apply limit if present: if (limit != null) query.setMaxResults(limit);
        if (limit != null) {
            bc.ifNotNull(limit, limitTrue -> {
                // Unbox Integer to int
                Expr limitValue = GizmoHelper.unboxInteger(limitTrue, limit);
                limitTrue.invokeInterface(TQ_SET_MAX_RESULTS, typedQuery, limitValue);
            });
        }
    }
}
