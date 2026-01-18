package io.quarkiverse.qubit.deployment.generation.join;

import static io.quarkiverse.qubit.deployment.generation.MethodDescriptors.CB_CREATE_QUERY;
import static io.quarkiverse.qubit.deployment.generation.MethodDescriptors.CQ_FROM;
import static io.quarkiverse.qubit.deployment.generation.MethodDescriptors.CQ_SELECT;
import static io.quarkiverse.qubit.deployment.generation.MethodDescriptors.EM_CREATE_QUERY;
import static io.quarkiverse.qubit.deployment.generation.MethodDescriptors.EM_GET_CRITERIA_BUILDER;
import static io.quarkiverse.qubit.deployment.generation.MethodDescriptors.FROM_JOIN;
import static io.quarkiverse.qubit.deployment.generation.MethodDescriptors.TQ_GET_RESULT_LIST;

import io.quarkiverse.qubit.deployment.generation.CriteriaExpressionGenerator;
import io.quarkiverse.qubit.deployment.generation.GizmoHelper;
import io.quarkus.gizmo.MethodCreator;
import io.quarkus.gizmo.ResultHandle;

import java.util.Objects;

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
    public ResultHandle build(JoinQueryContext ctx, JoinSelectionStrategy strategy) {
        MethodCreator method = ctx.method();

        // Step 1: Get CriteriaBuilder
        ResultHandle cb = method.invokeInterfaceMethod(
                EM_GET_CRITERIA_BUILDER, ctx.em());

        // Step 2: Create CriteriaQuery with appropriate result class
        ResultHandle query = method.invokeInterfaceMethod(
                CB_CREATE_QUERY, cb, strategy.resultClass());

        // Step 3: Create Root<Entity>
        ResultHandle root = method.invokeInterfaceMethod(
                CQ_FROM, query, ctx.entityClass());

        // Step 4: Create Join
        ResultHandle relationshipName = method.load(ctx.relationshipFieldName());
        ResultHandle jpaJoinType = GizmoHelper.loadJpaJoinType(method, ctx.joinType());
        ResultHandle joinHandle = method.invokeInterfaceMethod(
                FROM_JOIN, root, relationshipName, jpaJoinType);

        // Step 5: Apply selection based on strategy (using pattern matching)
        applySelection(method, query, cb, root, joinHandle, ctx, strategy);

        // Step 6: Apply WHERE predicate if present
        if (ctx.hasPredicate()) {
            ResultHandle predicate = expressionGenerator.generateBiEntityPredicateWithSubqueries(
                    method, ctx.biEntityPredicateExpression(), cb, query, root, joinHandle, ctx.capturedValues());
            clauseApplier.applyWherePredicate(method, query, predicate);
        }

        // Step 7: Apply ORDER BY
        clauseApplier.applyBiEntityOrderBy(
                method, query, root, joinHandle, cb, ctx.sortExpressions(), ctx.capturedValues());

        // Step 8: Apply DISTINCT
        clauseApplier.applyDistinct(method, query, ctx.distinct());

        // Step 9: Create TypedQuery
        ResultHandle typedQuery = method.invokeInterfaceMethod(
                EM_CREATE_QUERY, ctx.em(), query);

        // Step 10: Apply pagination
        clauseApplier.applyPagination(method, typedQuery, ctx.offset(), ctx.limit());

        // Step 11: Return getResultList()
        return method.invokeInterfaceMethod(TQ_GET_RESULT_LIST, typedQuery);
    }

    private void applySelection(
            MethodCreator method,
            ResultHandle query,
            ResultHandle cb,
            ResultHandle root,
            ResultHandle joinHandle,
            JoinQueryContext ctx,
            JoinSelectionStrategy strategy) {

        switch (strategy) {
            case JoinSelectionStrategy.SelectRoot ignored -> {
                // No explicit selection needed - JPA defaults to root
            }
            case JoinSelectionStrategy.SelectJoined ignored -> {
                // SELECT the joined entity
                method.invokeInterfaceMethod(CQ_SELECT, query, joinHandle);
            }
            case JoinSelectionStrategy.SelectProjection projection -> {
                // Generate and SELECT the projection
                ResultHandle projectionResult = expressionGenerator.generateBiEntityProjection(
                        method, projection.projectionExpression(), cb, root, joinHandle, ctx.capturedValues());
                method.invokeInterfaceMethod(CQ_SELECT, query, projectionResult);
            }
        }
    }
}
