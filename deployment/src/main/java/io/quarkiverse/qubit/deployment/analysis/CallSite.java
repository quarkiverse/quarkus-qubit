package io.quarkiverse.qubit.deployment.analysis;

import static io.quarkiverse.qubit.deployment.util.DescriptorParser.returnsBooleanType;
import static io.quarkiverse.qubit.runtime.internal.QubitConstants.*;

import org.jspecify.annotations.Nullable;

import java.util.List;

import io.quarkiverse.qubit.SortDirection;
import io.quarkus.logging.Log;

public sealed interface CallSite permits CallSite.SimpleCallSite, CallSite.AggregationCallSite,
        CallSite.JoinCallSite, CallSite.GroupCallSite {

    record LambdaPair(String methodName, String descriptor) {
    }

    record SortLambda(String methodName, String descriptor, SortDirection direction,
            @Nullable String nullPrecedence) {
        /** Extracts the method+descriptor pair as a LambdaPair. */
        public LambdaPair toLambdaPair() {
            return new LambdaPair(methodName, descriptor);
        }
    }

    enum JoinType {
        INNER,
        LEFT
    }

    record Common(
            String ownerClassName,
            String methodName,
            int lineNumber,
            String targetMethodName,
            int terminalInsnIndex,
            boolean hasDistinct,
            Integer skipValue,
            Integer limitValue) {
    }

    Common common();

    default String ownerClassName() {
        return common().ownerClassName();
    }

    default String methodName() {
        return common().methodName();
    }

    default int lineNumber() {
        return common().lineNumber();
    }

    default String targetMethodName() {
        return common().targetMethodName();
    }

    default int terminalInsnIndex() {
        return common().terminalInsnIndex();
    }

    default boolean hasDistinct() {
        return common().hasDistinct();
    }

    default Integer skipValue() {
        return common().skipValue();
    }

    default Integer limitValue() {
        return common().limitValue();
    }

    default boolean isCountQuery() {
        return METHOD_COUNT.equals(targetMethodName());
    }

    default String getCallSiteId() {
        return ownerClassName() + ":" + methodName() + ":" + lineNumber() + ":" + getPrimaryLambdaMethodName();
    }

    String getPrimaryLambdaMethodName();

    record SimpleCallSite(
            Common common,
            LambdaPair primaryLambda,
            String fluentMethodName,
            List<LambdaPair> predicateLambdas,
            @Nullable LambdaPair projectionLambda,
            List<SortLambda> sortLambdas) implements CallSite {

        public SimpleCallSite {
            predicateLambdas = predicateLambdas != null ? List.copyOf(predicateLambdas) : List.of();
            sortLambdas = sortLambdas != null ? List.copyOf(sortLambdas) : List.of();
        }

        public boolean isProjectionQuery() {
            if (projectionLambda != null) {
                return true;
            }

            if (METHOD_SELECT.equals(fluentMethodName)) {
                return true;
            }

            if (METHOD_WHERE.equals(fluentMethodName)) {
                return false;
            }

            if (METHOD_SORTED_BY.equals(fluentMethodName) || METHOD_SORTED_DESCENDING_BY.equals(fluentMethodName)) {
                return false;
            }

            if (AGGREGATION_METHOD_NAMES.contains(fluentMethodName)) {
                return false;
            }

            if (returnsBooleanType(primaryLambda.descriptor())) {
                return false;
            }

            Log.warnf("Treating as projection (non-boolean): descriptor=%s, fluent=%s",
                    primaryLambda.descriptor(), fluentMethodName);
            return true;
        }

        public boolean isCombinedQuery() {
            return !predicateLambdas.isEmpty() && projectionLambda != null;
        }

        @Override
        public String getPrimaryLambdaMethodName() {
            if (!predicateLambdas.isEmpty()) {
                return predicateLambdas.getFirst().methodName();
            }
            if (projectionLambda != null) {
                return projectionLambda.methodName();
            }
            if (!sortLambdas.isEmpty()) {
                return sortLambdas.getFirst().methodName();
            }
            return primaryLambda.methodName();
        }

        @Override
        public String toString() {
            if (isCombinedQuery()) {
                return String.format("CallSite{%s.%s line %d, where=%d predicates, select=%s, target=%s}",
                        ownerClassName(), methodName(), lineNumber(),
                        predicateLambdas.size(), projectionLambda.methodName(), targetMethodName());
            }
            return String.format("CallSite{%s.%s line %d, lambda=%s, fluent=%s, target=%s}",
                    ownerClassName(), methodName(), lineNumber(), primaryLambda.methodName(),
                    fluentMethodName, targetMethodName());
        }
    }

    record AggregationCallSite(
            Common common,
            List<LambdaPair> predicateLambdas,
            LambdaPair aggregationLambda) implements CallSite {

        public AggregationCallSite {
            predicateLambdas = predicateLambdas != null ? List.copyOf(predicateLambdas) : List.of();
        }

        @Override
        public String getPrimaryLambdaMethodName() {
            if (!predicateLambdas.isEmpty()) {
                return predicateLambdas.getFirst().methodName();
            }
            return aggregationLambda.methodName();
        }

        @Override
        public String toString() {
            return String.format("CallSite{%s.%s line %d, aggregation=%s, predicates=%d, target=%s}",
                    ownerClassName(), methodName(), lineNumber(),
                    aggregationLambda.methodName(), predicateLambdas.size(), targetMethodName());
        }
    }

    record JoinCallSite(
            Common common,
            JoinType joinType,
            @Nullable LambdaPair joinRelationshipLambda,
            List<LambdaPair> predicateLambdas,
            List<LambdaPair> biEntityPredicateLambdas,
            List<SortLambda> sortLambdas,
            boolean isSelectJoined,
            @Nullable LambdaPair biEntityProjectionLambda) implements CallSite {

        public JoinCallSite {
            predicateLambdas = predicateLambdas != null ? List.copyOf(predicateLambdas) : List.of();
            biEntityPredicateLambdas = biEntityPredicateLambdas != null ? List.copyOf(biEntityPredicateLambdas) : List.of();
            sortLambdas = sortLambdas != null ? List.copyOf(sortLambdas) : List.of();
        }

        public boolean isJoinProjectionQuery() {
            return biEntityProjectionLambda != null;
        }

        @Override
        public String getPrimaryLambdaMethodName() {
            if (!predicateLambdas.isEmpty()) {
                return predicateLambdas.getFirst().methodName();
            }
            if (!biEntityPredicateLambdas.isEmpty()) {
                return biEntityPredicateLambdas.getFirst().methodName();
            }
            if (joinRelationshipLambda != null) {
                return joinRelationshipLambda.methodName();
            }
            return String.valueOf(terminalInsnIndex());
        }

        @Override
        public String toString() {
            String relName = joinRelationshipLambda != null ? joinRelationshipLambda.methodName() : null;
            return String.format("CallSite{%s.%s line %d, %s JOIN, relationship=%s, biPredicates=%d, target=%s}",
                    ownerClassName(), methodName(), lineNumber(),
                    joinType, relName, biEntityPredicateLambdas.size(), targetMethodName());
        }
    }

    record GroupCallSite(
            Common common,
            List<LambdaPair> predicateLambdas,
            @Nullable LambdaPair groupByLambda,
            List<LambdaPair> havingLambdas,
            List<LambdaPair> groupSelectLambdas,
            List<SortLambda> groupSortLambdas,
            boolean isGroupSelectKey) implements CallSite {

        public GroupCallSite {
            predicateLambdas = predicateLambdas != null ? List.copyOf(predicateLambdas) : List.of();
            havingLambdas = havingLambdas != null ? List.copyOf(havingLambdas) : List.of();
            groupSelectLambdas = groupSelectLambdas != null ? List.copyOf(groupSelectLambdas) : List.of();
            groupSortLambdas = groupSortLambdas != null ? List.copyOf(groupSortLambdas) : List.of();
        }

        @Override
        public String getPrimaryLambdaMethodName() {
            if (!predicateLambdas.isEmpty()) {
                return predicateLambdas.getFirst().methodName();
            }
            if (groupByLambda != null) {
                return groupByLambda.methodName();
            }
            return String.valueOf(terminalInsnIndex());
        }

        @Override
        public String toString() {
            String groupByName = groupByLambda != null ? groupByLambda.methodName() : null;
            return String.format("CallSite{%s.%s line %d, GROUP BY=%s, having=%d, groupSelect=%d, target=%s}",
                    ownerClassName(), methodName(), lineNumber(),
                    groupByName, havingLambdas.size(), groupSelectLambdas.size(), targetMethodName());
        }
    }
}
