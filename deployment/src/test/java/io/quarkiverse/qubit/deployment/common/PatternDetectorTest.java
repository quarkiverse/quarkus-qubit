package io.quarkiverse.qubit.deployment.common;

import io.quarkiverse.qubit.deployment.ast.LambdaExpression;
import io.quarkiverse.qubit.deployment.ast.LambdaExpression.BinaryOp;
import io.quarkiverse.qubit.deployment.ast.LambdaExpression.BinaryOp.Operator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayDeque;
import java.util.Deque;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for PatternDetector utility class.
 *
 * <p>These tests target surviving mutations in the PatternDetector class,
 * particularly the boolean return value mutations and equality check conditions.
 *
 * <p>This class uses JUnit 5 parameterized tests to consolidate repetitive
 * test patterns, reducing code duplication while maintaining full coverage.
 */
class PatternDetectorTest {

    // ==================== isBooleanFieldCapturedVariableComparison Tests ====================

    @Nested
    @DisplayName("isBooleanFieldCapturedVariableComparison")
    class BooleanFieldCapturedVariableComparisonTests {

        @Test
        void returnsFalse_whenNotEqualityOperation() {
            BinaryOp binOp = new BinaryOp(
                    new LambdaExpression.FieldAccess("active", boolean.class),
                    Operator.LT,
                    new LambdaExpression.CapturedVariable(0, boolean.class)
            );
            assertThat(PatternDetector.isBooleanFieldCapturedVariableComparison(binOp)).isFalse();
        }

        @Test
        void returnsFalse_whenLeftIsNotFieldAccess() {
            BinaryOp binOp = new BinaryOp(
                    new LambdaExpression.Constant(true, boolean.class),
                    Operator.EQ,
                    new LambdaExpression.CapturedVariable(0, boolean.class)
            );
            assertThat(PatternDetector.isBooleanFieldCapturedVariableComparison(binOp)).isFalse();
        }

        @Test
        void returnsFalse_whenFieldTypeIsNotBoolean() {
            BinaryOp binOp = new BinaryOp(
                    new LambdaExpression.FieldAccess("name", String.class),
                    Operator.EQ,
                    new LambdaExpression.CapturedVariable(0, String.class)
            );
            assertThat(PatternDetector.isBooleanFieldCapturedVariableComparison(binOp)).isFalse();
        }

        @Test
        void returnsFalse_whenRightIsNotCapturedVariable() {
            BinaryOp binOp = new BinaryOp(
                    new LambdaExpression.FieldAccess("active", boolean.class),
                    Operator.EQ,
                    new LambdaExpression.Constant(true, boolean.class)
            );
            assertThat(PatternDetector.isBooleanFieldCapturedVariableComparison(binOp)).isFalse();
        }

        @Test
        void returnsFalse_whenCapturedVariableTypeIsNotBoolean() {
            BinaryOp binOp = new BinaryOp(
                    new LambdaExpression.FieldAccess("active", boolean.class),
                    Operator.EQ,
                    new LambdaExpression.CapturedVariable(0, int.class)
            );
            assertThat(PatternDetector.isBooleanFieldCapturedVariableComparison(binOp)).isFalse();
        }

        @ParameterizedTest(name = "returns true for {0} boolean field with {1} operator")
        @CsvSource({
                "boolean.class, EQ",
                "boolean.class, NE",
                "java.lang.Boolean, EQ"
        })
        void returnsTrue_forValidBooleanFieldCapturedVariable(String fieldTypeStr, Operator operator) {
            Class<?> fieldType = fieldTypeStr.equals("boolean.class") ? boolean.class : Boolean.class;
            BinaryOp binOp = new BinaryOp(
                    new LambdaExpression.FieldAccess("active", fieldType),
                    operator,
                    new LambdaExpression.CapturedVariable(0, fieldType)
            );
            assertThat(PatternDetector.isBooleanFieldCapturedVariableComparison(binOp)).isTrue();
        }
    }

    // ==================== isEqualityOperation Tests ====================

    @Nested
    @DisplayName("isEqualityOperation")
    class EqualityOperationTests {

        @ParameterizedTest(name = "{0} → {1}")
        @CsvSource({
                "EQ, true",
                "NE, true",
                "LT, false",
                "AND, false"
        })
        void operatorCheck(Operator operator, boolean expected) {
            BinaryOp binOp = createBinaryOp(operator);
            assertThat(PatternDetector.isEqualityOperation(binOp)).isEqualTo(expected);
        }

        private BinaryOp createBinaryOp(Operator operator) {
            LambdaExpression left = new LambdaExpression.Constant(1, int.class);
            LambdaExpression right = new LambdaExpression.Constant(1, int.class);
            return new BinaryOp(left, operator, right);
        }
    }

    // ==================== isCompareToEqualityPattern Tests ====================

    @Nested
    @DisplayName("isCompareToEqualityPattern")
    class CompareToEqualityPatternTests {

        @Test
        void returnsFalse_whenNotEQ() {
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
            BinaryOp binOp = new BinaryOp(
                    new LambdaExpression.Constant(0, int.class),
                    Operator.EQ,
                    new LambdaExpression.Constant(0, int.class)
            );
            assertThat(PatternDetector.isCompareToEqualityPattern(binOp)).isFalse();
        }

        @Test
        void returnsFalse_whenMethodNameIsNotCompareTo() {
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

        @ParameterizedTest(name = "constant {0} with type {1} → {2}")
        @CsvSource({
                "1, String.class, false",
                "2, int.class, false",
                "0, int.class, true",
                "1, int.class, true"
        })
        void constantTypeAndValueCheck(Object value, String typeStr, boolean expected) {
            Class<?> type = typeStr.equals("int.class") ? int.class : String.class;
            Object constantValue = type == int.class ? Integer.parseInt(value.toString()) : value.toString();
            BinaryOp binOp = new BinaryOp(
                    new LambdaExpression.FieldAccess("active", boolean.class),
                    Operator.EQ,
                    new LambdaExpression.Constant(constantValue, type)
            );
            assertThat(PatternDetector.isBooleanFieldConstantComparison(binOp)).isEqualTo(expected);
        }
    }

    // ==================== isLogicalOperation Tests ====================

    @Nested
    @DisplayName("isLogicalOperation")
    class LogicalOperationTests {

        @ParameterizedTest(name = "{0} → {1}")
        @CsvSource({
                "AND, true",
                "OR, true",
                "EQ, false"
        })
        void operatorCheck(Operator operator, boolean expected) {
            BinaryOp binOp = createBinaryOp(operator);
            assertThat(PatternDetector.isLogicalOperation(binOp)).isEqualTo(expected);
        }

        private BinaryOp createBinaryOp(Operator operator) {
            LambdaExpression left = new LambdaExpression.Constant(true, boolean.class);
            LambdaExpression right = new LambdaExpression.Constant(false, boolean.class);
            return new BinaryOp(left, operator, right);
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
            LambdaExpression expr = LambdaExpression.ScalarSubquery.avg(
                    Object.class,
                    new LambdaExpression.FieldAccess("salary", Long.class),
                    null
            );
            assertThat(PatternDetector.containsSubquery(expr)).isTrue();
        }

        @Test
        void returnsTrue_forExistsSubquery() {
            LambdaExpression expr = LambdaExpression.ExistsSubquery.exists(
                    Object.class,
                    new LambdaExpression.Constant(true, boolean.class)
            );
            assertThat(PatternDetector.containsSubquery(expr)).isTrue();
        }

        @Test
        void returnsTrue_forInSubquery() {
            LambdaExpression fieldExpr = new LambdaExpression.FieldAccess("id", Long.class);
            LambdaExpression selectExpr = new LambdaExpression.FieldAccess("foreignId", Long.class);
            LambdaExpression expr = new LambdaExpression.InSubquery(
                    fieldExpr, Object.class, null, selectExpr, null, false
            );
            assertThat(PatternDetector.containsSubquery(expr)).isTrue();
        }

        @ParameterizedTest(name = "returns false for {0}")
        @ValueSource(strings = {"constant", "null", "fieldAccess", "methodCall"})
        void returnsFalse_forNonSubqueryTypes(String type) {
            LambdaExpression expr = switch (type) {
                case "constant" -> new LambdaExpression.Constant(1, int.class);
                case "null" -> null;
                case "fieldAccess" -> new LambdaExpression.FieldAccess("name", String.class);
                case "methodCall" -> new LambdaExpression.MethodCall(
                        new LambdaExpression.FieldAccess("name", String.class),
                        "length", java.util.List.of(), int.class);
                default -> throw new IllegalArgumentException();
            };
            assertThat(PatternDetector.containsSubquery(expr)).isFalse();
        }

        @Test
        void returnsTrue_forNestedSubqueryInBinaryOp() {
            LambdaExpression subquery = LambdaExpression.ScalarSubquery.avg(
                    Object.class, new LambdaExpression.FieldAccess("salary", Long.class), null
            );
            BinaryOp binOp = BinaryOp.gt(new LambdaExpression.FieldAccess("salary", Long.class), subquery);
            assertThat(PatternDetector.containsSubquery(binOp)).isTrue();
        }

        @Test
        void returnsTrue_forSubqueryInUnaryOp() {
            LambdaExpression subquery = LambdaExpression.ExistsSubquery.exists(
                    Object.class, new LambdaExpression.Constant(true, boolean.class)
            );
            LambdaExpression unaryOp = new LambdaExpression.UnaryOp(
                    LambdaExpression.UnaryOp.Operator.NOT, subquery
            );
            assertThat(PatternDetector.containsSubquery(unaryOp)).isTrue();
        }

        @Test
        void returnsTrue_forSubqueryInLeftOfBinaryOp() {
            LambdaExpression subquery = LambdaExpression.ScalarSubquery.avg(
                    Object.class, new LambdaExpression.FieldAccess("salary", Long.class), null
            );
            BinaryOp binOp = BinaryOp.gt(subquery, new LambdaExpression.Constant(100, int.class));
            assertThat(PatternDetector.containsSubquery(binOp))
                    .as("Left side subquery must be detected").isTrue();
        }

        @Test
        void returnsTrue_forSubqueryOnlyInRightOfBinaryOp() {
            LambdaExpression subquery = LambdaExpression.ExistsSubquery.exists(
                    Object.class, new LambdaExpression.Constant(true, boolean.class)
            );
            BinaryOp binOp = BinaryOp.and(new LambdaExpression.Constant(true, boolean.class), subquery);
            assertThat(PatternDetector.containsSubquery(binOp))
                    .as("Right side subquery must be detected").isTrue();
        }

        @Test
        void returnsFalse_forBinaryOpWithNoSubqueries() {
            BinaryOp binOp = BinaryOp.and(
                    new LambdaExpression.FieldAccess("active", boolean.class),
                    new LambdaExpression.FieldAccess("enabled", boolean.class)
            );
            assertThat(PatternDetector.containsSubquery(binOp))
                    .as("BinaryOp with no subqueries should return false").isFalse();
        }
    }

    // ==================== containsScalarSubquery Tests ====================

    @Nested
    @DisplayName("containsScalarSubquery")
    class ContainsScalarSubqueryTests {

        @Test
        void returnsTrue_forScalarSubquery() {
            LambdaExpression expr = LambdaExpression.ScalarSubquery.avg(
                    Object.class, new LambdaExpression.FieldAccess("salary", Long.class), null
            );
            assertThat(PatternDetector.containsScalarSubquery(expr)).isTrue();
        }

        @Test
        void returnsFalse_forExistsSubquery() {
            LambdaExpression expr = LambdaExpression.ExistsSubquery.exists(
                    Object.class, new LambdaExpression.Constant(true, boolean.class)
            );
            assertThat(PatternDetector.containsScalarSubquery(expr)).isFalse();
        }

        @Test
        void returnsFalse_forInSubquery() {
            LambdaExpression fieldExpr = new LambdaExpression.FieldAccess("id", Long.class);
            LambdaExpression selectExpr = new LambdaExpression.FieldAccess("foreignId", Long.class);
            LambdaExpression expr = new LambdaExpression.InSubquery(
                    fieldExpr, Object.class, null, selectExpr, null, false
            );
            assertThat(PatternDetector.containsScalarSubquery(expr)).isFalse();
        }

        @Test
        void returnsTrue_forNestedScalarSubqueryInBinaryOp() {
            LambdaExpression subquery = LambdaExpression.ScalarSubquery.avg(
                    Object.class, new LambdaExpression.FieldAccess("salary", Long.class), null
            );
            BinaryOp binOp = BinaryOp.gt(new LambdaExpression.FieldAccess("salary", Long.class), subquery);
            assertThat(PatternDetector.containsScalarSubquery(binOp)).isTrue();
        }

        @Test
        void returnsTrue_forScalarSubqueryInLeftOfBinaryOp() {
            LambdaExpression subquery = LambdaExpression.ScalarSubquery.avg(
                    Object.class, new LambdaExpression.FieldAccess("salary", Long.class), null
            );
            BinaryOp binOp = BinaryOp.gt(subquery, new LambdaExpression.Constant(100, int.class));
            assertThat(PatternDetector.containsScalarSubquery(binOp))
                    .as("Left side ScalarSubquery must be detected").isTrue();
        }

        @Test
        void returnsTrue_forScalarSubqueryOnlyInRightOfBinaryOp() {
            LambdaExpression subquery = LambdaExpression.ScalarSubquery.avg(
                    Object.class, new LambdaExpression.FieldAccess("salary", Long.class), null
            );
            BinaryOp binOp = BinaryOp.gt(new LambdaExpression.Constant(100, int.class), subquery);
            assertThat(PatternDetector.containsScalarSubquery(binOp))
                    .as("Right side ScalarSubquery must be detected").isTrue();
        }

        @Test
        void returnsTrue_forScalarSubqueryInUnaryOp() {
            LambdaExpression subquery = LambdaExpression.ScalarSubquery.avg(
                    Object.class, new LambdaExpression.FieldAccess("salary", Long.class), null
            );
            LambdaExpression unaryOp = new LambdaExpression.UnaryOp(
                    LambdaExpression.UnaryOp.Operator.NOT, subquery
            );
            assertThat(PatternDetector.containsScalarSubquery(unaryOp)).isTrue();
        }

        @ParameterizedTest(name = "returns false for {0}")
        @ValueSource(strings = {"null", "constant", "binaryOpNoSubquery", "binaryOpWithExists"})
        void returnsFalse_forNonScalarTypes(String type) {
            LambdaExpression expr = switch (type) {
                case "null" -> null;
                case "constant" -> new LambdaExpression.Constant(1, int.class);
                case "binaryOpNoSubquery" -> BinaryOp.and(
                        new LambdaExpression.FieldAccess("active", boolean.class),
                        new LambdaExpression.FieldAccess("enabled", boolean.class));
                case "binaryOpWithExists" -> BinaryOp.and(
                        LambdaExpression.ExistsSubquery.exists(Object.class,
                                new LambdaExpression.Constant(true, boolean.class)),
                        new LambdaExpression.Constant(true, boolean.class));
                default -> throw new IllegalArgumentException();
            };
            assertThat(PatternDetector.containsScalarSubquery(expr)).isFalse();
        }
    }

    // ==================== isBooleanConstant Tests ====================

    @Nested
    @DisplayName("isBooleanConstant")
    class IsBooleanConstantTests {

        @ParameterizedTest(name = "constant {0} of type {1} → {2}")
        @CsvSource({
                "true, boolean.class, true",
                "false, boolean.class, true",
                "0, int.class, true",
                "1, int.class, true",
                "2, int.class, false",
                "true, String.class, false"
        })
        void constantCheck(String value, String typeStr, boolean expected) {
            Class<?> type = switch (typeStr) {
                case "boolean.class" -> boolean.class;
                case "int.class" -> int.class;
                default -> String.class;
            };
            Object constantValue = switch (typeStr) {
                case "boolean.class" -> Boolean.parseBoolean(value);
                case "int.class" -> Integer.parseInt(value);
                default -> value;
            };
            LambdaExpression expr = new LambdaExpression.Constant(constantValue, type);
            assertThat(PatternDetector.isBooleanConstant(expr)).isEqualTo(expected);
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
                    Object.class, new LambdaExpression.Constant(true, boolean.class)
            );
            BinaryOp binOp = BinaryOp.eq(subquery, new LambdaExpression.Constant(true, boolean.class));
            assertThat(PatternDetector.isSubqueryBooleanComparison(binOp)).isTrue();
        }

        @Test
        void returnsTrue_forExistsSubqueryNeOne() {
            LambdaExpression subquery = LambdaExpression.ExistsSubquery.exists(
                    Object.class, new LambdaExpression.Constant(true, boolean.class)
            );
            BinaryOp binOp = BinaryOp.ne(subquery, new LambdaExpression.Constant(1, int.class));
            assertThat(PatternDetector.isSubqueryBooleanComparison(binOp)).isTrue();
        }

        @Test
        void returnsTrue_forBooleanConstantOnLeftSubqueryOnRight() {
            LambdaExpression subquery = LambdaExpression.ExistsSubquery.exists(
                    Object.class, new LambdaExpression.Constant(true, boolean.class)
            );
            BinaryOp binOp = BinaryOp.eq(new LambdaExpression.Constant(true, boolean.class), subquery);
            assertThat(PatternDetector.isSubqueryBooleanComparison(binOp)).isTrue();
        }

        @Test
        void returnsFalse_forNonEqualityOperator() {
            LambdaExpression subquery = LambdaExpression.ExistsSubquery.exists(
                    Object.class, new LambdaExpression.Constant(true, boolean.class)
            );
            BinaryOp binOp = BinaryOp.lt(subquery, new LambdaExpression.Constant(1, int.class));
            assertThat(PatternDetector.isSubqueryBooleanComparison(binOp)).isFalse();
        }

        @Test
        void returnsFalse_forSubqueryComparedToNonBoolean() {
            LambdaExpression subquery = LambdaExpression.ExistsSubquery.exists(
                    Object.class, new LambdaExpression.Constant(true, boolean.class)
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

        @ParameterizedTest(name = "{0} with {1}={2} → {3}")
        @CsvSource({
                "EQ, false, boolean.class, true",
                "EQ, 0, int.class, true",
                "NE, true, boolean.class, true",
                "NE, 1, int.class, true",
                "EQ, true, boolean.class, false",
                "NE, false, boolean.class, false",
                "LT, false, boolean.class, false"
        })
        void negationCheck(Operator operator, String value, String typeStr, boolean expected) {
            Class<?> type = typeStr.equals("int.class") ? int.class : boolean.class;
            Object constantValue = type == int.class ? Integer.parseInt(value) : Boolean.parseBoolean(value);
            LambdaExpression constant = new LambdaExpression.Constant(constantValue, type);
            assertThat(PatternDetector.isNegatedSubqueryComparison(operator, constant)).isEqualTo(expected);
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
            assertThat(PatternDetector.BranchPattern.detect(stack))
                    .isEqualTo(PatternDetector.BranchPattern.OTHER);
        }

        @Test
        void returnsNumericComparison_forDcmplPattern() {
            Deque<LambdaExpression> stack = new ArrayDeque<>();
            stack.push(new LambdaExpression.FieldAccess("price", double.class));
            stack.push(new LambdaExpression.FieldAccess("discount", double.class));
            assertThat(PatternDetector.BranchPattern.detect(stack))
                    .isEqualTo(PatternDetector.BranchPattern.NUMERIC_COMPARISON);
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
            assertThat(PatternDetector.BranchPattern.detect(stack))
                    .isEqualTo(PatternDetector.BranchPattern.NUMERIC_COMPARISON);
        }

        @Test
        void returnsCompareTo_forCompareToPattern() {
            Deque<LambdaExpression> stack = new ArrayDeque<>();
            LambdaExpression target = new LambdaExpression.FieldAccess("name", String.class);
            stack.push(new LambdaExpression.MethodCall(target, "compareTo", java.util.List.of(), int.class));
            assertThat(PatternDetector.BranchPattern.detect(stack))
                    .isEqualTo(PatternDetector.BranchPattern.COMPARE_TO);
        }

        @Test
        void returnsArithmetic_forArithmeticExpression() {
            Deque<LambdaExpression> stack = new ArrayDeque<>();
            BinaryOp arithmetic = BinaryOp.add(
                    new LambdaExpression.FieldAccess("a", int.class),
                    new LambdaExpression.FieldAccess("b", int.class)
            );
            stack.push(arithmetic);
            assertThat(PatternDetector.BranchPattern.detect(stack))
                    .isEqualTo(PatternDetector.BranchPattern.ARITHMETIC);
        }

        @Test
        void returnsOther_forSimpleFieldAccess() {
            Deque<LambdaExpression> stack = new ArrayDeque<>();
            stack.push(new LambdaExpression.FieldAccess("active", boolean.class));
            assertThat(PatternDetector.BranchPattern.detect(stack))
                    .isEqualTo(PatternDetector.BranchPattern.OTHER);
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
            stack.push(new LambdaExpression.NullLiteral(Object.class));
            assertThat(PatternDetector.isDcmplPattern(stack)).isFalse();
        }

        @ParameterizedTest(name = "returns true for {0}")
        @ValueSource(strings = {
                "twoFieldAccesses", "fieldAndConstant", "capturedVariables",
                "groupAggregation", "scalarSubquery", "pathExpression",
                "biEntityFieldAccess", "groupKeyReference", "biEntityPathExpression", "arithmetic"
        })
        void returnsTrue_forComparableExpressions(String type) {
            Deque<LambdaExpression> stack = createStackForType(type);
            assertThat(PatternDetector.isDcmplPattern(stack)).isTrue();
        }

        private Deque<LambdaExpression> createStackForType(String type) {
            Deque<LambdaExpression> stack = new ArrayDeque<>();
            switch (type) {
                case "twoFieldAccesses" -> {
                    stack.push(new LambdaExpression.FieldAccess("a", double.class));
                    stack.push(new LambdaExpression.FieldAccess("b", double.class));
                }
                case "fieldAndConstant" -> {
                    stack.push(new LambdaExpression.FieldAccess("price", double.class));
                    stack.push(new LambdaExpression.Constant(100.0, double.class));
                }
                case "capturedVariables" -> {
                    stack.push(new LambdaExpression.CapturedVariable(0, double.class));
                    stack.push(new LambdaExpression.CapturedVariable(1, double.class));
                }
                case "groupAggregation" -> {
                    stack.push(new LambdaExpression.GroupAggregation(
                            LambdaExpression.GroupAggregationType.COUNT, null, Long.class));
                    stack.push(new LambdaExpression.Constant(1L, long.class));
                }
                case "scalarSubquery" -> {
                    stack.push(LambdaExpression.ScalarSubquery.avg(
                            Object.class, new LambdaExpression.FieldAccess("salary", Long.class), null));
                    stack.push(new LambdaExpression.FieldAccess("salary", Long.class));
                }
                case "pathExpression" -> {
                    var segments = java.util.List.of(
                            new LambdaExpression.PathSegment("address", Object.class, LambdaExpression.RelationType.MANY_TO_ONE),
                            new LambdaExpression.PathSegment("city", String.class, LambdaExpression.RelationType.FIELD));
                    stack.push(new LambdaExpression.PathExpression(segments, String.class));
                    stack.push(new LambdaExpression.Constant("NYC", String.class));
                }
                case "biEntityFieldAccess" -> {
                    stack.push(new LambdaExpression.BiEntityFieldAccess("salary", Long.class, LambdaExpression.EntityPosition.FIRST));
                    stack.push(new LambdaExpression.BiEntityFieldAccess("salary", Long.class, LambdaExpression.EntityPosition.SECOND));
                }
                case "groupKeyReference" -> {
                    stack.push(new LambdaExpression.GroupKeyReference(
                            new LambdaExpression.FieldAccess("department", String.class), String.class));
                    stack.push(new LambdaExpression.Constant("Sales", String.class));
                }
                case "biEntityPathExpression" -> {
                    var segments = java.util.List.of(
                            new LambdaExpression.PathSegment("address", Object.class, LambdaExpression.RelationType.MANY_TO_ONE),
                            new LambdaExpression.PathSegment("city", String.class, LambdaExpression.RelationType.FIELD));
                    stack.push(new LambdaExpression.BiEntityPathExpression(segments, String.class, LambdaExpression.EntityPosition.FIRST));
                    stack.push(new LambdaExpression.Constant("NYC", String.class));
                }
                case "arithmetic" -> {
                    stack.push(BinaryOp.add(
                            new LambdaExpression.FieldAccess("a", int.class),
                            new LambdaExpression.FieldAccess("b", int.class)));
                    stack.push(new LambdaExpression.Constant(10, int.class));
                }
                default -> throw new IllegalArgumentException("Unknown type: " + type);
            }
            return stack;
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
            assertThat(PatternDetector.BinaryOperationCategory.categorize(binOp, op -> true))
                    .isEqualTo(PatternDetector.BinaryOperationCategory.STRING_CONCATENATION);
        }

        @ParameterizedTest(name = "{0} → {1}")
        @CsvSource({
                "ADD, ARITHMETIC",
                "AND, LOGICAL"
        })
        void categorizesByOperator(Operator operator, PatternDetector.BinaryOperationCategory expected) {
            BinaryOp binOp = new BinaryOp(
                    new LambdaExpression.FieldAccess("a", int.class),
                    operator,
                    new LambdaExpression.FieldAccess("b", int.class)
            );
            assertThat(PatternDetector.BinaryOperationCategory.categorize(binOp, op -> false))
                    .isEqualTo(expected);
        }

        @Test
        void returnsNullCheck_forNullComparisonPattern() {
            BinaryOp binOp = BinaryOp.eq(
                    new LambdaExpression.FieldAccess("name", String.class),
                    new LambdaExpression.NullLiteral(String.class)
            );
            assertThat(PatternDetector.BinaryOperationCategory.categorize(binOp, op -> false))
                    .isEqualTo(PatternDetector.BinaryOperationCategory.NULL_CHECK);
        }

        @Test
        void returnsBooleanFieldConstant_forBooleanFieldWithConstant() {
            BinaryOp binOp = BinaryOp.eq(
                    new LambdaExpression.FieldAccess("active", boolean.class),
                    new LambdaExpression.Constant(1, int.class)
            );
            assertThat(PatternDetector.BinaryOperationCategory.categorize(binOp, op -> false))
                    .isEqualTo(PatternDetector.BinaryOperationCategory.BOOLEAN_FIELD_CONSTANT);
        }

        @Test
        void returnsBooleanFieldCapturedVariable_forBooleanFieldWithCapturedVar() {
            BinaryOp binOp = BinaryOp.eq(
                    new LambdaExpression.FieldAccess("active", boolean.class),
                    new LambdaExpression.CapturedVariable(0, boolean.class)
            );
            assertThat(PatternDetector.BinaryOperationCategory.categorize(binOp, op -> false))
                    .isEqualTo(PatternDetector.BinaryOperationCategory.BOOLEAN_FIELD_CAPTURED_VARIABLE);
        }

        @Test
        void returnsCompareToEquality_forCompareToEqZero() {
            LambdaExpression target = new LambdaExpression.FieldAccess("name", String.class);
            BinaryOp binOp = BinaryOp.eq(
                    new LambdaExpression.MethodCall(target, "compareTo", java.util.List.of(), int.class),
                    new LambdaExpression.Constant(0, int.class)
            );
            assertThat(PatternDetector.BinaryOperationCategory.categorize(binOp, op -> false))
                    .isEqualTo(PatternDetector.BinaryOperationCategory.COMPARE_TO_EQUALITY);
        }

        @Test
        void returnsComparison_forSimpleEquality() {
            BinaryOp binOp = BinaryOp.eq(
                    new LambdaExpression.FieldAccess("id", int.class),
                    new LambdaExpression.Constant(42, int.class)
            );
            assertThat(PatternDetector.BinaryOperationCategory.categorize(binOp, op -> false))
                    .isEqualTo(PatternDetector.BinaryOperationCategory.COMPARISON);
        }
    }

    // ==================== isArithmeticExpression Tests ====================

    @Nested
    @DisplayName("isArithmeticExpression")
    class IsArithmeticExpressionTests {

        @ParameterizedTest(name = "{0} → {1}")
        @CsvSource({
                "ADD, true",
                "SUB, true",
                "MUL, true",
                "DIV, true",
                "MOD, true",
                "AND, false",
                "EQ, false"
        })
        void operatorCheck(Operator operator, boolean expected) {
            BinaryOp binOp = new BinaryOp(
                    new LambdaExpression.FieldAccess("a", int.class),
                    operator,
                    new LambdaExpression.FieldAccess("b", int.class)
            );
            assertThat(PatternDetector.isArithmeticExpression(binOp)).isEqualTo(expected);
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
            BinaryOp binOp = new BinaryOp(
                    new LambdaExpression.FieldAccess("name", String.class),
                    Operator.EQ,
                    new LambdaExpression.CapturedVariable(0, boolean.class)
            );
            assertThat(PatternDetector.isBooleanFieldCapturedVariableComparison(binOp))
                    .as("Should return false when field type is not boolean").isFalse();
        }

        @Test
        void isSubqueryBooleanComparison_returnsFalse_whenRightIsNotSubqueryButLeftIsBooleanConstant() {
            BinaryOp binOp = BinaryOp.eq(
                    new LambdaExpression.Constant(true, boolean.class),
                    new LambdaExpression.FieldAccess("active", boolean.class)
            );
            assertThat(PatternDetector.isSubqueryBooleanComparison(binOp))
                    .as("Should return false when right is not subquery").isFalse();
        }

        @Test
        void isSubqueryBooleanComparison_returnsFalse_whenRightIsNotSubqueryAndLeftIsIntZero() {
            BinaryOp binOp = BinaryOp.eq(
                    new LambdaExpression.Constant(0, int.class),
                    new LambdaExpression.FieldAccess("count", int.class)
            );
            assertThat(PatternDetector.isSubqueryBooleanComparison(binOp))
                    .as("Should return false when right is not subquery").isFalse();
        }

        @Test
        void isNegatedSubqueryComparison_returnsFalse_forGtOperatorWithTrueValue() {
            LambdaExpression constant = new LambdaExpression.Constant(true, boolean.class);
            assertThat(PatternDetector.isNegatedSubqueryComparison(Operator.GT, constant))
                    .as("GT operator with TRUE should return false").isFalse();
        }

        @Test
        void isNegatedSubqueryComparison_returnsFalse_forLeOperatorWithIntOne() {
            LambdaExpression constant = new LambdaExpression.Constant(1, int.class);
            assertThat(PatternDetector.isNegatedSubqueryComparison(Operator.LE, constant))
                    .as("LE operator with 1 should return false").isFalse();
        }

        @Test
        void isBooleanFieldConstantComparison_returnsFalse_whenConstantIsLongType() {
            BinaryOp binOp = new BinaryOp(
                    new LambdaExpression.FieldAccess("active", boolean.class),
                    Operator.EQ,
                    new LambdaExpression.Constant(1L, long.class)
            );
            assertThat(PatternDetector.isBooleanFieldConstantComparison(binOp))
                    .as("Should return false when constant type is Long").isFalse();
        }

        @Test
        void branchPatternDetect_returnsCompareTo_forIntReturningMethodCall() {
            Deque<LambdaExpression> stack = new ArrayDeque<>();
            LambdaExpression target = new LambdaExpression.FieldAccess("name", String.class);
            stack.push(new LambdaExpression.MethodCall(target, "length", java.util.List.of(), int.class));
            assertThat(PatternDetector.BranchPattern.detect(stack))
                    .isEqualTo(PatternDetector.BranchPattern.COMPARE_TO);
        }

        @ParameterizedTest(name = "{0} returning method → OTHER")
        @ValueSource(strings = {"boolean", "void"})
        void branchPatternDetect_returnsOther_forNonIntReturningMethodCall(String returnType) {
            Deque<LambdaExpression> stack = new ArrayDeque<>();
            LambdaExpression target = new LambdaExpression.FieldAccess("name", String.class);
            Class<?> type = returnType.equals("boolean") ? boolean.class : void.class;
            String methodName = returnType.equals("boolean") ? "isEmpty" : "clear";
            stack.push(new LambdaExpression.MethodCall(target, methodName, java.util.List.of(), type));
            assertThat(PatternDetector.BranchPattern.detect(stack))
                    .isEqualTo(PatternDetector.BranchPattern.OTHER);
        }
    }
}
