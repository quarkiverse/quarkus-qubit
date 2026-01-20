package io.quarkiverse.qubit.deployment.analysis.instruction;

import io.quarkiverse.qubit.deployment.ast.LambdaExpression;
import io.quarkiverse.qubit.deployment.ast.LambdaExpression.ExistsSubquery;
import io.quarkiverse.qubit.deployment.ast.LambdaExpression.InSubquery;
import io.quarkiverse.qubit.deployment.ast.LambdaExpression.ScalarSubquery;
import io.quarkiverse.qubit.deployment.ast.LambdaExpression.SubqueryAggregationType;
import io.quarkiverse.qubit.deployment.ast.LambdaExpression.SubqueryBuilderReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import static io.quarkiverse.qubit.deployment.testutil.AstBuilders.*;
import static io.quarkiverse.qubit.runtime.internal.QubitConstants.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.objectweb.asm.Opcodes.*;

/**
 * Tests for {@link SubqueryAnalyzer}.
 *
 * <p>Tests subquery-related bytecode instruction handling includings
 * <ul>
 *   <li>Subqueries.subquery() factory method</li>
 *   <li>SubqueryBuilder.* methods (avg, sum, min, max, count, exists, in)</li>
 *   <li>Error paths for unexpected methods, empty stacks, wrong types</li>
 * </ul>
 */
@DisplayName("SubqueryAnalyzer Tests")
class SubqueryAnalyzerTest {

    private SubqueryAnalyzer analyzer;
    private MethodNode testMethod;
    private AnalysisContext context;

    @BeforeEach
    void setUp() {
        analyzer = new SubqueryAnalyzer();
        testMethod = new MethodNode();
        testMethod.name = "testPredicate";
        testMethod.desc = "(Lio/quarkiverse/qubit/test/Person;)Z";
        testMethod.instructions = new InsnList();
        context = new AnalysisContext(testMethod, 0);
    }

    // ==================== isSubqueriesMethodCall Tests ====================

    @Nested
    @DisplayName("isSubqueriesMethodCall detection")
    class IsSubqueriesMethodCallTests {

        @Test
        @DisplayName("Detects Subqueries.subquery() factory method")
        void isSubqueriesMethodCall_withSubqueriesClass_returnsTrue() {
            MethodInsnNode methodInsn = new MethodInsnNode(INVOKESTATIC,
                    SUBQUERIES_INTERNAL_NAME, METHOD_SUBQUERY,
                    "(Ljava/lang/Class;)Lio/quarkiverse/qubit/SubqueryBuilder;", false);

            assertThat(analyzer.isSubqueriesMethodCall(methodInsn))
                    .as("Should recognize Subqueries factory method call")
                    .isTrue();
        }

        @Test
        @DisplayName("Does not match non-Subqueries class")
        void isSubqueriesMethodCall_withOtherClass_returnsFalse() {
            MethodInsnNode methodInsn = new MethodInsnNode(INVOKESTATIC,
                    "java/util/Collections", "emptyList",
                    "()Ljava/util/List;", false);

            assertThat(analyzer.isSubqueriesMethodCall(methodInsn))
                    .as("Should not recognize non-Subqueries method")
                    .isFalse();
        }
    }

    // ==================== isSubqueryBuilderMethodCall Tests ====================

    @Nested
    @DisplayName("isSubqueryBuilderMethodCall detection")
    class IsSubqueryBuilderMethodCallTests {

        @Test
        @DisplayName("Detects SubqueryBuilder method calls")
        void isSubqueryBuilderMethodCall_withSubqueryBuilderClass_returnsTrue() {
            MethodInsnNode methodInsn = new MethodInsnNode(INVOKEVIRTUAL,
                    SUBQUERY_BUILDER_INTERNAL_NAME, SUBQUERY_AVG,
                    "(Ljava/util/function/Function;)D", false);

            assertThat(analyzer.isSubqueryBuilderMethodCall(methodInsn))
                    .as("Should recognize SubqueryBuilder method call")
                    .isTrue();
        }

        @Test
        @DisplayName("Does not match non-SubqueryBuilder class")
        void isSubqueryBuilderMethodCall_withOtherClass_returnsFalse() {
            MethodInsnNode methodInsn = new MethodInsnNode(INVOKEVIRTUAL,
                    "java/lang/StringBuilder", "append",
                    "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false);

            assertThat(analyzer.isSubqueryBuilderMethodCall(methodInsn))
                    .as("Should not recognize non-SubqueryBuilder method")
                    .isFalse();
        }
    }

    // ==================== handleSubqueriesFactoryMethod Tests ====================

    @Nested
    @DisplayName("handleSubqueriesFactoryMethod")
    class HandleSubqueriesFactoryMethodTests {

        @Test
        @DisplayName("Creates SubqueryBuilderReference from entity class constant")
        void handleSubqueriesFactoryMethod_withClassConstant_createsBuilderReference() {
            // Setup: push Person.class constant onto stack
            context.push(constant(Person.class));
            MethodInsnNode methodInsn = createSubqueriesMethodInsn(METHOD_SUBQUERY);

            analyzer.handleSubqueriesFactoryMethod(context, methodInsn);

            assertThat(context.getStackSize()).isEqualTo(1);
            assertThat(context.peek())
                    .as("Should create SubqueryBuilderReference")
                    .isInstanceOf(SubqueryBuilderReference.class);
            SubqueryBuilderReference ref = (SubqueryBuilderReference) context.peek();
            assertThat(ref.entityClass())
                    .as("Should have correct entity class")
                    .isEqualTo(Person.class);
        }

        @Test
        @DisplayName("Unexpected Subqueries method logs warning and returns")
        void handleSubqueriesFactoryMethod_unexpectedMethod_doesNothing() {
            context.push(constant(Person.class));
            // Use an unexpected method name
            MethodInsnNode methodInsn = new MethodInsnNode(INVOKESTATIC,
                    SUBQUERIES_INTERNAL_NAME, "unknownMethod",
                    "(Ljava/lang/Class;)Lio/quarkiverse/qubit/SubqueryBuilder;", false);

            analyzer.handleSubqueriesFactoryMethod(context, methodInsn);

            // Stack should be unchanged (method returned early)
            assertThat(context.getStackSize())
                    .as("Stack should be unchanged for unexpected method")
                    .isEqualTo(1);
            assertThat(context.peek())
                    .as("Original constant should remain on stack")
                    .isInstanceOf(LambdaExpression.Constant.class);
        }
    }

    // ==================== handleSubqueryBuilderMethod Error Path Tests ====================

    @Nested
    @DisplayName("handleSubqueryBuilderMethod error paths")
    class HandleSubqueryBuilderMethodErrorPathTests {

        @Test
        @DisplayName("Empty stack when expecting SubqueryBuilderReference logs warning")
        void handleSubqueryBuilderMethod_emptyStack_logsWarning() {
            // No setup - stack is empty
            MethodInsnNode methodInsn = createSubqueryBuilderMethodInsn(SUBQUERY_COUNT, "()J");

            analyzer.handleSubqueryBuilderMethod(context, methodInsn);

            assertThat(context.isStackEmpty())
                    .as("Stack should remain empty after error")
                    .isTrue();
        }

        @Test
        @DisplayName("Wrong stack type instead of SubqueryBuilderReference logs warning")
        void handleSubqueryBuilderMethod_wrongStackType_restoresStack() {
            // Push something other than SubqueryBuilderReference
            context.push(field("name", String.class));
            MethodInsnNode methodInsn = createSubqueryBuilderMethodInsn(SUBQUERY_COUNT, "()J");

            analyzer.handleSubqueryBuilderMethod(context, methodInsn);

            // Stack should be restored (builderRef pushed back)
            assertThat(context.getStackSize())
                    .as("Stack should have the original element restored")
                    .isEqualTo(1);
            assertThat(context.peek())
                    .as("Original field access should be restored on stack")
                    .isInstanceOf(LambdaExpression.FieldAccess.class);
        }

        @Test
        @DisplayName("SubqueryBuilder.where with wrong arg count logs warning")
        void handleSubqueryBuilderMethod_whereWrongArgCount_doesNothing() {
            // Setup: SubqueryBuilderReference but no argument for where()
            context.push(new SubqueryBuilderReference(Person.class, "Person"));
            // Descriptor says 1 arg but we don't push any
            MethodInsnNode methodInsn = createSubqueryBuilderMethodInsn(METHOD_WHERE,
                    "(Ljava/util/function/Predicate;)" + SUBQUERY_BUILDER_DESCRIPTOR);

            analyzer.handleSubqueryBuilderMethod(context, methodInsn);

            // Method should have returned early, stack should be empty
            assertThat(context.isStackEmpty())
                    .as("where() with wrong arg count should leave stack empty")
                    .isTrue();
        }

        @Test
        @DisplayName("SubqueryBuilder.avg with wrong arg count logs warning")
        void handleSubqueryBuilderMethod_avgWrongArgCount_doesNothing() {
            // Setup: SubqueryBuilderReference but no argument for avg()
            context.push(new SubqueryBuilderReference(Person.class, "Person"));
            // Descriptor says 1 arg but we don't push any
            MethodInsnNode methodInsn = createSubqueryBuilderMethodInsn(SUBQUERY_AVG,
                    "(Ljava/util/function/Function;)D");

            analyzer.handleSubqueryBuilderMethod(context, methodInsn);

            // Method should have returned early, stack should be empty
            assertThat(context.isStackEmpty())
                    .as("avg() with wrong arg count should leave stack empty")
                    .isTrue();
        }

        @Test
        @DisplayName("SubqueryBuilder.exists with wrong arg count logs warning")
        void handleSubqueryBuilderMethod_existsWrongArgCount_doesNothing() {
            // Setup: SubqueryBuilderReference but no argument for exists()
            context.push(new SubqueryBuilderReference(Person.class, "Person"));
            // Descriptor says 1 arg but we don't push any
            MethodInsnNode methodInsn = createSubqueryBuilderMethodInsn(SUBQUERY_EXISTS,
                    "(Ljava/util/function/Predicate;)Z");

            analyzer.handleSubqueryBuilderMethod(context, methodInsn);

            // Method should have returned early, stack should be empty
            assertThat(context.isStackEmpty())
                    .as("exists() with wrong arg count should leave stack empty")
                    .isTrue();
        }

        @Test
        @DisplayName("SubqueryBuilder.in with wrong arg count logs warning")
        void handleSubqueryBuilderMethod_inWrongArgCount_doesNothing() {
            // Setup: SubqueryBuilderReference but only 1 argument (needs 2-3)
            context.push(new SubqueryBuilderReference(Person.class, "Person"));
            context.push(field("name", String.class)); // Only 1 arg, needs 2
            // Descriptor says 2 args
            MethodInsnNode methodInsn = createSubqueryBuilderMethodInsn(SUBQUERY_IN,
                    "(Ljava/util/function/Function;Ljava/util/function/Function;)Z");

            analyzer.handleSubqueryBuilderMethod(context, methodInsn);

            // Method should have returned early, stack should be empty
            assertThat(context.isStackEmpty())
                    .as("in() with wrong arg count should leave stack empty")
                    .isTrue();
        }
    }

    // ==================== handleSubqueryBuilderMethod Success Path Tests ====================

    @Nested
    @DisplayName("handleSubqueryBuilderMethod success paths")
    class HandleSubqueryBuilderMethodSuccessTests {

        @Test
        @DisplayName("count() creates ScalarSubquery with COUNT aggregation")
        void handleSubqueryBuilderMethod_count_createsCountSubquery() {
            context.push(new SubqueryBuilderReference(Person.class, "Person"));
            MethodInsnNode methodInsn = createSubqueryBuilderMethodInsn(SUBQUERY_COUNT, "()J");

            analyzer.handleSubqueryBuilderMethod(context, methodInsn);

            assertThat(context.getStackSize()).isEqualTo(1);
            assertThat(context.peek())
                    .as("count() should create ScalarSubquery")
                    .isInstanceOf(ScalarSubquery.class);
            ScalarSubquery subquery = (ScalarSubquery) context.peek();
            assertThat(subquery.aggregationType())
                    .as("Should be COUNT aggregation")
                    .isEqualTo(SubqueryAggregationType.COUNT);
        }

        @Test
        @DisplayName("avg(field) creates ScalarSubquery with AVG aggregation")
        void handleSubqueryBuilderMethod_avg_createsAvgSubquery() {
            context.push(new SubqueryBuilderReference(Person.class, "Person"));
            context.push(field("salary", Double.class)); // selector argument
            MethodInsnNode methodInsn = createSubqueryBuilderMethodInsn(SUBQUERY_AVG,
                    "(Ljava/util/function/Function;)D");

            analyzer.handleSubqueryBuilderMethod(context, methodInsn);

            assertThat(context.getStackSize()).isEqualTo(1);
            assertThat(context.peek())
                    .as("avg() should create ScalarSubquery")
                    .isInstanceOf(ScalarSubquery.class);
            ScalarSubquery subquery = (ScalarSubquery) context.peek();
            assertThat(subquery.aggregationType())
                    .as("Should be AVG aggregation")
                    .isEqualTo(SubqueryAggregationType.AVG);
        }

        @Test
        @DisplayName("exists(predicate) creates ExistsSubquery")
        void handleSubqueryBuilderMethod_exists_createsExistsSubquery() {
            context.push(new SubqueryBuilderReference(Person.class, "Person"));
            context.push(eq(field("age", Integer.class), constant(30))); // predicate argument
            MethodInsnNode methodInsn = createSubqueryBuilderMethodInsn(SUBQUERY_EXISTS,
                    "(Ljava/util/function/Predicate;)Z");

            analyzer.handleSubqueryBuilderMethod(context, methodInsn);

            assertThat(context.getStackSize()).isEqualTo(1);
            assertThat(context.peek())
                    .as("exists() should create ExistsSubquery")
                    .isInstanceOf(ExistsSubquery.class);
            ExistsSubquery subquery = (ExistsSubquery) context.peek();
            assertThat(subquery.negated())
                    .as("exists() should not be negated")
                    .isFalse();
        }

        @Test
        @DisplayName("notExists(predicate) creates negated ExistsSubquery")
        void handleSubqueryBuilderMethod_notExists_createsNegatedExistsSubquery() {
            context.push(new SubqueryBuilderReference(Person.class, "Person"));
            context.push(eq(field("age", Integer.class), constant(30))); // predicate argument
            MethodInsnNode methodInsn = createSubqueryBuilderMethodInsn(SUBQUERY_NOT_EXISTS,
                    "(Ljava/util/function/Predicate;)Z");

            analyzer.handleSubqueryBuilderMethod(context, methodInsn);

            assertThat(context.getStackSize()).isEqualTo(1);
            assertThat(context.peek())
                    .as("notExists() should create ExistsSubquery")
                    .isInstanceOf(ExistsSubquery.class);
            ExistsSubquery subquery = (ExistsSubquery) context.peek();
            assertThat(subquery.negated())
                    .as("notExists() should be negated")
                    .isTrue();
        }

        @Test
        @DisplayName("in(field, selector) creates InSubquery")
        void handleSubqueryBuilderMethod_in_createsInSubquery() {
            context.push(new SubqueryBuilderReference(Person.class, "Person"));
            context.push(field("departmentId", Long.class)); // field argument
            context.push(field("id", Long.class)); // selector argument
            MethodInsnNode methodInsn = createSubqueryBuilderMethodInsn(SUBQUERY_IN,
                    "(Ljava/util/function/Function;Ljava/util/function/Function;)Z");

            analyzer.handleSubqueryBuilderMethod(context, methodInsn);

            assertThat(context.getStackSize()).isEqualTo(1);
            assertThat(context.peek())
                    .as("in() should create InSubquery")
                    .isInstanceOf(InSubquery.class);
            InSubquery subquery = (InSubquery) context.peek();
            assertThat(subquery.negated())
                    .as("in() should not be negated")
                    .isFalse();
        }

        @Test
        @DisplayName("notIn(field, selector) creates negated InSubquery")
        void handleSubqueryBuilderMethod_notIn_createsNegatedInSubquery() {
            context.push(new SubqueryBuilderReference(Person.class, "Person"));
            context.push(field("departmentId", Long.class)); // field argument
            context.push(field("id", Long.class)); // selector argument
            MethodInsnNode methodInsn = createSubqueryBuilderMethodInsn(SUBQUERY_NOT_IN,
                    "(Ljava/util/function/Function;Ljava/util/function/Function;)Z");

            analyzer.handleSubqueryBuilderMethod(context, methodInsn);

            assertThat(context.getStackSize()).isEqualTo(1);
            assertThat(context.peek())
                    .as("notIn() should create InSubquery")
                    .isInstanceOf(InSubquery.class);
            InSubquery subquery = (InSubquery) context.peek();
            assertThat(subquery.negated())
                    .as("notIn() should be negated")
                    .isTrue();
        }

        @Test
        @DisplayName("where(predicate) updates SubqueryBuilderReference with predicate")
        void handleSubqueryBuilderMethod_where_updatesBuilderWithPredicate() {
            SubqueryBuilderReference originalBuilder = new SubqueryBuilderReference(Person.class, "Person");
            context.push(originalBuilder);
            LambdaExpression predicate = eq(field("active", Boolean.class), constant(true));
            context.push(predicate);
            MethodInsnNode methodInsn = createSubqueryBuilderMethodInsn(METHOD_WHERE,
                    "(Ljava/util/function/Predicate;)" + SUBQUERY_BUILDER_DESCRIPTOR);

            analyzer.handleSubqueryBuilderMethod(context, methodInsn);

            assertThat(context.getStackSize()).isEqualTo(1);
            assertThat(context.peek())
                    .as("where() should push updated SubqueryBuilderReference")
                    .isInstanceOf(SubqueryBuilderReference.class);
            SubqueryBuilderReference updatedBuilder = (SubqueryBuilderReference) context.peek();
            assertThat(updatedBuilder.predicate())
                    .as("Updated builder should have predicate")
                    .isNotNull();
        }
    }

    // ==================== Unknown Method Tests ====================

    @Nested
    @DisplayName("Unknown method handling")
    class UnknownMethodTests {

        @Test
        @DisplayName("Unknown SubqueryBuilder method is logged and ignored")
        void handleSubqueryBuilderMethod_unknownMethod_logsDebug() {
            context.push(new SubqueryBuilderReference(Person.class, "Person"));
            MethodInsnNode methodInsn = createSubqueryBuilderMethodInsn("unknownMethod", "()V");

            analyzer.handleSubqueryBuilderMethod(context, methodInsn);

            // Unknown method is handled in default case - stack cleared by arg popping
            assertThat(context.isStackEmpty())
                    .as("Unknown method should result in empty stack")
                    .isTrue();
        }
    }

    // ==================== Helper Methods ====================

    private MethodInsnNode createSubqueriesMethodInsn(String methodName) {
        return new MethodInsnNode(INVOKESTATIC,
                SUBQUERIES_INTERNAL_NAME, methodName,
                "(Ljava/lang/Class;)Lio/quarkiverse/qubit/SubqueryBuilder;", false);
    }

    private MethodInsnNode createSubqueryBuilderMethodInsn(String methodName, String descriptor) {
        return new MethodInsnNode(INVOKEVIRTUAL,
                SUBQUERY_BUILDER_INTERNAL_NAME, methodName, descriptor, false);
    }

    // Test entity class
    static class Person {
        String name;
        Integer age;
        Double salary;
        Long departmentId;
        Boolean active;
    }
}
