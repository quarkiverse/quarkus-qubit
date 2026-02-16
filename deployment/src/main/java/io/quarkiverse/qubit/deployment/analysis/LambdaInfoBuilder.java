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

    // Single-value fields
    private @Nullable String groupByMethod;
    private @Nullable String groupByDescriptor;
    private @Nullable String firstWhereMethod;
    private @Nullable String firstWhereDescriptor;
    private @Nullable String selectMethod;
    private @Nullable String selectDescriptor;
    private @Nullable String aggregationMethod;
    private @Nullable String aggregationDescriptor;
    private @Nullable String joinRelationshipMethod;
    private @Nullable String joinRelationshipDescriptor;
    private @Nullable String biEntityProjectionMethod;
    private @Nullable String biEntityProjectionDescriptor;

    // Context
    private final boolean isGroupQuery;

    LambdaInfoBuilder(boolean isGroupQuery) {
        this.isGroupQuery = isGroupQuery;
    }

    /** Classifies a lambda and updates the appropriate field. Returns true if handled. */
    void classify(PendingLambda lambda, boolean isAggregation, boolean isLastLambda) {
        // Aggregation mapper (must be checked first - last lambda in aggregation query)
        if (isAggregation && isLastLambda) {
            aggregationMethod = lambda.methodName();
            aggregationDescriptor = lambda.descriptor();
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
        groupByMethod = lambda.methodName();
        groupByDescriptor = lambda.descriptor();
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
        if (METHOD_SORTED_BY.equals(fluentMethod)) {
            groupSortLambdas.add(new SortLambda(lambda.methodName(), lambda.descriptor(), SortDirection.ASCENDING));
            return true;
        }
        if (METHOD_SORTED_DESCENDING_BY.equals(fluentMethod)) {
            groupSortLambdas.add(new SortLambda(lambda.methodName(), lambda.descriptor(), SortDirection.DESCENDING));
            return true;
        }
        return false;
    }

    private boolean classifyJoinRelationshipLambda(PendingLambda lambda, @Nullable String fluentMethod) {
        if (!JOIN_ENTRY_METHODS.contains(fluentMethod))
            return false;
        joinRelationshipMethod = lambda.methodName();
        joinRelationshipDescriptor = lambda.descriptor();
        return true;
    }

    private boolean classifyWhereLambda(PendingLambda lambda, @Nullable String fluentMethod) {
        if (!METHOD_WHERE.equals(fluentMethod))
            return false;
        if (lambda.isBiEntity()) {
            biEntityWhereLambdas.add(new LambdaPair(lambda.methodName(), lambda.descriptor()));
        } else {
            whereLambdas.add(new LambdaPair(lambda.methodName(), lambda.descriptor()));
            if (firstWhereMethod == null) {
                firstWhereMethod = lambda.methodName();
                firstWhereDescriptor = lambda.descriptor();
            }
        }
        return true;
    }

    private boolean classifySelectLambda(PendingLambda lambda, @Nullable String fluentMethod) {
        if (!METHOD_SELECT.equals(fluentMethod))
            return false;
        if (lambda.isBiEntity()) {
            biEntityProjectionMethod = lambda.methodName();
            biEntityProjectionDescriptor = lambda.descriptor();
        } else {
            selectMethod = lambda.methodName();
            selectDescriptor = lambda.descriptor();
        }
        return true;
    }

    private void classifySortLambda(PendingLambda lambda, @Nullable String fluentMethod) {
        if (METHOD_SORTED_BY.equals(fluentMethod)) {
            sortLambdas.add(new SortLambda(lambda.methodName(), lambda.descriptor(), SortDirection.ASCENDING));
        } else if (METHOD_SORTED_DESCENDING_BY.equals(fluentMethod)) {
            sortLambdas.add(new SortLambda(lambda.methodName(), lambda.descriptor(), SortDirection.DESCENDING));
        }
    }

    /** Builds the final LambdaInfo record from accumulated state. */
    LambdaInfo build(List<PendingLambda> pendingLambdas) {
        PendingLambda first = pendingLambdas.isEmpty() ? null : pendingLambdas.getFirst();
        return new LambdaInfo(
                first != null ? first.methodName() : null,
                first != null ? first.descriptor() : null,
                first != null && first.fluentMethod() != null ? first.fluentMethod() : METHOD_WHERE,
                firstWhereMethod,
                firstWhereDescriptor,
                selectMethod,
                selectDescriptor,
                whereLambdas.isEmpty() ? null : whereLambdas,
                sortLambdas.isEmpty() ? null : sortLambdas,
                aggregationMethod,
                aggregationDescriptor,
                joinRelationshipMethod,
                joinRelationshipDescriptor,
                biEntityWhereLambdas.isEmpty() ? null : biEntityWhereLambdas,
                biEntityProjectionMethod,
                biEntityProjectionDescriptor,
                isGroupQuery,
                groupByMethod,
                groupByDescriptor,
                havingLambdas.isEmpty() ? null : havingLambdas,
                groupSelectLambdas.isEmpty() ? null : groupSelectLambdas,
                groupSortLambdas.isEmpty() ? null : groupSortLambdas);
    }
}

/** Grouped lambda info with aggregation, join, and group fields. */
record LambdaInfo(
        String primaryLambdaMethod,
        String primaryLambdaDescriptor,
        String primaryFluentMethod,
        String firstWhereLambdaMethod,
        String firstWhereLambdaDescriptor,
        String selectLambdaMethod,
        String selectLambdaDescriptor,
        List<LambdaPair> whereLambdas,
        List<SortLambda> sortLambdas,
        String aggregationLambdaMethod, // Aggregation mapper lambda
        String aggregationLambdaDescriptor, // Aggregation mapper descriptor
        String joinRelationshipLambdaMethod, // Join relationship lambda
        String joinRelationshipLambdaDescriptor, // Join relationship descriptor
        List<LambdaPair> biEntityWhereLambdas, // BiQuerySpec WHERE lambdas
        String biEntityProjectionLambdaMethod, // BiQuerySpec SELECT lambda for join projections
        String biEntityProjectionLambdaDescriptor, // BiQuerySpec SELECT lambda descriptor
        boolean isGroup, // True if this is a group query
        String groupByLambdaMethod, // groupBy() key extractor lambda
        String groupByLambdaDescriptor, // groupBy() lambda descriptor
        List<LambdaPair> havingLambdas, // having() lambdas
        List<LambdaPair> groupSelectLambdas, // select() on GroupStream
        List<SortLambda> groupSortLambdas //sortedBy() on GroupStream
) {
}
