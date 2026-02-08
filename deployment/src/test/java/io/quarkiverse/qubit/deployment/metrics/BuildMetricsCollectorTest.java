package io.quarkiverse.qubit.deployment.metrics;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

class BuildMetricsCollectorTest {

    @TempDir
    Path tempDir;

    @Test
    void collectsPhaseTiming() {
        BuildMetricsCollector collector = new BuildMetricsCollector();

        collector.startPhase("lambda_discovery");
        // Simulate work
        collector.endPhase("lambda_discovery");

        assertThat(collector.getPhaseDuration("lambda_discovery"))
            .isGreaterThanOrEqualTo(0L);
    }

    @Test
    void tracksQueryCount() {
        BuildMetricsCollector collector = new BuildMetricsCollector();

        collector.incrementQueryCount();
        collector.incrementQueryCount();

        assertThat(collector.getQueryCount()).isEqualTo(2);
    }

    @Test
    void tracksQueryTypeBreakdown() throws Exception {
        BuildMetricsCollector collector = new BuildMetricsCollector();

        collector.recordQueryType("SIMPLE");
        collector.recordQueryType("SIMPLE");
        collector.recordQueryType("JOIN");
        collector.addQueryTypeAnalysisTime("SIMPLE", 1000);
        collector.addQueryTypeCodeGenTime("SIMPLE", 500);

        // Verify through JSON report output
        Path outputPath = tempDir.resolve("query-types.json");
        collector.writeReport(outputPath);
        String content = Files.readString(outputPath);

        assertThat(content)
                .contains("\"SIMPLE\"")
                .contains("\"count\": 2")  // SIMPLE recorded twice
                .contains("\"JOIN\"")
                .contains("\"analysis_time_nanos\": 1000")
                .contains("\"codegen_time_nanos\": 500");
    }

    @Test
    void tracksExpressionTypes() throws Exception {
        BuildMetricsCollector collector = new BuildMetricsCollector();

        collector.recordExpressionType(BuildMetricsCollector.EXPR_COMPARISON);
        collector.recordExpressionType(BuildMetricsCollector.EXPR_STRING);
        collector.addExpressionTypeTime(BuildMetricsCollector.EXPR_COMPARISON, 100);

        // Verify through JSON report output
        Path outputPath = tempDir.resolve("expression-types.json");
        collector.writeReport(outputPath);
        String content = Files.readString(outputPath);

        assertThat(content)
                .contains("\"expression_types\"")
                .contains("\"COMPARISON\"")
                .contains("\"STRING\"")
                .contains("\"time_nanos\": 100");
    }

    @Test
    void tracksPerClassAnalysis() throws Exception {
        BuildMetricsCollector collector = new BuildMetricsCollector();

        collector.addClassAnalysisTime("com.example.Entity1", 1000);
        collector.addClassAnalysisTime("com.example.Entity2", 2000);
        collector.addClassLambdaCount("com.example.Entity1", 3);

        // Verify through JSON report output (top_classes_by_time section)
        Path outputPath = tempDir.resolve("per-class.json");
        collector.writeReport(outputPath);
        String content = Files.readString(outputPath);

        assertThat(content)
                .contains("\"top_classes_by_time\"")
                .contains("com.example.Entity1")
                .contains("com.example.Entity2")
                .contains("\"time_nanos\": 2000")  // Entity2 has higher time
                .contains("\"lambdas\": 3");       // Entity1 lambda count
    }

    @Test
    void tracksCacheEffectiveness() {
        BuildMetricsCollector collector = new BuildMetricsCollector();

        collector.recordBytecodeCacheHit();
        collector.recordBytecodeCacheHit();
        collector.recordBytecodeCacheMiss();
        collector.recordClassNodeCacheHit();
        collector.recordClassNodeCacheMiss();
        collector.recordClassNodeCacheMiss();

        assertThat(collector.getBytecodeCacheHitRate()).isEqualTo(2.0 / 3.0);
        assertThat(collector.getClassNodeCacheHitRate()).isEqualTo(1.0 / 3.0);
    }

    @Test
    void tracksEntityEnhancement() {
        BuildMetricsCollector collector = new BuildMetricsCollector();

        collector.incrementEntityClassesEnhanced();
        collector.incrementEntityClassesEnhanced();
        collector.incrementRepositoriesEnhanced();
        collector.addEntityEnhancementTime(5000);

        assertThat(collector.getEntityClassesEnhanced()).isEqualTo(2);
        assertThat(collector.getRepositoriesEnhanced()).isEqualTo(1);
    }

    @Test
    void tracksGranularMetrics() {
        BuildMetricsCollector collector = new BuildMetricsCollector();

        collector.addBytecodeLoadTime(1000);
        collector.addInstructionAnalysisTime(2000);
        collector.addCodeGenerationTime(3000);
        collector.incrementUniqueClassesLoaded();
        collector.incrementTotalBytecodeLoads();
        collector.incrementDuplicateCount();

        assertThat(collector.getBytecodeLoadTimeNanos()).isEqualTo(1000);
        assertThat(collector.getInstructionAnalysisTimeNanos()).isEqualTo(2000);
        assertThat(collector.getCodeGenerationTimeNanos()).isEqualTo(3000);
        assertThat(collector.getUniqueClassesLoaded()).isEqualTo(1);
        assertThat(collector.getTotalBytecodeLoads()).isEqualTo(1);
        assertThat(collector.getDuplicateCount()).isEqualTo(1);
    }

    @Test
    void tracksJandexPreFilterSkips() {
        var collector = new BuildMetricsCollector();

        collector.incrementJandexPreFilterSkips();
        collector.incrementJandexPreFilterSkips();

        assertThat(collector.getJandexPreFilterSkips()).isEqualTo(2);
    }

    @Test
    void tracksEarlyDeduplication() {
        BuildMetricsCollector collector = new BuildMetricsCollector();

        collector.incrementEarlyDeduplicationHits();
        collector.addEarlyDeduplicationCheckTime(500);

        assertThat(collector.getEarlyDeduplicationHits()).isEqualTo(1);
        assertThat(collector.getEarlyDeduplicationCheckTimeNanos()).isEqualTo(500);
    }

    @Test
    void recordsFailedClasses() throws Exception {
        BuildMetricsCollector collector = new BuildMetricsCollector();

        collector.recordFailedClass("com.example.FailedClass");
        collector.recordFailedClass("com.example.AnotherFailedClass");

        // Verify through JSON report output
        Path outputPath = tempDir.resolve("failed-classes.json");
        collector.writeReport(outputPath);
        String content = Files.readString(outputPath);

        assertThat(content)
                .contains("\"failed_classes\"")
                .contains("com.example.FailedClass")
                .contains("com.example.AnotherFailedClass");
    }

    @Test
    void writesJsonReport() throws Exception {
        BuildMetricsCollector collector = new BuildMetricsCollector();
        collector.startPhase("test_phase");
        collector.endPhase("test_phase");
        collector.incrementQueryCount();
        collector.recordQueryType("SIMPLE");
        collector.recordExpressionType(BuildMetricsCollector.EXPR_COMPARISON);
        collector.recordBytecodeCacheHit();
        collector.recordBytecodeCacheMiss();
        collector.incrementEntityClassesEnhanced();

        Path outputPath = tempDir.resolve("metrics.json");
        collector.writeReport(outputPath);

        assertThat(outputPath).exists();
        String content = Files.readString(outputPath);
        assertThat(content)
                .contains("\"total_ms\"")
                .contains("\"phases\"")
                .contains("\"test_phase\"")
                .contains("\"query_count\"")
                .contains("\"query_types\"")
                .contains("\"expression_types\"")
                .contains("\"cache\"")
                .contains("\"bytecode_hit_rate\"")
                .contains("\"threads\"")
                .contains("\"histograms\"")
                .contains("\"enhancement\"")
                .contains("\"entity_classes_enhanced\"")
                .contains("\"memory\"")
                .contains("\"jandex_pre_filter_skips\"");
    }

    @Test
    void writesFlameGraphOutput() throws Exception {
        BuildMetricsCollector collector = new BuildMetricsCollector();
        collector.startPhase("analysis");
        collector.endPhase("analysis");
        collector.addQueryTypeAnalysisTime("SIMPLE", 1_000_000); // 1ms
        collector.addQueryTypeCodeGenTime("SIMPLE", 500_000);    // 0.5ms

        Path outputPath = tempDir.resolve("flamegraph.collapsed");
        collector.writeFlameGraph(outputPath);

        assertThat(outputPath).exists();
        String content = Files.readString(outputPath);
        // Collapsed stack format: stack;frame;frame count
        assertThat(content).contains("qubit;analysis");
    }

    @Test
    void capturesMemoryMetrics() throws Exception {
        BuildMetricsCollector collector = new BuildMetricsCollector();

        // Memory metrics are captured at start and end of report generation
        Path outputPath = tempDir.resolve("memory.json");
        collector.writeReport(outputPath);
        String content = Files.readString(outputPath);

        // Verify memory section contains heap tracking
        assertThat(content)
                .contains("\"memory\"")
                .contains("\"start_heap_bytes\"")
                .contains("\"end_heap_bytes\"")
                .contains("\"delta_bytes\"");
    }

    @Test
    void tracksThreadUtilization() {
        BuildMetricsCollector collector = new BuildMetricsCollector();

        collector.recordThreadWork(1000);
        collector.recordThreadWork(2000);

        assertThat(collector.getActiveThreadCount()).isEqualTo(1); // Same thread
    }

    @Test
    void handlesZeroCacheAccesses() {
        BuildMetricsCollector collector = new BuildMetricsCollector();

        assertThat(collector.getBytecodeCacheHitRate()).isEqualTo(0.0);
        assertThat(collector.getClassNodeCacheHitRate()).isEqualTo(0.0);
    }
}
