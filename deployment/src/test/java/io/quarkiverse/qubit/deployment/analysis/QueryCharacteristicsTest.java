package io.quarkiverse.qubit.deployment.analysis;

import static io.quarkiverse.qubit.deployment.analysis.QueryCharacteristicsAssert.assertThat;

import org.junit.jupiter.api.Test;

class QueryCharacteristicsTest {

    @Test
    void forList_allFlagsAreFalse() {
        assertThat(QueryCharacteristics.forList())
                .isNotCountQuery()
                .isNotAggregationQuery()
                .isNotJoinQuery()
                .isNotSelectJoined()
                .isNotJoinProjection()
                .isNotGroupQuery();
    }

    @Test
    void forCount_onlyCountQueryIsTrue() {
        assertThat(QueryCharacteristics.forCount())
                .isCountQuery()
                .isNotAggregationQuery()
                .isNotJoinQuery()
                .isNotSelectJoined()
                .isNotJoinProjection()
                .isNotGroupQuery();
    }

    @Test
    void forAggregation_onlyAggregationQueryIsTrue() {
        assertThat(QueryCharacteristics.forAggregation())
                .isNotCountQuery()
                .isAggregationQuery()
                .isNotJoinQuery()
                .isNotSelectJoined()
                .isNotJoinProjection()
                .isNotGroupQuery();
    }

    @Test
    void forJoinList_onlyJoinQueryIsTrue() {
        assertThat(QueryCharacteristics.forJoinList())
                .isNotCountQuery()
                .isNotAggregationQuery()
                .isJoinQuery()
                .isNotSelectJoined()
                .isNotJoinProjection()
                .isNotGroupQuery();
    }

    @Test
    void forJoinCount_countAndJoinQueryAreTrue() {
        assertThat(QueryCharacteristics.forJoinCount())
                .isCountQuery()
                .isNotAggregationQuery()
                .isJoinQuery()
                .isNotSelectJoined()
                .isNotJoinProjection()
                .isNotGroupQuery();
    }

    @Test
    void forSelectJoined_joinQueryAndSelectJoinedAreTrue() {
        assertThat(QueryCharacteristics.forSelectJoined())
                .isNotCountQuery()
                .isNotAggregationQuery()
                .isJoinQuery()
                .isSelectJoined()
                .isNotJoinProjection()
                .isNotGroupQuery();
    }

    @Test
    void forJoinProjection_joinQueryAndJoinProjectionAreTrue() {
        assertThat(QueryCharacteristics.forJoinProjection())
                .isNotCountQuery()
                .isNotAggregationQuery()
                .isJoinQuery()
                .isNotSelectJoined()
                .isJoinProjection()
                .isNotGroupQuery();
    }

    @Test
    void forGroupList_onlyGroupQueryIsTrue() {
        assertThat(QueryCharacteristics.forGroupList())
                .isNotCountQuery()
                .isNotAggregationQuery()
                .isNotJoinQuery()
                .isNotSelectJoined()
                .isNotJoinProjection()
                .isGroupQuery();
    }

    @Test
    void forGroupCount_countAndGroupQueryAreTrue() {
        assertThat(QueryCharacteristics.forGroupCount())
                .isCountQuery()
                .isNotAggregationQuery()
                .isNotJoinQuery()
                .isNotSelectJoined()
                .isNotJoinProjection()
                .isGroupQuery();
    }
}
