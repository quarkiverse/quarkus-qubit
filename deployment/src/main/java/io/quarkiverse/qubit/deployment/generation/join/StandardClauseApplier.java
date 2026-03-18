package io.quarkiverse.qubit.deployment.generation.join;

import static io.quarkiverse.qubit.deployment.generation.MethodDescriptors.CQ_DISTINCT;
import static io.quarkiverse.qubit.deployment.generation.MethodDescriptors.CQ_WHERE;
import static io.quarkiverse.qubit.deployment.generation.MethodDescriptors.TQ_SET_FIRST_RESULT;
import static io.quarkiverse.qubit.deployment.generation.MethodDescriptors.TQ_SET_MAX_RESULTS;

import java.util.List;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

import jakarta.persistence.criteria.Predicate;

import io.quarkiverse.qubit.deployment.generation.CriteriaExpressionGenerator;
import io.quarkiverse.qubit.deployment.generation.GizmoHelper;
import io.quarkus.gizmo2.Expr;
import io.quarkus.gizmo2.creator.BlockCreator;

/**
 * Standard implementation generating JPA Criteria API clause bytecode. Thread-safe.
 */
public final class StandardClauseApplier implements QueryClauseApplier {

    private final CriteriaExpressionGenerator expressionGenerator;

    public StandardClauseApplier(CriteriaExpressionGenerator expressionGenerator) {
        this.expressionGenerator = Objects.requireNonNull(expressionGenerator,
                "expressionGenerator cannot be null");
    }

    @Override
    public void applyWherePredicate(BlockCreator bc, Expr query, @Nullable Expr predicate) {
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

        // Runtime null check: the Expr handle is non-null, but the runtime Boolean value may be null
        bc.ifNotNull(distinct, distinctNotNull -> {
            Expr distinctValue = GizmoHelper.unboxBoolean(distinctNotNull, distinct);
            distinctNotNull.invokeInterface(CQ_DISTINCT, query, distinctValue);
        });
    }

    @Override
    public void applyPagination(
            BlockCreator bc,
            Expr typedQuery,
            Expr offset,
            Expr limit) {

        // Runtime null check: the Expr handle is non-null, but the runtime Integer value may be null
        bc.ifNotNull(offset, offsetTrue -> {
            Expr offsetValue = GizmoHelper.unboxInteger(offsetTrue, offset);
            offsetTrue.invokeInterface(TQ_SET_FIRST_RESULT, typedQuery, offsetValue);
        });

        bc.ifNotNull(limit, limitTrue -> {
            Expr limitValue = GizmoHelper.unboxInteger(limitTrue, limit);
            limitTrue.invokeInterface(TQ_SET_MAX_RESULTS, typedQuery, limitValue);
        });
    }
}
