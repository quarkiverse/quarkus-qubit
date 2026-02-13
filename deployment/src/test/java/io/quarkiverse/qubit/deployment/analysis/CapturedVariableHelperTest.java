package io.quarkiverse.qubit.deployment.analysis;

import static io.quarkiverse.qubit.deployment.testutil.AstBuilders.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import io.quarkiverse.qubit.SortDirection;
import io.quarkiverse.qubit.deployment.ast.LambdaExpression;
import io.quarkiverse.qubit.deployment.ast.LambdaExpression.*;

/**
 * Tests for {@link CapturedVariableHelper}.
 *
 * <p>
 * Tests captured variable operations: counting, collecting indices,
 * renumbering, and predicate combination.
 */
class CapturedVariableHelperTest {

    @Nested
    class CountCapturedVariablesTests {

        @Test
        void countCapturedVariables_withNoCaptured_returnsZero() {
            LambdaExpression expr = field("name", String.class);

            int count = CapturedVariableHelper.countCapturedVariables(expr);

            assertThat(count)
                    .as("Field without captured variables")
                    .isZero();
        }

        @Test
        void countCapturedVariables_withOneCaptured_returnsOne() {
            LambdaExpression expr = captured(0, String.class);

            int count = CapturedVariableHelper.countCapturedVariables(expr);

            assertThat(count)
                    .as("Single captured variable")
                    .isEqualTo(1);
        }

        @Test
        void countCapturedVariables_withMultipleCaptured_returnsDistinctCount() {
            // (captured0 == captured1 && captured0 == captured2)
            LambdaExpression left = BinaryOp.eq(captured(0, String.class), captured(1, String.class));
            LambdaExpression right = BinaryOp.eq(captured(0, String.class), captured(2, String.class));
            LambdaExpression expr = BinaryOp.and(left, right);

            int count = CapturedVariableHelper.countCapturedVariables(expr);

            assertThat(count)
                    .as("Should count distinct captured variables (0, 1, 2)")
                    .isEqualTo(3);
        }

        @Test
        void countCapturedVariables_withDuplicateIndices_countsDistinct() {
            // captured0 == captured0 (same index twice)
            LambdaExpression expr = BinaryOp.eq(captured(0, String.class), captured(0, String.class));

            int count = CapturedVariableHelper.countCapturedVariables(expr);

            assertThat(count)
                    .as("Duplicate indices should count as one")
                    .isEqualTo(1);
        }

        @Test
        void countCapturedVariables_withNull_returnsZero() {
            int count = CapturedVariableHelper.countCapturedVariables(null);

            assertThat(count)
                    .as("Null expression should return zero")
                    .isZero();
        }

        @Test
        void countCapturedVariables_inMethodCall_countsAll() {
            // target.method(captured0, captured1)
            LambdaExpression target = field("name", String.class);
            LambdaExpression methodCall = new MethodCall(
                    target, "contains",
                    List.of(captured(0, String.class), captured(1, String.class)),
                    Boolean.class);

            int count = CapturedVariableHelper.countCapturedVariables(methodCall);

            assertThat(count)
                    .as("Should count captured variables in method call arguments")
                    .isEqualTo(2);
        }

        @Test
        void countCapturedVariables_inUnaryOp_countsOperand() {
            LambdaExpression unary = UnaryOp.not(captured(0, Boolean.class));

            int count = CapturedVariableHelper.countCapturedVariables(unary);

            assertThat(count)
                    .as("Should count captured variable in unary operand")
                    .isEqualTo(1);
        }

        @Test
        void countCapturedVariables_inConstructorCall_countsArguments() {
            LambdaExpression constructor = new ConstructorCall(
                    "com.example.Dto",
                    List.of(captured(0, String.class), field("id", Long.class), captured(1, Integer.class)),
                    Object.class);

            int count = CapturedVariableHelper.countCapturedVariables(constructor);

            assertThat(count)
                    .as("Should count captured variables in constructor arguments")
                    .isEqualTo(2);
        }

        @Test
        void countCapturedVariables_inInExpression_countsFieldAndCollection() {
            LambdaExpression inExpr = new InExpression(
                    captured(0, String.class),
                    captured(1, List.class),
                    false);

            int count = CapturedVariableHelper.countCapturedVariables(inExpr);

            assertThat(count)
                    .as("Should count captured variables in IN expression")
                    .isEqualTo(2);
        }

        @Test
        void countCapturedVariables_inMemberOfExpression_countsValueAndCollection() {
            LambdaExpression memberOf = new MemberOfExpression(
                    captured(0, String.class),
                    captured(1, List.class),
                    false);

            int count = CapturedVariableHelper.countCapturedVariables(memberOf);

            assertThat(count)
                    .as("Should count captured variables in MEMBER OF expression")
                    .isEqualTo(2);
        }
    }

    @Nested
    class CountCapturedVariablesInSortExpressionsTests {

        @Test
        void countCapturedVariablesInSortExpressions_withNull_returnsZero() {
            int count = CapturedVariableHelper.countCapturedVariablesInSortExpressions(null);

            assertThat(count)
                    .as("Null list should return zero")
                    .isZero();
        }

        @Test
        void countCapturedVariablesInSortExpressions_withEmptyList_returnsZero() {
            int count = CapturedVariableHelper.countCapturedVariablesInSortExpressions(List.of());

            assertThat(count)
                    .as("Empty list should return zero")
                    .isZero();
        }

        @Test
        void countCapturedVariablesInSortExpressions_withOneSortExpr_countsCorrectly() {
            LambdaAnalysisResult.SortExpression sort = new LambdaAnalysisResult.SortExpression(
                    captured(0, String.class), SortDirection.ASCENDING);

            int count = CapturedVariableHelper.countCapturedVariablesInSortExpressions(List.of(sort));

            assertThat(count)
                    .as("Should count captured variable in sort expression")
                    .isEqualTo(1);
        }

        @Test
        void countCapturedVariablesInSortExpressions_withMultipleSortExprs_sumsCorrectly() {
            LambdaAnalysisResult.SortExpression sort1 = new LambdaAnalysisResult.SortExpression(
                    captured(0, String.class), SortDirection.ASCENDING);
            LambdaAnalysisResult.SortExpression sort2 = new LambdaAnalysisResult.SortExpression(
                    captured(1, Integer.class), SortDirection.DESCENDING);

            int count = CapturedVariableHelper.countCapturedVariablesInSortExpressions(List.of(sort1, sort2));

            assertThat(count)
                    .as("Should sum captured variables across sort expressions")
                    .isEqualTo(2);
        }
    }

    @Nested
    class CollectCapturedVariableIndicesTests {

        @Test
        void collectCapturedVariableIndices_withNull_doesNotThrow() {
            Set<Integer> indices = new HashSet<>();

            CapturedVariableHelper.collectCapturedVariableIndices(null, indices);

            assertThat(indices)
                    .as("Null expression should not add any indices")
                    .isEmpty();
        }

        @Test
        void collectCapturedVariableIndices_withCapturedVariable_addsIndex() {
            Set<Integer> indices = new HashSet<>();

            CapturedVariableHelper.collectCapturedVariableIndices(captured(5, String.class), indices);

            assertThat(indices)
                    .as("Should collect captured variable index")
                    .containsExactly(5);
        }

        @Test
        void collectCapturedVariableIndices_withPathExpression_addsNothing() {
            Set<Integer> indices = new HashSet<>();
            PathExpression path = new PathExpression(
                    List.of(new PathSegment("entity", Object.class, RelationType.FIELD),
                            new PathSegment("name", String.class, RelationType.FIELD)),
                    String.class);

            CapturedVariableHelper.collectCapturedVariableIndices(path, indices);

            assertThat(indices)
                    .as("PathExpression should not contribute captured variables")
                    .isEmpty();
        }

        @Test
        void collectCapturedVariableIndices_withBiEntityFieldAccess_addsNothing() {
            Set<Integer> indices = new HashSet<>();
            BiEntityFieldAccess biEntity = new BiEntityFieldAccess("field", String.class, EntityPosition.FIRST);

            CapturedVariableHelper.collectCapturedVariableIndices(biEntity, indices);

            assertThat(indices)
                    .as("BiEntityFieldAccess should not contribute captured variables")
                    .isEmpty();
        }

        @Test
        void collectCapturedVariableIndices_withBiEntityPathExpression_addsNothing() {
            Set<Integer> indices = new HashSet<>();
            BiEntityPathExpression biPath = new BiEntityPathExpression(
                    List.of(new PathSegment("entity", Object.class, RelationType.FIELD)), String.class, EntityPosition.FIRST);

            CapturedVariableHelper.collectCapturedVariableIndices(biPath, indices);

            assertThat(indices)
                    .as("BiEntityPathExpression should not contribute captured variables")
                    .isEmpty();
        }

        @Test
        void collectCapturedVariableIndices_withBiEntityParameter_addsNothing() {
            Set<Integer> indices = new HashSet<>();
            BiEntityParameter biParam = new BiEntityParameter("e", Object.class, 0, EntityPosition.FIRST);

            CapturedVariableHelper.collectCapturedVariableIndices(biParam, indices);

            assertThat(indices)
                    .as("BiEntityParameter should not contribute captured variables")
                    .isEmpty();
        }

        @Test
        void collectCapturedVariableIndices_withDefault_addsNothing() {
            Set<Integer> indices = new HashSet<>();
            Constant constant = new Constant("test", String.class);

            CapturedVariableHelper.collectCapturedVariableIndices(constant, indices);

            assertThat(indices)
                    .as("Default case (Constant) should not contribute captured variables")
                    .isEmpty();
        }
    }

    @Nested
    class RenumberCapturedVariablesTests {

        @Test
        void renumberCapturedVariables_withNull_returnsNull() {
            LambdaExpression result = CapturedVariableHelper.renumberCapturedVariables(null, 5);

            assertThat(result)
                    .as("Null input should return null")
                    .isNull();
        }

        @Test
        void renumberCapturedVariables_withZeroOffset_returnsSameExpression() {
            LambdaExpression original = captured(3, String.class);

            LambdaExpression result = CapturedVariableHelper.renumberCapturedVariables(original, 0);

            assertThat(result)
                    .as("Zero offset should return same expression")
                    .isSameAs(original);
        }

        @Test
        void renumberCapturedVariables_withCapturedVariable_addsOffset() {
            CapturedVariable original = (CapturedVariable) captured(2, String.class);

            LambdaExpression result = CapturedVariableHelper.renumberCapturedVariables(original, 5);

            assertThat(result)
                    .as("Should return CapturedVariable with offset index")
                    .isInstanceOf(CapturedVariable.class);
            CapturedVariable renumbered = (CapturedVariable) result;
            assertThat(renumbered.index())
                    .as("Index should be 2 + 5 = 7")
                    .isEqualTo(7);
            assertThat(renumbered.type())
                    .as("Type should be preserved")
                    .isEqualTo(String.class);
        }

        @Test
        void renumberCapturedVariables_withBinaryOp_renumbersBothSides() {
            LambdaExpression original = BinaryOp.eq(captured(0, String.class), captured(1, String.class));

            LambdaExpression result = CapturedVariableHelper.renumberCapturedVariables(original, 10);

            assertThat(result).isInstanceOf(BinaryOp.class);
            BinaryOp binOp = (BinaryOp) result;
            assertThat(((CapturedVariable) binOp.left()).index())
                    .as("Left operand index should be 10")
                    .isEqualTo(10);
            assertThat(((CapturedVariable) binOp.right()).index())
                    .as("Right operand index should be 11")
                    .isEqualTo(11);
        }

        @Test
        void renumberCapturedVariables_withUnaryOp_renumbersOperand() {
            LambdaExpression original = UnaryOp.not(captured(0, Boolean.class));

            LambdaExpression result = CapturedVariableHelper.renumberCapturedVariables(original, 3);

            assertThat(result).isInstanceOf(UnaryOp.class);
            UnaryOp unary = (UnaryOp) result;
            assertThat(((CapturedVariable) unary.operand()).index())
                    .as("Operand index should be 3")
                    .isEqualTo(3);
        }

        @Test
        void renumberCapturedVariables_withMethodCall_renumbersTargetAndArgs() {
            MethodCall original = new MethodCall(
                    captured(0, String.class),
                    "startsWith",
                    List.of(captured(1, String.class)),
                    Boolean.class);

            LambdaExpression result = CapturedVariableHelper.renumberCapturedVariables(original, 5);

            assertThat(result).isInstanceOf(MethodCall.class);
            MethodCall mc = (MethodCall) result;
            assertThat(((CapturedVariable) mc.target()).index())
                    .as("Target index should be 5")
                    .isEqualTo(5);
            assertThat(((CapturedVariable) mc.arguments().getFirst()).index())
                    .as("Argument index should be 6")
                    .isEqualTo(6);
        }

        @Test
        void renumberCapturedVariables_withConstructorCall_renumbersArgs() {
            ConstructorCall original = new ConstructorCall(
                    "com.example.Dto",
                    List.of(captured(0, String.class), captured(1, Integer.class)),
                    Object.class);

            LambdaExpression result = CapturedVariableHelper.renumberCapturedVariables(original, 2);

            assertThat(result).isInstanceOf(ConstructorCall.class);
            ConstructorCall cc = (ConstructorCall) result;
            assertThat(((CapturedVariable) cc.arguments().getFirst()).index())
                    .as("First arg index should be 2")
                    .isEqualTo(2);
            assertThat(((CapturedVariable) cc.arguments().get(1)).index())
                    .as("Second arg index should be 3")
                    .isEqualTo(3);
        }

        @Test
        void renumberCapturedVariables_withCast_renumbersExpression() {
            Cast original = new Cast(captured(0, Object.class), String.class);

            LambdaExpression result = CapturedVariableHelper.renumberCapturedVariables(original, 4);

            assertThat(result).isInstanceOf(Cast.class);
            Cast cast = (Cast) result;
            assertThat(((CapturedVariable) cast.expression()).index())
                    .as("Cast expression index should be 4")
                    .isEqualTo(4);
        }

        @Test
        void renumberCapturedVariables_withInstanceOf_renumbersExpression() {
            InstanceOf original = new InstanceOf(captured(0, Object.class), String.class);

            LambdaExpression result = CapturedVariableHelper.renumberCapturedVariables(original, 2);

            assertThat(result).isInstanceOf(InstanceOf.class);
            InstanceOf io = (InstanceOf) result;
            assertThat(((CapturedVariable) io.expression()).index())
                    .as("InstanceOf expression index should be 2")
                    .isEqualTo(2);
        }

        @Test
        void renumberCapturedVariables_withConditional_renumbersAllParts() {
            Conditional original = new Conditional(
                    captured(0, Boolean.class),
                    captured(1, String.class),
                    captured(2, String.class));

            LambdaExpression result = CapturedVariableHelper.renumberCapturedVariables(original, 10);

            assertThat(result).isInstanceOf(Conditional.class);
            Conditional cond = (Conditional) result;
            assertThat(((CapturedVariable) cond.condition()).index())
                    .as("Condition index should be 10")
                    .isEqualTo(10);
            assertThat(((CapturedVariable) cond.trueValue()).index())
                    .as("True value index should be 11")
                    .isEqualTo(11);
            assertThat(((CapturedVariable) cond.falseValue()).index())
                    .as("False value index should be 12")
                    .isEqualTo(12);
        }

        @Test
        void renumberCapturedVariables_withInExpression_renumbersFieldAndCollection() {
            InExpression original = new InExpression(
                    captured(0, String.class),
                    captured(1, List.class),
                    false);

            LambdaExpression result = CapturedVariableHelper.renumberCapturedVariables(original, 3);

            assertThat(result).isInstanceOf(InExpression.class);
            InExpression in = (InExpression) result;
            assertThat(((CapturedVariable) in.field()).index())
                    .as("Field index should be 3")
                    .isEqualTo(3);
            assertThat(((CapturedVariable) in.collection()).index())
                    .as("Collection index should be 4")
                    .isEqualTo(4);
        }

        @Test
        void renumberCapturedVariables_withMemberOfExpression_renumbersValueAndCollection() {
            MemberOfExpression original = new MemberOfExpression(
                    captured(0, String.class),
                    captured(1, List.class),
                    false);

            LambdaExpression result = CapturedVariableHelper.renumberCapturedVariables(original, 2);

            assertThat(result).isInstanceOf(MemberOfExpression.class);
            MemberOfExpression mo = (MemberOfExpression) result;
            assertThat(((CapturedVariable) mo.value()).index())
                    .as("Value index should be 2")
                    .isEqualTo(2);
            assertThat(((CapturedVariable) mo.collectionField()).index())
                    .as("Collection field index should be 3")
                    .isEqualTo(3);
        }

        @Test
        void renumberCapturedVariables_withPathExpression_returnsSameInstance() {
            PathExpression original = new PathExpression(
                    List.of(new PathSegment("entity", Object.class, RelationType.FIELD)), String.class);

            LambdaExpression result = CapturedVariableHelper.renumberCapturedVariables(original, 5);

            assertThat(result)
                    .as("PathExpression should be returned unchanged")
                    .isSameAs(original);
        }

        @Test
        void renumberCapturedVariables_withBiEntityFieldAccess_returnsSameInstance() {
            BiEntityFieldAccess original = new BiEntityFieldAccess("field", String.class, EntityPosition.FIRST);

            LambdaExpression result = CapturedVariableHelper.renumberCapturedVariables(original, 5);

            assertThat(result)
                    .as("BiEntityFieldAccess should be returned unchanged")
                    .isSameAs(original);
        }

        @Test
        void renumberCapturedVariables_withDefault_returnsSameInstance() {
            Constant original = new Constant("test", String.class);

            LambdaExpression result = CapturedVariableHelper.renumberCapturedVariables(original, 5);

            assertThat(result)
                    .as("Default case (Constant) should be returned unchanged")
                    .isSameAs(original);
        }
    }

    @Nested
    class CombinePredicatesListTests {

        @Test
        void combinePredicatesWithAnd_withEmptyList_throwsException() {
            assertThatThrownBy(() -> CapturedVariableHelper.combinePredicatesWithAnd(List.of()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("empty");
        }

        @Test
        void combinePredicatesWithAnd_withSinglePredicate_returnsSamePredicate() {
            LambdaExpression predicate = field("active", Boolean.class);

            LambdaExpression result = CapturedVariableHelper.combinePredicatesWithAnd(List.of(predicate));

            assertThat(result)
                    .as("Single predicate should be returned unchanged")
                    .isSameAs(predicate);
        }

        @Test
        void combinePredicatesWithAnd_withTwoPredicates_combinesWithAnd() {
            LambdaExpression p1 = field("active", Boolean.class);
            LambdaExpression p2 = field("verified", Boolean.class);

            LambdaExpression result = CapturedVariableHelper.combinePredicatesWithAnd(List.of(p1, p2));

            assertThat(result).isInstanceOf(BinaryOp.class);
            BinaryOp binOp = (BinaryOp) result;
            assertThat(binOp.operator())
                    .as("Should use AND operator")
                    .isEqualTo(BinaryOp.Operator.AND);
            assertThat(binOp.left()).isSameAs(p1);
            assertThat(binOp.right()).isSameAs(p2);
        }

        @Test
        void combinePredicatesWithAnd_withThreePredicates_chainsWithAnd() {
            LambdaExpression p1 = field("a", Boolean.class);
            LambdaExpression p2 = field("b", Boolean.class);
            LambdaExpression p3 = field("c", Boolean.class);

            LambdaExpression result = CapturedVariableHelper.combinePredicatesWithAnd(List.of(p1, p2, p3));

            // Should be ((p1 AND p2) AND p3)
            assertThat(result).isInstanceOf(BinaryOp.class);
            BinaryOp outerAnd = (BinaryOp) result;
            assertThat(outerAnd.operator()).isEqualTo(BinaryOp.Operator.AND);
            assertThat(outerAnd.right()).isSameAs(p3);

            assertThat(outerAnd.left()).isInstanceOf(BinaryOp.class);
            BinaryOp innerAnd = (BinaryOp) outerAnd.left();
            assertThat(innerAnd.left()).isSameAs(p1);
            assertThat(innerAnd.right()).isSameAs(p2);
        }
    }

    @Nested
    class CombinePredicatesTwoArgsTests {

        @Test
        void combinePredicatesWithAnd_bothNonNull_returnsAndExpression() {
            LambdaExpression p1 = field("active", Boolean.class);
            LambdaExpression p2 = field("verified", Boolean.class);

            LambdaExpression result = CapturedVariableHelper.combinePredicatesWithAnd(p1, p2);

            assertThat(result).isInstanceOf(BinaryOp.class);
            BinaryOp binOp = (BinaryOp) result;
            assertThat(binOp.operator()).isEqualTo(BinaryOp.Operator.AND);
        }

        @Test
        void combinePredicatesWithAnd_firstNull_returnsSecond() {
            LambdaExpression p2 = field("verified", Boolean.class);

            LambdaExpression result = CapturedVariableHelper.combinePredicatesWithAnd(null, p2);

            assertThat(result)
                    .as("Should return second predicate when first is null")
                    .isSameAs(p2);
        }

        @Test
        void combinePredicatesWithAnd_secondNull_returnsFirst() {
            LambdaExpression p1 = field("active", Boolean.class);

            LambdaExpression result = CapturedVariableHelper.combinePredicatesWithAnd(p1, null);

            assertThat(result)
                    .as("Should return first predicate when second is null")
                    .isSameAs(p1);
        }

        @Test
        void combinePredicatesWithAnd_bothNull_returnsNull() {
            LambdaExpression result = CapturedVariableHelper.combinePredicatesWithAnd(null, null);

            assertThat(result)
                    .as("Should return null when both predicates are null")
                    .isNull();
        }
    }
}
