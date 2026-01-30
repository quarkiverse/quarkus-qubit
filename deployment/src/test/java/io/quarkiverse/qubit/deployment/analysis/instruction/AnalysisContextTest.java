package io.quarkiverse.qubit.deployment.analysis.instruction;

import io.quarkiverse.qubit.deployment.ast.LambdaExpression;
import io.quarkiverse.qubit.deployment.ast.LambdaExpression.EntityPosition;
import io.quarkiverse.qubit.deployment.common.BytecodeAnalysisException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.LocalVariableNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.ArrayList;
import java.util.List;

import static io.quarkiverse.qubit.deployment.testutil.AstBuilders.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link AnalysisContext}.
 *
 * <p>Tests stack operations, entity parameter handling, array creation tracking,
 * variable name lookup, method finding, and control flow management.
 */
class AnalysisContextTest {

    private MethodNode testMethod;
    private AnalysisContext context;

    @BeforeEach
    void setUp() {
        testMethod = new MethodNode();
        testMethod.name = "testLambda";
        testMethod.desc = "(Ljava/lang/Object;)Z";
        testMethod.instructions = new InsnList();
        context = new AnalysisContext(testMethod, 0);
    }

    // ==================== Stack Operations Tests ====================

    @Nested
    @DisplayName("Stack operations")
    class StackOperationsTests {

        @Test
        void push_addsToStack() {
            var expr = constant(42);
            context.push(expr);

            assertThat(context.getStackSize()).isEqualTo(1);
            assertThat(context.isStackEmpty()).isFalse();
        }

        @Test
        void pop_removesFromStack() {
            var expr = constant(42);
            context.push(expr);

            var result = context.pop();

            assertThat(result).isEqualTo(expr);
            assertThat(context.isStackEmpty()).isTrue();
        }

        @Test
        void pop_onEmptyStack_throwsException() {
            assertThatThrownBy(() -> context.pop())
                    .isInstanceOf(BytecodeAnalysisException.class)
                    .hasMessageContaining("Stack underflow");
        }

        @Test
        void peek_returnsTopWithoutRemoving() {
            var expr = constant(42);
            context.push(expr);

            var result = context.peek();

            assertThat(result).isEqualTo(expr);
            assertThat(context.getStackSize()).isEqualTo(1);
        }

        @Test
        void peek_onEmptyStack_returnsNull() {
            var result = context.peek();

            assertThat(result).isNull();
        }

        @Test
        void peek_onNonEmptyStack_returnsTop() {
            // This test explicitly verifies that isEmpty() == false leads to peek() returning the element
            context.push(constant(1));
            context.push(constant(2));

            var result = context.peek();

            assertThat(result).isNotNull();
            assertThat(((LambdaExpression.Constant) result).value()).isEqualTo(2);
            assertThat(context.getStackSize()).isEqualTo(2);
        }

        @Test
        void popPair_returnsTwoElements() {
            context.push(constant(1));
            context.push(constant(2));

            var result = context.popPair();

            assertThat(((LambdaExpression.Constant) result.left()).value()).isEqualTo(1);
            assertThat(((LambdaExpression.Constant) result.right()).value()).isEqualTo(2);
            assertThat(context.isStackEmpty()).isTrue();
        }

        @Test
        void popPair_withOneElement_throwsException() {
            context.push(constant(1));

            assertThatThrownBy(() -> context.popPair())
                    .isInstanceOf(BytecodeAnalysisException.class)
                    .hasMessageContaining("Stack underflow");
        }

        @Test
        void popN_returnsElementsInOriginalOrder() {
            context.push(constant(1));
            context.push(constant(2));
            context.push(constant(3));

            var result = context.popN(3);

            assertThat(result).hasSize(3);
            assertThat(((LambdaExpression.Constant) result.get(0)).value()).isEqualTo(1);
            assertThat(((LambdaExpression.Constant) result.get(1)).value()).isEqualTo(2);
            assertThat(((LambdaExpression.Constant) result.get(2)).value()).isEqualTo(3);
        }

        @Test
        void popN_withInsufficientElements_throwsException() {
            context.push(constant(1));

            assertThatThrownBy(() -> context.popN(3))
                    .isInstanceOf(BytecodeAnalysisException.class)
                    .hasMessageContaining("Stack underflow");
        }

        @Test
        void discardN_discardsElementsAndReturnsCount() {
            context.push(constant(1));
            context.push(constant(2));

            int discarded = context.discardN(2);

            assertThat(discarded).isEqualTo(2);
            assertThat(context.isStackEmpty()).isTrue();
        }

        @Test
        void discardN_withFewerElements_discardsAvailable() {
            context.push(constant(1));

            int discarded = context.discardN(5);

            assertThat(discarded).isEqualTo(1);
            assertThat(context.isStackEmpty()).isTrue();
        }
    }

    // ==================== Entity Parameter Tests ====================

    @Nested
    @DisplayName("isEntityParameter")
    class IsEntityParameterTests {

        @Test
        void matchingEntityIndex_returnsTrue() {
            context = new AnalysisContext(testMethod, 1);

            assertThat(context.isEntityParameter(1)).isTrue();
        }

        @Test
        void nonMatchingIndex_returnsFalse() {
            context = new AnalysisContext(testMethod, 1);

            assertThat(context.isEntityParameter(0)).isFalse();
            assertThat(context.isEntityParameter(2)).isFalse();
        }

        @Test
        void inBiEntityMode_firstEntityIndex_returnsTrue() {
            context = new AnalysisContext(testMethod, 0, 1);

            assertThat(context.isEntityParameter(0)).isTrue();
        }

        @Test
        void inBiEntityMode_secondEntityIndex_returnsTrue() {
            context = new AnalysisContext(testMethod, 0, 1);

            assertThat(context.isEntityParameter(1)).isTrue();
        }

        @Test
        void inBiEntityMode_nonEntityIndex_returnsFalse() {
            context = new AnalysisContext(testMethod, 0, 1);

            assertThat(context.isEntityParameter(2)).isFalse();
            assertThat(context.isEntityParameter(3)).isFalse();
        }

        @Test
        void inSingleEntityMode_secondSlotNotMatched() {
            // Single entity mode should NOT match second entity slot even if value matches
            context = new AnalysisContext(testMethod, 0);

            assertThat(context.isEntityParameter(0)).isTrue();
            assertThat(context.isEntityParameter(1)).isFalse();
        }
    }

    // ==================== Entity Position Tests ====================

    @Nested
    @DisplayName("getEntityPosition")
    class GetEntityPositionTests {

        @Test
        void firstEntityIndex_returnsFIRST() {
            context = new AnalysisContext(testMethod, 0, 1);

            assertThat(context.getEntityPosition(0)).isEqualTo(EntityPosition.FIRST);
        }

        @Test
        void secondEntityIndex_returnsSECOND() {
            context = new AnalysisContext(testMethod, 0, 1);

            assertThat(context.getEntityPosition(1)).isEqualTo(EntityPosition.SECOND);
        }

        @Test
        void nonEntityIndex_returnsNull() {
            context = new AnalysisContext(testMethod, 0, 1);

            assertThat(context.getEntityPosition(2)).isNull();
        }

        @Test
        void inSingleEntityMode_firstIndex_returnsFIRST() {
            context = new AnalysisContext(testMethod, 0);

            assertThat(context.getEntityPosition(0)).isEqualTo(EntityPosition.FIRST);
        }

        @Test
        void inSingleEntityMode_otherIndex_returnsNull() {
            context = new AnalysisContext(testMethod, 0);

            assertThat(context.getEntityPosition(1)).isNull();
            assertThat(context.getEntityPosition(2)).isNull();
        }
    }

    // ==================== Variable Name Lookup Tests ====================

    @Nested
    @DisplayName("getVariableNameForSlot")
    class GetVariableNameForSlotTests {

        @Test
        void withNullLocalVariables_returnsNull() {
            testMethod.localVariables = null;
            context = new AnalysisContext(testMethod, 0);

            assertThat(context.getVariableNameForSlot(0)).isNull();
        }

        @Test
        void withEmptyLocalVariables_returnsNull() {
            testMethod.localVariables = new ArrayList<>();
            context = new AnalysisContext(testMethod, 0);

            assertThat(context.getVariableNameForSlot(0)).isNull();
        }

        @Test
        void withMatchingSlot_returnsVariableName() {
            LocalVariableNode localVar = new LocalVariableNode("entity", "Ljava/lang/Object;", null, null, null, 0);
            testMethod.localVariables = List.of(localVar);
            context = new AnalysisContext(testMethod, 0);

            assertThat(context.getVariableNameForSlot(0)).isEqualTo("entity");
        }

        @Test
        void withNonMatchingSlot_returnsNull() {
            LocalVariableNode localVar = new LocalVariableNode("entity", "Ljava/lang/Object;", null, null, null, 0);
            testMethod.localVariables = List.of(localVar);
            context = new AnalysisContext(testMethod, 0);

            assertThat(context.getVariableNameForSlot(1)).isNull();
        }

        @Test
        void withMultipleVariables_returnsCorrectName() {
            LocalVariableNode var0 = new LocalVariableNode("this", "Ljava/lang/Object;", null, null, null, 0);
            LocalVariableNode var1 = new LocalVariableNode("entity", "Lcom/example/Entity;", null, null, null, 1);
            LocalVariableNode var2 = new LocalVariableNode("index", "I", null, null, null, 2);
            testMethod.localVariables = List.of(var0, var1, var2);
            context = new AnalysisContext(testMethod, 1);

            assertThat(context.getVariableNameForSlot(0)).isEqualTo("this");
            assertThat(context.getVariableNameForSlot(1)).isEqualTo("entity");
            assertThat(context.getVariableNameForSlot(2)).isEqualTo("index");
            assertThat(context.getVariableNameForSlot(3)).isNull();
        }
    }

    // ==================== Array Creation Tracking Tests ====================

    @Nested
    @DisplayName("Array creation tracking")
    class ArrayCreationTests {

        @Test
        void startArrayCreation_enablesArrayMode() {
            context.startArrayCreation("Ljava/lang/String;");

            assertThat(context.isInArrayCreation()).isTrue();
            assertThat(context.getPendingArrayElementType()).isEqualTo("Ljava/lang/String;");
            assertThat(context.getPendingArrayElements()).isEmpty();
        }

        @Test
        void addArrayElement_addsToCollection() {
            context.startArrayCreation("Ljava/lang/String;");
            context.addArrayElement(constant("hello"));
            context.addArrayElement(constant("world"));

            assertThat(context.getPendingArrayElements()).hasSize(2);
        }

        @Test
        void addArrayElement_withoutStarting_throwsException() {
            assertThatThrownBy(() -> context.addArrayElement(constant("hello")))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("not in array creation mode");
        }

        @Test
        void completeArrayCreation_returnsArrayCreation() {
            context.startArrayCreation("Ljava/lang/String;");
            context.addArrayElement(constant("hello"));

            var result = context.completeArrayCreation();

            assertThat(result).isNotNull();
            assertThat(result.elementType()).isEqualTo("Ljava/lang/String;");
            assertThat(result.elements()).hasSize(1);
        }

        @Test
        void completeArrayCreation_resetsState() {
            context.startArrayCreation("Ljava/lang/String;");
            context.addArrayElement(constant("hello"));
            context.completeArrayCreation();

            assertThat(context.isInArrayCreation()).isFalse();
            assertThat(context.getPendingArrayElementType()).isNull();
            assertThat(context.getPendingArrayElements()).isNull();
        }

        @Test
        void completeArrayCreation_withoutStarting_returnsNull() {
            var result = context.completeArrayCreation();

            assertThat(result).isNull();
        }

        @Test
        void completeArrayCreation_withNullElementType_returnsNull() {
            // pendingArrayElementType is null but we never started
            assertThat(context.completeArrayCreation()).isNull();
        }

        @Test
        void isInArrayCreation_beforeStart_returnsFalse() {
            assertThat(context.isInArrayCreation()).isFalse();
        }
    }

    // ==================== Find Method Tests ====================

    @Nested
    @DisplayName("findMethod")
    class FindMethodTests {

        @Test
        void withoutNestedLambdaSupport_returnsNull() {
            // Default context has no nested lambda support
            var result = context.findMethod("lambda$0", "()Ljava/lang/String;");

            assertThat(result).isNull();
        }

        @Test
        void withMatchingMethod_returnsMethod() {
            MethodNode nestedMethod = new MethodNode();
            nestedMethod.name = "lambda$0";
            nestedMethod.desc = "(Ljava/lang/Object;)Ljava/lang/String;";

            var support = new AnalysisContext.NestedLambdaSupport(
                    List.of(nestedMethod),
                    (m, i) -> null
            );
            context = new AnalysisContext(testMethod, 0, true, support);

            var result = context.findMethod("lambda$0", "(Ljava/lang/Object;)Ljava/lang/String;");

            assertThat(result).isSameAs(nestedMethod);
        }

        @Test
        void withWrongMethodName_returnsNull() {
            MethodNode nestedMethod = new MethodNode();
            nestedMethod.name = "lambda$0";
            nestedMethod.desc = "(Ljava/lang/Object;)Ljava/lang/String;";

            var support = new AnalysisContext.NestedLambdaSupport(
                    List.of(nestedMethod),
                    (m, i) -> null
            );
            context = new AnalysisContext(testMethod, 0, true, support);

            var result = context.findMethod("lambda$1", "(Ljava/lang/Object;)Ljava/lang/String;");

            assertThat(result).isNull();
        }

        @Test
        void withWrongDescriptor_returnsNull() {
            MethodNode nestedMethod = new MethodNode();
            nestedMethod.name = "lambda$0";
            nestedMethod.desc = "(Ljava/lang/Object;)Ljava/lang/String;";

            var support = new AnalysisContext.NestedLambdaSupport(
                    List.of(nestedMethod),
                    (m, i) -> null
            );
            context = new AnalysisContext(testMethod, 0, true, support);

            var result = context.findMethod("lambda$0", "(I)Ljava/lang/String;");

            assertThat(result).isNull();
        }

        @Test
        void withMultipleMethods_findsCorrectOne() {
            MethodNode method1 = new MethodNode();
            method1.name = "lambda$0";
            method1.desc = "(I)V";

            MethodNode method2 = new MethodNode();
            method2.name = "lambda$1";
            method2.desc = "(Ljava/lang/Object;)Ljava/lang/String;";

            MethodNode method3 = new MethodNode();
            method3.name = "lambda$0";
            method3.desc = "(Ljava/lang/Object;)Z";

            var support = new AnalysisContext.NestedLambdaSupport(
                    List.of(method1, method2, method3),
                    (m, i) -> null
            );
            context = new AnalysisContext(testMethod, 0, true, support);

            // Find method2 by exact name AND descriptor match
            var result = context.findMethod("lambda$1", "(Ljava/lang/Object;)Ljava/lang/String;");
            assertThat(result).isSameAs(method2);

            // Find method3: same name as method1 but different descriptor
            var result2 = context.findMethod("lambda$0", "(Ljava/lang/Object;)Z");
            assertThat(result2).isSameAs(method3);
        }
    }

    // ==================== Control Flow Tests ====================

    @Nested
    @DisplayName("Control flow")
    class ControlFlowTests {

        @Test
        void getLabelClassifications_returnsNonNullMap() {
            var classifications = context.getLabelClassifications();

            assertThat(classifications).isNotNull();
        }

        @Test
        void getLabelToValue_returnsNonNullMap() {
            var labelToValue = context.getLabelToValue();

            assertThat(labelToValue).isNotNull();
        }

        @Test
        void hasSeenBranch_initiallyFalse() {
            assertThat(context.hasSeenBranch()).isFalse();
        }

        @Test
        void markBranchSeen_setsToTrue() {
            context.markBranchSeen();

            assertThat(context.hasSeenBranch()).isTrue();
        }

        @Test
        void getBranchCoordinator_returnsNonNull() {
            assertThat(context.getBranchCoordinator()).isNotNull();
        }
    }

    // ==================== Constructor Variations Tests ====================

    @Nested
    @DisplayName("Constructor variations")
    class ConstructorTests {

        @Test
        void singleEntityConstructor_setsCorrectMode() {
            context = new AnalysisContext(testMethod, 1);

            assertThat(context.getFirstEntityParameterIndex()).isEqualTo(1);
            assertThat(context.isBiEntityMode()).isFalse();
            assertThat(context.isGroupContextMode()).isFalse();
            assertThat(context.hasNestedLambdaSupport()).isFalse();
        }

        @Test
        void biEntityConstructor_setsBothIndices() {
            context = new AnalysisContext(testMethod, 0, 1);

            assertThat(context.getFirstEntityParameterIndex()).isZero();
            assertThat(context.getSecondEntityParameterIndex()).isEqualTo(1);
            assertThat(context.isBiEntityMode()).isTrue();
        }

        @Test
        void groupContextConstructor_setsGroupMode() {
            var support = new AnalysisContext.NestedLambdaSupport(
                    List.of(),
                    (m, i) -> null
            );
            context = new AnalysisContext(testMethod, 0, support);

            assertThat(context.isGroupContextMode()).isTrue();
            assertThat(context.hasNestedLambdaSupport()).isTrue();
        }

        @Test
        void biEntityWithNestedSupport_setBothModesCorrectly() {
            var support = new AnalysisContext.NestedLambdaSupport(
                    List.of(),
                    (m, i) -> null
            );
            context = new AnalysisContext(testMethod, 0, 1, support);

            assertThat(context.isBiEntityMode()).isTrue();
            assertThat(context.hasNestedLambdaSupport()).isTrue();
        }
    }

    // ==================== Instruction Access Tests ====================

    @Nested
    @DisplayName("Instruction access")
    class InstructionAccessTests {

        @Test
        void getInstructions_returnsMethodInstructions() {
            assertThat(context.getInstructions()).isSameAs(testMethod.instructions);
        }

        @Test
        void getInstructionCount_returnsSize() {
            assertThat(context.getInstructionCount()).isEqualTo(testMethod.instructions.size());
        }

        @Test
        void getCurrentInstructionIndex_initiallyZero() {
            assertThat(context.getCurrentInstructionIndex()).isZero();
        }

        @Test
        void setCurrentInstructionIndex_updatesValue() {
            context.setCurrentInstructionIndex(5);

            assertThat(context.getCurrentInstructionIndex()).isEqualTo(5);
        }

        @Test
        void getMethod_returnsMethodNode() {
            assertThat(context.getMethod()).isSameAs(testMethod);
        }
    }

    // ==================== Analyze Nested Lambda Tests ====================

    @Nested
    @DisplayName("analyzeNestedLambda")
    class AnalyzeNestedLambdaTests {

        @Test
        void withoutSupport_returnsNull() {
            var nestedMethod = new MethodNode();

            var result = context.analyzeNestedLambda(nestedMethod, 0);

            assertThat(result).isNull();
        }

        @Test
        void withSupport_callsAnalyzer() {
            var nestedMethod = new MethodNode();
            var expectedResult = constant("analyzed");

            var support = new AnalysisContext.NestedLambdaSupport(
                    List.of(nestedMethod),
                    (m, i) -> expectedResult
            );
            context = new AnalysisContext(testMethod, 0, true, support);

            var result = context.analyzeNestedLambda(nestedMethod, 0);

            assertThat(result).isSameAs(expectedResult);
        }
    }

    // ==================== Nested Lambda Support Record Tests ====================

    @Nested
    @DisplayName("NestedLambdaSupport")
    class NestedLambdaSupportTests {

        @Test
        void constructor_validatesNonNullClassMethods() {
            assertThatThrownBy(() -> new AnalysisContext.NestedLambdaSupport(null, (m, i) -> null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("classMethods");
        }

        @Test
        void constructor_validatesNonNullAnalyzer() {
            assertThatThrownBy(() -> new AnalysisContext.NestedLambdaSupport(List.of(), null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("analyzer");
        }

        @Test
        void constructor_createsDefensiveCopyOfMethods() {
            var methods = new ArrayList<MethodNode>();
            methods.add(new MethodNode());

            var support = new AnalysisContext.NestedLambdaSupport(methods, (m, i) -> null);
            methods.clear();

            assertThat(support.classMethods()).hasSize(1);
        }
    }
}
