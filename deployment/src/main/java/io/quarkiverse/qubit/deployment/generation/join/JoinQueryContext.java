package io.quarkiverse.qubit.deployment.generation.join;

import io.quarkiverse.qubit.deployment.analysis.InvokeDynamicScanner;
import io.quarkiverse.qubit.deployment.analysis.LambdaAnalysisResult.SortExpression;
import io.quarkiverse.qubit.deployment.ast.LambdaExpression;
import io.quarkus.gizmo.MethodCreator;
import io.quarkus.gizmo.ResultHandle;

import java.util.List;
import java.util.Objects;

/**
 * Immutable context consolidating all parameters for join query bytecode generation.
 */
public record JoinQueryContext(
        MethodCreator method,
        ResultHandle em,
        ResultHandle entityClass,
        LambdaExpression joinRelationshipExpression,
        LambdaExpression biEntityPredicateExpression,
        InvokeDynamicScanner.JoinType joinType,
        List<SortExpression> sortExpressions,
        ResultHandle capturedValues,
        ResultHandle offset,
        ResultHandle limit,
        ResultHandle distinct
) {

    public JoinQueryContext {
        Objects.requireNonNull(method, "method cannot be null");
        Objects.requireNonNull(em, "em cannot be null");
        Objects.requireNonNull(entityClass, "entityClass cannot be null");
        Objects.requireNonNull(joinRelationshipExpression, "joinRelationshipExpression cannot be null");
        Objects.requireNonNull(joinType, "joinType cannot be null");
        // biEntityPredicateExpression, sortExpressions, capturedValues, offset, limit, distinct can be null
    }

    /** Extracts field name from join relationship (e.g., "phones" from p -> p.phones). */
    public String relationshipFieldName() {
        return joinRelationshipExpression.getFieldNameOrThrow();
    }

    public boolean hasPredicate() {
        return biEntityPredicateExpression != null;
    }

    public boolean hasSorting() {
        return sortExpressions != null && !sortExpressions.isEmpty();
    }
}
