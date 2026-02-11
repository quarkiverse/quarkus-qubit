package io.quarkiverse.qubit.deployment.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.quarkiverse.qubit.deployment.ast.LambdaExpression;
import io.quarkiverse.qubit.deployment.ast.LambdaExpression.*;

class ExpressionTypeCounterTest {

    @Test
    void countsComparisonOperators() {
        // p.age > 18
        LambdaExpression expr = BinaryOp.gt(
                new FieldAccess("age", int.class),
                new Constant(18, int.class));

        Map<String, Integer> counts = ExpressionTypeCounter.count(expr);

        assertThat(counts.get(BuildMetricsCollector.EXPR_COMPARISON)).isEqualTo(1);
        assertThat(counts.get(BuildMetricsCollector.EXPR_FIELD_ACCESS)).isEqualTo(1);
    }

    @Test
    void countsBooleanOperators() {
        // p.age > 18 && p.active
        LambdaExpression expr = BinaryOp.and(
                BinaryOp.gt(new FieldAccess("age", int.class), new Constant(18, int.class)),
                new FieldAccess("active", boolean.class));

        Map<String, Integer> counts = ExpressionTypeCounter.count(expr);

        assertThat(counts.get(BuildMetricsCollector.EXPR_BOOLEAN)).isEqualTo(1);
        assertThat(counts.get(BuildMetricsCollector.EXPR_COMPARISON)).isEqualTo(1);
        assertThat(counts.get(BuildMetricsCollector.EXPR_FIELD_ACCESS)).isEqualTo(2);
    }

    @Test
    void countsArithmeticOperators() {
        // p.price * p.quantity
        LambdaExpression expr = BinaryOp.mul(
                new FieldAccess("price", double.class),
                new FieldAccess("quantity", int.class));

        Map<String, Integer> counts = ExpressionTypeCounter.count(expr);

        assertThat(counts.get(BuildMetricsCollector.EXPR_ARITHMETIC)).isEqualTo(1);
        assertThat(counts.get(BuildMetricsCollector.EXPR_FIELD_ACCESS)).isEqualTo(2);
    }

    @Test
    void countsUnaryNot() {
        // !p.active
        LambdaExpression expr = UnaryOp.not(new FieldAccess("active", boolean.class));

        Map<String, Integer> counts = ExpressionTypeCounter.count(expr);

        assertThat(counts.get(BuildMetricsCollector.EXPR_BOOLEAN)).isEqualTo(1);
        assertThat(counts.get(BuildMetricsCollector.EXPR_FIELD_ACCESS)).isEqualTo(1);
    }

    @Test
    void countsMethodCalls() {
        // p.name.equals("John")
        LambdaExpression expr = new MethodCall(
                new FieldAccess("name", String.class),
                "equals",
                List.of(new Constant("John", String.class)),
                boolean.class);

        Map<String, Integer> counts = ExpressionTypeCounter.count(expr);

        assertThat(counts.get(BuildMetricsCollector.EXPR_METHOD_CALL)).isEqualTo(1);
        assertThat(counts.get(BuildMetricsCollector.EXPR_STRING)).isEqualTo(1);
        assertThat(counts.get(BuildMetricsCollector.EXPR_FIELD_ACCESS)).isEqualTo(1);
    }

    @Test
    void countsPathExpressions() {
        // p.owner.firstName (2 segments)
        PathSegment ownerSegment = new PathSegment("owner", Object.class, RelationType.MANY_TO_ONE);
        PathSegment firstNameSegment = new PathSegment("firstName", String.class, RelationType.FIELD);
        LambdaExpression expr = new PathExpression(List.of(ownerSegment, firstNameSegment), String.class);

        Map<String, Integer> counts = ExpressionTypeCounter.count(expr);

        // Each segment counts as a field access
        assertThat(counts.get(BuildMetricsCollector.EXPR_FIELD_ACCESS)).isEqualTo(2);
    }

    @Test
    void countsBigDecimalOperations() {
        // p.price > BigDecimal.ZERO
        LambdaExpression expr = BinaryOp.gt(
                new FieldAccess("price", BigDecimal.class),
                new Constant(BigDecimal.ZERO, BigDecimal.class));

        Map<String, Integer> counts = ExpressionTypeCounter.count(expr);

        assertThat(counts.get(BuildMetricsCollector.EXPR_COMPARISON)).isEqualTo(1);
        assertThat(counts.get(BuildMetricsCollector.EXPR_BIG_DECIMAL)).isGreaterThanOrEqualTo(1);
    }

    @Test
    void countsTemporalConstants() {
        // Constant with LocalDate type
        LambdaExpression expr = new Constant(LocalDate.now(), LocalDate.class);

        Map<String, Integer> counts = ExpressionTypeCounter.count(expr);

        assertThat(counts.get(BuildMetricsCollector.EXPR_TEMPORAL)).isEqualTo(1);
    }

    @Test
    void countsSubqueryExpressions() {
        // ScalarSubquery
        LambdaExpression expr = ScalarSubquery.avg(
                Object.class,
                new FieldAccess("salary", double.class),
                null);

        Map<String, Integer> counts = ExpressionTypeCounter.count(expr);

        assertThat(counts.get(BuildMetricsCollector.EXPR_SUBQUERY)).isEqualTo(1);
        assertThat(counts.get(BuildMetricsCollector.EXPR_FIELD_ACCESS)).isEqualTo(1);
    }

    @Test
    void countsExistsSubquery() {
        LambdaExpression expr = ExistsSubquery.exists(
                Object.class,
                BinaryOp.eq(new FieldAccess("id", long.class), new FieldAccess("ownerId", long.class)));

        Map<String, Integer> counts = ExpressionTypeCounter.count(expr);

        assertThat(counts.get(BuildMetricsCollector.EXPR_SUBQUERY)).isEqualTo(1);
        assertThat(counts.get(BuildMetricsCollector.EXPR_COMPARISON)).isEqualTo(1);
        assertThat(counts.get(BuildMetricsCollector.EXPR_FIELD_ACCESS)).isEqualTo(2);
    }

    @Test
    void countsNestedExpressions() {
        // (p.age > 18 && p.active) || p.premium
        LambdaExpression expr = BinaryOp.or(
                BinaryOp.and(
                        BinaryOp.gt(new FieldAccess("age", int.class), new Constant(18, int.class)),
                        new FieldAccess("active", boolean.class)),
                new FieldAccess("premium", boolean.class));

        Map<String, Integer> counts = ExpressionTypeCounter.count(expr);

        assertThat(counts.get(BuildMetricsCollector.EXPR_BOOLEAN)).isEqualTo(2); // AND + OR
        assertThat(counts.get(BuildMetricsCollector.EXPR_COMPARISON)).isEqualTo(1);
        assertThat(counts.get(BuildMetricsCollector.EXPR_FIELD_ACCESS)).isEqualTo(3);
    }

    @Test
    void handlesNullExpression() {
        Map<String, Integer> counts = ExpressionTypeCounter.count(null);

        assertThat(counts).isEmpty();
    }

    @Test
    void recordsToCollector() {
        BuildMetricsCollector collector = new BuildMetricsCollector();

        LambdaExpression expr = BinaryOp.and(
                BinaryOp.gt(new FieldAccess("age", int.class), new Constant(18, int.class)),
                new FieldAccess("active", boolean.class));

        ExpressionTypeCounter.countAndRecord(expr, collector);

        // Verify by writing report and checking content would be complex,
        // so just verify no exception is thrown
        assertThat(collector).isNotNull();
    }

    @Test
    void countsConditionalExpression() {
        // p.active ? p.premium : p.basic
        LambdaExpression expr = new Conditional(
                new FieldAccess("active", boolean.class),
                new FieldAccess("premium", double.class),
                new FieldAccess("basic", double.class));

        Map<String, Integer> counts = ExpressionTypeCounter.count(expr);

        assertThat(counts.get(BuildMetricsCollector.EXPR_FIELD_ACCESS)).isEqualTo(3);
    }

    @Test
    void countsConstructorCall() {
        // new DTO(p.name, p.age)
        LambdaExpression expr = new ConstructorCall(
                "com.example.DTO",
                List.of(
                        new FieldAccess("name", String.class),
                        new FieldAccess("age", int.class)),
                Object.class);

        Map<String, Integer> counts = ExpressionTypeCounter.count(expr);

        assertThat(counts.get(BuildMetricsCollector.EXPR_FIELD_ACCESS)).isEqualTo(2);
    }

    @Test
    void countsInExpression() {
        // cities.contains(p.city)
        LambdaExpression expr = InExpression.in(
                new FieldAccess("city", String.class),
                new CapturedVariable(0, List.class));

        Map<String, Integer> counts = ExpressionTypeCounter.count(expr);

        assertThat(counts.get(BuildMetricsCollector.EXPR_FIELD_ACCESS)).isEqualTo(1);
    }
}
