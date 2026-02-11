package io.quarkiverse.qubit.deployment.analysis.instruction;

import static io.quarkiverse.qubit.deployment.testutil.AstBuilders.*;
import static io.quarkiverse.qubit.runtime.internal.QubitConstants.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.objectweb.asm.Opcodes.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import io.quarkiverse.qubit.deployment.ast.LambdaExpression;
import io.quarkiverse.qubit.deployment.ast.LambdaExpression.ExistsSubquery;
import io.quarkiverse.qubit.deployment.ast.LambdaExpression.InSubquery;
import io.quarkiverse.qubit.deployment.ast.LambdaExpression.ScalarSubquery;
import io.quarkiverse.qubit.deployment.ast.LambdaExpression.SubqueryAggregationType;
import io.quarkiverse.qubit.deployment.ast.LambdaExpression.SubqueryBuilderReference;

/**
 * Tests for {@link SubqueryAnalyzer}.
 *
 * <p>
 * Tests subquery-related bytecode instruction handling includings
 * <ul>
 * <li>Subqueries.subquery() factory method</li>
 * <li>SubqueryBuilder.* methods (avg, sum, min, max, count, exists, in)</li>
 * <li>Error paths for unexpected methods, empty stacks, wrong types</li>
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

        @Test
        @DisplayName("avg(field) returns Double type specifically (not inferred)")
        void handleSubqueryBuilderMethod_avg_returnsDoubleType() {
            context.push(new SubqueryBuilderReference(Person.class, "Person"));
            // Push an Integer field - AVG should still return Double
            context.push(field("age", Integer.class));
            MethodInsnNode methodInsn = createSubqueryBuilderMethodInsn(SUBQUERY_AVG,
                    "(Ljava/util/function/Function;)D");

            analyzer.handleSubqueryBuilderMethod(context, methodInsn);

            ScalarSubquery subquery = (ScalarSubquery) context.peek();
            assertThat(subquery.resultType())
                    .as("AVG always returns Double regardless of selector type")
                    .isEqualTo(Double.class);
        }

        @Test
        @DisplayName("sum(field) creates SUM aggregation subquery")
        void handleSubqueryBuilderMethod_sum_createsSumSubquery() {
            context.push(new SubqueryBuilderReference(Person.class, "Person"));
            context.push(field("salary", Double.class));
            MethodInsnNode methodInsn = createSubqueryBuilderMethodInsn(SUBQUERY_SUM,
                    "(Ljava/util/function/Function;)Ljava/lang/Number;");

            analyzer.handleSubqueryBuilderMethod(context, methodInsn);

            ScalarSubquery subquery = (ScalarSubquery) context.peek();
            assertThat(subquery.aggregationType())
                    .as("Should be SUM aggregation")
                    .isEqualTo(SubqueryAggregationType.SUM);
        }

        @Test
        @DisplayName("min(field) creates MIN aggregation subquery")
        void handleSubqueryBuilderMethod_min_createsMinSubquery() {
            context.push(new SubqueryBuilderReference(Person.class, "Person"));
            context.push(field("age", Integer.class));
            MethodInsnNode methodInsn = createSubqueryBuilderMethodInsn(SUBQUERY_MIN,
                    "(Ljava/util/function/Function;)Ljava/lang/Comparable;");

            analyzer.handleSubqueryBuilderMethod(context, methodInsn);

            assertThat(context.peek()).isInstanceOf(ScalarSubquery.class);
            ScalarSubquery subquery = (ScalarSubquery) context.peek();
            assertThat(subquery.aggregationType())
                    .as("Should be MIN aggregation")
                    .isEqualTo(SubqueryAggregationType.MIN);
        }

        @Test
        @DisplayName("max(field) creates MAX aggregation subquery")
        void handleSubqueryBuilderMethod_max_createsMaxSubquery() {
            context.push(new SubqueryBuilderReference(Person.class, "Person"));
            context.push(field("salary", Double.class));
            MethodInsnNode methodInsn = createSubqueryBuilderMethodInsn(SUBQUERY_MAX,
                    "(Ljava/util/function/Function;)Ljava/lang/Comparable;");

            analyzer.handleSubqueryBuilderMethod(context, methodInsn);

            assertThat(context.peek()).isInstanceOf(ScalarSubquery.class);
            ScalarSubquery subquery = (ScalarSubquery) context.peek();
            assertThat(subquery.aggregationType())
                    .as("Should be MAX aggregation")
                    .isEqualTo(SubqueryAggregationType.MAX);
        }

        @Test
        @DisplayName("count(predicate) creates COUNT subquery with predicate")
        void handleSubqueryBuilderMethod_countWithPredicate_combinesPredicates() {
            // Builder with existing predicate
            LambdaExpression builderPredicate = eq(field("active", Boolean.class), constant(true));
            SubqueryBuilderReference builder = new SubqueryBuilderReference(Person.class, "Person")
                    .withPredicate(builderPredicate);
            context.push(builder);
            // count() with additional predicate argument
            LambdaExpression argPredicate = gt(field("age", Integer.class), constant(18));
            context.push(argPredicate);
            MethodInsnNode methodInsn = createSubqueryBuilderMethodInsn(SUBQUERY_COUNT,
                    "(Ljava/util/function/Predicate;)J");

            analyzer.handleSubqueryBuilderMethod(context, methodInsn);

            assertThat(context.peek()).isInstanceOf(ScalarSubquery.class);
            ScalarSubquery subquery = (ScalarSubquery) context.peek();
            assertThat(subquery.aggregationType()).isEqualTo(SubqueryAggregationType.COUNT);
            assertThat(subquery.predicate())
                    .as("count(predicate) should combine builder and arg predicates")
                    .isNotNull();
        }

        @Test
        @DisplayName("in(field, selector, predicate) creates InSubquery with 3 args")
        void handleSubqueryBuilderMethod_inWithThreeArgs_createsInSubquery() {
            SubqueryBuilderReference builder = new SubqueryBuilderReference(Person.class, "Person");
            context.push(builder);
            context.push(field("departmentId", Long.class)); // field
            context.push(field("id", Long.class)); // selector
            context.push(eq(field("active", Boolean.class), constant(true))); // predicate
            // 3-arg descriptor
            MethodInsnNode methodInsn = createSubqueryBuilderMethodInsn(SUBQUERY_IN,
                    "(Ljava/util/function/Function;Ljava/util/function/Function;Ljava/util/function/Predicate;)Z");

            analyzer.handleSubqueryBuilderMethod(context, methodInsn);

            assertThat(context.peek()).isInstanceOf(InSubquery.class);
            InSubquery subquery = (InSubquery) context.peek();
            assertThat(subquery.negated()).isFalse();
            assertThat(subquery.predicate())
                    .as("in() with 3 args should have predicate")
                    .isNotNull();
        }

        @Test
        @DisplayName("notIn(field, selector, predicate) creates negated InSubquery with 3 args")
        void handleSubqueryBuilderMethod_notInWithThreeArgs_createsNegatedInSubquery() {
            SubqueryBuilderReference builder = new SubqueryBuilderReference(Person.class, "Person");
            context.push(builder);
            context.push(field("departmentId", Long.class)); // field
            context.push(field("id", Long.class)); // selector
            context.push(eq(field("active", Boolean.class), constant(true))); // predicate
            // 3-arg descriptor
            MethodInsnNode methodInsn = createSubqueryBuilderMethodInsn(SUBQUERY_NOT_IN,
                    "(Ljava/util/function/Function;Ljava/util/function/Function;Ljava/util/function/Predicate;)Z");

            analyzer.handleSubqueryBuilderMethod(context, methodInsn);

            assertThat(context.peek()).isInstanceOf(InSubquery.class);
            InSubquery subquery = (InSubquery) context.peek();
            assertThat(subquery.negated()).isTrue();
            assertThat(subquery.predicate())
                    .as("notIn() with 3 args should have predicate")
                    .isNotNull();
        }

        @Test
        @DisplayName("in() with builder predicate and arg predicate combines them")
        void handleSubqueryBuilderMethod_inCombinesPredicates() {
            LambdaExpression builderPredicate = eq(field("status", String.class), constant("ACTIVE"));
            SubqueryBuilderReference builder = new SubqueryBuilderReference(Person.class, "Person")
                    .withPredicate(builderPredicate);
            context.push(builder);
            context.push(field("departmentId", Long.class)); // field
            context.push(field("id", Long.class)); // selector
            context.push(gt(field("age", Integer.class), constant(21))); // arg predicate
            MethodInsnNode methodInsn = createSubqueryBuilderMethodInsn(SUBQUERY_IN,
                    "(Ljava/util/function/Function;Ljava/util/function/Function;Ljava/util/function/Predicate;)Z");

            analyzer.handleSubqueryBuilderMethod(context, methodInsn);

            InSubquery subquery = (InSubquery) context.peek();
            assertThat(subquery.predicate())
                    .as("in() should combine builder and arg predicates with AND")
                    .isInstanceOf(LambdaExpression.BinaryOp.class);
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

    // ==================== Mutation-Killing Tests ====================

    @Nested
    @DisplayName("Stack underflow during arg popping (kill line 56 mutation)")
    class StackUnderflowDuringArgPoppingTests {

        @Test
        @DisplayName("Arg popping stops when stack becomes empty - mutation: replaced !ctx.isStackEmpty() with true")
        void handleSubqueryBuilderMethod_stackBecomesEmptyDuringArgPopping_popsOnlyAvailableArgs() {
            // Setup: Push only 1 arg but descriptor says 2 args
            // This tests line 56: if (!ctx.isStackEmpty()) during arg loop
            context.push(new SubqueryBuilderReference(Person.class, "Person"));
            context.push(field("departmentId", Long.class)); // Only 1 arg
            // Descriptor says 2 args - second iteration should find empty stack
            MethodInsnNode methodInsn = createSubqueryBuilderMethodInsn(SUBQUERY_IN,
                    "(Ljava/util/function/Function;Ljava/util/function/Function;)Z");

            analyzer.handleSubqueryBuilderMethod(context, methodInsn);

            // Should have processed with partial args (arg count check will fail, but line 56 was exercised)
            assertThat(context.isStackEmpty())
                    .as("Stack should be empty after failed arg count check")
                    .isTrue();
        }

        @Test
        @DisplayName("Arg popping with insufficient stack causes builder to be consumed as arg")
        void handleSubqueryBuilderMethod_countWithMissingPredicateArg_consumesBuilderAsArg() {
            // count(Predicate) descriptor expects 1 arg but we don't push any
            // The loop will pop SubqueryBuilderReference as the "arg", then stack is empty for builder pop
            context.push(new SubqueryBuilderReference(Person.class, "Person"));
            // No predicate argument pushed
            MethodInsnNode methodInsn = createSubqueryBuilderMethodInsn(SUBQUERY_COUNT,
                    "(Ljava/util/function/Predicate;)J");

            analyzer.handleSubqueryBuilderMethod(context, methodInsn);

            // SubqueryBuilderReference was consumed as arg, then stack was empty -> early return
            assertThat(context.isStackEmpty())
                    .as("Stack should be empty after builder consumed as arg and early return")
                    .isTrue();
        }

        @Test
        @DisplayName("Pop check prevents null args - if mutation makes check always true, null arg would cause NPE downstream")
        void handleSubqueryBuilderMethod_popCheckPreventsNullArgs_scalarSubqueryWithMissingSelector() {
            // Setup: Push SubqueryBuilderReference but NO selector arg
            // Descriptor says 1 arg - with normal check, args list will have 0 elements
            // With mutation (check always true), pop() returns null, args list has 1 null element
            context.push(new SubqueryBuilderReference(Person.class, "Person"));
            // No selector argument pushed - this is the key difference
            MethodInsnNode methodInsn = createSubqueryBuilderMethodInsn(SUBQUERY_AVG,
                    "(Ljava/util/function/Function;)D");

            analyzer.handleSubqueryBuilderMethod(context, methodInsn);

            // With proper check: args.size() != 1 -> early return, nothing pushed
            // With mutation: args would have null, size check passes, but NPE when using null selector
            // We verify by checking the stack is empty (early return happened)
            assertThat(context.isStackEmpty())
                    .as("Stack should be empty - arg count check should fail and return early")
                    .isTrue();
        }

        @Test
        @DisplayName("Pop check with exact arg count - verifies selector is valid, not null")
        void handleSubqueryBuilderMethod_avgWithValidSelector_producesNonNullSelector() {
            // This test verifies that when args ARE provided, the selector is used correctly
            // If mutation caused null to be added, this test would fail when selector is accessed
            context.push(new SubqueryBuilderReference(Person.class, "Person"));
            context.push(field("salary", Double.class)); // Valid selector

            MethodInsnNode methodInsn = createSubqueryBuilderMethodInsn(SUBQUERY_AVG,
                    "(Ljava/util/function/Function;)D");

            analyzer.handleSubqueryBuilderMethod(context, methodInsn);

            assertThat(context.getStackSize()).isEqualTo(1);
            ScalarSubquery subquery = (ScalarSubquery) context.peek();
            // If mutation popped extra null, the fieldExpression would be wrong
            assertThat(subquery.fieldExpression())
                    .as("Field expression must not be null - mutation would cause null field expression")
                    .isNotNull()
                    .isInstanceOf(LambdaExpression.FieldAccess.class);
        }
    }

    @Nested
    @DisplayName("Wrong stack type with args restoration (kill line 74 NO_COVERAGE)")
    class WrongStackTypeWithArgsRestorationTests {

        @Test
        @DisplayName("Wrong type with args causes restoration - line 74: ctx.push(arg)")
        void handleSubqueryBuilderMethod_wrongTypeWithMultipleArgs_restoresAllArgs() {
            // Push wrong type as builder reference, then push multiple args
            context.push(field("name", String.class)); // Wrong type - not SubqueryBuilderReference
            context.push(field("departmentId", Long.class)); // arg 1
            context.push(field("id", Long.class)); // arg 2
            MethodInsnNode methodInsn = createSubqueryBuilderMethodInsn(SUBQUERY_IN,
                    "(Ljava/util/function/Function;Ljava/util/function/Function;)Z");

            analyzer.handleSubqueryBuilderMethod(context, methodInsn);

            // All 3 elements should be restored (builderRef + 2 args)
            assertThat(context.getStackSize())
                    .as("All elements should be restored after wrong type detection")
                    .isEqualTo(3);
            // Verify order: last pushed should be at top (arg 2)
            assertThat(context.pop())
                    .as("Top should be last arg pushed")
                    .isInstanceOf(LambdaExpression.FieldAccess.class);
            assertThat(context.pop())
                    .as("Second should be first arg pushed")
                    .isInstanceOf(LambdaExpression.FieldAccess.class);
            assertThat(context.pop())
                    .as("Bottom should be the wrong type (original builderRef)")
                    .isInstanceOf(LambdaExpression.FieldAccess.class);
        }

        @Test
        @DisplayName("Wrong type with single arg causes restoration")
        void handleSubqueryBuilderMethod_wrongTypeWithSingleArg_restoresArg() {
            context.push(constant(42)); // Wrong type - Constant instead of SubqueryBuilderReference
            context.push(field("salary", Double.class)); // single arg
            MethodInsnNode methodInsn = createSubqueryBuilderMethodInsn(SUBQUERY_AVG,
                    "(Ljava/util/function/Function;)D");

            analyzer.handleSubqueryBuilderMethod(context, methodInsn);

            assertThat(context.getStackSize())
                    .as("Both elements should be restored")
                    .isEqualTo(2);
        }

        @Test
        @DisplayName("Wrong type with three args causes restoration - for in() with predicate")
        void handleSubqueryBuilderMethod_wrongTypeWithThreeArgs_restoresAllThreeArgs() {
            context.push(eq(field("active", Boolean.class), constant(true))); // Wrong type
            context.push(field("departmentId", Long.class)); // arg 1
            context.push(field("id", Long.class)); // arg 2
            context.push(eq(field("status", String.class), constant("ACTIVE"))); // arg 3 (predicate)
            MethodInsnNode methodInsn = createSubqueryBuilderMethodInsn(SUBQUERY_IN,
                    "(Ljava/util/function/Function;Ljava/util/function/Function;Ljava/util/function/Predicate;)Z");

            analyzer.handleSubqueryBuilderMethod(context, methodInsn);

            assertThat(context.getStackSize())
                    .as("All 4 elements should be restored")
                    .isEqualTo(4);
        }
    }

    @Nested
    @DisplayName("Switch case mutations (kill line 84 equality mutations)")
    class SwitchCaseMutationTests {

        @Test
        @DisplayName("AVG uses Double.class result type - mutation: if wrong case matches, result type would differ")
        void handleSubqueryBuilderMethod_avg_specificallyUsesDoubleResultType() {
            context.push(new SubqueryBuilderReference(Person.class, "Person"));
            context.push(field("age", Integer.class)); // Integer field
            MethodInsnNode methodInsn = createSubqueryBuilderMethodInsn(SUBQUERY_AVG,
                    "(Ljava/util/function/Function;)D");

            analyzer.handleSubqueryBuilderMethod(context, methodInsn);

            assertThat(context.getStackSize())
                    .as("AVG switch case must push a result to the stack")
                    .isEqualTo(1);
            assertThat(context.peek())
                    .as("AVG must push a non-null ScalarSubquery")
                    .isNotNull()
                    .isInstanceOf(ScalarSubquery.class);
            ScalarSubquery subquery = (ScalarSubquery) context.peek();
            assertThat(subquery.aggregationType())
                    .as("Must be AVG aggregation, not any other")
                    .isEqualTo(SubqueryAggregationType.AVG);
            assertThat(subquery.resultType())
                    .as("AVG must return Double.class (hardcoded), not inferred Integer")
                    .isEqualTo(Double.class);
        }

        @Test
        @DisplayName("SUM uses inferred result type, not Double - mutation: if AVG case matches, would use Double")
        void handleSubqueryBuilderMethod_sum_usesInferredResultType() {
            context.push(new SubqueryBuilderReference(Person.class, "Person"));
            context.push(field("salary", Double.class)); // Double field
            MethodInsnNode methodInsn = createSubqueryBuilderMethodInsn(SUBQUERY_SUM,
                    "(Ljava/util/function/Function;)Ljava/lang/Number;");

            analyzer.handleSubqueryBuilderMethod(context, methodInsn);

            assertThat(context.getStackSize())
                    .as("SUM switch case must push a result to the stack")
                    .isEqualTo(1);
            assertThat(context.peek())
                    .as("SUM must push a non-null ScalarSubquery")
                    .isNotNull()
                    .isInstanceOf(ScalarSubquery.class);
            ScalarSubquery subquery = (ScalarSubquery) context.peek();
            assertThat(subquery.aggregationType())
                    .as("Must be SUM aggregation")
                    .isEqualTo(SubqueryAggregationType.SUM);
            // SUM uses ExpressionTypeInferrer, which should infer Double from selector
            assertThat(subquery.resultType())
                    .as("SUM should infer type from selector")
                    .isEqualTo(Double.class);
        }

        @Test
        @DisplayName("MIN uses Comparable default type - mutation: wrong case would use different type")
        void handleSubqueryBuilderMethod_min_usesComparableDefault() {
            context.push(new SubqueryBuilderReference(Person.class, "Person"));
            context.push(field("age", Integer.class));
            MethodInsnNode methodInsn = createSubqueryBuilderMethodInsn(SUBQUERY_MIN,
                    "(Ljava/util/function/Function;)Ljava/lang/Comparable;");

            analyzer.handleSubqueryBuilderMethod(context, methodInsn);

            assertThat(context.getStackSize())
                    .as("MIN switch case must push a result to the stack")
                    .isEqualTo(1);
            assertThat(context.peek())
                    .as("MIN must push a non-null ScalarSubquery")
                    .isNotNull()
                    .isInstanceOf(ScalarSubquery.class);
            ScalarSubquery subquery = (ScalarSubquery) context.peek();
            assertThat(subquery.aggregationType())
                    .as("Must be MIN aggregation")
                    .isEqualTo(SubqueryAggregationType.MIN);
            // MIN infers from selector, should be Integer
            assertThat(subquery.resultType())
                    .as("MIN should infer Integer from Integer selector")
                    .isEqualTo(Integer.class);
        }

        @Test
        @DisplayName("MAX uses Comparable default type - mutation: wrong case would use different type")
        void handleSubqueryBuilderMethod_max_usesComparableDefault() {
            context.push(new SubqueryBuilderReference(Person.class, "Person"));
            context.push(field("salary", Double.class));
            MethodInsnNode methodInsn = createSubqueryBuilderMethodInsn(SUBQUERY_MAX,
                    "(Ljava/util/function/Function;)Ljava/lang/Comparable;");

            analyzer.handleSubqueryBuilderMethod(context, methodInsn);

            assertThat(context.getStackSize())
                    .as("MAX switch case must push a result to the stack")
                    .isEqualTo(1);
            assertThat(context.peek())
                    .as("MAX must push a non-null ScalarSubquery")
                    .isNotNull()
                    .isInstanceOf(ScalarSubquery.class);
            ScalarSubquery subquery = (ScalarSubquery) context.peek();
            assertThat(subquery.aggregationType())
                    .as("Must be MAX aggregation")
                    .isEqualTo(SubqueryAggregationType.MAX);
        }

        @Test
        @DisplayName("COUNT returns Long.class result type")
        void handleSubqueryBuilderMethod_count_returnsLongResultType() {
            context.push(new SubqueryBuilderReference(Person.class, "Person"));
            MethodInsnNode methodInsn = createSubqueryBuilderMethodInsn(SUBQUERY_COUNT, "()J");

            analyzer.handleSubqueryBuilderMethod(context, methodInsn);

            assertThat(context.getStackSize())
                    .as("COUNT switch case must push a result to the stack")
                    .isEqualTo(1);
            assertThat(context.peek())
                    .as("COUNT must push a non-null ScalarSubquery")
                    .isNotNull()
                    .isInstanceOf(ScalarSubquery.class);
            ScalarSubquery subquery = (ScalarSubquery) context.peek();
            assertThat(subquery.aggregationType())
                    .as("Must be COUNT aggregation")
                    .isEqualTo(SubqueryAggregationType.COUNT);
            assertThat(subquery.resultType())
                    .as("COUNT must return Long.class")
                    .isEqualTo(Long.class);
        }

        @Test
        @DisplayName("EXISTS creates ExistsSubquery, not other types")
        void handleSubqueryBuilderMethod_exists_createsExistsSubqueryType() {
            context.push(new SubqueryBuilderReference(Person.class, "Person"));
            context.push(eq(field("age", Integer.class), constant(30)));
            MethodInsnNode methodInsn = createSubqueryBuilderMethodInsn(SUBQUERY_EXISTS,
                    "(Ljava/util/function/Predicate;)Z");

            analyzer.handleSubqueryBuilderMethod(context, methodInsn);

            assertThat(context.getStackSize())
                    .as("EXISTS switch case must push a result to the stack")
                    .isEqualTo(1);
            assertThat(context.peek())
                    .as("EXISTS must create ExistsSubquery, not InSubquery or ScalarSubquery")
                    .isNotNull()
                    .isInstanceOf(ExistsSubquery.class);
            ExistsSubquery subquery = (ExistsSubquery) context.peek();
            assertThat(subquery.negated())
                    .as("EXISTS must not be negated")
                    .isFalse();
        }

        @Test
        @DisplayName("NOT_EXISTS creates negated ExistsSubquery")
        void handleSubqueryBuilderMethod_notExists_createsNegatedExistsSubquery() {
            context.push(new SubqueryBuilderReference(Person.class, "Person"));
            context.push(eq(field("age", Integer.class), constant(30)));
            MethodInsnNode methodInsn = createSubqueryBuilderMethodInsn(SUBQUERY_NOT_EXISTS,
                    "(Ljava/util/function/Predicate;)Z");

            analyzer.handleSubqueryBuilderMethod(context, methodInsn);

            assertThat(context.getStackSize())
                    .as("NOT_EXISTS switch case must push a result to the stack")
                    .isEqualTo(1);
            assertThat(context.peek())
                    .as("NOT_EXISTS must push a non-null ExistsSubquery")
                    .isNotNull()
                    .isInstanceOf(ExistsSubquery.class);
            ExistsSubquery subquery = (ExistsSubquery) context.peek();
            assertThat(subquery.negated())
                    .as("NOT_EXISTS must be negated")
                    .isTrue();
        }

        @Test
        @DisplayName("IN creates InSubquery, not ExistsSubquery")
        void handleSubqueryBuilderMethod_in_createsInSubqueryType() {
            context.push(new SubqueryBuilderReference(Person.class, "Person"));
            context.push(field("departmentId", Long.class));
            context.push(field("id", Long.class));
            MethodInsnNode methodInsn = createSubqueryBuilderMethodInsn(SUBQUERY_IN,
                    "(Ljava/util/function/Function;Ljava/util/function/Function;)Z");

            analyzer.handleSubqueryBuilderMethod(context, methodInsn);

            assertThat(context.getStackSize())
                    .as("IN switch case must push a result to the stack")
                    .isEqualTo(1);
            assertThat(context.peek())
                    .as("IN must create InSubquery, not ExistsSubquery")
                    .isNotNull()
                    .isInstanceOf(InSubquery.class);
            InSubquery subquery = (InSubquery) context.peek();
            assertThat(subquery.negated())
                    .as("IN must not be negated")
                    .isFalse();
        }

        @Test
        @DisplayName("NOT_IN creates negated InSubquery")
        void handleSubqueryBuilderMethod_notIn_createsNegatedInSubquery() {
            context.push(new SubqueryBuilderReference(Person.class, "Person"));
            context.push(field("departmentId", Long.class));
            context.push(field("id", Long.class));
            MethodInsnNode methodInsn = createSubqueryBuilderMethodInsn(SUBQUERY_NOT_IN,
                    "(Ljava/util/function/Function;Ljava/util/function/Function;)Z");

            analyzer.handleSubqueryBuilderMethod(context, methodInsn);

            assertThat(context.getStackSize())
                    .as("NOT_IN switch case must push a result to the stack")
                    .isEqualTo(1);
            assertThat(context.peek())
                    .as("NOT_IN must push a non-null InSubquery")
                    .isNotNull()
                    .isInstanceOf(InSubquery.class);
            InSubquery subquery = (InSubquery) context.peek();
            assertThat(subquery.negated())
                    .as("NOT_IN must be negated")
                    .isTrue();
        }

        @Test
        @DisplayName("WHERE creates updated SubqueryBuilderReference")
        void handleSubqueryBuilderMethod_where_createsUpdatedBuilderReference() {
            SubqueryBuilderReference original = new SubqueryBuilderReference(Person.class, "Person");
            context.push(original);
            LambdaExpression predicate = eq(field("active", Boolean.class), constant(true));
            context.push(predicate);
            MethodInsnNode methodInsn = createSubqueryBuilderMethodInsn(METHOD_WHERE,
                    "(Ljava/util/function/Predicate;)" + SUBQUERY_BUILDER_DESCRIPTOR);

            analyzer.handleSubqueryBuilderMethod(context, methodInsn);

            assertThat(context.getStackSize())
                    .as("WHERE switch case must push a result to the stack")
                    .isEqualTo(1);
            assertThat(context.peek())
                    .as("WHERE must create SubqueryBuilderReference, not subquery")
                    .isNotNull()
                    .isInstanceOf(SubqueryBuilderReference.class);
        }
    }

    @Nested
    @DisplayName("Arg count boundary mutations (kill lines 100, 113, 137, 149)")
    class ArgCountBoundaryMutationTests {

        // ==================== handleBuilderWhere line 100: args.size() != 1 ====================

        @Test
        @DisplayName("where() with 0 args returns early - mutation: replaced != 1 with false")
        void handleBuilderWhere_withZeroArgs_returnsEarly() {
            context.push(new SubqueryBuilderReference(Person.class, "Person"));
            // No args pushed
            MethodInsnNode methodInsn = createSubqueryBuilderMethodInsn(METHOD_WHERE,
                    "()" + SUBQUERY_BUILDER_DESCRIPTOR); // 0-arg descriptor

            analyzer.handleSubqueryBuilderMethod(context, methodInsn);

            assertThat(context.isStackEmpty())
                    .as("where() with 0 args should leave stack empty (returned early)")
                    .isTrue();
        }

        @Test
        @DisplayName("where() with 2 args returns early - mutation: replaced != 1 with false")
        void handleBuilderWhere_withTwoArgs_returnsEarly() {
            context.push(new SubqueryBuilderReference(Person.class, "Person"));
            context.push(eq(field("active", Boolean.class), constant(true)));
            context.push(gt(field("age", Integer.class), constant(18)));
            MethodInsnNode methodInsn = createSubqueryBuilderMethodInsn(METHOD_WHERE,
                    "(Ljava/util/function/Predicate;Ljava/util/function/Predicate;)" + SUBQUERY_BUILDER_DESCRIPTOR);

            analyzer.handleSubqueryBuilderMethod(context, methodInsn);

            assertThat(context.isStackEmpty())
                    .as("where() with 2 args should leave stack empty (returned early)")
                    .isTrue();
        }

        // ==================== handleBuilderScalarSubquery line 113: args.size() != 1 ====================

        @Test
        @DisplayName("avg() with 0 args returns early")
        void handleBuilderScalarSubquery_avgWithZeroArgs_returnsEarly() {
            context.push(new SubqueryBuilderReference(Person.class, "Person"));
            MethodInsnNode methodInsn = createSubqueryBuilderMethodInsn(SUBQUERY_AVG, "()D");

            analyzer.handleSubqueryBuilderMethod(context, methodInsn);

            assertThat(context.isStackEmpty())
                    .as("avg() with 0 args should leave stack empty")
                    .isTrue();
        }

        @Test
        @DisplayName("sum() with 2 args returns early")
        void handleBuilderScalarSubquery_sumWithTwoArgs_returnsEarly() {
            context.push(new SubqueryBuilderReference(Person.class, "Person"));
            context.push(field("salary", Double.class));
            context.push(field("bonus", Double.class));
            MethodInsnNode methodInsn = createSubqueryBuilderMethodInsn(SUBQUERY_SUM,
                    "(Ljava/util/function/Function;Ljava/util/function/Function;)Ljava/lang/Number;");

            analyzer.handleSubqueryBuilderMethod(context, methodInsn);

            assertThat(context.isStackEmpty())
                    .as("sum() with 2 args should leave stack empty")
                    .isTrue();
        }

        @Test
        @DisplayName("min() with 0 args returns early")
        void handleBuilderScalarSubquery_minWithZeroArgs_returnsEarly() {
            context.push(new SubqueryBuilderReference(Person.class, "Person"));
            MethodInsnNode methodInsn = createSubqueryBuilderMethodInsn(SUBQUERY_MIN, "()Ljava/lang/Comparable;");

            analyzer.handleSubqueryBuilderMethod(context, methodInsn);

            assertThat(context.isStackEmpty())
                    .as("min() with 0 args should leave stack empty")
                    .isTrue();
        }

        @Test
        @DisplayName("max() with 0 args returns early")
        void handleBuilderScalarSubquery_maxWithZeroArgs_returnsEarly() {
            context.push(new SubqueryBuilderReference(Person.class, "Person"));
            MethodInsnNode methodInsn = createSubqueryBuilderMethodInsn(SUBQUERY_MAX, "()Ljava/lang/Comparable;");

            analyzer.handleSubqueryBuilderMethod(context, methodInsn);

            assertThat(context.isStackEmpty())
                    .as("max() with 0 args should leave stack empty")
                    .isTrue();
        }

        // ==================== handleBuilderExistsSubquery line 137: args.size() != 1 ====================

        @Test
        @DisplayName("exists() with 0 args returns early")
        void handleBuilderExistsSubquery_withZeroArgs_returnsEarly() {
            context.push(new SubqueryBuilderReference(Person.class, "Person"));
            MethodInsnNode methodInsn = createSubqueryBuilderMethodInsn(SUBQUERY_EXISTS, "()Z");

            analyzer.handleSubqueryBuilderMethod(context, methodInsn);

            assertThat(context.isStackEmpty())
                    .as("exists() with 0 args should leave stack empty")
                    .isTrue();
        }

        @Test
        @DisplayName("exists() with 2 args returns early")
        void handleBuilderExistsSubquery_withTwoArgs_returnsEarly() {
            context.push(new SubqueryBuilderReference(Person.class, "Person"));
            context.push(eq(field("active", Boolean.class), constant(true)));
            context.push(gt(field("age", Integer.class), constant(18)));
            MethodInsnNode methodInsn = createSubqueryBuilderMethodInsn(SUBQUERY_EXISTS,
                    "(Ljava/util/function/Predicate;Ljava/util/function/Predicate;)Z");

            analyzer.handleSubqueryBuilderMethod(context, methodInsn);

            assertThat(context.isStackEmpty())
                    .as("exists() with 2 args should leave stack empty")
                    .isTrue();
        }

        @Test
        @DisplayName("notExists() with 0 args returns early")
        void handleBuilderExistsSubquery_notExistsWithZeroArgs_returnsEarly() {
            context.push(new SubqueryBuilderReference(Person.class, "Person"));
            MethodInsnNode methodInsn = createSubqueryBuilderMethodInsn(SUBQUERY_NOT_EXISTS, "()Z");

            analyzer.handleSubqueryBuilderMethod(context, methodInsn);

            assertThat(context.isStackEmpty())
                    .as("notExists() with 0 args should leave stack empty")
                    .isTrue();
        }

        // ==================== handleBuilderInSubquery line 149: args.size() < 2 || args.size() > 3 ====================

        @Test
        @DisplayName("in() with 0 args returns early - mutation: replaced < 2 with false")
        void handleBuilderInSubquery_withZeroArgs_returnsEarly() {
            context.push(new SubqueryBuilderReference(Person.class, "Person"));
            MethodInsnNode methodInsn = createSubqueryBuilderMethodInsn(SUBQUERY_IN, "()Z");

            analyzer.handleSubqueryBuilderMethod(context, methodInsn);

            assertThat(context.isStackEmpty())
                    .as("in() with 0 args should leave stack empty")
                    .isTrue();
        }

        @Test
        @DisplayName("in() with 1 arg returns early - mutation: replaced < 2 with false")
        void handleBuilderInSubquery_withOneArg_returnsEarly() {
            context.push(new SubqueryBuilderReference(Person.class, "Person"));
            context.push(field("departmentId", Long.class));
            MethodInsnNode methodInsn = createSubqueryBuilderMethodInsn(SUBQUERY_IN,
                    "(Ljava/util/function/Function;)Z");

            analyzer.handleSubqueryBuilderMethod(context, methodInsn);

            assertThat(context.isStackEmpty())
                    .as("in() with 1 arg should leave stack empty")
                    .isTrue();
        }

        @Test
        @DisplayName("in() with 4 args returns early - mutation: replaced > 3 with false")
        void handleBuilderInSubquery_withFourArgs_returnsEarly() {
            context.push(new SubqueryBuilderReference(Person.class, "Person"));
            context.push(field("departmentId", Long.class));
            context.push(field("id", Long.class));
            context.push(eq(field("active", Boolean.class), constant(true)));
            context.push(gt(field("age", Integer.class), constant(18))); // 4th arg - too many
            MethodInsnNode methodInsn = createSubqueryBuilderMethodInsn(SUBQUERY_IN,
                    "(Ljava/util/function/Function;Ljava/util/function/Function;Ljava/util/function/Predicate;Ljava/util/function/Predicate;)Z");

            analyzer.handleSubqueryBuilderMethod(context, methodInsn);

            assertThat(context.isStackEmpty())
                    .as("in() with 4 args should leave stack empty")
                    .isTrue();
        }

        @Test
        @DisplayName("notIn() with 1 arg returns early")
        void handleBuilderInSubquery_notInWithOneArg_returnsEarly() {
            context.push(new SubqueryBuilderReference(Person.class, "Person"));
            context.push(field("departmentId", Long.class));
            MethodInsnNode methodInsn = createSubqueryBuilderMethodInsn(SUBQUERY_NOT_IN,
                    "(Ljava/util/function/Function;)Z");

            analyzer.handleSubqueryBuilderMethod(context, methodInsn);

            assertThat(context.isStackEmpty())
                    .as("notIn() with 1 arg should leave stack empty")
                    .isTrue();
        }

        @Test
        @DisplayName("notIn() with 4 args returns early")
        void handleBuilderInSubquery_notInWithFourArgs_returnsEarly() {
            context.push(new SubqueryBuilderReference(Person.class, "Person"));
            context.push(field("departmentId", Long.class));
            context.push(field("id", Long.class));
            context.push(eq(field("active", Boolean.class), constant(true)));
            context.push(gt(field("age", Integer.class), constant(18)));
            MethodInsnNode methodInsn = createSubqueryBuilderMethodInsn(SUBQUERY_NOT_IN,
                    "(Ljava/util/function/Function;Ljava/util/function/Function;Ljava/util/function/Predicate;Ljava/util/function/Predicate;)Z");

            analyzer.handleSubqueryBuilderMethod(context, methodInsn);

            assertThat(context.isStackEmpty())
                    .as("notIn() with 4 args should leave stack empty")
                    .isTrue();
        }
    }

    @Nested
    @DisplayName("Aggregation type result inference (kill line 120 mutation)")
    class AggregationTypeResultInferenceMutationTests {

        @Test
        @DisplayName("SUM infers Integer type from Integer selector - mutation: if AVG case matches, would use Double")
        void handleBuilderScalarSubquery_sum_infersIntegerFromIntegerSelector() {
            context.push(new SubqueryBuilderReference(Person.class, "Person"));
            context.push(field("age", Integer.class)); // Integer selector
            MethodInsnNode methodInsn = createSubqueryBuilderMethodInsn(SUBQUERY_SUM,
                    "(Ljava/util/function/Function;)Ljava/lang/Number;");

            analyzer.handleSubqueryBuilderMethod(context, methodInsn);

            ScalarSubquery subquery = (ScalarSubquery) context.peek();
            assertThat(subquery.resultType())
                    .as("SUM with Integer selector should infer Integer, not Double like AVG would")
                    .isEqualTo(Integer.class);
        }

        @Test
        @DisplayName("MIN infers Long type from Long selector")
        void handleBuilderScalarSubquery_min_infersLongFromLongSelector() {
            context.push(new SubqueryBuilderReference(Person.class, "Person"));
            context.push(field("departmentId", Long.class)); // Long selector
            MethodInsnNode methodInsn = createSubqueryBuilderMethodInsn(SUBQUERY_MIN,
                    "(Ljava/util/function/Function;)Ljava/lang/Comparable;");

            analyzer.handleSubqueryBuilderMethod(context, methodInsn);

            ScalarSubquery subquery = (ScalarSubquery) context.peek();
            assertThat(subquery.resultType())
                    .as("MIN with Long selector should infer Long, not Double like AVG would")
                    .isEqualTo(Long.class);
        }

        @Test
        @DisplayName("MAX infers BigDecimal type from BigDecimal selector")
        void handleBuilderScalarSubquery_max_infersBigDecimalFromBigDecimalSelector() {
            context.push(new SubqueryBuilderReference(Person.class, "Person"));
            context.push(field("balance", java.math.BigDecimal.class)); // BigDecimal selector
            MethodInsnNode methodInsn = createSubqueryBuilderMethodInsn(SUBQUERY_MAX,
                    "(Ljava/util/function/Function;)Ljava/lang/Comparable;");

            analyzer.handleSubqueryBuilderMethod(context, methodInsn);

            ScalarSubquery subquery = (ScalarSubquery) context.peek();
            assertThat(subquery.resultType())
                    .as("MAX with BigDecimal selector should infer BigDecimal")
                    .isEqualTo(java.math.BigDecimal.class);
        }

        @Test
        @DisplayName("AVG always returns Double regardless of Integer selector")
        void handleBuilderScalarSubquery_avg_alwaysReturnsDouble() {
            context.push(new SubqueryBuilderReference(Person.class, "Person"));
            context.push(field("count", Long.class)); // Long selector
            MethodInsnNode methodInsn = createSubqueryBuilderMethodInsn(SUBQUERY_AVG,
                    "(Ljava/util/function/Function;)D");

            analyzer.handleSubqueryBuilderMethod(context, methodInsn);

            ScalarSubquery subquery = (ScalarSubquery) context.peek();
            assertThat(subquery.resultType())
                    .as("AVG with Long selector should still return Double (hardcoded)")
                    .isEqualTo(Double.class);
        }
    }

    @Nested
    @DisplayName("Count with/without predicate (kill line 130 mutation)")
    class CountPredicateMutationTests {

        @Test
        @DisplayName("count() without arg predicate uses null - mutation: args.isEmpty() replaced with true")
        void handleBuilderCountSubquery_withoutPredicate_usesNullArgPredicate() {
            SubqueryBuilderReference builder = new SubqueryBuilderReference(Person.class, "Person");
            context.push(builder);
            // No args - count() without predicate
            MethodInsnNode methodInsn = createSubqueryBuilderMethodInsn(SUBQUERY_COUNT, "()J");

            analyzer.handleSubqueryBuilderMethod(context, methodInsn);

            ScalarSubquery subquery = (ScalarSubquery) context.peek();
            assertThat(subquery.predicate())
                    .as("count() without builder predicate or arg predicate should have null predicate")
                    .isNull();
        }

        @Test
        @DisplayName("count() with arg predicate uses it - mutation: args.isEmpty() replaced with true would skip args.get(0)")
        void handleBuilderCountSubquery_withArgPredicate_usesArgPredicate() {
            SubqueryBuilderReference builder = new SubqueryBuilderReference(Person.class, "Person");
            context.push(builder);
            LambdaExpression argPredicate = gt(field("age", Integer.class), constant(21));
            context.push(argPredicate);
            MethodInsnNode methodInsn = createSubqueryBuilderMethodInsn(SUBQUERY_COUNT,
                    "(Ljava/util/function/Predicate;)J");

            analyzer.handleSubqueryBuilderMethod(context, methodInsn);

            ScalarSubquery subquery = (ScalarSubquery) context.peek();
            assertThat(subquery.predicate())
                    .as("count(predicate) should use the arg predicate")
                    .isNotNull()
                    .isEqualTo(argPredicate);
        }

        @Test
        @DisplayName("count() with builder and arg predicates combines them")
        void handleBuilderCountSubquery_withBothPredicates_combinesThem() {
            LambdaExpression builderPredicate = eq(field("active", Boolean.class), constant(true));
            SubqueryBuilderReference builder = new SubqueryBuilderReference(Person.class, "Person")
                    .withPredicate(builderPredicate);
            context.push(builder);
            LambdaExpression argPredicate = gt(field("age", Integer.class), constant(21));
            context.push(argPredicate);
            MethodInsnNode methodInsn = createSubqueryBuilderMethodInsn(SUBQUERY_COUNT,
                    "(Ljava/util/function/Predicate;)J");

            analyzer.handleSubqueryBuilderMethod(context, methodInsn);

            ScalarSubquery subquery = (ScalarSubquery) context.peek();
            assertThat(subquery.predicate())
                    .as("count() with both predicates should combine with AND")
                    .isNotNull()
                    .isInstanceOf(LambdaExpression.BinaryOp.class);
        }

        @Test
        @DisplayName("count() with only builder predicate uses it")
        void handleBuilderCountSubquery_withOnlyBuilderPredicate_usesBuilderPredicate() {
            LambdaExpression builderPredicate = eq(field("active", Boolean.class), constant(true));
            SubqueryBuilderReference builder = new SubqueryBuilderReference(Person.class, "Person")
                    .withPredicate(builderPredicate);
            context.push(builder);
            // No arg predicate
            MethodInsnNode methodInsn = createSubqueryBuilderMethodInsn(SUBQUERY_COUNT, "()J");

            analyzer.handleSubqueryBuilderMethod(context, methodInsn);

            ScalarSubquery subquery = (ScalarSubquery) context.peek();
            assertThat(subquery.predicate())
                    .as("count() with only builder predicate should use it")
                    .isEqualTo(builderPredicate);
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
