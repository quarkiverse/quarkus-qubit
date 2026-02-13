package io.quarkiverse.qubit.deployment.analysis;

import static io.quarkiverse.qubit.deployment.testutil.AstBuilders.*;
import static org.assertj.core.api.Assertions.*;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import io.quarkiverse.qubit.deployment.ast.LambdaExpression;
import io.quarkiverse.qubit.deployment.ast.LambdaExpression.*;
import io.quarkiverse.qubit.deployment.testutil.AstArbitraries;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;

/**
 * Property-based tests for AST transformation operations.
 *
 * <p>
 * This class uses jqwik to verify invariants that must hold for ANY valid input,
 * not just specific examples. Property-based testing complements example-based tests
 * by generating many random inputs to find edge cases.
 *
 * <p>
 * <strong>Properties Tested:</strong>
 * <ul>
 * <li><strong>renumberCapturedVariables</strong>: Identity, null safety, offset accumulation,
 * structure preservation, index transformation</li>
 * <li><strong>collectCapturedVariableIndices</strong>: Null safety, determinism,
 * captured variable detection, leaf type behavior</li>
 * <li><strong>combinePredicatesWithAnd</strong>: Single element identity, structure,
 * empty list exception</li>
 * </ul>
 *
 * @see CapturedVariableHelper
 * @see AstArbitraries
 */
class AstTransformationPropertyTest {

    // renumberCapturedVariables Properties

    @Property
    @Label("renumberCapturedVariables: zero offset returns same expression")
    void renumberWithZeroOffsetIsIdentity(
            @ForAll("expressionsWithCapturedVariables") LambdaExpression expr) {
        // Property: renumber(expr, 0) == expr
        LambdaExpression result = CapturedVariableHelper.renumberCapturedVariables(expr, 0);

        assertThat(result)
                .as("Zero offset should return the same expression")
                .isSameAs(expr);
    }

    @Property
    @Label("renumberCapturedVariables: null input returns null")
    void renumberNullReturnsNull(@ForAll @IntRange(min = 0, max = 100) int offset) {
        // Property: renumber(null, n) == null
        LambdaExpression result = CapturedVariableHelper.renumberCapturedVariables(null, offset);

        assertThat(result)
                .as("Null input should return null")
                .isNull();
    }

    @Property
    @Label("renumberCapturedVariables: CapturedVariable index increases by offset")
    void renumberCapturedVariableIncreasesIndex(
            @ForAll("capturedVariables") CapturedVariable capturedVar,
            @ForAll @IntRange(min = 1, max = 50) int offset) {
        // Property: renumber(CapturedVariable(n), offset).index() == n + offset
        LambdaExpression result = CapturedVariableHelper.renumberCapturedVariables(capturedVar, offset);

        assertThat(result)
                .as("Result should be a CapturedVariable")
                .isInstanceOf(CapturedVariable.class);

        CapturedVariable resultVar = (CapturedVariable) result;
        assertThat(resultVar.index())
                .as("Index should be increased by offset")
                .isEqualTo(capturedVar.index() + offset);
        assertThat(resultVar.type())
                .as("Type should be preserved")
                .isEqualTo(capturedVar.type());
    }

    @Property
    @Label("renumberCapturedVariables: leaf expressions without CapturedVariable unchanged")
    void renumberLeafWithoutCapturedVariableUnchanged(
            @ForAll("fieldAccesses") FieldAccess field,
            @ForAll @IntRange(min = 1, max = 100) int offset) {
        // Property: renumber(FieldAccess, offset) == FieldAccess (same instance)
        LambdaExpression result = CapturedVariableHelper.renumberCapturedVariables(field, offset);

        assertThat(result)
                .as("FieldAccess should be returned unchanged")
                .isSameAs(field);
    }

    @Property
    @Label("renumberCapturedVariables: Constant expressions unchanged")
    void renumberConstantUnchanged(
            @ForAll("constants") Constant constant,
            @ForAll @IntRange(min = 1, max = 100) int offset) {
        // Property: renumber(Constant, offset) == Constant (same instance)
        LambdaExpression result = CapturedVariableHelper.renumberCapturedVariables(constant, offset);

        assertThat(result)
                .as("Constant should be returned unchanged")
                .isSameAs(constant);
    }

    @Property
    @Label("renumberCapturedVariables: preserves BinaryOp operator")
    void renumberPreservesBinaryOperator(
            @ForAll("shallowBinaryOps") BinaryOp binOp,
            @ForAll @IntRange(min = 1, max = 50) int offset) {
        // Property: renumber preserves the operator in BinaryOp
        LambdaExpression result = CapturedVariableHelper.renumberCapturedVariables(binOp, offset);

        assertThat(result)
                .as("Result should be a BinaryOp")
                .isInstanceOf(BinaryOp.class);

        BinaryOp resultOp = (BinaryOp) result;
        assertThat(resultOp.operator())
                .as("Operator should be preserved")
                .isEqualTo(binOp.operator());
    }

    @Property
    @Label("renumberCapturedVariables: offset accumulation property")
    void renumberOffsetAccumulation(
            @ForAll("capturedVariables") CapturedVariable capturedVar,
            @ForAll @IntRange(min = 1, max = 25) int offset1,
            @ForAll @IntRange(min = 1, max = 25) int offset2) {
        // Property: renumber(renumber(expr, a), b).index() == expr.index() + a + b
        LambdaExpression afterFirst = CapturedVariableHelper.renumberCapturedVariables(capturedVar, offset1);
        LambdaExpression afterBoth = CapturedVariableHelper.renumberCapturedVariables(afterFirst, offset2);

        assertThat(afterBoth)
                .as("Result should be a CapturedVariable")
                .isInstanceOf(CapturedVariable.class);

        CapturedVariable resultVar = (CapturedVariable) afterBoth;
        assertThat(resultVar.index())
                .as("Accumulated offsets should equal sum of individual offsets")
                .isEqualTo(capturedVar.index() + offset1 + offset2);
    }

    @Property
    @Label("renumberCapturedVariables: all captured variables in tree are renumbered")
    void renumberAllCapturedVariablesInTree(
            @ForAll("expressionsWithCapturedVariables") LambdaExpression expr,
            @ForAll @IntRange(min = 1, max = 20) int offset) {
        // Property: all indices in result = all indices in original + offset
        Set<Integer> originalIndices = new HashSet<>();
        CapturedVariableHelper.collectCapturedVariableIndices(expr, originalIndices);

        LambdaExpression result = CapturedVariableHelper.renumberCapturedVariables(expr, offset);

        Set<Integer> resultIndices = new HashSet<>();
        CapturedVariableHelper.collectCapturedVariableIndices(result, resultIndices);

        // Each original index + offset should appear in result
        Set<Integer> expectedIndices = new HashSet<>();
        for (Integer idx : originalIndices) {
            expectedIndices.add(idx + offset);
        }

        assertThat(resultIndices)
                .as("Result indices should be original indices shifted by offset")
                .isEqualTo(expectedIndices);
    }

    // collectCapturedVariableIndices Properties

    @Property
    @Label("collectCapturedVariableIndices: null input leaves set unchanged")
    void collectFromNullLeavesSetUnchanged() {
        // Property: collect(null, set) does not modify set
        Set<Integer> indices = new HashSet<>();
        indices.add(42); // Pre-existing value

        CapturedVariableHelper.collectCapturedVariableIndices(null, indices);

        assertThat(indices)
                .as("Set should be unchanged after null input")
                .containsExactly(42);
    }

    @Property
    @Label("collectCapturedVariableIndices: CapturedVariable adds its index")
    void collectFromCapturedVariableAddsIndex(
            @ForAll("capturedVariables") CapturedVariable capturedVar) {
        // Property: collect(CapturedVariable(n), set) adds n to set
        Set<Integer> indices = new HashSet<>();

        CapturedVariableHelper.collectCapturedVariableIndices(capturedVar, indices);

        assertThat(indices)
                .as("Set should contain the captured variable index")
                .containsExactly(capturedVar.index());
    }

    @Property
    @Label("collectCapturedVariableIndices: FieldAccess adds nothing")
    void collectFromFieldAccessAddsNothing(
            @ForAll("fieldAccesses") FieldAccess field) {
        // Property: collect(FieldAccess, set) leaves set empty
        Set<Integer> indices = new HashSet<>();

        CapturedVariableHelper.collectCapturedVariableIndices(field, indices);

        assertThat(indices)
                .as("FieldAccess should not add any indices")
                .isEmpty();
    }

    @Property
    @Label("collectCapturedVariableIndices: Constant adds nothing")
    void collectFromConstantAddsNothing(
            @ForAll("constants") Constant constant) {
        // Property: collect(Constant, set) leaves set empty
        Set<Integer> indices = new HashSet<>();

        CapturedVariableHelper.collectCapturedVariableIndices(constant, indices);

        assertThat(indices)
                .as("Constant should not add any indices")
                .isEmpty();
    }

    @Property
    @Label("collectCapturedVariableIndices: deterministic results")
    void collectIsDeterministic(
            @ForAll("expressionsWithCapturedVariables") LambdaExpression expr) {
        // Property: collecting twice gives same result
        Set<Integer> indices1 = new HashSet<>();
        Set<Integer> indices2 = new HashSet<>();

        CapturedVariableHelper.collectCapturedVariableIndices(expr, indices1);
        CapturedVariableHelper.collectCapturedVariableIndices(expr, indices2);

        assertThat(indices1)
                .as("Multiple collections should produce identical results")
                .isEqualTo(indices2);
    }

    @Property
    @Label("collectCapturedVariableIndices: count matches set size")
    void collectCountMatchesSetSize(
            @ForAll("expressionsWithCapturedVariables") LambdaExpression expr) {
        // Property: countCapturedVariables(expr) == collect(expr, set).size()
        Set<Integer> indices = new HashSet<>();
        CapturedVariableHelper.collectCapturedVariableIndices(expr, indices);

        int count = CapturedVariableHelper.countCapturedVariables(expr);

        assertThat(count)
                .as("Count should match collected set size")
                .isEqualTo(indices.size());
    }

    // combinePredicatesWithAnd Properties

    @Property
    @Label("combinePredicatesWithAnd: single element returns itself")
    void combineSingleElementReturnsItself(
            @ForAll("predicateExpressions") LambdaExpression predicate) {
        // Property: combine([p]) == p
        List<LambdaExpression> singleList = List.of(predicate);

        LambdaExpression result = CapturedVariableHelper.combinePredicatesWithAnd(singleList);

        assertThat(result)
                .as("Single element list should return the element itself")
                .isSameAs(predicate);
    }

    @Property
    @Label("combinePredicatesWithAnd: two elements creates AND")
    void combineTwoElementsCreatesAnd(
            @ForAll("predicateExpressions") LambdaExpression p1,
            @ForAll("predicateExpressions") LambdaExpression p2) {
        // Property: combine([p1, p2]) == and(p1, p2)
        List<LambdaExpression> twoList = List.of(p1, p2);

        LambdaExpression result = CapturedVariableHelper.combinePredicatesWithAnd(twoList);

        assertThat(result)
                .as("Two element list should create BinaryOp")
                .isInstanceOf(BinaryOp.class);

        BinaryOp binOp = (BinaryOp) result;
        assertThat(binOp.operator())
                .as("Operator should be AND")
                .isEqualTo(BinaryOp.Operator.AND);
        assertThat(binOp.left())
                .as("Left operand should be first predicate")
                .isSameAs(p1);
        assertThat(binOp.right())
                .as("Right operand should be second predicate")
                .isSameAs(p2);
    }

    @Property
    @Label("combinePredicatesWithAnd: result contains all captured variables")
    void combinePreservesAllCapturedVariables(
            @ForAll("predicateLists") List<LambdaExpression> predicates) {
        // Property: captured vars in result = union of captured vars in inputs
        Set<Integer> expectedIndices = new HashSet<>();
        for (LambdaExpression pred : predicates) {
            CapturedVariableHelper.collectCapturedVariableIndices(pred, expectedIndices);
        }

        LambdaExpression result = CapturedVariableHelper.combinePredicatesWithAnd(predicates);

        Set<Integer> resultIndices = new HashSet<>();
        CapturedVariableHelper.collectCapturedVariableIndices(result, resultIndices);

        assertThat(resultIndices)
                .as("Combined result should contain all captured variables from inputs")
                .isEqualTo(expectedIndices);
    }

    @Property
    @Label("combinePredicatesWithAnd: empty list throws exception")
    void combineEmptyListThrowsException() {
        // Property: combine([]) throws IllegalArgumentException
        List<LambdaExpression> emptyList = List.of();

        assertThatThrownBy(() -> CapturedVariableHelper.combinePredicatesWithAnd(emptyList))
                .as("Empty list should throw IllegalArgumentException")
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("empty");
    }

    @Property
    @Label("combinePredicatesWithAnd(a, b): null handling")
    void combineTwoPredicatesNullHandling() {
        // Property: combine(null, null) == null
        LambdaExpression result1 = CapturedVariableHelper.combinePredicatesWithAnd(null, null);
        assertThat(result1).as("Both null should return null").isNull();

        // Property: combine(p, null) == p
        LambdaExpression p = field("name", String.class);
        LambdaExpression result2 = CapturedVariableHelper.combinePredicatesWithAnd(p, null);
        assertThat(result2).as("First non-null, second null should return first").isSameAs(p);

        // Property: combine(null, p) == p
        LambdaExpression result3 = CapturedVariableHelper.combinePredicatesWithAnd(null, p);
        assertThat(result3).as("First null, second non-null should return second").isSameAs(p);
    }

    // Arbitrary Providers

    @Provide
    Arbitrary<LambdaExpression> expressionsWithCapturedVariables() {
        return AstArbitraries.expressionsWithCapturedVariables();
    }

    @Provide
    Arbitrary<CapturedVariable> capturedVariables() {
        return AstArbitraries.capturedVariables();
    }

    @Provide
    Arbitrary<FieldAccess> fieldAccesses() {
        return AstArbitraries.fieldAccesses();
    }

    @Provide
    Arbitrary<Constant> constants() {
        return AstArbitraries.constants();
    }

    @Provide
    Arbitrary<BinaryOp> shallowBinaryOps() {
        return AstArbitraries.shallowBinaryOps();
    }

    @Provide
    Arbitrary<LambdaExpression> predicateExpressions() {
        return AstArbitraries.predicateExpressions();
    }

    @Provide
    Arbitrary<List<LambdaExpression>> predicateLists() {
        return AstArbitraries.predicateLists();
    }
}
