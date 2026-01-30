package io.quarkiverse.qubit.deployment.analysis;

import io.quarkiverse.qubit.SortDirection;
import io.quarkiverse.qubit.deployment.ast.LambdaExpression;
import io.quarkiverse.qubit.deployment.analysis.LambdaAnalysisResult.SortExpression;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link HashBuilder}.
 */
class HashBuilderTest {

    // ========================================================================
    // Factory Method Tests
    // ========================================================================

    @Nested
    class FactoryMethodTests {

        @Test
        void create_returnsNewInstance() {
            HashBuilder builder = HashBuilder.create();
            assertThat(builder).isNotNull();
        }

        @Test
        void create_returnsEmptyBuilder() {
            HashBuilder builder = HashBuilder.create();
            assertThat(builder.buildString()).isEmpty();
        }

        @Test
        void create_returnsDistinctInstances() {
            HashBuilder builder1 = HashBuilder.create();
            HashBuilder builder2 = HashBuilder.create();
            assertThat(builder1).isNotSameAs(builder2);
        }
    }

    // ========================================================================
    // Expression Component Tests
    // ========================================================================

    @Nested
    class ExpressionComponentTests {

        @Test
        void expression_withValue_addsToBuilder() {
            LambdaExpression expr = new LambdaExpression.FieldAccess("name", String.class);

            String result = HashBuilder.create()
                    .expression(expr)
                    .buildString();

            assertThat(result).contains("name");
        }

        @Test
        void expression_withNull_isIgnored() {
            String result = HashBuilder.create()
                    .expression(null)
                    .buildString();

            assertThat(result).isEmpty();
        }

        @Test
        void where_withValue_addsWherePrefix() {
            LambdaExpression expr = new LambdaExpression.FieldAccess("age", int.class);

            String result = HashBuilder.create()
                    .where(expr)
                    .buildString();

            assertThat(result)
                    .startsWith("WHERE=")
                    .contains("age");
        }

        @Test
        void where_withNull_isIgnored() {
            String result = HashBuilder.create()
                    .where(null)
                    .buildString();

            assertThat(result).isEmpty();
        }

        @Test
        void select_withValue_addsSelectPrefix() {
            LambdaExpression expr = new LambdaExpression.FieldAccess("email", String.class);

            String result = HashBuilder.create()
                    .select(expr)
                    .buildString();

            assertThat(result)
                    .startsWith("SELECT=")
                    .contains("email");
        }

        @Test
        void select_withNull_isIgnored() {
            String result = HashBuilder.create()
                    .select(null)
                    .buildString();

            assertThat(result).isEmpty();
        }

        @Test
        void aggregation_withValue_addsAggPrefix() {
            LambdaExpression expr = new LambdaExpression.FieldAccess("salary", double.class);

            String result = HashBuilder.create()
                    .aggregation(expr)
                    .buildString();

            assertThat(result)
                    .startsWith("AGG=")
                    .contains("salary");
        }

        @Test
        void aggregation_withNull_isIgnored() {
            String result = HashBuilder.create()
                    .aggregation(null)
                    .buildString();

            assertThat(result).isEmpty();
        }

        @Test
        void aggregationType_withValue_addsTypePrefix() {
            String result = HashBuilder.create()
                    .aggregationType("SUM")
                    .buildString();

            assertThat(result).isEqualTo("TYPE=SUM");
        }

        @Test
        void aggregationType_withNull_isIgnored() {
            String result = HashBuilder.create()
                    .aggregationType(null)
                    .buildString();

            assertThat(result).isEmpty();
        }
    }

    // ========================================================================
    // Sort Component Tests
    // ========================================================================

    @Nested
    class SortComponentTests {

        @Test
        void sort_withExpressions_addsSortString() {
            LambdaExpression keyExpr = new LambdaExpression.FieldAccess("name", String.class);
            SortExpression sortExpr = new SortExpression(keyExpr, SortDirection.ASCENDING);

            String result = HashBuilder.create()
                    .sort(List.of(sortExpr))
                    .buildString();

            assertThat(result)
                    .startsWith("SORT=")
                    .contains(":ASC");
        }

        @Test
        void sort_withMultipleExpressions_joinsWithComma() {
            LambdaExpression expr1 = new LambdaExpression.FieldAccess("lastName", String.class);
            LambdaExpression expr2 = new LambdaExpression.FieldAccess("firstName", String.class);
            SortExpression sort1 = new SortExpression(expr1, SortDirection.ASCENDING);
            SortExpression sort2 = new SortExpression(expr2, SortDirection.DESCENDING);

            String result = HashBuilder.create()
                    .sort(List.of(sort1, sort2))
                    .buildString();

            assertThat(result)
                    .contains(",")
                    .contains(":ASC")
                    .contains(":DESC");
        }

        @Test
        void sort_withEmptyList_isIgnored() {
            String result = HashBuilder.create()
                    .sort(List.of())
                    .buildString();

            assertThat(result).isEmpty();
        }

        @Test
        void sort_withNull_isIgnored() {
            String result = HashBuilder.create()
                    .sort(null)
                    .buildString();

            assertThat(result).isEmpty();
        }
    }

    // ========================================================================
    // Join Component Tests
    // ========================================================================

    @Nested
    class JoinComponentTests {

        @Test
        void join_withValue_addsJoinPrefix() {
            LambdaExpression expr = new LambdaExpression.FieldAccess("department", String.class);

            String result = HashBuilder.create()
                    .join(expr)
                    .buildString();

            assertThat(result)
                    .startsWith("JOIN=")
                    .contains("department");
        }

        @Test
        void join_withNull_addsJoinPrefixOnly() {
            String result = HashBuilder.create()
                    .join(null)
                    .buildString();

            // join always adds prefix, even with null
            assertThat(result).isEqualTo("JOIN=");
        }

        @Test
        void biWhere_withValue_addsBiWherePrefix() {
            LambdaExpression expr = new LambdaExpression.FieldAccess("status", String.class);

            String result = HashBuilder.create()
                    .biWhere(expr)
                    .buildString();

            assertThat(result)
                    .startsWith("BI_WHERE=")
                    .contains("status");
        }

        @Test
        void biWhere_withNull_isIgnored() {
            String result = HashBuilder.create()
                    .biWhere(null)
                    .buildString();

            assertThat(result).isEmpty();
        }

        @Test
        void biSelect_withValue_addsBiSelectPrefix() {
            LambdaExpression expr = new LambdaExpression.FieldAccess("result", Object.class);

            String result = HashBuilder.create()
                    .biSelect(expr)
                    .buildString();

            assertThat(result)
                    .startsWith("BI_SELECT=")
                    .contains("result");
        }

        @Test
        void biSelect_withNull_isIgnored() {
            String result = HashBuilder.create()
                    .biSelect(null)
                    .buildString();

            assertThat(result).isEmpty();
        }

        @Test
        void joinType_withValue_addsJoinTypePrefix() {
            String result = HashBuilder.create()
                    .joinType("INNER")
                    .buildString();

            assertThat(result).isEqualTo("JOIN_TYPE=INNER");
        }

        @Test
        void joinType_withNull_isIgnored() {
            String result = HashBuilder.create()
                    .joinType(null)
                    .buildString();

            assertThat(result).isEmpty();
        }

        @Test
        void selectJoined_whenTrue_addsFlag() {
            String result = HashBuilder.create()
                    .selectJoined(true)
                    .buildString();

            assertThat(result).isEqualTo("SELECT_JOINED=true");
        }

        @Test
        void selectJoined_whenFalse_isIgnored() {
            String result = HashBuilder.create()
                    .selectJoined(false)
                    .buildString();

            assertThat(result).isEmpty();
        }

        @Test
        void joinProjection_whenTrue_addsFlag() {
            String result = HashBuilder.create()
                    .joinProjection(true)
                    .buildString();

            assertThat(result).isEqualTo("JOIN_PROJECTION=true");
        }

        @Test
        void joinProjection_whenFalse_isIgnored() {
            String result = HashBuilder.create()
                    .joinProjection(false)
                    .buildString();

            assertThat(result).isEmpty();
        }
    }

    // ========================================================================
    // Group Component Tests
    // ========================================================================

    @Nested
    class GroupComponentTests {

        @Test
        void groupBy_withValue_addsGroupByPrefix() {
            LambdaExpression expr = new LambdaExpression.FieldAccess("category", String.class);

            String result = HashBuilder.create()
                    .groupBy(expr)
                    .buildString();

            assertThat(result)
                    .startsWith("GROUP_BY=")
                    .contains("category");
        }

        @Test
        void groupBy_withNull_addsGroupByPrefixOnly() {
            String result = HashBuilder.create()
                    .groupBy(null)
                    .buildString();

            // groupBy always adds prefix, even with null
            assertThat(result).isEqualTo("GROUP_BY=");
        }

        @Test
        void having_withValue_addsHavingPrefix() {
            LambdaExpression expr = new LambdaExpression.FieldAccess("count", int.class);

            String result = HashBuilder.create()
                    .having(expr)
                    .buildString();

            assertThat(result)
                    .startsWith("HAVING=")
                    .contains("count");
        }

        @Test
        void having_withNull_isIgnored() {
            String result = HashBuilder.create()
                    .having(null)
                    .buildString();

            assertThat(result).isEmpty();
        }

        @Test
        void groupSelect_withValue_addsGroupSelectPrefix() {
            LambdaExpression expr = new LambdaExpression.FieldAccess("total", long.class);

            String result = HashBuilder.create()
                    .groupSelect(expr)
                    .buildString();

            assertThat(result)
                    .startsWith("GROUP_SELECT=")
                    .contains("total");
        }

        @Test
        void groupSelect_withNull_isIgnored() {
            String result = HashBuilder.create()
                    .groupSelect(null)
                    .buildString();

            assertThat(result).isEmpty();
        }
    }

    // ========================================================================
    // Query Type Tests
    // ========================================================================

    @Nested
    class QueryTypeTests {

        @Test
        void queryType_withString_addsQueryTypePrefix() {
            String result = HashBuilder.create()
                    .queryType("LIST")
                    .buildString();

            assertThat(result).isEqualTo("queryType=LIST");
        }

        @Test
        void queryType_withNull_isIgnored() {
            String result = HashBuilder.create()
                    .queryType((String) null)
                    .buildString();

            assertThat(result).isEmpty();
        }

        @Test
        void queryType_booleanTrue_addsCountType() {
            String result = HashBuilder.create()
                    .queryType(true)
                    .buildString();

            assertThat(result).isEqualTo("queryType=COUNT");
        }

        @Test
        void queryType_booleanFalse_addsListType() {
            String result = HashBuilder.create()
                    .queryType(false)
                    .buildString();

            assertThat(result).isEqualTo("queryType=LIST");
        }
    }

    // ========================================================================
    // Chaining and Separator Tests
    // ========================================================================

    @Nested
    class ChainingTests {

        @Test
        void multipleComponents_separatedByPipe() {
            LambdaExpression whereExpr = new LambdaExpression.FieldAccess("age", int.class);
            LambdaExpression selectExpr = new LambdaExpression.FieldAccess("name", String.class);

            String result = HashBuilder.create()
                    .where(whereExpr)
                    .select(selectExpr)
                    .buildString();

            assertThat(result).contains("|");
            String[] parts = result.split("\\|");
            assertThat(parts).hasSize(2);
            assertThat(parts[0]).startsWith("WHERE=");
            assertThat(parts[1]).startsWith("SELECT=");
        }

        @Test
        void fluentChaining_returnsSameBuilder() {
            HashBuilder builder = HashBuilder.create();

            HashBuilder returned = builder
                    .where(null)
                    .select(null)
                    .sort(null)
                    .aggregation(null)
                    .aggregationType(null);

            assertThat(returned).isSameAs(builder);
        }

        @Test
        void complexQuery_buildsCorrectly() {
            LambdaExpression whereExpr = new LambdaExpression.FieldAccess("status", String.class);
            LambdaExpression selectExpr = new LambdaExpression.FieldAccess("name", String.class);
            LambdaExpression sortKeyExpr = new LambdaExpression.FieldAccess("createdAt", java.util.Date.class);
            SortExpression sortExpr = new SortExpression(sortKeyExpr, SortDirection.DESCENDING);

            String result = HashBuilder.create()
                    .where(whereExpr)
                    .select(selectExpr)
                    .sort(List.of(sortExpr))
                    .queryType(false)
                    .buildString();

            assertThat(result)
                    .contains("WHERE=")
                    .contains("SELECT=")
                    .contains("SORT=")
                    .contains(":DESC")
                    .contains("queryType=LIST");

            // Check separators
            long separatorCount = result.chars().filter(c -> c == '|').count();
            assertThat(separatorCount).isEqualTo(3);
        }

        @Test
        void nullsSkipped_noDuplicateSeparators() {
            LambdaExpression expr = new LambdaExpression.FieldAccess("test", String.class);

            String result = HashBuilder.create()
                    .where(null)
                    .expression(expr)
                    .select(null)
                    .aggregation(null)
                    .queryType("LIST")
                    .buildString();

            // Should not have consecutive separators
            assertThat(result)
                    .doesNotContain("||")
                    .doesNotStartWith("|");
        }
    }

    // ========================================================================
    // Build Methods Tests
    // ========================================================================

    @Nested
    class BuildMethodTests {

        @Test
        void buildString_returnsRawContent() {
            String result = HashBuilder.create()
                    .queryType("TEST")
                    .buildString();

            assertThat(result).isEqualTo("queryType=TEST");
        }

        @Test
        void buildHash_returnsMd5Hash() {
            String hash = HashBuilder.create()
                    .queryType("TEST")
                    .buildHash();

            // MD5 hash is 32 hex characters
            assertThat(hash)
                    .hasSize(32)
                    .matches("[0-9a-f]{32}");
        }

        @Test
        void buildHash_sameInput_sameHash() {
            String hash1 = HashBuilder.create()
                    .queryType("TEST")
                    .buildHash();

            String hash2 = HashBuilder.create()
                    .queryType("TEST")
                    .buildHash();

            assertThat(hash1).isEqualTo(hash2);
        }

        @Test
        void buildHash_differentInput_differentHash() {
            String hash1 = HashBuilder.create()
                    .queryType("LIST")
                    .buildHash();

            String hash2 = HashBuilder.create()
                    .queryType("COUNT")
                    .buildHash();

            assertThat(hash1).isNotEqualTo(hash2);
        }

        @Test
        void buildHash_emptyBuilder_returnsValidHash() {
            String hash = HashBuilder.create().buildHash();

            // MD5 of empty string
            assertThat(hash)
                    .hasSize(32)
                    .matches("[0-9a-f]{32}");
        }

        @Test
        void buildString_emptyBuilder_returnsEmptyString() {
            String result = HashBuilder.create().buildString();

            assertThat(result).isEmpty();
        }
    }

    // ========================================================================
    // Integration Tests
    // ========================================================================

    @Nested
    class IntegrationTests {

        @Test
        void fullQueryWithAllComponents_buildsCorrectly() {
            LambdaExpression whereExpr = new LambdaExpression.FieldAccess("active", boolean.class);
            LambdaExpression selectExpr = new LambdaExpression.FieldAccess("data", Object.class);
            LambdaExpression sortKeyExpr = new LambdaExpression.FieldAccess("priority", int.class);
            LambdaExpression groupByExpr = new LambdaExpression.FieldAccess("category", String.class);
            LambdaExpression havingExpr = new LambdaExpression.FieldAccess("count", int.class);

            String result = HashBuilder.create()
                    .where(whereExpr)
                    .select(selectExpr)
                    .sort(List.of(new SortExpression(sortKeyExpr, SortDirection.ASCENDING)))
                    .groupBy(groupByExpr)
                    .having(havingExpr)
                    .queryType(false)
                    .buildString();

            assertThat(result)
                    .contains("WHERE=")
                    .contains("SELECT=")
                    .contains("SORT=")
                    .contains("GROUP_BY=")
                    .contains("HAVING=")
                    .contains("queryType=LIST");
        }

        @Test
        void joinQuery_buildsCorrectly() {
            LambdaExpression joinExpr = new LambdaExpression.FieldAccess("relatedEntity", Object.class);
            LambdaExpression biWhereExpr = new LambdaExpression.FieldAccess("match", boolean.class);

            String result = HashBuilder.create()
                    .join(joinExpr)
                    .biWhere(biWhereExpr)
                    .joinType("LEFT")
                    .selectJoined(true)
                    .buildString();

            assertThat(result)
                    .contains("JOIN=")
                    .contains("BI_WHERE=")
                    .contains("JOIN_TYPE=LEFT")
                    .contains("SELECT_JOINED=true");
        }

        @Test
        void aggregationQuery_buildsCorrectly() {
            LambdaExpression aggExpr = new LambdaExpression.FieldAccess("amount", double.class);

            String result = HashBuilder.create()
                    .aggregation(aggExpr)
                    .aggregationType("SUM")
                    .queryType(true)
                    .buildString();

            assertThat(result)
                    .contains("AGG=")
                    .contains("TYPE=SUM")
                    .contains("queryType=COUNT");
        }
    }
}
