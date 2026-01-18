package io.quarkiverse.qubit.deployment.generation.join;

import io.quarkus.gizmo.ResultHandle;

/**
 * Strategy for join query SELECT clause: root entity, joined entity, or bi-entity projection.
 * Sealed interface for exhaustive pattern matching.
 */
public sealed interface JoinSelectionStrategy permits
        JoinSelectionStrategy.SelectRoot,
        JoinSelectionStrategy.SelectJoined,
        JoinSelectionStrategy.SelectProjection {

    /** Result class handle for CriteriaQuery (entity class or Object.class). */
    ResultHandle resultClass();

    /** True if explicit query.select() needed (not needed for root selection). */
    boolean requiresExplicitSelection();

    /** SELECT root entity (implicit, default JPA behavior). */
    record SelectRoot(ResultHandle entityClass) implements JoinSelectionStrategy {
        @Override
        public ResultHandle resultClass() {
            return entityClass;
        }

        @Override
        public boolean requiresExplicitSelection() {
            return false;
        }
    }

    /** SELECT joined entity (requires explicit query.select). */
    record SelectJoined(ResultHandle objectClass) implements JoinSelectionStrategy {
        @Override
        public ResultHandle resultClass() {
            return objectClass;
        }

        @Override
        public boolean requiresExplicitSelection() {
            return true;
        }
    }

    /** SELECT bi-entity projection (DTO or tuple). */
    record SelectProjection(
            ResultHandle objectClass,
            io.quarkiverse.qubit.deployment.ast.LambdaExpression projectionExpression
    ) implements JoinSelectionStrategy {

        @Override
        public ResultHandle resultClass() {
            return objectClass;
        }

        @Override
        public boolean requiresExplicitSelection() {
            return true;
        }
    }
}
