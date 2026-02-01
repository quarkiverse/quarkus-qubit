package io.quarkiverse.qubit.deployment.analysis;

import io.quarkiverse.qubit.deployment.analysis.InvokeDynamicScanner.JoinType;
import io.quarkiverse.qubit.deployment.analysis.InvokeDynamicScanner.LambdaSpecType;
import io.quarkiverse.qubit.deployment.analysis.InvokeDynamicScanner.MethodScanState;
import io.quarkiverse.qubit.deployment.analysis.InvokeDynamicScanner.PendingLambda;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link MethodScanState}.
 *
 * <p>Tests verify the mutable state container correctly tracks
 * all scanning context during bytecode iteration.
 */
@DisplayName("MethodScanState")
class MethodScanStateTest {

    private MethodScanState state;

    @BeforeEach
    void setUp() {
        state = new MethodScanState();
    }

    // ==================== Initial State Tests ====================

    @Nested
    @DisplayName("Initial State")
    class InitialStateTests {

        @Test
        @DisplayName("has no lambdas initially")
        void hasNoLambdasInitially() {
            assertThat(state.hasLambdas()).isFalse();
            assertThat(state.pendingLambdas()).isEmpty();
        }

        @Test
        @DisplayName("has no aggregation initially")
        void hasNoAggregationInitially() {
            assertThat(state.pendingAggregation()).isNull();
        }

        @Test
        @DisplayName("has no join type initially")
        void hasNoJoinTypeInitially() {
            assertThat(state.pendingJoinType()).isNull();
            assertThat(state.isJoinContext()).isFalse();
        }

        @Test
        @DisplayName("is not in group context initially")
        void notInGroupContextInitially() {
            assertThat(state.isGroupContext()).isFalse();
            assertThat(state.pendingGroupSelectKey()).isFalse();
        }

        @Test
        @DisplayName("has no pagination initially")
        void hasNoPaginationInitially() {
            assertThat(state.hasDistinct()).isFalse();
            assertThat(state.skipValue()).isNull();
            assertThat(state.limitValue()).isNull();
        }

        @Test
        @DisplayName("has default line number initially")
        void hasDefaultLineNumberInitially() {
            assertThat(state.currentLine()).isEqualTo(-1);
            assertThat(state.effectiveLine()).isEqualTo(-1);
        }
    }

    // ==================== Lambda Management Tests ====================

    @Nested
    @DisplayName("Lambda Management")
    class LambdaManagementTests {

        @Test
        @DisplayName("addLambda adds to pending list")
        void addLambdaAddsToPendingList() {
            PendingLambda lambda = new PendingLambda("lambda$0", "(LPerson;)Z", "where", LambdaSpecType.QUERY_SPEC);

            state.addLambda(lambda);

            assertThat(state.hasLambdas()).isTrue();
            assertThat(state.pendingLambdas()).hasSize(1);
            assertThat(state.pendingLambdas().getFirst()).isEqualTo(lambda);
        }

        @Test
        @DisplayName("multiple lambdas can be added")
        void multipleLambdasCanBeAdded() {
            state.addLambda(new PendingLambda("lambda$0", "(LPerson;)Z", "where", LambdaSpecType.QUERY_SPEC));
            state.addLambda(new PendingLambda("lambda$1", "(LPerson;)LString;", "select", LambdaSpecType.QUERY_SPEC));
            state.addLambda(new PendingLambda("lambda$2", "(LPerson;)I", "sortedBy", LambdaSpecType.QUERY_SPEC));

            assertThat(state.pendingLambdas()).hasSize(3);
        }

        @Test
        @DisplayName("pendingLambdas returns modifiable list")
        void pendingLambdasReturnsModifiableList() {
            state.addLambda(new PendingLambda("lambda$0", "(LPerson;)Z", "where", LambdaSpecType.QUERY_SPEC));

            // The list should be modifiable for reset()
            state.pendingLambdas().clear();

            assertThat(state.hasLambdas()).isFalse();
        }
    }

    // ==================== Aggregation Tests ====================

    @Nested
    @DisplayName("Aggregation State")
    class AggregationStateTests {

        @Test
        @DisplayName("setAggregation sets aggregation method")
        void setAggregationSetsMethod() {
            state.setAggregation("min");

            assertThat(state.pendingAggregation()).isNotNull();
            assertThat(state.pendingAggregation().aggregationMethod()).isEqualTo("min");
        }

        @Test
        @DisplayName("setAggregation overwrites previous aggregation")
        void setAggregationOverwritesPrevious() {
            state.setAggregation("min");
            state.setAggregation("max");

            assertThat(state.pendingAggregation().aggregationMethod()).isEqualTo("max");
        }
    }

    // ==================== Join State Tests ====================

    @Nested
    @DisplayName("Join State")
    class JoinStateTests {

        @Test
        @DisplayName("setJoinType sets INNER join")
        void setJoinTypeInner() {
            state.setJoinType(JoinType.INNER);

            assertThat(state.pendingJoinType()).isEqualTo(JoinType.INNER);
            assertThat(state.isJoinContext()).isTrue();
        }

        @Test
        @DisplayName("setJoinType sets LEFT join")
        void setJoinTypeLeft() {
            state.setJoinType(JoinType.LEFT);

            assertThat(state.pendingJoinType()).isEqualTo(JoinType.LEFT);
            assertThat(state.isJoinContext()).isTrue();
        }

        @Test
        @DisplayName("markJoinSelectJoined sets flag and line")
        void markJoinSelectJoinedSetsFlag() {
            state.markJoinSelectJoined(42);

            assertThat(state.pendingJoinSelectJoined()).isTrue();
        }

        @Test
        @DisplayName("markJoinSelect sets flag")
        void markJoinSelectSetsFlag() {
            state.markJoinSelect(55);

            assertThat(state.pendingJoinSelect()).isTrue();
        }
    }

    // ==================== Group State Tests ====================

    @Nested
    @DisplayName("Group State")
    class GroupStateTests {

        @Test
        @DisplayName("markGroupQuery sets group context")
        void markGroupQuerySetsContext() {
            state.markGroupQuery();

            assertThat(state.isGroupContext()).isTrue();
        }

        @Test
        @DisplayName("markGroupSelectKey sets flag")
        void markGroupSelectKeySetsFlag() {
            state.markGroupSelectKey(100);

            assertThat(state.pendingGroupSelectKey()).isTrue();
        }

        @Test
        @DisplayName("markGroupSelect updates groupSelectLine")
        void markGroupSelectUpdatesLine() {
            state.markGroupQuery();
            state.updateLine(80);
            state.markGroupSelect(80);

            // effectiveLine should return groupSelectLine when in group context
            assertThat(state.effectiveLine()).isEqualTo(80);
        }
    }

    // ==================== Pagination State Tests ====================

    @Nested
    @DisplayName("Pagination State")
    class PaginationStateTests {

        @Test
        @DisplayName("markDistinct sets flag")
        void markDistinctSetsFlag() {
            state.markDistinct();

            assertThat(state.hasDistinct()).isTrue();
        }

        @Test
        @DisplayName("setSkipValue sets skip value")
        void setSkipValueSetsValue() {
            state.setSkipValue(10);

            assertThat(state.skipValue()).isEqualTo(10);
        }

        @Test
        @DisplayName("setLimitValue sets limit value")
        void setLimitValueSetsValue() {
            state.setLimitValue(25);

            assertThat(state.limitValue()).isEqualTo(25);
        }

        @Test
        @DisplayName("null skip value is allowed")
        void nullSkipValueIsAllowed() {
            state.setSkipValue(10);
            state.setSkipValue(null);

            assertThat(state.skipValue()).isNull();
        }
    }

    // ==================== Line Number Tests ====================

    @Nested
    @DisplayName("Line Number Tracking")
    class LineNumberTests {

        @Test
        @DisplayName("updateLine updates current line")
        void updateLineUpdatesCurrent() {
            state.updateLine(42);

            assertThat(state.currentLine()).isEqualTo(42);
        }

        @Test
        @DisplayName("effectiveLine returns currentLine by default")
        void effectiveLineReturnsCurrentByDefault() {
            state.updateLine(50);

            assertThat(state.effectiveLine()).isEqualTo(50);
        }

        @Test
        @DisplayName("effectiveLine prioritizes groupSelectLine in group context")
        void effectiveLinePrioritizesGroupSelect() {
            state.updateLine(100);
            state.markGroupQuery();
            state.markGroupSelect(75);

            assertThat(state.effectiveLine()).isEqualTo(75);
        }

        @Test
        @DisplayName("effectiveLine prioritizes joinSelectJoinedLine")
        void effectiveLinePrioritizesJoinSelectJoined() {
            state.updateLine(100);
            state.markJoinSelectJoined(60);

            assertThat(state.effectiveLine()).isEqualTo(60);
        }

        @Test
        @DisplayName("effectiveLine prioritizes joinSelectLine")
        void effectiveLinePrioritizesJoinSelect() {
            state.updateLine(100);
            state.markJoinSelect(55);

            assertThat(state.effectiveLine()).isEqualTo(55);
        }

        @Test
        @DisplayName("effectiveLine priority: group > joinSelectJoined > joinSelect > current")
        void effectiveLinePriorityOrder() {
            state.updateLine(100);
            state.markGroupQuery();
            state.markGroupSelect(75);
            state.markJoinSelectJoined(60);
            state.markJoinSelect(55);

            // Group context takes priority
            assertThat(state.effectiveLine()).isEqualTo(75);
        }
    }

    // ==================== Reset Tests ====================

    @Nested
    @DisplayName("Reset")
    class ResetTests {

        @Test
        @DisplayName("reset clears all state except currentLine")
        void resetClearsAllStateExceptCurrentLine() {
            // Set up various state
            state.addLambda(new PendingLambda("lambda$0", "(LPerson;)Z", "where", LambdaSpecType.QUERY_SPEC));
            state.setAggregation("min");
            state.setJoinType(JoinType.LEFT);
            state.markJoinSelectJoined(50);
            state.markJoinSelect(55);
            state.markGroupQuery();
            state.markGroupSelectKey(60);
            state.markGroupSelect(65);
            state.markDistinct();
            state.setSkipValue(10);
            state.setLimitValue(25);
            state.updateLine(100);

            // Reset
            state.reset();

            // Verify all state is cleared
            assertThat(state.hasLambdas()).isFalse();
            assertThat(state.pendingAggregation()).isNull();
            assertThat(state.pendingJoinType()).isNull();
            assertThat(state.pendingJoinSelectJoined()).isFalse();
            assertThat(state.pendingJoinSelect()).isFalse();
            assertThat(state.isGroupContext()).isFalse();
            assertThat(state.pendingGroupSelectKey()).isFalse();
            assertThat(state.hasDistinct()).isFalse();
            assertThat(state.skipValue()).isNull();
            assertThat(state.limitValue()).isNull();

            // currentLine is NOT reset (tracks position in method)
            assertThat(state.currentLine()).isEqualTo(100);
        }

        @Test
        @DisplayName("reset can be called multiple times")
        void resetCanBeCalledMultipleTimes() {
            state.addLambda(new PendingLambda("lambda$0", "(LPerson;)Z", "where", LambdaSpecType.QUERY_SPEC));
            state.reset();
            state.reset();
            state.reset();

            assertThat(state.hasLambdas()).isFalse();
        }

        @Test
        @DisplayName("state can be reused after reset")
        void stateCanBeReusedAfterReset() {
            state.addLambda(new PendingLambda("lambda$0", "(LPerson;)Z", "where", LambdaSpecType.QUERY_SPEC));
            state.setJoinType(JoinType.INNER);
            state.reset();

            // Add new state
            state.addLambda(new PendingLambda("lambda$1", "(LOrder;)Z", "where", LambdaSpecType.QUERY_SPEC));
            state.markGroupQuery();

            assertThat(state.hasLambdas()).isTrue();
            assertThat(state.pendingLambdas().getFirst().methodName()).isEqualTo("lambda$1");
            assertThat(state.isGroupContext()).isTrue();
            assertThat(state.isJoinContext()).isFalse();
        }
    }

    // ==================== PendingLambda Record Tests ====================

    @Nested
    @DisplayName("PendingLambda Record")
    class PendingLambdaTests {

        @Test
        @DisplayName("isBiEntity returns true for BI_QUERY_SPEC")
        void isBiEntityReturnsTrueForBiQuerySpec() {
            PendingLambda lambda = new PendingLambda("lambda$0", "(LPerson;LPhone;)Z", "where", LambdaSpecType.BI_QUERY_SPEC);

            assertThat(lambda.isBiEntity()).isTrue();
            assertThat(lambda.isGroupSpec()).isFalse();
        }

        @Test
        @DisplayName("isGroupSpec returns true for GROUP_QUERY_SPEC")
        void isGroupSpecReturnsTrueForGroupQuerySpec() {
            PendingLambda lambda = new PendingLambda("lambda$0", "(LGroup;)Z", "having", LambdaSpecType.GROUP_QUERY_SPEC);

            assertThat(lambda.isGroupSpec()).isTrue();
            assertThat(lambda.isBiEntity()).isFalse();
        }

        @Test
        @DisplayName("standard QuerySpec is neither BiEntity nor GroupSpec")
        void standardQuerySpecIsNeither() {
            PendingLambda lambda = new PendingLambda("lambda$0", "(LPerson;)Z", "where", LambdaSpecType.QUERY_SPEC);

            assertThat(lambda.isBiEntity()).isFalse();
            assertThat(lambda.isGroupSpec()).isFalse();
        }
    }

    // ==================== Complex Scenario Tests ====================

    @Nested
    @DisplayName("Complex Scenarios")
    class ComplexScenarioTests {

        @Test
        @DisplayName("simulates simple where query scan")
        void simulatesSimpleWhereQuery() {
            // Simulate: Person.where(p -> p.age > 18).toList()
            state.updateLine(10);
            state.addLambda(new PendingLambda("lambda$0", "(LPerson;)Z", "where", LambdaSpecType.QUERY_SPEC));
            state.updateLine(11);

            assertThat(state.hasLambdas()).isTrue();
            assertThat(state.isJoinContext()).isFalse();
            assertThat(state.isGroupContext()).isFalse();
            assertThat(state.effectiveLine()).isEqualTo(11);
        }

        @Test
        @DisplayName("simulates join query scan")
        void simulatesJoinQuery() {
            // Simulate: Person.join(p -> p.phones).where((p, ph) -> ph.type.equals("mobile")).toList()
            state.updateLine(20);
            state.addLambda(new PendingLambda("lambda$0", "(LPerson;)LList;", "join", LambdaSpecType.QUERY_SPEC));
            state.setJoinType(JoinType.INNER);
            state.updateLine(21);
            state.addLambda(new PendingLambda("lambda$1", "(LPerson;LPhone;)Z", "where", LambdaSpecType.BI_QUERY_SPEC));

            assertThat(state.hasLambdas()).isTrue();
            assertThat(state.isJoinContext()).isTrue();
            assertThat(state.pendingJoinType()).isEqualTo(JoinType.INNER);
            assertThat(state.pendingLambdas()).hasSize(2);
        }

        @Test
        @DisplayName("simulates group query scan")
        void simulatesGroupQuery() {
            // Simulate: Person.groupBy(p -> p.department).select(g -> g.count()).toList()
            state.updateLine(30);
            state.addLambda(new PendingLambda("lambda$0", "(LPerson;)LString;", "groupBy", LambdaSpecType.QUERY_SPEC));
            state.markGroupQuery();
            state.updateLine(31);
            state.addLambda(new PendingLambda("lambda$1", "(LGroup;)J", "select", LambdaSpecType.GROUP_QUERY_SPEC));
            state.markGroupSelect(31);

            assertThat(state.isGroupContext()).isTrue();
            assertThat(state.effectiveLine()).isEqualTo(31);
        }

        @Test
        @DisplayName("simulates query with pagination")
        void simulatesQueryWithPagination() {
            // Simulate: Person.where(...).distinct().skip(10).limit(25).toList()
            state.updateLine(40);
            state.addLambda(new PendingLambda("lambda$0", "(LPerson;)Z", "where", LambdaSpecType.QUERY_SPEC));
            state.markDistinct();
            state.setSkipValue(10);
            state.setLimitValue(25);

            assertThat(state.hasDistinct()).isTrue();
            assertThat(state.skipValue()).isEqualTo(10);
            assertThat(state.limitValue()).isEqualTo(25);
        }

        @Test
        @DisplayName("simulates multiple call sites in same method")
        void simulatesMultipleCallSites() {
            // First query
            state.updateLine(50);
            state.addLambda(new PendingLambda("lambda$0", "(LPerson;)Z", "where", LambdaSpecType.QUERY_SPEC));
            state.setLimitValue(10);

            // Simulate terminal operation found - reset
            state.reset();

            // Second query
            state.updateLine(55);
            state.addLambda(new PendingLambda("lambda$1", "(LOrder;)Z", "where", LambdaSpecType.QUERY_SPEC));
            state.markDistinct();

            // Verify second query state
            assertThat(state.hasLambdas()).isTrue();
            assertThat(state.pendingLambdas().getFirst().methodName()).isEqualTo("lambda$1");
            assertThat(state.hasDistinct()).isTrue();
            assertThat(state.limitValue()).isNull(); // Reset from first query
            assertThat(state.currentLine()).isEqualTo(55);
        }
    }
}
