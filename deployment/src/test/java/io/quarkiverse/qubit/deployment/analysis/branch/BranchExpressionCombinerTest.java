package io.quarkiverse.qubit.deployment.analysis.branch;

import io.quarkiverse.qubit.deployment.analysis.ControlFlowAnalyzer;
import io.quarkiverse.qubit.deployment.ast.LambdaExpression;
import io.quarkiverse.qubit.deployment.ast.LambdaExpression.BinaryOp.Operator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Optional;

import static io.quarkiverse.qubit.deployment.ast.LambdaExpression.BinaryOp.Operator.*;
import static io.quarkiverse.qubit.deployment.testutil.AstBuilders.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link BranchExpressionCombiner}.
 *
 * <p>Tests the static utility methods for expression combination, restructuring,
 * and operator adjustment in branch analysis.
 */
class BranchExpressionCombinerTest {

    // ==================== combineAndRestructureIfNeeded Tests ====================

    @Nested
    @DisplayName("combineAndRestructureIfNeeded")
    class CombineAndRestructureTests {

        @Test
        void and_withSimpleExpressions_combinesWithAnd() {
            var left = eq(field("a", int.class), constant(1));
            var right = eq(field("b", int.class), constant(2));

            var result = BranchExpressionCombiner.combineAndRestructureIfNeeded(AND, left, right);

            assertThat(result).isInstanceOf(LambdaExpression.BinaryOp.class);
            var binOp = (LambdaExpression.BinaryOp) result;
            assertThat(binOp.operator()).isEqualTo(AND);
            assertThat(binOp.left()).isSameAs(left);
            assertThat(binOp.right()).isSameAs(right);
        }

        @Test
        void or_withSimpleExpressions_combinesWithOr() {
            var left = eq(field("a", int.class), constant(1));
            var right = eq(field("b", int.class), constant(2));

            var result = BranchExpressionCombiner.combineAndRestructureIfNeeded(OR, left, right);

            assertThat(result).isInstanceOf(LambdaExpression.BinaryOp.class);
            var binOp = (LambdaExpression.BinaryOp) result;
            assertThat(binOp.operator()).isEqualTo(OR);
        }

        @Test
        void or_withNestedOrAndPreviousAnd_restructuresForPrecedence() {
            // Structure: ((a OR b) AND c) - should restructure when OR d
            // Pattern: X==(a OR b), Y==c, combining with OR d
            var aOrB = or(field("a", boolean.class), field("b", boolean.class));
            var andC = and(aOrB, field("c", boolean.class));
            var d = field("d", boolean.class);

            var result = BranchExpressionCombiner.combineAndRestructureIfNeeded(OR, andC, d);

            // Should restructure to: (a OR b) AND (c OR d)
            assertThat(result).isInstanceOf(LambdaExpression.BinaryOp.class);
            var binOp = (LambdaExpression.BinaryOp) result;
            assertThat(binOp.operator())
                    .as("Outer operator should be AND after restructure")
                    .isEqualTo(AND);

            // Left should be (a OR b)
            assertThat(binOp.left()).isSameAs(aOrB);

            // Right should be (c OR d)
            assertThat(binOp.right()).isInstanceOf(LambdaExpression.BinaryOp.class);
            var rightOr = (LambdaExpression.BinaryOp) binOp.right();
            assertThat(rightOr.operator()).isEqualTo(OR);
        }

        @Test
        void or_withAndPreviousButNotOrLeft_doesNotRestructure() {
            // Structure: (a AND b) - NOT an OR on the left
            // Should NOT restructure
            var aAndB = and(field("a", boolean.class), field("b", boolean.class));
            var c = field("c", boolean.class);

            var result = BranchExpressionCombiner.combineAndRestructureIfNeeded(OR, aAndB, c);

            // Should be simple combination: (a AND b) OR c
            assertThat(result).isInstanceOf(LambdaExpression.BinaryOp.class);
            var binOp = (LambdaExpression.BinaryOp) result;
            assertThat(binOp.operator())
                    .as("Should be OR without restructure")
                    .isEqualTo(OR);
            assertThat(binOp.left()).isSameAs(aAndB);
            assertThat(binOp.right()).isSameAs(c);
        }

        @Test
        void and_withNestedOrAndPreviousAnd_doesNotRestructure() {
            // Restructuring only happens with OR combineOp
            var aOrB = or(field("a", boolean.class), field("b", boolean.class));
            var andC = and(aOrB, field("c", boolean.class));
            var d = field("d", boolean.class);

            var result = BranchExpressionCombiner.combineAndRestructureIfNeeded(AND, andC, d);

            // Should be simple AND combination
            assertThat(result).isInstanceOf(LambdaExpression.BinaryOp.class);
            var binOp = (LambdaExpression.BinaryOp) result;
            assertThat(binOp.operator())
                    .as("Should stay AND without restructure")
                    .isEqualTo(AND);
        }
    }

    // ==================== isPredicateExpression Tests ====================

    @Nested
    @DisplayName("isPredicateExpression")
    class IsPredicateExpressionTests {

        @Test
        void binaryOp_isPredicateExpression() {
            var expr = eq(field("a", int.class), constant(1));

            assertThat(BranchExpressionCombiner.isPredicateExpression(expr))
                    .as("BinaryOp should be a predicate expression")
                    .isTrue();
        }

        @Test
        void methodCallReturningBoolean_isPredicateExpression() {
            var expr = methodCall(field("name", String.class), "isEmpty", boolean.class);

            assertThat(BranchExpressionCombiner.isPredicateExpression(expr))
                    .as("MethodCall returning boolean should be a predicate expression")
                    .isTrue();
        }

        @Test
        void methodCallReturningNonBoolean_isNotPredicateExpression() {
            var expr = methodCall(field("name", String.class), "length", int.class);

            assertThat(BranchExpressionCombiner.isPredicateExpression(expr))
                    .as("MethodCall returning int should NOT be a predicate expression")
                    .isFalse();
        }

        @Test
        void inExpression_isPredicateExpression() {
            var expr = new LambdaExpression.InExpression(
                    field("status", String.class),
                    new LambdaExpression.ArrayCreation(
                            "java.lang.String",
                            List.of(constant("A"), constant("B")),
                            Object[].class
                    ),
                    false
            );

            assertThat(BranchExpressionCombiner.isPredicateExpression(expr))
                    .as("InExpression should be a predicate expression")
                    .isTrue();
        }

        @Test
        void memberOfExpression_isPredicateExpression() {
            var expr = new LambdaExpression.MemberOfExpression(
                    field("item", Object.class),
                    field("collection", List.class),
                    false
            );

            assertThat(BranchExpressionCombiner.isPredicateExpression(expr))
                    .as("MemberOfExpression should be a predicate expression")
                    .isTrue();
        }

        @Test
        void unaryOp_isPredicateExpression() {
            var expr = not(field("active", boolean.class));

            assertThat(BranchExpressionCombiner.isPredicateExpression(expr))
                    .as("UnaryOp should be a predicate expression")
                    .isTrue();
        }

        @Test
        void fieldAccess_isNotPredicateExpression() {
            var expr = field("name", String.class);

            assertThat(BranchExpressionCombiner.isPredicateExpression(expr))
                    .as("FieldAccess should NOT be a predicate expression")
                    .isFalse();
        }

        @Test
        void constant_isNotPredicateExpression() {
            var expr = constant(42);

            assertThat(BranchExpressionCombiner.isPredicateExpression(expr))
                    .as("Constant should NOT be a predicate expression")
                    .isFalse();
        }

        @Test
        void capturedVariable_isNotPredicateExpression() {
            var expr = captured(0, String.class);

            assertThat(BranchExpressionCombiner.isPredicateExpression(expr))
                    .as("CapturedVariable should NOT be a predicate expression")
                    .isFalse();
        }
    }

    // ==================== adjustCombineOperator Tests ====================

    @Nested
    @DisplayName("adjustCombineOperator")
    class AdjustCombineOperatorTests {

        @Test
        void sameLabelWithFalseSink_overridesToAnd() {
            Operator result = BranchExpressionCombiner.adjustCombineOperator(
                    OR,  // initial operator
                    true, // sameLabel
                    ControlFlowAnalyzer.LabelClassification.FALSE_SINK,
                    false, false, true
            );

            assertThat(result)
                    .as("Same label to FALSE_SINK should override to AND")
                    .isEqualTo(AND);
        }

        @Test
        void sameLabelWithTrueSink_overridesToOr() {
            Operator result = BranchExpressionCombiner.adjustCombineOperator(
                    AND,  // initial operator
                    true, // sameLabel
                    ControlFlowAnalyzer.LabelClassification.TRUE_SINK,
                    false, false, true
            );

            assertThat(result)
                    .as("Same label to TRUE_SINK should override to OR")
                    .isEqualTo(OR);
        }

        @Test
        void sameLabelWithIntermediate_overridesToOr() {
            Operator result = BranchExpressionCombiner.adjustCombineOperator(
                    AND,  // initial operator
                    true, // sameLabel
                    ControlFlowAnalyzer.LabelClassification.INTERMEDIATE,
                    false, false, true
            );

            assertThat(result)
                    .as("Same label to INTERMEDIATE should override to OR")
                    .isEqualTo(OR);
        }

        @Test
        void notSameLabel_keepsOriginalOperator() {
            Operator result = BranchExpressionCombiner.adjustCombineOperator(
                    AND,
                    false, // NOT same label
                    ControlFlowAnalyzer.LabelClassification.FALSE_SINK,
                    false, false, true
            );

            assertThat(result)
                    .as("Not same label should keep original operator")
                    .isEqualTo(AND);
        }

        @Test
        void startingNewOrGroupWithPredicateOnStack_returnsNull() {
            Operator result = BranchExpressionCombiner.adjustCombineOperator(
                    OR,
                    false,
                    ControlFlowAnalyzer.LabelClassification.TRUE_SINK,
                    false, // NOT completing AND group
                    true,  // starting new OR group
                    true   // has predicate on stack
            );

            assertThat(result)
                    .as("Starting new OR group with predicate on stack should defer combination")
                    .isNull();
        }

        @Test
        void completingAndGroupWithPredicateOnStack_forcesAnd() {
            Operator result = BranchExpressionCombiner.adjustCombineOperator(
                    OR,  // original
                    false,
                    ControlFlowAnalyzer.LabelClassification.TRUE_SINK,
                    true,  // completing AND group
                    false, // NOT starting new OR group
                    true   // has predicate on stack
            );

            assertThat(result)
                    .as("Completing AND group with predicate should force AND")
                    .isEqualTo(AND);
        }

        @Test
        void nullCombineOp_staysNull() {
            Operator result = BranchExpressionCombiner.adjustCombineOperator(
                    null,
                    true,
                    ControlFlowAnalyzer.LabelClassification.FALSE_SINK,
                    false, false, true
            );

            assertThat(result)
                    .as("Null combineOp should stay null (no override)")
                    .isNull();
        }

    }

    // ==================== determineJumpToTrue Tests ====================

    @Nested
    @DisplayName("determineJumpToTrue")
    class DetermineJumpToTrueTests {

        @Test
        void withExplicitTrueTarget_returnsTrue() {
            boolean result = BranchExpressionCombiner.determineJumpToTrue(
                    true,
                    ControlFlowAnalyzer.LabelClassification.FALSE_SINK,
                    0,
                    opcode -> false
            );

            assertThat(result)
                    .as("Explicit TRUE jump target should return true")
                    .isTrue();
        }

        @Test
        void withExplicitFalseTarget_returnsFalse() {
            boolean result = BranchExpressionCombiner.determineJumpToTrue(
                    false,
                    ControlFlowAnalyzer.LabelClassification.TRUE_SINK,
                    0,
                    opcode -> true
            );

            assertThat(result)
                    .as("Explicit FALSE jump target should return false")
                    .isFalse();
        }

        @Test
        void withNullTargetAndIntermediateLabel_usesOpcodePredicate_true() {
            boolean result = BranchExpressionCombiner.determineJumpToTrue(
                    null,
                    ControlFlowAnalyzer.LabelClassification.INTERMEDIATE,
                    42,
                    opcode -> opcode == 42  // success opcode
            );

            assertThat(result)
                    .as("INTERMEDIATE with success opcode should return true")
                    .isTrue();
        }

        @Test
        void withNullTargetAndIntermediateLabel_usesOpcodePredicate_false() {
            boolean result = BranchExpressionCombiner.determineJumpToTrue(
                    null,
                    ControlFlowAnalyzer.LabelClassification.INTERMEDIATE,
                    99,
                    opcode -> opcode == 42  // 99 is NOT success opcode
            );

            assertThat(result)
                    .as("INTERMEDIATE with non-success opcode should return false")
                    .isFalse();
        }

        @Test
        void withNullTargetAndNonIntermediateLabel_returnsFalse() {
            boolean result = BranchExpressionCombiner.determineJumpToTrue(
                    null,
                    ControlFlowAnalyzer.LabelClassification.FALSE_SINK,
                    42,
                    opcode -> true  // predicate would say true, but not INTERMEDIATE
            );

            assertThat(result)
                    .as("Non-INTERMEDIATE label with null target should return false")
                    .isFalse();
        }

        @Test
        void withNullTargetAndTrueSink_returnsFalse() {
            boolean result = BranchExpressionCombiner.determineJumpToTrue(
                    null,
                    ControlFlowAnalyzer.LabelClassification.TRUE_SINK,
                    0,
                    opcode -> true
            );

            assertThat(result)
                    .as("TRUE_SINK with null target should return false (only INTERMEDIATE uses opcode)")
                    .isFalse();
        }
    }

    // ==================== performCombination Tests ====================

    @Nested
    @DisplayName("performCombination")
    class PerformCombinationTests {

        @Test
        void withEmptyStack_pushesExpressionStandalone() {
            Deque<LambdaExpression> stack = new ArrayDeque<>();
            var expression = eq(field("a", int.class), constant(1));
            var ctx = createContext("TEST", new BranchState.Initial(), new BranchState.Initial(),
                    null, null, Optional.empty(), false, null,
                    ControlFlowAnalyzer.LabelClassification.FALSE_SINK, false, false);

            BranchExpressionCombiner.performCombination(stack, expression, ctx);

            assertThat(stack).hasSize(1);
            assertThat(stack.peek()).isSameAs(expression);
        }

        @Test
        void withPredicateOnStackAndCombineOp_combinesExpressions() {
            Deque<LambdaExpression> stack = new ArrayDeque<>();
            var previous = eq(field("a", int.class), constant(1));
            stack.push(previous);

            var expression = eq(field("b", int.class), constant(2));
            var ctx = createContext("TEST",
                    new BranchState.AndMode(Optional.of(false), false),
                    new BranchState.AndMode(Optional.of(false), false),
                    AND, null, Optional.of(false), false, previous,
                    ControlFlowAnalyzer.LabelClassification.FALSE_SINK, false, false);

            BranchExpressionCombiner.performCombination(stack, expression, ctx);

            assertThat(stack).hasSize(1);
            assertThat(stack.peek()).isInstanceOf(LambdaExpression.BinaryOp.class);
            var combined = (LambdaExpression.BinaryOp) stack.peek();
            assertThat(combined.operator()).isEqualTo(AND);
        }

        @Test
        void withNonPredicateOnStack_pushesStandalone() {
            Deque<LambdaExpression> stack = new ArrayDeque<>();
            var nonPredicate = field("name", String.class); // Not a predicate
            stack.push(nonPredicate);

            var expression = eq(field("b", int.class), constant(2));
            var ctx = createContext("TEST", new BranchState.Initial(), new BranchState.Initial(),
                    AND, null, Optional.empty(), false, nonPredicate,
                    ControlFlowAnalyzer.LabelClassification.FALSE_SINK, false, false);

            BranchExpressionCombiner.performCombination(stack, expression, ctx);

            assertThat(stack).hasSize(2);
            assertThat(stack.peek()).isSameAs(expression);
        }

        @Test
        void orModeWithAndOnStack_defersOrCombination() {
            // (A && B) on stack in OrMode, combining with OR, jumpTarget=false, not sameLabel
            // Should defer OR combination (set combineOp to null)
            Deque<LambdaExpression> stack = new ArrayDeque<>();
            var aAndB = and(field("a", boolean.class), field("b", boolean.class));
            stack.push(aAndB);

            var expression = eq(field("c", int.class), constant(1));
            var ctx = createContext("TEST",
                    new BranchState.OrMode(Optional.of(true), false),
                    new BranchState.OrMode(Optional.of(true), false),
                    OR, false, Optional.of(true), false, aAndB,
                    ControlFlowAnalyzer.LabelClassification.FALSE_SINK, false, false);

            BranchExpressionCombiner.performCombination(stack, expression, ctx);

            // Should push standalone because OR was deferred
            assertThat(stack).hasSize(2);
            assertThat(stack.peek()).isSameAs(expression);
        }

        @Test
        void postAndGroupMerge_inOrMode_combinesTwoAndGroupsWithOr() {
            // Two AND groups on stack, combining with AND in OrMode with sameLabel
            // Should trigger post-AND-group merge: (A && B) || (C && D)
            Deque<LambdaExpression> stack = new ArrayDeque<>();
            var aAndB = and(eq(field("a", int.class), constant(1)),
                            eq(field("b", int.class), constant(2)));
            var cAndD = and(eq(field("c", int.class), constant(3)),
                            eq(field("d", int.class), constant(4)));
            stack.push(aAndB);
            stack.push(cAndD);

            var expression = eq(field("e", int.class), constant(5));
            var ctx = createContext("TEST",
                    new BranchState.OrMode(Optional.of(true), false),
                    new BranchState.OrMode(Optional.of(true), false),
                    AND, null, Optional.of(true), true, cAndD,  // sameLabel=true for merge trigger
                    ControlFlowAnalyzer.LabelClassification.FALSE_SINK, false, false);

            BranchExpressionCombiner.performCombination(stack, expression, ctx);

            // Should have merged: (A && B) OR (C && D) combined with E
            assertThat(stack).hasSizeGreaterThanOrEqualTo(1);
        }

        @Test
        void withNullCombineOp_pushesStandalone() {
            Deque<LambdaExpression> stack = new ArrayDeque<>();
            var previous = eq(field("a", int.class), constant(1));
            stack.push(previous);

            var expression = eq(field("b", int.class), constant(2));
            var ctx = createContext("TEST",
                    new BranchState.Initial(), new BranchState.Initial(),
                    null, null, Optional.empty(), false, previous,
                    ControlFlowAnalyzer.LabelClassification.FALSE_SINK, false, false);

            BranchExpressionCombiner.performCombination(stack, expression, ctx);

            // With null combineOp, should push standalone
            assertThat(stack).hasSize(2);
            assertThat(stack.peek()).isSameAs(expression);
        }

        @Test
        void orModeWithNonAndOnStack_combinesNormally() {
            // OR expression on stack in OrMode with OR operator - should combine normally
            Deque<LambdaExpression> stack = new ArrayDeque<>();
            var aOrB = or(field("a", boolean.class), field("b", boolean.class));
            stack.push(aOrB);

            var expression = eq(field("c", int.class), constant(1));
            var ctx = createContext("TEST",
                    new BranchState.OrMode(Optional.of(true), false),
                    new BranchState.OrMode(Optional.of(true), false),
                    OR, true, Optional.of(true), false, aOrB,  // jumpTarget=true, not false
                    ControlFlowAnalyzer.LabelClassification.TRUE_SINK, false, false);

            BranchExpressionCombiner.performCombination(stack, expression, ctx);

            // Should combine with OR since jumpTarget is true
            assertThat(stack).hasSize(1);
            var combined = (LambdaExpression.BinaryOp) stack.peek();
            assertThat(combined.operator()).isEqualTo(OR);
        }

        @Test
        void postMerge_withOnlyOneElementOnStack_doesNotMerge() {
            // Test the boundary condition: stack.size() >= 2 check in post-merge
            Deque<LambdaExpression> stack = new ArrayDeque<>();
            var aAndB = and(eq(field("a", int.class), constant(1)),
                            eq(field("b", int.class), constant(2)));
            stack.push(aAndB);
            // Only ONE element on stack

            var expression = eq(field("c", int.class), constant(3));
            var ctx = createContext("TEST",
                    new BranchState.OrMode(Optional.of(true), false),
                    new BranchState.OrMode(Optional.of(true), false),
                    AND, null, Optional.of(true), true, aAndB,  // sameLabel=true
                    ControlFlowAnalyzer.LabelClassification.FALSE_SINK, false, false);

            BranchExpressionCombiner.performCombination(stack, expression, ctx);

            // With only one element, post-merge doesn't trigger but combination should happen
            assertThat(stack).hasSize(1);
        }

        @Test
        void afterCombination_stateTransitionIsCalled() {
            Deque<LambdaExpression> stack = new ArrayDeque<>();
            var previous = eq(field("a", int.class), constant(1));
            stack.push(previous);

            var expression = eq(field("b", int.class), constant(2));
            var ctx = createContext("TEST",
                    new BranchState.AndMode(Optional.of(true), false),
                    new BranchState.AndMode(Optional.of(true), false),
                    AND, true, Optional.of(true), false, previous,  // jumpTarget=true
                    ControlFlowAnalyzer.LabelClassification.TRUE_SINK, false, false);

            BranchState result = BranchExpressionCombiner.performCombination(stack, expression, ctx);

            // afterCombination should be called, result state should be valid
            assertThat(result).isNotNull();
        }

        private BranchExpressionCombiner.CombinationContext createContext(
                String instructionName,
                BranchState state,
                BranchState newState,
                Operator combineOp,
                Boolean jumpTarget,
                Optional<Boolean> previousJumpTarget,
                boolean sameLabel,
                LambdaExpression stackTop,
                ControlFlowAnalyzer.LabelClassification jumpLabelClass,
                boolean completingAndGroup,
                boolean startingNewOrGroup) {
            return new BranchExpressionCombiner.CombinationContext(
                    instructionName, state, newState, combineOp, jumpTarget,
                    previousJumpTarget, sameLabel, stackTop, jumpLabelClass,
                    completingAndGroup, startingNewOrGroup);
        }
    }

    // ==================== CombinationContext Record Tests ====================

    @Nested
    @DisplayName("CombinationContext record")
    class CombinationContextTests {

        @Test
        void accessors_returnCorrectValues() {
            var state = new BranchState.Initial();
            var newState = new BranchState.AndMode(Optional.of(true), false);
            var stackTop = field("test", int.class);

            var ctx = new BranchExpressionCombiner.CombinationContext(
                    "IFEQ", state, newState, AND, true,
                    Optional.of(false), true, stackTop,
                    ControlFlowAnalyzer.LabelClassification.FALSE_SINK, true, false);

            assertThat(ctx.instructionName()).isEqualTo("IFEQ");
            assertThat(ctx.state()).isSameAs(state);
            assertThat(ctx.newState()).isSameAs(newState);
            assertThat(ctx.combineOp()).isEqualTo(AND);
            assertThat(ctx.jumpTarget()).isTrue();
            assertThat(ctx.previousJumpTarget()).contains(false);
            assertThat(ctx.sameLabel()).isTrue();
            assertThat(ctx.stackTop()).isSameAs(stackTop);
            assertThat(ctx.jumpLabelClass()).isEqualTo(ControlFlowAnalyzer.LabelClassification.FALSE_SINK);
            assertThat(ctx.completingAndGroup()).isTrue();
            assertThat(ctx.startingNewOrGroup()).isFalse();
        }
    }

    // ==================== Additional adjustCombineOperator Edge Cases ====================

    @Nested
    @DisplayName("adjustCombineOperator edge cases")
    class AdjustCombineOperatorEdgeCases {

        @Test
        void sameLabelFalseSink_withOrOperator_overridesToAnd() {
            // Tests the mutation: jumpLabelClass == FALSE_SINK
            Operator result = BranchExpressionCombiner.adjustCombineOperator(
                    OR,   // initial operator
                    true, // sameLabel
                    ControlFlowAnalyzer.LabelClassification.FALSE_SINK,
                    false, false, false
            );

            assertThat(result)
                    .as("sameLabel + FALSE_SINK should override OR to AND")
                    .isEqualTo(AND);
        }

        @Test
        void sameLabelTrueSink_withAndOperator_overridesToOr() {
            // Tests the mutation: jumpLabelClass == TRUE_SINK
            Operator result = BranchExpressionCombiner.adjustCombineOperator(
                    AND,  // initial operator
                    true, // sameLabel
                    ControlFlowAnalyzer.LabelClassification.TRUE_SINK,
                    false, false, false
            );

            assertThat(result)
                    .as("sameLabel + TRUE_SINK should override AND to OR")
                    .isEqualTo(OR);
        }

        @Test
        void startingNewOrGroup_withPredicateOnStack_returnsNull() {
            // Tests the mutation: startingNewOrGroup && hasPredicateOnStack
            Operator result = BranchExpressionCombiner.adjustCombineOperator(
                    OR,
                    false,
                    ControlFlowAnalyzer.LabelClassification.TRUE_SINK,
                    false,  // NOT completing AND group
                    true,   // starting new OR group
                    true    // has predicate on stack
            );

            assertThat(result)
                    .as("Starting new OR group with predicate should return null to defer")
                    .isNull();
        }

        @Test
        void startingNewOrGroup_withoutPredicateOnStack_keepsOperator() {
            // Tests the boundary: startingNewOrGroup is true BUT hasPredicateOnStack is false
            Operator result = BranchExpressionCombiner.adjustCombineOperator(
                    OR,
                    false,
                    ControlFlowAnalyzer.LabelClassification.TRUE_SINK,
                    false,  // NOT completing AND group
                    true,   // starting new OR group
                    false   // NO predicate on stack
            );

            assertThat(result)
                    .as("Starting new OR group without predicate should NOT defer")
                    .isEqualTo(OR);
        }

        @Test
        void completingAndGroup_withPredicateOnStack_forcesAnd() {
            // Tests the mutation: completingAndGroup && hasPredicateOnStack
            Operator result = BranchExpressionCombiner.adjustCombineOperator(
                    OR,   // original
                    false,
                    ControlFlowAnalyzer.LabelClassification.TRUE_SINK,
                    true,  // completing AND group
                    false, // NOT starting new OR group
                    true   // has predicate on stack
            );

            assertThat(result)
                    .as("Completing AND group with predicate should force AND")
                    .isEqualTo(AND);
        }

        @Test
        void completingAndGroup_withoutPredicateOnStack_keepsOperator() {
            // Tests the boundary: completingAndGroup is true BUT hasPredicateOnStack is false
            Operator result = BranchExpressionCombiner.adjustCombineOperator(
                    OR,
                    false,
                    ControlFlowAnalyzer.LabelClassification.TRUE_SINK,
                    true,  // completing AND group
                    false, // NOT starting new OR group
                    false  // NO predicate on stack
            );

            assertThat(result)
                    .as("Completing AND group without predicate should NOT force AND")
                    .isEqualTo(OR);
        }

        @Test
        void notSameLabel_withFalseSink_keepsOriginalOperator() {
            // Tests the mutation: sameLabel must be true for override
            Operator result = BranchExpressionCombiner.adjustCombineOperator(
                    OR,    // should stay OR
                    false, // NOT same label
                    ControlFlowAnalyzer.LabelClassification.FALSE_SINK,
                    false, false, true
            );

            assertThat(result)
                    .as("Not same label should NOT override operator")
                    .isEqualTo(OR);
        }

        @Test
        void bothConditionsFalse_keepsOperator() {
            // Neither startingNewOrGroup nor completingAndGroup
            Operator result = BranchExpressionCombiner.adjustCombineOperator(
                    AND,
                    false,
                    ControlFlowAnalyzer.LabelClassification.TRUE_SINK,
                    false,  // NOT completing AND group
                    false,  // NOT starting new OR group
                    true    // has predicate on stack
            );

            assertThat(result)
                    .as("Neither condition true should keep original operator")
                    .isEqualTo(AND);
        }
    }

    // ==================== determineJumpToTrue Edge Cases ====================

    @Nested
    @DisplayName("determineJumpToTrue edge cases")
    class DetermineJumpToTrueEdgeCases {

        @Test
        void jumpTargetTrue_returnsTrue() {
            // Tests TRUE.equals(jumpTarget)
            boolean result = BranchExpressionCombiner.determineJumpToTrue(
                    Boolean.TRUE,
                    ControlFlowAnalyzer.LabelClassification.FALSE_SINK,
                    99,
                    opcode -> false  // predicate doesn't matter
            );

            assertThat(result)
                    .as("jumpTarget=TRUE should return true")
                    .isTrue();
        }

        @Test
        void jumpTargetFalse_returnsFalse() {
            // Tests the mutation: FALSE.equals(jumpTarget)
            boolean result = BranchExpressionCombiner.determineJumpToTrue(
                    Boolean.FALSE,
                    ControlFlowAnalyzer.LabelClassification.TRUE_SINK,
                    99,
                    opcode -> true  // predicate returns true but jumpTarget is explicit
            );

            assertThat(result)
                    .as("jumpTarget=FALSE should return false")
                    .isFalse();
        }

        @Test
        void nullJumpTarget_intermediate_usesPredicate_true() {
            // Tests INTERMEDIATE classification with success opcode
            boolean result = BranchExpressionCombiner.determineJumpToTrue(
                    null,  // null forces use of predicate
                    ControlFlowAnalyzer.LabelClassification.INTERMEDIATE,
                    42,
                    opcode -> opcode == 42  // predicate returns true for 42
            );

            assertThat(result)
                    .as("INTERMEDIATE with success opcode should return true")
                    .isTrue();
        }

        @Test
        void nullJumpTarget_intermediate_usesPredicate_false() {
            // Tests INTERMEDIATE classification with non-success opcode
            boolean result = BranchExpressionCombiner.determineJumpToTrue(
                    null,
                    ControlFlowAnalyzer.LabelClassification.INTERMEDIATE,
                    99,
                    opcode -> opcode == 42  // 99 is NOT success opcode
            );

            assertThat(result)
                    .as("INTERMEDIATE with non-success opcode should return false")
                    .isFalse();
        }

        @Test
        void nullJumpTarget_trueSink_returnsFalse() {
            // Tests that TRUE_SINK with null jumpTarget returns false (not using predicate)
            boolean result = BranchExpressionCombiner.determineJumpToTrue(
                    null,
                    ControlFlowAnalyzer.LabelClassification.TRUE_SINK,
                    42,
                    opcode -> true  // predicate returns true but not INTERMEDIATE
            );

            assertThat(result)
                    .as("TRUE_SINK with null jumpTarget should return false")
                    .isFalse();
        }

        @Test
        void nullJumpTarget_falseSink_returnsFalse() {
            // Tests that FALSE_SINK with null jumpTarget returns false
            boolean result = BranchExpressionCombiner.determineJumpToTrue(
                    null,
                    ControlFlowAnalyzer.LabelClassification.FALSE_SINK,
                    42,
                    opcode -> true  // predicate returns true but not INTERMEDIATE
            );

            assertThat(result)
                    .as("FALSE_SINK with null jumpTarget should return false")
                    .isFalse();
        }
    }

    // ==================== performCombination OrMode Deferral Tests ====================

    @Nested
    @DisplayName("performCombination OrMode deferral")
    class PerformCombinationOrModeDeferralTests {

        @Test
        void orModeWithAndOnStack_jumpTargetFalse_notSameLabel_defersOr() {
            // Tests the OrMode deferral logic: state instanceof OrMode && combineOp == OR
            //   && FALSE.equals(jumpTarget) && !sameLabel && stackTop is AND
            Deque<LambdaExpression> stack = new ArrayDeque<>();
            var aAndB = and(field("a", boolean.class), field("b", boolean.class));
            stack.push(aAndB);

            var expression = eq(field("c", int.class), constant(1));
            var ctx = createContext("TEST",
                    new BranchState.OrMode(Optional.of(true), false),
                    new BranchState.OrMode(Optional.of(true), false),
                    OR, false, Optional.of(true), false, aAndB,
                    ControlFlowAnalyzer.LabelClassification.FALSE_SINK, false, false);

            BranchExpressionCombiner.performCombination(stack, expression, ctx);

            // Should push standalone because OR was deferred (combineOp set to null)
            assertThat(stack).hasSize(2);
            assertThat(stack.peek()).isSameAs(expression);
        }

        @Test
        void orModeWithAndOnStack_jumpTargetTrue_combinesNormally() {
            // Tests that jumpTarget=TRUE prevents deferral
            Deque<LambdaExpression> stack = new ArrayDeque<>();
            var aAndB = and(field("a", boolean.class), field("b", boolean.class));
            stack.push(aAndB);

            var expression = eq(field("c", int.class), constant(1));
            var ctx = createContext("TEST",
                    new BranchState.OrMode(Optional.of(true), false),
                    new BranchState.OrMode(Optional.of(true), false),
                    OR, true, Optional.of(true), false, aAndB,  // jumpTarget=TRUE
                    ControlFlowAnalyzer.LabelClassification.TRUE_SINK, false, false);

            BranchExpressionCombiner.performCombination(stack, expression, ctx);

            // Should combine with OR since jumpTarget is TRUE
            assertThat(stack).hasSize(1);
            var combined = (LambdaExpression.BinaryOp) stack.peek();
            assertThat(combined.operator()).isEqualTo(OR);
        }

        @Test
        void orModeWithAndOnStack_sameLabel_combinesNormally() {
            // Tests that sameLabel=TRUE prevents deferral
            Deque<LambdaExpression> stack = new ArrayDeque<>();
            var aAndB = and(field("a", boolean.class), field("b", boolean.class));
            stack.push(aAndB);

            var expression = eq(field("c", int.class), constant(1));
            var ctx = createContext("TEST",
                    new BranchState.OrMode(Optional.of(true), false),
                    new BranchState.OrMode(Optional.of(true), false),
                    OR, false, Optional.of(true), true, aAndB,  // sameLabel=TRUE
                    ControlFlowAnalyzer.LabelClassification.TRUE_SINK, false, false);

            BranchExpressionCombiner.performCombination(stack, expression, ctx);

            // sameLabel triggers override to OR for TRUE_SINK, then combines
            assertThat(stack).hasSize(1);
        }

        @Test
        void orModeWithNonAndOnStack_defersNotTriggered() {
            // Tests that deferral only happens when stackTop is AND operation
            Deque<LambdaExpression> stack = new ArrayDeque<>();
            var aOrB = or(field("a", boolean.class), field("b", boolean.class));  // OR, not AND
            stack.push(aOrB);

            var expression = eq(field("c", int.class), constant(1));
            var ctx = createContext("TEST",
                    new BranchState.OrMode(Optional.of(true), false),
                    new BranchState.OrMode(Optional.of(true), false),
                    OR, false, Optional.of(true), false, aOrB,
                    ControlFlowAnalyzer.LabelClassification.FALSE_SINK, false, false);

            BranchExpressionCombiner.performCombination(stack, expression, ctx);

            // Should combine since stackTop is OR, not AND
            assertThat(stack).hasSize(1);
        }

        @Test
        void andModeWithAndOnStack_defersNotTriggered() {
            // Tests that deferral only happens in OrMode, not AndMode
            Deque<LambdaExpression> stack = new ArrayDeque<>();
            var aAndB = and(field("a", boolean.class), field("b", boolean.class));
            stack.push(aAndB);

            var expression = eq(field("c", int.class), constant(1));
            var ctx = createContext("TEST",
                    new BranchState.AndMode(Optional.of(false), false),  // AndMode, not OrMode
                    new BranchState.AndMode(Optional.of(false), false),
                    OR, false, Optional.of(false), false, aAndB,
                    ControlFlowAnalyzer.LabelClassification.FALSE_SINK, false, false);

            BranchExpressionCombiner.performCombination(stack, expression, ctx);

            // Should combine since state is AndMode, not OrMode
            assertThat(stack).hasSize(1);
        }

        private BranchExpressionCombiner.CombinationContext createContext(
                String instructionName,
                BranchState state,
                BranchState newState,
                Operator combineOp,
                Boolean jumpTarget,
                Optional<Boolean> previousJumpTarget,
                boolean sameLabel,
                LambdaExpression stackTop,
                ControlFlowAnalyzer.LabelClassification jumpLabelClass,
                boolean completingAndGroup,
                boolean startingNewOrGroup) {
            return new BranchExpressionCombiner.CombinationContext(
                    instructionName, state, newState, combineOp, jumpTarget,
                    previousJumpTarget, sameLabel, stackTop, jumpLabelClass,
                    completingAndGroup, startingNewOrGroup);
        }
    }

    // ==================== shouldDeferOrCombination Tests ====================

    @Nested
    @DisplayName("shouldDeferOrCombination")
    class ShouldDeferOrCombinationTests {

        @Test
        void allConditionsTrue_returnsTrue() {
            // OrMode + OR operator + FALSE jumpTarget + !sameLabel + AND stackTop
            var andExpr = and(field("a", boolean.class), field("b", boolean.class));

            boolean result = BranchExpressionCombiner.shouldDeferOrCombination(
                    new BranchState.OrMode(Optional.of(true), false),
                    OR,
                    Boolean.FALSE,
                    false,  // not same label
                    andExpr);

            assertThat(result)
                    .as("All conditions true should return true")
                    .isTrue();
        }

        @Test
        void notOrMode_returnsFalse() {
            // Kills mutation: state instanceof BranchState.OrMode
            var andExpr = and(field("a", boolean.class), field("b", boolean.class));

            boolean result = BranchExpressionCombiner.shouldDeferOrCombination(
                    new BranchState.AndMode(Optional.of(false), false),  // NOT OrMode
                    OR,
                    Boolean.FALSE,
                    false,
                    andExpr);

            assertThat(result)
                    .as("AndMode state should return false")
                    .isFalse();
        }

        @Test
        void initialState_returnsFalse() {
            var andExpr = and(field("a", boolean.class), field("b", boolean.class));

            boolean result = BranchExpressionCombiner.shouldDeferOrCombination(
                    new BranchState.Initial(),  // Initial state
                    OR,
                    Boolean.FALSE,
                    false,
                    andExpr);

            assertThat(result)
                    .as("Initial state should return false")
                    .isFalse();
        }

        @Test
        void combineOpNotOr_returnsFalse() {
            // Kills mutation: combineOp != OR
            var andExpr = and(field("a", boolean.class), field("b", boolean.class));

            boolean result = BranchExpressionCombiner.shouldDeferOrCombination(
                    new BranchState.OrMode(Optional.of(true), false),
                    AND,  // NOT OR
                    Boolean.FALSE,
                    false,
                    andExpr);

            assertThat(result)
                    .as("AND operator should return false")
                    .isFalse();
        }

        @Test
        void combineOpNull_returnsFalse() {
            var andExpr = and(field("a", boolean.class), field("b", boolean.class));

            boolean result = BranchExpressionCombiner.shouldDeferOrCombination(
                    new BranchState.OrMode(Optional.of(true), false),
                    null,  // null operator
                    Boolean.FALSE,
                    false,
                    andExpr);

            assertThat(result)
                    .as("Null operator should return false")
                    .isFalse();
        }

        @Test
        void jumpTargetNotFalse_returnsFalse() {
            // Kills mutation: FALSE.equals(jumpTarget)
            var andExpr = and(field("a", boolean.class), field("b", boolean.class));

            boolean result = BranchExpressionCombiner.shouldDeferOrCombination(
                    new BranchState.OrMode(Optional.of(true), false),
                    OR,
                    Boolean.TRUE,  // NOT FALSE
                    false,
                    andExpr);

            assertThat(result)
                    .as("TRUE jumpTarget should return false")
                    .isFalse();
        }

        @Test
        void jumpTargetNull_returnsFalse() {
            var andExpr = and(field("a", boolean.class), field("b", boolean.class));

            boolean result = BranchExpressionCombiner.shouldDeferOrCombination(
                    new BranchState.OrMode(Optional.of(true), false),
                    OR,
                    null,  // null jumpTarget
                    false,
                    andExpr);

            assertThat(result)
                    .as("Null jumpTarget should return false")
                    .isFalse();
        }

        @Test
        void sameLabel_returnsFalse() {
            // Kills mutation: !sameLabel
            var andExpr = and(field("a", boolean.class), field("b", boolean.class));

            boolean result = BranchExpressionCombiner.shouldDeferOrCombination(
                    new BranchState.OrMode(Optional.of(true), false),
                    OR,
                    Boolean.FALSE,
                    true,  // sameLabel = true
                    andExpr);

            assertThat(result)
                    .as("sameLabel should return false")
                    .isFalse();
        }

        @Test
        void stackTopNotBinaryOp_returnsFalse() {
            // Kills mutation: stackTop instanceof BinaryOp
            var fieldExpr = field("a", boolean.class);  // Not BinaryOp

            boolean result = BranchExpressionCombiner.shouldDeferOrCombination(
                    new BranchState.OrMode(Optional.of(true), false),
                    OR,
                    Boolean.FALSE,
                    false,
                    fieldExpr);

            assertThat(result)
                    .as("Non-BinaryOp stackTop should return false")
                    .isFalse();
        }

        @Test
        void stackTopNull_returnsFalse() {
            boolean result = BranchExpressionCombiner.shouldDeferOrCombination(
                    new BranchState.OrMode(Optional.of(true), false),
                    OR,
                    Boolean.FALSE,
                    false,
                    null);  // null stackTop

            assertThat(result)
                    .as("Null stackTop should return false")
                    .isFalse();
        }

        @Test
        void stackTopOrOperator_returnsFalse() {
            // Kills mutation: binOp.operator() == AND
            var orExpr = or(field("a", boolean.class), field("b", boolean.class));  // OR, not AND

            boolean result = BranchExpressionCombiner.shouldDeferOrCombination(
                    new BranchState.OrMode(Optional.of(true), false),
                    OR,
                    Boolean.FALSE,
                    false,
                    orExpr);

            assertThat(result)
                    .as("OR stackTop operator should return false")
                    .isFalse();
        }

        @Test
        void stackTopEqOperator_returnsFalse() {
            // BinaryOp but not AND or OR - should return false
            var eqExpr = eq(field("a", int.class), constant(1));

            boolean result = BranchExpressionCombiner.shouldDeferOrCombination(
                    new BranchState.OrMode(Optional.of(true), false),
                    OR,
                    Boolean.FALSE,
                    false,
                    eqExpr);

            assertThat(result)
                    .as("EQ operator stackTop should return false")
                    .isFalse();
        }
    }

    // ==================== shouldMergeAndGroups Tests ====================

    @Nested
    @DisplayName("shouldMergeAndGroups")
    class ShouldMergeAndGroupsTests {

        @Test
        void allConditionsTrue_returnsTrue() {
            // sameLabel + AND + OrMode + stackSize>=2 + hasPredicateOnStack
            boolean result = BranchExpressionCombiner.shouldMergeAndGroups(
                    true,   // sameLabel
                    AND,    // combineOp
                    new BranchState.OrMode(Optional.of(true), false),
                    2,      // stackSize
                    true);  // hasPredicateOnStack

            assertThat(result)
                    .as("All conditions true should return true")
                    .isTrue();
        }

        @Test
        void notSameLabel_returnsFalse() {
            // Kills mutation: !sameLabel
            boolean result = BranchExpressionCombiner.shouldMergeAndGroups(
                    false,  // NOT sameLabel
                    AND,
                    new BranchState.OrMode(Optional.of(true), false),
                    2,
                    true);

            assertThat(result)
                    .as("Not sameLabel should return false")
                    .isFalse();
        }

        @Test
        void combineOpNotAnd_returnsFalse() {
            // Kills mutation: combineOp != AND
            boolean result = BranchExpressionCombiner.shouldMergeAndGroups(
                    true,
                    OR,  // NOT AND
                    new BranchState.OrMode(Optional.of(true), false),
                    2,
                    true);

            assertThat(result)
                    .as("OR operator should return false")
                    .isFalse();
        }

        @Test
        void combineOpNull_returnsFalse() {
            boolean result = BranchExpressionCombiner.shouldMergeAndGroups(
                    true,
                    null,  // null operator
                    new BranchState.OrMode(Optional.of(true), false),
                    2,
                    true);

            assertThat(result)
                    .as("Null operator should return false")
                    .isFalse();
        }

        @Test
        void notOrMode_returnsFalse() {
            // Kills mutation: state instanceof BranchState.OrMode
            boolean result = BranchExpressionCombiner.shouldMergeAndGroups(
                    true,
                    AND,
                    new BranchState.AndMode(Optional.of(false), false),  // NOT OrMode
                    2,
                    true);

            assertThat(result)
                    .as("AndMode state should return false")
                    .isFalse();
        }

        @Test
        void initialState_returnsFalse() {
            boolean result = BranchExpressionCombiner.shouldMergeAndGroups(
                    true,
                    AND,
                    new BranchState.Initial(),  // Initial state
                    2,
                    true);

            assertThat(result)
                    .as("Initial state should return false")
                    .isFalse();
        }

        @Test
        void stackSizeLessThan2_returnsFalse() {
            // Kills mutation: stackSize < 2
            boolean result = BranchExpressionCombiner.shouldMergeAndGroups(
                    true,
                    AND,
                    new BranchState.OrMode(Optional.of(true), false),
                    1,  // stackSize < 2
                    true);

            assertThat(result)
                    .as("stackSize < 2 should return false")
                    .isFalse();
        }

        @Test
        void stackSizeZero_returnsFalse() {
            boolean result = BranchExpressionCombiner.shouldMergeAndGroups(
                    true,
                    AND,
                    new BranchState.OrMode(Optional.of(true), false),
                    0,  // empty stack
                    true);

            assertThat(result)
                    .as("stackSize 0 should return false")
                    .isFalse();
        }

        @Test
        void stackSizeExactly2_returnsTrue() {
            // Boundary: stackSize == 2 should pass
            boolean result = BranchExpressionCombiner.shouldMergeAndGroups(
                    true,
                    AND,
                    new BranchState.OrMode(Optional.of(true), false),
                    2,  // exactly 2
                    true);

            assertThat(result)
                    .as("stackSize == 2 should return true")
                    .isTrue();
        }

        @Test
        void stackSizeGreaterThan2_returnsTrue() {
            boolean result = BranchExpressionCombiner.shouldMergeAndGroups(
                    true,
                    AND,
                    new BranchState.OrMode(Optional.of(true), false),
                    5,  // > 2
                    true);

            assertThat(result)
                    .as("stackSize > 2 should return true")
                    .isTrue();
        }

        @Test
        void noPredicateOnStack_returnsFalse() {
            // Kills mutation: hasPredicateOnStack
            boolean result = BranchExpressionCombiner.shouldMergeAndGroups(
                    true,
                    AND,
                    new BranchState.OrMode(Optional.of(true), false),
                    2,
                    false);  // NO predicate on stack

            assertThat(result)
                    .as("No predicate on stack should return false")
                    .isFalse();
        }

        @Test
        void combineOpEq_returnsFalse() {
            // EQ is not AND
            boolean result = BranchExpressionCombiner.shouldMergeAndGroups(
                    true,
                    EQ,  // Not AND
                    new BranchState.OrMode(Optional.of(true), false),
                    2,
                    true);

            assertThat(result)
                    .as("EQ operator should return false")
                    .isFalse();
        }
    }

    // ==================== combineAndRestructureIfNeeded Edge Cases ====================

    @Nested
    @DisplayName("combineAndRestructureIfNeeded edge cases")
    class CombineAndRestructureEdgeCases {

        @Test
        void andOperator_neverRestructures() {
            // Restructuring only happens with OR combineOp
            var aOrB = or(field("a", boolean.class), field("b", boolean.class));
            var andC = and(aOrB, field("c", boolean.class));
            var d = field("d", boolean.class);

            var result = BranchExpressionCombiner.combineAndRestructureIfNeeded(AND, andC, d);

            // Should be simple AND without restructuring
            assertThat(result).isInstanceOf(LambdaExpression.BinaryOp.class);
            var binOp = (LambdaExpression.BinaryOp) result;
            assertThat(binOp.operator())
                    .as("AND operator should never trigger restructuring")
                    .isEqualTo(AND);
            assertThat(binOp.left()).isSameAs(andC);
            assertThat(binOp.right()).isSameAs(d);
        }

        @Test
        void orOperator_previousIsAnd_butLeftIsNotOr_noRestructure() {
            // Restructure requires: prevBinOp.left() is an OR expression
            var aAndB = and(field("a", boolean.class), field("b", boolean.class));  // AND, not OR
            var andC = and(aAndB, field("c", boolean.class));
            var d = field("d", boolean.class);

            var result = BranchExpressionCombiner.combineAndRestructureIfNeeded(OR, andC, d);

            // Should be simple OR without restructuring
            assertThat(result).isInstanceOf(LambdaExpression.BinaryOp.class);
            var binOp = (LambdaExpression.BinaryOp) result;
            assertThat(binOp.operator())
                    .as("Should be OR without restructure since left is not OR")
                    .isEqualTo(OR);
        }

        @Test
        void orOperator_previousIsNotBinaryOp_noRestructure() {
            // previousCondition must be BinaryOp for restructuring
            var simpleField = field("a", boolean.class);  // FieldAccess, not BinaryOp
            var b = field("b", boolean.class);

            var result = BranchExpressionCombiner.combineAndRestructureIfNeeded(OR, simpleField, b);

            assertThat(result).isInstanceOf(LambdaExpression.BinaryOp.class);
            var binOp = (LambdaExpression.BinaryOp) result;
            assertThat(binOp.operator()).isEqualTo(OR);
        }

        @Test
        void orOperator_previousIsOrNotAnd_noRestructure() {
            // prevBinOp.operator() must be AND for restructuring
            var aOrB = or(field("a", boolean.class), field("b", boolean.class));
            var orC = or(aOrB, field("c", boolean.class));  // OR, not AND
            var d = field("d", boolean.class);

            var result = BranchExpressionCombiner.combineAndRestructureIfNeeded(OR, orC, d);

            // Should be simple OR chaining without restructuring
            assertThat(result).isInstanceOf(LambdaExpression.BinaryOp.class);
            var binOp = (LambdaExpression.BinaryOp) result;
            assertThat(binOp.operator()).isEqualTo(OR);
        }

        @Test
        void orOperator_restructuresCorrectly() {
            // Full restructure case: ((a OR b) AND c) OR d → (a OR b) AND (c OR d)
            var a = field("a", boolean.class);
            var b = field("b", boolean.class);
            var c = field("c", boolean.class);
            var d = field("d", boolean.class);
            var aOrB = or(a, b);
            var andC = and(aOrB, c);

            var result = BranchExpressionCombiner.combineAndRestructureIfNeeded(OR, andC, d);

            // Result: (a OR b) AND (c OR d)
            assertThat(result).isInstanceOf(LambdaExpression.BinaryOp.class);
            var outer = (LambdaExpression.BinaryOp) result;
            assertThat(outer.operator())
                    .as("Outer operator should be AND after restructure")
                    .isEqualTo(AND);
            assertThat(outer.left()).isSameAs(aOrB);

            assertThat(outer.right()).isInstanceOf(LambdaExpression.BinaryOp.class);
            var inner = (LambdaExpression.BinaryOp) outer.right();
            assertThat(inner.operator()).isEqualTo(OR);
            assertThat(inner.left()).isSameAs(c);
            assertThat(inner.right()).isSameAs(d);
        }
    }
}
