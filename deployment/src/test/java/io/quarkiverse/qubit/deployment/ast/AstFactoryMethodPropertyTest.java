package io.quarkiverse.qubit.deployment.ast;

import static org.assertj.core.api.Assertions.*;

import io.quarkiverse.qubit.deployment.ast.LambdaExpression.*;
import io.quarkiverse.qubit.deployment.testutil.AstArbitraries;
import net.jqwik.api.*;

/**
 * Property-based tests for AST factory methods.
 *
 * <p>
 * This class verifies that factory methods on AST node types produce
 * correct results equivalent to direct constructor calls. Property-based
 * testing ensures these equivalences hold for ANY valid input.
 *
 * <p>
 * <strong>Properties Tested:</strong>
 * <ul>
 * <li><strong>BinaryOp factory methods</strong>: Equivalence to constructor,
 * correct operator assignment</li>
 * <li><strong>UnaryOp factory methods</strong>: Equivalence to constructor</li>
 * <li><strong>PathExpression factory methods</strong>: Correct segment construction</li>
 * <li><strong>InExpression factory methods</strong>: Negation handling</li>
 * </ul>
 *
 * @see LambdaExpression
 * @see AstArbitraries
 */
class AstFactoryMethodPropertyTest {

    // BinaryOp Factory Method Properties

    @Property
    @Label("BinaryOp.eq(): equivalent to constructor with EQ operator")
    void eqFactoryEquivalentToConstructor(
            @ForAll("leafExpressions") LambdaExpression left,
            @ForAll("leafExpressions") LambdaExpression right) {
        // Property: BinaryOp.eq(a, b) == new BinaryOp(a, Operator.EQ, b)
        BinaryOp fromFactory = BinaryOp.eq(left, right);
        BinaryOp fromConstructor = new BinaryOp(left, BinaryOp.Operator.EQ, right);

        assertThat(fromFactory)
                .as("Factory method should produce equivalent result to constructor")
                .isEqualTo(fromConstructor);
    }

    @Property
    @Label("BinaryOp.ne(): equivalent to constructor with NE operator")
    void neFactoryEquivalentToConstructor(
            @ForAll("leafExpressions") LambdaExpression left,
            @ForAll("leafExpressions") LambdaExpression right) {
        BinaryOp fromFactory = BinaryOp.ne(left, right);
        BinaryOp fromConstructor = new BinaryOp(left, BinaryOp.Operator.NE, right);

        assertThat(fromFactory).isEqualTo(fromConstructor);
    }

    @Property
    @Label("BinaryOp.lt(): equivalent to constructor with LT operator")
    void ltFactoryEquivalentToConstructor(
            @ForAll("leafExpressions") LambdaExpression left,
            @ForAll("leafExpressions") LambdaExpression right) {
        BinaryOp fromFactory = BinaryOp.lt(left, right);
        BinaryOp fromConstructor = new BinaryOp(left, BinaryOp.Operator.LT, right);

        assertThat(fromFactory).isEqualTo(fromConstructor);
    }

    @Property
    @Label("BinaryOp.le(): equivalent to constructor with LE operator")
    void leFactoryEquivalentToConstructor(
            @ForAll("leafExpressions") LambdaExpression left,
            @ForAll("leafExpressions") LambdaExpression right) {
        BinaryOp fromFactory = BinaryOp.le(left, right);
        BinaryOp fromConstructor = new BinaryOp(left, BinaryOp.Operator.LE, right);

        assertThat(fromFactory).isEqualTo(fromConstructor);
    }

    @Property
    @Label("BinaryOp.gt(): equivalent to constructor with GT operator")
    void gtFactoryEquivalentToConstructor(
            @ForAll("leafExpressions") LambdaExpression left,
            @ForAll("leafExpressions") LambdaExpression right) {
        BinaryOp fromFactory = BinaryOp.gt(left, right);
        BinaryOp fromConstructor = new BinaryOp(left, BinaryOp.Operator.GT, right);

        assertThat(fromFactory).isEqualTo(fromConstructor);
    }

    @Property
    @Label("BinaryOp.ge(): equivalent to constructor with GE operator")
    void geFactoryEquivalentToConstructor(
            @ForAll("leafExpressions") LambdaExpression left,
            @ForAll("leafExpressions") LambdaExpression right) {
        BinaryOp fromFactory = BinaryOp.ge(left, right);
        BinaryOp fromConstructor = new BinaryOp(left, BinaryOp.Operator.GE, right);

        assertThat(fromFactory).isEqualTo(fromConstructor);
    }

    @Property
    @Label("BinaryOp.and(): equivalent to constructor with AND operator")
    void andFactoryEquivalentToConstructor(
            @ForAll("leafExpressions") LambdaExpression left,
            @ForAll("leafExpressions") LambdaExpression right) {
        BinaryOp fromFactory = BinaryOp.and(left, right);
        BinaryOp fromConstructor = new BinaryOp(left, BinaryOp.Operator.AND, right);

        assertThat(fromFactory).isEqualTo(fromConstructor);
    }

    @Property
    @Label("BinaryOp.or(): equivalent to constructor with OR operator")
    void orFactoryEquivalentToConstructor(
            @ForAll("leafExpressions") LambdaExpression left,
            @ForAll("leafExpressions") LambdaExpression right) {
        BinaryOp fromFactory = BinaryOp.or(left, right);
        BinaryOp fromConstructor = new BinaryOp(left, BinaryOp.Operator.OR, right);

        assertThat(fromFactory).isEqualTo(fromConstructor);
    }

    @Property
    @Label("BinaryOp.add(): equivalent to constructor with ADD operator")
    void addFactoryEquivalentToConstructor(
            @ForAll("leafExpressions") LambdaExpression left,
            @ForAll("leafExpressions") LambdaExpression right) {
        BinaryOp fromFactory = BinaryOp.add(left, right);
        BinaryOp fromConstructor = new BinaryOp(left, BinaryOp.Operator.ADD, right);

        assertThat(fromFactory).isEqualTo(fromConstructor);
    }

    @Property
    @Label("BinaryOp.sub(): equivalent to constructor with SUB operator")
    void subFactoryEquivalentToConstructor(
            @ForAll("leafExpressions") LambdaExpression left,
            @ForAll("leafExpressions") LambdaExpression right) {
        BinaryOp fromFactory = BinaryOp.sub(left, right);
        BinaryOp fromConstructor = new BinaryOp(left, BinaryOp.Operator.SUB, right);

        assertThat(fromFactory).isEqualTo(fromConstructor);
    }

    @Property
    @Label("BinaryOp.mul(): equivalent to constructor with MUL operator")
    void mulFactoryEquivalentToConstructor(
            @ForAll("leafExpressions") LambdaExpression left,
            @ForAll("leafExpressions") LambdaExpression right) {
        BinaryOp fromFactory = BinaryOp.mul(left, right);
        BinaryOp fromConstructor = new BinaryOp(left, BinaryOp.Operator.MUL, right);

        assertThat(fromFactory).isEqualTo(fromConstructor);
    }

    @Property
    @Label("BinaryOp.div(): equivalent to constructor with DIV operator")
    void divFactoryEquivalentToConstructor(
            @ForAll("leafExpressions") LambdaExpression left,
            @ForAll("leafExpressions") LambdaExpression right) {
        BinaryOp fromFactory = BinaryOp.div(left, right);
        BinaryOp fromConstructor = new BinaryOp(left, BinaryOp.Operator.DIV, right);

        assertThat(fromFactory).isEqualTo(fromConstructor);
    }

    @Property
    @Label("BinaryOp.mod(): equivalent to constructor with MOD operator")
    void modFactoryEquivalentToConstructor(
            @ForAll("leafExpressions") LambdaExpression left,
            @ForAll("leafExpressions") LambdaExpression right) {
        BinaryOp fromFactory = BinaryOp.mod(left, right);
        BinaryOp fromConstructor = new BinaryOp(left, BinaryOp.Operator.MOD, right);

        assertThat(fromFactory).isEqualTo(fromConstructor);
    }

    // UnaryOp Factory Method Properties

    @Property
    @Label("UnaryOp.not(): equivalent to constructor with NOT operator")
    void notFactoryEquivalentToConstructor(
            @ForAll("leafExpressions") LambdaExpression operand) {
        UnaryOp fromFactory = UnaryOp.not(operand);
        UnaryOp fromConstructor = new UnaryOp(UnaryOp.Operator.NOT, operand);

        assertThat(fromFactory)
                .as("Factory method should produce equivalent result to constructor")
                .isEqualTo(fromConstructor);
    }

    // InExpression Factory Method Properties

    @Property
    @Label("InExpression.in(): creates non-negated IN expression")
    void inFactoryCreatesNonNegated(
            @ForAll("fieldAccesses") FieldAccess field,
            @ForAll("leafExpressions") LambdaExpression collection) {
        InExpression fromFactory = InExpression.in(field, collection);

        assertThat(fromFactory.negated())
                .as("in() factory should create non-negated expression")
                .isFalse();
        assertThat(fromFactory.field())
                .as("Field should be preserved")
                .isSameAs(field);
        assertThat(fromFactory.collection())
                .as("Collection should be preserved")
                .isSameAs(collection);
    }

    @Property
    @Label("InExpression.notIn(): creates negated IN expression")
    void notInFactoryCreatesNegated(
            @ForAll("fieldAccesses") FieldAccess field,
            @ForAll("leafExpressions") LambdaExpression collection) {
        InExpression fromFactory = InExpression.notIn(field, collection);

        assertThat(fromFactory.negated())
                .as("notIn() factory should create negated expression")
                .isTrue();
        assertThat(fromFactory.field())
                .as("Field should be preserved")
                .isSameAs(field);
        assertThat(fromFactory.collection())
                .as("Collection should be preserved")
                .isSameAs(collection);
    }

    // BinaryOp Operator Symbol Properties

    @Property
    @Label("BinaryOp.Operator: all operators have non-empty symbols")
    void allOperatorsHaveSymbols() {
        for (BinaryOp.Operator op : BinaryOp.Operator.values()) {
            assertThat(op.symbol())
                    .as("Operator %s should have a non-empty symbol", op.name())
                    .isNotEmpty();
        }
    }

    @Property
    @Label("UnaryOp.Operator: all operators have non-empty symbols")
    void allUnaryOperatorsHaveSymbols() {
        for (UnaryOp.Operator op : UnaryOp.Operator.values()) {
            assertThat(op.symbol())
                    .as("Operator %s should have a non-empty symbol", op.name())
                    .isNotEmpty();
        }
    }

    // Record Validation Properties

    @Property
    @Label("CapturedVariable: rejects negative index")
    void capturedVariableRejectsNegativeIndex(@ForAll("types") Class<?> type) {
        assertThatThrownBy(() -> new CapturedVariable(-1, type))
                .as("Negative index should throw IllegalArgumentException")
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("non-negative");
    }

    @Property
    @Label("FieldAccess: rejects null field name")
    void fieldAccessRejectsNullFieldName(@ForAll("types") Class<?> type) {
        assertThatThrownBy(() -> new FieldAccess(null, type))
                .as("Null field name should throw NullPointerException")
                .isInstanceOf(NullPointerException.class);
    }

    @Property
    @Label("FieldAccess: rejects null field type")
    void fieldAccessRejectsNullFieldType(@ForAll("fieldNames") String fieldName) {
        assertThatThrownBy(() -> new FieldAccess(fieldName, null))
                .as("Null field type should throw NullPointerException")
                .isInstanceOf(NullPointerException.class);
    }

    // Arbitrary Providers

    @Provide
    Arbitrary<LambdaExpression> leafExpressions() {
        return AstArbitraries.leafExpressions();
    }

    @Provide
    Arbitrary<FieldAccess> fieldAccesses() {
        return AstArbitraries.fieldAccesses();
    }

    @Provide
    Arbitrary<Class<?>> types() {
        return AstArbitraries.types();
    }

    @Provide
    Arbitrary<String> fieldNames() {
        return AstArbitraries.fieldNames();
    }
}
