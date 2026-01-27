package io.quarkiverse.qubit.it.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

class PerformanceReportIT {

    @TempDir
    Path tempDir;

    @Test
    void writesRuntimeMetrics() throws Exception {
        Path outputPath = tempDir.resolve("runtime-metrics.json");

        PerformanceReport.writeRuntimeMetrics(47, 102400L, outputPath);

        assertThat(outputPath).exists();
        String content = Files.readString(outputPath);
        assertThat(content).contains("\"executor_count\": 47");
        assertThat(content).contains("\"heap_bytes\": 102400");
        assertThat(content).contains("\"timestamp\"");
    }
}
