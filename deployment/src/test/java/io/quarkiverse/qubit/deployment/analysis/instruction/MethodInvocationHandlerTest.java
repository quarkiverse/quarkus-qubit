package io.quarkiverse.qubit.deployment.analysis.instruction;

import io.quarkiverse.qubit.deployment.ast.LambdaExpression;
import io.quarkiverse.qubit.deployment.ast.LambdaExpression.EntityPosition;
import io.quarkiverse.qubit.deployment.analysis.instruction.MethodInvocationHandler.VirtualMethodCategory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.math.BigDecimal;
import java.util.List;

import static io.quarkiverse.qubit.deployment.testutil.AstBuilders.*;
import static io.quarkiverse.qubit.deployment.testutil.fixtures.AsmFixtures.testMethod;
import static io.quarkiverse.qubit.deployment.testutil.fixtures.AnalysisContextFixtures.contextFor;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.objectweb.asm.Opcodes.*;

/**
 * Unit tests for {@link MethodInvocationHandler}.
 *
 * <p>Tests the method invocation handling for various bytecode patterns
 * including equals, compareTo, BigDecimal operations, temporal methods, and collection operations.
 */
class MethodInvocationHandlerTest {

    private final MethodInvocationHandler handler = MethodInvocationHandler.INSTANCE;
    private MethodNode testMethod;
    private AnalysisContext context;

    @BeforeEach
    void setUp() {
        testMethod = testMethod().build();
        context = contextFor(testMethod, 0);
    }

    // ==================== VirtualMethodCategory Tests ====================

    @Nested
    @DisplayName("VirtualMethodCategory.categorize")
    class VirtualMethodCategoryTests {

        @Test
        void equals_onAnyObject_returnsEquals() {
            var methodInsn = createMethodInsn(INVOKEVIRTUAL, "java/lang/Object", "equals", "(Ljava/lang/Object;)Z");

            var category = VirtualMethodCategory.categorize(methodInsn, handler);

            assertThat(category).isEqualTo(VirtualMethodCategory.EQUALS);
        }

        @Test
        void equals_onString_returnsEquals() {
            var methodInsn = createMethodInsn(INVOKEVIRTUAL, "java/lang/String", "equals", "(Ljava/lang/Object;)Z");

            var category = VirtualMethodCategory.categorize(methodInsn, handler);

            assertThat(category).isEqualTo(VirtualMethodCategory.EQUALS);
        }

        @Test
        void string_methodOnString_returnsStringMethod() {
            var methodInsn = createMethodInsn(INVOKEVIRTUAL, "java/lang/String", "length", "()I");

            var category = VirtualMethodCategory.categorize(methodInsn, handler);

            assertThat(category).isEqualTo(VirtualMethodCategory.STRING_METHOD);
        }

        @Test
        void compareTo_onComparable_returnsCompareTo() {
            var methodInsn = createMethodInsn(INVOKEVIRTUAL, "java/lang/Integer", "compareTo", "(Ljava/lang/Integer;)I");

            var category = VirtualMethodCategory.categorize(methodInsn, handler);

            assertThat(category).isEqualTo(VirtualMethodCategory.COMPARE_TO);
        }

        @Test
        void bigDecimalAdd_returnsBigDecimalArithmetic() {
            var methodInsn = createMethodInsn(INVOKEVIRTUAL, "java/math/BigDecimal", "add",
                    "(Ljava/math/BigDecimal;)Ljava/math/BigDecimal;");

            var category = VirtualMethodCategory.categorize(methodInsn, handler);

            assertThat(category).isEqualTo(VirtualMethodCategory.BIG_DECIMAL_ARITHMETIC);
        }

        @Test
        void localDate_method_returnsTemporalMethod() {
            var methodInsn = createMethodInsn(INVOKEVIRTUAL, "java/time/LocalDate", "getYear", "()I");

            var category = VirtualMethodCategory.categorize(methodInsn, handler);

            assertThat(category).isEqualTo(VirtualMethodCategory.TEMPORAL_METHOD);
        }

        @Test
        void localDateTime_method_returnsTemporalMethod() {
            var methodInsn = createMethodInsn(INVOKEVIRTUAL, "java/time/LocalDateTime", "getHour", "()I");

            var category = VirtualMethodCategory.categorize(methodInsn, handler);

            assertThat(category).isEqualTo(VirtualMethodCategory.TEMPORAL_METHOD);
        }

        @Test
        void localTime_method_returnsTemporalMethod() {
            var methodInsn = createMethodInsn(INVOKEVIRTUAL, "java/time/LocalTime", "getMinute", "()I");

            var category = VirtualMethodCategory.categorize(methodInsn, handler);

            assertThat(category).isEqualTo(VirtualMethodCategory.TEMPORAL_METHOD);
        }

        @Test
        void getter_methodStartingWithGet_returnsGetter() {
            var methodInsn = createMethodInsn(INVOKEVIRTUAL, "com/example/Entity", "getName", "()Ljava/lang/String;");

            var category = VirtualMethodCategory.categorize(methodInsn, handler);

            assertThat(category).isEqualTo(VirtualMethodCategory.GETTER);
        }

        @Test
        void getter_methodStartingWithIs_returnsGetter() {
            var methodInsn = createMethodInsn(INVOKEVIRTUAL, "com/example/Entity", "isActive", "()Z");

            var category = VirtualMethodCategory.categorize(methodInsn, handler);

            assertThat(category).isEqualTo(VirtualMethodCategory.GETTER);
        }

        @Test
        void unknownMethod_returnsUnhandled() {
            var methodInsn = createMethodInsn(INVOKEVIRTUAL, "com/example/Entity", "doSomething", "(I)V");

            var category = VirtualMethodCategory.categorize(methodInsn, handler);

            assertThat(category).isEqualTo(VirtualMethodCategory.UNHANDLED);
        }
    }

    // ==================== canHandle Tests ====================

    @Nested
    @DisplayName("canHandle")
    class CanHandleTests {

        @Test
        void invokeVirtual_canHandle() {
            var insn = createMethodInsn(INVOKEVIRTUAL, "java/lang/Object", "equals", "(Ljava/lang/Object;)Z");

            assertThat(handler.canHandle(insn)).isTrue();
        }

        @Test
        void invokeStatic_canHandle() {
            var insn = createMethodInsn(INVOKESTATIC, "java/lang/Boolean", "valueOf", "(Z)Ljava/lang/Boolean;");

            assertThat(handler.canHandle(insn)).isTrue();
        }

        @Test
        void invokeSpecial_canHandle() {
            var insn = createMethodInsn(INVOKESPECIAL, "java/math/BigDecimal", "<init>", "(Ljava/lang/String;)V");

            assertThat(handler.canHandle(insn)).isTrue();
        }

        @Test
        void invokeInterface_canHandle() {
            var insn = createMethodInsn(INVOKEINTERFACE, "java/util/Collection", "contains", "(Ljava/lang/Object;)Z");

            assertThat(handler.canHandle(insn)).isTrue();
        }

        @Test
        void nonMethodInstruction_cannotHandle() {
            // Use GETFIELD opcode which is NOT a method invocation
            // This kills the NO_COVERAGE mutation that replaces return with true
            var fieldInsn = new org.objectweb.asm.tree.FieldInsnNode(GETFIELD, "com/example/Entity", "name", "Ljava/lang/String;");

            assertThat(handler.canHandle(fieldInsn)).isFalse();
        }

        @Test
        void invokeDynamic_cannotHandle() {
            // INVOKEDYNAMIC is not in the supported opcodes
            var dynInsn = new org.objectweb.asm.tree.InvokeDynamicInsnNode("test", "()V", null);

            assertThat(handler.canHandle(dynInsn)).isFalse();
        }
    }

    // ==================== handle Tests - InvokeVirtual ====================

    @Nested
    @DisplayName("handle - INVOKEVIRTUAL")
    class HandleInvokeVirtualTests {

        @Test
        void equals_combinesAsEquality() {
            context.push(field("name", String.class));
            context.push(constant("John"));

            var insn = createMethodInsn(INVOKEVIRTUAL, "java/lang/String", "equals", "(Ljava/lang/Object;)Z");
            handler.handle(insn, context);

            assertThat(context.getStackSize()).isEqualTo(1);
            var result = context.pop();
            assertThat(result).isInstanceOf(LambdaExpression.BinaryOp.class);
            var binOp = (LambdaExpression.BinaryOp) result;
            assertThat(binOp.operator()).isEqualTo(LambdaExpression.BinaryOp.Operator.EQ);
        }

        @Test
        void compareTo_createsMethodCall() {
            context.push(field("price", BigDecimal.class));
            context.push(constant(new BigDecimal("100")));

            var insn = createMethodInsn(INVOKEVIRTUAL, "java/math/BigDecimal", "compareTo",
                    "(Ljava/math/BigDecimal;)I");
            handler.handle(insn, context);

            assertThat(context.getStackSize()).isEqualTo(1);
            var result = context.pop();
            assertThat(result).isInstanceOf(LambdaExpression.MethodCall.class);
            var methodCall = (LambdaExpression.MethodCall) result;
            assertThat(methodCall.methodName()).isEqualTo("compareTo");
            assertThat(methodCall.returnType()).isEqualTo(int.class);
        }

        @Test
        void stringLength_createsMethodCall() {
            context.push(field("name", String.class));

            var insn = createMethodInsn(INVOKEVIRTUAL, "java/lang/String", "length", "()I");
            handler.handle(insn, context);

            assertThat(context.getStackSize()).isEqualTo(1);
            var result = context.pop();
            assertThat(result).isInstanceOf(LambdaExpression.MethodCall.class);
            var methodCall = (LambdaExpression.MethodCall) result;
            assertThat(methodCall.methodName()).isEqualTo("length");
            assertThat(methodCall.returnType()).isEqualTo(int.class);
        }

        @Test
        void stringIsEmpty_createsMethodCall() {
            context.push(field("name", String.class));

            var insn = createMethodInsn(INVOKEVIRTUAL, "java/lang/String", "isEmpty", "()Z");
            handler.handle(insn, context);

            assertThat(context.getStackSize()).isEqualTo(1);
            var result = context.pop();
            assertThat(result).isInstanceOf(LambdaExpression.MethodCall.class);
            var methodCall = (LambdaExpression.MethodCall) result;
            assertThat(methodCall.methodName()).isEqualTo("isEmpty");
            assertThat(methodCall.returnType()).isEqualTo(boolean.class);
        }

        @Test
        void stringStartsWith_createsMethodCall() {
            context.push(field("name", String.class));
            context.push(constant("prefix"));

            var insn = createMethodInsn(INVOKEVIRTUAL, "java/lang/String", "startsWith",
                    "(Ljava/lang/String;)Z");
            handler.handle(insn, context);

            assertThat(context.getStackSize()).isEqualTo(1);
            var result = context.pop();
            assertThat(result).isInstanceOf(LambdaExpression.MethodCall.class);
            var methodCall = (LambdaExpression.MethodCall) result;
            assertThat(methodCall.methodName()).isEqualTo("startsWith");
        }

        @Test
        void stringEndsWith_createsMethodCall() {
            context.push(field("name", String.class));
            context.push(constant("suffix"));

            var insn = createMethodInsn(INVOKEVIRTUAL, "java/lang/String", "endsWith",
                    "(Ljava/lang/String;)Z");
            handler.handle(insn, context);

            assertThat(context.getStackSize()).isEqualTo(1);
            var result = context.pop();
            assertThat(result).isInstanceOf(LambdaExpression.MethodCall.class);
            var methodCall = (LambdaExpression.MethodCall) result;
            assertThat(methodCall.methodName()).isEqualTo("endsWith");
        }

        @Test
        void stringContains_createsMethodCall() {
            context.push(field("name", String.class));
            context.push(constant("substring"));

            var insn = createMethodInsn(INVOKEVIRTUAL, "java/lang/String", "contains",
                    "(Ljava/lang/CharSequence;)Z");
            handler.handle(insn, context);

            assertThat(context.getStackSize()).isEqualTo(1);
            var result = context.pop();
            assertThat(result).isInstanceOf(LambdaExpression.MethodCall.class);
        }

        @Test
        void stringToLowerCase_createsMethodCall() {
            context.push(field("name", String.class));

            var insn = createMethodInsn(INVOKEVIRTUAL, "java/lang/String", "toLowerCase",
                    "()Ljava/lang/String;");
            handler.handle(insn, context);

            assertThat(context.getStackSize()).isEqualTo(1);
            var result = context.pop();
            assertThat(result).isInstanceOf(LambdaExpression.MethodCall.class);
            var methodCall = (LambdaExpression.MethodCall) result;
            assertThat(methodCall.methodName()).isEqualTo("toLowerCase");
        }

        @Test
        void stringToUpperCase_createsMethodCall() {
            context.push(field("name", String.class));

            var insn = createMethodInsn(INVOKEVIRTUAL, "java/lang/String", "toUpperCase",
                    "()Ljava/lang/String;");
            handler.handle(insn, context);

            assertThat(context.getStackSize()).isEqualTo(1);
            var result = context.pop();
            assertThat(result).isInstanceOf(LambdaExpression.MethodCall.class);
            var methodCall = (LambdaExpression.MethodCall) result;
            assertThat(methodCall.methodName()).isEqualTo("toUpperCase");
        }

        @Test
        void stringTrim_createsMethodCall() {
            context.push(field("name", String.class));

            var insn = createMethodInsn(INVOKEVIRTUAL, "java/lang/String", "trim",
                    "()Ljava/lang/String;");
            handler.handle(insn, context);

            assertThat(context.getStackSize()).isEqualTo(1);
            var result = context.pop();
            assertThat(result).isInstanceOf(LambdaExpression.MethodCall.class);
            var methodCall = (LambdaExpression.MethodCall) result;
            assertThat(methodCall.methodName()).isEqualTo("trim");
        }

        @Test
        void stringSubstring_singleArg_createsMethodCall() {
            context.push(field("name", String.class));
            context.push(constant(5));

            var insn = createMethodInsn(INVOKEVIRTUAL, "java/lang/String", "substring",
                    "(I)Ljava/lang/String;");
            handler.handle(insn, context);

            assertThat(context.getStackSize()).isEqualTo(1);
            var result = context.pop();
            assertThat(result).isInstanceOf(LambdaExpression.MethodCall.class);
            var methodCall = (LambdaExpression.MethodCall) result;
            assertThat(methodCall.methodName()).isEqualTo("substring");
        }

        @Test
        void stringSubstring_twoArgs_createsMethodCall() {
            context.push(field("name", String.class));
            context.push(constant(0));
            context.push(constant(5));

            var insn = createMethodInsn(INVOKEVIRTUAL, "java/lang/String", "substring",
                    "(II)Ljava/lang/String;");
            handler.handle(insn, context);

            assertThat(context.getStackSize()).isEqualTo(1);
            var result = context.pop();
            assertThat(result).isInstanceOf(LambdaExpression.MethodCall.class);
            var methodCall = (LambdaExpression.MethodCall) result;
            assertThat(methodCall.methodName()).isEqualTo("substring");
            assertThat(methodCall.arguments()).hasSize(2);
        }

        @Test
        void bigDecimalAdd_createsMethodCall() {
            context.push(field("price", BigDecimal.class));
            context.push(constant(new BigDecimal("10")));

            var insn = createMethodInsn(INVOKEVIRTUAL, "java/math/BigDecimal", "add",
                    "(Ljava/math/BigDecimal;)Ljava/math/BigDecimal;");
            handler.handle(insn, context);

            assertThat(context.getStackSize()).isEqualTo(1);
            var result = context.pop();
            assertThat(result).isInstanceOf(LambdaExpression.MethodCall.class);
            var methodCall = (LambdaExpression.MethodCall) result;
            assertThat(methodCall.methodName()).isEqualTo("add");
        }

        @Test
        void bigDecimalSubtract_createsMethodCall() {
            context.push(field("price", BigDecimal.class));
            context.push(constant(new BigDecimal("10")));

            var insn = createMethodInsn(INVOKEVIRTUAL, "java/math/BigDecimal", "subtract",
                    "(Ljava/math/BigDecimal;)Ljava/math/BigDecimal;");
            handler.handle(insn, context);

            assertThat(context.getStackSize()).isEqualTo(1);
            var result = context.pop();
            assertThat(result).isInstanceOf(LambdaExpression.MethodCall.class);
            var methodCall = (LambdaExpression.MethodCall) result;
            assertThat(methodCall.methodName()).isEqualTo("subtract");
        }

        @Test
        void bigDecimalMultiply_createsMethodCall() {
            context.push(field("price", BigDecimal.class));
            context.push(constant(new BigDecimal("10")));

            var insn = createMethodInsn(INVOKEVIRTUAL, "java/math/BigDecimal", "multiply",
                    "(Ljava/math/BigDecimal;)Ljava/math/BigDecimal;");
            handler.handle(insn, context);

            assertThat(context.getStackSize()).isEqualTo(1);
            var result = context.pop();
            assertThat(result).isInstanceOf(LambdaExpression.MethodCall.class);
            var methodCall = (LambdaExpression.MethodCall) result;
            assertThat(methodCall.methodName()).isEqualTo("multiply");
        }

        @Test
        void bigDecimalDivide_createsMethodCall() {
            context.push(field("price", BigDecimal.class));
            context.push(constant(new BigDecimal("10")));

            var insn = createMethodInsn(INVOKEVIRTUAL, "java/math/BigDecimal", "divide",
                    "(Ljava/math/BigDecimal;)Ljava/math/BigDecimal;");
            handler.handle(insn, context);

            assertThat(context.getStackSize()).isEqualTo(1);
            var result = context.pop();
            assertThat(result).isInstanceOf(LambdaExpression.MethodCall.class);
            var methodCall = (LambdaExpression.MethodCall) result;
            assertThat(methodCall.methodName()).isEqualTo("divide");
        }

        @Test
        void getter_convertsToFieldAccess() {
            context.push(new LambdaExpression.Parameter("entity", Object.class, 0));

            var insn = createMethodInsn(INVOKEVIRTUAL, "com/example/Entity", "getName",
                    "()Ljava/lang/String;");
            handler.handle(insn, context);

            assertThat(context.getStackSize()).isEqualTo(1);
            var result = context.pop();
            assertThat(result).isInstanceOf(LambdaExpression.FieldAccess.class);
            var fieldAccess = (LambdaExpression.FieldAccess) result;
            assertThat(fieldAccess.fieldName()).isEqualTo("name");
        }

        @Test
        void getter_onBiEntityParameter_createsBiEntityFieldAccess() {
            context.push(new LambdaExpression.BiEntityParameter("entity", Object.class, 0, EntityPosition.FIRST));

            var insn = createMethodInsn(INVOKEVIRTUAL, "com/example/Entity", "getName",
                    "()Ljava/lang/String;");
            handler.handle(insn, context);

            assertThat(context.getStackSize()).isEqualTo(1);
            var result = context.pop();
            assertThat(result).isInstanceOf(LambdaExpression.BiEntityFieldAccess.class);
            var biFieldAccess = (LambdaExpression.BiEntityFieldAccess) result;
            assertThat(biFieldAccess.fieldName()).isEqualTo("name");
            assertThat(biFieldAccess.entityPosition()).isEqualTo(EntityPosition.FIRST);
        }

        @Test
        void temporalIsBefore_createsMethodCall() {
            context.push(field("date", java.time.LocalDate.class));
            context.push(constant(java.time.LocalDate.now()));

            var insn = createMethodInsn(INVOKEVIRTUAL, "java/time/LocalDate", "isBefore",
                    "(Ljava/time/chrono/ChronoLocalDate;)Z");
            handler.handle(insn, context);

            assertThat(context.getStackSize()).isEqualTo(1);
            var result = context.pop();
            assertThat(result).isInstanceOf(LambdaExpression.MethodCall.class);
            var methodCall = (LambdaExpression.MethodCall) result;
            assertThat(methodCall.methodName()).isEqualTo("isBefore");
        }

        @Test
        void temporalIsAfter_createsMethodCall() {
            context.push(field("date", java.time.LocalDate.class));
            context.push(constant(java.time.LocalDate.now()));

            var insn = createMethodInsn(INVOKEVIRTUAL, "java/time/LocalDate", "isAfter",
                    "(Ljava/time/chrono/ChronoLocalDate;)Z");
            handler.handle(insn, context);

            assertThat(context.getStackSize()).isEqualTo(1);
            var result = context.pop();
            assertThat(result).isInstanceOf(LambdaExpression.MethodCall.class);
            var methodCall = (LambdaExpression.MethodCall) result;
            assertThat(methodCall.methodName()).isEqualTo("isAfter");
        }

        @Test
        void temporalAccessor_getYear_createsMethodCall() {
            context.push(field("date", java.time.LocalDate.class));

            var insn = createMethodInsn(INVOKEVIRTUAL, "java/time/LocalDate", "getYear", "()I");
            handler.handle(insn, context);

            assertThat(context.getStackSize()).isEqualTo(1);
            var result = context.pop();
            assertThat(result).isInstanceOf(LambdaExpression.MethodCall.class);
            var methodCall = (LambdaExpression.MethodCall) result;
            assertThat(methodCall.methodName()).isEqualTo("getYear");
            assertThat(methodCall.returnType()).isEqualTo(int.class);
        }
    }

    // ==================== handle Tests - InvokeStatic ====================

    @Nested
    @DisplayName("handle - INVOKESTATIC")
    class HandleInvokeStaticTests {

        @Test
        void booleanValueOf_isSkipped() {
            var boolExpr = eq(field("active", boolean.class), constant(true));
            context.push(boolExpr);

            var insn = createMethodInsn(INVOKESTATIC, "java/lang/Boolean", "valueOf", "(Z)Ljava/lang/Boolean;");
            handler.handle(insn, context);

            // Stack should be unchanged - Boolean.valueOf is skipped
            assertThat(context.getStackSize()).isEqualTo(1);
            assertThat(context.pop()).isSameAs(boolExpr);
        }

        @Test
        void localDateOf_withConstants_foldsToConstant() {
            context.push(constant(2024));
            context.push(constant(6));
            context.push(constant(15));

            var insn = createMethodInsn(INVOKESTATIC, "java/time/LocalDate", "of", "(III)Ljava/time/LocalDate;");
            handler.handle(insn, context);

            assertThat(context.getStackSize()).isEqualTo(1);
            var result = context.pop();
            assertThat(result).isInstanceOf(LambdaExpression.Constant.class);
            var constant = (LambdaExpression.Constant) result;
            assertThat(constant.value()).isEqualTo(java.time.LocalDate.of(2024, 6, 15));
        }

        @Test
        void localTimeOf_withConstants_foldsToConstant() {
            context.push(constant(14));
            context.push(constant(30));

            var insn = createMethodInsn(INVOKESTATIC, "java/time/LocalTime", "of", "(II)Ljava/time/LocalTime;");
            handler.handle(insn, context);

            assertThat(context.getStackSize()).isEqualTo(1);
            var result = context.pop();
            assertThat(result).isInstanceOf(LambdaExpression.Constant.class);
            var constant = (LambdaExpression.Constant) result;
            assertThat(constant.value()).isEqualTo(java.time.LocalTime.of(14, 30));
        }

        @Test
        void localDateTimeOf_withConstants_foldsToConstant() {
            context.push(constant(2024));
            context.push(constant(6));
            context.push(constant(15));
            context.push(constant(14));
            context.push(constant(30));

            var insn = createMethodInsn(INVOKESTATIC, "java/time/LocalDateTime", "of",
                    "(IIIII)Ljava/time/LocalDateTime;");
            handler.handle(insn, context);

            assertThat(context.getStackSize()).isEqualTo(1);
            var result = context.pop();
            assertThat(result).isInstanceOf(LambdaExpression.Constant.class);
            var constant = (LambdaExpression.Constant) result;
            assertThat(constant.value()).isEqualTo(java.time.LocalDateTime.of(2024, 6, 15, 14, 30));
        }

        @Test
        void localDateOf_withNonConstants_createsMethodCall() {
            context.push(field("year", int.class));
            context.push(constant(6));
            context.push(constant(15));

            var insn = createMethodInsn(INVOKESTATIC, "java/time/LocalDate", "of", "(III)Ljava/time/LocalDate;");
            handler.handle(insn, context);

            assertThat(context.getStackSize()).isEqualTo(1);
            var result = context.pop();
            assertThat(result).isInstanceOf(LambdaExpression.MethodCall.class);
        }
    }

    // ==================== handle Tests - InvokeInterface ====================

    @Nested
    @DisplayName("handle - INVOKEINTERFACE")
    class HandleInvokeInterfaceTests {

        @Test
        void collectionContains_capturedCollection_createsInExpression() {
            // Captured collection (from outer scope)
            var capturedList = captured(0, List.class);
            context.push(capturedList);
            // Entity field
            var cityField = field("city", String.class);
            context.push(cityField);

            var insn = createMethodInsn(INVOKEINTERFACE, "java/util/Collection", "contains",
                    "(Ljava/lang/Object;)Z");
            handler.handle(insn, context);

            assertThat(context.getStackSize()).isEqualTo(1);
            var result = context.pop();
            assertThat(result).isInstanceOf(LambdaExpression.InExpression.class);
        }

        @Test
        void collectionContains_entityCollection_createsMemberOfExpression() {
            // Entity collection field
            var rolesField = field("roles", List.class);
            context.push(rolesField);
            // Constant value
            context.push(constant("admin"));

            var insn = createMethodInsn(INVOKEINTERFACE, "java/util/Collection", "contains",
                    "(Ljava/lang/Object;)Z");
            handler.handle(insn, context);

            assertThat(context.getStackSize()).isEqualTo(1);
            var result = context.pop();
            assertThat(result).isInstanceOf(LambdaExpression.MemberOfExpression.class);
        }

        @Test
        void collectionContains_capturedVariable_createsMemberOfExpression() {
            // Entity collection field
            var rolesField = field("roles", List.class);
            context.push(rolesField);
            // Captured variable (parameter value)
            context.push(captured(1, String.class));

            var insn = createMethodInsn(INVOKEINTERFACE, "java/util/Collection", "contains",
                    "(Ljava/lang/Object;)Z");
            handler.handle(insn, context);

            assertThat(context.getStackSize()).isEqualTo(1);
            var result = context.pop();
            assertThat(result).isInstanceOf(LambdaExpression.MemberOfExpression.class);
        }

        @Test
        void interfaceEquals_combinesAsEquality() {
            context.push(field("name", String.class));
            context.push(constant("John"));

            var insn = createMethodInsn(INVOKEINTERFACE, "java/lang/CharSequence", "equals",
                    "(Ljava/lang/Object;)Z");
            handler.handle(insn, context);

            assertThat(context.getStackSize()).isEqualTo(1);
            var result = context.pop();
            assertThat(result).isInstanceOf(LambdaExpression.BinaryOp.class);
        }

        @Test
        void interfaceCompareTo_createsMethodCall() {
            context.push(field("value", Comparable.class));
            context.push(constant(10));

            var insn = createMethodInsn(INVOKEINTERFACE, "java/lang/Comparable", "compareTo",
                    "(Ljava/lang/Object;)I");
            handler.handle(insn, context);

            assertThat(context.getStackSize()).isEqualTo(1);
            var result = context.pop();
            assertThat(result).isInstanceOf(LambdaExpression.MethodCall.class);
        }
    }

    // ==================== handle Tests - InvokeSpecial ====================

    @Nested
    @DisplayName("handle - INVOKESPECIAL")
    class HandleInvokeSpecialTests {

        @Test
        void bigDecimalConstructor_withStringConstant_foldsToConstant() {
            // Simulating: new BigDecimal("123.45")
            // Stack: NEW, DUP (discarded by handler), then String constant
            context.push(constant("placeholder1")); // NEW
            context.push(constant("placeholder2")); // DUP
            context.push(constant("123.45"));

            var insn = createMethodInsn(INVOKESPECIAL, "java/math/BigDecimal", "<init>",
                    "(Ljava/lang/String;)V");
            handler.handle(insn, context);

            assertThat(context.getStackSize()).isEqualTo(1);
            var result = context.pop();
            assertThat(result).isInstanceOf(LambdaExpression.Constant.class);
            var constant = (LambdaExpression.Constant) result;
            assertThat(constant.value()).isEqualTo(new BigDecimal("123.45"));
        }

        @Test
        void bigDecimalConstructor_withInvalidString_createsConstructorCall() {
            context.push(constant("placeholder1")); // NEW
            context.push(constant("placeholder2")); // DUP
            context.push(constant("not-a-number"));

            var insn = createMethodInsn(INVOKESPECIAL, "java/math/BigDecimal", "<init>",
                    "(Ljava/lang/String;)V");
            handler.handle(insn, context);

            assertThat(context.getStackSize()).isEqualTo(1);
            var result = context.pop();
            assertThat(result).isInstanceOf(LambdaExpression.ConstructorCall.class);
        }

        @Test
        void nonConstructorMethod_isIgnored() {
            int initialSize = context.getStackSize();

            var insn = createMethodInsn(INVOKESPECIAL, "java/lang/Object", "clone", "()Ljava/lang/Object;");
            handler.handle(insn, context);

            // Stack should be unchanged for non-constructor INVOKESPECIAL
            assertThat(context.getStackSize()).isEqualTo(initialSize);
        }
    }

    // ==================== String Method Boundary Tests ====================

    @Nested
    @DisplayName("String method boundary conditions")
    class StringMethodBoundaryTests {

        @Test
        void unknownStringMethod_doesNotModifyStack() {
            context.push(field("name", String.class));
            int initialSize = context.getStackSize();

            var insn = createMethodInsn(INVOKEVIRTUAL, "java/lang/String", "unknownMethod", "(I)V");
            handler.handle(insn, context);

            // Unknown method should not modify stack
            assertThat(context.getStackSize()).isEqualTo(initialSize);
        }

        @Test
        void startsWithWrongDescriptor_doesNotHandle() {
            context.push(field("name", String.class));
            context.push(constant("prefix"));

            // Wrong descriptor (should be String -> boolean, not int -> boolean)
            var insn = createMethodInsn(INVOKEVIRTUAL, "java/lang/String", "startsWith", "(I)Z");
            handler.handle(insn, context);

            // Stack should still have 2 elements (not processed)
            assertThat(context.getStackSize()).isEqualTo(2);
        }

        @Test
        void endsWithWrongDescriptor_doesNotHandle() {
            context.push(field("name", String.class));
            context.push(constant("suffix"));

            var insn = createMethodInsn(INVOKEVIRTUAL, "java/lang/String", "endsWith", "(I)Z");
            handler.handle(insn, context);

            assertThat(context.getStackSize()).isEqualTo(2);
        }

        @Test
        void containsWrongDescriptor_doesNotHandle() {
            context.push(field("name", String.class));
            context.push(constant("text"));

            // Wrong descriptor (should be CharSequence -> boolean)
            var insn = createMethodInsn(INVOKEVIRTUAL, "java/lang/String", "contains", "(Ljava/lang/String;)Z");
            handler.handle(insn, context);

            assertThat(context.getStackSize()).isEqualTo(2);
        }

        @Test
        void lengthWrongDescriptor_doesNotHandle() {
            context.push(field("name", String.class));

            var insn = createMethodInsn(INVOKEVIRTUAL, "java/lang/String", "length", "()V");
            handler.handle(insn, context);

            assertThat(context.getStackSize()).isEqualTo(1);
        }

        @Test
        void isEmptyWrongDescriptor_doesNotHandle() {
            context.push(field("name", String.class));

            var insn = createMethodInsn(INVOKEVIRTUAL, "java/lang/String", "isEmpty", "()I");
            handler.handle(insn, context);

            assertThat(context.getStackSize()).isEqualTo(1);
        }

        @Test
        void toLowerCaseWrongDescriptor_doesNotHandle() {
            context.push(field("name", String.class));

            var insn = createMethodInsn(INVOKEVIRTUAL, "java/lang/String", "toLowerCase", "(I)Ljava/lang/String;");
            handler.handle(insn, context);

            assertThat(context.getStackSize()).isEqualTo(1);
        }

        @Test
        void toUpperCaseWrongDescriptor_doesNotHandle() {
            context.push(field("name", String.class));

            var insn = createMethodInsn(INVOKEVIRTUAL, "java/lang/String", "toUpperCase", "(I)Ljava/lang/String;");
            handler.handle(insn, context);

            assertThat(context.getStackSize()).isEqualTo(1);
        }

        @Test
        void trimWrongDescriptor_doesNotHandle() {
            context.push(field("name", String.class));

            var insn = createMethodInsn(INVOKEVIRTUAL, "java/lang/String", "trim", "(I)Ljava/lang/String;");
            handler.handle(insn, context);

            assertThat(context.getStackSize()).isEqualTo(1);
        }

        @Test
        void substringWrongDescriptor_doesNotHandle() {
            context.push(field("name", String.class));
            context.push(constant(5));

            var insn = createMethodInsn(INVOKEVIRTUAL, "java/lang/String", "substring", "(Z)Ljava/lang/String;");
            handler.handle(insn, context);

            assertThat(context.getStackSize()).isEqualTo(2);
        }
    }

    // ==================== BigDecimal Boundary Tests ====================

    @Nested
    @DisplayName("BigDecimal boundary conditions")
    class BigDecimalBoundaryTests {

        @Test
        void bigDecimalConstructor_wrongOwner_createsConstructorCall() {
            context.push(constant("placeholder1"));
            context.push(constant("placeholder2"));
            context.push(constant("123.45"));

            var insn = createMethodInsn(INVOKESPECIAL, "java/lang/Integer", "<init>", "(Ljava/lang/String;)V");
            handler.handle(insn, context);

            assertThat(context.getStackSize()).isEqualTo(1);
            var result = context.pop();
            assertThat(result).isInstanceOf(LambdaExpression.ConstructorCall.class);
        }

        @Test
        void bigDecimalConstructor_multipleArgs_createsConstructorCall() {
            // BigDecimal(int) - not BigDecimal(String) pattern
            context.push(constant("placeholder1"));
            context.push(constant("placeholder2"));
            context.push(constant(123));
            context.push(constant(2)); // extra arg

            var insn = createMethodInsn(INVOKESPECIAL, "java/math/BigDecimal", "<init>", "(II)V");
            handler.handle(insn, context);

            assertThat(context.getStackSize()).isEqualTo(1);
            var result = context.pop();
            assertThat(result).isInstanceOf(LambdaExpression.ConstructorCall.class);
        }

        @Test
        void bigDecimalConstructor_nonConstantArg_createsConstructorCall() {
            context.push(constant("placeholder1"));
            context.push(constant("placeholder2"));
            context.push(field("value", String.class)); // field, not constant

            var insn = createMethodInsn(INVOKESPECIAL, "java/math/BigDecimal", "<init>", "(Ljava/lang/String;)V");
            handler.handle(insn, context);

            assertThat(context.getStackSize()).isEqualTo(1);
            var result = context.pop();
            assertThat(result).isInstanceOf(LambdaExpression.ConstructorCall.class);
        }

        @Test
        void bigDecimalConstructor_intConstant_createsConstructorCall() {
            context.push(constant("placeholder1"));
            context.push(constant("placeholder2"));
            context.push(constant(123)); // int, not String

            var insn = createMethodInsn(INVOKESPECIAL, "java/math/BigDecimal", "<init>", "(I)V");
            handler.handle(insn, context);

            assertThat(context.getStackSize()).isEqualTo(1);
            var result = context.pop();
            assertThat(result).isInstanceOf(LambdaExpression.ConstructorCall.class);
        }

        @Test
        void bigDecimalArithmetic_wrongOwner_notHandled() {
            context.push(field("price", BigDecimal.class));
            context.push(constant(new BigDecimal("10")));

            var insn = createMethodInsn(INVOKEVIRTUAL, "java/lang/Integer", "add", "(Ljava/math/BigDecimal;)Ljava/math/BigDecimal;");
            handler.handle(insn, context);

            // Should not be handled as BigDecimal arithmetic
            assertThat(context.getStackSize()).isEqualTo(2);
        }

        @Test
        void bigDecimalArithmetic_wrongDescriptor_notHandled() {
            context.push(field("price", BigDecimal.class));
            context.push(constant(new BigDecimal("10")));

            var insn = createMethodInsn(INVOKEVIRTUAL, "java/math/BigDecimal", "add", "(I)Ljava/math/BigDecimal;");
            handler.handle(insn, context);

            // Should not be handled as BigDecimal arithmetic
            assertThat(context.getStackSize()).isEqualTo(2);
        }
    }

    // ==================== Temporal Method Boundary Tests ====================

    @Nested
    @DisplayName("Temporal method boundary conditions")
    class TemporalMethodBoundaryTests {

        @Test
        void temporalAccessor_unknownMethod_notHandled() {
            context.push(field("date", java.time.LocalDate.class));

            var insn = createMethodInsn(INVOKEVIRTUAL, "java/time/LocalDate", "unknownMethod", "()I");
            handler.handle(insn, context);

            // Unknown temporal method should not be handled
            assertThat(context.getStackSize()).isEqualTo(1);
        }

        @Test
        void temporalAccessor_wrongOwner_notHandled() {
            context.push(field("date", java.time.LocalDate.class));

            // getYear on wrong owner (not a temporal type)
            var insn = createMethodInsn(INVOKEVIRTUAL, "java/util/Calendar", "getYear", "()I");
            handler.handle(insn, context);

            assertThat(context.getStackSize()).isEqualTo(1);
        }

        @Test
        void temporalFactory_wrongOwner_notHandled() {
            context.push(constant(2024));
            context.push(constant(6));
            context.push(constant(15));

            var insn = createMethodInsn(INVOKESTATIC, "java/util/Date", "of", "(III)Ljava/util/Date;");
            handler.handle(insn, context);

            // Stack should still have the 3 constants
            assertThat(context.getStackSize()).isEqualTo(3);
        }

        @Test
        void temporalFactory_wrongMethodName_notHandled() {
            context.push(constant(2024));
            context.push(constant(6));
            context.push(constant(15));

            var insn = createMethodInsn(INVOKESTATIC, "java/time/LocalDate", "create", "(III)Ljava/time/LocalDate;");
            handler.handle(insn, context);

            assertThat(context.getStackSize()).isEqualTo(3);
        }

        @Test
        void temporalFactory_wrongArgCount_notHandled() {
            context.push(constant(2024));
            context.push(constant(6));
            // Only 2 args for LocalDate.of which needs 3

            var insn = createMethodInsn(INVOKESTATIC, "java/time/LocalDate", "of", "(II)Ljava/time/LocalDate;");
            handler.handle(insn, context);

            // Should not be handled - wrong arg count
            assertThat(context.getStackSize()).isEqualTo(2);
        }

        @Test
        void localDateTimeAccessor_getHour_createsMethodCall() {
            context.push(field("dateTime", java.time.LocalDateTime.class));

            var insn = createMethodInsn(INVOKEVIRTUAL, "java/time/LocalDateTime", "getHour", "()I");
            handler.handle(insn, context);

            assertThat(context.getStackSize()).isEqualTo(1);
            var result = context.pop();
            assertThat(result).isInstanceOf(LambdaExpression.MethodCall.class);
            var methodCall = (LambdaExpression.MethodCall) result;
            assertThat(methodCall.methodName()).isEqualTo("getHour");
        }

        @Test
        void localTimeAccessor_getMinute_createsMethodCall() {
            context.push(field("time", java.time.LocalTime.class));

            var insn = createMethodInsn(INVOKEVIRTUAL, "java/time/LocalTime", "getMinute", "()I");
            handler.handle(insn, context);

            assertThat(context.getStackSize()).isEqualTo(1);
            var result = context.pop();
            assertThat(result).isInstanceOf(LambdaExpression.MethodCall.class);
            var methodCall = (LambdaExpression.MethodCall) result;
            assertThat(methodCall.methodName()).isEqualTo("getMinute");
        }
    }

    // ==================== InvokeStatic Boundary Tests ====================

    @Nested
    @DisplayName("InvokeStatic boundary conditions")
    class InvokeStaticBoundaryTests {

        @Test
        void booleanValueOf_wrongOwner_notSkipped() {
            var boolExpr = eq(field("active", boolean.class), constant(true));
            context.push(boolExpr);

            // Same method name but wrong owner
            var insn = createMethodInsn(INVOKESTATIC, "java/lang/Integer", "valueOf", "(Z)Ljava/lang/Boolean;");
            handler.handle(insn, context);

            // Should not be skipped
            assertThat(context.getStackSize()).isEqualTo(1);
        }

        @Test
        void booleanValueOf_wrongMethodName_notSkipped() {
            var boolExpr = eq(field("active", boolean.class), constant(true));
            context.push(boolExpr);

            var insn = createMethodInsn(INVOKESTATIC, "java/lang/Boolean", "parseBoolean", "(Z)Ljava/lang/Boolean;");
            handler.handle(insn, context);

            assertThat(context.getStackSize()).isEqualTo(1);
        }

        @Test
        void booleanValueOf_wrongDescriptor_notSkipped() {
            var boolExpr = eq(field("active", boolean.class), constant(true));
            context.push(boolExpr);

            var insn = createMethodInsn(INVOKESTATIC, "java/lang/Boolean", "valueOf", "(Ljava/lang/String;)Ljava/lang/Boolean;");
            handler.handle(insn, context);

            assertThat(context.getStackSize()).isEqualTo(1);
        }
    }

    // ==================== Collection Contains Boundary Tests ====================

    @Nested
    @DisplayName("Collection contains boundary conditions")
    class CollectionContainsBoundaryTests {

        @Test
        void collectionContains_bothCaptured_fallsBackToMethodCall() {
            // Both are captured (neither is entity field)
            context.push(captured(0, List.class));
            context.push(captured(1, String.class));

            var insn = createMethodInsn(INVOKEINTERFACE, "java/util/Collection", "contains", "(Ljava/lang/Object;)Z");
            handler.handle(insn, context);

            assertThat(context.getStackSize()).isEqualTo(1);
            var result = context.pop();
            assertThat(result).isInstanceOf(LambdaExpression.MethodCall.class);
        }

        @Test
        void collectionContains_wrongOwner_notHandled() {
            context.push(captured(0, List.class));
            context.push(field("city", String.class));

            var insn = createMethodInsn(INVOKEINTERFACE, "java/util/Map", "contains", "(Ljava/lang/Object;)Z");
            handler.handle(insn, context);

            // Map.contains is not recognized as Collection.contains
            assertThat(context.getStackSize()).isEqualTo(2);
        }

        @Test
        void collectionContains_wrongDescriptor_notHandled() {
            context.push(captured(0, List.class));
            context.push(field("city", String.class));

            var insn = createMethodInsn(INVOKEINTERFACE, "java/util/Collection", "contains", "(I)Z");
            handler.handle(insn, context);

            assertThat(context.getStackSize()).isEqualTo(2);
        }

        @Test
        void collectionContains_wrongMethodName_notHandled() {
            context.push(captured(0, List.class));
            context.push(field("city", String.class));

            var insn = createMethodInsn(INVOKEINTERFACE, "java/util/Collection", "containsAll", "(Ljava/lang/Object;)Z");
            handler.handle(insn, context);

            assertThat(context.getStackSize()).isEqualTo(2);
        }
    }

    // ==================== Equals and CompareTo Boundary Tests ====================

    @Nested
    @DisplayName("Equals and compareTo boundary conditions")
    class EqualsCompareTosBoundaryTests {

        @Test
        void equals_wrongDescriptor_notHandled() {
            context.push(field("name", String.class));
            context.push(constant("John"));

            var insn = createMethodInsn(INVOKEVIRTUAL, "java/lang/Object", "equals", "(I)Z");
            handler.handle(insn, context);

            assertThat(context.getStackSize()).isEqualTo(2);
        }

        @Test
        void equals_wrongMethodName_notHandled() {
            context.push(field("name", String.class));
            context.push(constant("John"));

            var insn = createMethodInsn(INVOKEVIRTUAL, "java/lang/Object", "equal", "(Ljava/lang/Object;)Z");
            handler.handle(insn, context);

            assertThat(context.getStackSize()).isEqualTo(2);
        }

        @Test
        void compareTo_wrongDescriptor_notHandled() {
            context.push(field("value", Integer.class));
            context.push(constant(10));

            // Returns void instead of int
            var insn = createMethodInsn(INVOKEVIRTUAL, "java/lang/Integer", "compareTo", "(Ljava/lang/Integer;)V");
            handler.handle(insn, context);

            assertThat(context.getStackSize()).isEqualTo(2);
        }

        @Test
        void compareTo_wrongMethodName_notHandled() {
            context.push(field("value", Integer.class));
            context.push(constant(10));

            var insn = createMethodInsn(INVOKEVIRTUAL, "java/lang/Integer", "compare", "(Ljava/lang/Integer;)I");
            handler.handle(insn, context);

            assertThat(context.getStackSize()).isEqualTo(2);
        }
    }

    // ==================== Edge Cases ====================

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCaseTests {

        @Test
        void equals_withEmptyStack_throwsStackUnderflow() {
            var insn = createMethodInsn(INVOKEVIRTUAL, "java/lang/Object", "equals", "(Ljava/lang/Object;)Z");

            assertThatThrownBy(() -> handler.handle(insn, context))
                    .hasMessageContaining("Stack underflow");
        }

        @Test
        void stringMethod_withEmptyStack_handlesGracefully() {
            var insn = createMethodInsn(INVOKEVIRTUAL, "java/lang/String", "length", "()I");
            handler.handle(insn, context);

            // Should not throw - no-argument methods check isStackEmpty first
            assertThat(context.isStackEmpty()).isTrue();
        }

        @Test
        void collectionContains_withEmptyStack_throwsStackUnderflow() {
            var insn = createMethodInsn(INVOKEINTERFACE, "java/util/Collection", "contains",
                    "(Ljava/lang/Object;)Z");

            assertThatThrownBy(() -> handler.handle(insn, context))
                    .hasMessageContaining("Stack underflow");
        }
    }

    // ==================== Mutation Killing Tests: handleStringMethods switch branches ====================

    /**
     * Tests targeting surviving mutations in handleStringMethods switch branches.
     * Each test verifies that the correct method name produces the correct result,
     * AND that wrong methods don't accidentally match.
     */
    @Nested
    @DisplayName("Mutation killing: handleStringMethods switch branches")
    class HandleStringMethodsMutationKillingTests {

        @Test
        void startsWith_producesStartsWithMethodCall_notAnotherMethod() {
            context.push(field("name", String.class));
            context.push(constant("prefix"));

            var insn = createMethodInsn(INVOKEVIRTUAL, "java/lang/String", "startsWith", "(Ljava/lang/String;)Z");
            handler.handle(insn, context);

            assertThat(context.getStackSize()).isEqualTo(1);
            var result = context.pop();
            assertThat(result).isInstanceOf(LambdaExpression.MethodCall.class);
            var methodCall = (LambdaExpression.MethodCall) result;
            assertThat(methodCall.methodName())
                    .as("startsWith should produce 'startsWith' method call, not any other")
                    .isEqualTo("startsWith")
                    .isNotEqualTo("endsWith")
                    .isNotEqualTo("contains");
            assertThat(methodCall.returnType()).isEqualTo(boolean.class);
        }

        @Test
        void endsWith_producesEndsWithMethodCall_notAnotherMethod() {
            context.push(field("name", String.class));
            context.push(constant("suffix"));

            var insn = createMethodInsn(INVOKEVIRTUAL, "java/lang/String", "endsWith", "(Ljava/lang/String;)Z");
            handler.handle(insn, context);

            assertThat(context.getStackSize()).isEqualTo(1);
            var result = context.pop();
            assertThat(result).isInstanceOf(LambdaExpression.MethodCall.class);
            var methodCall = (LambdaExpression.MethodCall) result;
            assertThat(methodCall.methodName())
                    .as("endsWith should produce 'endsWith' method call, not any other")
                    .isEqualTo("endsWith")
                    .isNotEqualTo("startsWith")
                    .isNotEqualTo("contains");
            assertThat(methodCall.returnType()).isEqualTo(boolean.class);
        }

        @Test
        void contains_producesContainsMethodCall_notAnotherMethod() {
            context.push(field("name", String.class));
            context.push(constant("sub"));

            var insn = createMethodInsn(INVOKEVIRTUAL, "java/lang/String", "contains", "(Ljava/lang/CharSequence;)Z");
            handler.handle(insn, context);

            assertThat(context.getStackSize()).isEqualTo(1);
            var result = context.pop();
            assertThat(result).isInstanceOf(LambdaExpression.MethodCall.class);
            var methodCall = (LambdaExpression.MethodCall) result;
            assertThat(methodCall.methodName())
                    .as("contains should produce 'contains' method call")
                    .isEqualTo("contains");
            assertThat(methodCall.returnType()).isEqualTo(boolean.class);
        }

        @Test
        void length_producesLengthMethodCall_notAnotherMethod() {
            context.push(field("name", String.class));

            var insn = createMethodInsn(INVOKEVIRTUAL, "java/lang/String", "length", "()I");
            handler.handle(insn, context);

            assertThat(context.getStackSize()).isEqualTo(1);
            var result = context.pop();
            assertThat(result).isInstanceOf(LambdaExpression.MethodCall.class);
            var methodCall = (LambdaExpression.MethodCall) result;
            assertThat(methodCall.methodName())
                    .as("length should produce 'length' method call")
                    .isEqualTo("length")
                    .isNotEqualTo("isEmpty");
            assertThat(methodCall.returnType()).isEqualTo(int.class);
        }

        @Test
        void isEmpty_producesIsEmptyMethodCall_notAnotherMethod() {
            context.push(field("name", String.class));

            var insn = createMethodInsn(INVOKEVIRTUAL, "java/lang/String", "isEmpty", "()Z");
            handler.handle(insn, context);

            assertThat(context.getStackSize()).isEqualTo(1);
            var result = context.pop();
            assertThat(result).isInstanceOf(LambdaExpression.MethodCall.class);
            var methodCall = (LambdaExpression.MethodCall) result;
            assertThat(methodCall.methodName())
                    .as("isEmpty should produce 'isEmpty' method call")
                    .isEqualTo("isEmpty")
                    .isNotEqualTo("length");
            assertThat(methodCall.returnType()).isEqualTo(boolean.class);
        }

        @Test
        void toLowerCase_producesToLowerCaseMethodCall_notAnotherMethod() {
            context.push(field("name", String.class));

            var insn = createMethodInsn(INVOKEVIRTUAL, "java/lang/String", "toLowerCase", "()Ljava/lang/String;");
            handler.handle(insn, context);

            assertThat(context.getStackSize()).isEqualTo(1);
            var result = context.pop();
            assertThat(result).isInstanceOf(LambdaExpression.MethodCall.class);
            var methodCall = (LambdaExpression.MethodCall) result;
            assertThat(methodCall.methodName())
                    .as("toLowerCase should produce 'toLowerCase' method call")
                    .isEqualTo("toLowerCase")
                    .isNotEqualTo("toUpperCase")
                    .isNotEqualTo("trim");
            assertThat(methodCall.returnType()).isEqualTo(String.class);
        }

        @Test
        void toUpperCase_producesToUpperCaseMethodCall_notAnotherMethod() {
            context.push(field("name", String.class));

            var insn = createMethodInsn(INVOKEVIRTUAL, "java/lang/String", "toUpperCase", "()Ljava/lang/String;");
            handler.handle(insn, context);

            assertThat(context.getStackSize()).isEqualTo(1);
            var result = context.pop();
            assertThat(result).isInstanceOf(LambdaExpression.MethodCall.class);
            var methodCall = (LambdaExpression.MethodCall) result;
            assertThat(methodCall.methodName())
                    .as("toUpperCase should produce 'toUpperCase' method call")
                    .isEqualTo("toUpperCase")
                    .isNotEqualTo("toLowerCase")
                    .isNotEqualTo("trim");
            assertThat(methodCall.returnType()).isEqualTo(String.class);
        }

        @Test
        void trim_producesTrimMethodCall_notAnotherMethod() {
            context.push(field("name", String.class));

            var insn = createMethodInsn(INVOKEVIRTUAL, "java/lang/String", "trim", "()Ljava/lang/String;");
            handler.handle(insn, context);

            assertThat(context.getStackSize()).isEqualTo(1);
            var result = context.pop();
            assertThat(result).isInstanceOf(LambdaExpression.MethodCall.class);
            var methodCall = (LambdaExpression.MethodCall) result;
            assertThat(methodCall.methodName())
                    .as("trim should produce 'trim' method call")
                    .isEqualTo("trim")
                    .isNotEqualTo("toLowerCase")
                    .isNotEqualTo("toUpperCase");
            assertThat(methodCall.returnType()).isEqualTo(String.class);
        }

        @Test
        void substring_producesSubstringMethodCall_notAnotherMethod() {
            context.push(field("name", String.class));
            context.push(constant(5));

            var insn = createMethodInsn(INVOKEVIRTUAL, "java/lang/String", "substring", "(I)Ljava/lang/String;");
            handler.handle(insn, context);

            assertThat(context.getStackSize()).isEqualTo(1);
            var result = context.pop();
            assertThat(result).isInstanceOf(LambdaExpression.MethodCall.class);
            var methodCall = (LambdaExpression.MethodCall) result;
            assertThat(methodCall.methodName())
                    .as("substring should produce 'substring' method call")
                    .isEqualTo("substring");
            assertThat(methodCall.returnType()).isEqualTo(String.class);
        }
    }

    // ==================== Mutation Killing Tests: handleBigDecimalMethods switch branches ====================

    @Nested
    @DisplayName("Mutation killing: handleBigDecimalMethods switch branches")
    class HandleBigDecimalMethodsMutationKillingTests {

        @Test
        void add_producesAddMethodCall_notAnotherOperation() {
            context.push(field("price", BigDecimal.class));
            context.push(constant(new BigDecimal("10")));

            var insn = createMethodInsn(INVOKEVIRTUAL, "java/math/BigDecimal", "add",
                    "(Ljava/math/BigDecimal;)Ljava/math/BigDecimal;");
            handler.handle(insn, context);

            assertThat(context.getStackSize()).isEqualTo(1);
            var result = context.pop();
            assertThat(result).isInstanceOf(LambdaExpression.MethodCall.class);
            var methodCall = (LambdaExpression.MethodCall) result;
            assertThat(methodCall.methodName())
                    .as("add should produce 'add' method call, not subtract/multiply/divide")
                    .isEqualTo("add")
                    .isNotEqualTo("subtract")
                    .isNotEqualTo("multiply")
                    .isNotEqualTo("divide");
            assertThat(methodCall.returnType()).isEqualTo(BigDecimal.class);
        }

        @Test
        void subtract_producesSubtractMethodCall_notAnotherOperation() {
            context.push(field("price", BigDecimal.class));
            context.push(constant(new BigDecimal("10")));

            var insn = createMethodInsn(INVOKEVIRTUAL, "java/math/BigDecimal", "subtract",
                    "(Ljava/math/BigDecimal;)Ljava/math/BigDecimal;");
            handler.handle(insn, context);

            assertThat(context.getStackSize()).isEqualTo(1);
            var result = context.pop();
            assertThat(result).isInstanceOf(LambdaExpression.MethodCall.class);
            var methodCall = (LambdaExpression.MethodCall) result;
            assertThat(methodCall.methodName())
                    .as("subtract should produce 'subtract' method call, not add/multiply/divide")
                    .isEqualTo("subtract")
                    .isNotEqualTo("add")
                    .isNotEqualTo("multiply")
                    .isNotEqualTo("divide");
            assertThat(methodCall.returnType()).isEqualTo(BigDecimal.class);
        }

        @Test
        void multiply_producesMultiplyMethodCall_notAnotherOperation() {
            context.push(field("price", BigDecimal.class));
            context.push(constant(new BigDecimal("10")));

            var insn = createMethodInsn(INVOKEVIRTUAL, "java/math/BigDecimal", "multiply",
                    "(Ljava/math/BigDecimal;)Ljava/math/BigDecimal;");
            handler.handle(insn, context);

            assertThat(context.getStackSize()).isEqualTo(1);
            var result = context.pop();
            assertThat(result).isInstanceOf(LambdaExpression.MethodCall.class);
            var methodCall = (LambdaExpression.MethodCall) result;
            assertThat(methodCall.methodName())
                    .as("multiply should produce 'multiply' method call, not add/subtract/divide")
                    .isEqualTo("multiply")
                    .isNotEqualTo("add")
                    .isNotEqualTo("subtract")
                    .isNotEqualTo("divide");
            assertThat(methodCall.returnType()).isEqualTo(BigDecimal.class);
        }

        @Test
        void divide_producesDivideMethodCall_notAnotherOperation() {
            context.push(field("price", BigDecimal.class));
            context.push(constant(new BigDecimal("10")));

            var insn = createMethodInsn(INVOKEVIRTUAL, "java/math/BigDecimal", "divide",
                    "(Ljava/math/BigDecimal;)Ljava/math/BigDecimal;");
            handler.handle(insn, context);

            assertThat(context.getStackSize()).isEqualTo(1);
            var result = context.pop();
            assertThat(result).isInstanceOf(LambdaExpression.MethodCall.class);
            var methodCall = (LambdaExpression.MethodCall) result;
            assertThat(methodCall.methodName())
                    .as("divide should produce 'divide' method call, not add/subtract/multiply")
                    .isEqualTo("divide")
                    .isNotEqualTo("add")
                    .isNotEqualTo("subtract")
                    .isNotEqualTo("multiply");
            assertThat(methodCall.returnType()).isEqualTo(BigDecimal.class);
        }

        @Test
        void unknownBigDecimalMethod_isNotHandled() {
            context.push(field("price", BigDecimal.class));
            context.push(constant(new BigDecimal("10")));

            // abs() is a valid BigDecimal method but not in our handled set
            var insn = createMethodInsn(INVOKEVIRTUAL, "java/math/BigDecimal", "abs",
                    "(Ljava/math/BigDecimal;)Ljava/math/BigDecimal;");
            handler.handle(insn, context);

            // Stack unchanged because abs is not in the switch
            assertThat(context.getStackSize()).isEqualTo(2);
        }
    }

    // ==================== Mutation Killing Tests: handleInvokeStatic Boolean.valueOf checks ====================

    @Nested
    @DisplayName("Mutation killing: handleInvokeStatic Boolean.valueOf conditions")
    class HandleInvokeStaticBooleanValueOfMutationKillingTests {

        @Test
        void booleanValueOf_allConditionsMustMatch_toBeSkipped() {
            var boolExpr = eq(field("active", boolean.class), constant(true));
            context.push(boolExpr);
            int initialSize = context.getStackSize();

            // All three conditions: owner=Boolean, name=valueOf, desc=(Z)Ljava/lang/Boolean;
            var insn = createMethodInsn(INVOKESTATIC, "java/lang/Boolean", "valueOf", "(Z)Ljava/lang/Boolean;");
            handler.handle(insn, context);

            // Should be skipped - stack unchanged, exact same object
            assertThat(context.getStackSize()).isEqualTo(initialSize);
            assertThat(context.pop()).isSameAs(boolExpr);
        }

        @Test
        void booleanValueOf_wrongOwner_mustNotMatch() {
            context.push(constant(true));
            int initialSize = context.getStackSize();

            // Wrong owner (Integer instead of Boolean)
            var insn = createMethodInsn(INVOKESTATIC, "java/lang/Integer", "valueOf", "(Z)Ljava/lang/Boolean;");
            handler.handle(insn, context);

            // Should NOT be skipped, but we don't have handler for Integer.valueOf
            // so stack remains unchanged
            assertThat(context.getStackSize()).isEqualTo(initialSize);
        }

        @Test
        void booleanValueOf_wrongMethodName_mustNotMatch() {
            context.push(constant(true));
            int initialSize = context.getStackSize();

            // Wrong method name (parseBoolean instead of valueOf)
            var insn = createMethodInsn(INVOKESTATIC, "java/lang/Boolean", "parseBoolean", "(Z)Ljava/lang/Boolean;");
            handler.handle(insn, context);

            assertThat(context.getStackSize()).isEqualTo(initialSize);
        }

        @Test
        void booleanValueOf_wrongDescriptor_mustNotMatch() {
            context.push(constant("true"));
            int initialSize = context.getStackSize();

            // Wrong descriptor (String -> Boolean instead of boolean -> Boolean)
            var insn = createMethodInsn(INVOKESTATIC, "java/lang/Boolean", "valueOf", "(Ljava/lang/String;)Ljava/lang/Boolean;");
            handler.handle(insn, context);

            assertThat(context.getStackSize()).isEqualTo(initialSize);
        }
    }

    // ==================== Mutation Killing Tests: handleTemporalMethods checks ====================

    @Nested
    @DisplayName("Mutation killing: handleTemporalMethods accessor method checks")
    class HandleTemporalMethodsMutationKillingTests {

        @Test
        void localDate_getYear_isValidAccessor() {
            context.push(field("date", java.time.LocalDate.class));

            var insn = createMethodInsn(INVOKEVIRTUAL, "java/time/LocalDate", "getYear", "()I");
            handler.handle(insn, context);

            assertThat(context.getStackSize()).isEqualTo(1);
            var result = context.pop();
            assertThat(result).isInstanceOf(LambdaExpression.MethodCall.class);
            var methodCall = (LambdaExpression.MethodCall) result;
            assertThat(methodCall.methodName()).isEqualTo("getYear");
        }

        @Test
        void localDate_getMonthValue_isValidAccessor() {
            context.push(field("date", java.time.LocalDate.class));

            var insn = createMethodInsn(INVOKEVIRTUAL, "java/time/LocalDate", "getMonthValue", "()I");
            handler.handle(insn, context);

            assertThat(context.getStackSize()).isEqualTo(1);
            var result = context.pop();
            assertThat(result).isInstanceOf(LambdaExpression.MethodCall.class);
            var methodCall = (LambdaExpression.MethodCall) result;
            assertThat(methodCall.methodName()).isEqualTo("getMonthValue");
        }

        @Test
        void localDate_getDayOfMonth_isValidAccessor() {
            context.push(field("date", java.time.LocalDate.class));

            var insn = createMethodInsn(INVOKEVIRTUAL, "java/time/LocalDate", "getDayOfMonth", "()I");
            handler.handle(insn, context);

            assertThat(context.getStackSize()).isEqualTo(1);
            var result = context.pop();
            assertThat(result).isInstanceOf(LambdaExpression.MethodCall.class);
            var methodCall = (LambdaExpression.MethodCall) result;
            assertThat(methodCall.methodName()).isEqualTo("getDayOfMonth");
        }

        @Test
        void localDateTime_getHour_isValidAccessor() {
            context.push(field("dateTime", java.time.LocalDateTime.class));

            var insn = createMethodInsn(INVOKEVIRTUAL, "java/time/LocalDateTime", "getHour", "()I");
            handler.handle(insn, context);

            assertThat(context.getStackSize()).isEqualTo(1);
            var result = context.pop();
            assertThat(result).isInstanceOf(LambdaExpression.MethodCall.class);
        }

        @Test
        void localTime_getSecond_isValidAccessor() {
            context.push(field("time", java.time.LocalTime.class));

            var insn = createMethodInsn(INVOKEVIRTUAL, "java/time/LocalTime", "getSecond", "()I");
            handler.handle(insn, context);

            assertThat(context.getStackSize()).isEqualTo(1);
            var result = context.pop();
            assertThat(result).isInstanceOf(LambdaExpression.MethodCall.class);
        }

        @Test
        void localDate_invalidMethod_isNotHandledAsAccessor() {
            context.push(field("date", java.time.LocalDate.class));

            // getDayOfWeek is not in the LOCAL_DATE_ACCESSOR_METHODS set
            var insn = createMethodInsn(INVOKEVIRTUAL, "java/time/LocalDate", "getDayOfWeek",
                    "()Ljava/time/DayOfWeek;");
            handler.handle(insn, context);

            // Stack unchanged - not a recognized accessor
            assertThat(context.getStackSize()).isEqualTo(1);
            // Still has the original field
            var result = context.pop();
            assertThat(result).isInstanceOf(LambdaExpression.FieldAccess.class);
        }
    }

    // ==================== Mutation Killing Tests: isMemberOfPattern checks ====================

    @Nested
    @DisplayName("Mutation killing: isMemberOfPattern instanceof checks")
    class IsMemberOfPatternMutationKillingTests {

        @Test
        void memberOf_constantArgument_matchesMemberOfPattern() {
            // Entity collection field as target
            var rolesField = field("roles", List.class);
            context.push(rolesField);
            // Constant as argument
            context.push(constant("admin"));

            var insn = createMethodInsn(INVOKEINTERFACE, "java/util/Collection", "contains", "(Ljava/lang/Object;)Z");
            handler.handle(insn, context);

            assertThat(context.getStackSize()).isEqualTo(1);
            var result = context.pop();
            assertThat(result)
                    .as("Constant argument with entity field target should create MemberOfExpression")
                    .isInstanceOf(LambdaExpression.MemberOfExpression.class);
        }

        @Test
        void memberOf_capturedVariableArgument_matchesMemberOfPattern() {
            // Entity collection field as target
            var rolesField = field("roles", List.class);
            context.push(rolesField);
            // Captured variable as argument
            context.push(captured(0, String.class));

            var insn = createMethodInsn(INVOKEINTERFACE, "java/util/Collection", "contains", "(Ljava/lang/Object;)Z");
            handler.handle(insn, context);

            assertThat(context.getStackSize()).isEqualTo(1);
            var result = context.pop();
            assertThat(result)
                    .as("CapturedVariable argument with entity field target should create MemberOfExpression")
                    .isInstanceOf(LambdaExpression.MemberOfExpression.class);
        }

        @Test
        void contains_fieldArgument_doesNotMatchMemberOf_fallsBackToMethodCall() {
            // Both are field accesses - neither pattern applies
            var rolesField = field("roles", List.class);
            context.push(rolesField);
            var otherField = field("category", String.class);
            context.push(otherField);

            var insn = createMethodInsn(INVOKEINTERFACE, "java/util/Collection", "contains", "(Ljava/lang/Object;)Z");
            handler.handle(insn, context);

            assertThat(context.getStackSize()).isEqualTo(1);
            var result = context.pop();
            // Neither IN nor MEMBER OF pattern - falls back to MethodCall
            assertThat(result)
                    .as("Field argument with field target should fall back to MethodCall")
                    .isInstanceOf(LambdaExpression.MethodCall.class);
        }
    }

    // ==================== Mutation Killing Tests: isBigDecimalStringConstruction checks ====================

    @Nested
    @DisplayName("Mutation killing: isBigDecimalStringConstruction checks")
    class IsBigDecimalStringConstructionMutationKillingTests {

        @Test
        void bigDecimalStringConstructor_correctOwner_isFolded() {
            context.push(constant("placeholder1")); // NEW
            context.push(constant("placeholder2")); // DUP
            context.push(constant("99.99"));

            var insn = createMethodInsn(INVOKESPECIAL, "java/math/BigDecimal", "<init>", "(Ljava/lang/String;)V");
            handler.handle(insn, context);

            assertThat(context.getStackSize()).isEqualTo(1);
            var result = context.pop();
            assertThat(result)
                    .as("BigDecimal(String) with correct owner should fold to Constant")
                    .isInstanceOf(LambdaExpression.Constant.class);
            var constant = (LambdaExpression.Constant) result;
            assertThat(constant.value()).isEqualTo(new BigDecimal("99.99"));
        }

        @Test
        void bigDecimalStringConstructor_wrongOwner_notFolded() {
            context.push(constant("placeholder1")); // NEW
            context.push(constant("placeholder2")); // DUP
            context.push(constant("99.99"));

            // Wrong owner (Integer instead of BigDecimal)
            var insn = createMethodInsn(INVOKESPECIAL, "java/lang/Integer", "<init>", "(Ljava/lang/String;)V");
            handler.handle(insn, context);

            assertThat(context.getStackSize()).isEqualTo(1);
            var result = context.pop();
            assertThat(result)
                    .as("Constructor with wrong owner should create ConstructorCall, not Constant")
                    .isInstanceOf(LambdaExpression.ConstructorCall.class);
        }

        @Test
        void bigDecimalStringConstructor_emptyArgs_notFolded() {
            context.push(constant("placeholder1")); // NEW
            context.push(constant("placeholder2")); // DUP
            // No string argument

            var insn = createMethodInsn(INVOKESPECIAL, "java/math/BigDecimal", "<init>", "()V");
            handler.handle(insn, context);

            // Should create ConstructorCall for no-arg constructor
            assertThat(context.getStackSize()).isEqualTo(1);
            var result = context.pop();
            assertThat(result).isInstanceOf(LambdaExpression.ConstructorCall.class);
        }

        @Test
        void bigDecimalStringConstructor_nonConstantArg_notFolded() {
            context.push(constant("placeholder1")); // NEW
            context.push(constant("placeholder2")); // DUP
            context.push(field("priceString", String.class)); // Non-constant arg

            var insn = createMethodInsn(INVOKESPECIAL, "java/math/BigDecimal", "<init>", "(Ljava/lang/String;)V");
            handler.handle(insn, context);

            assertThat(context.getStackSize()).isEqualTo(1);
            var result = context.pop();
            assertThat(result)
                    .as("Non-constant arg should create ConstructorCall, not fold to Constant")
                    .isInstanceOf(LambdaExpression.ConstructorCall.class);
        }

        @Test
        void bigDecimalStringConstructor_nonStringConstant_notFolded() {
            context.push(constant("placeholder1")); // NEW
            context.push(constant("placeholder2")); // DUP
            context.push(constant(123)); // Integer, not String

            var insn = createMethodInsn(INVOKESPECIAL, "java/math/BigDecimal", "<init>", "(I)V");
            handler.handle(insn, context);

            assertThat(context.getStackSize()).isEqualTo(1);
            var result = context.pop();
            assertThat(result)
                    .as("Non-String constant should create ConstructorCall, not fold to Constant")
                    .isInstanceOf(LambdaExpression.ConstructorCall.class);
        }
    }

    // ==================== Mutation Killing Tests: handleSubstringMethod descriptor checks ====================

    @Nested
    @DisplayName("Mutation killing: handleSubstringMethod descriptor checks")
    class HandleSubstringMethodMutationKillingTests {

        @Test
        void substringOneArg_correctDescriptor_isHandled() {
            context.push(field("name", String.class));
            context.push(constant(5));

            var insn = createMethodInsn(INVOKEVIRTUAL, "java/lang/String", "substring", "(I)Ljava/lang/String;");
            handler.handle(insn, context);

            assertThat(context.getStackSize()).isEqualTo(1);
            var result = context.pop();
            assertThat(result).isInstanceOf(LambdaExpression.MethodCall.class);
            var methodCall = (LambdaExpression.MethodCall) result;
            assertThat(methodCall.arguments()).hasSize(1);
        }

        @Test
        void substringTwoArgs_correctDescriptor_isHandled() {
            context.push(field("name", String.class));
            context.push(constant(0));
            context.push(constant(5));

            var insn = createMethodInsn(INVOKEVIRTUAL, "java/lang/String", "substring", "(II)Ljava/lang/String;");
            handler.handle(insn, context);

            assertThat(context.getStackSize()).isEqualTo(1);
            var result = context.pop();
            assertThat(result).isInstanceOf(LambdaExpression.MethodCall.class);
            var methodCall = (LambdaExpression.MethodCall) result;
            assertThat(methodCall.arguments()).hasSize(2);
        }

        @Test
        void substringOneArg_argCountVerification() {
            context.push(field("name", String.class));
            context.push(constant(5));

            var insn = createMethodInsn(INVOKEVIRTUAL, "java/lang/String", "substring", "(I)Ljava/lang/String;");
            handler.handle(insn, context);

            var result = (LambdaExpression.MethodCall) context.pop();
            // For single-arg substring, argCount should be 1, not 2
            assertThat(result.arguments())
                    .as("Single-arg substring should have exactly 1 argument")
                    .hasSize(1);
        }
    }

    // ==================== Mutation Killing Tests: handleCollectionContains null check ====================

    @Nested
    @DisplayName("Mutation killing: handleCollectionContains pair null check")
    class HandleCollectionContainsNullCheckTests {

        @Test
        void collectionContains_validPair_producesResult() {
            context.push(captured(0, List.class));
            context.push(field("city", String.class));

            var insn = createMethodInsn(INVOKEINTERFACE, "java/util/Collection", "contains", "(Ljava/lang/Object;)Z");
            handler.handle(insn, context);

            assertThat(context.getStackSize())
                    .as("Valid pair should produce exactly one result")
                    .isEqualTo(1);
        }

        @Test
        void collectionContains_nullCheckPreventsNPE() {
            // Only one item on stack - popPair will return null
            context.push(field("city", String.class));

            var insn = createMethodInsn(INVOKEINTERFACE, "java/util/Collection", "contains", "(Ljava/lang/Object;)Z");

            // Should throw due to stack underflow, not NPE from null pair
            assertThatThrownBy(() -> handler.handle(insn, context))
                    .hasMessageContaining("underflow");
        }
    }

    // ==================== Mutation Killing Tests: handleEqualsMethod null check ====================

    @Nested
    @DisplayName("Mutation killing: handleEqualsMethod pair null check")
    class HandleEqualsMethodNullCheckTests {

        @Test
        void equalsMethod_validPair_producesEquality() {
            context.push(field("name", String.class));
            context.push(constant("John"));

            var insn = createMethodInsn(INVOKEVIRTUAL, "java/lang/String", "equals", "(Ljava/lang/Object;)Z");
            handler.handle(insn, context);

            assertThat(context.getStackSize()).isEqualTo(1);
            var result = context.pop();
            assertThat(result).isInstanceOf(LambdaExpression.BinaryOp.class);
        }
    }

    // ==================== Mutation Killing Tests: handle() return value ====================

    /**
     * Tests that verify handle() always returns false.
     * This kills BooleanTrueReturnValsMutator on line 133.
     */
    @Nested
    @DisplayName("Mutation killing: handle() return value")
    class HandleReturnValueTests {

        @Test
        void handle_invokeVirtual_equals_returnsFalse() {
            context.push(field("name", String.class));
            context.push(constant("John"));

            var insn = createMethodInsn(INVOKEVIRTUAL, "java/lang/String", "equals", "(Ljava/lang/Object;)Z");
            boolean result = handler.handle(insn, context);

            assertThat(result)
                    .as("handle() must return false for INVOKEVIRTUAL")
                    .isFalse();
        }

        @Test
        void handle_invokeStatic_booleanValueOf_returnsFalse() {
            context.push(constant(true));

            var insn = createMethodInsn(INVOKESTATIC, "java/lang/Boolean", "valueOf", "(Z)Ljava/lang/Boolean;");
            boolean result = handler.handle(insn, context);

            assertThat(result)
                    .as("handle() must return false for INVOKESTATIC")
                    .isFalse();
        }

        @Test
        void handle_invokeSpecial_constructor_returnsFalse() {
            context.push(constant("placeholder1"));
            context.push(constant("placeholder2"));
            context.push(constant("123.45"));

            var insn = createMethodInsn(INVOKESPECIAL, "java/math/BigDecimal", "<init>", "(Ljava/lang/String;)V");
            boolean result = handler.handle(insn, context);

            assertThat(result)
                    .as("handle() must return false for INVOKESPECIAL")
                    .isFalse();
        }

        @Test
        void handle_invokeInterface_collectionContains_returnsFalse() {
            context.push(captured(0, List.class));
            context.push(field("city", String.class));

            var insn = createMethodInsn(INVOKEINTERFACE, "java/util/Collection", "contains", "(Ljava/lang/Object;)Z");
            boolean result = handler.handle(insn, context);

            assertThat(result)
                    .as("handle() must return false for INVOKEINTERFACE")
                    .isFalse();
        }

        @Test
        void handle_unhandledMethod_returnsFalse() {
            context.push(field("value", Object.class));

            var insn = createMethodInsn(INVOKEVIRTUAL, "com/example/Custom", "customMethod", "()V");
            boolean result = handler.handle(insn, context);

            assertThat(result)
                    .as("handle() must return false even for unhandled methods")
                    .isFalse();
        }
    }

    // ==================== Mutation Killing Tests: handleSubstringMethod argCount ====================

    /**
     * Tests targeting MathMutator on line 364 which changes argCount + 1 to argCount - 1.
     * These tests verify exact stack size boundary conditions.
     */
    @Nested
    @DisplayName("Mutation killing: handleSubstringMethod argCount boundary")
    class HandleSubstringMethodArgCountTests {

        @Test
        void substringOneArg_exactStackSize_handlesCorrectly() {
            // Stack: target string + 1 arg = 2 items
            // If mutation changes (argCount + 1) to (argCount - 1),
            // the check becomes stackSize >= 0 instead of stackSize >= 2
            context.push(field("name", String.class));
            context.push(constant(5));

            var insn = createMethodInsn(INVOKEVIRTUAL, "java/lang/String", "substring", "(I)Ljava/lang/String;");
            handler.handle(insn, context);

            assertThat(context.getStackSize()).isEqualTo(1);
            var result = context.pop();
            assertThat(result).isInstanceOf(LambdaExpression.MethodCall.class);
            var methodCall = (LambdaExpression.MethodCall) result;
            assertThat(methodCall.methodName()).isEqualTo("substring");
            assertThat(methodCall.arguments()).hasSize(1);
        }

        @Test
        void substringTwoArgs_exactStackSize_handlesCorrectly() {
            // Stack: target string + 2 args = 3 items
            // If mutation changes (argCount + 1) to (argCount - 1),
            // the check becomes stackSize >= 1 instead of stackSize >= 3
            context.push(field("name", String.class));
            context.push(constant(0));
            context.push(constant(5));

            var insn = createMethodInsn(INVOKEVIRTUAL, "java/lang/String", "substring", "(II)Ljava/lang/String;");
            handler.handle(insn, context);

            assertThat(context.getStackSize()).isEqualTo(1);
            var result = context.pop();
            assertThat(result).isInstanceOf(LambdaExpression.MethodCall.class);
            var methodCall = (LambdaExpression.MethodCall) result;
            assertThat(methodCall.methodName()).isEqualTo("substring");
            assertThat(methodCall.arguments()).hasSize(2);
        }

        @Test
        void substringOneArg_insufficientStack_doesNotHandle() {
            // Only 1 item on stack (need 2 for one-arg substring)
            context.push(field("name", String.class));
            // Missing the int argument

            var insn = createMethodInsn(INVOKEVIRTUAL, "java/lang/String", "substring", "(I)Ljava/lang/String;");
            handler.handle(insn, context);

            // Should not process - stack should still have original item
            assertThat(context.getStackSize()).isEqualTo(1);
            var result = context.pop();
            assertThat(result).isInstanceOf(LambdaExpression.FieldAccess.class);
        }

        @Test
        void substringTwoArgs_insufficientStack_doesNotHandle() {
            // Only 2 items on stack (need 3 for two-arg substring)
            context.push(field("name", String.class));
            context.push(constant(0));
            // Missing second int argument

            var insn = createMethodInsn(INVOKEVIRTUAL, "java/lang/String", "substring", "(II)Ljava/lang/String;");
            handler.handle(insn, context);

            // Should not process - stack should still have original items
            assertThat(context.getStackSize()).isEqualTo(2);
        }
    }

    // ==================== Mutation Killing Tests: handleInvokeStatic conditions ====================

    /**
     * Tests targeting EQUAL_IF mutations in handleInvokeStatic method.
     * These verify that all condition branches are exercised.
     */
    @Nested
    @DisplayName("Mutation killing: handleInvokeStatic conditions")
    class HandleInvokeStaticConditionsTests {

        @Test
        void localDateOf_allConstantsWithCorrectDescriptor_foldsToConstant() {
            context.push(constant(2024));
            context.push(constant(6));
            context.push(constant(15));

            var insn = createMethodInsn(INVOKESTATIC, "java/time/LocalDate", "of", "(III)Ljava/time/LocalDate;");
            handler.handle(insn, context);

            assertThat(context.getStackSize()).isEqualTo(1);
            var result = context.pop();
            assertThat(result)
                    .as("LocalDate.of with all constants should fold to Constant")
                    .isInstanceOf(LambdaExpression.Constant.class);
        }

        @Test
        void localDateOf_oneNonConstant_createsMethodCall() {
            context.push(field("year", int.class));  // Non-constant
            context.push(constant(6));
            context.push(constant(15));

            var insn = createMethodInsn(INVOKESTATIC, "java/time/LocalDate", "of", "(III)Ljava/time/LocalDate;");
            handler.handle(insn, context);

            assertThat(context.getStackSize()).isEqualTo(1);
            var result = context.pop();
            assertThat(result)
                    .as("LocalDate.of with non-constant should create MethodCall")
                    .isInstanceOf(LambdaExpression.MethodCall.class);
        }

        @Test
        void localTimeOf_twoArgs_allConstants_foldsToConstant() {
            context.push(constant(14));
            context.push(constant(30));

            var insn = createMethodInsn(INVOKESTATIC, "java/time/LocalTime", "of", "(II)Ljava/time/LocalTime;");
            handler.handle(insn, context);

            assertThat(context.getStackSize()).isEqualTo(1);
            var result = context.pop();
            assertThat(result)
                    .as("LocalTime.of(hour, minute) with constants should fold")
                    .isInstanceOf(LambdaExpression.Constant.class);
        }

        @Test
        void localTimeOf_threeArgs_allConstants_foldsToConstant() {
            context.push(constant(14));
            context.push(constant(30));
            context.push(constant(45));

            var insn = createMethodInsn(INVOKESTATIC, "java/time/LocalTime", "of", "(III)Ljava/time/LocalTime;");
            handler.handle(insn, context);

            assertThat(context.getStackSize()).isEqualTo(1);
            var result = context.pop();
            assertThat(result)
                    .as("LocalTime.of(hour, minute, second) with constants should fold to Constant")
                    .isInstanceOf(LambdaExpression.Constant.class);
            var constant = (LambdaExpression.Constant) result;
            assertThat(constant.value()).isEqualTo(java.time.LocalTime.of(14, 30, 45));
        }

        @Test
        void localDateTimeOf_fiveArgs_allConstants_foldsToConstant() {
            context.push(constant(2024));
            context.push(constant(6));
            context.push(constant(15));
            context.push(constant(14));
            context.push(constant(30));

            var insn = createMethodInsn(INVOKESTATIC, "java/time/LocalDateTime", "of", "(IIIII)Ljava/time/LocalDateTime;");
            handler.handle(insn, context);

            assertThat(context.getStackSize()).isEqualTo(1);
            var result = context.pop();
            assertThat(result)
                    .as("LocalDateTime.of with 5 constant args should fold")
                    .isInstanceOf(LambdaExpression.Constant.class);
        }

        @Test
        void localDateTimeOf_sixArgs_allConstants_foldsToConstant() {
            context.push(constant(2024));
            context.push(constant(6));
            context.push(constant(15));
            context.push(constant(14));
            context.push(constant(30));
            context.push(constant(45));

            var insn = createMethodInsn(INVOKESTATIC, "java/time/LocalDateTime", "of", "(IIIIII)Ljava/time/LocalDateTime;");
            handler.handle(insn, context);

            assertThat(context.getStackSize()).isEqualTo(1);
            var result = context.pop();
            assertThat(result)
                    .as("LocalDateTime.of(year, month, day, hour, minute, second) with constants should fold to Constant")
                    .isInstanceOf(LambdaExpression.Constant.class);
            var constant = (LambdaExpression.Constant) result;
            assertThat(constant.value()).isEqualTo(java.time.LocalDateTime.of(2024, 6, 15, 14, 30, 45));
        }

        @Test
        void unknownStaticMethod_notHandled() {
            context.push(constant("test"));

            var insn = createMethodInsn(INVOKESTATIC, "com/example/Utils", "process", "(Ljava/lang/String;)V");
            handler.handle(insn, context);

            // Unknown static method should leave stack unchanged
            assertThat(context.getStackSize()).isEqualTo(1);
        }
    }

    // ==================== Mutation Killing Tests: VirtualMethodCategory.categorize branches ====================

    /**
     * Tests targeting switch case mutations in VirtualMethodCategory.categorize().
     * Verify that each category is correctly identified and no cross-contamination occurs.
     */
    @Nested
    @DisplayName("Mutation killing: VirtualMethodCategory switch branches")
    class VirtualMethodCategorySwitchTests {

        @Test
        void categorize_equalsMethod_returnsEqualsNotOther() {
            var insn = createMethodInsn(INVOKEVIRTUAL, "java/lang/Object", "equals", "(Ljava/lang/Object;)Z");

            var category = VirtualMethodCategory.categorize(insn, handler);

            assertThat(category)
                    .as("equals method must categorize as EQUALS, not STRING_METHOD or GETTER")
                    .isEqualTo(VirtualMethodCategory.EQUALS)
                    .isNotEqualTo(VirtualMethodCategory.STRING_METHOD)
                    .isNotEqualTo(VirtualMethodCategory.GETTER)
                    .isNotEqualTo(VirtualMethodCategory.COMPARE_TO);
        }

        @Test
        void categorize_compareToMethod_returnsCompareToNotOther() {
            var insn = createMethodInsn(INVOKEVIRTUAL, "java/lang/Integer", "compareTo", "(Ljava/lang/Integer;)I");

            var category = VirtualMethodCategory.categorize(insn, handler);

            assertThat(category)
                    .as("compareTo method must categorize as COMPARE_TO, not EQUALS")
                    .isEqualTo(VirtualMethodCategory.COMPARE_TO)
                    .isNotEqualTo(VirtualMethodCategory.EQUALS)
                    .isNotEqualTo(VirtualMethodCategory.BIG_DECIMAL_ARITHMETIC);
        }

        @Test
        void categorize_bigDecimalAdd_returnsBigDecimalArithmeticNotOther() {
            var insn = createMethodInsn(INVOKEVIRTUAL, "java/math/BigDecimal", "add",
                    "(Ljava/math/BigDecimal;)Ljava/math/BigDecimal;");

            var category = VirtualMethodCategory.categorize(insn, handler);

            assertThat(category)
                    .as("BigDecimal.add must categorize as BIG_DECIMAL_ARITHMETIC")
                    .isEqualTo(VirtualMethodCategory.BIG_DECIMAL_ARITHMETIC)
                    .isNotEqualTo(VirtualMethodCategory.COMPARE_TO)
                    .isNotEqualTo(VirtualMethodCategory.STRING_METHOD);
        }

        @Test
        void categorize_localDateMethod_returnsTemporalMethodNotOther() {
            var insn = createMethodInsn(INVOKEVIRTUAL, "java/time/LocalDate", "getYear", "()I");

            var category = VirtualMethodCategory.categorize(insn, handler);

            assertThat(category)
                    .as("LocalDate.getYear must categorize as TEMPORAL_METHOD")
                    .isEqualTo(VirtualMethodCategory.TEMPORAL_METHOD)
                    .isNotEqualTo(VirtualMethodCategory.GETTER)
                    .isNotEqualTo(VirtualMethodCategory.STRING_METHOD);
        }

        @Test
        void categorize_getterMethod_returnsGetterNotOther() {
            var insn = createMethodInsn(INVOKEVIRTUAL, "com/example/Entity", "getName", "()Ljava/lang/String;");

            var category = VirtualMethodCategory.categorize(insn, handler);

            assertThat(category)
                    .as("getName() must categorize as GETTER")
                    .isEqualTo(VirtualMethodCategory.GETTER)
                    .isNotEqualTo(VirtualMethodCategory.STRING_METHOD)
                    .isNotEqualTo(VirtualMethodCategory.TEMPORAL_METHOD);
        }

        @Test
        void categorize_isBooleanGetter_returnsGetterNotOther() {
            var insn = createMethodInsn(INVOKEVIRTUAL, "com/example/Entity", "isActive", "()Z");

            var category = VirtualMethodCategory.categorize(insn, handler);

            assertThat(category)
                    .as("isActive() must categorize as GETTER")
                    .isEqualTo(VirtualMethodCategory.GETTER)
                    .isNotEqualTo(VirtualMethodCategory.UNHANDLED);
        }

        @Test
        void categorize_stringLength_returnsStringMethodNotOther() {
            var insn = createMethodInsn(INVOKEVIRTUAL, "java/lang/String", "length", "()I");

            var category = VirtualMethodCategory.categorize(insn, handler);

            assertThat(category)
                    .as("String.length() must categorize as STRING_METHOD")
                    .isEqualTo(VirtualMethodCategory.STRING_METHOD)
                    .isNotEqualTo(VirtualMethodCategory.GETTER)
                    .isNotEqualTo(VirtualMethodCategory.TEMPORAL_METHOD);
        }

        @Test
        void categorize_unknownMethod_returnsUnhandledNotOther() {
            var insn = createMethodInsn(INVOKEVIRTUAL, "com/example/Custom", "doSomething", "(I)V");

            var category = VirtualMethodCategory.categorize(insn, handler);

            assertThat(category)
                    .as("Unknown method must categorize as UNHANDLED")
                    .isEqualTo(VirtualMethodCategory.UNHANDLED)
                    .isNotEqualTo(VirtualMethodCategory.GETTER)
                    .isNotEqualTo(VirtualMethodCategory.STRING_METHOD);
        }
    }

    // ==================== Helper Methods ====================

    private MethodInsnNode createMethodInsn(int opcode, String owner, String name, String desc) {
        return new MethodInsnNode(opcode, owner, name, desc, opcode == INVOKEINTERFACE);
    }
}
