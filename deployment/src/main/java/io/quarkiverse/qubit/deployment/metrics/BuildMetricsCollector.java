package io.quarkiverse.qubit.deployment.metrics;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Collects build-time performance metrics for Qubit processing phases.
 * Thread-safe for use across build steps.
 */
public class BuildMetricsCollector {

    private final long startTime;
    private final Map<String, Long> phaseStartTimes = new LinkedHashMap<>();
    private final Map<String, Long> phaseDurations = new LinkedHashMap<>();
    private int queryCount;

    public BuildMetricsCollector() {
        this.startTime = System.nanoTime();
    }

    /**
     * Marks the start of a processing phase.
     */
    public synchronized void startPhase(String phase) {
        phaseStartTimes.put(phase, System.nanoTime());
    }

    /**
     * Marks the end of a processing phase and records duration.
     */
    public synchronized void endPhase(String phase) {
        Long start = phaseStartTimes.get(phase);
        if (start != null) {
            long durationNanos = System.nanoTime() - start;
            long durationMs = TimeUnit.NANOSECONDS.toMillis(durationNanos);
            phaseDurations.put(phase, durationMs);
        }
    }

    /**
     * Increments the count of processed queries.
     */
    public synchronized void incrementQueryCount() {
        queryCount++;
    }

    /**
     * Returns duration of a phase in milliseconds.
     */
    public synchronized long getPhaseDuration(String phase) {
        return phaseDurations.getOrDefault(phase, 0L);
    }

    /**
     * Returns total query count.
     */
    public synchronized int getQueryCount() {
        return queryCount;
    }

    /**
     * Writes metrics report to JSON file.
     */
    public synchronized void writeReport(Path outputPath) throws IOException {
        long totalMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime);

        StringBuilder json = new StringBuilder();
        json.append("{\n");
        json.append("  \"timestamp\": \"").append(Instant.now()).append("\",\n");
        json.append("  \"total_ms\": ").append(totalMs).append(",\n");
        json.append("  \"phases\": {\n");

        boolean first = true;
        for (Map.Entry<String, Long> entry : phaseDurations.entrySet()) {
            if (!first) {
                json.append(",\n");
            }
            json.append("    \"").append(entry.getKey()).append("\": ").append(entry.getValue());
            first = false;
        }

        json.append("\n  },\n");
        json.append("  \"query_count\": ").append(queryCount).append("\n");
        json.append("}\n");

        Files.createDirectories(outputPath.getParent());
        Files.writeString(outputPath, json.toString());
    }
}
