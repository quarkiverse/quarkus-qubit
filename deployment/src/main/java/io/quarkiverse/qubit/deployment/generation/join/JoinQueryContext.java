package io.quarkiverse.qubit.deployment.generation.join;

import java.util.List;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

import io.quarkiverse.qubit.deployment.analysis.CallSite;
import io.quarkiverse.qubit.deployment.analysis.LambdaAnalysisResult.SortExpression;
import io.quarkiverse.qubit.deployment.ast.LambdaExpression;
import io.quarkus.gizmo2.Expr;
import io.quarkus.gizmo2.creator.BlockCreator;

/**
 * Immutable context consolidating all parameters for join query bytecode generation.
 */
public record JoinQueryContext(
        BlockCreator bc,
        Expr em,
        Expr entityClass,
        LambdaExpression joinRelationshipExpression,
        @Nullable LambdaExpression sourcePredicateExpression,
        @Nullable LambdaExpression biEntityPredicateExpression,
        CallSite.JoinType joinType,
        @Nullable List<SortExpression> sortExpressions,
        Expr capturedValues,
        Expr offset,
        Expr limit,
        Expr distinct) {

    public JoinQueryContext {
        Objects.requireNonNull(bc, "bc cannot be null");
        Objects.requireNonNull(em, "em cannot be null");
        Objects.requireNonNull(entityClass, "entityClass cannot be null");
        Objects.requireNonNull(joinRelationshipExpression, "joinRelationshipExpression cannot be null");
        Objects.requireNonNull(joinType, "joinType cannot be null");
        Objects.requireNonNull(capturedValues, "capturedValues cannot be null");
        Objects.requireNonNull(offset, "offset cannot be null");
        Objects.requireNonNull(limit, "limit cannot be null");
        Objects.requireNonNull(distinct, "distinct cannot be null");
    }

    /** Extracts field name from join relationship (e.g., "phones" from p -> p.phones). */
    public String relationshipFieldName() {
        return joinRelationshipExpression.getFieldNameOrThrow();
    }

    public boolean hasSourcePredicate() {
        return sourcePredicateExpression != null;
    }

    public boolean hasPredicate() {
        return biEntityPredicateExpression != null;
    }

    public boolean hasSorting() {
        return sortExpressions != null && !sortExpressions.isEmpty();
    }
}
