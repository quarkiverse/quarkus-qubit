package io.quarkiverse.qubit.deployment.bytecode;

import io.quarkiverse.qubit.deployment.ast.LambdaExpression;
import io.quarkiverse.qubit.deployment.ast.LambdaExpression.BiEntityFieldAccess;
import io.quarkiverse.qubit.deployment.ast.LambdaExpression.BinaryOp;
import io.quarkiverse.qubit.deployment.ast.LambdaExpression.EntityPosition;
import io.quarkiverse.qubit.deployment.ast.LambdaExpression.MethodCall;
import io.quarkiverse.qubit.deployment.ast.LambdaExpression.UnaryOp;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Bytecode analysis tests for bi-entity lambda expressions (BiQuerySpec).
 *
 * <p>Tests lambda bytecode parsing for join query predicates and projections
 * without executing queries. Verifies that bi-entity lambdas are correctly
 * analyzed and produce the appropriate BiEntity AST nodes.
 *
 * <p>Uses pre-compiled lambda sources from {@link io.quarkiverse.qubit.deployment.testutil.BiEntityLambdaTestSources}
 * for reliable bytecode generation and analysis.
 *
 * <p>Iteration 6: Join Queries - BiQuerySpec lambda analysis.
 */
class BiEntityLambdaBytecodeTest extends PrecompiledBiEntityLambdaAnalyzer {

    // ==================== FIELD ACCESS ON JOINED ENTITY (SECOND) ====================

    @Test
    void joinedEntityFieldEquals() {
        LambdaExpression expr = analyzeBiEntityLambda("joinedEntityFieldEquals");

        // Expected: ph.type.equals("mobile") - optimized to BinaryOp(EQ)
        // The analyzer converts String.equals(constant) to BinaryOp for efficiency
        assertBinaryOp(expr, BinaryOp.Operator.EQ);
        BinaryOp binOp = (BinaryOp) expr;

        // Left should be BiEntityFieldAccess on SECOND entity (ph.type)
        assertThat(binOp.left()).isInstanceOf(BiEntityFieldAccess.class);
        BiEntityFieldAccess fieldAccess = (BiEntityFieldAccess) binOp.left();
        assertThat(fieldAccess.fieldName()).isEqualTo("type");
        assertThat(fieldAccess.entityPosition()).isEqualTo(EntityPosition.SECOND);

        // Right should be constant "mobile"
        assertConstant(binOp.right(), "mobile");
    }

    @Test
    void joinedEntityBooleanField() {
        LambdaExpression expr = analyzeBiEntityLambda("joinedEntityBooleanField");

        // Expected: ph.isPrimary (BiEntityFieldAccess on SECOND)
        assertThat(expr).isInstanceOf(BiEntityFieldAccess.class);
        BiEntityFieldAccess fieldAccess = (BiEntityFieldAccess) expr;
        assertThat(fieldAccess.fieldName()).isEqualTo("isPrimary");
        assertThat(fieldAccess.entityPosition()).isEqualTo(EntityPosition.SECOND);
    }

    @Test
    void joinedEntityNegatedBooleanField() {
        LambdaExpression expr = analyzeBiEntityLambda("joinedEntityNegatedBooleanField");

        // Expected: !ph.isPrimary (UnaryOp NOT on BiEntityFieldAccess)
        assertUnaryOp(expr, UnaryOp.Operator.NOT);
        UnaryOp unaryOp = (UnaryOp) expr;

        assertThat(unaryOp.operand()).isInstanceOf(BiEntityFieldAccess.class);
        BiEntityFieldAccess fieldAccess = (BiEntityFieldAccess) unaryOp.operand();
        assertThat(fieldAccess.fieldName()).isEqualTo("isPrimary");
        assertThat(fieldAccess.entityPosition()).isEqualTo(EntityPosition.SECOND);
    }

    // ==================== FIELD ACCESS ON SOURCE ENTITY (FIRST) ====================

    @Test
    void sourceEntityFieldEquals() {
        LambdaExpression expr = analyzeBiEntityLambda("sourceEntityFieldEquals");

        // Expected: p.firstName.equals("John") - optimized to BinaryOp(EQ)
        assertBinaryOp(expr, BinaryOp.Operator.EQ);
        BinaryOp binOp = (BinaryOp) expr;

        // Left should be BiEntityFieldAccess on FIRST entity (p.firstName)
        assertThat(binOp.left()).isInstanceOf(BiEntityFieldAccess.class);
        BiEntityFieldAccess fieldAccess = (BiEntityFieldAccess) binOp.left();
        assertThat(fieldAccess.fieldName()).isEqualTo("firstName");
        assertThat(fieldAccess.entityPosition()).isEqualTo(EntityPosition.FIRST);

        // Right should be constant "John"
        assertConstant(binOp.right(), "John");
    }

    @Test
    void sourceEntityIntegerComparison() {
        LambdaExpression expr = analyzeBiEntityLambda("sourceEntityIntegerComparison");

        // Expected: p.age > 30
        assertBinaryOp(expr, BinaryOp.Operator.GT);
        BinaryOp binOp = (BinaryOp) expr;

        // Left should be BiEntityFieldAccess on FIRST entity (p.age)
        assertThat(binOp.left()).isInstanceOf(BiEntityFieldAccess.class);
        BiEntityFieldAccess fieldAccess = (BiEntityFieldAccess) binOp.left();
        assertThat(fieldAccess.fieldName()).isEqualTo("age");
        assertThat(fieldAccess.entityPosition()).isEqualTo(EntityPosition.FIRST);

        // Right should be constant 30
        assertConstant(binOp.right(), 30);
    }

    @Test
    void sourceEntityBooleanField() {
        LambdaExpression expr = analyzeBiEntityLambda("sourceEntityBooleanField");

        // Expected: p.active (BiEntityFieldAccess on FIRST)
        assertThat(expr).isInstanceOf(BiEntityFieldAccess.class);
        BiEntityFieldAccess fieldAccess = (BiEntityFieldAccess) expr;
        assertThat(fieldAccess.fieldName()).isEqualTo("active");
        assertThat(fieldAccess.entityPosition()).isEqualTo(EntityPosition.FIRST);
    }

    // ==================== PREDICATES ON BOTH ENTITIES ====================

    @Test
    void bothEntitiesSimpleAnd() {
        LambdaExpression expr = analyzeBiEntityLambda("bothEntitiesSimpleAnd");

        // Expected: p.active && ph.isPrimary
        // Note: Boolean fields get compiled as field == true comparisons at bytecode level
        assertBinaryOp(expr, BinaryOp.Operator.AND);
        BinaryOp binOp = (BinaryOp) expr;

        // Left: p.active == true (FIRST entity)
        assertThat(binOp.left()).isInstanceOf(BinaryOp.class);
        BinaryOp leftBinOp = (BinaryOp) binOp.left();
        assertThat(leftBinOp.operator()).isEqualTo(BinaryOp.Operator.EQ);
        assertThat(leftBinOp.left()).isInstanceOf(BiEntityFieldAccess.class);
        BiEntityFieldAccess leftField = (BiEntityFieldAccess) leftBinOp.left();
        assertThat(leftField.fieldName()).isEqualTo("active");
        assertThat(leftField.entityPosition()).isEqualTo(EntityPosition.FIRST);

        // Right: ph.isPrimary == true (SECOND entity)
        assertThat(binOp.right()).isInstanceOf(BinaryOp.class);
        BinaryOp rightBinOp = (BinaryOp) binOp.right();
        assertThat(rightBinOp.operator()).isEqualTo(BinaryOp.Operator.EQ);
        assertThat(rightBinOp.left()).isInstanceOf(BiEntityFieldAccess.class);
        BiEntityFieldAccess rightField = (BiEntityFieldAccess) rightBinOp.left();
        assertThat(rightField.fieldName()).isEqualTo("isPrimary");
        assertThat(rightField.entityPosition()).isEqualTo(EntityPosition.SECOND);
    }

    @Test
    void bothEntitiesComplex() {
        LambdaExpression expr = analyzeBiEntityLambda("bothEntitiesComplex");

        // Expected: p.age >= 30 && ph.type.equals("work")
        // Note: String.equals() is optimized to BinaryOp(EQ), returned as-is (predicates not wrapped)
        assertBinaryOp(expr, BinaryOp.Operator.AND);
        BinaryOp binOp = (BinaryOp) expr;

        // Left: p.age >= 30
        assertThat(binOp.left()).isInstanceOf(BinaryOp.class);
        BinaryOp leftBinOp = (BinaryOp) binOp.left();
        assertThat(leftBinOp.operator()).isEqualTo(BinaryOp.Operator.GE);
        assertThat(leftBinOp.left()).isInstanceOf(BiEntityFieldAccess.class);
        BiEntityFieldAccess ageField = (BiEntityFieldAccess) leftBinOp.left();
        assertThat(ageField.fieldName()).isEqualTo("age");
        assertThat(ageField.entityPosition()).isEqualTo(EntityPosition.FIRST);

        // Right: ph.type.equals("work") - optimized to BinaryOp(field, EQ, "work")
        // Predicates (BinaryOp) are NOT wrapped with == true
        assertThat(binOp.right()).isInstanceOf(BinaryOp.class);
        BinaryOp rightBinOp = (BinaryOp) binOp.right();
        assertThat(rightBinOp.operator()).isEqualTo(BinaryOp.Operator.EQ);
        assertThat(rightBinOp.left()).isInstanceOf(BiEntityFieldAccess.class);
        BiEntityFieldAccess typeField = (BiEntityFieldAccess) rightBinOp.left();
        assertThat(typeField.fieldName()).isEqualTo("type");
        assertThat(typeField.entityPosition()).isEqualTo(EntityPosition.SECOND);
        assertConstant(rightBinOp.right(), "work");
    }

    @Test
    void bothEntitiesWithOr() {
        LambdaExpression expr = analyzeBiEntityLambda("bothEntitiesWithOr");

        // Expected: p.age > 50 || ph.type.equals("mobile")
        // Note: String.equals() is optimized to BinaryOp(EQ), returned as-is (predicates not wrapped)
        assertBinaryOp(expr, BinaryOp.Operator.OR);
        BinaryOp binOp = (BinaryOp) expr;

        // Left: p.age > 50 (FIRST entity)
        assertThat(binOp.left()).isInstanceOf(BinaryOp.class);
        BinaryOp leftBinOp = (BinaryOp) binOp.left();
        assertThat(leftBinOp.operator()).isEqualTo(BinaryOp.Operator.GT);
        assertThat(leftBinOp.left()).isInstanceOf(BiEntityFieldAccess.class);
        BiEntityFieldAccess ageField = (BiEntityFieldAccess) leftBinOp.left();
        assertThat(ageField.entityPosition()).isEqualTo(EntityPosition.FIRST);

        // Right: ph.type.equals("mobile") - optimized to BinaryOp(field, EQ, "mobile")
        // Predicates (BinaryOp) are NOT wrapped with == true
        assertThat(binOp.right()).isInstanceOf(BinaryOp.class);
        BinaryOp rightBinOp = (BinaryOp) binOp.right();
        assertThat(rightBinOp.operator()).isEqualTo(BinaryOp.Operator.EQ);
        assertThat(rightBinOp.left()).isInstanceOf(BiEntityFieldAccess.class);
        BiEntityFieldAccess typeField = (BiEntityFieldAccess) rightBinOp.left();
        assertThat(typeField.fieldName()).isEqualTo("type");
        assertThat(typeField.entityPosition()).isEqualTo(EntityPosition.SECOND);
        assertConstant(rightBinOp.right(), "mobile");
    }

    // ==================== STRING METHODS ON JOINED ENTITY ====================

    @Test
    void joinedEntityStartsWith() {
        LambdaExpression expr = analyzeBiEntityLambda("joinedEntityStartsWith");

        // Expected: ph.number.startsWith("555")
        assertMethodCall(expr, "startsWith");
        MethodCall methodCall = (MethodCall) expr;

        assertThat(methodCall.target()).isInstanceOf(BiEntityFieldAccess.class);
        BiEntityFieldAccess fieldAccess = (BiEntityFieldAccess) methodCall.target();
        assertThat(fieldAccess.fieldName()).isEqualTo("number");
        assertThat(fieldAccess.entityPosition()).isEqualTo(EntityPosition.SECOND);

        assertThat(methodCall.arguments()).hasSize(1);
        assertConstant(methodCall.arguments().get(0), "555");
    }

    @Test
    void joinedEntityContains() {
        LambdaExpression expr = analyzeBiEntityLambda("joinedEntityContains");

        // Expected: ph.number.contains("01")
        assertMethodCall(expr, "contains");
        MethodCall methodCall = (MethodCall) expr;

        assertThat(methodCall.target()).isInstanceOf(BiEntityFieldAccess.class);
        BiEntityFieldAccess fieldAccess = (BiEntityFieldAccess) methodCall.target();
        assertThat(fieldAccess.fieldName()).isEqualTo("number");
        assertThat(fieldAccess.entityPosition()).isEqualTo(EntityPosition.SECOND);
    }

    @Test
    void joinedEntityEndsWith() {
        LambdaExpression expr = analyzeBiEntityLambda("joinedEntityEndsWith");

        // Expected: ph.number.endsWith("00")
        assertMethodCall(expr, "endsWith");
        MethodCall methodCall = (MethodCall) expr;

        assertThat(methodCall.target()).isInstanceOf(BiEntityFieldAccess.class);
        BiEntityFieldAccess fieldAccess = (BiEntityFieldAccess) methodCall.target();
        assertThat(fieldAccess.fieldName()).isEqualTo("number");
        assertThat(fieldAccess.entityPosition()).isEqualTo(EntityPosition.SECOND);
    }

    // ==================== PROJECTIONS (SELECT) ====================

    @Test
    void projectJoinedEntityField() {
        LambdaExpression expr = analyzeBiEntityLambda("projectJoinedEntityField");

        // Expected: ph.number (BiEntityFieldAccess on SECOND)
        assertThat(expr).isInstanceOf(BiEntityFieldAccess.class);
        BiEntityFieldAccess fieldAccess = (BiEntityFieldAccess) expr;
        assertThat(fieldAccess.fieldName()).isEqualTo("number");
        assertThat(fieldAccess.entityPosition()).isEqualTo(EntityPosition.SECOND);
    }

    @Test
    void projectSourceEntityField() {
        LambdaExpression expr = analyzeBiEntityLambda("projectSourceEntityField");

        // Expected: p.firstName (BiEntityFieldAccess on FIRST)
        assertThat(expr).isInstanceOf(BiEntityFieldAccess.class);
        BiEntityFieldAccess fieldAccess = (BiEntityFieldAccess) expr;
        assertThat(fieldAccess.fieldName()).isEqualTo("firstName");
        assertThat(fieldAccess.entityPosition()).isEqualTo(EntityPosition.FIRST);
    }

    @Test
    void projectSourceEntityIntField() {
        LambdaExpression expr = analyzeBiEntityLambda("projectSourceEntityIntField");

        // Expected: p.age (BiEntityFieldAccess on FIRST)
        assertThat(expr).isInstanceOf(BiEntityFieldAccess.class);
        BiEntityFieldAccess fieldAccess = (BiEntityFieldAccess) expr;
        assertThat(fieldAccess.fieldName()).isEqualTo("age");
        assertThat(fieldAccess.entityPosition()).isEqualTo(EntityPosition.FIRST);
    }

    // ==================== CAPTURED VARIABLES ====================

    @Test
    void joinedEntityWithCapturedVariable() {
        LambdaExpression expr = analyzeBiEntityLambda("joinedEntityWithCapturedVariable");

        // Expected: ph.type.equals(phoneType) where phoneType is captured
        // Note: String.equals() is optimized to BinaryOp(EQ) even with captured variables
        assertBinaryOp(expr, BinaryOp.Operator.EQ);
        BinaryOp binOp = (BinaryOp) expr;

        // Left should be BiEntityFieldAccess on SECOND entity (ph.type)
        assertThat(binOp.left()).isInstanceOf(BiEntityFieldAccess.class);
        BiEntityFieldAccess fieldAccess = (BiEntityFieldAccess) binOp.left();
        assertThat(fieldAccess.fieldName()).isEqualTo("type");
        assertThat(fieldAccess.entityPosition()).isEqualTo(EntityPosition.SECOND);

        // Right should be captured variable (phoneType)
        assertCapturedVariable(binOp.right(), 0);
    }

    @Test
    void bothEntitiesWithCapturedVariable() {
        LambdaExpression expr = analyzeBiEntityLambda("bothEntitiesWithCapturedVariable");

        // Expected: p.age > minAge && ph.type.equals(targetType)
        // where minAge and targetType are captured variables
        // Note: String.equals() is optimized to BinaryOp(EQ), returned as-is (predicates not wrapped)
        assertBinaryOp(expr, BinaryOp.Operator.AND);
        BinaryOp binOp = (BinaryOp) expr;

        // Left: p.age > minAge (captured variable index 0)
        assertThat(binOp.left()).isInstanceOf(BinaryOp.class);
        BinaryOp leftBinOp = (BinaryOp) binOp.left();
        assertCapturedVariable(leftBinOp.right(), 0);

        // Right: ph.type.equals(targetType) - optimized to BinaryOp(field, EQ, capturedVar)
        // Predicates (BinaryOp) are NOT wrapped with == true
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
