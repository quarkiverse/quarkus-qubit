package io.quarkiverse.qubit.deployment.generation.expression;

import io.quarkus.gizmo.MethodCreator;
import io.quarkus.gizmo.ResultHandle;

/**
 * Sealed interface for bi-entity (join) query context.
 * Provides common accessors for expression generation parameters.
 *
 * <p>Two implementations exist:
 * <ul>
 *   <li>{@link BiEntityBaseContext} - for non-subquery methods (no query handle)</li>
 *   <li>{@link BiEntitySubqueryContext} - for subquery methods (includes query handle)</li>
 * </ul>
 */
public sealed interface BiEntityContext permits BiEntityBaseContext, BiEntitySubqueryContext {

    /** Gizmo method creator for bytecode generation. */
    MethodCreator method();

    /** CriteriaBuilder handle. */
    ResultHandle cb();

    /** Root entity handle. */
    ResultHandle root();

    /** Joined entity handle. */
    ResultHandle join();

    /** Captured lambda variables array handle. */
    ResultHandle capturedValues();

    /** Expression generator helper for delegating generation. */
    ExpressionGeneratorHelper helper();
}
