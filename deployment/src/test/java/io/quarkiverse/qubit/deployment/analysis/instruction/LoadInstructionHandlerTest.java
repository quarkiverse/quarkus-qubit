package io.quarkiverse.qubit.deployment.analysis.instruction;

import io.quarkiverse.qubit.deployment.ast.LambdaExpression;
import io.quarkiverse.qubit.deployment.ast.LambdaExpression.BiEntityFieldAccess;
import io.quarkiverse.qubit.deployment.ast.LambdaExpression.BiEntityParameter;
import io.quarkiverse.qubit.deployment.ast.LambdaExpression.BiEntityPathExpression;
import io.quarkiverse.qubit.deployment.ast.LambdaExpression.CapturedVariable;
import io.quarkiverse.qubit.deployment.ast.LambdaExpression.CorrelatedVariable;
import io.quarkiverse.qubit.deployment.ast.LambdaExpression.EntityPosition;
import io.quarkiverse.qubit.deployment.ast.LambdaExpression.FieldAccess;
import io.quarkiverse.qubit.deployment.ast.LambdaExpression.GroupParameter;
import io.quarkiverse.qubit.deployment.ast.LambdaExpression.Parameter;
import io.quarkiverse.qubit.deployment.ast.LambdaExpression.PathExpression;
import io.quarkiverse.qubit.deployment.common.BytecodeAnalysisException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import static io.quarkiverse.qubit.deployment.testutil.AstBuilders.*;
import static io.quarkiverse.qubit.deployment.testutil.fixtures.AsmFixtures.testMethod;
import static io.quarkiverse.qubit.deployment.testutil.fixtures.AnalysisContextFixtures.contextFor;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.objectweb.asm.Opcodes.*;

/**
 * Unit tests for {@link LoadInstructionHandler}.
 *
 * <p>Tests load instructions: ALOAD, primitives (ILOAD, LLOAD, FLOAD, DLOAD), and GETFIELD.
 * Covers single-entity, bi-entity (join), and group context modes.
 */
class LoadInstructionHandlerTest {

    private final LoadInstructionHandler handler = LoadInstructionHandler.INSTANCE;
    private MethodNode testMethod;
    private AnalysisContext context;

    @BeforeEach
    void setUp() {
        testMethod = testMethod().build();
        context = contextFor(testMethod, 0);
    }

    // ==================== canHandle Tests ====================

    @Nested
    @DisplayName("canHandle")
    class CanHandleTests {

        @Test
        void aload_canHandle() {
            var insn = new VarInsnNode(ALOAD, 0);
            assertThat(handler.canHandle(insn)).isTrue();
        }

        @Test
        void iload_canHandle() {
            var insn = new VarInsnNode(ILOAD, 0);
            assertThat(handler.canHandle(insn)).isTrue();
        }

        @Test
        void lload_canHandle() {
            var insn = new VarInsnNode(LLOAD, 0);
            assertThat(handler.canHandle(insn)).isTrue();
        }

        @Test
        void fload_canHandle() {
            var insn = new VarInsnNode(FLOAD, 0);
            assertThat(handler.canHandle(insn)).isTrue();
        }

        @Test
        void dload_canHandle() {
            var insn = new VarInsnNode(DLOAD, 0);
            assertThat(handler.canHandle(insn)).isTrue();
        }

        @Test
        void getfield_canHandle() {
            var insn = new FieldInsnNode(GETFIELD, "com/example/Entity", "name", "Ljava/lang/String;");
            assertThat(handler.canHandle(insn)).isTrue();
        }

        @Test
        void astore_cannotHandle() {
            var insn = new VarInsnNode(ASTORE, 0);
            assertThat(handler.canHandle(insn)).isFalse();
        }

        @Test
        void istore_cannotHandle() {
            var insn = new VarInsnNode(ISTORE, 0);
            assertThat(handler.canHandle(insn)).isFalse();
        }

        @Test
        void nop_cannotHandle() {
            var insn = new InsnNode(NOP);
            assertThat(handler.canHandle(insn)).isFalse();
        }
    }

    // ==================== handle - ALOAD Tests ====================

    @Nested
    @DisplayName("handle - ALOAD")
    class HandleALoadTests {

        @Test
        void aload_entityParameter_pushesParameter() {
            var insn = new VarInsnNode(ALOAD, 0);
            handler.handle(insn, context);

            assertThat(context.getStackSize()).isEqualTo(1);
            var result = context.pop();
            assertThat(result).isInstanceOf(Parameter.class);
            var param = (Parameter) result;
            assertThat(param.name()).isEqualTo("entity");
        }

        @Test
        void aload_capturedVariable_pushesCapturedVariable() {
            // Method with two parameters: (Object entity, String city)
            testMethod.desc = "(Ljava/lang/Object;Ljava/lang/String;)Z";
            context = new AnalysisContext(testMethod, 0);

            // Slot 1 is the second parameter (captured)
            var insn = new VarInsnNode(ALOAD, 1);
            handler.handle(insn, context);

            assertThat(context.getStackSize()).isEqualTo(1);
            var result = context.pop();
            assertThat(result).isInstanceOf(CapturedVariable.class);
            var captured = (CapturedVariable) result;
            assertThat(captured.index()).isEqualTo(1);
            assertThat(captured.type()).isEqualTo(String.class);
        }

        @Test
        void aload_biEntityMode_pushesBiEntityParameter() {
            // Set up bi-entity mode with positions at slots 0 and 1
            testMethod.desc = "(Ljava/lang/Object;Ljava/lang/Object;)Z";
            context = new AnalysisContext(testMethod, 0, 1);

            var insn = new VarInsnNode(ALOAD, 1);
            handler.handle(insn, context);

            assertThat(context.getStackSize()).isEqualTo(1);
            var result = context.pop();
            assertThat(result).isInstanceOf(BiEntityParameter.class);
            var biParam = (BiEntityParameter) result;
            assertThat(biParam.position()).isEqualTo(EntityPosition.SECOND);
        }

        @Test
        void aload_groupContextMode_pushesGroupParameter() {
            context = new AnalysisContext(testMethod, 0, true, null);

            var insn = new VarInsnNode(ALOAD, 0);
            handler.handle(insn, context);

            assertThat(context.getStackSize()).isEqualTo(1);
            var result = context.pop();
            assertThat(result).isInstanceOf(GroupParameter.class);
        }

        @Test
        void aload_invalidSlot_throwsException() {
            // Slot 5 does not correspond to any parameter in (Object)Z
            var insn = new VarInsnNode(ALOAD, 5);

            assertThatThrownBy(() -> handler.handle(insn, context))
                    .isInstanceOf(BytecodeAnalysisException.class)
                    .hasMessageContaining("does not correspond to a method parameter");
        }
    }

    // ==================== handle - Primitive Load Tests ====================

    @Nested
    @DisplayName("handle - Primitive Loads")
    class HandlePrimitiveLoadTests {

        @Test
        void iload_capturedInt_pushesCapturedVariable() {
            testMethod.desc = "(Ljava/lang/Object;I)Z";
            context = new AnalysisContext(testMethod, 0);

            var insn = new VarInsnNode(ILOAD, 1);
            handler.handle(insn, context);

            assertThat(context.getStackSize()).isEqualTo(1);
            var result = context.pop();
            assertThat(result).isInstanceOf(CapturedVariable.class);
            var captured = (CapturedVariable) result;
            assertThat(captured.type()).isEqualTo(int.class);
        }

        @Test
        void lload_capturedLong_pushesCapturedVariable() {
            testMethod.desc = "(Ljava/lang/Object;J)Z";
            context = new AnalysisContext(testMethod, 0);

            var insn = new VarInsnNode(LLOAD, 1);
            handler.handle(insn, context);

            assertThat(context.getStackSize()).isEqualTo(1);
            var result = context.pop();
            assertThat(result).isInstanceOf(CapturedVariable.class);
            var captured = (CapturedVariable) result;
            assertThat(captured.type()).isEqualTo(long.class);
        }

        @Test
        void fload_capturedFloat_pushesCapturedVariable() {
            testMethod.desc = "(Ljava/lang/Object;F)Z";
            context = new AnalysisContext(testMethod, 0);

            var insn = new VarInsnNode(FLOAD, 1);
            handler.handle(insn, context);

            assertThat(context.getStackSize()).isEqualTo(1);
            var result = context.pop();
            assertThat(result).isInstanceOf(CapturedVariable.class);
            var captured = (CapturedVariable) result;
            assertThat(captured.type()).isEqualTo(float.class);
        }

        @Test
        void dload_capturedDouble_pushesCapturedVariable() {
            testMethod.desc = "(Ljava/lang/Object;D)Z";
            context = new AnalysisContext(testMethod, 0);

            var insn = new VarInsnNode(DLOAD, 1);
            handler.handle(insn, context);

            assertThat(context.getStackSize()).isEqualTo(1);
            var result = context.pop();
            assertThat(result).isInstanceOf(CapturedVariable.class);
            var captured = (CapturedVariable) result;
            assertThat(captured.type()).isEqualTo(double.class);
        }

        @Test
        void iload_invalidSlot_throwsException() {
            var insn = new VarInsnNode(ILOAD, 5);

            assertThatThrownBy(() -> handler.handle(insn, context))
                    .isInstanceOf(BytecodeAnalysisException.class)
                    .hasMessageContaining("does not correspond to a method parameter");
        }
    }

    // ==================== handle - GETFIELD Tests ====================

    @Nested
    @DisplayName("handle - GETFIELD")
    class HandleGetFieldTests {

        @Test
        void getfield_fromParameter_createsFieldAccess() {
            context.push(new Parameter("entity", Object.class, 0));

            var insn = new FieldInsnNode(GETFIELD, "com/example/Entity", "name", "Ljava/lang/String;");
            handler.handle(insn, context);

            assertThat(context.getStackSize()).isEqualTo(1);
            var result = context.pop();
            assertThat(result).isInstanceOf(FieldAccess.class);
            var fieldAccess = (FieldAccess) result;
            assertThat(fieldAccess.fieldName()).isEqualTo("name");
            assertThat(fieldAccess.fieldType()).isEqualTo(String.class);
        }

        @Test
        void getfield_chained_createsPathExpression() {
            context.push(field("owner", Object.class));

            var insn = new FieldInsnNode(GETFIELD, "com/example/Person", "firstName", "Ljava/lang/String;");
            handler.handle(insn, context);

            assertThat(context.getStackSize()).isEqualTo(1);
            var result = context.pop();
            assertThat(result).isInstanceOf(PathExpression.class);
            var pathExpr = (PathExpression) result;
            assertThat(pathExpr.segments()).hasSize(2);
            assertThat(pathExpr.segments().get(0).fieldName()).isEqualTo("owner");
            assertThat(pathExpr.segments().get(1).fieldName()).isEqualTo("firstName");
        }

        @Test
        void getfield_extendingPath_addsSegment() {
            var existingPath = new PathExpression(
                    java.util.List.of(
                            new LambdaExpression.PathSegment("owner", Object.class, LambdaExpression.RelationType.FIELD),
                            new LambdaExpression.PathSegment("department", Object.class, LambdaExpression.RelationType.FIELD)
                    ),
                    Object.class
            );
            context.push(existingPath);

            var insn = new FieldInsnNode(GETFIELD, "com/example/Department", "name", "Ljava/lang/String;");
            handler.handle(insn, context);

            assertThat(context.getStackSize()).isEqualTo(1);
            var result = context.pop();
            assertThat(result).isInstanceOf(PathExpression.class);
            var pathExpr = (PathExpression) result;
            assertThat(pathExpr.segments()).hasSize(3);
            assertThat(pathExpr.segments().get(2).fieldName()).isEqualTo("name");
        }

        @Test
        void getfield_fromBiEntityParameter_createsBiEntityFieldAccess() {
            context.push(new BiEntityParameter("entity", Object.class, 0, EntityPosition.FIRST));

            var insn = new FieldInsnNode(GETFIELD, "com/example/Entity", "name", "Ljava/lang/String;");
            handler.handle(insn, context);

            assertThat(context.getStackSize()).isEqualTo(1);
            var result = context.pop();
            assertThat(result).isInstanceOf(BiEntityFieldAccess.class);
            var biFieldAccess = (BiEntityFieldAccess) result;
            assertThat(biFieldAccess.fieldName()).isEqualTo("name");
            assertThat(biFieldAccess.entityPosition()).isEqualTo(EntityPosition.FIRST);
        }

        @Test
        void getfield_chainedBiEntity_createsBiEntityPathExpression() {
            context.push(new BiEntityFieldAccess("owner", Object.class, EntityPosition.SECOND));

            var insn = new FieldInsnNode(GETFIELD, "com/example/Person", "firstName", "Ljava/lang/String;");
            handler.handle(insn, context);

            assertThat(context.getStackSize()).isEqualTo(1);
            var result = context.pop();
            assertThat(result).isInstanceOf(BiEntityPathExpression.class);
            var biPathExpr = (BiEntityPathExpression) result;
            assertThat(biPathExpr.segments()).hasSize(2);
            assertThat(biPathExpr.entityPosition()).isEqualTo(EntityPosition.SECOND);
        }

        @Test
        void getfield_extendingBiEntityPath_addsSegment() {
            var existingPath = new BiEntityPathExpression(
                    java.util.List.of(
                            new LambdaExpression.PathSegment("owner", Object.class, LambdaExpression.RelationType.FIELD)
                    ),
                    Object.class,
                    EntityPosition.FIRST
            );
            context.push(existingPath);

            var insn = new FieldInsnNode(GETFIELD, "com/example/Person", "name", "Ljava/lang/String;");
            handler.handle(insn, context);

            assertThat(context.getStackSize()).isEqualTo(1);
            var result = context.pop();
            assertThat(result).isInstanceOf(BiEntityPathExpression.class);
            var biPathExpr = (BiEntityPathExpression) result;
            assertThat(biPathExpr.segments()).hasSize(2);
        }

        @Test
        void getfield_fromCapturedVariable_createsCorrelatedVariable() {
            context.push(captured(1, Object.class));

            var insn = new FieldInsnNode(GETFIELD, "com/example/Entity", "id", "I");
            handler.handle(insn, context);

            assertThat(context.getStackSize()).isEqualTo(1);
            var result = context.pop();
            assertThat(result).isInstanceOf(CorrelatedVariable.class);
            var correlatedVar = (CorrelatedVariable) result;
            assertThat(correlatedVar.outerParameterIndex()).isEqualTo(1);
        }

        @Test
        void getfield_chainedFromCorrelatedVariable_extendsPath() {
            var innerField = field("owner", Object.class);
            context.push(new CorrelatedVariable(innerField, 0, Object.class));

            var insn = new FieldInsnNode(GETFIELD, "com/example/Person", "id", "I");
            handler.handle(insn, context);

            assertThat(context.getStackSize()).isEqualTo(1);
            var result = context.pop();
            assertThat(result).isInstanceOf(CorrelatedVariable.class);
            var correlatedVar = (CorrelatedVariable) result;
            assertThat(correlatedVar.fieldExpression()).isInstanceOf(PathExpression.class);
        }

        @Test
        void getfield_fromCorrelatedWithPath_extendsExistingPath() {
            var existingPath = new PathExpression(
                    java.util.List.of(
                            new LambdaExpression.PathSegment("owner", Object.class, LambdaExpression.RelationType.FIELD)
                    ),
                    Object.class
            );
            context.push(new CorrelatedVariable(existingPath, 0, Object.class));

            var insn = new FieldInsnNode(GETFIELD, "com/example/Person", "name", "Ljava/lang/String;");
            handler.handle(insn, context);

            assertThat(context.getStackSize()).isEqualTo(1);
            var result = context.pop();
            assertThat(result).isInstanceOf(CorrelatedVariable.class);
            var correlatedVar = (CorrelatedVariable) result;
            assertThat(correlatedVar.fieldExpression()).isInstanceOf(PathExpression.class);
            var pathExpr = (PathExpression) correlatedVar.fieldExpression();
            assertThat(pathExpr.segments()).hasSize(2);
        }

        @Test
        void getfield_unknownTarget_fallsBackToFieldAccess() {
            // Push a constant (not a recognized target type)
            context.push(constant("unknown"));

            var insn = new FieldInsnNode(GETFIELD, "com/example/Entity", "value", "I");
            handler.handle(insn, context);

            assertThat(context.getStackSize()).isEqualTo(1);
            var result = context.pop();
            assertThat(result).isInstanceOf(FieldAccess.class);
        }
    }

    // ==================== handle - Return Value Tests ====================

    @Nested
    @DisplayName("handle - Return Value")
    class HandleReturnValueTests {

        @Test
        void handle_aload_returnsFalse() {
            var insn = new VarInsnNode(ALOAD, 0);
            boolean result = handler.handle(insn, context);
            assertThat(result).as("handle() should return false to continue analysis").isFalse();
        }

        @Test
        void handle_iload_returnsFalse() {
            testMethod.desc = "(Ljava/lang/Object;I)Z";
            context = new AnalysisContext(testMethod, 0);

            var insn = new VarInsnNode(ILOAD, 1);
            boolean result = handler.handle(insn, context);
            assertThat(result).as("handle() should return false to continue analysis").isFalse();
        }

        @Test
        void handle_getfield_returnsFalse() {
            context.push(new Parameter("entity", Object.class, 0));

            var insn = new FieldInsnNode(GETFIELD, "com/example/Entity", "name", "Ljava/lang/String;");
            boolean result = handler.handle(insn, context);
            assertThat(result).as("handle() should return false to continue analysis").isFalse();
        }
    }

    // ==================== Correlated Variable Edge Cases ====================

    @Nested
    @DisplayName("Correlated Variable Edge Cases")
    class CorrelatedVariableEdgeCaseTests {

        @Test
        void getfield_fromCorrelatedWithNonFieldExpression_fallsBackToWrappedPath() {
            // Correlated variable with a non-standard inner expression (e.g., MethodCall)
            var methodCall = methodCall(null, "getValue", Object.class);
            context.push(new CorrelatedVariable(methodCall, 0, Object.class));

            var insn = new FieldInsnNode(GETFIELD, "com/example/Result", "id", "I");
            handler.handle(insn, context);

            assertThat(context.getStackSize()).isEqualTo(1);
            var result = context.pop();
            assertThat(result).isInstanceOf(CorrelatedVariable.class);
            // Should wrap in PathExpression via default case
        }
    }
}
