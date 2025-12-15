package io.quarkiverse.qubit.deployment.analysis;

/**
 * Encapsulates query type characteristics for deduplication and executor registration.
 *
 * <p>
 * These flags describe a query's execution characteristics and are used for:
 * <ul>
 *   <li>Lambda deduplication (computing hash, reusing executors)</li>
 *   <li>Executor registration (choosing correct registry method)</li>
 * </ul>
 *
 * <p><b>Query Type Taxonomy:</b>
 * <pre>
 * Query Types:
 * ├── Group Query (isGroupQuery)
 * │   ├── Count
 * │   └── List
 * ├── Join Query (isJoinQuery)
 * │   ├── Count
 * │   ├── JoinProjection
 * │   ├── SelectJoined
 * │   └── List
 * ├── Aggregation Query (isAggregationQuery)
 * ├── Count Query (isCountQuery)
 * └── List Query (default)
 * </pre>
 *
 * @param isCountQuery true if this is a count query (returns Long)
 * @param isAggregationQuery true if this is an aggregation query (min, max, avg, sum)
 * @param isJoinQuery true if this is a join query (join, leftJoin)
 * @param isSelectJoined true if selectJoined() was called (returns joined entities)
 * @param isJoinProjection true if select() with BiQuerySpec was called (returns projected objects)
 * @param isGroupQuery true if this is a group query (groupBy)
 */
public record QueryCharacteristics(
        boolean isCountQuery,
        boolean isAggregationQuery,
        boolean isJoinQuery,
        boolean isSelectJoined,
        boolean isJoinProjection,
        boolean isGroupQuery
) {

    // ========================================================================
    // Factory Methods for Common Query Types
    // ========================================================================

    /**
     * Creates characteristics for a simple list query.
     */
    public static QueryCharacteristics forList() {
        return new QueryCharacteristics(false, false, false, false, false, false);
    }

    /**
     * Creates characteristics for a count query.
     */
    public static QueryCharacteristics forCount() {
        return new QueryCharacteristics(true, false, false, false, false, false);
    }

    /**
     * Creates characteristics for an aggregation query (min, max, avg, sum).
     */
    public static QueryCharacteristics forAggregation() {
        return new QueryCharacteristics(false, true, false, false, false, false);
    }

    /**
     * Creates characteristics for a join list query.
     */
    public static QueryCharacteristics forJoinList() {
        return new QueryCharacteristics(false, false, true, false, false, false);
    }

    /**
     * Creates characteristics for a join count query.
     */
    public static QueryCharacteristics forJoinCount() {
        return new QueryCharacteristics(true, false, true, false, false, false);
    }

    /**
     * Creates characteristics for a selectJoined query.
     */
    public static QueryCharacteristics forSelectJoined() {
        return new QueryCharacteristics(false, false, true, true, false, false);
    }

    /**
     * Creates characteristics for a join projection query.
     */
    public static QueryCharacteristics forJoinProjection() {
        return new QueryCharacteristics(false, false, true, false, true, false);
    }

    /**
     * Creates characteristics for a group list query.
     */
    public static QueryCharacteristics forGroupList() {
        return new QueryCharacteristics(false, false, false, false, false, true);
    }

    /**
     * Creates characteristics for a group count query.
     */
    public static QueryCharacteristics forGroupCount() {
        return new QueryCharacteristics(true, false, false, false, false, true);
    }

    /**
     * Creates characteristics from a LambdaCallSite.
     *
     * @param callSite the lambda call site with query flags
     * @param isGroupQuery true if this is a group query (determined from result type)
     * @return QueryCharacteristics extracted from the call site
     */
    public static QueryCharacteristics fromCallSite(
            InvokeDynamicScanner.LambdaCallSite callSite,
            boolean isGroupQuery) {
        return new QueryCharacteristics(
                callSite.isCountQuery(),
                callSite.isAggregationQuery(),
                callSite.isJoinQuery(),
                callSite.isSelectJoinedQuery(),
                callSite.isJoinProjectionQuery(),
                isGroupQuery
        );
    }
}
