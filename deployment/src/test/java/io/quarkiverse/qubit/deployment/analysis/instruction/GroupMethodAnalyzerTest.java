package io.quarkiverse.qubit.deployment.analysis.instruction;

import io.quarkiverse.qubit.deployment.ast.LambdaExpression.GroupAggregation;
import io.quarkiverse.qubit.deployment.ast.LambdaExpression.GroupAggregationType;
import io.quarkiverse.qubit.deployment.ast.LambdaExpression.GroupKeyReference;
import io.quarkiverse.qubit.deployment.ast.LambdaExpression.GroupParameter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import static io.quarkiverse.qubit.deployment.testutil.AstBuilders.*;
import static io.quarkiverse.qubit.runtime.QubitConstants.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.objectweb.asm.Opcodes.*;

/**
 * Tests for {@link GroupMethodAnalyzer}.
 *
 * <p>Tests GROUP BY query handling with Group interface methods like g.key(), g.count(),
 * g.avg(), g.min(), g.max(), etc.
 */
class GroupMethodAnalyzerTest {

    private GroupMethodAnalyzer analyzer;
    private MethodNode testMethod;
    private AnalysisContext context;

    @BeforeEach
    void setUp() {
        analyzer = new GroupMethodAnalyzer();
        testMethod = new MethodNode();
        testMethod.name = "testHaving";
        testMethod.desc = "(Lio/quarkiverse/qubit/runtime/Group;)Z";
        testMethod.instructions = new InsnList();
        context = new AnalysisContext(testMethod, 0);
    }

    // ==================== isGroupMethodCall Tests ====================

    @Nested
    class IsGroupMethodCallTests {

        @Test
        void isGroupMethodCall_withGroupInterface_returnsTrue() {
            MethodInsnNode methodInsn = new MethodInsnNode(INVOKEINTERFACE,
                    GROUP_INTERNAL_NAME, "key", "()Ljava/lang/Object;", true);

            assertThat(analyzer.isGroupMethodCall(methodInsn))
                    .as("Should recognize Group interface method call")
                    .isTrue();
        }

        @Test
        void isGroupMethodCall_withNonGroupInterface_returnsFalse() {
            MethodInsnNode methodInsn = new MethodInsnNode(INVOKEVIRTUAL,
                    "java/lang/String", "length", "()I", false);

            assertThat(analyzer.isGroupMethodCall(methodInsn))
                    .as("Should not recognize non-Group interface method")
                    .isFalse();
        }

        @Test
        void isGroupMethodCall_withSimilarName_returnsFalse() {
            MethodInsnNode methodInsn = new MethodInsnNode(INVOKEINTERFACE,
                    "java/util/List", "get", "(I)Ljava/lang/Object;", true);

            assertThat(analyzer.isGroupMethodCall(methodInsn))
                    .as("Should not recognize List.get() as Group method")
                    .isFalse();
        }
    }

    // ==================== handleGroupKey Tests ====================

    @Nested
    class HandleGroupKeyTests {

        @Test
        void handleGroupMethod_key_withGroupParameter_createsGroupKeyReference() {
            context.push(new GroupParameter("g", Object.class, 0, Object.class, Object.class));
            MethodInsnNode methodInsn = createGroupMethodInsn(METHOD_KEY, "()Ljava/lang/Object;");

            analyzer.handleGroupMethod(context, methodInsn);

            assertThat(context.getStackSize()).isEqualTo(1);
            assertThat(context.peek())
                    .as("g.key() should create GroupKeyReference")
                    .isInstanceOf(GroupKeyReference.class);
        }

        @Test
        void handleGroupMethod_key_withEmptyStack_doesNothing() {
            MethodInsnNode methodInsn = createGroupMethodInsn(METHOD_KEY, "()Ljava/lang/Object;");

            analyzer.handleGroupMethod(context, methodInsn);

            assertThat(context.isStackEmpty())
                    .as("Empty stack should remain empty")
                    .isTrue();
        }

        @Test
        void handleGroupMethod_key_withNonGroupParameter_doesNothing() {
            context.push(field("name", String.class));
            MethodInsnNode methodInsn = createGroupMethodInsn(METHOD_KEY, "()Ljava/lang/Object;");

            analyzer.handleGroupMethod(context, methodInsn);

            assertThat(context.isStackEmpty())
                    .as("Non-GroupParameter should be popped without creating result")
                    .isTrue();
        }
    }

    // ==================== handleGroupCount Tests ====================

    @Nested
    class HandleGroupCountTests {

        @Test
        void handleGroupMethod_count_withGroupParameter_createsCountAggregation() {
            context.push(new GroupParameter("g", Object.class, 0, Object.class, Object.class));
            MethodInsnNode methodInsn = createGroupMethodInsn(METHOD_COUNT, "()J");

            analyzer.handleGroupMethod(context, methodInsn);

            assertThat(context.getStackSize()).isEqualTo(1);
            assertThat(context.peek())
                    .as("g.count() should create GroupAggregation")
                    .isInstanceOf(GroupAggregation.class);
            GroupAggregation agg = (GroupAggregation) context.peek();
            assertThat(agg.aggregationType())
                    .as("Should be COUNT aggregation")
                    .isEqualTo(GroupAggregationType.COUNT);
        }

        @Test
        void handleGroupMethod_count_withEmptyStack_doesNothing() {
            MethodInsnNode methodInsn = createGroupMethodInsn(METHOD_COUNT, "()J");

            analyzer.handleGroupMethod(context, methodInsn);

            assertThat(context.isStackEmpty()).isTrue();
        }

        @Test
        void handleGroupMethod_count_withNonGroupParameter_doesNothing() {
            context.push(constant(42));
            MethodInsnNode methodInsn = createGroupMethodInsn(METHOD_COUNT, "()J");

            analyzer.handleGroupMethod(context, methodInsn);

            assertThat(context.isStackEmpty())
                    .as("Non-GroupParameter should be popped without creating result")
                    .isTrue();
        }
    }

    // ==================== handleGroupCountDistinct Tests ====================

    @Nested
    class HandleGroupCountDistinctTests {

        @Test
        void handleGroupMethod_countDistinct_createsCountDistinctAggregation() {
            context.push(new GroupParameter("g", Object.class, 0, Object.class, Object.class));
            context.push(field("name", String.class));
            MethodInsnNode methodInsn = createGroupMethodInsn(METHOD_COUNT_DISTINCT,
                    "(Lio/quarkiverse/qubit/runtime/QuerySpec;)J");

            analyzer.handleGroupMethod(context, methodInsn);

            assertThat(context.getStackSize()).isEqualTo(1);
            assertThat(context.peek())
                    .as("g.countDistinct(field) should create GroupAggregation")
                    .isInstanceOf(GroupAggregation.class);
            GroupAggregation agg = (GroupAggregation) context.peek();
            assertThat(agg.aggregationType())
                    .as("Should be COUNT_DISTINCT aggregation")
                    .isEqualTo(GroupAggregationType.COUNT_DISTINCT);
        }

        @Test
        void handleGroupMethod_countDistinct_withEmptyStack_doesNothing() {
            MethodInsnNode methodInsn = createGroupMethodInsn(METHOD_COUNT_DISTINCT,
                    "(Lio/quarkiverse/qubit/runtime/QuerySpec;)J");

            analyzer.handleGroupMethod(context, methodInsn);

            assertThat(context.isStackEmpty()).isTrue();
        }

        @Test
        void handleGroupMethod_countDistinct_withOnlyOneElement_doesNothing() {
            context.push(new GroupParameter("g", Object.class, 0, Object.class, Object.class));
            // Missing field argument
            MethodInsnNode methodInsn = createGroupMethodInsn(METHOD_COUNT_DISTINCT,
                    "(Lio/quarkiverse/qubit/runtime/QuerySpec;)J");

            analyzer.handleGroupMethod(context, methodInsn);

            // popPair returns null without popping when stack has < 2 elements
            assertThat(context.getStackSize())
                    .as("popPair should not pop when fewer than 2 elements")
                    .isEqualTo(1);
        }

        @Test
        void handleGroupMethod_countDistinct_withNonGroupParameter_doesNothing() {
            context.push(constant("not a group"));
            context.push(field("name", String.class));
            MethodInsnNode methodInsn = createGroupMethodInsn(METHOD_COUNT_DISTINCT,
                    "(Lio/quarkiverse/qubit/runtime/QuerySpec;)J");

            analyzer.handleGroupMethod(context, methodInsn);

            assertThat(context.isStackEmpty())
                    .as("Non-GroupParameter should not create aggregation")
                    .isTrue();
        }
    }

    // ==================== handleGroupAggregationWithField Tests (avg, sumInteger, sumLong, sumDouble) ====================

    @Nested
    class HandleGroupAggregationWithFieldTests {

        @Test
        void handleGroupMethod_avg_createsAvgAggregation() {
            context.push(new GroupParameter("g", Object.class, 0, Object.class, Object.class));
            context.push(field("salary", Double.class));
            MethodInsnNode methodInsn = createGroupMethodInsn(METHOD_AVG,
                    "(Lio/quarkiverse/qubit/runtime/QuerySpec;)D");

            analyzer.handleGroupMethod(context, methodInsn);

            assertThat(context.getStackSize()).isEqualTo(1);
            GroupAggregation agg = (GroupAggregation) context.peek();
            assertThat(agg.aggregationType())
                    .as("g.avg(field) should create AVG aggregation")
                    .isEqualTo(GroupAggregationType.AVG);
        }

        @Test
        void handleGroupMethod_sumInteger_createsSumIntegerAggregation() {
            context.push(new GroupParameter("g", Object.class, 0, Object.class, Object.class));
            context.push(field("count", Integer.class));
            MethodInsnNode methodInsn = createGroupMethodInsn(METHOD_SUM_INTEGER,
                    "(Lio/quarkiverse/qubit/runtime/QuerySpec;)I");

            analyzer.handleGroupMethod(context, methodInsn);

            assertThat(context.getStackSize()).isEqualTo(1);
            GroupAggregation agg = (GroupAggregation) context.peek();
            assertThat(agg.aggregationType())
                    .as("g.sumInteger(field) should create SUM_INTEGER aggregation")
                    .isEqualTo(GroupAggregationType.SUM_INTEGER);
        }

        @Test
        void handleGroupMethod_sumLong_createsSumLongAggregation() {
            context.push(new GroupParameter("g", Object.class, 0, Object.class, Object.class));
            context.push(field("count", Long.class));
            MethodInsnNode methodInsn = createGroupMethodInsn(METHOD_SUM_LONG,
                    "(Lio/quarkiverse/qubit/runtime/QuerySpec;)J");

            analyzer.handleGroupMethod(context, methodInsn);

            assertThat(context.getStackSize()).isEqualTo(1);
            GroupAggregation agg = (GroupAggregation) context.peek();
            assertThat(agg.aggregationType())
                    .as("g.sumLong(field) should create SUM_LONG aggregation")
                    .isEqualTo(GroupAggregationType.SUM_LONG);
        }

        @Test
        void handleGroupMethod_sumDouble_createsSumDoubleAggregation() {
            context.push(new GroupParameter("g", Object.class, 0, Object.class, Object.class));
            context.push(field("amount", Double.class));
            MethodInsnNode methodInsn = createGroupMethodInsn(METHOD_SUM_DOUBLE,
                    "(Lio/quarkiverse/qubit/runtime/QuerySpec;)D");

            analyzer.handleGroupMethod(context, methodInsn);

            assertThat(context.getStackSize()).isEqualTo(1);
            GroupAggregation agg = (GroupAggregation) context.peek();
            assertThat(agg.aggregationType())
                    .as("g.sumDouble(field) should create SUM_DOUBLE aggregation")
                    .isEqualTo(GroupAggregationType.SUM_DOUBLE);
        }

        @Test
        void handleGroupMethod_avg_withEmptyStack_doesNothing() {
            MethodInsnNode methodInsn = createGroupMethodInsn(METHOD_AVG,
                    "(Lio/quarkiverse/qubit/runtime/QuerySpec;)D");

            analyzer.handleGroupMethod(context, methodInsn);

            assertThat(context.isStackEmpty()).isTrue();
        }

        @Test
        void handleGroupMethod_avg_withNonGroupParameter_doesNothing() {
            context.push(field("wrongTarget", Object.class));
            context.push(field("salary", Double.class));
            MethodInsnNode methodInsn = createGroupMethodInsn(METHOD_AVG,
                    "(Lio/quarkiverse/qubit/runtime/QuerySpec;)D");

            analyzer.handleGroupMethod(context, methodInsn);

            assertThat(context.isStackEmpty())
                    .as("Non-GroupParameter should not create aggregation")
                    .isTrue();
        }
    }

    // ==================== handleGroupMinMax Tests ====================

    @Nested
    class HandleGroupMinMaxTests {

        @Test
        void handleGroupMethod_min_createsMinAggregation() {
            context.push(new GroupParameter("g", Object.class, 0, Object.class, Object.class));
            context.push(field("salary", Double.class));
            MethodInsnNode methodInsn = createGroupMethodInsn(METHOD_MIN,
                    "(Lio/quarkiverse/qubit/runtime/QuerySpec;)Ljava/lang/Object;");

            analyzer.handleGroupMethod(context, methodInsn);

            assertThat(context.getStackSize()).isEqualTo(1);
            GroupAggregation agg = (GroupAggregation) context.peek();
            assertThat(agg.aggregationType())
                    .as("g.min(field) should create MIN aggregation")
                    .isEqualTo(GroupAggregationType.MIN);
        }

        @Test
        void handleGroupMethod_max_createsMaxAggregation() {
            context.push(new GroupParameter("g", Object.class, 0, Object.class, Object.class));
            context.push(field("salary", Double.class));
            MethodInsnNode methodInsn = createGroupMethodInsn(METHOD_MAX,
                    "(Lio/quarkiverse/qubit/runtime/QuerySpec;)Ljava/lang/Object;");

            analyzer.handleGroupMethod(context, methodInsn);

            assertThat(context.getStackSize()).isEqualTo(1);
            GroupAggregation agg = (GroupAggregation) context.peek();
            assertThat(agg.aggregationType())
                    .as("g.max(field) should create MAX aggregation")
                    .isEqualTo(GroupAggregationType.MAX);
        }

        @Test
        void handleGroupMethod_min_withEmptyStack_doesNothing() {
            MethodInsnNode methodInsn = createGroupMethodInsn(METHOD_MIN,
                    "(Lio/quarkiverse/qubit/runtime/QuerySpec;)Ljava/lang/Object;");

            analyzer.handleGroupMethod(context, methodInsn);

            assertThat(context.isStackEmpty()).isTrue();
        }

        @Test
        void handleGroupMethod_max_withEmptyStack_doesNothing() {
            MethodInsnNode methodInsn = createGroupMethodInsn(METHOD_MAX,
                    "(Lio/quarkiverse/qubit/runtime/QuerySpec;)Ljava/lang/Object;");

            analyzer.handleGroupMethod(context, methodInsn);

            assertThat(context.isStackEmpty()).isTrue();
        }

        @Test
        void handleGroupMethod_min_withNonGroupParameter_doesNothing() {
            context.push(constant("not a group"));
            context.push(field("salary", Double.class));
            MethodInsnNode methodInsn = createGroupMethodInsn(METHOD_MIN,
                    "(Lio/quarkiverse/qubit/runtime/QuerySpec;)Ljava/lang/Object;");

            analyzer.handleGroupMethod(context, methodInsn);

            assertThat(context.isStackEmpty())
                    .as("Non-GroupParameter should not create aggregation")
                    .isTrue();
        }

        @Test
        void handleGroupMethod_max_withNonGroupParameter_doesNothing() {
            context.push(param("entity", Object.class, 0));
            context.push(field("salary", Double.class));
            MethodInsnNode methodInsn = createGroupMethodInsn(METHOD_MAX,
                    "(Lio/quarkiverse/qubit/runtime/QuerySpec;)Ljava/lang/Object;");

            analyzer.handleGroupMethod(context, methodInsn);

            assertThat(context.isStackEmpty())
                    .as("Non-GroupParameter should not create aggregation")
                    .isTrue();
        }
    }

    // ==================== Unhandled Method Tests ====================

    @Nested
    class UnhandledMethodTests {

        @Test
        void handleGroupMethod_unknownMethod_doesNothing() {
            context.push(new GroupParameter("g", Object.class, 0, Object.class, Object.class));
            MethodInsnNode methodInsn = createGroupMethodInsn("unknownMethod", "()V");

            analyzer.handleGroupMethod(context, methodInsn);

            // Unknown method - GroupParameter is not consumed
            assertThat(context.getStackSize())
                    .as("Unknown method should not consume stack")
                    .isEqualTo(1);
        }
    }

    // ==================== Helper Methods ====================

    /**
     * Creates a Group interface method instruction.
     */
    private MethodInsnNode createGroupMethodInsn(String methodName, String descriptor) {
        return new MethodInsnNode(INVOKEINTERFACE,
                GROUP_INTERNAL_NAME, methodName, descriptor, true);
    }
}
