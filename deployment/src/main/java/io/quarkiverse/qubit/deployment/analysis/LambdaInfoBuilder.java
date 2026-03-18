package io.quarkiverse.qubit.deployment.analysis;

import static io.quarkiverse.qubit.runtime.internal.QubitConstants.*;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import io.quarkiverse.qubit.SortDirection;
import io.quarkiverse.qubit.deployment.analysis.CallSite.LambdaPair;
import io.quarkiverse.qubit.deployment.analysis.CallSite.SortLambda;
import io.quarkiverse.qubit.deployment.analysis.InvokeDynamicScanner.PendingLambda;

/**
 * Mutable builder for LambdaInfo. Encapsulates the classification logic
 * that was previously a "Brain Method" in extractLambdaInfo.
 *
 * <p>
 * Each classify* method handles one category of lambda, returning true
 * if the lambda was handled (allowing early exit from the if-else chain).
 */
final class LambdaInfoBuilder {
    // Collection fields
    private final List<LambdaPair> whereLambdas = new ArrayList<>();
    private final List<SortLambda> sortLambdas = new ArrayList<>();
    private final List<LambdaPair> biEntityWhereLambdas = new ArrayList<>();
    private final List<LambdaPair> havingLambdas = new ArrayList<>();
    private final List<LambdaPair> groupSelectLambdas = new ArrayList<>();
    private final List<SortLambda> groupSortLambdas = new ArrayList<>();

    // Single-value fields (coupled method+descriptor pairs)
    private @Nullable LambdaPair groupByLambda;
    private @Nullable LambdaPair firstWhereLambda;
    private @Nullable LambdaPair selectLambda;
    private @Nullable LambdaPair aggregationLambda;
    private @Nullable LambdaPair joinRelationshipLambda;
    private @Nullable LambdaPair biEntityProjectionLambda;

    // Context
    private final boolean isGroupQuery;

    LambdaInfoBuilder(boolean isGroupQuery) {
        this.isGroupQuery = isGroupQuery;
    }

    /** Classifies a lambda and updates the appropriate field. Returns true if handled. */
    void classify(PendingLambda lambda, boolean isAggregation, boolean isLastLambda) {
        // Aggregation mapper (must be checked first - last lambda in aggregation query)
        if (isAggregation && isLastLambda) {
            aggregationLambda = new LambdaPair(lambda.methodName(), lambda.descriptor());
            return;
        }

        String fluentMethod = lambda.fluentMethod();

        // Dispatch based on fluent method name
        if (classifyGroupByLambda(lambda, fluentMethod))
            return;
        if (classifyHavingLambda(lambda, fluentMethod))
            return;
        if (classifyGroupSelectLambda(lambda, fluentMethod))
            return;
        if (classifyGroupSortLambda(lambda, fluentMethod))
            return;
        if (classifyJoinRelationshipLambda(lambda, fluentMethod))
            return;
        if (classifyWhereLambda(lambda, fluentMethod))
            return;
        if (classifySelectLambda(lambda, fluentMethod))
            return;
        classifySortLambda(lambda, fluentMethod);
    }

    private boolean classifyGroupByLambda(PendingLambda lambda, @Nullable String fluentMethod) {
        if (!METHOD_GROUP_BY.equals(fluentMethod))
            return false;
        groupByLambda = new LambdaPair(lambda.methodName(), lambda.descriptor());
        return true;
    }

    private boolean classifyHavingLambda(PendingLambda lambda, @Nullable String fluentMethod) {
        if (!METHOD_HAVING.equals(fluentMethod))
            return false;
        havingLambdas.add(new LambdaPair(lambda.methodName(), lambda.descriptor()));
        return true;
    }

    private boolean classifyGroupSelectLambda(PendingLambda lambda, @Nullable String fluentMethod) {
        if (!METHOD_SELECT.equals(fluentMethod) || !lambda.isGroupSpec())
            return false;
        groupSelectLambdas.add(new LambdaPair(lambda.methodName(), lambda.descriptor()));
        return true;
    }

    private boolean classifyGroupSortLambda(PendingLambda lambda, @Nullable String fluentMethod) {
        if (!lambda.isGroupSpec())
            return false;
        if (METHOD_SORTED_BY.equals(fluentMethod) || METHOD_THEN_SORTED_BY.equals(fluentMethod)) {
            groupSortLambdas.add(new SortLambda(lambda.methodName(), lambda.descriptor(),
                    SortDirection.ASCENDING, lambda.nullPrecedence()));
            return true;
        }
        if (METHOD_SORTED_DESCENDING_BY.equals(fluentMethod) || METHOD_THEN_SORTED_DESCENDING_BY.equals(fluentMethod)) {
            groupSortLambdas.add(new SortLambda(lambda.methodName(), lambda.descriptor(),
                    SortDirection.DESCENDING, lambda.nullPrecedence()));
            return true;
        }
        return false;
    }

    private boolean classifyJoinRelationshipLambda(PendingLambda lambda, @Nullable String fluentMethod) {
        if (!JOIN_ENTRY_METHODS.contains(fluentMethod))
            return false;
        joinRelationshipLambda = new LambdaPair(lambda.methodName(), lambda.descriptor());
        return true;
    }

    private boolean classifyWhereLambda(PendingLambda lambda, @Nullable String fluentMethod) {
        if (!METHOD_WHERE.equals(fluentMethod))
            return false;
        if (lambda.isBiEntity()) {
            biEntityWhereLambdas.add(new LambdaPair(lambda.methodName(), lambda.descriptor()));
        } else {
            whereLambdas.add(new LambdaPair(lambda.methodName(), lambda.descriptor()));
            if (firstWhereLambda == null) {
                firstWhereLambda = new LambdaPair(lambda.methodName(), lambda.descriptor());
            }
        }
        return true;
    }

    private boolean classifySelectLambda(PendingLambda lambda, @Nullable String fluentMethod) {
        if (!METHOD_SELECT.equals(fluentMethod))
            return false;
        if (lambda.isBiEntity()) {
            biEntityProjectionLambda = new LambdaPair(lambda.methodName(), lambda.descriptor());
        } else {
            selectLambda = new LambdaPair(lambda.methodName(), lambda.descriptor());
        }
        return true;
    }

    private void classifySortLambda(PendingLambda lambda, @Nullable String fluentMethod) {
        if (METHOD_SORTED_BY.equals(fluentMethod) || METHOD_THEN_SORTED_BY.equals(fluentMethod)) {
            sortLambdas.add(new SortLambda(lambda.methodName(), lambda.descriptor(),
                    SortDirection.ASCENDING, lambda.nullPrecedence()));
        } else if (METHOD_SORTED_DESCENDING_BY.equals(fluentMethod) || METHOD_THEN_SORTED_DESCENDING_BY.equals(fluentMethod)) {
            sortLambdas.add(new SortLambda(lambda.methodName(), lambda.descriptor(),
                    SortDirection.DESCENDING, lambda.nullPrecedence()));
        }
    }

    /**
     * Builds the final LambdaInfo record from accumulated state.
     *
     * @param pendingLambdas must be non-empty (guaranteed by isTerminalOperation guard)
     */
    LambdaInfo build(List<PendingLambda> pendingLambdas) {
        PendingLambda first = pendingLambdas.getFirst();
        LambdaPair primaryLambda = new LambdaPair(first.methodName(), first.descriptor());
        String primaryFluentMethod = first.fluentMethod() != null
                ? first.fluentMethod()
                : METHOD_WHERE;
        return new LambdaInfo(
                primaryLambda,
                primaryFluentMethod,
                firstWhereLambda,
                selectLambda,
                whereLambdas.isEmpty() ? null : whereLambdas,
                sortLambdas.isEmpty() ? null : sortLambdas,
                aggregationLambda,
                joinRelationshipLambda,
                biEntityWhereLambdas.isEmpty() ? null : biEntityWhereLambdas,
                biEntityProjectionLambda,
                isGroupQuery,
                groupByLambda,
                havingLambdas.isEmpty() ? null : havingLambdas,
                groupSelectLambdas.isEmpty() ? null : groupSelectLambdas,
                groupSortLambdas.isEmpty() ? null : groupSortLambdas);
    }
}

/** Grouped lambda info with aggregation, join, and group fields. */
record LambdaInfo(
        LambdaPair primaryLambda,
        String primaryFluentMethod,
        @Nullable LambdaPair firstWhereLambda,
        @Nullable LambdaPair selectLambda,
        List<LambdaPair> whereLambdas,
        List<SortLambda> sortLambdas,
        @Nullable LambdaPair aggregationLambda,
        @Nullable LambdaPair joinRelationshipLambda,
        List<LambdaPair> biEntityWhereLambdas,
        @Nullable LambdaPair biEntityProjectionLambda,
        boolean isGroup,
        @Nullable LambdaPair groupByLambda,
        List<LambdaPair> havingLambdas,
        List<LambdaPair> groupSelectLambdas,
        List<SortLambda> groupSortLambdas) {
}
