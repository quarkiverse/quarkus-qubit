package io.quarkiverse.qubit.deployment.analysis;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link StreamPipelineAnalyzer}.
 * <p>
 * Tests the record types and basic analyzer behavior. Complex bytecode analysis
 * is covered by integration tests that use real compiled classes.
 */
class StreamPipelineAnalyzerTest {

    private StreamPipelineAnalyzer analyzer;
    private ClassNode classNode;
    private MethodNode methodNode;

    @BeforeEach
    void setUp() {
        analyzer = new StreamPipelineAnalyzer();
        classNode = new ClassNode();
        classNode.name = "com/example/TestClass";

        methodNode = new MethodNode();
        methodNode.name = "testMethod";
        methodNode.desc = "()Ljava/util/List;";
        methodNode.instructions = new InsnList();
    }

    // ========================================================================
    // StreamPipeline Record Behavior Tests
    // ========================================================================

    @Nested
    class StreamPipelineTests {

        @Test
        void streamPipeline_operationCount_returnsCorrectCount() {
            List<StreamPipelineAnalyzer.PipelineOperation> ops = List.of(
                    new StreamPipelineAnalyzer.PipelineOperation("where", "l1", "()Z", 1),
                    new StreamPipelineAnalyzer.PipelineOperation("select", "l2", "()V", 2),
                    new StreamPipelineAnalyzer.PipelineOperation("sortedBy", "l3", "()V", 3)
            );
            StreamPipelineAnalyzer.StreamPipeline pipeline = new StreamPipelineAnalyzer.StreamPipeline(
                    "Test", "method", "toList", ops, 1);

            assertThat(pipeline.operationCount()).isEqualTo(3);
        }

        @Test
        void streamPipeline_isCountQuery_returnsTrueForCount() {
            List<StreamPipelineAnalyzer.PipelineOperation> ops = List.of(
                    new StreamPipelineAnalyzer.PipelineOperation("where", "l1", "()Z", 1)
            );
            StreamPipelineAnalyzer.StreamPipeline pipeline = new StreamPipelineAnalyzer.StreamPipeline(
                    "Test", "method", "count", ops, 1);

            assertThat(pipeline.isCountQuery()).isTrue();
        }

        @Test
        void streamPipeline_isCountQuery_returnsFalseForOtherTerminals() {
            List<StreamPipelineAnalyzer.PipelineOperation> ops = List.of(
                    new StreamPipelineAnalyzer.PipelineOperation("where", "l1", "()Z", 1)
            );

            assertThat(createPipeline("toList").isCountQuery()).isFalse();
            assertThat(createPipeline("exists").isCountQuery()).isFalse();
            assertThat(createPipeline("getSingleResult").isCountQuery()).isFalse();
            assertThat(createPipeline("findFirst").isCountQuery()).isFalse();
        }

        @Test
        void streamPipeline_emptyOperations_hasZeroCount() {
            StreamPipelineAnalyzer.StreamPipeline pipeline = new StreamPipelineAnalyzer.StreamPipeline(
                    "Test", "method", "toList", List.of(), 1);

            assertThat(pipeline.operationCount()).isZero();
        }

        private StreamPipelineAnalyzer.StreamPipeline createPipeline(String terminal) {
            return new StreamPipelineAnalyzer.StreamPipeline(
                    "Test", "method", terminal,
                    List.of(new StreamPipelineAnalyzer.PipelineOperation("where", "l", "()Z", 1)),
                    1);
        }
    }

    // ========================================================================
    // analyzePipeline Edge Case Tests
    // ========================================================================

    @Nested
    class AnalyzePipelineEdgeCaseTests {

        @Test
        void analyzePipeline_withNoOperations_returnsNull() {
            // Add only a terminal operation with no intermediate operations
            methodNode.instructions.add(new MethodInsnNode(
                    Opcodes.INVOKEVIRTUAL, "SomeClass", "toList", "()Ljava/util/List;"));

            StreamPipelineAnalyzer.StreamPipeline result = analyzer.analyzePipeline(
                    classNode, methodNode, 0, "toList", 10);

            assertThat(result).isNull();
        }

        @Test
        void analyzePipeline_withEmptyInstructions_returnsNull() {
            // Empty instruction list
            StreamPipelineAnalyzer.StreamPipeline result = analyzer.analyzePipeline(
                    classNode, methodNode, 0, "toList", 10);

            assertThat(result).isNull();
        }

        @Test
        void analyzePipeline_withOnlyNonFluentMethods_returnsNull() {
            methodNode.instructions.add(new MethodInsnNode(
                    Opcodes.INVOKEVIRTUAL, "SomeClass", "someMethod", "()V"));
            methodNode.instructions.add(new MethodInsnNode(
                    Opcodes.INVOKEVIRTUAL, "SomeClass", "anotherMethod", "()V"));
            methodNode.instructions.add(new MethodInsnNode(
                    Opcodes.INVOKEVIRTUAL, "SomeClass", "toList", "()Ljava/util/List;"));

            StreamPipelineAnalyzer.StreamPipeline result = analyzer.analyzePipeline(
                    classNode, methodNode, 2, "toList", 10);

            assertThat(result).isNull();
        }

        @Test
        void analyzePipeline_withNegativeStartIndex_returnsNull() {
            StreamPipelineAnalyzer.StreamPipeline result = analyzer.analyzePipeline(
                    classNode, methodNode, -1, "toList", 10);

            assertThat(result).isNull();
        }

        @Test
        void analyzePipeline_stopsImmediatelyAtEntryPoint() {
            // If we start right at an entry point, we should return null (no operations found)
            methodNode.instructions.add(new MethodInsnNode(
                    Opcodes.INVOKESTATIC, "Repository", "where",
                    "(Lio/quarkiverse/qubit/QuerySpec;)LFluentQuery;"));
            methodNode.instructions.add(new MethodInsnNode(
                    Opcodes.INVOKEVIRTUAL, "FluentQuery", "toList", "()Ljava/util/List;"));

            StreamPipelineAnalyzer.StreamPipeline result = analyzer.analyzePipeline(
                    classNode, methodNode, 1, "toList", 10);

            // Should return null because no operations were found before hitting entry point
            assertThat(result).isNull();
        }

        @Test
        void analyzePipeline_handlesLineNode_gracefully() {
            // Add line number node (should be skipped)
            methodNode.instructions.add(new LineNumberNode(42, new LabelNode()));
            methodNode.instructions.add(new MethodInsnNode(
                    Opcodes.INVOKEVIRTUAL, "SomeClass", "toList", "()Ljava/util/List;"));

            StreamPipelineAnalyzer.StreamPipeline result = analyzer.analyzePipeline(
                    classNode, methodNode, 1, "toList", 10);

            assertThat(result).isNull(); // No operations found
        }

        @Test
        void analyzePipeline_handlesLabelNode_gracefully() {
            // Add label node (should be skipped)
            methodNode.instructions.add(new LabelNode());
            methodNode.instructions.add(new MethodInsnNode(
                    Opcodes.INVOKEVIRTUAL, "SomeClass", "toList", "()Ljava/util/List;"));

            StreamPipelineAnalyzer.StreamPipeline result = analyzer.analyzePipeline(
                    classNode, methodNode, 1, "toList", 10);

            assertThat(result).isNull(); // No operations found
        }
    }

    // ========================================================================
    // Analyzer Instance Tests
    // ========================================================================

    @Nested
    class AnalyzerInstanceTests {

        @Test
        void analyzer_isReusable() {
            // Same analyzer instance can be used for multiple calls
            StreamPipelineAnalyzer.StreamPipeline result1 = analyzer.analyzePipeline(
                    classNode, methodNode, 0, "toList", 10);
            StreamPipelineAnalyzer.StreamPipeline result2 = analyzer.analyzePipeline(
                    classNode, methodNode, 0, "count", 20);

            // Both return null (no operations) but analyzer didn't throw
            assertThat(result1).isNull();
            assertThat(result2).isNull();
        }
    }
}
