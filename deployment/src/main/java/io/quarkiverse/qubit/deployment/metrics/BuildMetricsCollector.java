package io.quarkiverse.qubit.deployment.metrics;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Collects build-time performance metrics for Qubit processing phases.
 * Thread-safe for use across build steps and parallel lambda analysis.
 */
public class BuildMetricsCollector {

    private final long startTime;
    private final Map<String, Long> phaseStartTimes = new LinkedHashMap<>();
    private final Map<String, Long> phaseDurations = new LinkedHashMap<>();
    private final AtomicInteger queryCount = new AtomicInteger();

    // Granular timing metrics (in nanoseconds)
    private final AtomicLong bytecodeLoadTimeNanos = new AtomicLong();
    private final AtomicLong asmParsingTimeNanos = new AtomicLong();
    private final AtomicLong instructionAnalysisTimeNanos = new AtomicLong();
    private final AtomicLong codeGenerationTimeNanos = new AtomicLong();
    private final AtomicLong deduplicationCheckTimeNanos = new AtomicLong();

    // Granular counting metrics
    private final AtomicLong uniqueClassesLoaded = new AtomicLong();
    private final AtomicLong totalBytecodeLoads = new AtomicLong();
    private final AtomicLong duplicateCount = new AtomicLong();
    private final AtomicLong earlyDeduplicationHits = new AtomicLong();
    private final AtomicLong earlyDeduplicationCheckTimeNanos = new AtomicLong();

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
     * Thread-safe for parallel lambda analysis.
     */
    public void incrementQueryCount() {
        queryCount.incrementAndGet();
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
    public int getQueryCount() {
        return queryCount.get();
    }

    // --- Granular timing methods ---

    /**
     * Adds time spent loading class bytecode from disk.
     */
    public void addBytecodeLoadTime(long nanos) {
        bytecodeLoadTimeNanos.addAndGet(nanos);
    }

    /**
     * Adds time spent parsing bytecode with ASM ClassReader.
     */
    public void addAsmParsingTime(long nanos) {
        asmParsingTimeNanos.addAndGet(nanos);
    }

    /**
     * Adds time spent walking bytecode instructions.
     */
    public void addInstructionAnalysisTime(long nanos) {
        instructionAnalysisTimeNanos.addAndGet(nanos);
    }

    /**
     * Adds time spent generating executor bytecode with Gizmo.
     */
    public void addCodeGenerationTime(long nanos) {
        codeGenerationTimeNanos.addAndGet(nanos);
    }

    /**
     * Adds time spent checking for duplicates.
     */
    public void addDeduplicationCheckTime(long nanos) {
        deduplicationCheckTimeNanos.addAndGet(nanos);
    }

    // --- Granular counting methods ---

    /**
     * Increments the count of unique classes loaded.
     */
    public void incrementUniqueClassesLoaded() {
        uniqueClassesLoaded.incrementAndGet();
    }

    /**
     * Increments the total number of bytecode load operations.
     */
    public void incrementTotalBytecodeLoads() {
        totalBytecodeLoads.incrementAndGet();
    }

    /**
     * Increments the count of deduplicated lambdas.
     */
    public void incrementDuplicateCount() {
        duplicateCount.incrementAndGet();
    }

    /**
     * Increments the count of early deduplication hits (analysis skipped).
     */
    public void incrementEarlyDeduplicationHits() {
        earlyDeduplicationHits.incrementAndGet();
    }

    /**
     * Adds time spent on early deduplication checks.
     */
    public void addEarlyDeduplicationCheckTime(long nanos) {
        earlyDeduplicationCheckTimeNanos.addAndGet(nanos);
    }

    // --- Granular getter methods (primarily for testing) ---

    /** Returns total bytecode load count. */
    public long getTotalBytecodeLoads() {
        return totalBytecodeLoads.get();
    }

    /** Returns unique classes loaded count. */
    public long getUniqueClassesLoaded() {
        return uniqueClassesLoaded.get();
    }

    /** Returns duplicate count. */
    public long getDuplicateCount() {
        return duplicateCount.get();
    }

    /** Returns bytecode load time in nanoseconds. */
    public long getBytecodeLoadTimeNanos() {
        return bytecodeLoadTimeNanos.get();
    }

    /** Returns early deduplication hit count. */
    public long getEarlyDeduplicationHits() {
        return earlyDeduplicationHits.get();
    }

    /** Returns early deduplication check time in nanoseconds. */
    public long getEarlyDeduplicationCheckTimeNanos() {
        return earlyDeduplicationCheckTimeNanos.get();
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
        json.append("  \"query_count\": ").append(queryCount.get()).append(",\n");
        json.append("  \"granular\": {\n");
        json.append("    \"bytecode_load_time_nanos\": ").append(bytecodeLoadTimeNanos.get()).append(",\n");
        json.append("    \"asm_parsing_time_nanos\": ").append(asmParsingTimeNanos.get()).append(",\n");
        json.append("    \"instruction_analysis_time_nanos\": ").append(instructionAnalysisTimeNanos.get()).append(",\n");
        json.append("    \"code_generation_time_nanos\": ").append(codeGenerationTimeNanos.get()).append(",\n");
        json.append("    \"deduplication_check_time_nanos\": ").append(deduplicationCheckTimeNanos.get()).append(",\n");
        json.append("    \"unique_classes_loaded\": ").append(uniqueClassesLoaded.get()).append(",\n");
        json.append("    \"total_bytecode_loads\": ").append(totalBytecodeLoads.get()).append(",\n");
        json.append("    \"duplicate_count\": ").append(duplicateCount.get()).append(",\n");
        json.append("    \"early_deduplication_hits\": ").append(earlyDeduplicationHits.get()).append(",\n");
        json.append("    \"early_deduplication_check_time_nanos\": ").append(earlyDeduplicationCheckTimeNanos.get()).append("\n");
        json.append("  }\n");
        json.append("}\n");

        Path parent = outputPath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.writeString(outputPath, json.toString());
    }
}
