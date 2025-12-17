package io.quarkiverse.qubit.deployment.analysis;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link QueryCharacteristics} factory methods and record properties.
 */
class QueryCharacteristicsTest {

    // ========================================================================
    // forList() Tests
    // ========================================================================

    @Test
    void forList_allFlagsAreFalse() {
        QueryCharacteristics result = QueryCharacteristics.forList();

        assertThat(result.isCountQuery()).isFalse();
        assertThat(result.isAggregationQuery()).isFalse();
        assertThat(result.isJoinQuery()).isFalse();
        assertThat(result.isSelectJoined()).isFalse();
        assertThat(result.isJoinProjection()).isFalse();
        assertThat(result.isGroupQuery()).isFalse();
    }

    // ========================================================================
    // forCount() Tests
    // ========================================================================

    @Test
    void forCount_onlyCountQueryIsTrue() {
        QueryCharacteristics result = QueryCharacteristics.forCount();

        assertThat(result.isCountQuery()).isTrue();
        assertThat(result.isAggregationQuery()).isFalse();
        assertThat(result.isJoinQuery()).isFalse();
        assertThat(result.isSelectJoined()).isFalse();
        assertThat(result.isJoinProjection()).isFalse();
        assertThat(result.isGroupQuery()).isFalse();
    }

    // ========================================================================
    // forAggregation() Tests
    // ========================================================================

    @Test
    void forAggregation_onlyAggregationQueryIsTrue() {
        QueryCharacteristics result = QueryCharacteristics.forAggregation();

        assertThat(result.isCountQuery()).isFalse();
        assertThat(result.isAggregationQuery()).isTrue();
        assertThat(result.isJoinQuery()).isFalse();
        assertThat(result.isSelectJoined()).isFalse();
        assertThat(result.isJoinProjection()).isFalse();
        assertThat(result.isGroupQuery()).isFalse();
    }

    // ========================================================================
    // forJoinList() Tests
    // ========================================================================

    @Test
    void forJoinList_onlyJoinQueryIsTrue() {
        QueryCharacteristics result = QueryCharacteristics.forJoinList();

        assertThat(result.isCountQuery()).isFalse();
        assertThat(result.isAggregationQuery()).isFalse();
        assertThat(result.isJoinQuery()).isTrue();
        assertThat(result.isSelectJoined()).isFalse();
        assertThat(result.isJoinProjection()).isFalse();
        assertThat(result.isGroupQuery()).isFalse();
    }

    // ========================================================================
    // forJoinCount() Tests
    // ========================================================================

    @Test
    void forJoinCount_countAndJoinQueryAreTrue() {
        QueryCharacteristics result = QueryCharacteristics.forJoinCount();

        assertThat(result.isCountQuery()).isTrue();
        assertThat(result.isAggregationQuery()).isFalse();
        assertThat(result.isJoinQuery()).isTrue();
        assertThat(result.isSelectJoined()).isFalse();
        assertThat(result.isJoinProjection()).isFalse();
        assertThat(result.isGroupQuery()).isFalse();
    }

    // ========================================================================
    // forSelectJoined() Tests
    // ========================================================================

    @Test
    void forSelectJoined_joinQueryAndSelectJoinedAreTrue() {
        QueryCharacteristics result = QueryCharacteristics.forSelectJoined();

        assertThat(result.isCountQuery()).isFalse();
        assertThat(result.isAggregationQuery()).isFalse();
        assertThat(result.isJoinQuery()).isTrue();
        assertThat(result.isSelectJoined()).isTrue();
        assertThat(result.isJoinProjection()).isFalse();
        assertThat(result.isGroupQuery()).isFalse();
    }

    // ========================================================================
    // forJoinProjection() Tests
    // ========================================================================

    @Test
    void forJoinProjection_joinQueryAndJoinProjectionAreTrue() {
        QueryCharacteristics result = QueryCharacteristics.forJoinProjection();

        assertThat(result.isCountQuery()).isFalse();
        assertThat(result.isAggregationQuery()).isFalse();
        assertThat(result.isJoinQuery()).isTrue();
        assertThat(result.isSelectJoined()).isFalse();
        assertThat(result.isJoinProjection()).isTrue();
        assertThat(result.isGroupQuery()).isFalse();
    }

    // ========================================================================
    // forGroupList() Tests
    // ========================================================================

    @Test
    void forGroupList_onlyGroupQueryIsTrue() {
        QueryCharacteristics result = QueryCharacteristics.forGroupList();

        assertThat(result.isCountQuery()).isFalse();
        assertThat(result.isAggregationQuery()).isFalse();
        assertThat(result.isJoinQuery()).isFalse();
        assertThat(result.isSelectJoined()).isFalse();
        assertThat(result.isJoinProjection()).isFalse();
        assertThat(result.isGroupQuery()).isTrue();
    }

    // ========================================================================
    // forGroupCount() Tests
    // ========================================================================

    @Test
    void forGroupCount_countAndGroupQueryAreTrue() {
        QueryCharacteristics result = QueryCharacteristics.forGroupCount();

        assertThat(result.isCountQuery()).isTrue();
        assertThat(result.isAggregationQuery()).isFalse();
        assertThat(result.isJoinQuery()).isFalse();
        assertThat(result.isSelectJoined()).isFalse();
        assertThat(result.isJoinProjection()).isFalse();
        assertThat(result.isGroupQuery()).isTrue();
    }

    // ========================================================================
    // Record Equality Tests
    // ========================================================================

    @Test
    void equalCharacteristics_areEqual() {
        QueryCharacteristics qc1 = QueryCharacteristics.forCount();
        QueryCharacteristics qc2 = QueryCharacteristics.forCount();

        assertThat(qc1).isEqualTo(qc2);
        assertThat(qc1.hashCode()).isEqualTo(qc2.hashCode());
    }

    @Test
    void differentCharacteristics_areNotEqual() {
        QueryCharacteristics count = QueryCharacteristics.forCount();
        QueryCharacteristics list = QueryCharacteristics.forList();

        assertThat(count).isNotEqualTo(list);
    }

    // ========================================================================
    // toString() Test
    // ========================================================================

    @Test
    void toString_containsAllFieldNames() {
        QueryCharacteristics result = QueryCharacteristics.forJoinCount();
        String str = result.toString();

        assertThat(str).contains("isCountQuery");
        assertThat(str).contains("isAggregationQuery");
        assertThat(str).contains("isJoinQuery");
        assertThat(str).contains("isSelectJoined");
        assertThat(str).contains("isJoinProjection");
        assertThat(str).contains("isGroupQuery");
    }
}
