package io.quarkiverse.qubit.deployment.generation.join;

import static io.quarkiverse.qubit.deployment.generation.MethodDescriptors.CB_CREATE_QUERY;
import static io.quarkiverse.qubit.deployment.generation.MethodDescriptors.CQ_FROM;
import static io.quarkiverse.qubit.deployment.generation.MethodDescriptors.CQ_SELECT;
import static io.quarkiverse.qubit.deployment.generation.MethodDescriptors.EM_CREATE_QUERY;
import static io.quarkiverse.qubit.deployment.generation.MethodDescriptors.EM_GET_CRITERIA_BUILDER;
import static io.quarkiverse.qubit.deployment.generation.MethodDescriptors.FROM_JOIN;
import static io.quarkiverse.qubit.deployment.generation.MethodDescriptors.TQ_GET_RESULT_LIST;

import java.util.Objects;

import io.quarkiverse.qubit.deployment.generation.CriteriaExpressionGenerator;
import io.quarkiverse.qubit.deployment.generation.GizmoHelper;
import io.quarkus.gizmo2.Const;
import io.quarkus.gizmo2.Expr;
import io.quarkus.gizmo2.LocalVar;
import io.quarkus.gizmo2.creator.BlockCreator;

/**
 * Generates join query bytecode using Template Method pattern.
 * Handles root/joined/projection selection via {@link JoinSelectionStrategy}.
 */
public final class JoinQueryBuilder {

    private final CriteriaExpressionGenerator expressionGenerator;
    private final QueryClauseApplier clauseApplier;

    public JoinQueryBuilder(CriteriaExpressionGenerator expressionGenerator, QueryClauseApplier clauseApplier) {
        this.expressionGenerator = Objects.requireNonNull(expressionGenerator, "expressionGenerator cannot be null");
        this.clauseApplier = Objects.requireNonNull(clauseApplier, "clauseApplier cannot be null");
    }

    /** Builds the join query and returns the result list handle. */
    public Expr build(JoinQueryContext ctx, JoinSelectionStrategy strategy) {
        BlockCreator bc = ctx.bc();

        // Use LocalVar to store values that are used across multiple operations (Gizmo2 requirement)
        // Step 1: Get CriteriaBuilder
        LocalVar cb = bc.localVar("cb", bc.invokeInterface(
                EM_GET_CRITERIA_BUILDER, ctx.em()));

        // Step 2: Create CriteriaQuery with appropriate result class
        LocalVar query = bc.localVar("query", bc.invokeInterface(
                CB_CREATE_QUERY, cb, strategy.resultClass()));

        // Step 3: Create Root<Entity>
        LocalVar root = bc.localVar("root", bc.invokeInterface(
                CQ_FROM, query, ctx.entityClass()));

        // Step 4: Create Join
        Expr relationshipName = Const.of(ctx.relationshipFieldName());
        Expr jpaJoinType = GizmoHelper.loadJpaJoinType(ctx.joinType());
        LocalVar joinHandle = bc.localVar("joinHandle", bc.invokeInterface(
                FROM_JOIN, root, relationshipName, jpaJoinType));

        // Step 5: Apply selection based on strategy (using pattern matching)
        applySelection(bc, query, cb, root, joinHandle, ctx, strategy);

        // Step 6: Apply WHERE predicate if present
        if (ctx.hasPredicate()) {
            Expr predicate = expressionGenerator.generateBiEntityPredicateWithSubqueries(
                    bc, ctx.biEntityPredicateExpression(), cb, query, root, joinHandle, ctx.capturedValues());
            clauseApplier.applyWherePredicate(bc, query, predicate);
        }

        // Step 7: Apply ORDER BY
        clauseApplier.applyBiEntityOrderBy(
                bc, query, root, joinHandle, cb, ctx.sortExpressions(), ctx.capturedValues());

        // Step 8: Apply DISTINCT
        clauseApplier.applyDistinct(bc, query, ctx.distinct());

        // Step 9: Create TypedQuery
        // Use LocalVar for values used across multiple operations (Gizmo2 requirement)
        LocalVar typedQuery = bc.localVar("typedQuery", bc.invokeInterface(
                EM_CREATE_QUERY, ctx.em(), query));

        // Step 10: Apply pagination
        clauseApplier.applyPagination(bc, typedQuery, ctx.offset(), ctx.limit());

        // Step 11: Return getResultList()
        return bc.invokeInterface(TQ_GET_RESULT_LIST, typedQuery);
    }

    private void applySelection(
            BlockCreator bc,
            Expr query,
            Expr cb,
            Expr root,
            Expr joinHandle,
            JoinQueryContext ctx,
            JoinSelectionStrategy strategy) {

        switch (strategy) {
            case JoinSelectionStrategy.SelectRoot _ -> {
                // No explicit selection needed - JPA defaults to root
            }
            // SELECT the joined entity
            case JoinSelectionStrategy.SelectJoined _ ->
                bc.invokeInterface(CQ_SELECT, query, joinHandle);
            case JoinSelectionStrategy.SelectProjection projection -> {
                // Generate and SELECT the projection
                Expr projectionResult = expressionGenerator.generateBiEntityProjection(
                        bc, projection.projectionExpression(), cb, root, joinHandle, ctx.capturedValues());
                bc.invokeInterface(CQ_SELECT, query, projectionResult);
            }
        }
    }
}
