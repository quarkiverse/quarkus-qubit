package io.quarkiverse.qubit.it.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

/**
 * Utility for writing runtime performance metrics to JSON.
 */
public final class PerformanceReport {

    private PerformanceReport() {
        // Utility class
    }

    /** Writes runtime metrics (executor count, heap usage) to a JSON file. */
    public static void writeRuntimeMetrics(int executorCount, long heapBytes, Path outputPath) throws IOException {
        StringBuilder json = new StringBuilder();
        json.append("{\n");
        json.append("  \"timestamp\": \"").append(Instant.now()).append("\",\n");
        json.append("  \"executor_count\": ").append(executorCount).append(",\n");
        json.append("  \"heap_bytes\": ").append(heapBytes).append(",\n");
        json.append("  \"heap_kb\": ").append(heapBytes / 1024).append(",\n");
        json.append("  \"heap_mb\": ").append(String.format("%.2f", heapBytes / (1024.0 * 1024.0))).append("\n");
        json.append("}\n");

        Path parent = outputPath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.writeString(outputPath, json.toString());
    }
}
