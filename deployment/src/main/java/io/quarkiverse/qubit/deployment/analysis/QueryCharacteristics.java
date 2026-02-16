package io.quarkiverse.qubit.deployment.analysis;

/**
 * Query type flags for deduplication and executor registration.
 * Types: Group, Join (count/projection/selectJoined/list), Aggregation, Count, List (default).
 */
public record QueryCharacteristics(
        boolean isCountQuery,
        boolean isAggregationQuery,
        boolean isJoinQuery,
        boolean isSelectJoined,
        boolean isJoinProjection,
        boolean isGroupQuery) {

    public static QueryCharacteristics forList() {
        return new QueryCharacteristics(false, false, false, false, false, false);
    }

    public static QueryCharacteristics forCount() {
        return new QueryCharacteristics(true, false, false, false, false, false);
    }

    public static QueryCharacteristics forAggregation() {
        return new QueryCharacteristics(false, true, false, false, false, false);
    }

    public static QueryCharacteristics forJoinList() {
        return new QueryCharacteristics(false, false, true, false, false, false);
    }

    public static QueryCharacteristics forJoinCount() {
        return new QueryCharacteristics(true, false, true, false, false, false);
    }

    public static QueryCharacteristics forSelectJoined() {
        return new QueryCharacteristics(false, false, true, true, false, false);
    }

    public static QueryCharacteristics forJoinProjection() {
        return new QueryCharacteristics(false, false, true, false, true, false);
    }

    public static QueryCharacteristics forGroupList() {
        return new QueryCharacteristics(false, false, false, false, false, true);
    }

    public static QueryCharacteristics forGroupCount() {
        return new QueryCharacteristics(true, false, false, false, false, true);
    }

    public static QueryCharacteristics fromCallSite(CallSite callSite) {
        boolean isCount = callSite.isCountQuery();
        return switch (callSite) {
            case CallSite.GroupCallSite _ -> isCount ? forGroupCount() : forGroupList();
            case CallSite.JoinCallSite j -> {
                if (isCount)
                    yield forJoinCount();
                if (j.isJoinProjectionQuery())
                    yield forJoinProjection();
                if (j.isSelectJoined())
                    yield forSelectJoined();
                yield forJoinList();
            }
            case CallSite.AggregationCallSite _ -> forAggregation();
            case CallSite.SimpleCallSite _ -> isCount ? forCount() : forList();
        };
    }
}
