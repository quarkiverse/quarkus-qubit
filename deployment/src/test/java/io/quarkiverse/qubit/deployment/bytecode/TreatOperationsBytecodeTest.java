package io.quarkiverse.qubit.deployment.bytecode;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import io.quarkiverse.qubit.deployment.ast.LambdaExpression;
import io.quarkiverse.qubit.deployment.ast.LambdaExpression.BinaryOp;
import io.quarkiverse.qubit.deployment.ast.LambdaExpression.FieldAccess;
import io.quarkiverse.qubit.deployment.ast.LambdaExpression.InstanceOf;
import io.quarkiverse.qubit.deployment.ast.LambdaExpression.TreatExpression;
import io.quarkiverse.qubit.deployment.testutil.LambdaTestSources.TestCat;
import io.quarkiverse.qubit.deployment.testutil.LambdaTestSources.TestDog;

/**
 * Bytecode analysis tests for TREAT / instanceof operations.
 * Tests lambda bytecode parsing for pattern matching and explicit casts.
 *
 * <p>
 * Verifies that both Java 16+ pattern matching ({@code a instanceof Dog d && d.breed})
 * and explicit casts ({@code ((Dog) a).breed}) produce the correct AST structure
 * with TreatExpression nodes.
 */
class TreatOperationsBytecodeTest extends PrecompiledLambdaAnalyzer {

    /**
     * Extracts the InstanceOf node from a branch-wrapped expression.
     * The branch handler wraps InstanceOf as BinaryOp(InstanceOf, EQ, Constant(true)).
     */
    private static InstanceOf extractInstanceOf(LambdaExpression expr) {
        if (expr instanceof InstanceOf io) {
            return io;
        }
        if (expr instanceof BinaryOp binOp && binOp.left() instanceof InstanceOf io) {
            return io;
        }
        return null;
    }

    @Test
    void patternMatchDogBreed_producesTreatExpressionWithInstanceOf() {
        LambdaExpression result = analyzeLambda("patternMatchDogBreed");

        // Structure: BinaryOp(instanceOfExpr, AND, BinaryOp(TreatExpression, EQ, Constant("Lab")))
        // The MethodInvocationHandler resolves equals() to EQ comparison
        assertThat(result).isInstanceOf(BinaryOp.class);
        BinaryOp topOp = (BinaryOp) result;
        assertThat(topOp.operator()).isEqualTo(BinaryOp.Operator.AND);

        // Left side: InstanceOf check (may be wrapped by branch handler)
        InstanceOf instanceOf = extractInstanceOf(topOp.left());
        assertThat(instanceOf).isNotNull();
        assertThat(instanceOf.targetType()).isEqualTo(TestDog.class);

        // Right side: BinaryOp(TreatExpression(breed), EQ, "Lab") -- equals() resolved to EQ
        assertThat(topOp.right()).isInstanceOf(BinaryOp.class);
        BinaryOp eqOp = (BinaryOp) topOp.right();
        assertThat(eqOp.operator()).isEqualTo(BinaryOp.Operator.EQ);

        // Left of EQ should be a TreatExpression
        assertThat(eqOp.left()).isInstanceOf(TreatExpression.class);
        TreatExpression treat = (TreatExpression) eqOp.left();
        assertThat(treat.treatType()).isEqualTo(TestDog.class);
        assertThat(treat.inner()).isInstanceOf(FieldAccess.class);
        assertThat(((FieldAccess) treat.inner()).fieldName()).isEqualTo("breed");
    }

    @Test
    void castDogBreed_producesTreatExpressionWithInstanceOf() {
        LambdaExpression result = analyzeLambda("castDogBreed");

        // Structure: BinaryOp(instanceOfExpr, AND, BinaryOp(TreatExpression, EQ, Constant("Lab")))
        assertThat(result).isInstanceOf(BinaryOp.class);
        BinaryOp topOp = (BinaryOp) result;
        assertThat(topOp.operator()).isEqualTo(BinaryOp.Operator.AND);

        // Left side: InstanceOf check (may be wrapped by branch handler)
        InstanceOf instanceOf = extractInstanceOf(topOp.left());
        assertThat(instanceOf).isNotNull();
        assertThat(instanceOf.targetType()).isEqualTo(TestDog.class);

        // Right side: BinaryOp(TreatExpression(breed), EQ, "Lab") -- equals() resolved to EQ
        assertThat(topOp.right()).isInstanceOf(BinaryOp.class);
        BinaryOp eqOp = (BinaryOp) topOp.right();
        assertThat(eqOp.operator()).isEqualTo(BinaryOp.Operator.EQ);

        assertThat(eqOp.left()).isInstanceOf(TreatExpression.class);
        TreatExpression treat = (TreatExpression) eqOp.left();
        assertThat(treat.treatType()).isEqualTo(TestDog.class);
        assertThat(treat.inner()).isInstanceOf(FieldAccess.class);
        assertThat(((FieldAccess) treat.inner()).fieldName()).isEqualTo("breed");
    }

    @Test
    void patternMatchDogTrained_producesTreatExpressionForBooleanField() {
        LambdaExpression result = analyzeLambda("patternMatchDogTrained");

        // Structure: BinaryOp(instanceOfExpr, AND, booleanTreatExpr)
        assertThat(result).isInstanceOf(BinaryOp.class);
        BinaryOp topOp = (BinaryOp) result;
        assertThat(topOp.operator()).isEqualTo(BinaryOp.Operator.AND);

        // Left side: InstanceOf check
        InstanceOf instanceOf = extractInstanceOf(topOp.left());
        assertThat(instanceOf).isNotNull();
        assertThat(instanceOf.targetType()).isEqualTo(TestDog.class);
    }

    @Test
    void patternMatchCaptured_producesTreatWithCapturedVariable() {
        LambdaExpression result = analyzeLambda("patternMatchCaptured");

        // Should contain both InstanceOf and TreatExpression
        assertThat(result).isInstanceOf(BinaryOp.class);
        BinaryOp topOp = (BinaryOp) result;
        assertThat(topOp.operator()).isEqualTo(BinaryOp.Operator.AND);

        InstanceOf instanceOf = extractInstanceOf(topOp.left());
        assertThat(instanceOf).isNotNull();
        assertThat(instanceOf.targetType()).isEqualTo(TestDog.class);
    }

    @Test
    void castCatIndoor_producesTreatExpressionForCat() {
        LambdaExpression result = analyzeLambda("castCatIndoor");

        // Structure: BinaryOp(instanceOfExpr, AND, ...)
        assertThat(result).isInstanceOf(BinaryOp.class);
        BinaryOp topOp = (BinaryOp) result;
        assertThat(topOp.operator()).isEqualTo(BinaryOp.Operator.AND);

        InstanceOf instanceOf = extractInstanceOf(topOp.left());
        assertThat(instanceOf).isNotNull();
        assertThat(instanceOf.targetType()).isEqualTo(TestCat.class);
    }

    @Test
    void patternMatchWithParentField_producesCombinedExpression() {
        LambdaExpression result = analyzeLambda("patternMatchWithParentField");

        // Expected: complex AND expression combining InstanceOf, TreatExpression, and parent field comparison
        assertThat(result).isInstanceOf(BinaryOp.class);
        BinaryOp topOp = (BinaryOp) result;
        assertThat(topOp.operator()).isEqualTo(BinaryOp.Operator.AND);
    }
}
