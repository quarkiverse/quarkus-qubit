package io.quarkus.qusaq.deployment.bytecode;

import io.quarkus.qusaq.deployment.LambdaExpression;
import io.quarkus.qusaq.deployment.LambdaExpression.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests bytecode analysis for subquery lambdas.
 *
 * <p>Iteration 8: Subqueries - bytecode analysis verification.
 *
 * <p>These tests verify that lambdas containing Subqueries.* calls are correctly
 * analyzed into ScalarSubquery, ExistsSubquery, and InSubquery AST nodes.
 */
class SubqueryLambdaBytecodeTest extends PrecompiledSubqueryLambdaAnalyzer {

    @Nested
    @DisplayName("Scalar Subqueries")
    class ScalarSubqueryTests {

        @Test
        @DisplayName("AVG subquery: p.salary > Subqueries.avg(Person.class, q -> q.salary)")
        void avgSubquery() {
            LambdaExpression expr = analyzeSubqueryLambda("avgSubquery");

            // Should be a binary comparison: p.salary > ScalarSubquery(AVG, ...)
            assertThat(expr).isInstanceOf(BinaryOp.class);
            BinaryOp binOp = (BinaryOp) expr;
            assertThat(binOp.operator()).isEqualTo(BinaryOp.Operator.GT);

            // Left side: p.salary field access
            assertFieldAccess(binOp.left(), "salary");

            // Right side: ScalarSubquery with AVG aggregation
            assertScalarSubquery(binOp.right(), SubqueryAggregationType.AVG);
        }

        @Test
        @DisplayName("MAX subquery: p.salary == Subqueries.max(Person.class, q -> q.salary)")
        void maxSubquery() {
            LambdaExpression expr = analyzeSubqueryLambda("maxSubquery");

            assertThat(expr).isInstanceOf(BinaryOp.class);
            BinaryOp binOp = (BinaryOp) expr;
            assertThat(binOp.operator()).isEqualTo(BinaryOp.Operator.EQ);

            assertFieldAccess(binOp.left(), "salary");
            assertScalarSubquery(binOp.right(), SubqueryAggregationType.MAX);
        }

        @Test
        @DisplayName("MIN subquery: p.salary >= Subqueries.min(Person.class, q -> q.salary)")
        void minSubquery() {
            LambdaExpression expr = analyzeSubqueryLambda("minSubquery");

            assertThat(expr).isInstanceOf(BinaryOp.class);
            BinaryOp binOp = (BinaryOp) expr;
            assertThat(binOp.operator()).isEqualTo(BinaryOp.Operator.GE);

            assertFieldAccess(binOp.left(), "salary");
            assertScalarSubquery(binOp.right(), SubqueryAggregationType.MIN);
        }

        @Test
        @DisplayName("SUM subquery: p.budget > Subqueries.sum(Department.class, d -> d.budget)")
        void sumSubquery() {
            LambdaExpression expr = analyzeSubqueryLambda("sumSubquery");

            assertThat(expr).isInstanceOf(BinaryOp.class);
            BinaryOp binOp = (BinaryOp) expr;
            assertThat(binOp.operator()).isEqualTo(BinaryOp.Operator.GT);

            assertFieldAccess(binOp.left(), "budget");
            assertScalarSubquery(binOp.right(), SubqueryAggregationType.SUM);
        }

        @Test
        @DisplayName("COUNT subquery: p.age > Subqueries.count(Person.class)")
        void countSubquery() {
            LambdaExpression expr = analyzeSubqueryLambda("countSubquery");

            assertThat(expr).isInstanceOf(BinaryOp.class);
            BinaryOp binOp = (BinaryOp) expr;
            assertThat(binOp.operator()).isEqualTo(BinaryOp.Operator.GT);

            assertFieldAccess(binOp.left(), "age");
            assertScalarSubquery(binOp.right(), SubqueryAggregationType.COUNT);
        }
    }

    @Nested
    @DisplayName("EXISTS Subqueries")
    class ExistsSubqueryTests {

        @Test
        @DisplayName("EXISTS subquery: Subqueries.exists(Phone.class, ph -> ph.ownerId == p.id)")
        void existsSubquery() {
            LambdaExpression expr = analyzeSubqueryLambda("existsSubquery");

            // Should be an ExistsSubquery with negated=false
            assertExistsSubquery(expr, false);
        }

        @Test
        @DisplayName("NOT EXISTS subquery: Subqueries.notExists(Phone.class, ph -> ph.ownerId == p.id)")
        void notExistsSubquery() {
            LambdaExpression expr = analyzeSubqueryLambda("notExistsSubquery");

            // Should be an ExistsSubquery with negated=true
            assertExistsSubquery(expr, true);
        }
    }

    @Nested
    @DisplayName("IN Subqueries")
    class InSubqueryTests {

        @Test
        @DisplayName("IN subquery: Subqueries.in(p.departmentId, Department.class, d -> d.id)")
        void inSubquery() {
            LambdaExpression expr = analyzeSubqueryLambda("inSubquery");

            // Should be an InSubquery with negated=false
            assertInSubquery(expr, false);
        }

        @Test
        @DisplayName("NOT IN subquery: Subqueries.notIn(p.departmentId, Department.class, d -> d.id)")
        void notInSubquery() {
            LambdaExpression expr = analyzeSubqueryLambda("notInSubquery");

            // Should be an InSubquery with negated=true
            assertInSubquery(expr, true);
        }
    }
}
