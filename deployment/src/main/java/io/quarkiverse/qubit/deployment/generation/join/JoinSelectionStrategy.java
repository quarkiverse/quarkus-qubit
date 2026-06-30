package io.quarkiverse.qubit.deployment.generation.join;

import io.quarkus.gizmo2.Expr;

/**
 * Strategy for join query SELECT clause: root entity, joined entity, or bi-entity projection.
 * Sealed interface for exhaustive pattern matching.
 */
public sealed interface JoinSelectionStrategy permits
        JoinSelectionStrategy.SelectRoot,
        JoinSelectionStrategy.SelectJoined,
        JoinSelectionStrategy.SelectProjection {

    /** Result class handle for CriteriaQuery (entity class or Object.class). */
    Expr resultClass();

    /** SELECT root entity (implicit, default JPA behavior). */
    record SelectRoot(Expr entityClass) implements JoinSelectionStrategy {
        @Override
        public Expr resultClass() {
            return entityClass;
        }
    }

    /** SELECT joined entity (requires explicit query.select). */
    record SelectJoined(Expr objectClass) implements JoinSelectionStrategy {
        @Override
        public Expr resultClass() {
            return objectClass;
        }
    }

    /** SELECT bi-entity projection (DTO or tuple). */
    record SelectProjection(
            Expr objectClass,
            io.quarkiverse.qubit.deployment.ast.LambdaExpression projectionExpression) implements JoinSelectionStrategy {

        @Override
        public Expr resultClass() {
            return objectClass;
        }
    }
}
