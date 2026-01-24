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
    void writesJsonReport() throws Exception {
        BuildMetricsCollector collector = new BuildMetricsCollector();
        collector.startPhase("test_phase");
        collector.endPhase("test_phase");
        collector.incrementQueryCount();

        Path outputPath = tempDir.resolve("metrics.json");
        collector.writeReport(outputPath);

        assertThat(outputPath).exists();
        String content = Files.readString(outputPath);
        assertThat(content).contains("\"total_ms\"");
        assertThat(content).contains("\"phases\"");
        assertThat(content).contains("\"test_phase\"");
        assertThat(content).contains("\"query_count\"");
    }
}
