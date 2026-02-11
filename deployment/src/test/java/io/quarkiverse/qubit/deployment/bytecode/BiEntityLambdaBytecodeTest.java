package io.quarkiverse.qubit.deployment.bytecode;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import io.quarkiverse.qubit.deployment.ast.LambdaExpression;
import io.quarkiverse.qubit.deployment.ast.LambdaExpression.BiEntityFieldAccess;
import io.quarkiverse.qubit.deployment.ast.LambdaExpression.BinaryOp;
import io.quarkiverse.qubit.deployment.ast.LambdaExpression.EntityPosition;
import io.quarkiverse.qubit.deployment.ast.LambdaExpression.MethodCall;
import io.quarkiverse.qubit.deployment.ast.LambdaExpression.UnaryOp;

/**
 * Bytecode analysis tests for bi-entity lambda expressions (BiQuerySpec).
 *
 * <p>
 * Tests lambda bytecode parsing for join query predicates and projections
 * without executing queries. Verifies that bi-entity lambdas are correctly
 * analyzed and produce the appropriate BiEntity AST nodes.
 *
 * <p>
 * Uses pre-compiled lambda sources from {@link io.quarkiverse.qubit.deployment.testutil.BiEntityLambdaTestSources}
 * for reliable bytecode generation and analysis.
 */
@DisplayName("BiEntityLambdaBytecodeTest")
class BiEntityLambdaBytecodeTest extends PrecompiledBiEntityLambdaAnalyzer {

    // ==================== SIMPLE FIELD ACCESS ====================

    @Nested
    @DisplayName("Simple Field Access")
    class SimpleFieldAccessTests {

        @ParameterizedTest(name = "{0} → {1}.{2}")
        @MethodSource("io.quarkiverse.qubit.deployment.bytecode.BiEntityLambdaBytecodeTest#simpleFieldAccessCases")
        @DisplayName("returns BiEntityFieldAccess")
        void simpleFieldAccess(String methodName, EntityPosition expectedPosition, String expectedField) {
            LambdaExpression expr = analyzeBiEntityLambda(methodName);

            assertThat(expr).isInstanceOf(BiEntityFieldAccess.class);
            BiEntityFieldAccess fieldAccess = (BiEntityFieldAccess) expr;
            assertThat(fieldAccess.fieldName()).isEqualTo(expectedField);
            assertThat(fieldAccess.entityPosition()).isEqualTo(expectedPosition);
        }

        @Test
        @DisplayName("negated boolean field returns UnaryOp NOT")
        void joinedEntityNegatedBooleanField() {
            LambdaExpression expr = analyzeBiEntityLambda("joinedEntityNegatedBooleanField");

            assertUnaryOp(expr, UnaryOp.Operator.NOT);
            UnaryOp unaryOp = (UnaryOp) expr;

            assertThat(unaryOp.operand()).isInstanceOf(BiEntityFieldAccess.class);
            BiEntityFieldAccess fieldAccess = (BiEntityFieldAccess) unaryOp.operand();
            assertThat(fieldAccess.fieldName()).isEqualTo("isPrimary");
            assertThat(fieldAccess.entityPosition()).isEqualTo(EntityPosition.SECOND);
        }
    }

    static Stream<Arguments> simpleFieldAccessCases() {
        return Stream.of(
                // Boolean fields
                Arguments.of("joinedEntityBooleanField", EntityPosition.SECOND, "isPrimary"),
                Arguments.of("sourceEntityBooleanField", EntityPosition.FIRST, "active"),
                // Projections
                Arguments.of("projectJoinedEntityField", EntityPosition.SECOND, "number"),
                Arguments.of("projectSourceEntityField", EntityPosition.FIRST, "firstName"),
                Arguments.of("projectSourceEntityIntField", EntityPosition.FIRST, "age"));
    }

    // ==================== FIELD COMPARISON WITH CONSTANT ====================

    @Nested
    @DisplayName("Field Comparison with Constant")
    class FieldComparisonTests {

        @ParameterizedTest(name = "{0} → {1}.{2} {3} {4}")
        @MethodSource("io.quarkiverse.qubit.deployment.bytecode.BiEntityLambdaBytecodeTest#fieldComparisonCases")
        @DisplayName("returns BinaryOp with field and constant")
        void fieldComparison(String methodName, EntityPosition expectedPosition,
                String expectedField, BinaryOp.Operator expectedOp, Object expectedConstant) {
            LambdaExpression expr = analyzeBiEntityLambda(methodName);

            assertBinaryOp(expr, expectedOp);
            BinaryOp binOp = (BinaryOp) expr;

            assertThat(binOp.left()).isInstanceOf(BiEntityFieldAccess.class);
            BiEntityFieldAccess fieldAccess = (BiEntityFieldAccess) binOp.left();
            assertThat(fieldAccess.fieldName()).isEqualTo(expectedField);
            assertThat(fieldAccess.entityPosition()).isEqualTo(expectedPosition);

            assertConstant(binOp.right(), expectedConstant);
        }
    }

    static Stream<Arguments> fieldComparisonCases() {
        return Stream.of(
                Arguments.of("joinedEntityFieldEquals", EntityPosition.SECOND, "type", BinaryOp.Operator.EQ, "mobile"),
                Arguments.of("sourceEntityFieldEquals", EntityPosition.FIRST, "firstName", BinaryOp.Operator.EQ, "John"),
                Arguments.of("sourceEntityIntegerComparison", EntityPosition.FIRST, "age", BinaryOp.Operator.GT, 30));
    }

    // ==================== STRING METHOD CALLS ====================

    @Nested
    @DisplayName("String Method Calls")
    class StringMethodTests {

        @ParameterizedTest(name = "{0} → {2}.{3}({4})")
        @MethodSource("io.quarkiverse.qubit.deployment.bytecode.BiEntityLambdaBytecodeTest#stringMethodCases")
        @DisplayName("returns MethodCall on BiEntityFieldAccess")
        void stringMethod(String lambdaName, EntityPosition expectedPosition,
                String expectedField, String expectedMethod, String expectedArg) {
            LambdaExpression expr = analyzeBiEntityLambda(lambdaName);

            assertMethodCall(expr, expectedMethod);
            MethodCall methodCall = (MethodCall) expr;

            assertThat(methodCall.target()).isInstanceOf(BiEntityFieldAccess.class);
            BiEntityFieldAccess fieldAccess = (BiEntityFieldAccess) methodCall.target();
            assertThat(fieldAccess.fieldName()).isEqualTo(expectedField);
            assertThat(fieldAccess.entityPosition()).isEqualTo(expectedPosition);

            assertThat(methodCall.arguments()).hasSize(1);
            assertConstant(methodCall.arguments().getFirst(), expectedArg);
        }
    }

    static Stream<Arguments> stringMethodCases() {
        return Stream.of(
                Arguments.of("joinedEntityStartsWith", EntityPosition.SECOND, "number", "startsWith", "555"),
                Arguments.of("joinedEntityContains", EntityPosition.SECOND, "number", "contains", "01"),
                Arguments.of("joinedEntityEndsWith", EntityPosition.SECOND, "number", "endsWith", "00"));
    }

    // ==================== PREDICATES ON BOTH ENTITIES ====================

    @Nested
    @DisplayName("Predicates on Both Entities")
    class BothEntitiesTests {

        @Test
        @DisplayName("AND of boolean fields on both entities")
        void bothEntitiesSimpleAnd() {
            LambdaExpression expr = analyzeBiEntityLambda("bothEntitiesSimpleAnd");

            // Expected: p.active && ph.isPrimary (with == true wrapping)
            assertBinaryOp(expr, BinaryOp.Operator.AND);
            BinaryOp binOp = (BinaryOp) expr;

            // Left: p.active == true (FIRST entity)
            assertBooleanFieldComparison(binOp.left(), EntityPosition.FIRST, "active");

            // Right: ph.isPrimary == true (SECOND entity)
            assertBooleanFieldComparison(binOp.right(), EntityPosition.SECOND, "isPrimary");
        }

        @Test
        @DisplayName("AND of comparison and equals on both entities")
        void bothEntitiesComplex() {
            LambdaExpression expr = analyzeBiEntityLambda("bothEntitiesComplex");

            // Expected: p.age >= 30 && ph.type.equals("work")
            assertBinaryOp(expr, BinaryOp.Operator.AND);
            BinaryOp binOp = (BinaryOp) expr;

            // Left: p.age >= 30
            assertFieldComparison(binOp.left(), EntityPosition.FIRST, "age", BinaryOp.Operator.GE, 30);

            // Right: ph.type == "work"
            assertFieldComparison(binOp.right(), EntityPosition.SECOND, "type", BinaryOp.Operator.EQ, "work");
        }

        @Test
        @DisplayName("OR of comparison and equals on both entities")
        void bothEntitiesWithOr() {
            LambdaExpression expr = analyzeBiEntityLambda("bothEntitiesWithOr");

            // Expected: p.age > 50 || ph.type.equals("mobile")
            assertBinaryOp(expr, BinaryOp.Operator.OR);
            BinaryOp binOp = (BinaryOp) expr;

            // Left: p.age > 50 (FIRST entity)
            BinaryOp leftBinOp = assertFieldComparisonGetBinOp(binOp.left(), EntityPosition.FIRST, BinaryOp.Operator.GT);
            assertThat(leftBinOp.left()).isInstanceOf(BiEntityFieldAccess.class);

            // Right: ph.type == "mobile"
            assertFieldComparison(binOp.right(), EntityPosition.SECOND, "type", BinaryOp.Operator.EQ, "mobile");
        }
    }

    // ==================== CAPTURED VARIABLES ====================

    @Nested
    @DisplayName("Captured Variables")
    class CapturedVariableTests {

        @Test
        @DisplayName("joined entity with captured variable")
        void joinedEntityWithCapturedVariable() {
            LambdaExpression expr = analyzeBiEntityLambda("joinedEntityWithCapturedVariable");

            assertBinaryOp(expr, BinaryOp.Operator.EQ);
            BinaryOp binOp = (BinaryOp) expr;

            assertThat(binOp.left()).isInstanceOf(BiEntityFieldAccess.class);
            BiEntityFieldAccess fieldAccess = (BiEntityFieldAccess) binOp.left();
            assertThat(fieldAccess.fieldName()).isEqualTo("type");
            assertThat(fieldAccess.entityPosition()).isEqualTo(EntityPosition.SECOND);

            assertCapturedVariable(binOp.right(), 0);
        }

        @Test
        @DisplayName("both entities with captured variables")
        void bothEntitiesWithCapturedVariable() {
            LambdaExpression expr = analyzeBiEntityLambda("bothEntitiesWithCapturedVariable");

            // Expected: p.age > minAge && ph.type.equals(targetType)
            assertBinaryOp(expr, BinaryOp.Operator.AND);
            BinaryOp binOp = (BinaryOp) expr;

            // Left: p.age > minAge (captured variable index 0)
            assertThat(binOp.left()).isInstanceOf(BinaryOp.class);
            BinaryOp leftBinOp = (BinaryOp) binOp.left();
            assertCapturedVariable(leftBinOp.right(), 0);

            // Right: ph.type == targetType (captured variable index 1)
            assertThat(binOp.right()).isInstanceOf(BinaryOp.class);
            BinaryOp rightBinOp = (BinaryOp) binOp.right();
            assertThat(rightBinOp.operator()).isEqualTo(BinaryOp.Operator.EQ);
            assertThat(rightBinOp.left()).isInstanceOf(BiEntityFieldAccess.class);
            BiEntityFieldAccess typeField = (BiEntityFieldAccess) rightBinOp.left();
            assertThat(typeField.fieldName()).isEqualTo("type");
            assertThat(typeField.entityPosition()).isEqualTo(EntityPosition.SECOND);
            assertCapturedVariable(rightBinOp.right(), 1);
        }
    }

    // ==================== HELPER METHODS ====================

    private void assertBooleanFieldComparison(LambdaExpression expr, EntityPosition position, String fieldName) {
        assertThat(expr).isInstanceOf(BinaryOp.class);
        BinaryOp binOp = (BinaryOp) expr;
        assertThat(binOp.operator()).isEqualTo(BinaryOp.Operator.EQ);
        assertThat(binOp.left()).isInstanceOf(BiEntityFieldAccess.class);
        BiEntityFieldAccess field = (BiEntityFieldAccess) binOp.left();
        assertThat(field.fieldName()).isEqualTo(fieldName);
        assertThat(field.entityPosition()).isEqualTo(position);
    }

    private void assertFieldComparison(LambdaExpression expr, EntityPosition position,
            String fieldName, BinaryOp.Operator op, Object constant) {
        assertThat(expr).isInstanceOf(BinaryOp.class);
        BinaryOp binOp = (BinaryOp) expr;
        assertThat(binOp.operator()).isEqualTo(op);
        assertThat(binOp.left()).isInstanceOf(BiEntityFieldAccess.class);
        BiEntityFieldAccess field = (BiEntityFieldAccess) binOp.left();
        assertThat(field.fieldName()).isEqualTo(fieldName);
        assertThat(field.entityPosition()).isEqualTo(position);
        assertConstant(binOp.right(), constant);
    }

    private BinaryOp assertFieldComparisonGetBinOp(LambdaExpression expr, EntityPosition position, BinaryOp.Operator op) {
        assertThat(expr).isInstanceOf(BinaryOp.class);
        BinaryOp binOp = (BinaryOp) expr;
        assertThat(binOp.operator()).isEqualTo(op);
        assertThat(binOp.left()).isInstanceOf(BiEntityFieldAccess.class);
        BiEntityFieldAccess field = (BiEntityFieldAccess) binOp.left();
        assertThat(field.entityPosition()).isEqualTo(position);
        return binOp;
    }
}
