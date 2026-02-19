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
            String lambdaMethodName,
            String lambdaMethodDescriptor,
            String fluentMethodName,
            List<LambdaPair> predicateLambdas,
            String projectionLambdaMethodName,
            String projectionLambdaMethodDescriptor,
            List<SortLambda> sortLambdas) implements CallSite {

        public SimpleCallSite {
            predicateLambdas = predicateLambdas != null ? List.copyOf(predicateLambdas) : List.of();
            sortLambdas = sortLambdas != null ? List.copyOf(sortLambdas) : List.of();
        }

        public boolean isProjectionQuery() {
            if (projectionLambdaMethodName != null) {
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

            if (returnsBooleanType(lambdaMethodDescriptor)) {
                return false;
            }

            Log.warnf("Treating as projection (non-boolean): descriptor=%s, fluent=%s", lambdaMethodDescriptor,
                    fluentMethodName);
            return true;
        }

        public boolean isCombinedQuery() {
            return predicateLambdas != null && !predicateLambdas.isEmpty() && projectionLambdaMethodName != null;
        }

        @Override
        public String getPrimaryLambdaMethodName() {
            if (predicateLambdas != null && !predicateLambdas.isEmpty()) {
                return predicateLambdas.getFirst().methodName();
            }
            if (projectionLambdaMethodName != null) {
                return projectionLambdaMethodName;
            }
            if (sortLambdas != null && !sortLambdas.isEmpty()) {
                return sortLambdas.getFirst().methodName();
            }
            if (lambdaMethodName != null) {
                return lambdaMethodName;
            }
            return String.valueOf(terminalInsnIndex());
        }

        @Override
        public String toString() {
            if (isCombinedQuery()) {
                int predicateCount = predicateLambdas != null ? predicateLambdas.size() : 0;
                return String.format("CallSite{%s.%s line %d, where=%d predicates, select=%s, target=%s}",
                        ownerClassName(), methodName(), lineNumber(),
                        predicateCount, projectionLambdaMethodName, targetMethodName());
            }
            return String.format("CallSite{%s.%s line %d, lambda=%s, fluent=%s, target=%s}",
                    ownerClassName(), methodName(), lineNumber(), lambdaMethodName, fluentMethodName,
                    targetMethodName());
        }
    }

    record AggregationCallSite(
            Common common,
            List<LambdaPair> predicateLambdas,
            String aggregationLambdaMethodName,
            String aggregationLambdaMethodDescriptor) implements CallSite {

        public AggregationCallSite {
            predicateLambdas = predicateLambdas != null ? List.copyOf(predicateLambdas) : List.of();
        }

        @Override
        public String getPrimaryLambdaMethodName() {
            if (predicateLambdas != null && !predicateLambdas.isEmpty()) {
                return predicateLambdas.getFirst().methodName();
            }
            if (aggregationLambdaMethodName != null) {
                return aggregationLambdaMethodName;
            }
            return String.valueOf(terminalInsnIndex());
        }

        @Override
        public String toString() {
            int predicateCount = predicateLambdas != null ? predicateLambdas.size() : 0;
            return String.format("CallSite{%s.%s line %d, aggregation=%s, predicates=%d, target=%s}",
                    ownerClassName(), methodName(), lineNumber(),
                    aggregationLambdaMethodName, predicateCount, targetMethodName());
        }
    }

    record JoinCallSite(
            Common common,
            JoinType joinType,
            String joinRelationshipLambdaMethodName,
            String joinRelationshipLambdaDescriptor,
            List<LambdaPair> predicateLambdas,
            List<LambdaPair> biEntityPredicateLambdas,
            List<SortLambda> sortLambdas,
            boolean isSelectJoined,
            String biEntityProjectionLambdaMethodName,
            String biEntityProjectionLambdaDescriptor) implements CallSite {

        public JoinCallSite {
            predicateLambdas = predicateLambdas != null ? List.copyOf(predicateLambdas) : List.of();
            biEntityPredicateLambdas = biEntityPredicateLambdas != null ? List.copyOf(biEntityPredicateLambdas) : List.of();
            sortLambdas = sortLambdas != null ? List.copyOf(sortLambdas) : List.of();
        }

        public boolean isJoinProjectionQuery() {
            return biEntityProjectionLambdaMethodName != null;
        }

        @Override
        public String getPrimaryLambdaMethodName() {
            if (predicateLambdas != null && !predicateLambdas.isEmpty()) {
                return predicateLambdas.getFirst().methodName();
            }
            if (biEntityPredicateLambdas != null && !biEntityPredicateLambdas.isEmpty()) {
                return biEntityPredicateLambdas.getFirst().methodName();
            }
            if (joinRelationshipLambdaMethodName != null) {
                return joinRelationshipLambdaMethodName;
            }
            return String.valueOf(terminalInsnIndex());
        }

        @Override
        public String toString() {
            int biPredicateCount = biEntityPredicateLambdas != null ? biEntityPredicateLambdas.size() : 0;
            return String.format("CallSite{%s.%s line %d, %s JOIN, relationship=%s, biPredicates=%d, target=%s}",
                    ownerClassName(), methodName(), lineNumber(),
                    joinType, joinRelationshipLambdaMethodName, biPredicateCount, targetMethodName());
        }
    }

    record GroupCallSite(
            Common common,
            List<LambdaPair> predicateLambdas,
            String groupByLambdaMethodName,
            String groupByLambdaDescriptor,
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
            if (predicateLambdas != null && !predicateLambdas.isEmpty()) {
                return predicateLambdas.getFirst().methodName();
            }
            if (groupByLambdaMethodName != null) {
                return groupByLambdaMethodName;
            }
            return String.valueOf(terminalInsnIndex());
        }

        @Override
        public String toString() {
            int havingCount = havingLambdas != null ? havingLambdas.size() : 0;
            int selectCount = groupSelectLambdas != null ? groupSelectLambdas.size() : 0;
            return String.format("CallSite{%s.%s line %d, GROUP BY=%s, having=%d, groupSelect=%d, target=%s}",
                    ownerClassName(), methodName(), lineNumber(),
                    groupByLambdaMethodName, havingCount, selectCount, targetMethodName());
        }
    }
}
