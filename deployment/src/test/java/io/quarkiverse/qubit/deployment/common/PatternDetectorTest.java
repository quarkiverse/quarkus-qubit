package io.quarkiverse.qubit.deployment.common;

import io.quarkiverse.qubit.deployment.ast.LambdaExpression;
import io.quarkiverse.qubit.deployment.ast.LambdaExpression.BinaryOp;
import io.quarkiverse.qubit.deployment.ast.LambdaExpression.BinaryOp.Operator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;

import java.util.ArrayDeque;
import java.util.Deque;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for PatternDetector utility class.
 *
 * <p>These tests target surviving mutations in the PatternDetector class,
 * particularly the boolean return value mutations and equality check conditions.
 */
class PatternDetectorTest {

    // ==================== isBooleanFieldCapturedVariableComparison Tests ====================

    @Nested
    @DisplayName("isBooleanFieldCapturedVariableComparison")
    class BooleanFieldCapturedVariableComparisonTests {

        @Test
        void returnsFalse_whenNotEqualityOperation() {
            // Test lines 367-368: !isEqualityOperation returns false
            BinaryOp binOp = new BinaryOp(
                    new LambdaExpression.FieldAccess("active", boolean.class),
                    Operator.LT,  // Not EQ or NE
                    new LambdaExpression.CapturedVariable(0, boolean.class)
            );

            boolean result = PatternDetector.isBooleanFieldCapturedVariableComparison(binOp);

            assertThat(result).isFalse();
        }

        @Test
        void returnsFalse_whenLeftIsNotFieldAccess() {
            // Test lines 371-372: left not instanceof FieldAccess
            BinaryOp binOp = new BinaryOp(
                    new LambdaExpression.Constant(true, boolean.class),
                    Operator.EQ,
                    new LambdaExpression.CapturedVariable(0, boolean.class)
            );

            boolean result = PatternDetector.isBooleanFieldCapturedVariableComparison(binOp);

            assertThat(result).isFalse();
        }

        @Test
        void returnsFalse_whenFieldTypeIsNotBoolean() {
            // Test lines 375-376: !isBooleanType(fieldType)
            BinaryOp binOp = new BinaryOp(
                    new LambdaExpression.FieldAccess("name", String.class),
                    Operator.EQ,
                    new LambdaExpression.CapturedVariable(0, String.class)
            );

            boolean result = PatternDetector.isBooleanFieldCapturedVariableComparison(binOp);

            assertThat(result).isFalse();
        }

        @Test
        void returnsFalse_whenRightIsNotCapturedVariable() {
            // Test lines 379-380: right not instanceof CapturedVariable
            BinaryOp binOp = new BinaryOp(
                    new LambdaExpression.FieldAccess("active", boolean.class),
                    Operator.EQ,
                    new LambdaExpression.Constant(true, boolean.class)
            );

            boolean result = PatternDetector.isBooleanFieldCapturedVariableComparison(binOp);

            assertThat(result).isFalse();
        }

        @Test
        void returnsFalse_whenCapturedVariableTypeIsNotBoolean() {
            // Test line 383: !isBooleanType(capturedVar.type())
            BinaryOp binOp = new BinaryOp(
                    new LambdaExpression.FieldAccess("active", boolean.class),
                    Operator.EQ,
                    new LambdaExpression.CapturedVariable(0, int.class)
            );

            boolean result = PatternDetector.isBooleanFieldCapturedVariableComparison(binOp);

            assertThat(result).isFalse();
        }

        @Test
        void returnsTrue_withPrimitiveBooleanFieldAndCapturedVariable() {
            // Test line 383: true when all conditions met (primitive boolean)
            BinaryOp binOp = new BinaryOp(
                    new LambdaExpression.FieldAccess("active", boolean.class),
                    Operator.EQ,
                    new LambdaExpression.CapturedVariable(0, boolean.class)
            );

            boolean result = PatternDetector.isBooleanFieldCapturedVariableComparison(binOp);

            assertThat(result).isTrue();
        }

        @Test
        void returnsTrue_withBoxedBooleanFieldAndCapturedVariable() {
            // Test with Boolean.class (boxed)
            BinaryOp binOp = new BinaryOp(
                    new LambdaExpression.FieldAccess("active", Boolean.class),
                    Operator.EQ,
                    new LambdaExpression.CapturedVariable(0, Boolean.class)
            );

            boolean result = PatternDetector.isBooleanFieldCapturedVariableComparison(binOp);

            assertThat(result).isTrue();
        }

        @Test
        void returnsTrue_withNEOperator() {
            // Test with NE instead of EQ
            BinaryOp binOp = new BinaryOp(
                    new LambdaExpression.FieldAccess("active", boolean.class),
                    Operator.NE,
                    new LambdaExpression.CapturedVariable(0, boolean.class)
            );

            boolean result = PatternDetector.isBooleanFieldCapturedVariableComparison(binOp);

            assertThat(result).isTrue();
        }
    }

    // ==================== isEqualityOperation Tests ====================

    @Nested
    @DisplayName("isEqualityOperation")
    class EqualityOperationTests {

        @Test
        void returnsTrue_forEQ() {
            BinaryOp binOp = BinaryOp.eq(
                    new LambdaExpression.Constant(1, int.class),
                    new LambdaExpression.Constant(1, int.class)
            );

            assertThat(PatternDetector.isEqualityOperation(binOp)).isTrue();
        }

        @Test
        void returnsTrue_forNE() {
            BinaryOp binOp = BinaryOp.ne(
                    new LambdaExpression.Constant(1, int.class),
                    new LambdaExpression.Constant(2, int.class)
            );

            assertThat(PatternDetector.isEqualityOperation(binOp)).isTrue();
        }

        @Test
        void returnsFalse_forLT() {
            BinaryOp binOp = BinaryOp.lt(
                    new LambdaExpression.Constant(1, int.class),
                    new LambdaExpression.Constant(2, int.class)
            );

            assertThat(PatternDetector.isEqualityOperation(binOp)).isFalse();
        }

        @Test
        void returnsFalse_forAND() {
            BinaryOp binOp = BinaryOp.and(
                    new LambdaExpression.Constant(true, boolean.class),
                    new LambdaExpression.Constant(true, boolean.class)
            );

            assertThat(PatternDetector.isEqualityOperation(binOp)).isFalse();
        }
    }

    // ==================== isCompareToEqualityPattern Tests ====================

    @Nested
    @DisplayName("isCompareToEqualityPattern")
    class CompareToEqualityPatternTests {

        @Test
        void returnsFalse_whenNotEQ() {
            // Test line 390: operator != EQ
            LambdaExpression target = new LambdaExpression.FieldAccess("name", String.class);
            BinaryOp binOp = new BinaryOp(
                    new LambdaExpression.MethodCall(target, "compareTo", java.util.List.of(), int.class),
                    Operator.NE,
                    new LambdaExpression.Constant(0, int.class)
            );

            assertThat(PatternDetector.isCompareToEqualityPattern(binOp)).isFalse();
        }

        @Test
        void returnsFalse_whenLeftIsNotMethodCall() {
            // Test lines 394-395: left not instanceof MethodCall
            BinaryOp binOp = new BinaryOp(
                    new LambdaExpression.Constant(0, int.class),
                    Operator.EQ,
                    new LambdaExpression.Constant(0, int.class)
            );

            assertThat(PatternDetector.isCompareToEqualityPattern(binOp)).isFalse();
        }

        @Test
        void returnsFalse_whenMethodNameIsNotCompareTo() {
            // Test lines 398-400: methodName != "compareTo"
            LambdaExpression target = new LambdaExpression.FieldAccess("name", String.class);
            BinaryOp binOp = new BinaryOp(
                    new LambdaExpression.MethodCall(target, "equals", java.util.List.of(), boolean.class),
                    Operator.EQ,
                    new LambdaExpression.Constant(0, int.class)
            );

            assertThat(PatternDetector.isCompareToEqualityPattern(binOp)).isFalse();
        }

        @Test
        void returnsFalse_whenRightIsNotConstant() {
            // Test line 402: right not instanceof Constant
            LambdaExpression target = new LambdaExpression.FieldAccess("name", String.class);
            BinaryOp binOp = new BinaryOp(
                    new LambdaExpression.MethodCall(target, "compareTo", java.util.List.of(), int.class),
                    Operator.EQ,
                    new LambdaExpression.FieldAccess("value", int.class)
            );

            assertThat(PatternDetector.isCompareToEqualityPattern(binOp)).isFalse();
        }

        @Test
        void returnsTrue_forValidPattern() {
            LambdaExpression target = new LambdaExpression.FieldAccess("name", String.class);
            BinaryOp binOp = new BinaryOp(
                    new LambdaExpression.MethodCall(target, "compareTo", java.util.List.of(), int.class),
                    Operator.EQ,
                    new LambdaExpression.Constant(0, int.class)
            );

            assertThat(PatternDetector.isCompareToEqualityPattern(binOp)).isTrue();
        }
    }

    // ==================== isNullCheckPattern Tests ====================

    @Nested
    @DisplayName("isNullCheckPattern")
    class NullCheckPatternTests {

        @Test
        void returnsFalse_whenNotEqualityOperation() {
            BinaryOp binOp = new BinaryOp(
                    new LambdaExpression.FieldAccess("name", String.class),
                    Operator.LT,
                    new LambdaExpression.NullLiteral(Object.class)
            );

            assertThat(PatternDetector.isNullCheckPattern(binOp)).isFalse();
        }

        @Test
        void returnsTrue_whenLeftIsNullLiteral() {
            BinaryOp binOp = new BinaryOp(
                    new LambdaExpression.NullLiteral(Object.class),
                    Operator.EQ,
                    new LambdaExpression.FieldAccess("name", String.class)
            );

            assertThat(PatternDetector.isNullCheckPattern(binOp)).isTrue();
        }

        @Test
        void returnsTrue_whenRightIsNullLiteral() {
            BinaryOp binOp = new BinaryOp(
                    new LambdaExpression.FieldAccess("name", String.class),
                    Operator.EQ,
                    new LambdaExpression.NullLiteral(Object.class)
            );

            assertThat(PatternDetector.isNullCheckPattern(binOp)).isTrue();
        }

        @Test
        void returnsFalse_whenNoNullLiteral() {
            BinaryOp binOp = new BinaryOp(
                    new LambdaExpression.FieldAccess("name", String.class),
                    Operator.EQ,
                    new LambdaExpression.Constant("test", String.class)
            );

            assertThat(PatternDetector.isNullCheckPattern(binOp)).isFalse();
        }
    }

    // ==================== isBooleanFieldConstantComparison Tests ====================

    @Nested
    @DisplayName("isBooleanFieldConstantComparison")
    class BooleanFieldConstantComparisonTests {

        @Test
        void returnsFalse_whenNotEqualityOperation() {
            BinaryOp binOp = new BinaryOp(
                    new LambdaExpression.FieldAccess("active", boolean.class),
                    Operator.LT,
                    new LambdaExpression.Constant(1, int.class)
            );

            assertThat(PatternDetector.isBooleanFieldConstantComparison(binOp)).isFalse();
        }

        @Test
        void returnsFalse_whenLeftIsNotFieldAccess() {
            BinaryOp binOp = new BinaryOp(
                    new LambdaExpression.Constant(true, boolean.class),
                    Operator.EQ,
                    new LambdaExpression.Constant(1, int.class)
            );

            assertThat(PatternDetector.isBooleanFieldConstantComparison(binOp)).isFalse();
        }

        @Test
        void returnsFalse_whenFieldTypeIsNotBoolean() {
            BinaryOp binOp = new BinaryOp(
                    new LambdaExpression.FieldAccess("count", int.class),
                    Operator.EQ,
                    new LambdaExpression.Constant(1, int.class)
            );

            assertThat(PatternDetector.isBooleanFieldConstantComparison(binOp)).isFalse();
        }

        @Test
        void returnsFalse_whenRightIsNotConstant() {
            BinaryOp binOp = new BinaryOp(
                    new LambdaExpression.FieldAccess("active", boolean.class),
                    Operator.EQ,
                    new LambdaExpression.FieldAccess("other", int.class)
            );

            assertThat(PatternDetector.isBooleanFieldConstantComparison(binOp)).isFalse();
        }

        @Test
        void returnsFalse_whenConstantTypeIsNotInt() {
            BinaryOp binOp = new BinaryOp(
                    new LambdaExpression.FieldAccess("active", boolean.class),
                    Operator.EQ,
                    new LambdaExpression.Constant("1", String.class)
            );

            assertThat(PatternDetector.isBooleanFieldConstantComparison(binOp)).isFalse();
        }

        @Test
        void returnsFalse_whenConstantValueIsNotZeroOrOne() {
            BinaryOp binOp = new BinaryOp(
                    new LambdaExpression.FieldAccess("active", boolean.class),
                    Operator.EQ,
                    new LambdaExpression.Constant(2, int.class)
            );

            assertThat(PatternDetector.isBooleanFieldConstantComparison(binOp)).isFalse();
        }

        @Test
        void returnsTrue_withZeroConstant() {
            BinaryOp binOp = new BinaryOp(
                    new LambdaExpression.FieldAccess("active", boolean.class),
                    Operator.EQ,
                    new LambdaExpression.Constant(0, int.class)
            );

            assertThat(PatternDetector.isBooleanFieldConstantComparison(binOp)).isTrue();
        }

        @Test
        void returnsTrue_withOneConstant() {
            BinaryOp binOp = new BinaryOp(
                    new LambdaExpression.FieldAccess("active", boolean.class),
                    Operator.EQ,
                    new LambdaExpression.Constant(1, int.class)
            );

            assertThat(PatternDetector.isBooleanFieldConstantComparison(binOp)).isTrue();
        }
    }

    // ==================== isLogicalOperation Tests ====================

    @Nested
    @DisplayName("isLogicalOperation")
    class LogicalOperationTests {

        @Test
        void returnsTrue_forAND() {
            BinaryOp binOp = BinaryOp.and(
                    new LambdaExpression.Constant(true, boolean.class),
                    new LambdaExpression.Constant(true, boolean.class)
            );

            assertThat(PatternDetector.isLogicalOperation(binOp)).isTrue();
        }

        @Test
        void returnsTrue_forOR() {
            BinaryOp binOp = BinaryOp.or(
                    new LambdaExpression.Constant(true, boolean.class),
                    new LambdaExpression.Constant(false, boolean.class)
            );

            assertThat(PatternDetector.isLogicalOperation(binOp)).isTrue();
        }

        @Test
        void returnsFalse_forEQ() {
            BinaryOp binOp = BinaryOp.eq(
                    new LambdaExpression.Constant(1, int.class),
                    new LambdaExpression.Constant(1, int.class)
            );

            assertThat(PatternDetector.isLogicalOperation(binOp)).isFalse();
        }
    }

    // ==================== isArithmeticComparisonPattern Tests ====================

    @Nested
    @DisplayName("isArithmeticComparisonPattern")
    class ArithmeticComparisonPatternTests {

        @Test
        void returnsFalse_whenStackSizeLessThanTwo() {
            Deque<LambdaExpression> stack = new ArrayDeque<>();
            stack.push(new LambdaExpression.Constant(1, int.class));

            assertThat(PatternDetector.isArithmeticComparisonPattern(stack)).isFalse();
        }

        @Test
        void returnsFalse_whenTopIsNotConstant() {
            Deque<LambdaExpression> stack = new ArrayDeque<>();
            stack.push(BinaryOp.add(
                    new LambdaExpression.Constant(1, int.class),
                    new LambdaExpression.Constant(2, int.class)
            ));
            stack.push(new LambdaExpression.FieldAccess("value", int.class));

            assertThat(PatternDetector.isArithmeticComparisonPattern(stack)).isFalse();
        }

        @Test
        void returnsFalse_whenSecondIsNotArithmetic() {
            Deque<LambdaExpression> stack = new ArrayDeque<>();
            stack.push(new LambdaExpression.FieldAccess("value", int.class));
            stack.push(new LambdaExpression.Constant(10, int.class));

            assertThat(PatternDetector.isArithmeticComparisonPattern(stack)).isFalse();
        }

        @Test
        void returnsTrue_forValidPattern() {
            Deque<LambdaExpression> stack = new ArrayDeque<>();
            BinaryOp arithmetic = BinaryOp.add(
                    new LambdaExpression.FieldAccess("a", int.class),
                    new LambdaExpression.FieldAccess("b", int.class)
            );
            stack.push(arithmetic);
            stack.push(new LambdaExpression.Constant(10, int.class));

            assertThat(PatternDetector.isArithmeticComparisonPattern(stack)).isTrue();
        }
    }

    // ==================== containsSubquery Tests ====================

    @Nested
    @DisplayName("containsSubquery")
    class ContainsSubqueryTests {

        @Test
        void returnsTrue_forScalarSubquery() {
            // Use factory method for correct construction
            LambdaExpression expr = LambdaExpression.ScalarSubquery.avg(
                    Object.class,  // dummy entity class
                    new LambdaExpression.FieldAccess("salary", Long.class),
                    null  // no predicate
            );

            assertThat(PatternDetector.containsSubquery(expr)).isTrue();
        }

        @Test
        void returnsTrue_forExistsSubquery() {
            // Use factory method for correct construction
            LambdaExpression expr = LambdaExpression.ExistsSubquery.exists(
                    Object.class,  // dummy entity class
                    new LambdaExpression.Constant(true, boolean.class)  // dummy predicate
            );

            assertThat(PatternDetector.containsSubquery(expr)).isTrue();
        }

        @Test
        void returnsFalse_forConstant() {
            LambdaExpression expr = new LambdaExpression.Constant(1, int.class);

            assertThat(PatternDetector.containsSubquery(expr)).isFalse();
        }

        @Test
        void returnsFalse_forNull() {
            assertThat(PatternDetector.containsSubquery(null)).isFalse();
        }

        @Test
        void returnsTrue_forNestedSubqueryInBinaryOp() {
            LambdaExpression subquery = LambdaExpression.ScalarSubquery.avg(
                    Object.class,
                    new LambdaExpression.FieldAccess("salary", Long.class),
                    null
            );
            BinaryOp binOp = BinaryOp.gt(
                    new LambdaExpression.FieldAccess("salary", Long.class),
                    subquery
            );

            assertThat(PatternDetector.containsSubquery(binOp)).isTrue();
        }

        @Test
        void returnsTrue_forInSubquery() {
            LambdaExpression fieldExpr = new LambdaExpression.FieldAccess("id", Long.class);
            LambdaExpression selectExpr = new LambdaExpression.FieldAccess("foreignId", Long.class);
            LambdaExpression expr = new LambdaExpression.InSubquery(
                    fieldExpr,
                    Object.class,
                    null,  // entityClassName
                    selectExpr,
                    null,  // no predicate
                    false  // not negated
            );

            assertThat(PatternDetector.containsSubquery(expr)).isTrue();
        }

        @Test
        void returnsTrue_forSubqueryInUnaryOp() {
            LambdaExpression subquery = LambdaExpression.ExistsSubquery.exists(
                    Object.class,
                    new LambdaExpression.Constant(true, boolean.class)
            );
            LambdaExpression unaryOp = new LambdaExpression.UnaryOp(
                    LambdaExpression.UnaryOp.Operator.NOT,
                    subquery
            );

            assertThat(PatternDetector.containsSubquery(unaryOp)).isTrue();
        }

        @Test
        void returnsTrue_forSubqueryInLeftOfBinaryOp() {
            // TEST-006: Kill mutation on left side || check
            // Right side MUST NOT contain a subquery
            LambdaExpression subquery = LambdaExpression.ScalarSubquery.avg(
                    Object.class,
                    new LambdaExpression.FieldAccess("salary", Long.class),
                    null
            );
            BinaryOp binOp = BinaryOp.gt(
                    subquery,  // left side has subquery
                    new LambdaExpression.Constant(100, int.class)  // right side is NOT a subquery
            );

            // If mutation replaces left check with false, this fails
            assertThat(PatternDetector.containsSubquery(binOp))
                    .as("Left side subquery must be detected")
                    .isTrue();
        }

        @Test
        void returnsTrue_forSubqueryOnlyInRightOfBinaryOp() {
            // TEST-006: Kill mutation on right side || check
            // Left side MUST NOT contain a subquery
            LambdaExpression subquery = LambdaExpression.ExistsSubquery.exists(
                    Object.class,
                    new LambdaExpression.Constant(true, boolean.class)
            );
            BinaryOp binOp = BinaryOp.and(
                    new LambdaExpression.Constant(true, boolean.class),  // left side is NOT a subquery
                    subquery  // right side has subquery
            );

            // If mutation replaces right check with false, this fails
            assertThat(PatternDetector.containsSubquery(binOp))
                    .as("Right side subquery must be detected")
                    .isTrue();
        }

        @Test
        void returnsFalse_forBinaryOpWithNoSubqueries() {
            // TEST-006: Kill mutation on BinaryOp case - neither side has subquery
            // This ensures the || expression evaluates both sides and returns false
            BinaryOp binOp = BinaryOp.and(
                    new LambdaExpression.FieldAccess("active", boolean.class),  // NOT a subquery
                    new LambdaExpression.FieldAccess("enabled", boolean.class)  // NOT a subquery
            );

            // Both containsSubquery(left) and containsSubquery(right) must be called and return false
            assertThat(PatternDetector.containsSubquery(binOp))
                    .as("BinaryOp with no subqueries should return false")
                    .isFalse();
        }

        @Test
        void returnsFalse_forFieldAccess() {
            // TEST-006: Kill default case mutation
            LambdaExpression expr = new LambdaExpression.FieldAccess("name", String.class);
            assertThat(PatternDetector.containsSubquery(expr)).isFalse();
        }

        @Test
        void returnsFalse_forMethodCall() {
            // TEST-006: Kill default case mutation
            LambdaExpression expr = new LambdaExpression.MethodCall(
                    new LambdaExpression.FieldAccess("name", String.class),
                    "length",
                    java.util.List.of(),
                    int.class
            );
            assertThat(PatternDetector.containsSubquery(expr)).isFalse();
        }
    }

    // ==================== containsScalarSubquery Tests ====================

    @Nested
    @DisplayName("containsScalarSubquery")
    class ContainsScalarSubqueryTests {

        @Test
        void returnsTrue_forScalarSubquery() {
            LambdaExpression expr = LambdaExpression.ScalarSubquery.avg(
                    Object.class,
                    new LambdaExpression.FieldAccess("salary", Long.class),
                    null
            );

            assertThat(PatternDetector.containsScalarSubquery(expr)).isTrue();
        }

        @Test
        void returnsFalse_forExistsSubquery() {
            // TEST-006: Kill mutation on ExistsSubquery case returning false
            LambdaExpression expr = LambdaExpression.ExistsSubquery.exists(
                    Object.class,
                    new LambdaExpression.Constant(true, boolean.class)
            );

            assertThat(PatternDetector.containsScalarSubquery(expr)).isFalse();
        }

        @Test
        void returnsFalse_forInSubquery() {
            // TEST-006: Kill mutation on InSubquery case returning false
            LambdaExpression fieldExpr = new LambdaExpression.FieldAccess("id", Long.class);
            LambdaExpression selectExpr = new LambdaExpression.FieldAccess("foreignId", Long.class);
            LambdaExpression expr = new LambdaExpression.InSubquery(
                    fieldExpr,
                    Object.class,
                    null,
                    selectExpr,
                    null,
                    false
            );

            assertThat(PatternDetector.containsScalarSubquery(expr)).isFalse();
        }

        @Test
        void returnsTrue_forNestedScalarSubqueryInBinaryOp() {
            LambdaExpression subquery = LambdaExpression.ScalarSubquery.avg(
                    Object.class,
                    new LambdaExpression.FieldAccess("salary", Long.class),
                    null
            );
            BinaryOp binOp = BinaryOp.gt(
                    new LambdaExpression.FieldAccess("salary", Long.class),
                    subquery
            );

            assertThat(PatternDetector.containsScalarSubquery(binOp)).isTrue();
        }

        @Test
        void returnsTrue_forScalarSubqueryInLeftOfBinaryOp() {
            // TEST-006: Kill mutation on left side || check
            // Right side MUST NOT contain a subquery for this to kill the left-side mutation
            LambdaExpression subquery = LambdaExpression.ScalarSubquery.avg(
                    Object.class,
                    new LambdaExpression.FieldAccess("salary", Long.class),
                    null
            );
            BinaryOp binOp = BinaryOp.gt(
                    subquery,  // left side has scalar subquery
                    new LambdaExpression.Constant(100, int.class)  // right side is NOT a subquery
            );

            // If mutation replaces left check with false, this would fail
            // because right (Constant) returns false for containsScalarSubquery
            assertThat(PatternDetector.containsScalarSubquery(binOp))
                    .as("Left side ScalarSubquery must be detected even if right side is not a subquery")
                    .isTrue();
        }

        @Test
        void returnsTrue_forScalarSubqueryOnlyInRightOfBinaryOp() {
            // TEST-006: Kill mutation on right side || check
            // Left side MUST NOT contain a subquery for this to kill the right-side mutation
            LambdaExpression subquery = LambdaExpression.ScalarSubquery.avg(
                    Object.class,
                    new LambdaExpression.FieldAccess("salary", Long.class),
                    null
            );
            BinaryOp binOp = BinaryOp.gt(
                    new LambdaExpression.Constant(100, int.class),  // left side is NOT a subquery
                    subquery  // right side has scalar subquery
            );

            // If mutation replaces right check with false, this would fail
            // because left (Constant) returns false for containsScalarSubquery
            assertThat(PatternDetector.containsScalarSubquery(binOp))
                    .as("Right side ScalarSubquery must be detected even if left side is not a subquery")
                    .isTrue();
        }

        @Test
        void returnsTrue_forScalarSubqueryInUnaryOp() {
            LambdaExpression subquery = LambdaExpression.ScalarSubquery.avg(
                    Object.class,
                    new LambdaExpression.FieldAccess("salary", Long.class),
                    null
            );
            LambdaExpression unaryOp = new LambdaExpression.UnaryOp(
                    LambdaExpression.UnaryOp.Operator.NOT,
                    subquery
            );

            assertThat(PatternDetector.containsScalarSubquery(unaryOp)).isTrue();
        }

        @Test
        void returnsFalse_forNull() {
            assertThat(PatternDetector.containsScalarSubquery(null)).isFalse();
        }

        @Test
        void returnsFalse_forConstant() {
            // TEST-006: Kill default case mutation
            LambdaExpression expr = new LambdaExpression.Constant(1, int.class);
            assertThat(PatternDetector.containsScalarSubquery(expr)).isFalse();
        }

        @Test
        void returnsFalse_forBinaryOpWithNoScalarSubqueries() {
            // TEST-006: Kill mutation on BinaryOp case - neither side has scalar subquery
            BinaryOp binOp = BinaryOp.and(
                    new LambdaExpression.FieldAccess("active", boolean.class),  // NOT a subquery
                    new LambdaExpression.FieldAccess("enabled", boolean.class)  // NOT a subquery
            );

            assertThat(PatternDetector.containsScalarSubquery(binOp))
                    .as("BinaryOp with no scalar subqueries should return false")
                    .isFalse();
        }

        @Test
        void returnsFalse_forBinaryOpWithExistsSubquery() {
            // TEST-006: Verify ExistsSubquery is not considered a scalar subquery even in BinaryOp
            LambdaExpression existsSubquery = LambdaExpression.ExistsSubquery.exists(
                    Object.class,
                    new LambdaExpression.Constant(true, boolean.class)
            );
            BinaryOp binOp = BinaryOp.and(
                    existsSubquery,  // ExistsSubquery is NOT a scalar subquery
                    new LambdaExpression.Constant(true, boolean.class)
            );

            assertThat(PatternDetector.containsScalarSubquery(binOp))
                    .as("ExistsSubquery in BinaryOp should not be considered scalar subquery")
                    .isFalse();
        }
    }

    // ==================== isBooleanConstant Tests ====================

    @Nested
    @DisplayName("isBooleanConstant")
    class IsBooleanConstantTests {

        @Test
        void returnsTrue_forBooleanTrue() {
            LambdaExpression expr = new LambdaExpression.Constant(true, boolean.class);
            assertThat(PatternDetector.isBooleanConstant(expr)).isTrue();
        }

        @Test
        void returnsTrue_forBooleanFalse() {
            LambdaExpression expr = new LambdaExpression.Constant(false, boolean.class);
            assertThat(PatternDetector.isBooleanConstant(expr)).isTrue();
        }

        @Test
        void returnsTrue_forIntegerZero() {
            // TEST-006: Kill mutation on Integer 0 check
            LambdaExpression expr = new LambdaExpression.Constant(0, int.class);
            assertThat(PatternDetector.isBooleanConstant(expr)).isTrue();
        }

        @Test
        void returnsTrue_forIntegerOne() {
            // TEST-006: Kill mutation on Integer 1 check
            LambdaExpression expr = new LambdaExpression.Constant(1, int.class);
            assertThat(PatternDetector.isBooleanConstant(expr)).isTrue();
        }

        @Test
        void returnsFalse_forIntegerTwo() {
            // TEST-006: Kill mutation - only 0 and 1 are boolean
            LambdaExpression expr = new LambdaExpression.Constant(2, int.class);
            assertThat(PatternDetector.isBooleanConstant(expr)).isFalse();
        }

        @Test
        void returnsFalse_forString() {
            LambdaExpression expr = new LambdaExpression.Constant("true", String.class);
            assertThat(PatternDetector.isBooleanConstant(expr)).isFalse();
        }

        @Test
        void returnsFalse_forFieldAccess() {
            LambdaExpression expr = new LambdaExpression.FieldAccess("active", boolean.class);
            assertThat(PatternDetector.isBooleanConstant(expr)).isFalse();
        }
    }

    // ==================== isSubqueryBooleanComparison Tests ====================

    @Nested
    @DisplayName("isSubqueryBooleanComparison")
    class IsSubqueryBooleanComparisonTests {

        @Test
        void returnsTrue_forExistsSubqueryEqTrue() {
            LambdaExpression subquery = LambdaExpression.ExistsSubquery.exists(
                    Object.class,
                    new LambdaExpression.Constant(true, boolean.class)
            );
            BinaryOp binOp = BinaryOp.eq(subquery, new LambdaExpression.Constant(true, boolean.class));

            assertThat(PatternDetector.isSubqueryBooleanComparison(binOp)).isTrue();
        }

        @Test
        void returnsTrue_forExistsSubqueryNeOne() {
            LambdaExpression subquery = LambdaExpression.ExistsSubquery.exists(
                    Object.class,
                    new LambdaExpression.Constant(true, boolean.class)
            );
            BinaryOp binOp = BinaryOp.ne(subquery, new LambdaExpression.Constant(1, int.class));

            assertThat(PatternDetector.isSubqueryBooleanComparison(binOp)).isTrue();
        }

        @Test
        void returnsTrue_forBooleanConstantOnLeftSubqueryOnRight() {
            // TEST-006: Kill mutation on rightIsSubquery check
            LambdaExpression subquery = LambdaExpression.ExistsSubquery.exists(
                    Object.class,
                    new LambdaExpression.Constant(true, boolean.class)
            );
            BinaryOp binOp = BinaryOp.eq(new LambdaExpression.Constant(true, boolean.class), subquery);

            assertThat(PatternDetector.isSubqueryBooleanComparison(binOp)).isTrue();
        }

        @Test
        void returnsFalse_forNonEqualityOperator() {
            // TEST-006: Kill mutation on operator check
            LambdaExpression subquery = LambdaExpression.ExistsSubquery.exists(
                    Object.class,
                    new LambdaExpression.Constant(true, boolean.class)
            );
            BinaryOp binOp = BinaryOp.lt(subquery, new LambdaExpression.Constant(1, int.class));

            assertThat(PatternDetector.isSubqueryBooleanComparison(binOp)).isFalse();
        }

        @Test
        void returnsFalse_forSubqueryComparedToNonBoolean() {
            LambdaExpression subquery = LambdaExpression.ExistsSubquery.exists(
                    Object.class,
                    new LambdaExpression.Constant(true, boolean.class)
            );
            BinaryOp binOp = BinaryOp.eq(subquery, new LambdaExpression.Constant("test", String.class));

            assertThat(PatternDetector.isSubqueryBooleanComparison(binOp)).isFalse();
        }

        @Test
        void returnsFalse_forNoSubquery() {
            BinaryOp binOp = BinaryOp.eq(
                    new LambdaExpression.FieldAccess("active", boolean.class),
                    new LambdaExpression.Constant(true, boolean.class)
            );

            assertThat(PatternDetector.isSubqueryBooleanComparison(binOp)).isFalse();
        }
    }

    // ==================== isNegatedSubqueryComparison Tests ====================

    @Nested
    @DisplayName("isNegatedSubqueryComparison")
    class IsNegatedSubqueryComparisonTests {

        @Test
        void returnsTrue_forEqFalse() {
            LambdaExpression constant = new LambdaExpression.Constant(false, boolean.class);
            assertThat(PatternDetector.isNegatedSubqueryComparison(Operator.EQ, constant)).isTrue();
        }

        @Test
        void returnsTrue_forEqZero() {
            // TEST-006: Kill mutation on Integer.valueOf(0) check
            LambdaExpression constant = new LambdaExpression.Constant(0, int.class);
            assertThat(PatternDetector.isNegatedSubqueryComparison(Operator.EQ, constant)).isTrue();
        }

        @Test
        void returnsTrue_forNeTrue() {
            LambdaExpression constant = new LambdaExpression.Constant(true, boolean.class);
            assertThat(PatternDetector.isNegatedSubqueryComparison(Operator.NE, constant)).isTrue();
        }

        @Test
        void returnsTrue_forNeOne() {
            // TEST-006: Kill mutation on Integer.valueOf(1) check
            LambdaExpression constant = new LambdaExpression.Constant(1, int.class);
            assertThat(PatternDetector.isNegatedSubqueryComparison(Operator.NE, constant)).isTrue();
        }

        @Test
        void returnsFalse_forEqTrue() {
            // Not negated - positive comparison
            LambdaExpression constant = new LambdaExpression.Constant(true, boolean.class);
            assertThat(PatternDetector.isNegatedSubqueryComparison(Operator.EQ, constant)).isFalse();
        }

        @Test
        void returnsFalse_forNeFalse() {
            // Not negated - NE false is same as EQ true
            LambdaExpression constant = new LambdaExpression.Constant(false, boolean.class);
            assertThat(PatternDetector.isNegatedSubqueryComparison(Operator.NE, constant)).isFalse();
        }

        @Test
        void returnsFalse_forLtOperator() {
            // TEST-006: Kill mutation on operator switch
            LambdaExpression constant = new LambdaExpression.Constant(false, boolean.class);
            assertThat(PatternDetector.isNegatedSubqueryComparison(Operator.LT, constant)).isFalse();
        }

        @Test
        void returnsFalse_forNonConstant() {
            LambdaExpression expr = new LambdaExpression.FieldAccess("active", boolean.class);
            assertThat(PatternDetector.isNegatedSubqueryComparison(Operator.EQ, expr)).isFalse();
        }
    }

    // ==================== BranchPattern.detect Tests ====================

    @Nested
    @DisplayName("BranchPattern.detect")
    class BranchPatternDetectTests {

        @Test
        void returnsOther_forEmptyStack() {
            Deque<LambdaExpression> stack = new ArrayDeque<>();

            PatternDetector.BranchPattern result = PatternDetector.BranchPattern.detect(stack);

            assertThat(result).isEqualTo(PatternDetector.BranchPattern.OTHER);
        }

        @Test
        void returnsNumericComparison_forDcmplPattern() {
            Deque<LambdaExpression> stack = new ArrayDeque<>();
            stack.push(new LambdaExpression.FieldAccess("price", double.class));
            stack.push(new LambdaExpression.FieldAccess("discount", double.class));

            PatternDetector.BranchPattern result = PatternDetector.BranchPattern.detect(stack);

            assertThat(result).isEqualTo(PatternDetector.BranchPattern.NUMERIC_COMPARISON);
        }

        @Test
        void returnsNumericComparison_forArithmeticComparisonPattern() {
            Deque<LambdaExpression> stack = new ArrayDeque<>();
            BinaryOp arithmetic = BinaryOp.sub(
                    new LambdaExpression.FieldAccess("a", int.class),
                    new LambdaExpression.FieldAccess("b", int.class)
            );
            stack.push(arithmetic);
            stack.push(new LambdaExpression.Constant(0, int.class));

            PatternDetector.BranchPattern result = PatternDetector.BranchPattern.detect(stack);

            assertThat(result).isEqualTo(PatternDetector.BranchPattern.NUMERIC_COMPARISON);
        }

        @Test
        void returnsCompareTo_forCompareToPattern() {
            Deque<LambdaExpression> stack = new ArrayDeque<>();
            LambdaExpression target = new LambdaExpression.FieldAccess("name", String.class);
            stack.push(new LambdaExpression.MethodCall(target, "compareTo", java.util.List.of(), int.class));

            PatternDetector.BranchPattern result = PatternDetector.BranchPattern.detect(stack);

            assertThat(result).isEqualTo(PatternDetector.BranchPattern.COMPARE_TO);
        }

        @Test
        void returnsArithmetic_forArithmeticExpression() {
            Deque<LambdaExpression> stack = new ArrayDeque<>();
            BinaryOp arithmetic = BinaryOp.add(
                    new LambdaExpression.FieldAccess("a", int.class),
                    new LambdaExpression.FieldAccess("b", int.class)
            );
            stack.push(arithmetic);

            PatternDetector.BranchPattern result = PatternDetector.BranchPattern.detect(stack);

            assertThat(result).isEqualTo(PatternDetector.BranchPattern.ARITHMETIC);
        }

        @Test
        void returnsOther_forSimpleFieldAccess() {
            Deque<LambdaExpression> stack = new ArrayDeque<>();
            stack.push(new LambdaExpression.FieldAccess("active", boolean.class));

            PatternDetector.BranchPattern result = PatternDetector.BranchPattern.detect(stack);

            assertThat(result).isEqualTo(PatternDetector.BranchPattern.OTHER);
        }
    }

    // ==================== BranchPatternAnalysis.analyze Tests ====================

    @Nested
    @DisplayName("BranchPatternAnalysis.analyze")
    class BranchPatternAnalysisTests {

        @Test
        void returnsNullTop_forEmptyStack() {
            Deque<LambdaExpression> stack = new ArrayDeque<>();

            PatternDetector.BranchPatternAnalysis result = PatternDetector.BranchPatternAnalysis.analyze(stack);

            assertThat(result.top()).isNull();
            assertThat(result.pattern()).isEqualTo(PatternDetector.BranchPattern.OTHER);
        }

        @Test
        void returnsTopElement_forNonEmptyStack() {
            Deque<LambdaExpression> stack = new ArrayDeque<>();
            LambdaExpression fieldAccess = new LambdaExpression.FieldAccess("name", String.class);
            stack.push(fieldAccess);

            PatternDetector.BranchPatternAnalysis result = PatternDetector.BranchPatternAnalysis.analyze(stack);

            assertThat(result.top()).isSameAs(fieldAccess);
        }
    }

    // ==================== isDcmplPattern Tests ====================

    @Nested
    @DisplayName("isDcmplPattern")
    class IsDcmplPatternTests {

        @Test
        void returnsFalse_forStackSizeLessThanTwo() {
            Deque<LambdaExpression> stack = new ArrayDeque<>();
            stack.push(new LambdaExpression.FieldAccess("price", double.class));

            assertThat(PatternDetector.isDcmplPattern(stack)).isFalse();
        }

        @Test
        void returnsFalse_whenFirstIsNotComparable() {
            Deque<LambdaExpression> stack = new ArrayDeque<>();
            stack.push(new LambdaExpression.FieldAccess("price", double.class));
            // NullLiteral is not in isComparableExpression
            stack.push(new LambdaExpression.NullLiteral(Object.class));

            assertThat(PatternDetector.isDcmplPattern(stack)).isFalse();
        }

        @Test
        void returnsTrue_forTwoFieldAccesses() {
            Deque<LambdaExpression> stack = new ArrayDeque<>();
            stack.push(new LambdaExpression.FieldAccess("a", double.class));
            stack.push(new LambdaExpression.FieldAccess("b", double.class));

            assertThat(PatternDetector.isDcmplPattern(stack)).isTrue();
        }

        @Test
        void returnsTrue_forFieldAccessAndConstant() {
            Deque<LambdaExpression> stack = new ArrayDeque<>();
            stack.push(new LambdaExpression.FieldAccess("price", double.class));
            stack.push(new LambdaExpression.Constant(100.0, double.class));

            assertThat(PatternDetector.isDcmplPattern(stack)).isTrue();
        }

        @Test
        void returnsTrue_forCapturedVariables() {
            Deque<LambdaExpression> stack = new ArrayDeque<>();
            stack.push(new LambdaExpression.CapturedVariable(0, double.class));
            stack.push(new LambdaExpression.CapturedVariable(1, double.class));

            assertThat(PatternDetector.isDcmplPattern(stack)).isTrue();
        }

        @Test
        void returnsTrue_forGroupAggregation() {
            Deque<LambdaExpression> stack = new ArrayDeque<>();
            stack.push(new LambdaExpression.GroupAggregation(
                    LambdaExpression.GroupAggregationType.COUNT,
                    null,  // no field expression for count()
                    Long.class
            ));
            stack.push(new LambdaExpression.Constant(1L, long.class));

            assertThat(PatternDetector.isDcmplPattern(stack)).isTrue();
        }

        @Test
        void returnsTrue_forScalarSubquery() {
            Deque<LambdaExpression> stack = new ArrayDeque<>();
            LambdaExpression subquery = LambdaExpression.ScalarSubquery.avg(
                    Object.class,
                    new LambdaExpression.FieldAccess("salary", Long.class),
                    null
            );
            stack.push(subquery);
            stack.push(new LambdaExpression.FieldAccess("salary", Long.class));

            assertThat(PatternDetector.isDcmplPattern(stack)).isTrue();
        }

        @Test
        void returnsTrue_forPathExpression() {
            Deque<LambdaExpression> stack = new ArrayDeque<>();
            // PathExpression requires List<PathSegment>
            java.util.List<LambdaExpression.PathSegment> segments = java.util.List.of(
                    new LambdaExpression.PathSegment("address", Object.class, LambdaExpression.RelationType.MANY_TO_ONE),
                    new LambdaExpression.PathSegment("city", String.class, LambdaExpression.RelationType.FIELD)
            );
            stack.push(new LambdaExpression.PathExpression(segments, String.class));
            stack.push(new LambdaExpression.Constant("NYC", String.class));

            assertThat(PatternDetector.isDcmplPattern(stack)).isTrue();
        }

        @Test
        void returnsTrue_forBiEntityFieldAccess() {
            Deque<LambdaExpression> stack = new ArrayDeque<>();
            stack.push(new LambdaExpression.BiEntityFieldAccess(
                    "salary", Long.class, LambdaExpression.EntityPosition.FIRST
            ));
            stack.push(new LambdaExpression.BiEntityFieldAccess(
                    "salary", Long.class, LambdaExpression.EntityPosition.SECOND
            ));

            assertThat(PatternDetector.isDcmplPattern(stack)).isTrue();
        }

        @Test
        void returnsTrue_forGroupKeyReference() {
            Deque<LambdaExpression> stack = new ArrayDeque<>();
            // GroupKeyReference takes (keyExpression, resultType)
            stack.push(new LambdaExpression.GroupKeyReference(
                    new LambdaExpression.FieldAccess("department", String.class),
                    String.class
            ));
            stack.push(new LambdaExpression.Constant("Sales", String.class));

            assertThat(PatternDetector.isDcmplPattern(stack)).isTrue();
        }

        @Test
        void returnsTrue_forBiEntityPathExpression() {
            // TEST-006: Kill mutation on BiEntityPathExpression instanceof check
            Deque<LambdaExpression> stack = new ArrayDeque<>();
            java.util.List<LambdaExpression.PathSegment> segments = java.util.List.of(
                    new LambdaExpression.PathSegment("address", Object.class, LambdaExpression.RelationType.MANY_TO_ONE),
                    new LambdaExpression.PathSegment("city", String.class, LambdaExpression.RelationType.FIELD)
            );
            stack.push(new LambdaExpression.BiEntityPathExpression(
                    segments,
                    String.class,
                    LambdaExpression.EntityPosition.FIRST
            ));
            stack.push(new LambdaExpression.Constant("NYC", String.class));

            assertThat(PatternDetector.isDcmplPattern(stack)).isTrue();
        }

        @Test
        void returnsTrue_forArithmeticExpression() {
            // TEST-006: Kill mutation on isArithmeticExpression check in isComparableExpression
            Deque<LambdaExpression> stack = new ArrayDeque<>();
            BinaryOp arithmetic = BinaryOp.add(
                    new LambdaExpression.FieldAccess("a", int.class),
                    new LambdaExpression.FieldAccess("b", int.class)
            );
            stack.push(arithmetic);
            stack.push(new LambdaExpression.Constant(10, int.class));

            assertThat(PatternDetector.isDcmplPattern(stack)).isTrue();
        }
    }

    // ==================== BinaryOperationCategory.categorize Tests ====================

    @Nested
    @DisplayName("BinaryOperationCategory.categorize")
    class BinaryOperationCategoryTests {

        @Test
        void returnsStringConcatenation_whenPredicateReturnsTrue() {
            BinaryOp binOp = BinaryOp.add(
                    new LambdaExpression.FieldAccess("first", String.class),
                    new LambdaExpression.FieldAccess("last", String.class)
            );

            PatternDetector.BinaryOperationCategory result =
                    PatternDetector.BinaryOperationCategory.categorize(binOp, op -> true);

            assertThat(result).isEqualTo(PatternDetector.BinaryOperationCategory.STRING_CONCATENATION);
        }

        @Test
        void returnsArithmetic_forAddOperation() {
            BinaryOp binOp = BinaryOp.add(
                    new LambdaExpression.FieldAccess("a", int.class),
                    new LambdaExpression.FieldAccess("b", int.class)
            );

            PatternDetector.BinaryOperationCategory result =
                    PatternDetector.BinaryOperationCategory.categorize(binOp, op -> false);

            assertThat(result).isEqualTo(PatternDetector.BinaryOperationCategory.ARITHMETIC);
        }

        @Test
        void returnsLogical_forAndOperation() {
            BinaryOp binOp = BinaryOp.and(
                    new LambdaExpression.Constant(true, boolean.class),
                    new LambdaExpression.Constant(false, boolean.class)
            );

            PatternDetector.BinaryOperationCategory result =
                    PatternDetector.BinaryOperationCategory.categorize(binOp, op -> false);

            assertThat(result).isEqualTo(PatternDetector.BinaryOperationCategory.LOGICAL);
        }

        @Test
        void returnsNullCheck_forNullComparisonPattern() {
            BinaryOp binOp = BinaryOp.eq(
                    new LambdaExpression.FieldAccess("name", String.class),
                    new LambdaExpression.NullLiteral(String.class)
            );

            PatternDetector.BinaryOperationCategory result =
                    PatternDetector.BinaryOperationCategory.categorize(binOp, op -> false);

            assertThat(result).isEqualTo(PatternDetector.BinaryOperationCategory.NULL_CHECK);
        }

        @Test
        void returnsBooleanFieldConstant_forBooleanFieldWithConstant() {
            BinaryOp binOp = BinaryOp.eq(
                    new LambdaExpression.FieldAccess("active", boolean.class),
                    new LambdaExpression.Constant(1, int.class)
            );

            PatternDetector.BinaryOperationCategory result =
                    PatternDetector.BinaryOperationCategory.categorize(binOp, op -> false);

            assertThat(result).isEqualTo(PatternDetector.BinaryOperationCategory.BOOLEAN_FIELD_CONSTANT);
        }

        @Test
        void returnsBooleanFieldCapturedVariable_forBooleanFieldWithCapturedVar() {
            BinaryOp binOp = BinaryOp.eq(
                    new LambdaExpression.FieldAccess("active", boolean.class),
                    new LambdaExpression.CapturedVariable(0, boolean.class)
            );

            PatternDetector.BinaryOperationCategory result =
                    PatternDetector.BinaryOperationCategory.categorize(binOp, op -> false);

            assertThat(result).isEqualTo(PatternDetector.BinaryOperationCategory.BOOLEAN_FIELD_CAPTURED_VARIABLE);
        }

        @Test
        void returnsCompareToEquality_forCompareToEqZero() {
            LambdaExpression target = new LambdaExpression.FieldAccess("name", String.class);
            BinaryOp binOp = BinaryOp.eq(
                    new LambdaExpression.MethodCall(target, "compareTo", java.util.List.of(), int.class),
                    new LambdaExpression.Constant(0, int.class)
            );

            PatternDetector.BinaryOperationCategory result =
                    PatternDetector.BinaryOperationCategory.categorize(binOp, op -> false);

            assertThat(result).isEqualTo(PatternDetector.BinaryOperationCategory.COMPARE_TO_EQUALITY);
        }

        @Test
        void returnsComparison_forSimpleEquality() {
            BinaryOp binOp = BinaryOp.eq(
                    new LambdaExpression.FieldAccess("id", int.class),
                    new LambdaExpression.Constant(42, int.class)
            );

            PatternDetector.BinaryOperationCategory result =
                    PatternDetector.BinaryOperationCategory.categorize(binOp, op -> false);

            assertThat(result).isEqualTo(PatternDetector.BinaryOperationCategory.COMPARISON);
        }
    }

    // ==================== isArithmeticExpression Tests ====================

    @Nested
    @DisplayName("isArithmeticExpression")
    class IsArithmeticExpressionTests {

        @Test
        void returnsTrue_forADD() {
            BinaryOp binOp = BinaryOp.add(
                    new LambdaExpression.FieldAccess("a", int.class),
                    new LambdaExpression.FieldAccess("b", int.class)
            );

            assertThat(PatternDetector.isArithmeticExpression(binOp)).isTrue();
        }

        @Test
        void returnsTrue_forSUB() {
            BinaryOp binOp = BinaryOp.sub(
                    new LambdaExpression.FieldAccess("a", int.class),
                    new LambdaExpression.FieldAccess("b", int.class)
            );

            assertThat(PatternDetector.isArithmeticExpression(binOp)).isTrue();
        }

        @Test
        void returnsTrue_forMUL() {
            BinaryOp binOp = BinaryOp.mul(
                    new LambdaExpression.FieldAccess("a", int.class),
                    new LambdaExpression.FieldAccess("b", int.class)
            );

            assertThat(PatternDetector.isArithmeticExpression(binOp)).isTrue();
        }

        @Test
        void returnsTrue_forDIV() {
            BinaryOp binOp = BinaryOp.div(
                    new LambdaExpression.FieldAccess("a", int.class),
                    new LambdaExpression.FieldAccess("b", int.class)
            );

            assertThat(PatternDetector.isArithmeticExpression(binOp)).isTrue();
        }

        @Test
        void returnsTrue_forMOD() {
            // TEST-006: Kill mutation on MOD check
            BinaryOp binOp = BinaryOp.mod(
                    new LambdaExpression.FieldAccess("a", int.class),
                    new LambdaExpression.FieldAccess("b", int.class)
            );

            assertThat(PatternDetector.isArithmeticExpression(binOp))
                    .as("MOD operator must be recognized as arithmetic")
                    .isTrue();
        }

        @Test
        void returnsFalse_forLogicalOperator() {
            BinaryOp binOp = BinaryOp.and(
                    new LambdaExpression.Constant(true, boolean.class),
                    new LambdaExpression.Constant(false, boolean.class)
            );

            assertThat(PatternDetector.isArithmeticExpression(binOp)).isFalse();
        }

        @Test
        void returnsFalse_forComparisonOperator() {
            BinaryOp binOp = BinaryOp.eq(
                    new LambdaExpression.FieldAccess("a", int.class),
                    new LambdaExpression.FieldAccess("b", int.class)
            );

            assertThat(PatternDetector.isArithmeticExpression(binOp)).isFalse();
        }

        @Test
        void returnsFalse_forNonBinaryOp() {
            LambdaExpression expr = new LambdaExpression.FieldAccess("value", int.class);

            assertThat(PatternDetector.isArithmeticExpression(expr)).isFalse();
        }
    }

    // ==================== Additional Mutation-Killing Tests ====================

    @Nested
    @DisplayName("Additional mutation-killing tests")
    class AdditionalMutationKillingTests {

        @Test
        void isBooleanFieldCapturedVariableComparison_returnsFalse_whenFieldTypeNotBooleanButCapturedVarIsBoolean() {
            // TEST-006: Kill mutation on fieldType check
            // Original: return false because fieldType (String) is not boolean
            // Mutant: skip fieldType check, capturedVar.type() is boolean -> return true
            // This test expects false, so mutant fails -> KILLED
            BinaryOp binOp = new BinaryOp(
                    new LambdaExpression.FieldAccess("name", String.class),  // NOT boolean
                    Operator.EQ,
                    new LambdaExpression.CapturedVariable(0, boolean.class)  // IS boolean
            );

            boolean result = PatternDetector.isBooleanFieldCapturedVariableComparison(binOp);

            assertThat(result)
                    .as("Should return false when field type is not boolean, even if captured var is boolean")
                    .isFalse();
        }

        @Test
        void isSubqueryBooleanComparison_returnsFalse_whenRightIsNotSubqueryButLeftIsBooleanConstant() {
            // TEST-006: Kill mutation at line 476 block 14 (rightIsSubquery check)
            // Mutation replaces "rightIsSubquery && isBooleanConstant(left)" with "true && ..."
            // We need: rightIsSubquery=FALSE, isBooleanConstant(left)=TRUE
            // Original returns false, mutant would return true -> KILLED
            BinaryOp binOp = BinaryOp.eq(
                    new LambdaExpression.Constant(true, boolean.class),  // left: IS boolean constant
                    new LambdaExpression.FieldAccess("active", boolean.class)  // right: NOT a subquery
            );

            assertThat(PatternDetector.isSubqueryBooleanComparison(binOp))
                    .as("Should return false when right is not subquery even if left is boolean constant")
                    .isFalse();
        }

        @Test
        void isSubqueryBooleanComparison_returnsFalse_whenRightIsNotSubqueryAndLeftIsIntZero() {
            // TEST-006: Kill mutation at line 476 block 17 (isBooleanConstant check on left)
            // Tests the right side not being a subquery with integer 0 on left
            BinaryOp binOp = BinaryOp.eq(
                    new LambdaExpression.Constant(0, int.class),  // left: IS boolean constant (0)
                    new LambdaExpression.FieldAccess("count", int.class)  // right: NOT a subquery
            );

            assertThat(PatternDetector.isSubqueryBooleanComparison(binOp))
                    .as("Should return false when right is not subquery")
                    .isFalse();
        }

        @Test
        void isNegatedSubqueryComparison_returnsFalse_forGtOperatorWithTrueValue() {
            // TEST-006: Kill mutation at line 524 block 13 (operator == NE check)
            // Mutation replaces "operator == NE" with "true"
            // We need: operator is GT (not EQ, not NE), value is TRUE (would match NE branch)
            // Original skips both branches (not EQ, not NE) and returns false
            // Mutant would enter NE branch and return true -> KILLED
            LambdaExpression constant = new LambdaExpression.Constant(true, boolean.class);

            assertThat(PatternDetector.isNegatedSubqueryComparison(Operator.GT, constant))
                    .as("GT operator with TRUE should return false (not negated)")
                    .isFalse();
        }

        @Test
        void isNegatedSubqueryComparison_returnsFalse_forLeOperatorWithIntOne() {
            // TEST-006: Additional test with different non-EQ/NE operator
            LambdaExpression constant = new LambdaExpression.Constant(1, int.class);

            assertThat(PatternDetector.isNegatedSubqueryComparison(Operator.LE, constant))
                    .as("LE operator with 1 should return false")
                    .isFalse();
        }

        @Test
        void isBooleanFieldConstantComparison_returnsFalse_whenConstantIsLongType() {
            // TEST-006: Kill mutation at line 360 (constant.type() == int.class check)
            // Mutation replaces equality check with true
            // We need: constant.type() is Long (not int), value is 1 (would match value check)
            // Original returns false because type check fails
            // Mutant would return true because type check replaced with true -> KILLED
            BinaryOp binOp = new BinaryOp(
                    new LambdaExpression.FieldAccess("active", boolean.class),  // boolean field
                    Operator.EQ,
                    new LambdaExpression.Constant(1L, long.class)  // Long type, not int!
            );

            assertThat(PatternDetector.isBooleanFieldConstantComparison(binOp))
                    .as("Should return false when constant type is Long, not int")
                    .isFalse();
        }

        @Test
        void branchPatternDetect_returnsCompareTo_forIntReturningMethodCall() {
            // TEST-006: isCompareToPattern checks any MethodCall with int return type
            Deque<LambdaExpression> stack = new ArrayDeque<>();
            LambdaExpression target = new LambdaExpression.FieldAccess("name", String.class);
            stack.push(new LambdaExpression.MethodCall(target, "length", java.util.List.of(), int.class));

            PatternDetector.BranchPattern result = PatternDetector.BranchPattern.detect(stack);

            // Any method returning int matches COMPARE_TO pattern
            assertThat(result).isEqualTo(PatternDetector.BranchPattern.COMPARE_TO);
        }

        @Test
        void branchPatternDetect_returnsOther_forBooleanReturningMethodCall() {
            // TEST-006: Kill mutation - method call with non-int return type should return OTHER
            Deque<LambdaExpression> stack = new ArrayDeque<>();
            LambdaExpression target = new LambdaExpression.FieldAccess("name", String.class);
            stack.push(new LambdaExpression.MethodCall(target, "isEmpty", java.util.List.of(), boolean.class));

            PatternDetector.BranchPattern result = PatternDetector.BranchPattern.detect(stack);

            assertThat(result)
                    .as("Method returning boolean should not match COMPARE_TO pattern")
                    .isEqualTo(PatternDetector.BranchPattern.OTHER);
        }

        @Test
        void branchPatternDetect_returnsOther_forVoidReturningMethodCall() {
            // TEST-006: Method call with void return type (Object.class in the model)
            Deque<LambdaExpression> stack = new ArrayDeque<>();
            LambdaExpression target = new LambdaExpression.FieldAccess("list", java.util.List.class);
            stack.push(new LambdaExpression.MethodCall(target, "clear", java.util.List.of(), void.class));

            PatternDetector.BranchPattern result = PatternDetector.BranchPattern.detect(stack);

            assertThat(result)
                    .as("Method returning void should not match COMPARE_TO pattern")
                    .isEqualTo(PatternDetector.BranchPattern.OTHER);
        }
    }
}
