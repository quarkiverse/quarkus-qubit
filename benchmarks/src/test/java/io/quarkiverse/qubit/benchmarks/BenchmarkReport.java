package io.quarkiverse.qubit.benchmarks;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;

/**
 * Writes benchmark results to {@code target/benchmark-results.json}.
 * Uses simple string building -- no external JSON library needed.
 */
final class BenchmarkReport {

    private BenchmarkReport() {
    }

    static void writeResults(List<BenchmarkResult> results) {
        writeResults(results, "benchmark-results");
    }

    static void writeResults(List<BenchmarkResult> results, String filename) {
        Path OUTPUT = Path.of("target/" + filename + ".json");
        var sb = new StringBuilder("[\n");
        for (int i = 0; i < results.size(); i++) {
            var r = results.get(i);
            sb.append("  {")
                    .append("\"testName\":\"").append(escape(r.testName())).append("\",")
                    .append("\"metric\":\"").append(escape(r.metric())).append("\",")
                    .append("\"value\":").append(r.value()).append(",")
                    .append("\"unit\":\"").append(escape(r.unit())).append("\"")
                    .append("}");
            if (i < results.size() - 1) {
                sb.append(",");
            }
            sb.append("\n");
        }
        sb.append("]\n");
        try {
            Files.createDirectories(OUTPUT.getParent());
            Files.writeString(OUTPUT, sb.toString(),
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            System.out.println("[benchmark] Results written to " + OUTPUT.toAbsolutePath());
        } catch (IOException e) {
            System.err.println("[benchmark] Failed to write results: " + e.getMessage());
        }
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
