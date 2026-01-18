package io.quarkiverse.qubit.deployment.generation.join;

import static io.quarkiverse.qubit.deployment.generation.MethodDescriptors.BOOLEAN_BOOLEAN_VALUE;
import static io.quarkiverse.qubit.deployment.generation.MethodDescriptors.CQ_DISTINCT;
import static io.quarkiverse.qubit.deployment.generation.MethodDescriptors.CQ_WHERE;
import static io.quarkiverse.qubit.deployment.generation.MethodDescriptors.INTEGER_INT_VALUE;
import static io.quarkiverse.qubit.deployment.generation.MethodDescriptors.TQ_SET_FIRST_RESULT;
import static io.quarkiverse.qubit.deployment.generation.MethodDescriptors.TQ_SET_MAX_RESULTS;

import io.quarkiverse.qubit.deployment.generation.CriteriaExpressionGenerator;
import io.quarkiverse.qubit.deployment.generation.GizmoHelper;
import io.quarkus.gizmo.BranchResult;
import io.quarkus.gizmo.BytecodeCreator;
import io.quarkus.gizmo.MethodCreator;
import io.quarkus.gizmo.ResultHandle;
import jakarta.persistence.criteria.Predicate;

import java.util.List;
import java.util.Objects;

/** Standard implementation generating JPA Criteria API clause bytecode. Thread-safe. */
public final class StandardClauseApplier implements QueryClauseApplier {

    private final CriteriaExpressionGenerator expressionGenerator;

    public StandardClauseApplier(CriteriaExpressionGenerator expressionGenerator) {
        this.expressionGenerator = Objects.requireNonNull(expressionGenerator,
                "expressionGenerator cannot be null");
    }

    @Override
    public void applyWherePredicate(MethodCreator method, ResultHandle query, ResultHandle predicate) {
        if (predicate != null) {
            ResultHandle predicateArray = GizmoHelper.createElementArray(method, Predicate.class, predicate);
            method.invokeInterfaceMethod(CQ_WHERE, query, predicateArray);
        }
    }

    @Override
    public void applyBiEntityOrderBy(
            MethodCreator method,
            ResultHandle query,
            ResultHandle root,
            ResultHandle join,
            ResultHandle cb,
            List<?> sortExpressions,
            ResultHandle capturedValues) {

        GizmoHelper.buildOrderByClause(method, query, cb, sortExpressions,
                sortExpr -> expressionGenerator.generateBiEntityExpressionAsJpaExpression(
                        method, sortExpr.keyExtractor(), cb, root, join, capturedValues));
    }

    @Override
    public void applyDistinct(
            MethodCreator method,
            ResultHandle query,
            ResultHandle distinct) {

        // Apply distinct if present and true: if (distinct != null && distinct) query.distinct(true);
        if (distinct != null) {
            BranchResult distinctBranch = method.ifNotNull(distinct);
            try (BytecodeCreator distinctNotNull = distinctBranch.trueBranch()) {
                // Unbox Boolean to boolean
                ResultHandle distinctValue = distinctNotNull.invokeVirtualMethod(BOOLEAN_BOOLEAN_VALUE, distinct);

                // Only call query.distinct(true) if the value is true
                BranchResult trueBranch = distinctNotNull.ifTrue(distinctValue);
                try (BytecodeCreator applyDistinctBlock = trueBranch.trueBranch()) {
                    applyDistinctBlock.invokeInterfaceMethod(CQ_DISTINCT, query,
                            applyDistinctBlock.load(true));
                }
            }
        }
    }

    @Override
    public void applyPagination(
            MethodCreator method,
            ResultHandle typedQuery,
            ResultHandle offset,
            ResultHandle limit) {

        // Apply offset if present: if (offset != null) query.setFirstResult(offset);
        if (offset != null) {
            BranchResult offsetBranch = method.ifNotNull(offset);
            try (BytecodeCreator offsetTrue = offsetBranch.trueBranch()) {
                // Unbox Integer to int
                ResultHandle offsetValue = offsetTrue.invokeVirtualMethod(INTEGER_INT_VALUE, offset);
                offsetTrue.invokeInterfaceMethod(TQ_SET_FIRST_RESULT, typedQuery, offsetValue);
            }
        }

        // Apply limit if present: if (limit != null) query.setMaxResults(limit);
        if (limit != null) {
            BranchResult limitBranch = method.ifNotNull(limit);
            try (BytecodeCreator limitTrue = limitBranch.trueBranch()) {
                // Unbox Integer to int
                ResultHandle limitValue = limitTrue.invokeVirtualMethod(INTEGER_INT_VALUE, limit);
                limitTrue.invokeInterfaceMethod(TQ_SET_MAX_RESULTS, typedQuery, limitValue);
            }
        }
    }
}
