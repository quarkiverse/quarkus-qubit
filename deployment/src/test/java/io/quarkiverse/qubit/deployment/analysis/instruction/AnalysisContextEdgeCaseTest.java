package io.quarkiverse.qubit.deployment.analysis.instruction;

import io.quarkiverse.qubit.deployment.ast.LambdaExpression;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodNode;

import java.util.List;

import static io.quarkiverse.qubit.deployment.testutil.AstBuilders.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Edge case tests for AnalysisContext.
 *
 * <p>TEST-001: Tests for null handling, empty collections, boundary conditions,
 * and invalid state scenarios in AnalysisContext.
 */
class AnalysisContextEdgeCaseTest {

    private MethodNode testMethod;
    private AnalysisContext context;

    @BeforeEach
    void setUp() {
        // Create a minimal method node for testing
        testMethod = new MethodNode();
        testMethod.name = "testLambda";
        testMethod.desc = "(Ljava/lang/Object;)Z";
        testMethod.instructions = new InsnList();
        context = new AnalysisContext(testMethod, 0);
    }

    // ==================== Stack Empty State Tests ====================

    @Nested
    class EmptyStackTests {

        @Test
        void pop_onEmptyStack_returnsNull() {
            assertThat(context.pop()).isNull();
        }

        @Test
        void peek_onEmptyStack_returnsNull() {
            assertThat(context.peek()).isNull();
        }

        @Test
        void isStackEmpty_onEmptyStack_returnsTrue() {
            assertThat(context.isStackEmpty()).isTrue();
        }

        @Test
        void getStackSize_onEmptyStack_returnsZero() {
            assertThat(context.getStackSize()).isZero();
        }

        @Test
        void popPair_onEmptyStack_returnsNull() {
            assertThat(context.popPair()).isNull();
        }

        @Test
        void popN_onEmptyStack_returnsNull() {
            assertThat(context.popN(1)).isNull();
            assertThat(context.popN(5)).isNull();
        }

        @Test
        void popN_withZeroElements_onEmptyStack_returnsEmptyList() {
            List<LambdaExpression> result = context.popN(0);
            assertThat(result).isNotNull().isEmpty();
        }

        @Test
        void discardN_onEmptyStack_returnsZero() {
            int discarded = context.discardN(5);
            assertThat(discarded).isZero();
        }

        @Test
        void discardN_withZero_onEmptyStack_returnsZero() {
            int discarded = context.discardN(0);
            assertThat(discarded).isZero();
        }
    }

    // ==================== Stack Single Element Tests ====================

    @Nested
    class SingleElementStackTests {

        @BeforeEach
        void pushOneElement() {
            context.push(constant(42));
        }

        @Test
        void popPair_withOneElement_returnsNull() {
            assertThat(context.popPair()).isNull();
            // Element should still be on stack
            assertThat(context.getStackSize()).isEqualTo(1);
        }

        @Test
        void popN_withInsufficientElements_returnsNull() {
            assertThat(context.popN(2)).isNull();
            assertThat(context.popN(10)).isNull();
            // Element should still be on stack
            assertThat(context.getStackSize()).isEqualTo(1);
        }

        @Test
        void discardN_discardsSingleElement() {
            int discarded = context.discardN(1);
            assertThat(discarded).isEqualTo(1);
            assertThat(context.isStackEmpty()).isTrue();
        }

        @Test
        void discardN_partialDiscard_whenMoreRequestedThanAvailable() {
            int discarded = context.discardN(5);
            assertThat(discarded).isEqualTo(1);
            assertThat(context.isStackEmpty()).isTrue();
        }
    }

    // ==================== Stack Multiple Elements Tests ====================

    @Nested
    class MultipleElementStackTests {

        @BeforeEach
        void pushElements() {
            context.push(constant(1)); // Bottom
            context.push(constant(2));
            context.push(constant(3)); // Top
        }

        @Test
        void popPair_returnsCorrectOrder() {
            AnalysisContext.PopPairResult result = context.popPair();

            assertThat(result).isNotNull();
            // Left was second-to-top, right was top
            assertConstantValue(result.left(), 2);
            assertConstantValue(result.right(), 3);
            assertThat(context.getStackSize()).isEqualTo(1);
        }

        @Test
        void popN_returnsReversedOrder() {
            List<LambdaExpression> result = context.popN(3);

            assertThat(result).hasSize(3);
            // Result should be in reverse stack order (first = deepest)
            assertConstantValue(result.get(0), 1);
            assertConstantValue(result.get(1), 2);
            assertConstantValue(result.get(2), 3);
            assertThat(context.isStackEmpty()).isTrue();
        }

        @Test
        void discardN_discardsExactCount() {
            int discarded = context.discardN(2);

            assertThat(discarded).isEqualTo(2);
            assertThat(context.getStackSize()).isEqualTo(1);
            assertConstantValue(context.peek(), 1);
        }

        @Test
        void discardN_discardsAllIfRequested() {
            int discarded = context.discardN(3);

            assertThat(discarded).isEqualTo(3);
            assertThat(context.isStackEmpty()).isTrue();
        }
    }

    // ==================== Array Creation Edge Cases ====================

    @Nested
    class ArrayCreationTests {

        @Test
        void addArrayElement_whenNotInArrayMode_throwsException() {
            assertThatThrownBy(() -> context.addArrayElement(constant(1)))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("not in array creation mode");
        }

        @Test
        void isInArrayCreation_beforeStart_returnsFalse() {
            assertThat(context.isInArrayCreation()).isFalse();
        }

        @Test
        void isInArrayCreation_afterStart_returnsTrue() {
            context.startArrayCreation("java/lang/Object");
            assertThat(context.isInArrayCreation()).isTrue();
        }

        @Test
        void completeArrayCreation_whenNotStarted_returnsNull() {
            assertThat(context.completeArrayCreation()).isNull();
        }

        @Test
        void completeArrayCreation_withEmptyArray_returnsEmptyArrayCreation() {
            context.startArrayCreation("java/lang/Object");

            LambdaExpression.ArrayCreation result = context.completeArrayCreation();

            assertThat(result).isNotNull();
            assertThat(result.elements()).isEmpty();
            assertThat(result.elementType()).isEqualTo("java/lang/Object");
        }

        @Test
        void completeArrayCreation_resetsState() {
            context.startArrayCreation("java/lang/Object");
            context.addArrayElement(constant(1));
            context.completeArrayCreation();

            assertThat(context.isInArrayCreation()).isFalse();
            assertThat(context.getPendingArrayElements()).isNull();
            assertThat(context.getPendingArrayElementType()).isNull();
        }
    }

    // ==================== Nested Lambda Support Tests ====================

    @Nested
    class NestedLambdaSupportTests {

        @Test
        void hasNestedLambdaSupport_withoutConfiguration_returnsFalse() {
            assertThat(context.hasNestedLambdaSupport()).isFalse();
        }

        @Test
        void findMethod_withoutNestedSupport_returnsNull() {
            assertThat(context.findMethod("anyMethod", "()V")).isNull();
        }

        @Test
        void analyzeNestedLambda_withoutNestedSupport_returnsNull() {
            MethodNode dummyMethod = new MethodNode();
            dummyMethod.name = "nested";
            dummyMethod.desc = "()V";

            assertThat(context.analyzeNestedLambda(dummyMethod, 0)).isNull();
        }

        @Test
        void nestedLambdaSupport_validatesNullClassMethods() {
            assertThatThrownBy(() -> new AnalysisContext.NestedLambdaSupport(null, (m, i) -> null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("classMethods");
        }

        @Test
        void nestedLambdaSupport_validatesNullAnalyzer() {
            assertThatThrownBy(() -> new AnalysisContext.NestedLambdaSupport(List.of(), null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("analyzer");
        }

        @Test
        void nestedLambdaSupport_defensivelyCopiesMethodList() {
            var methods = new java.util.ArrayList<MethodNode>();
            methods.add(testMethod);

            var support = new AnalysisContext.NestedLambdaSupport(methods, (m, i) -> null);

            // Modifying original list should not affect support
            methods.add(new MethodNode());
            assertThat(support.classMethods()).hasSize(1);
        }
    }

    // ==================== Entity Position Tests ====================

    @Nested
    class EntityPositionTests {

        @Test
        void getEntityPosition_forNonEntitySlot_returnsNull() {
            // Slot 99 is not an entity parameter
            assertThat(context.getEntityPosition(99)).isNull();
        }

        @Test
        void getEntityPosition_forEntitySlot_returnsFirst() {
            // Slot 0 is the entity parameter in our test context
            assertThat(context.getEntityPosition(0))
                    .isEqualTo(LambdaExpression.EntityPosition.FIRST);
        }

        @Test
        void isEntityParameter_forNonEntitySlot_returnsFalse() {
            assertThat(context.isEntityParameter(99)).isFalse();
        }

        @Test
        void isEntityParameter_forEntitySlot_returnsTrue() {
            assertThat(context.isEntityParameter(0)).isTrue();
        }
    }

    // ==================== Bi-Entity Mode Tests ====================

    @Nested
    class BiEntityModeTests {

        private AnalysisContext biEntityContext;

        @BeforeEach
        void setUp() {
            MethodNode biMethod = new MethodNode();
            biMethod.name = "biEntityLambda";
            biMethod.desc = "(Ljava/lang/Object;Ljava/lang/Object;)Z";
            biMethod.instructions = new InsnList();
            biEntityContext = new AnalysisContext(biMethod, 0, 1);
        }

        @Test
        void isBiEntityMode_returnsTrue() {
            assertThat(biEntityContext.isBiEntityMode()).isTrue();
        }

        @Test
        void getSecondEntityParameterIndex_returnsCorrectValue() {
            assertThat(biEntityContext.getSecondEntityParameterIndex()).isEqualTo(1);
        }

        @Test
        void getEntityPosition_forFirstEntity_returnsFirst() {
            assertThat(biEntityContext.getEntityPosition(0))
                    .isEqualTo(LambdaExpression.EntityPosition.FIRST);
        }

        @Test
        void getEntityPosition_forSecondEntity_returnsSecond() {
            assertThat(biEntityContext.getEntityPosition(1))
                    .isEqualTo(LambdaExpression.EntityPosition.SECOND);
        }

        @Test
        void isEntityParameter_recognizesBothEntities() {
            assertThat(biEntityContext.isEntityParameter(0)).isTrue();
            assertThat(biEntityContext.isEntityParameter(1)).isTrue();
            assertThat(biEntityContext.isEntityParameter(2)).isFalse();
        }
    }

    // ==================== Group Context Mode Tests ====================

    @Nested
    class GroupContextModeTests {

        private AnalysisContext groupContext;

        @BeforeEach
        void setUp() {
            MethodNode groupMethod = new MethodNode();
            groupMethod.name = "groupLambda";
            groupMethod.desc = "(Lio/quarkiverse/qubit/runtime/Group;)Ljava/lang/Object;";
            groupMethod.instructions = new InsnList();

            var nestedSupport = new AnalysisContext.NestedLambdaSupport(
                    List.of(groupMethod),
                    (m, i) -> constant(1)
            );
            groupContext = new AnalysisContext(groupMethod, 0, nestedSupport);
        }

        @Test
        void isGroupContextMode_returnsTrue() {
            assertThat(groupContext.isGroupContextMode()).isTrue();
        }

        @Test
        void hasNestedLambdaSupport_returnsTrue() {
            assertThat(groupContext.hasNestedLambdaSupport()).isTrue();
        }
    }

    // ==================== Instruction Index Boundary Tests ====================

    @Nested
    class InstructionIndexTests {

        @Test
        void currentInstructionIndex_initiallyZero() {
            assertThat(context.getCurrentInstructionIndex()).isZero();
        }

        @Test
        void setCurrentInstructionIndex_updatesValue() {
            context.setCurrentInstructionIndex(42);
            assertThat(context.getCurrentInstructionIndex()).isEqualTo(42);
        }

        @Test
        void instructionCount_onEmptyInstructionList_returnsZero() {
            assertThat(context.getInstructionCount()).isZero();
        }
    }

    // ==================== Branch State Tests ====================

    @Nested
    class BranchStateTests {

        @Test
        void hasSeenBranch_initiallyFalse() {
            assertThat(context.hasSeenBranch()).isFalse();
        }

        @Test
        void markBranchSeen_setsHasSeenBranchTrue() {
            context.markBranchSeen();
            assertThat(context.hasSeenBranch()).isTrue();
        }

        @Test
        void markBranchSeen_isIdempotent() {
            context.markBranchSeen();
            context.markBranchSeen();
            assertThat(context.hasSeenBranch()).isTrue();
        }
    }

    // ==================== Helper Methods ====================

    private void assertConstantValue(LambdaExpression expr, Object expectedValue) {
        assertThat(expr)
                .as("Expression should be a Constant")
                .isInstanceOf(LambdaExpression.Constant.class);
        assertThat(((LambdaExpression.Constant) expr).value())
                .as("Constant value should be %s", expectedValue)
                .isEqualTo(expectedValue);
    }
}
